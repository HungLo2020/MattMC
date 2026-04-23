package net.vulkanic;

/**
 * Texture formats supported by the Vulkanic abstraction layer.
 *
 * <p>In OpenGL, each value maps to a specific internal/external format pair.
 * In Vulkan, each value maps to a specific VkFormat.
 */
public enum VulkanicTextureFormat {
    /** 4-component RGBA, 8 bits per channel. */
    RGBA8(4),
    /** 4-component RGBA, 16-bit floating-point channels. */
    RGBA16F(8),
    /** 4-component BGRA, 8 bits per channel. */
    BGRA8(4),
    /** 1-component red, 8 bits (unsigned normalized). */
    RED8(1),
    /** 1-component red, 8 bits (signed integer). */
    RED8I(1),
    /** 32-bit depth. */
    DEPTH32(4);

    private final int pixelSize;

    VulkanicTextureFormat(int pixelSize) {
        this.pixelSize = pixelSize;
    }

    /** Returns the size in bytes of a single pixel in this format. */
    public int pixelSize() {
        return pixelSize;
    }

    /** Returns true if this format has a color component. */
    public boolean hasColorAspect() {
        return this == RGBA8 || this == RGBA16F || this == BGRA8 || this == RED8 || this == RED8I;
    }

    /** Returns true if this format has a depth component. */
    public boolean hasDepthAspect() {
        return this == DEPTH32;
    }
}
