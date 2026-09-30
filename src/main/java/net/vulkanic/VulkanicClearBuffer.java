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

}
