package net.vulkanic.backends.vulkan;

import net.vulkanic.VulkanicIndexType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Owns command-buffer-local graphics execution state for Vulkan command recording.
 *
 * <p>The coordinator stores only immutable identities and normalized command state supplied by
 * {@link VulkanBackend.NativeSpine}. It never allocates resources and never emits Vulkan calls.
 * NativeSpine remains responsible for materializing Vulkan structs/commands and publishes state
 * here only after command emission succeeds.</p>
 */
final class VulkanGraphicsCommandExecutionCoordinator {
    private static final GraphicsDynamicState EMPTY_DYNAMIC_STATE =
        new GraphicsDynamicState(Map.of(), false, null);

    private final Map<Long, GraphicsCommandBufferState> graphicsStates = new LinkedHashMap<>();
    private long skippedRedundantPipelineBindCount;
    private long skippedRedundantVertexBindCount;
    private long skippedRedundantIndexBindCount;
    private long skippedRedundantDescriptorBindCount;
    private long skippedRedundantDynamicStateCount;
    private long skippedRedundantPushConstantCount;

    GraphicsExecutionPlan planGraphicsExecution(GraphicsExecutionRequest request) {
        Objects.requireNonNull(request, "request");
        GraphicsCommandBufferState current = currentState(request.commandBuffer());
        List<GraphicsCommandOperation> operations = new ArrayList<>();

        PipelineBindingRequirement pipeline = request.pipeline();
        if (pipeline.pipelineHandle() != 0L && (current == null || !pipeline.equals(current.pipeline))) {
            operations.add(GraphicsCommandOperation.bindPipeline(pipeline));
        } else if (pipeline.pipelineHandle() != 0L) {
            skippedRedundantPipelineBindCount++;
        }

        DescriptorBindingRequirement descriptor = request.descriptor();
        if (descriptor != null) {
            boolean pipelineLayoutChanged = current == null
                || current.pipeline == null
                || current.pipeline.pipelineLayoutHandle() != pipeline.pipelineLayoutHandle();
            if (pipelineLayoutChanged || !descriptor.equals(current.descriptor)) {
                operations.add(GraphicsCommandOperation.bindDescriptorSet(descriptor));
            } else {
                skippedRedundantDescriptorBindCount++;
            }
        }

        Map<Integer, VertexBufferBindingRequirement> nextVertices = new LinkedHashMap<>();
        for (VertexBufferBindingRequirement vertex : request.vertexBuffers()) {
            nextVertices.put(vertex.binding(), vertex);
            VertexBufferBindingRequirement currentVertex = current == null
                ? null
                : current.vertexBuffers.get(vertex.binding());
            if (!vertex.equals(currentVertex)) {
                operations.add(GraphicsCommandOperation.bindVertexBuffer(vertex));
            } else {
                skippedRedundantVertexBindCount++;
            }
        }

        IndexBufferBindingRequirement index = request.indexBuffer();
        if (index != null) {
            if (current == null || !index.equals(current.indexBuffer)) {
                operations.add(GraphicsCommandOperation.bindIndexBuffer(index));
            } else {
                skippedRedundantIndexBindCount++;
            }
        }

        GraphicsDynamicState currentDynamicState = current == null ? EMPTY_DYNAMIC_STATE : current.dynamicState;
        Map<String, DynamicStateRequirement> nextDynamicStates =
            new LinkedHashMap<>(currentDynamicState.emittedStates());
        for (DynamicStateRequirement dynamicState : request.dynamicStates()) {
            DynamicStateRequirement currentDynamic = currentDynamicState.emittedStates().get(dynamicState.name());
            if (!dynamicState.equals(currentDynamic)) {
                operations.add(GraphicsCommandOperation.dynamicState(dynamicState));
                nextDynamicStates.put(dynamicState.name(), dynamicState);
            } else {
                skippedRedundantDynamicStateCount++;
            }
        }

        Map<PushConstantKey, PushConstantRequirement> nextPushConstants = current == null
            ? new LinkedHashMap<>()
            : new LinkedHashMap<>(current.pushConstants);
        for (PushConstantRequirement pushConstant : request.pushConstants()) {
            PushConstantKey key = PushConstantKey.from(pushConstant);
            PushConstantRequirement currentPushConstant = nextPushConstants.get(key);
            if (!pushConstant.equals(currentPushConstant)) {
                operations.add(GraphicsCommandOperation.pushConstants(pushConstant));
                nextPushConstants.put(key, pushConstant);
            } else {
                skippedRedundantPushConstantCount++;
            }
        }

        operations.add(GraphicsCommandOperation.draw(request.drawCommand()));

        GraphicsCommandBufferState published = new GraphicsCommandBufferState(
            request.commandBuffer().generation(),
            request.renderPassCompatibilityKey(),
            pipeline,
            descriptor,
            nextVertices,
            index,
            new GraphicsDynamicState(
                nextDynamicStates,
                currentDynamicState.scissorTestEnabled(),
                currentDynamicState.cachedScissor()
            ),
            nextPushConstants
        );
        return new GraphicsExecutionPlan(request, operations, published);
    }

