package net.vulkanic;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.Nullable;

/**
 * Versioned internal explicit Vulkanic GAL v2 model.
 *
 * <p>Version 1 is the frozen compatibility-submission contract in
 * {@link VulkanicGalExecutionRequest}. GAL v2 is the migration target: draws
 * reference persistent backend-neutral objects instead of serializing the whole
 * mutable GL-style state for every command. Native handles, Vulkan layouts,
 * access masks, descriptor pools, command buffers, and GL object materialization
 * remain backend-owned.</p>
 *
 * <p>The first production slice is intentionally narrow. It is fed by the
 * legacy compatibility translator for the dominant legacy-program draw
 * families, creates stable object handles from immutable semantic state, and
 * submits a compact draw command. Backends may cache native lowering artifacts
 * behind these handles, but must still validate resource generations before
 * command emission.</p>
 */
public final class VulkanicGalV2 {
    private VulkanicGalV2() {
    }

    public static final int CONTRACT_MAJOR_VERSION = 2;
    public static final int CONTRACT_MINOR_VERSION = 0;
    public static final int CONTRACT_PATCH_VERSION = 0;
    public static final String CONTRACT_VERSION =
        CONTRACT_MAJOR_VERSION + "." + CONTRACT_MINOR_VERSION + "." + CONTRACT_PATCH_VERSION;
    private static final String CONTRACT_SCHEMA = String.join("\n",
        "vulkanic-gal-v2-contract " + CONTRACT_VERSION,
        "Handle(kind,id,generation,semanticKey)",
        "VertexLayout(bindings,attributes,disabledDefaults,compatibilityIndex)",
        "VertexStreamBindings(vertexStreams,indexStream)",
        "ResourceLayout(bindings)",
        "ResourceSet(layout,bindings,generations)",
        "UniformLayout(bindingName,set,binding,members)",
        "UniformBinding(layout,bindingName,programId)",
        "ExplicitGraphicsObjects(program,pipeline,vertexLayout,resourceLayout,resourceSet,renderTarget)",
        "ExplicitGraphicsDrawRequest(identity,objects,resourceSet,drawCommand,vertexStreams,resourcePlanFingerprint)"
    );
    public static final String CONTRACT_SCHEMA_FINGERPRINT = sha256Hex(CONTRACT_SCHEMA);

    private static final boolean LEGACY_PROGRAM_SLICE_ENABLED =
        Boolean.parseBoolean(System.getProperty("mattmc.gal.v2.legacyProgramSlice", "true"));
    private static final int HOT_LEGACY_PROGRAM_A =
        Integer.getInteger("mattmc.gal.v2.legacyProgramA", 11);
    private static final int HOT_LEGACY_PROGRAM_B =
        Integer.getInteger("mattmc.gal.v2.legacyProgramB", 9);
    private static final boolean ALL_LEGACY_PROGRAMS_ENABLED =
        Boolean.parseBoolean(System.getProperty("mattmc.gal.v2.allLegacyPrograms", "false"));

    private static final AtomicInteger NEXT_HANDLE_ID = new AtomicInteger(1);
    private static final Map<String, Handle> HANDLES_BY_SEMANTIC_KEY = new ConcurrentHashMap<>();
    private static final Map<ExplicitGraphicsObjectKey, ExplicitGraphicsObjects> GRAPHICS_OBJECTS_BY_KEY =
        new ConcurrentHashMap<>();
    private static final Map<Handle, ExplicitGraphicsObjects> GRAPHICS_OBJECTS_BY_HANDLE =
        new ConcurrentHashMap<>();
    private static final Map<String, ResourceLayout> RESOURCE_LAYOUTS_BY_KEY = new ConcurrentHashMap<>();
    private static final Map<Handle, ResourceLayout> RESOURCE_LAYOUTS_BY_HANDLE = new ConcurrentHashMap<>();
    private static final Map<String, ResourceSet> RESOURCE_SETS_BY_KEY = new ConcurrentHashMap<>();
    private static final Map<Handle, ResourceSet> RESOURCE_SETS_BY_HANDLE = new ConcurrentHashMap<>();
    private static final Map<String, UniformLayout> UNIFORM_LAYOUTS_BY_KEY = new ConcurrentHashMap<>();
    private static final Map<Handle, UniformLayout> UNIFORM_LAYOUTS_BY_HANDLE = new ConcurrentHashMap<>();
    private static final Map<String, UniformBinding> UNIFORM_BINDINGS_BY_KEY = new ConcurrentHashMap<>();
    private static final Map<Handle, UniformBinding> UNIFORM_BINDINGS_BY_HANDLE = new ConcurrentHashMap<>();

    public static String contractSchema() {
        return CONTRACT_SCHEMA;
    }

    public static String contractSchemaFingerprint() {
        return CONTRACT_SCHEMA_FINGERPRINT;
    }

    public enum ObjectKind {
        PROGRAM,
        PIPELINE,
        VERTEX_LAYOUT,
        RESOURCE_LAYOUT,
        RESOURCE_SET,
        UNIFORM_LAYOUT,
        UNIFORM_BINDING,
        RENDER_TARGET,
        GRAPHICS_OBJECT_SET
    }

    public record Handle(ObjectKind kind, int id, long generation, String semanticKey) {
        public Handle {
            kind = Objects.requireNonNull(kind, "kind");
            if (id <= 0) {
                throw new IllegalArgumentException("handle id must be positive");
            }
            if (generation < 0L) {
                throw new IllegalArgumentException("handle generation must be >= 0");
            }
            semanticKey = requireNonBlank(semanticKey, "semanticKey");
        }
    }

