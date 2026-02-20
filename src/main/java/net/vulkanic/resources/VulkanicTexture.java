package net.vulkanic.resources;

/**
 * Vulkanic abstraction for a GPU texture resource.
 *
 * <p>This interface owns the texture lifecycle concept that previously lived only
 * in Blaze3D's {@code GpuTexture}. Both OpenGL and Vulkan backends implement it,
 * so game/mod code can hold {@code VulkanicTexture} references backend-agnostically.
 *
 * <ul>
 *   <li><b>OpenGL backend:</b> wraps a {@code GlTexture} (GL texture object handle)</li>
 *   <li><b>Vulkan backend:</b> will wrap a {@code VkImage} + {@code VkDeviceMemory}</li>
 * </ul>
 *
 * Create via {@link net.vulkanic.VulkanicAPI#createVulkanicTexture}.
 */
public interface VulkanicTexture extends AutoCloseable {

    // Usage flag constants (mirror GpuTexture values for compatibility)
    int USAGE_COPY_DST           = 1;
    int USAGE_COPY_SRC           = 2;
    int USAGE_TEXTURE_BINDING    = 4;
    int USAGE_RENDER_ATTACHMENT  = 8;
    int USAGE_CUBEMAP_COMPATIBLE = 16;

    /**
     * Returns the backend-native handle for this texture.
     * <ul>
     *   <li>OpenGL: the GL texture object name (int)</li>
     *   <li>Vulkan: the {@code VkImage} handle (long)</li>
     * </ul>
     */
    long getNativeHandle();

    /** Width of the base mip level in texels. */
    int getWidth();

    /** Height of the base mip level in texels. */
    int getHeight();

    /** Depth (for 3-D textures) or layer count (for arrays and cubemaps). */
    int getDepthOrLayers();

    /** Number of mip levels. */
    int getMipLevels();

    /** Usage flags bitmask (see {@code USAGE_*} constants). */
    int getUsage();

    /**
     * Returns the pixel format of this texture.
     * In Vulkan this corresponds to the {@code VkFormat} used when creating the {@code VkImage}.
     */
    VulkanicTextureFormat getVulkanicFormat();

    /** Human-readable label (used for debug output). */
    String getLabel();

    /** Returns {@code true} if this texture has been closed/freed. */
    boolean isClosed();

    /** Frees the GPU texture. Must not be used after this call. */
    @Override
    void close();
}
