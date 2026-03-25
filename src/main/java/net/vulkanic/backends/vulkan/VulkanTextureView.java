package net.vulkanic.backends.vulkan;

import net.vulkanic.VulkanicTexture;
import net.vulkanic.VulkanicTextureView;

import java.util.Objects;

/**
 * Vulkan-native implementation of {@link VulkanicTextureView}.
 *
 * <p>In Vulkan, every sampled-texture or attachment reference goes through a
 * {@code VkImageView}. This class wraps a {@code VkImageView} handle created for a
 * specific mip-level range of a parent {@link VulkanTexture}.
 *
 * <p>Calling {@link #close()} destroys the underlying {@code VkImageView} via the
 * {@code closeAction} provided at construction. The parent texture is <em>not</em>
 * affected by closing a view; texture lifetime is managed separately.
 *
 * <p>Thread safety: {@link #close()} is synchronized and idempotent. All other state
 * is set once at construction and effectively immutable.
 */
public class VulkanTextureView extends VulkanicTextureView {

    private final VulkanicTexture texture;
    private final long vkImageViewHandle;
    private final int baseMipLevel;
    private final int mipLevelCount;
    private final Runnable closeAction;

    private volatile boolean closed;

    public VulkanTextureView(VulkanicTexture texture,
                      long vkImageViewHandle,
                      int baseMipLevel,
                      int mipLevelCount,
                      Runnable closeAction) {
        Objects.requireNonNull(texture, "texture must not be null");
        if (baseMipLevel < 0 || mipLevelCount < 1
                || baseMipLevel + mipLevelCount > texture.getMipLevels()) {
            throw new IllegalArgumentException(
                "Invalid mip range [" + baseMipLevel + ", " + (baseMipLevel + mipLevelCount)
                    + ") for texture with " + texture.getMipLevels() + " mip levels");
        }
        this.texture = texture;
        this.vkImageViewHandle = vkImageViewHandle;
        this.baseMipLevel = baseMipLevel;
        this.mipLevelCount = mipLevelCount;
        this.closeAction = Objects.requireNonNull(closeAction, "closeAction must not be null");
    }

    /**
     * Returns the native {@code VkImageView} handle used by this view.
     *
     * <p>This handle is valid until {@link #close()} is called.
     */
    long getVkImageViewHandle() {
        return vkImageViewHandle;
    }

    @Override
    public VulkanicTexture texture() {
        return texture;
    }

    @Override
    public int getBaseMipLevel() {
        return baseMipLevel;
    }

    @Override
    public int getMipLevelCount() {
        return mipLevelCount;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    /**
     * Destroys the underlying {@code VkImageView}. Idempotent — subsequent calls are no-ops.
     *
     * <p>Does NOT affect the parent texture. Does NOT close the parent {@link VulkanTexture}.
     */
    @Override
    public void close() {
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
        }
        closeAction.run();
    }

    @Override
    public String toString() {
        return "VulkanTextureView{"
            + "texture='" + texture.getLabel() + '\''
            + ", vkImageView=0x" + Long.toHexString(vkImageViewHandle)
            + ", baseMip=" + baseMipLevel
            + ", mipCount=" + mipLevelCount
            + ", closed=" + closed
            + '}';
    }
}