    DynamicStatePlan planRenderPassDefaultDynamicState(
        CommandBufferIdentity commandBuffer,
        @Nullable Object renderPassCompatibilityKey,
        RenderTargetContext context,
        String semanticSource
    ) {
        Objects.requireNonNull(context, "context");
        GraphicsCommandBufferState current = currentState(commandBuffer);
        GraphicsCommandBufferState base = stateOrEmpty(commandBuffer, renderPassCompatibilityKey, current);
        Map<String, DynamicStateRequirement> nextDynamicStates = new LinkedHashMap<>();
        List<GraphicsCommandOperation> operations = new ArrayList<>();

        DynamicStateRequirement viewport = DynamicStateRequirement.viewport(
            normalizeViewport(context, new ViewportRequest(0, 0, context.activeWidth(), context.activeHeight(), 0.0f, 1.0f))
        );
        operations.add(GraphicsCommandOperation.dynamicState(viewport));
        nextDynamicStates.put(viewport.name(), viewport);

        DynamicStateRequirement scissor = DynamicStateRequirement.scissor(normalizeFullScissor(context));
        operations.add(GraphicsCommandOperation.dynamicState(scissor));
        nextDynamicStates.put(scissor.name(), scissor);

        return new DynamicStatePlan(
            commandBuffer,
            semanticSource,
            operations,
            base.withDynamicState(new GraphicsDynamicState(nextDynamicStates, false, null))
        );
    }

    DynamicStatePlan planViewport(
        CommandBufferIdentity commandBuffer,
        @Nullable Object renderPassCompatibilityKey,
        RenderTargetContext context,
        ViewportRequest request,
        String semanticSource
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(request, "request");
        GraphicsCommandBufferState current = currentState(commandBuffer);
        GraphicsCommandBufferState base = stateOrEmpty(commandBuffer, renderPassCompatibilityKey, current);
        GraphicsDynamicState dynamicState = base.dynamicState;
        Map<String, DynamicStateRequirement> nextDynamicStates =
            new LinkedHashMap<>(dynamicState.emittedStates());
        DynamicStateRequirement viewport = DynamicStateRequirement.viewport(normalizeViewport(context, request));
        List<GraphicsCommandOperation> operations = new ArrayList<>();
        if (!viewport.equals(dynamicState.emittedStates().get(viewport.name()))) {
            operations.add(GraphicsCommandOperation.dynamicState(viewport));
            nextDynamicStates.put(viewport.name(), viewport);
        } else {
            skippedRedundantDynamicStateCount++;
        }
        return new DynamicStatePlan(
            commandBuffer,
            semanticSource,
            operations,
            base.withDynamicState(new GraphicsDynamicState(
                nextDynamicStates,
                dynamicState.scissorTestEnabled(),
                dynamicState.cachedScissor()
            ))
        );
    }

    DynamicStatePlan planScissorRect(
        CommandBufferIdentity commandBuffer,
        @Nullable Object renderPassCompatibilityKey,
        RenderTargetContext context,
        ScissorRequest request,
        String semanticSource
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(request, "request");
        GraphicsCommandBufferState current = currentState(commandBuffer);
        GraphicsCommandBufferState base = stateOrEmpty(commandBuffer, renderPassCompatibilityKey, current);
        GraphicsDynamicState dynamicState = base.dynamicState;
        ScissorRequest cachedScissor = request;
        return planScissorPublication(
            commandBuffer,
            semanticSource,
            context,
            base,
            dynamicState.scissorTestEnabled(),
            cachedScissor
        );
    }

