package net.vulkanic;

/**
 * Internal explicit Vulkanic GAL execution boundary.
 *
 * <p>Legacy GL-style APIs build immutable, validated request objects before
 * reaching this interface. Backends lower those requests into native commands;
 * they do not receive raw GL-shaped execution arguments through this contract.</p>
 */
public interface VulkanicGalExecutor {
    default boolean requiresEagerGraphicsResourceDeclarations() {
        return true;
    }

    default VulkanicGalExecutionRequest.ExecutionResult executeGraphicsDraw(
        CommandContext ctx,
        VulkanicGalExecutionRequest.GraphicsDrawRequest request
    ) {
        return VulkanicGalExecutionRequest.backendFailure(request.semanticIdentity(), "backend does not implement typed graphics draw execution");
    }

    default VulkanicGalExecutionRequest.ExecutionResult executeGraphicsDrawV2(
        CommandContext ctx,
        VulkanicGalV2.ExplicitGraphicsDrawRequest request
    ) {
        return VulkanicGalExecutionRequest.backendFailure(request.semanticIdentity(), "backend does not implement explicit GAL v2 graphics draw execution");
    }

    default VulkanicGalExecutionRequest.ExecutionResult executeComputeDispatch(
        CommandContext ctx,
        VulkanicGalExecutionRequest.ComputeDispatchRequest request
    ) {
        return VulkanicGalExecutionRequest.backendFailure(request.semanticIdentity(), "backend does not implement typed compute dispatch execution");
    }

    default VulkanicGalExecutionRequest.ExecutionResult executeClear(
        CommandContext ctx,
        VulkanicGalExecutionRequest.ClearRequest request
    ) {
        return VulkanicGalExecutionRequest.backendFailure(request.semanticIdentity(), "backend does not implement typed clear execution");
    }

    default VulkanicGalExecutionRequest.ExecutionResult executeTransfer(
        CommandContext ctx,
        VulkanicGalExecutionRequest.TransferRequest request
    ) {
        return VulkanicGalExecutionRequest.backendFailure(request.semanticIdentity(), "backend does not implement typed transfer execution");
    }

    VulkanicRenderPass executeRenderPassBegin(
        CommandContext ctx,
        VulkanicGalExecutionRequest.RenderPassBeginRequest request
    );

    default VulkanicGalExecutionRequest.ExecutionResult executeRenderPassEnd(
        CommandContext ctx,
        VulkanicGalExecutionRequest.RenderPassEndRequest request,
        VulkanicRenderPass pass
    ) {
        try {
            pass.close();
            return VulkanicGalExecutionRequest.success(request.semanticIdentity());
        } catch (RuntimeException exception) {
            return VulkanicGalExecutionRequest.backendFailure(request.semanticIdentity(), exception.getMessage());
        }
    }

    default VulkanicGalExecutionRequest.ExecutionResult executeComputePassBegin(
        CommandContext ctx,
        VulkanicGalExecutionRequest.ComputePassBeginRequest request
    ) {
        return VulkanicGalExecutionRequest.success(request.semanticIdentity());
    }

    default VulkanicGalExecutionRequest.ExecutionResult executeComputePassEnd(
        CommandContext ctx,
        VulkanicGalExecutionRequest.ComputePassEndRequest request
    ) {
        return VulkanicGalExecutionRequest.success(request.semanticIdentity());
    }
}
