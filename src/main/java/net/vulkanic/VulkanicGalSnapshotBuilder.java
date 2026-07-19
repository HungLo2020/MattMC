package net.vulkanic;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Shared frontend-owned capture point for immutable internal GAL requests.
 *
 * <p>The legacy API mutates shared Vulkanic compatibility state. This builder
 * captures that state into immutable request objects before backend execution.
 * Backends may resolve native handles, shader modules, layouts, and descriptor
 * objects during lowering, but they must not own the legacy semantic state
 * represented by the request.</p>
 */
public final class VulkanicGalSnapshotBuilder {
    private VulkanicGalSnapshotBuilder() {
    }

    public static VulkanicGalExecutionRequest.GraphicsDrawRequest captureGraphicsDraw(
        CommandContext ctx,
        VulkanicGalExecutor executor,
        VulkanicGalExecutionRequest.GraphicsDrawRequest request
    ) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(request, "request");
        return executor.captureGraphicsRequest(ctx, request);
    }

    public static VulkanicGalExecutionRequest.GraphicsDrawRequest captureGraphicsDraw(
        CommandContext ctx,
        VulkanicGalExecutor executor,
        VulkanicCompatibilityState compatibilityState,
        VulkanicGalExecutionRequest.GraphicsDrawRequest request
    ) {
        Objects.requireNonNull(compatibilityState, "compatibilityState");
        VulkanicGalExecutionRequest.GraphicsDrawRequest sharedRequest = request.withCompatibilitySnapshot(
            compatibilityState.compatibilitySnapshotFor(request)
        );
        return captureGraphicsDraw(ctx, executor, sharedRequest);
    }

    public static VulkanicGalExecutionRequest.ComputeDispatchRequest captureComputeDispatch(
        CommandContext ctx,
        VulkanicGalExecutor executor,
        VulkanicGalExecutionRequest.ComputeDispatchRequest request
    ) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(request, "request");
        return executor.captureComputeDispatchRequest(ctx, request);
    }

    public static VulkanicGalExecutionRequest.ComputeDispatchRequest captureComputeDispatch(
        CommandContext ctx,
        VulkanicGalExecutor executor,
        VulkanicCompatibilityState compatibilityState,
        VulkanicGalExecutionRequest.ComputeDispatchRequest request
    ) {
        Objects.requireNonNull(compatibilityState, "compatibilityState");
        VulkanicGalExecutionRequest.ComputeDispatchRequest sharedRequest = request.withCompatibilitySnapshot(
            compatibilityState.compatibilitySnapshotFor(request)
        );
        return captureComputeDispatch(ctx, executor, sharedRequest);
    }

    public static VulkanicGalExecutionRequest.GraphicsCompatibilitySnapshot legacyGraphicsSnapshot(
        @Nullable PipelineDescriptor pipelineDescriptor,
        VulkanicGalExecutionRequest.VertexInputSnapshot vertexInput,
        List<VulkanicPassResourceModel.ResourceUse> resourceUses,
        @Nullable PipelineResourcePlanner.Plan resourceBindingPlan,
        List<VulkanicPassResourceModel.BindingSnapshot> descriptorBindings,
        String source
    ) {
        return new VulkanicGalExecutionRequest.GraphicsCompatibilitySnapshot(
            Optional.ofNullable(pipelineDescriptor),
            Objects.requireNonNull(vertexInput, "vertexInput"),
            resourceUses,
            Optional.ofNullable(resourceBindingPlan),
            descriptorBindings,
            source
        );
    }

    public static VulkanicGalExecutionRequest.GraphicsCompatibilitySnapshot legacyGraphicsSnapshot(
        @Nullable PipelineDescriptor pipelineDescriptor,
        VulkanicGalExecutionRequest.VertexInputSnapshot vertexInput,
        List<VulkanicPassResourceModel.ResourceUse> resourceUses,
        @Nullable PipelineResourcePlanner.Plan resourceBindingPlan,
        List<VulkanicPassResourceModel.BindingSnapshot> descriptorBindings,
        Optional<VulkanicCompatibilityState.GraphicsSnapshot> sharedCompatibilityState,
        String source
    ) {
        return new VulkanicGalExecutionRequest.GraphicsCompatibilitySnapshot(
            Optional.ofNullable(pipelineDescriptor),
            Objects.requireNonNull(vertexInput, "vertexInput"),
            resourceUses,
            Optional.ofNullable(resourceBindingPlan),
            descriptorBindings,
            Objects.requireNonNull(sharedCompatibilityState, "sharedCompatibilityState"),
            source
        );
    }
}
