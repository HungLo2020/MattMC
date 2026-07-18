package net.vulkanic;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VulkanicLegacyCompatibilityAdapterTest {
    @Test
    void equivalentCompatibilityStateProducesEquivalentImmutableRequests() {
        VulkanicLegacyCompatibilityAdapter.RenderPassSnapshot left = renderPassSnapshot(
            "world",
            colorAttachment(0, "texture:10"),
            depthAttachment(1, "texture:11")
        );
        VulkanicLegacyCompatibilityAdapter.RenderPassSnapshot right = renderPassSnapshot(
            "world",
            colorAttachment(0, "texture:10"),
            depthAttachment(1, "texture:11")
        );

        assertEquals(
            VulkanicLegacyCompatibilityAdapter.renderPassRequest(left),
            VulkanicLegacyCompatibilityAdapter.renderPassRequest(right)
        );
        assertEquals(
            VulkanicLegacyCompatibilityAdapter.planRenderPass(left).orderedUses(),
            VulkanicLegacyCompatibilityAdapter.planRenderPass(right).orderedUses()
        );
    }

    @Test
    void mutationAfterSnapshotCreationCannotChangePassRequest() {
        List<VulkanicLegacyCompatibilityAdapter.AttachmentSnapshot> mutableAttachments = new ArrayList<>();
        mutableAttachments.add(colorAttachment(0, "texture:20"));
        VulkanicLegacyCompatibilityAdapter.RenderPassSnapshot snapshot =
            renderPassSnapshot("gui", mutableAttachments);
        VulkanicPassResourceModel.PassRequest request =
            VulkanicLegacyCompatibilityAdapter.renderPassRequest(snapshot);

        mutableAttachments.add(colorAttachment(1, "texture:21"));

        assertEquals(1, snapshot.attachments().size());
        assertEquals(1, request.attachments().size());
        assertThrows(UnsupportedOperationException.class, () -> request.attachments().add(colorAttachment(2, "texture:22").toAttachmentUse()));
    }

    @Test
    void representativeRenderPassesDeclareMrtDepthOnlyAndFeedbackLoopIntent() {
        VulkanicPassResourceModel.PassExecutionPlan mrt = VulkanicLegacyCompatibilityAdapter.planRenderPass(
            renderPassSnapshot(
                "iris:composite",
                colorAttachment(0, "texture:30"),
                colorAttachment(1, "texture:31"),
                depthAttachment(2, "texture:32")
            )
        );
        assertEquals(3, mrt.orderedUses().size());
        assertEquals(VulkanicPassResourceModel.ResourceKind.COLOR_ATTACHMENT, mrt.orderedUses().get(0).kind());
        assertEquals(VulkanicPassResourceModel.ResourceKind.DEPTH_ATTACHMENT, mrt.orderedUses().get(2).kind());

        VulkanicPassResourceModel.PassExecutionPlan depthOnly = VulkanicLegacyCompatibilityAdapter.planRenderPass(
            renderPassSnapshot("iris:shadow", depthAttachment(0, "texture:shadow-depth"))
        );
        assertEquals(VulkanicResourceUsage.DEPTH_ATTACHMENT_WRITE, depthOnly.orderedUses().get(0).usage());

        VulkanicPassResourceModel.PassExecutionPlan feedback = VulkanicLegacyCompatibilityAdapter.planRenderPass(
            renderPassSnapshot("iris:feedback", feedbackAttachment(0, "texture:history"))
        );
        assertTrue(feedback.orderedUses().get(0).feedbackLoop());
        assertEquals(VulkanicResourceUsage.ATTACHMENT_FEEDBACK_LOOP, feedback.orderedUses().get(0).usage());
    }

    @Test
    void representativeWorldEntityGuiDhAndShaderpackDrawsUseOneDrawContract() {
        List<String> sources = List.of(
            "world:sodium-terrain:solid",
            "entity:player:armor_cutout_no_cull",
            "gui:item:hotbar",
            "dh:lod:opaque",
            "iris:composite3:fullscreen"
        );

        for (String source : sources) {
            VulkanicPassResourceModel.PassExecutionPlan plan = VulkanicLegacyCompatibilityAdapter.planDraw(
                new VulkanicLegacyCompatibilityAdapter.DrawSnapshot(
                    source,
                    List.of(new VulkanicLegacyCompatibilityAdapter.VertexBufferSnapshot(0, "vbo:" + source, 16, 8, false)),
                    Optional.empty(),
                    List.of(VulkanicLegacyCompatibilityAdapter.sampledTextureUse(
                        "atlas",
                        "texture:atlas",
                        VulkanicPassResourceModel.Subresource.color(0, 1, 0, 1),
                        source + ":sampler:atlas",
                        0
                    )),
                    List.of(),
                    VulkanicLegacyCompatibilityAdapter.DrawCommandSnapshot.arrays(2, 3, 1),
                    false,
                    false
                )
            );

            assertEquals(VulkanicPassResourceModel.PassKind.RENDER, plan.request().kind());
            assertEquals("draw:" + source, plan.request().label());
            assertEquals(2, plan.orderedUses().size());
            assertEquals("texture:atlas", plan.orderedUses().get(0).resource().stableKey());
            assertEquals("vbo:" + source, plan.orderedUses().get(1).resource().stableKey());
            assertEquals(32, plan.orderedUses().get(1).subresource().baseMipLevel());
            assertEquals(24, plan.orderedUses().get(1).subresource().levelCount());
        }
    }

    @Test
    void indexedDrawDeclaresVertexAndIndexConsumedRanges() {
        VulkanicPassResourceModel.PassExecutionPlan plan = VulkanicLegacyCompatibilityAdapter.planDraw(
            new VulkanicLegacyCompatibilityAdapter.DrawSnapshot(
                "world:sodium-terrain:indexed",
                List.of(new VulkanicLegacyCompatibilityAdapter.VertexBufferSnapshot(0, "vbo:terrain", 64, 16, false)),
                Optional.of(new VulkanicLegacyCompatibilityAdapter.IndexBufferSnapshot("ibo:terrain", 128, 2)),
                List.of(),
                List.of(),
                VulkanicLegacyCompatibilityAdapter.DrawCommandSnapshot.indexed(4, 6, 2, 1),
                false,
                false
            )
        );

        assertEquals(2, plan.orderedUses().size());
        assertEquals("vbo:terrain", plan.orderedUses().get(0).resource().stableKey());
        assertEquals(96, plan.orderedUses().get(0).subresource().baseMipLevel());
        assertEquals(96, plan.orderedUses().get(0).subresource().levelCount());
        assertEquals("ibo:terrain", plan.orderedUses().get(1).resource().stableKey());
        assertEquals(136, plan.orderedUses().get(1).subresource().baseMipLevel());
        assertEquals(12, plan.orderedUses().get(1).subresource().levelCount());
    }

    @Test
    void computeDirectIndirectStorageAndUniformResourcesUseOneContract() {
        VulkanicPassResourceModel.ResourceUse storage = VulkanicLegacyCompatibilityAdapter.bufferUse(
            "ssbo",
            VulkanicPassResourceModel.ResourceKind.STORAGE_BUFFER,
            "buffer:ssbo",
            VulkanicPassResourceModel.Access.READ_WRITE,
            0,
            256,
            VulkanicResourceUsage.STORAGE_READ_WRITE,
            "compute:write-ssbo",
            0
        );
        VulkanicPassResourceModel.ResourceUse uniform = VulkanicLegacyCompatibilityAdapter.uniformBufferUse(
            "ubo",
            "buffer:ubo",
            32,
            64,
            "compute:read-ubo",
            1
        );

        VulkanicPassResourceModel.PassExecutionPlan direct = VulkanicLegacyCompatibilityAdapter.planCompute(
            new VulkanicLegacyCompatibilityAdapter.ComputeSnapshot(
                "compute:lighting",
                "dispatch",
                List.of(storage, uniform),
                List.of(),
                Optional.empty(),
                false,
                false
            )
        );
        assertEquals(2, direct.orderedUses().size());

        VulkanicPassResourceModel.PassExecutionPlan indirect = VulkanicLegacyCompatibilityAdapter.planCompute(
            new VulkanicLegacyCompatibilityAdapter.ComputeSnapshot(
                "compute:lighting",
                "dispatch-indirect",
                List.of(storage),
                List.of(),
                Optional.of(new VulkanicLegacyCompatibilityAdapter.IndirectBufferSnapshot("buffer:indirect", 12)),
                false,
                false
            )
        );
        assertEquals(2, indirect.orderedUses().size());
        assertEquals(VulkanicPassResourceModel.ResourceKind.INDIRECT_BUFFER, indirect.orderedUses().get(1).kind());
    }

    @Test
    void transferAndReadbackSnapshotsDeclareOperationIntent() {
        VulkanicPassResourceModel.PassExecutionPlan upload = VulkanicLegacyCompatibilityAdapter.planTransfer(
            new VulkanicLegacyCompatibilityAdapter.TransferSnapshot(
                VulkanicPassResourceModel.PassKind.TRANSFER,
                "texture-transfer:upload",
                "copy-buffer-to-image",
                "texture-transfer-40",
                VulkanicPassResourceModel.ResourceKind.TRANSFER_DESTINATION,
                "texture:40",
                VulkanicPassResourceModel.Access.WRITE,
                VulkanicPassResourceModel.Subresource.color(0, 1, 0, 1),
                VulkanicResourceUsage.TRANSFER_DST,
                "texture-transfer:upload",
                List.of("transition-before-copy", "publish-layout-after-copy"),
                false,
                false
            )
        );
        assertEquals(VulkanicPassResourceModel.PassKind.TRANSFER, upload.request().kind());
        assertEquals(VulkanicResourceUsage.TRANSFER_DST, upload.orderedUses().get(0).usage());

        VulkanicPassResourceModel.PassExecutionPlan readback = VulkanicLegacyCompatibilityAdapter.planTransfer(
            new VulkanicLegacyCompatibilityAdapter.TransferSnapshot(
                VulkanicPassResourceModel.PassKind.READBACK,
                "texture-transfer:readback",
                "copy-image-to-buffer",
                "texture-readback-41",
                VulkanicPassResourceModel.ResourceKind.READBACK_SOURCE,
                "texture:41",
                VulkanicPassResourceModel.Access.READ,
                VulkanicPassResourceModel.Subresource.color(0, 1, 0, 1),
                VulkanicResourceUsage.TRANSFER_SRC,
                "texture-transfer:readback",
                List.of(),
                false,
                false
            )
        );
        assertEquals(VulkanicPassResourceModel.PassKind.READBACK, readback.request().kind());
        assertEquals(List.of("transition-before-operation", "publish-usage-after-operation"), readback.request().requiredOrdering());
    }

    @Test
    void contradictoryCompatibilityStateIsRejectedBeforeBackendPlanning() {
        VulkanicPassResourceModel.ResourceUse undeclaredFeedbackRead = VulkanicLegacyCompatibilityAdapter.sampledTextureUse(
            "colortex0",
            "texture:50",
            VulkanicPassResourceModel.Subresource.color(0, 1, 0, 1),
            "iris:composite:sampler:colortex0",
            1
        );

        VulkanicLegacyCompatibilityAdapter.RenderPassSnapshot snapshot =
            new VulkanicLegacyCompatibilityAdapter.RenderPassSnapshot(
                "bad-feedback",
                List.of(colorAttachment(0, "texture:50")),
                List.of(undeclaredFeedbackRead),
                List.of(),
                List.of(),
                List.of(),
                false,
                false
            );

        assertThrows(IllegalArgumentException.class, () -> VulkanicLegacyCompatibilityAdapter.planRenderPass(snapshot));
    }

    @Test
    void abandonedAndDeviceLostSnapshotsArePreserved() {
        VulkanicPassResourceModel.PassRequest request = VulkanicLegacyCompatibilityAdapter.renderPassRequest(
            new VulkanicLegacyCompatibilityAdapter.RenderPassSnapshot(
                "abandoned",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("discard-unpublished-state"),
                true,
                true
            )
        );

        assertTrue(request.abandoned());
        assertTrue(request.deviceLost());
        assertEquals(List.of("discard-unpublished-state"), request.requiredOrdering());
    }

    private static VulkanicLegacyCompatibilityAdapter.RenderPassSnapshot renderPassSnapshot(
        String label,
        VulkanicLegacyCompatibilityAdapter.AttachmentSnapshot... attachments
    ) {
        return renderPassSnapshot(label, List.of(attachments));
    }

    private static VulkanicLegacyCompatibilityAdapter.RenderPassSnapshot renderPassSnapshot(
        String label,
        List<VulkanicLegacyCompatibilityAdapter.AttachmentSnapshot> attachments
    ) {
        return new VulkanicLegacyCompatibilityAdapter.RenderPassSnapshot(
            label,
            attachments,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            false,
            false
        );
    }

    private static VulkanicLegacyCompatibilityAdapter.AttachmentSnapshot colorAttachment(int index, String stableKey) {
        return new VulkanicLegacyCompatibilityAdapter.AttachmentSnapshot(
            index,
            "color" + index,
            VulkanicPassResourceModel.ResourceKind.COLOR_ATTACHMENT,
            stableKey,
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

    private static VulkanicLegacyCompatibilityAdapter.AttachmentSnapshot feedbackAttachment(int index, String stableKey) {
        return new VulkanicLegacyCompatibilityAdapter.AttachmentSnapshot(
            index,
            "color" + index,
            VulkanicPassResourceModel.ResourceKind.COLOR_ATTACHMENT,
            stableKey,
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
    }

    private static VulkanicLegacyCompatibilityAdapter.AttachmentSnapshot depthAttachment(int index, String stableKey) {
        return new VulkanicLegacyCompatibilityAdapter.AttachmentSnapshot(
            index,
            "depth",
            VulkanicPassResourceModel.ResourceKind.DEPTH_ATTACHMENT,
            stableKey,
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
}