    DynamicStatePlan planScissorTestEnabled(
        CommandBufferIdentity commandBuffer,
        @Nullable Object renderPassCompatibilityKey,
        RenderTargetContext context,
        boolean enabled,
        String semanticSource
    ) {
        Objects.requireNonNull(context, "context");
        GraphicsCommandBufferState current = currentState(commandBuffer);
        GraphicsCommandBufferState base = stateOrEmpty(commandBuffer, renderPassCompatibilityKey, current);
        return planScissorPublication(
            commandBuffer,
            semanticSource,
            context,
            base,
            enabled,
            base.dynamicState.cachedScissor()
        );
    }

    DynamicStatePlan planResetScissorToRenderArea(
        CommandBufferIdentity commandBuffer,
        @Nullable Object renderPassCompatibilityKey,
        RenderTargetContext context,
        String semanticSource
    ) {
        Objects.requireNonNull(context, "context");
        GraphicsCommandBufferState current = currentState(commandBuffer);
        GraphicsCommandBufferState base = stateOrEmpty(commandBuffer, renderPassCompatibilityKey, current);
        return planScissorPublication(
            commandBuffer,
            semanticSource,
            context,
            base,
            base.dynamicState.scissorTestEnabled(),
            base.dynamicState.cachedScissor(),
            true
        );
    }

    GraphicsExecutionPlan planPipelineBinding(
        CommandBufferIdentity commandBuffer,
        @Nullable Object renderPassCompatibilityKey,
        PipelineBindingRequirement pipeline,
        String semanticSource
    ) {
        GraphicsCommandBufferState current = currentState(commandBuffer);
        List<VertexBufferBindingRequirement> vertices = current == null
            ? List.of()
            : new ArrayList<>(current.vertexBuffers.values());
        return planGraphicsExecution(new GraphicsExecutionRequest(
            commandBuffer,
            renderPassCompatibilityKey,
            semanticSource,
            pipeline,
            null,
            vertices,
            current == null ? null : current.indexBuffer,
            List.of(),
            List.of(),
            DrawCommandRequirement.none(semanticSource)
        ));
    }

    GraphicsExecutionPlan planDescriptorBinding(
        CommandBufferIdentity commandBuffer,
        @Nullable Object renderPassCompatibilityKey,
        PipelineBindingRequirement pipeline,
        @Nullable DescriptorBindingRequirement descriptor,
        String semanticSource
    ) {
        GraphicsCommandBufferState current = currentState(commandBuffer);
        List<VertexBufferBindingRequirement> vertices = current == null
            ? List.of()
            : new ArrayList<>(current.vertexBuffers.values());
        return planGraphicsExecution(new GraphicsExecutionRequest(
            commandBuffer,
            renderPassCompatibilityKey,
            semanticSource,
            pipeline,
            descriptor,
            vertices,
            current == null ? null : current.indexBuffer,
            List.of(),
            List.of(),
            DrawCommandRequirement.none(semanticSource)
        ));
    }

    GraphicsExecutionPlan planVertexBufferBinding(
        CommandBufferIdentity commandBuffer,
        @Nullable Object renderPassCompatibilityKey,
        VertexBufferBindingRequirement vertexBuffer,
        String semanticSource
    ) {
        GraphicsCommandBufferState current = currentState(commandBuffer);
        PipelineBindingRequirement pipeline = current == null ? PipelineBindingRequirement.none() : current.pipeline;
        DescriptorBindingRequirement descriptor = current == null ? null : current.descriptor;
        List<VertexBufferBindingRequirement> vertices = current == null
            ? new ArrayList<>()
            : new ArrayList<>(current.vertexBuffers.values());
        vertices.removeIf(existing -> existing.binding() == vertexBuffer.binding());
        vertices.add(vertexBuffer);
        return planGraphicsExecution(new GraphicsExecutionRequest(
            commandBuffer,
            renderPassCompatibilityKey,
            semanticSource,
            pipeline,
            descriptor,
            vertices,
            current == null ? null : current.indexBuffer,
            List.of(),
            List.of(),
            DrawCommandRequirement.none(semanticSource)
        ));
    }

