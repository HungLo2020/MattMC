package net.vulkanic;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Immutable internal Vulkanic GAL submission requests.
 *
 * <p>These records are the backend-neutral boundary between the legacy
 * OpenGL-shaped frontend and backend execution. Requests intentionally avoid
 * Vulkan layouts, access masks, stages, queue families, descriptor handles, and
 * native object handles. Temporary legacy compatibility metadata is kept in a
 * separate record so future public GAL work can remove it without changing the
 * backend-neutral command model.</p>
 */
public final class VulkanicGalExecutionRequest {
    private VulkanicGalExecutionRequest() {
    }

    public record SemanticIdentity(
        String subsystem,
        String phase,
        String pipeline,
        String material,
        String outputTarget,
        String frameContext,
        int ordinal
    ) {
        public SemanticIdentity {
            subsystem = requireNonBlank(subsystem, "subsystem");
            phase = requireNonBlank(phase, "phase");
            pipeline = requireNonBlank(pipeline, "pipeline");
            material = requireNonBlank(material, "material");
            outputTarget = requireNonBlank(outputTarget, "outputTarget");
            frameContext = requireNonBlank(frameContext, "frameContext");
            if (ordinal < 0) {
                throw new IllegalArgumentException("ordinal must be >= 0");
            }
        }

        public static SemanticIdentity legacy(String operation) {
            return new SemanticIdentity(
                "legacy",
                operation,
                "compatibility-state",
                "legacy",
                "active-target",
                "current-frame",
                0
            );
        }

        public String label() {
            return subsystem + ":" + phase + ":" + pipeline + ":" + material + ":" + outputTarget
                + ":frame=" + frameContext + ":ordinal=" + ordinal;
        }
    }

    public record LegacyCompatibilityMetadata(
        String operation,
        OptionalInt legacyMode,
        OptionalInt legacyIndexType,
        List<Integer> legacyTargets
    ) {
        public LegacyCompatibilityMetadata {
            operation = requireNonBlank(operation, "operation");
            legacyMode = Objects.requireNonNull(legacyMode, "legacyMode");
            legacyIndexType = Objects.requireNonNull(legacyIndexType, "legacyIndexType");
            legacyTargets = List.copyOf(Objects.requireNonNull(legacyTargets, "legacyTargets"));
        }

        public static LegacyCompatibilityMetadata operation(String operation) {
            return new LegacyCompatibilityMetadata(operation, OptionalInt.empty(), OptionalInt.empty(), List.of());
        }

        public static LegacyCompatibilityMetadata draw(String operation, VulkanicPrimitiveMode mode) {
            return new LegacyCompatibilityMetadata(
                operation,
                OptionalInt.of(Objects.requireNonNull(mode, "mode").toGlModeConstant()),
                OptionalInt.empty(),
                List.of()
            );
        }

        public static LegacyCompatibilityMetadata indexedDraw(
            String operation,
            VulkanicPrimitiveMode mode,
            VulkanicIndexType indexType
        ) {
            return new LegacyCompatibilityMetadata(
                operation,
                OptionalInt.of(Objects.requireNonNull(mode, "mode").toGlModeConstant()),
                OptionalInt.of(Objects.requireNonNull(indexType, "indexType").toGlTypeConstant()),
                List.of()
            );
        }

        public static LegacyCompatibilityMetadata targets(String operation, int... targets) {
            List<Integer> targetList = java.util.Arrays.stream(targets).boxed().toList();
            return new LegacyCompatibilityMetadata(operation, OptionalInt.empty(), OptionalInt.empty(), targetList);
        }
    }

    public record PipelineSnapshot(String stableKey, String programIdentity) {
        public PipelineSnapshot {
            stableKey = requireNonBlank(stableKey, "stableKey");
            programIdentity = requireNonBlank(programIdentity, "programIdentity");
        }

        public static PipelineSnapshot legacyCurrent() {
            return new PipelineSnapshot("legacy-current-pipeline", "legacy-current-program");
        }

        public static PipelineSnapshot legacyProgram(int programId) {
            return new PipelineSnapshot("legacy-program:" + programId, "legacy-program:" + programId);
        }
    }

    public record FramebufferSnapshot(String stableKey, String logicalTarget) {
        public FramebufferSnapshot {
            stableKey = requireNonBlank(stableKey, "stableKey");
            logicalTarget = requireNonBlank(logicalTarget, "logicalTarget");
        }

        public static FramebufferSnapshot active() {
            return new FramebufferSnapshot("legacy-active-framebuffer", "active-target");
        }

        public static FramebufferSnapshot legacyFramebuffer(int framebuffer) {
            return new FramebufferSnapshot("legacy-framebuffer:" + framebuffer, "legacy-framebuffer:" + framebuffer);
        }
    }

    public record DynamicStateSnapshot(
        Optional<Viewport> viewport,
        Optional<Scissor> scissor
    ) {
        public DynamicStateSnapshot {
            viewport = Objects.requireNonNull(viewport, "viewport");
            scissor = Objects.requireNonNull(scissor, "scissor");
        }

        public static DynamicStateSnapshot currentLegacy() {
            return new DynamicStateSnapshot(Optional.empty(), Optional.empty());
        }
    }

    public record Viewport(int x, int y, int width, int height, float minDepth, float maxDepth) {
        public Viewport {
            if (width < 0 || height < 0) {
                throw new IllegalArgumentException("viewport width/height must be >= 0");
            }
        }
    }

    public record Scissor(int x, int y, int width, int height) {
        public Scissor {
            if (width < 0 || height < 0) {
                throw new IllegalArgumentException("scissor width/height must be >= 0");
            }
        }
    }

    public record VertexInputSnapshot(
        List<VulkanicLegacyCompatibilityAdapter.VertexBufferSnapshot> vertexBuffers,
        Optional<VulkanicLegacyCompatibilityAdapter.IndexBufferSnapshot> indexBuffer
    ) {
        public VertexInputSnapshot {
            vertexBuffers = List.copyOf(Objects.requireNonNull(vertexBuffers, "vertexBuffers"));
            indexBuffer = Objects.requireNonNull(indexBuffer, "indexBuffer");
        }

        public static VertexInputSnapshot currentLegacy() {
            return new VertexInputSnapshot(List.of(), Optional.empty());
        }
    }

