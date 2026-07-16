package net.blaze3d.audio;

import net.logging.LogUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
	private final Library.Pool pool;
	private final NativeAudio.SoundConfig config = new NativeAudio.SoundConfig();
	private final AtomicBoolean initialized = new AtomicBoolean(true);
	private long soundHandle;
	private int streamingBufferSize = 16384;
	@Nullable
	private AudioStream stream;

	Channel(long deviceHandle, Library.Pool pool) {
		this.deviceHandle = deviceHandle;
		this.pool = pool;
	}

	public void destroy() {
		if (this.initialized.compareAndSet(true, false)) {
			if (this.stream != null) {
				try {
					this.stream.close();
				} catch (IOException var2) {
					LOGGER.error("Failed to close audio stream", (Throwable)var2);
				}

				this.stream = null;
			}

			if (this.soundHandle != 0L) {
				NativeAudio.soundStopAndDestroy(this.soundHandle);
				this.soundHandle = 0L;
			}
		}
	}

	public void play() {
		if (this.soundHandle != 0L) {
			NativeAudio.soundPlay(this.soundHandle);
		}
	}

	private int getState() {
		return !this.initialized.get() || this.soundHandle == 0L ? AL_STOPPED : NativeAudio.soundState(this.soundHandle);
	}

	public void pause() {
		if (this.getState() == AL_PLAYING) {
			NativeAudio.soundPause(this.soundHandle);
		}
	}

	public void unpause() {
		if (this.getState() == AL_PAUSED) {
			NativeAudio.soundPlay(this.soundHandle);
		}
	}

	public void stop() {
		if (this.initialized.get() && this.soundHandle != 0L) {
			NativeAudio.soundStop(this.soundHandle);
		}
	}

	public boolean playing() {
		return this.getState() == AL_PLAYING;
	}

	public boolean stopped() {
		return this.getState() == AL_STOPPED;
	}

	public void setSelfPosition(Vec3 vec3) {
		this.config.x = (float)vec3.x;
		this.config.y = (float)vec3.y;
		this.config.z = (float)vec3.z;
		this.updateNativeSound(NativeAudio.SOUND_UPDATE_POSITION);
	}

	public void setPitch(float f) {
		this.config.pitch = f;
		this.updateNativeSound(NativeAudio.SOUND_UPDATE_PITCH);
	}

	public void setLooping(boolean bl) {
		this.setFlag(NativeAudio.SOUND_FLAG_LOOPING, bl);
		this.updateNativeSound(NativeAudio.SOUND_UPDATE_LOOPING);
	}

	public void setVolume(float f) {
		this.config.gain = f;
		this.updateNativeSound(NativeAudio.SOUND_UPDATE_GAIN);
	}

	public void disableAttenuation() {
		this.config.flags &= ~NativeAudio.SOUND_FLAG_LINEAR_ATTENUATION;
		this.config.flags |= NativeAudio.SOUND_FLAG_DISABLE_ATTENUATION;
		this.updateNativeSound(NativeAudio.SOUND_UPDATE_ATTENUATION);
	}

	public void linearAttenuation(float f) {
		this.config.attenuationDistance = f;
		this.config.flags &= ~NativeAudio.SOUND_FLAG_DISABLE_ATTENUATION;
		this.config.flags |= NativeAudio.SOUND_FLAG_LINEAR_ATTENUATION;
		this.updateNativeSound(NativeAudio.SOUND_UPDATE_ATTENUATION);
	}

	public void setRelative(boolean bl) {
		this.setFlag(NativeAudio.SOUND_FLAG_RELATIVE, bl);
		this.updateNativeSound(NativeAudio.SOUND_UPDATE_RELATIVE);
	}

	public void attachStaticBuffer(SoundBuffer soundBuffer) {
		if (!this.initialized.get() || this.soundHandle != 0L) {
			return;
		}

		soundBuffer.getNativeBuffer(this.deviceHandle).ifPresent(buffer -> {
			this.soundHandle = NativeAudio.soundCreateStatic(this.deviceHandle, this.config, buffer);
			this.warnIfPoolExhausted();
		});
	}

	public void attachBufferStream(AudioStream audioStream) {
		this.stream = audioStream;
		AudioFormat audioFormat = audioStream.getFormat();
		this.streamingBufferSize = calculateBufferSize(audioFormat, 1);
		if (this.initialized.get() && this.soundHandle == 0L) {
			this.soundHandle = NativeAudio.soundCreateStreaming(this.deviceHandle, this.config);
			this.warnIfPoolExhausted();
		}
		if (this.soundHandle != 0L) {
			this.pumpBuffers(QUEUED_BUFFER_COUNT);
		}
	}

	private static int calculateBufferSize(AudioFormat audioFormat, int i) {
		return (int)(i * audioFormat.getSampleSizeInBits() / 8.0F * audioFormat.getChannels() * audioFormat.getSampleRate());
	}

	private void pumpBuffers(int i) {
		if (this.stream != null && this.soundHandle != 0L) {
			try {
				List<ByteBuffer> buffers = new ArrayList<>(i);
				for (int j = 0; j < i; j++) {
					ByteBuffer byteBuffer = this.stream.read(this.streamingBufferSize);
					if (byteBuffer != null) {
						buffers.add(byteBuffer);
					}
				}
				NativeAudio.soundSubmitStreamChunks(this.soundHandle, buffers, this.stream.getFormat());
			} catch (IOException var4) {
				LOGGER.error("Failed to read from audio stream", (Throwable)var4);
			}
		}
	}

	public void updateStream() {
		if (this.stream != null && this.soundHandle != 0L) {
			int i = this.removeProcessedBuffers();
			this.pumpBuffers(i);
		}
	}

	private int removeProcessedBuffers() {
		return NativeAudio.soundRemoveProcessedStreamBuffers(this.soundHandle);
	}

	private void updateNativeSound(int updateMask) {
		if (this.initialized.get() && this.soundHandle != 0L) {
			NativeAudio.soundUpdate(this.soundHandle, updateMask, this.config);
		}
	}

	private void setFlag(int flag, boolean enabled) {
		if (enabled) {
			this.config.flags |= flag;
		} else {
			this.config.flags &= ~flag;
		}
	}

	private void warnIfPoolExhausted() {
		if (this.soundHandle == 0L && net.minecraft.SharedConstants.IS_RUNNING_IN_IDE) {
			LOGGER.warn("Maximum sound pool size reached for {}", this.pool);
		}
	}
}
