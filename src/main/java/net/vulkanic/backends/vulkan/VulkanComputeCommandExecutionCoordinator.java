package net.vulkanic.backends.vulkan;

import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Owns command-buffer-local compute execution state for Vulkan command recording.
 *
 * <p>The coordinator plans immutable compute command operations only. NativeSpine supplies
 * materialized native handles, emits Vulkan commands, and publishes state here after emission
 * succeeds.</p>
 */
final class VulkanComputeCommandExecutionCoordinator {
    private final Map<Long, ComputeCommandBufferState> computeStates = new LinkedHashMap<>();
    private long skippedRedundantPipelineBindCount;
    private long skippedRedundantDescriptorBindCount;
    private long skippedRedundantPushConstantCount;

    ComputeExecutionPlan planComputeExecution(ComputeExecutionRequest request) {
        Objects.requireNonNull(request, "request");
        ComputeCommandBufferState current = currentState(request.commandBuffer());
        List<ComputeCommandOperation> operations = new java.util.ArrayList<>();

        ComputePipelineRequirement pipeline = request.pipeline();
        if (current == null || !pipeline.equals(current.pipeline)) {
            operations.add(ComputeCommandOperation.bindPipeline(pipeline));
        } else {
            skippedRedundantPipelineBindCount++;
        }

        ComputeDescriptorRequirement descriptor = request.descriptor();
        if (descriptor != null) {
            boolean pipelineLayoutChanged = current == null
                || current.pipeline == null
                || current.pipeline.pipelineLayoutHandle() != pipeline.pipelineLayoutHandle();
            if (pipelineLayoutChanged || !descriptor.equals(current.descriptor)) {
                operations.add(ComputeCommandOperation.bindDescriptorSet(descriptor));
            } else {
                skippedRedundantDescriptorBindCount++;
            }
        }

        Map<PushConstantKey, PushConstantRequirement> nextPushConstants = current == null
            ? new LinkedHashMap<>()
            : new LinkedHashMap<>(current.pushConstants);
        for (PushConstantRequirement pushConstant : request.pushConstants()) {
            PushConstantKey key = PushConstantKey.from(pushConstant);
            PushConstantRequirement currentPushConstant = nextPushConstants.get(key);
            if (!pushConstant.equals(currentPushConstant)) {
                operations.add(ComputeCommandOperation.pushConstants(pushConstant));
                nextPushConstants.put(key, pushConstant);
            } else {
                skippedRedundantPushConstantCount++;
            }
        }

        operations.add(ComputeCommandOperation.dispatch(request.dispatch()));

        return new ComputeExecutionPlan(
            request,
            operations,
            new ComputeCommandBufferState(
                request.commandBuffer().generation(),
                pipeline,
                descriptor,
                nextPushConstants
            )
        );
    }

    void complete(ComputeExecutionPlan plan) {
        Objects.requireNonNull(plan, "plan");
        computeStates.put(plan.request().commandBuffer().handle(), plan.publishedState());
    }

    void abandon(ComputeExecutionPlan plan) {
        Objects.requireNonNull(plan, "plan");
    }

    void resetCommandBuffer(long commandBufferHandle) {
        computeStates.remove(commandBufferHandle);
    }

    void clear() {
        computeStates.clear();
    }

    int computeStateCountForTests() {
        return computeStates.size();
    }

    long skippedRedundantPipelineBindCount() {
        return skippedRedundantPipelineBindCount;
    }

    long skippedRedundantDescriptorBindCount() {
        return skippedRedundantDescriptorBindCount;
    }

    long skippedRedundantPushConstantCount() {
        return skippedRedundantPushConstantCount;
    }

    private ComputeCommandBufferState currentState(CommandBufferIdentity commandBuffer) {
        ComputeCommandBufferState current = computeStates.get(commandBuffer.handle());
        if (current != null && current.generation != commandBuffer.generation()) {
            computeStates.remove(commandBuffer.handle());
            return null;
        }
        return current;
    }

    private record ComputeCommandBufferState(
        long generation,
        ComputePipelineRequirement pipeline,
        @Nullable ComputeDescriptorRequirement descriptor,
        Map<PushConstantKey, PushConstantRequirement> pushConstants
    ) {
        ComputeCommandBufferState {
            Objects.requireNonNull(pipeline, "pipeline");
            pushConstants = Map.copyOf(pushConstants);
        }
    }

    record CommandBufferIdentity(long handle, long generation, CommandBufferKind kind) {
        CommandBufferIdentity {
            Objects.requireNonNull(kind, "kind");
            if (handle == 0L) {
                throw new IllegalArgumentException("command buffer handle must be non-zero");
            }
        }
    }

    enum CommandBufferKind {
        FRAME,
        IMMEDIATE,
        PRIMARY
    }

    record ComputeExecutionRequest(
        CommandBufferIdentity commandBuffer,
        String semanticSource,
        ComputePipelineRequirement pipeline,
        @Nullable ComputeDescriptorRequirement descriptor,
        List<PushConstantRequirement> pushConstants,
        ComputeDispatchRequirement dispatch
    ) {
        ComputeExecutionRequest {
            Objects.requireNonNull(commandBuffer, "commandBuffer");
            Objects.requireNonNull(semanticSource, "semanticSource");
            Objects.requireNonNull(pipeline, "pipeline");
            pushConstants = List.copyOf(pushConstants);
            Objects.requireNonNull(dispatch, "dispatch");
        }
    }

