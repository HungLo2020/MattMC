package net.vulkanic.backends.vulkan;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.EXTAttachmentFeedbackLoopLayout;
import org.lwjgl.vulkan.VK10;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class VulkanImageStateTrackerTest {
    @Test
    public void testRegistersTextureAndTracksMipLayoutsIndependently() {
        VulkanImageStateTracker tracker = new VulkanImageStateTracker();
        VulkanImageState state = tracker.registerTexture(
            7,
            0xCAFE,
            VK10.VK_IMAGE_ASPECT_COLOR_BIT,
            3,
            1,
            false,
            VK10.VK_IMAGE_LAYOUT_UNDEFINED
        );

        assertEquals(7, state.textureId());
        assertEquals(0xCAFE, state.imageHandle());
        assertEquals(VK10.VK_IMAGE_LAYOUT_UNDEFINED, tracker.layoutFor(7, 0, VK10.VK_IMAGE_LAYOUT_GENERAL));

        tracker.recordLayout(7, 1, VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

        assertEquals(VK10.VK_IMAGE_LAYOUT_UNDEFINED, tracker.layoutFor(7, 0, VK10.VK_IMAGE_LAYOUT_GENERAL));
        assertEquals(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, tracker.layoutFor(7, 1, VK10.VK_IMAGE_LAYOUT_GENERAL));
        assertEquals(VK10.VK_IMAGE_LAYOUT_UNDEFINED, tracker.layoutFor(7, 2, VK10.VK_IMAGE_LAYOUT_GENERAL));
    }

    @Test
    public void testTransitionPlanOnlyIncludesMipsThatNeedWork() {
        VulkanImageStateTracker tracker = new VulkanImageStateTracker();
        tracker.registerTexture(
            11,
            0xBEEF,
            VK10.VK_IMAGE_ASPECT_COLOR_BIT,
            4,
            1,
            false,
            VK10.VK_IMAGE_LAYOUT_UNDEFINED
        );
        tracker.recordLayout(11, 1, VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

        List<VulkanImageStateTracker.VulkanImageTransition> transitions =
            tracker.planTransitions(11, 0, 3, VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

        assertEquals(2, transitions.size());
        assertEquals(0, transitions.get(0).mipLevel());
        assertEquals(VK10.VK_IMAGE_LAYOUT_UNDEFINED, transitions.get(0).oldLayout());
        assertEquals(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, transitions.get(0).newLayout());
        assertEquals(2, transitions.get(1).mipLevel());
    }

    @Test
    public void testClearingStorageKeepsTextureIdentityButDropsLayouts() {
        VulkanImageStateTracker tracker = new VulkanImageStateTracker();
        tracker.registerTexture(3, 0x1234, VK10.VK_IMAGE_ASPECT_DEPTH_BIT, 2, 1, false, VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
        tracker.clearTextureStorage(3);

        assertNotNull(tracker.state(3));
        assertEquals(VK10.VK_IMAGE_LAYOUT_UNDEFINED, tracker.layoutFor(3, 0, VK10.VK_IMAGE_LAYOUT_GENERAL));
        assertTrue(tracker.planTransitions(3, 0, 1, VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL).isEmpty());
    }

    @Test
    public void testRenderPassKeyIsTypedAndDefensive() {
        VulkanRenderPassKey.Attachment color = new VulkanRenderPassKey.Attachment(
            VK10.VK_FORMAT_R8G8B8A8_UNORM,
            VK10.VK_ATTACHMENT_LOAD_OP_LOAD,
            VK10.VK_ATTACHMENT_STORE_OP_STORE,
            VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
            VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
            VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL
        );
        VulkanRenderPassKey key = VulkanRenderPassKey.framebuffer(List.of(color), null, false);

        assertEquals(List.of(color), key.colorAttachments());
        assertNull(key.depthAttachment());
        assertFalse(key.feedbackLoop());
        assertThrows(IllegalArgumentException.class, () -> VulkanRenderPassKey.framebuffer(List.of(), null, false));
    }

    @Test
    public void testImageUseMapsKnownLayouts() {
        assertEquals(VulkanImageUse.SAMPLED_COLOR, VulkanImageUse.fromVkLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL));
        assertEquals(VulkanImageUse.FEEDBACK_LOOP, VulkanImageUse.fromVkLayout(EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT));
        assertEquals(VulkanImageUse.GENERAL, VulkanImageUse.fromVkLayout(0x7FFFFFFF));
    }
}