    GraphicsExecutionPlan planIndexBufferBinding(
        CommandBufferIdentity commandBuffer,
        @Nullable Object renderPassCompatibilityKey,
        IndexBufferBindingRequirement indexBuffer,
        String semanticSource
    ) {
        GraphicsCommandBufferState current = currentState(commandBuffer);
        PipelineBindingRequirement pipeline = current == null ? PipelineBindingRequirement.none() : current.pipeline;
        DescriptorBindingRequirement descriptor = current == null ? null : current.descriptor;
        List<VertexBufferBindingRequirement> vertices = current == null
            ? List.of()
            : new ArrayList<>(current.vertexBuffers.values());
        return planGraphicsExecution(new GraphicsExecutionRequest(
            commandBuffer,
            renderPassCompatibilityKey,
            semanticSource,
            pipeline,
            descriptor,
            vertices,
            indexBuffer,
            List.of(),
            List.of(),
            DrawCommandRequirement.none(semanticSource)
        ));
    }

    GraphicsExecutionPlan planDrawFromCurrentState(
        CommandBufferIdentity commandBuffer,
        @Nullable Object renderPassCompatibilityKey,
        DrawCommandRequirement drawCommand
    ) {
        Objects.requireNonNull(drawCommand, "drawCommand");
        GraphicsCommandBufferState current = currentState(commandBuffer);
        if (current == null || current.pipeline.pipelineHandle() == 0L) {
            throw new IllegalStateException("Graphics draw requires a published pipeline binding for " + drawCommand.semanticSource());
        }
        if (drawCommand.kind() == DrawCommandKind.INDEXED && current.indexBuffer == null) {
            throw new IllegalStateException("Indexed graphics draw requires a published index buffer for " + drawCommand.semanticSource());
        }
        return planGraphicsExecution(new GraphicsExecutionRequest(
            commandBuffer,
            renderPassCompatibilityKey,
            drawCommand.semanticSource(),
            current.pipeline,
            current.descriptor,
            new ArrayList<>(current.vertexBuffers.values()),
            current.indexBuffer,
            List.of(),
            List.of(),
            drawCommand
        ));
    }

    void complete(GraphicsExecutionPlan plan) {
        Objects.requireNonNull(plan, "plan");
        if (plan.request().drawCommand().kind() == DrawCommandKind.NONE
            && plan.operations().stream().noneMatch(operation -> operation.type() != GraphicsCommandOperationType.DRAW)) {
            return;
        }
        graphicsStates.put(plan.request().commandBuffer().handle(), plan.publishedState());
    }

    void complete(DynamicStatePlan plan) {
        Objects.requireNonNull(plan, "plan");
        graphicsStates.put(plan.commandBuffer().handle(), plan.publishedState());
    }

    void abandon(GraphicsExecutionPlan plan) {
        Objects.requireNonNull(plan, "plan");
    }

    void abandon(DynamicStatePlan plan) {
        Objects.requireNonNull(plan, "plan");
    }

    void resetCommandBuffer(long commandBufferHandle) {
        graphicsStates.remove(commandBufferHandle);
    }

    void beginRenderPass(long commandBufferHandle, @Nullable Object compatibilityKey) {
        GraphicsCommandBufferState current = graphicsStates.get(commandBufferHandle);
        if (current != null && !Objects.equals(current.renderPassCompatibilityKey, compatibilityKey)) {
            graphicsStates.put(commandBufferHandle, current.withoutRenderPassSensitiveState(compatibilityKey));
        }
    }

    void endRenderPass(long commandBufferHandle) {
        GraphicsCommandBufferState current = graphicsStates.get(commandBufferHandle);
        if (current != null) {
            graphicsStates.put(commandBufferHandle, current.withoutRenderPassCompatibility());
        }
    }

    void clear() {
        graphicsStates.clear();
    }

    int graphicsStateCountForTests() {
        return graphicsStates.size();
    }

    long skippedRedundantPipelineBindCount() {
        return skippedRedundantPipelineBindCount;
    }

    long skippedRedundantVertexBindCount() {
        return skippedRedundantVertexBindCount;
    }

    long skippedRedundantIndexBindCount() {
        return skippedRedundantIndexBindCount;
    }