    record ComputePipelineRequirement(long pipelineHandle, long pipelineLayoutHandle) {
        ComputePipelineRequirement {
            if (pipelineHandle == 0L) {
                throw new IllegalArgumentException("pipelineHandle must be non-zero");
            }
            if (pipelineLayoutHandle == 0L) {
                throw new IllegalArgumentException("pipelineLayoutHandle must be non-zero");
            }
        }
    }

    record ComputeDescriptorRequirement(
        long pipelineLayoutHandle,
        long descriptorSetHandle,
        long descriptorSetLayoutHandle,
        @Nullable Object cacheKey,
        List<Integer> dynamicOffsets
    ) {
        ComputeDescriptorRequirement {
            if (descriptorSetHandle == 0L) {
                throw new IllegalArgumentException("descriptorSetHandle must be non-zero");
            }
            dynamicOffsets = List.copyOf(dynamicOffsets);
        }
    }

    record PushConstantRequirement(long pipelineLayoutHandle, int stageFlags, int offset, byte[] bytes) {
        PushConstantRequirement {
            if (pipelineLayoutHandle == 0L) {
                throw new IllegalArgumentException("pipelineLayoutHandle must be non-zero");
            }
            if (stageFlags == 0) {
                throw new IllegalArgumentException("stageFlags must be non-zero");
            }
            if (offset < 0) {
                throw new IllegalArgumentException("offset must be >= 0");
            }
            Objects.requireNonNull(bytes, "bytes");
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PushConstantRequirement that)) {
                return false;
            }
            return pipelineLayoutHandle == that.pipelineLayoutHandle
                && stageFlags == that.stageFlags
                && offset == that.offset
                && Arrays.equals(bytes, that.bytes);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(pipelineLayoutHandle, stageFlags, offset);
            result = 31 * result + Arrays.hashCode(bytes);
            return result;
        }
    }

    private record PushConstantKey(long pipelineLayoutHandle, int stageFlags, int offset, int size) {
        private static PushConstantKey from(PushConstantRequirement requirement) {
            return new PushConstantKey(
                requirement.pipelineLayoutHandle(),
                requirement.stageFlags(),
                requirement.offset(),
                requirement.bytes.length
            );
        }
    }

    record ComputeDispatchRequirement(
        ComputeDispatchKind kind,
        String semanticSource,
        int workX,
        int workY,
        int workZ,
        long indirectBufferHandle,
        long indirectOffset
    ) {
        ComputeDispatchRequirement {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(semanticSource, "semanticSource");
            if (kind == ComputeDispatchKind.DIRECT && (workX <= 0 || workY <= 0 || workZ <= 0)) {
                throw new IllegalArgumentException("Direct compute dispatch dimensions must be positive");
            }
            if (kind == ComputeDispatchKind.INDIRECT && indirectBufferHandle == 0L) {
                throw new IllegalArgumentException("Indirect compute dispatch requires a buffer handle");
            }
            if (kind == ComputeDispatchKind.INDIRECT && indirectOffset < 0L) {
                throw new IllegalArgumentException("Indirect compute dispatch offset must be >= 0");
            }
        }

        static ComputeDispatchRequirement direct(String semanticSource, int workX, int workY, int workZ) {
            return new ComputeDispatchRequirement(
                ComputeDispatchKind.DIRECT,
                semanticSource,
                workX,
                workY,
                workZ,
                0L,
                0L
            );
        }

        static ComputeDispatchRequirement indirect(String semanticSource, long bufferHandle, long offset) {
            return new ComputeDispatchRequirement(
                ComputeDispatchKind.INDIRECT,
                semanticSource,
                0,
                0,
                0,
                bufferHandle,
                offset
            );
        }
    }

    enum ComputeDispatchKind {
        DIRECT,
        INDIRECT
    }

    record ComputeCommandOperation(ComputeCommandOperationType type, Object payload) {
        ComputeCommandOperation {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(payload, "payload");
        }

        static ComputeCommandOperation bindPipeline(ComputePipelineRequirement pipeline) {
            return new ComputeCommandOperation(ComputeCommandOperationType.BIND_PIPELINE, pipeline);
        }

        static ComputeCommandOperation bindDescriptorSet(ComputeDescriptorRequirement descriptor) {
            return new ComputeCommandOperation(ComputeCommandOperationType.BIND_DESCRIPTOR_SET, descriptor);
        }

        static ComputeCommandOperation pushConstants(PushConstantRequirement pushConstant) {
            return new ComputeCommandOperation(ComputeCommandOperationType.PUSH_CONSTANTS, pushConstant);
        }

        static ComputeCommandOperation dispatch(ComputeDispatchRequirement dispatch) {
            return new ComputeCommandOperation(ComputeCommandOperationType.DISPATCH, dispatch);
        }
    }

    enum ComputeCommandOperationType {
        BIND_PIPELINE,
        BIND_DESCRIPTOR_SET,
        PUSH_CONSTANTS,
        DISPATCH
    }

    record ComputeExecutionPlan(
        ComputeExecutionRequest request,
        List<ComputeCommandOperation> operations,
        ComputeCommandBufferState publishedState
    ) {
        ComputeExecutionPlan {
            operations = List.copyOf(operations);
        }
    }
}
