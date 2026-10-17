package net.minecraft.world.level.storage;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.ref.Reference;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.util.RandomSource;

/**
 * Native world clock, weather cycle and sleep-threshold policy. One {@link Clock}
 * per primary level data owns time and weather counters; each server dimension
 * holds a {@link View} with dimension-local rain/thunder levels.
 *
 * <p>Thread contract: Java never reads native memory directly. Clock fields are
 * Rust atomics read and written only through native calls, so any thread may use
 * a clock. A view is mutated only on its owner thread (the thread of its first
 * mutation, normally the server thread); other threads get an exception. Other
 * threads may read a view's {@link Levels}, an immutable snapshot.
 */
public final class NativeWeatherTime {
	private static MethodHandle handle(String name, FunctionDescriptor descriptor) {
		return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_weather_time_" + name, descriptor, Linker.Option.critical(false));
	}
	/** Allocation and release run outside critical regions. */
	private static MethodHandle lifecycle(String name, FunctionDescriptor descriptor) {
		return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_weather_time_" + name, descriptor);
	}
	private static final ValueLayout.OfInt I = ValueLayout.JAVA_INT;
	private static final ValueLayout.OfLong J = ValueLayout.JAVA_LONG;
	private static final AddressLayout P = ValueLayout.ADDRESS;
	// Hot calls pass the owner address as a long (same ABI as a pointer on 64-bit targets).
	private static final MethodHandle CREATE = lifecycle("create", FunctionDescriptor.of(P, J, J, I, I, I, I, I));
	private static final MethodHandle RELEASE = lifecycle("release", FunctionDescriptor.ofVoid(P));
	private static final MethodHandle GAME_TIME = handle("game_time", FunctionDescriptor.of(J, J));
	private static final MethodHandle DAY_TIME = handle("day_time", FunctionDescriptor.of(J, J));
	private static final MethodHandle CLEAR_WEATHER_TIME = handle("clear_weather_time", FunctionDescriptor.of(I, J));
	private static final MethodHandle RAIN_TIME = handle("rain_time", FunctionDescriptor.of(I, J));
	private static final MethodHandle THUNDER_TIME = handle("thunder_time", FunctionDescriptor.of(I, J));
	private static final MethodHandle RAINING = handle("raining", FunctionDescriptor.of(I, J));
	private static final MethodHandle THUNDERING = handle("thundering", FunctionDescriptor.of(I, J));
	private static final MethodHandle SET_GAME_TIME = handle("set_game_time", FunctionDescriptor.ofVoid(J, J));
	private static final MethodHandle SET_DAY_TIME = handle("set_day_time", FunctionDescriptor.ofVoid(J, J));
	private static final MethodHandle SET_CLEAR_WEATHER_TIME = handle("set_clear_weather_time", FunctionDescriptor.ofVoid(J, I));
	private static final MethodHandle SET_RAIN_TIME = handle("set_rain_time", FunctionDescriptor.ofVoid(J, I));
	private static final MethodHandle SET_THUNDER_TIME = handle("set_thunder_time", FunctionDescriptor.ofVoid(J, I));
	private static final MethodHandle SET_RAINING = handle("set_raining", FunctionDescriptor.ofVoid(J, I));
	private static final MethodHandle SET_THUNDERING = handle("set_thundering", FunctionDescriptor.ofVoid(J, I));
	private static final MethodHandle VIEW_CREATE = lifecycle("view_create", FunctionDescriptor.of(P, P, I));
	private static final MethodHandle VIEW_RELEASE = lifecycle("view_release", FunctionDescriptor.ofVoid(P));
	private static final MethodHandle VIEW_WRITABLE = handle("view_writable", FunctionDescriptor.of(I, J));
	private static final MethodHandle VIEW_LEVELS = handle("view_levels", FunctionDescriptor.ofVoid(J, P));
	private static final MethodHandle VIEW_PREPARE = handle("view_prepare", FunctionDescriptor.ofVoid(J));
	private static final MethodHandle VIEW_SET_RAIN_LEVEL = handle("view_set_rain_level", FunctionDescriptor.ofVoid(J, ValueLayout.JAVA_FLOAT));
	private static final MethodHandle VIEW_SET_THUNDER_LEVEL = handle("view_set_thunder_level", FunctionDescriptor.ofVoid(J, ValueLayout.JAVA_FLOAT));
	private static final MethodHandle VIEW_TICK_WEATHER = handle("view_tick_weather", FunctionDescriptor.of(I, J, I, I));
	private static final MethodHandle VIEW_COMMIT = handle("view_commit", FunctionDescriptor.of(I, J, I, I, I));
	private static final MethodHandle VIEW_RESET_WEATHER = handle("view_reset_weather", FunctionDescriptor.ofVoid(J));
	private static final MethodHandle VIEW_SET_WEATHER = handle("view_set_weather", FunctionDescriptor.ofVoid(J, I, I, I, I));
	private static final MethodHandle VIEW_ADVANCE_GAME_TIME = handle("view_advance_game_time", FunctionDescriptor.of(J, J));
	private static final MethodHandle VIEW_ADVANCE_DAY_TIME = handle("view_advance_day_time", FunctionDescriptor.ofVoid(J));
	private static final MethodHandle VIEW_SKIP_TO_MORNING = handle("view_skip_to_morning", FunctionDescriptor.ofVoid(J));
	private static final MethodHandle SLEEPERS_NEEDED = handle("sleepers_needed", FunctionDescriptor.of(I, I, I));
	private static final MethodHandle LIVE = lifecycle("live", FunctionDescriptor.of(J));

