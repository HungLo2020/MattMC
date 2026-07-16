package net.vulkanic.backends.vulkan;

import net.vulkanic.VulkanicRenderPassDescriptor;
import net.vulkanic.VulkanicResourceUsage;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.EXTAttachmentFeedbackLoopLayout;
import org.lwjgl.vulkan.KHRSwapchain;
import org.lwjgl.vulkan.VK10;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VulkanRenderPassLayoutPlannerTest {
    @Test
    void colorAttachmentIndexZeroRemainsValidAttachment() {
        VulkanRenderPassLayoutPlanner.RenderPassPlan plan = VulkanRenderPassLayoutPlanner.planFramebuffer(
            List.of(color(0, VK10.VK_FORMAT_R8G8B8A8_UNORM)),
            null
        );

        assertEquals(1, plan.colorAttachments().size());
        assertEquals(0, plan.colorAttachment(0).attachmentIndex());
        assertEquals(0, plan.colorAttachment(0).cacheKey().subpassLayout()
            - VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        assertEquals(1, plan.compatibilityKey().colorAttachmentCount());
    }

    @Test
    void colorOnlyFramebufferUsesSampledReadFinalLayout() {
        VulkanRenderPassLayoutPlanner.RenderPassPlan plan = VulkanRenderPassLayoutPlanner.planFramebuffer(
            List.of(color(0, VK10.VK_FORMAT_R16G16B16A16_SFLOAT)),
            null
        );

        VulkanRenderPassLayoutPlanner.AttachmentPlan color = plan.colorAttachment(0);
        assertEquals(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, color.initialLayout());
        assertEquals(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, color.subpassLayout());
        assertEquals(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, color.finalLayout());
        assertFalse(plan.hasDepthAttachment());
    }

    @Test
    void depthOnlyFramebufferUsesDepthAttachmentAndReadOnlyFinalLayouts() {
        VulkanRenderPassLayoutPlanner.RenderPassPlan plan = VulkanRenderPassLayoutPlanner.planFramebuffer(
            List.of(),
            depth(0, VK10.VK_FORMAT_D32_SFLOAT, false, false)
        );

        VulkanRenderPassLayoutPlanner.AttachmentPlan depth = plan.depthAttachment();
        assertEquals(0, plan.compatibilityKey().colorAttachmentCount());
        assertTrue(plan.compatibilityKey().hasDepthAttachment());
        assertEquals(VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL, depth.initialLayout());
        assertEquals(VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL, depth.subpassLayout());
        assertEquals(VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL, depth.finalLayout());
    }

    @Test
    void combinedPassPlansColorAndDepthAttachments() {
        VulkanRenderPassLayoutPlanner.RenderPassPlan plan = VulkanRenderPassLayoutPlanner.planFramebuffer(
            List.of(color(0, VK10.VK_FORMAT_R8G8B8A8_UNORM)),
            depth(1, VK10.VK_FORMAT_D24_UNORM_S8_UINT, false, true)
        );

        assertEquals(1, plan.colorAttachments().size());
        assertTrue(plan.hasDepthAttachment());
        assertEquals(VK10.VK_ATTACHMENT_LOAD_OP_LOAD, plan.colorAttachment(0).loadOp());
        assertEquals(VK10.VK_ATTACHMENT_STORE_OP_STORE, plan.depthAttachment().storeOp());
        assertEquals(VK10.VK_ATTACHMENT_LOAD_OP_LOAD, plan.depthAttachment().stencilLoadOp());
        assertEquals(VK10.VK_ATTACHMENT_STORE_OP_STORE, plan.depthAttachment().stencilStoreOp());
    }

    @Test
    void clearLoadStoreCombinationsArePreserved() {
        VulkanRenderPassLayoutPlanner.AttachmentInput color = VulkanRenderPassLayoutPlanner.AttachmentInput.color(
            0,
            VK10.VK_FORMAT_R8G8B8A8_UNORM,
            false,
            false,
            VK10.VK_IMAGE_LAYOUT_UNDEFINED,
            VulkanicRenderPassDescriptor.LoadOp.CLEAR,
            VulkanicRenderPassDescriptor.StoreOp.DONT_CARE,
            VulkanicResourceUsage.INFERRED,
            VulkanicResourceUsage.INFERRED,
            VulkanicResourceUsage.INFERRED
        );

        VulkanRenderPassLayoutPlanner.AttachmentPlan plan =
            VulkanRenderPassLayoutPlanner.planFramebuffer(List.of(color), null).colorAttachment(0);

        assertEquals(VK10.VK_ATTACHMENT_LOAD_OP_CLEAR, plan.loadOp());
        assertEquals(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE, plan.storeOp());
        assertTrue(plan.clear());
    }

    @Test
    void feedbackLoopAttachmentsUseFeedbackLayoutsAndDependencyIntent() {
        VulkanRenderPassLayoutPlanner.RenderPassPlan plan = VulkanRenderPassLayoutPlanner.planFramebuffer(
            List.of(color(0, VK10.VK_FORMAT_R8G8B8A8_UNORM, true)),
            null
        );

        assertEquals(
            EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT,
            plan.colorAttachment(0).initialLayout()
        );
        assertEquals(
            EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT,
            plan.colorAttachment(0).subpassLayout()
        );
        assertTrue(plan.compatibilityKey().feedbackLoop());
        assertEquals(4, plan.dependencyIntent().size());
        assertTrue((plan.dependencyIntent().get(3).dependencyFlags()
            & EXTAttachmentFeedbackLoopLayout.VK_DEPENDENCY_FEEDBACK_LOOP_BIT_EXT) != 0);
    }

    @Test
    void explicitSampledToAttachmentAndAttachmentToSampledUsageIsPlanned() {
        VulkanRenderPassLayoutPlanner.AttachmentInput color = VulkanRenderPassLayoutPlanner.AttachmentInput.color(
            0,
            VK10.VK_FORMAT_R8G8B8A8_UNORM,
            false,
            false,
            VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
            VulkanicRenderPassDescriptor.LoadOp.LOAD,
            VulkanicRenderPassDescriptor.StoreOp.STORE,
            VulkanicResourceUsage.SAMPLED_READ,
            VulkanicResourceUsage.COLOR_ATTACHMENT_WRITE,
            VulkanicResourceUsage.SAMPLED_READ
        );

        VulkanRenderPassLayoutPlanner.AttachmentPlan plan =
            VulkanRenderPassLayoutPlanner.planFramebuffer(List.of(color), null).colorAttachment(0);

        assertEquals(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, plan.initialLayout());
        assertEquals(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, plan.subpassLayout());
        assertEquals(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, plan.finalLayout());
    }

    @Test
    void swapchainTextureViewPlansPresentFinalLayout() {
        VulkanRenderPassLayoutPlanner.AttachmentInput swapchainColor =
            VulkanRenderPassLayoutPlanner.AttachmentInput.color(
                0,
                VK10.VK_FORMAT_B8G8R8A8_UNORM,
                false,
                true,
                VK10.VK_IMAGE_LAYOUT_UNDEFINED,
                VulkanicRenderPassDescriptor.LoadOp.CLEAR,
                VulkanicRenderPassDescriptor.StoreOp.STORE,
                VulkanicResourceUsage.INFERRED,
                VulkanicResourceUsage.INFERRED,
                VulkanicResourceUsage.INFERRED
            );

        VulkanRenderPassLayoutPlanner.RenderPassPlan plan =
            VulkanRenderPassLayoutPlanner.planTextureView(swapchainColor, null);

        assertEquals(KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR, plan.colorAttachment(0).finalLayout());
        assertEquals(VulkanRenderPassCompatibilityKey.DependencyProfile.TEXTURE_VIEW,
            plan.compatibilityKey().dependencyProfile());
    }

    @Test
    void swapchainPresentDependencyIntentIsAvailableForPersistentPresentPass() {
        VulkanRenderPassCompatibilityKey key =
            VulkanRenderPassLayoutPlanner.swapchainPresentCompatibility(VK10.VK_FORMAT_B8G8R8A8_UNORM);

        List<VulkanRenderPassLayoutPlanner.SubpassDependencyPlan> dependencies =
            VulkanRenderPassLayoutPlanner.dependencyIntent(key);

        assertEquals(VulkanRenderPassCompatibilityKey.DependencyProfile.SWAPCHAIN_PRESENT, key.dependencyProfile());
        assertEquals(2, dependencies.size());
        assertEquals(VK10.VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, dependencies.get(1).dstStageMask());
    }

    @Test
    void pipelineCompatiblePassKeepsFixedAttachmentLayoutsAndLoadStorePolicy() {
        VulkanRenderPassCompatibilityKey key = VulkanRenderPassCompatibilityKey.framebuffer(
            List.of(VK10.VK_FORMAT_R8G8B8A8_UNORM),
            VK10.VK_FORMAT_D32_SFLOAT,
            false
        );

        VulkanRenderPassLayoutPlanner.RenderPassPlan plan =
            VulkanRenderPassLayoutPlanner.planPipelineCompatible(key);

        assertEquals(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, plan.colorAttachment(0).initialLayout());
        assertEquals(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, plan.colorAttachment(0).finalLayout());
        assertEquals(VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE, plan.colorAttachment(0).loadOp());
        assertEquals(VK10.VK_ATTACHMENT_STORE_OP_STORE, plan.colorAttachment(0).storeOp());
        assertEquals(VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL, plan.depthAttachment().subpassLayout());
        assertEquals(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE, plan.depthAttachment().storeOp());
    }

    @Test
    void noColorDepthOnlyPassRejectsDepthAtWrongAttachmentIndex() {
        assertThrows(
            IllegalArgumentException.class,
            () -> VulkanRenderPassLayoutPlanner.planFramebuffer(
                List.of(),
                depth(1, VK10.VK_FORMAT_D32_SFLOAT, false, false)
            )
        );
    }

    private static VulkanRenderPassLayoutPlanner.AttachmentInput color(int index, int format) {
        return color(index, format, false);
    }

    private static VulkanRenderPassLayoutPlanner.AttachmentInput color(int index, int format, boolean feedbackLoop) {
        return VulkanRenderPassLayoutPlanner.AttachmentInput.color(
            index,
            format,
            feedbackLoop,
            false,
            VK10.VK_IMAGE_LAYOUT_UNDEFINED,
            VulkanicRenderPassDescriptor.LoadOp.LOAD,
            VulkanicRenderPassDescriptor.StoreOp.STORE,
            VulkanicResourceUsage.INFERRED,
            VulkanicResourceUsage.INFERRED,
            VulkanicResourceUsage.INFERRED
        );
    }

    private static VulkanRenderPassLayoutPlanner.AttachmentInput depth(
        int index,
        int format,
        boolean feedbackLoop,
        boolean stencil
    ) {
        return VulkanRenderPassLayoutPlanner.AttachmentInput.depth(
            index,
            format,
            feedbackLoop,
            stencil,
            VK10.VK_IMAGE_LAYOUT_UNDEFINED,
            VulkanicRenderPassDescriptor.LoadOp.LOAD,
            VulkanicRenderPassDescriptor.StoreOp.STORE,
            VulkanicResourceUsage.INFERRED,
            VulkanicResourceUsage.INFERRED,
            VulkanicResourceUsage.INFERRED
        );
    }
}
