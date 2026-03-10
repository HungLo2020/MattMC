package net.vulkanic.backends.vulkan;

import net.vulkanic.CommandContext;
import net.vulkanic.GraphicsBackendType;
import net.vulkanic.PipelineDescriptor;
import net.vulkanic.PipelineHandle;
import net.vulkanic.backends.opengl.OpenGLBackend;

public class VulkanBackend extends OpenGLBackend {
    @Override
    public GraphicsBackendType getBackendType() {
        return GraphicsBackendType.VULKAN;
    }

    @Override
    public boolean isNativeVulkanReady() {
        return false;
    }


    private void ensureNativeReady(String operation) {
        if (isNativeVulkanReady()) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Vulkan backend cannot perform '").append(operation).append("' because native Vulkan execution is not ready.\n");
        sb.append("isNativeVulkanReady()=").append(isNativeVulkanReady()).append('\n');
        sb.append("Note: Backend capability profiling is not available in this build.\n");
        sb.append("Suggested actions:\n");
        sb.append(" - Ensure the Vulkan runtime & drivers are available on this system.\n");
        sb.append(" - Ensure the Vulkan backend is correctly initialized before calling Vulkan APIs.\n");
        sb.append(" - If OpenGL is desired, select/initialize the OpenGL backend instead.\n");

        throw new IllegalStateException(sb.toString());
    }

    @Override
    public PipelineHandle createPipeline(PipelineDescriptor descriptor) {
        ensureNativeReady("createPipeline");
        throw new UnsupportedOperationException("Vulkan-native pipeline creation is not implemented yet.");
    }

    @Override
    public CommandContext beginCommandBuffer() {
        ensureNativeReady("beginCommandBuffer");
        throw new UnsupportedOperationException("Vulkan-native command buffer lifecycle is not implemented yet.");
    }

    @Override
    public void submitCommandBuffer(CommandContext ctx) {
        ensureNativeReady("submitCommandBuffer");
        throw new UnsupportedOperationException("Vulkan-native command buffer submission is not implemented yet.");
    }

    @Override
    public net.vulkanic.VulkanicRenderPass beginRenderPass(CommandContext ctx,
            java.util.function.Supplier<String> label,
            net.vulkanic.VulkanicTextureView colorTarget, java.util.OptionalInt clearColor) {
        ensureNativeReady("beginRenderPass");
        throw new UnsupportedOperationException("Vulkan-native render pass lifecycle is not implemented yet.");
    }

    @Override
    public net.vulkanic.VulkanicRenderPass beginRenderPass(CommandContext ctx,
            java.util.function.Supplier<String> label,
            net.vulkanic.VulkanicTextureView colorTarget, java.util.OptionalInt clearColor,
            @org.jetbrains.annotations.Nullable net.vulkanic.VulkanicTextureView depthTarget,
            java.util.OptionalDouble clearDepth) {
        ensureNativeReady("beginRenderPass");
        throw new UnsupportedOperationException("Vulkan-native render pass lifecycle is not implemented yet.");
    }

    public boolean isFallbackMode() {
        return !isNativeVulkanReady();
    }
}