	/** Plan bits; samples are drawn in this order. */
	public static final int PLANNED = 1, THUNDER_SAMPLE = 2, THUNDER_DURATION = 4, RAIN_SAMPLE = 8, RAIN_DURATION = 16;

	private NativeWeatherTime() {
	}

	private static RuntimeException failure(Throwable t) {
		if (t instanceof RuntimeException r) return r;
		if (t instanceof Error e) throw e;
		return new IllegalStateException("Native weather/time call failed", t);
	}

	/** Attach a cleaner to a fresh native pointer, releasing it if attachment fails. */
	private static MemorySegment adopt(MemorySegment pointer, MethodHandle release, String what) {
		if (pointer.address() == 0L) {
			throw new IllegalStateException("Native " + what + " allocation failed");
		}
		boolean attached = false;
		try {
			// The cleaner captures only the static release handle, never the owner.
			MemorySegment owned = pointer.reinterpret(1, Arena.ofAuto(), p -> releaseQuietly(release, p));
			attached = true;
			return owned;
		} finally {
			if (!attached) releaseQuietly(release, pointer);
		}
	}
	private static void releaseQuietly(MethodHandle release, MemorySegment pointer) {
		try {
			release.invokeExact(pointer);
		} catch (Throwable t) {
			throw failure(t);
		}
	}

	/** Draws one weather duration: {@code sample} is a plan bit, {@code duration} non-zero for a duration. */
	@FunctionalInterface
	public interface Sampler {
		int sample(RandomSource random, int sample, int duration);
	}

	/** Canonical time/weather owner of one primary level data. Safe from any thread. */
	public static final class Clock {
		final MemorySegment owner;
		/** Raw owner address for downcalls; {@code owner} keeps it alive (fenced per call). */
		private final long address;

		public Clock(long gameTime, long dayTime, int clearWeatherTime, int rainTime, boolean raining, int thunderTime, boolean thundering) {
			try {
				var pointer = (MemorySegment)CREATE.invokeExact(gameTime, dayTime, clearWeatherTime, rainTime, raining ? 1 : 0, thunderTime, thundering ? 1 : 0);
				this.owner = adopt(pointer, RELEASE, "clock");
				this.address = this.owner.address();
			} catch (Throwable t) {
				throw failure(t);
			}
		}

		public long gameTime() { return this.getLong(GAME_TIME); }
		public long dayTime() { return this.getLong(DAY_TIME); }
		public int clearWeatherTime() { return this.getInt(CLEAR_WEATHER_TIME); }
		public int rainTime() { return this.getInt(RAIN_TIME); }
		public int thunderTime() { return this.getInt(THUNDER_TIME); }
		public boolean raining() { return this.getInt(RAINING) != 0; }
		public boolean thundering() { return this.getInt(THUNDERING) != 0; }

		public void setGameTime(long value) { this.set(SET_GAME_TIME, value); }
		public void setDayTime(long value) { this.set(SET_DAY_TIME, value); }
		public void setClearWeatherTime(int value) { this.set(SET_CLEAR_WEATHER_TIME, value); }
		public void setRainTime(int value) { this.set(SET_RAIN_TIME, value); }
		public void setThunderTime(int value) { this.set(SET_THUNDER_TIME, value); }
		public void setRaining(boolean value) { this.set(SET_RAINING, value ? 1 : 0); }
		public void setThundering(boolean value) { this.set(SET_THUNDERING, value ? 1 : 0); }

