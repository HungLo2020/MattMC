package mattmc.client.sounds;

import mattmc.client.settings.OptionsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.List;

/**
 * High-level sound management API, mirroring Minecraft's SoundManager.
 * <p>
 * Provides a simplified interface for playing sounds, managing volume,
 * and controlling playback. This is the primary API that game code should use.
 */
public final class SoundManager {

    private static final Logger logger = LoggerFactory.getLogger(SoundManager.class);

    private final SoundEngine soundEngine;
    private boolean initialized;

    /**
     * Create a new SoundManager.
     */
    public SoundManager() {
        this.soundEngine = new SoundEngine(this);
    }

    /**
     * Initialize the sound system.
     * Should be called during game startup.
     */
    public void init() {
        if (initialized) {
            logger.warn("SoundManager already initialized");
            return;
        }

        soundEngine.loadLibrary(null);
        initialized = true;
        logger.info("SoundManager initialized");
    }

    /**
     * Check if the sound system is initialized.
     */
    public boolean isInitialized() {
        return initialized && soundEngine.isLoaded();
    }

    /**
     * Reload the sound system.
     */
    public void reload() {
        soundEngine.reload();
    }

    /**
     * Clean up all sound resources.
     * Should be called during game shutdown.
     */
    public void destroy() {
        soundEngine.destroy();
        initialized = false;
        logger.info("SoundManager destroyed");
    }

    /**
     * Play a sound.
     * @param sound The sound instance to play
     */
    public void play(SoundInstance sound) {
        soundEngine.play(sound);
    }

    /**
     * Play a sound after a delay.
     * @param sound The sound to play
     * @param delayTicks Delay in game ticks (20 ticks = 1 second)
     */
    public void playDelayed(SoundInstance sound, int delayTicks) {
        soundEngine.playDelayed(sound, delayTicks);
    }

    /**
     * Stop a specific sound.
     */
    public void stop(SoundInstance sound) {
        soundEngine.stop(sound);
    }

    /**
     * Stop all playing sounds.
     */
    public void stop() {
        soundEngine.stopAll();
    }

    /**
     * Stop sounds by location and/or category.
     */
    public void stop(String location, SoundSource category) {
        soundEngine.stop(location, category);
    }

    /**
     * Check if a sound is currently playing.
     */
    public boolean isActive(SoundInstance sound) {
        return soundEngine.isActive(sound);
    }

    /**
     * Pause all sounds.
     */
    public void pause() {
        soundEngine.pause();
    }

    /**
     * Resume paused sounds.
     */
    public void resume() {
        soundEngine.resume();
    }

    /**
     * Update the volume for a sound category.
     * Called when settings change.
     */
    public void updateSourceVolume(SoundSource category, float volume) {
        if (category == SoundSource.MASTER && volume <= 0.0f) {
            stop();
        }
        soundEngine.updateCategoryVolume(category, volume);
    }

    /**
     * Update listener position from the camera/player.
     * Should be called each frame.
     */
    public void updateListener(double x, double y, double z,
                                float lookX, float lookY, float lookZ,
                                float upX, float upY, float upZ) {
        soundEngine.updateListener(x, y, z, lookX, lookY, lookZ, upX, upY, upZ);
    }

    /**
     * Tick the sound system.
     * Should be called each game tick.
     * @param isPaused Whether the game is paused
     */
    public void tick(boolean isPaused) {
        soundEngine.tick(isPaused);
    }

    /**
     * Get a list of available audio devices.
     */
    public List<String> getAvailableSoundDevices() {
        return soundEngine.getLibrary().getAvailableSoundDevices();
    }

    /**
     * Get a debug string showing current state.
     */
    public String getDebugString() {
        return soundEngine.getDebugString();
    }

    // === Convenience methods for playing common sounds ===

    /**
     * Play a UI sound (non-positional).
     * @param event The sound event to play
     */
    public void playUISound(SoundEvent event) {
        play(SimpleSoundInstance.forUI(event, 1.0f));
    }

    /**
     * Play a UI sound with custom pitch.
     * @param event The sound event to play
     * @param pitch Pitch multiplier
     */
    public void playUISound(SoundEvent event, float pitch) {
        play(SimpleSoundInstance.forUI(event, pitch));
    }

    /**
     * Play a positional world sound.
     * @param event Sound event
     * @param source Sound category
     * @param x World X position
     * @param y World Y position
     * @param z World Z position
     */
    public void playWorldSound(SoundEvent event, SoundSource source,
                                double x, double y, double z) {
        playWorldSound(event, source, 1.0f, 1.0f, x, y, z);
    }

    /**
     * Play a positional world sound with volume and pitch.
     */
    public void playWorldSound(SoundEvent event, SoundSource source,
                                float volume, float pitch,
                                double x, double y, double z) {
        play(SimpleSoundInstance.forWorld(event, source, volume, pitch, x, y, z));
    }

    /**
     * Play a block sound (breaking, placing, stepping).
     * TODO: Hook this into BlockEventHandler when block sounds are implemented
     */
    public void playBlockSound(SoundEvent event, int blockX, int blockY, int blockZ) {
        playBlockSound(event, 1.0f, 1.0f, blockX, blockY, blockZ);
    }

    /**
     * Play a block sound with volume and pitch.
     */
    public void playBlockSound(SoundEvent event, float volume, float pitch,
                                int blockX, int blockY, int blockZ) {
        play(SimpleSoundInstance.forBlock(event, volume, pitch, blockX, blockY, blockZ));
    }
}
