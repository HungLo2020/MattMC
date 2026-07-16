package net.blaze3d.audio;

import net.minecraft.util.NativeLibraryLoader;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class NativeAudio {
	static final int OK = 0;
	static final int ERR_INVALID_HANDLE = -1;
	static final int ERR_INVALID_ARGUMENT = -2;
	static final int ERR_POOL_EXHAUSTED = -6;
	private static final int STRING_BUFFER_LIMIT = 16 * 1024;
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
	private static final FunctionDescriptor SOURCE_CREATE_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS
	);
	private static final FunctionDescriptor SOURCE_STATE_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS
	);
	private static final FunctionDescriptor SOURCE_POSITION_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_FLOAT,
		ValueLayout.JAVA_FLOAT,
		ValueLayout.JAVA_FLOAT
	);
	private static final FunctionDescriptor SOURCE_FLOAT_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_FLOAT
	);
	private static final FunctionDescriptor SOURCE_INT_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_INT
	);
	private static final FunctionDescriptor BUFFER_CREATE_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS
	);
	private static final FunctionDescriptor ATTACH_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_LONG
	);
	private static final FunctionDescriptor QUEUE_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG,
		ValueLayout.ADDRESS,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_INT
	);
	private static final FunctionDescriptor LISTENER_TRANSFORM_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_FLOAT,
		ValueLayout.JAVA_FLOAT,
		ValueLayout.JAVA_FLOAT,
		ValueLayout.JAVA_FLOAT,
		ValueLayout.JAVA_FLOAT,
		ValueLayout.JAVA_FLOAT,
		ValueLayout.JAVA_FLOAT,
		ValueLayout.JAVA_FLOAT,
		ValueLayout.JAVA_FLOAT
	);
	private static final FunctionDescriptor LISTENER_GAIN_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_LONG,
		ValueLayout.JAVA_FLOAT
	);
	private static final FunctionDescriptor FORMAT_DESCRIPTOR = FunctionDescriptor.of(
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_INT,
		ValueLayout.JAVA_INT,
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
		SOURCE_STATE_DESCRIPTOR
	);
	private static final MethodHandle DEVICE_DEFAULT_CHANGED = downcall(
		"mattmc_audio_device_has_default_changed",
		SOURCE_STATE_DESCRIPTOR
	);
	private static final MethodHandle DEVICE_POOL_COUNTS = downcall("mattmc_audio_device_pool_counts", SOURCE_STATE_DESCRIPTOR);
	private static final MethodHandle SOURCE_CREATE = downcall("mattmc_audio_source_create", SOURCE_CREATE_DESCRIPTOR);
	private static final MethodHandle SOURCE_DESTROY = downcall("mattmc_audio_source_destroy", HANDLE_DESCRIPTOR);
	private static final MethodHandle SOURCE_PLAY = downcall("mattmc_audio_source_play", HANDLE_DESCRIPTOR);
	private static final MethodHandle SOURCE_PAUSE = downcall("mattmc_audio_source_pause", HANDLE_DESCRIPTOR);
	private static final MethodHandle SOURCE_STOP = downcall("mattmc_audio_source_stop", HANDLE_DESCRIPTOR);
	private static final MethodHandle SOURCE_STATE = downcall("mattmc_audio_source_state", SOURCE_STATE_DESCRIPTOR);
	private static final MethodHandle SOURCE_SET_POSITION = downcall(
		"mattmc_audio_source_set_position",
		SOURCE_POSITION_DESCRIPTOR
	);
	private static final MethodHandle SOURCE_SET_PITCH = downcall("mattmc_audio_source_set_pitch", SOURCE_FLOAT_DESCRIPTOR);
	private static final MethodHandle SOURCE_SET_VOLUME = downcall("mattmc_audio_source_set_volume", SOURCE_FLOAT_DESCRIPTOR);
	private static final MethodHandle SOURCE_SET_LOOPING = downcall("mattmc_audio_source_set_looping", SOURCE_INT_DESCRIPTOR);
	private static final MethodHandle SOURCE_SET_RELATIVE = downcall("mattmc_audio_source_set_relative", SOURCE_INT_DESCRIPTOR);
	private static final MethodHandle SOURCE_DISABLE_ATTENUATION = downcall(
		"mattmc_audio_source_disable_attenuation",
		HANDLE_DESCRIPTOR
	);
	private static final MethodHandle SOURCE_LINEAR_ATTENUATION = downcall(
		"mattmc_audio_source_linear_attenuation",
		SOURCE_FLOAT_DESCRIPTOR
	);
	private static final MethodHandle BUFFER_CREATE = downcall("mattmc_audio_buffer_create", BUFFER_CREATE_DESCRIPTOR);
	private static final MethodHandle BUFFER_DESTROY = downcall("mattmc_audio_buffer_destroy", HANDLE_DESCRIPTOR);
	private static final MethodHandle SOURCE_ATTACH_STATIC_BUFFER = downcall(
		"mattmc_audio_source_attach_static_buffer",
		ATTACH_DESCRIPTOR
	);
	private static final MethodHandle SOURCE_QUEUE_STREAM_BUFFER = downcall(
		"mattmc_audio_source_queue_stream_buffer",
		QUEUE_DESCRIPTOR
	);
	private static final MethodHandle SOURCE_REMOVE_PROCESSED_BUFFERS = downcall(
		"mattmc_audio_source_remove_processed_buffers",
		SOURCE_STATE_DESCRIPTOR
	);
	private static final MethodHandle LISTENER_SET_TRANSFORM = downcall(
		"mattmc_audio_listener_set_transform",
		LISTENER_TRANSFORM_DESCRIPTOR
	);
	private static final MethodHandle LISTENER_RESET = downcall("mattmc_audio_listener_reset", HANDLE_DESCRIPTOR);
	private static final MethodHandle LISTENER_SET_GAIN = downcall("mattmc_audio_listener_set_gain", LISTENER_GAIN_DESCRIPTOR);
	private static final MethodHandle FORMAT_TO_OPENAL = downcall("mattmc_audio_format_to_openal", FORMAT_DESCRIPTOR);

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
			return new int[]{
				output.get(ValueLayout.JAVA_INT, 0),
				output.get(ValueLayout.JAVA_INT, Integer.BYTES),
				output.get(ValueLayout.JAVA_INT, 2L * Integer.BYTES),
				output.get(ValueLayout.JAVA_INT, 3L * Integer.BYTES)
			};
		} catch (Throwable throwable) {
			throw nativeFailure("Read audio pool counts", throwable);
		}
	}

	static long sourceCreate(long deviceHandle, Library.Pool pool) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment output = arena.allocate(ValueLayout.JAVA_LONG);
			int status = (int)SOURCE_CREATE.invokeExact(deviceHandle, pool == Library.Pool.STREAMING ? 1 : 0, output);
			if (status == ERR_POOL_EXHAUSTED) {
				return 0L;
			}

			check(status, "Create audio source");
			return output.get(ValueLayout.JAVA_LONG, 0);
		} catch (Throwable throwable) {
			throw nativeFailure("Create audio source", throwable);
		}
	}

	static void sourceDestroy(long sourceHandle) {
		checkHandle(SOURCE_DESTROY, sourceHandle, "Destroy audio source");
	}

	static void sourcePlay(long sourceHandle) {
		checkHandle(SOURCE_PLAY, sourceHandle, "Play source");
	}

	static void sourcePause(long sourceHandle) {
		checkHandle(SOURCE_PAUSE, sourceHandle, "Pause source");
	}

	static void sourceStop(long sourceHandle) {
		checkHandle(SOURCE_STOP, sourceHandle, "Stop source");
	}

	static int sourceState(long sourceHandle) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment output = arena.allocate(ValueLayout.JAVA_INT);
			int status = (int)SOURCE_STATE.invokeExact(sourceHandle, output);
			check(status, "Read source state");
			return output.get(ValueLayout.JAVA_INT, 0);
		} catch (Throwable throwable) {
			throw nativeFailure("Read source state", throwable);
		}
	}

	static void sourceSetPosition(long sourceHandle, float x, float y, float z) {
		try {
			check((int)SOURCE_SET_POSITION.invokeExact(sourceHandle, x, y, z), "Set source position");
		} catch (Throwable throwable) {
			throw nativeFailure("Set source position", throwable);
		}
	}

	static void sourceSetPitch(long sourceHandle, float pitch) {
		checkFloat(SOURCE_SET_PITCH, sourceHandle, pitch, "Set source pitch");
	}

	static void sourceSetVolume(long sourceHandle, float volume) {
		checkFloat(SOURCE_SET_VOLUME, sourceHandle, volume, "Set source volume");
	}

	static void sourceSetLooping(long sourceHandle, boolean looping) {
		checkInt(SOURCE_SET_LOOPING, sourceHandle, looping ? 1 : 0, "Set source looping");
	}

	static void sourceSetRelative(long sourceHandle, boolean relative) {
		checkInt(SOURCE_SET_RELATIVE, sourceHandle, relative ? 1 : 0, "Set source relative mode");
	}

	static void sourceDisableAttenuation(long sourceHandle) {
		checkHandle(SOURCE_DISABLE_ATTENUATION, sourceHandle, "Disable source attenuation");
	}

	static void sourceLinearAttenuation(long sourceHandle, float distance) {
		checkFloat(SOURCE_LINEAR_ATTENUATION, sourceHandle, distance, "Set source linear attenuation");
	}

	static long bufferCreate(long deviceHandle, ByteBuffer data, javax.sound.sampled.AudioFormat format) {
		BufferSlice slice = bufferSlice(data);
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment output = arena.allocate(ValueLayout.JAVA_LONG);
			int status = (int)BUFFER_CREATE.invokeExact(
				deviceHandle,
				slice.segment,
				slice.length,
				format.getChannels(),
				format.getSampleSizeInBits(),
				isPcm(format) ? 1 : 0,
				(int)format.getSampleRate(),
				output
			);
			check(status, "Create audio buffer");
			return output.get(ValueLayout.JAVA_LONG, 0);
		} catch (Throwable throwable) {
			throw nativeFailure("Create audio buffer", throwable);
		}
	}

	static void bufferDestroy(long bufferHandle) {
		checkHandle(BUFFER_DESTROY, bufferHandle, "Destroy audio buffer");
	}

	static void sourceAttachStaticBuffer(long sourceHandle, long bufferHandle) {
		try {
			check((int)SOURCE_ATTACH_STATIC_BUFFER.invokeExact(sourceHandle, bufferHandle), "Attach static audio buffer");
		} catch (Throwable throwable) {
			throw nativeFailure("Attach static audio buffer", throwable);
		}
	}

	static void sourceQueueStreamBuffer(long sourceHandle, ByteBuffer data, javax.sound.sampled.AudioFormat format) {
		BufferSlice slice = bufferSlice(data);
		try {
			int status = (int)SOURCE_QUEUE_STREAM_BUFFER.invokeExact(
				sourceHandle,
				slice.segment,
				slice.length,
				format.getChannels(),
				format.getSampleSizeInBits(),
				isPcm(format) ? 1 : 0,
				(int)format.getSampleRate()
			);
			check(status, "Queue streaming audio buffer");
		} catch (Throwable throwable) {
			throw nativeFailure("Queue streaming audio buffer", throwable);
		}
	}

	static int sourceRemoveProcessedBuffers(long sourceHandle) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment output = arena.allocate(ValueLayout.JAVA_INT);
			int status = (int)SOURCE_REMOVE_PROCESSED_BUFFERS.invokeExact(sourceHandle, output);
			check(status, "Remove processed stream buffers");
			return output.get(ValueLayout.JAVA_INT, 0);
		} catch (Throwable throwable) {
			throw nativeFailure("Remove processed stream buffers", throwable);
		}
	}

	static void listenerSetTransform(long deviceHandle, ListenerTransform transform) {
		var position = transform.position();
		var forward = transform.forward();
		var up = transform.up();
		try {
			check(
				(int)LISTENER_SET_TRANSFORM.invokeExact(
					deviceHandle,
					(float)position.x,
					(float)position.y,
					(float)position.z,
					(float)forward.x,
					(float)forward.y,
					(float)forward.z,
					(float)up.x(),
					(float)up.y(),
					(float)up.z()
				),
				"Set listener transform"
			);
		} catch (Throwable throwable) {
			throw nativeFailure("Set listener transform", throwable);
		}
	}

	static void listenerReset(long deviceHandle) {
		checkHandle(LISTENER_RESET, deviceHandle, "Reset listener");
	}

	static void listenerSetGain(long deviceHandle, float gain) {
		checkFloat(LISTENER_SET_GAIN, deviceHandle, gain, "Set listener gain");
	}

	static int audioFormatToOpenAl(javax.sound.sampled.AudioFormat format) {
		try (Arena arena = Arena.ofConfined()) {
			MemorySegment output = arena.allocate(ValueLayout.JAVA_INT);
			int status = (int)FORMAT_TO_OPENAL.invokeExact(
				format.getChannels(),
				format.getSampleSizeInBits(),
				isPcm(format) ? 1 : 0,
				output
			);
			check(status, "Map audio format");
			return output.get(ValueLayout.JAVA_INT, 0);
		} catch (Throwable throwable) {
			throw nativeFailure("Map audio format", throwable);
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

	private static void checkHandle(MethodHandle handle, long handleValue, String operation) {
		try {
			check((int)handle.invokeExact(handleValue), operation);
		} catch (Throwable throwable) {
			throw nativeFailure(operation, throwable);
		}
	}

	private static void checkFloat(MethodHandle handle, long handleValue, float value, String operation) {
		try {
			check((int)handle.invokeExact(handleValue, value), operation);
		} catch (Throwable throwable) {
			throw nativeFailure(operation, throwable);
		}
	}

	private static void checkInt(MethodHandle handle, long handleValue, int value, String operation) {
		try {
			check((int)handle.invokeExact(handleValue, value), operation);
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

	private static BufferSlice bufferSlice(ByteBuffer buffer) {
		MemorySegment segment = MemorySegment.ofBuffer(buffer);
		int position = buffer.position();
		int length = buffer.remaining();
		return new BufferSlice(segment.asSlice(position, length), length);
	}

	private static boolean isPcm(javax.sound.sampled.AudioFormat format) {
		var encoding = format.getEncoding();
		return encoding.equals(javax.sound.sampled.AudioFormat.Encoding.PCM_UNSIGNED)
			|| encoding.equals(javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED);
	}

	private record BufferSlice(MemorySegment segment, long length) {
	}
}
