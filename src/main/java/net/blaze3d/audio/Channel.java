package net.blaze3d.audio;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.world.phys.Vec3;
import net.logging.LogUtils;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class Channel {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final int BUFFER_DURATION_SECONDS = 1;
	private static final int AL_PLAYING = 4114;
	private static final int AL_PAUSED = 4115;
	private static final int AL_STOPPED = 4116;
	private final long deviceHandle;
	private final NativeAudio.SoundConfig config = new NativeAudio.SoundConfig();
	private long soundHandle;
	private boolean awaitingAttachment = true;
	private boolean stopRequested;

	Channel(long deviceHandle) {
		this.deviceHandle = deviceHandle;
	}

	public void destroy() {
		this.awaitingAttachment = false;
		this.stopRequested = true;
		if (this.soundHandle != 0L) {
			NativeAudio.soundStopAndDestroy(this.soundHandle);
			this.soundHandle = 0L;
		}
	}

	public void play() {
		if (!this.stopRequested && this.soundHandle != 0L) {
			NativeAudio.soundPlay(this.soundHandle);
		}
	}

	private int getState() {
		return this.soundHandle == 0L ? AL_STOPPED : NativeAudio.soundState(this.soundHandle);
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
		this.awaitingAttachment = false;
		this.stopRequested = true;
		if (this.soundHandle != 0L) {
			NativeAudio.soundStop(this.soundHandle);
		}
	}

	public boolean playing() {
		return this.getState() == AL_PLAYING;
	}

	public boolean stopped() {
		return !this.awaitingAttachment && this.getState() == AL_STOPPED;
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

	public void attachStaticAsset(NativeAudioAsset asset) {
		if (this.stopRequested) {
			this.awaitingAttachment = false;
			return;
		}

		if (this.soundHandle != 0L) {
			this.awaitingAttachment = false;
			return;
		}

		try {
			this.soundHandle = NativeAudio.soundCreateStaticFromAsset(this.deviceHandle, this.config, asset.handleForPlayback());
			this.warnIfPoolExhausted("static");
		} finally {
			this.awaitingAttachment = false;
		}
	}

	public void attachStreamingAsset(NativeAudioAsset asset, boolean looping) {
		if (this.stopRequested) {
			this.awaitingAttachment = false;
			return;
		}

		if (this.soundHandle != 0L) {
			this.awaitingAttachment = false;
			return;
		}

		try {
			this.soundHandle = NativeAudio.soundCreateStreamingFromAsset(this.deviceHandle, this.config, asset.handleForPlayback(), looping);
			this.warnIfPoolExhausted("streaming");
		} finally {
			this.awaitingAttachment = false;
		}
	}

	public void failAttachment() {
		this.awaitingAttachment = false;
		this.stopRequested = true;
	}

	private void updateNativeSound(int updateMask) {
		if (this.soundHandle != 0L) {
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

	private void warnIfPoolExhausted(String pool) {
		if (this.soundHandle == 0L && net.minecraft.SharedConstants.IS_RUNNING_IN_IDE) {
			LOGGER.warn("Maximum sound pool size reached for {}", pool);
		}
	}

}
