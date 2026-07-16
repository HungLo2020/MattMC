package net.vulkanic.backends.vulkan;

import net.vulkanic.VulkanicResourceBarriers;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.EXTAttachmentFeedbackLoopLayout;
import org.lwjgl.vulkan.VK10;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VulkanSynchronizationPlannerTest {

    @Test
    public void imageLayoutTransitionPlansColorSubresourceMasks() {
        VulkanSynchronizationPlanner.ImageBarrierPlan plan =
            VulkanSynchronizationPlanner.planImageLayoutTransition(
                VK10.VK_IMAGE_ASPECT_COLOR_BIT,
                VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                2,
                3,
                4
            ).orElseThrow();

        assertEquals(VK10.VK_ACCESS_TRANSFER_WRITE_BIT, plan.srcAccessMask());
        assertEquals(VK10.VK_ACCESS_SHADER_READ_BIT, plan.dstAccessMask());
        assertEquals(VK10.VK_PIPELINE_STAGE_TRANSFER_BIT, plan.srcStageMask());
        assertEquals(
            VK10.VK_PIPELINE_STAGE_ALL_GRAPHICS_BIT | VK10.VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
            plan.dstStageMask()
        );
        assertEquals(VK10.VK_IMAGE_ASPECT_COLOR_BIT, plan.range().aspectMask());
        assertEquals(2, plan.range().baseMipLevel());
        assertEquals(3, plan.range().levelCount());
        assertEquals(0, plan.range().baseArrayLayer());
        assertEquals(4, plan.range().layerCount());
    }

    @Test
    public void noOpImageLayoutTransitionProducesNoBarrier() {
        Optional<VulkanSynchronizationPlanner.ImageBarrierPlan> plan =
            VulkanSynchronizationPlanner.planImageLayoutTransition(
                VK10.VK_IMAGE_ASPECT_COLOR_BIT,
                VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                0,
                1,
                1
            );

        assertTrue(plan.isEmpty());
    }

    @Test
    public void depthReadOnlyLayoutIncludesShaderReadAndDepthTestStages() {
        int accessMask = VulkanSynchronizationPlanner.accessMaskForLayout(
            VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL
        );
        int stageMask = VulkanSynchronizationPlanner.stageMaskForLayout(
            VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL
        );

        assertTrue((accessMask & VK10.VK_ACCESS_SHADER_READ_BIT) != 0);
        assertTrue((stageMask & VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT) != 0);
        assertTrue((stageMask & VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT) != 0);
        assertTrue((stageMask & VK10.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT) != 0);
    }

    @Test
    public void feedbackLoopLayoutPreservesAttachmentAndShaderVisibility() {
        int feedbackLayout = EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT;

        int accessMask = VulkanSynchronizationPlanner.accessMaskForLayout(feedbackLayout);
        int stageMask = VulkanSynchronizationPlanner.stageMaskForLayout(feedbackLayout);

        assertTrue((accessMask & VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT) != 0);
        assertTrue((accessMask & VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT) != 0);
        assertTrue((accessMask & VK10.VK_ACCESS_SHADER_READ_BIT) != 0);
        assertTrue((stageMask & VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT) != 0);
        assertTrue((stageMask & VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT) != 0);
        assertTrue((stageMask & VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT) != 0);
        assertTrue((stageMask & VK10.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT) != 0);
    }

    @Test
    public void bufferTransferWriteVisibilityPreservesAllCurrentConsumers() {
        VulkanSynchronizationPlanner.BufferBarrierPlan plan =
            VulkanSynchronizationPlanner.planBufferTransferWriteVisibility(16L, 128L).orElseThrow();

        assertEquals(VK10.VK_ACCESS_TRANSFER_WRITE_BIT, plan.srcAccessMask());
        assertTrue((plan.dstAccessMask() & VK10.VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT) != 0);
        assertTrue((plan.dstAccessMask() & VK10.VK_ACCESS_INDEX_READ_BIT) != 0);
        assertTrue((plan.dstAccessMask() & VK10.VK_ACCESS_UNIFORM_READ_BIT) != 0);
        assertTrue((plan.dstAccessMask() & VK10.VK_ACCESS_SHADER_READ_BIT) != 0);
        assertTrue((plan.dstAccessMask() & VK10.VK_ACCESS_SHADER_WRITE_BIT) != 0);
        assertTrue((plan.dstAccessMask() & VK10.VK_ACCESS_INDIRECT_COMMAND_READ_BIT) != 0);
        assertTrue((plan.dstAccessMask() & VK10.VK_ACCESS_TRANSFER_READ_BIT) != 0);
        assertTrue((plan.dstStageMask() & VK10.VK_PIPELINE_STAGE_VERTEX_INPUT_BIT) != 0);
        assertTrue((plan.dstStageMask() & VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT) != 0);
        assertTrue((plan.dstStageMask() & VK10.VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT) != 0);
        assertEquals(16L, plan.offset());
        assertEquals(128L, plan.size());
    }

    @Test
    public void zeroLengthBufferTransferWriteNeedsNoBarrier() {
        assertTrue(VulkanSynchronizationPlanner.planBufferTransferWriteVisibility(0L, 0L).isEmpty());
    }

    @Test
    public void typedResourceBarrierKeepsOutsideRenderPassShaderAndTransferStages() {
        VulkanSynchronizationPlanner.MemoryBarrierPlan plan =
            VulkanSynchronizationPlanner.planResourceBarrier(
                VulkanicResourceBarriers.computeWritesVisibleToTextureSampling(),
                false
            );

        assertTrue((plan.srcStageMask() & VK10.VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT) != 0);
        assertTrue((plan.srcStageMask() & VK10.VK_PIPELINE_STAGE_TRANSFER_BIT) != 0);
        assertTrue((plan.dstStageMask() & VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT) != 0);
        assertTrue((plan.srcAccessMask() & VK10.VK_ACCESS_TRANSFER_WRITE_BIT) != 0);
        assertTrue((plan.srcAccessMask() & VK10.VK_ACCESS_SHADER_WRITE_BIT) != 0);
        assertTrue((plan.dstAccessMask() & VK10.VK_ACCESS_SHADER_READ_BIT) != 0);
        assertEquals(0, plan.dependencyFlags());
    }

    @Test
    public void renderPassResourceBarrierUsesOnlyFramebufferLegalStages() {
        VulkanSynchronizationPlanner.MemoryBarrierPlan plan =
            VulkanSynchronizationPlanner.planResourceBarrier(
                VulkanicResourceBarriers.computeWritesVisibleToTextureSampling(),
                true
            );

        assertEquals(0, plan.srcStageMask() & VK10.VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT);
        assertEquals(0, plan.srcStageMask() & VK10.VK_PIPELINE_STAGE_TRANSFER_BIT);
        assertEquals(0, plan.dstStageMask() & VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT);
        assertTrue((plan.srcStageMask() & VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT) != 0);
        assertTrue((plan.dstStageMask() & VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT) != 0);
        assertTrue((plan.srcAccessMask() & VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT) != 0);
        assertTrue((plan.dstAccessMask() & VK10.VK_ACCESS_COLOR_ATTACHMENT_READ_BIT) != 0);
        assertEquals(VK10.VK_DEPENDENCY_BY_REGION_BIT, plan.dependencyFlags());
    }

    @Test
    public void conservativeBarrierPreservesInsideAndOutsideRenderPassPolicies() {
        VulkanSynchronizationPlanner.MemoryBarrierPlan outside =
            VulkanSynchronizationPlanner.planConservativeMemoryBarrier(false);
        VulkanSynchronizationPlanner.MemoryBarrierPlan inside =
            VulkanSynchronizationPlanner.planConservativeMemoryBarrier(true);

        assertEquals(VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, outside.srcStageMask());
        assertEquals(VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, outside.dstStageMask());
        assertEquals(0, outside.dependencyFlags());
        assertEquals(VK10.VK_ACCESS_MEMORY_WRITE_BIT, outside.srcAccessMask());
        assertEquals(VK10.VK_ACCESS_MEMORY_READ_BIT | VK10.VK_ACCESS_MEMORY_WRITE_BIT, outside.dstAccessMask());

        assertTrue((inside.srcStageMask() & VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT) != 0);
        assertTrue((inside.srcAccessMask() & VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT) != 0);
        assertEquals(VK10.VK_DEPENDENCY_BY_REGION_BIT, inside.dependencyFlags());
    }
}
