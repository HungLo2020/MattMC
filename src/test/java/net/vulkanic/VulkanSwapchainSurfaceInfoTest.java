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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies backend-neutral Vulkan surface/swapchain diagnostics and lifecycle hooks.
 */
public class VulkanSwapchainSurfaceInfoTest {

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
    public void testOpenGLBackendReportsUnavailableVulkanSwapchainSurfaceInfo() {
        VulkanicAPI.initialize(GraphicsBackendType.OPENGL);

        VulkanSwapchainSurfaceInfo info = VulkanicAPI.getVulkanSwapchainSurfaceInfo();
        assertNotNull(info);
        assertEquals(GraphicsBackendType.OPENGL, info.getBackendType());
        assertFalse(info.isNativeVulkanReady());
        assertFalse(info.isAvailable());
        assertEquals(0L, info.getSurfaceHandle());
        assertEquals(0L, info.getSwapchainHandle());
        assertEquals(0, info.getSwapchainImageCount());
        assertEquals(0, info.getSwapchainWidth());
        assertEquals(0, info.getSwapchainHeight());
        assertTrue(info.getStatus().contains("OpenGL backend"));
    }

    @Test
    public void testVulkanBackendSwapchainSnapshotIsSafeAndInformative() {
        VulkanicAPI.initialize(GraphicsBackendType.VULKAN);

        VulkanSwapchainSurfaceInfo info = assertDoesNotThrow(VulkanicAPI::getVulkanSwapchainSurfaceInfo,
            "Swapchain diagnostics query should not throw even when native Vulkan is unavailable");
        assertEquals(GraphicsBackendType.VULKAN, info.getBackendType());

        if (info.isAvailable()) {
            assertTrue(info.isNativeVulkanReady());
            assertTrue(info.getSurfaceHandle() != 0L);
            assertTrue(info.getSwapchainHandle() != 0L);
            assertTrue(info.getSwapchainImageCount() > 0);
            assertTrue(info.getSwapchainWidth() > 0);
            assertTrue(info.getSwapchainHeight() > 0);
            assertTrue(info.getStatus().contains("available"));
        } else {
            assertFalse(info.isNativeVulkanReady());
            assertEquals(0L, info.getSurfaceHandle());
            assertEquals(0L, info.getSwapchainHandle());
            assertEquals(0, info.getSwapchainImageCount());
            assertTrue(info.getStatus().contains("unavailable") || info.getStatus().contains("failed"));
        }
    }

    @Test
    public void testDescribeSwapchainSurfaceInfoIncludesSummaryFields() {
        VulkanicAPI.initialize(GraphicsBackendType.OPENGL);
        String description = VulkanicAPI.describeVulkanSwapchainSurfaceInfo();

        assertTrue(description.contains("Vulkan swapchain/surface info"));
        assertTrue(description.contains("backendType="));
        assertTrue(description.contains("swapchainAvailable="));
        assertTrue(description.contains("surfaceHandle="));
    }

    @Test
    public void testRecreateSwapchainOpenGLNoOpAndVulkanFailFastWhenUnavailable() throws Exception {
        VulkanicAPI.initialize(GraphicsBackendType.OPENGL);
        assertDoesNotThrow(VulkanicAPI::recreateVulkanSwapchain,
            "OpenGL backend should no-op swapchain recreation requests");
        assertFalse(VulkanicAPI.recreateVulkanSwapchainIfNeeded(),
            "OpenGL backend should report no conditional swapchain recreation work");

        resetBackendState();
        VulkanicAPI.initialize(GraphicsBackendType.VULKAN);
        VulkanSwapchainSurfaceInfo info = VulkanicAPI.getVulkanSwapchainSurfaceInfo();

        if (info.isAvailable()) {
            assertDoesNotThrow(VulkanicAPI::recreateVulkanSwapchain,
                "Native-ready Vulkan backend should recreate swapchain without falling back to OpenGL");
            assertDoesNotThrow(VulkanicAPI::recreateVulkanSwapchainIfNeeded,
                "Native-ready Vulkan backend should support conditional swapchain recreation checks");
        } else {
            IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                VulkanicAPI::recreateVulkanSwapchain,
                "Vulkan-selected backend should fail hard when swapchain recreation is not natively ready");
            assertTrue(exception.getMessage().contains("Readiness report:"));

            IllegalStateException conditionalException = assertThrows(
                IllegalStateException.class,
                VulkanicAPI::recreateVulkanSwapchainIfNeeded,
                "Conditional swapchain recreation should also fail hard when native Vulkan is unavailable");
            assertTrue(conditionalException.getMessage().contains("Readiness report:"));
        }
    }

    @Test
    public void testSwapchainSourceTracksImageViewsAcrossLifecycle() throws Exception {
        String source = Files.readString(PROJECT_ROOT
            .resolve("src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java"));

        assertTrue(source.contains("vkGetSwapchainImagesKHR(list)"),
            "Swapchain lifecycle should enumerate swapchain images during creation/recreation");
        assertTrue(source.contains("vkCreateImageView(swapchain)"),
            "Swapchain lifecycle should materialize VkImageView handles for swapchain images");
        assertTrue(source.contains("swapchainImageViewHandles"),
            "Swapchain lifecycle should track swapchain image view handles for cleanup/recreation");
        assertTrue(source.contains("destroyTrackedSwapchainImageViews()"),
            "Swapchain lifecycle should destroy tracked image views during teardown");
        assertTrue(source.contains("outside tracked swapchain image/view range"),
            "Frame acquisition should validate acquired image indices against tracked swapchain resources");
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
