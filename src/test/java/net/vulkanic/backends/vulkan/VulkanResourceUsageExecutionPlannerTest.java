package net.vulkanic.backends.vulkan;

import net.vulkanic.VulkanicPassResourceModel;
import net.vulkanic.VulkanicPassResourcePlanner;
import net.vulkanic.VulkanicResourceUsage;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.EXTAttachmentFeedbackLoopLayout;
import org.lwjgl.vulkan.VK10;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VulkanResourceUsageExecutionPlannerTest {
    @Test
    void consumesCanonicalGalPlanAsVulkanExecutionIntent() {
        VulkanicPassResourceModel.ResourceUse sampled = VulkanicPassResourceModel.ResourceUse.of(
            VulkanicPassResourceModel.ResourceIdentity.of(
                "colortex0",
                VulkanicPassResourceModel.ResourceKind.SAMPLED_TEXTURE,
                "texture:10"
            ),
            VulkanicPassResourceModel.Access.READ,
            VulkanicPassResourceModel.Subresource.color(0, 1, 0, 1),
            VulkanicResourceUsage.SAMPLED_READ,
            "sampler:colortex0",
            false,
            2
        );
        VulkanicPassResourceModel.ResourceUse vertex = VulkanicPassResourceModel.ResourceUse.of(
            VulkanicPassResourceModel.ResourceIdentity.of(
                "terrain-vbo",
                VulkanicPassResourceModel.ResourceKind.VERTEX_BUFFER,
                "buffer:4"
            ),
            VulkanicPassResourceModel.Access.READ,
            VulkanicPassResourceModel.Subresource.bufferRange(64, 128),
            VulkanicResourceUsage.INFERRED,
            "vertex-buffer",
            false,
            1
        );
        VulkanicPassResourceModel.PassExecutionPlan plan = VulkanicPassResourcePlanner.plan(
            new VulkanicPassResourceModel.PassRequest(
                VulkanicPassResourceModel.PassKind.RENDER,
                "terrain-draw",
                List.of(),
                List.of(sampled, vertex),
                List.of(),
                List.of(),
                List.of("resources-visible-before-draw"),
                false,
                false
            )
        );

        VulkanResourceUsageExecutionPlanner.ExecutionPlan execution =
            VulkanResourceUsageExecutionPlanner.plan(plan);

        assertEquals(VulkanicPassResourceModel.PassKind.RENDER, execution.kind());
        assertEquals("terrain-draw", execution.label());
        assertEquals(2, execution.orderedUses().size());
        assertEquals("buffer:4", execution.orderedUses().get(0).stableKey());
        assertEquals("texture:10", execution.orderedUses().get(1).stableKey());
    }

    @Test
    void rejectsNonCanonicalRequestPlanBeforeNativeExecution() {
        VulkanicPassResourceModel.ResourceUse use = VulkanicPassResourceModel.ResourceUse.of(
            VulkanicPassResourceModel.ResourceIdentity.of(
                "upload",
                VulkanicPassResourceModel.ResourceKind.TRANSFER_DESTINATION,
                "texture:11"
            ),
            VulkanicPassResourceModel.Access.WRITE,
            VulkanicPassResourceModel.Subresource.color(0, 1, 0, 1),
            VulkanicResourceUsage.TRANSFER_DST,
            "upload",
            false,
            0
        );
        VulkanicPassResourceModel.PassRequest request = new VulkanicPassResourceModel.PassRequest(
            VulkanicPassResourceModel.PassKind.TRANSFER,
            "upload",
            List.of(),
            List.of(use),
            List.of(),
            List.of(),
            List.of(),
            false,
            false
        );
        VulkanicPassResourceModel.PassExecutionPlan invalid =
            new VulkanicPassResourceModel.PassExecutionPlan(request, List.of(), List.of());

        assertThrows(IllegalArgumentException.class, () -> VulkanResourceUsageExecutionPlanner.plan(invalid));
    }

    @Test
    void imageTransitionsAreDerivedFromSemanticUsage() {
        VulkanicPassResourceModel.ResourceUse sampledDepth = VulkanicPassResourceModel.ResourceUse.of(
            VulkanicPassResourceModel.ResourceIdentity.of(
                "shadow-depth",
                VulkanicPassResourceModel.ResourceKind.SAMPLED_TEXTURE,
                "texture:shadow"
            ),
            VulkanicPassResourceModel.Access.READ,
            VulkanicPassResourceModel.Subresource.depth(1, 2, 3, 4),
            VulkanicResourceUsage.SAMPLED_READ,
            "sampler:shadow",
            false,
            0
        );

        VulkanSynchronizationPlanner.ImageBarrierPlan plan =
            VulkanResourceUsageExecutionPlanner.planImageTransitionForUse(
                VK10.VK_IMAGE_ASPECT_DEPTH_BIT,
                VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL,
                sampledDepth,
                true,
                false,
                VK10.VK_IMAGE_LAYOUT_GENERAL
            ).orElseThrow();

        assertEquals(VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_READ_ONLY_OPTIMAL, plan.newLayout());
        assertEquals(VK10.VK_IMAGE_ASPECT_DEPTH_BIT, plan.range().aspectMask());
        assertEquals(1, plan.range().baseMipLevel());
        assertEquals(2, plan.range().levelCount());
        assertEquals(3, plan.range().baseArrayLayer());
        assertEquals(4, plan.range().layerCount());
    }

    @Test
    void explicitFeedbackLoopTransitionKeepsExistingLayoutPolicy() {
        VulkanSynchronizationPlanner.ImageBarrierPlan plan =
            VulkanResourceUsageExecutionPlanner.planExplicitImageTransition(
                VK10.VK_IMAGE_ASPECT_COLOR_BIT,
                VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT,
                0,
                1,
                0,
                1
            ).orElseThrow();

        assertEquals(
            EXTAttachmentFeedbackLoopLayout.VK_IMAGE_LAYOUT_ATTACHMENT_FEEDBACK_LOOP_OPTIMAL_EXT,
            plan.newLayout()
        );
        assertTrue((plan.dstAccessMask() & VK10.VK_ACCESS_SHADER_READ_BIT) != 0);
        assertTrue((plan.dstAccessMask() & VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT) != 0);
    }

    @Test
    void bufferTransferVisibilityIsPlannedFromSemanticTransferWrite() {
        VulkanSynchronizationPlanner.BufferBarrierPlan plan =
            VulkanResourceUsageExecutionPlanner.planBufferTransferWriteVisibility(32, 96).orElseThrow();

        assertEquals(32L, plan.offset());
        assertEquals(96L, plan.size());
        assertEquals(VK10.VK_ACCESS_TRANSFER_WRITE_BIT, plan.srcAccessMask());
        assertTrue((plan.dstAccessMask() & VK10.VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT) != 0);
        assertTrue((plan.dstAccessMask() & VK10.VK_ACCESS_SHADER_READ_BIT) != 0);
        assertTrue((plan.dstStageMask() & VK10.VK_PIPELINE_STAGE_TRANSFER_BIT) != 0);
    }
}