    public record ExplicitGraphicsObjects(
        Handle handle,
        Handle program,
        Handle pipeline,
        Handle vertexLayoutHandle,
        Handle resourceLayout,
        Handle resourceSet,
        Handle renderTarget,
        String programKey,
        String pipelineKey,
        String vertexInputKey,
        String resourceLayoutKey,
        String resourceSetKey,
        String renderTargetKey,
        VertexLayout vertexLayout,
        VulkanicCompatibilityState.GraphicsSnapshot immutableCompatibilitySeed
    ) {
        public ExplicitGraphicsObjects {
            handle = Objects.requireNonNull(handle, "handle");
            program = Objects.requireNonNull(program, "program");
            pipeline = Objects.requireNonNull(pipeline, "pipeline");
            vertexLayoutHandle = Objects.requireNonNull(vertexLayoutHandle, "vertexLayoutHandle");
            resourceLayout = Objects.requireNonNull(resourceLayout, "resourceLayout");
            resourceSet = Objects.requireNonNull(resourceSet, "resourceSet");
            renderTarget = Objects.requireNonNull(renderTarget, "renderTarget");
            programKey = requireNonBlank(programKey, "programKey");
            pipelineKey = requireNonBlank(pipelineKey, "pipelineKey");
            vertexInputKey = requireNonBlank(vertexInputKey, "vertexInputKey");
            resourceLayoutKey = requireNonBlank(resourceLayoutKey, "resourceLayoutKey");
            resourceSetKey = requireNonBlank(resourceSetKey, "resourceSetKey");
            renderTargetKey = requireNonBlank(renderTargetKey, "renderTargetKey");
            vertexLayout = Objects.requireNonNull(vertexLayout, "vertexLayout");
            immutableCompatibilitySeed = Objects.requireNonNull(immutableCompatibilitySeed, "immutableCompatibilitySeed");
        }
    }

    public record VertexLayout(
        java.util.List<VertexBindingLayout> bindings,
        java.util.List<VertexAttributeLayout> attributes,
        java.util.Map<Integer, float[]> disabledAttributeDefaults,
        boolean requiresCompatibilityIndexBuffer
    ) {
        public VertexLayout {
            bindings = java.util.List.copyOf(Objects.requireNonNull(bindings, "bindings"));
            attributes = java.util.List.copyOf(Objects.requireNonNull(attributes, "attributes"));
            disabledAttributeDefaults = copyDefaultAttributeMap(disabledAttributeDefaults);
        }
    }

    public record VertexBindingLayout(int binding, int stride, int divisor) {
        public VertexBindingLayout {
            if (binding < 0) {
                throw new IllegalArgumentException("binding must be >= 0");
            }
            if (stride < 0) {
                throw new IllegalArgumentException("stride must be >= 0");
            }
            if (divisor < 0) {
                throw new IllegalArgumentException("divisor must be >= 0");
            }
        }
    }

    public record VertexAttributeLayout(
        int location,
        int binding,
        int size,
        int type,
        boolean normalized,
        boolean integer,
        int relativeOffset,
        int divisor
    ) {
        public VertexAttributeLayout {
            if (location < 0) {
                throw new IllegalArgumentException("location must be >= 0");
            }
            if (binding < 0) {
                throw new IllegalArgumentException("binding must be >= 0");
            }
            if (relativeOffset < 0) {
                throw new IllegalArgumentException("relativeOffset must be >= 0");
            }
        }
    }

    public record VertexStreamBindings(
        java.util.List<VertexStream> vertexStreams,
        Optional<IndexStream> indexStream
    ) {
        public VertexStreamBindings {
            vertexStreams = java.util.List.copyOf(Objects.requireNonNull(vertexStreams, "vertexStreams"));
            indexStream = Objects.requireNonNull(indexStream, "indexStream");
        }
    }

    public record VertexStream(int binding, int buffer, long baseOffset, boolean defaultAttributeBuffer) {
        public VertexStream {
            if (binding < 0) {
                throw new IllegalArgumentException("binding must be >= 0");
            }
            if (baseOffset < 0L) {
                throw new IllegalArgumentException("baseOffset must be >= 0");
            }
        }
    }

    public record IndexStream(int buffer, VulkanicIndexType type, long baseOffset) {
        public IndexStream {
            type = Objects.requireNonNull(type, "type");
            if (baseOffset < 0L) {
                throw new IllegalArgumentException("baseOffset must be >= 0");
            }
        }
    }

    public record ResourceLayout(
        Handle handle,
        java.util.List<ResourceLayoutBinding> bindings,
        String semanticKey
    ) {
        public ResourceLayout {
            handle = Objects.requireNonNull(handle, "handle");
            bindings = java.util.List.copyOf(Objects.requireNonNull(bindings, "bindings"));
            semanticKey = requireNonBlank(semanticKey, "semanticKey");
        }
    }

    public record ResourceLayoutBinding(
        String name,
        VulkanicPassResourceModel.BindingKind bindingKind,
        VulkanicPassResourceModel.ResourceKind resourceKind,
        OptionalInt set,
        OptionalInt binding,
        OptionalInt bindingUnit
    ) {
        public ResourceLayoutBinding {
            name = requireNonBlank(name, "name");
            bindingKind = Objects.requireNonNull(bindingKind, "bindingKind");
            resourceKind = Objects.requireNonNull(resourceKind, "resourceKind");
            set = Objects.requireNonNull(set, "set");
            binding = Objects.requireNonNull(binding, "binding");
            bindingUnit = Objects.requireNonNull(bindingUnit, "bindingUnit");
        }
    }

