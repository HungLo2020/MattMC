package net.vulkanic;

import net.vulkanic.backends.vulkan.VulkanBackend;
import net.vulkanic.backends.vulkan.VulkanCommandContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for Vulkan command-buffer lifecycle routing and validation behavior.
 */
public class VulkanCommandBufferLifecycleTest {

    @AfterEach
    public void tearDown() {
        resetBackendStateUnchecked();
    }

    @Test
    public void testSubmitCommandBufferRejectsNonVulkanContextBeforeNativeReadinessChecks() {
        VulkanBackend backend = new VulkanBackend();

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> backend.submitCommandBuffer(VulkanicAPI.getImmediateContext())
        );

        assertTrue(exception.getMessage().contains("submitCommandBuffer requires VulkanCommandContext"));
    }

    @Test
    public void testApplyResourceBarriersRejectsNonVulkanContextBeforeNativeReadinessChecks() {
        VulkanBackend backend = new VulkanBackend();

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> backend.applyResourceBarriers(
                VulkanicAPI.getImmediateContext(),
                VulkanicResourceBarriers.of(VulkanicResourceBarriers.Barrier.TEXTURE_FETCH)
            )
        );

        assertTrue(exception.getMessage().contains("applyResourceBarriers requires VulkanCommandContext"));
    }

    @Test
    public void testBeginCommandBufferInVulkanModeStillFailsHardWithReadinessDiagnosticsWhenNativeUnavailable() {
        VulkanicAPI.initialize(GraphicsBackendType.VULKAN);

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            VulkanicAPI::beginCommandBuffer
        );

        String message = exception.getMessage();
        assertNotNull(message);
        assertTrue(message.contains("Readiness report:"));
        assertTrue(message.contains("nativeVulkanReady=false"));
    }

    @Test
    public void testApplyResourceBarriersRejectsNullBarriersWhenContextTypeIsValid() {
        VulkanBackend backend = new VulkanBackend();

        NullPointerException exception = assertThrows(
            NullPointerException.class,
            () -> backend.applyResourceBarriers(new VulkanCommandContext(1L, "test-cmd"), null)
        );

        assertTrue(exception.getMessage().contains("barriers must not be null"));
    }

    private static void resetBackendStateUnchecked() {
        try {
            for (String fieldName : new String[]{"backend", "rawVulkanBackend"}) {
                Field field = VulkanicAPI.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(null, null);
            }
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException("Failed to reset VulkanicAPI backend state", exception);
        }
    }
}