    long skippedRedundantDescriptorBindCount() {
        return skippedRedundantDescriptorBindCount;
    }

    long skippedRedundantDynamicStateCount() {
        return skippedRedundantDynamicStateCount;
    }

    long skippedRedundantPushConstantCount() {
        return skippedRedundantPushConstantCount;
    }

    private GraphicsCommandBufferState currentState(CommandBufferIdentity commandBuffer) {
        GraphicsCommandBufferState current = graphicsStates.get(commandBuffer.handle());
        if (current != null && current.generation != commandBuffer.generation()) {
            graphicsStates.remove(commandBuffer.handle());
            return null;
        }
        return current;
    }

    private static GraphicsCommandBufferState stateOrEmpty(
        CommandBufferIdentity commandBuffer,
        @Nullable Object renderPassCompatibilityKey,
        @Nullable GraphicsCommandBufferState current
    ) {
        if (current != null) {
            return current.withRenderPassCompatibility(renderPassCompatibilityKey);
        }
        return new GraphicsCommandBufferState(
            commandBuffer.generation(),
            renderPassCompatibilityKey,
            PipelineBindingRequirement.none(),
            null,
            Map.of(),
            null,
            EMPTY_DYNAMIC_STATE,
            Map.of()
        );
    }

    private DynamicStatePlan planScissorPublication(
        CommandBufferIdentity commandBuffer,
        String semanticSource,
        RenderTargetContext context,
        GraphicsCommandBufferState base,
        boolean scissorTestEnabled,
        @Nullable ScissorRequest cachedScissor
    ) {
        return planScissorPublication(commandBuffer, semanticSource, context, base, scissorTestEnabled, cachedScissor, false);
    }

    private DynamicStatePlan planScissorPublication(
        CommandBufferIdentity commandBuffer,
        String semanticSource,
        RenderTargetContext context,
        GraphicsCommandBufferState base,
        boolean scissorTestEnabled,
        @Nullable ScissorRequest cachedScissor,
        boolean forceFullRenderArea
    ) {
        GraphicsDynamicState dynamicState = base.dynamicState;
        Map<String, DynamicStateRequirement> nextDynamicStates =
            new LinkedHashMap<>(dynamicState.emittedStates());
        List<GraphicsCommandOperation> operations = new ArrayList<>();

        DynamicStateRequirement scissor = null;
        if (forceFullRenderArea) {
            if (context.renderPassRecording()) {
                scissor = DynamicStateRequirement.scissor(normalizeFullScissor(context));
            }
        } else if (context.renderPassRecording() && context.targetsSwapchain()) {
            scissor = DynamicStateRequirement.scissor(normalizeFullScissor(context));
        } else if (context.renderPassRecording() && scissorTestEnabled) {
            scissor = DynamicStateRequirement.scissor(
                normalizeScissor(context, cachedScissor == null
                    ? new ScissorRequest(0, 0, context.activeWidth(), context.activeHeight())
                    : cachedScissor)
            );
        } else if (context.renderPassRecording() && !scissorTestEnabled) {
            scissor = DynamicStateRequirement.scissor(normalizeFullScissor(context));
        }

        if (scissor != null) {
            if (!scissor.equals(dynamicState.emittedStates().get(scissor.name()))) {
                operations.add(GraphicsCommandOperation.dynamicState(scissor));
                nextDynamicStates.put(scissor.name(), scissor);
            } else {
                skippedRedundantDynamicStateCount++;
            }
        }

        return new DynamicStatePlan(
            commandBuffer,
            semanticSource,
            operations,
            base.withDynamicState(new GraphicsDynamicState(nextDynamicStates, scissorTestEnabled, cachedScissor))
        );
    }

    private static NormalizedViewport normalizeViewport(RenderTargetContext context, ViewportRequest request) {
        int x = request.x();
        int y = request.y();
        int width = request.width();
        int height = request.height();
        if (context.renderPassRecording()
            && context.targetsSwapchain()
            && context.activeWidth() > 0
            && context.activeHeight() > 0) {
            x = 0;
            y = 0;
            width = context.activeWidth();
            height = context.activeHeight();
        }
        int viewportWidth = Math.max(width, 1);
        int viewportHeight = Math.max(height, 1);
        int framebufferHeight = context.activeHeight() > 0
            ? context.activeHeight()
            : Math.max(context.swapchainHeight(), viewportHeight);
        if (context.renderPassRecording() && context.targetsSwapchain()) {
            return new NormalizedViewport(x, y, viewportWidth, viewportHeight, request.minDepth(), request.maxDepth());
        }
        return new NormalizedViewport(
            x,
            framebufferHeight - y,
            viewportWidth,
            -viewportHeight,
            request.minDepth(),
            request.maxDepth()
        );
    }