    public record GraphicsCompatibilitySnapshot(
        Optional<PipelineDescriptor> pipelineDescriptor,
        VertexInputSnapshot vertexInput,
        List<VulkanicPassResourceModel.ResourceUse> resourceUses,
        Optional<PipelineResourcePlanner.Plan> resourceBindingPlan,
        List<VulkanicPassResourceModel.BindingSnapshot> descriptorBindings,
        Optional<VulkanicCompatibilityState.GraphicsSnapshot> sharedCompatibilityState,
        String source
    ) {
        public GraphicsCompatibilitySnapshot {
            pipelineDescriptor = Objects.requireNonNull(pipelineDescriptor, "pipelineDescriptor");
            vertexInput = Objects.requireNonNull(vertexInput, "vertexInput");
            resourceUses = List.copyOf(Objects.requireNonNull(resourceUses, "resourceUses"));
            resourceBindingPlan = Objects.requireNonNull(resourceBindingPlan, "resourceBindingPlan");
            descriptorBindings = List.copyOf(Objects.requireNonNull(descriptorBindings, "descriptorBindings"));
            sharedCompatibilityState = Objects.requireNonNull(sharedCompatibilityState, "sharedCompatibilityState");
            source = requireNonBlank(source, "source");
        }

        public GraphicsCompatibilitySnapshot(
            Optional<PipelineDescriptor> pipelineDescriptor,
            VertexInputSnapshot vertexInput,
            List<VulkanicPassResourceModel.ResourceUse> resourceUses,
            Optional<PipelineResourcePlanner.Plan> resourceBindingPlan,
            List<VulkanicPassResourceModel.BindingSnapshot> descriptorBindings,
            String source
        ) {
            this(
                pipelineDescriptor,
                vertexInput,
                resourceUses,
                resourceBindingPlan,
                descriptorBindings,
                Optional.empty(),
                source
            );
        }

        public static GraphicsCompatibilitySnapshot unresolvedLegacy() {
            return new GraphicsCompatibilitySnapshot(
                Optional.empty(),
                VertexInputSnapshot.currentLegacy(),
                List.of(),
                Optional.empty(),
                List.of(),
                Optional.empty(),
                "unresolved-legacy-compatibility"
            );
        }
    }

    public enum DrawCommandKind {
        ARRAYS,
        INDEXED,
        MULTI_INDEXED_BASE_VERTEX
    }

    public record IndexedDraw(int firstIndex, int indexCount, int baseVertex) {
        public IndexedDraw {
            if (firstIndex < 0 || indexCount < 0) {
                throw new IllegalArgumentException("indexed draw firstIndex/indexCount must be >= 0");
            }
        }
    }

    public record GraphicsDrawCommand(
        DrawCommandKind kind,
        VulkanicPrimitiveMode mode,
        int firstVertex,
        int vertexCount,
        long indexByteOffset,
        int indexCount,
        VulkanicIndexType indexType,
        int instanceCount,
        int baseVertex,
        List<IndexedDraw> indexedDraws
    ) {
        public GraphicsDrawCommand {
            kind = Objects.requireNonNull(kind, "kind");
            mode = Objects.requireNonNull(mode, "mode");
            indexType = Objects.requireNonNull(indexType, "indexType");
            indexedDraws = List.copyOf(Objects.requireNonNull(indexedDraws, "indexedDraws"));
            if (firstVertex < 0 || vertexCount < 0 || indexByteOffset < 0L || indexCount < 0 || instanceCount < 0) {
                throw new IllegalArgumentException("draw command counts and offsets must be >= 0");
            }
            if (kind == DrawCommandKind.MULTI_INDEXED_BASE_VERTEX && indexedDraws.isEmpty()) {
                throw new IllegalArgumentException("multi draw command requires at least one draw");
            }
        }

        public static GraphicsDrawCommand arrays(
            VulkanicPrimitiveMode mode,
            int firstVertex,
            int vertexCount,
            int instanceCount
        ) {
            return new GraphicsDrawCommand(
                DrawCommandKind.ARRAYS,
                mode,
                firstVertex,
                vertexCount,
                0L,
                0,
                VulkanicIndexType.SHORT,
                instanceCount,
                0,
                List.of()
            );
        }

        public static GraphicsDrawCommand indexed(
            VulkanicPrimitiveMode mode,
            int indexCount,
            VulkanicIndexType indexType,
            long indexByteOffset,
            int instanceCount,
            int baseVertex
        ) {
            return new GraphicsDrawCommand(
                DrawCommandKind.INDEXED,
                mode,
                0,
                0,
                indexByteOffset,
                indexCount,
                indexType,
                instanceCount,
                baseVertex,
                List.of()
            );
        }

        public static GraphicsDrawCommand multiIndexedBaseVertex(
            VulkanicPrimitiveMode mode,
            VulkanicIndexType indexType,
            List<IndexedDraw> indexedDraws
        ) {
            return new GraphicsDrawCommand(
                DrawCommandKind.MULTI_INDEXED_BASE_VERTEX,
                mode,
                0,
                0,
                0L,
                0,
                indexType,
                1,
                0,
                indexedDraws
            );
        }
    }