		private long getLong(MethodHandle getter) {
			try {
				return (long)getter.invokeExact(this.address);
			} catch (Throwable t) {
				throw failure(t);
			} finally {
				Reference.reachabilityFence(this);
			}
		}
		private int getInt(MethodHandle getter) {
			try {
				return (int)getter.invokeExact(this.address);
			} catch (Throwable t) {
				throw failure(t);
			} finally {
				Reference.reachabilityFence(this);
			}
		}
		private void set(MethodHandle setter, long value) {
			try {
				setter.invokeExact(this.address, value);
			} catch (Throwable t) {
				throw failure(t);
			} finally {
				Reference.reachabilityFence(this);
			}
		}
		private void set(MethodHandle setter, int value) {
			try {
				setter.invokeExact(this.address, value);
			} catch (Throwable t) {
				throw failure(t);
			} finally {
				Reference.reachabilityFence(this);
			}
		}
	}

	/** One consistent set of a view's interpolation levels. */
	public record Levels(float oRain, float rain, float oThunder, float thunder) {
		static final Levels ZERO = new Levels(0.0F, 0.0F, 0.0F, 0.0F);
	}

	/** One dimension's interpolation levels over a shared clock. Mutated only on its owner thread. */
	public static final class View {
		private final MemorySegment owner;
		/** Raw owner address for downcalls; {@code owner} keeps it alive (fenced per call). */
		private final long address;
		private final Clock clock;
		/** Native-decided and immutable; a read-only view skips its no-op time downcalls. */
		private final boolean writable;
		/** Output buffer for {@code view_levels}; touched only on the owner thread. */
		private final MemorySegment levelsBuffer = Arena.ofAuto().allocate(16, 4);
		/**
		 * Read-only projection of the native levels, replaced after every native call
		 * that can change them. Rust remains the only writer of the levels; this is
		 * never passed back. Immutable, so concurrent readers never see a mix.
		 */
		private volatile Levels levels = Levels.ZERO;
		/**
		 * The only thread allowed to mutate this view, claimed atomically by the first
		 * mutation. In production that is the server thread: ServerLevel's constructor
		 * calls prepare() on the thread that then ticks the level.
		 */
		private final AtomicReference<Thread> ownerThread = new AtomicReference<>();

		/** Derived dimensions read the shared clock but never write it. */
		public View(Clock clock, boolean primaryData) {
			this.clock = clock;
			try {
				var pointer = (MemorySegment)VIEW_CREATE.invokeExact(clock.owner, primaryData ? 1 : 0);
				this.owner = adopt(pointer, VIEW_RELEASE, "weather view");
				this.address = this.owner.address();
				this.writable = (int)VIEW_WRITABLE.invokeExact(this.address) != 0;
			} catch (Throwable t) {
				throw failure(t);
			} finally {
				Reference.reachabilityFence(clock);
			}
		}

		public Clock clock() { return this.clock; }
		public boolean writable() { return this.writable; }
		/** The latest consistent levels; safe from any thread. */
		public Levels levels() { return this.levels; }
		public float oRainLevel() { return this.levels.oRain(); }
		public float rainLevel() { return this.levels.rain(); }
		public float oThunderLevel() { return this.levels.oThunder(); }
		public float thunderLevel() { return this.levels.thunder(); }

		/** Fail fast if a second thread tries to mutate this view. */
		private void checkOwner() {
			Thread current = Thread.currentThread();
			Thread owner = this.ownerThread.get();
			if (owner == current) {
				return;
			}
			if (owner == null && this.ownerThread.compareAndSet(null, current)) {
				return;
			}
			owner = this.ownerThread.get();
			if (owner != current) {
				throw new IllegalStateException("Weather view mutated from " + current.getName() + ", owned by " + owner.getName());
			}
		}

