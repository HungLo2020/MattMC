package mattmc.client.resources.metadata.animation;

/**
 * Represents the calculated frame size for animated textures.
 * Matches Minecraft's FrameSize record.
 */
public class FrameSize {
    private final int width;
    private final int height;

    public FrameSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }
}
