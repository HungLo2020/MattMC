package mattmc.client.sounds;

import mattmc.client.settings.OptionsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Core audio engine, mirroring Minecraft's SoundEngine.
 * <p>
 * Handles OpenAL initialization, sound playback, channel management,
 * and listener updates. All sound operations go through this class.
 */
public final class SoundEngine {

    private static final Logger logger = LoggerFactory.getLogger(SoundEngine.class);

    private static final float PITCH_MIN = 0.5f;
    private static final float PITCH_MAX = 2.0f;
    private static final float VOLUME_MIN = 0.0f;
    private static final float VOLUME_MAX = 1.0f;
    private static final int MIN_SOURCE_LIFETIME_TICKS = 20;

    private final SoundManager soundManager;
    private final OpenALLibrary library = new OpenALLibrary();
    private boolean loaded;
    private int tickCount;

    // Active sound tracking
    private final Map<SoundInstance, Channel> instanceToChannel = new HashMap<>();
    private final Map<SoundInstance, Integer> soundDeleteTime = new HashMap<>();
    private final Map<SoundInstance, Integer> queuedSounds = new HashMap<>();
    private final Set<String> onlyWarnOnce = new HashSet<>();

    /**
     * Create a new SoundEngine.
     * @param soundManager The parent sound manager
     */
    public SoundEngine(SoundManager soundManager) {
        this.soundManager = soundManager;
    }

    /**
     * Initialize or reload the sound engine.
     * @param deviceName Specific device to use, or null for default
     */
    public synchronized void loadLibrary(String deviceName) {
        if (loaded) {
            return;
        }

        try {
            if (!library.init(deviceName)) {
                logger.error("Failed to initialize sound engine. Sounds will be disabled.");
                return;
            }

            // Set initial master volume
            float masterVolume = OptionsManager.getSoundSourceVolume(SoundSource.MASTER);
            library.getListener().setGain(masterVolume);

            loaded = true;
            logger.info("Sound engine started");

        } catch (Exception e) {
            logger.error("Error starting sound system", e);
        }
    }

    /**
     * Reload the sound engine (e.g., after device change).
     */
    public void reload() {
        onlyWarnOnce.clear();
        destroy();
        loadLibrary(null);
    }

    /**
     * Clean up all sound resources.
     */
    public void destroy() {
        if (loaded) {
            stopAll();
            library.cleanup();
            loaded = false;
        }
    }

    /**
     * Check if the sound engine is loaded and ready.
     */
    public boolean isLoaded() {
        return loaded;
    }

    /**
     * Get the volume for a sound category.
     * @param source The sound category, or null for 1.0
     */
    private float getVolume(SoundSource source) {
        if (source == null || source == SoundSource.MASTER) {
            return 1.0f;
        }
        return OptionsManager.getSoundSourceVolume(source);
    }

    /**
     * Update the volume for a sound category.
     * Called when settings change.
     */
    public void updateCategoryVolume(SoundSource source, float volume) {
        if (!loaded) return;

        if (source == SoundSource.MASTER) {
            // Master volume affects the listener gain
            library.getListener().setGain(volume);
        } else {
            // Update all playing sounds in this category
            for (Map.Entry<SoundInstance, Channel> entry : instanceToChannel.entrySet()) {
                SoundInstance sound = entry.getKey();
                if (sound.getSource() == source) {
                    float finalVolume = calculateVolume(sound);
                    Channel channel = entry.getValue();
                    if (finalVolume <= 0.0f) {
                        channel.stop();
                    } else {
                        channel.setVolume(finalVolume);
                    }
                }
            }
        }
    }

    /**
     * Stop a specific sound instance.
     */
    public void stop(SoundInstance sound) {
        if (!loaded) return;

        Channel channel = instanceToChannel.get(sound);
        if (channel != null) {
            channel.stop();
        }
    }

    /**
     * Stop all playing sounds.
     */
    public void stopAll() {
        if (!loaded) return;

        for (Channel channel : instanceToChannel.values()) {
            channel.stop();
            library.releaseChannel(channel);
        }
        instanceToChannel.clear();
        soundDeleteTime.clear();
        queuedSounds.clear();
    }

    /**
     * Pause all sounds.
     */
    public void pause() {
        if (!loaded) return;

        for (Channel channel : instanceToChannel.values()) {
            channel.pause();
        }
    }

    /**
     * Resume all paused sounds.
     */
    public void resume() {
        if (!loaded) return;

        for (Channel channel : instanceToChannel.values()) {
            channel.unpause();
        }
    }

