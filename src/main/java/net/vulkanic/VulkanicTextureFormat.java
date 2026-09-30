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
    /** 4-component RGBA, 8-bit signed-normalized channels. */
    RGBA8_SNORM(4),
    /** Packed unsigned floating-point R11/G11/B10 color. */
    R11F_G11F_B10F(4),
    /** 1-component red, 16-bit floating point. */
    RED16F(2),
    /** 1-component red, 32-bit floating point. */
    RED32F(4),
    /** 4-component BGRA, 8 bits per channel. */
    BGRA8(4),
    /** 1-component red, 8 bits (unsigned normalized). */
    RED8(1),
    /** 1-component red, 8 bits (signed integer). */
    RED8I(1),
    /** 1-component red, 8 bits (unsigned integer). */
    RED8UI(1),
    /** 32-bit depth. */
    DEPTH32(4),
    /** 24-bit depth plus 8-bit stencil. */
    DEPTH24_STENCIL8(4),
    /** 32-bit float depth plus 8-bit stencil. */
    DEPTH32F_STENCIL8(8);

    private final int pixelSize;

    VulkanicTextureFormat(int pixelSize) {
        this.pixelSize = pixelSize;
    }

}
