package net.vulkanic;

/**
 * Abstract GPU texture managed by the Vulkanic abstraction layer.
 *
 * <p>In OpenGL, this wraps a GL texture object.
 * In Vulkan, this will wrap a VkImage + VkDeviceMemory pair.
 */
public abstract class VulkanicTexture implements AutoCloseable {

    /** Texture can be the destination of a copy operation. */
    public static final int USAGE_COPY_DST = 1;
    /** Texture can be the source of a copy operation. */
    public static final int USAGE_COPY_SRC = 2;
    /** Texture can be bound as a sampled texture in shaders. */
    public static final int USAGE_TEXTURE_BINDING = 4;
    /** Texture can be used as a render pass attachment. */
    public static final int USAGE_RENDER_ATTACHMENT = 8;
    /** Texture is cubemap-compatible (must have 6 or 6*N layers). */
    public static final int USAGE_CUBEMAP_COMPATIBLE = 16;

    /**
     * Returns the width of the texture at the given mip level.
     *
     * @param mipLevel the mip level (0 = full resolution)
     */
    public abstract int getWidth(int mipLevel);

    /**
     * Returns the height of the texture at the given mip level.
     *
     * @param mipLevel the mip level (0 = full resolution)
     */
    public abstract int getHeight(int mipLevel);

    /** Returns the number of mip levels in this texture (at least 1). */
    public abstract int getMipLevels();

    /** Returns the depth (for 3D textures) or layer count (for arrays). */
    public abstract int getDepthOrLayers();

    /** Returns the format this texture was created with. */
    public abstract VulkanicTextureFormat getFormat();

    /** Returns the usage flags this texture was created with. */
    public abstract int usage();

    /** Returns the debug label for this texture. */
    public abstract String getLabel();

    /** Returns true if this texture has been closed and its GPU resources freed. */
    public abstract boolean isClosed();

    /** Frees the GPU resources backing this texture. */
    @Override
    public abstract void close();
}
