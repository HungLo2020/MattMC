package net.vulkanic;

import java.util.Optional;

/**
 * Backend-neutral clear-buffer bit semantics.
 */
public enum VulkanicClearBuffer {
    COLOR(VulkanicAPI.GL_COLOR_BUFFER_BIT),
    DEPTH(VulkanicAPI.GL_DEPTH_BUFFER_BIT),
    STENCIL(VulkanicAPI.GL_STENCIL_BUFFER_BIT);

    private final int legacyGlMaskBit;

    VulkanicClearBuffer(int legacyGlMaskBit) {
        this.legacyGlMaskBit = legacyGlMaskBit;
    }

    public int toLegacyGlMaskBit() {
        return legacyGlMaskBit;
    }

    public static Optional<VulkanicClearBuffer> fromLegacyGlMaskBit(int legacyGlMaskBit) {
        for (VulkanicClearBuffer buffer : values()) {
            if (buffer.legacyGlMaskBit == legacyGlMaskBit) {
                return Optional.of(buffer);
            }
        }

        return Optional.empty();
    }

    public static int toLegacyGlMask(VulkanicClearBuffer... buffers) {
        if (buffers == null || buffers.length == 0) {
            return 0;
        }

        int mask = 0;
        for (VulkanicClearBuffer buffer : buffers) {
            if (buffer != null) {
                mask |= buffer.legacyGlMaskBit;
            }
        }

        return mask;
    }
}
