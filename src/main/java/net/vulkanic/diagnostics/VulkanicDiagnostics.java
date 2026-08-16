package net.vulkanic.diagnostics;

import net.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.dev.DeterministicCameraCapture;
import net.minecraft.client.dev.GraphicsFrameBenchmark;
import net.vulkanic.PipelineDescriptor;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.zip.CRC32;
import org.slf4j.Logger;
import net.logging.LogUtils;

/**
 * Owns development-only Vulkanic parity and diagnostic state.
 *
 * <p>This class must stay inert unless the corresponding system properties are
 * enabled. It intentionally stores counters, thread-local semantic draw context,
 * and bounded diagnostic budgets outside production renderer classes.</p>
 */
public final class VulkanicDiagnostics {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String GENERATED_STANDALONE_UNIFORM_BLOCK_NAME = "VulkanicStandaloneUniforms";

    public static final boolean TRACE_STANDALONE_UNIFORMS =
        Boolean.getBoolean("mattmc.vulkan.traceStandaloneUniforms");
    public static final boolean TRACE_SHADER_INPUT_PARITY =
        Boolean.getBoolean("mattmc.vulkan.traceShaderInputParity");
    public static final boolean TRACE_SHADER_INPUT_PARITY_POSE_ONLY =
        Boolean.getBoolean("mattmc.vulkan.traceShaderInputParity.poseOnly");
    public static final boolean TRACE_STANDALONE_UNIFORM_BLOCK_MEMBERS =
        Boolean.getBoolean("mattmc.vulkan.traceStandaloneUniformBlockMembers");
    public static final boolean DEDUPE_STANDALONE_UNIFORM_BLOCK_MEMBER_TRACE =
        Boolean.parseBoolean(System.getProperty("mattmc.vulkan.traceStandaloneUniformBlockMembers.dedupe", "true"));
    public static final boolean TRACE_RENDER_TARGET_CONTENT_HASHES =
        Boolean.getBoolean("mattmc.vulkan.traceRenderTargetContentHashes");
    public static final boolean TRACE_RENDER_TARGET_PRODUCER_HASHES =
        Boolean.getBoolean("mattmc.vulkan.traceRenderTargetProducerHashes");
    public static final boolean TRACE_RENDER_TARGET_SAMPLER_BINDING_HASHES =
        Boolean.getBoolean("mattmc.vulkan.traceRenderTargetSamplerBindingHashes");
    public static final boolean TRACE_IRIS_COLORTEX0_PHASE_HASHES =
        Boolean.getBoolean("mattmc.vulkan.traceIrisColortex0PhaseHashes");
    /** Exact Iris phase to retain as a bounded diagnostic image, or {@code *}. */
    public static final String DUMP_IRIS_COLORTEX0_PHASE =
        System.getProperty("mattmc.vulkan.dumpIrisColortex0Phase", "").trim();
    public static final boolean TRACE_SHADER_INPUT_SAMPLER_CONTENT_HASHES =
        Boolean.getBoolean("mattmc.vulkan.traceShaderInputSamplerContentHashes");
    public static final boolean TRACE_RENDER_TARGET_CONTENT_HASHES_INITIAL_POSE_ONLY =
        Boolean.getBoolean("mattmc.vulkan.traceRenderTargetContentHashes.initialPoseOnly");
    public static final boolean TRACE_SHADER_INPUT_PARITY_GEOMETRY_DETAIL =
        Boolean.getBoolean("mattmc.vulkan.traceShaderInputParity.geometryDetail");
    public static final boolean FORCE_MAPPABLE_GEOMETRY_PARITY_BUFFERS =
        Boolean.getBoolean("mattmc.vulkan.traceShaderInputParity.forceMappableGeometryBuffers");

