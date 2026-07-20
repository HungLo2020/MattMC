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
        return request;
    }

    public static VulkanicGalExecutionRequest.GraphicsDrawRequest captureGraphicsDraw(
        CommandContext ctx,
        VulkanicGalExecutor executor,
        VulkanicCompatibilityState compatibilityState,
        VulkanicGalExecutionRequest.GraphicsDrawRequest request
    ) {
        Objects.requireNonNull(compatibilityState, "compatibilityState");
        VulkanicGalExecutionRequest.GraphicsCompatibilitySnapshot snapshot =
            compatibilityState.compatibilitySnapshotFor(request);
        compatibilityState.validateResourceGenerations(snapshot);
        return request.withCompatibilitySnapshot(snapshot);
    }

    public static VulkanicGalExecutionRequest.ComputeDispatchRequest captureComputeDispatch(
        CommandContext ctx,
        VulkanicGalExecutor executor,
        VulkanicGalExecutionRequest.ComputeDispatchRequest request
    ) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(request, "request");
        return request;
    }

    public static VulkanicGalExecutionRequest.ComputeDispatchRequest captureComputeDispatch(
        CommandContext ctx,
        VulkanicGalExecutor executor,
        VulkanicCompatibilityState compatibilityState,
        VulkanicGalExecutionRequest.ComputeDispatchRequest request
    ) {
        Objects.requireNonNull(compatibilityState, "compatibilityState");
        VulkanicGalExecutionRequest.ComputeCompatibilitySnapshot snapshot =
            compatibilityState.compatibilitySnapshotFor(request);
        compatibilityState.validateResourceGenerations(snapshot);
        VulkanicGalExecutionRequest.ComputeDispatchRequest sharedRequest = request.withCompatibilitySnapshot(snapshot);
        return captureComputeDispatch(ctx, executor, sharedRequest);
    }

    public static VulkanicGalExecutionRequest.ClearRequest captureClear(
        CommandContext ctx,
        VulkanicGalExecutor executor,
        VulkanicGalExecutionRequest.ClearRequest request
    ) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(request, "request");
        return request;
    }

    public static VulkanicGalExecutionRequest.ClearRequest captureClear(
        CommandContext ctx,
        VulkanicGalExecutor executor,
        VulkanicCompatibilityState compatibilityState,
        VulkanicGalExecutionRequest.ClearRequest request
    ) {
        Objects.requireNonNull(compatibilityState, "compatibilityState");
        VulkanicGalExecutionRequest.ClearRequest sharedRequest =
            request.withFramebufferSnapshot(compatibilityState.boundDrawFramebuffer());
        return captureClear(ctx, executor, sharedRequest);
    }

    public static VulkanicGalExecutionRequest.TransferRequest captureTransfer(
        CommandContext ctx,
        VulkanicGalExecutor executor,
        VulkanicGalExecutionRequest.TransferRequest request
    ) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(request, "request");
        return request;
    }

    public static VulkanicGalExecutionRequest.TransferRequest captureTransfer(
        CommandContext ctx,
        VulkanicGalExecutor executor,
        VulkanicCompatibilityState compatibilityState,
        VulkanicGalExecutionRequest.TransferRequest request
    ) {
        Objects.requireNonNull(compatibilityState, "compatibilityState");
        VulkanicGalExecutionRequest.TransferCompatibilitySnapshot snapshot =
            compatibilityState.compatibilitySnapshotFor(request);
        compatibilityState.validateResourceGenerations(snapshot);
        VulkanicGalExecutionRequest.TransferRequest sharedRequest = request.withTransferSnapshot(snapshot);
        return captureTransfer(ctx, executor, sharedRequest);
    }

    public static VulkanicGalExecutionRequest.RenderPassBeginRequest captureRenderPassBegin(
        CommandContext ctx,
        VulkanicGalExecutor executor,
        VulkanicGalExecutionRequest.RenderPassBeginRequest request
    ) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(request, "request");
        return request;
    }

    public static VulkanicGalExecutionRequest.RenderPassEndRequest captureRenderPassEnd(
        CommandContext ctx,
        VulkanicGalExecutor executor,
        VulkanicGalExecutionRequest.RenderPassEndRequest request
    ) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(request, "request");
        return request;
    }

    public static VulkanicGalExecutionRequest.ComputePassBeginRequest captureComputePassBegin(
        CommandContext ctx,
        VulkanicGalExecutor executor,
        VulkanicGalExecutionRequest.ComputePassBeginRequest request
    ) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(request, "request");
        return request;
    }

    public static VulkanicGalExecutionRequest.ComputePassEndRequest captureComputePassEnd(
        CommandContext ctx,
        VulkanicGalExecutor executor,
        VulkanicGalExecutionRequest.ComputePassEndRequest request
    ) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(request, "request");
        return request;
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