    public record GraphicsDrawRequest(
        SemanticIdentity semanticIdentity,
        PipelineSnapshot pipeline,
        FramebufferSnapshot framebuffer,
        VertexInputSnapshot vertexInput,
        List<VulkanicPassResourceModel.BindingSnapshot> descriptors,
        DynamicStateSnapshot dynamicState,
        GraphicsCompatibilitySnapshot compatibilitySnapshot,
        GraphicsDrawCommand command,
        VulkanicPassResourceModel.PassExecutionPlan resourcePlan,
        LegacyCompatibilityMetadata legacyMetadata
    ) {
        public GraphicsDrawRequest {
            semanticIdentity = Objects.requireNonNull(semanticIdentity, "semanticIdentity");
            pipeline = Objects.requireNonNull(pipeline, "pipeline");
            framebuffer = Objects.requireNonNull(framebuffer, "framebuffer");
            vertexInput = Objects.requireNonNull(vertexInput, "vertexInput");
            descriptors = List.copyOf(Objects.requireNonNull(descriptors, "descriptors"));
            dynamicState = Objects.requireNonNull(dynamicState, "dynamicState");
            compatibilitySnapshot = Objects.requireNonNull(compatibilitySnapshot, "compatibilitySnapshot");
            command = Objects.requireNonNull(command, "command");
            resourcePlan = Objects.requireNonNull(resourcePlan, "resourcePlan");
            legacyMetadata = Objects.requireNonNull(legacyMetadata, "legacyMetadata");
        }

        public static GraphicsDrawRequest legacyArrays(
            String operation,
            VulkanicPrimitiveMode mode,
            int firstVertex,
            int vertexCount,
            int instanceCount
        ) {
            GraphicsDrawCommand command = GraphicsDrawCommand.arrays(mode, firstVertex, vertexCount, instanceCount);
            return legacyDraw(operation, command, LegacyCompatibilityMetadata.draw(operation, mode));
        }

        public static GraphicsDrawRequest legacyIndexed(
            String operation,
            VulkanicPrimitiveMode mode,
            int indexCount,
            VulkanicIndexType indexType,
            long indexByteOffset,
            int instanceCount,
            int baseVertex
        ) {
            GraphicsDrawCommand command = GraphicsDrawCommand.indexed(mode, indexCount, indexType, indexByteOffset, instanceCount, baseVertex);
            return legacyDraw(operation, command, LegacyCompatibilityMetadata.indexedDraw(operation, mode, indexType));
        }

        public static GraphicsDrawRequest legacyMultiIndexedBaseVertex(
            String operation,
            VulkanicPrimitiveMode mode,
            VulkanicIndexType indexType,
            List<IndexedDraw> draws
        ) {
            GraphicsDrawCommand command = GraphicsDrawCommand.multiIndexedBaseVertex(mode, indexType, draws);
            return legacyDraw(operation, command, LegacyCompatibilityMetadata.indexedDraw(operation, mode, indexType));
        }

        private static GraphicsDrawRequest legacyDraw(
            String operation,
            GraphicsDrawCommand command,
            LegacyCompatibilityMetadata metadata
        ) {
            VulkanicLegacyCompatibilityAdapter.DrawCommandSnapshot drawCommand = switch (command.kind()) {
                case ARRAYS -> VulkanicLegacyCompatibilityAdapter.DrawCommandSnapshot.arrays(
                    command.firstVertex(),
                    command.vertexCount(),
                    command.instanceCount()
                );
                case INDEXED -> VulkanicLegacyCompatibilityAdapter.DrawCommandSnapshot.indexed(
                    (int) (command.indexByteOffset() / command.indexType().bytesPerIndex()),
                    command.indexCount(),
                    command.baseVertex(),
                    command.instanceCount()
                );
                case MULTI_INDEXED_BASE_VERTEX -> VulkanicLegacyCompatibilityAdapter.DrawCommandSnapshot.indexed(
                    command.indexedDraws().get(0).firstIndex(),
                    command.indexedDraws().stream().mapToInt(IndexedDraw::indexCount).sum(),
                    command.indexedDraws().get(0).baseVertex(),
                    1
                );
            };
            Optional<VulkanicLegacyCompatibilityAdapter.IndexBufferSnapshot> indexBuffer =
                command.kind() == DrawCommandKind.ARRAYS
                    ? Optional.empty()
                    : Optional.of(new VulkanicLegacyCompatibilityAdapter.IndexBufferSnapshot(
                        "legacy-current-index-buffer",
                        0,
                        command.indexType().bytesPerIndex()
                    ));
            VulkanicPassResourceModel.PassExecutionPlan resourcePlan =
                VulkanicLegacyCompatibilityAdapter.planDraw(new VulkanicLegacyCompatibilityAdapter.DrawSnapshot(
                    operation,
                    List.of(),
                    indexBuffer,
                    List.of(),
                    List.of(),
                    drawCommand,
                    false,
                    false
                ));
            return new GraphicsDrawRequest(
                SemanticIdentity.legacy(operation),
                PipelineSnapshot.legacyCurrent(),
                FramebufferSnapshot.active(),
                VertexInputSnapshot.currentLegacy(),
                List.of(),
                DynamicStateSnapshot.currentLegacy(),
                GraphicsCompatibilitySnapshot.unresolvedLegacy(),
                command,
                resourcePlan,
                metadata
            );
        }

        public GraphicsDrawRequest withCompatibilitySnapshot(GraphicsCompatibilitySnapshot snapshot) {
            Objects.requireNonNull(snapshot, "snapshot");
            VulkanicPassResourceModel.PassExecutionPlan capturedPlan =
                snapshot.resourceUses().isEmpty()
                    ? this.resourcePlan
                    : resourcePlanWithResources(this.resourcePlan, snapshot.resourceUses());
            PipelineSnapshot capturedPipeline = pipeline;
            FramebufferSnapshot capturedFramebuffer = framebuffer;
            DynamicStateSnapshot capturedDynamicState = dynamicState;
            Optional<VulkanicCompatibilityState.GraphicsSnapshot> sharedState = snapshot.sharedCompatibilityState();
            if (sharedState.isPresent()) {
                VulkanicCompatibilityState.GraphicsSnapshot shared = sharedState.get();
                capturedPipeline = PipelineSnapshot.legacyProgram(shared.programId());
                capturedFramebuffer = FramebufferSnapshot.legacyFramebuffer(shared.drawFramebuffer());
                capturedDynamicState = new DynamicStateSnapshot(
                    shared.fixedFunction().viewport(),
                    shared.fixedFunction().scissor()
                );
            }
            return new GraphicsDrawRequest(
                semanticIdentity,
                capturedPipeline,
                capturedFramebuffer,
                snapshot.vertexInput(),
                snapshot.descriptorBindings(),
                capturedDynamicState,
                snapshot,
                command,
                capturedPlan,
                legacyMetadata
            );
        }
    }

    public record ComputeDispatchCommand(
        boolean indirect,
        int workX,
        int workY,
        int workZ,
        long indirectOffset
    ) {
        public ComputeDispatchCommand {
            if (!indirect && (workX < 0 || workY < 0 || workZ < 0)) {
                throw new IllegalArgumentException("compute workgroup counts must be >= 0");
            }
            if (indirect && indirectOffset < 0L) {
                throw new IllegalArgumentException("indirect dispatch offset must be >= 0");
            }
        }

        public static ComputeDispatchCommand direct(int workX, int workY, int workZ) {
            return new ComputeDispatchCommand(false, workX, workY, workZ, 0L);
        }

        public static ComputeDispatchCommand indirect(long offset) {
            return new ComputeDispatchCommand(true, 0, 0, 0, offset);
        }
    }

    public record ComputeCompatibilitySnapshot(
        Optional<PipelineDescriptor> pipelineDescriptor,
        List<VulkanicPassResourceModel.ResourceUse> resourceUses,
        Optional<PipelineResourcePlanner.Plan> resourceBindingPlan,
        List<VulkanicPassResourceModel.BindingSnapshot> descriptorBindings,
        Optional<VulkanicCompatibilityState.ComputeSnapshot> sharedCompatibilityState,
        String source
    ) {
        public ComputeCompatibilitySnapshot {
            pipelineDescriptor = Objects.requireNonNull(pipelineDescriptor, "pipelineDescriptor");
            resourceUses = List.copyOf(Objects.requireNonNull(resourceUses, "resourceUses"));
            resourceBindingPlan = Objects.requireNonNull(resourceBindingPlan, "resourceBindingPlan");
            descriptorBindings = List.copyOf(Objects.requireNonNull(descriptorBindings, "descriptorBindings"));
            sharedCompatibilityState = Objects.requireNonNull(sharedCompatibilityState, "sharedCompatibilityState");
            source = requireNonBlank(source, "source");
        }

        public static ComputeCompatibilitySnapshot unresolvedLegacy() {
            return new ComputeCompatibilitySnapshot(
                Optional.empty(),
                List.of(),
                Optional.empty(),
                List.of(),
                Optional.empty(),
                "unresolved-legacy-compute-compatibility"
            );
        }

        public ComputeCompatibilitySnapshot withResourceBindingPlan(PipelineResourcePlanner.Plan bindingPlan) {
            Objects.requireNonNull(bindingPlan, "bindingPlan");
            return new ComputeCompatibilitySnapshot(
                Optional.of(bindingPlan.descriptor()),
                resourceUses,
                Optional.of(bindingPlan),
                descriptorBindings,
                sharedCompatibilityState,
                source
            );
        }
    }

