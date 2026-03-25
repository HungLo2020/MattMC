package net.vulkanic;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies backend-neutral Vulkan execution-context diagnostics.
 */
public class VulkanExecutionContextInfoTest {

    @BeforeEach
    public void beforeEach() throws Exception {
        resetBackendState();
    }

    @AfterEach
    public void afterEach() throws Exception {
        resetBackendState();
    }

    @Test
    public void testOpenGLBackendReportsUnavailableVulkanExecutionContext() {
        VulkanicAPI.initialize(GraphicsBackendType.OPENGL);

        VulkanExecutionContextInfo info = VulkanicAPI.getVulkanExecutionContextInfo();
        assertNotNull(info);
        assertEquals(GraphicsBackendType.OPENGL, info.getBackendType());
        assertFalse(info.isNativeVulkanReady());
        assertFalse(info.isAvailable());
        assertEquals(0L, info.getLogicalDeviceHandle());
        assertEquals(0L, info.getGraphicsQueueHandle());
        assertEquals(0L, info.getCommandPoolHandle());
        assertEquals(0L, info.getCommandBufferHandle());
        assertTrue(info.getStatus().contains("OpenGL backend"));
    }

    @Test
    public void testVulkanBackendExecutionContextSnapshotIsSafeAndInformative() {
        VulkanicAPI.initialize(GraphicsBackendType.VULKAN);

        VulkanExecutionContextInfo info = assertDoesNotThrow(VulkanicAPI::getVulkanExecutionContextInfo,
            "Execution-context query should not throw even when native Vulkan is unavailable");
        assertEquals(GraphicsBackendType.VULKAN, info.getBackendType());

        if (info.isAvailable()) {
            assertTrue(info.isNativeVulkanReady());
            assertTrue(info.getLogicalDeviceHandle() != 0L);
            assertTrue(info.getGraphicsQueueHandle() != 0L);
            assertTrue(info.getCommandPoolHandle() != 0L);
            assertTrue(info.getCommandBufferHandle() != 0L);
            assertTrue(info.getGraphicsQueueFamilyIndex() >= 0);
            assertFalse(info.getCommandContextDebugName().isBlank());
            assertTrue(info.getStatus().contains("available"));
        } else {
            assertFalse(info.isNativeVulkanReady());
            assertEquals(0L, info.getLogicalDeviceHandle());
            assertEquals(0L, info.getGraphicsQueueHandle());
            assertEquals(0L, info.getCommandPoolHandle());
            assertTrue(info.getGraphicsQueueFamilyIndex() < 0);
            assertFalse(info.getStatus().isBlank());
        }
    }

    @Test
    public void testExecutionContextDescriptionIncludesSummaryFields() {
        VulkanicAPI.initialize(GraphicsBackendType.OPENGL);
        String description = VulkanicAPI.describeVulkanExecutionContextInfo();

        assertTrue(description.contains("Vulkan execution context info"));
        assertTrue(description.contains("backendType="));
        assertTrue(description.contains("nativeVulkanReady="));
        assertTrue(description.contains("available="));
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
