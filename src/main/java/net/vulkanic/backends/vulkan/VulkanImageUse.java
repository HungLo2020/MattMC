package net.vulkanic.backends.vulkan;

import org.lwjgl.vulkan.EXTAttachmentFeedbackLoopLayout;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VK10;

/**
 * Logical Vulkan image usages that Vulkanic needs to reason about across the
 * legacy OpenGL compatibility path and native render-pass path.
 */
enum VulkanImageUse {
    UNDEFINED(VK10.VK_IMAGE_LAYOUT_UNDEFINED),
    COLOR_ATTACHMENT(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL),
    DEPTH_ATTACHMENT(VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL),
    SAMPLED_COLOR(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL),
    SAMPLED_DEPTH(VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL),
    TRANSFER_SOURCE(VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL),
    TRANSFER_DESTINATION(VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL),
    GENERAL(VK10.VK_IMAGE_LAYOUT_GENERAL),
    FEEDBACK_LOOP(EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT),
    PRESENT(KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);

    private final int vkLayout;

    VulkanImageUse(int vkLayout) {
        this.vkLayout = vkLayout;
    }

    int vkLayout() {
        return this.vkLayout;
    }

    static VulkanImageUse fromVkLayout(int layout) {
        for (VulkanImageUse use : values()) {
            if (use.vkLayout == layout) {
                return use;
            }
        }

        return GENERAL;
    }
}
