package net.minecraft.client.sounds;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import org.jetbrains.annotations.Nullable;

import static net.minecraft.client.sounds.NativeSoundPolicy.SCRATCH;
import static net.minecraft.client.sounds.NativeSoundPolicy.failure;
import static net.minecraft.client.sounds.NativeSoundPolicy.handle;
import static net.minecraft.client.sounds.NativeSoundPolicy.loadAssetsIfRequested;
import static net.minecraft.client.sounds.NativeSoundPolicy.putText;
import static net.minecraft.client.sounds.NativeSoundPolicy.putThirdText;
import static net.minecraft.client.sounds.NativeSoundPolicy.readText;
import static net.minecraft.client.sounds.NativeSoundPolicy.voidHandle;

/**
 * Bindings for the sounds Rust owns outright (`audio/policy`): seeded world
 * sound events, music, the local player's ambience, the volume preview and
 * the subtitle overlay's frames. Calls that can start a sound read any asset
 * requests it made right away.
 */
@Environment(EnvType.CLIENT)
final class NativeSoundClient {
	static final int MUSIC_SHOW_TOAST = 1;
	static final int MUSIC_HIDE_TOAST = 1 << 1;
	static final int EVENT_FLAG_LINEAR = 1;
	static final int EVENT_FLAG_RELATIVE = 1 << 1;
	static final int AMBIENT_UNDERWATER = 1;
	static final int AMBIENT_SPECTATOR = 1 << 1;
	static final int AMBIENT_BUBBLE_UP = 1 << 2;
	static final int AMBIENT_BUBBLE_DOWN = 1 << 3;
	static final int AMBIENT_BIOME_CHANGED = 1 << 4;
	static final int AMBIENT_HAS_LOOP = 1 << 5;
	static final int AMBIENT_HAS_MOOD = 1 << 6;
	static final int AMBIENT_HAS_ADDITIONS = 1 << 7;
	private static final long SUBTITLE_SIZE = 40L;

	private static final MethodHandle PLAY_EVENT = handle("play_event", ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS);
	private static final MethodHandle MUSIC_RESET = voidHandle("music_reset", ValueLayout.JAVA_INT);
	private static final MethodHandle MUSIC_TICK = handle("music_tick", ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT);
	private static final MethodHandle MUSIC_START = handle("music_start", ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_FLOAT);
	private static final MethodHandle MUSIC_STOP = handle("music_stop", ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);
	private static final MethodHandle MUSIC_TOAST_IF_NEEDED = handle("music_toast_if_needed", ValueLayout.JAVA_INT);
	private static final MethodHandle MUSIC_IS_PLAYING = handle("music_is_playing", ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG);
	private static final MethodHandle MUSIC_CURRENT_SOUND = handle("music_current_sound", ValueLayout.JAVA_LONG, ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG);
	private static final MethodHandle MUSIC_SET_FREQUENCY = voidHandle("music_set_frequency", ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT);
	private static final MethodHandle AMBIENT_TICK = handle("ambient_tick", ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS,
		ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);
	private static final MethodHandle AMBIENT_FINISH = handle("ambient_finish", ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
		ValueLayout.JAVA_INT, ValueLayout.ADDRESS);
	private static final MethodHandle PLAY_UNDERWATER_LOOP = handle("play_underwater_loop", ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG);
	private static final MethodHandle PREVIEW = handle("preview", ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT);
	private static final MethodHandle SUBTITLES_LISTEN = voidHandle("subtitles_listen", ValueLayout.JAVA_INT, ValueLayout.JAVA_INT);
	private static final MethodHandle SUBTITLES_FRAME = handle("subtitles_frame", ValueLayout.JAVA_LONG, ValueLayout.JAVA_DOUBLE,
		ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE);
	private static final MethodHandle SUBTITLES_TAKE = handle("subtitles_take", ValueLayout.JAVA_LONG, ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG);

	private NativeSoundClient() {
	}

