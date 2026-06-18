package net.vulkanic.backends.vulkan;

import java.util.List;

record VulkanRenderPassKey(
    List<VulkanRenderPassKey.Attachment> colorAttachments,
    Attachment depthAttachment,
    boolean feedbackLoop,
    boolean swapchainPresentCompatible
) {
    VulkanRenderPassKey {
        colorAttachments = List.copyOf(colorAttachments);
        if (colorAttachments.isEmpty()) {
            throw new IllegalArgumentException("Render pass key requires at least one color attachment");
        }
        if (feedbackLoop && swapchainPresentCompatible) {
            throw new IllegalArgumentException("A render pass key cannot be both feedback-loop and swapchain-present compatible");
        }
    }

    static VulkanRenderPassKey framebuffer(List<Attachment> colorAttachments, Attachment depthAttachment, boolean feedbackLoop) {
        return new VulkanRenderPassKey(colorAttachments, depthAttachment, feedbackLoop, false);
    }

    static VulkanRenderPassKey pipelineCompatible(
        List<Integer> colorFormats,
        boolean includeDepth,
        int depthFormat,
        int colorLayout,
        int depthLayout,
        boolean feedbackLoop,
        boolean swapchainPresentCompatible
    ) {
        List<Attachment> colors = colorFormats.stream()
            .map(format -> new Attachment(format, -1, -1, colorLayout, colorLayout, colorLayout))
            .toList();
        Attachment depth = includeDepth
            ? new Attachment(depthFormat, -1, -1, depthLayout, depthLayout, depthLayout)
            : null;
        return new VulkanRenderPassKey(colors, depth, feedbackLoop, swapchainPresentCompatible);
    }

    record Attachment(int format, int loadOp, int storeOp, int initialLayout, int finalLayout, int subpassLayout) {
    }
}
