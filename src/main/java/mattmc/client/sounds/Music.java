package mattmc.client.sounds;

/**
 * Represents music track configuration, mirroring Minecraft's Music class.
 * <p>
 * Contains the sound event to play and timing information for when
 * the music manager should start/stop playing and whether it should
 * interrupt currently playing music.
 */
public final class Music {

    private final SoundEvent event;
    private final int minDelay;
    private final int maxDelay;
    private final boolean replaceCurrentMusic;

    /**
     * Create a music track configuration.
     * @param event The sound event for this music
     * @param minDelay Minimum ticks before this music can start
     * @param maxDelay Maximum ticks before this music must start
     * @param replaceCurrentMusic Whether to stop current music when this should play
     */
    public Music(SoundEvent event, int minDelay, int maxDelay, boolean replaceCurrentMusic) {
        this.event = event;
        this.minDelay = minDelay;
        this.maxDelay = maxDelay;
        this.replaceCurrentMusic = replaceCurrentMusic;
    }

    /**
     * Get the sound event for this music track.
     */
    public SoundEvent getEvent() {
        return event;
    }

    /**
     * Get the minimum delay in ticks before this music should start playing.
     */
    public int getMinDelay() {
        return minDelay;
    }

    /**
     * Get the maximum delay in ticks - music will definitely start by this time.
     */
    public int getMaxDelay() {
        return maxDelay;
    }

    /**
     * Whether this music should interrupt currently playing music.
     */
    public boolean replaceCurrentMusic() {
        return replaceCurrentMusic;
    }
}
