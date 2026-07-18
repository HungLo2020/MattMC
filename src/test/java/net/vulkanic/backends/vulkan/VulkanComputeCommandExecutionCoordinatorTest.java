package net.vulkanic.backends.vulkan;

import org.junit.jupiter.api.Test;

import java.util.List;

import static net.vulkanic.backends.vulkan.VulkanComputeCommandExecutionCoordinator.ComputeCommandOperationType.BIND_DESCRIPTOR_SET;
import static net.vulkanic.backends.vulkan.VulkanComputeCommandExecutionCoordinator.ComputeCommandOperationType.BIND_PIPELINE;
import static net.vulkanic.backends.vulkan.VulkanComputeCommandExecutionCoordinator.ComputeCommandOperationType.DISPATCH;
import static net.vulkanic.backends.vulkan.VulkanComputeCommandExecutionCoordinator.ComputeCommandOperationType.PUSH_CONSTANTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VulkanComputeCommandExecutionCoordinatorTest {
    private static final VulkanComputeCommandExecutionCoordinator.CommandBufferIdentity COMMAND_BUFFER =
        new VulkanComputeCommandExecutionCoordinator.CommandBufferIdentity(
            21L,
            1L,
            VulkanComputeCommandExecutionCoordinator.CommandBufferKind.FRAME
        );

    @Test
    void firstDirectDispatchPlansOrderedCommands() {
        VulkanComputeCommandExecutionCoordinator coordinator = new VulkanComputeCommandExecutionCoordinator();

        VulkanComputeCommandExecutionCoordinator.ComputeExecutionPlan plan =
            coordinator.planComputeExecution(request(
                pipeline(101L, 201L),
                descriptor(201L, 301L, List.of()),
                VulkanComputeCommandExecutionCoordinator.ComputeDispatchRequirement.direct("dispatch", 4, 5, 6)
            ));

        assertTypes(plan, BIND_PIPELINE, BIND_DESCRIPTOR_SET, DISPATCH);
        VulkanComputeCommandExecutionCoordinator.ComputeDispatchRequirement dispatch =
            (VulkanComputeCommandExecutionCoordinator.ComputeDispatchRequirement) plan.operations().get(2).payload();
        assertEquals(VulkanComputeCommandExecutionCoordinator.ComputeDispatchKind.DIRECT, dispatch.kind());
        assertEquals(4, dispatch.workX());
        assertEquals(5, dispatch.workY());
        assertEquals(6, dispatch.workZ());
    }

    @Test
    void repeatedDispatchElidesEquivalentPipelineAndDescriptor() {
        VulkanComputeCommandExecutionCoordinator coordinator = new VulkanComputeCommandExecutionCoordinator();
        coordinator.complete(coordinator.planComputeExecution(request(
            pipeline(101L, 201L),
            descriptor(201L, 301L, List.of()),
            VulkanComputeCommandExecutionCoordinator.ComputeDispatchRequirement.direct("dispatch", 1, 1, 1)
        )));

        VulkanComputeCommandExecutionCoordinator.ComputeExecutionPlan repeat =
            coordinator.planComputeExecution(request(
                pipeline(101L, 201L),
                descriptor(201L, 301L, List.of()),
                VulkanComputeCommandExecutionCoordinator.ComputeDispatchRequirement.direct("dispatch", 2, 1, 1)
            ));

        assertTypes(repeat, DISPATCH);
        assertEquals(1L, coordinator.skippedRedundantPipelineBindCount());
        assertEquals(1L, coordinator.skippedRedundantDescriptorBindCount());
    }

    @Test
    void pipelineChangeRebindsDescriptorBecauseLayoutAssumptionsChanged() {
        VulkanComputeCommandExecutionCoordinator coordinator = new VulkanComputeCommandExecutionCoordinator();
        coordinator.complete(coordinator.planComputeExecution(request(
            pipeline(101L, 201L),
            descriptor(201L, 301L, List.of()),
            VulkanComputeCommandExecutionCoordinator.ComputeDispatchRequirement.direct("dispatch", 1, 1, 1)
        )));

        VulkanComputeCommandExecutionCoordinator.ComputeExecutionPlan changedPipeline =
            coordinator.planComputeExecution(request(
                pipeline(102L, 202L),
                descriptor(202L, 301L, List.of()),
                VulkanComputeCommandExecutionCoordinator.ComputeDispatchRequirement.direct("dispatch", 1, 1, 1)
            ));

        assertTypes(changedPipeline, BIND_PIPELINE, BIND_DESCRIPTOR_SET, DISPATCH);
    }

    @Test
    void dynamicOffsetsParticipateInDescriptorIdentity() {
        VulkanComputeCommandExecutionCoordinator coordinator = new VulkanComputeCommandExecutionCoordinator();
        coordinator.complete(coordinator.planComputeExecution(request(
            pipeline(101L, 201L),
            descriptor(201L, 301L, List.of(16)),
            VulkanComputeCommandExecutionCoordinator.ComputeDispatchRequirement.direct("dispatch", 1, 1, 1)
        )));

        VulkanComputeCommandExecutionCoordinator.ComputeExecutionPlan changedOffset =
            coordinator.planComputeExecution(request(
                pipeline(101L, 201L),
                descriptor(201L, 301L, List.of(32)),
                VulkanComputeCommandExecutionCoordinator.ComputeDispatchRequirement.direct("dispatch", 1, 1, 1)
            ));

        assertTypes(changedOffset, BIND_DESCRIPTOR_SET, DISPATCH);
    }

    @Test
    void pushConstantsUseExactPayloadAndLayoutIdentity() {
        VulkanComputeCommandExecutionCoordinator coordinator = new VulkanComputeCommandExecutionCoordinator();
        coordinator.complete(coordinator.planComputeExecution(request(
            pipeline(101L, 201L),
            descriptor(201L, 301L, List.of()),
            List.of(new VulkanComputeCommandExecutionCoordinator.PushConstantRequirement(201L, 32, 0, new byte[] {1, 2})),
            VulkanComputeCommandExecutionCoordinator.ComputeDispatchRequirement.direct("dispatch", 1, 1, 1)
        )));

        VulkanComputeCommandExecutionCoordinator.ComputeExecutionPlan samePayload =
            coordinator.planComputeExecution(request(
                pipeline(101L, 201L),
                descriptor(201L, 301L, List.of()),
                List.of(new VulkanComputeCommandExecutionCoordinator.PushConstantRequirement(201L, 32, 0, new byte[] {1, 2})),
                VulkanComputeCommandExecutionCoordinator.ComputeDispatchRequirement.direct("dispatch", 1, 1, 1)
            ));
        VulkanComputeCommandExecutionCoordinator.ComputeExecutionPlan changedPayload =
            coordinator.planComputeExecution(request(
                pipeline(101L, 201L),
                descriptor(201L, 301L, List.of()),
                List.of(new VulkanComputeCommandExecutionCoordinator.PushConstantRequirement(201L, 32, 0, new byte[] {1, 3})),
                VulkanComputeCommandExecutionCoordinator.ComputeDispatchRequirement.direct("dispatch", 1, 1, 1)
            ));

        assertTypes(samePayload, DISPATCH);
        assertTypes(changedPayload, PUSH_CONSTANTS, DISPATCH);
    }

    @Test
    void indirectDispatchIsRepresentedWithoutDirectWorkgroupDimensions() {
        VulkanComputeCommandExecutionCoordinator coordinator = new VulkanComputeCommandExecutionCoordinator();

        VulkanComputeCommandExecutionCoordinator.ComputeExecutionPlan plan =
            coordinator.planComputeExecution(request(
                pipeline(101L, 201L),
                descriptor(201L, 301L, List.of()),
                VulkanComputeCommandExecutionCoordinator.ComputeDispatchRequirement.indirect("dispatchIndirect", 909L, 64L)
            ));

        assertTypes(plan, BIND_PIPELINE, BIND_DESCRIPTOR_SET, DISPATCH);
        VulkanComputeCommandExecutionCoordinator.ComputeDispatchRequirement dispatch =
            (VulkanComputeCommandExecutionCoordinator.ComputeDispatchRequirement) plan.operations().get(2).payload();
        assertEquals(VulkanComputeCommandExecutionCoordinator.ComputeDispatchKind.INDIRECT, dispatch.kind());
        assertEquals(909L, dispatch.indirectBufferHandle());
        assertEquals(64L, dispatch.indirectOffset());
    }

    @Test
    void invalidDispatchRequestsAreRejectedBeforePublication() {
        assertThrows(IllegalArgumentException.class, () ->
            VulkanComputeCommandExecutionCoordinator.ComputeDispatchRequirement.direct("dispatch", 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () ->
            VulkanComputeCommandExecutionCoordinator.ComputeDispatchRequirement.indirect("dispatchIndirect", 0L, 0L));
    }

    @Test
    void abandonedPlanDoesNotPublishState() {
        VulkanComputeCommandExecutionCoordinator coordinator = new VulkanComputeCommandExecutionCoordinator();
        VulkanComputeCommandExecutionCoordinator.ComputeExecutionPlan plan = coordinator.planComputeExecution(request(
            pipeline(101L, 201L),
            descriptor(201L, 301L, List.of()),
            VulkanComputeCommandExecutionCoordinator.ComputeDispatchRequirement.direct("dispatch", 1, 1, 1)
        ));

        coordinator.abandon(plan);

        assertEquals(0, coordinator.computeStateCountForTests());
        assertTypes(
            coordinator.planComputeExecution(request(
                pipeline(101L, 201L),
                descriptor(201L, 301L, List.of()),
                VulkanComputeCommandExecutionCoordinator.ComputeDispatchRequirement.direct("dispatch", 1, 1, 1)
            )),
            BIND_PIPELINE,
            BIND_DESCRIPTOR_SET,
            DISPATCH
        );
    }

    @Test
    void resetClearsComputeStateWithoutTouchingGraphicsCoordinator() {
        VulkanComputeCommandExecutionCoordinator compute = new VulkanComputeCommandExecutionCoordinator();
        VulkanGraphicsCommandExecutionCoordinator graphics = new VulkanGraphicsCommandExecutionCoordinator();
        compute.complete(compute.planComputeExecution(request(
            pipeline(101L, 201L),
            descriptor(201L, 301L, List.of()),
            VulkanComputeCommandExecutionCoordinator.ComputeDispatchRequirement.direct("dispatch", 1, 1, 1)
        )));
        graphics.complete(graphics.planGraphicsExecution(new VulkanGraphicsCommandExecutionCoordinator.GraphicsExecutionRequest(
            new VulkanGraphicsCommandExecutionCoordinator.CommandBufferIdentity(
                COMMAND_BUFFER.handle(),
                COMMAND_BUFFER.generation(),
                VulkanGraphicsCommandExecutionCoordinator.CommandBufferKind.FRAME
            ),
            "pass-a",
            "draw",
            new VulkanGraphicsCommandExecutionCoordinator.PipelineBindingRequirement(501L, 601L, "pass-a"),
            null,
            List.of(new VulkanGraphicsCommandExecutionCoordinator.VertexBufferBindingRequirement(0, 701L, 0L, 32, false)),
            null,
            List.of(),
            List.of(),
            VulkanGraphicsCommandExecutionCoordinator.DrawCommandRequirement.arrays("draw", 0, 3, 1)
        )));

        compute.resetCommandBuffer(COMMAND_BUFFER.handle());

        assertEquals(0, compute.computeStateCountForTests());
        assertEquals(1, graphics.graphicsStateCountForTests());
    }

    private static VulkanComputeCommandExecutionCoordinator.ComputeExecutionRequest request(
        VulkanComputeCommandExecutionCoordinator.ComputePipelineRequirement pipeline,
        VulkanComputeCommandExecutionCoordinator.ComputeDescriptorRequirement descriptor,
        VulkanComputeCommandExecutionCoordinator.ComputeDispatchRequirement dispatch
    ) {
        return request(pipeline, descriptor, List.of(), dispatch);
    }

    private static VulkanComputeCommandExecutionCoordinator.ComputeExecutionRequest request(
        VulkanComputeCommandExecutionCoordinator.ComputePipelineRequirement pipeline,
        VulkanComputeCommandExecutionCoordinator.ComputeDescriptorRequirement descriptor,
        List<VulkanComputeCommandExecutionCoordinator.PushConstantRequirement> pushConstants,
        VulkanComputeCommandExecutionCoordinator.ComputeDispatchRequirement dispatch
    ) {
        return new VulkanComputeCommandExecutionCoordinator.ComputeExecutionRequest(
            COMMAND_BUFFER,
            dispatch.semanticSource(),
            pipeline,
            descriptor,
            pushConstants,
            dispatch
        );
    }

    private static VulkanComputeCommandExecutionCoordinator.ComputePipelineRequirement pipeline(long pipeline, long layout) {
        return new VulkanComputeCommandExecutionCoordinator.ComputePipelineRequirement(pipeline, layout);
    }

    private static VulkanComputeCommandExecutionCoordinator.ComputeDescriptorRequirement descriptor(
        long pipelineLayoutHandle,
        long descriptorSetHandle,
        List<Integer> dynamicOffsets
    ) {
        return new VulkanComputeCommandExecutionCoordinator.ComputeDescriptorRequirement(
            pipelineLayoutHandle,
            descriptorSetHandle,
            401L,
            "descriptor-key",
            dynamicOffsets
        );
    }

    private static void assertTypes(
        VulkanComputeCommandExecutionCoordinator.ComputeExecutionPlan plan,
        VulkanComputeCommandExecutionCoordinator.ComputeCommandOperationType... expected
    ) {
        assertEquals(
            List.of(expected),
            plan.operations().stream().map(VulkanComputeCommandExecutionCoordinator.ComputeCommandOperation::type).toList()
        );
    }
}
