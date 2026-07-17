package net.blaze3d.audio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sound.sampled.AudioFormat;

public final class DevAudioValidation {
	private static final boolean ENABLED = Boolean.getBoolean("mattmc.dev.audioValidation");
	private static final String STATUS_PATH = System.getProperty("mattmc.dev.audioValidation.status", "");
	private static final AtomicBoolean RAN = new AtomicBoolean(false);
	private static final int AL_INITIAL = 4113;
	private static final int AL_PLAYING = 4114;
	private static final int AL_PAUSED = 4115;
	private static final int AL_STOPPED = 4116;
	private static final AudioFormat PCM = new AudioFormat(44100.0F, 16, 1, true, false);
	private static final String STATIC_VALIDATION_SOUND = "assets/minecraft/sounds/random/pop.ogg";

	private DevAudioValidation() {
	}

	public static void maybeRun(Library library, String preferredDevice, boolean hrtf) {
		if (!ENABLED || !RAN.compareAndSet(false, true)) {
			return;
		}

		Result result = new Result();
		result.threadName = Thread.currentThread().getName();
		result.openAlOnSoundThread = "Sound engine".equals(result.threadName);
		try {
			run(library, preferredDevice, hrtf, result);
			result.status = "complete";
		} catch (Throwable throwable) {
			result.status = "failed";
			result.error = throwable.getClass().getName() + ": " + throwable.getMessage();
		}
		writeStatus(result);
		if (!"complete".equals(result.status)) {
			throw new IllegalStateException("Dev audio validation failed: " + result.error);
		}
	}

	private static void run(Library library, String preferredDevice, boolean hrtf, Result result) throws Exception {
		require(result.openAlOnSoundThread, "OpenAL validation is not running on the sound engine thread");
		result.initialCounts = NativeAudio.debugLiveCounts();
		result.listenerUpdated = updateListener(library);
		validateStaticSound(library, result);
		validateStreamingSound(library, result);
		result.afterReleaseCounts = NativeAudio.debugLiveCounts();
		requireZeroChildren(result.afterReleaseCounts, "after sound release");

		library.cleanup();
		result.shutdownCounts = NativeAudio.debugLiveCounts();
		requireCounts(result.shutdownCounts, 0, 0, 0, 0, "after validation shutdown");

		library.init(preferredDevice, hrtf);
		library.getListener().reset();
		result.reloadCounts = NativeAudio.debugLiveCounts();
		requireCounts(result.reloadCounts, 1, 0, 0, 0, "after validation reload");
		result.reloadSucceeded = true;
	}

	private static boolean updateListener(Library library) {
		NativeAudio.listenerUpdate(
			library.currentDeviceHandleForDevValidation(),
			ListenerTransform.INITIAL,
			0.75F
		);
		NativeAudio.listenerUpdate(
			library.currentDeviceHandleForDevValidation(),
			ListenerTransform.INITIAL,
			1.0F
		);
		return true;
	}

