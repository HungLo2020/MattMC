package net.vulkanic.backends.vulkan;

import org.lwjgl.vulkan.VK10;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class VulkanImageState {
    private final int textureId;
    private final Map<VulkanImageSubresourceKey, Integer> layouts = new ConcurrentHashMap<>();
    private volatile long imageHandle;
    private volatile int aspectMask;
    private volatile int mipLevels;
    private volatile int layerCount;
    private volatile boolean feedbackLoopCapable;

    VulkanImageState(int textureId) {
        if (textureId <= 0) {
            throw new IllegalArgumentException("textureId must be > 0");
        }

        this.textureId = textureId;
        this.aspectMask = VK10.VK_IMAGE_ASPECT_COLOR_BIT;
        this.mipLevels = 1;
        this.layerCount = 1;
    }

    int textureId() {
        return this.textureId;
    }

    long imageHandle() {
        return this.imageHandle;
    }

    int aspectMask() {
        return this.aspectMask;
    }

    int mipLevels() {
        return this.mipLevels;
    }

    int layerCount() {
        return this.layerCount;
    }

    boolean feedbackLoopCapable() {
        return this.feedbackLoopCapable;
    }

    void configure(long imageHandle, int aspectMask, int mipLevels, int layerCount, boolean feedbackLoopCapable, int initialLayout) {
        this.imageHandle = imageHandle;
        this.aspectMask = aspectMask;
        this.mipLevels = Math.max(1, mipLevels);
        this.layerCount = Math.max(1, layerCount);
        this.feedbackLoopCapable = feedbackLoopCapable;
        this.layouts.clear();
        for (int mip = 0; mip < this.mipLevels; mip++) {
            recordLayout(mip, initialLayout);
        }
    }

    int layoutFor(int mipLevel) {
        return this.layouts.getOrDefault(new VulkanImageSubresourceKey(mipLevel, 0), VK10.VK_IMAGE_LAYOUT_UNDEFINED);
    }

    void recordLayout(int mipLevel, int layout) {
        this.layouts.put(new VulkanImageSubresourceKey(mipLevel, 0), layout);
    }

    void clear() {
        this.imageHandle = VK10.VK_NULL_HANDLE;
        this.layouts.clear();
        this.mipLevels = 1;
        this.layerCount = 1;
        this.feedbackLoopCapable = false;
    }
}
