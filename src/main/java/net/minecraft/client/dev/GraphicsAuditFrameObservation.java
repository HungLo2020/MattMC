package net.minecraft.client.dev;

/** One frame's diagnostic payload, never a source of rendering state. */
public final class GraphicsAuditFrameObservation {
    private long frameIndex = -1;
    private String payload;

    public void record(long frameIndex, String payload) {
        if (frameIndex < 1 || payload == null) {
            throw new IllegalArgumentException("diagnostic observation requires a positive frame and payload");
        }
        this.frameIndex = frameIndex;
        this.payload = payload;
    }

    /** A startup, previous-frame, or future-frame receipt is not capture evidence. */
    public String matching(long capturedFrameIndex) {
        return capturedFrameIndex > 0 && frameIndex == capturedFrameIndex ? payload : null;
    }
}