	private static void validateStaticSound(Library library, Result result) throws Exception {
		long device = library.currentDeviceHandleForDevValidation();
		NativeAudioAsset asset = loadStaticValidationAsset();
		long sound = 0L;
		long repeatedSound = 0L;
		try {
			NativeAudio.SoundConfig config = new NativeAudio.SoundConfig();
			config.pitch = 0.85F;
			config.gain = 0.35F;
			config.x = 1.0F;
			config.y = 2.0F;
			config.z = 3.0F;
			config.attenuationDistance = 16.0F;
			config.flags = NativeAudio.SOUND_FLAG_LOOPING | NativeAudio.SOUND_FLAG_LINEAR_ATTENUATION;
			sound = NativeAudio.soundCreateStaticFromAsset(device, config, asset.handleForPlayback());
			require(sound != 0L, "static sound was not created");
			repeatedSound = NativeAudio.soundCreateStaticFromAsset(device, config, asset.handleForPlayback());
			require(repeatedSound != 0L, "repeated static sound was not created from the same asset");
			NativeAudio.soundPlay(sound);
			Thread.sleep(75L);
			result.staticPlaybackState = NativeAudio.soundState(sound);
			require(isValidPlaybackState(result.staticPlaybackState), "static sound returned invalid playback state");

			config.pitch = 1.15F;
			config.gain = 0.20F;
			config.x = -2.0F;
			config.y = 0.5F;
			config.z = 4.0F;
			config.attenuationDistance = 8.0F;
			config.flags = NativeAudio.SOUND_FLAG_RELATIVE | NativeAudio.SOUND_FLAG_DISABLE_ATTENUATION;
			NativeAudio.soundUpdate(
				sound,
				NativeAudio.SOUND_UPDATE_POSITION
					| NativeAudio.SOUND_UPDATE_PITCH
					| NativeAudio.SOUND_UPDATE_GAIN
					| NativeAudio.SOUND_UPDATE_LOOPING
					| NativeAudio.SOUND_UPDATE_ATTENUATION
					| NativeAudio.SOUND_UPDATE_RELATIVE,
				config
			);
			result.staticUpdatesSucceeded = true;

			NativeAudio.soundPause(sound);
			Thread.sleep(25L);
			result.pauseState = NativeAudio.soundState(sound);
			require(result.pauseState == AL_PAUSED || result.pauseState == AL_STOPPED, "pause returned unexpected state");
			NativeAudio.soundPlay(sound);
			Thread.sleep(25L);
			result.resumeState = NativeAudio.soundState(sound);
			require(isValidPlaybackState(result.resumeState), "resume returned invalid playback state");
			NativeAudio.soundStop(sound);
			result.stopState = NativeAudio.soundState(sound);
			require(result.stopState == AL_STOPPED || result.stopState == AL_INITIAL, "stop returned unexpected state");
		} finally {
			if (repeatedSound != 0L) {
				NativeAudio.soundStopAndDestroy(repeatedSound);
			}
			if (sound != 0L) {
				NativeAudio.soundStopAndDestroy(sound);
			}
			asset.close();
		}
	}

	private static void validateStreamingSound(Library library, Result result) throws Exception {
		long device = library.currentDeviceHandleForDevValidation();
		long sound = 0L;
		try {
			NativeAudio.SoundConfig config = new NativeAudio.SoundConfig();
			config.pitch = 1.0F;
			config.gain = 0.25F;
			config.flags = NativeAudio.SOUND_FLAG_DISABLE_ATTENUATION;
			sound = NativeAudio.soundCreateStreaming(device, config);
			require(sound != 0L, "streaming sound was not created");
			List<ByteBuffer> chunks = new ArrayList<>();
			chunks.add(pcmBuffer(2205, 0.03F));
			chunks.add(pcmBuffer(2205, 0.04F));
			chunks.add(pcmBuffer(2205, 0.05F));
			result.streamingChunksSubmitted = NativeAudio.soundSubmitStreamChunks(sound, chunks, PCM);
			require(result.streamingChunksSubmitted == chunks.size(), "streaming chunks were not fully accepted");
			NativeAudio.soundPlay(sound);
			int processed = 0;
			for (int i = 0; i < 20 && processed == 0; i++) {
				Thread.sleep(50L);
				processed += NativeAudio.soundRemoveProcessedStreamBuffers(sound);
			}
			result.streamingChunksProcessed = processed;
			require(processed > 0, "streaming sound did not consume any queued chunks");

			config.pitch = 0.95F;
			config.gain = 0.15F;
			config.x = 3.0F;
			config.y = -1.0F;
			config.z = 2.0F;
			config.flags = NativeAudio.SOUND_FLAG_RELATIVE | NativeAudio.SOUND_FLAG_DISABLE_ATTENUATION;
			NativeAudio.soundUpdate(
				sound,
				NativeAudio.SOUND_UPDATE_POSITION
					| NativeAudio.SOUND_UPDATE_PITCH
					| NativeAudio.SOUND_UPDATE_GAIN
					| NativeAudio.SOUND_UPDATE_ATTENUATION
					| NativeAudio.SOUND_UPDATE_RELATIVE,
				config
			);
			result.streamingUpdatesSucceeded = true;
		} finally {
			if (sound != 0L) {
				NativeAudio.soundStopAndDestroy(sound);
			}
		}
	}

	private static ByteBuffer pcmBuffer(int frames, float phaseStep) {
		ByteBuffer buffer = ByteBuffer.allocateDirect(frames * Short.BYTES).order(ByteOrder.LITTLE_ENDIAN);
		for (int i = 0; i < frames; i++) {
			short sample = (short)(Math.sin(i * phaseStep) * 8000.0);
			buffer.putShort(sample);
		}
		buffer.flip();
		return buffer;
	}

