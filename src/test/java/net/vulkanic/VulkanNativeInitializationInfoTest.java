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
 * Verifies explicit native Vulkan runtime initialization diagnostics.
 */
public class VulkanNativeInitializationInfoTest {

    @BeforeEach
    public void beforeEach() throws Exception {
        resetBackendState();
    }

    @AfterEach
    public void afterEach() throws Exception {
        resetBackendState();
    }

    @Test
    public void testOpenGLBackendReturnsUnsupportedInitializationResult() {
        VulkanicAPI.initialize(GraphicsBackendType.OPENGL);

        VulkanNativeInitializationInfo info = VulkanicAPI.initializeNativeVulkanRuntime();
        assertNotNull(info);
        assertEquals(GraphicsBackendType.OPENGL, info.getBackendType());
        assertFalse(info.isInitializationAttempted());
        assertFalse(info.isNativeVulkanReady());
        assertFalse(info.isInitializationSuccessful());
        assertTrue(info.getStatus().contains("OpenGL backend"));
    }

    @Test
    public void testVulkanBackendInitializationAttemptProducesStructuredOutcome() {
        VulkanicAPI.initialize(GraphicsBackendType.VULKAN);

        VulkanNativeInitializationInfo info = assertDoesNotThrow(VulkanicAPI::initializeNativeVulkanRuntime,
            "Explicit Vulkan runtime initialization should return diagnostics instead of throwing");

        assertEquals(GraphicsBackendType.VULKAN, info.getBackendType());
        assertTrue(info.isInitializationAttempted());

        if (info.isInitializationSuccessful()) {
            assertTrue(info.isNativeVulkanReady());
            assertTrue(info.getStatus().contains("successfully"));
        } else {
            assertFalse(info.isNativeVulkanReady());
            assertTrue(
                info.getStatus().contains("failed")
                    || info.getStatus().contains("did not become ready"),
                "Failure path should include clear initialization status"
            );
        }

        assertFalse(info.getReadinessSummary().isBlank(),
            "Initialization result should include readiness summary details");
    }

    @Test
    public void testNativeInitializationDescriptionIncludesExpectedFields() {
        VulkanicAPI.initialize(GraphicsBackendType.OPENGL);

        String description = VulkanicAPI.describeNativeVulkanInitialization();
        assertTrue(description.contains("Vulkan native initialization info"));
        assertTrue(description.contains("backendType="));
        assertTrue(description.contains("initializationAttempted="));
        assertTrue(description.contains("initializationSuccessful="));
    }

    private static void resetBackendState() throws Exception {
        Field backendField = VulkanicAPI.class.getDeclaredField("backend");
        backendField.setAccessible(true);
        backendField.set(null, null);

        Field rawVulkanBackendField = VulkanicAPI.class.getDeclaredField("rawVulkanBackend");
        rawVulkanBackendField.setAccessible(true);
        rawVulkanBackendField.set(null, null);
        net.vulkanic.bridge.RustGalVulkanWholeFrameMode.deactivateRustPresentation();
        net.vulkanic.bridge.RustGalVulkanWholeFrameMode.clearVulkanBackendSelection();
    }
}