    public static final int MAX_STANDALONE_UNIFORM_TRACE_LOGS =
        diagnosticLimit("mattmc.vulkan.traceStandaloneUniforms.maxLogs", 512);
    public static final int MAX_RENDER_TARGET_CONTENT_READBACKS =
        diagnosticLimit("mattmc.vulkan.traceRenderTargetContentHashes.maxReadbacks", 8);
    public static final int MAX_SHADER_INPUT_PARITY_LOGS =
        diagnosticLimit("mattmc.vulkan.traceShaderInputParity.maxLogs", 20000);
    public static final int SHADER_INPUT_PARITY_GEOMETRY_MAX_BYTES =
        diagnosticLimit("mattmc.vulkan.traceShaderInputParity.geometryMaxBytes", 2 * 1024 * 1024);
    public static final int TRACE_SHADER_INPUT_PARITY_GEOMETRY_DETAIL_MAX_VERTICES =
        diagnosticLimit("mattmc.vulkan.traceShaderInputParity.geometryDetailMaxVertices", 12);
    public static final int DIAGNOSTIC_GEOMETRY_SHADOW_MAX_BUFFER_BYTES =
        Integer.getInteger("mattmc.vulkan.traceShaderInputParity.geometryShadowMaxBufferBytes", 2 * 1024 * 1024);
    public static final long DIAGNOSTIC_GEOMETRY_SHADOW_MAX_TOTAL_BYTES =
        Long.getLong("mattmc.vulkan.traceShaderInputParity.geometryShadowMaxTotalBytes", 96L * 1024L * 1024L);
    public static final int MAX_STANDALONE_SLICE_TRACE_LOGS =
        Integer.getInteger("mattmc.vulkan.traceStandaloneUniforms.maxLogs", 512);
    public static final int MAX_RENDER_TARGET_PARITY_LOGS =
        Integer.getInteger("mattmc.vulkan.traceRenderTargetParity.maxLogs", 160);

    public static final Set<String> DECODED_STANDALONE_UNIFORM_TRACE_NAMES = Set.of(
        "uProj",
        "uInvProj",
        "uInvMvmProj",
        "uDhInvMvmProj",
        "uMcInvMvmProj",
        "uCameraBlockYPos",
        "frameCounter",
        "frameTime",
        "frameTimeCounter",
        "frameTimeSmooth",
        "dhProjectionInverse",
        "iris_ModelViewMatrix",
        "iris_ProjectionMatrix",
        "shadowModelView",
        "shadowModelViewInverse"
    );