    public record ResourceSet(
        Handle handle,
        Handle layout,
        java.util.List<ResourceBinding> bindings,
        String semanticKey
    ) {
        public ResourceSet {
            handle = Objects.requireNonNull(handle, "handle");
            layout = Objects.requireNonNull(layout, "layout");
            bindings = java.util.List.copyOf(Objects.requireNonNull(bindings, "bindings"));
            semanticKey = requireNonBlank(semanticKey, "semanticKey");
        }

        public Optional<ResourceBinding> sampledTextureBinding(int textureUnit) {
            return Optional.ofNullable(sampledTextureBindingOrNull(textureUnit));
        }

        public Optional<ResourceBinding> storageImageBinding(int imageUnit) {
            return Optional.ofNullable(storageImageBindingOrNull(imageUnit));
        }

        public Optional<ResourceBinding> bufferRangeBinding(int bindingIndex) {
            return Optional.ofNullable(bufferRangeBindingOrNull(bindingIndex));
        }

        public Optional<ResourceBinding> uniformBinding(String bindingName) {
            return Optional.ofNullable(uniformBindingOrNull(bindingName));
        }

        @Nullable
        public ResourceBinding sampledTextureBindingOrNull(int textureUnit) {
            for (ResourceBinding binding : bindings) {
                if (binding.resourceUse().kind() != VulkanicPassResourceModel.ResourceKind.SAMPLED_TEXTURE) {
                    continue;
                }
                Optional<VulkanicPassResourceModel.CanonicalResourceReference> reference = binding.resourceReference();
                if (reference.isEmpty()) {
                    continue;
                }
                OptionalInt bindingUnit = reference.orElseThrow().bindingUnit();
                if (bindingUnit.isPresent() && bindingUnit.getAsInt() == textureUnit) {
                    return binding;
                }
            }
            return null;
        }

        @Nullable
        public ResourceBinding storageImageBindingOrNull(int imageUnit) {
            for (ResourceBinding binding : bindings) {
                if (binding.resourceUse().kind() != VulkanicPassResourceModel.ResourceKind.STORAGE_TEXTURE) {
                    continue;
                }
                Optional<VulkanicPassResourceModel.CanonicalResourceReference> reference = binding.resourceReference();
                if (reference.isEmpty()) {
                    continue;
                }
                OptionalInt bindingUnit = reference.orElseThrow().bindingUnit();
                if (bindingUnit.isPresent() && bindingUnit.getAsInt() == imageUnit) {
                    return binding;
                }
            }
            return null;
        }

        @Nullable
        public ResourceBinding bufferRangeBindingOrNull(int bindingIndex) {
            for (ResourceBinding binding : bindings) {
                Optional<VulkanicPassResourceModel.CanonicalResourceReference> reference = binding.resourceReference();
                if (reference.isEmpty()) {
                    continue;
                }
                VulkanicPassResourceModel.CanonicalResourceReference canonicalReference = reference.orElseThrow();
                OptionalInt bindingUnit = canonicalReference.bindingUnit();
                if (canonicalReference.bindingKind() == VulkanicPassResourceModel.BindingKind.BUFFER_RANGE
                    && bindingUnit.isPresent()
                    && bindingUnit.getAsInt() == bindingIndex) {
                    return binding;
                }
            }
            return null;
        }

        @Nullable
        public ResourceBinding uniformBindingOrNull(String bindingName) {
            for (ResourceBinding binding : bindings) {
                if (binding.name().equals(bindingName) && binding.uniformBinding().isPresent()) {
                    return binding;
                }
            }
            return null;
        }
    }

    public record ResourceBinding(
        String name,
        VulkanicPassResourceModel.ResourceUse resourceUse,
        OptionalInt set,
        OptionalInt binding,
        Optional<VulkanicPassResourceModel.CanonicalResourceReference> resourceReference,
        Optional<Handle> uniformBinding
    ) {
        public ResourceBinding {
            name = requireNonBlank(name, "name");
            resourceUse = Objects.requireNonNull(resourceUse, "resourceUse");
            set = Objects.requireNonNull(set, "set");
            binding = Objects.requireNonNull(binding, "binding");
            resourceReference = Objects.requireNonNull(resourceReference, "resourceReference");
            uniformBinding = Objects.requireNonNull(uniformBinding, "uniformBinding");
        }
    }

    public record UniformLayout(
        Handle handle,
        String bindingName,
        OptionalInt set,
        OptionalInt binding,
        java.util.List<UniformMember> members,
        String semanticKey
    ) {
        public UniformLayout {
            handle = Objects.requireNonNull(handle, "handle");
            bindingName = requireNonBlank(bindingName, "bindingName");
            set = Objects.requireNonNull(set, "set");
            binding = Objects.requireNonNull(binding, "binding");
            members = java.util.List.copyOf(Objects.requireNonNull(members, "members"));
            semanticKey = requireNonBlank(semanticKey, "semanticKey");
        }
    }

    public record UniformMember(String name, int offset, String type, int range) {
        public UniformMember {
            name = requireNonBlank(name, "name");
            type = requireNonBlank(type, "type");
            if (offset < 0) {
                throw new IllegalArgumentException("uniform member offset must be >= 0");
            }
            if (range < 0) {
                throw new IllegalArgumentException("uniform member range must be >= 0");
            }
        }
    }

    public record UniformBinding(
        Handle handle,
        Handle layout,
        int programId,
        String bindingName,
        String semanticKey
    ) {
        public UniformBinding {
            handle = Objects.requireNonNull(handle, "handle");
            layout = Objects.requireNonNull(layout, "layout");
            if (programId <= 0) {
                throw new IllegalArgumentException("programId must be positive");
            }
            bindingName = requireNonBlank(bindingName, "bindingName");
            semanticKey = requireNonBlank(semanticKey, "semanticKey");
        }
    }

    public record ExplicitGraphicsDrawRequest(
        VulkanicGalExecutionRequest.SemanticIdentity semanticIdentity,
        Handle graphicsObjects,
        Handle resourceSet,
        VulkanicGalExecutionRequest.GraphicsDrawCommand command,
        VertexStreamBindings vertexStreams,
        VulkanicPassResourceModel.PassExecutionPlan resourcePlan,
        String resourcePlanFingerprint
    ) {
        public ExplicitGraphicsDrawRequest {
            semanticIdentity = Objects.requireNonNull(semanticIdentity, "semanticIdentity");
            graphicsObjects = Objects.requireNonNull(graphicsObjects, "graphicsObjects");
            resourceSet = Objects.requireNonNull(resourceSet, "resourceSet");
            command = Objects.requireNonNull(command, "command");
            vertexStreams = Objects.requireNonNull(vertexStreams, "vertexStreams");
            resourcePlan = Objects.requireNonNull(resourcePlan, "resourcePlan");
            resourcePlanFingerprint = requireNonBlank(resourcePlanFingerprint, "resourcePlanFingerprint");
        }
    }

    public static Optional<ExplicitGraphicsDrawRequest> tryCaptureLegacyProgramSlice(
        VulkanicGalExecutionRequest.GraphicsDrawRequest capturedV1Request
    ) {
        Objects.requireNonNull(capturedV1Request, "capturedV1Request");
        if (!LEGACY_PROGRAM_SLICE_ENABLED) {
            return Optional.empty();
        }
        Optional<VulkanicCompatibilityState.GraphicsSnapshot> shared =
            capturedV1Request.compatibilitySnapshot().sharedCompatibilityState();
        if (shared.isEmpty()) {
            return Optional.empty();
        }
        int programId = shared.get().programId();
        if (!isHotLegacyProgram(programId)) {
            return Optional.empty();
        }
        ExplicitGraphicsObjects objects = graphicsObjectsFor(shared.get(), capturedV1Request);
        return Optional.of(new ExplicitGraphicsDrawRequest(
            capturedV1Request.semanticIdentity(),
            objects.handle(),
            objects.resourceSet(),
            capturedV1Request.command(),
            vertexStreamsFor(shared.get(), capturedV1Request.command()),
            capturedV1Request.resourcePlan(),
            sha256Hex(capturedV1Request.resourcePlan().orderedUses().toString())
        ));
    }

