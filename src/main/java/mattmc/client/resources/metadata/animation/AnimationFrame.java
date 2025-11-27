package mattmc.client.resources.metadata.animation;

/**
 * Represents a single frame in an animated texture.
 * Matches Minecraft's AnimationFrame class.
 */
public class AnimationFrame {
    public static final int UNKNOWN_FRAME_TIME = -1;
    private final int index;
    private final int time;

    public AnimationFrame(int index) {
        this(index, UNKNOWN_FRAME_TIME);
    }

    public AnimationFrame(int index, int time) {
        this.index = index;
        this.time = time;
    }

    /**
     * Get the time for this frame, using the default if not specified.
     */
    public int getTime(int defaultTime) {
        return this.time == UNKNOWN_FRAME_TIME ? defaultTime : this.time;
    }

    public int getIndex() {
        return this.index;
    }
}
