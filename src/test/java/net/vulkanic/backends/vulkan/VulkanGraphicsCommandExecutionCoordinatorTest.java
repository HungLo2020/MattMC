package net.vulkanic.backends.vulkan;

import net.vulkanic.VulkanicIndexType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static net.vulkanic.backends.vulkan.VulkanGraphicsCommandExecutionCoordinator.GraphicsCommandOperationType.BIND_DESCRIPTOR_SET;
import static net.vulkanic.backends.vulkan.VulkanGraphicsCommandExecutionCoordinator.GraphicsCommandOperationType.BIND_INDEX_BUFFER;
import static net.vulkanic.backends.vulkan.VulkanGraphicsCommandExecutionCoordinator.GraphicsCommandOperationType.BIND_PIPELINE;
import static net.vulkanic.backends.vulkan.VulkanGraphicsCommandExecutionCoordinator.GraphicsCommandOperationType.BIND_VERTEX_BUFFER;
import static net.vulkanic.backends.vulkan.VulkanGraphicsCommandExecutionCoordinator.GraphicsCommandOperationType.DYNAMIC_STATE;
import static net.vulkanic.backends.vulkan.VulkanGraphicsCommandExecutionCoordinator.GraphicsCommandOperationType.DRAW;
import static net.vulkanic.backends.vulkan.VulkanGraphicsCommandExecutionCoordinator.GraphicsCommandOperationType.PUSH_CONSTANTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VulkanGraphicsCommandExecutionCoordinatorTest {
    private static final VulkanGraphicsCommandExecutionCoordinator.CommandBufferIdentity COMMAND_BUFFER =
        new VulkanGraphicsCommandExecutionCoordinator.CommandBufferIdentity(
            11L,
            1L,
            VulkanGraphicsCommandExecutionCoordinator.CommandBufferKind.FRAME
        );

    @Test
    void firstIndexedDrawPlansOrderedGraphicsCommands() {
        VulkanGraphicsCommandExecutionCoordinator coordinator = new VulkanGraphicsCommandExecutionCoordinator();

        VulkanGraphicsCommandExecutionCoordinator.GraphicsExecutionPlan plan =
            coordinator.planGraphicsExecution(indexedRequest(pipeline(101L, 201L, "pass-a"), descriptor(201L, 301L)));

        assertTypes(plan, BIND_PIPELINE, BIND_DESCRIPTOR_SET, BIND_VERTEX_BUFFER, BIND_INDEX_BUFFER, DRAW);

        VulkanGraphicsCommandExecutionCoordinator.DrawCommandRequirement draw =
            (VulkanGraphicsCommandExecutionCoordinator.DrawCommandRequirement) plan.operations().get(4).payload();
        assertEquals(VulkanGraphicsCommandExecutionCoordinator.DrawCommandKind.INDEXED, draw.kind());
        assertEquals(4, draw.firstIndex());
        assertEquals(12, draw.indexCount());
        assertEquals(-2, draw.baseVertex());
        assertEquals(3, draw.instanceCount());
    }

    @Test
    void repeatedEquivalentDrawOnlyRecordsDrawCommand() {
        VulkanGraphicsCommandExecutionCoordinator coordinator = new VulkanGraphicsCommandExecutionCoordinator();
        coordinator.complete(coordinator.planGraphicsExecution(
            indexedRequest(pipeline(101L, 201L, "pass-a"), descriptor(201L, 301L))
        ));

        VulkanGraphicsCommandExecutionCoordinator.GraphicsExecutionPlan repeat =
            coordinator.planGraphicsExecution(indexedRequest(pipeline(101L, 201L, "pass-a"), descriptor(201L, 301L)));

        assertTypes(repeat, DRAW);
        assertEquals(1L, coordinator.skippedRedundantPipelineBindCount());
        assertEquals(1L, coordinator.skippedRedundantDescriptorBindCount());
        assertEquals(1L, coordinator.skippedRedundantVertexBindCount());
        assertEquals(1L, coordinator.skippedRedundantIndexBindCount());
    }

    @Test
    void descriptorDynamicOffsetsParticipateInBindingIdentity() {
        VulkanGraphicsCommandExecutionCoordinator coordinator = new VulkanGraphicsCommandExecutionCoordinator();
        coordinator.complete(coordinator.planGraphicsExecution(
            indexedRequest(pipeline(101L, 201L, "pass-a"), descriptor(201L, 301L, List.of(16)))
        ));

        VulkanGraphicsCommandExecutionCoordinator.GraphicsExecutionPlan changedOffset =
            coordinator.planGraphicsExecution(indexedRequest(
                pipeline(101L, 201L, "pass-a"),
                descriptor(201L, 301L, List.of(32)),
                List.of(32)
            ));

        assertTypes(changedOffset, BIND_DESCRIPTOR_SET, DRAW);
    }

    @Test
    void renderPassCompatibilityChangeInvalidatesRenderPassSensitiveBindings() {
        VulkanGraphicsCommandExecutionCoordinator coordinator = new VulkanGraphicsCommandExecutionCoordinator();
        coordinator.complete(coordinator.planGraphicsExecution(
            indexedRequest(pipeline(101L, 201L, "pass-a"), descriptor(201L, 301L))
        ));

        coordinator.beginRenderPass(COMMAND_BUFFER.handle(), "pass-b");
        VulkanGraphicsCommandExecutionCoordinator.GraphicsExecutionPlan nextPass =
            coordinator.planGraphicsExecution(indexedRequest(pipeline(101L, 201L, "pass-a"), descriptor(201L, 301L)));

        assertTypes(nextPass, BIND_PIPELINE, BIND_DESCRIPTOR_SET, DRAW);
    }

    @Test
    void commandBufferResetClearsPublishedGraphicsState() {
        VulkanGraphicsCommandExecutionCoordinator coordinator = new VulkanGraphicsCommandExecutionCoordinator();
        coordinator.complete(coordinator.planGraphicsExecution(
            indexedRequest(pipeline(101L, 201L, "pass-a"), descriptor(201L, 301L))
        ));

        coordinator.resetCommandBuffer(COMMAND_BUFFER.handle());

        assertEquals(0, coordinator.graphicsStateCountForTests());
        assertTypes(
            coordinator.planGraphicsExecution(indexedRequest(pipeline(101L, 201L, "pass-a"), descriptor(201L, 301L))),
            BIND_PIPELINE,
            BIND_DESCRIPTOR_SET,
            BIND_VERTEX_BUFFER,
            BIND_INDEX_BUFFER,
            DRAW
        );
    }

    @Test
    void abandonedGraphicsPlanDoesNotPublishState() {
        VulkanGraphicsCommandExecutionCoordinator coordinator = new VulkanGraphicsCommandExecutionCoordinator();
        VulkanGraphicsCommandExecutionCoordinator.GraphicsExecutionPlan plan =
            coordinator.planGraphicsExecution(indexedRequest(pipeline(101L, 201L, "pass-a"), descriptor(201L, 301L)));

        coordinator.abandon(plan);

        assertEquals(0, coordinator.graphicsStateCountForTests());
        assertTypes(
            coordinator.planGraphicsExecution(indexedRequest(pipeline(101L, 201L, "pass-a"), descriptor(201L, 301L))),
            BIND_PIPELINE,
            BIND_DESCRIPTOR_SET,
            BIND_VERTEX_BUFFER,
            BIND_INDEX_BUFFER,
            DRAW
        );
    }

    @Test
    void defaultRenderPassDynamicStateNormalizesSwapchainViewportAndScissor() {
        VulkanGraphicsCommandExecutionCoordinator coordinator = new VulkanGraphicsCommandExecutionCoordinator();
        VulkanGraphicsCommandExecutionCoordinator.DynamicStatePlan plan =
            coordinator.planRenderPassDefaultDynamicState(
                COMMAND_BUFFER,
                "swapchain-pass",
                context(true, 1280, 720),
                "beginRenderPass"
            );

        assertTypes(plan, DYNAMIC_STATE, DYNAMIC_STATE);
        VulkanGraphicsCommandExecutionCoordinator.DynamicStateRequirement viewport =
            (VulkanGraphicsCommandExecutionCoordinator.DynamicStateRequirement) plan.operations().get(0).payload();
        VulkanGraphicsCommandExecutionCoordinator.DynamicStateRequirement scissor =
            (VulkanGraphicsCommandExecutionCoordinator.DynamicStateRequirement) plan.operations().get(1).payload();
        assertEquals(
            new VulkanGraphicsCommandExecutionCoordinator.NormalizedViewport(0.0f, 0.0f, 1280.0f, 720.0f, 0.0f, 1.0f),
            viewport.value()
        );
        assertEquals(
            new VulkanGraphicsCommandExecutionCoordinator.NormalizedScissor(0, 0, 1280, 720),
            scissor.value()
        );
    }

    @Test
    void repeatedEquivalentViewportIsElidedAfterPublication() {
        VulkanGraphicsCommandExecutionCoordinator coordinator = new VulkanGraphicsCommandExecutionCoordinator();
        VulkanGraphicsCommandExecutionCoordinator.DynamicStatePlan first = coordinator.planViewport(
            COMMAND_BUFFER,
            "offscreen-pass",
            context(false, 640, 480),
            new VulkanGraphicsCommandExecutionCoordinator.ViewportRequest(0, 0, 640, 480, 0.0f, 1.0f),
            "setViewport"
        );
        coordinator.complete(first);

        VulkanGraphicsCommandExecutionCoordinator.DynamicStatePlan repeat = coordinator.planViewport(
            COMMAND_BUFFER,
            "offscreen-pass",
            context(false, 640, 480),
            new VulkanGraphicsCommandExecutionCoordinator.ViewportRequest(0, 0, 640, 480, 0.0f, 1.0f),
            "setViewport"
        );

        assertTypes(first, DYNAMIC_STATE);
        assertTypes(repeat);
        assertEquals(1L, coordinator.skippedRedundantDynamicStateCount());
    }

    @Test
    void offscreenViewportUsesVulkanYInversionAndFramebufferHeight() {
        VulkanGraphicsCommandExecutionCoordinator coordinator = new VulkanGraphicsCommandExecutionCoordinator();

        VulkanGraphicsCommandExecutionCoordinator.DynamicStatePlan plan = coordinator.planViewport(
            COMMAND_BUFFER,
            "offscreen-pass",
            context(false, 640, 480),
            new VulkanGraphicsCommandExecutionCoordinator.ViewportRequest(4, 10, 320, 200, 0.25f, 0.75f),
            "setViewport"
        );

        VulkanGraphicsCommandExecutionCoordinator.DynamicStateRequirement viewport =
            (VulkanGraphicsCommandExecutionCoordinator.DynamicStateRequirement) plan.operations().get(0).payload();
        assertEquals(
            new VulkanGraphicsCommandExecutionCoordinator.NormalizedViewport(4.0f, 470.0f, 320.0f, -200.0f, 0.25f, 0.75f),
            viewport.value()
        );
    }

    @Test
    void scissorEnableDisableAndClippingAreOwnedByDynamicState() {
        VulkanGraphicsCommandExecutionCoordinator coordinator = new VulkanGraphicsCommandExecutionCoordinator();
        coordinator.complete(coordinator.planScissorRect(
            COMMAND_BUFFER,
            "offscreen-pass",
            context(false, 100, 80),
            new VulkanGraphicsCommandExecutionCoordinator.ScissorRequest(90, 70, 40, 30),
            "setScissor"
        ));

        VulkanGraphicsCommandExecutionCoordinator.DynamicStatePlan enabled = coordinator.planScissorTestEnabled(
            COMMAND_BUFFER,
            "offscreen-pass",
            context(false, 100, 80),
            true,
            "enableScissor"
        );
        coordinator.complete(enabled);
        VulkanGraphicsCommandExecutionCoordinator.DynamicStateRequirement clipped =
            (VulkanGraphicsCommandExecutionCoordinator.DynamicStateRequirement) enabled.operations().get(0).payload();

        VulkanGraphicsCommandExecutionCoordinator.DynamicStatePlan disabled = coordinator.planScissorTestEnabled(
            COMMAND_BUFFER,
            "offscreen-pass",
            context(false, 100, 80),
            false,
            "disableScissor"
        );

        assertEquals(
            new VulkanGraphicsCommandExecutionCoordinator.NormalizedScissor(90, 0, 10, 30),
            clipped.value()
        );
        assertTypes(disabled, DYNAMIC_STATE);
    }

    @Test
    void renderTargetChangeForcesDynamicReEmission() {
        VulkanGraphicsCommandExecutionCoordinator coordinator = new VulkanGraphicsCommandExecutionCoordinator();
        VulkanGraphicsCommandExecutionCoordinator.DynamicStatePlan first =
            coordinator.planRenderPassDefaultDynamicState(COMMAND_BUFFER, "pass-a", context(false, 320, 240), "begin");
        coordinator.complete(first);

        coordinator.beginRenderPass(COMMAND_BUFFER.handle(), "pass-b");
        VulkanGraphicsCommandExecutionCoordinator.DynamicStatePlan resized =
            coordinator.planRenderPassDefaultDynamicState(COMMAND_BUFFER, "pass-b", context(false, 640, 480), "begin");

        assertTypes(resized, DYNAMIC_STATE, DYNAMIC_STATE);
    }

    @Test
    void pushConstantPayloadIdentityUsesExactBytesAndLayout() {
        VulkanGraphicsCommandExecutionCoordinator coordinator = new VulkanGraphicsCommandExecutionCoordinator();
        coordinator.complete(coordinator.planGraphicsExecution(arraysRequest(
            pipeline(101L, 201L, "pass-a"),
            null,
            List.of(new VulkanGraphicsCommandExecutionCoordinator.PushConstantRequirement(201L, 1, 0, new byte[] {1, 2, 3})),
            vertex(0, 401L, 0L)
        )));

        VulkanGraphicsCommandExecutionCoordinator.GraphicsExecutionPlan repeat =
            coordinator.planGraphicsExecution(arraysRequest(
                pipeline(101L, 201L, "pass-a"),
                null,
                List.of(new VulkanGraphicsCommandExecutionCoordinator.PushConstantRequirement(201L, 1, 0, new byte[] {1, 2, 3})),
                vertex(0, 401L, 0L)
            ));
        VulkanGraphicsCommandExecutionCoordinator.GraphicsExecutionPlan changedPayload =
            coordinator.planGraphicsExecution(arraysRequest(
                pipeline(101L, 201L, "pass-a"),
                null,
                List.of(new VulkanGraphicsCommandExecutionCoordinator.PushConstantRequirement(201L, 1, 0, new byte[] {1, 2, 4})),
                vertex(0, 401L, 0L)
            ));

        assertTypes(repeat, DRAW);
        assertTypes(changedPayload, PUSH_CONSTANTS, DRAW);
    }

    @Test
    void individualVertexBufferBindingPreservesPreviouslyPublishedBindings() {
        VulkanGraphicsCommandExecutionCoordinator coordinator = new VulkanGraphicsCommandExecutionCoordinator();
        coordinator.complete(coordinator.planGraphicsExecution(arraysRequest(
            pipeline(101L, 201L, "pass-a"),
            null,
            vertex(0, 401L, 0L),
            vertex(1, 402L, 64L)
        )));

        VulkanGraphicsCommandExecutionCoordinator.GraphicsExecutionPlan changeSlotOne =
            coordinator.planVertexBufferBinding(COMMAND_BUFFER, "pass-a", vertex(1, 403L, 96L), "bindVertexBuffer");
        coordinator.complete(changeSlotOne);

        VulkanGraphicsCommandExecutionCoordinator.GraphicsExecutionPlan draw =
            coordinator.planDrawFromCurrentState(
                COMMAND_BUFFER,
                "pass-a",
                VulkanGraphicsCommandExecutionCoordinator.DrawCommandRequirement.arrays("draw", 0, 3, 1)
            );

        assertTypes(changeSlotOne, BIND_VERTEX_BUFFER, DRAW);
        assertTypes(draw, DRAW);
        assertEquals(3L, coordinator.skippedRedundantVertexBindCount(),
            "The draw should still know both vertex bindings after rebinding one slot");
    }

    @Test
    void indexedDrawWithoutPublishedIndexBufferIsRejected() {
        VulkanGraphicsCommandExecutionCoordinator coordinator = new VulkanGraphicsCommandExecutionCoordinator();
        coordinator.complete(coordinator.planGraphicsExecution(arraysRequest(
            pipeline(101L, 201L, "pass-a"),
            null,
            vertex(0, 401L, 0L)
        )));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> coordinator.planDrawFromCurrentState(
            COMMAND_BUFFER,
            "pass-a",
            VulkanGraphicsCommandExecutionCoordinator.DrawCommandRequirement.indexed("drawIndexed", 0, 3, 0, 1)
        ));

        assertEquals("Indexed graphics draw requires a published index buffer for drawIndexed", exception.getMessage());
    }

    private static VulkanGraphicsCommandExecutionCoordinator.GraphicsExecutionRequest indexedRequest(
        VulkanGraphicsCommandExecutionCoordinator.PipelineBindingRequirement pipeline,
        VulkanGraphicsCommandExecutionCoordinator.DescriptorBindingRequirement descriptor
    ) {
        return indexedRequest(pipeline, descriptor, List.of(16));
    }

    private static VulkanGraphicsCommandExecutionCoordinator.GraphicsExecutionRequest indexedRequest(
        VulkanGraphicsCommandExecutionCoordinator.PipelineBindingRequirement pipeline,
        VulkanGraphicsCommandExecutionCoordinator.DescriptorBindingRequirement descriptor,
        List<Integer> dynamicOffsets
    ) {
        return new VulkanGraphicsCommandExecutionCoordinator.GraphicsExecutionRequest(
            COMMAND_BUFFER,
            "pass-a",
            "drawIndexed",
            pipeline,
            descriptor == null
                ? null
                : descriptor(descriptor.pipelineLayoutHandle(), descriptor.descriptorSetHandle(), dynamicOffsets),
            List.of(vertex(0, 401L, 0L)),
            index(501L, 0L),
            List.of(),
            List.of(),
            VulkanGraphicsCommandExecutionCoordinator.DrawCommandRequirement.indexed("drawIndexed", 4, 12, -2, 3)
        );
    }

    private static VulkanGraphicsCommandExecutionCoordinator.GraphicsExecutionRequest arraysRequest(
        VulkanGraphicsCommandExecutionCoordinator.PipelineBindingRequirement pipeline,
        VulkanGraphicsCommandExecutionCoordinator.DescriptorBindingRequirement descriptor,
        VulkanGraphicsCommandExecutionCoordinator.VertexBufferBindingRequirement... vertices
    ) {
        return arraysRequest(pipeline, descriptor, List.of(), vertices);
    }

    private static VulkanGraphicsCommandExecutionCoordinator.GraphicsExecutionRequest arraysRequest(
        VulkanGraphicsCommandExecutionCoordinator.PipelineBindingRequirement pipeline,
        VulkanGraphicsCommandExecutionCoordinator.DescriptorBindingRequirement descriptor,
        List<VulkanGraphicsCommandExecutionCoordinator.PushConstantRequirement> pushConstants,
        VulkanGraphicsCommandExecutionCoordinator.VertexBufferBindingRequirement... vertices
    ) {
        return new VulkanGraphicsCommandExecutionCoordinator.GraphicsExecutionRequest(
            COMMAND_BUFFER,
            "pass-a",
            "draw",
            pipeline,
            descriptor,
            List.of(vertices),
            null,
            List.of(),
            pushConstants,
            VulkanGraphicsCommandExecutionCoordinator.DrawCommandRequirement.arrays("draw", 0, 3, 1)
        );
    }

    private static VulkanGraphicsCommandExecutionCoordinator.RenderTargetContext context(
        boolean targetsSwapchain,
        int width,
        int height
    ) {
        return new VulkanGraphicsCommandExecutionCoordinator.RenderTargetContext(
            true,
            targetsSwapchain,
            width,
            height,
            1920,
            1080
        );
    }

    private static VulkanGraphicsCommandExecutionCoordinator.PipelineBindingRequirement pipeline(
        long pipelineHandle,
        long layoutHandle,
        String compatibilityKey
    ) {
        return new VulkanGraphicsCommandExecutionCoordinator.PipelineBindingRequirement(
            pipelineHandle,
            layoutHandle,
            compatibilityKey
        );
    }

    private static VulkanGraphicsCommandExecutionCoordinator.DescriptorBindingRequirement descriptor(
        long pipelineLayoutHandle,
        long descriptorSetHandle
    ) {
        return descriptor(pipelineLayoutHandle, descriptorSetHandle, List.of(16));
    }

    private static VulkanGraphicsCommandExecutionCoordinator.DescriptorBindingRequirement descriptor(
        long pipelineLayoutHandle,
        long descriptorSetHandle,
        List<Integer> dynamicOffsets
    ) {
        return new VulkanGraphicsCommandExecutionCoordinator.DescriptorBindingRequirement(
            pipelineLayoutHandle,
            descriptorSetHandle,
            701L,
            "descriptor-key",
            dynamicOffsets
        );
    }

    private static VulkanGraphicsCommandExecutionCoordinator.VertexBufferBindingRequirement vertex(
        int binding,
        long bufferHandle,
        long offset
    ) {
        return new VulkanGraphicsCommandExecutionCoordinator.VertexBufferBindingRequirement(
            binding,
            bufferHandle,
            offset,
            32,
            false
        );
    }

    private static VulkanGraphicsCommandExecutionCoordinator.IndexBufferBindingRequirement index(long bufferHandle, long offset) {
        return new VulkanGraphicsCommandExecutionCoordinator.IndexBufferBindingRequirement(
            bufferHandle,
            offset,
            256,
            VulkanicIndexType.SHORT
        );
    }

    private static void assertTypes(
        VulkanGraphicsCommandExecutionCoordinator.GraphicsExecutionPlan plan,
        VulkanGraphicsCommandExecutionCoordinator.GraphicsCommandOperationType... expected
    ) {
        assertEquals(
            List.of(expected),
            plan.operations().stream().map(VulkanGraphicsCommandExecutionCoordinator.GraphicsCommandOperation::type).toList()
        );
    }

    private static void assertTypes(
        VulkanGraphicsCommandExecutionCoordinator.DynamicStatePlan plan,
        VulkanGraphicsCommandExecutionCoordinator.GraphicsCommandOperationType... expected
    ) {
        assertEquals(
            List.of(expected),
            plan.operations().stream().map(VulkanGraphicsCommandExecutionCoordinator.GraphicsCommandOperation::type).toList()
        );
    }
}
