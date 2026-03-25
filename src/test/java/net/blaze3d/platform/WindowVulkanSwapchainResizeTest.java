package net.blaze3d.platform;

import net.vulkanic.GraphicsBackendType;
import net.vulkanic.VulkanSwapchainSurfaceInfo;
import net.vulkanic.VulkanicAPI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowVulkanSwapchainResizeTest {

    @BeforeEach
    void beforeEach() throws Exception {
        resetBackendState();
    }

    @AfterEach
    void afterEach() throws Exception {
        resetBackendState();
    }

    @Test
    void testResizeHookNoOpsWhenBackendUninitialized() throws Exception {
        assertFalse(Window.handleVulkanSwapchainFramebufferResize(1280, 720),
            "Resize hook should no-op before Vulkanic backend initialization");
        assertNull(getBackendFieldValue(),
            "Resize hook must not auto-initialize Vulkanic backend");
    }

    @Test
    void testResizeHookOpenGLNoOp() {
        VulkanicAPI.initialize(GraphicsBackendType.OPENGL);

        assertFalse(Window.handleVulkanSwapchainFramebufferResize(1280, 720),
            "OpenGL backend should no-op swapchain recreation on resize");
    }

    @Test
    void testResizeHookVulkanFailHardOrExecutesWhenReady() throws Exception {
        VulkanicAPI.initialize(GraphicsBackendType.VULKAN);
        VulkanSwapchainSurfaceInfo info = VulkanicAPI.getVulkanSwapchainSurfaceInfo();

        if (info.isAvailable()) {
            assertDoesNotThrow(() -> Window.handleVulkanSwapchainFramebufferResize(1280, 720),
                "Native-ready Vulkan backend should service resize recreation without fallback");
        } else {
            IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> Window.handleVulkanSwapchainFramebufferResize(1280, 720),
                "Vulkan-selected backend should fail hard when resize-triggered swapchain recreation is unavailable");
            assertTrue(exception.getMessage().contains("Readiness report:"));
        }
    }

    @Test
    void testResizeHookSkipsMinimizedDimensionsInVulkanMode() {
        VulkanicAPI.initialize(GraphicsBackendType.VULKAN);

        assertFalse(Window.handleVulkanSwapchainFramebufferResize(0, 720),
            "Width=0 should be treated as minimized and skip recreation");
        assertFalse(Window.handleVulkanSwapchainFramebufferResize(1280, 0),
            "Height=0 should be treated as minimized and skip recreation");
    }

    @Test
    void testNoApiWindowHintNotRequestedWhenBackendUninitialized() throws Exception {
        assertFalse(Window.shouldRequestNoApiWindowClientForVulkanBackend(),
            "Window hint selection must not request NO_API when backend is uninitialized");
        assertNull(getBackendFieldValue(),
            "Window hint selection must not auto-initialize Vulkanic backend");
    }

    @Test
    void testNoApiWindowHintRequestedWhenVulkanBackendSelected() {
        VulkanicAPI.initialize(GraphicsBackendType.VULKAN);

        assertTrue(Window.shouldRequestNoApiWindowClientForVulkanBackend(),
            "Window hint selection should request NO_API when Vulkan backend is selected");
    }

    @Test
    void testNoApiWindowHintNotRequestedWhenOpenGLBackendSelected() {
        VulkanicAPI.initialize(GraphicsBackendType.OPENGL);

        assertFalse(Window.shouldRequestNoApiWindowClientForVulkanBackend(),
            "Window hint selection should keep OpenGL client API when OpenGL backend is selected");
    }

    private static Object getBackendFieldValue() throws Exception {
        Field backendField = VulkanicAPI.class.getDeclaredField("backend");
        backendField.setAccessible(true);
        return backendField.get(null);
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
