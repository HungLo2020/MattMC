package net.blaze3d.audio;

import net.logging.LogUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sound.sampled.AudioFormat;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class Channel {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int QUEUED_BUFFER_COUNT = 4;
	public static final int BUFFER_DURATION_SECONDS = 1;
	private static final int AL_PLAYING = 4114;
	private static final int AL_PAUSED = 4115;
	private static final int AL_STOPPED = 4116;
	private final long deviceHandle;
	private final long sourceHandle;
	private final AtomicBoolean initialized = new AtomicBoolean(true);
	private int streamingBufferSize = 16384;
	@Nullable
	private AudioStream stream;

	Channel(long deviceHandle, long sourceHandle) {
		this.deviceHandle = deviceHandle;
		this.sourceHandle = sourceHandle;
	}

	public void destroy() {
		if (this.initialized.compareAndSet(true, false)) {
			NativeAudio.sourceStop(this.sourceHandle);
			if (this.stream != null) {
				try {
					this.stream.close();
				} catch (IOException var2) {
					LOGGER.error("Failed to close audio stream", (Throwable)var2);
				}

				this.removeProcessedBuffers();
				this.stream = null;
			}

			NativeAudio.sourceDestroy(this.sourceHandle);
		}
	}

	public void play() {
		NativeAudio.sourcePlay(this.sourceHandle);
	}

	private int getState() {
		return !this.initialized.get() ? AL_STOPPED : NativeAudio.sourceState(this.sourceHandle);
	}

	public void pause() {
		if (this.getState() == AL_PLAYING) {
			NativeAudio.sourcePause(this.sourceHandle);
		}
	}

	public void unpause() {
		if (this.getState() == AL_PAUSED) {
			NativeAudio.sourcePlay(this.sourceHandle);
		}
	}

	public void stop() {
		if (this.initialized.get()) {
			NativeAudio.sourceStop(this.sourceHandle);
		}
	}

	public boolean playing() {
		return this.getState() == AL_PLAYING;
	}

	public boolean stopped() {
		return this.getState() == AL_STOPPED;
	}

	public void setSelfPosition(Vec3 vec3) {
		NativeAudio.sourceSetPosition(this.sourceHandle, (float)vec3.x, (float)vec3.y, (float)vec3.z);
	}

	public void setPitch(float f) {
		NativeAudio.sourceSetPitch(this.sourceHandle, f);
	}

	public void setLooping(boolean bl) {
		NativeAudio.sourceSetLooping(this.sourceHandle, bl);
	}

	public void setVolume(float f) {
		NativeAudio.sourceSetVolume(this.sourceHandle, f);
	}

	public void disableAttenuation() {
		NativeAudio.sourceDisableAttenuation(this.sourceHandle);
	}

	public void linearAttenuation(float f) {
		NativeAudio.sourceLinearAttenuation(this.sourceHandle, f);
	}

	public void setRelative(boolean bl) {
		NativeAudio.sourceSetRelative(this.sourceHandle, bl);
	}

	public void attachStaticBuffer(SoundBuffer soundBuffer) {
		soundBuffer.getNativeBuffer(this.deviceHandle).ifPresent(buffer -> NativeAudio.sourceAttachStaticBuffer(this.sourceHandle, buffer));
	}

	public void attachBufferStream(AudioStream audioStream) {
		this.stream = audioStream;
		AudioFormat audioFormat = audioStream.getFormat();
		this.streamingBufferSize = calculateBufferSize(audioFormat, 1);
		this.pumpBuffers(QUEUED_BUFFER_COUNT);
	}

	private static int calculateBufferSize(AudioFormat audioFormat, int i) {
		return (int)(i * audioFormat.getSampleSizeInBits() / 8.0F * audioFormat.getChannels() * audioFormat.getSampleRate());
	}

	private void pumpBuffers(int i) {
		if (this.stream != null) {
			try {
				for (int j = 0; j < i; j++) {
					ByteBuffer byteBuffer = this.stream.read(this.streamingBufferSize);
					if (byteBuffer != null) {
						NativeAudio.sourceQueueStreamBuffer(this.sourceHandle, byteBuffer, this.stream.getFormat());
					}
				}
			} catch (IOException var4) {
				LOGGER.error("Failed to read from audio stream", (Throwable)var4);
			}
		}
	}

	public void updateStream() {
		if (this.stream != null) {
			int i = this.removeProcessedBuffers();
			this.pumpBuffers(i);
		}
	}

	private int removeProcessedBuffers() {
		return NativeAudio.sourceRemoveProcessedBuffers(this.sourceHandle);
	}
}
