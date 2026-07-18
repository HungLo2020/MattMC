package net.vulkanic.backends.vulkan;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.vulkan.EXTAttachmentFeedbackLoopLayout;
import org.lwjgl.vulkan.KHRPresentId;
import org.lwjgl.vulkan.KHRPresentWait;
import org.lwjgl.vulkan.KHRSurface;
import org.lwjgl.vulkan.VK10;

import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VulkanBackendLifecycleManagerTest {

    @Test
    void windowSelectionPrefersRegisteredWindowAndRejectsMissingWindow() {
        VulkanBackendLifecycleManager manager = new VulkanBackendLifecycleManager();

        VulkanBackendLifecycleManager.WindowSelection registered = manager.selectWindow(0xCAFE, 0xBEEF);
        assertEquals(0xCAFE, registered.selectedWindowHandle());
        assertTrue(registered.registeredWindowPreferred());

        VulkanBackendLifecycleManager.WindowSelection current = manager.selectWindow(0L, 0xBEEF);
        assertEquals(0xBEEF, current.selectedWindowHandle());
        assertFalse(current.registeredWindowPreferred());

        assertThrows(IllegalStateException.class, () -> manager.selectWindow(0L, 0L));
    }

    @Test
    void combinedGraphicsPresentQueuePreservesExistingQueueSelectionPolicy() {
        VulkanBackendLifecycleManager.QueueFamilyPlan singleQueue =
            VulkanBackendLifecycleManager.planCombinedGraphicsPresentQueue(3, 1);
        assertEquals(3, singleQueue.graphicsFamilyIndex());
        assertEquals(1, singleQueue.requestedQueueCount());
        assertEquals(0, singleQueue.graphicsQueueIndex());
        assertEquals(0, singleQueue.presentQueueIndex());
        assertFalse(singleQueue.usesSeparatePresentQueueHandle());

        VulkanBackendLifecycleManager.QueueFamilyPlan multiQueue =
            VulkanBackendLifecycleManager.planCombinedGraphicsPresentQueue(5, 4);
        assertEquals(2, multiQueue.requestedQueueCount());
        assertEquals(0, multiQueue.graphicsQueueIndex());
        assertEquals(1, multiQueue.presentQueueIndex());
        assertTrue(multiQueue.usesSeparatePresentQueueHandle());
    }

    @Test
    void extensionPlanningKeepsPresentCompletionDisabledAndFeedbackLoopOptional() {
        VulkanBackendLifecycleManager.DeviceExtensionPlan noOptional =
            VulkanBackendLifecycleManager.planDeviceExtensions(Set.of(
                KHRPresentId.VK_KHR_PRESENT_ID_EXTENSION_NAME,
                KHRPresentWait.VK_KHR_PRESENT_WAIT_EXTENSION_NAME
            ));
        assertFalse(noOptional.presentId());
        assertFalse(noOptional.presentWait());
        assertFalse(noOptional.attachmentFeedbackLoopLayout());

        VulkanBackendLifecycleManager.DeviceExtensionPlan feedback =
            VulkanBackendLifecycleManager.planDeviceExtensions(Set.of(
                EXTAttachmentFeedbackLoopLayout.VK_EXT_ATTACHMENT_FEEDBACK_LOOP_LAYOUT_EXTENSION_NAME
            ));
        assertFalse(feedback.presentId());
        assertFalse(feedback.presentWait());
        assertTrue(feedback.attachmentFeedbackLoopLayout());
    }

    @Test
    void presentModeSelectionPreservesFifoExperimentAndFallbackOrder() {
        IntBuffer fifoAndMailbox = IntBuffer.wrap(new int[] {
            KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR,
            KHRSurface.VK_PRESENT_MODE_FIFO_KHR
        });
        assertEquals(
            KHRSurface.VK_PRESENT_MODE_FIFO_KHR,
            VulkanBackendLifecycleManager.choosePresentMode(fifoAndMailbox, true, GLFW.GLFW_PLATFORM_X11)
        );

        IntBuffer mailbox = IntBuffer.wrap(new int[] {
            KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR,
            KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR
        });
        assertEquals(
            KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR,
            VulkanBackendLifecycleManager.choosePresentMode(mailbox, true, GLFW.GLFW_PLATFORM_X11)
        );

        IntBuffer immediateOnX11 = IntBuffer.wrap(new int[] {
            KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR,
            KHRSurface.VK_PRESENT_MODE_FIFO_RELAXED_KHR
        });
        assertEquals(
            KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR,
            VulkanBackendLifecycleManager.choosePresentMode(immediateOnX11, true, GLFW.GLFW_PLATFORM_X11)
        );

        IntBuffer fifoRelaxedFallback = IntBuffer.wrap(new int[] {
            KHRSurface.VK_PRESENT_MODE_FIFO_RELAXED_KHR,
            KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR
        });
        assertEquals(
            KHRSurface.VK_PRESENT_MODE_FIFO_RELAXED_KHR,
            VulkanBackendLifecycleManager.choosePresentMode(fifoRelaxedFallback, true, GLFW.GLFW_PLATFORM_WAYLAND)
        );
    }

    @Test
    void imageCountAndUsagePlanningPreserveExistingSwapchainPolicy() {
        assertEquals(3, VulkanBackendLifecycleManager.chooseImageCount(2, 0));
        assertEquals(2, VulkanBackendLifecycleManager.chooseImageCount(1, 2));
        assertEquals(1, VulkanBackendLifecycleManager.chooseImageCount(0, 1));

        assertEquals(
            VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT,
            VulkanBackendLifecycleManager.chooseSwapchainImageUsage(0)
        );
        assertEquals(
            VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT,
            VulkanBackendLifecycleManager.chooseSwapchainImageUsage(VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT)
        );
    }

    @Test
    void lifecycleStateMarksDeviceLossAndIdempotentShutdownCompletion() {
        VulkanBackendLifecycleManager manager = new VulkanBackendLifecycleManager();
        assertEquals(VulkanBackendLifecycleManager.State.NEW, manager.state());

        manager.markDeviceLost();
        assertEquals(VulkanBackendLifecycleManager.State.DEVICE_LOST, manager.state());

        manager.markShutdownComplete();
        assertEquals(VulkanBackendLifecycleManager.State.SHUTDOWN, manager.state());
        manager.markShutdownComplete();
        assertEquals(VulkanBackendLifecycleManager.State.SHUTDOWN, manager.state());
    }

    @Test
    void commandRuntimeSnapshotsPublishGenerationsAndRejectStaleSnapshots() {
        VulkanBackendLifecycleManager manager = new VulkanBackendLifecycleManager();
        VulkanBackendLifecycleManager.CommandRuntimeSnapshot initial = manager.commandRuntimeSnapshot();
        assertEquals(VulkanBackendLifecycleManager.State.NEW, initial.state());
        assertEquals(0L, initial.windowHandle());

        manager.recordWindowSelection(new VulkanBackendLifecycleManager.WindowSelection(0x1010L, 0L, 0x1010L, true));
        VulkanBackendLifecycleManager.CommandRuntimeSnapshot selectedWindow = manager.commandRuntimeSnapshot();
        assertEquals(0x1010L, selectedWindow.windowHandle());
        assertTrue(selectedWindow.generation() > initial.generation());

        manager.updateWindowHandle(0x2020L);
        VulkanBackendLifecycleManager.CommandRuntimeSnapshot replacedWindow = manager.commandRuntimeSnapshot();
        assertEquals(0x2020L, replacedWindow.windowHandle());
        assertTrue(replacedWindow.generation() > selectedWindow.generation());
        assertThrows(
            IllegalStateException.class,
            () -> manager.validateSnapshotCurrent(selectedWindow, "unit-test")
        );
    }

    @Test
    void commandRuntimeSnapshotsRejectExecutionAfterDeviceLossAndShutdown() {
        VulkanBackendLifecycleManager manager = new VulkanBackendLifecycleManager();

        assertFalse(manager.commandRuntimeSnapshot().commandExecutionAllowed());
        assertThrows(
            IllegalStateException.class,
            () -> manager.requireCommandRuntimeSnapshot("unit-test")
        );

        manager.markDeviceLost();
        VulkanBackendLifecycleManager.CommandRuntimeSnapshot deviceLost = manager.commandRuntimeSnapshot();
        assertEquals(VulkanBackendLifecycleManager.State.DEVICE_LOST, deviceLost.state());
        assertFalse(deviceLost.commandExecutionAllowed());
        assertThrows(
            IllegalStateException.class,
            () -> manager.requireCommandRuntimeSnapshot("unit-test")
        );

        manager.markShutdownComplete();
        VulkanBackendLifecycleManager.CommandRuntimeSnapshot shutdown = manager.commandRuntimeSnapshot();
        assertEquals(VulkanBackendLifecycleManager.State.SHUTDOWN, shutdown.state());
        assertFalse(shutdown.commandExecutionAllowed());
        assertThrows(
            IllegalStateException.class,
            () -> manager.validateSnapshotCurrent(shutdown, "unit-test")
        );
    }

    @Test
    void swapchainSnapshotDefensivelyCopiesLifecycleOwnedCollections() {
        long[] semaphores = {31L, 32L};
        VulkanBackendLifecycleManager.SwapchainResourceSnapshot snapshot =
            new VulkanBackendLifecycleManager.SwapchainResourceSnapshot(
                11L,
                VK10.VK_FORMAT_R8G8B8A8_UNORM,
                KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR,
                KHRSurface.VK_PRESENT_MODE_FIFO_KHR,
                1280,
                720,
                VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT,
                List.of(21L, 22L),
                List.of(41L, 42L),
                semaphores
            );

        semaphores[0] = 999L;
        assertArrayEquals(new long[] {31L, 32L}, snapshot.renderFinishedSemaphores());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.imageHandles().add(23L));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.imageViewHandles().add(43L));
    }

    @Test
    void commandRuntimeSnapshotDefensivelyCopiesSwapchainResourceIdentity() {
        VulkanBackendLifecycleManager.CommandRuntimeSnapshot snapshot =
            new VulkanBackendLifecycleManager.CommandRuntimeSnapshot(
                7L,
                VulkanBackendLifecycleManager.State.SWAPCHAIN_READY,
                null,
                null,
                null,
                null,
                null,
                11L,
                12L,
                13L,
                true,
                new VulkanBackendLifecycleManager.DeviceCapabilitySnapshot(
                    0,
                    VK10.VK_API_VERSION_1_0,
                    "Vulkan GPU",
                    false,
                    false,
                    false,
                    false,
                    false,
                    1L,
                    16384,
                    32
                ),
                new VulkanBackendLifecycleManager.QueueFamilyPlan(2, 3, 0, 1),
                true,
                true,
                true,
                true,
                true,
                false,
                false,
                true,
                VK10.VK_FORMAT_R8G8B8A8_UNORM,
                KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR,
                KHRSurface.VK_PRESENT_MODE_FIFO_KHR,
                1280,
                720,
                List.of(21L, 22L),
                List.of(31L, 32L)
            );

        assertThrows(UnsupportedOperationException.class, () -> snapshot.swapchainImageHandles().add(23L));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.swapchainImageViewHandles().add(33L));
        assertTrue(snapshot.swapchainAvailable() || !snapshot.commandExecutionAllowed());
    }

    @Test
    void nativeSpineNoLongerDeclaresMirroredLifecycleHandles() throws Exception {
        Path sourcePath = Paths.get(System.getProperty("user.dir"))
            .resolve("src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java");
        String source = Files.readString(sourcePath);

        assertFalse(source.contains("private VkInstance instance;"));
        assertFalse(source.contains("private VkPhysicalDevice physicalDevice;"));
        assertFalse(source.contains("private VkDevice logicalDevice;"));
        assertFalse(source.contains("private VkQueue graphicsQueue;"));
        assertFalse(source.contains("private VkQueue presentQueue;"));
        assertFalse(source.contains("private long surface;"));
        assertFalse(source.contains("private long swapchain;"));
        assertTrue(source.contains("return runtimeSnapshot().logicalDevice();"));
        assertTrue(source.contains("return runtimeSnapshot().swapchain();"));
    }
}
