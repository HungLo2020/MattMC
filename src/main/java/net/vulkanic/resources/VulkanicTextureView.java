package net.vulkanic.resources;

/**
 * Vulkanic abstraction for a GPU texture view.
 *
 * <p>A texture view is a window into a {@link VulkanicTexture} that may expose only
 * a sub-range of mip levels or array layers.  It is used as a render-pass attachment
 * (colour or depth target) and as a shader-sampling source.
 *
 * <ul>
 *   <li><b>OpenGL backend:</b> wraps a {@code GlTextureView}</li>
 *   <li><b>Vulkan backend:</b> will wrap a {@code VkImageView}</li>
 * </ul>
 *
 * Create via {@link net.vulkanic.VulkanicAPI#createVulkanicTextureView}.
 */
public interface VulkanicTextureView extends AutoCloseable {

    /**
     * Returns the backend-native handle for this texture view.
     * <ul>
     *   <li>OpenGL: same as the underlying texture object name (int), since OpenGL
     *       does not have a distinct "texture view" object for the render-pass use-case</li>
     *   <li>Vulkan: the {@code VkImageView} handle (long)</li>
     * </ul>
     */
    long getNativeHandle();

    /** The underlying texture this view refers to. */
    VulkanicTexture texture();

    /** Index of the first mip level exposed by this view. */
    int getBaseMipLevel();

    /** Number of mip levels exposed by this view. */
    int getMipLevelCount();

    /**
     * Returns the width (in texels) of the given mip level of the underlying texture.
     * Mip level 0 is the full-resolution image.
     *
     * <p>Used to set the {@code glViewport} / {@code VkViewport} dimensions when
     * beginning a render pass that targets this view.
     */
    int getWidth(int mipLevel);

    /**
     * Returns the height (in texels) of the given mip level of the underlying texture.
     * Mip level 0 is the full-resolution image.
     */
    int getHeight(int mipLevel);

    /** Returns {@code true} if the underlying texture has been closed. */
    boolean isClosed();

    /** Releases this view. The underlying texture is not freed here. */
    @Override
    void close();
}
