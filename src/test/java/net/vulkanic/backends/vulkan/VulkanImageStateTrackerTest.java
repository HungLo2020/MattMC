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
	        VulkanRenderPassKey attachmentlessKey = VulkanRenderPassKey.framebuffer(List.of(), null, false);
	        assertTrue(attachmentlessKey.colorAttachments().isEmpty());
	        assertNull(attachmentlessKey.depthAttachment());

	        VulkanRenderPassKey.Attachment depth = new VulkanRenderPassKey.Attachment(
	            VK10.VK_FORMAT_D32_SFLOAT,
	            VK10.VK_ATTACHMENT_LOAD_OP_LOAD,
	            VK10.VK_ATTACHMENT_STORE_OP_STORE,
	            VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL,
	            VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL,
	            VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL
	        );
	        VulkanRenderPassKey depthOnlyKey = VulkanRenderPassKey.framebuffer(List.of(), depth, false);

	        assertTrue(depthOnlyKey.colorAttachments().isEmpty());
	        assertEquals(depth, depthOnlyKey.depthAttachment());
	    }

    @Test
    public void testRenderPassCompatibilityKeyCapturesDependencyProfile() {
        VulkanRenderPassCompatibilityKey textureViewKey = VulkanRenderPassCompatibilityKey.textureView(
            List.of(VK10.VK_FORMAT_R8G8B8A8_UNORM),
            VK10.VK_FORMAT_D32_SFLOAT,
            false
        );
        VulkanRenderPassCompatibilityKey framebufferKey = VulkanRenderPassCompatibilityKey.framebuffer(
            List.of(VK10.VK_FORMAT_R8G8B8A8_UNORM),
            VK10.VK_FORMAT_D32_SFLOAT,
            false
        );
        VulkanRenderPassCompatibilityKey feedbackFramebufferKey = VulkanRenderPassCompatibilityKey.framebuffer(
            List.of(VK10.VK_FORMAT_R8G8B8A8_UNORM),
            VK10.VK_FORMAT_D32_SFLOAT,
            true
        );
        VulkanRenderPassCompatibilityKey swapchainKey = VulkanRenderPassCompatibilityKey.swapchainPresent(
            VK10.VK_FORMAT_B8G8R8A8_UNORM
        );

        assertNotEquals(textureViewKey, framebufferKey);
        assertNotEquals(framebufferKey, feedbackFramebufferKey);
        assertEquals(VulkanRenderPassCompatibilityKey.DependencyProfile.TEXTURE_VIEW, textureViewKey.dependencyProfile());
        assertEquals(VulkanRenderPassCompatibilityKey.DependencyProfile.FRAMEBUFFER, framebufferKey.dependencyProfile());
        assertEquals(VulkanRenderPassCompatibilityKey.DependencyProfile.SWAPCHAIN_PRESENT, swapchainKey.dependencyProfile());
        assertTrue(textureViewKey.hasDepthAttachment());
        assertFalse(textureViewKey.hasStencilAttachment());
        assertTrue(VulkanRenderPassCompatibilityKey.framebuffer(
            List.of(VK10.VK_FORMAT_R8G8B8A8_UNORM),
            VK10.VK_FORMAT_D24_UNORM_S8_UINT,
            false
        ).hasStencilAttachment());
        assertEquals(1, swapchainKey.colorAttachmentCount());
        assertFalse(swapchainKey.hasDepthAttachment());
        assertFalse(swapchainKey.hasStencilAttachment());
    }

    @Test
    public void testRenderPassCompatibilityKeyDefensivelyCopiesFormats() {
        java.util.ArrayList<Integer> mutableFormats = new java.util.ArrayList<>();
        mutableFormats.add(VK10.VK_FORMAT_R8G8B8A8_UNORM);

        VulkanRenderPassCompatibilityKey key = VulkanRenderPassCompatibilityKey.framebuffer(
            mutableFormats,
            VK10.VK_FORMAT_UNDEFINED,
            false
        );
        mutableFormats.set(0, VK10.VK_FORMAT_B8G8R8A8_UNORM);

        assertEquals(List.of(VK10.VK_FORMAT_R8G8B8A8_UNORM), key.colorFormats());
        assertThrows(UnsupportedOperationException.class, () -> key.colorFormats().add(VK10.VK_FORMAT_R8G8B8A8_UNORM));
    }

    @Test
    public void testImageUseMapsKnownLayouts() {
        assertEquals(VulkanImageUse.SAMPLED_COLOR, VulkanImageUse.fromVkLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL));
        assertEquals(VulkanImageUse.FEEDBACK_LOOP, VulkanImageUse.fromVkLayout(EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT));
        assertEquals(VulkanImageUse.GENERAL, VulkanImageUse.fromVkLayout(0x7FFFFFFF));
    }
}