    public static final Set<String> TRACE_SHADER_INPUT_PARITY_GEOMETRY_DETAIL_PIPELINES =
        Arrays.stream(System.getProperty("mattmc.vulkan.traceShaderInputParity.geometryDetailPipeline", "minecraft:pipeline/vignette").split(","))
            .map(String::trim)
            .filter(entry -> !entry.isEmpty())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    public static final AtomicInteger STANDALONE_UNIFORM_TRACE_LOG_COUNT = new AtomicInteger();
    public static final AtomicInteger RENDER_TARGET_CONTENT_READBACK_COUNT = new AtomicInteger();
    public static final Set<String> RENDER_TARGET_CONTENT_READBACK_KEYS =
        java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());
    public static final Set<String> IRIS_COLORTEX0_PHASE_HASH_KEYS =
        java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());
    public static final Set<String> IRIS_COLORTEX0_PHASE_IMAGE_KEYS =
        java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());
    public static final AtomicInteger SHADER_INPUT_PARITY_LOG_COUNT = new AtomicInteger();
    public static final AtomicLong SHADER_INPUT_PARITY_ORDERING_ORDINAL = new AtomicLong();
    public static final ConcurrentMap<Integer, String> SHADER_INPUT_PARITY_PROGRAM_NAMES = new ConcurrentHashMap<>();
    public static final Set<String> STANDALONE_UNIFORM_BLOCK_MEMBER_TRACE_KEYS =
        java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());
    public static final Set<String> STANDALONE_SLICE_TRACE_KEYS = ConcurrentHashMap.newKeySet();
    public static final AtomicLong STANDALONE_UNIFORM_CALL_COUNT = new AtomicLong();
    public static final AtomicLong STANDALONE_UNIFORM_TOKEN_HIT_COUNT = new AtomicLong();
    public static final AtomicLong STANDALONE_UNIFORM_FALLBACK_COUNT = new AtomicLong();
    public static final AtomicLong STANDALONE_UNIFORM_WRITE_COUNT = new AtomicLong();
    public static final AtomicInteger STANDALONE_UNIFORM_STATS_LOG_COUNT = new AtomicInteger();
    public static final AtomicInteger STANDALONE_SLICE_TRACE_LOG_COUNT = new AtomicInteger();
    public static final AtomicInteger STANDALONE_LOOKUP_SAMPLE_PROGRAM = new AtomicInteger(-1);

    private static final AtomicLong DIAGNOSTIC_GEOMETRY_SHADOW_BYTES = new AtomicLong();
    private static final ThreadLocal<SemanticDrawIdentity> SHADER_INPUT_PARITY_SEMANTIC_DRAW =
        new ThreadLocal<>();
    private static final ThreadLocal<java.util.ArrayDeque<String>> SHADER_INPUT_PARITY_SEMANTIC_CONTEXT =
        ThreadLocal.withInitial(java.util.ArrayDeque::new);
    private static final ConcurrentMap<String, AtomicInteger> SHADER_INPUT_PARITY_SEMANTIC_DRAW_ORDINALS =
        new ConcurrentHashMap<>();

    private VulkanicDiagnostics() {
    }

    public static int diagnosticLimit(String property, int defaultValue) {
        int value = Integer.getInteger(property, defaultValue);
        return value < 0 ? defaultValue : value;
    }

    public static boolean shouldDumpIrisColortex0Phase(String phase) {
        return !DUMP_IRIS_COLORTEX0_PHASE.isEmpty()
            && ("*".equals(DUMP_IRIS_COLORTEX0_PHASE) || DUMP_IRIS_COLORTEX0_PHASE.equals(phase));
    }

    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }

    private static final Scope NO_SCOPE = () -> {
    };

    public record SemanticDrawIdentity(
        String key,
        String subsystem,
        String phase,
        String pass,
        String pipeline,
        String vertexShader,
        String fragmentShader,
        String material,
        String output,
        int ordinal
    ) {
        public String fields() {
            return "semanticDrawKey=" + key
                + " semanticSubsystem=" + subsystem
                + " semanticPhase=" + phase
                + " semanticPass=" + pass
                + " semanticPipeline=" + pipeline
                + " semanticVertexShader=" + vertexShader
                + " semanticFragmentShader=" + fragmentShader
                + " semanticMaterial=" + material
                + " semanticOutput=" + output
                + " semanticOrdinal=" + ordinal;
        }
    }

    public static boolean shouldTraceStandaloneUniforms() {
        return TRACE_STANDALONE_UNIFORMS
            && STANDALONE_UNIFORM_TRACE_LOG_COUNT.incrementAndGet() <= MAX_STANDALONE_UNIFORM_TRACE_LOGS;
    }

    public static boolean shouldTraceStandaloneUniform(String name) {
        return GENERATED_STANDALONE_UNIFORM_BLOCK_NAME.equals(name) && shouldTraceStandaloneUniforms();
    }

    public static boolean shouldTraceStandaloneUniformBlockMembers() {
        return TRACE_SHADER_INPUT_PARITY && TRACE_STANDALONE_UNIFORM_BLOCK_MEMBERS;
    }

    public static void registerProgramName(int program, String name) {
        if (program > 0 && name != null && !name.isBlank()) {
            SHADER_INPUT_PARITY_PROGRAM_NAMES.put(program, name);
        }
    }

    public static String programIdentity(int program, @Nullable String explicitIdentity) {
        if (explicitIdentity != null && !explicitIdentity.isBlank()) {
            return sanitizeLabel(explicitIdentity);
        }
        String registeredName = SHADER_INPUT_PARITY_PROGRAM_NAMES.get(program);
        if (registeredName != null && !registeredName.isBlank()) {
            return sanitizeLabel(registeredName);
        }
        return "program:" + program;
    }

    public static Scope pushSemanticContext(@Nullable String context) {
        if (!TRACE_SHADER_INPUT_PARITY || context == null || context.isBlank()) {
            return NO_SCOPE;
        }
        java.util.ArrayDeque<String> stack = SHADER_INPUT_PARITY_SEMANTIC_CONTEXT.get();
        stack.push(valueOrUnknown(context));
        return () -> {
            java.util.ArrayDeque<String> currentStack = SHADER_INPUT_PARITY_SEMANTIC_CONTEXT.get();
            if (!currentStack.isEmpty()) {
                currentStack.pop();
            }
            if (currentStack.isEmpty()) {
                SHADER_INPUT_PARITY_SEMANTIC_CONTEXT.remove();
            }
        };
    }

    public static String currentSemanticContext() {
        java.util.ArrayDeque<String> stack = SHADER_INPUT_PARITY_SEMANTIC_CONTEXT.get();
        return stack.isEmpty() ? "" : stack.peek();
    }

    public static Scope beginSemanticDraw(
        String backend,
        String source,
        String subsystem,
        String phase,
        @Nullable String pass,
        @Nullable RenderPipeline renderPipeline,
        @Nullable PipelineDescriptor descriptor,
        @Nullable String material,
        @Nullable String output,
        boolean indexed,
        int firstVertex,
        int vertexCount,
        int firstIndex,
        int indexCount,
        int instanceCount,
        int baseVertex
    ) {
        if (!TRACE_SHADER_INPUT_PARITY) {
            return NO_SCOPE;
        }

        SemanticDrawIdentity previous = SHADER_INPUT_PARITY_SEMANTIC_DRAW.get();
        PipelineDescriptor.PortableState portableState = descriptor != null ? descriptor.getPortableState() : null;
        String normalizedSubsystem = valueOrUnknown(subsystem);
        if (previous != null
            && "sodium-terrain".equals(previous.subsystem())
            && "blaze3d-renderpass".equals(normalizedSubsystem)) {
            return NO_SCOPE;
        }
        String normalizedPass = valueOrUnknown(pass);
        if ("blaze3d-renderpass".equals(normalizedSubsystem) && normalizedPass.startsWith("extent=")) {
            normalizedPass = "legacy-renderpass";
        }
        String pipeline = pipelineLocation(renderPipeline, portableState);
        String vertexShader = vertexShader(renderPipeline, portableState);
        String fragmentShader = fragmentShader(renderPipeline, portableState);
        String normalizedMaterial = valueOrUnknown(material != null ? material : pipeline);
        String semanticContext = currentSemanticContext();
        if (!semanticContext.isEmpty()) {
            normalizedPass = normalizedPass + ":ctx=" + semanticContext;
            normalizedMaterial = normalizedMaterial + ":ctx=" + semanticContext;
        }
        String normalizedOutput = normalizeSemanticOutput(output);
        String poseContext = DeterministicCameraCapture.shaderInputParityContextFields().replace(' ', '|');
        String ordinalKey = String.join("|",
            normalizedSubsystem,
            phase,
            normalizedPass,
            pipeline,
            normalizedMaterial,
            normalizedOutput,
            poseContext
        );
        int ordinal = SHADER_INPUT_PARITY_SEMANTIC_DRAW_ORDINALS
            .computeIfAbsent(ordinalKey, ignored -> new AtomicInteger())
            .incrementAndGet();
        SemanticDrawIdentity identity = new SemanticDrawIdentity(
            hashString(ordinalKey + "|ordinal=" + ordinal),
            normalizedSubsystem,
            phase,
            normalizedPass,
            pipeline,
            vertexShader,
            fragmentShader,
            normalizedMaterial,
            normalizedOutput,
            ordinal
        );
        SHADER_INPUT_PARITY_SEMANTIC_DRAW.set(identity);
        if (shouldTraceShaderInputParityLog()) {
            LOGGER.info(
                "ShaderInputParitySemanticDraw backend={} source={} {} indexed={} firstVertex={} vertexCount={} firstIndex={} indexCount={} instanceCount={} baseVertex={} {}",
                backend,
                source,
                identity.fields(),
                indexed,
                firstVertex,
                vertexCount,
                firstIndex,
                indexCount,
                instanceCount,
                baseVertex,
                DeterministicCameraCapture.shaderInputParityContextFields()
            );
        }
        return () -> {
            if (previous == null) {
                SHADER_INPUT_PARITY_SEMANTIC_DRAW.remove();
            } else {
                SHADER_INPUT_PARITY_SEMANTIC_DRAW.set(previous);
            }
        };
    }

    public static String currentSemanticDrawContextFields() {
        SemanticDrawIdentity identity = SHADER_INPUT_PARITY_SEMANTIC_DRAW.get();
        return identity == null
            ? "semanticDrawKey=unavailable semanticSubsystem=unknown semanticPhase=unknown semanticPass=unknown semanticPipeline=unknown semanticVertexShader=unknown semanticFragmentShader=unknown semanticMaterial=unknown semanticOutput=unknown semanticOrdinal=0"
            : identity.fields();
    }

    public static String currentSemanticDrawKeyOrUnavailable() {
        SemanticDrawIdentity identity = SHADER_INPUT_PARITY_SEMANTIC_DRAW.get();
        return identity == null ? "unavailable" : identity.key();
    }

    public static @Nullable String currentSemanticPipeline() {
        SemanticDrawIdentity identity = SHADER_INPUT_PARITY_SEMANTIC_DRAW.get();
        return identity == null ? null : identity.pipeline();
    }

    public static boolean shouldTraceShaderInputParityLog() {
        if (!shouldCollectShaderInputParityDiagnostics()) {
            return false;
        }
        return SHADER_INPUT_PARITY_LOG_COUNT.incrementAndGet() <= MAX_SHADER_INPUT_PARITY_LOGS;
    }

    public static boolean shouldCollectShaderInputParityDiagnostics() {
        if (!TRACE_SHADER_INPUT_PARITY) {
            return false;
        }
        if (TRACE_SHADER_INPUT_PARITY_POSE_ONLY) {
            if (!DeterministicCameraCapture.isReadyForShaderInputParityPoseDiagnostics()) {
                return false;
            }
            String poseName = DeterministicCameraCapture.currentPoseNameForDiagnostics();
            if ("none".equals(poseName) || "complete".equals(poseName)) {
                return false;
            }
        }
        return SHADER_INPUT_PARITY_LOG_COUNT.get() < MAX_SHADER_INPUT_PARITY_LOGS;
    }

    public static boolean shouldTraceStandaloneUniformBlockMember(
        String backend,
        String source,
        int program,
        @Nullable String programIdentity,
        @Nullable String shaderStages,
        @Nullable String name,
        String valueKind,
        int offset,
        int arraySize,
        int stride,
        String payloadHash,
        String phase,
        String deterministicFields
    ) {
        if (DEDUPE_STANDALONE_UNIFORM_BLOCK_MEMBER_TRACE) {
            String key = backend
                + "|source=" + valueOrUnknown(source)
                + "|program=" + programIdentity(program, programIdentity)
                + "|stages=" + valueOrUnknown(shaderStages)
                + "|phase=" + phase
                + "|name=" + sanitizeUniformName(name)
                + "|kind=" + valueKind
                + "|offset=" + offset
                + "|array=" + arraySize
                + "|stride=" + stride
                + "|payload=" + payloadHash
                + "|" + deterministicFields;
            if (!STANDALONE_UNIFORM_BLOCK_MEMBER_TRACE_KEYS.add(key)) {
                return false;
            }
        }
        return shouldTraceShaderInputParityLog();
    }

    public static boolean reserveGeometryShadowBytes(int size) {
        if (size <= 0) {
            return true;
        }
        while (true) {
            long current = DIAGNOSTIC_GEOMETRY_SHADOW_BYTES.get();
            long next = current + size;
            if (next > DIAGNOSTIC_GEOMETRY_SHADOW_MAX_TOTAL_BYTES) {
                return false;
            }
            if (DIAGNOSTIC_GEOMETRY_SHADOW_BYTES.compareAndSet(current, next)) {
                return true;
            }
        }
    }

    public static void releaseGeometryShadowBytes(int size) {
        if (size > 0) {
            DIAGNOSTIC_GEOMETRY_SHADOW_BYTES.addAndGet(-size);
        }
    }

    public static long geometryShadowBytesForTests() {
        return DIAGNOSTIC_GEOMETRY_SHADOW_BYTES.get();
    }

    public static String sanitizeUniformName(@Nullable String name) {
        if (name == null || name.isBlank()) {
            return "unknown";
        }
        return name.replaceAll("[^A-Za-z0-9_.$:-]", "_");
    }

    public static String valueOrUnknown(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return sanitizeLabel(value);
    }

    public static String sanitizeLabel(String value) {
        return value
            .replace(',', ';')
            .replace('{', '(')
            .replace('}', ')')
            .replace('[', '(')
            .replace(']', ')')
            .replace('"', '\'')
            .replace(' ', '_');
    }

    public static String hash(ByteBuffer data, int length) {
        CRC32 crc32 = new CRC32();
        for (int index = 0; index < length; index++) {
            crc32.update(data.get(index) & 0xFF);
        }
        return "crc32:" + Long.toHexString(crc32.getValue()) + "/bytes:" + length;
    }

    public static String hashString(String value) {
        ByteBuffer bytes = ByteBuffer.wrap(value.getBytes(StandardCharsets.UTF_8));
        return hash(bytes, bytes.remaining());
    }

    public static long nextOrderingOrdinal() {
        return SHADER_INPUT_PARITY_ORDERING_ORDINAL.incrementAndGet();
    }

    public static String pipelineLocation(@Nullable RenderPipeline renderPipeline, @Nullable PipelineDescriptor.PortableState portableState) {
        if (portableState != null) {
            return sanitizeLabel(portableState.location().toString());
        }
        if (renderPipeline != null) {
            return sanitizeLabel(renderPipeline.getLocation().toString());
        }
        return "unknown";
    }

    public static String vertexShader(@Nullable RenderPipeline renderPipeline, @Nullable PipelineDescriptor.PortableState portableState) {
        if (portableState != null) {
            return sanitizeLabel(portableState.vertexShader().toString());
        }
        if (renderPipeline != null) {
            return sanitizeLabel(renderPipeline.getVertexShader().toString());
        }
        return "unknown";
    }

    public static String fragmentShader(@Nullable RenderPipeline renderPipeline, @Nullable PipelineDescriptor.PortableState portableState) {
        if (portableState != null) {
            return sanitizeLabel(portableState.fragmentShader().toString());
        }
        if (renderPipeline != null) {
            return sanitizeLabel(renderPipeline.getFragmentShader().toString());
        }
        return "unknown";
    }

    public static String normalizeSemanticOutput(@Nullable String output) {
        String normalized = valueOrUnknown(output);
        if (normalized.equals("framebuffer")
            || normalized.equals("framebuffer-or-texture-view")
            || normalized.startsWith("framebuffer:")
            || normalized.startsWith("extent=")) {
            return "legacy-framebuffer";
        }
        return normalized.replaceAll("\\btex=\\d+", "tex=<id>");
    }

    public static boolean shouldTraceGeometryDetail() {
        if (!TRACE_SHADER_INPUT_PARITY_GEOMETRY_DETAIL) {
            return false;
        }
        String pipeline = currentSemanticPipeline();
        if (pipeline == null) {
            return TRACE_SHADER_INPUT_PARITY_GEOMETRY_DETAIL_PIPELINES.isEmpty()
                || TRACE_SHADER_INPUT_PARITY_GEOMETRY_DETAIL_PIPELINES.contains("*");
        }
        return TRACE_SHADER_INPUT_PARITY_GEOMETRY_DETAIL_PIPELINES.isEmpty()
            || TRACE_SHADER_INPUT_PARITY_GEOMETRY_DETAIL_PIPELINES.contains("*")
            || TRACE_SHADER_INPUT_PARITY_GEOMETRY_DETAIL_PIPELINES.contains(pipeline);
    }

	public static void recordSubmittedWorkIdentity(String family, String identity) {
		if (TRACE_SHADER_INPUT_PARITY) {
			DeterministicCameraCapture.recordSubmittedWorkIdentity(family, identity);
		}
		GraphicsFrameBenchmark.recordSubmittedWorkIdentity(family, identity);
	}

    public static boolean defaultDiagnosticsDisabledForTests() {
        return !TRACE_STANDALONE_UNIFORMS
            && !TRACE_SHADER_INPUT_PARITY
            && !TRACE_RENDER_TARGET_CONTENT_HASHES
            && !TRACE_SHADER_INPUT_SAMPLER_CONTENT_HASHES;
    }

    public static boolean reservePerBufferGeometryShadowForTests(int size) {
        return size <= DIAGNOSTIC_GEOMETRY_SHADOW_MAX_BUFFER_BYTES && reserveGeometryShadowBytes(size);
    }

    public static void resetMutableStateForTests() {
        STANDALONE_UNIFORM_TRACE_LOG_COUNT.set(0);
        RENDER_TARGET_CONTENT_READBACK_COUNT.set(0);
        RENDER_TARGET_CONTENT_READBACK_KEYS.clear();
        IRIS_COLORTEX0_PHASE_HASH_KEYS.clear();
        IRIS_COLORTEX0_PHASE_IMAGE_KEYS.clear();
        SHADER_INPUT_PARITY_LOG_COUNT.set(0);
        SHADER_INPUT_PARITY_ORDERING_ORDINAL.set(0);
        SHADER_INPUT_PARITY_PROGRAM_NAMES.clear();
        STANDALONE_UNIFORM_BLOCK_MEMBER_TRACE_KEYS.clear();
        STANDALONE_SLICE_TRACE_KEYS.clear();
        STANDALONE_UNIFORM_CALL_COUNT.set(0);
        STANDALONE_UNIFORM_TOKEN_HIT_COUNT.set(0);
        STANDALONE_UNIFORM_FALLBACK_COUNT.set(0);
        STANDALONE_UNIFORM_WRITE_COUNT.set(0);
        STANDALONE_UNIFORM_STATS_LOG_COUNT.set(0);
        STANDALONE_SLICE_TRACE_LOG_COUNT.set(0);
        STANDALONE_LOOKUP_SAMPLE_PROGRAM.set(-1);
        DIAGNOSTIC_GEOMETRY_SHADOW_BYTES.set(0);
        SHADER_INPUT_PARITY_SEMANTIC_DRAW.remove();
        SHADER_INPUT_PARITY_SEMANTIC_CONTEXT.remove();
        SHADER_INPUT_PARITY_SEMANTIC_DRAW_ORDINALS.clear();
    }
}