    /**
     * Play a sound instance.
     * @param sound The sound to play
     */
    public void play(SoundInstance sound) {
        if (!loaded) return;

        if (!sound.canPlaySound()) {
            return;
        }

        String location = sound.getLocation();
        SoundSource source = sound.getSource();
        float volume = sound.getVolume();
        float pitch = sound.getPitch();

        // Calculate final volume with category volume
        float finalVolume = calculateVolume(volume, source);
        float finalPitch = clamp(pitch, PITCH_MIN, PITCH_MAX);

        // Skip silent sounds unless they can start silent
        if (finalVolume <= 0.0f && !sound.canStartSilent()) {
            logger.debug("Skipping sound {} - volume is zero", location);
            return;
        }

        // Check master volume
        if (library.getListener().getGain() <= 0.0f) {
            logger.debug("Skipping sound {} - master volume is zero", location);
            return;
        }

        // Acquire a channel
        // TODO: Add streaming support for music files
        Channel channel = library.acquireStaticChannel();
        if (channel == null) {
            logger.warn("Failed to acquire channel for sound {}", location);
            return;
        }

        // Configure the channel
        channel.setPitch(finalPitch);
        channel.setVolume(finalVolume);
        channel.setLooping(sound.isLooping());

        if (sound.getAttenuation() == SoundInstance.Attenuation.LINEAR) {
            channel.enableLinearAttenuation(16.0f);  // Default 16 block range
            channel.setPosition(sound.getX(), sound.getY(), sound.getZ());
        } else {
            channel.disableAttenuation();
        }

        channel.setRelative(sound.isRelative());

        // Track the sound
        soundDeleteTime.put(sound, tickCount + MIN_SOURCE_LIFETIME_TICKS);
        instanceToChannel.put(sound, channel);

        // TODO: Load actual sound data from resources
        // For now, the channel is configured but won't play anything
        // since we haven't attached a buffer
        // 
        // When sound assets are added:
        // 1. Load OGG/WAV file from resources
        // 2. Create OpenAL buffer with decoded audio data
        // 3. Attach buffer to channel using AL10.alSourcei(source, AL10.AL_BUFFER, bufferId)
        // 4. Call channel.play()
        
        logger.debug("Playing sound {} (vol={}, pitch={})", location, finalVolume, finalPitch);
        
        // Uncomment when audio assets are available:
        // channel.play();
    }

    /**
     * Queue a sound to play after a delay.
     */
    public void playDelayed(SoundInstance sound, int delayTicks) {
        queuedSounds.put(sound, tickCount + delayTicks);
    }

    /**
     * Check if a sound is currently playing.
     */
    public boolean isActive(SoundInstance sound) {
        if (!loaded) return false;

        if (soundDeleteTime.containsKey(sound) && soundDeleteTime.get(sound) <= tickCount) {
            return true;
        }
        return instanceToChannel.containsKey(sound);
    }

    /**
     * Stop all sounds with a specific location and/or category.
     */
    public void stop(String location, SoundSource source) {
        if (source != null) {
            for (Map.Entry<SoundInstance, Channel> entry : instanceToChannel.entrySet()) {
                SoundInstance sound = entry.getKey();
                if (sound.getSource() == source) {
                    if (location == null || sound.getLocation().equals(location)) {
                        entry.getValue().stop();
                    }
                }
            }
        } else if (location == null) {
            stopAll();
        } else {
            for (Map.Entry<SoundInstance, Channel> entry : instanceToChannel.entrySet()) {
                if (entry.getKey().getLocation().equals(location)) {
                    entry.getValue().stop();
                }
            }
        }
    }

    /**
     * Tick the sound engine - update playing sounds and clean up finished ones.
     * @param isPaused Whether the game is paused
     */
    public void tick(boolean isPaused) {
        if (!loaded) return;

        if (!isPaused) {
            tickNonPaused();
        }
    }

    private void tickNonPaused() {
        tickCount++;

        // Process queued sounds
        Iterator<Map.Entry<SoundInstance, Integer>> queuedIt = queuedSounds.entrySet().iterator();
        while (queuedIt.hasNext()) {
            Map.Entry<SoundInstance, Integer> entry = queuedIt.next();
            if (tickCount >= entry.getValue()) {
                play(entry.getKey());
                queuedIt.remove();
            }
        }

        // Clean up finished sounds
        Iterator<Map.Entry<SoundInstance, Channel>> it = instanceToChannel.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<SoundInstance, Channel> entry = it.next();
            SoundInstance sound = entry.getKey();
            Channel channel = entry.getValue();

            // Check if category volume is zero
            float categoryVolume = OptionsManager.getSoundSourceVolume(sound.getSource());
            if (categoryVolume <= 0.0f) {
                channel.stop();
                it.remove();
                soundDeleteTime.remove(sound);
                library.releaseChannel(channel);
                continue;
            }

            // Check if channel stopped
            if (channel.isStopped()) {
                Integer deleteTime = soundDeleteTime.get(sound);
                if (deleteTime != null && deleteTime <= tickCount) {
                    it.remove();
                    soundDeleteTime.remove(sound);
                    library.releaseChannel(channel);
                }
            }
        }
    }

    /**
     * Update the listener position and orientation from the camera.
     */
    public void updateListener(double x, double y, double z,
                                float lookX, float lookY, float lookZ,
                                float upX, float upY, float upZ) {
        if (!loaded) return;

        Listener listener = library.getListener();
        listener.setPosition(x, y, z);
        listener.setOrientation(lookX, lookY, lookZ, upX, upY, upZ);
    }

    /**
     * Calculate the final volume for a sound.
     */
    private float calculateVolume(SoundInstance sound) {
        return calculateVolume(sound.getVolume(), sound.getSource());
    }

    private float calculateVolume(float baseVolume, SoundSource source) {
        float categoryVolume = getVolume(source);
        return clamp(baseVolume * categoryVolume, VOLUME_MIN, VOLUME_MAX);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Get the OpenAL library for low-level operations.
     */
    OpenALLibrary getLibrary() {
        return library;
    }

    /**
     * Get a debug string showing current state.
     */
    public String getDebugString() {
        return library.getDebugString();
    }
}
