package net.minecraft.client.sounds;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.util.NativeLibraryLoader;
import org.jetbrains.annotations.Nullable;

/**
 * Bindings for the Rust sound policy (`audio/policy`): the `sounds.json`
 * registry, event resolution, the playback engine and its log lines.
 * {@link NativeSoundClient} binds the Rust-owned sounds. Per-play and
 * per-tick calls reuse one native scratch region per thread.
 */
@Environment(EnvType.CLIENT)
final class NativeSoundPolicy {
	static final int RESOLVE_UNKNOWN = 0;
	static final int RESOLVE_NEED_DRAW = 1;
	static final int RESOLVE_EMPTY = 2;
	static final int RESOLVE_SOUND = 3;
	static final int RESOLVED_STREAM = 1;
	static final int RESOLVED_PRELOAD = 1 << 1;
	static final int PLAY_FLAG_LINEAR = 1;
	static final int PLAY_FLAG_RELATIVE = 1 << 1;
	static final int PLAY_FLAG_LOOPING = 1 << 2;
	static final int PLAY_FLAG_STREAM = 1 << 3;
	static final int PLAY_FLAG_CAN_START_SILENT = 1 << 4;
	/** Result bit of plays and ticks: asset loads wait to be taken. */
	static final int ASSET_REQUESTS = 0x100;
	static final int TICK_REOPENED = 1;
	static final int TICK_STOPPED = 1 << 1;
	static final long RELEASED_REQUEUED = 1L << 63;
	static final long OUTCOME_STOPPED = 1L << 63;
	static final long OUTCOME_SWITCHED = 1L << 62;
	private static final int TICK_PLAIN = 1 << 31;
	private static final long[] NO_IDS = new long[0];
	private static final int MAX_DRAWS = 64;
	private static final long RESOLVED_SIZE = 28L;
	private static final long PLAY_REQUEST_SIZE = 80L;
	private static final long TICKING_UPDATE_SIZE = 48L;
	private static final int CATEGORY_COUNT = 11;
	private static final long RESOURCE_SIZE = 48L;

