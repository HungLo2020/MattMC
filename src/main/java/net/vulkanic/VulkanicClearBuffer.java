package net.vulkanic;

import java.util.Optional;
import java.util.List;
import java.util.ArrayList;

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

    public static List<VulkanicClearBuffer> fromLegacyGlMask(int legacyGlMask) {
        List<VulkanicClearBuffer> buffers = new ArrayList<>();
        for (VulkanicClearBuffer buffer : values()) {
            if ((legacyGlMask & buffer.legacyGlMaskBit) != 0) {
                buffers.add(buffer);
                legacyGlMask &= ~buffer.legacyGlMaskBit;
            }
        }
        if (legacyGlMask != 0) {
            return List.of();
        }
        return List.copyOf(buffers);
    }
}
