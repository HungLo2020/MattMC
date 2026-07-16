package net.blaze3d.audio;

import java.nio.ByteBuffer;
import java.util.OptionalLong;
import javax.sound.sampled.AudioFormat;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class SoundBuffer {
	@Nullable
	private ByteBuffer data;
	private final AudioFormat format;
	private long nativeDeviceHandle;
	private long nativeBufferHandle;

	public SoundBuffer(ByteBuffer byteBuffer, AudioFormat audioFormat) {
		this.data = byteBuffer;
		this.format = audioFormat;
	}

	OptionalLong getNativeBuffer(long deviceHandle) {
		if (this.nativeBufferHandle == 0L || this.nativeDeviceHandle != deviceHandle) {
			if (this.nativeBufferHandle != 0L) {
				NativeAudio.bufferDestroy(this.nativeBufferHandle);
				this.nativeBufferHandle = 0L;
				this.nativeDeviceHandle = 0L;
			}

			if (this.data == null) {
				return OptionalLong.empty();
			}

			this.nativeBufferHandle = NativeAudio.bufferCreate(deviceHandle, this.data, this.format);
			this.nativeDeviceHandle = deviceHandle;
			this.data = null;
		}

		return OptionalLong.of(this.nativeBufferHandle);
	}

	public void discardAlBuffer() {
		if (this.nativeBufferHandle != 0L) {
			NativeAudio.bufferDestroy(this.nativeBufferHandle);
		}

		this.nativeBufferHandle = 0L;
		this.nativeDeviceHandle = 0L;
	}
}