		private void refreshLevels() {
			try {
				VIEW_LEVELS.invokeExact(this.address, this.levelsBuffer);
				this.levels = new Levels(this.levelsBuffer.getAtIndex(ValueLayout.JAVA_FLOAT, 0), this.levelsBuffer.getAtIndex(ValueLayout.JAVA_FLOAT, 1),
					this.levelsBuffer.getAtIndex(ValueLayout.JAVA_FLOAT, 2), this.levelsBuffer.getAtIndex(ValueLayout.JAVA_FLOAT, 3));
			} catch (Throwable t) {
				throw failure(t);
			} finally {
				Reference.reachabilityFence(this);
			}
		}

		public void prepare() {
			this.checkOwner();
			this.call(VIEW_PREPARE);
			this.refreshLevels();
		}
		public void setRainLevel(float f) {
			this.checkOwner();
			this.call(VIEW_SET_RAIN_LEVEL, f);
			this.refreshLevels();
		}
		public void setThunderLevel(float f) {
			this.checkOwner();
			this.call(VIEW_SET_THUNDER_LEVEL, f);
			this.refreshLevels();
		}
		public void resetWeather() {
			this.checkOwner();
			this.call(VIEW_RESET_WEATHER);
		}
		/** Last {@code tickTime} phase; a read-only view's advance is a no-op. */
		public void advanceDayTime() {
			this.checkOwner();
			if (this.writable) this.call(VIEW_ADVANCE_DAY_TIME);
		}
		public void skipToMorning() {
			this.checkOwner();
			this.call(VIEW_SKIP_TO_MORNING);
		}

		public void setWeather(int clearWeatherTime, int duration, boolean raining, boolean thundering) {
			this.checkOwner();
			try {
				VIEW_SET_WEATHER.invokeExact(this.address, clearWeatherTime, duration, raining ? 1 : 0, thundering ? 1 : 0);
			} catch (Throwable t) {
				throw failure(t);
			} finally {
				Reference.reachabilityFence(this);
			}
		}

		/** First {@code tickTime} phase: the game time scheduled functions observe. */
		public long advanceGameTime() {
			this.checkOwner();
			if (!this.writable) {
				// The native read-only view returns this without writing.
				return this.clock.gameTime() + 1L;
			}
			try {
				return (long)VIEW_ADVANCE_GAME_TIME.invokeExact(this.address);
			} catch (Throwable t) {
				throw failure(t);
			} finally {
				Reference.reachabilityFence(this);
			}
		}

		/**
		 * One weather-cycle step. {@code sampler} draws from the level's own random
		 * source and is called for exactly the samples the native policy requests,
		 * thunder before rain.
		 */
		public void advanceWeather(boolean hasSkyLight, boolean cycle, RandomSource random, Sampler sampler) {
			this.checkOwner();
			try {
				int plan = (int)VIEW_TICK_WEATHER.invokeExact(this.address, hasSkyLight ? 1 : 0, cycle ? 1 : 0);
				if (plan == 0) {
					this.refreshLevels();
					return;
				}
				int thunder = (plan & THUNDER_SAMPLE) != 0 ? sampler.sample(random, THUNDER_SAMPLE, plan & THUNDER_DURATION) : 0;
				int rain = (plan & RAIN_SAMPLE) != 0 ? sampler.sample(random, RAIN_SAMPLE, plan & RAIN_DURATION) : 0;
				int result = (int)VIEW_COMMIT.invokeExact(this.address, plan, thunder, rain);
				this.refreshLevels();
				if (result != 0) {
					// Samples already consumed the level's random source; never replan.
					throw new IllegalStateException("Native weather commit rejected (" + result + ")");
				}
			} catch (Throwable t) {
				throw failure(t);
			} finally {
				Reference.reachabilityFence(this);
			}
		}

		private void call(MethodHandle handle) {
			try {
				handle.invokeExact(this.address);
			} catch (Throwable t) {
				throw failure(t);
			} finally {
				Reference.reachabilityFence(this);
			}
		}
		private void call(MethodHandle handle, float f) {
			try {
				handle.invokeExact(this.address, f);
			} catch (Throwable t) {
				throw failure(t);
			} finally {
				Reference.reachabilityFence(this);
			}
		}
	}

	/** Live native clocks plus views; cleaners release them after Java unreachability. */
	public static long liveAllocations() {
		try {
			return (long)LIVE.invokeExact();
		} catch (Throwable t) {
			throw failure(t);
		}
	}

	public static int sleepersNeeded(int activePlayers, int percentage) {
		try {
			return (int)SLEEPERS_NEEDED.invokeExact(activePlayers, percentage);
		} catch (Throwable t) {
			throw failure(t);
		}
	}
}
