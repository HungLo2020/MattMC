package net.vulkanic.backends.vulkan;

record VulkanImageSubresourceKey(int mipLevel, int layer) {
    VulkanImageSubresourceKey {
        if (mipLevel < 0) {
            throw new IllegalArgumentException("mipLevel must be >= 0");
        }
        if (layer < 0) {
            throw new IllegalArgumentException("layer must be >= 0");
        }
    }
}
