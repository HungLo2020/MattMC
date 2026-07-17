package net.blaze3d.audio;

import net.minecraft.util.NativeLibraryLoader;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class NativeAudio {
	static final int OK = 0;
	static final int ERR_INVALID_HANDLE = -1;
	static final int ERR_INVALID_ARGUMENT = -2;
	static final int ERR_POOL_EXHAUSTED = -6;
	static final int SOUND_FLAG_LOOPING = 1 << 0;
	static final int SOUND_FLAG_RELATIVE = 1 << 1;
	static final int SOUND_FLAG_DISABLE_ATTENUATION = 1 << 2;
	static final int SOUND_FLAG_LINEAR_ATTENUATION = 1 << 3;
	static final int SOUND_UPDATE_POSITION = 1 << 0;
	static final int SOUND_UPDATE_PITCH = 1 << 1;
	static final int SOUND_UPDATE_GAIN = 1 << 2;
	static final int SOUND_UPDATE_LOOPING = 1 << 3;
	static final int SOUND_UPDATE_RELATIVE = 1 << 4;
	static final int SOUND_UPDATE_ATTENUATION = 1 << 5;
	private static final int STRING_BUFFER_LIMIT = 16 * 1024;
	private static final long SOUND_CONFIG_SIZE = 28L;
	private static final long SOUND_CONFIG_X = 0L;
	private static final long SOUND_CONFIG_Y = 4L;
	private static final long SOUND_CONFIG_Z = 8L;
	private static final long SOUND_CONFIG_PITCH = 12L;
	private static final long SOUND_CONFIG_GAIN = 16L;
	private static final long SOUND_CONFIG_ATTENUATION_DISTANCE = 20L;
	private static final long SOUND_CONFIG_FLAGS = 24L;
	private static final long LISTENER_STATE_SIZE = 40L;
	private static final FunctionDescriptor STRING_GLOBAL_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS
	);
	private static final FunctionDescriptor STRING_DEVICE_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS
	);
	private static final FunctionDescriptor DEVICE_CREATE_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS
	);
	private static final FunctionDescriptor HANDLE_DESCRIPTOR = FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG);
	private static final FunctionDescriptor HANDLE_INT_OUTPUT_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS
	);
	private static final FunctionDescriptor INT_ARRAY_OUTPUT_DESCRIPTOR = FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS);
	private static final FunctionDescriptor ASSET_CREATE_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS
	);
	private static final FunctionDescriptor SOUND_CREATE_STATIC_FROM_ASSET_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS
	);
	private static final FunctionDescriptor SOUND_CREATE_STREAMING_FROM_ASSET_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS
	);
	private static final FunctionDescriptor SOUND_UPDATE_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS
	);
	private static final FunctionDescriptor LISTENER_UPDATE_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS
	);
	private static final MethodHandle DEFAULT_DEVICE_NAME = downcall(
		"mattmc_audio_default_device_name",
		STRING_GLOBAL_DESCRIPTOR
	);
	private static final MethodHandle AVAILABLE_DEVICES = downcall("mattmc_audio_available_devices", STRING_GLOBAL_DESCRIPTOR);
	private static final MethodHandle CURRENT_DEVICE_NAME = downcall(
		"mattmc_audio_current_device_name",
		STRING_DEVICE_DESCRIPTOR
	);
	private static final MethodHandle DEVICE_CREATE = downcall("mattmc_audio_device_create", DEVICE_CREATE_DESCRIPTOR);
	private static final MethodHandle DEVICE_DESTROY = downcall("mattmc_audio_device_destroy", HANDLE_DESCRIPTOR);
	private static final MethodHandle DEVICE_DISCONNECTED = downcall(
		"mattmc_audio_device_is_disconnected",
		HANDLE_INT_OUTPUT_DESCRIPTOR
	);
	private static final MethodHandle DEVICE_DEFAULT_CHANGED = downcall(
		"mattmc_audio_device_has_default_changed",
		HANDLE_INT_OUTPUT_DESCRIPTOR
	);
	private static final MethodHandle DEVICE_POOL_COUNTS = downcall("mattmc_audio_device_pool_counts", HANDLE_INT_OUTPUT_DESCRIPTOR);
	private static final MethodHandle DEBUG_LIVE_COUNTS = downcall("mattmc_audio_debug_live_counts", INT_ARRAY_OUTPUT_DESCRIPTOR);
	private static final MethodHandle ASSET_CREATE = downcall("mattmc_audio_asset_create", ASSET_CREATE_DESCRIPTOR);
	private static final MethodHandle ASSET_DESTROY = downcall("mattmc_audio_asset_destroy", HANDLE_DESCRIPTOR);
	private static final MethodHandle ASSET_DESTROY_GENERATION = downcall(
		"mattmc_audio_asset_destroy_generation",
		HANDLE_DESCRIPTOR
	);
	private static final MethodHandle SOUND_CREATE_STATIC_FROM_ASSET = downcall(
		"mattmc_audio_sound_create_static_from_asset",
		SOUND_CREATE_STATIC_FROM_ASSET_DESCRIPTOR
	);
	private static final MethodHandle SOUND_CREATE_STREAMING_FROM_ASSET = downcall(
		"mattmc_audio_sound_create_streaming_from_asset",
		SOUND_CREATE_STREAMING_FROM_ASSET_DESCRIPTOR
	);
	private static final MethodHandle SOUND_UPDATE = downcall("mattmc_audio_sound_update", SOUND_UPDATE_DESCRIPTOR);
	private static final MethodHandle SOUND_PLAY = downcall("mattmc_audio_sound_play", HANDLE_DESCRIPTOR);
	private static final MethodHandle SOUND_PAUSE = downcall("mattmc_audio_sound_pause", HANDLE_DESCRIPTOR);
	private static final MethodHandle SOUND_STOP = downcall("mattmc_audio_sound_stop", HANDLE_DESCRIPTOR);
	private static final MethodHandle SOUND_STATE = downcall("mattmc_audio_sound_state", HANDLE_INT_OUTPUT_DESCRIPTOR);
	private static final MethodHandle SOUND_STOP_AND_DESTROY = downcall(
		"mattmc_audio_sound_stop_and_destroy",
		HANDLE_DESCRIPTOR
	);
	private static final MethodHandle DEVICE_TICK = downcall("mattmc_audio_device_tick", HANDLE_DESCRIPTOR);
	private static final MethodHandle LISTENER_UPDATE = downcall("mattmc_audio_listener_update", LISTENER_UPDATE_DESCRIPTOR);

	private NativeAudio() {
	}

	static String defaultDeviceName() {
		return readString(DEFAULT_DEVICE_NAME, "Default device name");
	}

	static List<String> availableDevices() {
		String devices = readString(AVAILABLE_DEVICES, "Available devices");
		if (devices.isEmpty()) {
			return Collections.emptyList();
		}

		return Arrays.asList(devices.split("\n"));
	}

	static String currentDeviceName(long deviceHandle) {
		return readDeviceString(CURRENT_DEVICE_NAME, deviceHandle, "Current device name");
	}

	static long deviceCreate(String preferredDevice, boolean hrtf) {
		byte[] preferredBytes = preferredDevice == null ? null : preferredDevice.getBytes(StandardCharsets.UTF_8);
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment preferred = preferredBytes == null
				? MemorySegment.NULL
				: arena.allocateFrom(ValueLayout.JAVA_BYTE, preferredBytes);
			MemorySegment output = arena.allocate(ValueLayout.JAVA_LONG);
			int status = (int)DEVICE_CREATE.invokeExact(
				preferred,
				(long)(preferredBytes == null ? 0 : preferredBytes.length),
				hrtf ? 1 : 0,
				output
			);
			check(status, "Create audio device");
			return output.get(ValueLayout.JAVA_LONG, 0);
		} catch (Throwable throwable) {
			throw nativeFailure("Create audio device", throwable);
		}
	}

	static void deviceDestroy(long deviceHandle) {
		checkHandle(DEVICE_DESTROY, deviceHandle, "Destroy audio device");
	}

	static boolean deviceIsDisconnected(long deviceHandle) {
		return readBoolean(DEVICE_DISCONNECTED, deviceHandle, "Check audio device disconnect");
	}

	static boolean deviceHasDefaultChanged(long deviceHandle) {
		return readBoolean(DEVICE_DEFAULT_CHANGED, deviceHandle, "Check default audio device");
	}

	static int[] devicePoolCounts(long deviceHandle) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment output = arena.allocate(ValueLayout.JAVA_INT, 4);
			int status = (int)DEVICE_POOL_COUNTS.invokeExact(deviceHandle, output);
			check(status, "Read audio pool counts");
			return readIntArray4(output);
		} catch (Throwable throwable) {
			throw nativeFailure("Read audio pool counts", throwable);
		}
	}

	static int[] debugLiveCounts() {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment output = arena.allocate(ValueLayout.JAVA_INT, 8);
			int status = (int)DEBUG_LIVE_COUNTS.invokeExact(output);
			check(status, "Read native audio live counts");
			return readIntArray8(output);
		} catch (Throwable throwable) {
			throw nativeFailure("Read native audio live counts", throwable);
		}
	}

	static long assetCreate(byte[] encoded, String debugName, long reloadGeneration) {
		byte[] debugBytes = debugName == null || debugName.isBlank() ? null : debugName.getBytes(StandardCharsets.UTF_8);
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment encodedSegment = arena.allocateFrom(ValueLayout.JAVA_BYTE, encoded);
			MemorySegment debugSegment = debugBytes == null
				? MemorySegment.NULL
				: arena.allocateFrom(ValueLayout.JAVA_BYTE, debugBytes);
			MemorySegment output = arena.allocate(ValueLayout.JAVA_LONG);
			int status = (int)ASSET_CREATE.invokeExact(
				encodedSegment,
				(long)encoded.length,
				debugSegment,
				(long)(debugBytes == null ? 0 : debugBytes.length),
				reloadGeneration,
				output
			);
			check(status, "Create native audio asset");
			return output.get(ValueLayout.JAVA_LONG, 0);
		} catch (Throwable throwable) {
			throw nativeFailure("Create native audio asset", throwable);
		}
	}

	static void assetDestroy(long assetHandle) {
		checkHandleAllowInvalid(ASSET_DESTROY, assetHandle, "Destroy native audio asset");
	}

	static void assetDestroyGeneration(long reloadGeneration) {
		checkHandleAllowInvalid(ASSET_DESTROY_GENERATION, reloadGeneration, "Destroy native audio asset generation");
	}

	static long soundCreateStaticFromAsset(long deviceHandle, SoundConfig config, long assetHandle) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment configSegment = writeSoundConfig(arena, config);
			MemorySegment output = arena.allocate(ValueLayout.JAVA_LONG);
			int status = (int)SOUND_CREATE_STATIC_FROM_ASSET.invokeExact(deviceHandle, configSegment, assetHandle, output);
			if (status == ERR_POOL_EXHAUSTED) {
				return 0L;
			}

			check(status, "Create static sound");
			return output.get(ValueLayout.JAVA_LONG, 0);
		} catch (Throwable throwable) {
			throw nativeFailure("Create static sound", throwable);
		}
	}

	static long soundCreateStreamingFromAsset(long deviceHandle, SoundConfig config, long assetHandle, boolean looping) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment configSegment = writeSoundConfig(arena, config);
			MemorySegment output = arena.allocate(ValueLayout.JAVA_LONG);
			int status = (int)SOUND_CREATE_STREAMING_FROM_ASSET.invokeExact(
				deviceHandle,
				configSegment,
				assetHandle,
				looping ? 1 : 0,
				output
			);
			if (status == ERR_POOL_EXHAUSTED) {
				return 0L;
			}

			check(status, "Create streaming sound");
			return output.get(ValueLayout.JAVA_LONG, 0);
		} catch (Throwable throwable) {
			throw nativeFailure("Create streaming sound", throwable);
		}
	}

	static void soundUpdate(long soundHandle, int updateMask, SoundConfig config) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment configSegment = writeSoundConfig(arena, config);
			check((int)SOUND_UPDATE.invokeExact(soundHandle, updateMask, configSegment), "Update sound");
		} catch (Throwable throwable) {
			throw nativeFailure("Update sound", throwable);
		}
	}

	static void soundPlay(long soundHandle) {
		checkHandle(SOUND_PLAY, soundHandle, "Play sound");
	}

	static void soundPause(long soundHandle) {
		checkHandle(SOUND_PAUSE, soundHandle, "Pause sound");
	}

	static void soundStop(long soundHandle) {
		checkHandle(SOUND_STOP, soundHandle, "Stop sound");
	}

	static int soundState(long soundHandle) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment output = arena.allocate(ValueLayout.JAVA_INT);
			int status = (int)SOUND_STATE.invokeExact(soundHandle, output);
			check(status, "Read sound state");
			return output.get(ValueLayout.JAVA_INT, 0);
		} catch (Throwable throwable) {
			throw nativeFailure("Read sound state", throwable);
		}
	}

	static void soundStopAndDestroy(long soundHandle) {
		checkHandle(SOUND_STOP_AND_DESTROY, soundHandle, "Stop and destroy sound");
	}

	static void deviceTick(long deviceHandle) {
		checkHandle(DEVICE_TICK, deviceHandle, "Tick audio device");
	}

	static void listenerUpdate(long deviceHandle, ListenerTransform transform, float gain) {
		var position = transform.position();
		var forward = transform.forward();
		var up = transform.up();
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment state = arena.allocate(LISTENER_STATE_SIZE, 4L);
			state.set(ValueLayout.JAVA_FLOAT, 0L, (float)position.x);
			state.set(ValueLayout.JAVA_FLOAT, 4L, (float)position.y);
			state.set(ValueLayout.JAVA_FLOAT, 8L, (float)position.z);
			state.set(ValueLayout.JAVA_FLOAT, 12L, (float)forward.x);
			state.set(ValueLayout.JAVA_FLOAT, 16L, (float)forward.y);
			state.set(ValueLayout.JAVA_FLOAT, 20L, (float)forward.z);
			state.set(ValueLayout.JAVA_FLOAT, 24L, (float)up.x());
			state.set(ValueLayout.JAVA_FLOAT, 28L, (float)up.y());
			state.set(ValueLayout.JAVA_FLOAT, 32L, (float)up.z());
			state.set(ValueLayout.JAVA_FLOAT, 36L, gain);
			check((int)LISTENER_UPDATE.invokeExact(deviceHandle, state), "Update listener");
		} catch (Throwable throwable) {
			throw nativeFailure("Update listener", throwable);
		}
	}

	private static MethodHandle downcall(String symbol, FunctionDescriptor descriptor) {
		return NativeLibraryLoader.downcallHandle("mattmc_rust", symbol, descriptor);
	}

	private static String readString(MethodHandle handle, String operation) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment outputLength = arena.allocate(ValueLayout.JAVA_LONG);
			int status = (int)handle.invokeExact(MemorySegment.NULL, 0L, outputLength);
			check(status, operation);
			long length = outputLength.get(ValueLayout.JAVA_LONG, 0);
			if (length == 0L) {
				return "";
			}

			if (length > STRING_BUFFER_LIMIT) {
				throw new IllegalStateException(operation + " returned oversized string: " + length);
			}

			MemorySegment output = arena.allocate(ValueLayout.JAVA_BYTE, length);
			status = (int)handle.invokeExact(output, length, outputLength);
			check(status, operation);
			byte[] bytes = output.toArray(ValueLayout.JAVA_BYTE);
			return new String(bytes, StandardCharsets.UTF_8);
		} catch (Throwable throwable) {
			throw nativeFailure(operation, throwable);
		}
	}

	private static String readDeviceString(MethodHandle handle, long deviceHandle, String operation) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment outputLength = arena.allocate(ValueLayout.JAVA_LONG);
			int status = (int)handle.invokeExact(deviceHandle, MemorySegment.NULL, 0L, outputLength);
			check(status, operation);
			long length = outputLength.get(ValueLayout.JAVA_LONG, 0);
			if (length == 0L) {
				return "";
			}

			if (length > STRING_BUFFER_LIMIT) {
				throw new IllegalStateException(operation + " returned oversized string: " + length);
			}

			MemorySegment output = arena.allocate(ValueLayout.JAVA_BYTE, length);
			status = (int)handle.invokeExact(deviceHandle, output, length, outputLength);
			check(status, operation);
			byte[] bytes = output.toArray(ValueLayout.JAVA_BYTE);
			return new String(bytes, StandardCharsets.UTF_8);
		} catch (Throwable throwable) {
			throw nativeFailure(operation, throwable);
		}
	}

	private static boolean readBoolean(MethodHandle handle, long handleValue, String operation) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment output = arena.allocate(ValueLayout.JAVA_INT);
			int status = (int)handle.invokeExact(handleValue, output);
			check(status, operation);
			return output.get(ValueLayout.JAVA_INT, 0) != 0;
		} catch (Throwable throwable) {
			throw nativeFailure(operation, throwable);
		}
	}

	private static int[] readIntArray4(MemorySegment output) {
		return new int[]{
			output.get(ValueLayout.JAVA_INT, 0),
			output.get(ValueLayout.JAVA_INT, Integer.BYTES),
			output.get(ValueLayout.JAVA_INT, 2L * Integer.BYTES),
			output.get(ValueLayout.JAVA_INT, 3L * Integer.BYTES)
		};
	}

	private static int[] readIntArray8(MemorySegment output) {
		return new int[]{
			output.get(ValueLayout.JAVA_INT, 0),
			output.get(ValueLayout.JAVA_INT, Integer.BYTES),
			output.get(ValueLayout.JAVA_INT, 2L * Integer.BYTES),
			output.get(ValueLayout.JAVA_INT, 3L * Integer.BYTES),
			output.get(ValueLayout.JAVA_INT, 4L * Integer.BYTES),
			output.get(ValueLayout.JAVA_INT, 5L * Integer.BYTES),
			output.get(ValueLayout.JAVA_INT, 6L * Integer.BYTES),
			output.get(ValueLayout.JAVA_INT, 7L * Integer.BYTES)
		};
	}

	private static void checkHandle(MethodHandle handle, long handleValue, String operation) {
		try {
			check((int)handle.invokeExact(handleValue), operation);
		} catch (Throwable throwable) {
			throw nativeFailure(operation, throwable);
		}
	}

	private static void checkHandleAllowInvalid(MethodHandle handle, long handleValue, String operation) {
		try {
			int status = (int)handle.invokeExact(handleValue);
			if (status != ERR_INVALID_HANDLE) {
				check(status, operation);
			}
		} catch (Throwable throwable) {
			throw nativeFailure(operation, throwable);
		}
	}

	private static void check(int status, String operation) {
		if (status != OK) {
			throw new IllegalStateException(operation + " failed with native audio status " + status);
		}
	}

	private static IllegalStateException nativeFailure(String operation, Throwable throwable) {
		if (throwable instanceof IllegalStateException illegalStateException) {
			return illegalStateException;
		}

		return new IllegalStateException(operation + " failed", throwable);
	}

	private static MemorySegment writeSoundConfig(Arena arena, SoundConfig config) {
		MemorySegment segment = arena.allocate(SOUND_CONFIG_SIZE, 4L);
		segment.set(ValueLayout.JAVA_FLOAT, SOUND_CONFIG_X, config.x);
		segment.set(ValueLayout.JAVA_FLOAT, SOUND_CONFIG_Y, config.y);
		segment.set(ValueLayout.JAVA_FLOAT, SOUND_CONFIG_Z, config.z);
		segment.set(ValueLayout.JAVA_FLOAT, SOUND_CONFIG_PITCH, config.pitch);
		segment.set(ValueLayout.JAVA_FLOAT, SOUND_CONFIG_GAIN, config.gain);
		segment.set(ValueLayout.JAVA_FLOAT, SOUND_CONFIG_ATTENUATION_DISTANCE, config.attenuationDistance);
		segment.set(ValueLayout.JAVA_INT, SOUND_CONFIG_FLAGS, config.flags);
		return segment;
	}

	static final class SoundConfig {
		float x;
		float y;
		float z;
		float pitch = 1.0F;
		float gain = 1.0F;
		float attenuationDistance;
		int flags;
	}

}
