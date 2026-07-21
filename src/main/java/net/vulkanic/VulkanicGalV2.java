package net.vulkanic;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
        "ExplicitGraphicsObjects(program,pipeline,vertexLayout,resourceLayout,renderTarget)",
        "ExplicitGraphicsDrawRequest(identity,objects,drawCommand,vertexStreams,resourcePlanFingerprint)"
    );
    public static final String CONTRACT_SCHEMA_FINGERPRINT = sha256Hex(CONTRACT_SCHEMA);

    private static final boolean LEGACY_PROGRAM_SLICE_ENABLED =
        Boolean.parseBoolean(System.getProperty("mattmc.gal.v2.legacyProgramSlice", "true"));
    private static final int HOT_LEGACY_PROGRAM_A =
        Integer.getInteger("mattmc.gal.v2.legacyProgramA", 11);
    private static final int HOT_LEGACY_PROGRAM_B =
        Integer.getInteger("mattmc.gal.v2.legacyProgramB", 9);

    private static final AtomicInteger NEXT_HANDLE_ID = new AtomicInteger(1);
    private static final Map<String, Handle> HANDLES_BY_SEMANTIC_KEY = new ConcurrentHashMap<>();
    private static final Map<ExplicitGraphicsObjectKey, ExplicitGraphicsObjects> GRAPHICS_OBJECTS_BY_KEY =
        new ConcurrentHashMap<>();
    private static final Map<Handle, ExplicitGraphicsObjects> GRAPHICS_OBJECTS_BY_HANDLE =
        new ConcurrentHashMap<>();

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
        Handle renderTarget,
        String programKey,
        String pipelineKey,
        String vertexInputKey,
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
            renderTarget = Objects.requireNonNull(renderTarget, "renderTarget");
            programKey = requireNonBlank(programKey, "programKey");
            pipelineKey = requireNonBlank(pipelineKey, "pipelineKey");
            vertexInputKey = requireNonBlank(vertexInputKey, "vertexInputKey");
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

    public record ExplicitGraphicsDrawRequest(
        VulkanicGalExecutionRequest.SemanticIdentity semanticIdentity,
        Handle graphicsObjects,
        VulkanicGalExecutionRequest.GraphicsDrawCommand command,
        VertexStreamBindings vertexStreams,
        VulkanicCompatibilityState.GraphicsSnapshot compatibilitySnapshot,
        VulkanicPassResourceModel.PassExecutionPlan resourcePlan,
        String resourcePlanFingerprint
    ) {
        public ExplicitGraphicsDrawRequest {
            semanticIdentity = Objects.requireNonNull(semanticIdentity, "semanticIdentity");
            graphicsObjects = Objects.requireNonNull(graphicsObjects, "graphicsObjects");
            command = Objects.requireNonNull(command, "command");
            vertexStreams = Objects.requireNonNull(vertexStreams, "vertexStreams");
            compatibilitySnapshot = Objects.requireNonNull(compatibilitySnapshot, "compatibilitySnapshot");
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
        if (programId != HOT_LEGACY_PROGRAM_A && programId != HOT_LEGACY_PROGRAM_B) {
            return Optional.empty();
        }
        ExplicitGraphicsObjects objects = graphicsObjectsFor(shared.get(), capturedV1Request);
        return Optional.of(new ExplicitGraphicsDrawRequest(
            capturedV1Request.semanticIdentity(),
            objects.handle(),
            capturedV1Request.command(),
            vertexStreamsFor(shared.get(), capturedV1Request.command()),
            shared.get(),
            capturedV1Request.resourcePlan(),
            sha256Hex(capturedV1Request.resourcePlan().orderedUses().toString())
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
        if (programId != HOT_LEGACY_PROGRAM_A && programId != HOT_LEGACY_PROGRAM_B) {
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

    public static int graphicsObjectCountForTests() {
        return GRAPHICS_OBJECTS_BY_HANDLE.size();
    }

    public static void clearForTests() {
        GRAPHICS_OBJECTS_BY_KEY.clear();
        GRAPHICS_OBJECTS_BY_HANDLE.clear();
        HANDLES_BY_SEMANTIC_KEY.clear();
        NEXT_HANDLE_ID.set(1);
    }

    private static ExplicitGraphicsObjects graphicsObjectsFor(
        VulkanicCompatibilityState.GraphicsSnapshot snapshot,
        VulkanicGalExecutionRequest.GraphicsDrawRequest request
    ) {
        ExplicitGraphicsObjectKey key = explicitKey(snapshot, request);
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
                handleFor(ObjectKind.RESOURCE_LAYOUT, createdKey.resourceSetKey()),
                handleFor(ObjectKind.RENDER_TARGET, createdKey.renderTargetKey()),
                createdKey.programKey(),
                createdKey.pipelineKey(),
                createdKey.vertexInputKey(),
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
        String programKey = "legacy-program:" + snapshot.programId()
            + ":uniform-shape=" + sha256Hex(programUniformShapeKey(snapshot.program()));
        String pipelineKey = "pipeline:" + snapshot.programId()
            + ":mode=" + request.command().mode()
            + ":fixed=" + sha256Hex(snapshot.fixedFunction().toString());
        String vertexInputKey = "vertex-layout:"
            + sha256Hex(vertexLayoutShapeKey(snapshot.vao()));
        String resourceSetKey = "resources:"
            + sha256Hex(resourceShapeKey(snapshot));
        String renderTargetKey = "framebuffer:" + snapshot.drawFramebuffer()
            + ":state=" + sha256Hex(framebufferSnapshotKey(snapshot.framebuffer()));
        String semanticKey = String.join("|", programKey, pipelineKey, vertexInputKey, resourceSetKey, renderTargetKey);
        long generation = Integer.toUnsignedLong(semanticKey.hashCode());
        return new ExplicitGraphicsObjectKey(
            programKey,
            pipelineKey,
            vertexInputKey,
            resourceSetKey,
            renderTargetKey,
            semanticKey,
            generation
        );
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

    private static String resourceShapeKey(VulkanicCompatibilityState.GraphicsSnapshot snapshot) {
        StringBuilder builder = new StringBuilder(512);
        appendSortedKeys(builder, "bufferTarget", snapshot.bufferBindings());
        appendSortedKeys(builder, "indexedBuffer", snapshot.indexedBufferBindings());
        appendSortedKeys(builder, "texture2DUnit", snapshot.texture2DByUnit());
        appendSortedKeys(builder, "textureUnit", snapshot.textureUnitBindings());
        appendSortedKeys(builder, "textureKey", snapshot.textureBindingsByKey());
        appendSortedKeys(builder, "samplerUnit", snapshot.samplerBindings());
        snapshot.imageUnitBindings().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> builder.append("imageUnit[")
                .append(entry.getKey())
                .append("]=")
                .append(imageUnitShapeKey(entry.getValue()))
                .append(';'));
        return builder.toString();
    }

    private static String imageUnitShapeKey(VulkanicCompatibilityState.ImageUnitBindingState image) {
        return "level=" + image.level()
            + ":layered=" + image.layered()
            + ":layer=" + image.layer()
            + ":access=" + image.access()
            + ":format=" + image.format();
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
        String resourceSetKey,
        String renderTargetKey,
        String semanticKey,
        long generation
    ) {
        private ExplicitGraphicsObjectKey {
            programKey = requireNonBlank(programKey, "programKey");
            pipelineKey = requireNonBlank(pipelineKey, "pipelineKey");
            vertexInputKey = requireNonBlank(vertexInputKey, "vertexInputKey");
            resourceSetKey = requireNonBlank(resourceSetKey, "resourceSetKey");
            renderTargetKey = requireNonBlank(renderTargetKey, "renderTargetKey");
            semanticKey = requireNonBlank(semanticKey, "semanticKey");
            if (generation < 0L) {
                throw new IllegalArgumentException("generation must be >= 0");
            }
        }
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