    public record ComputeDispatchRequest(
        SemanticIdentity semanticIdentity,
        PipelineSnapshot pipeline,
        List<VulkanicPassResourceModel.BindingSnapshot> descriptors,
        Optional<PipelineResourcePlanner.Plan> resourceBindingPlan,
        ComputeCompatibilitySnapshot compatibilitySnapshot,
        ComputeDispatchCommand command,
        VulkanicPassResourceModel.PassExecutionPlan resourcePlan,
        LegacyCompatibilityMetadata legacyMetadata
    ) {
        public ComputeDispatchRequest {
            semanticIdentity = Objects.requireNonNull(semanticIdentity, "semanticIdentity");
            pipeline = Objects.requireNonNull(pipeline, "pipeline");
            descriptors = List.copyOf(Objects.requireNonNull(descriptors, "descriptors"));
            resourceBindingPlan = Objects.requireNonNull(resourceBindingPlan, "resourceBindingPlan");
            compatibilitySnapshot = Objects.requireNonNull(compatibilitySnapshot, "compatibilitySnapshot");
            command = Objects.requireNonNull(command, "command");
            resourcePlan = Objects.requireNonNull(resourcePlan, "resourcePlan");
            legacyMetadata = Objects.requireNonNull(legacyMetadata, "legacyMetadata");
        }

        public static ComputeDispatchRequest legacyDirect(String operation, int workX, int workY, int workZ) {
            ComputeDispatchCommand command = ComputeDispatchCommand.direct(workX, workY, workZ);
            return legacy(operation, command);
        }

        public static ComputeDispatchRequest legacyIndirect(String operation, long offset) {
            return legacy(operation, ComputeDispatchCommand.indirect(offset));
        }

        private static ComputeDispatchRequest legacy(String operation, ComputeDispatchCommand command) {
            VulkanicPassResourceModel.PassExecutionPlan resourcePlan =
                VulkanicLegacyCompatibilityAdapter.planCompute(new VulkanicLegacyCompatibilityAdapter.ComputeSnapshot(
                    operation,
                    command.indirect() ? "dispatch-indirect" : "dispatch",
                    List.of(),
                    List.of(),
                    command.indirect()
                        ? Optional.of(new VulkanicLegacyCompatibilityAdapter.IndirectBufferSnapshot("legacy-dispatch-indirect-buffer", command.indirectOffset()))
                        : Optional.empty(),
                    false,
                    false
                ));
            return new ComputeDispatchRequest(
                SemanticIdentity.legacy(operation),
                PipelineSnapshot.legacyCurrent(),
                List.of(),
                Optional.empty(),
                ComputeCompatibilitySnapshot.unresolvedLegacy(),
                command,
                resourcePlan,
                LegacyCompatibilityMetadata.operation(operation)
            );
        }

        public ComputeDispatchRequest withResourceBindingPlan(PipelineResourcePlanner.Plan bindingPlan) {
            Objects.requireNonNull(bindingPlan, "bindingPlan");
            ComputeCompatibilitySnapshot capturedSnapshot = compatibilitySnapshot.withResourceBindingPlan(bindingPlan);
            return new ComputeDispatchRequest(
                semanticIdentity,
                pipeline,
                capturedSnapshot.descriptorBindings().isEmpty() ? descriptors : capturedSnapshot.descriptorBindings(),
                Optional.of(bindingPlan),
                capturedSnapshot,
                command,
                resourcePlan,
                legacyMetadata
            );
        }

        public ComputeDispatchRequest withCompatibilitySnapshot(ComputeCompatibilitySnapshot snapshot) {
            Objects.requireNonNull(snapshot, "snapshot");
            VulkanicPassResourceModel.PassExecutionPlan capturedPlan =
                snapshot.resourceUses().isEmpty()
                    ? this.resourcePlan
                    : resourcePlanWithResources(this.resourcePlan, snapshot.resourceUses());
            return new ComputeDispatchRequest(
                semanticIdentity,
                pipeline,
                snapshot.descriptorBindings(),
                snapshot.resourceBindingPlan(),
                snapshot,
                command,
                capturedPlan,
                legacyMetadata
            );
        }
    }

    public enum RenderPassBeginKind {
        DESCRIPTOR,
        TARGET_DESCRIPTOR,
        FRAMEBUFFER
    }

    public record RenderPassBeginRequest(
        SemanticIdentity semanticIdentity,
        RenderPassBeginKind kind,
        String label,
        Optional<VulkanicRenderPassDescriptor> descriptor,
        Optional<VulkanicRenderTargetDescriptor> targetDescriptor,
        OptionalInt framebuffer,
        boolean hasDepthTexture,
        VulkanicPassResourceModel.PassExecutionPlan resourcePlan,
        LegacyCompatibilityMetadata legacyMetadata
    ) {
        public RenderPassBeginRequest {
            semanticIdentity = Objects.requireNonNull(semanticIdentity, "semanticIdentity");
            kind = Objects.requireNonNull(kind, "kind");
            label = requireNonBlank(label, "label");
            descriptor = Objects.requireNonNull(descriptor, "descriptor");
            targetDescriptor = Objects.requireNonNull(targetDescriptor, "targetDescriptor");
            framebuffer = Objects.requireNonNull(framebuffer, "framebuffer");
            resourcePlan = Objects.requireNonNull(resourcePlan, "resourcePlan");
            legacyMetadata = Objects.requireNonNull(legacyMetadata, "legacyMetadata");
            int presentShapes = (descriptor.isPresent() ? 1 : 0)
                + (targetDescriptor.isPresent() ? 1 : 0)
                + (framebuffer.isPresent() ? 1 : 0);
            if (presentShapes != 1) {
                throw new IllegalArgumentException("render-pass begin request must contain exactly one target shape");
            }
            if (kind == RenderPassBeginKind.DESCRIPTOR && descriptor.isEmpty()) {
                throw new IllegalArgumentException("descriptor render-pass begin request requires descriptor");
            }
            if (kind == RenderPassBeginKind.TARGET_DESCRIPTOR && targetDescriptor.isEmpty()) {
                throw new IllegalArgumentException("target descriptor render-pass begin request requires targetDescriptor");
            }
            if (kind == RenderPassBeginKind.FRAMEBUFFER && framebuffer.isEmpty()) {
                throw new IllegalArgumentException("framebuffer render-pass begin request requires framebuffer");
            }
        }

        public static RenderPassBeginRequest descriptor(String operation, VulkanicRenderPassDescriptor descriptor) {
            Objects.requireNonNull(descriptor, "descriptor");
            String label = capturedLabel(descriptor.label(), operation);
            VulkanicRenderPassDescriptor frozen = freezeDescriptor(descriptor, label);
            return new RenderPassBeginRequest(
                SemanticIdentity.legacy(operation),
                RenderPassBeginKind.DESCRIPTOR,
                label,
                Optional.of(frozen),
                Optional.empty(),
                OptionalInt.empty(),
                true,
                renderPassLifecyclePlan("render-pass:" + operation + ":" + label, frozen),
                LegacyCompatibilityMetadata.operation(operation)
            );
        }

        public static RenderPassBeginRequest targetDescriptor(String operation, VulkanicRenderTargetDescriptor descriptor) {
            Objects.requireNonNull(descriptor, "descriptor");
            String label = capturedLabel(descriptor.label(), operation);
            VulkanicRenderTargetDescriptor frozen = freezeTargetDescriptor(descriptor, label);
            return new RenderPassBeginRequest(
                SemanticIdentity.legacy(operation),
                RenderPassBeginKind.TARGET_DESCRIPTOR,
                label,
                Optional.empty(),
                Optional.of(frozen),
                OptionalInt.empty(),
                true,
                renderTargetLifecyclePlan("render-pass:" + operation + ":" + label, frozen),
                LegacyCompatibilityMetadata.operation(operation)
            );
        }

        public static RenderPassBeginRequest framebuffer(
            String operation,
            java.util.function.Supplier<String> label,
            int framebuffer,
            boolean hasDepthTexture
        ) {
            if (framebuffer < 0) {
                throw new IllegalArgumentException("framebuffer must be >= 0");
            }
            String capturedLabel = capturedLabel(label, operation);
            return new RenderPassBeginRequest(
                SemanticIdentity.legacy(operation),
                RenderPassBeginKind.FRAMEBUFFER,
                capturedLabel,
                Optional.empty(),
                Optional.empty(),
                OptionalInt.of(framebuffer),
                hasDepthTexture,
                framebufferLifecyclePlan("render-pass:" + operation + ":" + capturedLabel, framebuffer, hasDepthTexture),
                LegacyCompatibilityMetadata.operation(operation)
            );
        }
    }

