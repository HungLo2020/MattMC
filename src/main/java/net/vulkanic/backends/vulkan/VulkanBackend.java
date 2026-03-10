package net.vulkanic.backends.vulkan;

import net.vulkanic.CommandContext;
import net.vulkanic.GraphicsBackendType;
import net.vulkanic.PipelineDescriptor;
import net.vulkanic.PipelineHandle;
import net.vulkanic.VulkanReadinessReport;
import net.vulkanic.backends.opengl.OpenGLBackend;
import org.lwjgl.glfw.GLFWVulkan;

import java.util.ArrayList;
import java.util.List;

public class VulkanBackend extends OpenGLBackend {

    private volatile VulkanReadinessReport cachedReadinessReport;

    @Override
    public GraphicsBackendType getBackendType() {
        return GraphicsBackendType.VULKAN;
    }

    @Override
    public boolean isNativeVulkanReady() {
        return false;
    }

    /**
     * Returns the latest cached readiness report (probing once lazily).
     */
    public VulkanReadinessReport getReadinessReport() {
        VulkanReadinessReport report = cachedReadinessReport;
        if (report == null) {
            report = probeReadiness();
            cachedReadinessReport = report;
        }
        return report;
    }

    /**
     * Forces a fresh runtime probe and updates the cached readiness report.
     */
    public VulkanReadinessReport refreshReadinessReport() {
        VulkanReadinessReport report = probeReadiness();
        cachedReadinessReport = report;
        return report;
    }

    private static String compactThrowable(Throwable throwable) {
        return throwable.getClass().getSimpleName() + ": "
            + (throwable.getMessage() == null ? "<no-message>" : throwable.getMessage());
    }

    private VulkanReadinessReport probeReadiness() {
        boolean lwjglBindingsPresent = false;
        String bindingsStatus;
        try {
            Class.forName("org.lwjgl.vulkan.VK10", false, VulkanBackend.class.getClassLoader());
            lwjglBindingsPresent = true;
            bindingsStatus = "available";
        } catch (Throwable throwable) {
            bindingsStatus = "unavailable (" + compactThrowable(throwable) + ")";
        }

        boolean glfwVulkanSupported = false;
        String glfwProbeStatus;
        if (lwjglBindingsPresent) {
            try {
                glfwVulkanSupported = GLFWVulkan.glfwVulkanSupported();
                glfwProbeStatus = glfwVulkanSupported ? "supported" : "unsupported";
            } catch (Throwable throwable) {
                glfwProbeStatus = "probe failed (" + compactThrowable(throwable) + ")";
            }
        } else {
            glfwProbeStatus = "skipped (LWJGL Vulkan bindings unavailable)";
        }

        List<String> blockers = new ArrayList<>();
        blockers.add("Native Vulkan command/pipeline implementation has not been integrated yet.");

        if (!lwjglBindingsPresent) {
            blockers.add("LWJGL Vulkan bindings are not available: " + bindingsStatus + ".");
        }

        if (!glfwVulkanSupported) {
            blockers.add("GLFW Vulkan support probe did not pass: " + glfwProbeStatus + ".");
        }

        return new VulkanReadinessReport(
            GraphicsBackendType.VULKAN,
            true,
            isNativeVulkanReady(),
            lwjglBindingsPresent,
            glfwVulkanSupported,
            glfwProbeStatus,
            blockers
        );
    }


    private void ensureNativeReady(String operation) {
        if (isNativeVulkanReady()) {
            return;
        }

        VulkanReadinessReport report = getReadinessReport();

        StringBuilder sb = new StringBuilder();
        sb.append("Vulkan backend cannot perform '").append(operation).append("' because native Vulkan execution is not ready.\n");
        sb.append("isNativeVulkanReady()=").append(isNativeVulkanReady()).append('\n');
        sb.append("Readiness report: ").append(report.summaryLine()).append('\n');
        sb.append(report.toMultilineString());
        sb.append("Suggested actions:\n");
        sb.append(" - Ensure the Vulkan runtime & drivers are available on this system and that GLFW reports Vulkan support.\n");
        sb.append(" - Ensure LWJGL Vulkan bindings are present in the runtime classpath.\n");
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