	private static NativeAudioAsset loadStaticValidationAsset() throws IOException {
		try (var inputStream = DevAudioValidation.class.getClassLoader().getResourceAsStream(STATIC_VALIDATION_SOUND)) {
			if (inputStream == null) {
				throw new IOException("Missing static validation sound resource " + STATIC_VALIDATION_SOUND);
			}

			return NativeAudioAsset.create(inputStream.readAllBytes(), STATIC_VALIDATION_SOUND, 1L);
		}
	}

	private static boolean isValidPlaybackState(int state) {
		return state == AL_INITIAL || state == AL_PLAYING || state == AL_PAUSED || state == AL_STOPPED;
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalStateException(message);
		}
	}

	private static void requireZeroChildren(int[] counts, String label) {
		require(
			counts[1] == 0 && counts[2] == 0 && counts[3] == 0 && counts[4] == 0 && counts[5] == 0,
			"native audio counts were not clear " + label
		);
	}

	private static void requireCounts(int[] counts, int devices, int sounds, int buffers, int queued, String label) {
		require(
			counts[0] == devices
				&& counts[1] == sounds
				&& counts[2] == buffers
				&& counts[3] == queued
				&& counts[4] == 0
				&& counts[5] == 0,
			"unexpected native audio counts " + label
		);
	}

	private static void writeStatus(Result result) {
		if (STATUS_PATH.isBlank()) {
			return;
		}
		try {
			Path path = Path.of(STATUS_PATH);
			Path parent = path.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			Files.writeString(path, result.toJson());
		} catch (IOException ioException) {
			throw new IllegalStateException("Failed to write dev audio validation status", ioException);
		}
	}

	private static String countsJson(int[] counts) {
		if (counts == null) {
			return "null";
		}
		return "{\"devices\":"
			+ counts[0]
			+ ",\"sounds\":"
			+ counts[1]
			+ ",\"buffers\":"
			+ counts[2]
			+ ",\"queuedStreamBuffers\":"
			+ counts[3]
			+ ",\"assets\":"
			+ counts[4]
			+ ",\"staticCacheEntries\":"
			+ counts[5]
			+ "}";
	}

	private static String jsonString(String value) {
		if (value == null) {
			return "null";
		}
		return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
	}

	private static final class Result {
		String status = "pending";
		String error = "";
		String threadName = "";
		boolean openAlOnSoundThread;
		boolean listenerUpdated;
		boolean staticUpdatesSucceeded;
		boolean streamingUpdatesSucceeded;
		boolean reloadSucceeded;
		int staticPlaybackState;
		int pauseState;
		int resumeState;
		int stopState;
		int streamingChunksSubmitted;
		int streamingChunksProcessed;
		int[] initialCounts;
		int[] afterReleaseCounts;
		int[] shutdownCounts;
		int[] reloadCounts;

		String toJson() {
			return "{\n"
				+ "  \"status\": " + jsonString(this.status) + ",\n"
				+ "  \"error\": " + jsonString(this.error) + ",\n"
				+ "  \"threadName\": " + jsonString(this.threadName) + ",\n"
				+ "  \"openAlOnSoundThread\": " + this.openAlOnSoundThread + ",\n"
				+ "  \"listenerUpdated\": " + this.listenerUpdated + ",\n"
				+ "  \"staticPlaybackState\": " + this.staticPlaybackState + ",\n"
				+ "  \"staticUpdatesSucceeded\": " + this.staticUpdatesSucceeded + ",\n"
				+ "  \"pauseState\": " + this.pauseState + ",\n"
				+ "  \"resumeState\": " + this.resumeState + ",\n"
				+ "  \"stopState\": " + this.stopState + ",\n"
				+ "  \"streamingChunksSubmitted\": " + this.streamingChunksSubmitted + ",\n"
				+ "  \"streamingChunksProcessed\": " + this.streamingChunksProcessed + ",\n"
				+ "  \"streamingUpdatesSucceeded\": " + this.streamingUpdatesSucceeded + ",\n"
				+ "  \"reloadSucceeded\": " + this.reloadSucceeded + ",\n"
				+ "  \"initialCounts\": " + countsJson(this.initialCounts) + ",\n"
				+ "  \"afterReleaseCounts\": " + countsJson(this.afterReleaseCounts) + ",\n"
				+ "  \"shutdownCounts\": " + countsJson(this.shutdownCounts) + ",\n"
				+ "  \"reloadCounts\": " + countsJson(this.reloadCounts) + "\n"
				+ "}\n";
		}
	}
}