	/**
	 * `ClientLevel.playSound`: a seeded `SimpleSoundInstance` at a position,
	 * delayed by `delay` ticks when positive. Returns the `PlayResult` ordinal.
	 */
	static int playEvent(String event, int category, float volume, float pitch, long seed, double x, double y, double z, int delay, int flags) {
		NativeSoundPolicy.Scratch scratch = SCRATCH.get();
		try {
			MemorySegment record = scratch.record;
			record.set(ValueLayout.JAVA_LONG, 0, seed);
			record.set(ValueLayout.JAVA_DOUBLE, 8, x);
			record.set(ValueLayout.JAVA_DOUBLE, 16, y);
			record.set(ValueLayout.JAVA_DOUBLE, 24, z);
			record.set(ValueLayout.JAVA_FLOAT, 32, volume);
			record.set(ValueLayout.JAVA_FLOAT, 36, pitch);
			record.set(ValueLayout.JAVA_INT, 40, delay);
			record.set(ValueLayout.JAVA_INT, 44, category);
			record.set(ValueLayout.JAVA_INT, 48, flags);
			record.set(ValueLayout.JAVA_INT, 52, 0);
			long length = putText(scratch, false, event);
			int result = (int)PLAY_EVENT.invokeExact(scratch.text, length, record);
			if (result < 0) {
				throw new IllegalStateException("Rust rejected sound event " + event);
			}
			return loadAssetsIfRequested(result) & 0xFF;
		} catch (Throwable throwable) {
			throw failure("Play sound event", throwable);
		}
	}

	static void musicReset(int frequency) {
		try {
			MUSIC_RESET.invokeExact(frequency);
		} catch (Throwable throwable) {
			throw failure("Reset music", throwable);
		}
	}

	/** Returns the {@link #MUSIC_SHOW_TOAST} / {@link #MUSIC_HIDE_TOAST} actions. */
	static int musicTick(@Nullable String event, int minDelay, int maxDelay, boolean replace, float volume) {
		NativeSoundPolicy.Scratch scratch = SCRATCH.get();
		try {
			long length = event == null ? 0L : putText(scratch, false, event);
			MemorySegment text = event == null ? MemorySegment.NULL : scratch.text;
			return loadAssetsIfRequested((int)MUSIC_TICK.invokeExact(text, length, minDelay, maxDelay, replace ? 1 : 0, volume));
		} catch (Throwable throwable) {
			throw failure("Tick music", throwable);
		}
	}

	static int musicStart(String event, float volume) {
		NativeSoundPolicy.Scratch scratch = SCRATCH.get();
		try {
			long length = putText(scratch, false, event);
			return loadAssetsIfRequested((int)MUSIC_START.invokeExact(scratch.text, length, volume));
		} catch (Throwable throwable) {
			throw failure("Start music", throwable);
		}
	}

	/** Stops the current track, or only when it plays `event`. */
	static int musicStop(@Nullable String event) {
		NativeSoundPolicy.Scratch scratch = SCRATCH.get();
		try {
			long length = event == null ? 0L : putText(scratch, false, event);
			MemorySegment text = event == null ? MemorySegment.NULL : scratch.text;
			return (int)MUSIC_STOP.invokeExact(text, length);
		} catch (Throwable throwable) {
			throw failure("Stop music", throwable);
		}
	}

	static int musicToastIfNeeded() {
		try {
			return (int)MUSIC_TOAST_IF_NEEDED.invokeExact();
		} catch (Throwable throwable) {
			throw failure("Check music toast", throwable);
		}
	}

	static boolean musicIsPlaying(String event) {
		NativeSoundPolicy.Scratch scratch = SCRATCH.get();
		try {
			long length = putText(scratch, false, event);
			return (int)MUSIC_IS_PLAYING.invokeExact(scratch.text, length) != 0;
		} catch (Throwable throwable) {
			throw failure("Query music", throwable);
		}
	}