    private static NormalizedScissor normalizeFullScissor(RenderTargetContext context) {
        int fullWidth = context.activeWidth() > 0 ? context.activeWidth() : context.swapchainWidth();
        int fullHeight = context.activeHeight() > 0 ? context.activeHeight() : context.swapchainHeight();
        return normalizeScissor(context, new ScissorRequest(0, 0, fullWidth, fullHeight));
    }

    private static NormalizedScissor normalizeScissor(RenderTargetContext context, ScissorRequest request) {
        int scissorWidth = Math.max(request.width(), 0);
        int scissorHeight = Math.max(request.height(), 0);
        int framebufferWidth = context.activeWidth() > 0
            ? context.activeWidth()
            : Math.max(context.swapchainWidth(), scissorWidth);
        int framebufferHeight = context.activeHeight() > 0
            ? context.activeHeight()
            : Math.max(context.swapchainHeight(), scissorHeight);
        int translatedY = framebufferHeight - (request.y() + scissorHeight);
        int clampedX = Math.max(0, Math.min(request.x(), framebufferWidth));
        int clampedY = Math.max(0, Math.min(translatedY, framebufferHeight));
        int maxWidth = Math.max(0, framebufferWidth - clampedX);
        int maxHeight = Math.max(0, framebufferHeight - clampedY);
        int clampedWidth = Math.min(scissorWidth, maxWidth);
        int clampedHeight = Math.min(scissorHeight, maxHeight);
        return new NormalizedScissor(clampedX, clampedY, clampedWidth, clampedHeight);
    }

