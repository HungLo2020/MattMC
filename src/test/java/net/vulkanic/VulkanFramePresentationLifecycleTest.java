package net.vulkanic;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VulkanFramePresentationLifecycleTest {

    private static final Path PROJECT_ROOT = Paths.get(System.getProperty("user.dir"));

    private static String readSource(Path path) throws IOException {
        return Files.readString(path).replace("\r\n", "\n").replace('\r', '\n');
    }

    @BeforeEach
    public void beforeEach() throws Exception {
        resetBackendState();
    }

    @AfterEach
    public void afterEach() throws Exception {
        resetBackendState();
    }

    @Test
    public void testOpenGLFrameLifecycleMethodsRemainNoOp() {
        VulkanicAPI.initialize(GraphicsBackendType.OPENGL);

        int imageIndex = assertDoesNotThrow(VulkanicAPI::beginFrame,
            "OpenGL backend should keep frame lifecycle begin as a safe no-op");
        assertEquals(-1, imageIndex,
            "OpenGL beginFrame should return sentinel image index -1");

        assertDoesNotThrow(VulkanicAPI::endFrame,
            "OpenGL backend should keep frame lifecycle end as a safe no-op");
    }

    @Test
    public void testVulkanFrameLifecycleFailsHardOrExecutesWhenReady() {
        VulkanicAPI.initialize(GraphicsBackendType.VULKAN);
        VulkanNativeInitializationInfo info = VulkanicAPI.initializeNativeVulkanRuntime();

        if (!info.isNativeVulkanReady()) {
            IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                VulkanicAPI::beginFrame,
                "Vulkan-selected beginFrame should fail hard when native runtime is unavailable"
            );
            String message = failure.getMessage();
            assertNotNull(message);
            assertTrue(message.contains("Readiness report:"));
            return;
        }

        int imageIndex = assertDoesNotThrow(VulkanicAPI::beginFrame,
            "Native-ready Vulkan beginFrame should acquire a swapchain image index");
        assertTrue(imageIndex >= 0, "Acquired swapchain image index should be non-negative");

        assertDoesNotThrow(VulkanicAPI::endFrame,
            "Native-ready Vulkan endFrame should present the acquired swapchain image");
    }

    @Test
    public void testFramePresentationSourceWiring() throws Exception {
        String vulkanBackendSource = readSource(PROJECT_ROOT
            .resolve("src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java"));
        String renderSystemSource = readSource(PROJECT_ROOT
            .resolve("src/main/java/net/blaze3d/systems/RenderSystem.java"));
        String glCommandEncoderSource = readSource(PROJECT_ROOT
            .resolve("src/main/java/net/blaze3d/opengl/GlCommandEncoder.java"));
        String openGLBackendSource = readSource(PROJECT_ROOT
            .resolve("src/main/java/net/vulkanic/backends/opengl/OpenGLBackend.java"));

        int presentStart = openGLBackendSource.indexOf("public void presentTextureToScreen(CommandContext ctx, GpuTextureView textureView)");
        int presentEnd = openGLBackendSource.indexOf("private int ensurePresentScratchReadFramebuffer()", presentStart);
        assertTrue(presentStart >= 0 && presentEnd > presentStart,
            "OpenGL backend should expose presentTextureToScreen implementation and scratch framebuffer helper");
        String openGLPresentSource = openGLBackendSource.substring(presentStart, presentEnd);

        assertTrue(vulkanBackendSource.contains("vkAcquireNextImageKHR"),
            "Vulkan backend should acquire swapchain images via vkAcquireNextImageKHR");
        assertTrue(vulkanBackendSource.contains("vkQueuePresentKHR"),
            "Vulkan backend should present swapchain images via vkQueuePresentKHR");
        assertTrue(vulkanBackendSource.contains("public int beginFrame()"),
            "Vulkan backend should expose frame begin lifecycle entrypoint");
        assertTrue(vulkanBackendSource.contains("public void endFrame()"),
            "Vulkan backend should expose frame end lifecycle entrypoint");
        assertTrue(vulkanBackendSource.contains("composePendingPresentTexture("),
            "Vulkan backend should compose queued present textures into acquired swapchain images during endFrame");
        assertTrue(vulkanBackendSource.contains("frameCommandBuffers"),
            "Vulkan backend should dedicate separate command buffers to swapchain frame submission");
        assertTrue(vulkanBackendSource.contains("ensureCurrentFrameCommandBufferRecording"),
            "Vulkan backend should begin frame-presentation command recording on a dedicated frame buffer path");
        assertTrue(vulkanBackendSource.contains("createSwapchainRenderFinishedSemaphores(imageResources.imageHandles.size())"),
            "Vulkan backend should create render-finished semaphores from the swapchain image count");
        assertTrue(vulkanBackendSource.contains("swapchainRenderFinishedSemaphoresByImage[acquiredSwapchainImageIndex]"),
            "Vulkan backend should signal/present with a render-finished semaphore owned by the acquired swapchain image");
        assertTrue(vulkanBackendSource.contains("destroySwapchainRenderFinishedSemaphores()"),
            "Vulkan backend should destroy per-image render-finished semaphores with swapchain resources");
        assertFalse(vulkanBackendSource.contains("swapchainRenderFinishedSemaphores[currentFrameSyncIndex]"),
            "Vulkan backend must not reuse present wait semaphores by frame slot across different swapchain images");
        assertTrue(vulkanBackendSource.contains("vkGetPhysicalDeviceFeatures(device, features)"),
            "Vulkan backend should query core physical-device feature support before device creation");
        assertTrue(vulkanBackendSource.contains("enabledFeatures.fillModeNonSolid(true)"),
            "Vulkan backend should enable fillModeNonSolid when the selected device supports it");
        assertTrue(vulkanBackendSource.contains(".pEnabledFeatures(enabledFeatures)"),
            "Vulkan backend should pass requested core features to vkCreateDevice");
        assertTrue(vulkanBackendSource.contains("mode -> toVkPolygonMode(mode, portableState.location().toString())"),
            "Vulkan backend should map polygon mode with pipeline context for diagnostics");
        assertTrue(vulkanBackendSource.contains("fillModeNonSolidEnabled"),
            "Vulkan backend should track whether non-solid fill mode was actually enabled");
        assertTrue(vulkanBackendSource.contains("falling back to filled polygon mode for wireframe pipelines"),
            "Vulkan backend should avoid invalid VK_POLYGON_MODE_LINE when fillModeNonSolid is unavailable");
        assertTrue(vulkanBackendSource.contains("if (spine.isFrameInProgress())"),
            "Vulkan beginCommandBuffer should route in-frame work to the frame command buffer lifecycle");
        assertTrue(vulkanBackendSource.contains("commandBufferHandle = spine.currentFrameCommandBufferHandle();"),
            "Vulkan beginCommandBuffer should reuse the frame command buffer during an active frame");
        assertTrue(vulkanBackendSource.contains("if (spine.isCurrentFrameCommandBufferHandle(commandBufferHandle))"),
            "Vulkan submitCommandBuffer should avoid force-submitting the active frame command buffer per render pass");
        assertTrue(vulkanBackendSource.contains("int submitSlot = immediateSubmitSlotForKnownCommandBuffer(commandBufferHandle);")
                && vulkanBackendSource.contains("if (submitSlot != recordingImmediateSubmitSlot || submitSlot < 0)")
                && vulkanBackendSource.contains("VkCommandBuffer submitCommandBuffer = immediateCommandBuffers[submitSlot];")
                && vulkanBackendSource.contains("commandBuffers.put(0, submitCommandBuffer.address());"),
            "Vulkan immediate submits must submit and mark the same recording ring slot to avoid fence/command-buffer mismatches");
        assertTrue(vulkanBackendSource.contains("shouldSynchronizeImmediateSubmitCompletion()")
                && vulkanBackendSource.contains("return true;")
                && vulkanBackendSource.contains("awaitDeferredImmediateSubmitCompletion();")
                && vulkanBackendSource.contains("waitForAllSwapchainFrameFences();")
                && vulkanBackendSource.contains("vkWaitForFences(immediateSubmitComplete["),
            "Vulkan immediate submits should keep conservative completion/lifetime rules until deferred ring reuse is proven safe");
        assertTrue(
            vulkanBackendSource.contains("vkCmdBlitImage") || vulkanBackendSource.contains("vkCmdCopyImage"),
            "Vulkan backend should blit/copy queued present textures into swapchain images before queue present"
        );
        assertTrue(vulkanBackendSource.contains("int composeSourceLayout = requiresShaderCompose\n                ? VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL\n                : VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;"),
            "Vulkan shader present compose must sample the source image from SHADER_READ_ONLY_OPTIMAL to match descriptor layout");

        assertTrue(renderSystemSource.contains("VulkanicAPI.beginFrame()"),
            "RenderSystem flip path should begin Vulkan frame lifecycle when Vulkan routing is selected");
        assertTrue(renderSystemSource.contains("VulkanicAPI.endFrame()"),
            "RenderSystem flip path should end Vulkan frame lifecycle when Vulkan routing is selected");
        assertTrue(renderSystemSource.contains("GLFW.glfwSwapBuffers(window.handle())"),
            "RenderSystem must preserve OpenGL swap-buffers path");
        assertTrue(glCommandEncoderSource.contains("VulkanicCoreAPI.presentTextureToScreen(ctx, gpuTextureView);"),
            "GlCommandEncoder presentTexture should route through the backend-owned present seam");
        assertTrue(openGLPresentSource.contains("ensurePresentScratchReadFramebuffer()"),
            "OpenGL present path should validate/recreate scratch framebuffer objects before blitting");
        assertTrue(openGLPresentSource.contains("GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER)"),
            "OpenGL present path should verify read framebuffer completeness before blit");
        assertFalse(openGLPresentSource.contains("GL45.glNamedFramebufferTexture"),
            "OpenGL present path should avoid hard dependency on GL45 named-framebuffer attachment calls");
        assertFalse(openGLPresentSource.contains("GL45.glBlitNamedFramebuffer"),
            "OpenGL present path should avoid hard dependency on GL45 named-framebuffer blit calls");
    }

    private static void resetBackendState() throws Exception {
        Field backendField = VulkanicAPI.class.getDeclaredField("backend");
        backendField.setAccessible(true);
        backendField.set(null, null);

        Field rawVulkanBackendField = VulkanicAPI.class.getDeclaredField("rawVulkanBackend");
        rawVulkanBackendField.setAccessible(true);
        rawVulkanBackendField.set(null, null);
    }
}
