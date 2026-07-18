package net.vulkanic.backends.vulkan;

import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VK10;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VulkanRenderTargetStateManagerTest {
    @Test
    void colorAttachmentIndexZeroRemainsDistinctFromNoAttachment() {
        VulkanRenderTargetStateManager<Integer, Integer> manager = new VulkanRenderTargetStateManager<>();
        manager.beginPass(
            compatibility(false),
            16,
            16,
            false,
            -1,
            List.of(0),
            List.of(123),
            null,
            VK10.VK_IMAGE_LAYOUT_UNDEFINED
        );

        assertEquals(1, manager.activeColorAttachmentCount());
        assertTrue(manager.isActiveAttachment(0));

        manager.resetActivePass();

        assertEquals(0, manager.activeColorAttachmentCount());
        assertFalse(manager.isActiveAttachment(0));
    }

    @Test
    void tracksColorDepthTargetsDimensionsAndFinalLayouts() {
        VulkanRenderTargetStateManager<String, String> manager = new VulkanRenderTargetStateManager<>();
        List<String> layoutEvents = new ArrayList<>();
        manager.beginPass(
            compatibility(false),
            320,
            180,
            false,
            -1,
            List.of("color0", "color1"),
            List.of(10, 11),
            "depth",
            12
        );

        manager.forEachActiveColorAttachment((attachment, finalLayout) ->
            layoutEvents.add(attachment + ":" + finalLayout)
        );

        assertEquals(320, manager.activeWidth());
        assertEquals(180, manager.activeHeight());
        assertEquals(List.of("color0:10", "color1:11"), layoutEvents);
        assertSame("depth", manager.activeDepthAttachment());
        assertEquals(12, manager.activeDepthFinalLayout());
    }

    @Test
    void tracksFeedbackLoopCompatibilityState() {
        VulkanRenderTargetStateManager<String, String> manager = new VulkanRenderTargetStateManager<>();
        VulkanRenderPassCompatibilityKey feedbackCompatibility = compatibility(true);

        manager.beginPass(
            feedbackCompatibility,
            64,
            64,
            false,
            -1,
            List.of("color"),
            List.of(1),
            null,
            VK10.VK_IMAGE_LAYOUT_UNDEFINED
        );

        assertSame(feedbackCompatibility, manager.activeCompatibilityKey());
        assertTrue(manager.activeCompatibilityKey().feedbackLoop());
    }

    @Test
    void beginEndStateResetClearsAllActivePassState() {
        VulkanRenderTargetStateManager<String, String> manager = new VulkanRenderTargetStateManager<>();
        manager.beginPass(
            compatibility(false),
            100,
            50,
            true,
            2,
            List.of(),
            List.of(),
            null,
            VK10.VK_IMAGE_LAYOUT_UNDEFINED
        );

        assertTrue(manager.activeTargetsSwapchain());
        assertEquals(2, manager.activeSwapchainImageIndex());
        assertEquals(1, manager.activeColorAttachmentCount());

        manager.resetActivePass();

        assertNull(manager.activeCompatibilityKey());
        assertEquals(0, manager.activeWidth());
        assertEquals(0, manager.activeHeight());
        assertFalse(manager.activeTargetsSwapchain());
        assertEquals(-1, manager.activeSwapchainImageIndex());
        assertEquals(0, manager.activeColorAttachmentCount());
        assertFalse(manager.hasActiveDepthAttachment());
    }

    @Test
    void resizeLikeBeginPassReplacesAttachmentIdentityAndDimensions() {
        VulkanRenderTargetStateManager<String, String> manager = new VulkanRenderTargetStateManager<>();
        manager.beginPass(compatibility(false), 640, 360, false, -1, List.of("old"), List.of(1), null, 0);
        manager.beginPass(compatibility(false), 1280, 720, false, -1, List.of("new"), List.of(2), null, 0);

        List<String> attachments = new ArrayList<>();
        manager.forEachActiveColorAttachment((attachment, finalLayout) ->
            attachments.add(attachment + ":" + finalLayout)
        );

        assertEquals(1280, manager.activeWidth());
        assertEquals(720, manager.activeHeight());
        assertEquals(List.of("new:2"), attachments);
        assertFalse(manager.isActiveAttachment("old"));
    }

    private static VulkanRenderPassCompatibilityKey compatibility(boolean feedbackLoop) {
        return VulkanRenderPassCompatibilityKey.framebuffer(
            List.of(VK10.VK_FORMAT_R8G8B8A8_UNORM),
            VK10.VK_FORMAT_D32_SFLOAT,
            feedbackLoop
        );
    }
}