	private static final MethodHandle REGISTRY_BUILD = handle("registry_build", ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);
	private static final MethodHandle REGISTRY_WARNINGS = handle("registry_warnings", ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);
	private static final MethodHandle REGISTRY_INSTALL = handle("registry_install", ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG);
	private static final MethodHandle REGISTRY_DISCARD = voidHandle("registry_discard", ValueLayout.JAVA_LONG);
	private static final MethodHandle EVENT_NAMES = handle("event_names", ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);
	private static final MethodHandle EVENT_LOOKUP = handle("event_lookup", ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);
	private static final MethodHandle EVENT_WEIGHT = handle("event_weight", ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);
	private static final MethodHandle RESOLVE = handle("resolve", ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);
	private static final MethodHandle ENGINE_INIT = handle("engine_init", ValueLayout.JAVA_LONG, ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);
	private static final MethodHandle ENGINE_DESTROY = voidHandle("engine_destroy");
	private static final MethodHandle ENGINE_EMERGENCY_SHUTDOWN = voidHandle("engine_emergency_shutdown");
	private static final MethodHandle ENGINE_SET_CATEGORY_VOLUMES = handle("engine_set_category_volumes", ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS, ValueLayout.JAVA_INT);
	private static final MethodHandle ENGINE_PLAY = handle("engine_play", ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
		ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);
	private static final MethodHandle ENGINE_PLAY_DELAYED = voidHandle("engine_play_delayed", ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT);
	private static final MethodHandle ENGINE_KNOWS = handle("engine_knows", ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG);
	private static final MethodHandle ENGINE_STOP = voidHandle("engine_stop", ValueLayout.JAVA_LONG);
	private static final MethodHandle ENGINE_STOP_MATCHING = voidHandle("engine_stop_matching", ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT);
	private static final MethodHandle ENGINE_STOP_ALL = voidHandle("engine_stop_all");
	private static final MethodHandle ENGINE_SET_VOLUME = voidHandle("engine_set_volume", ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_FLOAT);
	private static final MethodHandle ENGINE_UPDATE_TICKING = handle("engine_update_ticking", ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);
	private static final MethodHandle ENGINE_PAUSE_ALL_EXCEPT = voidHandle("engine_pause_all_except", ValueLayout.JAVA_INT);
	private static final MethodHandle ENGINE_RESUME = voidHandle("engine_resume");
	private static final MethodHandle ENGINE_LISTENER = handle("engine_listener", ValueLayout.JAVA_INT, ValueLayout.ADDRESS);
	private static final MethodHandle ENGINE_TICK_BEGIN = handle("engine_tick_begin", ValueLayout.JAVA_INT, ValueLayout.JAVA_INT);
	private static final MethodHandle ENGINE_TICK_END = handle("engine_tick_end", ValueLayout.JAVA_INT, ValueLayout.JAVA_INT);
	private static final MethodHandle ENGINE_DEVICE_TICK = voidHandle("engine_device_tick");
	private static final MethodHandle ENGINE_TAKE_RELEASED = handle("engine_take_released", ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);
	private static final MethodHandle ENGINE_TAKE_DUE = handle("engine_take_due", ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);
	private static final MethodHandle ENGINE_TAKE_ASSET_REQUESTS = handle("engine_take_asset_requests", ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);
	private static final MethodHandle ENGINE_PROVIDE_ASSET = handle("engine_provide_asset", ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);
	private static final MethodHandle ENGINE_FAIL_ASSET = voidHandle("engine_fail_asset", ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG);
	private static final MethodHandle ENGINE_POOL_COUNTS = handle("engine_pool_counts", ValueLayout.JAVA_INT, ValueLayout.ADDRESS);
	private static final MethodHandle TAKE_LOGS = handle("take_logs", ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);
	private static final MethodHandle WARN_ONCE = handle("warn_once", ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);
	private static final MethodHandle RESET_WARNINGS = voidHandle("reset_warnings", ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);

	/** Per-thread native scratch: strings, draws, records and result buffers. */
	static final class Scratch {
		final Arena arena = Arena.ofAuto();
		MemorySegment text = this.arena.allocate(1024);
		MemorySegment text2 = this.arena.allocate(1024);
		MemorySegment text3 = this.arena.allocate(256);
		MemorySegment out = this.arena.allocate(1024);
		final MemorySegment draws = this.arena.allocate(ValueLayout.JAVA_INT, MAX_DRAWS);
		final MemorySegment record = this.arena.allocate(96, 8);
		final MemorySegment output = this.arena.allocate(64, 8);
		final MemorySegment longs = this.arena.allocate(ValueLayout.JAVA_LONG, 512);
		MemorySegment ticking = this.arena.allocate(TICKING_UPDATE_SIZE * 64, 8);
		MemorySegment outcomes = this.arena.allocate(ValueLayout.JAVA_LONG, 64);

		MemorySegment text(MemorySegment current, byte[] bytes) {
			return current.byteSize() >= bytes.length ? current : this.arena.allocate(Math.max(bytes.length, current.byteSize() * 2));
		}
	}

