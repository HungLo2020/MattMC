package net.vulkanic;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
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
     * native object handles. Compatibility provenance belongs in diagnostics; it is
     * not part of executable request semantics, equality, caching, or resource
     * resolution.</p>
 */
public final class VulkanicGalExecutionRequest {
    private VulkanicGalExecutionRequest() {
    }

    public static final int CONTRACT_MAJOR_VERSION = 1;
    public static final int CONTRACT_MINOR_VERSION = 0;
    public static final int CONTRACT_PATCH_VERSION = 0;
    public static final String CONTRACT_VERSION = CONTRACT_MAJOR_VERSION + "." + CONTRACT_MINOR_VERSION + "." + CONTRACT_PATCH_VERSION;
    private static final String CONTRACT_SCHEMA = buildContractSchema();
    public static final String CONTRACT_SCHEMA_FINGERPRINT = sha256Hex(CONTRACT_SCHEMA);

    public static String contractSchema() {
        return CONTRACT_SCHEMA;
    }

    public static String contractSchemaFingerprint() {
        return CONTRACT_SCHEMA_FINGERPRINT;
    }

    public enum ExecutionStatus {
        SUCCESS,
        REJECTED,
        ABANDONED,
        STALE_RESOURCE,
        DEVICE_LOST,
        BACKEND_FAILURE
    }

    public sealed interface ExecutionResult permits ExecutionSuccess, ExecutionFailure {
        ExecutionStatus status();
        String requestIdentity();
        String detail();

        default boolean successful() {
            return status() == ExecutionStatus.SUCCESS;
        }
    }

    public record ExecutionSuccess(String requestIdentity, String detail) implements ExecutionResult {
        public ExecutionSuccess {
            requestIdentity = requireNonBlank(requestIdentity, "requestIdentity");
            detail = requireNonBlank(detail, "detail");
        }

        @Override
        public ExecutionStatus status() {
            return ExecutionStatus.SUCCESS;
        }
    }

    public record ExecutionFailure(
        ExecutionStatus status,
        String requestIdentity,
        String detail
    ) implements ExecutionResult {
        public ExecutionFailure {
            status = Objects.requireNonNull(status, "status");
            if (status == ExecutionStatus.SUCCESS) {
                throw new IllegalArgumentException("ExecutionFailure cannot use SUCCESS status");
            }
            requestIdentity = requireNonBlank(requestIdentity, "requestIdentity");
            detail = requireNonBlank(detail, "detail");
        }
    }

