package net.vulkanic;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VulkanFramePresentationLifecycleTest {

    private static final Path PROJECT_ROOT = Paths.get(System.getProperty("user.dir"));

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
        String vulkanBackendSource = Files.readString(PROJECT_ROOT
            .resolve("src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java"));
        String renderSystemSource = Files.readString(PROJECT_ROOT
            .resolve("src/main/java/net/blaze3d/systems/RenderSystem.java"));

        assertTrue(vulkanBackendSource.contains("vkAcquireNextImageKHR"),
            "Vulkan backend should acquire swapchain images via vkAcquireNextImageKHR");
        assertTrue(vulkanBackendSource.contains("vkQueuePresentKHR"),
            "Vulkan backend should present swapchain images via vkQueuePresentKHR");
        assertTrue(vulkanBackendSource.contains("public int beginFrame()"),
            "Vulkan backend should expose frame begin lifecycle entrypoint");
        assertTrue(vulkanBackendSource.contains("public void endFrame()"),
            "Vulkan backend should expose frame end lifecycle entrypoint");

        assertTrue(renderSystemSource.contains("VulkanicAPI.beginFrame()"),
            "RenderSystem flip path should begin Vulkan frame lifecycle when Vulkan routing is selected");
        assertTrue(renderSystemSource.contains("VulkanicAPI.endFrame()"),
            "RenderSystem flip path should end Vulkan frame lifecycle when Vulkan routing is selected");
        assertTrue(renderSystemSource.contains("GLFW.glfwSwapBuffers(window.handle())"),
            "RenderSystem must preserve OpenGL swap-buffers path");
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
