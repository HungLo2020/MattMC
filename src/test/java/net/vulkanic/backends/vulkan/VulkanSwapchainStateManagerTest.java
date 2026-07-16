package net.vulkanic.backends.vulkan;

import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VK10;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VulkanSwapchainStateManagerTest {
    @Test
    void acquisitionKeepsImageIndexZeroDistinctFromNoImage() {
        VulkanSwapchainStateManager manager = readyManager();

        manager.beginAcquiredFrame(0, 301L);

        assertTrue(manager.frameInProgress());
        assertEquals(0, manager.acquiredImageIndex());
        assertEquals(301L, manager.imageInFlightFence(0));
        assertEquals(201L, manager.acquiredRenderFinishedSemaphore());
    }

    @Test
    void frameReuseAdvancesAndWrapsSyncSlot() {
        VulkanSwapchainStateManager manager = readyManager();

        assertEquals(0, manager.currentFrameSyncIndex());
        manager.skipFrameAndAdvance();
        assertEquals(1, manager.currentFrameSyncIndex());
        manager.beginAcquiredFrame(1, 302L);
        manager.finishFrameAndAdvance();

        assertEquals(0, manager.currentFrameSyncIndex());
        assertEquals(-1, manager.acquiredImageIndex());
        assertFalse(manager.frameInProgress());
    }

    @Test
    void resizeInvalidatesImageStateAndFrameProgress() {
        VulkanSwapchainStateManager manager = readyManager();
        manager.beginAcquiredFrame(1, 302L);
        manager.setCurrentFrameCommandBufferRecording(true);

        manager.clearSwapchainImages();
        manager.resetFrameState();

        assertFalse(manager.hasValidFrameSyncPrimitives());
        assertEquals(0, manager.imageCount());
        assertEquals(-1, manager.acquiredImageIndex());
        assertFalse(manager.frameInProgress());
        assertFalse(manager.isCurrentFrameCommandBufferRecording());
    }

    @Test
    void recordsOutOfDateAndSuboptimalStatusWithoutChangingPolicy() {
        VulkanSwapchainStateManager manager = readyManager();

        manager.markAcquireOutOfDate();
        assertTrue(manager.lastAcquireOutOfDate());

        manager.markPresentSuboptimal();
        assertTrue(manager.lastPresentSuboptimal());
        assertFalse(manager.lastPresentOutOfDate());

        manager.markPresentOutOfDate();
        assertTrue(manager.lastPresentOutOfDate());
        assertFalse(manager.lastPresentSuboptimal());

        manager.clearSwapchainStatusFlags();
        assertFalse(manager.lastAcquireOutOfDate());
        assertFalse(manager.lastPresentOutOfDate());
        assertFalse(manager.lastPresentSuboptimal());
    }

    @Test
    void tracksSwapchainImageLayoutsAndPresentTargets() {
        VulkanSwapchainStateManager manager = readyManager();

        assertEquals(VK10.VK_IMAGE_LAYOUT_UNDEFINED, manager.imageLayout(0));
        manager.recordImageLayout(0, KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
        manager.recordPresentTargets(401L, List.of(501L, 502L));

        assertEquals(KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR, manager.imageLayout(0));
        assertEquals(401L, manager.presentRenderPass());
        assertEquals(501L, manager.presentFramebufferHandle(0));
        assertEquals(1, manager.imageIndexForViewHandle(102L));
    }

    @Test
    void shutdownCleanupClearsAllOwnedState() {
        VulkanSwapchainStateManager manager = readyManager();
        manager.beginAcquiredFrame(0, 301L);
        manager.recordPresentTargets(401L, List.of(501L, 502L));

        manager.clearForDeviceLossOrShutdown();

        assertEquals(VK10.VK_FORMAT_UNDEFINED, manager.imageFormat());
        assertEquals(0, manager.imageCount());
        assertEquals(VK10.VK_NULL_HANDLE, manager.presentRenderPass());
        assertEquals(-1, manager.acquiredImageIndex());
        assertFalse(manager.frameInProgress());
        assertFalse(manager.hasValidFrameSyncPrimitives());
    }

    @Test
    void renderFinishedSemaphoreRequiresAValidAcquiredImage() {
        VulkanSwapchainStateManager manager = readyManager();

        assertThrows(IllegalStateException.class, manager::acquiredRenderFinishedSemaphore);
    }

    private static VulkanSwapchainStateManager readyManager() {
        VulkanSwapchainStateManager manager = new VulkanSwapchainStateManager(2);
        manager.setImageAvailableSemaphore(0, 11L);
        manager.setImageAvailableSemaphore(1, 12L);
        manager.setFrameFence(0, 21L);
        manager.setFrameFence(1, 22L);
        manager.installSwapchain(
            VK10.VK_FORMAT_B8G8R8A8_UNORM,
            41,
            51,
            1280,
            720,
            List.of(91L, 92L),
            List.of(101L, 102L),
            new long[] { 201L, 202L }
        );
        assertTrue(manager.hasValidFrameSyncPrimitives());
        return manager;
    }
}
