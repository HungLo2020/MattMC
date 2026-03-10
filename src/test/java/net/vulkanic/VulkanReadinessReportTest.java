package net.vulkanic;

import net.vulkanic.backends.vulkan.VulkanBackend;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class VulkanReadinessReportTest {

    @Test
    public void testReportForNonVulkanBackend() {
        VulkanReadinessReport report = VulkanReadinessReport.forNonVulkanBackend(
            GraphicsBackendType.OPENGL,
            false
        );

        assertEquals(GraphicsBackendType.OPENGL, report.getActiveBackendType());
        assertFalse(report.isVulkanBackendSelected(),
            "Non-Vulkan backend report should not mark Vulkan as selected");
        assertFalse(report.isNativeVulkanReady(),
            "OpenGL path should not report native Vulkan readiness");
        assertFalse(report.getBlockers().isEmpty(),
            "Non-Vulkan report should include an explanatory blocker");
    }

    @Test
    public void testBootstrapVulkanBackendReadinessReportContainsImplementationBlocker() {
        VulkanBackend backend = new VulkanBackend();

        VulkanReadinessReport report = backend.getReadinessReport();

        assertEquals(GraphicsBackendType.VULKAN, report.getActiveBackendType());
        assertTrue(report.isVulkanBackendSelected(),
            "Bootstrap Vulkan backend should report Vulkan-selected state");
        assertFalse(report.isNativeVulkanReady(),
            "Bootstrap Vulkan backend should remain non-native-ready");
        assertTrue(report.getBlockers().stream().anyMatch(blocker ->
                blocker.contains("Native Vulkan command/pipeline implementation has not been integrated yet")),
            "Readiness blockers should include the explicit implementation gap");
    }

    @Test
    public void testNativeOperationFailureIncludesReadinessDiagnostics() {
        VulkanBackend backend = new VulkanBackend();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
            backend::beginCommandBuffer,
            "Bootstrap Vulkan backend should gate native operations behind readiness checks");

        String message = exception.getMessage();
        assertNotNull(message);
        assertTrue(message.contains("Readiness report:"),
            "Failure should include structured readiness summary");
        assertTrue(message.contains("nativeVulkanReady=false"),
            "Failure should explicitly report native readiness state");
    }
}
