package mattmc.client.sounds;

import org.lwjgl.openal.AL10;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Represents an OpenAL sound source (channel), mirroring Minecraft's Channel.
 * <p>
 * A Channel wraps an OpenAL source and provides methods to control playback,
 * position, volume, pitch, and looping. Each playing sound gets its own channel.
 */
public final class Channel {

    private static final Logger logger = LoggerFactory.getLogger(Channel.class);

    // OpenAL source state constants
    private static final int AL_INITIAL = 0x1011;
    private static final int AL_PLAYING = 0x1012;
    private static final int AL_PAUSED = 0x1013;
    private static final int AL_STOPPED = 0x1014;
    
    // OpenAL source property constants
    private static final int AL_SOURCE_RELATIVE = 0x202;
    private static final int AL_DISTANCE_MODEL = 0xD000;
    private static final int AL_NONE = 0;
    private static final int AL_LINEAR_DISTANCE_CLAMPED = 0xD004;

    private final int source;
    private final AtomicBoolean initialized = new AtomicBoolean(true);

    /**
     * Create a new OpenAL channel (source).
     * @return A new Channel, or null if source creation failed
     */
    public static Channel create() {
        int[] sources = new int[1];
        AL10.alGenSources(sources);
        if (OpenALUtils.checkALError("Generate source")) {
            return null;
        }
        return new Channel(sources[0]);
    }

    private Channel(int source) {
        this.source = source;
    }

    /**
     * Destroy this channel, releasing the OpenAL source.
     */
    public void destroy() {
        if (initialized.compareAndSet(true, false)) {
            AL10.alSourceStop(source);
            OpenALUtils.checkALError("Stop source for cleanup");
            
            AL10.alDeleteSources(new int[] { source });
            OpenALUtils.checkALError("Delete source");
        }
    }

    /**
     * Start or resume playing.
     */
    public void play() {
        if (initialized.get()) {
            AL10.alSourcePlay(source);
            OpenALUtils.checkALError("Play source");
        }
    }

    /**
     * Pause playback.
     */
    public void pause() {
        if (getState() == AL_PLAYING) {
            AL10.alSourcePause(source);
            OpenALUtils.checkALError("Pause source");
        }
    }

    /**
     * Resume from pause.
     */
    public void unpause() {
        if (getState() == AL_PAUSED) {
            AL10.alSourcePlay(source);
            OpenALUtils.checkALError("Unpause source");
        }
    }

    /**
     * Stop playback.
     */
    public void stop() {
        if (initialized.get()) {
            AL10.alSourceStop(source);
            OpenALUtils.checkALError("Stop source");
        }
    }

    /**
     * Check if the source is currently playing.
     */
    public boolean isPlaying() {
        return getState() == AL_PLAYING;
    }

    /**
     * Check if the source is stopped.
     */
    public boolean isStopped() {
        return getState() == AL_STOPPED || getState() == AL_INITIAL;
    }

    private int getState() {
        if (!initialized.get()) {
            return AL_STOPPED;
        }
        return AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
    }

    /**
     * Set the 3D position of this sound source.
     */
    public void setPosition(double x, double y, double z) {
        if (initialized.get()) {
            AL10.alSource3f(source, AL10.AL_POSITION, (float) x, (float) y, (float) z);
            OpenALUtils.checkALError("Set source position");
        }
    }

    /**
     * Set the pitch multiplier.
     * @param pitch Pitch multiplier (0.5 - 2.0)
     */
    public void setPitch(float pitch) {
        if (initialized.get()) {
            AL10.alSourcef(source, AL10.AL_PITCH, pitch);
            OpenALUtils.checkALError("Set source pitch");
        }
    }

    /**
     * Set the volume/gain.
     * @param volume Volume (0.0 - 1.0)
     */
    public void setVolume(float volume) {
        if (initialized.get()) {
            AL10.alSourcef(source, AL10.AL_GAIN, volume);
            OpenALUtils.checkALError("Set source volume");
        }
    }

    /**
     * Set whether this sound should loop.
     */
    public void setLooping(boolean looping) {
        if (initialized.get()) {
            AL10.alSourcei(source, AL10.AL_LOOPING, looping ? AL10.AL_TRUE : AL10.AL_FALSE);
            OpenALUtils.checkALError("Set source looping");
        }
    }

    /**
     * Disable distance attenuation (for UI sounds, music).
     */
    public void disableAttenuation() {
        if (initialized.get()) {
            AL10.alSourcei(source, AL_SOURCE_RELATIVE, AL10.AL_TRUE);
            AL10.alSourcei(source, AL_DISTANCE_MODEL, AL_NONE);
            OpenALUtils.checkALError("Disable attenuation");
        }
    }

    /**
     * Enable linear distance attenuation.
     * @param maxDistance The distance at which sound is fully attenuated
     */
    public void enableLinearAttenuation(float maxDistance) {
        if (initialized.get()) {
            AL10.alSourcei(source, AL_SOURCE_RELATIVE, AL10.AL_FALSE);
            AL10.alSourcei(source, AL_DISTANCE_MODEL, AL_LINEAR_DISTANCE_CLAMPED);
            AL10.alSourcef(source, AL10.AL_REFERENCE_DISTANCE, 1.0f);
            AL10.alSourcef(source, AL10.AL_MAX_DISTANCE, maxDistance);
            AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 1.0f);
            OpenALUtils.checkALError("Enable linear attenuation");
        }
    }

    /**
     * Set whether position is relative to the listener.
     */
    public void setRelative(boolean relative) {
        if (initialized.get()) {
            AL10.alSourcei(source, AL_SOURCE_RELATIVE, relative ? AL10.AL_TRUE : AL10.AL_FALSE);
            OpenALUtils.checkALError("Set source relative");
        }
    }

    /**
     * Get the OpenAL source ID for direct operations.
     * Use with caution.
     */
    int getSourceId() {
        return source;
    }
}