    public record RenderPassEndRequest(
        SemanticIdentity semanticIdentity,
        String renderPassIdentity,
        boolean abandoned,
        Optional<String> failureReason,
        VulkanicPassResourceModel.PassExecutionPlan resourcePlan,
        LegacyCompatibilityMetadata legacyMetadata
    ) {
        public RenderPassEndRequest {
            semanticIdentity = Objects.requireNonNull(semanticIdentity, "semanticIdentity");
            renderPassIdentity = requireNonBlank(renderPassIdentity, "renderPassIdentity");
            failureReason = Objects.requireNonNull(failureReason, "failureReason");
            resourcePlan = Objects.requireNonNull(resourcePlan, "resourcePlan");
            legacyMetadata = Objects.requireNonNull(legacyMetadata, "legacyMetadata");
        }

        public static RenderPassEndRequest complete(String source, String renderPassIdentity) {
            return new RenderPassEndRequest(
                SemanticIdentity.legacy(source),
                renderPassIdentity,
                false,
                Optional.empty(),
                passLifecyclePlan(VulkanicPassResourceModel.PassKind.RENDER, "render-pass-end:" + renderPassIdentity),
                LegacyCompatibilityMetadata.operation(source)
            );
        }

        public static RenderPassEndRequest abandoned(String source, String renderPassIdentity, String failureReason) {
            return new RenderPassEndRequest(
                SemanticIdentity.legacy(source),
                renderPassIdentity,
                true,
                Optional.of(requireNonBlank(failureReason, "failureReason")),
                passLifecyclePlan(VulkanicPassResourceModel.PassKind.RENDER, "render-pass-abandon:" + renderPassIdentity),
                LegacyCompatibilityMetadata.operation(source)
            );
        }
    }

    public record ComputePassBeginRequest(
        SemanticIdentity semanticIdentity,
        String label,
        VulkanicPassResourceModel.PassExecutionPlan resourcePlan,
        LegacyCompatibilityMetadata legacyMetadata
    ) {
        public ComputePassBeginRequest {
            semanticIdentity = Objects.requireNonNull(semanticIdentity, "semanticIdentity");
            label = requireNonBlank(label, "label");
            resourcePlan = Objects.requireNonNull(resourcePlan, "resourcePlan");
            legacyMetadata = Objects.requireNonNull(legacyMetadata, "legacyMetadata");
        }

        public static ComputePassBeginRequest legacy(String operation) {
            return new ComputePassBeginRequest(
                SemanticIdentity.legacy(operation),
                operation,
                passLifecyclePlan(VulkanicPassResourceModel.PassKind.COMPUTE, "compute-pass:" + operation),
                LegacyCompatibilityMetadata.operation(operation)
            );
        }
    }

    public record ComputePassEndRequest(
        SemanticIdentity semanticIdentity,
        String label,
        boolean abandoned,
        Optional<String> failureReason,
        VulkanicPassResourceModel.PassExecutionPlan resourcePlan,
        LegacyCompatibilityMetadata legacyMetadata
    ) {
        public ComputePassEndRequest {
            semanticIdentity = Objects.requireNonNull(semanticIdentity, "semanticIdentity");
            label = requireNonBlank(label, "label");
            failureReason = Objects.requireNonNull(failureReason, "failureReason");
            resourcePlan = Objects.requireNonNull(resourcePlan, "resourcePlan");
            legacyMetadata = Objects.requireNonNull(legacyMetadata, "legacyMetadata");
        }

        public static ComputePassEndRequest complete(String operation) {
            return new ComputePassEndRequest(
                SemanticIdentity.legacy(operation),
                operation,
                false,
                Optional.empty(),
                passLifecyclePlan(VulkanicPassResourceModel.PassKind.COMPUTE, "compute-pass-end:" + operation),
                LegacyCompatibilityMetadata.operation(operation)
            );
        }

        public static ComputePassEndRequest abandoned(String operation, String failureReason) {
            return new ComputePassEndRequest(
                SemanticIdentity.legacy(operation),
                operation,
                true,
                Optional.of(requireNonBlank(failureReason, "failureReason")),
                passLifecyclePlan(VulkanicPassResourceModel.PassKind.COMPUTE, "compute-pass-abandon:" + operation),
                LegacyCompatibilityMetadata.operation(operation)
            );
        }
    }

    public record ClearRequest(
        SemanticIdentity semanticIdentity,
        List<VulkanicClearBuffer> buffers,
        FramebufferSnapshot framebuffer,
        VulkanicPassResourceModel.PassExecutionPlan resourcePlan,
        LegacyCompatibilityMetadata legacyMetadata
    ) {
        public ClearRequest {
            semanticIdentity = Objects.requireNonNull(semanticIdentity, "semanticIdentity");
            buffers = List.copyOf(Objects.requireNonNull(buffers, "buffers"));
            if (buffers.isEmpty()) {
                throw new IllegalArgumentException("clear request must declare at least one buffer");
            }
            framebuffer = Objects.requireNonNull(framebuffer, "framebuffer");
            resourcePlan = Objects.requireNonNull(resourcePlan, "resourcePlan");
            legacyMetadata = Objects.requireNonNull(legacyMetadata, "legacyMetadata");
        }

        public static ClearRequest legacy(String operation, VulkanicClearBuffer... buffers) {
            List<VulkanicClearBuffer> bufferList = List.of(Objects.requireNonNull(buffers, "buffers"));
            VulkanicPassResourceModel.PassExecutionPlan plan =
                VulkanicLegacyCompatibilityAdapter.planTransfer(new VulkanicLegacyCompatibilityAdapter.TransferSnapshot(
                    VulkanicPassResourceModel.PassKind.TRANSFER,
                    "clear:" + operation,
                    "clear",
                    "legacy-active-framebuffer",
                    VulkanicPassResourceModel.ResourceKind.TRANSFER_DESTINATION,
                    "legacy-active-framebuffer",
                    VulkanicPassResourceModel.Access.WRITE,
                    VulkanicPassResourceModel.Subresource.color(0, 1, 0, 1),
                    VulkanicResourceUsage.TRANSFER_DST,
                    "clear:" + operation,
                    List.of("clear-visible-before-next-use"),
                    false,
                    false
                ));
            return new ClearRequest(
                SemanticIdentity.legacy(operation),
                bufferList,
                FramebufferSnapshot.active(),
                plan,
                LegacyCompatibilityMetadata.operation(operation)
            );
        }
    }