	/** The current track's resolved sound location, if any. */
	@Nullable
	static String musicCurrentSound() {
		NativeSoundPolicy.Scratch scratch = SCRATCH.get();
		try {
			long length = (long)MUSIC_CURRENT_SOUND.invokeExact(scratch.out, scratch.out.byteSize());
			if (length > scratch.out.byteSize()) {
				scratch.out = scratch.arena.allocate(length);
				length = (long)MUSIC_CURRENT_SOUND.invokeExact(scratch.out, scratch.out.byteSize());
			}
			return length < 0L ? null : readText(scratch.out, length);
		} catch (Throwable throwable) {
			throw failure("Query music sound", throwable);
		}
	}

	static void musicSetFrequency(int frequency, @Nullable String event, int minDelay, int maxDelay) {
		NativeSoundPolicy.Scratch scratch = SCRATCH.get();
		try {
			long length = event == null ? 0L : putText(scratch, false, event);
			MemorySegment text = event == null ? MemorySegment.NULL : scratch.text;
			MUSIC_SET_FREQUENCY.invokeExact(frequency, text, length, minDelay, maxDelay);
		} catch (Throwable throwable) {
			throw failure("Set music frequency", throwable);
		}
	}

	/** One ambient tick's results: the level random's state and the mood sample to look up. */
	record AmbientOutput(long random, boolean hasSample, int sampleX, int sampleY, int sampleZ, float moodiness, float constantMoodiness) {
	}

	/**
	 * A local player's ambient handlers' tick; `owner` identifies the player.
	 * The biome settings are read only with {@link #AMBIENT_BIOME_CHANGED}.
	 */
	static AmbientOutput ambientTick(
		long owner, double x, double y, double eyeY, double z, int flags, int biome, int skyLight, int blockLight,
		@Nullable String ambientLoop, @Nullable String mood, int moodTickDelay, int moodExtent, double moodOffset,
		@Nullable String additions, double additionsChance, long levelRandom
	) {
		NativeSoundPolicy.Scratch scratch = SCRATCH.get();
		try {
			MemorySegment record = scratch.record;
			record.set(ValueLayout.JAVA_DOUBLE, 0, x);
			record.set(ValueLayout.JAVA_DOUBLE, 8, y);
			record.set(ValueLayout.JAVA_DOUBLE, 16, eyeY);
			record.set(ValueLayout.JAVA_DOUBLE, 24, z);
			record.set(ValueLayout.JAVA_DOUBLE, 32, moodOffset);
			record.set(ValueLayout.JAVA_DOUBLE, 40, additionsChance);
			record.set(ValueLayout.JAVA_INT, 48, flags);
			record.set(ValueLayout.JAVA_INT, 52, biome);
			record.set(ValueLayout.JAVA_INT, 56, skyLight);
			record.set(ValueLayout.JAVA_INT, 60, blockLight);
			record.set(ValueLayout.JAVA_INT, 64, moodTickDelay);
			record.set(ValueLayout.JAVA_INT, 68, moodExtent);
			long loopLength = ambientLoop == null ? 0L : putText(scratch, false, ambientLoop);
			long moodLength = mood == null ? 0L : putText(scratch, true, mood);
			long additionsLength = additions == null ? 0L : putThirdText(scratch, additions);
			// Typed locals: a conditional argument would reach invokeExact as Object.
			MemorySegment loopText = ambientLoop == null ? MemorySegment.NULL : scratch.text;
			MemorySegment moodText = mood == null ? MemorySegment.NULL : scratch.text2;
			MemorySegment additionsText = additions == null ? MemorySegment.NULL : scratch.text3;
			int result = (int)AMBIENT_TICK.invokeExact(
				owner, record, loopText, loopLength, moodText, moodLength, additionsText, additionsLength, levelRandom, scratch.output
			);
			if (result < 0) {
				throw new IllegalStateException("Ambient sound tick failed: " + result);
			}
			loadAssetsIfRequested(result);
			return output(scratch.output);
		} catch (Throwable throwable) {
			throw failure("Tick ambient sounds", throwable);
		}
	}

