package net.vulkanic;

/**
 * Interface for a GPU texture managed by the Vulkanic abstraction layer.
 *
 * <p>In OpenGL, implementations wrap a GL texture object.
 * In Vulkan, implementations will wrap a VkImage + VkDeviceMemory pair.
 *
 * <p>This is an interface (not abstract class) so that Blaze3D's {@code GpuTexture} can
 * implement it without changing its inheritance chain. That unifies the texture type
 * hierarchy: any {@code GpuTexture} (including {@code GlTexture}) is automatically a
 * {@code VulkanicTexture}, eliminating GL-handle bridge objects at render-pass boundaries.
 */
public interface VulkanicTexture extends AutoCloseable {

    /** Texture can be the destination of a copy operation. */
    int USAGE_COPY_DST = 1;
    /** Texture can be the source of a copy operation. */
    int USAGE_COPY_SRC = 2;
    /** Texture can be bound as a sampled texture in shaders. */
    int USAGE_TEXTURE_BINDING = 4;
    /** Texture can be used as a render pass attachment. */
    int USAGE_RENDER_ATTACHMENT = 8;
    /** Texture is cubemap-compatible (must have 6 or 6*N layers). */
    int USAGE_CUBEMAP_COMPATIBLE = 16;

    /**
     * Returns the width of the texture at the given mip level.
     *
     * @param mipLevel the mip level (0 = full resolution)
     */
    int getWidth(int mipLevel);

    /**
     * Returns the height of the texture at the given mip level.
     *
     * @param mipLevel the mip level (0 = full resolution)
     */
    int getHeight(int mipLevel);

    /** Returns the number of mip levels in this texture (at least 1). */
    int getMipLevels();

    /** Returns the depth (for 3D textures) or layer count (for arrays). */
    int getDepthOrLayers();

    /**
     * Returns the Vulkanic texture format for this texture.
     *
     * <p>This method is named {@code getVulkanicFormat()} rather than {@code getFormat()}
     * to avoid a return-type conflict with {@code GpuTexture.getFormat()} which returns
     * the Blaze3D {@code TextureFormat} enum. Both enums carry identical values; the
     * distinction exists only during the migration period while both type hierarchies coexist.
     */
    VulkanicTextureFormat getVulkanicFormat();

    /** Returns the usage flags this texture was created with. */
    int usage();

    /** Returns the debug label for this texture. */
    String getLabel();

    /** Returns true if this texture has been closed and its GPU resources freed. */
    boolean isClosed();

    /** Frees the GPU resources backing this texture. */
    @Override
    void close();
}