    public record RenderPassDrawRequest(
        SemanticIdentity semanticIdentity,
        String renderPassIdentity,
        Optional<PipelineSnapshot> pipeline,
        VertexInputSnapshot vertexInput,
        GraphicsDrawCommand command,
        LegacyCompatibilityMetadata legacyMetadata
    ) {
        public RenderPassDrawRequest {
            semanticIdentity = Objects.requireNonNull(semanticIdentity, "semanticIdentity");
            renderPassIdentity = requireNonBlank(renderPassIdentity, "renderPassIdentity");
            pipeline = Objects.requireNonNull(pipeline, "pipeline");
            vertexInput = Objects.requireNonNull(vertexInput, "vertexInput");
            command = Objects.requireNonNull(command, "command");
            legacyMetadata = Objects.requireNonNull(legacyMetadata, "legacyMetadata");
        }

        public static RenderPassDrawRequest indexed(
            String source,
            String renderPassIdentity,
            Optional<PipelineSnapshot> pipeline,
            VertexInputSnapshot vertexInput,
            int firstIndex,
            int indexCount,
            VulkanicIndexType indexType,
            int baseVertex,
            int instanceCount
        ) {
            return new RenderPassDrawRequest(
                SemanticIdentity.legacy(source),
                renderPassIdentity,
                pipeline,
                vertexInput,
                GraphicsDrawCommand.indexed(
                    VulkanicPrimitiveMode.TRIANGLES,
                    indexCount,
                    indexType,
                    (long) firstIndex * indexType.bytesPerIndex(),
                    instanceCount,
                    baseVertex
                ),
                LegacyCompatibilityMetadata.operation(source)
            );
        }

        public static RenderPassDrawRequest arrays(
            String source,
            String renderPassIdentity,
            Optional<PipelineSnapshot> pipeline,
            VertexInputSnapshot vertexInput,
            int firstVertex,
            int vertexCount
        ) {
            return new RenderPassDrawRequest(
                SemanticIdentity.legacy(source),
                renderPassIdentity,
                pipeline,
                vertexInput,
                GraphicsDrawCommand.arrays(VulkanicPrimitiveMode.TRIANGLES, firstVertex, vertexCount, 1),
                LegacyCompatibilityMetadata.operation(source)
            );
        }
    }

    public enum TransferKind {
        COPY_BUFFER_SUB_DATA,
        COPY_NAMED_BUFFER_SUB_DATA,
        COPY_IMAGE_SUB_DATA,
        COPY_TEXTURE_SUB_IMAGE_2D,
        COPY_TEX_IMAGE_2D,
        COPY_TEX_SUB_IMAGE_2D,
        BLIT_FRAMEBUFFER,
        BLIT_NAMED_FRAMEBUFFER,
        READ_PIXELS,
        READ_PIXELS_FLOAT_ARRAY,
        BUFFER_SUB_DATA,
        NAMED_BUFFER_SUB_DATA,
        UPLOAD_TEXTURE_1D,
        UPLOAD_TEXTURE_2D,
        UPLOAD_TEXTURE_2D_SUB_IMAGE_POINTER,
        UPLOAD_TEXTURE_2D_SUB_IMAGE_BUFFER,
        UPLOAD_TEXTURE_3D,
        CLEAR_TEX_IMAGE_INT,
        CLEAR_BUFFER_SUB_DATA_INT,
        CLEAR_BUFFER_FLOAT,
        CLEAR_BUFFER_INT,
        CLEAR_BUFFER_UINT,
        CLEAR_NAMED_FRAMEBUFFER_FLOAT,
        CLEAR_NAMED_FRAMEBUFFER_INT,
        CLEAR_NAMED_FRAMEBUFFER_UINT,
        GENERATE_MIPMAP,
        GENERATE_TEXTURE_MIPMAP
    }

    public record TransferRequest(
        SemanticIdentity semanticIdentity,
        TransferKind kind,
        List<VulkanicPassResourceModel.ResourceUse> resources,
        VulkanicPassResourceModel.PassExecutionPlan resourcePlan,
        LegacyCompatibilityMetadata legacyMetadata,
        int[] intArgs,
        long[] longArgs,
        java.nio.ByteBuffer bytePayload,
        float[] floatPayload,
        int[] intPayload,
        float[] floatArrayOutput
    ) {
        public TransferRequest {
            semanticIdentity = Objects.requireNonNull(semanticIdentity, "semanticIdentity");
            kind = Objects.requireNonNull(kind, "kind");
            resources = List.copyOf(Objects.requireNonNull(resources, "resources"));
            resourcePlan = Objects.requireNonNull(resourcePlan, "resourcePlan");
            legacyMetadata = Objects.requireNonNull(legacyMetadata, "legacyMetadata");
            intArgs = java.util.Arrays.copyOf(Objects.requireNonNull(intArgs, "intArgs"), intArgs.length);
            longArgs = java.util.Arrays.copyOf(Objects.requireNonNull(longArgs, "longArgs"), longArgs.length);
            bytePayload = copyBytePayload(bytePayload);
            floatPayload = floatPayload == null ? null : java.util.Arrays.copyOf(floatPayload, floatPayload.length);
            intPayload = intPayload == null ? null : java.util.Arrays.copyOf(intPayload, intPayload.length);
        }

        @Override
        public int[] intArgs() {
            return java.util.Arrays.copyOf(intArgs, intArgs.length);
        }

        @Override
        public long[] longArgs() {
            return java.util.Arrays.copyOf(longArgs, longArgs.length);
        }

        @Override
        public java.nio.ByteBuffer bytePayload() {
            return bytePayload == null ? null : bytePayload.asReadOnlyBuffer();
        }

        @Override
        public float[] floatPayload() {
            return floatPayload == null ? null : java.util.Arrays.copyOf(floatPayload, floatPayload.length);
        }

        @Override
        public int[] intPayload() {
            return intPayload == null ? null : java.util.Arrays.copyOf(intPayload, intPayload.length);
        }

        public static TransferRequest legacy(
            String operation,
            TransferKind kind,
            VulkanicPassResourceModel.ResourceKind resourceKind,
            String stableKey,
            VulkanicPassResourceModel.Access access,
            VulkanicResourceUsage usage,
            int[] intArgs,
            long[] longArgs
        ) {
            VulkanicPassResourceModel.ResourceUse use = VulkanicLegacyCompatibilityAdapter.resourceUse(
                operation,
                resourceKind,
                stableKey,
                access,
                VulkanicPassResourceModel.Subresource.bufferRange(0, 1),
                usage,
                operation,
                false,
                0
            );
            VulkanicPassResourceModel.PassExecutionPlan plan =
                VulkanicLegacyCompatibilityAdapter.planTransfer(new VulkanicLegacyCompatibilityAdapter.TransferSnapshot(
                    kind == TransferKind.READ_PIXELS
                        ? VulkanicPassResourceModel.PassKind.READBACK
                        : VulkanicPassResourceModel.PassKind.TRANSFER,
                    operation,
                    kind.name().toLowerCase(java.util.Locale.ROOT),
                    operation,
                    resourceKind,
                    stableKey,
                    access,
                    VulkanicPassResourceModel.Subresource.bufferRange(0, 1),
                    usage,
                    operation,
                    List.of(),
                    false,
                    false
                ));
            return new TransferRequest(
                SemanticIdentity.legacy(operation),
                kind,
                List.of(use),
                plan,
                LegacyCompatibilityMetadata.operation(operation),
                intArgs,
                longArgs,
                null,
                null,
                null,
                null
            );
        }

        public static TransferRequest legacyWithBytePayload(
            String operation,
            TransferKind kind,
            VulkanicPassResourceModel.ResourceKind resourceKind,
            String stableKey,
            VulkanicPassResourceModel.Access access,
            VulkanicResourceUsage usage,
            int[] intArgs,
            long[] longArgs,
            java.nio.ByteBuffer bytePayload
        ) {
            TransferRequest request = legacy(operation, kind, resourceKind, stableKey, access, usage, intArgs, longArgs);
            return new TransferRequest(
                request.semanticIdentity(),
                request.kind(),
                request.resources(),
                request.resourcePlan(),
                request.legacyMetadata(),
                request.intArgs(),
                request.longArgs(),
                bytePayload,
                null,
                null,
                null
            );
        }

        public static TransferRequest legacyWithFloatPayload(
            String operation,
            TransferKind kind,
            VulkanicPassResourceModel.ResourceKind resourceKind,
            String stableKey,
            VulkanicPassResourceModel.Access access,
            VulkanicResourceUsage usage,
            int[] intArgs,
            long[] longArgs,
            float[] floatPayload
        ) {
            TransferRequest request = legacy(operation, kind, resourceKind, stableKey, access, usage, intArgs, longArgs);
            return new TransferRequest(
                request.semanticIdentity(),
                request.kind(),
                request.resources(),
                request.resourcePlan(),
                request.legacyMetadata(),
                request.intArgs(),
                request.longArgs(),
                null,
                floatPayload,
                null,
                null
            );
        }

        public static TransferRequest legacyWithIntPayload(
            String operation,
            TransferKind kind,
            VulkanicPassResourceModel.ResourceKind resourceKind,
            String stableKey,
            VulkanicPassResourceModel.Access access,
            VulkanicResourceUsage usage,
            int[] intArgs,
            long[] longArgs,
            int[] intPayload
        ) {
            TransferRequest request = legacy(operation, kind, resourceKind, stableKey, access, usage, intArgs, longArgs);
            return new TransferRequest(
                request.semanticIdentity(),
                request.kind(),
                request.resources(),
                request.resourcePlan(),
                request.legacyMetadata(),
                request.intArgs(),
                request.longArgs(),
                null,
                null,
                intPayload,
                null
            );
        }

        public static TransferRequest legacyWithFloatArrayOutput(
            String operation,
            TransferKind kind,
            VulkanicPassResourceModel.ResourceKind resourceKind,
            String stableKey,
            VulkanicPassResourceModel.Access access,
            VulkanicResourceUsage usage,
            int[] intArgs,
            long[] longArgs,
            float[] floatArrayOutput
        ) {
            TransferRequest request = legacy(operation, kind, resourceKind, stableKey, access, usage, intArgs, longArgs);
            return new TransferRequest(
                request.semanticIdentity(),
                request.kind(),
                request.resources(),
                request.resourcePlan(),
                request.legacyMetadata(),
                request.intArgs(),
                request.longArgs(),
                null,
                null,
                null,
                floatArrayOutput
            );
        }
    }

