package net.vulkanic.backends.vulkan;

import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VK10;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VulkanCommandSubmissionStateManagerTest {
    @Test
    void beginEndAndResetLifecycleTracksImmediateRecordingSlot() {
        VulkanCommandSubmissionStateManager manager = readyManager();

        manager.markImmediateRecordingStarted();

        assertTrue(manager.commandBufferRecording());
        assertEquals(0, manager.recordingImmediateSubmitSlot());
        assertEquals(0xA0L, manager.currentCommandPool());
        assertEquals(VK10.VK_NULL_HANDLE, manager.primaryCommandBufferHandle());

        manager.markImmediateSubmitted(0);

        assertFalse(manager.commandBufferRecording());
        assertEquals(-1, manager.recordingImmediateSubmitSlot());
        assertTrue(manager.immediateSubmitInFlight());
        assertTrue(manager.isImmediateSubmitSlotInFlight(0));
    }

    @Test
    void frameSlotReuseKeepsIndependentCommandPoolsAndBuffers() {
        VulkanCommandSubmissionStateManager manager = readyManager();

        assertEquals(0xF0L, manager.frameCommandPool(0));
        assertEquals(0xF1L, manager.frameCommandPool(1));
        assertEquals(null, manager.frameCommandBuffer(0));
        assertEquals(null, manager.frameCommandBuffer(1));

        manager.clearFrameSlot(0);

        assertEquals(VK10.VK_NULL_HANDLE, manager.frameCommandPool(0));
        assertEquals(0xF1L, manager.frameCommandPool(1));
    }

    @Test
    void immediateSubmitReuseAdvancesRingSlot() {
        VulkanCommandSubmissionStateManager manager = readyManager();

        manager.advanceImmediateSlotAfterSubmit(0);
        assertEquals(1, manager.currentImmediateSubmitSlot());
        assertEquals(0xA1L, manager.currentCommandPool());
        assertEquals(0xB1L, manager.currentImmediateSubmitFence());
        assertEquals(VK10.VK_NULL_HANDLE, manager.primaryCommandBufferHandle());

        manager.advanceImmediateSlotAfterSubmit(1);
        assertEquals(0, manager.currentImmediateSubmitSlot());
    }

    @Test
    void fenceCompletionClearsOnlyCompletedImmediateSlot() {
        VulkanCommandSubmissionStateManager manager = readyManager();

        manager.markImmediateSubmitted(0);
        manager.advanceImmediateSlotAfterSubmit(0);
        manager.markImmediateRecordingStarted();
        manager.markImmediateSubmitted(1);

        manager.markImmediateSlotComplete(0);

        assertFalse(manager.isImmediateSubmitSlotInFlight(0));
        assertTrue(manager.isImmediateSubmitSlotInFlight(1));
        assertTrue(manager.immediateSubmitInFlight());

        manager.markImmediateSlotComplete(1);
        assertFalse(manager.immediateSubmitInFlight());
    }

    @Test
    void failedSubmitCleanupDropsRecordingStateWithoutPretendingSlotIsSubmitted() {
        VulkanCommandSubmissionStateManager manager = readyManager();

        manager.markImmediateRecordingStarted();
        manager.markImmediateSubmitFailed(0);

        assertFalse(manager.commandBufferRecording());
        assertEquals(-1, manager.recordingImmediateSubmitSlot());
        assertFalse(manager.immediateSubmitInFlight());
        assertFalse(manager.isImmediateSubmitSlotInFlight(0));
    }

    @Test
    void generationReservationAndRetirementAreTiedToSubmissionSlots() {
        VulkanCommandSubmissionStateManager manager = readyManager();
        VulkanDeferredResourceLifetime<String> lifetime = new VulkanDeferredResourceLifetime<>(2, 2);

        long immediateGeneration = manager.reserveImmediateWorkGeneration(lifetime);
        assertEquals(immediateGeneration, manager.reservedImmediateWorkGeneration(lifetime, 0));

        long frameGeneration = manager.reserveFrameWorkGeneration(lifetime, 1);
        assertEquals(frameGeneration, manager.reservedFrameWorkGeneration(lifetime, 1));
    }

    @Test
    void shutdownAndDeviceLossCleanupClearsAllOwnedState() {
        VulkanCommandSubmissionStateManager manager = readyManager();
        manager.markImmediateRecordingStarted();
        manager.markImmediateSubmitted(0);

        manager.clearForDeviceLossOrShutdown();

        assertEquals(VK10.VK_NULL_HANDLE, manager.currentCommandPool());
        assertEquals(VK10.VK_NULL_HANDLE, manager.currentImmediateSubmitFence());
        assertEquals(VK10.VK_NULL_HANDLE, manager.primaryCommandBufferHandle());
        assertFalse(manager.commandBufferRecording());
        assertFalse(manager.immediateSubmitInFlight());
        assertEquals(-1, manager.recordingImmediateSubmitSlot());
        assertEquals(VK10.VK_NULL_HANDLE, manager.immediateCommandPool(0));
        assertEquals(VK10.VK_NULL_HANDLE, manager.frameCommandPool(0));
    }

    @Test
    void rejectsInvalidSlotConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new VulkanCommandSubmissionStateManager(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new VulkanCommandSubmissionStateManager(1, 0));
    }

    private static VulkanCommandSubmissionStateManager readyManager() {
        VulkanCommandSubmissionStateManager manager = new VulkanCommandSubmissionStateManager(2, 2);
        manager.installImmediateSlot(0, 0xA0L, null, 0xB0L);
        manager.installImmediateSlot(1, 0xA1L, null, 0xB1L);
        manager.installFrameSlot(0, 0xF0L, null);
        manager.installFrameSlot(1, 0xF1L, null);
        manager.activateImmediateSlot(0);
        return manager;
    }
}