	/** The mood handler's rest, with the sample's sky and block light. */
	static AmbientOutput ambientFinish(int skyLight, int blockLight) {
		NativeSoundPolicy.Scratch scratch = SCRATCH.get();
		try {
			int result = (int)AMBIENT_FINISH.invokeExact(skyLight, blockLight, scratch.output);
			if (result < 0) {
				throw new IllegalStateException("Ambient mood tick failed: " + result);
			}
			loadAssetsIfRequested(result);
			return output(scratch.output);
		} catch (Throwable throwable) {
			throw failure("Finish ambient sounds", throwable);
		}
	}

	private static AmbientOutput output(MemorySegment out) {
		return new AmbientOutput(
			out.get(ValueLayout.JAVA_LONG, 0),
			out.get(ValueLayout.JAVA_INT, 20) != 0,
			out.get(ValueLayout.JAVA_INT, 8),
			out.get(ValueLayout.JAVA_INT, 12),
			out.get(ValueLayout.JAVA_INT, 16),
			out.get(ValueLayout.JAVA_FLOAT, 24),
			out.get(ValueLayout.JAVA_FLOAT, 28)
		);
	}

	static void playUnderwaterLoop(long owner) {
		try {
			loadAssetsIfRequested((int)PLAY_UNDERWATER_LOOP.invokeExact(owner));
		} catch (Throwable throwable) {
			throw failure("Play underwater ambience", throwable);
		}
	}

	static void preview(int category, float volume) {
		try {
			loadAssetsIfRequested((int)PREVIEW.invokeExact(category, volume));
		} catch (Throwable throwable) {
			throw failure("Preview sound", throwable);
		}
	}

	static void subtitlesListen(boolean listening, boolean debug) {
		try {
			SUBTITLES_LISTEN.invokeExact(listening ? 1 : 0, debug ? 1 : 0);
		} catch (Throwable throwable) {
			throw failure("Set subtitle listening", throwable);
		}
	}

	/** The overlay's per-frame pass for a listener position, in draw order. */
	static List<SoundSubtitle> subtitleFrame(double x, double y, double z, double displayMillis) {
		NativeSoundPolicy.Scratch scratch = SCRATCH.get();
		try {
			long count = (long)SUBTITLES_FRAME.invokeExact(x, y, z, displayMillis);
			if (count <= 0L) {
				return List.of();
			}
			MemorySegment records = count * SUBTITLE_SIZE <= scratch.longs.byteSize()
				? scratch.longs
				: scratch.arena.allocate(count * SUBTITLE_SIZE, 8);
			long textLength = (long)SUBTITLES_TAKE.invokeExact(records, count, scratch.out, scratch.out.byteSize());
			if (textLength > scratch.out.byteSize()) {
				scratch.out = scratch.arena.allocate(textLength);
				textLength = (long)SUBTITLES_TAKE.invokeExact(records, count, scratch.out, scratch.out.byteSize());
			}
			List<SoundSubtitle> subtitles = new ArrayList<>((int)count);
			long offset = 0L;
			for (int index = 0; index < count; index++) {
				long base = index * SUBTITLE_SIZE;
				int length = records.get(ValueLayout.JAVA_INT, base + 32);
				subtitles.add(new SoundSubtitle(
					readText(scratch.out.asSlice(offset), length),
					records.get(ValueLayout.JAVA_DOUBLE, base),
					records.get(ValueLayout.JAVA_DOUBLE, base + 8),
					records.get(ValueLayout.JAVA_DOUBLE, base + 16),
					records.get(ValueLayout.JAVA_LONG, base + 24)
				));
				offset += length;
			}
			return subtitles;
		} catch (Throwable throwable) {
			throw failure("Read subtitle frame", throwable);
		}
	}
}
