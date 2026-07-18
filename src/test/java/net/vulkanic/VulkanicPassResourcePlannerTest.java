package net.vulkanic;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VulkanicPassResourcePlannerTest {
    @Test
    void colorDepthMrtAndDepthOnlyPassesDeclareAttachmentUsage() {
        VulkanicPassResourceModel.PassExecutionPlan mrt = VulkanicPassResourcePlanner.plan(
            new VulkanicPassResourceModel.PassRequest(
                VulkanicPassResourceModel.PassKind.RENDER,
                "mrt",
                List.of(
                    colorAttachment(0, "texture:10"),
                    colorAttachment(1, "texture:11"),
                    depthAttachment(2, "texture:12")
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false,
                false
            )
        );

        assertEquals(3, mrt.orderedUses().size());
        assertEquals(VulkanicPassResourceModel.ResourceKind.COLOR_ATTACHMENT, mrt.orderedUses().get(0).kind());
        assertEquals(VulkanicPassResourceModel.ResourceKind.COLOR_ATTACHMENT, mrt.orderedUses().get(1).kind());
        assertEquals(VulkanicPassResourceModel.ResourceKind.DEPTH_ATTACHMENT, mrt.orderedUses().get(2).kind());

        VulkanicPassResourceModel.PassExecutionPlan depthOnly = VulkanicPassResourcePlanner.plan(
            new VulkanicPassResourceModel.PassRequest(
                VulkanicPassResourceModel.PassKind.RENDER,
                "depth-only",
                List.of(depthAttachment(0, "texture:20")),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false,
                false
            )
        );

        assertEquals(1, depthOnly.orderedUses().size());
        assertEquals(VulkanicResourceUsage.DEPTH_ATTACHMENT_WRITE, depthOnly.orderedUses().get(0).usage());
    }

    @Test
    void sampledAttachmentFeedbackLoopMustBeDeclaredExplicitly() {
        VulkanicPassResourceModel.ResourceIdentity color = VulkanicPassResourceModel.ResourceIdentity.of(
            "color0",
            VulkanicPassResourceModel.ResourceKind.COLOR_ATTACHMENT,
            "texture:30"
        );
        VulkanicPassResourceModel.ResourceUse sampledRead = VulkanicPassResourceModel.ResourceUse.of(
            color,
            VulkanicPassResourceModel.Access.READ,
            VulkanicPassResourceModel.Subresource.color(0, 1, 0, 1),
            VulkanicResourceUsage.SAMPLED_READ,
            "sampler:colortex0",
            false,
            1
        );

        assertThrows(IllegalArgumentException.class, () -> VulkanicPassResourcePlanner.plan(
            new VulkanicPassResourceModel.PassRequest(
                VulkanicPassResourceModel.PassKind.RENDER,
                "undeclared-feedback",
                List.of(colorAttachment(0, "texture:30")),
                List.of(sampledRead),
                List.of(),
                List.of(),
                List.of(),
                false,
                false
            )
        ));

        VulkanicPassResourceModel.AttachmentUse feedbackAttachment = new VulkanicPassResourceModel.AttachmentUse(
            0,
            color,
            VulkanicPassResourceModel.Subresource.color(0, 1, 0, 1),
            VulkanicRenderPassDescriptor.LoadOp.LOAD,
            VulkanicRenderPassDescriptor.StoreOp.STORE,
            OptionalInt.empty(),
            OptionalDouble.empty(),
            VulkanicResourceUsage.SAMPLED_READ,
            VulkanicResourceUsage.ATTACHMENT_FEEDBACK_LOOP,
            VulkanicResourceUsage.SAMPLED_READ,
            true
        );
        VulkanicPassResourceModel.ResourceUse declaredRead = VulkanicPassResourceModel.ResourceUse.of(
            color,
            VulkanicPassResourceModel.Access.READ,
            VulkanicPassResourceModel.Subresource.color(0, 1, 0, 1),
            VulkanicResourceUsage.SAMPLED_READ,
            "sampler:colortex0",
            true,
            1
        );

        VulkanicPassResourceModel.PassExecutionPlan feedback = VulkanicPassResourcePlanner.plan(
            new VulkanicPassResourceModel.PassRequest(
                VulkanicPassResourceModel.PassKind.RENDER,
                "declared-feedback",
                List.of(feedbackAttachment),
                List.of(declaredRead),
                List.of(),
                List.of(),
                List.of(),
                false,
                false
            )
        );
        assertEquals(2, feedback.orderedUses().size());
        assertTrue(feedback.orderedUses().stream().allMatch(VulkanicPassResourceModel.ResourceUse::feedbackLoop));
    }

    @Test
    void computeTransferReadbackAndBufferUsesShareOneContract() {
        VulkanicPassResourceModel.ResourceUse storageBuffer = bufferUse(
            "storage:ssbo",
            VulkanicPassResourceModel.ResourceKind.STORAGE_BUFFER,
            VulkanicPassResourceModel.Access.READ_WRITE,
            VulkanicResourceUsage.STORAGE_READ_WRITE,
            0,
            256
        );
        VulkanicPassResourceModel.PassExecutionPlan compute = VulkanicPassResourcePlanner.plan(
            request(VulkanicPassResourceModel.PassKind.COMPUTE, "compute", storageBuffer)
        );
        assertEquals(VulkanicPassResourceModel.PassKind.COMPUTE, compute.request().kind());

        VulkanicPassResourceModel.ResourceUse vertexRead = bufferUse(
            "vertex:vbo",
            VulkanicPassResourceModel.ResourceKind.VERTEX_BUFFER,
            VulkanicPassResourceModel.Access.READ,
            VulkanicResourceUsage.INFERRED,
            16,
            128
        );
        VulkanicPassResourceModel.ResourceUse indexRead = bufferUse(
            "index:ibo",
            VulkanicPassResourceModel.ResourceKind.INDEX_BUFFER,
            VulkanicPassResourceModel.Access.READ,
            VulkanicResourceUsage.INFERRED,
            0,
            48
        );
        VulkanicPassResourceModel.ResourceUse indirectRead = bufferUse(
            "indirect:draw",
            VulkanicPassResourceModel.ResourceKind.INDIRECT_BUFFER,
            VulkanicPassResourceModel.Access.READ,
            VulkanicResourceUsage.INFERRED,
            0,
            20
        );
        VulkanicPassResourceModel.PassExecutionPlan graphics = VulkanicPassResourcePlanner.plan(
            new VulkanicPassResourceModel.PassRequest(
                VulkanicPassResourceModel.PassKind.RENDER,
                "graphics-buffers",
                List.of(),
                List.of(vertexRead, indexRead, indirectRead),
                List.of(),
                List.of(),
                List.of(),
                false,
                false
            )
        );
        assertEquals(3, graphics.orderedUses().size());

        VulkanicPassResourceModel.ResourceUse transferWrite = imageUse(
            "texture:40",
            VulkanicPassResourceModel.ResourceKind.TRANSFER_DESTINATION,
            VulkanicPassResourceModel.Access.WRITE,
            VulkanicResourceUsage.TRANSFER_DST
        );
        VulkanicPassResourceModel.ResourceUse sampledRead = imageUse(
            "texture:40",
            VulkanicPassResourceModel.ResourceKind.SAMPLED_TEXTURE,
            VulkanicPassResourceModel.Access.READ,
            VulkanicResourceUsage.SAMPLED_READ
        );

        VulkanicPassResourceModel.PassExecutionPlan transfer = VulkanicPassResourcePlanner.plan(
            request(VulkanicPassResourceModel.PassKind.TRANSFER, "upload", transferWrite)
        );
        VulkanicPassResourceModel.PassExecutionPlan readback = VulkanicPassResourcePlanner.plan(
            request(
                VulkanicPassResourceModel.PassKind.READBACK,
                "readback",
                imageUse(
                    "texture:41",
                    VulkanicPassResourceModel.ResourceKind.READBACK_SOURCE,
                    VulkanicPassResourceModel.Access.READ,
                    VulkanicResourceUsage.TRANSFER_SRC
                )
            )
        );

        assertEquals(VulkanicResourceUsage.TRANSFER_DST, transfer.orderedUses().get(0).usage());
        assertEquals(VulkanicPassResourceModel.PassKind.READBACK, readback.request().kind());
        assertTrue(sampledRead.reads());
    }

    @Test
    void subresourceSpecificMipLayerAccessAllowsIndependentWrites() {
        VulkanicPassResourceModel.ResourceIdentity texture = VulkanicPassResourceModel.ResourceIdentity.of(
            "mip-chain",
            VulkanicPassResourceModel.ResourceKind.STORAGE_TEXTURE,
            "texture:50"
        );
        VulkanicPassResourceModel.ResourceUse mip0 = VulkanicPassResourceModel.ResourceUse.of(
            texture,
            VulkanicPassResourceModel.Access.WRITE,
            VulkanicPassResourceModel.Subresource.color(0, 1, 0, 1),
            VulkanicResourceUsage.STORAGE_READ_WRITE,
            "write-mip0",
            false,
            0
        );
        VulkanicPassResourceModel.ResourceUse mip1 = VulkanicPassResourceModel.ResourceUse.of(
            texture,
            VulkanicPassResourceModel.Access.WRITE,
            VulkanicPassResourceModel.Subresource.color(1, 1, 0, 1),
            VulkanicResourceUsage.STORAGE_READ_WRITE,
            "write-mip1",
            false,
            1
        );

        VulkanicPassResourceModel.PassExecutionPlan plan = VulkanicPassResourcePlanner.plan(
            request(VulkanicPassResourceModel.PassKind.COMPUTE, "independent-mips", mip0, mip1)
        );

        assertEquals(2, plan.orderedUses().size());
    }

    @Test
    void passAbandonmentAndDeviceLossAreRepresentedWithoutPlanningNativeCommands() {
        VulkanicPassResourceModel.PassRequest abandoned = new VulkanicPassResourceModel.PassRequest(
            VulkanicPassResourceModel.PassKind.RENDER,
            "abandoned",
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of("discard-unpublished-state"),
            true,
            true
        );

        VulkanicPassResourceModel.PassExecutionPlan plan = VulkanicPassResourcePlanner.plan(abandoned);

        assertTrue(plan.request().abandoned());
        assertTrue(plan.request().deviceLost());
        assertEquals(List.of("discard-unpublished-state"), plan.request().requiredOrdering());
    }

    private static VulkanicPassResourceModel.PassRequest request(
        VulkanicPassResourceModel.PassKind kind,
        String label,
        VulkanicPassResourceModel.ResourceUse... uses
    ) {
        return new VulkanicPassResourceModel.PassRequest(
            kind,
            label,
            List.of(),
            List.of(uses),
            List.of(),
            List.of(),
            List.of(),
            false,
            false
        );
    }

    private static VulkanicPassResourceModel.AttachmentUse colorAttachment(int index, String stableKey) {
        return new VulkanicPassResourceModel.AttachmentUse(
            index,
            VulkanicPassResourceModel.ResourceIdentity.of(
                "color" + index,
                VulkanicPassResourceModel.ResourceKind.COLOR_ATTACHMENT,
                stableKey
            ),
            VulkanicPassResourceModel.Subresource.color(0, 1, 0, 1),
            VulkanicRenderPassDescriptor.LoadOp.LOAD,
            VulkanicRenderPassDescriptor.StoreOp.STORE,
            OptionalInt.empty(),
            OptionalDouble.empty(),
            VulkanicResourceUsage.SAMPLED_READ,
            VulkanicResourceUsage.COLOR_ATTACHMENT_WRITE,
            VulkanicResourceUsage.SAMPLED_READ,
            false
        );
    }

    private static VulkanicPassResourceModel.AttachmentUse depthAttachment(int index, String stableKey) {
        return new VulkanicPassResourceModel.AttachmentUse(
            index,
            VulkanicPassResourceModel.ResourceIdentity.of(
                "depth",
                VulkanicPassResourceModel.ResourceKind.DEPTH_ATTACHMENT,
                stableKey
            ),
            VulkanicPassResourceModel.Subresource.depth(0, 1, 0, 1),
            VulkanicRenderPassDescriptor.LoadOp.LOAD,
            VulkanicRenderPassDescriptor.StoreOp.STORE,
            OptionalInt.empty(),
            OptionalDouble.empty(),
            VulkanicResourceUsage.SAMPLED_READ,
            VulkanicResourceUsage.DEPTH_ATTACHMENT_WRITE,
            VulkanicResourceUsage.SAMPLED_READ,
            false
        );
    }

    private static VulkanicPassResourceModel.ResourceUse bufferUse(
        String stableKey,
        VulkanicPassResourceModel.ResourceKind kind,
        VulkanicPassResourceModel.Access access,
        VulkanicResourceUsage usage,
        int offset,
        int size
    ) {
        return VulkanicPassResourceModel.ResourceUse.of(
            VulkanicPassResourceModel.ResourceIdentity.of(stableKey, kind, stableKey),
            access,
            VulkanicPassResourceModel.Subresource.bufferRange(offset, size),
            usage,
            stableKey,
            false,
            0
        );
    }

    private static VulkanicPassResourceModel.ResourceUse imageUse(
        String stableKey,
        VulkanicPassResourceModel.ResourceKind kind,
        VulkanicPassResourceModel.Access access,
        VulkanicResourceUsage usage
    ) {
        return VulkanicPassResourceModel.ResourceUse.of(
            VulkanicPassResourceModel.ResourceIdentity.of(stableKey, kind, stableKey),
            access,
            VulkanicPassResourceModel.Subresource.color(0, 1, 0, 1),
            usage,
            stableKey,
            false,
            0
        );
    }
}