    public static Optional<ExplicitGraphicsDrawRequest> tryCaptureLegacyProgramSlice(
        VulkanicCompatibilityState.GraphicsSnapshot snapshot,
        VulkanicGalExecutionRequest.GraphicsDrawRequest draftRequest,
        boolean eagerResourceDeclarations
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(draftRequest, "draftRequest");
        if (!LEGACY_PROGRAM_SLICE_ENABLED || !isSupportedLegacyProgram(snapshot.programId(), eagerResourceDeclarations)) {
            return Optional.empty();
        }

        VulkanicGalExecutionRequest.VertexInputSnapshot vertexInput =
            snapshot.vertexInputSnapshot(draftRequest);
        List<VulkanicPassResourceModel.BindingSnapshot> bindings =
            eagerResourceDeclarations ? snapshot.bindingSnapshots() : List.of();
        VulkanicPassResourceModel.PassExecutionPlan resourcePlan =
            VulkanicLegacyCompatibilityAdapter.planDraw(new VulkanicLegacyCompatibilityAdapter.DrawSnapshot(
                draftRequest.semanticIdentity().label(),
                vertexInput.vertexBuffers(),
                vertexInput.indexBuffer(),
                List.of(),
                bindings,
                drawCommandSnapshot(draftRequest.command()),
                false,
                false
            ));
        VulkanicGalExecutionRequest.GraphicsCompatibilitySnapshot compatibilitySnapshot =
            new VulkanicGalExecutionRequest.GraphicsCompatibilitySnapshot(
                Optional.empty(),
                vertexInput,
                resourcePlan.orderedUses(),
                Optional.empty(),
                bindings,
                Optional.of(snapshot),
                "frontend-shared-gal-v2-draw"
            );
        VulkanicGalExecutionRequest.GraphicsDrawRequest capturedRequest =
            draftRequest.withCompatibilitySnapshot(compatibilitySnapshot);
        ExplicitGraphicsObjects objects = graphicsObjectsFor(snapshot, capturedRequest, eagerResourceDeclarations);
        ResourceSet requestResourceSet = eagerResourceDeclarations
            ? requireResourceSet(objects.resourceSet())
            : currentRequestResourceSetFor(snapshot);
        return Optional.of(new ExplicitGraphicsDrawRequest(
            draftRequest.semanticIdentity(),
            objects.handle(),
            requestResourceSet.handle(),
            draftRequest.command(),
            vertexStreamsFor(snapshot, draftRequest.command()),
            resourcePlan,
            sha256Hex(resourcePlan.orderedUses().toString())
        ));
    }

    public static String fallbackReasonFor(VulkanicGalExecutionRequest.GraphicsDrawRequest capturedV1Request) {
        Objects.requireNonNull(capturedV1Request, "capturedV1Request");
        if (!LEGACY_PROGRAM_SLICE_ENABLED) {
            return "disabled";
        }
        Optional<VulkanicCompatibilityState.GraphicsSnapshot> shared =
            capturedV1Request.compatibilitySnapshot().sharedCompatibilityState();
        if (shared.isEmpty()) {
            return "missing-shared-compatibility-snapshot";
        }
        int programId = shared.get().programId();
        if (!isHotLegacyProgram(programId)) {
            return "unsupported-program-" + programId;
        }
        return "unknown";
    }

    public static ExplicitGraphicsObjects requireGraphicsObjects(Handle handle) {
        Objects.requireNonNull(handle, "handle");
        ExplicitGraphicsObjects objects = GRAPHICS_OBJECTS_BY_HANDLE.get(handle);
        if (objects == null) {
            throw new IllegalStateException("Unknown GAL v2 graphics object handle: " + handle);
        }
        return objects;
    }

    public static ResourceSet requireResourceSet(Handle handle) {
        Objects.requireNonNull(handle, "handle");
        ResourceSet resourceSet = RESOURCE_SETS_BY_HANDLE.get(handle);
        if (resourceSet == null) {
            throw new IllegalStateException("Unknown GAL v2 resource-set handle: " + handle);
        }
        return resourceSet;
    }

    public static UniformBinding requireUniformBinding(Handle handle) {
        Objects.requireNonNull(handle, "handle");
        UniformBinding binding = UNIFORM_BINDINGS_BY_HANDLE.get(handle);
        if (binding == null) {
            throw new IllegalStateException("Unknown GAL v2 uniform-binding handle: " + handle);
        }
        return binding;
    }

    public static int graphicsObjectCountForTests() {
        return GRAPHICS_OBJECTS_BY_HANDLE.size();
    }

    public static int resourceSetCountForTests() {
        return RESOURCE_SETS_BY_HANDLE.size();
    }

    public static int uniformBindingCountForTests() {
        return UNIFORM_BINDINGS_BY_HANDLE.size();
    }

    public static boolean supportsLegacyProgramId(int programId) {
        return LEGACY_PROGRAM_SLICE_ENABLED && isHotLegacyProgram(programId);
    }

    public static void clearForTests() {
        GRAPHICS_OBJECTS_BY_KEY.clear();
        GRAPHICS_OBJECTS_BY_HANDLE.clear();
        RESOURCE_LAYOUTS_BY_KEY.clear();
        RESOURCE_LAYOUTS_BY_HANDLE.clear();
        RESOURCE_SETS_BY_KEY.clear();
        RESOURCE_SETS_BY_HANDLE.clear();
        UNIFORM_LAYOUTS_BY_KEY.clear();
        UNIFORM_LAYOUTS_BY_HANDLE.clear();
        UNIFORM_BINDINGS_BY_KEY.clear();
        UNIFORM_BINDINGS_BY_HANDLE.clear();
        HANDLES_BY_SEMANTIC_KEY.clear();
        NEXT_HANDLE_ID.set(1);
    }

    private static ExplicitGraphicsObjects graphicsObjectsFor(
        VulkanicCompatibilityState.GraphicsSnapshot snapshot,
        VulkanicGalExecutionRequest.GraphicsDrawRequest request
    ) {
        return graphicsObjectsFor(snapshot, request, true);
    }

