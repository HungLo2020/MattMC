package mattmc.client.sounds;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.Random;

/**
 * Music playback manager, mirroring Minecraft's MusicManager.
 * <p>
 * Handles background music playback with delays between tracks.
 * Music selection is based on the current game situation (menu, game, biome, etc.).
 */
public final class MusicManager {

    private static final Logger logger = LoggerFactory.getLogger(MusicManager.class);

    /** Initial delay before first music track in ticks */
    private static final int STARTING_DELAY = 100;

    private final SoundManager soundManager;
    private final Random random = new Random();

    private SoundInstance currentMusic;
    private int nextSongDelay = STARTING_DELAY;

    /**
     * Create a new MusicManager.
     * @param soundManager The parent sound manager
     */
    public MusicManager(SoundManager soundManager) {
        this.soundManager = soundManager;
    }

    /**
     * Tick the music manager.
     * Should be called each game tick.
     * @param situationalMusic The music that should be playing for the current situation
     */
    public void tick(Music situationalMusic) {
        if (situationalMusic == null) {
            // No music for current situation
            if (currentMusic != null) {
                // Let current music finish naturally
            }
            return;
        }

        if (currentMusic != null) {
            // Check if we should replace current music
            if (situationalMusic.replaceCurrentMusic() && 
                !situationalMusic.getEvent().getLocation().equals(currentMusic.getLocation())) {
                
                soundManager.stop(currentMusic);
                nextSongDelay = randomDelay(0, situationalMusic.getMinDelay() / 2);
            }

            // Check if current music finished
            if (!soundManager.isActive(currentMusic)) {
                currentMusic = null;
                nextSongDelay = Math.min(nextSongDelay, 
                    randomDelay(situationalMusic.getMinDelay(), situationalMusic.getMaxDelay()));
            }
        }

        // Clamp delay to max delay of current music
        nextSongDelay = Math.min(nextSongDelay, situationalMusic.getMaxDelay());

        // Start new music if delay elapsed
        if (currentMusic == null && nextSongDelay-- <= 0) {
            startPlaying(situationalMusic);
        }
    }

    /**
     * Start playing a specific music track.
     */
    public void startPlaying(Music music) {
        currentMusic = SimpleSoundInstance.forMusic(music.getEvent());
        
        // TODO: When music assets are available, this will actually play
        // For now, just log that we would be playing music
        logger.debug("Starting music: {}", music.getEvent().getLocation());
        
        soundManager.play(currentMusic);
        
        // Don't start another track until this one finishes
        nextSongDelay = Integer.MAX_VALUE;
    }

    /**
     * Stop a specific music type if it's playing.
     */
    public void stopPlaying(Music music) {
        if (isPlayingMusic(music)) {
            stopPlaying();
        }
    }

    /**
     * Stop current music.
     */
    public void stopPlaying() {
        if (currentMusic != null) {
            soundManager.stop(currentMusic);
            currentMusic = null;
        }
        nextSongDelay += STARTING_DELAY;
    }

    /**
     * Check if a specific music type is currently playing.
     */
    public boolean isPlayingMusic(Music music) {
        if (currentMusic == null) return false;
        return music.getEvent().getLocation().equals(currentMusic.getLocation());
    }

    /**
     * Check if any music is currently playing.
     */
    public boolean isPlaying() {
        return currentMusic != null && soundManager.isActive(currentMusic);
    }

    /**
     * Get the currently playing music instance.
     * @return The current music instance, or null if no music is playing
     */
    public SoundInstance getCurrentMusic() {
        return currentMusic;
    }

    private int randomDelay(int min, int max) {
        if (max <= min) return min;
        return min + random.nextInt(max - min + 1);
    }
}
