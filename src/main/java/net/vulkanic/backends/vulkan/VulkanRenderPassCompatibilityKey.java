package net.vulkanic.backends.vulkan;

import org.lwjgl.vulkan.VK10;

import java.util.List;

record VulkanRenderPassCompatibilityKey(
    DependencyProfile dependencyProfile,
    List<Integer> colorFormats,
    int depthFormat,
    boolean feedbackLoop
) {
    VulkanRenderPassCompatibilityKey {
        if (dependencyProfile == null) {
            throw new IllegalArgumentException("dependencyProfile must not be null");
        }
        colorFormats = List.copyOf(colorFormats);
        for (int colorFormat : colorFormats) {
            if (colorFormat == VK10.VK_FORMAT_UNDEFINED) {
                throw new IllegalArgumentException("Color attachment formats must be defined");
            }
        }
        if (dependencyProfile == DependencyProfile.SWAPCHAIN_PRESENT) {
            if (colorFormats.size() != 1) {
                throw new IllegalArgumentException("Swapchain-present render passes require exactly one color attachment");
            }
            if (depthFormat != VK10.VK_FORMAT_UNDEFINED) {
                throw new IllegalArgumentException("Swapchain-present render passes must not include depth");
            }
            if (feedbackLoop) {
                throw new IllegalArgumentException("Swapchain-present render passes must not use feedback-loop compatibility");
            }
        }
    }

    static VulkanRenderPassCompatibilityKey textureView(
        List<Integer> colorFormats,
        int depthFormat,
        boolean feedbackLoop
    ) {
        return new VulkanRenderPassCompatibilityKey(
            DependencyProfile.TEXTURE_VIEW,
            colorFormats,
            depthFormat,
            feedbackLoop
        );
    }

    static VulkanRenderPassCompatibilityKey framebuffer(
        List<Integer> colorFormats,
        int depthFormat,
        boolean feedbackLoop
    ) {
        return new VulkanRenderPassCompatibilityKey(
            DependencyProfile.FRAMEBUFFER,
            colorFormats,
            depthFormat,
            feedbackLoop
        );
    }

    static VulkanRenderPassCompatibilityKey swapchainPresent(int colorFormat) {
        return new VulkanRenderPassCompatibilityKey(
            DependencyProfile.SWAPCHAIN_PRESENT,
            List.of(colorFormat),
            VK10.VK_FORMAT_UNDEFINED,
            false
        );
    }

    int colorAttachmentCount() {
        return colorFormats.size();
    }

    boolean hasDepthAttachment() {
        return depthFormat != VK10.VK_FORMAT_UNDEFINED;
    }

    enum DependencyProfile {
        TEXTURE_VIEW,
        FRAMEBUFFER,
        SWAPCHAIN_PRESENT
    }
}