    private record GraphicsCommandBufferState(
        long generation,
        @Nullable Object renderPassCompatibilityKey,
        PipelineBindingRequirement pipeline,
        @Nullable DescriptorBindingRequirement descriptor,
        Map<Integer, VertexBufferBindingRequirement> vertexBuffers,
        @Nullable IndexBufferBindingRequirement indexBuffer,
        GraphicsDynamicState dynamicState,
        Map<PushConstantKey, PushConstantRequirement> pushConstants
    ) {
        GraphicsCommandBufferState {
            Objects.requireNonNull(pipeline, "pipeline");
            vertexBuffers = Map.copyOf(vertexBuffers);
            Objects.requireNonNull(dynamicState, "dynamicState");
            pushConstants = Map.copyOf(pushConstants);
        }

        GraphicsCommandBufferState withoutRenderPassSensitiveState(@Nullable Object newCompatibilityKey) {
            return new GraphicsCommandBufferState(
                generation,
                newCompatibilityKey,
                PipelineBindingRequirement.none(),
                null,
                vertexBuffers,
                indexBuffer,
                new GraphicsDynamicState(Map.of(), dynamicState.scissorTestEnabled(), dynamicState.cachedScissor()),
                pushConstants
            );
        }

        GraphicsCommandBufferState withoutRenderPassCompatibility() {
            return withRenderPassCompatibility(null);
        }

        GraphicsCommandBufferState withRenderPassCompatibility(@Nullable Object newCompatibilityKey) {
            return new GraphicsCommandBufferState(
                generation,
                newCompatibilityKey,
                pipeline,
                descriptor,
                vertexBuffers,
                indexBuffer,
                dynamicState,
                pushConstants
            );
        }

        GraphicsCommandBufferState withDynamicState(GraphicsDynamicState newDynamicState) {
            return new GraphicsCommandBufferState(
                generation,
                renderPassCompatibilityKey,
                pipeline,
                descriptor,
                vertexBuffers,
                indexBuffer,
                newDynamicState,
                pushConstants
            );
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

    record RenderTargetContext(
        boolean renderPassRecording,
        boolean targetsSwapchain,
        int activeWidth,
        int activeHeight,
        int swapchainWidth,
        int swapchainHeight
    ) {
    }

    record ViewportRequest(int x, int y, int width, int height, float minDepth, float maxDepth) {
    }

    record ScissorRequest(int x, int y, int width, int height) {
    }

    record NormalizedViewport(float x, float y, float width, float height, float minDepth, float maxDepth) {
    }

    record NormalizedScissor(int x, int y, int width, int height) {
    }

    record GraphicsDynamicState(
        Map<String, DynamicStateRequirement> emittedStates,
        boolean scissorTestEnabled,
        @Nullable ScissorRequest cachedScissor
    ) {
        GraphicsDynamicState {
            emittedStates = Map.copyOf(emittedStates);
        }
    }

    record GraphicsExecutionRequest(
        CommandBufferIdentity commandBuffer,
        @Nullable Object renderPassCompatibilityKey,
        String semanticSource,
        PipelineBindingRequirement pipeline,
        @Nullable DescriptorBindingRequirement descriptor,
        List<VertexBufferBindingRequirement> vertexBuffers,
        @Nullable IndexBufferBindingRequirement indexBuffer,
        List<DynamicStateRequirement> dynamicStates,
        List<PushConstantRequirement> pushConstants,
        DrawCommandRequirement drawCommand
    ) {
        GraphicsExecutionRequest {
            Objects.requireNonNull(commandBuffer, "commandBuffer");
            Objects.requireNonNull(semanticSource, "semanticSource");
            Objects.requireNonNull(pipeline, "pipeline");
            vertexBuffers = List.copyOf(vertexBuffers);
            dynamicStates = List.copyOf(dynamicStates);
            pushConstants = List.copyOf(pushConstants);
            Objects.requireNonNull(drawCommand, "drawCommand");
            if (drawCommand.kind() == DrawCommandKind.INDEXED && indexBuffer == null) {
                throw new IllegalArgumentException("Indexed graphics execution requires an index buffer");
            }
            if (pipeline.pipelineHandle() == 0L && drawCommand.kind() != DrawCommandKind.NONE) {
                throw new IllegalArgumentException("Graphics draw requires a bound pipeline");
            }
        }
    }

    record PipelineBindingRequirement(long pipelineHandle, long pipelineLayoutHandle, @Nullable Object compatibilityKey) {
        static PipelineBindingRequirement none() {
            return new PipelineBindingRequirement(0L, 0L, null);
        }
    }

    record DescriptorBindingRequirement(
        long pipelineLayoutHandle,
        long descriptorSetHandle,
        long descriptorSetLayoutHandle,
        @Nullable Object cacheKey,
        List<Integer> dynamicOffsets
    ) {
        DescriptorBindingRequirement {
            if (descriptorSetHandle == 0L) {
                throw new IllegalArgumentException("descriptorSetHandle must be non-zero");
            }
            dynamicOffsets = List.copyOf(dynamicOffsets);
        }
    }

    record VertexBufferBindingRequirement(
        int binding,
        long bufferHandle,
        long offset,
        int stride,
        boolean defaultAttributeBuffer
    ) {
        VertexBufferBindingRequirement {
            if (binding < 0) {
                throw new IllegalArgumentException("vertex binding must be >= 0");
            }
            if (bufferHandle == 0L) {
                throw new IllegalArgumentException("vertex buffer handle must be non-zero");
            }
            if (offset < 0L) {
                throw new IllegalArgumentException("vertex buffer offset must be >= 0");
            }
        }
    }

    record IndexBufferBindingRequirement(
        long bufferHandle,
        long offset,
        int sizeBytes,
        VulkanicIndexType indexType
    ) {
        IndexBufferBindingRequirement {
            if (bufferHandle == 0L) {
                throw new IllegalArgumentException("index buffer handle must be non-zero");
            }
            if (offset < 0L) {
                throw new IllegalArgumentException("index buffer offset must be >= 0");
            }
            if (sizeBytes <= 0) {
                throw new IllegalArgumentException("index buffer size must be > 0");
            }
            Objects.requireNonNull(indexType, "indexType");
        }
    }

    record DynamicStateRequirement(String name, Object value) {
        DynamicStateRequirement {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
        }

        static DynamicStateRequirement viewport(NormalizedViewport viewport) {
            return new DynamicStateRequirement("viewport", viewport);
        }

        static DynamicStateRequirement scissor(NormalizedScissor scissor) {
            return new DynamicStateRequirement("scissor", scissor);
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

    record DrawCommandRequirement(
        DrawCommandKind kind,
        String semanticSource,
        int firstVertex,
        int vertexCount,
        int firstIndex,
        int indexCount,
        int baseVertex,
        int instanceCount,
        int baseInstance
    ) {
        DrawCommandRequirement {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(semanticSource, "semanticSource");
        }

        static DrawCommandRequirement none(String semanticSource) {
            return new DrawCommandRequirement(DrawCommandKind.NONE, semanticSource, 0, 0, 0, 0, 0, 0, 0);
        }

        static DrawCommandRequirement arrays(String semanticSource, int firstVertex, int vertexCount, int instanceCount) {
            return new DrawCommandRequirement(
                DrawCommandKind.ARRAYS,
                semanticSource,
                firstVertex,
                vertexCount,
                0,
                0,
                0,
                instanceCount,
                0
            );
        }

        static DrawCommandRequirement indexed(
            String semanticSource,
            int firstIndex,
            int indexCount,
            int baseVertex,
            int instanceCount
        ) {
            return new DrawCommandRequirement(
                DrawCommandKind.INDEXED,
                semanticSource,
                0,
                0,
                firstIndex,
                indexCount,
                baseVertex,
                instanceCount,
                0
            );
        }
    }

    enum DrawCommandKind {
        NONE,
        ARRAYS,
        INDEXED
    }

    record GraphicsCommandOperation(GraphicsCommandOperationType type, Object payload) {
        GraphicsCommandOperation {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(payload, "payload");
        }

        static GraphicsCommandOperation bindPipeline(PipelineBindingRequirement pipeline) {
            return new GraphicsCommandOperation(GraphicsCommandOperationType.BIND_PIPELINE, pipeline);
        }

        static GraphicsCommandOperation bindDescriptorSet(DescriptorBindingRequirement descriptor) {
            return new GraphicsCommandOperation(GraphicsCommandOperationType.BIND_DESCRIPTOR_SET, descriptor);
        }

        static GraphicsCommandOperation bindVertexBuffer(VertexBufferBindingRequirement vertex) {
            return new GraphicsCommandOperation(GraphicsCommandOperationType.BIND_VERTEX_BUFFER, vertex);
        }

        static GraphicsCommandOperation bindIndexBuffer(IndexBufferBindingRequirement index) {
            return new GraphicsCommandOperation(GraphicsCommandOperationType.BIND_INDEX_BUFFER, index);
        }

        static GraphicsCommandOperation dynamicState(DynamicStateRequirement dynamicState) {
            return new GraphicsCommandOperation(GraphicsCommandOperationType.DYNAMIC_STATE, dynamicState);
        }

        static GraphicsCommandOperation pushConstants(PushConstantRequirement pushConstant) {
            return new GraphicsCommandOperation(GraphicsCommandOperationType.PUSH_CONSTANTS, pushConstant);
        }

        static GraphicsCommandOperation draw(DrawCommandRequirement draw) {
            return new GraphicsCommandOperation(GraphicsCommandOperationType.DRAW, draw);
        }
    }

    enum GraphicsCommandOperationType {
        BIND_PIPELINE,
        BIND_DESCRIPTOR_SET,
        BIND_VERTEX_BUFFER,
        BIND_INDEX_BUFFER,
        DYNAMIC_STATE,
        PUSH_CONSTANTS,
        DRAW
    }

    record GraphicsExecutionPlan(
        GraphicsExecutionRequest request,
        List<GraphicsCommandOperation> operations,
        GraphicsCommandBufferState publishedState
    ) {
        GraphicsExecutionPlan {
            operations = List.copyOf(operations);
        }
    }

    record DynamicStatePlan(
        CommandBufferIdentity commandBuffer,
        String semanticSource,
        List<GraphicsCommandOperation> operations,
        GraphicsCommandBufferState publishedState
    ) {
        DynamicStatePlan {
            Objects.requireNonNull(commandBuffer, "commandBuffer");
            Objects.requireNonNull(semanticSource, "semanticSource");
            operations = List.copyOf(operations);
            Objects.requireNonNull(publishedState, "publishedState");
        }
    }
}