    private static ExplicitGraphicsObjects graphicsObjectsFor(
        VulkanicCompatibilityState.GraphicsSnapshot snapshot,
        VulkanicGalExecutionRequest.GraphicsDrawRequest request,
        boolean includeResourceBindings
    ) {
        List<ResourceLayoutBinding> layoutBindings = includeResourceBindings
            ? v2ResourceLayoutBindings(snapshot)
            : List.of();
        List<ResourceBinding> resourceBindings = includeResourceBindings
            ? v2ResourceBindings(snapshot)
            : List.of();
        ExplicitGraphicsObjectKey key = explicitKey(snapshot, request, includeResourceBindings, layoutBindings);
        ResourceLayout resourceLayout = resourceLayoutFor(key.resourceLayoutKey(), layoutBindings);
        ResourceSet resourceSet = resourceSetFor(resourceLayout.handle(), key.resourceSetKey(), resourceBindings);
        return GRAPHICS_OBJECTS_BY_KEY.computeIfAbsent(key, createdKey -> {
            Handle handle = new Handle(
                ObjectKind.GRAPHICS_OBJECT_SET,
                NEXT_HANDLE_ID.getAndIncrement(),
                createdKey.generation(),
                createdKey.semanticKey()
            );
            ExplicitGraphicsObjects objects = new ExplicitGraphicsObjects(
                handle,
                handleFor(ObjectKind.PROGRAM, createdKey.programKey()),
                handleFor(ObjectKind.PIPELINE, createdKey.pipelineKey()),
                handleFor(ObjectKind.VERTEX_LAYOUT, createdKey.vertexInputKey()),
                resourceLayout.handle(),
                resourceSet.handle(),
                handleFor(ObjectKind.RENDER_TARGET, createdKey.renderTargetKey()),
                createdKey.programKey(),
                createdKey.pipelineKey(),
                createdKey.vertexInputKey(),
                createdKey.resourceLayoutKey(),
                createdKey.resourceSetKey(),
                createdKey.renderTargetKey(),
                vertexLayoutFor(snapshot),
                snapshot
            );
            GRAPHICS_OBJECTS_BY_HANDLE.put(handle, objects);
            return objects;
        });
    }

    private static Handle handleFor(ObjectKind kind, String semanticKey) {
        String key = kind + ":" + semanticKey;
        return HANDLES_BY_SEMANTIC_KEY.computeIfAbsent(key, createdKey -> {
            long generation = Integer.toUnsignedLong(createdKey.hashCode());
            return new Handle(kind, NEXT_HANDLE_ID.getAndIncrement(), generation, createdKey);
        });
    }

    private static ExplicitGraphicsObjectKey explicitKey(
        VulkanicCompatibilityState.GraphicsSnapshot snapshot,
        VulkanicGalExecutionRequest.GraphicsDrawRequest request
    ) {
        return explicitKey(snapshot, request, true, v2ResourceLayoutBindings(snapshot));
    }

    private static ExplicitGraphicsObjectKey explicitKey(
        VulkanicCompatibilityState.GraphicsSnapshot snapshot,
        VulkanicGalExecutionRequest.GraphicsDrawRequest request,
        boolean includeResourceBindings,
        List<ResourceLayoutBinding> layoutBindings
    ) {
        String programKey = "legacy-program:" + snapshot.programId()
            + ":uniform-shape=" + sha256Hex(programUniformShapeKey(snapshot.program()));
        String pipelineKey = "pipeline:" + snapshot.programId()
            + ":mode=" + request.command().mode()
            + ":fixed=" + sha256Hex(snapshot.fixedFunction().toString());
        String vertexInputKey = "vertex-layout:"
            + sha256Hex(vertexLayoutShapeKey(snapshot.vao()));
        String resourceLayoutKey = includeResourceBindings
            ? "resource-layout:" + sha256Hex(resourceLayoutShapeKey(layoutBindings))
            : "resource-layout:none";
        String resourceSetKey = includeResourceBindings
            ? "resource-set:"
                + "program=" + snapshot.programId()
                + ":uniform-shape=" + sha256Hex(programUniformShapeKey(snapshot.program()))
                + ":resourceBindingVersion=" + snapshot.resourceBindingVersion()
            : "resource-set:none";
        String renderTargetKey = "framebuffer:" + snapshot.drawFramebuffer()
            + ":state=" + sha256Hex(framebufferSnapshotKey(snapshot.framebuffer()));
        String semanticKey = String.join("|", programKey, pipelineKey, vertexInputKey, resourceSetKey, renderTargetKey);
        long generation = Integer.toUnsignedLong(semanticKey.hashCode());
        return new ExplicitGraphicsObjectKey(
            programKey,
            pipelineKey,
            vertexInputKey,
            resourceLayoutKey,
            resourceSetKey,
            renderTargetKey,
            semanticKey,
            generation
        );
    }

    private static ResourceLayout resourceLayoutFor(String semanticKey, List<ResourceLayoutBinding> bindings) {
        ResourceLayout cached = RESOURCE_LAYOUTS_BY_KEY.get(semanticKey);
        if (cached != null) {
            VulkanPerfAudit.recordGalV2ResourceLayoutLookup(false, RESOURCE_LAYOUTS_BY_KEY.size());
            return cached;
        }
        final boolean[] created = {false};
        ResourceLayout layout = RESOURCE_LAYOUTS_BY_KEY.computeIfAbsent(semanticKey, key -> {
            created[0] = true;
            Handle handle = handleFor(ObjectKind.RESOURCE_LAYOUT, key);
            ResourceLayout createdLayout = new ResourceLayout(handle, bindings, key);
            RESOURCE_LAYOUTS_BY_HANDLE.put(handle, createdLayout);
            return createdLayout;
        });
        VulkanPerfAudit.recordGalV2ResourceLayoutLookup(created[0], RESOURCE_LAYOUTS_BY_KEY.size());
        return layout;
    }

    private static ResourceSet resourceSetFor(
        Handle layout,
        String semanticKey,
        List<ResourceBinding> bindings
    ) {
        ResourceSet cached = RESOURCE_SETS_BY_KEY.get(semanticKey);
        if (cached != null) {
            VulkanPerfAudit.recordGalV2ResourceSetLookup(false, RESOURCE_SETS_BY_KEY.size());
            return cached;
        }
        final boolean[] created = {false};
        ResourceSet resourceSet = RESOURCE_SETS_BY_KEY.computeIfAbsent(semanticKey, key -> {
            created[0] = true;
            Handle handle = handleFor(ObjectKind.RESOURCE_SET, key);
            ResourceSet createdResourceSet = new ResourceSet(handle, layout, bindings, key);
            RESOURCE_SETS_BY_HANDLE.put(handle, createdResourceSet);
            return createdResourceSet;
        });
        VulkanPerfAudit.recordGalV2ResourceSetLookup(created[0], RESOURCE_SETS_BY_KEY.size());
        return resourceSet;
    }

