package net.vulkanic;

/**
 * Abstract view into a {@link VulkanicTexture}.
 *
 * <p>A texture view selects a subset of mip levels from a parent texture.
 * In OpenGL, this is a lightweight descriptor (no separate GL object needed for simple views).
 * In Vulkan, this wraps a VkImageView.
 */
public abstract class VulkanicTextureView implements AutoCloseable {

    /** Returns the parent texture this view was created from. */
    public abstract VulkanicTexture texture();

    /** Returns the index of the first mip level this view exposes. */
    public abstract int getBaseMipLevel();

    /** Returns the number of mip levels this view exposes. */
    public abstract int getMipLevelCount();

    /**
     * Returns the width at the view's base mip level.
     */
    public int getWidth(int mipOffset) {
        return texture().getWidth(getBaseMipLevel() + mipOffset);
    }

    /**
     * Returns the height at the view's base mip level.
     */
    public int getHeight(int mipOffset) {
        return texture().getHeight(getBaseMipLevel() + mipOffset);
    }

    /** Returns true if this view has been closed. */
    public abstract boolean isClosed();

    /** Closes this view (does NOT close the parent texture). */
    @Override
    public abstract void close();
}