    public static ExecutionResult validateGraphicsDraw(GraphicsDrawRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.compatibilitySnapshot().sharedCompatibilityState().isEmpty()) {
            return rejected(request.semanticIdentity(), "graphics request has no captured shared compatibility state");
        }
        if (request.compatibilitySnapshot().source().equals("frontend-compatibility-draft")) {
            return rejected(request.semanticIdentity(), "graphics request still carries draft compatibility provenance");
        }
        if (request.pipeline().stableKey().equals("compatibility-draft-pipeline")) {
            return rejected(request.semanticIdentity(), "graphics request still carries draft pipeline state");
        }
        if (request.framebuffer().stableKey().equals("compatibility-draft-framebuffer")) {
            return rejected(request.semanticIdentity(), "graphics request still carries draft framebuffer state");
        }
        return success(request.semanticIdentity());
    }

    public static ExecutionResult validateComputeDispatch(ComputeDispatchRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.compatibilitySnapshot().sharedCompatibilityState().isEmpty()) {
            return rejected(request.semanticIdentity(), "compute request has no captured shared compatibility state");
        }
        if (request.compatibilitySnapshot().source().equals("frontend-compute-compatibility-draft")) {
            return rejected(request.semanticIdentity(), "compute request still carries draft compatibility provenance");
        }
        if (request.pipeline().stableKey().equals("compatibility-draft-pipeline")) {
            return rejected(request.semanticIdentity(), "compute request still carries draft pipeline state");
        }
        return success(request.semanticIdentity());
    }

    public static ExecutionResult validateClear(ClearRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.framebuffer().stableKey().equals("compatibility-draft-framebuffer")) {
            return rejected(request.semanticIdentity(), "clear request still carries draft framebuffer state");
        }
        return success(request.semanticIdentity());
    }

    public static ExecutionResult validateTransfer(TransferRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.transferSnapshot().isEmpty()) {
            return rejected(request.semanticIdentity(), "transfer request has no canonical transfer snapshot");
        }
        return success(request.semanticIdentity());
    }

    public static ExecutionResult validateRenderPassBegin(RenderPassBeginRequest request) {
        Objects.requireNonNull(request, "request");
        return success(request.semanticIdentity());
    }

    public static ExecutionResult validateRenderPassEnd(RenderPassEndRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.abandoned()) {
            return new ExecutionFailure(
                ExecutionStatus.ABANDONED,
                request.semanticIdentity().label(),
                request.failureReason().orElse("render pass abandoned")
            );
        }
        return success(request.semanticIdentity());
    }

    public static ExecutionResult validateComputePassBegin(ComputePassBeginRequest request) {
        Objects.requireNonNull(request, "request");
        return success(request.semanticIdentity());
    }

    public static ExecutionResult validateComputePassEnd(ComputePassEndRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.abandoned()) {
            return new ExecutionFailure(
                ExecutionStatus.ABANDONED,
                request.semanticIdentity().label(),
                request.failureReason().orElse("compute pass abandoned")
            );
        }
        return success(request.semanticIdentity());
    }

    public static ExecutionResult staleResource(SemanticIdentity identity, String detail) {
        return new ExecutionFailure(ExecutionStatus.STALE_RESOURCE, identity.label(), detail);
    }

    public static ExecutionResult deviceLost(SemanticIdentity identity, String detail) {
        return new ExecutionFailure(ExecutionStatus.DEVICE_LOST, identity.label(), detail);
    }

    public static ExecutionResult backendFailure(SemanticIdentity identity, String detail) {
        return new ExecutionFailure(ExecutionStatus.BACKEND_FAILURE, identity.label(), safeDetail(detail, "backend failure"));
    }

    public static ExecutionResult success(SemanticIdentity identity) {
        return new ExecutionSuccess(identity.label(), "validated");
    }

    private static ExecutionResult rejected(SemanticIdentity identity, String detail) {
        return new ExecutionFailure(ExecutionStatus.REJECTED, identity.label(), detail);
    }

    private static String safeDetail(String detail, String fallback) {
        return detail == null || detail.isBlank() ? fallback : detail;
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

    public record PipelineSnapshot(String stableKey, String programIdentity) {
        public PipelineSnapshot {
            stableKey = requireNonBlank(stableKey, "stableKey");
            programIdentity = requireNonBlank(programIdentity, "programIdentity");
        }

        public static PipelineSnapshot compatibilityDraft() {
            return new PipelineSnapshot("compatibility-draft-pipeline", "compatibility-draft-program");
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

        public static FramebufferSnapshot compatibilityDraft() {
            return new FramebufferSnapshot("compatibility-draft-framebuffer", "active-target");
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

        public static DynamicStateSnapshot empty() {
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

        public static VertexInputSnapshot empty() {
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

        public static GraphicsCompatibilitySnapshot compatibilityDraft() {
            return new GraphicsCompatibilitySnapshot(
                Optional.empty(),
                VertexInputSnapshot.empty(),
                List.of(),
                Optional.empty(),
                List.of(),
                Optional.empty(),
                "frontend-compatibility-draft"
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
        VulkanicPassResourceModel.PassExecutionPlan resourcePlan
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
        }

        public static GraphicsDrawRequest arrays(
            String operation,
            VulkanicPrimitiveMode mode,
            int firstVertex,
            int vertexCount,
            int instanceCount
        ) {
            GraphicsDrawCommand command = GraphicsDrawCommand.arrays(mode, firstVertex, vertexCount, instanceCount);
            return draw(operation, command);
        }

        public static GraphicsDrawRequest indexed(
            String operation,
            VulkanicPrimitiveMode mode,
            int indexCount,
            VulkanicIndexType indexType,
            long indexByteOffset,
            int instanceCount,
            int baseVertex
        ) {
            GraphicsDrawCommand command = GraphicsDrawCommand.indexed(mode, indexCount, indexType, indexByteOffset, instanceCount, baseVertex);
            return draw(operation, command);
        }

        public static GraphicsDrawRequest multiIndexedBaseVertex(
            String operation,
            VulkanicPrimitiveMode mode,
            VulkanicIndexType indexType,
            List<IndexedDraw> draws
        ) {
            GraphicsDrawCommand command = GraphicsDrawCommand.multiIndexedBaseVertex(mode, indexType, draws);
            return draw(operation, command);
        }

        private static GraphicsDrawRequest draw(
            String operation,
            GraphicsDrawCommand command
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
                PipelineSnapshot.compatibilityDraft(),
                FramebufferSnapshot.compatibilityDraft(),
                VertexInputSnapshot.empty(),
                List.of(),
                DynamicStateSnapshot.empty(),
                GraphicsCompatibilitySnapshot.compatibilityDraft(),
                command,
                resourcePlan
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
                capturedPlan
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

        public static ComputeCompatibilitySnapshot compatibilityDraft() {
            return new ComputeCompatibilitySnapshot(
                Optional.empty(),
                List.of(),
                Optional.empty(),
                List.of(),
                Optional.empty(),
                "frontend-compute-compatibility-draft"
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
        VulkanicPassResourceModel.PassExecutionPlan resourcePlan
    ) {
        public ComputeDispatchRequest {
            semanticIdentity = Objects.requireNonNull(semanticIdentity, "semanticIdentity");
            pipeline = Objects.requireNonNull(pipeline, "pipeline");
            descriptors = List.copyOf(Objects.requireNonNull(descriptors, "descriptors"));
            resourceBindingPlan = Objects.requireNonNull(resourceBindingPlan, "resourceBindingPlan");
            compatibilitySnapshot = Objects.requireNonNull(compatibilitySnapshot, "compatibilitySnapshot");
            command = Objects.requireNonNull(command, "command");
            resourcePlan = Objects.requireNonNull(resourcePlan, "resourcePlan");
        }

        public static ComputeDispatchRequest direct(String operation, int workX, int workY, int workZ) {
            ComputeDispatchCommand command = ComputeDispatchCommand.direct(workX, workY, workZ);
            return dispatch(operation, command);
        }

        public static ComputeDispatchRequest indirect(String operation, long offset) {
            return dispatch(operation, ComputeDispatchCommand.indirect(offset));
        }

        private static ComputeDispatchRequest dispatch(String operation, ComputeDispatchCommand command) {
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
                PipelineSnapshot.compatibilityDraft(),
                List.of(),
                Optional.empty(),
                ComputeCompatibilitySnapshot.compatibilityDraft(),
                command,
                resourcePlan
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
                resourcePlan
            );
        }

        public ComputeDispatchRequest withCompatibilitySnapshot(ComputeCompatibilitySnapshot snapshot) {
            Objects.requireNonNull(snapshot, "snapshot");
            VulkanicPassResourceModel.PassExecutionPlan capturedPlan =
                snapshot.resourceUses().isEmpty()
                    ? this.resourcePlan
                    : resourcePlanWithResources(this.resourcePlan, snapshot.resourceUses());
            PipelineSnapshot capturedPipeline = pipeline;
            Optional<VulkanicCompatibilityState.ComputeSnapshot> sharedState = snapshot.sharedCompatibilityState();
            if (sharedState.isPresent()) {
                capturedPipeline = PipelineSnapshot.legacyProgram(sharedState.get().programId());
            }
            return new ComputeDispatchRequest(
                semanticIdentity,
                capturedPipeline,
                snapshot.descriptorBindings(),
                snapshot.resourceBindingPlan(),
                snapshot,
                command,
                capturedPlan
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
        VulkanicPassResourceModel.PassExecutionPlan resourcePlan
    ) {
        public RenderPassBeginRequest {
            semanticIdentity = Objects.requireNonNull(semanticIdentity, "semanticIdentity");
            kind = Objects.requireNonNull(kind, "kind");
            label = requireNonBlank(label, "label");
            descriptor = Objects.requireNonNull(descriptor, "descriptor");
            targetDescriptor = Objects.requireNonNull(targetDescriptor, "targetDescriptor");
            framebuffer = Objects.requireNonNull(framebuffer, "framebuffer");
            resourcePlan = Objects.requireNonNull(resourcePlan, "resourcePlan");
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
                renderPassLifecyclePlan("render-pass:" + operation + ":" + label, frozen)
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
                renderTargetLifecyclePlan("render-pass:" + operation + ":" + label, frozen)
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
                framebufferLifecyclePlan("render-pass:" + operation + ":" + capturedLabel, framebuffer, hasDepthTexture)
            );
        }
    }

    public record RenderPassEndRequest(
        SemanticIdentity semanticIdentity,
        String renderPassIdentity,
        boolean abandoned,
        Optional<String> failureReason,
        VulkanicPassResourceModel.PassExecutionPlan resourcePlan
    ) {
        public RenderPassEndRequest {
            semanticIdentity = Objects.requireNonNull(semanticIdentity, "semanticIdentity");
            renderPassIdentity = requireNonBlank(renderPassIdentity, "renderPassIdentity");
            failureReason = Objects.requireNonNull(failureReason, "failureReason");
            resourcePlan = Objects.requireNonNull(resourcePlan, "resourcePlan");
        }

        public static RenderPassEndRequest complete(String source, String renderPassIdentity) {
            return new RenderPassEndRequest(
                SemanticIdentity.legacy(source),
                renderPassIdentity,
                false,
                Optional.empty(),
                passLifecyclePlan(VulkanicPassResourceModel.PassKind.RENDER, "render-pass-end:" + renderPassIdentity)
            );
        }

        public static RenderPassEndRequest abandoned(String source, String renderPassIdentity, String failureReason) {
            return new RenderPassEndRequest(
                SemanticIdentity.legacy(source),
                renderPassIdentity,
                true,
                Optional.of(requireNonBlank(failureReason, "failureReason")),
                passLifecyclePlan(VulkanicPassResourceModel.PassKind.RENDER, "render-pass-abandon:" + renderPassIdentity)
            );
        }
    }

    public record ComputePassBeginRequest(
        SemanticIdentity semanticIdentity,
        String label,
        VulkanicPassResourceModel.PassExecutionPlan resourcePlan
    ) {
        public ComputePassBeginRequest {
            semanticIdentity = Objects.requireNonNull(semanticIdentity, "semanticIdentity");
            label = requireNonBlank(label, "label");
            resourcePlan = Objects.requireNonNull(resourcePlan, "resourcePlan");
        }

        public static ComputePassBeginRequest begin(String operation) {
            return new ComputePassBeginRequest(
                SemanticIdentity.legacy(operation),
                operation,
                passLifecyclePlan(VulkanicPassResourceModel.PassKind.COMPUTE, "compute-pass:" + operation)
            );
        }
    }

    public record ComputePassEndRequest(
        SemanticIdentity semanticIdentity,
        String label,
        boolean abandoned,
        Optional<String> failureReason,
        VulkanicPassResourceModel.PassExecutionPlan resourcePlan
    ) {
        public ComputePassEndRequest {
            semanticIdentity = Objects.requireNonNull(semanticIdentity, "semanticIdentity");
            label = requireNonBlank(label, "label");
            failureReason = Objects.requireNonNull(failureReason, "failureReason");
            resourcePlan = Objects.requireNonNull(resourcePlan, "resourcePlan");
        }

        public static ComputePassEndRequest complete(String operation) {
            return new ComputePassEndRequest(
                SemanticIdentity.legacy(operation),
                operation,
                false,
                Optional.empty(),
                passLifecyclePlan(VulkanicPassResourceModel.PassKind.COMPUTE, "compute-pass-end:" + operation)
            );
        }

        public static ComputePassEndRequest abandoned(String operation, String failureReason) {
            return new ComputePassEndRequest(
                SemanticIdentity.legacy(operation),
                operation,
                true,
                Optional.of(requireNonBlank(failureReason, "failureReason")),
                passLifecyclePlan(VulkanicPassResourceModel.PassKind.COMPUTE, "compute-pass-abandon:" + operation)
            );
        }
    }

    public record ClearRequest(
        SemanticIdentity semanticIdentity,
        List<VulkanicClearBuffer> buffers,
        FramebufferSnapshot framebuffer,
        VulkanicPassResourceModel.PassExecutionPlan resourcePlan
    ) {
        public ClearRequest {
            semanticIdentity = Objects.requireNonNull(semanticIdentity, "semanticIdentity");
            buffers = List.copyOf(Objects.requireNonNull(buffers, "buffers"));
            if (buffers.isEmpty()) {
                throw new IllegalArgumentException("clear request must declare at least one buffer");
            }
            framebuffer = Objects.requireNonNull(framebuffer, "framebuffer");
            resourcePlan = Objects.requireNonNull(resourcePlan, "resourcePlan");
        }

        public static ClearRequest of(String operation, VulkanicClearBuffer... buffers) {
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
                FramebufferSnapshot.compatibilityDraft(),
                plan
            );
        }

        public ClearRequest withFramebufferSnapshot(int framebuffer) {
            if (framebuffer < 0) {
                throw new IllegalArgumentException("framebuffer must be >= 0");
            }
            return new ClearRequest(
                semanticIdentity,
                buffers,
                FramebufferSnapshot.legacyFramebuffer(framebuffer),
                resourcePlan
            );
        }
    }

    public record RenderPassDrawRequest(
        SemanticIdentity semanticIdentity,
        String renderPassIdentity,
        Optional<PipelineSnapshot> pipeline,
        VertexInputSnapshot vertexInput,
        GraphicsDrawCommand command,
        String source
    ) {
        public RenderPassDrawRequest {
            semanticIdentity = Objects.requireNonNull(semanticIdentity, "semanticIdentity");
            renderPassIdentity = requireNonBlank(renderPassIdentity, "renderPassIdentity");
            pipeline = Objects.requireNonNull(pipeline, "pipeline");
            vertexInput = Objects.requireNonNull(vertexInput, "vertexInput");
            command = Objects.requireNonNull(command, "command");
            source = requireNonBlank(source, "source");
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
                source
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
                source
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

    public record TransferPixelStoreSnapshot(
        int packRowLength,
        int packAlignment,
        int unpackRowLength,
        int unpackSkipRows,
        int unpackSkipPixels,
        int unpackAlignment
    ) {
        public TransferPixelStoreSnapshot {
            requireNonNegative(packRowLength, "packRowLength");
            requireAlignment(packAlignment, "packAlignment");
            requireNonNegative(unpackRowLength, "unpackRowLength");
            requireNonNegative(unpackSkipRows, "unpackSkipRows");
            requireNonNegative(unpackSkipPixels, "unpackSkipPixels");
            requireAlignment(unpackAlignment, "unpackAlignment");
        }

        public static TransferPixelStoreSnapshot defaults() {
            return new TransferPixelStoreSnapshot(0, 4, 0, 0, 0, 4);
        }

        private static void requireNonNegative(int value, String name) {
            if (value < 0) {
                throw new IllegalArgumentException(name + " must be >= 0");
            }
        }

        private static void requireAlignment(int value, String name) {
            if (value != 1 && value != 2 && value != 4 && value != 8) {
                throw new IllegalArgumentException(name + " must be one of {1,2,4,8}");
            }
        }
    }

    public record TransferCompatibilitySnapshot(
        List<VulkanicPassResourceModel.CanonicalResourceReference> sources,
        List<VulkanicPassResourceModel.CanonicalResourceReference> destinations,
        TransferPixelStoreSnapshot pixelStore,
        String source
    ) {
        public TransferCompatibilitySnapshot {
            sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
            destinations = List.copyOf(Objects.requireNonNull(destinations, "destinations"));
            pixelStore = Objects.requireNonNull(pixelStore, "pixelStore");
            source = requireNonBlank(source, "source");
        }

        public List<VulkanicPassResourceModel.CanonicalResourceReference> allResources() {
            java.util.ArrayList<VulkanicPassResourceModel.CanonicalResourceReference> all =
                new java.util.ArrayList<>(sources.size() + destinations.size());
            all.addAll(sources);
            all.addAll(destinations);
            return List.copyOf(all);
        }

        public VulkanicPassResourceModel.CanonicalResourceReference source(int index) {
            return sources.get(index);
        }

        public VulkanicPassResourceModel.CanonicalResourceReference destination(int index) {
            return destinations.get(index);
        }

        public int sourceLegacyIdOr(int index, int fallback) {
            return sources.size() > index ? sources.get(index).legacyId().orElse(fallback) : fallback;
        }

        public int destinationLegacyIdOr(int index, int fallback) {
            return destinations.size() > index ? destinations.get(index).legacyId().orElse(fallback) : fallback;
        }

        public int sourceLegacyTargetOr(int index, int fallback) {
            return sources.size() > index ? sources.get(index).legacyTarget().orElse(fallback) : fallback;
        }

        public int destinationLegacyTargetOr(int index, int fallback) {
            return destinations.size() > index ? destinations.get(index).legacyTarget().orElse(fallback) : fallback;
        }
    }

    public sealed interface TransferOperation permits
        CopyBufferSubData,
        CopyNamedBufferSubData,
        CopyImageSubData,
        CopyTextureSubImage2D,
        CopyTexImage2D,
        CopyTexSubImage2D,
        BlitFramebuffer,
        BlitNamedFramebuffer,
        ReadPixelsPointer,
        ReadPixelsFloatArray,
        BufferSubData,
        NamedBufferSubData,
        UploadTexture1D,
        UploadTexture2D,
        UploadTexture2DSubImagePointer,
        UploadTexture2DSubImageBuffer,
        UploadTexture3D,
        ClearTexImageInt,
        ClearBufferSubDataInt,
        ClearBufferFloat,
        ClearBufferInt,
        ClearBufferUint,
        ClearNamedFramebufferFloat,
        ClearNamedFramebufferInt,
        ClearNamedFramebufferUint,
        GenerateMipmap,
        GenerateTextureMipmap {
        TransferKind kind();
    }

    public record CopyBufferSubData(int readTarget, int writeTarget, long readOffset, long writeOffset, long size)
        implements TransferOperation {
        public CopyBufferSubData {
            requireNonNegative(readOffset, "readOffset");
            requireNonNegative(writeOffset, "writeOffset");
            requirePositive(size, "size");
        }
        @Override public TransferKind kind() { return TransferKind.COPY_BUFFER_SUB_DATA; }
    }

    public record CopyNamedBufferSubData(int readBuffer, int writeBuffer, long readOffset, long writeOffset, long size)
        implements TransferOperation {
        public CopyNamedBufferSubData {
            requireNonNegative(readOffset, "readOffset");
            requireNonNegative(writeOffset, "writeOffset");
            requirePositive(size, "size");
        }
        @Override public TransferKind kind() { return TransferKind.COPY_NAMED_BUFFER_SUB_DATA; }
    }

    public record CopyImageSubData(
        int srcName, int srcTarget, int srcLevel, int srcX, int srcY, int srcZ,
        int dstName, int dstTarget, int dstLevel, int dstX, int dstY, int dstZ,
        int width, int height, int depth
    ) implements TransferOperation {
        public CopyImageSubData {
            requireNonNegative(srcLevel, "srcLevel");
            requireNonNegative(dstLevel, "dstLevel");
            requirePositive(width, "width");
            requirePositive(height, "height");
            requirePositive(depth, "depth");
        }
        @Override public TransferKind kind() { return TransferKind.COPY_IMAGE_SUB_DATA; }
    }

    public record CopyTextureSubImage2D(int texture, int level, int xOffset, int yOffset, int x, int y, int width, int height)
        implements TransferOperation {
        public CopyTextureSubImage2D {
            requireNonNegative(level, "level");
            requirePositive(width, "width");
            requirePositive(height, "height");
        }
        @Override public TransferKind kind() { return TransferKind.COPY_TEXTURE_SUB_IMAGE_2D; }
    }

    public record CopyTexImage2D(int target, int level, int internalFormat, int x, int y, int width, int height, int border)
        implements TransferOperation {
        public CopyTexImage2D {
            requireNonNegative(level, "level");
            requirePositive(width, "width");
            requirePositive(height, "height");
        }
        @Override public TransferKind kind() { return TransferKind.COPY_TEX_IMAGE_2D; }
    }

    public record CopyTexSubImage2D(int target, int level, int xOffset, int yOffset, int x, int y, int width, int height)
        implements TransferOperation {
        public CopyTexSubImage2D {
            requireNonNegative(level, "level");
            requirePositive(width, "width");
            requirePositive(height, "height");
        }
        @Override public TransferKind kind() { return TransferKind.COPY_TEX_SUB_IMAGE_2D; }
    }

    public record BlitFramebuffer(
        int srcX0, int srcY0, int srcX1, int srcY1,
        int dstX0, int dstY0, int dstX1, int dstY1,
        int mask, int filter
    ) implements TransferOperation {
        @Override public TransferKind kind() { return TransferKind.BLIT_FRAMEBUFFER; }
    }

    public record BlitNamedFramebuffer(
        int readFramebuffer, int drawFramebuffer,
        int srcX0, int srcY0, int srcX1, int srcY1,
        int dstX0, int dstY0, int dstX1, int dstY1,
        int mask, int filter
    ) implements TransferOperation {
        @Override public TransferKind kind() { return TransferKind.BLIT_NAMED_FRAMEBUFFER; }
    }

    public record ReadPixelsPointer(int x, int y, int width, int height, int format, int type, long pixels)
        implements TransferOperation {
        public ReadPixelsPointer {
            requirePositive(width, "width");
            requirePositive(height, "height");
        }
        @Override public TransferKind kind() { return TransferKind.READ_PIXELS; }
    }

    public record ReadPixelsFloatArray(int x, int y, int width, int height, int format, int type, float[] pixels)
        implements TransferOperation {
        public ReadPixelsFloatArray {
            requirePositive(width, "width");
            requirePositive(height, "height");
            Objects.requireNonNull(pixels, "pixels");
        }
        @Override public TransferKind kind() { return TransferKind.READ_PIXELS_FLOAT_ARRAY; }
    }

    public record BufferSubData(int target, long offset, java.nio.ByteBuffer payload) implements TransferOperation {
        public BufferSubData {
            requireNonNegative(offset, "offset");
            payload = copyBytePayload(Objects.requireNonNull(payload, "payload"));
        }
        @Override public java.nio.ByteBuffer payload() { return payload.asReadOnlyBuffer(); }
        @Override public TransferKind kind() { return TransferKind.BUFFER_SUB_DATA; }
    }

    public record NamedBufferSubData(int buffer, long offset, java.nio.ByteBuffer payload) implements TransferOperation {
        public NamedBufferSubData {
            requireNonNegative(offset, "offset");
            payload = copyBytePayload(Objects.requireNonNull(payload, "payload"));
        }
        @Override public java.nio.ByteBuffer payload() { return payload.asReadOnlyBuffer(); }
        @Override public TransferKind kind() { return TransferKind.NAMED_BUFFER_SUB_DATA; }
    }

    public record UploadTexture1D(int target, int level, int internalFormat, int width, int border, int format, int type,
                                  java.nio.ByteBuffer payload) implements TransferOperation {
        public UploadTexture1D {
            requireNonNegative(level, "level");
            requirePositive(width, "width");
            payload = copyBytePayload(payload);
        }
        @Override public java.nio.ByteBuffer payload() { return payload == null ? null : payload.asReadOnlyBuffer(); }
        @Override public TransferKind kind() { return TransferKind.UPLOAD_TEXTURE_1D; }
    }

    public record UploadTexture2D(int target, int level, int internalFormat, int width, int height, int border,
                                  int format, int type, java.nio.ByteBuffer payload) implements TransferOperation {
        public UploadTexture2D {
            requireNonNegative(level, "level");
            requirePositive(width, "width");
            requirePositive(height, "height");
            payload = copyBytePayload(payload);
        }
        @Override public java.nio.ByteBuffer payload() { return payload == null ? null : payload.asReadOnlyBuffer(); }
        @Override public TransferKind kind() { return TransferKind.UPLOAD_TEXTURE_2D; }
    }

    public record UploadTexture2DSubImagePointer(int target, int level, int xOffset, int yOffset, int width, int height,
                                                 int format, int type, long pixels) implements TransferOperation {
        public UploadTexture2DSubImagePointer {
            requireNonNegative(level, "level");
            requirePositive(width, "width");
            requirePositive(height, "height");
            requireNonNegative(pixels, "pixels");
        }
        @Override public TransferKind kind() { return TransferKind.UPLOAD_TEXTURE_2D_SUB_IMAGE_POINTER; }
    }

    public record UploadTexture2DSubImageBuffer(int target, int level, int xOffset, int yOffset, int width, int height,
                                                int format, int type, java.nio.ByteBuffer payload) implements TransferOperation {
        public UploadTexture2DSubImageBuffer {
            requireNonNegative(level, "level");
            requirePositive(width, "width");
            requirePositive(height, "height");
            payload = copyBytePayload(Objects.requireNonNull(payload, "payload"));
        }
        @Override public java.nio.ByteBuffer payload() { return payload.asReadOnlyBuffer(); }
        @Override public TransferKind kind() { return TransferKind.UPLOAD_TEXTURE_2D_SUB_IMAGE_BUFFER; }
    }

    public record UploadTexture3D(int target, int level, int internalFormat, int width, int height, int depth, int border,
                                  int format, int type, java.nio.ByteBuffer payload) implements TransferOperation {
        public UploadTexture3D {
            requireNonNegative(level, "level");
            requirePositive(width, "width");
            requirePositive(height, "height");
            requirePositive(depth, "depth");
            payload = copyBytePayload(payload);
        }
        @Override public java.nio.ByteBuffer payload() { return payload == null ? null : payload.asReadOnlyBuffer(); }
        @Override public TransferKind kind() { return TransferKind.UPLOAD_TEXTURE_3D; }
    }

    public record ClearTexImageInt(int texture, int level, int format, int type, int[] data) implements TransferOperation {
        public ClearTexImageInt {
            requireNonNegative(level, "level");
            data = data == null ? null : java.util.Arrays.copyOf(data, data.length);
        }
        @Override public int[] data() { return data == null ? null : java.util.Arrays.copyOf(data, data.length); }
        @Override public TransferKind kind() { return TransferKind.CLEAR_TEX_IMAGE_INT; }
    }

    public record ClearBufferSubDataInt(int target, int internalFormat, long offset, long size, int format, int type, int[] data)
        implements TransferOperation {
        public ClearBufferSubDataInt {
            requireNonNegative(offset, "offset");
            requirePositive(size, "size");
            data = data == null ? null : java.util.Arrays.copyOf(data, data.length);
        }
        @Override public int[] data() { return data == null ? null : java.util.Arrays.copyOf(data, data.length); }
        @Override public TransferKind kind() { return TransferKind.CLEAR_BUFFER_SUB_DATA_INT; }
    }

    public record ClearBufferFloat(int buffer, int drawBuffer, float[] values) implements TransferOperation {
        public ClearBufferFloat {
            values = values == null ? null : java.util.Arrays.copyOf(values, values.length);
        }
        @Override public float[] values() { return values == null ? null : java.util.Arrays.copyOf(values, values.length); }
        @Override public TransferKind kind() { return TransferKind.CLEAR_BUFFER_FLOAT; }
    }

    public record ClearBufferInt(int buffer, int drawBuffer, int[] values) implements TransferOperation {
        public ClearBufferInt {
            values = values == null ? null : java.util.Arrays.copyOf(values, values.length);
        }
        @Override public int[] values() { return values == null ? null : java.util.Arrays.copyOf(values, values.length); }
        @Override public TransferKind kind() { return TransferKind.CLEAR_BUFFER_INT; }
    }

    public record ClearBufferUint(int buffer, int drawBuffer, int[] values) implements TransferOperation {
        public ClearBufferUint {
            values = values == null ? null : java.util.Arrays.copyOf(values, values.length);
        }
        @Override public int[] values() { return values == null ? null : java.util.Arrays.copyOf(values, values.length); }
        @Override public TransferKind kind() { return TransferKind.CLEAR_BUFFER_UINT; }
    }

    public record ClearNamedFramebufferFloat(int framebuffer, int buffer, int drawBuffer, float[] values)
        implements TransferOperation {
        public ClearNamedFramebufferFloat {
            values = values == null ? null : java.util.Arrays.copyOf(values, values.length);
        }
        @Override public float[] values() { return values == null ? null : java.util.Arrays.copyOf(values, values.length); }
        @Override public TransferKind kind() { return TransferKind.CLEAR_NAMED_FRAMEBUFFER_FLOAT; }
    }

    public record ClearNamedFramebufferInt(int framebuffer, int buffer, int drawBuffer, int[] values)
        implements TransferOperation {
        public ClearNamedFramebufferInt {
            values = values == null ? null : java.util.Arrays.copyOf(values, values.length);
        }
        @Override public int[] values() { return values == null ? null : java.util.Arrays.copyOf(values, values.length); }
        @Override public TransferKind kind() { return TransferKind.CLEAR_NAMED_FRAMEBUFFER_INT; }
    }

    public record ClearNamedFramebufferUint(int framebuffer, int buffer, int drawBuffer, int[] values)
        implements TransferOperation {
        public ClearNamedFramebufferUint {
            values = values == null ? null : java.util.Arrays.copyOf(values, values.length);
        }
        @Override public int[] values() { return values == null ? null : java.util.Arrays.copyOf(values, values.length); }
        @Override public TransferKind kind() { return TransferKind.CLEAR_NAMED_FRAMEBUFFER_UINT; }
    }

    public record GenerateMipmap(int target) implements TransferOperation {
        @Override public TransferKind kind() { return TransferKind.GENERATE_MIPMAP; }
    }

    public record GenerateTextureMipmap(int texture) implements TransferOperation {
        @Override public TransferKind kind() { return TransferKind.GENERATE_TEXTURE_MIPMAP; }
    }

    public record TransferRequest(
        SemanticIdentity semanticIdentity,
        TransferKind kind,
        TransferOperation operation,
        List<VulkanicPassResourceModel.ResourceUse> resources,
        VulkanicPassResourceModel.PassExecutionPlan resourcePlan,
        java.nio.ByteBuffer bytePayload,
        float[] floatPayload,
        int[] intPayload,
        float[] floatArrayOutput,
        Optional<TransferCompatibilitySnapshot> transferSnapshot
    ) {
        public TransferRequest {
            semanticIdentity = Objects.requireNonNull(semanticIdentity, "semanticIdentity");
            kind = Objects.requireNonNull(kind, "kind");
            operation = Objects.requireNonNull(operation, "operation");
            if (operation.kind() != kind) {
                throw new IllegalArgumentException("transfer operation kind " + operation.kind() + " does not match request kind " + kind);
            }
            resources = List.copyOf(Objects.requireNonNull(resources, "resources"));
            resourcePlan = Objects.requireNonNull(resourcePlan, "resourcePlan");
            bytePayload = copyBytePayload(bytePayload);
            floatPayload = floatPayload == null ? null : java.util.Arrays.copyOf(floatPayload, floatPayload.length);
            intPayload = intPayload == null ? null : java.util.Arrays.copyOf(intPayload, intPayload.length);
            transferSnapshot = Objects.requireNonNull(transferSnapshot, "transferSnapshot");
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

        public TransferCompatibilitySnapshot requireTransferSnapshot() {
            return transferSnapshot.orElseThrow(() ->
                new IllegalStateException("Transfer request reached backend without canonical transfer snapshot"));
        }

        public TransferRequest withTransferSnapshot(TransferCompatibilitySnapshot snapshot) {
            Objects.requireNonNull(snapshot, "snapshot");
            List<VulkanicPassResourceModel.CanonicalResourceReference> references = snapshot.allResources();
            java.util.ArrayList<VulkanicPassResourceModel.ResourceUse> snapshotUses = new java.util.ArrayList<>(references.size());
            for (int i = 0; i < references.size(); i++) {
                VulkanicPassResourceModel.CanonicalResourceReference reference = references.get(i);
                snapshotUses.add(reference.asResourceUse(
                    semanticIdentity.label() + ":transfer:" + reference.resource().logicalName(),
                    false,
                    i
                ));
            }
            VulkanicPassResourceModel.PassKind passKind = kind == TransferKind.READ_PIXELS
                || kind == TransferKind.READ_PIXELS_FLOAT_ARRAY
                    ? VulkanicPassResourceModel.PassKind.READBACK
                    : VulkanicPassResourceModel.PassKind.TRANSFER;
            VulkanicPassResourceModel.PassRequest passRequest = new VulkanicPassResourceModel.PassRequest(
                passKind,
                semanticIdentity.label(),
                List.of(),
                snapshotUses,
                List.of(),
                List.of(new VulkanicPassResourceModel.Command(
                    kind.name().toLowerCase(java.util.Locale.ROOT),
                    OptionalInt.empty(),
                    OptionalInt.empty()
                )),
                List.of("transition-before-operation", "publish-usage-after-operation"),
                false,
                false
            );
            return new TransferRequest(
                semanticIdentity,
                kind,
                operation,
                snapshotUses,
                VulkanicPassResourcePlanner.plan(passRequest),
                bytePayload,
                floatPayload,
                intPayload,
                floatArrayOutput,
                Optional.of(snapshot)
            );
        }

        public static TransferRequest of(
            String operationLabel,
            TransferOperation operation,
            VulkanicPassResourceModel.ResourceKind resourceKind,
            String stableKey,
            VulkanicPassResourceModel.Access access,
            VulkanicResourceUsage usage
        ) {
            operationLabel = requireNonBlank(operationLabel, "operationLabel");
            operation = Objects.requireNonNull(operation, "operation");
            TransferKind kind = operation.kind();
            VulkanicPassResourceModel.ResourceUse use = VulkanicLegacyCompatibilityAdapter.resourceUse(
                operationLabel,
                resourceKind,
                stableKey,
                access,
                VulkanicPassResourceModel.Subresource.bufferRange(0, 1),
                usage,
                operationLabel,
                false,
                0
            );
            VulkanicPassResourceModel.PassExecutionPlan plan =
                VulkanicLegacyCompatibilityAdapter.planTransfer(new VulkanicLegacyCompatibilityAdapter.TransferSnapshot(
                    kind == TransferKind.READ_PIXELS
                        || kind == TransferKind.READ_PIXELS_FLOAT_ARRAY
                            ? VulkanicPassResourceModel.PassKind.READBACK
                            : VulkanicPassResourceModel.PassKind.TRANSFER,
                    operationLabel,
                    kind.name().toLowerCase(java.util.Locale.ROOT),
                    operationLabel,
                    resourceKind,
                    stableKey,
                    access,
                    VulkanicPassResourceModel.Subresource.bufferRange(0, 1),
                    usage,
                    operationLabel,
                    List.of(),
                    false,
                    false
                ));
            return new TransferRequest(
                SemanticIdentity.legacy(operationLabel),
                kind,
                operation,
                List.of(use),
                plan,
                operationBytePayload(operation),
                operationFloatPayload(operation),
                operationIntPayload(operation),
                operationFloatArrayOutput(operation),
                Optional.empty()
            );
        }

        private static java.nio.ByteBuffer operationBytePayload(TransferOperation operation) {
            return switch (operation) {
                case BufferSubData op -> op.payload();
                case NamedBufferSubData op -> op.payload();
                case UploadTexture1D op -> op.payload();
                case UploadTexture2D op -> op.payload();
                case UploadTexture2DSubImageBuffer op -> op.payload();
                case UploadTexture3D op -> op.payload();
                default -> null;
            };
        }

        private static float[] operationFloatPayload(TransferOperation operation) {
            return switch (operation) {
                case ClearBufferFloat op -> op.values();
                case ClearNamedFramebufferFloat op -> op.values();
                default -> null;
            };
        }

        private static int[] operationIntPayload(TransferOperation operation) {
            return switch (operation) {
                case ClearTexImageInt op -> op.data();
                case ClearBufferSubDataInt op -> op.data();
                case ClearBufferInt op -> op.values();
                case ClearBufferUint op -> op.values();
                case ClearNamedFramebufferInt op -> op.values();
                case ClearNamedFramebufferUint op -> op.values();
                default -> null;
            };
        }

        private static float[] operationFloatArrayOutput(TransferOperation operation) {
            return switch (operation) {
                case ReadPixelsFloatArray op -> op.pixels();
                default -> null;
            };
        }

        private static void requireLength(int[] values, int minimum, String name) {
            if (values.length < minimum) {
                throw new IllegalArgumentException(name + " requires at least " + minimum + " values");
            }
        }

        private static void requireLength(long[] values, int minimum, String name) {
            if (values.length < minimum) {
                throw new IllegalArgumentException(name + " requires at least " + minimum + " values");
            }
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

    private static void requireNonNegative(long value, String name) {
        if (value < 0L) {
            throw new IllegalArgumentException(name + " must be >= 0");
        }
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0L) {
            throw new IllegalArgumentException(name + " must be > 0");
        }
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

    private static String buildContractSchema() {
        StringBuilder builder = new StringBuilder(4096);
        builder.append("vulkanic-gal-contract ").append(CONTRACT_VERSION).append('\n');
        appendLine(builder, "requests",
            "GraphicsDrawRequest",
            "RenderPassDrawRequest",
            "ComputeDispatchRequest",
            "RenderPassBeginRequest",
            "RenderPassEndRequest",
            "ComputePassBeginRequest",
            "ComputePassEndRequest",
            "ClearRequest",
            "TransferRequest");
        appendEnum(builder, "ExecutionStatus", ExecutionStatus.values());
        appendEnum(builder, "DrawCommandKind", DrawCommandKind.values());
        appendLine(builder, "ComputeDispatchCommand", "direct", "indirect");
        appendEnum(builder, "RenderPassBeginKind", RenderPassBeginKind.values());
        appendEnum(builder, "TransferKind", TransferKind.values());
        appendPermitted(builder, "ExecutionResult", ExecutionResult.class);
        appendPermitted(builder, "TransferOperation", TransferOperation.class);
        appendLine(builder, "resource-references",
            "CanonicalResourceReference",
            "ResourceIdentity(kind,logicalName,stableKey)",
            "ResourceGeneration",
            "Subresource(mip,layer,aspect,range)",
            "Access",
            "VulkanicResourceUsage");
        appendLine(builder, "ownership",
            "requests-own-payloads",
            "requests-have-no-native-handles",
            "backends-resolve-stable-resource-identities",
            "diagnostics-outside-semantic-cache-identity");
        return builder.toString();
    }

    private static void appendEnum(StringBuilder builder, String name, Enum<?>[] values) {
        appendLine(
            builder,
            name,
            Arrays.stream(values)
                .map(Enum::name)
                .sorted()
                .toArray(String[]::new)
        );
    }

    private static void appendPermitted(StringBuilder builder, String name, Class<?> type) {
        Class<?>[] permitted = type.getPermittedSubclasses();
        appendLine(
            builder,
            name,
            Arrays.stream(permitted == null ? new Class<?>[0] : permitted)
                .map(Class::getSimpleName)
                .sorted()
                .toArray(String[]::new)
        );
    }

    private static void appendLine(StringBuilder builder, String name, String... values) {
        builder.append(name).append('=');
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(values[index]);
        }
        builder.append('\n');
    }

    private static String sha256Hex(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(StandardCharsets.UTF_8));
            char[] output = new char[digest.length * 2];
            char[] hex = "0123456789abcdef".toCharArray();
            for (int i = 0; i < digest.length; i++) {
                int value = digest[i] & 0xFF;
                output[i * 2] = hex[value >>> 4];
                output[i * 2 + 1] = hex[value & 0x0F];
            }
            return new String(output);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is unavailable", exception);
        }
    }
}
