package net.vulkanic;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
    }

    public record FramebufferSnapshot(String stableKey, String logicalTarget) {
        public FramebufferSnapshot {
            stableKey = requireNonBlank(stableKey, "stableKey");
            logicalTarget = requireNonBlank(logicalTarget, "logicalTarget");
        }

        public static FramebufferSnapshot active() {
            return new FramebufferSnapshot("legacy-active-framebuffer", "active-target");
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
                    : new VulkanicPassResourceModel.PassExecutionPlan(
                        this.resourcePlan.request(),
                        snapshot.resourceUses(),
                        this.resourcePlan.finalResourceUsages()
                    );
            return new GraphicsDrawRequest(
                semanticIdentity,
                pipeline,
                framebuffer,
                snapshot.vertexInput(),
                snapshot.descriptorBindings(),
                dynamicState,
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
                    : new VulkanicPassResourceModel.PassExecutionPlan(
                        this.resourcePlan.request(),
                        snapshot.resourceUses(),
                        this.resourcePlan.finalResourceUsages()
                    );
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

    public enum TransferKind {
        COPY_BUFFER_SUB_DATA,
        COPY_NAMED_BUFFER_SUB_DATA,
        COPY_IMAGE_SUB_DATA,
        COPY_TEXTURE_SUB_IMAGE_2D,
        COPY_TEX_IMAGE_2D,
        COPY_TEX_SUB_IMAGE_2D,
        BLIT_FRAMEBUFFER,
        BLIT_NAMED_FRAMEBUFFER,
        READ_PIXELS
    }

    public record TransferRequest(
        SemanticIdentity semanticIdentity,
        TransferKind kind,
        List<VulkanicPassResourceModel.ResourceUse> resources,
        VulkanicPassResourceModel.PassExecutionPlan resourcePlan,
        LegacyCompatibilityMetadata legacyMetadata,
        int[] intArgs,
        long[] longArgs
    ) {
        public TransferRequest {
            semanticIdentity = Objects.requireNonNull(semanticIdentity, "semanticIdentity");
            kind = Objects.requireNonNull(kind, "kind");
            resources = List.copyOf(Objects.requireNonNull(resources, "resources"));
            resourcePlan = Objects.requireNonNull(resourcePlan, "resourcePlan");
            legacyMetadata = Objects.requireNonNull(legacyMetadata, "legacyMetadata");
            intArgs = java.util.Arrays.copyOf(Objects.requireNonNull(intArgs, "intArgs"), intArgs.length);
            longArgs = java.util.Arrays.copyOf(Objects.requireNonNull(longArgs, "longArgs"), longArgs.length);
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
                longArgs
            );
        }
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