    private static ResourceSet currentRequestResourceSetFor(VulkanicCompatibilityState.GraphicsSnapshot snapshot) {
        List<ResourceLayoutBinding> layoutBindings = v2ResourceLayoutBindings(snapshot);
        List<ResourceBinding> resourceBindings = v2ResourceBindings(snapshot);
        String layoutKey = "resource-layout:" + sha256Hex(resourceLayoutShapeKey(layoutBindings));
        ResourceLayout resourceLayout = resourceLayoutFor(layoutKey, layoutBindings);
        String resourceSetKey = "resource-set:"
            + "program=" + snapshot.programId()
            + ":uniform-shape=" + sha256Hex(programUniformShapeKey(snapshot.program()))
            + ":resourceBindingVersion=" + snapshot.resourceBindingVersion();
        return resourceSetFor(resourceLayout.handle(), resourceSetKey, resourceBindings);
    }

    private static ResourceLayoutBinding resourceLayoutBindingFor(VulkanicPassResourceModel.BindingSnapshot binding) {
        Optional<VulkanicPassResourceModel.CanonicalResourceReference> reference = binding.resourceReference();
        return new ResourceLayoutBinding(
            binding.name(),
            reference.map(VulkanicPassResourceModel.CanonicalResourceReference::bindingKind)
                .orElse(VulkanicPassResourceModel.BindingKind.BUFFER_RANGE),
            binding.resourceUse().kind(),
            binding.set(),
            binding.binding(),
            reference.flatMap(ref -> optionalIntAsOptional(ref.bindingUnit()))
                .map(OptionalInt::of)
                .orElseGet(OptionalInt::empty)
        );
    }

    private static ResourceBinding resourceBindingFor(VulkanicPassResourceModel.BindingSnapshot binding) {
        return new ResourceBinding(
            binding.name(),
            binding.resourceUse(),
            binding.set(),
            binding.binding(),
            binding.resourceReference(),
            Optional.empty()
        );
    }

    private static List<ResourceLayoutBinding> v2ResourceLayoutBindings(VulkanicCompatibilityState.GraphicsSnapshot snapshot) {
        ArrayList<ResourceLayoutBinding> bindings = new ArrayList<>(snapshot.bindingSnapshots().size() + 1);
        snapshot.bindingSnapshots().stream()
            .map(VulkanicGalV2::resourceLayoutBindingFor)
            .forEach(bindings::add);
        standaloneUniformLayoutBinding(snapshot).ifPresent(bindings::add);
        return List.copyOf(bindings);
    }

    private static List<ResourceBinding> v2ResourceBindings(VulkanicCompatibilityState.GraphicsSnapshot snapshot) {
        ArrayList<ResourceBinding> bindings = new ArrayList<>(snapshot.bindingSnapshots().size() + 1);
        snapshot.bindingSnapshots().stream()
            .map(VulkanicGalV2::resourceBindingFor)
            .forEach(bindings::add);
        standaloneUniformResourceBinding(snapshot).ifPresent(bindings::add);
        return List.copyOf(bindings);
    }

    private static Optional<ResourceLayoutBinding> standaloneUniformLayoutBinding(
        VulkanicCompatibilityState.GraphicsSnapshot snapshot
    ) {
        if (!isHotLegacyProgram(snapshot.programId())) {
            return Optional.empty();
        }
        return Optional.of(new ResourceLayoutBinding(
            VulkanicAPI.generatedStandaloneUniformBlockName(),
            VulkanicPassResourceModel.BindingKind.BUFFER_RANGE,
            VulkanicPassResourceModel.ResourceKind.UNIFORM_BUFFER,
            OptionalInt.empty(),
            OptionalInt.empty(),
            OptionalInt.empty()
        ));
    }

    private static Optional<ResourceBinding> standaloneUniformResourceBinding(
        VulkanicCompatibilityState.GraphicsSnapshot snapshot
    ) {
        if (!isHotLegacyProgram(snapshot.programId())) {
            return Optional.empty();
        }
        String bindingName = VulkanicAPI.generatedStandaloneUniformBlockName();
        UniformLayout layout = uniformLayoutFor(snapshot, bindingName);
        UniformBinding uniformBinding = uniformBindingFor(snapshot, layout);
        VulkanicPassResourceModel.ResourceUse use = VulkanicLegacyCompatibilityAdapter.uniformBufferUse(
            "gal-v2-standalone-uniforms",
            "gal-v2-uniform-binding:" + snapshot.programId() + ":" + bindingName,
            0L,
            Math.max(1L, snapshot.program().uniformsByLocation().size()),
            snapshot.semanticIdentity() + ":standalone-uniforms",
            Integer.MAX_VALUE
        );
        return Optional.of(new ResourceBinding(
            bindingName,
            use,
            OptionalInt.empty(),
            OptionalInt.empty(),
            Optional.empty(),
            Optional.of(uniformBinding.handle())
        ));
    }

    private static UniformLayout uniformLayoutFor(
        VulkanicCompatibilityState.GraphicsSnapshot snapshot,
        String bindingName
    ) {
        String key = "uniform-layout:program=" + snapshot.programId()
            + ":binding=" + bindingName
            + ":shape=" + sha256Hex(programUniformShapeKey(snapshot.program()));
        return UNIFORM_LAYOUTS_BY_KEY.computeIfAbsent(key, createdKey -> {
            Handle handle = handleFor(ObjectKind.UNIFORM_LAYOUT, createdKey);
            UniformLayout layout = new UniformLayout(
                handle,
                bindingName,
                OptionalInt.empty(),
                OptionalInt.empty(),
                standaloneUniformMembers(snapshot.program()),
                createdKey
            );
            UNIFORM_LAYOUTS_BY_HANDLE.put(handle, layout);
            return layout;
        });
    }

    private static UniformBinding uniformBindingFor(
        VulkanicCompatibilityState.GraphicsSnapshot snapshot,
        UniformLayout layout
    ) {
        String key = "uniform-binding:program=" + snapshot.programId()
            + ":layout=" + layout.handle().semanticKey();
        return UNIFORM_BINDINGS_BY_KEY.computeIfAbsent(key, createdKey -> {
            Handle handle = handleFor(ObjectKind.UNIFORM_BINDING, createdKey);
            UniformBinding binding = new UniformBinding(
                handle,
                layout.handle(),
                snapshot.programId(),
                layout.bindingName(),
                createdKey
            );
            UNIFORM_BINDINGS_BY_HANDLE.put(handle, binding);
            return binding;
        });
    }