    private static java.nio.ByteBuffer copyBytePayload(java.nio.ByteBuffer payload) {
        if (payload == null) {
            return null;
        }
        java.nio.ByteBuffer source = payload.duplicate();
        java.nio.ByteBuffer copy = java.nio.ByteBuffer.allocateDirect(source.remaining()).order(source.order());
        copy.put(source);
        copy.flip();
        return copy;
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static String capturedLabel(java.util.function.Supplier<String> label, String fallback) {
        if (label == null) {
            return requireNonBlank(fallback, "fallback");
        }
        String supplied = label.get();
        if (supplied == null || supplied.isBlank()) {
            return requireNonBlank(fallback, "fallback");
        }
        return supplied;
    }

    private static VulkanicRenderPassDescriptor freezeDescriptor(VulkanicRenderPassDescriptor descriptor, String label) {
        VulkanicRenderPassDescriptor.ColorAttachment color = descriptor.colorAttachment();
        VulkanicRenderPassDescriptor.DepthAttachment depth = descriptor.depthAttachment();
        VulkanicRenderPassDescriptor.DepthAttachment frozenDepth = null;
        if (depth != null) {
            frozenDepth = new VulkanicRenderPassDescriptor.DepthAttachment(
                depth.target(),
                depth.loadOp(),
                depth.storeOp(),
                depth.clearDepth(),
                depth.initialUsage(),
                depth.passUsage(),
                depth.finalUsage()
            );
        }
        return new VulkanicRenderPassDescriptor(
            () -> label,
            new VulkanicRenderPassDescriptor.ColorAttachment(
                color.target(),
                color.loadOp(),
                color.storeOp(),
                color.clearColor(),
                color.initialUsage(),
                color.passUsage(),
                color.finalUsage()
            ),
            frozenDepth
        );
    }

    private static VulkanicRenderTargetDescriptor freezeTargetDescriptor(VulkanicRenderTargetDescriptor descriptor, String label) {
        return new VulkanicRenderTargetDescriptor(
            () -> label,
            descriptor.colorAttachments().stream()
                .map(color -> new VulkanicRenderTargetDescriptor.ColorAttachment(
                    color.textureId(),
                    color.loadOp(),
                    color.storeOp(),
                    color.clearColor(),
                    color.initialUsage(),
                    color.passUsage(),
                    color.finalUsage()
                ))
                .toList(),
            descriptor.depthAttachment() == null ? null : new VulkanicRenderTargetDescriptor.DepthAttachment(
                descriptor.depthAttachment().textureId(),
                descriptor.depthAttachment().loadOp(),
                descriptor.depthAttachment().storeOp(),
                descriptor.depthAttachment().clearDepth(),
                descriptor.depthAttachment().initialUsage(),
                descriptor.depthAttachment().passUsage(),
                descriptor.depthAttachment().finalUsage()
            ),
            descriptor.width(),
            descriptor.height()
        );
    }

    private static VulkanicPassResourceModel.PassExecutionPlan passLifecyclePlan(
        VulkanicPassResourceModel.PassKind kind,
        String label
    ) {
        VulkanicPassResourceModel.PassRequest request = new VulkanicPassResourceModel.PassRequest(
            kind,
            label,
            List.of(),
            List.of(),
            List.of(),
            List.of(new VulkanicPassResourceModel.Command(label, OptionalInt.empty(), OptionalInt.empty())),
            List.of(),
            false,
            false
        );
        return VulkanicPassResourcePlanner.plan(request);
    }

    private static VulkanicPassResourceModel.PassExecutionPlan renderPassLifecyclePlan(
        String label,
        VulkanicRenderPassDescriptor descriptor
    ) {
        VulkanicRenderPassDescriptor.ColorAttachment color = descriptor.colorAttachment();
        List<VulkanicPassResourceModel.AttachmentUse> attachments;
        if (descriptor.depthAttachment() == null) {
            attachments = List.of(colorAttachmentUse(0, color, textureViewStableKey("color[0]", color.target())));
        } else {
            VulkanicRenderPassDescriptor.DepthAttachment depth = descriptor.depthAttachment();
            attachments = List.of(
                colorAttachmentUse(0, color, textureViewStableKey("color[0]", color.target())),
                depthAttachmentUse(0, depth, textureViewStableKey("depth", depth.target()))
            );
        }
        return renderLifecyclePlan(label, attachments);
    }

    private static VulkanicPassResourceModel.PassExecutionPlan renderTargetLifecyclePlan(
        String label,
        VulkanicRenderTargetDescriptor descriptor
    ) {
        List<VulkanicPassResourceModel.AttachmentUse> attachments = new java.util.ArrayList<>();
        for (int colorIndex = 0; colorIndex < descriptor.colorAttachments().size(); colorIndex++) {
            VulkanicRenderTargetDescriptor.ColorAttachment color = descriptor.colorAttachments().get(colorIndex);
            attachments.add(new VulkanicPassResourceModel.AttachmentUse(
                colorIndex,
                VulkanicPassResourceModel.ResourceIdentity.of(
                    "color[" + colorIndex + "]",
                    VulkanicPassResourceModel.ResourceKind.COLOR_ATTACHMENT,
                    "legacy-texture:" + color.textureId()
                ),
                VulkanicPassResourceModel.Subresource.color(0, 1, 0, 1),
                color.loadOp(),
                color.storeOp(),
                color.clearColor(),
                OptionalDouble.empty(),
                color.initialUsage(),
                color.passUsage(),
                color.finalUsage(),
                color.passUsage() == VulkanicResourceUsage.ATTACHMENT_FEEDBACK_LOOP
            ));
        }
        if (descriptor.depthAttachment() != null) {
            VulkanicRenderTargetDescriptor.DepthAttachment depth = descriptor.depthAttachment();
            attachments.add(new VulkanicPassResourceModel.AttachmentUse(
                Math.max(0, descriptor.colorAttachments().size()),
                VulkanicPassResourceModel.ResourceIdentity.of(
                    "depth",
                    VulkanicPassResourceModel.ResourceKind.DEPTH_ATTACHMENT,
                    "legacy-texture:" + depth.textureId()
                ),
                VulkanicPassResourceModel.Subresource.depth(0, 1, 0, 1),
                depth.loadOp(),
                depth.storeOp(),
                OptionalInt.empty(),
                depth.clearDepth(),
                depth.initialUsage(),
                depth.passUsage(),
                depth.finalUsage(),
                depth.passUsage() == VulkanicResourceUsage.ATTACHMENT_FEEDBACK_LOOP
            ));
        }
        return renderLifecyclePlan(label, attachments);
    }

    private static VulkanicPassResourceModel.PassExecutionPlan framebufferLifecyclePlan(
        String label,
        int framebuffer,
        boolean hasDepthTexture
    ) {
        List<VulkanicPassResourceModel.ResourceUse> uses = new java.util.ArrayList<>();
        uses.add(VulkanicPassResourceModel.ResourceUse.of(
            VulkanicPassResourceModel.ResourceIdentity.of(
                "framebuffer-color",
                VulkanicPassResourceModel.ResourceKind.COLOR_ATTACHMENT,
                "legacy-framebuffer:" + framebuffer + ":color"
            ),
            VulkanicPassResourceModel.Access.WRITE,
            VulkanicPassResourceModel.Subresource.color(0, 1, 0, 1),
            VulkanicResourceUsage.INFERRED,
            "framebuffer-color",
            false,
            0
        ));
        if (hasDepthTexture) {
            uses.add(VulkanicPassResourceModel.ResourceUse.of(
                VulkanicPassResourceModel.ResourceIdentity.of(
                    "framebuffer-depth",
                    VulkanicPassResourceModel.ResourceKind.DEPTH_ATTACHMENT,
                    "legacy-framebuffer:" + framebuffer + ":depth"
                ),
                VulkanicPassResourceModel.Access.WRITE,
                VulkanicPassResourceModel.Subresource.depth(0, 1, 0, 1),
                VulkanicResourceUsage.INFERRED,
                "framebuffer-depth",
                false,
                1
            ));
        }
        VulkanicPassResourceModel.PassRequest request = new VulkanicPassResourceModel.PassRequest(
            VulkanicPassResourceModel.PassKind.RENDER,
            label,
            List.of(),
            uses,
            List.of(),
            List.of(new VulkanicPassResourceModel.Command(label, OptionalInt.empty(), OptionalInt.empty())),
            List.of(),
            false,
            false
        );
        return VulkanicPassResourcePlanner.plan(request);
    }

    private static VulkanicPassResourceModel.PassExecutionPlan renderLifecyclePlan(
        String label,
        List<VulkanicPassResourceModel.AttachmentUse> attachments
    ) {
        VulkanicPassResourceModel.PassRequest request = new VulkanicPassResourceModel.PassRequest(
            VulkanicPassResourceModel.PassKind.RENDER,
            label,
            attachments,
            List.of(),
            List.of(),
            List.of(new VulkanicPassResourceModel.Command(label, OptionalInt.empty(), OptionalInt.empty())),
            List.of(),
            false,
            false
        );
        return VulkanicPassResourcePlanner.plan(request);
    }

    private static VulkanicPassResourceModel.PassExecutionPlan resourcePlanWithResources(
        VulkanicPassResourceModel.PassExecutionPlan basePlan,
        List<VulkanicPassResourceModel.ResourceUse> resources
    ) {
        VulkanicPassResourceModel.PassRequest base = basePlan.request();
        VulkanicPassResourceModel.PassRequest request = new VulkanicPassResourceModel.PassRequest(
            base.kind(),
            base.label(),
            base.attachments(),
            resources,
            base.bindings(),
            base.commands(),
            base.requiredOrdering(),
            base.abandoned(),
            base.deviceLost()
        );
        return VulkanicPassResourcePlanner.plan(request);
    }

    private static VulkanicPassResourceModel.AttachmentUse colorAttachmentUse(
        int attachmentIndex,
        VulkanicRenderPassDescriptor.ColorAttachment color,
        String stableKey
    ) {
        return new VulkanicPassResourceModel.AttachmentUse(
            attachmentIndex,
            VulkanicPassResourceModel.ResourceIdentity.of(
                "color[" + attachmentIndex + "]",
                VulkanicPassResourceModel.ResourceKind.COLOR_ATTACHMENT,
                stableKey
            ),
            VulkanicPassResourceModel.Subresource.color(color.target().getBaseMipLevel(), color.target().getMipLevelCount(), 0, 1),
            color.loadOp(),
            color.storeOp(),
            color.clearColor(),
            OptionalDouble.empty(),
            color.initialUsage(),
            color.passUsage(),
            color.finalUsage(),
            color.passUsage() == VulkanicResourceUsage.ATTACHMENT_FEEDBACK_LOOP
        );
    }

    private static VulkanicPassResourceModel.AttachmentUse depthAttachmentUse(
        int attachmentIndex,
        VulkanicRenderPassDescriptor.DepthAttachment depth,
        String stableKey
    ) {
        return new VulkanicPassResourceModel.AttachmentUse(
            attachmentIndex,
            VulkanicPassResourceModel.ResourceIdentity.of(
                "depth",
                VulkanicPassResourceModel.ResourceKind.DEPTH_ATTACHMENT,
                stableKey
            ),
            VulkanicPassResourceModel.Subresource.depth(depth.target().getBaseMipLevel(), depth.target().getMipLevelCount(), 0, 1),
            depth.loadOp(),
            depth.storeOp(),
            OptionalInt.empty(),
            depth.clearDepth(),
            depth.initialUsage(),
            depth.passUsage(),
            depth.finalUsage(),
            depth.passUsage() == VulkanicResourceUsage.ATTACHMENT_FEEDBACK_LOOP
        );
    }

    private static String textureViewStableKey(String role, VulkanicTextureView view) {
        return role
            + ":viewClass=" + view.getClass().getName()
            + ":baseMip=" + view.getBaseMipLevel()
            + ":levels=" + view.getMipLevelCount()
            + ":extent=" + view.getWidth(0) + "x" + view.getHeight(0);
    }
}