	static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);
	/** Reads waiting asset requests; set by the sound engine. */
	private static volatile Runnable assetLoader = () -> {
	};

	private NativeSoundPolicy() {
	}

	static MethodHandle handle(String name, ValueLayout result, ValueLayout... arguments) {
		return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_sound_" + name, FunctionDescriptor.of(result, arguments));
	}

	static MethodHandle voidHandle(String name, ValueLayout... arguments) {
		return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_sound_" + name, FunctionDescriptor.ofVoid(arguments));
	}

	static IllegalStateException failure(String operation, Throwable throwable) {
		if (throwable instanceof RuntimeException runtime && !(throwable instanceof IllegalStateException)) {
			return new IllegalStateException(operation + " failed", runtime);
		}
		return throwable instanceof IllegalStateException state ? state : new IllegalStateException(operation + " failed", throwable);
	}

	/** Writes `value` into a scratch text slot; returns its byte length. */
	static long putText(Scratch scratch, boolean second, String value) {
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		if (second) {
			scratch.text2 = scratch.text(scratch.text2, bytes);
			MemorySegment.copy(bytes, 0, scratch.text2, ValueLayout.JAVA_BYTE, 0, bytes.length);
		} else {
			scratch.text = scratch.text(scratch.text, bytes);
			MemorySegment.copy(bytes, 0, scratch.text, ValueLayout.JAVA_BYTE, 0, bytes.length);
		}
		return bytes.length;
	}

	/** Writes `value` into the third scratch text slot; returns its byte length. */
	static long putThirdText(Scratch scratch, String value) {
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		scratch.text3 = scratch.text(scratch.text3, bytes);
		MemorySegment.copy(bytes, 0, scratch.text3, ValueLayout.JAVA_BYTE, 0, bytes.length);
		return bytes.length;
	}

	static void setAssetLoader(Runnable loader) {
		assetLoader = loader;
	}

	/** Runs the asset loader when a result carries {@link #ASSET_REQUESTS}; returns the result. */
	static int loadAssetsIfRequested(int result) {
		if ((result & ASSET_REQUESTS) != 0) {
			assetLoader.run();
		}
		return result;
	}

	static String readText(MemorySegment segment, long length) {
		return length <= 0 ? "" : new String(segment.asSlice(0, length).toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8);
	}

	/** A `sounds.json` resource to register, in resource-stack order. */
	record Resource(String namespace, String pack, String json) {
	}

	/** Builds a registry (any thread); the handle is installed or discarded once. */
	static long buildRegistry(long generation, List<Resource> resources, String files) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment records = arena.allocate(RESOURCE_SIZE * Math.max(1, resources.size()), 8);
			for (int index = 0; index < resources.size(); index++) {
				Resource resource = resources.get(index);
				long base = index * RESOURCE_SIZE;
				byte[][] parts = {
					resource.namespace().getBytes(StandardCharsets.UTF_8),
					resource.pack().getBytes(StandardCharsets.UTF_8),
					resource.json().getBytes(StandardCharsets.UTF_8)
				};
				for (int part = 0; part < 3; part++) {
					records.set(ValueLayout.ADDRESS, base + part * 16L, arena.allocateFrom(ValueLayout.JAVA_BYTE, parts[part]));
					records.set(ValueLayout.JAVA_LONG, base + part * 16L + 8L, parts[part].length);
				}
			}
			byte[] fileBytes = files.getBytes(StandardCharsets.UTF_8);
			long handle = (long)REGISTRY_BUILD.invokeExact(generation, records, (long)resources.size(),
				arena.allocateFrom(ValueLayout.JAVA_BYTE, fileBytes), (long)fileBytes.length);
			if (handle == 0L) {
				throw new IllegalStateException("Rust sound registry rejected its input");
			}
			return handle;
		} catch (Throwable throwable) {
			throw failure("Build sound registry", throwable);
		}
	}

	static List<String> registryWarnings(long handle) {
		try (Arena arena = Arena.ofConfined()) {
			long length = (long)REGISTRY_WARNINGS.invokeExact(handle, MemorySegment.NULL, 0L);
			if (length <= 0L) {
				return List.of();
			}
			MemorySegment out = arena.allocate(length);
			long written = (long)REGISTRY_WARNINGS.invokeExact(handle, out, length);
			return List.of(readText(out, Math.min(written, length)).split("\n"));
		} catch (Throwable throwable) {
			throw failure("Read sound registry warnings", throwable);
		}
	}

	static void installRegistry(long handle) {
		try {
			int status = (int)REGISTRY_INSTALL.invokeExact(handle);
			if (status != 0) {
				throw new IllegalStateException("Install sound registry failed: " + status);
			}
		} catch (Throwable throwable) {
			throw failure("Install sound registry", throwable);
		}
	}

	static void discardRegistry(long handle) {
		try {
			REGISTRY_DISCARD.invokeExact(handle);
		} catch (Throwable throwable) {
			throw failure("Discard sound registry", throwable);
		}
	}

	static List<String> eventNames() {
		try (Arena arena = Arena.ofConfined()) {
			long length = (long)EVENT_NAMES.invokeExact(MemorySegment.NULL, 0L);
			if (length <= 0L) {
				return List.of();
			}
			MemorySegment out = arena.allocate(length);
			long written = (long)EVENT_NAMES.invokeExact(out, length);
			return List.of(readText(out, Math.min(written, length)).split("\n"));
		} catch (Throwable throwable) {
			throw failure("List sound events", throwable);
		}
	}

	/** Event lookup: null when unknown, else its optional subtitle key. */
	@Nullable
	static EventInfo lookupEvent(String name) {
		Scratch scratch = SCRATCH.get();
		try {
			long nameLength = putText(scratch, false, name);
			long result = (long)EVENT_LOOKUP.invokeExact(scratch.text, nameLength, scratch.out, scratch.out.byteSize());
			if (result == -2L) {
				return null;
			}
			if (result == -1L) {
				return new EventInfo(null);
			}
			if (result > scratch.out.byteSize()) {
				scratch.out = scratch.arena.allocate(result);
				result = (long)EVENT_LOOKUP.invokeExact(scratch.text, nameLength, scratch.out, scratch.out.byteSize());
			}
			return new EventInfo(readText(scratch.out, result));
		} catch (Throwable throwable) {
			throw failure("Look up sound event", throwable);
		}
	}

	record EventInfo(@Nullable String subtitle) {
	}

	static int eventWeight(String name) {
		Scratch scratch = SCRATCH.get();
		try {
			long nameLength = putText(scratch, false, name);
			return (int)EVENT_WEIGHT.invokeExact(scratch.text, nameLength);
		} catch (Throwable throwable) {
			throw failure("Read sound event weight", throwable);
		}
	}

	/** The result of one resolution step; `bound` is set for {@link #RESOLVE_NEED_DRAW}. */
	record Resolved(int kind, int bound, String location, float volume, float pitch, int weight, int flags, int attenuationDistance) {
	}

	/** Draws one bound at a time from the caller's random source. */
	interface Draws {
		int next(int bound);
	}

	static Resolved resolve(String name, Draws draws) {
		Scratch scratch = SCRATCH.get();
		try {
			long nameLength = putText(scratch, false, name);
			int count = 0;
			while (true) {
				int kind = (int)RESOLVE.invokeExact(scratch.text, nameLength, scratch.draws, (long)count, scratch.record,
					scratch.out, scratch.out.byteSize());
				if (kind < 0) {
					throw new IllegalStateException("Resolve sound event failed: " + kind);
				}
				if (kind == RESOLVE_NEED_DRAW) {
					if (count == MAX_DRAWS) {
						return new Resolved(RESOLVE_EMPTY, 0, "", 1.0F, 1.0F, 1, 0, 16);
					}
					int bound = scratch.record.get(ValueLayout.JAVA_INT, 0);
					scratch.draws.setAtIndex(ValueLayout.JAVA_INT, count++, draws.next(bound));
					continue;
				}
				if (kind != RESOLVE_SOUND) {
					return new Resolved(kind, 0, "", 1.0F, 1.0F, 1, 0, 16);
				}
				int locationLength = scratch.record.get(ValueLayout.JAVA_INT, 24);
				if (locationLength > scratch.out.byteSize()) {
					scratch.out = scratch.arena.allocate(locationLength);
					continue;
				}
				return new Resolved(kind, 0, readText(scratch.out, locationLength),
					scratch.record.get(ValueLayout.JAVA_FLOAT, 8), scratch.record.get(ValueLayout.JAVA_FLOAT, 12),
					scratch.record.get(ValueLayout.JAVA_INT, 4), scratch.record.get(ValueLayout.JAVA_INT, 20),
					scratch.record.get(ValueLayout.JAVA_INT, 16));
			}
		} catch (Throwable throwable) {
			throw failure("Resolve sound event", throwable);
		}
	}

	/** Opens the device; returns null on success, else the error text. */
	@Nullable
	static String engineInit(@Nullable String preferred, boolean hrtf) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment name = MemorySegment.NULL;
			long nameLength = 0L;
			if (preferred != null) {
				byte[] bytes = preferred.getBytes(StandardCharsets.UTF_8);
				name = arena.allocateFrom(ValueLayout.JAVA_BYTE, bytes);
				nameLength = bytes.length;
			}
			MemorySegment error = arena.allocate(1024);
			long result = (long)ENGINE_INIT.invokeExact(name, nameLength, hrtf ? 1 : 0, error, 1024L);
			return result == 0L ? null : readText(error, Math.min(-result, 1024L));
		} catch (Throwable throwable) {
			throw failure("Open sound device", throwable);
		}
	}

	static void engineDestroy() {
		invoke(ENGINE_DESTROY, "Destroy sound engine");
	}

	static void engineEmergencyShutdown() {
		invoke(ENGINE_EMERGENCY_SHUTDOWN, "Emergency sound shutdown");
	}

	private static void invoke(MethodHandle handle, String operation) {
		try {
			handle.invokeExact();
		} catch (Throwable throwable) {
			throw failure(operation, throwable);
		}
	}

	static void setCategoryVolumes(float[] volumes, boolean refresh) {
		Scratch scratch = SCRATCH.get();
		try {
			MemorySegment.copy(volumes, 0, scratch.record, ValueLayout.JAVA_FLOAT, 0, CATEGORY_COUNT);
			int status = (int)ENGINE_SET_CATEGORY_VOLUMES.invokeExact(scratch.record, refresh ? 1 : 0);
			if (status != 0) {
				throw new IllegalStateException("Set sound category volumes failed: " + status);
			}
		} catch (Throwable throwable) {
			throw failure("Set sound category volumes", throwable);
		}
	}

	/** A ruled instance's rule and starting fields, or {@link #NO_RULE}. */
	record Rule(int kind, int parameter, float baseVolume, float basePitch, float soundVolume, float soundPitch) {
	}

	static final Rule NO_RULE = new Rule(0, 0, 0.0F, 0.0F, 0.0F, 0.0F);

	/** Returns the `PlayResult` ordinal, with {@link #ASSET_REQUESTS} set when loads wait. */
	static int play(long instance, double x, double y, double z, float volume, float pitch, int delay, int attenuationDistance,
		int category, int flags, Rule rule, String event, String path) {
		Scratch scratch = SCRATCH.get();
		try {
			MemorySegment record = scratch.record;
			record.set(ValueLayout.JAVA_LONG, 0, instance);
			record.set(ValueLayout.JAVA_DOUBLE, 8, x);
			record.set(ValueLayout.JAVA_DOUBLE, 16, y);
			record.set(ValueLayout.JAVA_DOUBLE, 24, z);
			record.set(ValueLayout.JAVA_FLOAT, 32, volume);
			record.set(ValueLayout.JAVA_FLOAT, 36, pitch);
			record.set(ValueLayout.JAVA_INT, 40, delay);
			record.set(ValueLayout.JAVA_INT, 44, attenuationDistance);
			record.set(ValueLayout.JAVA_INT, 48, category);
			record.set(ValueLayout.JAVA_INT, 52, flags);
			record.set(ValueLayout.JAVA_INT, 56, rule.kind());
			record.set(ValueLayout.JAVA_INT, 60, rule.parameter());
			record.set(ValueLayout.JAVA_FLOAT, 64, rule.baseVolume());
			record.set(ValueLayout.JAVA_FLOAT, 68, rule.basePitch());
			record.set(ValueLayout.JAVA_FLOAT, 72, rule.soundVolume());
			record.set(ValueLayout.JAVA_FLOAT, 76, rule.soundPitch());
			long eventLength = putText(scratch, false, event);
			long pathLength = putText(scratch, true, path);
			return (int)ENGINE_PLAY.invokeExact(record, scratch.text, eventLength, scratch.text2, pathLength);
		} catch (Throwable throwable) {
			throw failure("Play sound", throwable);
		}
	}

	static void playDelayed(long instance, int delay) {
		try {
			ENGINE_PLAY_DELAYED.invokeExact(instance, delay);
		} catch (Throwable throwable) {
			throw failure("Queue delayed sound", throwable);
		}
	}

	static boolean knows(long instance) {
		try {
			return (int)ENGINE_KNOWS.invokeExact(instance) != 0;
		} catch (Throwable throwable) {
			throw failure("Query sound instance", throwable);
		}
	}

	static void stop(long instance) {
		try {
			ENGINE_STOP.invokeExact(instance);
		} catch (Throwable throwable) {
			throw failure("Stop sound", throwable);
		}
	}

	static void stopMatching(@Nullable String event, int category) {
		Scratch scratch = SCRATCH.get();
		try {
			if (event == null) {
				ENGINE_STOP_MATCHING.invokeExact(MemorySegment.NULL, 0L, category);
			} else {
				long length = putText(scratch, false, event);
				ENGINE_STOP_MATCHING.invokeExact(scratch.text, length, category);
			}
		} catch (Throwable throwable) {
			throw failure("Stop matching sounds", throwable);
		}
	}

	static void stopAll() {
		invoke(ENGINE_STOP_ALL, "Stop all sounds");
	}

	static void setVolume(long instance, float factor, float volume) {
		try {
			ENGINE_SET_VOLUME.invokeExact(instance, factor, volume);
		} catch (Throwable throwable) {
			throw failure("Set sound volume", throwable);
		}
	}

	/**
	 * Collects one tick's ticking updates into a single native call: plain
	 * tickables' values, or a ruled instance's sample for its Rust rule.
	 */
	static final class TickingBatch {
		private int count;

		void addPlain(long instance, float volume, float pitch, double x, double y, double z) {
			this.add(instance, TICK_PLAIN, volume, pitch, x, y, z);
		}

		void addSample(long instance, int flags, float value, double x, double y, double z) {
			this.add(instance, flags & ~TICK_PLAIN, value, 0.0F, x, y, z);
		}

		private void add(long instance, int flags, float value, float pitch, double x, double y, double z) {
			Scratch scratch = SCRATCH.get();
			long needed = (this.count + 1) * TICKING_UPDATE_SIZE;
			if (needed > scratch.ticking.byteSize()) {
				MemorySegment grown = scratch.arena.allocate(scratch.ticking.byteSize() * 2, 8);
				MemorySegment.copy(scratch.ticking, 0, grown, 0, this.count * TICKING_UPDATE_SIZE);
				scratch.ticking = grown;
			}
			long base = this.count++ * TICKING_UPDATE_SIZE;
			scratch.ticking.set(ValueLayout.JAVA_LONG, base, instance);
			scratch.ticking.set(ValueLayout.JAVA_INT, base + 8, flags);
			scratch.ticking.set(ValueLayout.JAVA_FLOAT, base + 12, value);
			scratch.ticking.set(ValueLayout.JAVA_FLOAT, base + 16, pitch);
			scratch.ticking.set(ValueLayout.JAVA_DOUBLE, base + 24, x);
			scratch.ticking.set(ValueLayout.JAVA_DOUBLE, base + 32, y);
			scratch.ticking.set(ValueLayout.JAVA_DOUBLE, base + 40, z);
		}

		/** Sends the batch; returns the ruled instances that stopped or switched. */
		long[] flush() {
			if (this.count == 0) {
				return NO_IDS;
			}
			Scratch scratch = SCRATCH.get();
			try {
				if (scratch.outcomes.byteSize() < this.count * 8L) {
					scratch.outcomes = scratch.arena.allocate(ValueLayout.JAVA_LONG, this.count);
				}
				long written = (long)ENGINE_UPDATE_TICKING.invokeExact(scratch.ticking, (long)this.count, scratch.outcomes,
					scratch.outcomes.byteSize() / 8L);
				if (written < 0L) {
					throw new IllegalStateException("Update ticking sounds failed: " + written);
				}
				return written == 0L ? NO_IDS : scratch.outcomes.asSlice(0, written * 8L).toArray(ValueLayout.JAVA_LONG);
			} catch (Throwable throwable) {
				throw failure("Update ticking sounds", throwable);
			} finally {
				this.count = 0;
			}
		}
	}

	static void pauseAllExcept(int keptCategories) {
		try {
			ENGINE_PAUSE_ALL_EXCEPT.invokeExact(keptCategories);
		} catch (Throwable throwable) {
			throw failure("Pause sounds", throwable);
		}
	}

	static void resume() {
		invoke(ENGINE_RESUME, "Resume sounds");
	}

	static void listener(double px, double py, double pz, double fx, double fy, double fz, double ux, double uy, double uz) {
		Scratch scratch = SCRATCH.get();
		try {
			float[] values = {(float)px, (float)py, (float)pz, (float)fx, (float)fy, (float)fz, (float)ux, (float)uy, (float)uz};
			MemorySegment.copy(values, 0, scratch.record, ValueLayout.JAVA_FLOAT, 0, 9);
			int status = (int)ENGINE_LISTENER.invokeExact(scratch.record);
			if (status != 0) {
				throw new IllegalStateException("Update sound listener failed: " + status);
			}
		} catch (Throwable throwable) {
			throw failure("Update sound listener", throwable);
		}
	}

	/** Returns {@link #TICK_REOPENED} / {@link #TICK_STOPPED} after a device change, and {@link #ASSET_REQUESTS}. */
	static int tickBegin(boolean paused) {
		try {
			return (int)ENGINE_TICK_BEGIN.invokeExact(paused ? 1 : 0);
		} catch (Throwable throwable) {
			throw failure("Begin sound tick", throwable);
		}
	}

	/** Returns {@link #ASSET_REQUESTS} when Rust-owned due sounds wait for assets. */
	static int tickEnd(boolean paused) {
		try {
			return (int)ENGINE_TICK_END.invokeExact(paused ? 1 : 0);
		} catch (Throwable throwable) {
			throw failure("End sound tick", throwable);
		}
	}

	static void deviceTick() {
		invoke(ENGINE_DEVICE_TICK, "Tick sound device");
	}

	/** Released instance IDs ({@link #RELEASED_REQUEUED} marks requeued ones). */
	static long[] takeReleased() {
		return takeLongs(ENGINE_TAKE_RELEASED, "Take released sounds");
	}

	static long[] takeDue() {
		return takeLongs(ENGINE_TAKE_DUE, "Take due sounds");
	}

	private static long[] takeLongs(MethodHandle handle, String operation) {
		Scratch scratch = SCRATCH.get();
		try {
			long[] result = NO_IDS;
			while (true) {
				long count = (long)handle.invokeExact(scratch.longs, 512L);
				if (count == 0L) {
					return result;
				}
				int start = result.length;
				result = java.util.Arrays.copyOf(result, start + (int)count);
				MemorySegment.copy(scratch.longs, ValueLayout.JAVA_LONG, 0, result, start, (int)count);
				if (count < 512L) {
					return result;
				}
			}
		} catch (Throwable throwable) {
			throw failure(operation, throwable);
		}
	}

	record AssetRequests(long generation, List<String> paths) {
	}

	static AssetRequests takeAssetRequests() {
		Scratch scratch = SCRATCH.get();
		try {
			MemorySegment generation = scratch.record;
			long length = (long)ENGINE_TAKE_ASSET_REQUESTS.invokeExact(scratch.out, scratch.out.byteSize(), generation);
			if (length > scratch.out.byteSize()) {
				scratch.out = scratch.arena.allocate(length);
				length = (long)ENGINE_TAKE_ASSET_REQUESTS.invokeExact(scratch.out, scratch.out.byteSize(), generation);
			}
			if (length <= 0L) {
				return new AssetRequests(0L, List.of());
			}
			List<String> paths = new ArrayList<>(List.of(readText(scratch.out, length).split("\n")));
			return new AssetRequests(generation.get(ValueLayout.JAVA_LONG, 0), paths);
		} catch (Throwable throwable) {
			throw failure("Take sound asset requests", throwable);
		}
	}

	/** Publishes a loaded asset; returns false when the request was stale. */
	static boolean provideAsset(String path, long generation, byte[] bytes) {
		try (Arena arena = Arena.ofConfined()) {
			byte[] name = path.getBytes(StandardCharsets.UTF_8);
			int result = (int)ENGINE_PROVIDE_ASSET.invokeExact(arena.allocateFrom(ValueLayout.JAVA_BYTE, name), (long)name.length,
				generation, arena.allocateFrom(ValueLayout.JAVA_BYTE, bytes), (long)bytes.length);
			if (result < 0) {
				throw new IllegalStateException("Rust rejected sound asset " + path + ": " + result);
			}
			return result == 1;
		} catch (Throwable throwable) {
			throw failure("Provide sound asset", throwable);
		}
	}

	static void failAsset(String path, long generation) {
		try (Arena arena = Arena.ofConfined()) {
			byte[] name = path.getBytes(StandardCharsets.UTF_8);
			ENGINE_FAIL_ASSET.invokeExact(arena.allocateFrom(ValueLayout.JAVA_BYTE, name), (long)name.length, generation);
		} catch (Throwable throwable) {
			throw failure("Fail sound asset", throwable);
		}
	}

	/** Queued Rust log lines, each prefixed `I `, `W ` or `E `. */
	static List<String> takeLogs() {
		Scratch scratch = SCRATCH.get();
		try {
			long length = (long)TAKE_LOGS.invokeExact(scratch.out, scratch.out.byteSize());
			if (length > scratch.out.byteSize()) {
				scratch.out = scratch.arena.allocate(length);
				length = (long)TAKE_LOGS.invokeExact(scratch.out, scratch.out.byteSize());
			}
			return length <= 0L ? List.of() : List.of(readText(scratch.out, length).split("\n"));
		} catch (Throwable throwable) {
			throw failure("Take sound log lines", throwable);
		}
	}

	/** `ONLY_WARN_ONCE.add(event)`, shared with Rust-owned plays. */
	static boolean warnOnce(String event) {
		Scratch scratch = SCRATCH.get();
		try {
			long length = putText(scratch, false, event);
			return (int)WARN_ONCE.invokeExact(scratch.text, length) != 0;
		} catch (Throwable throwable) {
			throw failure("Record sound warning", throwable);
		}
	}

	/** Resets the once-per-event warnings to the events found missing. */
	static void resetWarnings(List<String> missing) {
		Scratch scratch = SCRATCH.get();
		try {
			long length = putText(scratch, false, String.join("\n", missing));
			RESET_WARNINGS.invokeExact(scratch.text, length);
		} catch (Throwable throwable) {
			throw failure("Reset sound warnings", throwable);
		}
	}

	static int[] poolCounts() {
		Scratch scratch = SCRATCH.get();
		try {
			int status = (int)ENGINE_POOL_COUNTS.invokeExact(scratch.record);
			if (status != 0) {
				throw new IllegalStateException("Read sound pool counts failed: " + status);
			}
			return scratch.record.asSlice(0, 16).toArray(ValueLayout.JAVA_INT);
		} catch (Throwable throwable) {
			throw failure("Read sound pool counts", throwable);
		}
	}
}