    private static List<UniformMember> standaloneUniformMembers(VulkanicCompatibilityState.ProgramSnapshot program) {
        ArrayList<UniformMember> members = new ArrayList<>(program.uniformsByLocation().size());
        int[] ordinal = {0};
        program.uniformsByLocation().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> members.add(new UniformMember(
                "location-" + entry.getKey(),
                ordinal[0]++,
                entry.getValue().type(),
                Math.max(entry.getValue().ints().length, entry.getValue().floats().length)
            )));
        return List.copyOf(members);
    }

    private static String programUniformShapeKey(VulkanicCompatibilityState.ProgramSnapshot program) {
        StringBuilder builder = new StringBuilder(128);
        builder.append("program=").append(program.programId()).append(';');
        program.uniformsByLocation().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> builder.append("uniform[")
                .append(entry.getKey())
                .append("]=")
                .append(uniformShapeKey(entry.getValue()))
                .append(';'));
        return builder.toString();
    }

    private static String uniformShapeKey(VulkanicCompatibilityState.UniformValue value) {
        return value.type()
            + ":transpose=" + value.transpose()
            + ":cols=" + value.columns()
            + ":rows=" + value.rows()
            + ":ints=" + value.ints().length
            + ":floats=" + value.floats().length;
    }

    private static VertexLayout vertexLayoutFor(VulkanicCompatibilityState.GraphicsSnapshot snapshot) {
        VulkanicCompatibilityState.VaoSnapshot vao = snapshot.vao();
        java.util.ArrayList<VertexBindingLayout> bindings = new java.util.ArrayList<>(vao.vertexBindings().size());
        vao.vertexBindings().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                VulkanicCompatibilityState.VertexBindingState binding = entry.getValue();
                bindings.add(new VertexBindingLayout(binding.binding(), binding.stride(), binding.divisor()));
            });
        java.util.ArrayList<VertexAttributeLayout> attributes = new java.util.ArrayList<>(vao.enabledAttributes().size());
        Integer previousAttribute = null;
        java.util.ArrayList<Integer> enabledAttributes = new java.util.ArrayList<>(vao.enabledAttributes());
        enabledAttributes.sort(Integer::compareTo);
        for (Integer attributeIndex : enabledAttributes) {
            if (attributeIndex == null || attributeIndex.equals(previousAttribute)) {
                continue;
            }
            previousAttribute = attributeIndex;
            VulkanicCompatibilityState.VertexAttributeState attribute = vao.attributes().get(attributeIndex);
            if (attribute != null) {
                attributes.add(new VertexAttributeLayout(
                    attribute.index(),
                    attribute.binding(),
                    attribute.size(),
                    attribute.type(),
                    attribute.normalized(),
                    attribute.integer(),
                    attribute.relativeOffset(),
                    attribute.divisor()
                ));
            }
        }
        return new VertexLayout(
            bindings,
            attributes,
            vao.defaultAttributes(),
            false
        );
    }

    private static VertexStreamBindings vertexStreamsFor(
        VulkanicCompatibilityState.GraphicsSnapshot snapshot,
        VulkanicGalExecutionRequest.GraphicsDrawCommand command
    ) {
        VulkanicCompatibilityState.VaoSnapshot vao = snapshot.vao();
        java.util.ArrayList<VertexStream> streams = new java.util.ArrayList<>(vao.vertexBindings().size());
        vao.vertexBindings().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                VulkanicCompatibilityState.VertexBindingState binding = entry.getValue();
                streams.add(new VertexStream(
                    binding.binding(),
                    binding.buffer(),
                    binding.offset(),
                    binding.buffer() <= 0
                ));
            });
        Optional<IndexStream> index = Optional.empty();
        if (command.kind() != VulkanicGalExecutionRequest.DrawCommandKind.ARRAYS
            && vao.elementBuffer() > 0) {
            index = Optional.of(new IndexStream(
                vao.elementBuffer(),
                command.indexType(),
                command.indexByteOffset()
            ));
        }
        return new VertexStreamBindings(streams, index);
    }

    private static String vertexLayoutShapeKey(VulkanicCompatibilityState.VaoSnapshot vao) {
        StringBuilder builder = new StringBuilder(256);
        java.util.ArrayList<Integer> enabledAttributes = new java.util.ArrayList<>(vao.enabledAttributes());
        enabledAttributes.sort(Integer::compareTo);
        java.util.ArrayList<Integer> uniqueEnabledAttributes = new java.util.ArrayList<>(enabledAttributes.size());
        Integer previousAttribute = null;
        for (Integer attribute : enabledAttributes) {
            if (attribute == null || attribute.equals(previousAttribute)) {
                continue;
            }
            previousAttribute = attribute;
            uniqueEnabledAttributes.add(attribute);
        }
        builder.append("enabled=").append(uniqueEnabledAttributes)
            .append(';');
        vao.attributes().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> builder.append("attr[")
                .append(entry.getKey())
                .append("]=")
                .append(vertexAttributeShapeKey(entry.getValue()))
                .append(';'));
        vao.vertexBindings().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> builder.append("binding[")
                .append(entry.getKey())
                .append("]=stride:")
                .append(entry.getValue().stride())
                .append(":divisor:")
                .append(entry.getValue().divisor())
                .append(';'));
        return builder.toString();
    }

    private static String vertexAttributeShapeKey(VulkanicCompatibilityState.VertexAttributeState attribute) {
        return "index=" + attribute.index()
            + ":binding=" + attribute.binding()
            + ":size=" + attribute.size()
            + ":type=" + attribute.type()
            + ":normalized=" + attribute.normalized()
            + ":integer=" + attribute.integer()
            + ":relativeOffset=" + attribute.relativeOffset()
            + ":divisor=" + attribute.divisor();
    }

    private static String framebufferSnapshotKey(VulkanicCompatibilityState.FramebufferSnapshot framebuffer) {
        StringBuilder builder = new StringBuilder(128);
        builder.append("framebuffer=").append(framebuffer.framebuffer())
            .append(";drawBuffers=").append(framebuffer.drawBuffers())
            .append(";readBuffer=").append(framebuffer.readBuffer())
            .append(';');
        appendSortedMap(builder, "attachment", framebuffer.attachments());
        return builder.toString();
    }

    private static String resourceLayoutShapeKey(List<ResourceLayoutBinding> bindings) {
        StringBuilder builder = new StringBuilder(512);
        bindings.stream()
            .sorted(Comparator.comparing(ResourceLayoutBinding::name)
                .thenComparing(binding -> binding.resourceKind().name()))
            .forEach(binding -> {
                builder.append("binding[")
                    .append(binding.name())
                    .append("]:kind=")
                    .append(binding.resourceKind())
                    .append(":set=")
                    .append(optionalIntKey(binding.set()))
                    .append(":binding=")
                    .append(optionalIntKey(binding.binding()))
                    .append(":bindKind=")
                    .append(binding.bindingKind())
                    .append(":unit=")
                    .append(optionalIntKey(binding.bindingUnit()))
                    .append(';');
            });
        return builder.toString();
    }

    private static String resourceSetSemanticKey(List<ResourceBinding> bindings) {
        StringBuilder builder = new StringBuilder(1024);
        bindings.stream()
            .sorted(Comparator.comparing(ResourceBinding::name)
                .thenComparing(binding -> binding.resourceUse().role()))
            .forEach(binding -> {
                VulkanicPassResourceModel.ResourceUse use = binding.resourceUse();
                builder.append("binding[")
                    .append(binding.name())
                    .append("]:logical=")
                    .append(use.resource().logicalName())
                    .append(":kind=")
                    .append(use.kind())
                    .append(":stable=")
                    .append(use.resource().stableKey())
                    .append(":access=")
                    .append(use.access())
                    .append(":usage=")
                    .append(use.usage())
                    .append(":feedback=")
                    .append(use.feedbackLoop())
                    .append(":sub=")
                    .append(use.subresource())
                    .append(":set=")
                    .append(optionalIntKey(binding.set()))
                    .append(":binding=")
                    .append(optionalIntKey(binding.binding()));
                binding.uniformBinding().ifPresent(uniform -> builder
                    .append(":uniformBinding=")
                    .append(uniform.semanticKey()));
                binding.resourceReference().ifPresent(reference -> builder
                    .append(":refKind=")
                    .append(reference.bindingKind())
                    .append(":legacyId=")
                    .append(optionalIntKey(reference.legacyId()))
                    .append(":generation=")
                    .append(reference.generation())
                    .append(":legacyTarget=")
                    .append(optionalIntKey(reference.legacyTarget()))
                    .append(":unit=")
                    .append(optionalIntKey(reference.bindingUnit()))
                    .append(":sampler=")
                    .append(optionalIntKey(reference.samplerObject()))
                    .append(":imageAccess=")
                    .append(optionalIntKey(reference.imageAccess()))
                    .append(":imageFormat=")
                    .append(optionalIntKey(reference.imageFormat()))
                    .append(":layered=")
                    .append(reference.layered()));
                builder.append(';');
            });
        return builder.toString();
    }

    private static boolean isHotLegacyProgram(int programId) {
        if (ALL_LEGACY_PROGRAMS_ENABLED) {
            return programId > 0;
        }
        return programId == HOT_LEGACY_PROGRAM_A || programId == HOT_LEGACY_PROGRAM_B;
    }

    private static boolean isSupportedLegacyProgram(int programId, boolean eagerResourceDeclarations) {
        if (!eagerResourceDeclarations) {
            return programId > 0;
        }
        return isHotLegacyProgram(programId);
    }

    private static VulkanicLegacyCompatibilityAdapter.DrawCommandSnapshot drawCommandSnapshot(
        VulkanicGalExecutionRequest.GraphicsDrawCommand command
    ) {
        return switch (command.kind()) {
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
                command.indexedDraws().stream().mapToInt(VulkanicGalExecutionRequest.IndexedDraw::indexCount).sum(),
                command.indexedDraws().get(0).baseVertex(),
                command.instanceCount()
            );
        };
    }

    private static void appendSortedMap(StringBuilder builder, String label, Map<?, ?> map) {
        map.entrySet().stream()
            .sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
            .forEach(entry -> builder.append(label)
                .append('[')
                .append(entry.getKey())
                .append("]=")
                .append(entry.getValue())
                .append(';'));
    }

    private static void appendSortedKeys(StringBuilder builder, String label, Map<?, ?> map) {
        map.keySet().stream()
            .sorted(Comparator.comparing(String::valueOf))
            .forEach(key -> builder.append(label)
                .append('[')
                .append(key)
                .append("];"));
    }

    private record ExplicitGraphicsObjectKey(
        String programKey,
        String pipelineKey,
        String vertexInputKey,
        String resourceLayoutKey,
        String resourceSetKey,
        String renderTargetKey,
        String semanticKey,
        long generation
    ) {
        private ExplicitGraphicsObjectKey {
            programKey = requireNonBlank(programKey, "programKey");
            pipelineKey = requireNonBlank(pipelineKey, "pipelineKey");
            vertexInputKey = requireNonBlank(vertexInputKey, "vertexInputKey");
            resourceLayoutKey = requireNonBlank(resourceLayoutKey, "resourceLayoutKey");
            resourceSetKey = requireNonBlank(resourceSetKey, "resourceSetKey");
            renderTargetKey = requireNonBlank(renderTargetKey, "renderTargetKey");
            semanticKey = requireNonBlank(semanticKey, "semanticKey");
            if (generation < 0L) {
                throw new IllegalArgumentException("generation must be >= 0");
            }
        }
    }

    private static Optional<Integer> optionalIntAsOptional(OptionalInt value) {
        return value.isPresent() ? Optional.of(value.getAsInt()) : Optional.empty();
    }

    private static String optionalIntKey(OptionalInt value) {
        return value.isPresent() ? Integer.toString(value.getAsInt()) : "none";
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(Character.forDigit((b >>> 4) & 0x0f, 16));
                builder.append(Character.forDigit(b & 0x0f, 16));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static Map<Integer, float[]> copyDefaultAttributeMap(Map<Integer, float[]> defaults) {
        java.util.LinkedHashMap<Integer, float[]> copy = new java.util.LinkedHashMap<>();
        defaults.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> copy.put(entry.getKey(), java.util.Arrays.copyOf(entry.getValue(), entry.getValue().length)));
        return Map.copyOf(copy);
    }
}
