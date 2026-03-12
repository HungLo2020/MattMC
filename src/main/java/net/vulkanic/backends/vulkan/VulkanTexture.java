package net.vulkanic.backends.vulkan;

import net.vulkanic.VulkanicTexture;
import net.vulkanic.VulkanicTextureFormat;

import java.util.Objects;

/**
 * Vulkan-native implementation of {@link VulkanicTexture}.
 *
 * <p>Wraps a {@code VkImage + VkDeviceMemory} pair plus a default {@code VkImageView}
 * that covers all mip levels. Resources are freed deterministically when {@link #close()}
 * is called; the {@code closeAction} supplied at construction time performs the actual
 * Vulkan destruction so that the {@link VulkanBackend.NativeSpine} can manage lifetime
 * tracking safely.
 *
 * <p>Thread safety: {@link #close()} is synchronized and idempotent. All other state
 * is set once at construction and is effectively immutable.
 */
public class VulkanTexture implements VulkanicTexture {

    private final long vkImageHandle;
    private final long vkMemoryHandle;
    /**
     * Default {@code VkImageView} covering all mip levels. Stored here for
     * convenience so that render-pass attachment binding code can use the texture
     * directly without requiring a separately created view object.
     */
    private final long vkDefaultViewHandle;
    private final int usage;
    private final VulkanicTextureFormat format;
    private final int width;
    private final int height;
    private final int depthOrLayers;
    private final int mipLevels;
    private final String label;
    private final Runnable closeAction;

    private volatile boolean closed;

    public VulkanTexture(long vkImageHandle,
                  long vkMemoryHandle,
                  long vkDefaultViewHandle,
                  int usage,
                  VulkanicTextureFormat format,
                  int width,
                  int height,
                  int depthOrLayers,
                  int mipLevels,
                  String label,
                  Runnable closeAction) {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("Texture dimensions must be >= 1, got " + width + "x" + height);
        }
        if (mipLevels < 1) {
            throw new IllegalArgumentException("mipLevels must be >= 1, got " + mipLevels);
        }
        if (depthOrLayers < 1) {
            throw new IllegalArgumentException("depthOrLayers must be >= 1, got " + depthOrLayers);
        }
        this.vkImageHandle = vkImageHandle;
        this.vkMemoryHandle = vkMemoryHandle;
        this.vkDefaultViewHandle = vkDefaultViewHandle;
        this.usage = usage;
        this.format = Objects.requireNonNull(format, "format must not be null");
        this.width = width;
        this.height = height;
        this.depthOrLayers = depthOrLayers;
        this.mipLevels = mipLevels;
        this.label = label != null ? label : "VulkanTexture-0x" + Long.toHexString(vkImageHandle);
        this.closeAction = Objects.requireNonNull(closeAction, "closeAction must not be null");
    }

    /** Returns the native {@code VkImage} handle. */
    long getVkImageHandle() {
        return vkImageHandle;
    }

    /** Returns the {@code VkDeviceMemory} handle backing this image. */
    long getVkMemoryHandle() {
        return vkMemoryHandle;
    }

    /**
     * Returns the default {@code VkImageView} handle covering all mip levels.
     *
     * <p>This view is destroyed along with the texture when {@link #close()} is called.
     * Code that needs a view with a restricted mip range should create a separate
     * {@link VulkanTextureView} via {@code VulkanBackend.createManagedTextureView(...)}.
     */
    long getVkDefaultViewHandle() {
        return vkDefaultViewHandle;
    }

    @Override
    public int getWidth(int mipLevel) {
        return Math.max(1, width >> mipLevel);
    }

    @Override
    public int getHeight(int mipLevel) {
        return Math.max(1, height >> mipLevel);
    }

    @Override
    public int getMipLevels() {
        return mipLevels;
    }

    @Override
    public int getDepthOrLayers() {
        return depthOrLayers;
    }

    @Override
    public VulkanicTextureFormat getVulkanicFormat() {
        return format;
    }

    @Override
    public int usage() {
        return usage;
    }

    @Override
    public String getLabel() {
        return label;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    /**
     * Frees the underlying {@code VkImage}, {@code VkDeviceMemory}, and default
     * {@code VkImageView}. Idempotent — subsequent calls are no-ops.
     *
     * <p><strong>Does NOT close any {@link VulkanTextureView} objects that were created
     * via {@code createManagedTextureView}.</strong> Callers must close views before
     * closing the parent texture to avoid dangling VkImageView references.
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
        return "VulkanTexture{"
            + "label='" + label + '\''
            + ", vkImage=0x" + Long.toHexString(vkImageHandle)
            + ", " + width + "x" + height
            + ", format=" + format
            + ", mips=" + mipLevels
            + ", closed=" + closed
            + '}';
    }
}
