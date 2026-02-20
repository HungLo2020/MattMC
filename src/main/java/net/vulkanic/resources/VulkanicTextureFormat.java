package net.vulkanic.resources;

/**
 * Vulkanic-owned texture format enumeration.
 *
 * <p>This enum lives in the Vulkanic layer so that both the OpenGL and Vulkan
 * backends can receive texture creation requests using a type that is independent
 * of any graphics-API SDK.
 *
 * <ul>
 *   <li><b>OpenGL backend:</b> maps each value to the GL internal format, external format,
 *       and component type constants used by {@code glTexImage2D}.</li>
 *   <li><b>Vulkan backend (future):</b> will map each value to the corresponding
 *       {@code VkFormat} constant (e.g. {@code VK_FORMAT_R8G8B8A8_UNORM},
 *       {@code VK_FORMAT_D32_SFLOAT}, etc.).</li>
 * </ul>
 *
 * <p>Blaze3D's {@code TextureFormat} enum is mapped to this type inside
 * {@link net.blaze3d.opengl.GlDevice} — the conversion stays on the Blaze3D side
 * so that Vulkanic never depends on Blaze3D.
 */
public enum VulkanicTextureFormat {

    /** 4-channel 8-bit-per-channel RGBA colour.  Vulkan: {@code VK_FORMAT_R8G8B8A8_UNORM}. */
    RGBA8(4),

    /** Single-channel 8-bit unsigned normalised (red/greyscale).  Vulkan: {@code VK_FORMAT_R8_UNORM}. */
    RED8(1),

    /** Single-channel 8-bit signed integer.  Vulkan: {@code VK_FORMAT_R8_SINT}. */
    RED8I(1),

    /** 32-bit floating-point depth.  Vulkan: {@code VK_FORMAT_D32_SFLOAT}. */
    DEPTH32(4);

    private final int pixelSize;

    VulkanicTextureFormat(int pixelSize) {
        this.pixelSize = pixelSize;
    }

    /**
     * Returns the size in bytes of a single texel for this format.
     * Useful when calculating staging-buffer sizes for texture uploads.
     */
    public int pixelSize() {
        return pixelSize;
    }

    /**
     * Returns {@code true} when this format has a colour aspect (not depth/stencil).
     * In Vulkan terms this corresponds to {@code VK_IMAGE_ASPECT_COLOR_BIT}.
     */
    public boolean hasColorAspect() {
        return this != DEPTH32;
    }

    /**
     * Returns {@code true} when this format carries depth information.
     * In Vulkan terms this corresponds to {@code VK_IMAGE_ASPECT_DEPTH_BIT}.
     */
    public boolean hasDepthAspect() {
        return this == DEPTH32;
    }
}
