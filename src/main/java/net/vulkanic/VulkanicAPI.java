package net.vulkanic;

import net.blaze3d.ProjectionType;
import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.buffers.GpuBufferSlice;
import net.blaze3d.pipeline.CompiledRenderPipeline;
import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.platform.GLX;
import net.blaze3d.shaders.ShaderType;
import net.blaze3d.systems.CommandEncoder;
import net.blaze3d.systems.GpuDevice;
import net.blaze3d.systems.RenderPass;
import net.blaze3d.systems.ScissorState;
import net.blaze3d.textures.GpuTexture;
import net.blaze3d.textures.GpuTextureView;
import net.blaze3d.textures.TextureFormat;
import net.blaze3d.vertex.VertexFormat;
import net.blaze3d.vertex.VertexFormatElement;
import net.minecraft.Util;
import net.minecraft.client.dev.DeterministicCameraCapture;
import net.minecraft.client.renderer.DynamicUniforms;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.TimeSource.NanoTimeSource;
import net.vulkanic.backends.opengl.OpenGLBackend;
import net.vulkanic.backends.vulkan.VulkanBackend;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.glfw.GLFWErrorCallbackI;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

/**
 * Main entry point for the Vulkanic Graphics Abstraction Layer.
 * Provides a unified API for graphics operations that can be backed by different graphics APIs.
 */
public class VulkanicAPI {
    private static final String LWJGL_STACK_SIZE_PROPERTY = "org.lwjgl.system.stackSize";
    private static final String GENERATED_STANDALONE_UNIFORM_BLOCK_NAME = "VulkanicStandaloneUniforms";
    private static final org.slf4j.Logger LOGGER = net.logging.LogUtils.getLogger();
    private static final boolean TRACE_STANDALONE_UNIFORMS = Boolean.getBoolean("mattmc.vulkan.traceStandaloneUniforms");
    private static final int MAX_STANDALONE_UNIFORM_TRACE_LOGS =
        diagnosticLimit("mattmc.vulkan.traceStandaloneUniforms.maxLogs", 512);
    private static final java.util.concurrent.atomic.AtomicInteger STANDALONE_UNIFORM_TRACE_LOG_COUNT = new java.util.concurrent.atomic.AtomicInteger();
    private static final boolean TRACE_SHADER_INPUT_PARITY = Boolean.getBoolean("mattmc.vulkan.traceShaderInputParity");
    private static final boolean TRACE_SHADER_INPUT_PARITY_POSE_ONLY =
        Boolean.getBoolean("mattmc.vulkan.traceShaderInputParity.poseOnly");
    private static final java.util.Set<String> DECODED_STANDALONE_UNIFORM_TRACE_NAMES = java.util.Set.of(
        "uProj",
        "uInvProj",
        "uInvMvmProj",
        "uDhInvMvmProj",
        "uMcInvMvmProj",
        "uCameraBlockYPos",
        "dhProjectionInverse",
        "iris_ModelViewMatrix",
        "iris_ProjectionMatrix",
        "shadowModelView",
        "shadowModelViewInverse"
    );
    private static final boolean TRACE_STANDALONE_UNIFORM_BLOCK_MEMBERS =
        Boolean.getBoolean("mattmc.vulkan.traceStandaloneUniformBlockMembers");
    private static final boolean TRACE_RENDER_TARGET_CONTENT_HASHES =
        Boolean.getBoolean("mattmc.vulkan.traceRenderTargetContentHashes");
    private static final boolean TRACE_RENDER_TARGET_PRODUCER_HASHES =
        Boolean.getBoolean("mattmc.vulkan.traceRenderTargetProducerHashes");
    private static final boolean TRACE_RENDER_TARGET_SAMPLER_BINDING_HASHES =
        Boolean.getBoolean("mattmc.vulkan.traceRenderTargetSamplerBindingHashes");
    private static final boolean TRACE_IRIS_COLORTEX0_PHASE_HASHES =
        Boolean.getBoolean("mattmc.vulkan.traceIrisColortex0PhaseHashes");
    private static final boolean TRACE_SHADER_INPUT_SAMPLER_CONTENT_HASHES =
        Boolean.getBoolean("mattmc.vulkan.traceShaderInputSamplerContentHashes");
    private static final int MAX_RENDER_TARGET_CONTENT_READBACKS =
        diagnosticLimit("mattmc.vulkan.traceRenderTargetContentHashes.maxReadbacks", 8);
    private static final boolean TRACE_RENDER_TARGET_CONTENT_HASHES_INITIAL_POSE_ONLY =
        Boolean.getBoolean("mattmc.vulkan.traceRenderTargetContentHashes.initialPoseOnly");
    private static final java.util.concurrent.atomic.AtomicInteger RENDER_TARGET_CONTENT_READBACK_COUNT =
        new java.util.concurrent.atomic.AtomicInteger();
    private static final java.util.Set<String> RENDER_TARGET_CONTENT_READBACK_KEYS =
        java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private static final java.util.Set<String> IRIS_COLORTEX0_PHASE_HASH_KEYS =
        java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private static final int MAX_SHADER_INPUT_PARITY_LOGS =
        diagnosticLimit("mattmc.vulkan.traceShaderInputParity.maxLogs", 20000);
    private static final java.util.concurrent.atomic.AtomicInteger SHADER_INPUT_PARITY_LOG_COUNT = new java.util.concurrent.atomic.AtomicInteger();
    private static final java.util.concurrent.atomic.AtomicLong SHADER_INPUT_PARITY_ORDERING_ORDINAL =
        new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.ConcurrentMap<Integer, String> SHADER_INPUT_PARITY_PROGRAM_NAMES =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final ThreadLocal<ShaderInputParitySemanticDrawIdentity> SHADER_INPUT_PARITY_SEMANTIC_DRAW =
        new ThreadLocal<>();
    private static final java.util.concurrent.ConcurrentMap<String, java.util.concurrent.atomic.AtomicInteger> SHADER_INPUT_PARITY_SEMANTIC_DRAW_ORDINALS =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final int SHADER_INPUT_PARITY_GEOMETRY_MAX_BYTES =
        diagnosticLimit("mattmc.vulkan.traceShaderInputParity.geometryMaxBytes", 2 * 1024 * 1024);
    private static final boolean TRACE_SHADER_INPUT_PARITY_GEOMETRY_DETAIL =
        Boolean.getBoolean("mattmc.vulkan.traceShaderInputParity.geometryDetail");
    private static final String TRACE_SHADER_INPUT_PARITY_GEOMETRY_DETAIL_PIPELINE =
        System.getProperty("mattmc.vulkan.traceShaderInputParity.geometryDetailPipeline", "minecraft:pipeline/vignette");
    private static final java.util.Set<String> TRACE_SHADER_INPUT_PARITY_GEOMETRY_DETAIL_PIPELINES =
        java.util.Arrays.stream(TRACE_SHADER_INPUT_PARITY_GEOMETRY_DETAIL_PIPELINE.split(","))
            .map(String::trim)
            .filter(entry -> !entry.isEmpty())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    private static final int TRACE_SHADER_INPUT_PARITY_GEOMETRY_DETAIL_MAX_VERTICES =
        diagnosticLimit("mattmc.vulkan.traceShaderInputParity.geometryDetailMaxVertices", 12);
    private static final int VULKAN_LWJGL_STACK_SIZE_KB = 512;
    private static GraphicsBackend backend;
    @Nullable
    private static VulkanBackend rawVulkanBackend;
    @Nullable
    private static ScopedCompositeColortex0Binding scopedCompositeColortex0Binding;
    private static final java.util.Set<String> SCOPED_COMPOSITE_COLORTEX0_EMITTED =
        java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private static final java.util.concurrent.ConcurrentMap<Integer, java.util.List<DiagnosticIrisColorAttachment>> DIAGNOSTIC_IRIS_FRAMEBUFFER_ATTACHMENTS =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentMap<Integer, DiagnosticIrisColorAttachment> DIAGNOSTIC_IRIS_TEXTURE_ATTACHMENTS =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentMap<String, ScopedCompositeColortex0Producer> SCOPED_COMPOSITE_COLORTEX0_PRODUCERS =
        new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentMap<String, PendingScopedCompositeColortex0SamplerReadback> PENDING_SCOPED_COMPOSITE_COLORTEX0_SAMPLER_READBACKS =
        new java.util.concurrent.ConcurrentHashMap<>();
    @Nullable
    private static volatile DiagnosticViewportState diagnosticLastViewport;

    private static final boolean IS_MACOS = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("mac");
    @Nullable
    private static Thread renderThread;
    @Nullable
    private static GpuDevice device;
    private static volatile long registeredGlfwWindowHandleForVulkanSurface;
    private static final ThreadLocal<java.util.ArrayDeque<CommandContext>> CONTEXT_STACK = ThreadLocal.withInitial(java.util.ArrayDeque::new);
    private static ProjectionType projectionType = ProjectionType.PERSPECTIVE;
    private static ProjectionType savedProjectionType = ProjectionType.PERSPECTIVE;
    @Nullable
    private static GpuBufferSlice projectionMatrixBuffer;
    @Nullable
    private static GpuBufferSlice savedProjectionMatrixBuffer;
    private static final java.util.Map<GpuBufferSlice, String> projectionMatrixLabels =
        java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static final Matrix4fStack modelViewStack = new Matrix4fStack(16);
    private static Matrix4f textureMatrix = new Matrix4f();
    private static float shaderLineWidth = 1.0F;
    private static double lastDrawTime = Double.MIN_VALUE;
    private static final AtomicLong pollEventsWaitStart = new AtomicLong();
    private static final AtomicBoolean pollingEvents = new AtomicBoolean(false);
    private static final ScissorState scissorStateForRenderTypeDraws = new ScissorState();
    private static final VulkanicAPI.AutoStorageIndexBuffer sharedSequential = new VulkanicAPI.AutoStorageIndexBuffer(1, 1, it.unimi.dsi.fastutil.ints.IntConsumer::accept);
    private static final VulkanicAPI.AutoStorageIndexBuffer sharedSequentialQuad = new VulkanicAPI.AutoStorageIndexBuffer(4, 6, (intConsumer, i) -> {
        intConsumer.accept(i);
        intConsumer.accept(i + 1);
        intConsumer.accept(i + 2);
        intConsumer.accept(i + 2);
        intConsumer.accept(i + 3);
        intConsumer.accept(i);
    });

    private static int diagnosticLimit(String property, int defaultValue) {
        int value = Integer.getInteger(property, defaultValue);
        if (value < 0) {
            LOGGER.warn("Ignoring negative diagnostic limit {}={}; using {}", property, value, defaultValue);
            return defaultValue;
        }
        return value;
    }

    private record ScopedCompositeColortex0Binding(
        PipelineHandle pipeline,
        String pipelineLocation,
        String vertexShader,
        String fragmentShader,
        String pipelineKey,
        String stableKey,
        String resourceName,
        int resourceSet,
        int resourceBinding,
        String resourceType,
        java.util.List<String> stages,
        int samplerUnit,
        @Nullable Object samplerObject,
        @Nullable VulkanicTexture texture,
        int baseMipLevel,
        int mipLevelCount,
        int legacyTextureId,
        String source
    ) {}

    private record DiagnosticIrisColorAttachment(
        int framebuffer,
        int colorAttachment,
        int logicalIndex,
        int textureId,
        String logicalName,
        String pingPong,
        String source
    ) {}

    private record ScopedCompositeColortex0Producer(
        String backend,
        String source,
        String passLabel,
        String customPassName,
        String pipelineLocation,
        String physicalKey,
        int textureId,
        String logicalAttachment,
        int colorAttachment,
        String pingPong,
        String descriptorSignature,
        String attachmentUsage,
        String lifecycleInfo,
        String poseName,
        String deterministicFields,
        DiagnosticTextureContentHash hash
    ) {}

    private record PendingScopedCompositeColortex0SamplerReadback(
        String backend,
        String customPassName,
        String pipelineLocation,
        String vertexShader,
        String fragmentShader,
        String pipelineKey,
        String stableKey,
        String resourceName,
        int textureUnit,
        @Nullable Object samplerObject,
        VulkanicTextureView textureView,
        int legacyTextureId,
        String physicalKey,
        String outputLogical,
        String outputPingPong,
        int colorAttachment,
        int outputTextureId,
        String renderTarget,
        String attachmentUsage,
        String draw,
        String vertexInput,
        String pipelineState,
        String viewport,
        String scissor,
        String poseName,
        String deterministicFields
    ) {}

    private record DiagnosticProducerAttachment(
        int colorAttachment,
        int textureId,
        String logicalName,
        String pingPong,
        String usage
    ) {}

    private record DiagnosticViewportState(int x, int y, int width, int height) {
        private String describe() {
            return x + "," + y + "," + width + "," + height;
        }
    }

    public static VulkanicDrawStateSnapshot.ViewportStateSnapshot drawStateParityViewportSnapshot() {
        DiagnosticViewportState viewport = diagnosticLastViewport;
        if (viewport == null) {
            return VulkanicDrawStateSnapshot.ViewportStateSnapshot.unknown();
        }
        return new VulkanicDrawStateSnapshot.ViewportStateSnapshot(
            true,
            viewport.x(),
            viewport.y(),
            viewport.width(),
            viewport.height()
        );
    }

    public record DiagnosticTextureContentHash(
        String logicalResource,
        int width,
        int height,
        VulkanicTextureFormat storageFormat,
        String canonicalFormat,
        int mip,
        int layer,
        String originConvention,
        String channelInterpretation,
        String hash,
        String tileHashes
    ) {
        public static DiagnosticTextureContentHash unavailable(
            String logicalResource,
            @Nullable VulkanicTexture texture,
            @Nullable VulkanicTextureView textureView,
            String reason
        ) {
            VulkanicTextureFormat format = texture == null ? null : texture.getVulkanicFormat();
            int width = textureView == null ? -1 : safeTextureViewWidth(textureView);
            int height = textureView == null ? -1 : safeTextureViewHeight(textureView);
            int mip = textureView == null ? 0 : textureView.getBaseMipLevel();
            return new DiagnosticTextureContentHash(
                shaderInputParitySanitizeLabel(logicalResource),
                width,
                height,
                format,
                "unavailable",
                mip,
                0,
                "unavailable",
                "unavailable",
                "unavailable:" + shaderInputParitySanitizeLabel(reason),
                ""
            );
        }
    }
    private static final VulkanicAPI.AutoStorageIndexBuffer sharedSequentialLines = new VulkanicAPI.AutoStorageIndexBuffer(4, 6, (intConsumer, i) -> {
        intConsumer.accept(i);
        intConsumer.accept(i + 1);
        intConsumer.accept(i + 2);
        intConsumer.accept(i + 3);
        intConsumer.accept(i + 2);
        intConsumer.accept(i + 1);
    });
    private static int readFramebufferBinding;
    private static int drawFramebufferBinding;
    private static final java.util.ArrayDeque<GpuAsyncTask> PENDING_FENCED_TASKS = new java.util.ArrayDeque<>();
    @Nullable
    private static GpuBufferSlice shaderFog;
    @Nullable
    private static GpuBufferSlice shaderLightDirections;
    @Nullable
    private static GpuBuffer globalSettingsUniform;
    @Nullable
    private static GpuTextureView outputColorTextureOverride;
    @Nullable
    private static GpuTextureView outputDepthTextureOverride;
    @Nullable
    private static DynamicUniforms dynamicUniforms;
    @Nullable
    private static Runnable fogStartListener;
    @Nullable
    private static Runnable fogEndListener;

    static {
        net.irisshaders.iris.gl.state.StateUpdateNotifiers.fogStartNotifier = listener -> fogStartListener = listener;
        net.irisshaders.iris.gl.state.StateUpdateNotifiers.fogEndNotifier = listener -> fogEndListener = listener;
    }

    private record GpuAsyncTask(Runnable callback, long syncObject) {
    }

    public record ActiveUniformInfo(
        String name,
        int arraySize,
        int legacyType,
        java.util.Optional<VulkanicUniformReflectionType> reflectionType
    ) {
        public ActiveUniformInfo {
            if (name == null) {
                name = "";
            }
            if (reflectionType == null) {
                reflectionType = java.util.Optional.empty();
            }
        }

        public String reflectionTypeName() {
            return reflectionType
                .map(VulkanicUniformReflectionType::getGlslTypeName)
                .orElse("(unknown:" + legacyType + ")");
        }
    }

    public record ActiveUniformBlockInfo(int index, String name) {
        public ActiveUniformBlockInfo {
            if (name == null) {
                name = "";
            }
        }
    }

    // Functional interfaces for debug callbacks
    @FunctionalInterface
    public interface DebugMessageCallback {
        void invoke(int source, int type, int id, int severity, String message);
    }

    @FunctionalInterface
    public interface DebugMessageCallbackAMD {
        void invoke(int id, int category, int severity, String message);
    }
    
    // OpenGL Constants - Buffer Targets
    public static final int GL_ARRAY_BUFFER = 0x8892;
    public static final int GL_ELEMENT_ARRAY_BUFFER = 0x8893;
    public static final int GL_COPY_READ_BUFFER = 0x8F36;
    public static final int GL_COPY_WRITE_BUFFER = 0x8F37;
    public static final int GL_PIXEL_PACK_BUFFER = 0x88EB;
    public static final int GL_SHADER_STORAGE_BUFFER = 0x90D2;
    
    // OpenGL Constants - Buffer Usage
    public static final int GL_STATIC_DRAW = 0x88E4;
    public static final int GL_DYNAMIC_DRAW = 0x88E8;
    
    // OpenGL Constants - Buffer Mapping
    public static final int GL_MAP_WRITE_BIT = 0x0002;
    public static final int GL_MAP_INVALIDATE_BUFFER_BIT = 0x0008;
    public static final int GL_MAP_UNSYNCHRONIZED_BIT = 0x0020;
    
    // OpenGL Constants - String Names
    public static final int GL_VENDOR = 0x1F00;
    public static final int GL_RENDERER = 0x1F01;
    public static final int GL_VERSION = 0x1F02;
    
    // OpenGL Constants - Sync
    public static final int GL_SYNC_GPU_COMMANDS_COMPLETE = 0x9117;
    public static final int GL_SYNC_STATUS = 0x9114;
    public static final int GL_SIGNALED = 0x9119;
    public static final int GL_SYNC_FLUSH_COMMANDS_BIT = 0x00000001;
    public static final int GL_TIMEOUT_EXPIRED = 0x911B;
    public static final int GL_WAIT_FAILED = 0x911D;
    
    // OpenGL Constants - Primitive Types
    public static final int GL_LINES = 0x0001;
    public static final int GL_TRIANGLES = 0x0004;
    public static final int GL_TRIANGLE_FAN = 0x0006;
    public static final int GL_PATCHES = 0x000E;
    
    // OpenGL Constants - Shader/Program Status
    public static final int GL_COMPILE_STATUS = 0x8B81;  // 35713
    public static final int GL_LINK_STATUS = 0x8B82;     // 35714
    public static final int GL_TRUE = 1;
    
    // OpenGL Constants - Debug Objects
    public static final int GL_SHADER = 0x82E1;
    public static final int GL_PROGRAM = 0x82E2;
    public static final int GL_VERTEX_ARRAY = 0x8074;
    
    // OpenGL Constants - Texture Targets and Units
    public static final int GL_TEXTURE_2D = 0x0DE1;      // 3553
    public static final int GL_TEXTURE0 = 0x84C0;        // 33984
    public static final int GL_TEXTURE1 = 0x84C1;        // 33985
    public static final int GL_TEXTURE2 = 0x84C2;        // 33986
    public static final int GL_TEXTURE3 = 0x84C3;        // 33987
    public static final int GL_TEXTURE4 = 0x84C4;        // 33988
    public static final int GL_TEXTURE5 = 0x84C5;        // 33989
    public static final int GL_TEXTURE6 = 0x84C6;        // 33990
    public static final int GL_TEXTURE7 = 0x84C7;        // 33991
    public static final int GL_TEXTURE8 = 0x84C8;        // 33992
    public static final int GL_TEXTURE9 = 0x84C9;        // 33993
    public static final int GL_TEXTURE10 = 0x84CA;       // 33994
    public static final int GL_TEXTURE11 = 0x84CB;       // 33995
    public static final int GL_TEXTURE12 = 0x84CC;       // 33996
    public static final int GL_TEXTURE13 = 0x84CD;       // 33997
    public static final int GL_TEXTURE14 = 0x84CE;       // 33998
    public static final int GL_TEXTURE15 = 0x84CF;       // 33999
    public static final int GL_TEXTURE16 = 0x84D0;       // 34000
    public static final int GL_TEXTURE17 = 0x84D1;       // 34001
    public static final int GL_TEXTURE18 = 0x84D2;       // 34002
    public static final int GL_TEXTURE19 = 0x84D3;       // 34003
    public static final int GL_TEXTURE20 = 0x84D4;       // 34004
    public static final int GL_TEXTURE21 = 0x84D5;       // 34005
    public static final int GL_TEXTURE22 = 0x84D6;       // 34006
    public static final int GL_TEXTURE23 = 0x84D7;       // 34007
    public static final int GL_TEXTURE24 = 0x84D8;       // 34008
    public static final int GL_TEXTURE25 = 0x84D9;       // 34009
    public static final int GL_TEXTURE26 = 0x84DA;       // 34010
    public static final int GL_TEXTURE27 = 0x84DB;       // 34011
    public static final int GL_TEXTURE28 = 0x84DC;       // 34012
    public static final int GL_TEXTURE29 = 0x84DD;       // 34013
    public static final int GL_TEXTURE30 = 0x84DE;       // 34014
    public static final int GL_TEXTURE31 = 0x84DF;       // 34015
    public static final int GL_TEXTURE_BASE_LEVEL = 0x813C;  // 33084
    public static final int GL_TEXTURE_MAX_LEVEL = 0x813D;   // 33085
    
    // OpenGL Constants - Framebuffer/Buffer
    public static final int GL_FRAMEBUFFER = 0x8D40;     // 36160
    public static final int GL_FRAMEBUFFER_COMPLETE = 0x8CD5;  // 36053
    public static final int GL_COLOR = 0x1800;
    
    // OpenGL Constants - Image Access
    public static final int GL_READ_WRITE = 0x88BA;
    
    // OpenGL Constants - Query Limits
    public static final int GL_MAX_TEXTURE_SIZE = 0x0D33;
    public static final int GL_MAX_TEXTURE_IMAGE_UNITS = 0x8872;
    public static final int GL_MAX_DRAW_BUFFERS = 0x8824;
    public static final int GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS = 0x90DD;
    public static final int GL_UNIFORM_BUFFER_OFFSET_ALIGNMENT = 0x8A34;
    
    // OpenGL Constants - Shader Types
    public static final int GL_VERTEX_SHADER = 0x8B31;
    public static final int GL_FRAGMENT_SHADER = 0x8B30;
    public static final int GL_GEOMETRY_SHADER = 0x8DD9;
    public static final int GL_COMPUTE_SHADER = 0x91B9;
    public static final int GL_TESS_CONTROL_SHADER = 0x8E88;
    public static final int GL_TESS_EVALUATION_SHADER = 0x8E87;
    
    // OpenGL Constants - Alpha Test Functions
    public static final int GL_NEVER = 0x0200;
    public static final int GL_LESS = 0x0201;
    public static final int GL_EQUAL = 0x0202;
    public static final int GL_LEQUAL = 0x0203;
    public static final int GL_GREATER = 0x0204;
    public static final int GL_NOTEQUAL = 0x0205;
    public static final int GL_GEQUAL = 0x0206;
    public static final int GL_ALWAYS = 0x0207;
    
    // OpenGL Constants - Stencil Operations
    public static final int GL_KEEP = 0x1E00;
    public static final int GL_REPLACE = 0x1E01;
    public static final int GL_INCR = 0x1E02;
    public static final int GL_DECR = 0x1E03;
    public static final int GL_INVERT = 0x150A;
    public static final int GL_INCR_WRAP = 0x8507;
    public static final int GL_DECR_WRAP = 0x8508;
    
    // OpenGL Constants - Blend Functions
    public static final int GL_ZERO = 0;
    public static final int GL_ONE = 1;
    public static final int GL_SRC_COLOR = 0x0300;
    public static final int GL_ONE_MINUS_SRC_COLOR = 0x0301;
    public static final int GL_DST_COLOR = 0x0306;
    public static final int GL_ONE_MINUS_DST_COLOR = 0x0307;
    public static final int GL_SRC_ALPHA = 0x0302;
    public static final int GL_ONE_MINUS_SRC_ALPHA = 0x0303;
    public static final int GL_DST_ALPHA = 0x0304;
    public static final int GL_ONE_MINUS_DST_ALPHA = 0x0305;
    public static final int GL_SRC_ALPHA_SATURATE = 0x0308;
    public static final int GL_CONSTANT_COLOR = 0x8001;
    public static final int GL_ONE_MINUS_CONSTANT_COLOR = 0x8002;
    public static final int GL_CONSTANT_ALPHA = 0x8003;
    public static final int GL_ONE_MINUS_CONSTANT_ALPHA = 0x8004;
    
    // OpenGL Constants - Texture Types
    public static final int GL_TEXTURE_1D = 0x0DE0;
    public static final int GL_PROXY_TEXTURE_2D = 0x8064;
    public static final int GL_TEXTURE_3D = 0x806F;
    public static final int GL_TEXTURE_BUFFER = 0x8C2A;
    public static final int GL_TEXTURE_CUBE_MAP = 0x8513;
    public static final int GL_TEXTURE_RECTANGLE = 0x84F5;

    // OpenGL Constants - Buffer Targets (extended)
    public static final int GL_UNIFORM_BUFFER = 0x8A11;
    
    // OpenGL Constants - Query Names
    public static final int GL_SHADING_LANGUAGE_VERSION = 0x8B8C;
    public static final int GL_EXTENSIONS = 0x1F03;
    public static final int GL_NUM_EXTENSIONS = 0x821D;
    
    // OpenGL Constants - Debug Capabilities
    public static final int GL_DEBUG_OUTPUT_SYNCHRONOUS = 0x8242;
    public static final int GL_CONTEXT_FLAGS = 0x821E;
    public static final int GL_CONTEXT_FLAG_DEBUG_BIT = 0x00000002;
    public static final int GL_DEBUG_OUTPUT = 0x92E0;
    public static final int GL_DONT_CARE = 0x1100;
    public static final int GL_MAX_LABEL_LENGTH = 0x82E8;
    
    // OpenGL Constants - Framebuffer Targets and Attachments
    public static final int GL_READ_FRAMEBUFFER = 0x8CA8;
    public static final int GL_DRAW_FRAMEBUFFER = 0x8CA9;
    public static final int GL_COLOR_ATTACHMENT0 = 0x8CE0;
    public static final int GL_COLOR_ATTACHMENT1 = 0x8CE1;
    public static final int GL_DEPTH_ATTACHMENT = 0x8D00;
    public static final int GL_DEPTH_STENCIL_ATTACHMENT = 0x821A;
    public static final int GL_MAX_COLOR_ATTACHMENTS = 0x8CDF;
    public static final int GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME = 0x8CD1;
    public static final int GL_NONE = 0;
    
    // OpenGL Constants - Blend State
    public static final int GL_BLEND = 0x0BE2;
    public static final int GL_FUNC_ADD = 0x8006;
    public static final int GL_FUNC_SUBTRACT = 0x800A;
    public static final int GL_FUNC_REVERSE_SUBTRACT = 0x800B;
    public static final int GL_MIN = 0x8007;
    public static final int GL_MAX = 0x8008;

    // OpenGL Constants - Color Logic
    public static final int GL_COLOR_LOGIC_OP = 0x0BF2;
    public static final int GL_OR_REVERSE = 0x150B;
    
    // OpenGL Constants - Culling
    public static final int GL_CULL_FACE = 0x0B44;
    public static final int GL_PROGRAM_POINT_SIZE = 0x8642;
    
    // OpenGL Constants - Tests
    public static final int GL_DEPTH_TEST = 0x0B71;
    public static final int GL_SCISSOR_TEST = 0x0C11;
    public static final int GL_POLYGON_OFFSET_FILL = 0x8037;
    
    // OpenGL Constants - Texture Parameters
    public static final int GL_TEXTURE_MIN_FILTER = 0x2801;
    public static final int GL_TEXTURE_MAG_FILTER = 0x2800;
    public static final int GL_TEXTURE_WRAP_S = 0x2802;
    public static final int GL_TEXTURE_WRAP_T = 0x2803;
    public static final int GL_TEXTURE_WRAP_R = 0x8072;
    public static final int GL_TEXTURE_MIN_LOD = 0x813A;
    public static final int GL_TEXTURE_MAX_LOD = 0x813B;
    public static final int GL_TEXTURE_LOD_BIAS = 0x8501;
    public static final int GL_MAX_TEXTURE_LOD_BIAS = 0x84FD;
    public static final int GL_LINEAR = 0x2601;
    public static final int GL_NEAREST = 0x2600;
    public static final int GL_NEAREST_MIPMAP_NEAREST = 0x2700;
    public static final int GL_LINEAR_MIPMAP_NEAREST = 0x2701;
    public static final int GL_NEAREST_MIPMAP_LINEAR = 0x2702;
    public static final int GL_LINEAR_MIPMAP_LINEAR = 0x2703;
    public static final int GL_CLAMP_TO_EDGE = 0x812F;
    public static final int GL_TEXTURE_COMPARE_MODE = 0x884C;
    public static final int GL_COMPARE_REF_TO_TEXTURE = 0x884E;
    public static final int GL_TEXTURE_SWIZZLE_RGBA = 0x8E46;
    
    // OpenGL Constants - Compute Shader
    public static final int GL_COMPUTE_WORK_GROUP_SIZE = 0x8267;
    public static final int GL_SHADER_IMAGE_ACCESS_BARRIER_BIT = 0x00000020;
    public static final int GL_TEXTURE_FETCH_BARRIER_BIT = 0x00000008;
    public static final int GL_SHADER_STORAGE_BARRIER_BIT = 0x00002000;
    public static final int GL_DISPATCH_INDIRECT_BUFFER = 0x90EE;
    public static final int GL_MAX_IMAGE_UNITS = 0x8F38;
    public static final int GL_MAX_IMAGE_UNITS_EXT = 0x8F38;
    
    // OpenGL Constants - Image/Texture Formats
    public static final int GL_RED = 0x1903;
    public static final int GL_GREEN = 0x1904;
    public static final int GL_BLUE = 0x1905;
    public static final int GL_ALPHA = 0x1906;
    public static final int GL_RG = 0x8227;
    public static final int GL_BGR = 0x80E0;
    public static final int GL_BGRA = 0x80E1;
    public static final int GL_RED_INTEGER = 0x8D94;
    public static final int GL_RG_INTEGER = 0x8228;
    public static final int GL_RGB_INTEGER = 0x8D98;
    public static final int GL_BGR_INTEGER = 0x8D9A;
    public static final int GL_RGBA_INTEGER = 0x8D99;
    public static final int GL_BGRA_INTEGER = 0x8D9B;
    public static final int GL_BYTE = 0x1400;
    public static final int GL_UNSIGNED_SHORT_4_4_4_4 = 0x8033;
    public static final int GL_UNSIGNED_INT = 0x1405;
    public static final int GL_R8 = 0x8229;
    
    // OpenGL Constants - Other
    public static final int GL_BUFFER = 0x82E0;
    public static final int GL_TEXTURE = 0x1702;
    public static final int GL_DYNAMIC_STORAGE_BIT = 0x0100;
    
    // OpenGL Constants - Debug Severity Levels
    public static final int GL_DEBUG_SEVERITY_HIGH = 0x9146;
    public static final int GL_DEBUG_SEVERITY_MEDIUM = 0x9147;
    public static final int GL_DEBUG_SEVERITY_LOW = 0x9148;
    public static final int GL_DEBUG_SEVERITY_NOTIFICATION = 0x826B;
    
    // OpenGL Constants - Debug Source
    public static final int GL_DEBUG_SOURCE_APPLICATION = 0x824A;
    
    // OpenGL Constants - Pixel Store Parameters
    public static final int GL_PACK_ROW_LENGTH = 0x0D02;
    public static final int GL_UNPACK_ROW_LENGTH = 0x0CF2;
    public static final int GL_UNPACK_SKIP_ROWS = 0x0CF3;
    public static final int GL_UNPACK_SKIP_PIXELS = 0x0CF4;
    public static final int GL_UNPACK_ALIGNMENT = 0x0CF5;

    // OpenGL Constants - Query Targets/Results
    public static final int GL_TIME_ELAPSED = 0x88BF;
    public static final int GL_QUERY_RESULT = 0x8866;
    public static final int GL_QUERY_RESULT_AVAILABLE = 0x8867;
    
    // OpenGL Constants - Clear Bits
    public static final int GL_COLOR_BUFFER_BIT = 0x00004000;
    public static final int GL_DEPTH_BUFFER_BIT = 0x00000100;
    public static final int GL_STENCIL_BUFFER_BIT = 0x00000400;
    
    // OpenGL Constants - Pixel Types and Formats
    public static final int GL_UNSIGNED_BYTE = 0x1401;
    public static final int GL_RGBA = 0x1908;
    public static final int GL_RGBA8 = 0x8058;
    public static final int GL_RGBA16 = 0x805B;
    public static final int GL_RGB = 0x1907;
    public static final int GL_R16F = 0x822D;
    
    // OpenGL Constants - Texture Wrap Modes
    public static final int GL_REPEAT = 0x2901;
    
    // OpenGL Constants - Fog Modes
    public static final int GL_EXP2 = 0x0801;
    
    // OpenGL Constants - Polygon Mode
    public static final int GL_POINT = 0x1B00;
    public static final int GL_LINE = 0x1B01;
    public static final int GL_FILL = 0x1B02;
    
    // OpenGL Constants - Face Culling
    public static final int GL_FRONT = 0x0404;
    public static final int GL_BACK = 0x0405;
    public static final int GL_FRONT_AND_BACK = 0x0408;
    
    // OpenGL Constants - GPU Memory Info (NVX)
    public static final int GL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX = 0x9049;
    
    // OpenGL Constants - Texture Level Parameters
    public static final int GL_TEXTURE_INTERNAL_FORMAT = 0x1003;
    public static final int GL_TEXTURE_WIDTH = 0x1000;
    public static final int GL_TEXTURE_HEIGHT = 0x1001;
    public static final int GL_TEXTURE_BINDING_2D = 0x8069;
    
    // OpenGL Constants - Framebuffer Binding
    public static final int GL_FRAMEBUFFER_BINDING = 0x8CA6;
    
    // OpenGL Constants - Get Parameters
    public static final int GL_COLOR_CLEAR_VALUE = 0x0C22;
    public static final int GL_VIEWPORT = 0x0BA2;
    
    // OpenGL Constants - Boolean values
    public static final int GL_FALSE = 0;
    
    // OpenGL Constants - Uniform/Active Types
    public static final int GL_FLOAT = 0x1406;
    public static final int GL_DOUBLE = 0x140A;
    public static final int GL_INT = 0x1404;
    public static final int GL_SHORT = 0x1402;
    public static final int GL_UNSIGNED_SHORT = 0x1403;
    public static final int GL_HALF_FLOAT = 0x140B;
    public static final int GL_BOOL = 0x8B56;
    public static final int GL_UNSIGNED_INT_VEC2 = 0x8DC6;
    public static final int GL_UNSIGNED_INT_VEC3 = 0x8DC7;
    public static final int GL_UNSIGNED_INT_VEC4 = 0x8DC8;
    public static final int GL_BOOL_VEC2 = 0x8B57;
    public static final int GL_BOOL_VEC3 = 0x8B58;
    public static final int GL_BOOL_VEC4 = 0x8B59;
    public static final int GL_FLOAT_VEC2 = 0x8B50;
    public static final int GL_FLOAT_VEC3 = 0x8B51;
    public static final int GL_FLOAT_VEC4 = 0x8B52;
    public static final int GL_INT_VEC2 = 0x8B53;
    public static final int GL_INT_VEC3 = 0x8B54;
    public static final int GL_INT_VEC4 = 0x8B55;
    public static final int GL_FLOAT_MAT2 = 0x8B5A;
    public static final int GL_FLOAT_MAT3 = 0x8B5B;
    public static final int GL_FLOAT_MAT4 = 0x8B5C;
    public static final int GL_SAMPLER_1D = 0x8B5D;
    public static final int GL_SAMPLER_2D = 0x8B5E;
    public static final int GL_SAMPLER_3D = 0x8B5F;
    public static final int GL_SAMPLER_CUBE = 0x8B60;
    public static final int GL_SAMPLER_2D_ARRAY = 0x8DC1;
    public static final int GL_SAMPLER_1D_SHADOW = 0x8B61;
    public static final int GL_SAMPLER_2D_SHADOW = 0x8B62;
    public static final int GL_SAMPLER_CUBE_SHADOW = 0x8DC5;
    public static final int GL_INT_SAMPLER_2D = 0x8DCA;
    public static final int GL_INT_SAMPLER_3D = 0x8DCB;
    public static final int GL_INT_SAMPLER_CUBE = 0x8DCC;
    public static final int GL_INT_SAMPLER_2D_ARRAY = 0x8DCF;
    public static final int GL_UNSIGNED_INT_SAMPLER_1D = 0x8DD1;
    public static final int GL_UNSIGNED_INT_SAMPLER_2D = 0x8DD2;
    public static final int GL_UNSIGNED_INT_SAMPLER_3D = 0x8DD3;
    public static final int GL_UNSIGNED_INT_SAMPLER_CUBE = 0x8DD4;
    public static final int GL_UNSIGNED_INT_SAMPLER_1D_ARRAY = 0x8DD6;
    public static final int GL_UNSIGNED_INT_SAMPLER_2D_ARRAY = 0x8DD7;
    
    // OpenGL Constants - Image Types (ARB_shader_image_load_store)
    public static final int GL_IMAGE_1D = 0x904C;
    public static final int GL_IMAGE_2D = 0x904D;
    public static final int GL_IMAGE_3D = 0x904E;
    public static final int GL_IMAGE_1D_ARRAY = 0x9052;
    public static final int GL_IMAGE_2D_ARRAY = 0x9053;
    public static final int GL_INT_IMAGE_1D = 0x9057;
    public static final int GL_INT_IMAGE_2D = 0x9058;
    public static final int GL_INT_IMAGE_3D = 0x9059;
    public static final int GL_INT_IMAGE_1D_ARRAY = 0x905E;
    public static final int GL_INT_IMAGE_2D_ARRAY = 0x905F;
    public static final int GL_UNSIGNED_INT_IMAGE_1D = 0x9062;
    public static final int GL_UNSIGNED_INT_IMAGE_2D = 0x9063;
    public static final int GL_UNSIGNED_INT_IMAGE_3D = 0x9064;
    public static final int GL_UNSIGNED_INT_IMAGE_1D_ARRAY = 0x9069;
    public static final int GL_UNSIGNED_INT_IMAGE_2D_ARRAY = 0x906A;
    
    // OpenGL Constants - Program Query
    public static final int GL_ACTIVE_UNIFORMS = 0x8B86;
    public static final int GL_ACTIVE_UNIFORM_BLOCKS = 0x8A36;

    // OpenGL Constants - EXT_debug_label types
    public static final int GL_BUFFER_OBJECT_EXT = 0x9151;
    public static final int GL_SHADER_OBJECT_EXT = 0x8B48;
    public static final int GL_PROGRAM_OBJECT_EXT = 0x8B40;
    
    // OpenGL Constants - GL State Query
    public static final int GL_CURRENT_PROGRAM = 0x8B8D;
    public static final int GL_VERTEX_ARRAY_BINDING = 0x85B5;
    public static final int GL_ARRAY_BUFFER_BINDING = 0x8894;
    public static final int GL_ELEMENT_ARRAY_BUFFER_BINDING = 0x8895;
    public static final int GL_ACTIVE_TEXTURE = 0x84E0;
    public static final int GL_BLEND_EQUATION_RGB = 0x8009;
    public static final int GL_BLEND_EQUATION_ALPHA = 0x883D;
    public static final int GL_BLEND_SRC_RGB = 0x80C9;
    public static final int GL_BLEND_SRC_ALPHA = 0x80CA;
    public static final int GL_BLEND_DST_RGB = 0x80C8;
    public static final int GL_BLEND_DST_ALPHA = 0x80CB;
    public static final int GL_DEPTH_WRITEMASK = 0x0B72;
    public static final int GL_DEPTH_FUNC = 0x0B74;
    public static final int GL_STENCIL_TEST = 0x0B90;
    public static final int GL_STENCIL_FUNC = 0x0B92;
    public static final int GL_STENCIL_REF = 0x0B97;
    public static final int GL_STENCIL_VALUE_MASK = 0x0B93;
    public static final int GL_STENCIL_FAIL = 0x0B94;
    public static final int GL_STENCIL_PASS_DEPTH_FAIL = 0x0B95;
    public static final int GL_STENCIL_PASS_DEPTH_PASS = 0x0B96;
    public static final int GL_STENCIL_WRITEMASK = 0x0B98;
    public static final int GL_CULL_FACE_MODE = 0x0B45;
    public static final int GL_POLYGON_MODE = 0x0B40;
    
    // OpenGL Constants - Texture Formats (GL11)
    public static final int GL_RGB8 = 0x8051;
    public static final int GL_RGB16 = 0x8054;
    public static final int GL_R3_G3_B2 = 0x2A10;
    public static final int GL_RGB5_A1 = 0x8057;
    public static final int GL_RGB10_A2 = 0x8059;
    
    // OpenGL Constants - Texture Formats (GL30)
    public static final int GL_RG8 = 0x822B;
    public static final int GL_R16 = 0x822A;
    public static final int GL_RG16 = 0x822C;
    public static final int GL_RG16F = 0x822F;
    public static final int GL_RGB16F = 0x881B;
    public static final int GL_RGBA16F = 0x881A;
    public static final int GL_R32F = 0x822E;
    public static final int GL_RG32F = 0x8230;
    public static final int GL_RGB32F = 0x8815;
    public static final int GL_RGBA32F = 0x8814;
    public static final int GL_R8I = 0x8231;
    public static final int GL_RG8I = 0x8237;
    public static final int GL_RGB8I = 0x8D8F;
    public static final int GL_RGBA8I = 0x8D8E;
    public static final int GL_R8UI = 0x8232;
    public static final int GL_RG8UI = 0x8238;
    public static final int GL_RGB8UI = 0x8D7D;
    public static final int GL_RGBA8UI = 0x8D7C;
    public static final int GL_R16I = 0x8233;
    public static final int GL_RG16I = 0x8239;
    public static final int GL_RGB16I = 0x8D89;
    public static final int GL_RGBA16I = 0x8D88;
    public static final int GL_R16UI = 0x8234;
    public static final int GL_RG16UI = 0x823A;
    public static final int GL_RGB16UI = 0x8D77;
    public static final int GL_RGBA16UI = 0x8D76;
    public static final int GL_R32I = 0x8235;
    public static final int GL_RG32I = 0x823B;
    public static final int GL_RGB32I = 0x8D83;
    public static final int GL_RGBA32I = 0x8D82;
    public static final int GL_R32UI = 0x8236;
    public static final int GL_RG32UI = 0x823C;
    public static final int GL_RGB32UI = 0x8D71;
    public static final int GL_RGBA32UI = 0x8D70;
    public static final int GL_R11F_G11F_B10F = 0x8C3A;
    public static final int GL_RGB9_E5 = 0x8C3D;
    
    // OpenGL Constants - Texture Formats (GL31)
    public static final int GL_R8_SNORM = 0x8F94;
    public static final int GL_RG8_SNORM = 0x8F95;
    public static final int GL_RGB8_SNORM = 0x8F96;
    public static final int GL_RGBA8_SNORM = 0x8F97;
    public static final int GL_R16_SNORM = 0x8F98;
    public static final int GL_RG16_SNORM = 0x8F99;
    public static final int GL_RGB16_SNORM = 0x8F9A;
    public static final int GL_RGBA16_SNORM = 0x8F9B;
    
    // OpenGL Constants - Depth Formats (GL30)
    public static final int GL_DEPTH_COMPONENT = 0x1902;
    public static final int GL_DEPTH_COMPONENT16 = 0x81A5;
    public static final int GL_DEPTH_COMPONENT24 = 0x81A6;
    public static final int GL_DEPTH_COMPONENT32 = 0x81A7;
    public static final int GL_DEPTH_COMPONENT32F = 0x8CAC;
    public static final int GL_DEPTH_STENCIL = 0x84F9;
    public static final int GL_DEPTH24_STENCIL8 = 0x88F0;
    public static final int GL_DEPTH32F_STENCIL8 = 0x8CAD;
    public static final int GL_DEPTH_STENCIL_TEXTURE_MODE = 0x90EA;
    
    // OpenGL Constants - Pixel Types (Additional)
    public static final int GL_UNSIGNED_BYTE_3_3_2 = 0x8032;
    public static final int GL_UNSIGNED_BYTE_2_3_3_REV = 0x8362;
    public static final int GL_UNSIGNED_SHORT_5_6_5 = 0x8363;
    public static final int GL_UNSIGNED_SHORT_5_6_5_REV = 0x8364;
    public static final int GL_UNSIGNED_SHORT_4_4_4_4_REV = 0x8365;
    public static final int GL_UNSIGNED_SHORT_5_5_5_1 = 0x8034;
    public static final int GL_UNSIGNED_SHORT_1_5_5_5_REV = 0x8366;
    public static final int GL_UNSIGNED_INT_8_8_8_8 = 0x8035;
    public static final int GL_UNSIGNED_INT_8_8_8_8_REV = 0x8367;
    public static final int GL_UNSIGNED_INT_10_10_10_2 = 0x8036;
    public static final int GL_UNSIGNED_INT_2_10_10_10_REV = 0x8368;
    public static final int GL_UNSIGNED_INT_10F_11F_11F_REV = 0x8C3B;
    public static final int GL_UNSIGNED_INT_24_8 = 0x84FA;
    public static final int GL_FLOAT_32_UNSIGNED_INT_24_8_REV = 0x8DAD;
    
    /**
     * Initialize the Vulkanic API with the default backend (OpenGL).
     */
    public static void initialize() {
        initialize(GraphicsBackendType.OPENGL);
    }
    
    /**
     * Initialize the Vulkanic API with a specific backend.
     * @param backendType The backend type to use
     */
    public static synchronized void initialize(GraphicsBackendType backendType) {
        if (backend == null) {
            switch (backendType) {
                case OPENGL:
                    backend = new OpenGLBackend();
                    rawVulkanBackend = null;
                    break;
                case VULKAN:
                    ensureVulkanLwjglStackSize();
                    rawVulkanBackend = new VulkanBackend();
                    backend = createFailFastVulkanProxy(rawVulkanBackend);
            }

			readFramebufferBinding = 0;
			drawFramebufferBinding = 0;
        }
    }

    /**
     * Normalizes a backend option value from {@code options.txt}.
     *
     * <p>Accepted values are {@code opengl} and {@code vulkan} (case-insensitive).
     * Any missing/unknown value falls back to {@code opengl}.
     */
    public static String normalizeBackendOptionValue(@Nullable String configuredValue) {
        if (configuredValue == null) {
            return "opengl";
        }

        String normalized = configuredValue.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("vulkan")) {
            return "vulkan";
        }
        return "opengl";
    }

    /**
     * Converts a backend option value from {@code options.txt} into a backend type.
     *
     * <p>Any missing/unknown value maps to {@link GraphicsBackendType#OPENGL}.
     */
    public static GraphicsBackendType backendTypeFromOptionsValue(@Nullable String configuredValue) {
        return normalizeBackendOptionValue(configuredValue).equals("vulkan")
            ? GraphicsBackendType.VULKAN
            : GraphicsBackendType.OPENGL;
    }

    /**
     * Initializes Vulkanic backend routing from {@code options.txt} value semantics.
     *
     * <p>This is intended for startup code that reads hidden options like
     * {@code graphics_backend=vulkan}. Unknown values default to OpenGL.
     */
    public static synchronized void initializeFromOptionsValue(@Nullable String configuredValue) {
        initialize(backendTypeFromOptionsValue(configuredValue));
    }

    private static void ensureVulkanLwjglStackSize() {
        String configuredValue = System.getProperty(LWJGL_STACK_SIZE_PROPERTY);
        if (configuredValue != null) {
            try {
                if (Integer.parseInt(configuredValue.trim()) >= VULKAN_LWJGL_STACK_SIZE_KB) {
                    return;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        System.setProperty(LWJGL_STACK_SIZE_PROPERTY, Integer.toString(VULKAN_LWJGL_STACK_SIZE_KB));
    }
    
    /**
     * Get the current graphics backend.
     */
    public static GraphicsBackend getBackend() {
        if (backend == null) {
            initialize();
        }
        return backend;
    }

    @Nullable
    private static VulkanBackend directVulkanBackendForImplementedMethods() {
        if (backend == null) {
            initialize();
        }
        return rawVulkanBackend;
    }

    private static void dispatchImplementedVoid(
        java.util.function.Consumer<VulkanBackend> directCall,
        java.util.function.Consumer<GraphicsBackend> fallbackCall
    ) {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        if (directVulkanBackend != null) {
            directCall.accept(directVulkanBackend);
            return;
        }

        fallbackCall.accept(getBackend());
    }

    private static <T> T dispatchImplementedValue(
        java.util.function.Function<VulkanBackend, T> directCall,
        java.util.function.Function<GraphicsBackend, T> fallbackCall
    ) {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        if (directVulkanBackend != null) {
            return directCall.apply(directVulkanBackend);
        }

        return fallbackCall.apply(getBackend());
    }

    /**
     * Gets the currently active backend identity.
     */
    public static GraphicsBackendType getActiveBackendType() {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        if (directVulkanBackend != null) {
            return directVulkanBackend.getBackendType();
        }
        return getBackend().getBackendType();
    }

    /**
     * Returns true when Vulkan backend routing is active.
     *
     * <p>Note: this may still be running in OpenGL-delegated bootstrap mode.</p>
     */
    public static boolean isVulkanBackendSelected() {
        return getActiveBackendType() == GraphicsBackendType.VULKAN;
    }

    /**
     * Returns true only when backend routing has already been initialized and
     * the selected backend is Vulkan.
     *
     * <p>Unlike {@link #isVulkanBackendSelected()}, this method does not
     * auto-initialize backend routing.</p>
     */
    public static synchronized boolean isVulkanBackendInitializedAndSelected() {
        return backend != null && backend.getBackendType() == GraphicsBackendType.VULKAN;
    }

    /**
     * Returns true only when native Vulkan internals are active and ready.
     */
    public static boolean isNativeVulkanBackendReady() {
        return getBackend().isNativeVulkanReady();
    }

    /**
     * Returns a diagnostics report describing Vulkan backend readiness and
     * environment preconditions.
     *
     * <p>When Vulkan backend routing is not selected, this returns a report
     * that explains why Vulkan-specific probes were skipped.</p>
     */
    public static VulkanReadinessReport getVulkanReadinessReport() {
        if (rawVulkanBackend != null) {
            return rawVulkanBackend.getReadinessReport();
        }

        GraphicsBackend activeBackend = getBackend();
        if (activeBackend instanceof VulkanBackend vulkanBackend) {
            return vulkanBackend.getReadinessReport();
        }

        return VulkanReadinessReport.forNonVulkanBackend(
            activeBackend.getBackendType(),
            activeBackend.isNativeVulkanReady()
        );
    }

    /**
     * Returns a human-readable multiline Vulkan readiness summary.
     */
    public static String describeVulkanReadiness() {
        return getVulkanReadinessReport().toMultilineString();
    }

    /**
     * Returns a snapshot of Vulkan-native execution-context ownership for the
     * currently active backend.
     */
    public static VulkanExecutionContextInfo getVulkanExecutionContextInfo() {
        return getBackend().getVulkanExecutionContextInfo();
    }

    /**
     * Returns a human-readable multiline Vulkan execution-context summary.
     */
    public static String describeVulkanExecutionContextInfo() {
        return getVulkanExecutionContextInfo().toMultilineString();
    }

    /**
     * Returns a snapshot of Vulkan surface/swapchain ownership for the
     * currently active backend.
     */
    public static VulkanSwapchainSurfaceInfo getVulkanSwapchainSurfaceInfo() {
        return getBackend().getVulkanSwapchainSurfaceInfo();
    }

    /**
     * Returns a human-readable multiline Vulkan surface/swapchain summary.
     */
    public static String describeVulkanSwapchainSurfaceInfo() {
        return getVulkanSwapchainSurfaceInfo().toMultilineString();
    }

    /**
     * Recreates swapchain-dependent Vulkan resources for the active backend.
     *
     * <p>OpenGL backends treat this as a no-op.</p>
     */
    public static void recreateVulkanSwapchain() {
        getBackend().recreateVulkanSwapchain();
    }

    /**
     * Recreates swapchain-dependent Vulkan resources only when a resize mismatch
     * is detected for the active surface.
     *
     * @return true when recreation occurred, false when no recreation was required
     */
    public static boolean recreateVulkanSwapchainIfNeeded() {
        return getBackend().recreateVulkanSwapchainIfNeeded();
    }

    /**
     * Handles a framebuffer resize event by conditionally recreating the Vulkan
     * swapchain when Vulkan backend routing is already active.
     *
     * <p>This method intentionally does <b>not</b> auto-initialize the backend:
     * when Vulkanic is not initialized yet, this is treated as a no-op to avoid
     * accidentally locking startup into the default OpenGL backend.</p>
     *
     * @param framebufferWidth new framebuffer width in pixels
     * @param framebufferHeight new framebuffer height in pixels
     * @return true if Vulkan swapchain recreation occurred, false otherwise
     */
    public static boolean recreateVulkanSwapchainIfNeededOnFramebufferResize(int framebufferWidth, int framebufferHeight) {
        if (framebufferWidth <= 0 || framebufferHeight <= 0) {
            return false;
        }

        GraphicsBackend activeBackend = backend;
        if (activeBackend == null || activeBackend.getBackendType() != GraphicsBackendType.VULKAN) {
            return false;
        }

        return activeBackend.recreateVulkanSwapchainIfNeeded();
    }

    /**
     * Attempts explicit native Vulkan runtime initialization for the active
     * backend and returns structured diagnostics about the outcome.
     */
    public static VulkanNativeInitializationInfo initializeNativeVulkanRuntime() {
        return getBackend().initializeNativeVulkanRuntime();
    }

    /**
     * Returns a human-readable multiline summary of explicit native Vulkan
     * initialization diagnostics.
     */
    public static String describeNativeVulkanInitialization() {
        return initializeNativeVulkanRuntime().toMultilineString();
    }

    /**
     * Performs fail-hard native Vulkan runtime initialization during renderer
     * startup only when Vulkan backend routing is already selected.
     *
     * <p>This method intentionally avoids implicit backend initialization. If
     * backend routing has not been selected yet, it is treated as a no-op.
     * OpenGL routing is also a no-op.</p>
     *
     * <p>When Vulkan routing is selected but native bring-up fails, this throws
     * an {@link IllegalStateException} containing initialization diagnostics.</p>
     */
    public static void initializeNativeVulkanRuntimeOnRendererStartupIfSelected() {
        GraphicsBackend activeBackend = backend;
        if (activeBackend == null || activeBackend.getBackendType() != GraphicsBackendType.VULKAN) {
            return;
        }

        VulkanNativeInitializationInfo info = activeBackend.initializeNativeVulkanRuntime();
        if (!info.isNativeVulkanReady()) {
            throw new IllegalStateException(
                "Vulkan backend is selected but native Vulkan runtime initialization failed during renderer startup.\n"
                    + info.toMultilineString());
        }

        VulkanExecutionContextInfo executionContextInfo = activeBackend.getVulkanExecutionContextInfo();
        if (!executionContextInfo.isAvailable()) {
            throw new IllegalStateException(
                "Vulkan backend is selected but execution context is unavailable during renderer startup.\n"
                    + executionContextInfo.toMultilineString());
        }

        VulkanSwapchainSurfaceInfo swapchainSurfaceInfo = activeBackend.getVulkanSwapchainSurfaceInfo();
        if (!swapchainSurfaceInfo.isAvailable()) {
            throw new IllegalStateException(
                "Vulkan backend is selected but swapchain/surface is unavailable during renderer startup.\n"
                    + swapchainSurfaceInfo.toMultilineString());
        }
    }

    public static long prepareRendererBootstrapWindowHandle(long mainWindowHandle) {
        return getBackend().prepareRendererBootstrapWindow(mainWindowHandle);
    }

    public static GpuDevice createRendererDevice(
        long rendererBootstrapWindowHandle,
        int debugVerbosity,
        boolean debugEnabled,
        BiFunction<ResourceLocation, ShaderType, String> defaultShaderSource,
        boolean debugLabelsEnabled
    ) {
        return getBackend().createRendererDevice(
            rendererBootstrapWindowHandle,
            debugVerbosity,
            debugEnabled,
            defaultShaderSource,
            debugLabelsEnabled
        );
    }

    public static void onRendererDeviceInitialized(long mainWindowHandle, GpuDevice gpuDevice) {
        getBackend().onRendererDeviceInitialized(mainWindowHandle, gpuDevice);
    }

    public static void cleanupRendererBootstrapResources() {
        GraphicsBackend activeBackend = backend;
        if (activeBackend != null) {
            activeBackend.cleanupRendererBootstrapResources();
        }
    }

    private static GraphicsBackend createFailFastVulkanProxy(VulkanBackend vulkanBackend) {
        java.util.Map<Method, Method> implementedMethods = new java.util.HashMap<>();
        java.util.Set<Method> missingMethods = new java.util.HashSet<>();

        for (Method interfaceMethod : GraphicsBackend.class.getMethods()) {
            if (interfaceMethod.getDeclaringClass() == Object.class) {
                continue;
            }

            try {
                implementedMethods.put(
                    interfaceMethod,
                    VulkanBackend.class.getMethod(interfaceMethod.getName(), interfaceMethod.getParameterTypes())
                );
            } catch (NoSuchMethodException ignored) {
                missingMethods.add(interfaceMethod);
            }
        }

        return (GraphicsBackend) Proxy.newProxyInstance(
            GraphicsBackend.class.getClassLoader(),
            new Class<?>[]{GraphicsBackend.class},
            (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return method.invoke(vulkanBackend, args);
                }

                Method backendMethod = implementedMethods.get(method);
                if (backendMethod == null && missingMethods.contains(method)) {
                    if (method.isDefault()) {
                        return invokeDefaultInterfaceMethod(proxy, method, args);
                    }
                    throw new IllegalStateException(
                        "Vulkan backend selected but method '" + method.getName() + "' is not implemented natively; "
                            + "OpenGL fallback is intentionally blocked.");
                }

                try {
                    return backendMethod.invoke(vulkanBackend, args);
                } catch (InvocationTargetException exception) {
                    throw exception.getTargetException();
                }
            }
        );
    }

    private static Object invokeDefaultInterfaceMethod(Object proxy, Method method, Object[] args) throws Throwable {
        Class<?> declaringClass = method.getDeclaringClass();
        MethodHandles.Lookup privateLookup = MethodHandles.privateLookupIn(declaringClass, MethodHandles.lookup());
        MethodType methodType = MethodType.methodType(method.getReturnType(), method.getParameterTypes());
        Object[] safeArgs = args == null ? new Object[0] : args;

        return privateLookup
            .findSpecial(declaringClass, method.getName(), methodType, declaringClass)
            .bindTo(proxy)
            .invokeWithArguments(safeArgs);
    }
    
    /**
     * Gets the immediate-mode command context for OpenGL rendering.
     * 
     * For OpenGL backend: Returns a singleton immediate-mode context
     * For Vulkan backend: This method would not be used (explicit command buffers instead)
     * 
     * <p><b>USAGE GUIDANCE:</b></p>
     * <ul>
     * <li><b>Current transitional pattern:</b> Call this for each CommandContext-aware API method.
     *     This is acceptable during migration since most API methods don't take CommandContext yet.</li>
     * <li><b>Future Vulkan-compatible pattern:</b> Get the context ONCE at the start of a rendering
     *     operation and reuse it for multiple API calls. This matches Vulkan's command buffer model:
     *     <pre>
     *     // Get context once
    *     CommandContext ctx = VulkanicAPI.getCommandContext(); // or beginCommandBuffer() for Vulkan
     *     
     *     // Reuse for multiple operations
     *     VulkanicAPI.setDynamicViewport(ctx, ...);
     *     VulkanicAPI.setDynamicScissor(ctx, ...);
     *     VulkanicAPI.bindPipeline(ctx, ...);
     *     VulkanicAPI.drawIndexed(ctx, ...);
     *     </pre>
     * </li>
    * <li><b>Low-level utilities (GlStateManager):</b> Calling the legacy immediate-context accessor internally
     *     is acceptable since they're OpenGL-specific and called from framework code we don't control.</li>
     * </ul>
     * 
     * <p>This is a convenience method for migrating code to use CommandContext parameters
     * without changing the immediate execution model during the transition period.</p>
     * 
     * @return Immediate-mode command context (OpenGL singleton)
     */
    public static CommandContext getCommandContext() {
        java.util.ArrayDeque<CommandContext> stack = CONTEXT_STACK.get();
        if (stack != null && !stack.isEmpty()) {
            return stack.peek();
        }
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        return directVulkanBackend != null
            ? directVulkanBackend.getCurrentCommandContext()
            : getBackend().getCurrentCommandContext();
    }

    /**
     * Pushes a CommandContext onto the thread-local stack. Use when entering
     * a scope where the provided context should be returned by getCommandContext().
     */
    public static void pushCommandContext(CommandContext ctx) {
        if (ctx == null) return;
        CONTEXT_STACK.get().push(ctx);
    }

    /**
     * Pops the current CommandContext from the thread-local stack.
     */
    public static void popCommandContext() {
        java.util.ArrayDeque<CommandContext> stack = CONTEXT_STACK.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
        if (stack.isEmpty()) {
            CONTEXT_STACK.remove();
        }
    }

    /**
     * Pushes a context and returns an AutoCloseable scope that pops it safely.
     *
     * <p>Typical usage:</p>
     * <pre>
     * try (AutoCloseable scope = VulkanicAPI.withCommandContext(ctx)) {
     *     // all VulkanicAPI.getCommandContext() calls in this scope return ctx
     * }
     * </pre>
     */
    public static AutoCloseable withCommandContext(CommandContext ctx) {
        if (ctx == null) {
            return () -> {
            };
        }

        pushCommandContext(ctx);
        return new AutoCloseable() {
            private boolean closed;

            @Override
            public void close() {
                if (!closed) {
                    closed = true;
                    popCommandContext();
                }
            }
        };
    }

    /**
     * @deprecated Use {@link #getCommandContext()} for backend-neutral command-context retrieval.
     */
    @Deprecated
    public static CommandContext getImmediateContext() {
        return getCommandContext();
    }
    
    // Context operations
    /**
     * Gets the current graphics context (platform-specific).
     * On Windows, this returns the WGL context handle.
     * Returns 0 or NULL if no context is current.
     */
    public static long getGraphicsContext() {
        return getBackend().getGraphicsContext();
    }
    
    // Convenience methods that delegate to the backend
    
    /**
     * Sets the dynamic viewport state for rendering with explicit command context.
     * 
     * This is the preferred method for setting viewport - it explicitly takes a CommandContext
     * parameter to support both immediate (OpenGL) and deferred (Vulkan) rendering models.
     * 
     * In OpenGL: Maps to glViewport()
     * In Vulkan: Maps to vkCmdSetViewport() (dynamic state in command buffer)
     * 
     * @param ctx Command context for recording this command
     * @param x The x coordinate of the viewport's lower-left corner
     * @param y The y coordinate of the viewport's lower-left corner  
     * @param width The width of the viewport in pixels
     * @param height The height of the viewport in pixels
     */
    public static void setDynamicViewport(CommandContext ctx, int x, int y, int width, int height) {
        if ((TRACE_RENDER_TARGET_CONTENT_HASHES && DeterministicCameraCapture.isEnabledForDiagnostics())
            || VulkanicDrawStateDiagnostics.enabled()) {
            diagnosticLastViewport = new DiagnosticViewportState(x, y, width, height);
        }
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        if (directVulkanBackend != null) {
            directVulkanBackend.setDynamicViewport(ctx, x, y, width, height);
            return;
        }
        getBackend().setDynamicViewport(ctx, x, y, width, height);
    }
    
    
    /**
     * Sets the dynamic scissor rectangle for rendering with explicit command context.
     * 
     * This is the preferred method for setting scissor - it explicitly takes a CommandContext
     * parameter to support both immediate (OpenGL) and deferred (Vulkan) rendering models.
     * 
     * In OpenGL: Maps to glScissor()
     * In Vulkan: Maps to vkCmdSetScissor() (dynamic state in command buffer)
     * 
     * @param ctx Command context for recording this command
     * @param x The x coordinate of the scissor rectangle's lower-left corner
     * @param y The y coordinate of the scissor rectangle's lower-left corner
     * @param width The width of the scissor rectangle in pixels
     * @param height The height of the scissor rectangle in pixels
     */
    public static void setDynamicScissor(CommandContext ctx, int x, int y, int width, int height) {
        getBackend().setDynamicScissor(ctx, x, y, width, height);
    }
    
    /**
     * Clears buffers to preset values.
     * 
     * @param ctx Command context for recording this command
     * @param mask Bitwise OR of masks (GL_COLOR_BUFFER_BIT, GL_DEPTH_BUFFER_BIT, etc.)
     */
    public static void clearBuffers(CommandContext ctx, int mask) {
        traceShaderInputParityOrdering(
            "clear",
            "vulkanic-clearBuffers-mask",
            "mask=0x" + Integer.toHexString(mask)
        );
        getBackend().clearBuffers(ctx, mask);
    }

    /**
     * Clears buffers using backend-neutral clear-buffer bits.
     */
    public static void clearBuffers(CommandContext ctx, VulkanicClearBuffer... buffers) {
        traceShaderInputParityOrdering(
            "clear",
            "vulkanic-clearBuffers",
            "buffers=" + shaderInputParitySanitizeLabel(java.util.Arrays.toString(buffers))
        );
        getBackend().clearBuffers(ctx, buffers);
    }

    public static void clearColorBuffer(CommandContext ctx) {
        clearBuffers(ctx, VulkanicClearBuffer.COLOR);
    }

    public static void clearDepthBuffer(CommandContext ctx) {
        clearBuffers(ctx, VulkanicClearBuffer.DEPTH);
    }

    public static void clearColorAndDepthBuffers(CommandContext ctx) {
        clearBuffers(ctx, VulkanicClearBuffer.COLOR, VulkanicClearBuffer.DEPTH);
    }

    /**
     * Clears buffers and drains the error queue on macOS for compatibility with legacy GL behavior.
     */
    public static void clearBuffersWithMacosWorkaround(CommandContext ctx, int mask) {
        clearBuffers(ctx, mask);
        if (IS_MACOS) {
            getBackend().getError(ctx);
        }
    }

    /**
     * Clears buffers using backend-neutral clear-buffer bits and drains the error queue on macOS.
     */
    public static void clearBuffersWithMacosWorkaround(CommandContext ctx, VulkanicClearBuffer... buffers) {
        clearBuffersWithMacosWorkaround(ctx, VulkanicClearBuffer.toLegacyGlMask(buffers));
    }

    public static void clearColorBufferWithMacosWorkaround(CommandContext ctx) {
        clearBuffersWithMacosWorkaround(ctx, VulkanicClearBuffer.COLOR);
    }

    public static void clearDepthBufferWithMacosWorkaround(CommandContext ctx) {
        clearBuffersWithMacosWorkaround(ctx, VulkanicClearBuffer.DEPTH);
    }

    public static void clearColorAndDepthBuffersWithMacosWorkaround(CommandContext ctx) {
        clearBuffersWithMacosWorkaround(ctx, VulkanicClearBuffer.COLOR, VulkanicClearBuffer.DEPTH);
    }
    
    /**
     * Sets blending enabled or disabled.
     * 
     * @param ctx Command context for recording this command
     * @param enabled True to enable, false to disable
     */
    public static void setBlendEnabled(CommandContext ctx, boolean enabled) {
        getBackend().setBlendEnabled(ctx, enabled);
    }
    
    /**
     * Enables or disables a capability for a specific buffer.
     * 
     * @param ctx Command context for recording this command
     * @param capability The capability to enable/disable
     * @param index The buffer index
     * @param enabled True to enable, false to disable
     */
    public static void setIndexedEnabled(CommandContext ctx, int capability, int index, boolean enabled) {
        VulkanicCapability.fromLegacyGlConstant(capability)
            .ifPresentOrElse(
                typedCapability -> setIndexedEnabled(ctx, typedCapability, index, enabled),
                () -> getBackend().setIndexedEnabled(ctx, capability, index, enabled)
            );
    }

    public static void setIndexedEnabled(CommandContext ctx, VulkanicCapability capability, int index, boolean enabled) {
        getBackend().setIndexedEnabled(ctx, capability, index, enabled);
    }
    
    /**
     * Sets the face culling mode.
     * 
     * @param ctx Command context for recording this command
     * @param mode The face culling mode
     */
    public static void setCullFaceMode(CommandContext ctx, int mode) {
        VulkanicCullFaceMode.fromLegacyGlConstant(mode)
            .ifPresentOrElse(
                typedMode -> setCullFaceMode(ctx, typedMode),
                () -> getBackend().setCullFaceMode(ctx, mode)
            );
    }

    public static void setCullFaceMode(CommandContext ctx, VulkanicCullFaceMode mode) {
        getBackend().setCullFaceMode(ctx, mode);
    }
    
    /**
     * Binds a shader program for use.
     * 
     * @param ctx Command context for recording this command
     * @param programId The shader program ID
     */
    public static void bindShaderProgram(CommandContext ctx, int programId) {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        if (directVulkanBackend != null) {
            directVulkanBackend.bindShaderProgram(ctx, programId);
            return;
        }
        getBackend().bindShaderProgram(ctx, programId);
    }
    
    /**
     * Sets a capability enabled or disabled.
     * 
     * @param ctx Command context for recording this command
     * @param cap The capability
     * @param enabled True to enable, false to disable
     */
    public static void setCapabilityEnabled(CommandContext ctx, int cap, boolean enabled) {
        VulkanicCapability.fromLegacyGlConstant(cap)
            .ifPresentOrElse(
                capability -> setCapabilityEnabled(ctx, capability, enabled),
                () -> getBackend().setCapabilityEnabled(ctx, cap, enabled)
            );
    }

    public static void setCapabilityEnabled(CommandContext ctx, VulkanicCapability capability, boolean enabled) {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        if (directVulkanBackend != null) {
            directVulkanBackend.setCapabilityEnabled(ctx, capability, enabled);
            return;
        }
        getBackend().setCapabilityEnabled(ctx, capability, enabled);
    }

    /**
     * Enables or disables synchronous debug output.
     */
    public static void setDebugOutputSynchronousEnabled(CommandContext ctx, boolean enabled) {
        setCapabilityEnabled(ctx, VulkanicCapability.DEBUG_OUTPUT_SYNCHRONOUS, enabled);
    }

    /**
     * Enables or disables debug output generation.
     */
    public static void setDebugOutputEnabled(CommandContext ctx, boolean enabled) {
        setCapabilityEnabled(ctx, VulkanicCapability.DEBUG_OUTPUT, enabled);
    }

    /**
     * Returns true when the current context has the debug flag set.
     */
    public static boolean isDebugContext(CommandContext ctx) {
        return (getBackend().getInteger(ctx, GL_CONTEXT_FLAGS) & GL_CONTEXT_FLAG_DEBUG_BIT) != 0;
    }
    
    /**
     * Binds a 2D texture to the current texture unit.
     * 
     * @param ctx Command context for recording this command
     * @param textureId The texture ID to bind
     */
    public static void bindTexture2D(CommandContext ctx, int textureId) {
        dispatchImplementedVoid(
            direct -> direct.bindTexture2D(ctx, textureId),
            activeBackend -> activeBackend.bindTexture2D(ctx, textureId)
        );
    }

    public static void bindTexture(CommandContext ctx, VulkanicTextureTarget target, int textureId) {
        dispatchImplementedVoid(
            direct -> direct.bindTexture(ctx, target, textureId),
            activeBackend -> activeBackend.bindTexture(ctx, target, textureId)
        );
    }
    
    public static void bindTexture(CommandContext ctx, int target, int textureId) {
        VulkanicTextureTarget.fromLegacyGlTarget(target)
            .ifPresentOrElse(
                typedTarget -> bindTexture(ctx, typedTarget, textureId),
                () -> dispatchImplementedVoid(
                    direct -> direct.bindTexture(ctx, target, textureId),
                    activeBackend -> activeBackend.bindTexture(ctx, target, textureId)
                )
            );
    }

    /**
     * Binds a cubemap texture to the current texture unit.
     *
     * <p>Transitional convenience wrapper to reduce target-specific integer usage in callsites.
     */
    public static void bindCubemapTexture(CommandContext ctx, int textureId) {
        bindTexture(ctx, VulkanicTextureTarget.TEXTURE_CUBE_MAP, textureId);
    }

    /**
     * Binds a texture buffer object to the current texture unit.
     *
     * <p>Transitional convenience wrapper to reduce target-specific integer usage in callsites.
     */
    public static void bindTextureBuffer(CommandContext ctx, int textureId) {
        bindTexture(ctx, VulkanicTextureTarget.TEXTURE_BUFFER, textureId);
    }
    
    public static void bindSampler(CommandContext ctx, int unit, int sampler) {
        dispatchImplementedVoid(
            direct -> direct.bindSampler(ctx, unit, sampler),
            activeBackend -> activeBackend.bindSampler(ctx, unit, sampler)
        );
    }
    
    /**
     * Sets the depth test comparison function.
     * 
     * @param ctx Command context for recording this command
     * @param func The depth comparison function
     */
    public static void setDepthTest(CommandContext ctx, int func) {
        VulkanicDepthCompareOp.fromLegacyGlConstant(func)
            .ifPresentOrElse(
                typedFunc -> setDepthTest(ctx, typedFunc),
                () -> getBackend().setDepthTest(ctx, func)
            );
    }

    public static void setDepthTest(CommandContext ctx, VulkanicDepthCompareOp func) {
        getBackend().setDepthTest(ctx, func);
    }
    
    /**
     * Sets the depth write mask.
     * 
     * @param ctx Command context for recording this command
     * @param enabled True to enable depth writes, false to disable
     */
    public static void setDepthWriteMask(CommandContext ctx, boolean enabled) {
        getBackend().setDepthWriteMask(ctx, enabled);
    }
    
    /**
     * Sets the color write mask.
     * 
     * @param ctx Command context for recording this command
     * @param r Red channel write enabled
     * @param g Green channel write enabled
     * @param b Blue channel write enabled
     * @param a Alpha channel write enabled
     */
    public static void setColorMask(CommandContext ctx, boolean r, boolean g, boolean b, boolean a) {
        getBackend().setColorMask(ctx, r, g, b, a);
    }
    
    /**
     * Generates mipmaps for a texture.
     * 
     * @param ctx Command context for recording this command
     * @param target The texture target
     */
    public static void generateTextureMipmap(CommandContext ctx, int target) {
        getBackend().generateTextureMipmap(ctx, target);
    }
    
    /**
     * Sets pixel storage mode parameters.
     * 
     * @param ctx Command context for recording this command
     * @param pname The pixel storage parameter to set
     * @param value The value to set
     */
    public static void setPixelStore(CommandContext ctx, int pname, int value) {
        getBackend().setPixelStore(ctx, pname, value);
    }
    
    /**
     * Binds a framebuffer object.
     * 
     * @param ctx Command context for recording this command
     * @param target The framebuffer target (read, draw, or both)
     * @param fbo The framebuffer object ID
     */
    public static void bindFramebuffer(CommandContext ctx, int target, int fbo) {
        if (target == GL_READ_FRAMEBUFFER) {
            if (readFramebufferBinding != fbo) {
                dispatchImplementedVoid(
                    direct -> direct.bindFramebuffer(ctx, target, fbo),
                    activeBackend -> activeBackend.bindFramebuffer(ctx, target, fbo)
                );
                readFramebufferBinding = fbo;
            }
            return;
        }

        if (target == GL_DRAW_FRAMEBUFFER) {
            if (drawFramebufferBinding != fbo) {
                dispatchImplementedVoid(
                    direct -> direct.bindFramebuffer(ctx, target, fbo),
                    activeBackend -> activeBackend.bindFramebuffer(ctx, target, fbo)
                );
                drawFramebufferBinding = fbo;
            }
            return;
        }

        if (target == GL_FRAMEBUFFER) {
            if (readFramebufferBinding != fbo || drawFramebufferBinding != fbo) {
                dispatchImplementedVoid(
                    direct -> direct.bindFramebuffer(ctx, target, fbo),
                    activeBackend -> activeBackend.bindFramebuffer(ctx, target, fbo)
                );
                readFramebufferBinding = fbo;
                drawFramebufferBinding = fbo;
            }
            return;
        }

        dispatchImplementedVoid(
            direct -> direct.bindFramebuffer(ctx, target, fbo),
            activeBackend -> activeBackend.bindFramebuffer(ctx, target, fbo)
        );
    }

    /**
     * Binds both read and draw framebuffers to the same FBO.
     */
    public static void bindFramebuffer(CommandContext ctx, int fbo) {
        bindFramebuffer(ctx, GL_FRAMEBUFFER, fbo);
    }

    /**
     * Binds framebuffer 0 for both read and draw targets.
     */
    public static void bindDefaultFramebuffer(CommandContext ctx) {
        bindFramebuffer(ctx, 0);
    }

    /**
     * Binds a render target described by a color/depth texture pair.
     */
    public static void bindRenderTarget(CommandContext ctx, @Nullable GpuTexture colorTexture, @Nullable GpuTexture depthTexture) {
        if (!(colorTexture instanceof VulkanicTexture colorTarget)) {
            bindDefaultFramebuffer(ctx);
            return;
        }

        VulkanicTexture depthTarget = depthTexture instanceof VulkanicTexture texture ? texture : null;
        getBackend().bindRenderTarget(ctx, colorTarget, depthTarget);

        int framebuffer = dispatchImplementedValue(
            direct -> direct.resolveFramebufferForTextures(ctx, colorTarget, depthTarget),
            activeBackend -> activeBackend.resolveFramebufferForTextures(ctx, colorTarget, depthTarget)
        );
        readFramebufferBinding = framebuffer;
        drawFramebufferBinding = framebuffer;
    }

    /**
     * Binds the read framebuffer target.
     */
    public static void bindReadFramebuffer(CommandContext ctx, int fbo) {
        bindFramebuffer(ctx, GL_READ_FRAMEBUFFER, fbo);
    }

    /**
     * Binds the draw framebuffer target.
     */
    public static void bindDrawFramebuffer(CommandContext ctx, int fbo) {
		bindFramebuffer(ctx, GL_DRAW_FRAMEBUFFER, fbo);
        }

        /**
         * Gets cached bound read framebuffer ID.
         */
        public static int getReadFramebufferBinding() {
		return readFramebufferBinding;
        }

        /**
         * Gets cached bound draw framebuffer ID.
         */
        public static int getDrawFramebufferBinding() {
		return drawFramebufferBinding;
        }

        /**
         * Gets cached framebuffer binding for a target.
         */
        public static int getFramebufferBinding(int target) {
		if (target == GL_READ_FRAMEBUFFER) {
			return readFramebufferBinding;
		}

		return target == GL_DRAW_FRAMEBUFFER ? drawFramebufferBinding : 0;
    }
    
    /**
     * Binds a buffer object to a target.
     * 
     * @param ctx Command context for recording this command
     * @param target The buffer target
     * @param buffer The buffer object ID
     */
    public static void bindBuffer(CommandContext ctx, int target, int buffer) {
        VulkanicBufferTarget.fromLegacyGlTarget(target)
            .ifPresentOrElse(
                typedTarget -> bindBuffer(ctx, typedTarget, buffer),
                () -> dispatchImplementedVoid(
                    direct -> direct.bindBuffer(ctx, target, buffer),
                    activeBackend -> activeBackend.bindBuffer(ctx, target, buffer)
                )
            );
    }

    public static void bindBuffer(CommandContext ctx, VulkanicBufferTarget target, int buffer) {
        dispatchImplementedVoid(
            direct -> direct.bindBuffer(ctx, target, buffer),
            activeBackend -> activeBackend.bindBuffer(ctx, target, buffer)
        );
    }

    /**
     * Binds the pixel-pack buffer used for texture readbacks.
     */
    public static void bindPixelPackBuffer(CommandContext ctx, int buffer) {
        bindBuffer(ctx, VulkanicBufferTarget.PIXEL_PACK, buffer);
    }

    /**
     * Binds the copy-read buffer target.
     */
    public static void bindCopyReadBuffer(CommandContext ctx, int buffer) {
        bindBuffer(ctx, VulkanicBufferTarget.COPY_READ, buffer);
    }

    /**
     * Binds the copy-write buffer target.
     */
    public static void bindCopyWriteBuffer(CommandContext ctx, int buffer) {
        bindBuffer(ctx, VulkanicBufferTarget.COPY_WRITE, buffer);
    }

    /**
     * Binds an index buffer using backend-agnostic intent.
     *
     * <p>In OpenGL this binds {@code GL_ELEMENT_ARRAY_BUFFER}. In Vulkan this maps to
     * index-buffer binding semantics for the active command context.
     */
    public static void bindIndexBuffer(CommandContext ctx, int buffer) {
        bindBuffer(ctx, VulkanicBufferTarget.INDEX, buffer);
    }
    
    /**
     * Binds a buffer to an indexed buffer target.
     * 
     * @param ctx Command context for recording this command
     * @param target The buffer target
     * @param index The index of the binding point
     * @param buffer The buffer object ID
     */
    public static void bindBufferBase(CommandContext ctx, int target, int index, int buffer) {
        VulkanicBufferTarget.fromLegacyGlTarget(target)
            .ifPresentOrElse(
                typedTarget -> bindBufferBase(ctx, typedTarget, index, buffer),
                () -> dispatchImplementedVoid(
                    direct -> direct.bindBufferBase(ctx, target, index, buffer),
                    activeBackend -> activeBackend.bindBufferBase(ctx, target, index, buffer)
                )
            );
    }

    public static void bindBufferBase(CommandContext ctx, VulkanicBufferTarget target, int index, int buffer) {
        dispatchImplementedVoid(
            direct -> direct.bindBufferBase(ctx, target.toLegacyGlTarget(), index, buffer),
            activeBackend -> activeBackend.bindBufferBase(ctx, target, index, buffer)
        );
    }
    
    /**
     * Sets the active texture unit.
     * 
     * @param ctx Command context for recording this command
     * @param unit The texture unit to activate
     */
    public static void setActiveTextureUnit(CommandContext ctx, int unit) {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        if (directVulkanBackend != null) {
            directVulkanBackend.setActiveTextureUnit(ctx, unit);
            return;
        }
        getBackend().setActiveTextureUnit(ctx, unit);
    }

    /**
     * Sets the active texture unit by index (0 -> texture unit 0, etc.).
     */
    public static void setActiveTextureUnitIndex(CommandContext ctx, int unitIndex) {
        setActiveTextureUnit(ctx, textureUnitFromIndex(unitIndex));
    }

    /**
     * Returns a GL texture-unit enum from a zero-based texture unit index.
     */
    public static int textureUnitFromIndex(int unitIndex) {
        return GL_TEXTURE0 + unitIndex;
    }

    /**
     * Returns a zero-based texture unit index from a GL texture-unit enum.
     */
    public static int textureUnitToIndex(int textureUnit) {
        return textureUnit - GL_TEXTURE0;
    }
    
    /**
     * Sets a texture parameter.
     * 
     * @param ctx Command context for recording this command
     * @param target The texture target
     * @param pname The parameter name
     * @param param The parameter value
     */
    public static void setTextureParameter(CommandContext ctx, int target, int pname, int param) {
        VulkanicTextureTarget.fromLegacyGlTarget(target)
            .ifPresentOrElse(
                typedTarget -> VulkanicTextureParameterName.fromLegacyGlPName(pname)
                    .ifPresentOrElse(
                        typedParameterName -> setTextureParameter(ctx, typedTarget, typedParameterName, param),
                        () -> setTextureParameterRaw(ctx, target, pname, param)
                    ),
                () -> setTextureParameterRaw(ctx, target, pname, param)
            );
    }

    public static void setTextureParameter(
        CommandContext ctx,
        int target,
        VulkanicTextureParameterName pname,
        int param
    ) {
        VulkanicTextureTarget.fromLegacyGlTarget(target)
            .ifPresentOrElse(
                typedTarget -> setTextureParameter(ctx, typedTarget, pname, param),
                () -> setTextureParameterRaw(ctx, target, pname.toLegacyGlPName(), param)
            );
    }

    public static void setTextureParameter(
        CommandContext ctx,
        int target,
        VulkanicTextureParameterName pname,
        VulkanicTextureParameterValue param
    ) {
        setTextureParameter(ctx, target, pname, param.toLegacyGlConstant());
    }

    public static void setTextureParameter(
        CommandContext ctx,
        VulkanicTextureTarget target,
        VulkanicTextureParameterName pname,
        int param
    ) {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        if (directVulkanBackend != null) {
            directVulkanBackend.setTextureParameter(ctx, target.toLegacyGlTarget(), pname.toLegacyGlPName(), param);
            return;
        }
        getBackend().setTextureParameter(ctx, target, pname, param);
    }

    public static void setTextureParameter(
        CommandContext ctx,
        VulkanicTextureTarget target,
        VulkanicTextureParameterName pname,
        VulkanicTextureParameterValue param
    ) {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        if (directVulkanBackend != null) {
            directVulkanBackend.setTextureParameter(
                ctx,
                target.toLegacyGlTarget(),
                pname.toLegacyGlPName(),
                param.toLegacyGlConstant()
            );
            return;
        }
        getBackend().setTextureParameter(ctx, target, pname, param);
    }

    private static void setTextureParameterRaw(CommandContext ctx, int target, int pname, int param) {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        if (directVulkanBackend != null) {
            directVulkanBackend.setTextureParameter(ctx, target, pname, param);
            return;
        }
        getBackend().setTextureParameter(ctx, target, pname, param);
    }

    /**
     * Sets the maximum mip level that can be sampled from a texture target.
     */
    public static void setTextureMaxLevel(CommandContext ctx, int target, int maxLevel) {
        setTextureParameter(ctx, target, VulkanicTextureParameterName.MAX_LEVEL, maxLevel);
    }

    /**
     * Sets the maximum mip level that can be sampled from a texture target.
     */
    public static void setTextureMaxLevel(CommandContext ctx, VulkanicTextureTarget target, int maxLevel) {
        setTextureParameter(ctx, target, VulkanicTextureParameterName.MAX_LEVEL, maxLevel);
    }

    /**
     * Sets the minimum level-of-detail clamp for a texture target.
     */
    public static void setTextureMinLod(CommandContext ctx, int target, int minLod) {
        setTextureParameter(ctx, target, VulkanicTextureParameterName.MIN_LOD, minLod);
    }

    /**
     * Sets the minimum level-of-detail clamp for a texture target.
     */
    public static void setTextureMinLod(CommandContext ctx, VulkanicTextureTarget target, int minLod) {
        setTextureParameter(ctx, target, VulkanicTextureParameterName.MIN_LOD, minLod);
    }

    /**
     * Sets the maximum level-of-detail clamp for a texture target.
     */
    public static void setTextureMaxLod(CommandContext ctx, int target, int maxLod) {
        setTextureParameter(ctx, target, VulkanicTextureParameterName.MAX_LOD, maxLod);
    }

    /**
     * Sets the maximum level-of-detail clamp for a texture target.
     */
    public static void setTextureMaxLod(CommandContext ctx, VulkanicTextureTarget target, int maxLod) {
        setTextureParameter(ctx, target, VulkanicTextureParameterName.MAX_LOD, maxLod);
    }

    /**
     * Sets both min and mag filters to linear sampling for a texture target.
     */
    public static void setTextureLinearFiltering(CommandContext ctx, int target) {
        setTextureParameter(ctx, target, VulkanicTextureParameterName.MIN_FILTER, VulkanicTextureParameterValue.LINEAR);
        setTextureParameter(ctx, target, VulkanicTextureParameterName.MAG_FILTER, VulkanicTextureParameterValue.LINEAR);
    }

    /**
     * Sets both min and mag filters to linear sampling for a texture target.
     */
    public static void setTextureLinearFiltering(CommandContext ctx, VulkanicTextureTarget target) {
        setTextureParameter(ctx, target, VulkanicTextureParameterName.MIN_FILTER, VulkanicTextureParameterValue.LINEAR);
        setTextureParameter(ctx, target, VulkanicTextureParameterName.MAG_FILTER, VulkanicTextureParameterValue.LINEAR);
    }

    /**
     * Sets both min and mag filters to nearest sampling for a texture target.
     */
    public static void setTextureNearestFiltering(CommandContext ctx, int target) {
        setTextureParameter(ctx, target, VulkanicTextureParameterName.MIN_FILTER, VulkanicTextureParameterValue.NEAREST);
        setTextureParameter(ctx, target, VulkanicTextureParameterName.MAG_FILTER, VulkanicTextureParameterValue.NEAREST);
    }

    /**
     * Sets both min and mag filters to nearest sampling for a texture target.
     */
    public static void setTextureNearestFiltering(CommandContext ctx, VulkanicTextureTarget target) {
        setTextureParameter(ctx, target, VulkanicTextureParameterName.MIN_FILTER, VulkanicTextureParameterValue.NEAREST);
        setTextureParameter(ctx, target, VulkanicTextureParameterName.MAG_FILTER, VulkanicTextureParameterValue.NEAREST);
    }

    /**
     * Applies wrap mode for S and optionally T/R coordinates on a texture target.
     */
    public static void setTextureWrapMode(CommandContext ctx, int target, boolean clampToEdge, boolean includeWrapT, boolean includeWrapR) {
        VulkanicTextureParameterValue wrapMode = clampToEdge
            ? VulkanicTextureParameterValue.CLAMP_TO_EDGE
            : VulkanicTextureParameterValue.REPEAT;
        setTextureParameter(ctx, target, VulkanicTextureParameterName.WRAP_S, wrapMode);

        if (includeWrapT) {
            setTextureParameter(ctx, target, VulkanicTextureParameterName.WRAP_T, wrapMode);
        }

        if (includeWrapR) {
            setTextureParameter(ctx, target, VulkanicTextureParameterName.WRAP_R, wrapMode);
        }
    }

    /**
     * Applies wrap mode for S and optionally T/R coordinates on a texture target.
     */
    public static void setTextureWrapMode(CommandContext ctx, VulkanicTextureTarget target, boolean clampToEdge, boolean includeWrapT, boolean includeWrapR) {
        VulkanicTextureParameterValue wrapMode = clampToEdge
            ? VulkanicTextureParameterValue.CLAMP_TO_EDGE
            : VulkanicTextureParameterValue.REPEAT;
        setTextureParameter(ctx, target, VulkanicTextureParameterName.WRAP_S, wrapMode);

        if (includeWrapT) {
            setTextureParameter(ctx, target, VulkanicTextureParameterName.WRAP_T, wrapMode);
        }

        if (includeWrapR) {
            setTextureParameter(ctx, target, VulkanicTextureParameterName.WRAP_R, wrapMode);
        }
    }

    /**
     * Resets texture LOD state to base level 0 with no LOD bias.
     */
    public static void resetTextureLodRangeToZero(CommandContext ctx, int target) {
        setTextureMaxLevel(ctx, target, 0);
        setTextureMinLod(ctx, target, 0);
        setTextureMaxLod(ctx, target, 0);
        texParameterf(ctx, target, GL_TEXTURE_LOD_BIAS, 0.0F);
    }

    /**
     * Resets texture LOD state to base level 0 with no LOD bias.
     */
    public static void resetTextureLodRangeToZero(CommandContext ctx, VulkanicTextureTarget target) {
        setTextureMaxLevel(ctx, target, 0);
        setTextureMinLod(ctx, target, 0);
        setTextureMaxLod(ctx, target, 0);
        texParameterf(ctx, target.toLegacyGlTarget(), GL_TEXTURE_LOD_BIAS, 0.0F);
    }

    /**
     * Disables depth-compare mode for a depth texture target.
     */
    public static void disableTextureCompareMode(CommandContext ctx, int target) {
        setTextureParameter(ctx, target, VulkanicTextureParameterName.COMPARE_MODE, GL_NONE);
    }

    /**
     * Disables depth-compare mode for a depth texture target.
     */
    public static void disableTextureCompareMode(CommandContext ctx, VulkanicTextureTarget target) {
        setTextureParameter(ctx, target, VulkanicTextureParameterName.COMPARE_MODE, GL_NONE);
    }

    public static void useDepthAspectForDepthStencilTexture(CommandContext ctx, VulkanicTextureTarget target) {
        setTextureParameter(ctx, target, VulkanicTextureParameterName.DEPTH_STENCIL_TEXTURE_MODE, GL_DEPTH_COMPONENT);
    }
    
    /**
     * Copies a region from the framebuffer to a texture subregion.
     * 
     * @param ctx Command context for recording this command
     * @param target The texture target
     * @param level The mipmap level
     * @param xoffset The x offset in the texture
     * @param yoffset The y offset in the texture
     * @param x The x coordinate in the framebuffer
     * @param y The y coordinate in the framebuffer
     * @param width The width of the region
     * @param height The height of the region
     */
    public static void copyTexSubImage2D(CommandContext ctx, int target, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
        getBackend().copyTexSubImage2D(ctx, target, level, xoffset, yoffset, x, y, width, height);
    }

    /**
     * Copies a region from the framebuffer to the currently bound 2D texture.
     */
    public static void copyTexSubImage2D(CommandContext ctx, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
        getBackend().copyTexSubImage2D(ctx, GL_TEXTURE_2D, level, xoffset, yoffset, x, y, width, height);
    }
    
    /**
     * Gets a texture parameter value.
     * 
     * @param ctx Command context for recording this command
     * @param target The texture target
     * @param pname The parameter name
     * @return The parameter value
     */
    public static int getTexParameteri(CommandContext ctx, int target, int pname) {
        return getBackend().getTexParameteri(ctx, target, pname);
    }
    
    /**
     * Attaches a texture to a framebuffer attachment point.
     * 
     * @param ctx Command context for recording this command
     * @param target The framebuffer target
     * @param attachment The attachment point
     * @param textarget The texture target
     * @param texture The texture object ID
     * @param level The mipmap level
     */
    public static void framebufferTexture(CommandContext ctx, int target, int attachment, int textarget, int texture, int level) {
        getBackend().framebufferTexture(ctx, target, attachment, textarget, texture, level);
    }
    
    /**
     * Attaches a 2D texture image to a framebuffer attachment point.
     * 
     * @param ctx Command context for recording this command
     * @param target The framebuffer target
     * @param attachment The attachment point
     * @param textarget The texture target
     * @param texture The texture object ID
     * @param level The mipmap level
     */
    public static void framebufferTexture2D(CommandContext ctx, int target, int attachment, int textarget, int texture, int level) {
        getBackend().framebufferTexture2D(ctx, target, attachment, textarget, texture, level);
    }

    /**
     * Attaches a 2D texture image to a framebuffer attachment point.
     */
    public static void framebufferTexture2D(CommandContext ctx, int target, int attachment, int texture, int level) {
        getBackend().framebufferTexture2D(ctx, target, attachment, GL_TEXTURE_2D, texture, level);
    }

    /**
     * Attaches a 2D texture image to an attachment point on GL_FRAMEBUFFER.
     */
    public static void framebufferTexture2D(CommandContext ctx, int attachment, int texture, int level) {
        framebufferTexture2D(ctx, GL_FRAMEBUFFER, attachment, texture, level);
    }

    /**
     * Returns the framebuffer color-attachment enum for a zero-based attachment index.
     */
    public static int colorAttachment(int colorAttachmentIndex) {
        return GL_COLOR_ATTACHMENT0 + colorAttachmentIndex;
    }

    /**
     * Attaches a 2D texture to a framebuffer color attachment by index.
     */
    public static void framebufferColorAttachmentTexture2D(CommandContext ctx, int target, int colorAttachmentIndex, int texture, int level) {
        framebufferTexture2D(ctx, target, colorAttachment(colorAttachmentIndex), texture, level);
    }

    /**
     * Attaches a 2D texture to a framebuffer color attachment by index on GL_FRAMEBUFFER.
     */
    public static void framebufferColorAttachmentTexture2D(CommandContext ctx, int colorAttachmentIndex, int texture, int level) {
        framebufferColorAttachmentTexture2D(ctx, GL_FRAMEBUFFER, colorAttachmentIndex, texture, level);
    }

    /**
     * Attaches a 2D texture to color attachment 0 of a framebuffer target.
     */
    public static void framebufferColorAttachment0Texture2D(CommandContext ctx, int target, int texture, int level) {
        getBackend().framebufferTexture2D(ctx, target, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, level);
    }

    /**
     * Attaches a 2D texture to color attachment 1 of a framebuffer target.
     */
    public static void framebufferColorAttachment1Texture2D(CommandContext ctx, int target, int texture, int level) {
        getBackend().framebufferTexture2D(ctx, target, GL_COLOR_ATTACHMENT1, GL_TEXTURE_2D, texture, level);
    }

    /**
     * Attaches a 2D texture to the depth attachment of a framebuffer target.
     */
    public static void framebufferDepthAttachmentTexture2D(CommandContext ctx, int target, int texture, int level) {
        getBackend().framebufferTexture2D(ctx, target, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, texture, level);
    }

    /**
     * Attaches a 2D texture to color attachment 0 of the default framebuffer target.
     */
    public static void framebufferColorAttachment0Texture2D(CommandContext ctx, int texture, int level) {
        framebufferColorAttachment0Texture2D(ctx, GL_FRAMEBUFFER, texture, level);
    }

    /**
     * Attaches a 2D texture to color attachment 1 of the default framebuffer target.
     */
    public static void framebufferColorAttachment1Texture2D(CommandContext ctx, int texture, int level) {
        framebufferColorAttachment1Texture2D(ctx, GL_FRAMEBUFFER, texture, level);
    }

    /**
     * Attaches a 2D texture to the depth attachment of the default framebuffer target.
     */
    public static void framebufferDepthAttachmentTexture2D(CommandContext ctx, int texture, int level) {
        framebufferDepthAttachmentTexture2D(ctx, GL_FRAMEBUFFER, texture, level);
    }
    
    /**
     * Specifies a list of color buffers to be drawn into.
     * 
     * @param ctx Command context for recording this command
     * @param buffers Array of buffers to draw into
     */
    public static void drawBuffers(CommandContext ctx, int[] buffers) {
        getBackend().drawBuffers(ctx, buffers);
    }
    
    /**
     * Sets the blend function for source and destination blend factors.
     * 
     * @param ctx Command context for recording this command
     * @param sfactor Source blend factor
     * @param dfactor Destination blend factor
     */
    public static void blendFunc(CommandContext ctx, int sfactor, int dfactor) {
        java.util.Optional<VulkanicBlendFactor> typedSFactor = VulkanicBlendFactor.fromLegacyGlConstant(sfactor);
        java.util.Optional<VulkanicBlendFactor> typedDFactor = VulkanicBlendFactor.fromLegacyGlConstant(dfactor);

        if (typedSFactor.isPresent() && typedDFactor.isPresent()) {
            blendFunc(ctx, typedSFactor.get(), typedDFactor.get());
            return;
        }

        getBackend().blendFunc(ctx, sfactor, dfactor);
    }

    public static void blendFunc(CommandContext ctx, VulkanicBlendFactor sfactor, VulkanicBlendFactor dfactor) {
        getBackend().blendFunc(ctx, sfactor, dfactor);
    }
    
    /**
     * Queries an integer state variable.
     * 
     * @param ctx Command context for recording this command
     * @param pname The parameter name to query
     * @return The queried integer value
     */
    public static int getInteger(CommandContext ctx, int pname) {
        return VulkanicIntegerQuery.fromLegacyGlPName(pname)
            .map(query -> getInteger(ctx, query))
            .orElseGet(() -> getBackend().getInteger(ctx, pname));
    }

    public static int getInteger(CommandContext ctx, VulkanicIntegerQuery query) {
        return getBackend().getInteger(ctx, query);
    }

    /**
     * Queries a boolean-like integer state variable.
     */
    public static boolean getBoolean(CommandContext ctx, VulkanicIntegerQuery query) {
        return getInteger(ctx, query) != 0;
    }

    /**
     * Queries the required alignment for uniform-buffer range offsets.
     */
    public static int getUniformBufferOffsetAlignment(CommandContext ctx) {
        return getBackend().getInteger(ctx, GL_UNIFORM_BUFFER_OFFSET_ALIGNMENT);
    }

    /**
     * Enables or disables program point-size behavior.
     */
    public static void setProgramPointSizeEnabled(CommandContext ctx, boolean enabled) {
        setCapabilityEnabled(ctx, VulkanicCapability.PROGRAM_POINT_SIZE, enabled);
    }

    /**
     * Enables or disables face culling.
     */
    public static void setCullFaceEnabled(CommandContext ctx, boolean enabled) {
        setCapabilityEnabled(ctx, VulkanicCapability.CULL_FACE, enabled);
    }

    /**
     * Enables or disables depth testing.
     */
    public static void setDepthTestEnabled(CommandContext ctx, boolean enabled) {
        setCapabilityEnabled(ctx, VulkanicCapability.DEPTH_TEST, enabled);
    }

    /**
     * Enables or disables scissor testing.
     */
    public static void setScissorTestEnabled(CommandContext ctx, boolean enabled) {
        setCapabilityEnabled(ctx, VulkanicCapability.SCISSOR_TEST, enabled);
    }

    /**
     * Enables or disables polygon offset for filled primitives.
     */
    public static void setPolygonOffsetFillEnabled(CommandContext ctx, boolean enabled) {
        setCapabilityEnabled(ctx, VulkanicCapability.POLYGON_OFFSET_FILL, enabled);
    }

    /**
     * Enables or disables color-logic operations.
     */
    public static void setColorLogicOpEnabled(CommandContext ctx, boolean enabled) {
        setCapabilityEnabled(ctx, VulkanicCapability.COLOR_LOGIC_OP, enabled);
    }
    
    /**
     * Sets uniform values for a vec3 shader variable.
     * 
     * @param ctx Command context for recording this command
     * @param location The uniform location
     * @param v0 The first component value
     * @param v1 The second component value
     * @param v2 The third component value
     */
    public static void setUniform3f(CommandContext ctx, int location, float v0, float v1, float v2) {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        if (directVulkanBackend != null) {
            directVulkanBackend.setUniform3f(ctx, location, v0, v1, v2);
            return;
        }
        getBackend().setUniform3f(ctx, location, v0, v1, v2);
    }
    
    /**
     * Sets the clear color value.
     * 
     * @param ctx Command context for recording this command
     * @param r Red component
     * @param g Green component
     * @param b Blue component
     * @param a Alpha component
     */
    public static void setClearColor(CommandContext ctx, float r, float g, float b, float a) {
        getBackend().setClearColor(ctx, r, g, b, a);
    }
    
    /**
     * Sets the viewport transformation.
     * 
     * @param ctx Command context for recording this command
     * @param x The lower left corner x coordinate
     * @param y The lower left corner y coordinate
     * @param width The viewport width
     * @param height The viewport height
     */
    public static void setViewport(CommandContext ctx, int x, int y, int width, int height) {
        getBackend().setViewport(ctx, x, y, width, height);
    }
    
    /**
     * Sets the polygon rasterization mode.
     * 
     * @param ctx Command context for recording this command
     * @param face Which polygons the mode applies to
     * @param mode The rasterization mode
     */
    public static void setPolygonMode(CommandContext ctx, int face, int mode) {
        java.util.Optional<VulkanicPolygonFace> typedFace = VulkanicPolygonFace.fromLegacyGlConstant(face);
        java.util.Optional<VulkanicPolygonMode> typedMode = VulkanicPolygonMode.fromLegacyGlConstant(mode);
        if (typedFace.isPresent() && typedMode.isPresent()) {
            setPolygonMode(ctx, typedFace.get(), typedMode.get());
            return;
        }

        getBackend().setPolygonMode(ctx, face, mode);
    }

    /**
     * Sets the polygon rasterization mode using backend-neutral typed arguments.
     */
    public static void setPolygonMode(CommandContext ctx, VulkanicPolygonFace face, VulkanicPolygonMode mode) {
        getBackend().setPolygonMode(ctx, face.toGlFaceConstant(), mode.toGlModeConstant());
    }

    /**
     * Sets the polygon rasterization mode for a typed face with a legacy mode constant.
     */
    public static void setPolygonMode(CommandContext ctx, VulkanicPolygonFace face, int mode) {
        VulkanicPolygonMode.fromLegacyGlConstant(mode)
            .ifPresentOrElse(
                typedMode -> setPolygonMode(ctx, face, typedMode),
                () -> getBackend().setPolygonMode(ctx, face.toGlFaceConstant(), mode)
            );
    }
    
    /**
     * Sets the polygon offset parameters for depth offset calculation.
     * 
     * @param ctx Command context for recording this command
     * @param factor Scale factor for variable depth offset
     * @param units Scale factor for constant depth offset
     */
    public static void setPolygonOffset(CommandContext ctx, float factor, float units) {
        getBackend().setPolygonOffset(ctx, factor, units);
    }
    
    /**
     * Sets the logical operation for color blending.
     * 
     * @param ctx Command context for recording this command
     * @param opcode The logical operation
     */
    public static void setLogicOp(CommandContext ctx, int opcode) {
        VulkanicLogicOp.fromLegacyGlConstant(opcode)
            .ifPresentOrElse(
                typedOp -> setLogicOp(ctx, typedOp),
                () -> getBackend().setLogicOp(ctx, opcode)
            );
    }

    /**
     * Sets the logical operation using backend-neutral logic-op semantics.
     */
    public static void setLogicOp(CommandContext ctx, VulkanicLogicOp opcode) {
        getBackend().setLogicOp(ctx, opcode);
    }
    
    /**
     * Creates a new framebuffer object.
     * 
     * @param ctx Command context for recording this command
     * @return The framebuffer object ID
     */
    public static int createFramebuffer(CommandContext ctx) {
        return getBackend().createFramebuffer(ctx);
    }
    
    // Direct State Access buffer operations
    // CommandContext versions of DSA buffer operations
    public static int createBufferDSA(CommandContext ctx) {
        return dispatchImplementedValue(
            direct -> direct.createBufferDSA(ctx),
            activeBackend -> activeBackend.createBufferDSA(ctx)
        );
    }
    
    public static void namedBufferDataDSA(CommandContext ctx, int buffer, long size, int usage) {
        dispatchImplementedVoid(
            direct -> direct.namedBufferDataDSA(ctx, buffer, size, usage),
            activeBackend -> activeBackend.namedBufferDataDSA(ctx, buffer, size, usage)
        );
    }
    
    public static void namedBufferDataDSA(CommandContext ctx, int buffer, java.nio.ByteBuffer data, int usage) {
        dispatchImplementedVoid(
            direct -> direct.namedBufferDataDSA(ctx, buffer, data, usage),
            activeBackend -> activeBackend.namedBufferDataDSA(ctx, buffer, data, usage)
        );
    }
    
    public static void namedBufferSubDataDSA(CommandContext ctx, int buffer, long offset, java.nio.ByteBuffer data) {
        dispatchImplementedVoid(
            direct -> direct.namedBufferSubDataDSA(ctx, buffer, offset, data),
            activeBackend -> activeBackend.namedBufferSubDataDSA(ctx, buffer, offset, data)
        );
    }
    
    public static void namedBufferStorageDSA(CommandContext ctx, int buffer, long size, int flags) {
        getBackend().namedBufferStorageDSA(ctx, buffer, size, flags);
    }
    
    public static void namedBufferStorageDSA(CommandContext ctx, int buffer, java.nio.ByteBuffer data, int flags) {
        getBackend().namedBufferStorageDSA(ctx, buffer, data, flags);
    }
    
    public static java.nio.ByteBuffer mapNamedBufferRangeDSA(CommandContext ctx, int buffer, long offset, long length, int access) {
        return dispatchImplementedValue(
            direct -> direct.mapNamedBufferRangeDSA(ctx, buffer, offset, length, access),
            activeBackend -> activeBackend.mapNamedBufferRangeDSA(ctx, buffer, offset, length, access)
        );
    }
    
    // CommandContext versions of DSA operations
    /**
     * Unmaps a previously mapped buffer using Direct State Access (DSA).
     * @param ctx Command context for recording this command
     * @param buffer The buffer object to unmap
     */
    public static void unmapNamedBufferDSA(CommandContext ctx, int buffer) {
        dispatchImplementedVoid(
            direct -> direct.unmapNamedBufferDSA(ctx, buffer),
            activeBackend -> activeBackend.unmapNamedBufferDSA(ctx, buffer)
        );
    }
    
    /**
     * Flushes a range of a mapped buffer using Direct State Access (DSA).
     * @param ctx Command context for recording this command
     * @param buffer The buffer object
     * @param offset Offset within the mapped buffer range
     * @param length Length of the range to flush
     */
    public static void flushMappedNamedBufferRangeDSA(CommandContext ctx, int buffer, long offset, long length) {
        getBackend().flushMappedNamedBufferRangeDSA(ctx, buffer, offset, length);
    }
    
    /**
     * Copies data between buffers using Direct State Access (DSA).
     * @param ctx Command context for recording this command
     * @param readBuffer Source buffer
     * @param writeBuffer Destination buffer
     * @param readOffset Offset in source buffer
     * @param writeOffset Offset in destination buffer
     * @param size Number of bytes to copy
     */
    public static void copyNamedBufferSubDataDSA(CommandContext ctx, int readBuffer, int writeBuffer, long readOffset, long writeOffset, long size) {
        dispatchImplementedVoid(
            direct -> direct.copyNamedBufferSubDataDSA(ctx, readBuffer, writeBuffer, readOffset, writeOffset, size),
            activeBackend -> activeBackend.copyNamedBufferSubDataDSA(ctx, readBuffer, writeBuffer, readOffset, writeOffset, size)
        );
    }
    
    /**
     * Attaches a texture to a framebuffer using Direct State Access (DSA).
     * @param ctx Command context for recording this command
     * @param framebuffer The framebuffer object
     * @param attachment The attachment point (e.g., GL_COLOR_ATTACHMENT0)
     * @param texture The texture to attach
     * @param level The mipmap level of the texture
     */
    public static void namedFramebufferTextureDSA(CommandContext ctx, int framebuffer, int attachment, int texture, int level) {
        getBackend().namedFramebufferTextureDSA(ctx, framebuffer, attachment, texture, level);
    }

    /**
     * Attaches a texture to COLOR_ATTACHMENT0 using DSA.
     */
    public static void namedFramebufferColorAttachment0DSA(CommandContext ctx, int framebuffer, int texture, int level) {
        getBackend().namedFramebufferTextureDSA(ctx, framebuffer, GL_COLOR_ATTACHMENT0, texture, level);
    }

    /**
     * Attaches a texture to DEPTH_ATTACHMENT using DSA.
     */
    public static void namedFramebufferDepthAttachmentDSA(CommandContext ctx, int framebuffer, int texture, int level) {
        getBackend().namedFramebufferTextureDSA(ctx, framebuffer, GL_DEPTH_ATTACHMENT, texture, level);
    }
    
    /**
     * Blits (copies) pixels between framebuffers using Direct State Access (DSA).
     * @param ctx Command context for recording this command
     * @param readFramebuffer Source framebuffer
     * @param drawFramebuffer Destination framebuffer
     * @param srcX0 Source region left
     * @param srcY0 Source region bottom
     * @param srcX1 Source region right
     * @param srcY1 Source region top
     * @param dstX0 Destination region left
     * @param dstY0 Destination region bottom
     * @param dstX1 Destination region right
     * @param dstY1 Destination region top
     * @param mask Buffer mask (GL_COLOR_BUFFER_BIT, etc.)
     * @param filter Interpolation filter (GL_NEAREST or GL_LINEAR)
     */
    public static void blitNamedFramebufferDSA(CommandContext ctx, int readFramebuffer, int drawFramebuffer, int srcX0, int srcY0, int srcX1, int srcY1,
                                                int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        getBackend().blitNamedFramebufferDSA(ctx, readFramebuffer, drawFramebuffer, srcX0, srcY0, srcX1, srcY1,
                                              dstX0, dstY0, dstX1, dstY1, mask, filter);
    }
    
    public static int createTexture2D(CommandContext ctx) {
        return getBackend().createTexture2D(ctx);
    }
    
    public static void deleteTexture(CommandContext ctx, int texture) {
        getBackend().deleteTexture(ctx, texture);
    }
    
    public static boolean isTexture(CommandContext ctx, int texture) {
        return getBackend().isTexture(ctx, texture);
    }
    
    public static void drawArrays(CommandContext ctx, int mode, int first, int count) {
        VulkanicPrimitiveMode.fromLegacyGlConstant(mode)
            .ifPresentOrElse(
                typedMode -> drawArrays(ctx, typedMode, first, count),
                () -> dispatchImplementedVoid(
                    direct -> direct.drawArrays(ctx, mode, first, count),
                    activeBackend -> activeBackend.drawArrays(ctx, mode, first, count)
                )
            );
    }

    /**
     * Draws array primitives using a backend-neutral primitive mode.
     */
    public static void drawArrays(CommandContext ctx, VulkanicPrimitiveMode mode, int first, int count) {
        dispatchImplementedVoid(
            direct -> direct.drawArrays(ctx, mode.toGlModeConstant(), first, count),
            activeBackend -> activeBackend.drawArrays(ctx, mode.toGlModeConstant(), first, count)
        );
    }
    
    public static void drawElements(CommandContext ctx, int mode, int count, int type, long indices) {
        java.util.Optional<VulkanicPrimitiveMode> typedMode = VulkanicPrimitiveMode.fromLegacyGlConstant(mode);
        java.util.Optional<VulkanicIndexType> typedIndexType = VulkanicIndexType.fromLegacyGlConstant(type);
        if (typedMode.isPresent() && typedIndexType.isPresent()) {
            drawElements(ctx, typedMode.get(), count, typedIndexType.get(), indices);
            return;
        }

        dispatchImplementedVoid(
            direct -> direct.drawElements(ctx, mode, count, type, indices),
            activeBackend -> activeBackend.drawElements(ctx, mode, count, type, indices)
        );
    }

    /**
     * Draws indexed primitives using a backend-agnostic index type.
     */
    public static void drawElements(CommandContext ctx, int mode, int count, VulkanicIndexType indexType, long indices) {
        VulkanicPrimitiveMode.fromLegacyGlConstant(mode)
            .ifPresentOrElse(
                typedMode -> drawElements(ctx, typedMode, count, indexType, indices),
                () -> dispatchImplementedVoid(
                    direct -> direct.drawElements(ctx, mode, count, indexType.toGlTypeConstant(), indices),
                    activeBackend -> activeBackend.drawElements(ctx, mode, count, indexType.toGlTypeConstant(), indices)
                )
            );
    }

    /**
     * Draws indexed primitives using a backend-neutral primitive mode with legacy index-type constant.
     */
    public static void drawElements(CommandContext ctx, VulkanicPrimitiveMode mode, int count, int type, long indices) {
        VulkanicIndexType.fromLegacyGlConstant(type)
            .ifPresentOrElse(
                typedIndexType -> drawElements(ctx, mode, count, typedIndexType, indices),
                () -> dispatchImplementedVoid(
                    direct -> direct.drawElements(ctx, mode.toGlModeConstant(), count, type, indices),
                    activeBackend -> activeBackend.drawElements(ctx, mode.toGlModeConstant(), count, type, indices)
                )
            );
    }

    /**
     * Draws indexed primitives using backend-neutral primitive and index types.
     */
    public static void drawElements(CommandContext ctx, VulkanicPrimitiveMode mode, int count, VulkanicIndexType indexType, long indices) {
        dispatchImplementedVoid(
            direct -> direct.drawElements(ctx, mode.toGlModeConstant(), count, indexType.toGlTypeConstant(), indices),
            activeBackend -> activeBackend.drawElements(ctx, mode.toGlModeConstant(), count, indexType.toGlTypeConstant(), indices)
        );
    }
    
    public static void setBlendFunction(CommandContext ctx, int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        java.util.Optional<VulkanicBlendFactor> typedSrcRgb = VulkanicBlendFactor.fromLegacyGlConstant(srcRgb);
        java.util.Optional<VulkanicBlendFactor> typedDstRgb = VulkanicBlendFactor.fromLegacyGlConstant(dstRgb);
        java.util.Optional<VulkanicBlendFactor> typedSrcAlpha = VulkanicBlendFactor.fromLegacyGlConstant(srcAlpha);
        java.util.Optional<VulkanicBlendFactor> typedDstAlpha = VulkanicBlendFactor.fromLegacyGlConstant(dstAlpha);

        if (typedSrcRgb.isPresent() && typedDstRgb.isPresent() && typedSrcAlpha.isPresent() && typedDstAlpha.isPresent()) {
            setBlendFunction(ctx, typedSrcRgb.get(), typedDstRgb.get(), typedSrcAlpha.get(), typedDstAlpha.get());
            return;
        }

        getBackend().setBlendFunction(ctx, srcRgb, dstRgb, srcAlpha, dstAlpha);
    }

    public static void setBlendFunction(
        CommandContext ctx,
        VulkanicBlendFactor srcRgb,
        VulkanicBlendFactor dstRgb,
        VulkanicBlendFactor srcAlpha,
        VulkanicBlendFactor dstAlpha
    ) {
        getBackend().setBlendFunction(ctx, srcRgb, dstRgb, srcAlpha, dstAlpha);
    }
    
    public static void setBlendEquation(CommandContext ctx, int mode) {
        VulkanicBlendEquation.fromLegacyGlConstant(mode)
            .ifPresentOrElse(
                typedMode -> setBlendEquation(ctx, typedMode),
                () -> getBackend().setBlendEquation(ctx, mode)
            );
    }

    public static void setBlendEquation(CommandContext ctx, VulkanicBlendEquation mode) {
        getBackend().setBlendEquation(ctx, mode);
    }
    
    public static void setDepthFunc(CommandContext ctx, int func) {
        VulkanicDepthCompareOp.fromLegacyGlConstant(func)
            .ifPresentOrElse(
                typedFunc -> setDepthFunc(ctx, typedFunc),
                () -> getBackend().setDepthFunc(ctx, func)
            );
    }

    public static void setDepthFunc(CommandContext ctx, VulkanicDepthCompareOp func) {
        getBackend().setDepthFunc(ctx, func);
    }
    
    public static void setReadBuffer(CommandContext ctx, int buffer) {
        getBackend().setReadBuffer(ctx, buffer);
    }

    /**
     * Sets the read buffer to a color attachment by index.
     */
    public static void setReadBufferColorAttachment(CommandContext ctx, int colorAttachmentIndex) {
        getBackend().setReadBuffer(ctx, colorAttachment(colorAttachmentIndex));
    }
    
    
    public static int getError(CommandContext ctx) {
        return getBackend().getError(ctx);
    }
    
    
    public static void uploadTexture2D(CommandContext ctx, int target, int level, int internalFormat, int width, int height, 
                                        int border, int format, int type, java.nio.ByteBuffer pixels) {
        traceShaderInputParityOrdering(
            "texture-upload",
            "vulkanic-uploadTexture2D",
            "target=" + target + "|level=" + level + "|internalFormat=" + internalFormat
                + "|format=" + format + "|type=" + type + "|size=" + width + "x" + height
        );
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();

        // Preserve exact legacy GL tuples on OpenGL to avoid any accidental
        // reinterpretation of caller-provided texture upload semantics.
        if (directVulkanBackend != null) {
            java.util.Optional<VulkanicTextureUploadFormat> knownFormat = VulkanicTextureUploadFormat.fromLegacyGlTuple(internalFormat, format, type);
            java.util.Optional<VulkanicTextureTarget> knownTarget = VulkanicTextureTarget.fromLegacyGlTarget(target);

            if (knownFormat.isPresent() && knownTarget.isPresent()) {
                directVulkanBackend.uploadTexture2D(ctx, knownTarget.get(), level, knownFormat.get(), width, height, border, pixels);
                return;
            }

            directVulkanBackend.uploadTexture2D(ctx, target, level, internalFormat, width, height, border, format, type, pixels);
            return;
        }

        GraphicsBackend activeBackend = getBackend();
        if (activeBackend.getBackendType() == GraphicsBackendType.VULKAN) {
            java.util.Optional<VulkanicTextureUploadFormat> knownFormat = VulkanicTextureUploadFormat.fromLegacyGlTuple(internalFormat, format, type);
            java.util.Optional<VulkanicTextureTarget> knownTarget = VulkanicTextureTarget.fromLegacyGlTarget(target);

            if (knownFormat.isPresent() && knownTarget.isPresent()) {
                activeBackend.uploadTexture2D(ctx, knownTarget.get(), level, knownFormat.get(), width, height, border, pixels);
                return;
            }
        }

        activeBackend.uploadTexture2D(ctx, target, level, internalFormat, width, height, border, format, type, pixels);
    }

    public static void uploadTexture2D(
        CommandContext ctx,
        VulkanicTextureTarget target,
        int level,
        VulkanicTextureUploadFormat uploadFormat,
        int width,
        int height,
        int border,
        java.nio.ByteBuffer pixels
    ) {
        traceShaderInputParityOrdering(
            "texture-upload",
            "vulkanic-uploadTexture2D-typed",
            "target=" + target + "|level=" + level + "|format=" + uploadFormat + "|size=" + width + "x" + height
        );
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        if (directVulkanBackend != null) {
            directVulkanBackend.uploadTexture2D(ctx, target, level, uploadFormat, width, height, border, pixels);
            return;
        }
        getBackend().uploadTexture2D(ctx, target, level, uploadFormat, width, height, border, pixels);
    }

    /**
     * Uploads a 2D texture image to the currently bound 2D texture target using
     * backend-neutral upload-format semantics.
     */
    public static void uploadTexture2D(
        CommandContext ctx,
        int level,
        VulkanicTextureUploadFormat uploadFormat,
        int width,
        int height,
        int border,
        java.nio.ByteBuffer pixels
    ) {
        traceShaderInputParityOrdering(
            "texture-upload",
            "vulkanic-uploadTexture2D-typed-default",
            "target=TEXTURE_2D|level=" + level + "|format=" + uploadFormat + "|size=" + width + "x" + height
        );
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        if (directVulkanBackend != null) {
            directVulkanBackend.uploadTexture2D(ctx, VulkanicTextureTarget.TEXTURE_2D, level, uploadFormat, width, height, border, pixels);
            return;
        }
        getBackend().uploadTexture2D(ctx, level, uploadFormat, width, height, border, pixels);
    }

    /**
     * Uploads a 2D texture image to the currently bound 2D texture target.
     */
    public static void uploadTexture2D(CommandContext ctx, int level, int internalFormat, int width, int height,
                                       int border, int format, int type, java.nio.ByteBuffer pixels) {
        uploadTexture2D(ctx, GL_TEXTURE_2D, level, internalFormat, width, height, border, format, type, pixels);
    }
    
    public static void uploadTexture2DSubImage(CommandContext ctx, int target, int level, int xOffset, int yOffset, 
                                                int width, int height, int format, int type, long pixels) {
        traceShaderInputParityOrdering(
            "texture-upload",
            "vulkanic-uploadTexture2DSubImage-address",
            "target=" + target + "|level=" + level + "|offset=" + xOffset + "x" + yOffset
                + "|format=" + format + "|type=" + type + "|size=" + width + "x" + height
        );
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        if (directVulkanBackend != null) {
            directVulkanBackend.uploadTexture2DSubImage(ctx, target, level, xOffset, yOffset, width, height, format, type, pixels);
            return;
        }
        getBackend().uploadTexture2DSubImage(ctx, target, level, xOffset, yOffset, width, height, format, type, pixels);
    }

    public static void uploadTexture2DSubImage(CommandContext ctx, VulkanicTextureTarget target, int level, int xOffset, int yOffset,
                                                int width, int height, int format, int type, long pixels) {
        uploadTexture2DSubImage(ctx, target.toLegacyGlTarget(), level, xOffset, yOffset, width, height, format, type, pixels);
    }

    /**
     * Uploads a 2D texture sub-image to the currently bound 2D texture target.
     */
    public static void uploadTexture2DSubImage(CommandContext ctx, int level, int xOffset, int yOffset,
                                                int width, int height, int format, int type, long pixels) {
        traceShaderInputParityOrdering(
            "texture-upload",
            "vulkanic-uploadTexture2DSubImage-address-default",
            "target=" + GL_TEXTURE_2D + "|level=" + level + "|offset=" + xOffset + "x" + yOffset
                + "|format=" + format + "|type=" + type + "|size=" + width + "x" + height
        );
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        if (directVulkanBackend != null) {
            directVulkanBackend.uploadTexture2DSubImage(ctx, GL_TEXTURE_2D, level, xOffset, yOffset, width, height, format, type, pixels);
            return;
        }
        getBackend().uploadTexture2DSubImage(ctx, GL_TEXTURE_2D, level, xOffset, yOffset, width, height, format, type, pixels);
    }
    
    public static void uploadTexture2DSubImage(CommandContext ctx, int target, int level, int xOffset, int yOffset, 
                                                int width, int height, int format, int type, java.nio.ByteBuffer pixels) {
        traceShaderInputParityOrdering(
            "texture-upload",
            "vulkanic-uploadTexture2DSubImage-buffer",
            "target=" + target + "|level=" + level + "|offset=" + xOffset + "x" + yOffset
                + "|format=" + format + "|type=" + type + "|size=" + width + "x" + height
        );
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        if (directVulkanBackend != null) {
            directVulkanBackend.uploadTexture2DSubImage(ctx, target, level, xOffset, yOffset, width, height, format, type, pixels);
            return;
        }
        getBackend().uploadTexture2DSubImage(ctx, target, level, xOffset, yOffset, width, height, format, type, pixels);
    }

    public static void uploadTexture2DSubImage(CommandContext ctx, VulkanicTextureTarget target, int level, int xOffset, int yOffset,
                                                int width, int height, int format, int type, java.nio.ByteBuffer pixels) {
        uploadTexture2DSubImage(ctx, target.toLegacyGlTarget(), level, xOffset, yOffset, width, height, format, type, pixels);
    }

    /**
     * Uploads a 2D texture sub-image to the currently bound 2D texture target.
     */
    public static void uploadTexture2DSubImage(CommandContext ctx, int level, int xOffset, int yOffset,
                                                int width, int height, int format, int type, java.nio.ByteBuffer pixels) {
        traceShaderInputParityOrdering(
            "texture-upload",
            "vulkanic-uploadTexture2DSubImage-buffer-default",
            "target=" + GL_TEXTURE_2D + "|level=" + level + "|offset=" + xOffset + "x" + yOffset
                + "|format=" + format + "|type=" + type + "|size=" + width + "x" + height
        );
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        if (directVulkanBackend != null) {
            directVulkanBackend.uploadTexture2DSubImage(ctx, GL_TEXTURE_2D, level, xOffset, yOffset, width, height, format, type, pixels);
            return;
        }
        getBackend().uploadTexture2DSubImage(ctx, GL_TEXTURE_2D, level, xOffset, yOffset, width, height, format, type, pixels);
    }
    
    
    
    
    public static int createBuffer(CommandContext ctx) {
        return getBackend().createBuffer(ctx);
    }
    
    public static void deleteBuffer(CommandContext ctx, int buffer) {
        getBackend().deleteBuffer(ctx, buffer);
    }
    
    public static void bufferData(CommandContext ctx, int target, java.nio.ByteBuffer data, int usage) {
        getBackend().bufferData(ctx, target, data, usage);
    }
    
    public static void bufferData(CommandContext ctx, int target, long size, int usage) {
        getBackend().bufferData(ctx, target, size, usage);
    }
    
    public static void bufferData(CommandContext ctx, int target, float[] data, int usage) {
        getBackend().bufferData(ctx, target, data, usage);
    }
    
    public static void bufferData(CommandContext ctx, int target, int[] data, int usage) {
        getBackend().bufferData(ctx, target, data, usage);
    }
    
    
    
    
    
    public static void bufferSubData(CommandContext ctx, int target, long offset, java.nio.ByteBuffer data) {
        VulkanicBufferTarget.fromLegacyGlTarget(target)
            .ifPresentOrElse(
                typedTarget -> bufferSubData(ctx, typedTarget, offset, data),
                () -> getBackend().bufferSubData(ctx, target, offset, data)
            );
    }

    public static void bufferSubData(CommandContext ctx, VulkanicBufferTarget target, long offset, java.nio.ByteBuffer data) {
        getBackend().bufferSubData(ctx, target.toLegacyGlTarget(), offset, data);
    }
    
    public static void bufferStorage(CommandContext ctx, int target, long size, int flags) {
        VulkanicBufferTarget.fromLegacyGlTarget(target)
            .ifPresentOrElse(
                typedTarget -> bufferStorage(ctx, typedTarget, size, flags),
                () -> getBackend().bufferStorage(ctx, target, size, flags)
            );
    }

    public static void bufferStorage(CommandContext ctx, VulkanicBufferTarget target, long size, int flags) {
        getBackend().bufferStorage(ctx, target.toLegacyGlTarget(), size, flags);
    }
    
    public static void bufferStorage(CommandContext ctx, int target, java.nio.ByteBuffer data, int flags) {
        VulkanicBufferTarget.fromLegacyGlTarget(target)
            .ifPresentOrElse(
                typedTarget -> bufferStorage(ctx, typedTarget, data, flags),
                () -> getBackend().bufferStorage(ctx, target, data, flags)
            );
    }

    public static void bufferStorage(CommandContext ctx, VulkanicBufferTarget target, java.nio.ByteBuffer data, int flags) {
        getBackend().bufferStorage(ctx, target.toLegacyGlTarget(), data, flags);
    }
    
    public static void copyBufferSubData(CommandContext ctx, int readTarget, int writeTarget, long readOffset, long writeOffset, long size) {
        getBackend().copyBufferSubData(ctx, readTarget, writeTarget, readOffset, writeOffset, size);
    }

    /**
     * Copies data between buffers using copy-read/copy-write intent targets.
     */
    public static void copyBufferSubDataBetweenCopyTargets(CommandContext ctx, long readOffset, long writeOffset, long size) {
        getBackend().copyBufferSubData(ctx, GL_COPY_READ_BUFFER, GL_COPY_WRITE_BUFFER, readOffset, writeOffset, size);
    }
    
    public static void flushMappedBufferRange(CommandContext ctx, int target, long offset, long length) {
        getBackend().flushMappedBufferRange(ctx, target, offset, length);
    }
    
    
    public static int createVertexArray(CommandContext ctx) {
        return getBackend().createVertexArray(ctx);
    }
    
    public static void bindVertexArray(CommandContext ctx, int vao) {
        getBackend().bindVertexArray(ctx, vao);
    }
    
    
    
    public static java.nio.ByteBuffer mapBuffer(CommandContext ctx, int target, long offset, long length, int access) {
        return getBackend().mapBuffer(ctx, target, offset, length, access);
    }
    
    
    public static void unmapBuffer(CommandContext ctx, int target) {
        getBackend().unmapBuffer(ctx, target);
    }
    
    
    
    public static void deleteFramebuffer(CommandContext ctx, int fbo) {
        getBackend().deleteFramebuffer(ctx, fbo);
    }
    
    
    public static void blitFramebuffer(CommandContext ctx, int srcX0, int srcY0, int srcX1, int srcY1, 
                                       int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        getBackend().blitFramebuffer(ctx, srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }

    public static void blitColorBufferNearest(CommandContext ctx, int srcX0, int srcY0, int srcX1, int srcY1,
                                              int dstX0, int dstY0, int dstX1, int dstY1) {
        blitFramebuffer(ctx, srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, GL_COLOR_BUFFER_BIT, GL_NEAREST);
    }

    public static void blitDepthBufferNearest(CommandContext ctx, int srcX0, int srcY0, int srcX1, int srcY1,
                                              int dstX0, int dstY0, int dstX1, int dstY1) {
        blitFramebuffer(ctx, srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, GL_DEPTH_BUFFER_BIT, GL_NEAREST);
    }

    public static void blitDepthAndStencilBuffersNearest(CommandContext ctx, int srcX0, int srcY0, int srcX1, int srcY1,
                                                          int dstX0, int dstY0, int dstX1, int dstY1) {
        blitFramebuffer(ctx, srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT, GL_NEAREST);
    }
    
    
    public static int createShader(CommandContext ctx, int shaderType) {
        java.util.Optional<VulkanicShaderStage> typedShaderType = VulkanicShaderStage.fromLegacyGlShaderType(shaderType);
        if (typedShaderType.isPresent()) {
            return createShader(ctx, typedShaderType.get());
        }
        return getBackend().createShader(ctx, shaderType);
    }

    public static int createShader(CommandContext ctx, VulkanicShaderStage shaderStage) {
        return getBackend().createShader(ctx, shaderStage);
    }

    public static VulkanicShaderHandle createShaderHandle(CommandContext ctx, int shaderType) {
        java.util.Optional<VulkanicShaderStage> typedShaderType = VulkanicShaderStage.fromLegacyGlShaderType(shaderType);
        if (typedShaderType.isPresent()) {
            return createShaderHandle(ctx, typedShaderType.get());
        }
        return getBackend().createShaderHandle(ctx, shaderType);
    }

    public static VulkanicShaderHandle createShaderHandle(CommandContext ctx, VulkanicShaderStage shaderStage) {
        return getBackend().createShaderHandle(ctx, shaderStage);
    }

    /**
     * Compiles GLSL source to a SPIR-V module through the active backend.
     */
    public static VulkanicSpirvModule compileSpirvModule(
        CommandContext ctx,
        VulkanicShaderStage shaderStage,
        CharSequence glslSource,
        String sourceName,
        String entryPoint
    ) {
        return getBackend().compileSpirvModule(ctx, shaderStage, glslSource, sourceName, entryPoint);
    }

    /**
     * Compiles GLSL source to a SPIR-V module using entry point {@code main}.
     */
    public static VulkanicSpirvModule compileSpirvModule(
        CommandContext ctx,
        VulkanicShaderStage shaderStage,
        CharSequence glslSource,
        String sourceName
    ) {
        return getBackend().compileSpirvModule(ctx, shaderStage, glslSource, sourceName);
    }

    /**
     * Returns compiled SPIR-V for a shader handle when the backend tracks it.
     */
    public static java.util.Optional<VulkanicSpirvModule> getCompiledSpirvModule(CommandContext ctx, int shader) {
        return getBackend().getCompiledSpirvModule(ctx, shader);
    }

    /**
     * Returns compiled SPIR-V for a typed shader handle when the backend tracks it.
     */
    public static java.util.Optional<VulkanicSpirvModule> getCompiledSpirvModule(CommandContext ctx, VulkanicShaderHandle shader) {
        return getBackend().getCompiledSpirvModule(ctx, shader);
    }
    
    public static void compileShader(CommandContext ctx, int shader) {
        getBackend().compileShader(ctx, shader);
    }

    public static void compileShader(CommandContext ctx, VulkanicShaderHandle shader) {
        getBackend().compileShader(ctx, shader);
    }
    
    public static int createShaderProgram(CommandContext ctx) {
        return getBackend().createShaderProgram(ctx);
    }

    public static VulkanicProgramHandle createShaderProgramHandle(CommandContext ctx) {
        return getBackend().createShaderProgramHandle(ctx);
    }
    
    
    public static void deleteShader(CommandContext ctx, int shader) {
        getBackend().deleteShader(ctx, shader);
    }

    public static void deleteShader(CommandContext ctx, VulkanicShaderHandle shader) {
        getBackend().deleteShader(ctx, shader);
    }
    
    
    
    
    public static void deleteProgram(CommandContext ctx, int program) {
        getBackend().deleteProgram(ctx, program);
    }

    public static void deleteProgram(CommandContext ctx, VulkanicProgramHandle program) {
        getBackend().deleteProgram(ctx, program);
    }
    
    
    public static void attachShader(CommandContext ctx, int program, int shader) {
        getBackend().attachShader(ctx, program, shader);
    }

    public static void attachShader(CommandContext ctx, VulkanicProgramHandle program, VulkanicShaderHandle shader) {
        getBackend().attachShader(ctx, program, shader);
    }
    
    public static void detachShader(CommandContext ctx, int program, int shader) {
        getBackend().detachShader(ctx, program, shader);
    }

    public static void detachShader(CommandContext ctx, VulkanicProgramHandle program, VulkanicShaderHandle shader) {
        getBackend().detachShader(ctx, program, shader);
    }
    
    public static void linkProgram(CommandContext ctx, int program) {
        getBackend().linkProgram(ctx, program);
    }

    public static void linkProgram(CommandContext ctx, VulkanicProgramHandle program) {
        getBackend().linkProgram(ctx, program);
    }
    
    public static int getProgramParameter(CommandContext ctx, int program, int pname) {
        java.util.Optional<VulkanicProgramParameterName> typedPName = VulkanicProgramParameterName.fromLegacyGlPName(pname);
        if (typedPName.isPresent()) {
            return getProgramParameter(ctx, program, typedPName.get());
        }
        return getBackend().getProgramParameter(ctx, program, pname);
    }

    public static int getProgramParameter(CommandContext ctx, VulkanicProgramHandle program, int pname) {
        java.util.Optional<VulkanicProgramParameterName> typedPName = VulkanicProgramParameterName.fromLegacyGlPName(pname);
        if (typedPName.isPresent()) {
            return getProgramParameter(ctx, program, typedPName.get());
        }
        return getBackend().getProgramParameter(ctx, program, pname);
    }

    public static int getProgramParameter(CommandContext ctx, int program, VulkanicProgramParameterName pname) {
        return getBackend().getProgramParameter(ctx, program, pname.toLegacyGlPName());
    }

    public static int getProgramParameter(CommandContext ctx, VulkanicProgramHandle program, VulkanicProgramParameterName pname) {
        return getBackend().getProgramParameter(ctx, program, pname);
    }

    /**
     * Evaluates a legacy OpenGL boolean-style integer as true when non-zero.
     */
    public static boolean isLegacyGlBooleanTrue(int value) {
        return value != GL_FALSE;
    }

    /**
     * Evaluates a legacy OpenGL boolean-style integer as false when zero.
     */
    public static boolean isLegacyGlBooleanFalse(int value) {
        return value == GL_FALSE;
    }

    /**
     * Returns true when a program currently reports LINK_STATUS success.
     */
    public static boolean isProgramLinkSuccessful(CommandContext ctx, int program) {
        return isLegacyGlBooleanTrue(getProgramParameter(ctx, program, VulkanicProgramParameterName.LINK_STATUS));
    }

    public static boolean isProgramLinkSuccessful(CommandContext ctx, VulkanicProgramHandle program) {
        return isLegacyGlBooleanTrue(getProgramParameter(ctx, program, VulkanicProgramParameterName.LINK_STATUS));
    }
    
    public static int getShaderParameter(CommandContext ctx, int shader, int pname) {
        java.util.Optional<VulkanicShaderParameterName> typedPName = VulkanicShaderParameterName.fromLegacyGlPName(pname);
        if (typedPName.isPresent()) {
            return getShaderParameter(ctx, shader, typedPName.get());
        }
        return getBackend().getShaderParameter(ctx, shader, pname);
    }

    public static int getShaderParameter(CommandContext ctx, VulkanicShaderHandle shader, int pname) {
        java.util.Optional<VulkanicShaderParameterName> typedPName = VulkanicShaderParameterName.fromLegacyGlPName(pname);
        if (typedPName.isPresent()) {
            return getShaderParameter(ctx, shader, typedPName.get());
        }
        return getBackend().getShaderParameter(ctx, shader, pname);
    }

    public static int getShaderParameter(CommandContext ctx, int shader, VulkanicShaderParameterName pname) {
        return getBackend().getShaderParameter(ctx, shader, pname.toLegacyGlPName());
    }

    public static int getShaderParameter(CommandContext ctx, VulkanicShaderHandle shader, VulkanicShaderParameterName pname) {
        return getBackend().getShaderParameter(ctx, shader, pname);
    }

    /**
     * Returns true when a shader currently reports COMPILE_STATUS success.
     */
    public static boolean isShaderCompileSuccessful(CommandContext ctx, int shader) {
        return isLegacyGlBooleanTrue(getShaderParameter(ctx, shader, VulkanicShaderParameterName.COMPILE_STATUS));
    }

    public static boolean isShaderCompileSuccessful(CommandContext ctx, VulkanicShaderHandle shader) {
        return isLegacyGlBooleanTrue(getShaderParameter(ctx, shader, VulkanicShaderParameterName.COMPILE_STATUS));
    }
    
    public static String getProgramInfoLog(CommandContext ctx, int program) {
        return getBackend().getProgramInfoLog(ctx, program);
    }

    public static String getProgramInfoLog(CommandContext ctx, VulkanicProgramHandle program) {
        return getBackend().getProgramInfoLog(ctx, program);
    }
    
    
    
    
    
    
    public static String getShaderInfoLog(CommandContext ctx, int shader) {
        return getBackend().getShaderInfoLog(ctx, shader);
    }

    public static String getShaderInfoLog(CommandContext ctx, VulkanicShaderHandle shader) {
        return getBackend().getShaderInfoLog(ctx, shader);
    }
    
    public static int getUniformLocation(CommandContext ctx, int program, CharSequence name) {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        if (directVulkanBackend != null) {
            return directVulkanBackend.getUniformLocation(ctx, program, name);
        }
        return getBackend().getUniformLocation(ctx, program, name);
    }

    /**
     * Resolves a uniform binding slot using a backend-neutral uniform-location wrapper.
     */
    public static VulkanicUniformLocation resolveUniformLocation(CommandContext ctx, int program, CharSequence name) {
        return getBackend().resolveUniformLocation(ctx, program, name);
    }

    public static int getUniformLocationWithLegacySamplerFallback(CommandContext ctx, int program, CharSequence name) {
        int location = getUniformLocation(ctx, program, name);
        if (location != -1) {
            return location;
        }

        String uniformName = name.toString();
        if (uniformName.equals("Sampler0")) {
            location = getUniformLocation(ctx, program, "tex");
            if (location == -1) {
                location = getUniformLocation(ctx, program, "gtexture");
                if (location == -1) {
                    location = getUniformLocation(ctx, program, "texture");
                }
            }
        } else if (uniformName.equals("Sampler1")) {
            location = getUniformLocation(ctx, program, "iris_overlay");
        } else if (uniformName.equals("Sampler2")) {
            location = getUniformLocation(ctx, program, "lightmap");
            if (location == -1) {
                location = getUniformLocation(ctx, program, "iris_lightmap");
            }
            if (location == -1) {
                location = getUniformLocation(ctx, program, "gaux2");
            }
        }

        return location;
    }

    /**
     * Resolves a uniform binding slot with legacy sampler-name fallback behavior.
     */
    public static VulkanicUniformLocation resolveUniformLocationWithLegacySamplerFallback(CommandContext ctx, int program, CharSequence name) {
        return VulkanicUniformLocation.of(getUniformLocationWithLegacySamplerFallback(ctx, program, name));
    }

    public static String generatedStandaloneUniformBlockName() {
        return GENERATED_STANDALONE_UNIFORM_BLOCK_NAME;
    }

    public static boolean isStandaloneUniformTracingEnabled() {
        return TRACE_STANDALONE_UNIFORMS;
    }

    public static boolean isShaderInputParityTracingEnabled() {
        return TRACE_SHADER_INPUT_PARITY;
    }

    public static void registerShaderInputParityProgramName(int program, String name) {
        if (program <= 0 || name == null || name.isBlank()) {
            return;
        }
        SHADER_INPUT_PARITY_PROGRAM_NAMES.put(program, name);
    }

    public static boolean shouldTraceStandaloneUniformBlockMembers() {
        return TRACE_SHADER_INPUT_PARITY && TRACE_STANDALONE_UNIFORM_BLOCK_MEMBERS;
    }

    public static boolean shouldTraceStandaloneUniforms() {
        if (!isStandaloneUniformTracingEnabled()) {
            return false;
        }
        return STANDALONE_UNIFORM_TRACE_LOG_COUNT.incrementAndGet() <= MAX_STANDALONE_UNIFORM_TRACE_LOGS;
    }

    public static boolean shouldTraceStandaloneUniform(String name) {
        return GENERATED_STANDALONE_UNIFORM_BLOCK_NAME.equals(name) && shouldTraceStandaloneUniforms();
    }

    public interface ShaderInputParityScope extends AutoCloseable {
        @Override
        void close();
    }

    private static final ShaderInputParityScope NO_SHADER_INPUT_PARITY_SCOPE = () -> {
    };

    private record ShaderInputParitySemanticDrawIdentity(
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
        String fields() {
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

    private static String shaderInputParityNormalizeSemanticOutput(@Nullable String output) {
        String normalized = shaderInputParityValueOrUnknown(output);
        if (normalized.equals("framebuffer")
            || normalized.equals("framebuffer-or-texture-view")
            || normalized.startsWith("framebuffer:")
            || normalized.startsWith("extent=")) {
            return "legacy-framebuffer";
        }
        return normalized.replaceAll("\\btex=\\d+", "tex=<id>");
    }

    public static ShaderInputParityScope beginShaderInputParitySemanticDraw(
        String source,
        String subsystem,
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
            return NO_SHADER_INPUT_PARITY_SCOPE;
        }

        ShaderInputParitySemanticDrawIdentity previous = SHADER_INPUT_PARITY_SEMANTIC_DRAW.get();
        PipelineDescriptor.PortableState portableState = descriptor != null ? descriptor.getPortableState() : null;
        String phase = shaderInputParityRenderPhase();
        String normalizedSubsystem = shaderInputParityValueOrUnknown(subsystem);
        if (previous != null
            && "sodium-terrain".equals(previous.subsystem())
            && "blaze3d-renderpass".equals(normalizedSubsystem)) {
            return NO_SHADER_INPUT_PARITY_SCOPE;
        }
        String normalizedPass = shaderInputParityValueOrUnknown(pass);
        String pipeline = shaderInputParityPipelineLocation(renderPipeline, portableState);
        String vertexShader = shaderInputParityVertexShader(renderPipeline, portableState);
        String fragmentShader = shaderInputParityFragmentShader(renderPipeline, portableState);
        String normalizedMaterial = shaderInputParityValueOrUnknown(material != null ? material : pipeline);
        String normalizedOutput = shaderInputParityNormalizeSemanticOutput(output);
        String projectionLabel = shaderInputParityCurrentProjectionLabel();
        if (!projectionLabel.isEmpty()) {
            normalizedOutput = normalizedOutput + "|projection:" + projectionLabel;
        }
        String poseContext = shaderInputParityDeterministicContextFields().replace(' ', '|');
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
            .computeIfAbsent(ordinalKey, ignored -> new java.util.concurrent.atomic.AtomicInteger())
            .incrementAndGet();
        String semanticKey = shaderInputParityHashString(ordinalKey + "|ordinal=" + ordinal);
        ShaderInputParitySemanticDrawIdentity identity = new ShaderInputParitySemanticDrawIdentity(
            semanticKey,
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
                getActiveBackendType().name().toLowerCase(Locale.ROOT),
                source,
                identity.fields(),
                indexed,
                firstVertex,
                vertexCount,
                firstIndex,
                indexCount,
                instanceCount,
                baseVertex,
                shaderInputParityDeterministicContextFields()
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

    @Nullable
    public static VulkanicBufferSlice getStandaloneUniformBufferSlice(CommandContext ctx, int program) {
        return dispatchImplementedValue(
            vulkanBackend -> vulkanBackend.getStandaloneUniformBufferSlice(ctx, program),
            ignored -> null
        );
    }

    public static void logStandaloneSliceTrace(
        CommandContext ctx,
        String stage,
        int program,
        @Nullable String programName,
        @Nullable String note
    ) {
        if (!isStandaloneUniformTracingEnabled()) {
            return;
        }
        GraphicsBackend activeBackend = getBackend();
        if (activeBackend instanceof VulkanBackend vulkanBackend) {
            vulkanBackend.logStandaloneSliceTrace(ctx, stage, program, programName, note);
        }
    }
    
    public static int getAttributeLocation(CommandContext ctx, int program, CharSequence name) {
        return getBackend().getAttributeLocation(ctx, program, name);
    }
    
    public static void setUniform1i(CommandContext ctx, int location, int value) {
        setUniform1i(ctx, VulkanicUniformLocation.of(location), value);
    }

    public static void setUniform1i(CommandContext ctx, VulkanicUniformLocation location, int value) {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        if (directVulkanBackend != null) {
            directVulkanBackend.setUniform1i(ctx, location.value(), value);
            return;
        }
        getBackend().setUniform1i(ctx, location, value);
    }
    
    public static void setUniform1f(CommandContext ctx, int location, float value) {
        setUniform1f(ctx, VulkanicUniformLocation.of(location), value);
    }

    public static void setUniform1f(CommandContext ctx, VulkanicUniformLocation location, float value) {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        if (directVulkanBackend != null) {
            directVulkanBackend.setUniform1f(ctx, location.value(), value);
            return;
        }
        getBackend().setUniform1f(ctx, location, value);
    }
    
    public static void setUniform2f(CommandContext ctx, int location, float v0, float v1) {
        setUniform2f(ctx, VulkanicUniformLocation.of(location), v0, v1);
    }

    public static void setUniform2f(CommandContext ctx, VulkanicUniformLocation location, float v0, float v1) {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        if (directVulkanBackend != null) {
            directVulkanBackend.setUniform2f(ctx, location.value(), v0, v1);
            return;
        }
        getBackend().setUniform2f(ctx, location, v0, v1);
    }
    
    public static void setUniform3i(CommandContext ctx, int location, int v0, int v1, int v2) {
        setUniform3i(ctx, VulkanicUniformLocation.of(location), v0, v1, v2);
    }

    public static void setUniform3i(CommandContext ctx, VulkanicUniformLocation location, int v0, int v1, int v2) {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        if (directVulkanBackend != null) {
            directVulkanBackend.setUniform3i(ctx, location.value(), v0, v1, v2);
            return;
        }
        getBackend().setUniform3i(ctx, location, v0, v1, v2);
    }
    
    public static void setUniform4f(CommandContext ctx, int location, float v0, float v1, float v2, float v3) {
        setUniform4f(ctx, VulkanicUniformLocation.of(location), v0, v1, v2, v3);
    }

    public static void setUniform4f(CommandContext ctx, VulkanicUniformLocation location, float v0, float v1, float v2, float v3) {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        if (directVulkanBackend != null) {
            directVulkanBackend.setUniform4f(ctx, location.value(), v0, v1, v2, v3);
            return;
        }
        getBackend().setUniform4f(ctx, location, v0, v1, v2, v3);
    }
    
    public static void setUniform4i(CommandContext ctx, int location, int v0, int v1, int v2, int v3) {
        setUniform4i(ctx, VulkanicUniformLocation.of(location), v0, v1, v2, v3);
    }

    public static void setUniform4i(CommandContext ctx, VulkanicUniformLocation location, int v0, int v1, int v2, int v3) {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        if (directVulkanBackend != null) {
            directVulkanBackend.setUniform4i(ctx, location.value(), v0, v1, v2, v3);
            return;
        }
        getBackend().setUniform4i(ctx, location, v0, v1, v2, v3);
    }
    
    public static void setUniformMatrix3fv(CommandContext ctx, int location, boolean transpose, java.nio.FloatBuffer matrix) {
        setUniformMatrix3fv(ctx, VulkanicUniformLocation.of(location), transpose, matrix);
    }

    public static void setUniformMatrix3fv(CommandContext ctx, VulkanicUniformLocation location, boolean transpose, java.nio.FloatBuffer matrix) {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        if (directVulkanBackend != null) {
            directVulkanBackend.setUniformMatrix3fv(ctx, location.value(), transpose, matrix);
            return;
        }
        getBackend().setUniformMatrix3fv(ctx, location, transpose, matrix);
    }
    
    public static void setUniformMatrix3fv(CommandContext ctx, int location, boolean transpose, float[] matrix) {
        setUniformMatrix3fv(ctx, VulkanicUniformLocation.of(location), transpose, matrix);
    }

    public static void setUniformMatrix3fv(CommandContext ctx, VulkanicUniformLocation location, boolean transpose, float[] matrix) {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        if (directVulkanBackend != null) {
            directVulkanBackend.setUniformMatrix3fv(ctx, location.value(), transpose, matrix);
            return;
        }
        getBackend().setUniformMatrix3fv(ctx, location, transpose, matrix);
    }
    
    public static void setUniformMatrix4fv(CommandContext ctx, int location, boolean transpose, java.nio.FloatBuffer matrix) {
        setUniformMatrix4fv(ctx, VulkanicUniformLocation.of(location), transpose, matrix);
    }

    public static void setUniformMatrix4fv(CommandContext ctx, VulkanicUniformLocation location, boolean transpose, java.nio.FloatBuffer matrix) {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        if (directVulkanBackend != null) {
            directVulkanBackend.setUniformMatrix4fv(ctx, location.value(), transpose, matrix);
            return;
        }
        getBackend().setUniformMatrix4fv(ctx, location, transpose, matrix);
    }
    
    public static void setUniformMatrix4fv(CommandContext ctx, int location, boolean transpose, float[] matrix) {
        setUniformMatrix4fv(ctx, VulkanicUniformLocation.of(location), transpose, matrix);
    }

    public static void setUniformMatrix4fv(CommandContext ctx, VulkanicUniformLocation location, boolean transpose, float[] matrix) {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        if (directVulkanBackend != null) {
            directVulkanBackend.setUniformMatrix4fv(ctx, location.value(), transpose, matrix);
            return;
        }
        getBackend().setUniformMatrix4fv(ctx, location, transpose, matrix);
    }
    
    public static void setVertexAttribPointer(CommandContext ctx, int index, int size, int type, boolean normalized, int stride, long pointer) {
        getBackend().setVertexAttribPointer(ctx, index, size, type, normalized, stride, pointer);
    }

    public static void setVertexAttribPointer(
        CommandContext ctx,
        int index,
        int size,
        VulkanicVertexAttributeType type,
        boolean normalized,
        int stride,
        long pointer
    ) {
        getBackend().setVertexAttribPointer(ctx, index, size, type.toLegacyGlConstant(), normalized, stride, pointer);
    }
    
    public static void enableVertexAttribArray(CommandContext ctx, int index) {
        getBackend().enableVertexAttribArray(ctx, index);
    }
    
    public static void bindVertexBuffer(CommandContext ctx, int bindingindex, int buffer, long offset, int stride) {
        getBackend().bindVertexBuffer(ctx, bindingindex, buffer, offset, stride);
    }
    
    
    public static void setVertexAttribIPointer(CommandContext ctx, int index, int size, int type, int stride, long pointer) {
        getBackend().setVertexAttribIPointer(ctx, index, size, type, stride, pointer);
    }

    public static void setVertexAttribIPointer(
        CommandContext ctx,
        int index,
        int size,
        VulkanicVertexAttributeType type,
        int stride,
        long pointer
    ) {
        getBackend().setVertexAttribIPointer(ctx, index, size, type.toLegacyGlConstant(), stride, pointer);
    }
    
    
    
    public static void disableVertexAttribArray(CommandContext ctx, int index) {
        getBackend().disableVertexAttribArray(ctx, index);
    }
    
    
    public static void setVertexAttribDivisor(CommandContext ctx, int index, int divisor) {
        dispatchImplementedVoid(
            direct -> direct.setVertexAttribDivisor(ctx, index, divisor),
            activeBackend -> activeBackend.setVertexAttribDivisor(ctx, index, divisor)
        );
    }
    
    
    
    
    public static void setAttributeLocation(CommandContext ctx, int program, int index, CharSequence name) {
        getBackend().setAttributeLocation(ctx, program, index, name);
    }
    
    
    public static long createFenceSync(CommandContext ctx, int condition, int flags) {
        return getBackend().createFenceSync(ctx, condition, flags);
    }

    /**
     * Creates a fence that is signaled when prior GPU commands complete.
     */
    public static long createGpuCompletionFence(CommandContext ctx) {
        return getBackend().createFenceSync(ctx, GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
    }
    
    public static int waitForSync(CommandContext ctx, long sync, int flags, long timeout) {
        return getBackend().waitForSync(ctx, sync, flags, timeout);
    }

    /**
     * Waits for a sync object while requesting command-stream flush semantics.
     */
    public static int waitForSyncWithFlush(CommandContext ctx, long sync, long timeout) {
        return getBackend().waitForSync(ctx, sync, GL_SYNC_FLUSH_COMMANDS_BIT, timeout);
    }

    /**
     * Returns true if a sync wait result indicates timeout expiration.
     */
    public static boolean isSyncWaitTimeout(int waitResult) {
        return waitResult == GL_TIMEOUT_EXPIRED;
    }

    /**
     * Returns true if a sync wait result indicates failure.
     */
    public static boolean isSyncWaitFailed(int waitResult) {
        return waitResult == GL_WAIT_FAILED;
    }
    
    public static void destroySync(CommandContext ctx, long sync) {
        getBackend().destroySync(ctx, sync);
    }

    /**
     * Enqueues a callback to run after all currently submitted GPU work completes.
     */
    public static void queueFencedTask(Runnable runnable) {
        long syncObject = createGpuCompletionFence(getCommandContext());
        PENDING_FENCED_TASKS.addLast(new GpuAsyncTask(runnable, syncObject));
    }

    /**
     * Executes queued fenced callbacks whose GPU fences have signaled.
     */
    public static void executePendingFenceTasks() {
        CommandContext ctx = getCommandContext();

        for (GpuAsyncTask task = PENDING_FENCED_TASKS.peekFirst();
             task != null;
             task = PENDING_FENCED_TASKS.peekFirst()) {
            int waitResult = waitForSyncWithFlush(ctx, task.syncObject(), 0L);
            if (isSyncWaitTimeout(waitResult)) {
                return;
            }

            try {
                if (!isSyncWaitFailed(waitResult)) {
                    task.callback().run();
                }
            } finally {
                destroySync(ctx, task.syncObject());
                PENDING_FENCED_TASKS.removeFirst();
            }
        }
    }

    public static String getBackendDescription() {
        return String.format(Locale.ROOT, "LWJGL version %s", GLX._getLWJGLVersion());
    }

    public static void initRenderThread() {
        if (renderThread != null) {
            throw new IllegalStateException("Could not initialize render thread");
        }

        renderThread = Thread.currentThread();
    }

    public static boolean isOnRenderThread() {
        return Thread.currentThread() == renderThread;
    }

    public static boolean isInInit() {
        return renderThread == null || Thread.currentThread() == renderThread;
    }

    public static void assertOnRenderThread() {
        if (!isOnRenderThread()) {
            throw constructThreadException();
        }
    }

    public static void assertOnRenderThreadOrInit() {
        if (!isOnRenderThread() && !isInInit()) {
            throw constructThreadException();
        }
    }

    private static IllegalStateException constructThreadException() {
        return new IllegalStateException("Rendersystem called from wrong thread");
    }

    public static void setDevice(GpuDevice gpuDevice) {
        assertOnRenderThread();
        device = gpuDevice;
    }

    public static void registerGlfwWindowHandleForVulkanSurface(long windowHandle) {
        if (windowHandle != MemoryUtil.NULL) {
            registeredGlfwWindowHandleForVulkanSurface = windowHandle;
        }
    }

    public static long getRegisteredGlfwWindowHandleForVulkanSurface() {
        return registeredGlfwWindowHandleForVulkanSurface;
    }

    public static void clearRegisteredGlfwWindowHandleForVulkanSurface(long windowHandle) {
        if (windowHandle != MemoryUtil.NULL && registeredGlfwWindowHandleForVulkanSurface == windowHandle) {
            registeredGlfwWindowHandleForVulkanSurface = MemoryUtil.NULL;
        }
    }

    public static GpuDevice getDevice() {
        if (device == null) {
            throw new IllegalStateException("Can't getDevice() before it was initialized");
        }

        return device;
    }

    @Nullable
    public static GpuDevice tryGetDevice() {
        return device;
    }

    /**
     * Creates (or retrieves) a backend-owned command encoder for shared render
     * callsites.
     */
    public static CommandEncoder createCommandEncoder() {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        return directVulkanBackend != null
            ? directVulkanBackend.createCommandEncoder()
            : getBackend().createCommandEncoder();
    }

    /**
     * Creates a native Vulkan command encoder for the Sodium chunk-terrain vertical slice.
     *
     * <p>The general {@link #createCommandEncoder()} path intentionally remains unchanged while
     * renderer workloads are migrated one at a time.</p>
     */
    public static CommandEncoder createNativeTerrainCommandEncoder() {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        return directVulkanBackend != null
            ? directVulkanBackend.createNativeTerrainCommandEncoder()
            : getBackend().createCommandEncoder();
    }

    /**
     * Creates a backend-owned render pass targeting a color attachment.
     */
    public static RenderPass createRenderPass(
        java.util.function.Supplier<String> supplier,
        GpuTextureView colorTextureView,
        java.util.OptionalInt clearColor
    ) {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        return directVulkanBackend != null
            ? directVulkanBackend.createRenderPass(supplier, colorTextureView, clearColor)
            : getBackend().createRenderPass(supplier, colorTextureView, clearColor);
    }

    /**
     * Creates a backend-owned render pass targeting color and optional depth attachments.
     */
    public static RenderPass createRenderPass(
        java.util.function.Supplier<String> supplier,
        GpuTextureView colorTextureView,
        java.util.OptionalInt clearColor,
        @Nullable GpuTextureView depthTextureView,
        java.util.OptionalDouble clearDepth
    ) {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        return directVulkanBackend != null
            ? directVulkanBackend.createRenderPass(supplier, colorTextureView, clearColor, depthTextureView, clearDepth)
            : getBackend().createRenderPass(supplier, colorTextureView, clearColor, depthTextureView, clearDepth);
    }

    /**
     * Creates a backend-owned render pass targeting an existing framebuffer contract.
     */
    public static RenderPass createRenderPass(
        java.util.function.Supplier<String> supplier,
        int framebuffer,
        boolean hasDepthTexture
    ) {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        return directVulkanBackend != null
            ? directVulkanBackend.createRenderPass(supplier, framebuffer, hasDepthTexture)
            : getBackend().createRenderPass(supplier, framebuffer, hasDepthTexture);
    }

    /**
     * Creates a backend-owned render pass from an explicit multi-attachment render-target contract.
     */
    public static RenderPass createRenderPass(VulkanicRenderTargetDescriptor descriptor) {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        return directVulkanBackend != null
            ? directVulkanBackend.createRenderPass(descriptor)
            : getBackend().createRenderPass(descriptor);
    }

    /**
     * Checks whether an explicit render-target descriptor matches the currently
     * tracked framebuffer contract closely enough to use native descriptor-backed
     * rendering without changing the pass' attachment shape.
     */
    public static boolean isRenderTargetDescriptorEquivalentToFramebuffer(
        int framebuffer,
        VulkanicRenderTargetDescriptor descriptor
    ) {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        return directVulkanBackend != null
            && directVulkanBackend.isRenderTargetDescriptorEquivalentToFramebuffer(framebuffer, descriptor);
    }

    /**
     * Checks whether an explicit render-target descriptor is safe to use for a
     * pass currently associated with a tracked framebuffer. This is intentionally
     * broader than exact equivalence: shader-pack framebuffers can carry stale
     * leading GL attachments while the explicit descriptor contains the active
     * Vulkan render-target contract.
     */
    public static boolean isRenderTargetDescriptorCompatibleWithFramebuffer(
        int framebuffer,
        VulkanicRenderTargetDescriptor descriptor
    ) {
        return renderTargetDescriptorCompatibilityWithFramebuffer(framebuffer, descriptor).isCompatible();
    }

    /**
     * Classifies how an explicit render-target descriptor relates to a tracked
     * legacy framebuffer contract.
     */
    public static VulkanicRenderTargetCompatibility renderTargetDescriptorCompatibilityWithFramebuffer(
        int framebuffer,
        VulkanicRenderTargetDescriptor descriptor
    ) {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        return directVulkanBackend != null
            ? directVulkanBackend.renderTargetDescriptorCompatibilityWithFramebuffer(framebuffer, descriptor)
            : VulkanicRenderTargetCompatibility.MISMATCH;
    }

    /**
     * Creates a backend-owned GPU texture with supplier-based debug label.
     */
    public static GpuTexture createTexture(
        @Nullable java.util.function.Supplier<String> supplier,
        int usage,
        TextureFormat format,
        int width,
        int height,
        int depthOrLayers,
        int mipLevels
    ) {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        return directVulkanBackend != null
            ? directVulkanBackend.createTexture(supplier, usage, format, width, height, depthOrLayers, mipLevels)
            : getBackend().createTexture(supplier, usage, format, width, height, depthOrLayers, mipLevels);
    }

    /**
     * Creates a backend-owned GPU texture with string debug label.
     */
    public static GpuTexture createTexture(
        @Nullable String label,
        int usage,
        TextureFormat format,
        int width,
        int height,
        int depthOrLayers,
        int mipLevels
    ) {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        return directVulkanBackend != null
            ? directVulkanBackend.createTexture(label, usage, format, width, height, depthOrLayers, mipLevels)
            : getBackend().createTexture(label, usage, format, width, height, depthOrLayers, mipLevels);
    }

    /**
     * Creates a backend-owned GPU buffer with size allocation.
     */
    public static GpuBuffer createBuffer(@Nullable java.util.function.Supplier<String> supplier, int usage, int size) {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        return directVulkanBackend != null
            ? directVulkanBackend.createBuffer(supplier, usage, size)
            : getBackend().createBuffer(supplier, usage, size);
    }

    /**
     * Creates a backend-owned GPU buffer initialized from byte data.
     */
    public static GpuBuffer createBuffer(@Nullable java.util.function.Supplier<String> supplier, int usage, ByteBuffer data) {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        return directVulkanBackend != null
            ? directVulkanBackend.createBuffer(supplier, usage, data)
            : getBackend().createBuffer(supplier, usage, data);
    }

    /**
     * Creates a backend-owned texture view for a full texture range.
     */
    public static GpuTextureView createTextureView(GpuTexture texture) {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        return directVulkanBackend != null
            ? directVulkanBackend.createTextureView(texture)
            : getBackend().createTextureView(texture);
    }

    /**
     * Creates a backend-owned texture view for an explicit mip range.
     */
    public static GpuTextureView createTextureView(GpuTexture texture, int baseMipLevel, int mipLevelCount) {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        return directVulkanBackend != null
            ? directVulkanBackend.createTextureView(texture, baseMipLevel, mipLevelCount)
            : getBackend().createTextureView(texture, baseMipLevel, mipLevelCount);
    }

    public static String getApiDescription() {
        GpuDevice gpuDevice = tryGetDevice();
        return gpuDevice == null ? "Unknown" : gpuDevice.getImplementationInformation();
    }

    public static NanoTimeSource initBackendSystem() {
        return GLX._initGlfw()::getAsLong;
    }

    public static void setupDefaultState() {
        assertOnRenderThread();
        getModelViewStack().clear();
        resetTextureMatrix();
    }

    public static void setErrorCallback(GLFWErrorCallbackI glfwErrorCallback) {
        GLX._setGlfwErrorCallback(glfwErrorCallback);
    }

    public static void pollEvents() {
        pollEventsWaitStart.set(Util.getMillis());
        pollingEvents.set(true);
        GLFW.glfwPollEvents();
        pollingEvents.set(false);
    }

    public static boolean isFrozenAtPollEvents() {
        return pollingEvents.get() && Util.getMillis() - pollEventsWaitStart.get() > 200L;
    }

    public static void limitDisplayFPS(int fpsLimit) {
        if (fpsLimit <= 0) {
            lastDrawTime = GLFW.glfwGetTime();
            return;
        }

        double frameDuration = 1.0 / fpsLimit;
        double currentTime = GLFW.glfwGetTime();
        if (lastDrawTime == Double.MIN_VALUE) {
            lastDrawTime = currentTime;
            return;
        }

        // Resync when the timer jumps or we've stalled far longer than one frame budget.
        if (lastDrawTime > currentTime + frameDuration * 4.0D || currentTime - lastDrawTime > 1.0D) {
            lastDrawTime = currentTime;
            return;
        }

        double targetTime = lastDrawTime + frameDuration;
        while (currentTime < targetTime) {
            double remainingSeconds = targetTime - currentTime;
            if (remainingSeconds > 0.0015D) {
                long sleepNanos = (long)(Math.min(remainingSeconds, 0.010D) * 1_000_000_000.0D);
                if (sleepNanos > 0L) {
                    try {
                        Thread.sleep(sleepNanos / 1_000_000L, (int)(sleepNanos % 1_000_000L));
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } else {
                Thread.onSpinWait();
            }

            currentTime = GLFW.glfwGetTime();
        }

        lastDrawTime = Math.max(targetTime, currentTime);
    }

    public static void initializeDynamicUniforms() {
        assertOnRenderThread();
        dynamicUniforms = new DynamicUniforms();
    }

    public static DynamicUniforms getDynamicUniforms() {
        assertOnRenderThread();
        if (dynamicUniforms == null) {
            throw new IllegalStateException("Can't getDynamicUniforms() before device was initialized");
        }

        return dynamicUniforms;
    }

    public static void resetDynamicUniforms() {
        getDynamicUniforms().reset();
    }

    public static VulkanicAPI.AutoStorageIndexBuffer getSequentialBuffer(VertexFormat.Mode mode) {
        assertOnRenderThread();

        return switch (mode) {
            case QUADS -> sharedSequentialQuad;
            case LINES -> sharedSequentialLines;
            default -> sharedSequential;
        };
    }

    public static final class AutoStorageIndexBuffer {
        private final int vertexStride;
        private final int indexStride;
        private final VulkanicAPI.AutoStorageIndexBuffer.IndexGenerator generator;
        @Nullable
        private GpuBuffer buffer;
        private VertexFormat.IndexType type = VertexFormat.IndexType.SHORT;
        private int indexCount;

        public AutoStorageIndexBuffer(int i, int j, VulkanicAPI.AutoStorageIndexBuffer.IndexGenerator indexGenerator) {
            this.vertexStride = i;
            this.indexStride = j;
            this.generator = indexGenerator;
        }

        public boolean hasStorage(int i) {
            return i <= this.indexCount;
        }

        public GpuBuffer getBuffer(int i) {
            this.ensureStorage(i);
            return this.buffer;
        }

        private void ensureStorage(int i) {
            if (!this.hasStorage(i)) {
                i = Mth.roundToward(i * 2, this.indexStride);
                int j = i / this.indexStride;
                int k = j * this.vertexStride;
                VertexFormat.IndexType indexType = VertexFormat.IndexType.least(k);
                int l = Mth.roundToward(i * indexType.bytes, 4);
                ByteBuffer byteBuffer = MemoryUtil.memAlloc(l);

                try {
                    this.type = indexType;
                    it.unimi.dsi.fastutil.ints.IntConsumer intConsumer = this.intConsumer(byteBuffer);

                    for (int m = 0; m < i; m += this.indexStride) {
                        this.generator.accept(intConsumer, m * this.vertexStride / this.indexStride);
                    }

                    byteBuffer.flip();
                    if (this.buffer != null) {
                        this.buffer.close();
                    }

                    this.buffer = getDevice().createBuffer(() -> "Auto Storage index buffer", 64, byteBuffer);
                } finally {
                    MemoryUtil.memFree(byteBuffer);
                }

                this.indexCount = i;
            }
        }

        private it.unimi.dsi.fastutil.ints.IntConsumer intConsumer(ByteBuffer byteBuffer) {
            return switch (this.type) {
                case SHORT -> i -> byteBuffer.putShort((short)i);
                case INT -> byteBuffer::putInt;
            };
        }

        public VertexFormat.IndexType type() {
            return this.type;
        }

        public interface IndexGenerator {
            void accept(it.unimi.dsi.fastutil.ints.IntConsumer intConsumer, int i);
        }
    }

    public static void setProjectionMatrix(@Nullable GpuBufferSlice gpuBufferSlice, ProjectionType projectionType) {
        assertOnRenderThread();
        projectionMatrixBuffer = gpuBufferSlice;
        VulkanicAPI.projectionType = projectionType;
    }

    public static void labelProjectionMatrix(GpuBufferSlice gpuBufferSlice, String label) {
        if (gpuBufferSlice == null || label == null || label.isBlank()) {
            return;
        }

        projectionMatrixLabels.put(gpuBufferSlice, label);
    }

    private static String shaderInputParityCurrentProjectionLabel() {
        GpuBufferSlice current = projectionMatrixBuffer;
        if (current == null) {
            return "";
        }

        String currentBufferKey = null;
        try {
            currentBufferKey = shaderInputParityBufferKey(resolveVulkanicBuffer(current.buffer()));
        } catch (RuntimeException ignored) {
            // Diagnostic identity should never perturb rendering if a transient buffer is unavailable.
        }

        synchronized (projectionMatrixLabels) {
            for (java.util.Map.Entry<GpuBufferSlice, String> entry : projectionMatrixLabels.entrySet()) {
                GpuBufferSlice labeled = entry.getKey();
                if (labeled == null || labeled.offset() != current.offset() || labeled.length() != current.length()) {
                    continue;
                }
                if (labeled.equals(current)) {
                    return shaderInputParitySanitizeLabel(entry.getValue());
                }
                if (currentBufferKey != null) {
                    try {
                        if (currentBufferKey.equals(shaderInputParityBufferKey(resolveVulkanicBuffer(labeled.buffer())))) {
                            return shaderInputParitySanitizeLabel(entry.getValue());
                        }
                    } catch (RuntimeException ignored) {
                        // Keep searching; this is diagnostic-only identity metadata.
                    }
                }
            }
        }

        return "";
    }

    public static void backupProjectionMatrix() {
        assertOnRenderThread();
        savedProjectionMatrixBuffer = projectionMatrixBuffer;
        savedProjectionType = projectionType;
    }

    public static void restoreProjectionMatrix() {
        assertOnRenderThread();
        projectionMatrixBuffer = savedProjectionMatrixBuffer;
        projectionType = savedProjectionType;
    }

    @Nullable
    public static GpuBufferSlice getProjectionMatrixBuffer() {
        assertOnRenderThread();
        return projectionMatrixBuffer;
    }

    public static ProjectionType getProjectionType() {
        assertOnRenderThread();
        return projectionType;
    }

    public static Matrix4f getModelViewMatrix() {
        assertOnRenderThread();
        return modelViewStack;
    }

    public static Matrix4fStack getModelViewStack() {
        assertOnRenderThread();
        return modelViewStack;
    }

    public static void setTextureMatrix(Matrix4f matrix4f) {
        assertOnRenderThread();
        textureMatrix = new Matrix4f(matrix4f);
    }

    public static void resetTextureMatrix() {
        assertOnRenderThread();
        textureMatrix.identity();
    }

    public static Matrix4f getTextureMatrix() {
        assertOnRenderThread();
        return textureMatrix;
    }

    public static void lineWidth(float f) {
        assertOnRenderThread();
        shaderLineWidth = f;
    }

    public static float getShaderLineWidth() {
        assertOnRenderThread();
        return shaderLineWidth;
    }

    public static void enableScissorForRenderTypeDraws(int i, int j, int k, int l) {
        assertOnRenderThread();
        scissorStateForRenderTypeDraws.enable(i, j, k, l);
    }

    public static void disableScissorForRenderTypeDraws() {
        assertOnRenderThread();
        scissorStateForRenderTypeDraws.disable();
    }

    public static ScissorState getScissorStateForRenderTypeDraws() {
        assertOnRenderThread();
        return scissorStateForRenderTypeDraws;
    }

    public static void setOutputColorTextureOverride(@Nullable GpuTextureView gpuTextureView) {
        assertOnRenderThread();
        outputColorTextureOverride = gpuTextureView;
    }

    @Nullable
    public static GpuTextureView getOutputColorTextureOverride() {
        assertOnRenderThread();
        return outputColorTextureOverride;
    }

    public static void setOutputDepthTextureOverride(@Nullable GpuTextureView gpuTextureView) {
        assertOnRenderThread();
        outputDepthTextureOverride = gpuTextureView;
    }

    @Nullable
    public static GpuTextureView getOutputDepthTextureOverride() {
        assertOnRenderThread();
        return outputDepthTextureOverride;
    }

    public static void setShaderFog(@Nullable GpuBufferSlice gpuBufferSlice) {
        if (fogStartListener != null) {
            fogStartListener.run();
        }
        if (fogEndListener != null) {
            fogEndListener.run();
        }
        shaderFog = gpuBufferSlice;
    }

    @Nullable
    public static GpuBufferSlice getShaderFog() {
        return shaderFog;
    }

    public static void setShaderLights(@Nullable GpuBufferSlice gpuBufferSlice) {
        shaderLightDirections = gpuBufferSlice;
    }

    @Nullable
    public static GpuBufferSlice getShaderLights() {
        return shaderLightDirections;
    }

    public static void setGlobalSettingsUniform(@Nullable GpuBuffer gpuBuffer) {
        globalSettingsUniform = gpuBuffer;
    }

    @Nullable
    public static GpuBuffer getGlobalSettingsUniform() {
        return globalSettingsUniform;
    }

    /**
     * Binds shared/default per-frame uniforms expected by standard pipelines.
     */
    public static void bindDefaultUniforms(RenderPass renderPass) {
        GpuBufferSlice projection = getProjectionMatrixBuffer();
        if (projection != null) {
            renderPass.setUniform("Projection", projection);
        }

        GpuBufferSlice fog = getShaderFog();
        if (fog != null) {
            renderPass.setUniform("Fog", fog);
        }

        GpuBuffer globals = getGlobalSettingsUniform();
        if (globals != null) {
            renderPass.setUniform("Globals", globals);
        }

        GpuBufferSlice lighting = getShaderLights();
        if (lighting != null) {
            renderPass.setUniform("Lighting", lighting);
        }
    }
    
    public static void clearTexImage(CommandContext ctx, int texture, int level, int format, int type, int[] data) {
        getBackend().clearTexImage(ctx, texture, level, format, type, data);
    }
    
    public static void setMaxShaderCompilerThreads(int count) {
        getBackend().setMaxShaderCompilerThreads(count);
    }
    
    public static GraphicsCapabilities getGraphicsCapabilities() {
        return getBackend().getGraphicsCapabilities();
    }

    /**
     * Returns the backend-owned GPU vendor name for shader macro and diagnostics.
     */
    public static String getBackendVendorName() {
        String value = getBackend().getBackendVendorName();
        return value == null || value.isBlank() ? "unknown" : value;
    }

    /**
     * Returns the backend-owned GPU renderer name for shader macro and diagnostics.
     */
    public static String getBackendRendererName() {
        String value = getBackend().getBackendRendererName();
        return value == null || value.isBlank() ? "unknown" : value;
    }

    /**
     * Returns the backend-owned version descriptor for diagnostics.
     */
    public static String getBackendVersionName() {
        String value = getBackend().getBackendVersionName();
        return value == null || value.isBlank() ? "unknown" : value;
    }

    /**
     * Returns backend-owned extension identifiers when the backend exposes them.
     */
    public static java.util.List<String> getBackendEnabledExtensions() {
        java.util.List<String> values = getBackend().getBackendEnabledExtensions();
        return values == null ? java.util.List.of() : java.util.List.copyOf(values);
    }

    /**
     * Returns backend-owned optional feature names for diagnostics/reporting.
     */
    public static java.util.List<String> getBackendOptionalFeatureNames() {
        java.util.List<String> values = getBackend().getBackendOptionalFeatureNames();
        return values == null ? java.util.List.of() : java.util.List.copyOf(values);
    }

    /**
     * Returns backend-owned max texture size capability.
     */
    public static int getBackendMaxTextureSize() {
        return getBackend().getBackendMaxTextureSize();
    }

    /**
     * Returns backend-owned uniform-buffer offset alignment capability.
     */
    public static int getBackendUniformOffsetAlignment() {
        return getBackend().getBackendUniformOffsetAlignment();
    }

    /**
     * Returns backend-owned device info snapshot for diagnostics and warnlist checks.
     */
    public static GpuDevice.GpuDeviceInfo getBackendDeviceInfo() {
        return getBackend().getBackendDeviceInfo();
    }

    /**
     * Returns the backend-native texture handle for transitional integrations.
     *
     * <p>Resolution is delegated to the active backend so callsites stay backend-neutral
     * while GL/Vulkan-specific handle semantics remain backend-owned.</p>
     */
    public static int getTextureHandle(@Nullable GpuTexture texture) {
        if (texture == null) {
            return 0;
        }

        if (!(texture instanceof VulkanicTexture target)) {
            return 0;
        }

        CommandContext ctx = getCommandContext();
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        return directVulkanBackend != null
            ? directVulkanBackend.resolveTextureHandle(ctx, target)
            : getBackend().resolveTextureHandle(ctx, target);
    }

    /**
     * Returns the backend-native buffer handle for transitional integrations.
     *
     * <p>Resolution is delegated to the active backend so callsites stay backend-neutral
     * while GL/Vulkan-specific handle semantics remain backend-owned.</p>
     */
    public static int getBufferHandle(@Nullable GpuBuffer buffer) {
        if (buffer == null) {
            return 0;
        }

        CommandContext ctx = getCommandContext();
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        return directVulkanBackend != null
            ? directVulkanBackend.resolveBufferHandle(ctx, buffer)
            : getBackend().resolveBufferHandle(ctx, buffer);
    }

    /**
     * Returns a backend-native framebuffer handle for a color/depth texture pair.
     */
    public static int resolveFramebufferForTextures(@Nullable GpuTexture colorTexture, @Nullable GpuTexture depthTexture) {
        if (colorTexture == null) {
            return 0;
        }

        if (!(colorTexture instanceof VulkanicTexture colorTarget)) {
            return 0;
        }

        VulkanicTexture depthTarget = depthTexture instanceof VulkanicTexture texture ? texture : null;
        CommandContext ctx = getCommandContext();
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        return directVulkanBackend != null
            ? directVulkanBackend.resolveFramebufferForTextures(ctx, colorTarget, depthTarget)
            : getBackend().resolveFramebufferForTextures(ctx, colorTarget, depthTarget);
    }
    
    public static int getTextureLevelParameter(CommandContext ctx, int target, int level, int pname) {
        return getBackend().getTextureLevelParameter(ctx, target, level, pname);
    }

    /**
     * Queries a level parameter from the currently bound 2D texture.
     */
    public static int getTexture2DLevelParameter(CommandContext ctx, int level, int pname) {
        return getBackend().getTextureLevelParameter(ctx, GL_TEXTURE_2D, level, pname);
    }

    /**
     * Queries the width of a mip level from the currently bound 2D texture.
     */
    public static int getTexture2DLevelWidth(CommandContext ctx, int level) {
        return getTexture2DLevelParameter(ctx, level, GL_TEXTURE_WIDTH);
    }

    /**
     * Queries the height of a mip level from the currently bound 2D texture.
     */
    public static int getTexture2DLevelHeight(CommandContext ctx, int level) {
        return getTexture2DLevelParameter(ctx, level, GL_TEXTURE_HEIGHT);
    }

    /**
     * Uploads shader source using a backend-neutral source string API.
     */
    public static void uploadShaderSource(CommandContext ctx, int shader, CharSequence source) {
        getBackend().uploadShaderSource(ctx, shader, source);
    }

    public static void uploadShaderSource(CommandContext ctx, VulkanicShaderHandle shader, CharSequence source) {
        getBackend().uploadShaderSource(ctx, shader.value(), source);
    }
    
    public static void uploadShaderSource(CommandContext ctx, int shader, long pointerBufferAddress, int stringCount, long lengthsPointer) {
        getBackend().uploadShaderSource(ctx, shader, pointerBufferAddress, stringCount, lengthsPointer);
    }
    
    public static void uniformBlockBinding(CommandContext ctx, int program, int uniformBlockIndex, int uniformBlockBinding) {
        getBackend().uniformBlockBinding(ctx, program, uniformBlockIndex, uniformBlockBinding);
    }
    
    /**
     * @deprecated Prefer {@link #getActiveUniformBlockInfo(CommandContext, int, int)} or
     * {@link #getActiveUniformBlocks(CommandContext, int)} for backend-neutral typed metadata.
     */
    @Deprecated
    public static String retrieveActiveUniformBlockName(CommandContext ctx, int program, int uniformBlockIndex) {
        return getBackend().retrieveActiveUniformBlockName(ctx, program, uniformBlockIndex);
    }

    /**
     * Retrieves active uniform-block metadata at the specified block index.
     */
    public static ActiveUniformBlockInfo getActiveUniformBlockInfo(CommandContext ctx, int program, int uniformBlockIndex) {
        String uniformBlockName = retrieveActiveUniformBlockName(ctx, program, uniformBlockIndex);
        return new ActiveUniformBlockInfo(uniformBlockIndex, uniformBlockName);
    }

    /**
     * Retrieves all active uniform blocks for a program as backend-neutral typed metadata.
     */
    public static java.util.List<ActiveUniformBlockInfo> getActiveUniformBlocks(CommandContext ctx, int program) {
        int activeUniformBlockCount = getProgramParameter(ctx, program, VulkanicProgramParameterName.ACTIVE_UNIFORM_BLOCKS);
        java.util.ArrayList<ActiveUniformBlockInfo> blocks = new java.util.ArrayList<>(Math.max(activeUniformBlockCount, 0));

        for (int blockIndex = 0; blockIndex < activeUniformBlockCount; blockIndex++) {
            blocks.add(getActiveUniformBlockInfo(ctx, program, blockIndex));
        }

        return java.util.List.copyOf(blocks);
    }

    /**
     * Retrieves all active uniform blocks for a backend-neutral program handle.
     */
    public static java.util.List<ActiveUniformBlockInfo> getActiveUniformBlocks(CommandContext ctx, VulkanicProgramHandle program) {
        return getActiveUniformBlocks(ctx, program.value());
    }
    
    public static int generateQueryObject(CommandContext ctx) {
        return getBackend().generateQueryObject(ctx);
    }
    
    public static void initiateQuery(CommandContext ctx, int target, int id) {
        getBackend().initiateQuery(ctx, target, id);
    }

    /**
     * Begins a time-elapsed query.
     */
    public static void beginTimeElapsedQuery(CommandContext ctx, int id) {
        getBackend().initiateQuery(ctx, GL_TIME_ELAPSED, id);
    }
    
    public static void concludeQuery(CommandContext ctx, int target) {
        getBackend().concludeQuery(ctx, target);
    }

    /**
     * Ends a time-elapsed query.
     */
    public static void endTimeElapsedQuery(CommandContext ctx) {
        getBackend().concludeQuery(ctx, GL_TIME_ELAPSED);
    }
    
    public static void disposeQueryObject(CommandContext ctx, int id) {
        getBackend().disposeQueryObject(ctx, id);
    }
    
    public static int retrieveQueryObjectInt(CommandContext ctx, int id, int pname) {
        return getBackend().retrieveQueryObjectInt(ctx, id, pname);
    }

    /**
     * Returns whether query results are available.
     */
    public static boolean isQueryResultAvailable(CommandContext ctx, int id) {
        return getBackend().retrieveQueryObjectInt(ctx, id, GL_QUERY_RESULT_AVAILABLE) == GL_TRUE;
    }
    
    public static long retrieveQueryObjectInt64(CommandContext ctx, int id, int pname) {
        return getBackend().retrieveQueryObjectInt64(ctx, id, pname);
    }

    /**
     * Retrieves a 64-bit query result value.
     */
    public static long getQueryResultInt64(CommandContext ctx, int id) {
        return getBackend().retrieveQueryObjectInt64(ctx, id, GL_QUERY_RESULT);
    }
    
    public static void labelDebugObject(CommandContext ctx, int identifier, int name, String label) {
        getBackend().labelDebugObject(ctx, identifier, name, label);
    }

    /**
     * Queries the implementation's maximum debug label length.
     */
    public static int getMaxDebugLabelLength(CommandContext ctx) {
        return getBackend().getInteger(ctx, GL_MAX_LABEL_LENGTH);
    }

    public static void labelBufferDebugObject(CommandContext ctx, int name, String label) {
        getBackend().labelDebugObject(ctx, GL_BUFFER, name, label);
    }

    public static void labelTextureDebugObject(CommandContext ctx, int name, String label) {
        getBackend().labelDebugObject(ctx, GL_TEXTURE, name, label);
    }

    public static void labelShaderDebugObject(CommandContext ctx, int name, String label) {
        getBackend().labelDebugObject(ctx, GL_SHADER, name, label);
    }

    public static void labelProgramDebugObject(CommandContext ctx, int name, String label) {
        getBackend().labelDebugObject(ctx, GL_PROGRAM, name, label);
    }

    public static void labelVertexArrayDebugObject(CommandContext ctx, int name, String label) {
        getBackend().labelDebugObject(ctx, GL_VERTEX_ARRAY, name, label);
    }
    
    public static void enterDebugGroup(CommandContext ctx, int source, int id, CharSequence message) {
        getBackend().enterDebugGroup(ctx, source, id, message);
    }

    public static void enterApplicationDebugGroup(CommandContext ctx, int id, CharSequence message) {
        getBackend().enterDebugGroup(ctx, GL_DEBUG_SOURCE_APPLICATION, id, message);
    }
    
    public static void exitDebugGroup(CommandContext ctx) {
        getBackend().exitDebugGroup(ctx);
    }
    
    public static void debugMessageControl(CommandContext ctx, int source, int type, int severity, int[] ids, boolean enabled) {
        getBackend().debugMessageControl(ctx, source, type, severity, ids, enabled);
    }

    /**
     * Controls debug-message filtering across all sources and types.
     */
    public static void setDebugMessageControlAll(CommandContext ctx, int severity, boolean enabled) {
        getBackend().debugMessageControl(ctx, GL_DONT_CARE, GL_DONT_CARE, severity, null, enabled);
    }
    
    public static void debugMessageControlKHR(CommandContext ctx, int source, int type, int severity, int[] ids, boolean enabled) {
        getBackend().debugMessageControlKHR(ctx, source, type, severity, ids, enabled);
    }

    /**
     * Controls KHR_debug message filtering across all sources and types.
     */
    public static void setDebugMessageControlAllKHR(CommandContext ctx, int severity, boolean enabled) {
        getBackend().debugMessageControlKHR(ctx, GL_DONT_CARE, GL_DONT_CARE, severity, null, enabled);
    }
    
    public static void debugMessageControlARB(CommandContext ctx, int source, int type, int severity, int[] ids, boolean enabled) {
        getBackend().debugMessageControlARB(ctx, source, type, severity, ids, enabled);
    }

    /**
     * Controls ARB_debug_output filtering across all sources and types.
     */
    public static void setDebugMessageControlAllARB(CommandContext ctx, int severity, boolean enabled) {
        getBackend().debugMessageControlARB(ctx, GL_DONT_CARE, GL_DONT_CARE, severity, null, enabled);
    }
    
    public static void debugMessageEnableAMD(CommandContext ctx, int category, int severity, int[] ids, boolean enabled) {
        getBackend().debugMessageEnableAMD(ctx, category, severity, ids, enabled);
    }
    
    /**
     * Labels an object using the EXT_debug_label extension.
     * @param ctx Command context for recording this command
     * @param type The type identifier for the object
     * @param object The object name/handle
     * @param label The debug label string
     */
    public static void labelObjectExt(CommandContext ctx, int type, int object, String label) {
        getBackend().labelObjectExt(ctx, type, object, label);
    }

    public static void labelBufferExtObject(CommandContext ctx, int object, String label) {
        getBackend().labelObjectExt(ctx, GL_BUFFER_OBJECT_EXT, object, label);
    }

    public static void labelTextureExtObject(CommandContext ctx, int object, String label) {
        getBackend().labelObjectExt(ctx, GL_TEXTURE, object, label);
    }

    public static void labelShaderExtObject(CommandContext ctx, int object, String label) {
        getBackend().labelObjectExt(ctx, GL_SHADER_OBJECT_EXT, object, label);
    }

    public static void labelProgramExtObject(CommandContext ctx, int object, String label) {
        getBackend().labelObjectExt(ctx, GL_PROGRAM_OBJECT_EXT, object, label);
    }

    public static void labelVertexArrayExtObject(CommandContext ctx, int object, String label) {
        getBackend().labelObjectExt(ctx, GL_VERTEX_ARRAY, object, label);
    }
    
    public static boolean supportsKhrDebug() {
        return getBackend().supportsKhrDebug();
    }
    
    public static boolean supportsArbDebugOutput() {
        return getBackend().supportsArbDebugOutput();
    }
    
    public static void setupKhrDebugSystem(int verbosityLevel, boolean synchronous, java.util.function.Consumer<String> messageHandler) {
        getBackend().setupKhrDebugSystem(verbosityLevel, synchronous, messageHandler);
    }
    
    public static void setupArbDebugSystem(int verbosityLevel, boolean synchronous, java.util.function.Consumer<String> messageHandler) {
        getBackend().setupArbDebugSystem(verbosityLevel, synchronous, messageHandler);
    }
    
    public static boolean hasBufferStorageExtension() {
        return getBackend().hasBufferStorageExtension();
    }
    
    public static boolean hasVertexAttribBindingExtension() {
        return getBackend().hasVertexAttribBindingExtension();
    }
    
    /**
     * Sets the depth clear value for subsequent clear operations.
     * @param ctx Command context
     * @param depth The depth value (0.0 to 1.0)
     */
    public static void setClearDepth(CommandContext ctx, double depth) {
        getBackend().setClearDepth(ctx, depth);
    }

    /**
     * Sets the stencil clear value for subsequent stencil clear operations.
     * @param ctx Command context
     * @param stencil The stencil value
     */
    public static void setClearStencil(CommandContext ctx, int stencil) {
        getBackend().setClearStencil(ctx, stencil);
    }
    
    /**
     * Specifies the color buffer to draw into.
     * @param ctx Command context
     * @param mode The draw buffer target
     */
    public static void setDrawBuffer(CommandContext ctx, int mode) {
        getBackend().setDrawBuffer(ctx, mode);
    }

    /**
     * Sets the draw buffer to none.
     */
    public static void setDrawBufferNone(CommandContext ctx) {
        getBackend().setDrawBuffer(ctx, GL_NONE);
    }

    /**
     * Sets the draw buffer to the first color attachment.
     */
    public static void setDrawBufferColorAttachment0(CommandContext ctx) {
        getBackend().setDrawBuffer(ctx, GL_COLOR_ATTACHMENT0);
    }
    
    /**
     * Renders indexed primitives with instancing and a base vertex.
     * @param ctx Command context
     */
    public static void drawIndexedInstancedBaseVertex(CommandContext ctx, int mode, int count, int type, long indices, int instanceCount, int baseVertex) {
        drawIndexedInstancedBaseVertexRaw(ctx, mode, count, type, indices, instanceCount, baseVertex);
    }

    /**
     * Renders indexed primitives with instancing and a base vertex using a backend-neutral primitive mode.
     */
    public static void drawIndexedInstancedBaseVertex(CommandContext ctx, VulkanicPrimitiveMode mode, int count, int type, long indices, int instanceCount, int baseVertex) {
        VulkanicIndexType.fromLegacyGlConstant(type)
            .ifPresentOrElse(
                typedIndexType -> drawIndexedInstancedBaseVertex(ctx, mode, count, typedIndexType, indices, instanceCount, baseVertex),
                () -> drawIndexedInstancedBaseVertex(ctx, mode.toGlModeConstant(), count, type, indices, instanceCount, baseVertex)
            );
    }

    /**
     * Renders indexed primitives with instancing and a base vertex using a backend-agnostic index type.
     */
    public static void drawIndexedInstancedBaseVertex(CommandContext ctx, int mode, int count, VulkanicIndexType indexType, long indices, int instanceCount, int baseVertex) {
        drawIndexedInstancedBaseVertexRaw(ctx, mode, count, indexType.toGlTypeConstant(), indices, instanceCount, baseVertex);
    }

    /**
     * Renders indexed primitives with instancing and a base vertex using backend-neutral primitive and index types.
     */
    public static void drawIndexedInstancedBaseVertex(CommandContext ctx, VulkanicPrimitiveMode mode, int count, VulkanicIndexType indexType, long indices, int instanceCount, int baseVertex) {
        drawIndexedInstancedBaseVertexRaw(ctx, mode.toGlModeConstant(), count, indexType.toGlTypeConstant(), indices, instanceCount, baseVertex);
    }

    private static void drawIndexedInstancedBaseVertexRaw(CommandContext ctx, int mode, int count, int type, long indices, int instanceCount, int baseVertex) {
        dispatchImplementedVoid(
            direct -> direct.drawIndexedInstancedBaseVertex(ctx, mode, count, type, indices, instanceCount, baseVertex),
            activeBackend -> activeBackend.drawIndexedInstancedBaseVertex(ctx, mode, count, type, indices, instanceCount, baseVertex)
        );
    }
    
    /**
     * Renders indexed primitives with a base vertex offset.
     * @param ctx Command context
     */
    public static void drawIndexedBaseVertex(CommandContext ctx, int mode, int count, int type, long indices, int baseVertex) {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        if (directVulkanBackend != null) {
            directVulkanBackend.drawIndexedBaseVertex(ctx, mode, count, type, indices, baseVertex);
            return;
        }
        getBackend().drawIndexedBaseVertex(ctx, mode, count, type, indices, baseVertex);
    }

    /**
     * Renders indexed primitives with a base vertex offset using a backend-neutral primitive mode.
     */
    public static void drawIndexedBaseVertex(CommandContext ctx, VulkanicPrimitiveMode mode, int count, int type, long indices, int baseVertex) {
        VulkanicIndexType.fromLegacyGlConstant(type)
            .ifPresentOrElse(
                typedIndexType -> drawIndexedBaseVertex(ctx, mode, count, typedIndexType, indices, baseVertex),
                () -> getBackend().drawIndexedBaseVertex(ctx, mode.toGlModeConstant(), count, type, indices, baseVertex)
            );
    }

    /**
     * Renders indexed primitives with a base vertex offset using a backend-agnostic index type.
     */
    public static void drawIndexedBaseVertex(CommandContext ctx, int mode, int count, VulkanicIndexType indexType, long indices, int baseVertex) {
        drawIndexedBaseVertex(ctx, mode, count, indexType.toGlTypeConstant(), indices, baseVertex);
    }

    /**
     * Renders indexed primitives with a base vertex offset using backend-neutral primitive and index types.
     */
    public static void drawIndexedBaseVertex(CommandContext ctx, VulkanicPrimitiveMode mode, int count, VulkanicIndexType indexType, long indices, int baseVertex) {
        drawIndexedBaseVertex(ctx, mode.toGlModeConstant(), count, indexType.toGlTypeConstant(), indices, baseVertex);
    }
    
    /**
     * Renders indexed primitives with instancing.
     * @param ctx Command context
     */
    public static void drawIndexedInstanced(CommandContext ctx, int mode, int count, int type, long indices, int instanceCount) {
        java.util.Optional<VulkanicPrimitiveMode> typedMode = VulkanicPrimitiveMode.fromLegacyGlConstant(mode);
        java.util.Optional<VulkanicIndexType> typedIndexType = VulkanicIndexType.fromLegacyGlConstant(type);
        if (typedMode.isPresent() && typedIndexType.isPresent()) {
            drawIndexedInstanced(ctx, typedMode.get(), count, typedIndexType.get(), indices, instanceCount);
            return;
        }

        drawIndexedInstancedRaw(ctx, mode, count, type, indices, instanceCount);
    }

    /**
     * Renders indexed primitives with instancing using a backend-agnostic index type.
     */
    public static void drawIndexedInstanced(CommandContext ctx, int mode, int count, VulkanicIndexType indexType, long indices, int instanceCount) {
        VulkanicPrimitiveMode.fromLegacyGlConstant(mode)
            .ifPresentOrElse(
                typedMode -> drawIndexedInstanced(ctx, typedMode, count, indexType, indices, instanceCount),
                () -> drawIndexedInstancedRaw(ctx, mode, count, indexType.toGlTypeConstant(), indices, instanceCount)
            );
    }

    /**
     * Renders indexed primitives with instancing using backend-neutral primitive and index types.
     */
    public static void drawIndexedInstanced(CommandContext ctx, VulkanicPrimitiveMode mode, int count, VulkanicIndexType indexType, long indices, int instanceCount) {
        drawIndexedInstancedRaw(ctx, mode.toGlModeConstant(), count, indexType.toGlTypeConstant(), indices, instanceCount);
    }

    private static void drawIndexedInstancedRaw(CommandContext ctx, int mode, int count, int type, long indices, int instanceCount) {
        dispatchImplementedVoid(
            direct -> direct.drawIndexedInstanced(ctx, mode, count, type, indices, instanceCount),
            activeBackend -> activeBackend.drawIndexedInstanced(ctx, mode, count, type, indices, instanceCount)
        );
    }
    
    /**
     * Renders primitives using array data with instancing.
     * @param ctx Command context
     */
    public static void drawArraysInstanced(CommandContext ctx, int mode, int first, int count, int instanceCount) {
        dispatchImplementedVoid(
            direct -> direct.drawArraysInstanced(ctx, mode, first, count, instanceCount),
            activeBackend -> activeBackend.drawArraysInstanced(ctx, mode, first, count, instanceCount)
        );
    }

    /**
     * Renders primitives using array data with instancing via backend-neutral primitive mode.
     */
    public static void drawArraysInstanced(CommandContext ctx, VulkanicPrimitiveMode mode, int first, int count, int instanceCount) {
        dispatchImplementedVoid(
            direct -> direct.drawArraysInstanced(ctx, mode.toGlModeConstant(), first, count, instanceCount),
            activeBackend -> activeBackend.drawArraysInstanced(ctx, mode.toGlModeConstant(), first, count, instanceCount)
        );
    }
    
    /**
     * Binds a range of a buffer to a uniform buffer binding point.
     * @param ctx Command context
     */
    public static void bindUniformBufferRange(CommandContext ctx, int target, int index, int buffer, long offset, long size) {
        VulkanicBufferTarget.fromLegacyGlTarget(target)
            .ifPresentOrElse(
                typedTarget -> bindUniformBufferRange(ctx, typedTarget, index, buffer, offset, size),
                () -> dispatchImplementedVoid(
                    direct -> direct.bindUniformBufferRange(ctx, target, index, buffer, offset, size),
                    activeBackend -> activeBackend.bindUniformBufferRange(ctx, target, index, buffer, offset, size)
                )
            );
    }

    /**
     * Binds a range of a buffer to a uniform buffer binding point using backend-neutral target semantics.
     */
    public static void bindUniformBufferRange(CommandContext ctx, VulkanicBufferTarget target, int index, int buffer, long offset, long size) {
        dispatchImplementedVoid(
            direct -> direct.bindUniformBufferRange(ctx, target.toLegacyGlTarget(), index, buffer, offset, size),
            activeBackend -> activeBackend.bindUniformBufferRange(ctx, target, index, buffer, offset, size)
        );
    }

    /**
     * Binds a range of a buffer to a uniform buffer binding point.
     */
    public static void bindUniformBufferRange(CommandContext ctx, int index, int buffer, long offset, long size) {
        dispatchImplementedVoid(
            direct -> direct.bindUniformBufferRange(ctx, VulkanicBufferTarget.UNIFORM.toLegacyGlTarget(), index, buffer, offset, size),
            activeBackend -> activeBackend.bindUniformBufferRange(ctx, VulkanicBufferTarget.UNIFORM, index, buffer, offset, size)
        );
    }
    
    /**
     * Attaches a buffer object to a texture buffer.
     * @param ctx Command context
     */
    public static void texBuffer(CommandContext ctx, int target, int internalFormat, int buffer) {
        VulkanicTextureTarget.fromLegacyGlTarget(target)
            .ifPresentOrElse(
                typedTarget -> texBuffer(ctx, typedTarget, internalFormat, buffer),
                () -> getBackend().texBuffer(ctx, target, internalFormat, buffer)
            );
    }

    /**
     * Attaches a buffer object to a texture buffer using backend-neutral texture-target semantics.
     */
    public static void texBuffer(CommandContext ctx, VulkanicTextureTarget target, int internalFormat, int buffer) {
        getBackend().texBuffer(ctx, target, internalFormat, buffer);
    }

    /**
     * Attaches a buffer object to a texture buffer target.
     */
    public static void bindTextureBufferData(CommandContext ctx, int internalFormat, int buffer) {
        getBackend().texBuffer(ctx, VulkanicTextureTarget.TEXTURE_BUFFER, internalFormat, buffer);
    }
    
    
    public static void setUniform2fv(CommandContext ctx, int location, float[] value) {
        getBackend().setUniform2fv(ctx, location, value);
    }
    
    public static void setUniform3fv(CommandContext ctx, int location, float[] value) {
        getBackend().setUniform3fv(ctx, location, value);
    }
    
    public static void setUniform4fv(CommandContext ctx, int location, float[] value) {
        getBackend().setUniform4fv(ctx, location, value);
    }
    
    public static void bindUniformBufferBase(CommandContext ctx, int bindingPoint, int bufferId) {
        getBackend().bindUniformBufferBase(ctx, bindingPoint, bufferId);
    }
    
    public static void bindFragDataLocation(CommandContext ctx, int program, int colorNumber, CharSequence name) {
        getBackend().bindFragDataLocation(ctx, program, colorNumber, name);
    }
    
    public static int getSynci(CommandContext ctx, long sync, int pname, java.nio.IntBuffer length) {
        return getBackend().getSynci(ctx, sync, pname, length);
    }

    /**
     * Queries the signal status of a sync object.
     */
    public static int getSyncStatus(CommandContext ctx, long sync, java.nio.IntBuffer length) {
        return getBackend().getSynci(ctx, sync, GL_SYNC_STATUS, length);
    }
    
    public static GraphicsCapabilities initializeGraphicsCapabilities() {
        return getBackend().initializeGraphicsCapabilities();
    }
    
    public static boolean checkFunctionAvailable(String functionName) {
        return getBackend().checkFunctionAvailable(functionName);
    }
    
    public static void deleteVertexArrays(CommandContext ctx, int vertexArray) {
        getBackend().deleteVertexArrays(ctx, vertexArray);
    }
    
    public static void multiDrawElementsBaseVertex(CommandContext ctx, int mode, long pCount, int type, long pIndices, int drawCount, long pBaseVertex) {
        getBackend().multiDrawElementsBaseVertex(ctx, mode, pCount, type, pIndices, drawCount, pBaseVertex);
    }

    public static void multiDrawElementsBaseVertex(CommandContext ctx, VulkanicPrimitiveMode mode, long pCount, VulkanicIndexType type, long pIndices, int drawCount, long pBaseVertex) {
        getBackend().multiDrawElementsBaseVertex(ctx, mode, pCount, type, pIndices, drawCount, pBaseVertex);
    }

    
    
    // Additional methods for IrisRenderSystem migration
    
    public static void getIntegerv(CommandContext ctx, int pname, int[] params) {
        getBackend().getIntegerv(ctx, pname, params);
    }

    /**
     * Reads the active viewport as {x, y, width, height}.
     */
    public static void getViewport(CommandContext ctx, int[] params) {
        getBackend().getIntegerv(ctx, GL_VIEWPORT, params);
    }
    
    
    public static void getFloatv(CommandContext ctx, int pname, float[] params) {
        getBackend().getFloatv(ctx, pname, params);
    }

    /**
     * Reads the active clear color as {r, g, b, a}.
     */
    public static void getClearColor(CommandContext ctx, float[] params) {
        getBackend().getFloatv(ctx, GL_COLOR_CLEAR_VALUE, params);
    }
    
    
    public static void uploadTexture1D(CommandContext ctx, int target, int level, int internalformat, int width, int border, int format, int type, java.nio.ByteBuffer pixels) {
        getBackend().uploadTexture1D(ctx, target, level, internalformat, width, border, format, type, pixels);
    }
    
    
    
    public static void uploadTexture3D(CommandContext ctx, int target, int level, int internalformat, int width, int height, int depth, int border, int format, int type, java.nio.ByteBuffer pixels) {
        getBackend().uploadTexture3D(ctx, target, level, internalformat, width, height, depth, border, format, type, pixels);
    }
    
    
    
    
    public static void copyTexImage2D(CommandContext ctx, int target, int level, int internalFormat, int x, int y, int width, int height, int border) {
        getBackend().copyTexImage2D(ctx, target, level, internalFormat, x, y, width, height, border);
    }

    /**
     * Copies pixels from the framebuffer to the currently bound 2D texture.
     */
    public static void copyTexImage2D(CommandContext ctx, int level, int internalFormat, int x, int y, int width, int height, int border) {
        getBackend().copyTexImage2D(ctx, GL_TEXTURE_2D, level, internalFormat, x, y, width, height, border);
    }
    
    
    
    
    public static void setUniform2i(CommandContext ctx, int location, int v0, int v1) {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        if (directVulkanBackend != null) {
            directVulkanBackend.setUniform2i(ctx, location, v0, v1);
            return;
        }
        getBackend().setUniform2i(ctx, location, v0, v1);
    }
    
    
    
    
    
    
    /**
     * Sets texture parameters using an array of integers.
     * 
     * @param ctx Command context for recording this command
     * @param target Texture target (GL_TEXTURE_2D, etc.)
     * @param pname Parameter name
     * @param params Array of parameter values
     */
    public static void texParameteriv(CommandContext ctx, int target, int pname, int[] params) {
        VulkanicTextureTarget typedTarget = VulkanicTextureTarget.fromLegacyGlTarget(target).orElse(null);
        VulkanicTextureParameterName typedParameterName = VulkanicTextureParameterName.fromLegacyGlPName(pname).orElse(null);

        if (typedTarget != null && typedParameterName != null) {
            texParameteriv(ctx, typedTarget, typedParameterName, params);
            return;
        }

        getBackend().texParameteriv(ctx, target, pname, params);
    }

    public static void texParameteriv(
        CommandContext ctx,
        VulkanicTextureTarget target,
        VulkanicTextureParameterName pname,
        int[] params
    ) {
        getBackend().texParameteriv(ctx, target.toLegacyGlTarget(), pname.toLegacyGlPName(), params);
    }

    public static void setTextureSwizzleRgba(
        CommandContext ctx,
        VulkanicTextureTarget target,
        VulkanicTextureSwizzleComponent red,
        VulkanicTextureSwizzleComponent green,
        VulkanicTextureSwizzleComponent blue,
        VulkanicTextureSwizzleComponent alpha
    ) {
        texParameteriv(
            ctx,
            target,
            VulkanicTextureParameterName.SWIZZLE_RGBA,
            new int[] {
                red.toLegacyGlConstant(),
                green.toLegacyGlConstant(),
                blue.toLegacyGlConstant(),
                alpha.toLegacyGlConstant()
            }
        );
    }
    
    
    
    /**
     * Sets a floating-point texture parameter for a texture bound to the specified target.
     * 
     * @param ctx Command context for recording this command
     * @param target The texture target (e.g., GL_TEXTURE_2D)
     * @param pname The parameter name (e.g., GL_TEXTURE_MIN_FILTER)
     * @param param The parameter value
     */
    public static void texParameterf(CommandContext ctx, int target, int pname, float param) {
        getBackend().texParameterf(ctx, target, pname, param);
    }
    
    public static void texParameteri(CommandContext ctx, int target, int pname, int param) {
        VulkanicTextureTarget typedTarget = VulkanicTextureTarget.fromLegacyGlTarget(target).orElse(null);
        VulkanicTextureParameterName typedParameterName = VulkanicTextureParameterName.fromLegacyGlPName(pname).orElse(null);

        if (typedTarget != null && typedParameterName != null) {
            texParameteri(ctx, typedTarget, typedParameterName, param);
            return;
        }

        getBackend().texParameteri(ctx, target, pname, param);
    }

    public static void texParameteri(
        CommandContext ctx,
        VulkanicTextureTarget target,
        VulkanicTextureParameterName pname,
        int param
    ) {
        getBackend().texParameteri(ctx, target, pname, param);
    }

    public static void texParameteri(
        CommandContext ctx,
        VulkanicTextureTarget target,
        VulkanicTextureParameterName pname,
        VulkanicTextureParameterValue param
    ) {
        getBackend().texParameteri(ctx, target, pname, param.toLegacyGlConstant());
    }
    
    
    
    
    
    
    public static void clearBufferfv(CommandContext ctx, int buffer, int drawbuffer, float[] values) {
        getBackend().clearBufferfv(ctx, buffer, drawbuffer, values);
    }
    
    
    public static void clearBufferiv(CommandContext ctx, int buffer, int drawbuffer, int[] values) {
        getBackend().clearBufferiv(ctx, buffer, drawbuffer, values);
    }
    
    
    public static void clearBufferuiv(CommandContext ctx, int buffer, int drawbuffer, int[] values) {
        getBackend().clearBufferuiv(ctx, buffer, drawbuffer, values);
    }
    
    
    /**
     * @deprecated Prefer {@link #getActiveUniformInfo(CommandContext, int, int, int)} or
     * {@link #getActiveUniforms(CommandContext, int, int)} for backend-neutral typed metadata.
     */
    @Deprecated
    public static String getActiveUniform(CommandContext ctx, int program, int index, int size, java.nio.IntBuffer type, java.nio.IntBuffer name) {
        return getBackend().getActiveUniform(ctx, program, index, size, type, name);
    }

    /**
     * Retrieves reflected metadata for an active uniform as a backend-neutral typed structure.
     */
    public static ActiveUniformInfo getActiveUniformInfo(CommandContext ctx, int program, int index, int maxNameLength) {
        java.nio.IntBuffer arraySize = BufferUtils.createIntBuffer(1);
        java.nio.IntBuffer legacyType = BufferUtils.createIntBuffer(1);
        String uniformName = getActiveUniform(ctx, program, index, maxNameLength, arraySize, legacyType);

        int reflectedArraySize = arraySize.get(0);
        int reflectedLegacyType = legacyType.get(0);
        java.util.Optional<VulkanicUniformReflectionType> typedReflectionType =
            VulkanicUniformReflectionType.fromLegacyGlConstant(reflectedLegacyType);

        return new ActiveUniformInfo(uniformName, reflectedArraySize, reflectedLegacyType, typedReflectionType);
    }

    /**
     * Retrieves reflected metadata for an active uniform from a backend-neutral program handle.
     */
    public static ActiveUniformInfo getActiveUniformInfo(
        CommandContext ctx,
        VulkanicProgramHandle program,
        int index,
        int maxNameLength
    ) {
        return getActiveUniformInfo(ctx, program.value(), index, maxNameLength);
    }

    /**
     * Retrieves all active uniforms for a program as backend-neutral typed metadata.
     */
    public static java.util.List<ActiveUniformInfo> getActiveUniforms(CommandContext ctx, int program, int maxNameLength) {
        int activeUniformCount = getProgramParameter(ctx, program, VulkanicProgramParameterName.ACTIVE_UNIFORMS);
        java.util.ArrayList<ActiveUniformInfo> uniforms = new java.util.ArrayList<>(Math.max(activeUniformCount, 0));

        for (int uniformIndex = 0; uniformIndex < activeUniformCount; uniformIndex++) {
            uniforms.add(getActiveUniformInfo(ctx, program, uniformIndex, maxNameLength));
        }

        return java.util.List.copyOf(uniforms);
    }

    /**
     * Retrieves all active uniforms for a backend-neutral program handle.
     */
    public static java.util.List<ActiveUniformInfo> getActiveUniforms(
        CommandContext ctx,
        VulkanicProgramHandle program,
        int maxNameLength
    ) {
        return getActiveUniforms(ctx, program.value(), maxNameLength);
    }

    /**
     * Derives descriptor-style resource layout metadata from linked program reflection.
     *
     * <p>This seam prepares frontend callsites for future Vulkan descriptor-layout synthesis
     * while remaining fully backend-neutral.</p>
     */
    public static PipelineDescriptor.ResourceLayout deriveResourceLayoutFromProgramReflection(
        CommandContext ctx,
        int program,
        int maxNameLength
    ) {
        return deriveResourceLayoutFromProgramReflection(ctx, program, maxNameLength, defaultReflectedResourceStages());
    }

    /**
     * Derives descriptor-style resource layout metadata from linked program reflection.
     *
     * <p>All emitted resource bindings are tagged with the provided stage-visibility
     * metadata for future Vulkan descriptor-layout synthesis.</p>
     */
    public static PipelineDescriptor.ResourceLayout deriveResourceLayoutFromProgramReflection(
        CommandContext ctx,
        int program,
        int maxNameLength,
        java.util.Set<VulkanicShaderStage> stages
    ) {
        if (maxNameLength <= 0) {
            throw new IllegalArgumentException("maxNameLength must be > 0");
        }
        java.util.Set<VulkanicShaderStage> normalizedStages = normalizeReflectedResourceStages(stages);
        PipelineDescriptor.ResourceLayout backendNativeLayout =
            deriveBackendNativeResourceLayoutFromProgramReflection(ctx, program, normalizedStages);
        if (backendNativeLayout != null) {
            return backendNativeLayout;
        }

        java.util.ArrayList<PipelineDescriptor.ResourceBinding> bindings = new java.util.ArrayList<>();
        java.util.LinkedHashSet<String> seenNames = new java.util.LinkedHashSet<>();
        int bindingIndex = 0;
        boolean hasGeneratedStandaloneUniformBlock = false;

        for (ActiveUniformBlockInfo blockInfo : getActiveUniformBlocks(ctx, program)) {
            String name = normalizeReflectedResourceName(blockInfo.name());
            if (name.isBlank() || name.startsWith("gl_")) {
                continue;
            }
            if (!seenNames.add(name)) {
                continue;
            }
            if (GENERATED_STANDALONE_UNIFORM_BLOCK_NAME.equals(name)) {
                hasGeneratedStandaloneUniformBlock = true;
                continue;
            }

            bindings.add(new PipelineDescriptor.ResourceBinding(
                0,
                bindingIndex,
                name,
                PipelineDescriptor.ResourceType.UNIFORM_BUFFER,
                null,
                normalizedStages
            ));
            bindingIndex++;
        }

        for (ActiveUniformInfo uniformInfo : getActiveUniforms(ctx, program, maxNameLength)) {
            java.util.Optional<PipelineDescriptor.ResourceType> resourceType = toReflectedResourceType(uniformInfo.reflectionType());
            if (resourceType.isEmpty()) {
                continue;
            }

            String name = normalizeReflectedResourceName(uniformInfo.name());
            if (name.isBlank() || name.startsWith("gl_")) {
                continue;
            }
            if (!seenNames.add(name)) {
                continue;
            }

            bindings.add(new PipelineDescriptor.ResourceBinding(
                0,
                bindingIndex,
                name,
                resourceType.get(),
                null,
                normalizedStages
            ));
            bindingIndex++;
        }

        if (hasGeneratedStandaloneUniformBlock) {
            bindings.add(new PipelineDescriptor.ResourceBinding(
                0,
                bindingIndex,
                GENERATED_STANDALONE_UNIFORM_BLOCK_NAME,
                PipelineDescriptor.ResourceType.UNIFORM_BUFFER,
                null,
                normalizedStages
            ));
            bindingIndex++;
        }

        return new PipelineDescriptor.ResourceLayout(bindings);
    }

    @Nullable
    private static PipelineDescriptor.ResourceLayout deriveBackendNativeResourceLayoutFromProgramReflection(
        CommandContext ctx,
        int program,
        java.util.Set<VulkanicShaderStage> stages
    ) {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        return directVulkanBackend != null
            ? directVulkanBackend.getLinkedProgramResourceLayout(ctx, program, stages)
            : null;
    }

    /**
     * Derives descriptor-style resource layout metadata using a default max reflected-name length.
     */
    public static PipelineDescriptor.ResourceLayout deriveResourceLayoutFromProgramReflection(CommandContext ctx, int program) {
        return deriveResourceLayoutFromProgramReflection(ctx, program, 256);
    }

    /**
     * Derives descriptor-style resource layout metadata using a default reflected-name
     * length and explicit stage-visibility metadata.
     */
    public static PipelineDescriptor.ResourceLayout deriveResourceLayoutFromProgramReflection(
        CommandContext ctx,
        int program,
        java.util.Set<VulkanicShaderStage> stages
    ) {
        return deriveResourceLayoutFromProgramReflection(ctx, program, 256, stages);
    }

    /**
     * Derives descriptor-style resource layout metadata for a backend-neutral program handle.
     */
    public static PipelineDescriptor.ResourceLayout deriveResourceLayoutFromProgramReflection(
        CommandContext ctx,
        VulkanicProgramHandle program,
        int maxNameLength
    ) {
        return deriveResourceLayoutFromProgramReflection(ctx, program.value(), maxNameLength);
    }

    /**
     * Derives descriptor-style resource layout metadata for a backend-neutral program handle
     * using explicit stage-visibility metadata.
     */
    public static PipelineDescriptor.ResourceLayout deriveResourceLayoutFromProgramReflection(
        CommandContext ctx,
        VulkanicProgramHandle program,
        int maxNameLength,
        java.util.Set<VulkanicShaderStage> stages
    ) {
        return deriveResourceLayoutFromProgramReflection(ctx, program.value(), maxNameLength, stages);
    }

    /**
     * Derives descriptor-style resource layout metadata for a backend-neutral program handle.
     */
    public static PipelineDescriptor.ResourceLayout deriveResourceLayoutFromProgramReflection(
        CommandContext ctx,
        VulkanicProgramHandle program
    ) {
        return deriveResourceLayoutFromProgramReflection(ctx, program.value(), 256);
    }

    /**
     * Derives descriptor-style resource layout metadata for a backend-neutral program handle
     * using explicit stage-visibility metadata.
     */
    public static PipelineDescriptor.ResourceLayout deriveResourceLayoutFromProgramReflection(
        CommandContext ctx,
        VulkanicProgramHandle program,
        java.util.Set<VulkanicShaderStage> stages
    ) {
        return deriveResourceLayoutFromProgramReflection(ctx, program.value(), 256, stages);
    }

    /**
     * Returns a descriptor copy enriched with reflected resource layout metadata.
     */
    public static PipelineDescriptor withReflectedResourceLayout(
        CommandContext ctx,
        PipelineDescriptor descriptor,
        int program,
        int maxNameLength
    ) {
        return withReflectedResourceLayout(ctx, descriptor, program, maxNameLength, defaultReflectedResourceStages());
    }

    /**
     * Returns a descriptor copy enriched with reflected resource layout metadata.
     */
    public static PipelineDescriptor withReflectedResourceLayout(
        CommandContext ctx,
        PipelineDescriptor descriptor,
        int program,
        int maxNameLength,
        java.util.Set<VulkanicShaderStage> stages
    ) {
        return withMergedReflectedResourceLayout(ctx, descriptor, program, maxNameLength, stages);
    }

    /**
     * Returns a descriptor copy enriched with reflected resource layout metadata while preserving
     * the portable pipeline resource contract derived from the originating RenderPipeline.
     *
     * <p>This keeps uniform-buffer and texel-buffer bindings declared by the pipeline even when
     * linked-program reflection only reports sampler uniforms, which is critical for Vulkan
     * descriptor layout parity with the precompiled pipeline cache.</p>
     */
    public static PipelineDescriptor withMergedReflectedResourceLayout(
        CommandContext ctx,
        PipelineDescriptor descriptor,
        int program,
        int maxNameLength,
        java.util.Set<VulkanicShaderStage> stages
    ) {
        return withMergedReflectedResourceLayout(ctx, descriptor, program, maxNameLength, stages, binding -> true);
    }

    public static PipelineDescriptor withMergedReflectedResourceLayout(
        CommandContext ctx,
        PipelineDescriptor descriptor,
        int program,
        int maxNameLength,
        java.util.Set<VulkanicShaderStage> stages,
        java.util.function.Predicate<PipelineDescriptor.ResourceBinding> reflectedResourceFilter
    ) {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        Objects.requireNonNull(reflectedResourceFilter, "reflectedResourceFilter must not be null");
        PipelineDescriptor.ResourceLayout reflectedLayout =
            deriveResourceLayoutFromProgramReflection(ctx, program, maxNameLength, stages);
        reflectedLayout = filterResourceLayout(reflectedLayout, reflectedResourceFilter);
        PipelineDescriptor.ResourceLayout mergedLayout = mergeResourceLayouts(
            descriptor.getResourceLayout(),
            reflectedLayout
        );
        return descriptor.withResourceLayout(mergedLayout);
    }

    private static PipelineDescriptor withNativeReflectedResourceLayout(
        CommandContext ctx,
        PipelineDescriptor descriptor,
        int program,
        int maxNameLength,
        java.util.Set<VulkanicShaderStage> stages,
        java.util.function.Predicate<PipelineDescriptor.ResourceBinding> reflectedResourceFilter
    ) {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        Objects.requireNonNull(reflectedResourceFilter, "reflectedResourceFilter must not be null");
        PipelineDescriptor.ResourceLayout reflectedLayout =
            deriveResourceLayoutFromProgramReflection(ctx, program, maxNameLength, stages);
        reflectedLayout = filterResourceLayout(reflectedLayout, reflectedResourceFilter);
        PipelineDescriptor.ResourceLayout mergedLayout = mergeResourceLayoutsPreservingReflectedBindings(
            descriptor.getResourceLayout(),
            reflectedLayout
        );
        return descriptor.withResourceLayout(mergedLayout);
    }

    public static PipelineDescriptor withMergedReflectedResourceLayout(
        CommandContext ctx,
        PipelineDescriptor descriptor,
        int program,
        int maxNameLength
    ) {
        return withMergedReflectedResourceLayout(
            ctx,
            descriptor,
            program,
            maxNameLength,
            defaultReflectedResourceStages()
        );
    }

    public static PipelineDescriptor withMergedReflectedResourceLayout(
        CommandContext ctx,
        PipelineDescriptor descriptor,
        int program
    ) {
        return withMergedReflectedResourceLayout(ctx, descriptor, program, 256);
    }

    private static PipelineDescriptor.ResourceLayout mergeResourceLayouts(
        PipelineDescriptor.ResourceLayout baseLayout,
        PipelineDescriptor.ResourceLayout reflectedLayout
    ) {
        java.util.List<PipelineDescriptor.ResourceBinding> baseBindings = baseLayout.bindings();
        java.util.List<PipelineDescriptor.ResourceBinding> reflectedBindings = reflectedLayout.bindings();
        if (reflectedBindings.isEmpty()) {
            return baseLayout;
        }

        java.util.Map<String, PipelineDescriptor.ResourceBinding> reflectedByName = new java.util.LinkedHashMap<>();
        for (PipelineDescriptor.ResourceBinding reflectedBinding : reflectedBindings) {
            reflectedByName.put(reflectedBinding.name(), reflectedBinding);
        }

        java.util.List<PipelineDescriptor.ResourceBinding> merged = new java.util.ArrayList<>(baseBindings.size() + reflectedBindings.size());
        java.util.Set<String> seenNames = new java.util.LinkedHashSet<>();
        java.util.Set<String> usedSlots = new java.util.LinkedHashSet<>();
        java.util.Map<Integer, Integer> nextBindingBySet = new java.util.LinkedHashMap<>();

        for (PipelineDescriptor.ResourceBinding baseBinding : baseBindings) {
            usedSlots.add(baseBinding.set() + ":" + baseBinding.binding());
            nextBindingBySet.merge(baseBinding.set(), baseBinding.binding() + 1, Math::max);
        }

        for (PipelineDescriptor.ResourceBinding baseBinding : baseBindings) {
            PipelineDescriptor.ResourceBinding reflectedBinding = reflectedByName.get(baseBinding.name());
            java.util.Set<VulkanicShaderStage> mergedStages = new java.util.LinkedHashSet<>(baseBinding.stages());
            if (reflectedBinding != null) {
                mergedStages.addAll(reflectedBinding.stages());
            }

            merged.add(new PipelineDescriptor.ResourceBinding(
                baseBinding.set(),
                baseBinding.binding(),
                baseBinding.name(),
                reflectedBinding != null ? reflectedBinding.type() : baseBinding.type(),
                reflectedBinding != null ? reflectedBinding.textureFormat() : baseBinding.textureFormat(),
                java.util.Set.copyOf(mergedStages)
            ));
            seenNames.add(baseBinding.name());
        }

        for (PipelineDescriptor.ResourceBinding reflectedBinding : reflectedBindings) {
            if (!seenNames.add(reflectedBinding.name())) {
                continue;
            }

            int set = reflectedBinding.set();
            int binding = nextBindingBySet.getOrDefault(set, 0);
            while (usedSlots.contains(set + ":" + binding)) {
                binding++;
            }
            usedSlots.add(set + ":" + binding);
            nextBindingBySet.put(set, binding + 1);

            merged.add(new PipelineDescriptor.ResourceBinding(
                set,
                binding,
                reflectedBinding.name(),
                reflectedBinding.type(),
                reflectedBinding.textureFormat(),
                reflectedBinding.stages()
            ));
        }

        return new PipelineDescriptor.ResourceLayout(merged);
    }

    private static PipelineDescriptor.ResourceLayout mergeResourceLayoutsPreservingReflectedBindings(
        PipelineDescriptor.ResourceLayout baseLayout,
        PipelineDescriptor.ResourceLayout reflectedLayout
    ) {
        java.util.List<PipelineDescriptor.ResourceBinding> baseBindings = baseLayout.bindings();
        java.util.List<PipelineDescriptor.ResourceBinding> reflectedBindings = reflectedLayout.bindings();
        if (reflectedBindings.isEmpty()) {
            return baseLayout;
        }

        java.util.Map<String, PipelineDescriptor.ResourceBinding> baseByName = new java.util.LinkedHashMap<>();
        for (PipelineDescriptor.ResourceBinding baseBinding : baseBindings) {
            baseByName.put(baseBinding.name(), baseBinding);
        }

        java.util.List<PipelineDescriptor.ResourceBinding> merged = new java.util.ArrayList<>(reflectedBindings.size() + baseBindings.size());
        java.util.Set<String> seenNames = new java.util.LinkedHashSet<>();
        java.util.Set<String> usedSlots = new java.util.LinkedHashSet<>();

        for (PipelineDescriptor.ResourceBinding reflectedBinding : reflectedBindings) {
            PipelineDescriptor.ResourceBinding baseBinding = baseByName.get(reflectedBinding.name());
            java.util.Set<VulkanicShaderStage> mergedStages = new java.util.LinkedHashSet<>(reflectedBinding.stages());
            if (baseBinding != null) {
                mergedStages.addAll(baseBinding.stages());
            }

            merged.add(new PipelineDescriptor.ResourceBinding(
                reflectedBinding.set(),
                reflectedBinding.binding(),
                reflectedBinding.name(),
                reflectedBinding.type(),
                reflectedBinding.textureFormat(),
                java.util.Set.copyOf(mergedStages)
            ));
            seenNames.add(reflectedBinding.name());
            usedSlots.add(reflectedBinding.set() + ":" + reflectedBinding.binding());
        }

        java.util.Map<Integer, Integer> nextBindingBySet = new java.util.LinkedHashMap<>();
        for (PipelineDescriptor.ResourceBinding reflectedBinding : reflectedBindings) {
            nextBindingBySet.merge(reflectedBinding.set(), reflectedBinding.binding() + 1, Math::max);
        }

        for (PipelineDescriptor.ResourceBinding baseBinding : baseBindings) {
            if (!seenNames.add(baseBinding.name())) {
                continue;
            }

            int set = baseBinding.set();
            int binding = nextBindingBySet.getOrDefault(set, 0);
            while (usedSlots.contains(set + ":" + binding)) {
                binding++;
            }
            usedSlots.add(set + ":" + binding);
            nextBindingBySet.put(set, binding + 1);

            merged.add(new PipelineDescriptor.ResourceBinding(
                set,
                binding,
                baseBinding.name(),
                baseBinding.type(),
                baseBinding.textureFormat(),
                baseBinding.stages()
            ));
        }

        return new PipelineDescriptor.ResourceLayout(merged);
    }

    private static PipelineDescriptor.ResourceLayout filterResourceLayout(
        PipelineDescriptor.ResourceLayout layout,
        java.util.function.Predicate<PipelineDescriptor.ResourceBinding> filter
    ) {
        java.util.List<PipelineDescriptor.ResourceBinding> filtered = new java.util.ArrayList<>(layout.bindings().size());
        for (PipelineDescriptor.ResourceBinding binding : layout.bindings()) {
            if (!filter.test(binding)) {
                continue;
            }

            filtered.add(new PipelineDescriptor.ResourceBinding(
                binding.set(),
                binding.binding(),
                binding.name(),
                binding.type(),
                binding.textureFormat(),
                binding.stages()
            ));
        }
        return new PipelineDescriptor.ResourceLayout(filtered);
    }

    /**
     * Returns a descriptor copy enriched with reflected resource layout metadata.
     */
    public static PipelineDescriptor withReflectedResourceLayout(CommandContext ctx, PipelineDescriptor descriptor, int program) {
        return withReflectedResourceLayout(ctx, descriptor, program, 256);
    }

    /**
     * Returns a descriptor copy enriched with reflected resource layout metadata.
     */
    public static PipelineDescriptor withReflectedResourceLayout(
        CommandContext ctx,
        PipelineDescriptor descriptor,
        int program,
        java.util.Set<VulkanicShaderStage> stages
    ) {
        return withReflectedResourceLayout(ctx, descriptor, program, 256, stages);
    }

    /**
     * Returns a descriptor copy enriched with reflected resource layout metadata.
     */
    public static PipelineDescriptor withReflectedResourceLayout(
        CommandContext ctx,
        PipelineDescriptor descriptor,
        VulkanicProgramHandle program,
        int maxNameLength
    ) {
        return withReflectedResourceLayout(ctx, descriptor, program.value(), maxNameLength);
    }

    /**
     * Returns a descriptor copy enriched with reflected resource layout metadata.
     */
    public static PipelineDescriptor withReflectedResourceLayout(
        CommandContext ctx,
        PipelineDescriptor descriptor,
        VulkanicProgramHandle program,
        int maxNameLength,
        java.util.Set<VulkanicShaderStage> stages
    ) {
        return withReflectedResourceLayout(ctx, descriptor, program.value(), maxNameLength, stages);
    }

    /**
     * Returns a descriptor copy enriched with reflected resource layout metadata.
     */
    public static PipelineDescriptor withReflectedResourceLayout(
        CommandContext ctx,
        PipelineDescriptor descriptor,
        VulkanicProgramHandle program
    ) {
        return withReflectedResourceLayout(ctx, descriptor, program.value(), 256);
    }

    /**
     * Returns a descriptor copy enriched with reflected resource layout metadata.
     */
    public static PipelineDescriptor withReflectedResourceLayout(
        CommandContext ctx,
        PipelineDescriptor descriptor,
        VulkanicProgramHandle program,
        java.util.Set<VulkanicShaderStage> stages
    ) {
        return withReflectedResourceLayout(ctx, descriptor, program.value(), 256, stages);
    }

    private static java.util.Set<VulkanicShaderStage> defaultReflectedResourceStages() {
        return java.util.Set.of(VulkanicShaderStage.VERTEX, VulkanicShaderStage.FRAGMENT);
    }

    private static java.util.Set<VulkanicShaderStage> normalizeReflectedResourceStages(
        java.util.Set<VulkanicShaderStage> stages
    ) {
        java.util.Set<VulkanicShaderStage> normalized = java.util.Set.copyOf(
            Objects.requireNonNull(stages, "stages must not be null")
        );
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("stages must not be empty");
        }
        return normalized;
    }

    private static java.util.Optional<PipelineDescriptor.ResourceType> toReflectedResourceType(
        java.util.Optional<VulkanicUniformReflectionType> reflectionType
    ) {
        if (reflectionType.isPresent() && reflectionType.get().isSampler()) {
            VulkanicUniformReflectionType type = reflectionType.get();
            boolean comparison = type == VulkanicUniformReflectionType.SAMPLER_1D_SHADOW
                || type == VulkanicUniformReflectionType.SAMPLER_2D_SHADOW
                || type == VulkanicUniformReflectionType.SAMPLER_CUBE_SHADOW;
            return java.util.Optional.of(comparison
                ? PipelineDescriptor.ResourceType.COMPARISON_SAMPLER
                : PipelineDescriptor.ResourceType.SAMPLER);
        }
        if (reflectionType.isPresent() && reflectionType.get().isImage()) {
            return java.util.Optional.of(PipelineDescriptor.ResourceType.STORAGE_IMAGE);
        }
        return java.util.Optional.empty();
    }

    private static String normalizeReflectedResourceName(String reflectedName) {
        String normalized = Objects.requireNonNull(reflectedName, "reflectedName must not be null").trim();
        while (normalized.endsWith("[0]")) {
            normalized = normalized.substring(0, normalized.length() - 3);
        }
        return normalized;
    }
    
    
    public static void readPixels(CommandContext ctx, int x, int y, int width, int height, int format, int type, float[] pixels) {
        getBackend().readPixels(ctx, x, y, width, height, format, type, pixels);
    }
    
    public static void readPixels(CommandContext ctx, int x, int y, int width, int height, int format, int type, long pixels) {
        getBackend().readPixels(ctx, x, y, width, height, format, type, pixels);
    }
    
    
    
    
    
    
    
    
    
    
    
    
    
    public static void setVertexAttrib4f(CommandContext ctx, int index, float v0, float v1, float v2, float v3) {
        getBackend().setVertexAttrib4f(ctx, index, v0, v1, v2, v3);
    }
    
    
    
    
    
    
    public static void bindImageTexture(CommandContext ctx, int unit, int texture, int level, boolean layered, int layer, int access, int format) {
        getBackend().bindImageTexture(ctx, unit, texture, level, layered, layer, access, format);
    }
    
    
    public static int getMaxImageUnits(CommandContext ctx) {
        return getBackend().getMaxImageUnits(ctx);
    }
    
    
    public static void createBuffers(CommandContext ctx, int[] buffers) {
        getBackend().createBuffers(ctx, buffers);
    }
    
    
    public static void clearBufferSubData(CommandContext ctx, int target, int internalformat, long offset, long size, int format, int type, int[] data) {
        VulkanicBufferTarget.fromLegacyGlTarget(target)
            .ifPresentOrElse(
                typedTarget -> clearBufferSubData(ctx, typedTarget, internalformat, offset, size, format, type, data),
                () -> getBackend().clearBufferSubData(ctx, target, internalformat, offset, size, format, type, data)
            );
    }

    public static void clearBufferSubData(CommandContext ctx, VulkanicBufferTarget target, int internalformat, long offset, long size, int format, int type, int[] data) {
        getBackend().clearBufferSubData(ctx, target.toLegacyGlTarget(), internalformat, offset, size, format, type, data);
    }
    
    
    public static void getProgramiv(CommandContext ctx, int program, int pname, int[] params) {
        java.util.Optional<VulkanicProgramParameterName> typedPName = VulkanicProgramParameterName.fromLegacyGlPName(pname);
        if (typedPName.isPresent()) {
            getProgramiv(ctx, program, typedPName.get(), params);
            return;
        }
        getBackend().getProgramiv(ctx, program, pname, params);
    }

    public static void getProgramiv(CommandContext ctx, int program, VulkanicProgramParameterName pname, int[] params) {
        getBackend().getProgramiv(ctx, program, pname.toLegacyGlPName(), params);
    }
    
    
    
    public static void memoryBarrier(CommandContext ctx, int barriers) {
        getBackend().memoryBarrier(ctx, barriers);
    }

    /**
     * Applies backend-agnostic resource barrier metadata.
     *
     * <p>This is the preferred pre-Vulkan seam over raw OpenGL barrier bitfields.</p>
     */
    public static void applyResourceBarriers(CommandContext ctx, VulkanicResourceBarriers barriers) {
        getBackend().applyResourceBarriers(ctx, barriers);
    }
    
    
    
    
    
    public static void blendFuncSeparatei(CommandContext ctx, int buffer, int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        java.util.Optional<VulkanicBlendFactor> typedSrcRgb = VulkanicBlendFactor.fromLegacyGlConstant(srcRGB);
        java.util.Optional<VulkanicBlendFactor> typedDstRgb = VulkanicBlendFactor.fromLegacyGlConstant(dstRGB);
        java.util.Optional<VulkanicBlendFactor> typedSrcAlpha = VulkanicBlendFactor.fromLegacyGlConstant(srcAlpha);
        java.util.Optional<VulkanicBlendFactor> typedDstAlpha = VulkanicBlendFactor.fromLegacyGlConstant(dstAlpha);

        if (typedSrcRgb.isPresent() && typedDstRgb.isPresent() && typedSrcAlpha.isPresent() && typedDstAlpha.isPresent()) {
            blendFuncSeparatei(
                ctx,
                buffer,
                typedSrcRgb.get(),
                typedDstRgb.get(),
                typedSrcAlpha.get(),
                typedDstAlpha.get()
            );
            return;
        }

        getBackend().blendFuncSeparatei(ctx, buffer, srcRGB, dstRGB, srcAlpha, dstAlpha);
    }

    public static void blendFuncSeparatei(
        CommandContext ctx,
        int buffer,
        VulkanicBlendFactor srcRGB,
        VulkanicBlendFactor dstRGB,
        VulkanicBlendFactor srcAlpha,
        VulkanicBlendFactor dstAlpha
    ) {
        getBackend().blendFuncSeparatei(ctx, buffer, srcRGB, dstRGB, srcAlpha, dstAlpha);
    }
    
    
    
    
    public static int createSampler(CommandContext ctx) {
        return getBackend().createSampler(ctx);
    }
    
    
    public static void deleteSampler(CommandContext ctx, int sampler) {
        getBackend().deleteSampler(ctx, sampler);
    }
    
    
    
    public static void bindSamplers(CommandContext ctx, int first, int[] samplers) {
        dispatchImplementedVoid(
            direct -> direct.bindSamplers(ctx, first, samplers),
            activeBackend -> activeBackend.bindSamplers(ctx, first, samplers)
        );
    }
    
    
    public static void setSamplerParameteri(CommandContext ctx, int sampler, int pname, int param) {
        VulkanicTextureParameterName.fromLegacyGlPName(pname)
            .ifPresentOrElse(
                typedParameterName -> VulkanicTextureParameterValue.fromLegacyGlConstant(param)
                    .ifPresentOrElse(
                        typedValue -> setSamplerParameteri(ctx, sampler, typedParameterName, typedValue),
                        () -> setSamplerParameteri(ctx, sampler, typedParameterName, param)
                    ),
                () -> getBackend().setSamplerParameteri(ctx, sampler, pname, param)
            );
    }

    public static void setSamplerParameteri(
        CommandContext ctx,
        int sampler,
        VulkanicTextureParameterName pname,
        int param
    ) {
        getBackend().setSamplerParameteri(ctx, sampler, pname, param);
    }

    public static void setSamplerParameteri(
        CommandContext ctx,
        int sampler,
        VulkanicTextureParameterName pname,
        VulkanicTextureParameterValue param
    ) {
        getBackend().setSamplerParameteri(ctx, sampler, pname, param);
    }
    
    
    public static void setSamplerParameterf(CommandContext ctx, int sampler, int pname, float param) {
        getBackend().setSamplerParameterf(ctx, sampler, pname, param);
    }
    
    
    public static void setSamplerParameteriv(CommandContext ctx, int sampler, int pname, int[] params) {
        getBackend().setSamplerParameteriv(ctx, sampler, pname, params);
    }
    
    
    
    
    
    
    public static void dispatchComputeIndirect(CommandContext ctx, long offset) {
        getBackend().dispatchComputeIndirect(ctx, offset);
    }
    
    
    
    public static String getString(CommandContext ctx, int name, int index) {
        return getBackend().getString(ctx, name, index);
    }
    
    public static String getString(CommandContext ctx, int name) {
        return getBackend().getString(ctx, name);
    }
    
    
    public static void copyImageSubData(CommandContext ctx, int srcName, int srcTarget, int srcLevel, int srcX, int srcY, int srcZ, 
                                        int dstName, int dstTarget, int dstLevel, int dstX, int dstY, int dstZ, 
                                        int width, int height, int depth) {
        getBackend().copyImageSubData(ctx, srcName, srcTarget, srcLevel, srcX, srcY, srcZ, 
                                      dstName, dstTarget, dstLevel, dstX, dstY, dstZ, 
                                      width, height, depth);
    }

    public static void copyImageSubData(
        CommandContext ctx,
        int srcName,
        VulkanicTextureTarget srcTarget,
        int srcLevel,
        int srcX,
        int srcY,
        int srcZ,
        int dstName,
        VulkanicTextureTarget dstTarget,
        int dstLevel,
        int dstX,
        int dstY,
        int dstZ,
        int width,
        int height,
        int depth
    ) {
        copyImageSubData(
            ctx,
            srcName,
            srcTarget.toLegacyGlTarget(),
            srcLevel,
            srcX,
            srcY,
            srcZ,
            dstName,
            dstTarget.toLegacyGlTarget(),
            dstLevel,
            dstX,
            dstY,
            dstZ,
            width,
            height,
            depth
        );
    }

    public static void copyImageSubData2D(
        CommandContext ctx,
        int srcName,
        int srcLevel,
        int srcX,
        int srcY,
        int srcZ,
        int dstName,
        int dstLevel,
        int dstX,
        int dstY,
        int dstZ,
        int width,
        int height,
        int depth
    ) {
        copyImageSubData(
            ctx,
            srcName,
            VulkanicTextureTarget.TEXTURE_2D,
            srcLevel,
            srcX,
            srcY,
            srcZ,
            dstName,
            VulkanicTextureTarget.TEXTURE_2D,
            dstLevel,
            dstX,
            dstY,
            dstZ,
            width,
            height,
            depth
        );
    }
    
    
    public static int checkFramebufferStatus(CommandContext ctx, int target) {
        return getBackend().checkFramebufferStatus(ctx, target);
    }

    public static int checkFramebufferStatus(CommandContext ctx) {
        return checkFramebufferStatus(ctx, GL_FRAMEBUFFER);
    }

    public static boolean isFramebufferComplete(int framebufferStatus) {
        return framebufferStatus == GL_FRAMEBUFFER_COMPLETE;
    }
    
    
    
    
    
    
    /**
     * Generates mipmaps for a texture bound to the specified target.
     * 
     * @param ctx Command context for recording this command
     * @param target The texture target (e.g., GL_TEXTURE_2D)
     */
    public static void generateMipmap(CommandContext ctx, int target) {
        getBackend().generateMipmap(ctx, target);
    }
    
    
    
    // DSA (Direct State Access) methods - ARB versions
    
    public static void generateTextureMipmapDSA(CommandContext ctx, int texture) {
        getBackend().generateTextureMipmapDSA(ctx, texture);
    }
    
    
    public static void textureParameteri(CommandContext ctx, int texture, int pname, int param) {
        getBackend().textureParameteri(ctx, texture, pname, param);
    }
    
    
    public static void textureParameterf(CommandContext ctx, int texture, int pname, float param) {
        getBackend().textureParameterf(ctx, texture, pname, param);
    }
    
    
    public static void textureParameteriv(CommandContext ctx, int texture, int pname, int[] params) {
        getBackend().textureParameteriv(ctx, texture, pname, params);
    }
    
    
    public static void namedFramebufferReadBuffer(CommandContext ctx, int framebuffer, int mode) {
        getBackend().namedFramebufferReadBuffer(ctx, framebuffer, mode);
    }
    
    
    public static void namedFramebufferDrawBuffers(CommandContext ctx, int framebuffer, int[] bufs) {
        getBackend().namedFramebufferDrawBuffers(ctx, framebuffer, bufs);
    }
    
    
    public static void clearNamedFramebufferfv(CommandContext ctx, int framebuffer, int buffer, int drawbuffer, float[] value) {
        getBackend().clearNamedFramebufferfv(ctx, framebuffer, buffer, drawbuffer, value);
    }
    
    
    public static void clearNamedFramebufferiv(CommandContext ctx, int framebuffer, int buffer, int drawbuffer, int[] value) {
        getBackend().clearNamedFramebufferiv(ctx, framebuffer, buffer, drawbuffer, value);
    }
    
    
    public static void clearNamedFramebufferuiv(CommandContext ctx, int framebuffer, int buffer, int drawbuffer, int[] value) {
        getBackend().clearNamedFramebufferuiv(ctx, framebuffer, buffer, drawbuffer, value);
    }
    
    
    public static int getTextureParameteri(CommandContext ctx, int texture, int pname) {
        return getBackend().getTextureParameteri(ctx, texture, pname);
    }
    
    
    /**
     * Copies a portion of a read framebuffer to a texture subregion using Direct State Access.
     * See {@link GraphicsBackend#copyTextureSubImage2D(CommandContext, int, int, int, int, int, int, int, int)}
     */
    public static void copyTextureSubImage2D(CommandContext ctx, int texture, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
        getBackend().copyTextureSubImage2D(ctx, texture, level, xoffset, yoffset, x, y, width, height);
    }
    
    
    /**
     * Binds a texture to a specified texture unit using Direct State Access.
     * See {@link GraphicsBackend#bindTextureUnit(CommandContext, int, int)}
     */
    public static void bindTextureUnit(CommandContext ctx, int unit, int texture) {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        if (directVulkanBackend != null) {
            directVulkanBackend.bindTextureUnit(ctx, unit, texture);
            return;
        }
        getBackend().bindTextureUnit(ctx, unit, texture);
    }


    /**
     * Binds a texture view to a specified texture unit through backend-owned handle resolution.
     * See {@link GraphicsBackend#bindTextureUnit(CommandContext, int, GpuTextureView)}
     */
    public static void bindTextureUnit(CommandContext ctx, int unit, GpuTextureView textureView) {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        if (directVulkanBackend != null) {
            if (textureView == null) {
                throw new IllegalArgumentException("textureView must not be null");
            }
            if (textureView.isClosed()) {
                throw new IllegalStateException("Cannot bind closed texture view");
            }

            int textureHandle = directVulkanBackend.resolveTextureHandle(ctx, textureView.texture());
            if (textureHandle <= 0) {
                throw new IllegalStateException(
                    "Unable to resolve backend texture handle for view texture: " + textureView.texture().getLabel());
            }
            directVulkanBackend.bindTextureUnit(ctx, unit, textureHandle);
            net.irisshaders.iris.gl.IrisRenderSystem.setTextureBinding(unit, textureHandle);
            return;
        }

        getBackend().bindTextureUnit(ctx, unit, textureView);

        // Iris tracks texture-unit state in a cache used by shader/pipeline integration.
        int textureHandle = getBackend().resolveTextureHandle(ctx, textureView.texture());
        net.irisshaders.iris.gl.IrisRenderSystem.setTextureBinding(unit, textureHandle);
    }
    
    
    /**
     * Creates a new buffer object using Direct State Access.
     * See {@link GraphicsBackend#createBuffers(CommandContext)}
     */
    public static int createBuffers(CommandContext ctx) {
        return getBackend().createBuffers(ctx);
    }
    
    
    /**
     * Uploads float array data to a named buffer using Direct State Access.
     * See {@link GraphicsBackend#namedBufferData(CommandContext, int, float[], int)}
     */
    public static void namedBufferData(CommandContext ctx, int buffer, float[] data, int usage) {
        getBackend().namedBufferData(ctx, buffer, data, usage);
    }
    
    
    /**
     * Copies a rectangular region between two named framebuffers using Direct State Access.
     * See {@link GraphicsBackend#blitNamedFramebuffer(CommandContext, int, int, int, int, int, int, int, int, int, int, int, int)}
     */
    public static void blitNamedFramebuffer(CommandContext ctx, int readFramebuffer, int drawFramebuffer, int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        getBackend().blitNamedFramebuffer(ctx, readFramebuffer, drawFramebuffer, srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }
    
    
    /**
     * Attaches a texture to a framebuffer attachment point using Direct State Access.
     * 
     * @param ctx Command context for recording this command
     * @param framebuffer The framebuffer object
     * @param attachment The attachment point (e.g., GL_COLOR_ATTACHMENT0)
     * @param texture The texture object to attach
     * @param level The mipmap level of the texture to attach
     */
    public static void namedFramebufferTexture(CommandContext ctx, int framebuffer, int attachment, int texture, int level) {
        getBackend().namedFramebufferTexture(ctx, framebuffer, attachment, texture, level);
    }
    
    
    /**
     * Creates a new framebuffer object using Direct State Access.
     * 
     * @param ctx Command context for recording this command
     * @return The framebuffer object ID
     */
    public static int createFramebuffers(CommandContext ctx) {
        return getBackend().createFramebuffers(ctx);
    }
    
    
    
    
    /**
     * Creates a new texture object for a specific target using Direct State Access.
     * 
     * @param ctx Command context for recording this command
     * @param target The texture target (e.g., GL_TEXTURE_2D, GL_TEXTURE_CUBE_MAP)
     * @return The texture object ID
     */
    public static int createTextures(CommandContext ctx, int target) {
        return getBackend().createTextures(ctx, target);
    }
    
    
    
    
    
    /**
     * Queries a framebuffer attachment parameter.
     * @param ctx Command context
     * @param target The framebuffer target
     * @param attachment The attachment point
     * @param pname The parameter name to query
     * @return The queried parameter value
     */
    public static int getFramebufferAttachmentParameteri(CommandContext ctx, int target, int attachment, int pname) {
        return getBackend().getFramebufferAttachmentParameteri(ctx, target, attachment, pname);
    }

    /**
     * Queries the attached object name for a framebuffer attachment.
     */
    public static int getFramebufferAttachmentObjectName(CommandContext ctx, int target, int attachment) {
        return getBackend().getFramebufferAttachmentParameteri(ctx, target, attachment, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
    }

    /**
     * Queries the object name attached to COLOR_ATTACHMENT0.
     */
    public static int getFramebufferColorAttachment0ObjectName(CommandContext ctx, int target) {
        return getFramebufferAttachmentObjectName(ctx, target, GL_COLOR_ATTACHMENT0);
    }

    /**
     * Queries the object name attached to COLOR_ATTACHMENT1.
     */
    public static int getFramebufferColorAttachment1ObjectName(CommandContext ctx, int target) {
        return getFramebufferAttachmentObjectName(ctx, target, GL_COLOR_ATTACHMENT1);
    }

    /**
     * Queries the object name attached to DEPTH_ATTACHMENT.
     */
    public static int getFramebufferDepthAttachmentObjectName(CommandContext ctx, int target) {
        return getFramebufferAttachmentObjectName(ctx, target, GL_DEPTH_ATTACHMENT);
    }

    /**
     * Queries the object name attached to COLOR_ATTACHMENT0 on GL_FRAMEBUFFER.
     */
    public static int getFramebufferColorAttachment0ObjectName(CommandContext ctx) {
        return getFramebufferColorAttachment0ObjectName(ctx, GL_FRAMEBUFFER);
    }

    /**
     * Queries the object name attached to COLOR_ATTACHMENT1 on GL_FRAMEBUFFER.
     */
    public static int getFramebufferColorAttachment1ObjectName(CommandContext ctx) {
        return getFramebufferColorAttachment1ObjectName(ctx, GL_FRAMEBUFFER);
    }

    /**
     * Queries the object name attached to DEPTH_ATTACHMENT on GL_FRAMEBUFFER.
     */
    public static int getFramebufferDepthAttachmentObjectName(CommandContext ctx) {
        return getFramebufferDepthAttachmentObjectName(ctx, GL_FRAMEBUFFER);
    }
    
    
    
    
    
    
    // High-level debug callback wrapper methods that accept functional interfaces
    public static void setupDebugMessageCallback(DebugMessageCallback callback) {
        getBackend().setupDebugMessageCallback(callback);
    }
    
    public static void setupDebugMessageCallbackKHR(DebugMessageCallback callback) {
        getBackend().setupDebugMessageCallbackKHR(callback);
    }
    
    public static void setupDebugMessageCallbackARB(DebugMessageCallback callback) {
        getBackend().setupDebugMessageCallbackARB(callback);
    }
    
    public static void setupDebugMessageCallbackAMD(DebugMessageCallbackAMD callback) {
        getBackend().setupDebugMessageCallbackAMD(callback);
    }
    
    public static void clearDebugMessageCallback() {
        getBackend().clearDebugMessageCallback();
    }
    
    public static void clearDebugMessageCallbackKHR() {
        getBackend().clearDebugMessageCallbackKHR();
    }
    
    public static void clearDebugMessageCallbackARB() {
        getBackend().clearDebugMessageCallbackARB();
    }
    
    public static void clearDebugMessageCallbackAMD() {
        getBackend().clearDebugMessageCallbackAMD();
    }
    
    // GL-style wrapper methods for backward compatibility
    // These delegate to the abstracted methods above
    
    
    
    
    
    
    
    
    
    
    
    
    // GL43+ Vertex Attribute methods
    
    
    /**
     * Specifies the organization of vertex arrays (GL43+).
     */
    public static void setVertexAttribFormat(CommandContext ctx, int attribindex, int size, int type, boolean normalized, int relativeoffset) {
        getBackend().setVertexAttribFormat(ctx, attribindex, size, type, normalized, relativeoffset);
    }
    
    /**
     * Specifies the organization of vertex arrays for integer data (GL43+).
     */
    public static void setVertexAttribIFormat(CommandContext ctx, int attribindex, int size, int type, int relativeoffset) {
        getBackend().setVertexAttribIFormat(ctx, attribindex, size, type, relativeoffset);
    }
    
    /**
     * Associates a vertex attribute and a vertex buffer binding (GL43+).
     */
    public static void setVertexAttribBinding(CommandContext ctx, int attribindex, int bindingindex) {
        getBackend().setVertexAttribBinding(ctx, attribindex, bindingindex);
    }
    
    
    
    // VAO methods
    
    
    
    
    // GL.getCapabilities() and GLUtil support
    
    /**
     * Gets the OpenGL capabilities for the current context.
     * Returns a platform-specific capabilities object that should be cast to the appropriate type.
     * For OpenGL backend, returns GLCapabilities from the LWJGL library.
     * 
     * @return Platform-specific capabilities object (cast to GLCapabilities for OpenGL backend)
     */
    public static Object getGLCapabilities() {
        return getBackend().getGLCapabilities();
    }
    
    /**
     * Sets up debug message callback using GLUtil-style callback.
     * @param stream The PrintStream to write debug messages to
     */
    public static void setupDebugMessageCallback(java.io.PrintStream stream) {
        getBackend().setupDebugMessageCallback(stream);
    }
    
    // Capability checking methods (to avoid casting GLCapabilities outside backends/opengl)
    
    /**
     * Checks if OpenGL 3.2 is supported.
     * @return true if OpenGL 3.2 is supported
     */
    public static boolean checkOpenGL32Support() {
        return getBackend().checkOpenGL32Support();
    }
    
    /**
     * Checks if OpenGL 3.3 is supported.
     * @return true if OpenGL 3.3 is supported
     */
    public static boolean checkOpenGL33Support() {
        return getBackend().checkOpenGL33Support();
    }
    
    /**
     * Checks if ARB_instanced_arrays extension is supported.
     * @return true if ARB_instanced_arrays is supported
     */
    public static boolean checkARBInstancedArraysSupport() {
        return getBackend().checkARBInstancedArraysSupport();
    }
    
    /**
     * Gets the function pointer for glNamedBufferData.
     * @return function pointer, or 0 if not available
     */
    public static long getNamedBufferDataPointer() {
        return getBackend().getNamedBufferDataPointer();
    }
    
    /**
     * Gets the function pointer for glBufferStorage.
     * @return function pointer, or 0 if not available
     */
    public static long getBufferStoragePointer() {
        return getBackend().getBufferStoragePointer();
    }
    
    /**
     * Gets the function pointer for glBindVertexBuffer.
     * @return function pointer, or 0 if not available
     */
    public static long getBindVertexBufferPointer() {
        return getBackend().getBindVertexBufferPointer();
    }
    
    
    /**
     * Gets capability information as a formatted string for debugging.
     * @return formatted capability information
     */
    public static String getCapabilityDebugInfo() {
        return "Your OpenGL support:\n" +
                "openGL version 3.2+: [" + checkOpenGL32Support() + "] <- REQUIRED\n" +
                "Vertex Attribute Buffer Binding: [" + (getBindVertexBufferPointer() != 0) + "] <- optional improvement\n" +
                "Buffer Storage: [" + (getBufferStoragePointer() != 0) + "] <- optional improvement\n";
    }
    
    // Additional GL query and state methods
    
    
    /**
     * Determines if a name corresponds to a framebuffer object.
     */
    public static boolean isFramebuffer(CommandContext ctx, int framebuffer) {
        return getBackend().isFramebuffer(ctx, framebuffer);
    }
    
    
    /**
     * Determines if a name corresponds to a vertex array object.
     */
    public static boolean isVertexArray(CommandContext ctx, int array) {
        return getBackend().isVertexArray(ctx, array);
    }
    
    /**
     * Determines if a name corresponds to a program object.
     */
    public static boolean isProgram(CommandContext ctx, int program) {
        return getBackend().isProgram(ctx, program);
    }
    
    /**
     * Sets the RGB blend equation and the alpha blend equation separately.
     */
    public static void setBlendEquationSeparate(CommandContext ctx, int modeRGB, int modeAlpha) {
        java.util.Optional<VulkanicBlendEquation> typedRgb = VulkanicBlendEquation.fromLegacyGlConstant(modeRGB);
        java.util.Optional<VulkanicBlendEquation> typedAlpha = VulkanicBlendEquation.fromLegacyGlConstant(modeAlpha);

        if (typedRgb.isPresent() && typedAlpha.isPresent()) {
            setBlendEquationSeparate(ctx, typedRgb.get(), typedAlpha.get());
            return;
        }

        getBackend().setBlendEquationSeparate(ctx, modeRGB, modeAlpha);
    }

    public static void setBlendEquationSeparate(CommandContext ctx, VulkanicBlendEquation modeRGB, VulkanicBlendEquation modeAlpha) {
        getBackend().setBlendEquationSeparate(ctx, modeRGB, modeAlpha);
    }
    
    /**
     * Sets the stencil test function.
     */
    public static void setStencilFunc(CommandContext ctx, int func, int ref, int mask) {
        VulkanicStencilCompareOp.fromLegacyGlConstant(func)
            .ifPresentOrElse(
                typedFunc -> setStencilFunc(ctx, typedFunc, ref, mask),
                () -> dispatchImplementedVoid(
                    direct -> direct.setStencilFunc(ctx, func, ref, mask),
                    activeBackend -> activeBackend.setStencilFunc(ctx, func, ref, mask)
                )
            );
    }

    public static void setStencilFunc(CommandContext ctx, VulkanicStencilCompareOp func, int ref, int mask) {
        dispatchImplementedVoid(
            direct -> direct.setStencilFunc(ctx, toLegacyStencilCompareOp(func), ref, mask),
            activeBackend -> activeBackend.setStencilFunc(ctx, func, ref, mask)
        );
    }

    /**
     * Sets the stencil test function for a specific face.
     */
    public static void setStencilFuncSeparate(CommandContext ctx, int face, int func, int ref, int mask) {
        java.util.Optional<VulkanicStencilFace> typedFace = VulkanicStencilFace.fromLegacyGlConstant(face);
        java.util.Optional<VulkanicStencilCompareOp> typedFunc = VulkanicStencilCompareOp.fromLegacyGlConstant(func);

        if (typedFace.isPresent() && typedFunc.isPresent()) {
            setStencilFuncSeparate(ctx, typedFace.get(), typedFunc.get(), ref, mask);
            return;
        }

        dispatchImplementedVoid(
            direct -> direct.setStencilFuncSeparate(ctx, face, func, ref, mask),
            activeBackend -> activeBackend.setStencilFuncSeparate(ctx, face, func, ref, mask)
        );
    }

    /**
     * Sets the stencil test function for a specific face using backend-neutral semantics.
     */
    public static void setStencilFuncSeparate(CommandContext ctx, VulkanicStencilFace face, VulkanicStencilCompareOp func, int ref, int mask) {
        dispatchImplementedVoid(
            direct -> direct.setStencilFuncSeparate(ctx, toLegacyStencilFace(face), toLegacyStencilCompareOp(func), ref, mask),
            activeBackend -> activeBackend.setStencilFuncSeparate(ctx, face, func, ref, mask)
        );
    }

    /**
     * Sets stencil operations for stencil-fail, depth-fail, and depth-pass outcomes.
     */
    public static void setStencilOp(CommandContext ctx, int stencilFailOp, int depthFailOp, int depthPassOp) {
        java.util.Optional<VulkanicStencilOperation> typedStencilFailOp = VulkanicStencilOperation.fromLegacyGlConstant(stencilFailOp);
        java.util.Optional<VulkanicStencilOperation> typedDepthFailOp = VulkanicStencilOperation.fromLegacyGlConstant(depthFailOp);
        java.util.Optional<VulkanicStencilOperation> typedDepthPassOp = VulkanicStencilOperation.fromLegacyGlConstant(depthPassOp);

        if (typedStencilFailOp.isPresent() && typedDepthFailOp.isPresent() && typedDepthPassOp.isPresent()) {
            setStencilOp(ctx, typedStencilFailOp.get(), typedDepthFailOp.get(), typedDepthPassOp.get());
            return;
        }

        dispatchImplementedVoid(
            direct -> direct.setStencilOp(ctx, stencilFailOp, depthFailOp, depthPassOp),
            activeBackend -> activeBackend.setStencilOp(ctx, stencilFailOp, depthFailOp, depthPassOp)
        );
    }

    /**
     * Sets stencil operations using backend-neutral semantics.
     */
    public static void setStencilOp(
        CommandContext ctx,
        VulkanicStencilOperation stencilFailOp,
        VulkanicStencilOperation depthFailOp,
        VulkanicStencilOperation depthPassOp
    ) {
        dispatchImplementedVoid(
            direct -> direct.setStencilOp(
                ctx,
                toLegacyStencilOperation(stencilFailOp),
                toLegacyStencilOperation(depthFailOp),
                toLegacyStencilOperation(depthPassOp)
            ),
            activeBackend -> activeBackend.setStencilOp(ctx, stencilFailOp, depthFailOp, depthPassOp)
        );
    }

    /**
     * Sets stencil operations for a specific face.
     */
    public static void setStencilOpSeparate(CommandContext ctx, int face, int stencilFailOp, int depthFailOp, int depthPassOp) {
        java.util.Optional<VulkanicStencilFace> typedFace = VulkanicStencilFace.fromLegacyGlConstant(face);
        java.util.Optional<VulkanicStencilOperation> typedStencilFailOp = VulkanicStencilOperation.fromLegacyGlConstant(stencilFailOp);
        java.util.Optional<VulkanicStencilOperation> typedDepthFailOp = VulkanicStencilOperation.fromLegacyGlConstant(depthFailOp);
        java.util.Optional<VulkanicStencilOperation> typedDepthPassOp = VulkanicStencilOperation.fromLegacyGlConstant(depthPassOp);

        if (typedFace.isPresent() && typedStencilFailOp.isPresent() && typedDepthFailOp.isPresent() && typedDepthPassOp.isPresent()) {
            setStencilOpSeparate(
                ctx,
                typedFace.get(),
                typedStencilFailOp.get(),
                typedDepthFailOp.get(),
                typedDepthPassOp.get()
            );
            return;
        }

        dispatchImplementedVoid(
            direct -> direct.setStencilOpSeparate(ctx, face, stencilFailOp, depthFailOp, depthPassOp),
            activeBackend -> activeBackend.setStencilOpSeparate(ctx, face, stencilFailOp, depthFailOp, depthPassOp)
        );
    }

    /**
     * Sets stencil operations for a specific face using backend-neutral semantics.
     */
    public static void setStencilOpSeparate(
        CommandContext ctx,
        VulkanicStencilFace face,
        VulkanicStencilOperation stencilFailOp,
        VulkanicStencilOperation depthFailOp,
        VulkanicStencilOperation depthPassOp
    ) {
        dispatchImplementedVoid(
            direct -> direct.setStencilOpSeparate(
                ctx,
                toLegacyStencilFace(face),
                toLegacyStencilOperation(stencilFailOp),
                toLegacyStencilOperation(depthFailOp),
                toLegacyStencilOperation(depthPassOp)
            ),
            activeBackend -> activeBackend.setStencilOpSeparate(ctx, face, stencilFailOp, depthFailOp, depthPassOp)
        );
    }

    /**
     * Sets the stencil write mask.
     */
    public static void setStencilWriteMask(CommandContext ctx, int mask) {
        dispatchImplementedVoid(
            direct -> direct.setStencilWriteMask(ctx, mask),
            activeBackend -> activeBackend.setStencilWriteMask(ctx, mask)
        );
    }

    /**
     * Sets the stencil write mask for a specific face.
     */
    public static void setStencilWriteMaskSeparate(CommandContext ctx, int face, int mask) {
        VulkanicStencilFace.fromLegacyGlConstant(face)
            .ifPresentOrElse(
                typedFace -> setStencilWriteMaskSeparate(ctx, typedFace, mask),
                () -> dispatchImplementedVoid(
                    direct -> direct.setStencilWriteMaskSeparate(ctx, face, mask),
                    activeBackend -> activeBackend.setStencilWriteMaskSeparate(ctx, face, mask)
                )
            );
    }

    /**
     * Sets the stencil write mask for a specific face using backend-neutral semantics.
     */
    public static void setStencilWriteMaskSeparate(CommandContext ctx, VulkanicStencilFace face, int mask) {
        dispatchImplementedVoid(
            direct -> direct.setStencilWriteMaskSeparate(ctx, toLegacyStencilFace(face), mask),
            activeBackend -> activeBackend.setStencilWriteMaskSeparate(ctx, face, mask)
        );
    }

    private static int toLegacyStencilCompareOp(VulkanicStencilCompareOp op) {
        return switch (op) {
            case NEVER -> GL_NEVER;
            case LESS -> GL_LESS;
            case EQUAL -> GL_EQUAL;
            case LEQUAL -> GL_LEQUAL;
            case GREATER -> GL_GREATER;
            case NOTEQUAL -> GL_NOTEQUAL;
            case GEQUAL -> GL_GEQUAL;
            case ALWAYS -> GL_ALWAYS;
        };
    }

    private static int toLegacyStencilFace(VulkanicStencilFace face) {
        return switch (face) {
            case FRONT -> GL_FRONT;
            case BACK -> GL_BACK;
            case FRONT_AND_BACK -> GL_FRONT_AND_BACK;
        };
    }

    private static int toLegacyStencilOperation(VulkanicStencilOperation op) {
        return switch (op) {
            case KEEP -> GL_KEEP;
            case ZERO -> GL_ZERO;
            case REPLACE -> GL_REPLACE;
            case INCREMENT_CLAMP -> GL_INCR;
            case DECREMENT_CLAMP -> GL_DECR;
            case INVERT -> GL_INVERT;
            case INCREMENT_WRAP -> GL_INCR_WRAP;
            case DECREMENT_WRAP -> GL_DECR_WRAP;
        };
    }
    
    
    
    // CommandContext-aware methods for Batch 23
    
    /**
     * Dispatches compute shader work groups.
     * 
     * @param ctx Command context for recording this command
     * @param workX Number of work groups in X dimension
     * @param workY Number of work groups in Y dimension
     * @param workZ Number of work groups in Z dimension
     */
    public static void dispatchCompute(CommandContext ctx, int workX, int workY, int workZ) {
        getBackend().dispatchCompute(ctx, workX, workY, workZ);
    }
    
    /**
     * Checks if a name corresponds to a buffer object.
     * 
     * @param ctx Command context for recording this command
     * @param buffer The buffer name to check
     * @return true if buffer is a valid buffer object name
     */
    public static boolean isBuffer(CommandContext ctx, int buffer) {
        return getBackend().isBuffer(ctx, buffer);
    }
    
    public static boolean isEnabled(CommandContext ctx, int cap) {
        return getBackend().isEnabled(ctx, cap);
    }

    /**
     * Tests whether a capability is enabled using backend-neutral semantics.
     */
    public static boolean isEnabled(CommandContext ctx, VulkanicCapability capability) {
        return getBackend().isEnabled(ctx, capability);
    }
    
    /**
     * Retrieves the index of a uniform block in a shader program.
     * 
     * @param ctx Command context for recording this command
     * @param program The shader program
     * @param uniformBlockName Name of the uniform block
     * @return The index of the uniform block
     */
    public static int getUniformBlockIndex(CommandContext ctx, int program, String uniformBlockName) {
        return getBackend().getUniformBlockIndex(ctx, program, uniformBlockName);
    }

    // =========================================================================
    // Phase 3a: Buffer Lifecycle — high-level managed buffer operations
    // =========================================================================

    /**
     * Creates a new GPU buffer managed by the Vulkanic abstraction layer.
     *
     * @param label debug label for the buffer (may be null)
     * @param usage usage flags (VulkanicBuffer.USAGE_* constants)
     * @param size  size in bytes (must be > 0)
     * @return a new VulkanicBuffer
     */
    public static VulkanicBuffer createManagedBuffer(java.util.function.Supplier<String> label, int usage, int size) {
        return getBackend().createManagedBuffer(label, usage, size);
    }

    /**
     * Creates a new GPU buffer initialized with the given data.
     *
     * @param label       debug label (may be null)
     * @param usage       usage flags (VulkanicBuffer.USAGE_* constants)
     * @param initialData initial contents (must have remaining bytes)
     * @return a new VulkanicBuffer containing the uploaded data
     */
    public static VulkanicBuffer createManagedBuffer(java.util.function.Supplier<String> label, int usage,
                                                      java.nio.ByteBuffer initialData) {
        return getBackend().createManagedBuffer(label, usage, initialData);
    }

    /**
     * Maps a managed buffer for CPU access.
     *
     * @param buffer the buffer to map
     * @param read   true if the mapping is used for reading
     * @param write  true if the mapping is used for writing
     * @return a MappedView providing CPU-side access; must be closed when done
     */
    public static VulkanicBuffer.MappedView mapManagedBuffer(VulkanicBuffer buffer, boolean read, boolean write) {
        return getBackend().mapManagedBuffer(buffer, read, write);
    }

    // =========================================================================
    // Phase 3b: Texture Lifecycle — high-level managed texture operations
    // =========================================================================

    /**
     * Creates a new GPU texture managed by the Vulkanic abstraction layer.
     *
     * @param label         debug label (may be null)
     * @param usage         usage flags (VulkanicTexture.USAGE_* constants)
     * @param format        texture format
     * @param width         width in pixels
     * @param height        height in pixels
     * @param depthOrLayers depth (3D) or layer count (array); usually 1
     * @param mipLevels     number of mip levels (at least 1)
     * @return a new VulkanicTexture
     */
    public static VulkanicTexture createManagedTexture(String label, int usage,
            VulkanicTextureFormat format, int width, int height, int depthOrLayers, int mipLevels) {
        return getBackend().createManagedTexture(label, usage, format, width, height, depthOrLayers, mipLevels);
    }

    /**
     * Creates a view of the given texture covering all its mip levels.
     *
     * @param texture the parent texture
     * @return a VulkanicTextureView
     */
    public static VulkanicTextureView createManagedTextureView(VulkanicTexture texture) {
        return getBackend().createManagedTextureView(texture);
    }

    /**
     * Creates a view of the given texture covering a specific mip range.
     *
     * @param texture       the parent texture
     * @param baseMipLevel  first mip level to expose
     * @param mipLevelCount number of mip levels to expose
     * @return a VulkanicTextureView
     */
    public static VulkanicTextureView createManagedTextureView(VulkanicTexture texture,
                                                                int baseMipLevel, int mipLevelCount) {
        return getBackend().createManagedTextureView(texture, baseMipLevel, mipLevelCount);
    }

    /**
     * Creates a managed texture view for a legacy texture handle when a render-pass binding path
     * only has access to the bound texture object name.
     *
     * @param legacyTextureHandle legacy texture object handle
     * @return a managed texture view, or {@code null} when the active backend cannot recover one
     */
    @Nullable
    public static VulkanicTextureView createManagedLegacyTextureView(int legacyTextureHandle) {
        return getBackend().createManagedLegacyTextureView(legacyTextureHandle);
    }

    // =========================================================================
    // Phase 3c: Pipeline Objects
    // =========================================================================

    /**
     * Precompiles a render pipeline through the active backend.
     *
     * <p>This keeps startup callsites backend-neutral and avoids coupling to a
     * concrete {@code GpuDevice} implementation when validating shader/pipeline
     * readiness.</p>
     */
    public static CompiledRenderPipeline precompileRenderPipeline(
        RenderPipeline pipeline,
        @Nullable BiFunction<ResourceLocation, ShaderType, String> sourceProvider
    ) {
        return getBackend().precompileRenderPipeline(pipeline, sourceProvider);
    }

    /**
     * Clears backend-owned precompiled pipeline caches.
     */
    public static void clearBackendPipelineCache() {
        getBackend().clearPrecompiledPipelineCache();
    }

    /**
     * Creates (or retrieves a cached) compiled render pipeline.
     *
     * @param descriptor the pipeline descriptor
     * @return a PipelineHandle for the compiled pipeline
     */
    public static PipelineHandle createPipeline(PipelineDescriptor descriptor) {
        return getBackend().createPipeline(descriptor);
    }

    /**
     * Creates a pipeline handle compatible with the attachment contract of an existing framebuffer.
     */
    public static PipelineHandle createPipeline(PipelineDescriptor descriptor, int framebuffer) {
        return getBackend().createPipeline(descriptor, framebuffer);
    }

    /**
     * Creates a pipeline handle compatible with an explicit render-target attachment contract.
     */
    public static PipelineHandle createPipeline(PipelineDescriptor descriptor, VulkanicRenderTargetDescriptor renderTarget) {
        return getBackend().createPipeline(descriptor, renderTarget);
    }

    /**
     * Convenience overload: creates a pipeline directly from a Blaze3D RenderPipeline.
     *
     * @param pipeline the RenderPipeline to compile
     * @return a PipelineHandle for the compiled pipeline
     */
    public static PipelineHandle createPipeline(net.blaze3d.pipeline.RenderPipeline pipeline) {
        return getBackend().createPipeline(PipelineDescriptor.fromRenderPipeline(pipeline));
    }

    /**
     * Convenience overload: creates a pipeline descriptor carrying portable state
     * and precompiled SPIR-V modules, then compiles through the active backend.
     */
    public static PipelineHandle createPipeline(
        PipelineDescriptor.PortableState portableState,
        java.util.List<VulkanicSpirvModule> spirvModules
    ) {
        return getBackend().createPipeline(
            PipelineDescriptor.fromPortableStateAndSpirvModules(portableState, spirvModules)
        );
    }

    public static java.util.List<VulkanicSpirvModule> getLinkedProgramSpirvModules(CommandContext ctx, int program) {
        return getBackend().getLinkedProgramSpirvModules(ctx, program);
    }

    public static PipelineDescriptor createLiveProgramPipelineDescriptor(
        CommandContext ctx,
        PipelineDescriptor baseDescriptor,
        int program
    ) {
        return createLiveProgramPipelineDescriptor(ctx, baseDescriptor, program, binding -> true);
    }

    public static PipelineDescriptor createLiveProgramPipelineDescriptor(
        CommandContext ctx,
        PipelineDescriptor baseDescriptor,
        int program,
        java.util.function.Predicate<PipelineDescriptor.ResourceBinding> reflectedResourceFilter
    ) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(baseDescriptor, "baseDescriptor must not be null");
        Objects.requireNonNull(reflectedResourceFilter, "reflectedResourceFilter must not be null");

        java.util.List<VulkanicSpirvModule> linkedModules = getLinkedProgramSpirvModules(ctx, program);
        if (linkedModules.isEmpty()) {
            return withMergedReflectedResourceLayout(
                ctx,
                baseDescriptor,
                program,
                256,
                defaultReflectedResourceStages(),
                reflectedResourceFilter
            );
        }

        PipelineDescriptor descriptorWithModules = PipelineDescriptor
            .fromPortableStateAndSpirvModules(baseDescriptor.getPortableState(), linkedModules)
            .withPushConstantRanges(baseDescriptor.getPushConstantRanges())
            .withResourceLayout(baseDescriptor.getResourceLayout());

        java.util.Set<VulkanicShaderStage> stages = linkedModules.stream()
            .map(VulkanicSpirvModule::stage)
            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        if (stages.isEmpty()) {
            return withNativeReflectedResourceLayout(
                ctx,
                descriptorWithModules,
                program,
                256,
                defaultReflectedResourceStages(),
                reflectedResourceFilter
            );
        }

        return withNativeReflectedResourceLayout(
            ctx,
            descriptorWithModules,
            program,
            256,
            java.util.Set.copyOf(stages),
            reflectedResourceFilter
        );
    }

    /**
     * Builds a pipeline descriptor from portable state plus precompiled SPIR-V modules.
     */
    public static PipelineDescriptor createPipelineDescriptor(
        PipelineDescriptor.PortableState portableState,
        java.util.List<VulkanicSpirvModule> spirvModules
    ) {
        return PipelineDescriptor.fromPortableStateAndSpirvModules(portableState, spirvModules);
    }

    /**
     * Creates a descriptor-pool-style allocation domain for descriptor sets.
     */
    public static DescriptorPoolHandle createDescriptorPool(DescriptorPoolDescriptor descriptor) {
        return getBackend().createDescriptorPool(descriptor);
    }

    /**
     * Allocates a descriptor set from a pool for a specific pipeline descriptor layout.
     */
    public static DescriptorSetHandle allocateDescriptorSet(DescriptorPoolHandle pool,
            PipelineDescriptor descriptor) {
        return getBackend().allocateDescriptorSet(pool, descriptor);
    }

    /**
     * Updates resources for a descriptor set allocation.
     */
    public static void updateDescriptorSet(DescriptorSetHandle descriptorSet,
            PipelineResourceBindings bindings) {
        getBackend().updateDescriptorSet(descriptorSet, bindings);
    }

    /**
     * Binds an allocated descriptor set for the given pipeline.
     */
    public static void bindDescriptorSet(CommandContext ctx,
            PipelineHandle pipeline,
            PipelineDescriptor descriptor,
            DescriptorSetHandle descriptorSet) {
        getBackend().bindDescriptorSet(ctx, pipeline, descriptor, descriptorSet);
    }

    /**
     * Resets/recycles all descriptor-set allocations in a pool.
     */
    public static void resetDescriptorPool(DescriptorPoolHandle pool) {
        getBackend().resetDescriptorPool(pool);
    }

    /**
     * Binds descriptor-style resources for a compiled pipeline.
     *
     * @param ctx command context
     * @param pipeline compiled pipeline handle
     * @param descriptor pipeline descriptor containing resource layout metadata
     * @param bindings resources keyed by descriptor resource names
     */
    public static void bindPipelineResources(CommandContext ctx,
            PipelineHandle pipeline,
            PipelineDescriptor descriptor,
            PipelineResourceBindings bindings) {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        if (directVulkanBackend != null) {
            directVulkanBackend.bindPipelineResources(ctx, pipeline, descriptor, bindings);
            return;
        }
        getBackend().bindPipelineResources(ctx, pipeline, descriptor, bindings);
    }

    public static void traceShaderInputParityResources(
            String source,
            PipelineHandle pipeline,
            PipelineDescriptor descriptor,
            PipelineResourceBindings bindings) {
        if (!shouldTraceShaderInputParityLog()) {
            return;
        }

        java.util.List<String> resources = new java.util.ArrayList<>();
        for (PipelineDescriptor.ResourceBinding resourceBinding : descriptor.getResourceLayout().bindings()) {
            switch (resourceBinding.type()) {
                case UNIFORM_BUFFER -> {
                    VulkanicBufferSlice slice = bindings.getUniformBufferBindingOrNull(resourceBinding.name());
                    if (slice != null) {
                        resources.add(describeShaderInputParityUniform(resourceBinding, slice));
                    }
                }
                case SAMPLER, COMPARISON_SAMPLER -> {
                    PipelineResourceBindings.SamplerBinding samplerBinding = bindings.getSamplerBindingOrNull(resourceBinding.name());
                    if (samplerBinding != null) {
                        recordScopedCompositeColortex0Binding(source, pipeline, descriptor, resourceBinding, samplerBinding);
                        resources.add(describeShaderInputParitySampler(resourceBinding, samplerBinding));
                    }
                }
                case STORAGE_IMAGE, TEXEL_BUFFER -> {
                    // Not logged yet: current parity pass only normalizes shader-visible UBOs and samplers.
                }
            }
        }

        if (resources.isEmpty()) {
            return;
        }

        LOGGER.info(
            "ShaderInputParityResources backend={} source={} pipelineLocation={} vertexShader={} fragmentShader={} pipelineHandle={} pipelineKey={} stableKey={} {} {} resources=[{}]",
            getActiveBackendType().name().toLowerCase(java.util.Locale.ROOT),
            source,
            descriptor.getPortableState().location(),
            descriptor.getPortableState().vertexShader(),
            descriptor.getPortableState().fragmentShader(),
            pipeline == null ? "none" : pipeline.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(pipeline)),
            descriptor.getPipelineCompilationKey(),
            descriptor.getStableCacheKey(),
            shaderInputParitySemanticDrawContextFields(),
            shaderInputParityDeterministicContextFields(),
            String.join(", ", resources)
        );
    }

    public static void traceShaderInputParitySyntheticResources(
            String source,
            String pipelineLocation,
            @Nullable String vertexShader,
            @Nullable String fragmentShader,
            String stableKey,
            java.util.List<String> resources) {
        if (resources == null || resources.isEmpty() || !shouldTraceShaderInputParityLog()) {
            return;
        }

        LOGGER.info(
            "ShaderInputParityResources backend={} source={} pipelineLocation={} vertexShader={} fragmentShader={} pipelineHandle=none pipelineKey=synthetic stableKey={} {} {} resources=[{}]",
            getActiveBackendType().name().toLowerCase(java.util.Locale.ROOT),
            source,
            shaderInputParitySanitizeLabel(pipelineLocation),
            vertexShader == null || vertexShader.isBlank() ? "unknown" : shaderInputParitySanitizeLabel(vertexShader),
            fragmentShader == null || fragmentShader.isBlank() ? "unknown" : shaderInputParitySanitizeLabel(fragmentShader),
            shaderInputParitySanitizeLabel(stableKey),
            shaderInputParitySemanticDrawContextFields(),
            shaderInputParityDeterministicContextFields(),
            String.join(", ", resources)
        );
    }

    public static String shaderInputParitySamplerResource(String name, int unit, @Nullable GpuTextureView textureView) {
        StringBuilder builder = new StringBuilder();
        builder.append(shaderInputParitySanitizeLabel(name))
            .append("{layout=set:0,binding:").append(unit)
            .append(",type=SAMPLER")
            .append(",stages:[FRAGMENT, VERTEX]")
            .append(",sampler={unit=").append(unit)
            .append(",samplerObject=none");

        if (textureView == null) {
            return builder.append(",view=missing}}").toString();
        }

        GpuTexture texture = textureView.texture();
        if (texture == null) {
            return builder.append(",view={viewClass=").append(textureView.getClass().getSimpleName())
                .append(",baseMip=").append(textureView.baseMipLevel())
                .append(",mips=").append(textureView.mipLevels())
                .append(",texture=missing}}}").toString();
        }

        builder.append(",view={viewClass=").append(textureView.getClass().getSimpleName())
            .append(",viewId=").append(Integer.toHexString(System.identityHashCode(textureView)))
            .append(",baseMip=").append(textureView.baseMipLevel())
            .append(",mips=").append(textureView.mipLevels())
            .append(",width=").append(textureView.getWidth(0))
            .append(",height=").append(textureView.getHeight(0))
            .append(",closed=").append(textureView.isClosed())
            .append(",texture={class=").append(texture.getClass().getSimpleName())
            .append(",id=").append(Integer.toHexString(System.identityHashCode(texture)))
            .append(",label=\"").append(shaderInputParitySanitizeLabel(texture.getLabel())).append('"')
            .append(",format=").append(texture.getVulkanicFormat())
            .append(",width=").append(texture.getWidth(0))
            .append(",height=").append(texture.getHeight(0))
            .append(",layers=").append(texture.getDepthOrLayers())
            .append(",mips=").append(texture.getMipLevels())
            .append(",usage=").append(texture.usage())
            .append(",closed=").append(texture.isClosed())
            .append(",samplerState={").append(shaderInputParitySamplerState(texture)).append('}')
            .append("}}}}");
        return builder.toString();
    }

    public static String shaderInputParitySamplerResource(String name, int unit, @Nullable VulkanicTextureView textureView) {
        PipelineDescriptor.ResourceBinding binding = new PipelineDescriptor.ResourceBinding(
            0,
            Math.max(0, unit),
            shaderInputParitySanitizeLabel(name),
            PipelineDescriptor.ResourceType.SAMPLER,
            null
        );
        return describeShaderInputParitySampler(
            binding,
            new PipelineResourceBindings.SamplerBinding(Math.max(0, unit), null, textureView)
        );
    }

    public static void traceShaderInputParityDraw(
            String source,
            boolean indexed,
            int mode,
            int firstVertex,
            int vertexCount,
            long firstIndexOrByteOffset,
            int indexCount,
            int type,
            int instanceCount,
            int baseVertex) {
        if (!shouldTraceShaderInputParityLog()) {
            return;
        }

        String primitive = VulkanicPrimitiveMode.fromLegacyGlConstant(mode)
            .map(Enum::name)
            .orElse("legacy:0x" + Integer.toHexString(mode));
        String indexType = indexed
            ? VulkanicIndexType.fromLegacyGlConstant(type)
                .map(Enum::name)
                .orElse("legacy:0x" + Integer.toHexString(type))
            : "none";
        long firstIndex = firstIndexOrByteOffset;
        if (indexed) {
            java.util.Optional<VulkanicIndexType> typedIndex = VulkanicIndexType.fromLegacyGlConstant(type);
            if (typedIndex.isPresent() && typedIndex.get().bytesPerIndex() > 0) {
                firstIndex = firstIndexOrByteOffset / typedIndex.get().bytesPerIndex();
            }
        }

        LOGGER.info(
            "ShaderInputParityDraw backend={} source={} renderPhase={} indexed={} primitive={} mode={} firstVertex={} vertexCount={} firstIndex={} indexByteOffset={} indexCount={} indexType={} instanceCount={} baseVertex={} {} {}",
            getActiveBackendType().name().toLowerCase(java.util.Locale.ROOT),
            source,
            shaderInputParityRenderPhase(),
            indexed,
            primitive,
            mode,
            firstVertex,
            vertexCount,
            firstIndex,
            firstIndexOrByteOffset,
            indexCount,
            indexType,
            instanceCount,
            baseVertex,
            shaderInputParitySemanticDrawContextFields(),
            shaderInputParityDeterministicContextFields()
        );
    }

    public static void traceShaderInputParityGeometry(
            String source,
            @Nullable GpuBuffer vertexBuffer,
            @Nullable GpuBuffer indexBuffer,
            VertexFormat vertexFormat,
            VertexFormat.Mode mode,
            boolean indexed,
            int firstVertex,
            int vertexCount,
            int firstIndex,
            int indexCount,
            @Nullable VertexFormat.IndexType indexType,
            int instanceCount,
            int baseVertex) {
        if (!shouldTraceShaderInputParityLog()) {
            return;
        }

        GeometryParityResult result = buildShaderInputParityGeometry(
            vertexBuffer,
            indexBuffer,
            vertexFormat,
            indexed,
            firstVertex,
            vertexCount,
            firstIndex,
            indexCount,
            indexType,
            instanceCount,
            baseVertex
        );
        LOGGER.info(
            "ShaderInputParityGeometry backend={} source={} {} mode={} indexed={} vertexFormat={} vertexStride={} layoutHash={} totalVertices={} totalIndices={} totalPrimitives={} instances={} vertexHash={} indexHash={} status={} reason={} detail={} {}",
            getActiveBackendType().name().toLowerCase(Locale.ROOT),
            source,
            shaderInputParitySemanticDrawContextFields(),
            mode,
            indexed,
            shaderInputParitySanitizeLabel(vertexFormat.toString()),
            vertexFormat.getVertexSize(),
            shaderInputParityVertexFormatHash(vertexFormat),
            result.totalVertices(),
            result.totalIndices(),
            shaderInputParityPrimitiveCount(mode, indexed ? indexCount : vertexCount, instanceCount),
            instanceCount,
            result.vertexHash(),
            result.indexHash(),
            result.status(),
            result.reason(),
            result.detail(),
            shaderInputParityDeterministicContextFields()
        );
    }

    public static void traceShaderInputParityOpenGLLegacyGeometry(
            String source,
            int vertexBufferHandle,
            int indexBufferHandle,
            VertexFormat vertexFormat,
            VertexFormat.Mode mode,
            boolean indexed,
            int firstVertex,
            int vertexCount,
            int firstIndex,
            int indexCount,
            @Nullable VertexFormat.IndexType indexType,
            int instanceCount,
            int baseVertex) {
        if (!shouldTraceShaderInputParityLog()) {
            return;
        }

        GpuBuffer vertexBuffer = shaderInputParityOpenGLLegacyBuffer(
            vertexBufferHandle,
            GpuBuffer.USAGE_VERTEX
        );
        GpuBuffer indexBuffer = indexed
            ? shaderInputParityOpenGLLegacyBuffer(indexBufferHandle, GpuBuffer.USAGE_INDEX)
            : null;
        GeometryParityResult result = buildShaderInputParityGeometry(
            vertexBuffer,
            indexBuffer,
            vertexFormat,
            indexed,
            firstVertex,
            vertexCount,
            firstIndex,
            indexCount,
            indexType,
            instanceCount,
            baseVertex
        );
        LOGGER.info(
            "ShaderInputParityGeometry backend={} source={} {} mode={} indexed={} vertexFormat={} vertexStride={} layoutHash={} totalVertices={} totalIndices={} totalPrimitives={} instances={} vertexHash={} indexHash={} status={} reason={} detail={} {}",
            getActiveBackendType().name().toLowerCase(Locale.ROOT),
            source,
            shaderInputParitySemanticDrawContextFields(),
            mode,
            indexed,
            shaderInputParitySanitizeLabel(vertexFormat.toString()),
            vertexFormat.getVertexSize(),
            shaderInputParityVertexFormatHash(vertexFormat),
            result.totalVertices(),
            result.totalIndices(),
            shaderInputParityPrimitiveCount(mode, indexed ? indexCount : vertexCount, instanceCount),
            instanceCount,
            result.vertexHash(),
            result.indexHash(),
            result.status(),
            result.reason(),
            result.detail(),
            shaderInputParityDeterministicContextFields()
        );
    }

    private static @Nullable GpuBuffer shaderInputParityOpenGLLegacyBuffer(int handle, int usage) {
        if (handle <= 0) {
            return null;
        }

        int size;
        try {
            size = org.lwjgl.opengl.GL45.glGetNamedBufferParameteri(handle, org.lwjgl.opengl.GL15.GL_BUFFER_SIZE);
        } catch (RuntimeException ex) {
            return null;
        }
        if (size < 0) {
            return null;
        }

        return new ShaderInputParityOpenGLLegacyGpuBuffer(handle, usage, size);
    }

    private static void recordScopedCompositeColortex0Binding(
        String source,
        PipelineHandle pipeline,
        PipelineDescriptor descriptor,
        PipelineDescriptor.ResourceBinding resourceBinding,
        PipelineResourceBindings.SamplerBinding samplerBinding
    ) {
        if (!TRACE_RENDER_TARGET_CONTENT_HASHES) {
            return;
        }
        if (!DeterministicCameraCapture.isEnabledForDiagnostics()) {
            return;
        }
        if (!"iris:composite".contentEquals(String.valueOf(descriptor.getPortableState().location()))) {
            return;
        }
        if (!isLogicalColortex0(resourceBinding.name(), samplerBinding.textureView())) {
            return;
        }
        VulkanicTextureView textureView = samplerBinding.textureView();
        if (textureView == null) {
            return;
        }
        java.util.List<String> stages = resourceBinding.stages().stream()
            .map(Enum::name)
            .sorted()
            .toList();
        scopedCompositeColortex0Binding = new ScopedCompositeColortex0Binding(
            pipeline,
            String.valueOf(descriptor.getPortableState().location()),
            String.valueOf(descriptor.getPortableState().vertexShader()),
            String.valueOf(descriptor.getPortableState().fragmentShader()),
            String.valueOf(descriptor.getPipelineCompilationKey()),
            String.valueOf(descriptor.getStableCacheKey()),
            resourceBinding.name(),
            resourceBinding.set(),
            resourceBinding.binding(),
            String.valueOf(resourceBinding.type()),
            stages,
            samplerBinding.textureUnit(),
            samplerBinding.samplerObject(),
            textureView.texture(),
            textureView.getBaseMipLevel(),
            textureView.getMipLevelCount(),
            legacyTextureIdFromLabel(textureView.texture().getLabel()),
            source
        );
    }

    public static void recordScopedCompositeColortex0RenderPassBinding(
        @Nullable RenderPipeline renderPipeline,
        String resourceName,
        @Nullable GpuTextureView textureView,
        int textureUnit,
        String source
    ) {
        if (!shouldRecordScopedCompositeColortex0RenderPassBinding(renderPipeline, resourceName)) {
            return;
        }
        if (textureView == null || !(textureView.texture() instanceof VulkanicTexture texture)) {
            return;
        }
        recordScopedCompositeColortex0RenderPassBinding(
            renderPipeline,
            resourceName,
            textureUnit,
            texture,
            textureView.baseMipLevel(),
            textureView.mipLevels(),
            0,
            source
        );
    }

    public static void recordScopedCompositeColortex0RenderPassLegacyBinding(
        @Nullable RenderPipeline renderPipeline,
        String resourceName,
        int textureId,
        int textureUnit,
        String source
    ) {
        if (!shouldRecordScopedCompositeColortex0RenderPassBinding(renderPipeline, resourceName) || textureId <= 0) {
            return;
        }
        recordScopedCompositeColortex0RenderPassBinding(
            renderPipeline,
            resourceName,
            textureUnit,
            null,
            0,
            1,
            textureId,
            source
        );
    }

    public static void traceScopedCompositeColortex0SamplerBinding(
        RenderPass renderPass,
        String resourceName,
        int textureUnit,
        int textureId,
        String source
    ) {
        if (!TRACE_RENDER_TARGET_CONTENT_HASHES || !DeterministicCameraCapture.isEnabledForDiagnostics()) {
            return;
        }
        if (!isScopedCompositeColortex0ResourceName(resourceName)) {
            return;
        }
        String customPassName;
        try {
            customPassName = diagnosticCustomPassName(renderPass.iris$getCustomPass());
        } catch (RuntimeException exception) {
            customPassName = "unavailable:" + exception.getClass().getSimpleName();
        }
        if (!shouldTraceScopedCompositeColortex0Pass(customPassName)) {
            return;
        }

        VulkanicTextureView textureView = textureId > 0 ? createManagedLegacyTextureView(textureId) : null;
        DiagnosticTextureContentHash contentHash;
        String physicalKey = physicalResourceKey(textureId, textureView == null ? null : textureView.texture());
        String readbackKey = getActiveBackendType().name().toLowerCase(java.util.Locale.ROOT)
            + "|scoped-colortex0-sampler|"
            + DeterministicCameraCapture.currentPoseNameForDiagnostics()
            + '|' + customPassName
            + '|' + physicalKey
            + "|unit:" + textureUnit;
        if (!TRACE_RENDER_TARGET_SAMPLER_BINDING_HASHES) {
            contentHash = DiagnosticTextureContentHash.unavailable(
                "colortex0",
                textureView == null ? null : textureView.texture(),
                textureView,
                "sampler-binding-hash-disabled"
            );
        } else if (!"composite".equals(customPassName)) {
            contentHash = DiagnosticTextureContentHash.unavailable(
                "colortex0",
                textureView == null ? null : textureView.texture(),
                textureView,
                "sampler-binding-hash-out-of-scope"
            );
        } else if (!isDeterministicCaptureEligiblePose()) {
            contentHash = DiagnosticTextureContentHash.unavailable(
                "colortex0",
                textureView == null ? null : textureView.texture(),
                textureView,
                "sampler-binding-hash-pose-out-of-scope"
            );
        } else if (getActiveBackendType() == GraphicsBackendType.VULKAN) {
            contentHash = DiagnosticTextureContentHash.unavailable(
                "colortex0",
                textureView == null ? null : textureView.texture(),
                textureView,
                "sampler-binding-deferred-until-renderpass-close"
            );
        } else if (!reserveDiagnosticContentReadback("sampler-binding", readbackKey)) {
            contentHash = DiagnosticTextureContentHash.unavailable(
                "colortex0",
                textureView == null ? null : textureView.texture(),
                textureView,
                diagnosticContentReadbackUnavailableReason(null, "sampler-binding", readbackKey)
            );
        } else {
            try {
                contentHash = diagnosticTextureContentHash(textureView, "colortex0");
            } catch (RuntimeException exception) {
                contentHash = DiagnosticTextureContentHash.unavailable(
                    "colortex0",
                    textureView == null ? null : textureView.texture(),
                    textureView,
                    "exception-" + exception.getClass().getSimpleName() + '-' + exception.getMessage()
                );
            }
        }
        String lifecycleInfo = textureView == null
            ? "textureView=missing"
            : diagnosticTextureLifecycleInfo(textureView, "colortex0");
        if (textureView != null) {
            textureView.close();
        }

        LOGGER.info(
            "ShaderInputParityScopedColortex0SamplerBinding backend={} source={} customPass={} resourceName={} textureUnit={} textureId={} physicalKey={} lifecycle=\"{}\" {} contentHash={{{}}}",
            getActiveBackendType().name().toLowerCase(java.util.Locale.ROOT),
            source,
            customPassName,
            shaderInputParitySanitizeLabel(resourceName),
            textureUnit,
            textureId,
            physicalKey,
            shaderInputParitySanitizeLabel(lifecycleInfo),
            shaderInputParityDeterministicContextFields(),
            diagnosticContentHashFields(contentHash)
        );
    }

    private static boolean shouldRecordScopedCompositeColortex0RenderPassBinding(
        @Nullable RenderPipeline renderPipeline,
        String resourceName
    ) {
        if (!TRACE_RENDER_TARGET_CONTENT_HASHES) {
            return false;
        }
        if (!DeterministicCameraCapture.isEnabledForDiagnostics()) {
            return false;
        }
        if (renderPipeline == null || !"iris:composite".contentEquals(String.valueOf(renderPipeline.getLocation()))) {
            return false;
        }
        return isScopedCompositeColortex0ResourceName(resourceName);
    }

    private static void recordScopedCompositeColortex0RenderPassBinding(
        RenderPipeline renderPipeline,
        String resourceName,
        int textureUnit,
        @Nullable VulkanicTexture texture,
        int baseMipLevel,
        int mipLevelCount,
        int legacyTextureId,
        String source
    ) {
        PipelineDescriptor descriptor = PipelineDescriptor.fromRenderPipeline(renderPipeline);
        scopedCompositeColortex0Binding = new ScopedCompositeColortex0Binding(
            null,
            String.valueOf(renderPipeline.getLocation()),
            String.valueOf(renderPipeline.getVertexShader()),
            String.valueOf(renderPipeline.getFragmentShader()),
            String.valueOf(descriptor.getPipelineCompilationKey()),
            String.valueOf(descriptor.getStableCacheKey()),
            resourceName,
            0,
            Math.max(0, textureUnit),
            "SAMPLER",
            java.util.List.of("FRAGMENT"),
            textureUnit,
            null,
            texture,
            baseMipLevel,
            mipLevelCount,
            legacyTextureId > 0 ? legacyTextureId : legacyTextureIdFromLabel(texture == null ? null : texture.getLabel()),
            source
        );
    }

    public static void recordDiagnosticIrisColorAttachment(
        int framebuffer,
        int colorAttachment,
        int logicalIndex,
        int textureId,
        boolean writesMain,
        String source
    ) {
        if (!TRACE_RENDER_TARGET_CONTENT_HASHES || textureId <= 0) {
            return;
        }
        DiagnosticIrisColorAttachment attachment = new DiagnosticIrisColorAttachment(
            framebuffer,
            colorAttachment,
            logicalIndex,
            textureId,
            "colortex" + logicalIndex,
            writesMain ? "main" : "alt",
            source
        );
        DIAGNOSTIC_IRIS_TEXTURE_ATTACHMENTS.put(textureId, attachment);
        DIAGNOSTIC_IRIS_FRAMEBUFFER_ATTACHMENTS.compute(framebuffer, (ignored, existing) -> {
            java.util.ArrayList<DiagnosticIrisColorAttachment> updated = existing == null
                ? new java.util.ArrayList<>()
                : new java.util.ArrayList<>(existing);
            updated.removeIf(previous -> previous.colorAttachment() == colorAttachment);
            updated.add(attachment);
            updated.sort(java.util.Comparator.comparingInt(DiagnosticIrisColorAttachment::colorAttachment));
            return java.util.List.copyOf(updated);
        });
    }

    public static void traceIrisColortex0PhaseHash(String phase, String pingPong, int textureId) {
        if (!TRACE_RENDER_TARGET_CONTENT_HASHES || !TRACE_IRIS_COLORTEX0_PHASE_HASHES) {
            return;
        }
        if (!DeterministicCameraCapture.isEnabledForDiagnostics() || !isDeterministicCaptureEligiblePose()) {
            return;
        }
        if (textureId <= 0) {
            return;
        }

        String backendName = getActiveBackendType().name().toLowerCase(java.util.Locale.ROOT);
        String poseName = DeterministicCameraCapture.currentPoseNameForDiagnostics();
        String phaseKey = backendName + '|' + poseName + '|' + phase + '|' + pingPong + "|tex:" + textureId;
        if (!IRIS_COLORTEX0_PHASE_HASH_KEYS.add(phaseKey)) {
            return;
        }

        VulkanicTextureView textureView = createManagedLegacyTextureView(textureId);
        DiagnosticTextureContentHash contentHash;
        String physicalKey = physicalResourceKey(textureId, textureView == null ? null : textureView.texture());
        String readbackKey = backendName
            + "|iris-phase|"
            + poseName
            + '|' + phase
            + '|' + pingPong
            + '|' + physicalKey;
        if (!reserveDiagnosticContentReadback("iris-phase", readbackKey)) {
            contentHash = DiagnosticTextureContentHash.unavailable(
                "colortex0",
                textureView == null ? null : textureView.texture(),
                textureView,
                diagnosticContentReadbackUnavailableReason(null, "iris-phase", readbackKey)
            );
        } else {
            try {
                contentHash = diagnosticTextureContentHash(textureView, "colortex0");
            } catch (RuntimeException exception) {
                contentHash = DiagnosticTextureContentHash.unavailable(
                    "colortex0",
                    textureView == null ? null : textureView.texture(),
                    textureView,
                    "exception-" + exception.getClass().getSimpleName() + '-' + exception.getMessage()
                );
            }
        }
        String lifecycleInfo = textureView == null
            ? "textureView=missing"
            : diagnosticTextureLifecycleInfo(textureView, "colortex0");
        if (textureView != null) {
            textureView.close();
        }

        LOGGER.info(
            "ShaderInputParityIrisColortex0PhaseHash backend={} phase={} logicalResource=colortex0 pingPong={} textureId={} physicalKey={} lifecycle=\"{}\" {} contentHash={{{}}}",
            backendName,
            shaderInputParitySanitizeLabel(phase),
            shaderInputParitySanitizeLabel(pingPong),
            textureId,
            physicalKey,
            shaderInputParitySanitizeLabel(lifecycleInfo),
            shaderInputParityDeterministicContextFields(),
            diagnosticContentHashFields(contentHash)
        );
    }

    public static void recordScopedCompositeColortex0ProducerCompletion(
        @Nullable VulkanicRenderTargetDescriptor descriptor,
        int framebuffer,
        @Nullable RenderPipeline renderPipeline,
        @Nullable Object customPass,
        String source
    ) {
        if (!TRACE_RENDER_TARGET_CONTENT_HASHES || !DeterministicCameraCapture.isEnabledForDiagnostics()) {
            return;
        }

        java.util.List<DiagnosticProducerAttachment> attachments = diagnosticProducerAttachments(descriptor, framebuffer);
        if (attachments.isEmpty()) {
            return;
        }

        String backendName = getActiveBackendType().name().toLowerCase(java.util.Locale.ROOT);
        String passLabel = descriptor == null ? "framebuffer:" + framebuffer : safeSupplierLabel(descriptor.label());
        String customPassName = diagnosticCustomPassName(customPass);
        String pipelineLocation = renderPipeline == null ? "unknown" : String.valueOf(renderPipeline.getLocation());
        String descriptorSignature = descriptor == null ? "framebuffer:" + framebuffer : descriptor.debugSignature();
        for (DiagnosticProducerAttachment attachment : attachments) {
            if (!"colortex0".equals(attachment.logicalName())) {
                continue;
            }

            VulkanicTextureView textureView = createManagedLegacyTextureView(attachment.textureId());
            DiagnosticTextureContentHash contentHash;
            String physicalKey = physicalResourceKey(attachment.textureId(), textureView == null ? null : textureView.texture());
            String readbackKey = backendName
                + "|producer|"
                + DeterministicCameraCapture.currentPoseNameForDiagnostics()
                + '|' + customPassName
                + '|' + pipelineLocation
                + '|' + attachment.logicalName()
                + '|' + attachment.pingPong()
                + '|' + physicalKey;
            if (!TRACE_RENDER_TARGET_PRODUCER_HASHES) {
                contentHash = DiagnosticTextureContentHash.unavailable(
                    "colortex0",
                    textureView == null ? null : textureView.texture(),
                    textureView,
                    "producer-hash-disabled"
                );
            } else if (!shouldHashScopedCompositeColortex0Producer(customPassName, attachment)) {
                contentHash = DiagnosticTextureContentHash.unavailable(
                    "colortex0",
                    textureView == null ? null : textureView.texture(),
                    textureView,
                    "producer-hash-out-of-scope"
                );
            } else if (!reserveDiagnosticContentReadback("producer", readbackKey)) {
                contentHash = DiagnosticTextureContentHash.unavailable(
                    "colortex0",
                    textureView == null ? null : textureView.texture(),
                    textureView,
                    diagnosticContentReadbackUnavailableReason(null, "producer", readbackKey)
                );
            } else {
                try {
                    contentHash = diagnosticTextureContentHash(textureView, "colortex0");
                } catch (RuntimeException exception) {
                    contentHash = DiagnosticTextureContentHash.unavailable(
                        "colortex0",
                        textureView == null ? null : textureView.texture(),
                        textureView,
                        "exception-" + exception.getClass().getSimpleName() + '-' + exception.getMessage()
                    );
                }
            }
            String lifecycleInfo = textureView == null
                ? "textureView=missing"
                : diagnosticTextureLifecycleInfo(textureView, "colortex0");
            if (textureView != null) {
                textureView.close();
            }

            ScopedCompositeColortex0Producer producer = new ScopedCompositeColortex0Producer(
                backendName,
                source,
                passLabel,
                customPassName,
                pipelineLocation,
                physicalKey,
                attachment.textureId(),
                attachment.logicalName(),
                attachment.colorAttachment(),
                attachment.pingPong(),
                descriptorSignature,
                attachment.usage(),
                lifecycleInfo,
                DeterministicCameraCapture.currentPoseNameForDiagnostics(),
                shaderInputParityDeterministicContextFields(),
                contentHash
            );
            SCOPED_COMPOSITE_COLORTEX0_PRODUCERS.put(backendName + '|' + physicalKey, producer);
            LOGGER.info(
                "ShaderInputParityScopedColortex0Lifecycle backend={} event=producer-complete source={} logicalAttachment={} colorAttachment={} pingPong={} textureId={} physicalKey={} passLabel={} customPass={} pipelineLocation={} descriptorSignature=\"{}\" attachmentUsage=\"{}\" lifecycle=\"{}\" {} contentHash={{{}}}",
                backendName,
                source,
                producer.logicalAttachment(),
                producer.colorAttachment(),
                producer.pingPong(),
                producer.textureId(),
                producer.physicalKey(),
                shaderInputParitySanitizeLabel(producer.passLabel()),
                shaderInputParitySanitizeLabel(producer.customPassName()),
                shaderInputParitySanitizeLabel(producer.pipelineLocation()),
                shaderInputParitySanitizeLabel(producer.descriptorSignature()),
                shaderInputParitySanitizeLabel(producer.attachmentUsage()),
                shaderInputParitySanitizeLabel(producer.lifecycleInfo()),
                producer.deterministicFields(),
                diagnosticContentHashFields(producer.hash())
            );
        }
    }

    private static boolean shouldHashScopedCompositeColortex0Producer(
        String customPassName,
        DiagnosticProducerAttachment attachment
    ) {
        if (!isDeterministicCaptureEligiblePose()) {
            return false;
        }
        if ("composite".equals(customPassName)) {
            return "main".equals(attachment.pingPong());
        }
        if (TRACE_RENDER_TARGET_CONTENT_HASHES_INITIAL_POSE_ONLY) {
            return false;
        }
        if ("composite3".equals(customPassName)) {
            return "alt".equals(attachment.pingPong());
        }
        return false;
    }

    private static boolean isDeterministicCaptureEligiblePose() {
        if (TRACE_RENDER_TARGET_CONTENT_HASHES_INITIAL_POSE_ONLY) {
            return "initial".equals(DeterministicCameraCapture.currentPoseNameForDiagnostics());
        }
        String poseName = DeterministicCameraCapture.currentPoseNameForDiagnostics();
        return "initial".equals(poseName)
            || "right".equals(poseName)
            || "left".equals(poseName)
            || "return".equals(poseName);
    }

    private static boolean shouldTraceScopedCompositeColortex0Pass(String customPassName) {
        return "composite".equals(customPassName) || "composite3".equals(customPassName);
    }

    private static java.util.List<DiagnosticProducerAttachment> diagnosticProducerAttachments(
        @Nullable VulkanicRenderTargetDescriptor descriptor,
        int framebuffer
    ) {
        java.util.ArrayList<DiagnosticProducerAttachment> attachments = new java.util.ArrayList<>();
        if (descriptor != null) {
            for (int colorIndex = 0; colorIndex < descriptor.colorAttachments().size(); colorIndex++) {
                VulkanicRenderTargetDescriptor.ColorAttachment colorAttachment = descriptor.colorAttachments().get(colorIndex);
                DiagnosticIrisColorAttachment irisAttachment = DIAGNOSTIC_IRIS_TEXTURE_ATTACHMENTS.get(colorAttachment.textureId());
                String logicalName = irisAttachment == null ? "unknown" : irisAttachment.logicalName();
                String pingPong = irisAttachment == null ? "unknown" : irisAttachment.pingPong();
                String usage = "initial=" + colorAttachment.initialUsage()
                    + ",pass=" + colorAttachment.passUsage()
                    + ",final=" + colorAttachment.finalUsage()
                    + ",load=" + colorAttachment.loadOp()
                    + ",store=" + colorAttachment.storeOp();
                attachments.add(new DiagnosticProducerAttachment(
                    colorIndex,
                    colorAttachment.textureId(),
                    logicalName,
                    pingPong,
                    usage
                ));
            }
            return attachments;
        }

        java.util.List<DiagnosticIrisColorAttachment> framebufferAttachments =
            DIAGNOSTIC_IRIS_FRAMEBUFFER_ATTACHMENTS.getOrDefault(framebuffer, java.util.List.of());
        for (DiagnosticIrisColorAttachment attachment : framebufferAttachments) {
            attachments.add(new DiagnosticProducerAttachment(
                attachment.colorAttachment(),
                attachment.textureId(),
                attachment.logicalName(),
                attachment.pingPong(),
                "irisFramebufferAttachment=true,source=" + attachment.source()
            ));
        }
        return attachments;
    }

    public static void traceScopedCompositeColortex0ProducerDraw(
        @Nullable VulkanicRenderTargetDescriptor descriptor,
        int framebuffer,
        @Nullable RenderPipeline renderPipeline,
        @Nullable Object customPass,
        @Nullable PipelineHandle pipelineHandle,
        @Nullable PipelineDescriptor pipelineDescriptor,
        @Nullable PipelineResourceBindings bindings,
        String source,
        boolean indexed,
        int firstVertex,
        int baseVertex,
        int firstIndex,
        int indexCount,
        int vertexCount,
        int instanceCount,
        @Nullable VertexFormat.IndexType indexType,
        boolean scissorEnabled,
        int scissorX,
        int scissorY,
        int scissorWidth,
        int scissorHeight
    ) {
        if (!shouldTraceScopedCompositeColortex0ProducerDraw(descriptor, framebuffer, renderPipeline, customPass)) {
            return;
        }
        String customPassName = diagnosticCustomPassName(customPass);

        DiagnosticProducerAttachment outputAttachment = null;
        for (DiagnosticProducerAttachment attachment : diagnosticProducerAttachments(descriptor, framebuffer)) {
            if (isScopedCompositeColortex0ProducerOutput(customPassName, attachment)) {
                outputAttachment = attachment;
                break;
            }
        }
        if (outputAttachment == null) {
            return;
        }

        PipelineDescriptor resolvedDescriptor = pipelineDescriptor != null
            ? pipelineDescriptor
            : PipelineDescriptor.fromRenderPipeline(renderPipeline);
        DiagnosticViewportState viewport = diagnosticLastViewport;
        PipelineDescriptor.PortableState state = resolvedDescriptor.getPortableState();
        String backendName = getActiveBackendType().name().toLowerCase(java.util.Locale.ROOT);
        String renderTarget = descriptor == null ? "framebuffer:" + framebuffer : descriptor.debugSignature();
        String attachmentUsage = outputAttachment.usage();
        String resources = bindings == null
            ? "unavailable:no-bindings"
            : diagnosticResourceSummary(resolvedDescriptor, bindings);
        if ("unavailable:no-bindings".equals(resources) && "composite".equals(customPassName)) {
            resources = diagnosticScopedCompositeColortex0BindingResourceSummary();
        }
        maybeRecordPendingScopedCompositeColortex0SamplerReadback(
            resolvedDescriptor,
            bindings,
            customPassName,
            state,
            outputAttachment,
            renderTarget,
            attachmentUsage,
            diagnosticDrawCall(
                indexed,
                firstVertex,
                baseVertex,
                firstIndex,
                indexCount,
                vertexCount,
                instanceCount,
                indexType
            ),
            diagnosticVertexInput(state),
            diagnosticPipelineState(state),
            viewport == null ? "unknown" : viewport.describe(),
            diagnosticScissor(scissorEnabled, scissorX, scissorY, scissorWidth, scissorHeight)
        );

        LOGGER.info(
            "ShaderInputParityScopedColortex0ProducerDraw backend={} source={} customPass={} renderPhase={} drawOrdinal={} pipelineLocation={} vertexShader={} fragmentShader={} pipelineKey={} stableKey={} pipelineHandle={} programDescriptorResources={} boundResources={} completeResourceCoverage={} outputLogical={} outputPingPong={} colorAttachment={} outputTextureId={} renderTarget=\"{}\" attachmentUsage=\"{}\" viewport={} scissor={} draw={} vertexInput={} pipelineState={} {} resources=[{}]",
            backendName,
            source,
            customPassName,
            shaderInputParityRenderPhase(),
            1,
            state.location(),
            state.vertexShader(),
            state.fragmentShader(),
            resolvedDescriptor.getPipelineCompilationKey(),
            resolvedDescriptor.getStableCacheKey(),
            pipelineHandle == null ? "none" : pipelineHandle.getClass().getSimpleName(),
            resolvedDescriptor.getResourceLayout().bindings().size(),
            bindings == null ? 0 : diagnosticBoundResourceCount(resolvedDescriptor, bindings),
            bindings != null && diagnosticBoundResourceCount(resolvedDescriptor, bindings) == resolvedDescriptor.getResourceLayout().bindings().size(),
            outputAttachment.logicalName(),
            outputAttachment.pingPong(),
            outputAttachment.colorAttachment(),
            outputAttachment.textureId(),
            shaderInputParitySanitizeLabel(renderTarget),
            shaderInputParitySanitizeLabel(attachmentUsage),
            viewport == null ? "unknown" : viewport.describe(),
            diagnosticScissor(scissorEnabled, scissorX, scissorY, scissorWidth, scissorHeight),
            diagnosticDrawCall(indexed, firstVertex, baseVertex, firstIndex, indexCount, vertexCount, instanceCount, indexType),
            diagnosticVertexInput(state),
            diagnosticPipelineState(state),
            shaderInputParityDeterministicContextFields(),
            resources
        );
    }

    private static String diagnosticScopedCompositeColortex0BindingResourceSummary() {
        ScopedCompositeColortex0Binding binding = scopedCompositeColortex0Binding;
        if (binding == null) {
            return "unavailable:no-scoped-colortex0-binding";
        }
        if (!"iris:composite".equals(binding.pipelineLocation())) {
            return "unavailable:scoped-binding-pipeline-" + shaderInputParitySanitizeLabel(binding.pipelineLocation());
        }
        VulkanicTextureView textureView = createScopedCompositeColortex0DiagnosticView(binding);
        try {
            DiagnosticTextureContentHash contentHash = DiagnosticTextureContentHash.unavailable(
                "colortex0",
                textureView == null ? binding.texture() : textureView.texture(),
                textureView,
                "draw-resource-list-no-readback"
            );
            return scopedCompositeColortex0ResourceString(binding, textureView, contentHash);
        } finally {
            if (textureView != null) {
                textureView.close();
            }
        }
    }

    private static void maybeRecordPendingScopedCompositeColortex0SamplerReadback(
        PipelineDescriptor descriptor,
        @Nullable PipelineResourceBindings bindings,
        String customPassName,
        PipelineDescriptor.PortableState state,
        DiagnosticProducerAttachment outputAttachment,
        String renderTarget,
        String attachmentUsage,
        String draw,
        String vertexInput,
        String pipelineState,
        String viewport,
        String scissor
    ) {
        if (getActiveBackendType() != GraphicsBackendType.VULKAN) {
            return;
        }
        if (!TRACE_RENDER_TARGET_CONTENT_HASHES || !TRACE_RENDER_TARGET_SAMPLER_BINDING_HASHES) {
            return;
        }
        if (!"composite".equals(customPassName) || !"main".equals(outputAttachment.pingPong())) {
            return;
        }
        if (!isDeterministicCaptureEligiblePose() || bindings == null) {
            return;
        }

        PipelineDescriptor.ResourceBinding resourceBinding = null;
        PipelineResourceBindings.SamplerBinding samplerBinding = null;
        for (PipelineDescriptor.ResourceBinding binding : descriptor.getResourceLayout().bindings()) {
            if (!isScopedCompositeColortex0ResourceName(binding.name())) {
                continue;
            }
            PipelineResourceBindings.SamplerBinding candidate = bindings.getSamplerBindingOrNull(binding.name());
            if (candidate != null && candidate.textureView() != null) {
                resourceBinding = binding;
                samplerBinding = candidate;
                break;
            }
        }
        if (resourceBinding == null || samplerBinding == null || samplerBinding.textureView() == null) {
            return;
        }

        VulkanicTextureView textureView = samplerBinding.textureView();
        int legacyTextureId = legacyTextureIdFromLabel(textureView.texture().getLabel());
        String physicalKey = physicalResourceKey(legacyTextureId, textureView.texture());
        String pendingKey = getActiveBackendType().name().toLowerCase(java.util.Locale.ROOT)
            + "|deferred-composite-sampler|"
            + DeterministicCameraCapture.currentPoseNameForDiagnostics()
            + '|' + state.location()
            + '|' + descriptor.getStableCacheKey()
            + '|' + resourceBinding.name()
            + '|' + physicalKey
            + "|unit:" + samplerBinding.textureUnit();
        PENDING_SCOPED_COMPOSITE_COLORTEX0_SAMPLER_READBACKS.putIfAbsent(
            pendingKey,
            new PendingScopedCompositeColortex0SamplerReadback(
                getActiveBackendType().name().toLowerCase(java.util.Locale.ROOT),
                customPassName,
                String.valueOf(state.location()),
                String.valueOf(state.vertexShader()),
                String.valueOf(state.fragmentShader()),
                descriptor.getPipelineCompilationKey(),
                descriptor.getStableCacheKey(),
                resourceBinding.name(),
                samplerBinding.textureUnit(),
                samplerBinding.samplerObject(),
                textureView,
                legacyTextureId,
                physicalKey,
                outputAttachment.logicalName(),
                outputAttachment.pingPong(),
                outputAttachment.colorAttachment(),
                outputAttachment.textureId(),
                renderTarget,
                attachmentUsage,
                draw,
                vertexInput,
                pipelineState,
                viewport,
                scissor,
                DeterministicCameraCapture.currentPoseNameForDiagnostics(),
                shaderInputParityDeterministicContextFields()
            )
        );
    }

    public static void traceDeferredScopedCompositeColortex0SamplerReadbacks(String source) {
        if (!TRACE_RENDER_TARGET_CONTENT_HASHES || !TRACE_RENDER_TARGET_SAMPLER_BINDING_HASHES) {
            return;
        }
        if (PENDING_SCOPED_COMPOSITE_COLORTEX0_SAMPLER_READBACKS.isEmpty()) {
            return;
        }
        java.util.List<java.util.Map.Entry<String, PendingScopedCompositeColortex0SamplerReadback>> pending =
            new java.util.ArrayList<>(PENDING_SCOPED_COMPOSITE_COLORTEX0_SAMPLER_READBACKS.entrySet());
        pending.sort(java.util.Map.Entry.comparingByKey());
        for (java.util.Map.Entry<String, PendingScopedCompositeColortex0SamplerReadback> entry : pending) {
            PendingScopedCompositeColortex0SamplerReadback snapshot = entry.getValue();
            if (!PENDING_SCOPED_COMPOSITE_COLORTEX0_SAMPLER_READBACKS.remove(entry.getKey(), snapshot)) {
                continue;
            }

            DiagnosticTextureContentHash contentHash;
            if (!reserveDiagnosticContentReadback("deferred-sampler", entry.getKey())) {
                contentHash = DiagnosticTextureContentHash.unavailable(
                    "colortex0",
                    snapshot.textureView().texture(),
                    snapshot.textureView(),
                    diagnosticContentReadbackUnavailableReason(null, "deferred-sampler", entry.getKey())
                );
            } else {
                try {
                    contentHash = diagnosticTextureContentHash(snapshot.textureView(), "colortex0");
                } catch (RuntimeException exception) {
                    contentHash = DiagnosticTextureContentHash.unavailable(
                        "colortex0",
                        snapshot.textureView().texture(),
                        snapshot.textureView(),
                        "exception-" + exception.getClass().getSimpleName() + '-' + exception.getMessage()
                    );
                }
            }
            String lifecycleInfo = diagnosticTextureLifecycleInfo(snapshot.textureView(), "colortex0");
            LOGGER.info(
                "ShaderInputParityScopedColortex0DeferredSamplerReadback backend={} source={} customPass={} pipelineLocation={} vertexShader={} fragmentShader={} pipelineKey={} stableKey={} resourceName={} textureUnit={} samplerObject={} legacyTextureId={} physicalKey={} outputLogical={} outputPingPong={} colorAttachment={} outputTextureId={} renderTarget=\"{}\" attachmentUsage=\"{}\" viewport={} scissor={} draw={} vertexInput={} pipelineState={} lifecycle=\"{}\" {} contentHash={{{}}}",
                snapshot.backend(),
                source,
                shaderInputParitySanitizeLabel(snapshot.customPassName()),
                shaderInputParitySanitizeLabel(snapshot.pipelineLocation()),
                shaderInputParitySanitizeLabel(snapshot.vertexShader()),
                shaderInputParitySanitizeLabel(snapshot.fragmentShader()),
                snapshot.pipelineKey(),
                snapshot.stableKey(),
                shaderInputParitySanitizeLabel(snapshot.resourceName()),
                snapshot.textureUnit(),
                snapshot.samplerObject() == null ? "none" : snapshot.samplerObject(),
                snapshot.legacyTextureId(),
                snapshot.physicalKey(),
                snapshot.outputLogical(),
                snapshot.outputPingPong(),
                snapshot.colorAttachment(),
                snapshot.outputTextureId(),
                shaderInputParitySanitizeLabel(snapshot.renderTarget()),
                shaderInputParitySanitizeLabel(snapshot.attachmentUsage()),
                snapshot.viewport(),
                snapshot.scissor(),
                snapshot.draw(),
                snapshot.vertexInput(),
                snapshot.pipelineState(),
                shaderInputParitySanitizeLabel(lifecycleInfo),
                snapshot.deterministicFields(),
                diagnosticContentHashFields(contentHash)
            );
        }
    }

    public static boolean shouldTraceScopedCompositeColortex0ProducerDraw(
        @Nullable VulkanicRenderTargetDescriptor descriptor,
        int framebuffer,
        @Nullable RenderPipeline renderPipeline,
        @Nullable Object customPass
    ) {
        if (!TRACE_RENDER_TARGET_CONTENT_HASHES || !DeterministicCameraCapture.isEnabledForDiagnostics()) {
            return false;
        }
        if (renderPipeline == null || !"iris:composite".contentEquals(String.valueOf(renderPipeline.getLocation()))) {
            return false;
        }
        String customPassName = diagnosticCustomPassName(customPass);
        if (!shouldTraceScopedCompositeColortex0Pass(customPassName)) {
            return false;
        }
        for (DiagnosticProducerAttachment attachment : diagnosticProducerAttachments(descriptor, framebuffer)) {
            if (isScopedCompositeColortex0ProducerOutput(customPassName, attachment)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isScopedCompositeColortex0ProducerOutput(
        String customPassName,
        DiagnosticProducerAttachment attachment
    ) {
        if (!"colortex0".equals(attachment.logicalName())) {
            return false;
        }
        if ("composite".equals(customPassName)) {
            return "main".equals(attachment.pingPong());
        }
        if ("composite3".equals(customPassName)) {
            return "alt".equals(attachment.pingPong());
        }
        return false;
    }

    private static int diagnosticBoundResourceCount(PipelineDescriptor descriptor, PipelineResourceBindings bindings) {
        int count = 0;
        for (PipelineDescriptor.ResourceBinding resourceBinding : descriptor.getResourceLayout().bindings()) {
            switch (resourceBinding.type()) {
                case UNIFORM_BUFFER -> {
                    if (bindings.getUniformBufferBindingOrNull(resourceBinding.name()) != null) {
                        count++;
                    }
                }
                case SAMPLER, COMPARISON_SAMPLER -> {
                    if (bindings.getSamplerBindingOrNull(resourceBinding.name()) != null) {
                        count++;
                    }
                }
                case STORAGE_IMAGE -> {
                    if (bindings.getStorageImageBindingOrNull(resourceBinding.name()) != null) {
                        count++;
                    }
                }
                case TEXEL_BUFFER -> {
                    if (bindings.getTexelBufferBindingOrNull(resourceBinding.name()) != null) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static String diagnosticResourceSummary(PipelineDescriptor descriptor, PipelineResourceBindings bindings) {
        java.util.List<String> resources = new java.util.ArrayList<>();
        for (PipelineDescriptor.ResourceBinding resourceBinding : descriptor.getResourceLayout().bindings()) {
            switch (resourceBinding.type()) {
                case UNIFORM_BUFFER -> {
                    VulkanicBufferSlice slice = bindings.getUniformBufferBindingOrNull(resourceBinding.name());
                    if (slice != null) {
                        resources.add(describeShaderInputParityUniform(resourceBinding, slice));
                    } else {
                        resources.add(resourceBinding.name() + "{type=" + resourceBinding.type() + ",binding=missing}");
                    }
                }
                case SAMPLER, COMPARISON_SAMPLER -> {
                    PipelineResourceBindings.SamplerBinding samplerBinding = bindings.getSamplerBindingOrNull(resourceBinding.name());
                    if (samplerBinding != null) {
                        resources.add(describeShaderInputParitySampler(resourceBinding, samplerBinding));
                    } else {
                        resources.add(resourceBinding.name() + "{type=" + resourceBinding.type() + ",binding=missing}");
                    }
                }
                case STORAGE_IMAGE -> {
                    PipelineResourceBindings.StorageImageBinding storageImage = bindings.getStorageImageBindingOrNull(resourceBinding.name());
                    resources.add(resourceBinding.name() + "{type=" + resourceBinding.type()
                        + (storageImage == null
                            ? ",binding=missing}"
                            : ",imageUnit=" + storageImage.imageUnit()
                                + ",texture=" + storageImage.texture()
                                + ",level=" + storageImage.level()
                                + ",layered=" + storageImage.layered()
                                + ",layer=" + storageImage.layer()
                                + ",access=" + storageImage.access()
                                + ",format=" + storageImage.format()
                                + "}"));
                }
                case TEXEL_BUFFER -> {
                    PipelineResourceBindings.TexelBufferBinding texelBuffer = bindings.getTexelBufferBindingOrNull(resourceBinding.name());
                    resources.add(resourceBinding.name() + "{type=" + resourceBinding.type()
                        + (texelBuffer == null ? ",binding=missing}" : ",textureUnit=" + texelBuffer.textureUnit() + "}"));
                }
            }
        }
        return String.join(", ", resources);
    }

    private static String diagnosticScissor(
        boolean scissorEnabled,
        int scissorX,
        int scissorY,
        int scissorWidth,
        int scissorHeight
    ) {
        return scissorEnabled
            ? "enabled:" + scissorX + "," + scissorY + "," + scissorWidth + "," + scissorHeight
            : "disabled";
    }

    private static String diagnosticDrawCall(
        boolean indexed,
        int firstVertex,
        int baseVertex,
        int firstIndex,
        int indexCount,
        int vertexCount,
        int instanceCount,
        @Nullable VertexFormat.IndexType indexType
    ) {
        return "{indexed=" + indexed
            + ",firstVertex=" + firstVertex
            + ",baseVertex=" + baseVertex
            + ",firstIndex=" + firstIndex
            + ",indexCount=" + indexCount
            + ",vertexCount=" + vertexCount
            + ",instanceCount=" + instanceCount
            + ",indexType=" + (indexType == null ? "none" : indexType.name())
            + "}";
    }

    private static String diagnosticVertexInput(PipelineDescriptor.PortableState state) {
        return "{format=" + shaderInputParitySanitizeLabel(String.valueOf(state.vertexFormat()))
            + ",mode=" + state.vertexFormatMode()
            + "}";
    }

    private static String diagnosticPipelineState(PipelineDescriptor.PortableState state) {
        return "{blend=" + state.blendState().map(Object::toString).orElse("disabled")
            + ",writeColor=" + state.writeColor()
            + ",writeAlpha=" + state.writeAlpha()
            + ",writeDepth=" + state.writeDepth()
            + ",depthTest=" + state.depthTestFunction()
            + ",cull=" + state.cull()
            + ",cullFace=" + state.cullFaceMode()
            + ",polygonMode=" + state.polygonMode()
            + ",colorLogic=" + state.colorLogic()
            + ",depthBiasScale=" + state.depthBiasScaleFactor()
            + ",depthBiasConstant=" + state.depthBiasConstant()
            + ",defines=" + state.shaderDefineValues()
            + ",flags=" + state.shaderDefineFlags()
            + "}";
    }

    private static String safeSupplierLabel(java.util.function.Supplier<String> supplier) {
        try {
            return supplier == null ? "unknown" : String.valueOf(supplier.get());
        } catch (RuntimeException exception) {
            return "unavailable:" + exception.getClass().getSimpleName();
        }
    }

    private static String diagnosticCustomPassName(@Nullable Object customPass) {
        if (customPass == null) {
            return "none";
        }
        Class<?> type = customPass.getClass();
        while (type != null) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField("name");
                field.setAccessible(true);
                Object value = field.get(customPass);
                if (value != null) {
                    return String.valueOf(value);
                }
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
                continue;
            } catch (IllegalAccessException | RuntimeException exception) {
                return "unavailable:" + exception.getClass().getSimpleName();
            }
        }
        return customPass.getClass().getName();
    }

    private static String physicalResourceKey(int legacyTextureId, @Nullable VulkanicTexture texture) {
        if (legacyTextureId > 0) {
            return "legacy:" + legacyTextureId;
        }
        int labelId = texture == null ? 0 : legacyTextureIdFromLabel(texture.getLabel());
        if (labelId > 0) {
            return "legacy:" + labelId;
        }
        return texture == null
            ? "missing"
            : "texture-label:" + shaderInputParitySanitizeLabel(texture.getLabel());
    }

    private static int legacyTextureIdFromLabel(@Nullable String label) {
        if (label == null) {
            return 0;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
            .compile("Legacy(?:_| )texture(?:_| )([0-9]+)", java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(label);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    public static void traceScopedCompositeColortex0PoseBoundary() {
        if (!TRACE_RENDER_TARGET_CONTENT_HASHES) {
            return;
        }
        if (!DeterministicCameraCapture.isEnabledForDiagnostics()) {
            return;
        }
        if (!isDeterministicCaptureEligiblePose()) {
            return;
        }
        ScopedCompositeColortex0Binding binding = scopedCompositeColortex0Binding;
        if (binding == null) {
            LOGGER.info(
                "ShaderInputParityResources backend={} source=scoped-composite-colortex0-content pipelineLocation=iris:composite vertexShader=unknown fragmentShader=unknown pipelineHandle=none pipelineKey=unavailable stableKey=unavailable {} resources=[colortex0{layout=set:0,binding:0,type:SAMPLER,stages:[FRAGMENT],sampler={unit=0,view=missing},contentHash={logicalResource=colortex0,mip=0,layer=0,region=0:0:-1:-1,canonicalFormat=unavailable,origin=unavailable,channels=unavailable,hash=unavailable:not-bound-at-pose-boundary,poseContext={}}}]",
                getActiveBackendType().name().toLowerCase(java.util.Locale.ROOT),
                shaderInputParityDeterministicContextFields(),
                shaderInputParityDeterministicContextFields().replace(' ', ',')
            );
            return;
        }

        String poseName = DeterministicCameraCapture.currentPoseNameForDiagnostics();
        String emitKey = getActiveBackendType() + "|" + poseName + "|"
            + binding.stableKey() + "|"
            + (binding.texture() == null ? "legacy:" + binding.legacyTextureId() : "texture:" + System.identityHashCode(binding.texture()))
            + "|mip:" + binding.baseMipLevel() + ':' + binding.mipLevelCount();
        if (!SCOPED_COMPOSITE_COLORTEX0_EMITTED.add(emitKey)) {
            return;
        }

        VulkanicTextureView textureView = createScopedCompositeColortex0DiagnosticView(binding);
        DiagnosticTextureContentHash contentHash;
        String physicalKey = physicalResourceKey(binding.legacyTextureId(), textureView == null ? binding.texture() : textureView.texture());
        String readbackKey = getActiveBackendType().name().toLowerCase(java.util.Locale.ROOT)
            + "|pose-boundary|"
            + poseName
            + '|' + binding.pipelineLocation()
            + '|' + binding.stableKey()
            + '|' + physicalKey;
        if (!reserveDiagnosticContentReadback("pose-boundary", readbackKey)) {
            contentHash = DiagnosticTextureContentHash.unavailable(
                "colortex0",
                textureView == null ? binding.texture() : textureView.texture(),
                textureView,
                diagnosticContentReadbackUnavailableReason(null, "pose-boundary", readbackKey)
            );
        } else {
            try {
                contentHash = diagnosticTextureContentHash(textureView, "colortex0");
            } catch (RuntimeException exception) {
                contentHash = DiagnosticTextureContentHash.unavailable(
                    "colortex0",
                    textureView == null ? binding.texture() : textureView.texture(),
                    textureView,
                    "exception-" + exception.getClass().getSimpleName() + '-' + exception.getMessage()
                );
            }
        }
        String resource = scopedCompositeColortex0ResourceString(binding, textureView, contentHash);
        String backendName = getActiveBackendType().name().toLowerCase(java.util.Locale.ROOT);
        ScopedCompositeColortex0Producer producer = SCOPED_COMPOSITE_COLORTEX0_PRODUCERS.get(backendName + '|' + physicalKey);
        String lifecycleInfo = textureView == null
            ? "textureView=missing"
            : diagnosticTextureLifecycleInfo(textureView, "colortex0");
        traceScopedCompositeColortex0Consumer(binding, physicalKey, lifecycleInfo, producer, contentHash);
        if (textureView != null) {
            textureView.close();
        }

        LOGGER.info(
            "ShaderInputParityResources backend={} source=scoped-composite-colortex0-content pipelineLocation={} vertexShader={} fragmentShader={} pipelineHandle={} pipelineKey={} stableKey={} {} resources=[{}]",
            getActiveBackendType().name().toLowerCase(java.util.Locale.ROOT),
            binding.pipelineLocation(),
            binding.vertexShader(),
            binding.fragmentShader(),
            binding.pipeline() == null ? "none" : binding.pipeline().getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(binding.pipeline())),
            binding.pipelineKey(),
            binding.stableKey(),
            shaderInputParityDeterministicContextFields(),
            resource
        );
    }

    private static void traceScopedCompositeColortex0Consumer(
        ScopedCompositeColortex0Binding binding,
        String physicalKey,
        String lifecycleInfo,
        @Nullable ScopedCompositeColortex0Producer producer,
        DiagnosticTextureContentHash contentHash
    ) {
        String backendName = getActiveBackendType().name().toLowerCase(java.util.Locale.ROOT);
        boolean physicalResourceMatches = producer != null && producer.physicalKey().equals(physicalKey);
        boolean producerAvailable = producer != null && producer.hash() != null && !producer.hash().hash().startsWith("unavailable:");
        boolean consumerAvailable = contentHash != null && !contentHash.hash().startsWith("unavailable:");
        boolean contentsChanged = producerAvailable && consumerAvailable && !producer.hash().hash().equals(contentHash.hash());
        LOGGER.info(
            "ShaderInputParityScopedColortex0Lifecycle backend={} event=consumer-sample source=scoped-composite-colortex0-content logicalResource=colortex0 resourceName={} physicalKey={} legacyTextureId={} producerFound={} physicalResourceMatches={} contentsChangedBetweenProducerAndConsumer={} producerPassLabel={} producerCustomPass={} producerPipelineLocation={} producerPingPong={} producerAttachmentUsage=\"{}\" producerLifecycle=\"{}\" consumerPipelineLocation={} consumerStableKey={} consumerLifecycle=\"{}\" {} producerHash={} consumerHash={{{}}}",
            backendName,
            shaderInputParitySanitizeLabel(binding.resourceName()),
            physicalKey,
            binding.legacyTextureId(),
            producer != null,
            physicalResourceMatches,
            contentsChanged,
            producer == null ? "none" : shaderInputParitySanitizeLabel(producer.passLabel()),
            producer == null ? "none" : shaderInputParitySanitizeLabel(producer.customPassName()),
            producer == null ? "none" : shaderInputParitySanitizeLabel(producer.pipelineLocation()),
            producer == null ? "none" : producer.pingPong(),
            producer == null ? "none" : shaderInputParitySanitizeLabel(producer.attachmentUsage()),
            producer == null ? "none" : shaderInputParitySanitizeLabel(producer.lifecycleInfo()),
            shaderInputParitySanitizeLabel(binding.pipelineLocation()),
            binding.stableKey(),
            shaderInputParitySanitizeLabel(lifecycleInfo),
            shaderInputParityDeterministicContextFields(),
            producer == null ? "unavailable:no-producer" : producer.hash().hash(),
            diagnosticContentHashFields(contentHash)
        );
    }

    @Nullable
    private static VulkanicTextureView createScopedCompositeColortex0DiagnosticView(ScopedCompositeColortex0Binding binding) {
        try {
            if (binding.texture() != null) {
                return createManagedTextureView(
                    binding.texture(),
                    Math.max(0, binding.baseMipLevel()),
                    Math.max(1, binding.mipLevelCount())
                );
            }
            if (binding.legacyTextureId() > 0) {
                return createManagedLegacyTextureView(binding.legacyTextureId());
            }
        } catch (RuntimeException exception) {
            LOGGER.info(
                "ShaderInputParityResourceReadback backend={} source=scoped-composite-colortex0-content logicalResource=colortex0 result=unavailable reason={} {}",
                getActiveBackendType().name().toLowerCase(java.util.Locale.ROOT),
                shaderInputParitySanitizeLabel(exception.getClass().getSimpleName() + ':' + exception.getMessage()),
                shaderInputParityDeterministicContextFields()
            );
        }
        return null;
    }

    private static String scopedCompositeColortex0ResourceString(
        ScopedCompositeColortex0Binding binding,
        @Nullable VulkanicTextureView textureView,
        DiagnosticTextureContentHash contentHash
    ) {
        VulkanicTexture texture = textureView == null ? binding.texture() : textureView.texture();
        String viewDescription = textureView == null
            ? "missing"
            : "{viewClass=" + textureView.getClass().getSimpleName()
            + ",baseMip=" + textureView.getBaseMipLevel()
            + ",mips=" + textureView.getMipLevelCount()
            + ",width=" + safeTextureViewWidth(textureView)
            + ",height=" + safeTextureViewHeight(textureView)
            + ",closed=" + textureView.isClosed()
            + ",texture=" + scopedCompositeColortex0TextureString(texture)
            + "}";
        return binding.resourceName()
            + "{layout=set:" + binding.resourceSet()
            + ",binding:" + binding.resourceBinding()
            + ",type:" + binding.resourceType()
            + ",stages:[" + String.join(", ", binding.stages()) + "]"
            + ",sampler={unit=" + binding.samplerUnit()
            + ",samplerObject=" + (binding.samplerObject() == null ? "none" : binding.samplerObject())
            + ",legacyTextureId=" + binding.legacyTextureId()
            + ",view=" + viewDescription
            + "},contentHash={" + diagnosticContentHashFields(contentHash)
            + ",poseContext={" + shaderInputParityDeterministicContextFields().replace(' ', ',') + "}}}";
    }

    private static String diagnosticContentHashFields(DiagnosticTextureContentHash contentHash) {
        return "logicalResource=" + contentHash.logicalResource()
            + ",mip=" + contentHash.mip()
            + ",layer=" + contentHash.layer()
            + ",region=0:0:" + contentHash.width() + ':' + contentHash.height()
            + ",canonicalFormat=" + contentHash.canonicalFormat()
            + ",storageFormat=" + contentHash.storageFormat()
            + ",origin=" + contentHash.originConvention()
            + ",channels=" + contentHash.channelInterpretation()
            + ",hash=" + contentHash.hash()
            + (contentHash.tileHashes().isBlank() ? "" : ",tileHashes=" + contentHash.tileHashes());
    }

    private static String scopedCompositeColortex0TextureString(@Nullable VulkanicTexture texture) {
        if (texture == null) {
            return "missing";
        }
        return "{label=\"" + shaderInputParitySanitizeLabel(texture.getLabel()) + '"'
            + ",format=" + texture.getVulkanicFormat()
            + ",width=" + safeTextureWidth(texture, 0)
            + ",height=" + safeTextureHeight(texture, 0)
            + ",layers=" + texture.getDepthOrLayers()
            + ",mips=" + texture.getMipLevels()
            + ",usage=" + texture.usage()
            + ",closed=" + texture.isClosed()
            + "}";
    }

    private static boolean isLogicalColortex0(String resourceName, @Nullable VulkanicTextureView textureView) {
        String normalizedName = resourceName == null ? "" : resourceName.toLowerCase(Locale.ROOT);
        if ("colortex0".equals(normalizedName)) {
            return true;
        }
        if ("gcolor".equals(normalizedName) || "tex".equals(normalizedName)) {
            if (textureView == null) {
                return true;
            }
            String label = textureView.texture().getLabel();
            return label != null && label.toLowerCase(Locale.ROOT).startsWith("colortex0");
        }
        return false;
    }

    private static boolean isScopedCompositeColortex0ResourceName(String resourceName) {
        String normalizedName = resourceName == null ? "" : resourceName.toLowerCase(Locale.ROOT);
        return "colortex0".equals(normalizedName) || "gcolor".equals(normalizedName);
    }

    private static DiagnosticTextureContentHash diagnosticTextureContentHash(VulkanicTextureView textureView, String logicalResource) {
        if (textureView == null || textureView.texture() == null) {
            return DiagnosticTextureContentHash.unavailable(logicalResource, null, textureView, "missing-view");
        }
        if (textureView.isClosed() || textureView.texture().isClosed()) {
            return DiagnosticTextureContentHash.unavailable(logicalResource, textureView.texture(), textureView, "closed");
        }
        if (getActiveBackendType() == GraphicsBackendType.OPENGL) {
            return diagnosticOpenGLTextureContentHash(textureView, logicalResource);
        }
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        return directVulkanBackend != null
            ? directVulkanBackend.diagnosticTextureContentHash(getCommandContext(), textureView, logicalResource)
            : getBackend().diagnosticTextureContentHash(getCommandContext(), textureView, logicalResource);
    }

    private static boolean reserveDiagnosticContentReadback(String feature, String readbackKey) {
        if (!TRACE_RENDER_TARGET_CONTENT_HASHES) {
            return false;
        }

        String normalizedKey = feature + '|' + readbackKey;
        if (!RENDER_TARGET_CONTENT_READBACK_KEYS.add(normalizedKey)) {
            return false;
        }

        int count = RENDER_TARGET_CONTENT_READBACK_COUNT.incrementAndGet();
        if (count <= MAX_RENDER_TARGET_CONTENT_READBACKS) {
            return true;
        }
        RENDER_TARGET_CONTENT_READBACK_KEYS.remove(normalizedKey);
        return false;
    }

    private static String diagnosticContentReadbackUnavailableReason(String featureDisabledReason, String feature, String readbackKey) {
        if (!TRACE_RENDER_TARGET_CONTENT_HASHES) {
            return "content-hashes-disabled";
        }
        if (featureDisabledReason != null && !featureDisabledReason.isBlank()) {
            return featureDisabledReason;
        }
        String normalizedKey = feature + '|' + readbackKey;
        boolean alreadyReserved = RENDER_TARGET_CONTENT_READBACK_KEYS.contains(normalizedKey);
        if (alreadyReserved) {
            return "content-readback-duplicate-skipped";
        }
        if (RENDER_TARGET_CONTENT_READBACK_COUNT.get() >= MAX_RENDER_TARGET_CONTENT_READBACKS) {
            return "content-readback-budget-exhausted";
        }
        return "content-readback-unavailable";
    }

    private static String diagnosticTextureLifecycleInfo(VulkanicTextureView textureView, String logicalResource) {
        if (textureView == null || textureView.texture() == null) {
            return "textureView=missing";
        }
        if (getActiveBackendType() == GraphicsBackendType.OPENGL) {
            return "backend=opengl,logicalResource=" + logicalResource
                + ",ordering=render-pass-close-before-sampler-read"
                + ",producerWrite=framebuffer-color-attachment"
                + ",consumerRead=texture-sampling"
                + ",explicitBarrier=none";
        }
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        return directVulkanBackend != null
            ? directVulkanBackend.diagnosticTextureLifecycleInfo(getCommandContext(), textureView, logicalResource)
            : getBackend().diagnosticTextureLifecycleInfo(getCommandContext(), textureView, logicalResource);
    }

    public static void traceShaderInputParityStandaloneUniformFloats(
        String source,
        int program,
        int location,
        @Nullable String name,
        String valueKind,
        boolean transpose,
        float[] values
    ) {
        traceShaderInputParityStandaloneUniformFloats(source, program, location, null, null, name, valueKind, transpose, values);
    }

    public static void traceShaderInputParityStandaloneUniformFloats(
        String source,
        int program,
        int location,
        @Nullable String programIdentity,
        @Nullable String shaderStages,
        @Nullable String name,
        String valueKind,
        boolean transpose,
        float[] values
    ) {
        if (values == null || values.length == 0 || !shouldTraceShaderInputParityLog()) {
            return;
        }

        float[] normalized = normalizeShaderInputParityFloatValues(valueKind, transpose, values);
        ByteBuffer bytes = BufferUtils.createByteBuffer(normalized.length * Float.BYTES);
        for (float value : normalized) {
            bytes.putInt(Float.floatToRawIntBits(value));
        }
        bytes.flip();

        LOGGER.info(
            "ShaderInputParityStandaloneUniform backend={} source={} program={} programIdentity={} shaderStages={} location={} renderPhase={} drawKey={} name={} valueKind={} componentCount={} transpose={} {} {} payloadHash={},sample={}{}",
            getActiveBackendType().name().toLowerCase(Locale.ROOT),
            source,
            program,
            shaderInputParityProgramIdentity(program, programIdentity),
            shaderInputParityValueOrUnknown(shaderStages),
            location,
            shaderInputParityRenderPhase(),
            shaderInputParitySemanticDrawKeyOrUnavailable(),
            sanitizeShaderInputParityUniformName(name),
            valueKind,
            normalized.length,
            transpose,
            shaderInputParitySemanticDrawContextFields(),
            shaderInputParityDeterministicContextFields(),
            shaderInputParityHash(bytes, bytes.remaining()),
            shaderInputParityFloatSample(normalized),
            shaderInputParityDecodedFloatField(name, normalized)
        );
    }

    public static void traceShaderInputParityStandaloneUniformInts(
        String source,
        int program,
        int location,
        @Nullable String name,
        String valueKind,
        int[] values
    ) {
        traceShaderInputParityStandaloneUniformInts(source, program, location, null, null, name, valueKind, values);
    }

    public static void traceShaderInputParityStandaloneUniformInts(
        String source,
        int program,
        int location,
        @Nullable String programIdentity,
        @Nullable String shaderStages,
        @Nullable String name,
        String valueKind,
        int[] values
    ) {
        if (values == null || values.length == 0 || !shouldTraceShaderInputParityLog()) {
            return;
        }

        ByteBuffer bytes = BufferUtils.createByteBuffer(values.length * Integer.BYTES);
        for (int value : values) {
            bytes.putInt(value);
        }
        bytes.flip();

        LOGGER.info(
            "ShaderInputParityStandaloneUniform backend={} source={} program={} programIdentity={} shaderStages={} location={} renderPhase={} drawKey={} name={} valueKind={} componentCount={} transpose=false {} {} payloadHash={},sample={}",
            getActiveBackendType().name().toLowerCase(Locale.ROOT),
            source,
            program,
            shaderInputParityProgramIdentity(program, programIdentity),
            shaderInputParityValueOrUnknown(shaderStages),
            location,
            shaderInputParityRenderPhase(),
            shaderInputParitySemanticDrawKeyOrUnavailable(),
            sanitizeShaderInputParityUniformName(name),
            valueKind,
            values.length,
            shaderInputParitySemanticDrawContextFields(),
            shaderInputParityDeterministicContextFields(),
            shaderInputParityHash(bytes, bytes.remaining()),
            shaderInputParityIntSample(values)
        );
    }

    public static void traceShaderInputParityStandaloneUniformBlockMemberFloats(
        String source,
        int program,
        int location,
        @Nullable String name,
        String valueKind,
        int offset,
        int arraySize,
        int stride,
        float[] values
    ) {
        traceShaderInputParityStandaloneUniformBlockMemberFloats(source, program, location, null, null, name, valueKind, offset, arraySize, stride, values);
    }

    public static void traceShaderInputParityStandaloneUniformBlockMemberFloats(
        String source,
        int program,
        int location,
        @Nullable String programIdentity,
        @Nullable String shaderStages,
        @Nullable String name,
        String valueKind,
        int offset,
        int arraySize,
        int stride,
        float[] values
    ) {
        if (values == null || values.length == 0 || !shouldTraceShaderInputParityLog()) {
            return;
        }

        ByteBuffer bytes = BufferUtils.createByteBuffer(values.length * Float.BYTES);
        for (float value : values) {
            bytes.putInt(Float.floatToRawIntBits(value));
        }
        bytes.flip();

        LOGGER.info(
            "ShaderInputParityStandaloneUniformBlockMember backend={} source={} program={} programIdentity={} shaderStages={} location={} renderPhase={} drawKey={} name={} valueKind={} componentCount={} offset={} arraySize={} stride={} {} {} payloadHash={},sample={}{}",
            getActiveBackendType().name().toLowerCase(Locale.ROOT),
            source,
            program,
            shaderInputParityProgramIdentity(program, programIdentity),
            shaderInputParityValueOrUnknown(shaderStages),
            location,
            shaderInputParityRenderPhase(),
            shaderInputParitySemanticDrawKeyOrUnavailable(),
            sanitizeShaderInputParityUniformName(name),
            valueKind,
            values.length,
            offset,
            arraySize,
            stride,
            shaderInputParitySemanticDrawContextFields(),
            shaderInputParityDeterministicContextFields(),
            shaderInputParityHash(bytes, bytes.remaining()),
            shaderInputParityFloatSample(values),
            shaderInputParityDecodedFloatField(name, values)
        );
    }

    public static void traceShaderInputParityStandaloneUniformBlockMemberInts(
        String source,
        int program,
        int location,
        @Nullable String name,
        String valueKind,
        int offset,
        int arraySize,
        int stride,
        int[] values
    ) {
        traceShaderInputParityStandaloneUniformBlockMemberInts(source, program, location, null, null, name, valueKind, offset, arraySize, stride, values);
    }

    public static void traceShaderInputParityStandaloneUniformBlockMemberInts(
        String source,
        int program,
        int location,
        @Nullable String programIdentity,
        @Nullable String shaderStages,
        @Nullable String name,
        String valueKind,
        int offset,
        int arraySize,
        int stride,
        int[] values
    ) {
        if (values == null || values.length == 0 || !shouldTraceShaderInputParityLog()) {
            return;
        }

        ByteBuffer bytes = BufferUtils.createByteBuffer(values.length * Integer.BYTES);
        for (int value : values) {
            bytes.putInt(value);
        }
        bytes.flip();

        LOGGER.info(
            "ShaderInputParityStandaloneUniformBlockMember backend={} source={} program={} programIdentity={} shaderStages={} location={} renderPhase={} drawKey={} name={} valueKind={} componentCount={} offset={} arraySize={} stride={} {} {} payloadHash={},sample={}",
            getActiveBackendType().name().toLowerCase(Locale.ROOT),
            source,
            program,
            shaderInputParityProgramIdentity(program, programIdentity),
            shaderInputParityValueOrUnknown(shaderStages),
            location,
            shaderInputParityRenderPhase(),
            shaderInputParitySemanticDrawKeyOrUnavailable(),
            sanitizeShaderInputParityUniformName(name),
            valueKind,
            values.length,
            offset,
            arraySize,
            stride,
            shaderInputParitySemanticDrawContextFields(),
            shaderInputParityDeterministicContextFields(),
            shaderInputParityHash(bytes, bytes.remaining()),
            shaderInputParityIntSample(values)
        );
    }

    private static String shaderInputParityDeterministicContextFields() {
        return DeterministicCameraCapture.shaderInputParityContextFields();
    }

    private static String shaderInputParitySemanticDrawContextFields() {
        ShaderInputParitySemanticDrawIdentity identity = SHADER_INPUT_PARITY_SEMANTIC_DRAW.get();
        return identity == null
            ? "semanticDrawKey=unavailable semanticSubsystem=unknown semanticPhase=unknown semanticPass=unknown semanticPipeline=unknown semanticVertexShader=unknown semanticFragmentShader=unknown semanticMaterial=unknown semanticOutput=unknown semanticOrdinal=0"
            : identity.fields();
    }

    public static String currentShaderInputParitySemanticDrawContextFields() {
        return shaderInputParitySemanticDrawContextFields();
    }

    private static String shaderInputParitySemanticDrawKeyOrUnavailable() {
        ShaderInputParitySemanticDrawIdentity identity = SHADER_INPUT_PARITY_SEMANTIC_DRAW.get();
        return identity == null ? "unavailable" : identity.key();
    }

    private static String shaderInputParityPipelineLocation(
        @Nullable RenderPipeline renderPipeline,
        @Nullable PipelineDescriptor.PortableState portableState
    ) {
        if (portableState != null) {
            return shaderInputParitySanitizeLabel(portableState.location().toString());
        }
        if (renderPipeline != null) {
            return shaderInputParitySanitizeLabel(renderPipeline.getLocation().toString());
        }
        return "unknown";
    }

    private static String shaderInputParityVertexShader(
        @Nullable RenderPipeline renderPipeline,
        @Nullable PipelineDescriptor.PortableState portableState
    ) {
        if (portableState != null) {
            return shaderInputParitySanitizeLabel(portableState.vertexShader().toString());
        }
        if (renderPipeline != null) {
            return shaderInputParitySanitizeLabel(renderPipeline.getVertexShader().toString());
        }
        return "unknown";
    }

    private static String shaderInputParityFragmentShader(
        @Nullable RenderPipeline renderPipeline,
        @Nullable PipelineDescriptor.PortableState portableState
    ) {
        if (portableState != null) {
            return shaderInputParitySanitizeLabel(portableState.fragmentShader().toString());
        }
        if (renderPipeline != null) {
            return shaderInputParitySanitizeLabel(renderPipeline.getFragmentShader().toString());
        }
        return "unknown";
    }

    private static String shaderInputParityHashString(String value) {
        ByteBuffer bytes = ByteBuffer.wrap(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return shaderInputParityHash(bytes, bytes.remaining());
    }

    private record GeometryParityResult(
        String status,
        String reason,
        long totalVertices,
        long totalIndices,
        String vertexHash,
        String indexHash,
        String detail
    ) {
    }

    private static final class ShaderInputParityOpenGLLegacyGpuBuffer extends GpuBuffer {
        private final net.vulkanic.backends.opengl.OpenGLBuffer buffer;

        private ShaderInputParityOpenGLLegacyGpuBuffer(int handle, int usage, int size) {
            super(usage, size);
            this.buffer = new net.vulkanic.backends.opengl.OpenGLBuffer(handle, usage, size);
        }

        @Override
        public boolean isClosed() {
            return false;
        }

        @Override
        public void close() {
        }
    }

    private static GeometryParityResult buildShaderInputParityGeometry(
        @Nullable GpuBuffer vertexBuffer,
        @Nullable GpuBuffer indexBuffer,
        VertexFormat vertexFormat,
        boolean indexed,
        int firstVertex,
        int vertexCount,
        int firstIndex,
        int indexCount,
        @Nullable VertexFormat.IndexType indexType,
        int instanceCount,
        int baseVertex
    ) {
        if (vertexBuffer == null || vertexBuffer.isClosed()) {
            return new GeometryParityResult("not-comparable", "vertex-buffer-missing-or-closed", 0, 0, "unavailable", "unavailable", "none");
        }
        int stride = vertexFormat.getVertexSize();
        if (stride <= 0) {
            return new GeometryParityResult("not-comparable", "vertex-stride-invalid", 0, 0, "unavailable", "unavailable", "none");
        }
        int safeInstances = Math.max(1, instanceCount);
        int safeVertexCount = Math.max(0, vertexCount);
        int safeIndexCount = Math.max(0, indexCount);
        long logicalElements = indexed ? safeIndexCount : safeVertexCount;
        long logicalVertices = logicalElements * safeInstances;
        long logicalIndexBytes = 0L;
        if (indexed) {
            if (indexBuffer == null || indexBuffer.isClosed()) {
                return new GeometryParityResult("not-comparable", "index-buffer-missing-or-closed", logicalVertices, safeIndexCount, "unavailable", "unavailable", "none");
            }
            if (indexType == null) {
                return new GeometryParityResult("not-comparable", "index-type-missing", logicalVertices, safeIndexCount, "unavailable", "unavailable", "none");
            }
            logicalIndexBytes = (long) safeIndexCount * indexType.bytes;
        }
        long logicalVertexBytes = logicalVertices * stride;
        if (logicalVertexBytes + logicalIndexBytes > SHADER_INPUT_PARITY_GEOMETRY_MAX_BYTES) {
            return new GeometryParityResult(
                "not-comparable",
                "geometry-byte-budget-exceeded:" + (logicalVertexBytes + logicalIndexBytes) + ">" + SHADER_INPUT_PARITY_GEOMETRY_MAX_BYTES,
                logicalVertices,
                indexed ? safeIndexCount * (long) safeInstances : 0L,
                "unavailable",
                "unavailable",
                "none"
            );
        }

        try {
            VulkanicBuffer resolvedVertexBuffer = resolveVulkanicBuffer(vertexBuffer);
            java.util.zip.CRC32 vertexCrc = new java.util.zip.CRC32();
            java.util.zip.CRC32 indexCrc = new java.util.zip.CRC32();
            long totalIndices = indexed ? safeIndexCount * (long) safeInstances : 0L;
            boolean includeDetail = shaderInputParityGeometryDetailEnabled()
                && logicalVertices <= TRACE_SHADER_INPUT_PARITY_GEOMETRY_DETAIL_MAX_VERTICES;
            StringBuilder detail = includeDetail ? new StringBuilder() : null;
            int detailVertexOrdinal = 0;
            if (indexed) {
                VulkanicBuffer resolvedIndexBuffer = resolveVulkanicBuffer(indexBuffer);
                int indexBytes = safeIndexCount * indexType.bytes;
                int indexOffset = Math.multiplyExact(firstIndex, indexType.bytes);
                java.nio.ByteBuffer indexData = shaderInputParityRead(new VulkanicBufferSlice(resolvedIndexBuffer, indexOffset, indexBytes), indexBytes);
                if (indexData == null) {
                    return new GeometryParityResult("not-comparable", "index-read-unavailable", logicalVertices, totalIndices, "unavailable", "unavailable", "none");
                }
                int minVertex = Integer.MAX_VALUE;
                int maxVertex = Integer.MIN_VALUE;
                int[] logicalIndices = new int[safeIndexCount];
                for (int index = 0; index < safeIndexCount; index++) {
                    int rawIndex = readShaderInputParityIndex(indexData, index * indexType.bytes, indexType);
                    int logicalIndex = rawIndex + baseVertex;
                    logicalIndices[index] = logicalIndex;
                    minVertex = Math.min(minVertex, logicalIndex);
                    maxVertex = Math.max(maxVertex, logicalIndex);
                }
                if (safeIndexCount == 0) {
                    minVertex = 0;
                    maxVertex = -1;
                }
                if (minVertex < 0 || maxVertex < minVertex) {
                    return new GeometryParityResult("not-comparable", "indexed-vertex-range-invalid", logicalVertices, totalIndices, "unavailable", "unavailable", "none");
                }
                int vertexReadBytes = Math.multiplyExact(maxVertex - minVertex + 1, stride);
                int vertexReadOffset = Math.multiplyExact(minVertex, stride);
                if ((long) vertexReadOffset + vertexReadBytes > vertexBuffer.size()) {
                    return new GeometryParityResult("not-comparable", "indexed-vertex-range-out-of-bounds", logicalVertices, totalIndices, "unavailable", "unavailable", "none");
                }
                java.nio.ByteBuffer vertexData = shaderInputParityRead(new VulkanicBufferSlice(resolvedVertexBuffer, vertexReadOffset, vertexReadBytes), vertexReadBytes);
                if (vertexData == null) {
                    return new GeometryParityResult("not-comparable", "vertex-read-unavailable", logicalVertices, totalIndices, "unavailable", "unavailable", "none");
                }
                for (int instance = 0; instance < safeInstances; instance++) {
                    updateShaderInputParityInt(indexCrc, instance);
                    updateShaderInputParityInt(vertexCrc, instance);
                    for (int logicalIndex : logicalIndices) {
                        updateShaderInputParityInt(indexCrc, logicalIndex);
                        int localVertex = logicalIndex - minVertex;
                        updateShaderInputParityVertex(vertexCrc, vertexData, localVertex * stride, vertexFormat);
                        if (includeDetail && detailVertexOrdinal < TRACE_SHADER_INPUT_PARITY_GEOMETRY_DETAIL_MAX_VERTICES) {
                            appendShaderInputParityVertexDetail(detail, detailVertexOrdinal, instance, logicalIndex, vertexData, localVertex * stride, vertexFormat);
                        }
                        detailVertexOrdinal++;
                    }
                }
            } else {
                if (safeVertexCount == 0) {
                    return new GeometryParityResult("equivalent-candidate", "empty-non-indexed-draw", 0, 0, "crc32:0/vertices:0", "none", "none");
                }
                int vertexReadBytes = Math.multiplyExact(safeVertexCount, stride);
                int vertexReadOffset = Math.multiplyExact(firstVertex, stride);
                if ((long) vertexReadOffset + vertexReadBytes > vertexBuffer.size()) {
                    return new GeometryParityResult("not-comparable", "vertex-range-out-of-bounds", logicalVertices, 0, "unavailable", "none", "none");
                }
                java.nio.ByteBuffer vertexData = shaderInputParityRead(new VulkanicBufferSlice(resolvedVertexBuffer, vertexReadOffset, vertexReadBytes), vertexReadBytes);
                if (vertexData == null) {
                    return new GeometryParityResult("not-comparable", "vertex-read-unavailable", logicalVertices, 0, "unavailable", "none", "none");
                }
                for (int instance = 0; instance < safeInstances; instance++) {
                    updateShaderInputParityInt(vertexCrc, instance);
                    for (int vertex = 0; vertex < safeVertexCount; vertex++) {
                        updateShaderInputParityVertex(vertexCrc, vertexData, vertex * stride, vertexFormat);
                        if (includeDetail && detailVertexOrdinal < TRACE_SHADER_INPUT_PARITY_GEOMETRY_DETAIL_MAX_VERTICES) {
                            appendShaderInputParityVertexDetail(detail, detailVertexOrdinal, instance, firstVertex + vertex, vertexData, vertex * stride, vertexFormat);
                        }
                        detailVertexOrdinal++;
                    }
                }
            }
            String vertexHash = "crc32:" + Long.toHexString(vertexCrc.getValue()) + "/vertices:" + logicalVertices;
            String indexHash = indexed
                ? "crc32:" + Long.toHexString(indexCrc.getValue()) + "/indices:" + totalIndices
                : "none";
            return new GeometryParityResult(
                "equivalent-candidate",
                "ok",
                logicalVertices,
                totalIndices,
                vertexHash,
                indexHash,
                detail == null || detail.isEmpty() ? "none" : detail.toString()
            );
        } catch (RuntimeException ex) {
            return new GeometryParityResult(
                "not-comparable",
                "exception:" + ex.getClass().getSimpleName(),
                logicalVertices,
                indexed ? safeIndexCount * (long) safeInstances : 0L,
                "unavailable",
                "unavailable",
                "none"
            );
        }
    }

    private static void updateShaderInputParityVertex(java.util.zip.CRC32 crc, java.nio.ByteBuffer vertexData, int baseOffset, VertexFormat vertexFormat) {
        for (VertexFormatElement element : vertexFormat.getElements()) {
            updateShaderInputParityInt(crc, element.id());
            updateShaderInputParityInt(crc, element.index());
            updateShaderInputParityInt(crc, element.type().ordinal());
            updateShaderInputParityInt(crc, element.usage().ordinal());
            updateShaderInputParityInt(crc, element.count());
            int elementOffset = vertexFormat.getOffset(element);
            int byteSize = element.byteSize();
            for (int index = 0; index < byteSize; index++) {
                crc.update(vertexData.get(baseOffset + elementOffset + index) & 0xFF);
            }
        }
    }

    private static boolean shaderInputParityGeometryDetailEnabled() {
        if (!TRACE_SHADER_INPUT_PARITY_GEOMETRY_DETAIL) {
            return false;
        }
        ShaderInputParitySemanticDrawIdentity identity = SHADER_INPUT_PARITY_SEMANTIC_DRAW.get();
        if (identity == null) {
            return TRACE_SHADER_INPUT_PARITY_GEOMETRY_DETAIL_PIPELINES.isEmpty()
                || TRACE_SHADER_INPUT_PARITY_GEOMETRY_DETAIL_PIPELINES.contains("*");
        }
        return TRACE_SHADER_INPUT_PARITY_GEOMETRY_DETAIL_PIPELINES.isEmpty()
            || TRACE_SHADER_INPUT_PARITY_GEOMETRY_DETAIL_PIPELINES.contains("*")
            || TRACE_SHADER_INPUT_PARITY_GEOMETRY_DETAIL_PIPELINES.contains(identity.pipeline());
    }

    private static void appendShaderInputParityVertexDetail(
        StringBuilder builder,
        int consumedVertexOrdinal,
        int instance,
        int logicalVertexIndex,
        java.nio.ByteBuffer vertexData,
        int baseOffset,
        VertexFormat vertexFormat
    ) {
        for (VertexFormatElement element : vertexFormat.getElements()) {
            if (builder.length() > 0) {
                builder.append(';');
            }
            int elementOffset = vertexFormat.getOffset(element);
            int byteSize = element.byteSize();
            builder
                .append('v').append(consumedVertexOrdinal)
                .append(".inst").append(instance)
                .append(".idx").append(logicalVertexIndex)
                .append('.').append(element.usage().name())
                .append(element.index())
                .append('@').append(elementOffset)
                .append('.').append(element.type().name())
                .append(element.count())
                .append(".hash=").append(shaderInputParityElementHash(vertexData, baseOffset + elementOffset, byteSize))
                .append(".value=").append(shaderInputParityDecodeElement(vertexData, baseOffset + elementOffset, element));
        }
    }

    private static String shaderInputParityElementHash(java.nio.ByteBuffer vertexData, int offset, int byteSize) {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        for (int index = 0; index < byteSize; index++) {
            crc.update(vertexData.get(offset + index) & 0xFF);
        }
        return "crc32:" + Long.toHexString(crc.getValue()) + "/bytes:" + byteSize;
    }

    private static String shaderInputParityDecodeElement(java.nio.ByteBuffer vertexData, int offset, VertexFormatElement element) {
        StringBuilder builder = new StringBuilder("(");
        for (int component = 0; component < element.count(); component++) {
            if (component > 0) {
                builder.append(',');
            }
            int componentOffset = offset + component * element.type().size();
            builder.append(shaderInputParityDecodeComponent(vertexData, componentOffset, element.type(), element.usage()));
        }
        return builder.append(')').toString();
    }

    private static String shaderInputParityDecodeComponent(
        java.nio.ByteBuffer vertexData,
        int offset,
        VertexFormatElement.Type type,
        VertexFormatElement.Usage usage
    ) {
        return switch (type) {
            case FLOAT -> Float.toHexString(vertexData.getFloat(offset));
            case UBYTE -> {
                int value = vertexData.get(offset) & 0xFF;
                if (usage == VertexFormatElement.Usage.COLOR) {
                    yield String.format(Locale.ROOT, "%.8f", value / 255.0F);
                }
                yield Integer.toUnsignedString(value);
            }
            case BYTE -> Integer.toString(vertexData.get(offset));
            case USHORT -> Integer.toUnsignedString(vertexData.getShort(offset) & 0xFFFF);
            case SHORT -> Short.toString(vertexData.getShort(offset));
            case UINT -> Integer.toUnsignedString(vertexData.getInt(offset));
            case INT -> Integer.toString(vertexData.getInt(offset));
        };
    }

    private static int readShaderInputParityIndex(java.nio.ByteBuffer data, int offset, VertexFormat.IndexType indexType) {
        return switch (indexType) {
            case SHORT -> data.getShort(offset) & 0xFFFF;
            case INT -> data.getInt(offset);
        };
    }

    private static void updateShaderInputParityInt(java.util.zip.CRC32 crc, int value) {
        crc.update(value & 0xFF);
        crc.update((value >>> 8) & 0xFF);
        crc.update((value >>> 16) & 0xFF);
        crc.update((value >>> 24) & 0xFF);
    }

    private static long shaderInputParityPrimitiveCount(VertexFormat.Mode mode, int elementCount, int instanceCount) {
        int count = Math.max(0, elementCount);
        long primitives = switch (mode) {
            case LINES, DEBUG_LINES -> count / 2L;
            case LINE_STRIP, DEBUG_LINE_STRIP -> Math.max(0, count - 1L);
            case TRIANGLES -> count / 3L;
            case TRIANGLE_STRIP, TRIANGLE_FAN -> Math.max(0, count - 2L);
            case QUADS -> count / 4L;
        };
        return primitives * Math.max(1, instanceCount);
    }

    private static String shaderInputParityVertexFormatHash(VertexFormat vertexFormat) {
        StringBuilder builder = new StringBuilder();
        builder.append("stride=").append(vertexFormat.getVertexSize()).append(';');
        for (VertexFormatElement element : vertexFormat.getElements()) {
            builder
                .append(element.id()).append(':')
                .append(element.index()).append(':')
                .append(element.usage()).append(':')
                .append(element.type()).append(':')
                .append(element.count()).append('@')
                .append(vertexFormat.getOffset(element)).append(';');
        }
        return shaderInputParityHashString(builder.toString());
    }

    private static String shaderInputParityProgramIdentity(int program, @Nullable String explicitIdentity) {
        if (explicitIdentity != null && !explicitIdentity.isBlank()) {
            return shaderInputParitySanitizeLabel(explicitIdentity);
        }
        String registeredName = SHADER_INPUT_PARITY_PROGRAM_NAMES.get(program);
        if (registeredName != null && !registeredName.isBlank()) {
            return shaderInputParitySanitizeLabel(registeredName);
        }
        return "program:" + program;
    }

    private static String shaderInputParityValueOrUnknown(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return shaderInputParitySanitizeLabel(value);
    }

    private static String shaderInputParityRenderPhase() {
        try {
            return shaderInputParitySanitizeLabel(net.irisshaders.iris.layer.GbufferPrograms.getCurrentPhase().name());
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    private static boolean shouldTraceShaderInputParityLog() {
        if (!TRACE_SHADER_INPUT_PARITY) {
            return false;
        }
        if (TRACE_SHADER_INPUT_PARITY_POSE_ONLY) {
            String poseName = DeterministicCameraCapture.currentPoseNameForDiagnostics();
            if ("none".equals(poseName) || "complete".equals(poseName)) {
                return false;
            }
        }
        return SHADER_INPUT_PARITY_LOG_COUNT.incrementAndGet() <= MAX_SHADER_INPUT_PARITY_LOGS;
    }

    public static boolean shouldCollectShaderInputParityDiagnostics() {
        if (!TRACE_SHADER_INPUT_PARITY) {
            return false;
        }
        if (TRACE_SHADER_INPUT_PARITY_POSE_ONLY) {
            String poseName = DeterministicCameraCapture.currentPoseNameForDiagnostics();
            if ("none".equals(poseName) || "complete".equals(poseName)) {
                return false;
            }
        }
        return SHADER_INPUT_PARITY_LOG_COUNT.get() < MAX_SHADER_INPUT_PARITY_LOGS;
    }

    private static String sanitizeShaderInputParityUniformName(@Nullable String name) {
        if (name == null || name.isBlank()) {
            return "unknown";
        }
        return name.replaceAll("[^A-Za-z0-9_.$:-]", "_");
    }

    private static float[] normalizeShaderInputParityFloatValues(String valueKind, boolean transpose, float[] values) {
        if (!transpose) {
            return java.util.Arrays.copyOf(values, values.length);
        }
        if ("mat3".equals(valueKind) && values.length >= 9) {
            return new float[] {
                values[0], values[3], values[6],
                values[1], values[4], values[7],
                values[2], values[5], values[8]
            };
        }
        if ("mat4".equals(valueKind) && values.length >= 16) {
            return new float[] {
                values[0], values[4], values[8], values[12],
                values[1], values[5], values[9], values[13],
                values[2], values[6], values[10], values[14],
                values[3], values[7], values[11], values[15]
            };
        }
        return java.util.Arrays.copyOf(values, values.length);
    }

    private static String shaderInputParityFloatSample(float[] values) {
        StringBuilder builder = new StringBuilder("[");
        int limit = Math.min(values.length, 4);
        for (int index = 0; index < limit; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(Integer.toHexString(Float.floatToRawIntBits(values[index])));
        }
        if (values.length > limit) {
            builder.append(",...");
        }
        return builder.append(']').toString();
    }

    private static String shaderInputParityDecodedFloatField(@Nullable String name, float[] values) {
        String sanitizedName = sanitizeShaderInputParityUniformName(name);
        if (!DECODED_STANDALONE_UNIFORM_TRACE_NAMES.contains(sanitizedName)) {
            return "";
        }
        StringBuilder builder = new StringBuilder(" decoded=[");
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(Float.toString(values[index]));
        }
        return builder.append(']').toString();
    }

    private static String shaderInputParityIntSample(int[] values) {
        StringBuilder builder = new StringBuilder("[");
        int limit = Math.min(values.length, 4);
        for (int index = 0; index < limit; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(Integer.toHexString(values[index]));
        }
        if (values.length > limit) {
            builder.append(",...");
        }
        return builder.append(']').toString();
    }

    private static String describeShaderInputParityUniform(
            PipelineDescriptor.ResourceBinding resourceBinding,
            VulkanicBufferSlice slice) {
        String payloadHash = shaderInputParityPayloadHash(resourceBinding.name(), slice);
        String rangeHash = shaderInputParityHash(slice, slice.length());
        java.util.List<String> stages = resourceBinding.stages().stream()
            .map(Enum::name)
            .sorted()
            .toList();

        return resourceBinding.name()
            + "{layout=set:" + resourceBinding.set()
            + ",binding:" + resourceBinding.binding()
            + ",type:" + resourceBinding.type()
            + ",stages:[" + String.join(", ", stages) + "]"
            + ",buffer={bufferClass=" + slice.buffer().getClass().getSimpleName()
            + ",bufferId=" + Integer.toHexString(System.identityHashCode(slice.buffer()))
            + ",size=" + slice.buffer().size()
            + ",usage=" + slice.buffer().usage()
            + ",closed=" + slice.buffer().isClosed()
            + ",offset=" + slice.offset()
            + ",length=" + slice.length()
            + ",payloadHash=" + payloadHash
            + ",rangeHash=" + rangeHash
            + shaderInputParityProjectionLabel(resourceBinding.name(), slice)
            + shaderInputParitySemanticDetails(resourceBinding.name(), slice)
            + "}}";
    }

    private static String describeShaderInputParitySampler(
            PipelineDescriptor.ResourceBinding resourceBinding,
            PipelineResourceBindings.SamplerBinding samplerBinding) {
        VulkanicTextureView textureView = samplerBinding.textureView();
        StringBuilder builder = new StringBuilder();
        java.util.List<String> stages = resourceBinding.stages().stream()
            .map(Enum::name)
            .sorted()
            .toList();

        builder.append(resourceBinding.name())
            .append("{layout=set:").append(resourceBinding.set())
            .append(",binding:").append(resourceBinding.binding())
            .append(",type:").append(resourceBinding.type())
            .append(",stages:[").append(String.join(", ", stages)).append("]")
            .append(",sampler={unit=").append(samplerBinding.textureUnit())
            .append(",samplerObject=").append(samplerBinding.samplerObject() == null ? "none" : samplerBinding.samplerObject());

        if (textureView == null) {
            return builder.append(",view=missing}}").toString();
        }

        VulkanicTexture texture = textureView.texture();
        builder.append(",view={viewClass=").append(textureView.getClass().getSimpleName())
            .append(",viewId=").append(Integer.toHexString(System.identityHashCode(textureView)))
            .append(",baseMip=").append(textureView.getBaseMipLevel())
            .append(",mips=").append(textureView.getMipLevelCount())
            .append(",width=").append(safeTextureViewWidth(textureView))
            .append(",height=").append(safeTextureViewHeight(textureView))
            .append(",closed=").append(textureView.isClosed())
            .append(",texture={class=").append(texture.getClass().getSimpleName())
            .append(",id=").append(Integer.toHexString(System.identityHashCode(texture)))
            .append(",label=\"").append(shaderInputParitySanitizeLabel(texture.getLabel())).append('"')
            .append(",format=").append(texture.getVulkanicFormat())
            .append(",width=").append(safeTextureWidth(texture, 0))
            .append(",height=").append(safeTextureHeight(texture, 0))
            .append(",layers=").append(texture.getDepthOrLayers())
            .append(",mips=").append(texture.getMipLevels())
            .append(",usage=").append(texture.usage())
            .append(",closed=").append(texture.isClosed())
            .append(",samplerState={").append(shaderInputParitySamplerState(texture)).append('}')
            .append("}}}}");

        if (TRACE_SHADER_INPUT_SAMPLER_CONTENT_HASHES && shouldTraceRenderTargetContentHash(resourceBinding.name(), texture.getLabel())) {
            builder.append(",contentHash={")
                .append("logicalResource=").append(shaderInputParitySanitizeLabel(resourceBinding.name()))
                .append(",mip=").append(textureView.getBaseMipLevel())
                .append(",layer=0")
                .append(",region=0:0:").append(safeTextureViewWidth(textureView)).append(':').append(safeTextureViewHeight(textureView))
                .append(",hash=").append(shaderInputParityTextureContentHash(textureView, resourceBinding.name()))
                .append(",poseContext={").append(shaderInputParityDeterministicContextFields().replace(' ', ',')).append("}")
                .append('}');
        }

        return builder.append('}').toString();
    }

    private static String shaderInputParitySamplerState(VulkanicTexture texture) {
        if (texture instanceof GpuTexture gpuTexture) {
            return "minFilter=" + gpuTexture.getMinFilter()
                + ",magFilter=" + gpuTexture.getMagFilter()
                + ",mipmaps=" + gpuTexture.usesMipmaps()
                + ",wrapU=" + gpuTexture.getAddressModeU()
                + ",wrapV=" + gpuTexture.getAddressModeV()
                + ",compare=none"
                + ",swizzle=identity"
                + ",srgbInterpretation=" + shaderInputParitySrgbInterpretation(gpuTexture.getFormat());
        }
        return "minFilter=unknown,magFilter=unknown,mipmaps=unknown,wrapU=unknown,wrapV=unknown,compare=unknown,swizzle=unknown,srgbInterpretation=unknown";
    }

    private static String shaderInputParitySrgbInterpretation(TextureFormat format) {
        return switch (format) {
            case RGBA8, BGRA8 -> "linear-unorm";
            case RED8, RED8I -> "linear-red";
            case DEPTH32, DEPTH24_STENCIL8, DEPTH32F_STENCIL8 -> "depth";
        };
    }

    private static boolean shouldTraceRenderTargetContentHash(String resourceName, @Nullable String label) {
        String normalizedName = resourceName == null ? "" : resourceName.toLowerCase(Locale.ROOT);
        String normalizedLabel = label == null ? "" : label.toLowerCase(Locale.ROOT);
        return normalizedName.startsWith("colortex")
            || normalizedName.startsWith("depthtex")
            || normalizedName.contains("dhdepth")
            || normalizedName.contains("shadow")
            || normalizedName.contains("floodfill")
            || normalizedName.contains("history")
            || normalizedLabel.startsWith("colortex")
            || normalizedLabel.startsWith("depthtex")
            || normalizedLabel.contains("dhdepth")
            || normalizedLabel.contains("shadow")
            || normalizedLabel.contains("floodfill")
            || normalizedLabel.contains("history");
    }

    private static String shaderInputParityTextureContentHash(VulkanicTextureView textureView, String resourceName) {
        VulkanicTexture texture = textureView.texture();
        VulkanicTextureFormat format = texture.getVulkanicFormat();
        if (textureView.isClosed() || texture.isClosed()) {
            return "unavailable:closed";
        }
        if (getActiveBackendType() != GraphicsBackendType.OPENGL) {
            return "unavailable:vulkan-readback-not-yet-normalized";
        }
        if (!(textureView instanceof net.vulkanic.backends.opengl.OpenGLTextureView openGLTextureView)) {
            return "unavailable:not-opengl-view";
        }
        int width = Math.min(Math.max(0, safeTextureViewWidth(textureView)), 128);
        int height = Math.min(Math.max(0, safeTextureViewHeight(textureView)), 128);
        if (width <= 0 || height <= 0) {
            return "unavailable:empty";
        }
        int externalFormat;
        int externalType;
        int bytesPerPixel;
        switch (format) {
            case RGBA8, BGRA8, RGBA8_SNORM -> {
                externalFormat = org.lwjgl.opengl.GL11.GL_RGBA;
                externalType = org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
                bytesPerPixel = 4;
            }
            case RED8, RED8I, RED8UI -> {
                externalFormat = org.lwjgl.opengl.GL11.GL_RED;
                externalType = org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
                bytesPerPixel = 1;
            }
            case RGBA16F -> {
                externalFormat = org.lwjgl.opengl.GL11.GL_RGBA;
                externalType = org.lwjgl.opengl.GL11.GL_FLOAT;
                bytesPerPixel = 16;
            }
            case R11F_G11F_B10F -> {
                externalFormat = org.lwjgl.opengl.GL11.GL_RGB;
                externalType = org.lwjgl.opengl.GL11.GL_FLOAT;
                bytesPerPixel = 12;
            }
            case RED16F, RED32F, DEPTH32 -> {
                externalFormat = format.hasDepthAspect() ? org.lwjgl.opengl.GL11.GL_DEPTH_COMPONENT : org.lwjgl.opengl.GL11.GL_RED;
                externalType = org.lwjgl.opengl.GL11.GL_FLOAT;
                bytesPerPixel = 4;
            }
            case DEPTH24_STENCIL8 -> {
                externalFormat = org.lwjgl.opengl.GL30.GL_DEPTH_STENCIL;
                externalType = org.lwjgl.opengl.GL30.GL_UNSIGNED_INT_24_8;
                bytesPerPixel = 4;
            }
            case DEPTH32F_STENCIL8 -> {
                return "unavailable:depth32f-stencil8-normalization";
            }
            default -> {
                return "unavailable:format-" + format.name().toLowerCase(Locale.ROOT);
            }
        }
        java.nio.ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * bytesPerPixel);
        try {
            org.lwjgl.opengl.GL45.glGetTextureSubImage(
                openGLTextureView.glHandle(),
                textureView.getBaseMipLevel(),
                0,
                0,
                0,
                width,
                height,
                1,
                externalFormat,
                externalType,
                pixels
            );
        } catch (Throwable exception) {
            return "unavailable:readback-" + shaderInputParitySanitizeLabel(exception.getClass().getSimpleName());
        }
        pixels.position(0);
        pixels.limit(pixels.capacity());
        return shaderInputParityHash(pixels, pixels.remaining()) + "/region:0x0x" + width + "x" + height + "/format:" + format.name();
    }

    private static DiagnosticTextureContentHash diagnosticOpenGLTextureContentHash(
        VulkanicTextureView textureView,
        String logicalResource
    ) {
        VulkanicTexture texture = textureView.texture();
        VulkanicTextureFormat format = texture.getVulkanicFormat();
        if (!(textureView instanceof net.vulkanic.backends.opengl.OpenGLTextureView openGLTextureView)) {
            return DiagnosticTextureContentHash.unavailable(logicalResource, texture, textureView, "not-opengl-view");
        }

        int width = Math.max(0, safeTextureViewWidth(textureView));
        int height = Math.max(0, safeTextureViewHeight(textureView));
        if (width <= 0 || height <= 0) {
            return DiagnosticTextureContentHash.unavailable(logicalResource, texture, textureView, "empty");
        }

        int externalFormat;
        int externalType;
        int componentCount;
        switch (format) {
            case RGBA8, BGRA8, RGBA8_SNORM, RGBA16F -> {
                externalFormat = org.lwjgl.opengl.GL11.GL_RGBA;
                externalType = org.lwjgl.opengl.GL11.GL_FLOAT;
                componentCount = 4;
            }
            case R11F_G11F_B10F -> {
                externalFormat = org.lwjgl.opengl.GL11.GL_RGB;
                externalType = org.lwjgl.opengl.GL11.GL_FLOAT;
                componentCount = 3;
            }
            case RED8, RED8I, RED8UI, RED16F, RED32F -> {
                externalFormat = org.lwjgl.opengl.GL11.GL_RED;
                externalType = org.lwjgl.opengl.GL11.GL_FLOAT;
                componentCount = 1;
            }
            default -> {
                return DiagnosticTextureContentHash.unavailable(
                    logicalResource,
                    texture,
                    textureView,
                    "format-" + format.name().toLowerCase(Locale.ROOT)
                );
            }
        }

        java.nio.ByteBuffer componentBytes = BufferUtils.createByteBuffer(width * height * componentCount * Float.BYTES);
        try {
            org.lwjgl.opengl.GL45.glGetTextureSubImage(
                openGLTextureView.glHandle(),
                textureView.getBaseMipLevel(),
                0,
                0,
                0,
                width,
                height,
                1,
                externalFormat,
                externalType,
                componentBytes
            );
        } catch (Throwable exception) {
            return DiagnosticTextureContentHash.unavailable(
                logicalResource,
                texture,
                textureView,
                "readback-" + exception.getClass().getSimpleName()
            );
        }
        componentBytes.position(0);
        componentBytes.limit(componentBytes.capacity());
        java.nio.ByteBuffer canonical = canonicalizeFloatComponentsToRgba32fTopLeft(
            componentBytes,
            width,
            height,
            componentCount,
            true
        );
        return diagnosticContentHashFromCanonical(logicalResource, texture, textureView, canonical, width, height);
    }

    public static DiagnosticTextureContentHash diagnosticContentHashFromCanonical(
        String logicalResource,
        VulkanicTexture texture,
        VulkanicTextureView textureView,
        java.nio.ByteBuffer canonicalRgba32fTopLeft,
        int width,
        int height
    ) {
        java.nio.ByteBuffer bytes = canonicalRgba32fTopLeft.duplicate();
        bytes.position(0);
        bytes.limit(bytes.capacity());
        bytes.limit(width * height * 4 * Float.BYTES);
        String hash = shaderInputParityHash(bytes, bytes.remaining());
        String tileHashes;
        try {
            tileHashes = diagnosticTileHashes(bytes, width, height);
        } catch (RuntimeException exception) {
            tileHashes = "unavailable:" + shaderInputParitySanitizeLabel(
                exception.getClass().getSimpleName() + '-' + exception.getMessage()
            );
        }
        return new DiagnosticTextureContentHash(
            shaderInputParitySanitizeLabel(logicalResource),
            width,
            height,
            texture.getVulkanicFormat(),
            "RGBA32F_LE",
            textureView.getBaseMipLevel(),
            0,
            "top-left-row-major",
            "raw-linear-shader-visible-components-alpha-one-when-source-lacks-alpha",
            hash,
            tileHashes
        );
    }

    public static java.nio.ByteBuffer canonicalizeFloatComponentsToRgba32fTopLeft(
        java.nio.ByteBuffer source,
        int width,
        int height,
        int sourceComponents,
        boolean sourceRowsAreBottomToTop
    ) {
        java.nio.ByteBuffer input = source.duplicate().order(java.nio.ByteOrder.nativeOrder());
        input.position(0);
        java.nio.FloatBuffer floats = input.asFloatBuffer();
        java.nio.ByteBuffer canonical = BufferUtils.createByteBuffer(width * height * 4 * Float.BYTES)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (int y = 0; y < height; y++) {
            int sourceY = sourceRowsAreBottomToTop ? height - 1 - y : y;
            for (int x = 0; x < width; x++) {
                int sourceIndex = (sourceY * width + x) * sourceComponents;
                float r = sourceComponents >= 1 ? floats.get(sourceIndex) : 0.0F;
                float g = sourceComponents >= 2 ? floats.get(sourceIndex + 1) : r;
                float b = sourceComponents >= 3 ? floats.get(sourceIndex + 2) : r;
                float a = sourceComponents >= 4 ? floats.get(sourceIndex + 3) : 1.0F;
                canonical.putFloat(r);
                canonical.putFloat(g);
                canonical.putFloat(b);
                canonical.putFloat(a);
            }
        }
        canonical.flip();
        return canonical;
    }

    public static String diagnosticHash(java.nio.ByteBuffer data, int length) {
        return shaderInputParityHash(data, length);
    }

    private static String diagnosticTileHashes(java.nio.ByteBuffer canonical, int width, int height) {
        if (width <= 0 || height <= 0) {
            return "";
        }
        int tilesX = 4;
        int tilesY = 4;
        int bytesPerPixel = 4 * Float.BYTES;
        int expectedBytes = width * height * bytesPerPixel;
        if (canonical.capacity() < expectedBytes) {
            return "unavailable:canonical-size-mismatch:expected-" + expectedBytes + ":actual-" + canonical.capacity();
        }
        java.util.List<String> hashes = new java.util.ArrayList<>(tilesX * tilesY);
        for (int tileY = 0; tileY < tilesY; tileY++) {
            int y0 = tileY * height / tilesY;
            int y1 = (tileY + 1) * height / tilesY;
            for (int tileX = 0; tileX < tilesX; tileX++) {
                int x0 = tileX * width / tilesX;
                int x1 = (tileX + 1) * width / tilesX;
                java.security.MessageDigest digest;
                try {
                    digest = java.security.MessageDigest.getInstance("SHA-256");
                } catch (java.security.NoSuchAlgorithmException exception) {
                    throw new IllegalStateException("SHA-256 digest unavailable", exception);
                }
                java.nio.ByteBuffer tileSource = canonical.duplicate();
                tileSource.position(0);
                tileSource.limit(tileSource.capacity());
                for (int y = y0; y < y1; y++) {
                    int rowOffset = (y * width + x0) * bytesPerPixel;
                    int rowLength = (x1 - x0) * bytesPerPixel;
                    if (rowOffset < 0 || rowLength < 0 || rowOffset + rowLength > tileSource.capacity()) {
                        return "unavailable:tile-range-mismatch:offset-" + rowOffset + ":length-" + rowLength + ":capacity-" + tileSource.capacity();
                    }
                    tileSource.limit(tileSource.capacity());
                    tileSource.position(rowOffset);
                    tileSource.limit(rowOffset + rowLength);
                    digest.update(tileSource.slice());
                }
                hashes.add(tileX + "x" + tileY + ":" + toHex(digest.digest()).substring(0, 16));
            }
        }
        return String.join("|", hashes);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(Character.forDigit((value >>> 4) & 0xF, 16));
            builder.append(Character.forDigit(value & 0xF, 16));
        }
        return builder.toString();
    }

    private static int safeTextureViewWidth(VulkanicTextureView textureView) {
        try {
            return textureView.getWidth(0);
        } catch (RuntimeException exception) {
            return -1;
        }
    }

    private static int safeTextureViewHeight(VulkanicTextureView textureView) {
        try {
            return textureView.getHeight(0);
        } catch (RuntimeException exception) {
            return -1;
        }
    }

    private static int safeTextureWidth(VulkanicTexture texture, int mipLevel) {
        try {
            return texture.getWidth(mipLevel);
        } catch (RuntimeException exception) {
            return -1;
        }
    }

    private static int safeTextureHeight(VulkanicTexture texture, int mipLevel) {
        try {
            return texture.getHeight(mipLevel);
        } catch (RuntimeException exception) {
            return -1;
        }
    }

    private static String shaderInputParityProjectionLabel(String name, VulkanicBufferSlice slice) {
        if (!"Projection".equals(name)) {
            return "";
        }

        String targetBufferKey = shaderInputParityBufferKey(slice.buffer());
        synchronized (projectionMatrixLabels) {
            for (java.util.Map.Entry<GpuBufferSlice, String> entry : projectionMatrixLabels.entrySet()) {
                GpuBufferSlice gpuSlice = entry.getKey();
                if (gpuSlice == null) {
                    continue;
                }

                VulkanicBuffer labeledBuffer;
                try {
                    labeledBuffer = resolveVulkanicBuffer(gpuSlice.buffer());
                } catch (RuntimeException ex) {
                    continue;
                }

                if (!targetBufferKey.equals(shaderInputParityBufferKey(labeledBuffer))) {
                    continue;
                }

                if (gpuSlice.offset() == slice.offset() && gpuSlice.length() == slice.length()) {
                    return ",projectionLabel=" + shaderInputParitySanitizeLabel(entry.getValue());
                }
            }
        }

        return ",projectionLabel=unlabeled";
    }

    private static String shaderInputParityBufferKey(VulkanicBuffer buffer) {
        if (buffer instanceof net.vulkanic.backends.opengl.OpenGLBuffer openGLBuffer) {
            return "opengl:" + openGLBuffer.getGlHandle();
        }

        return "managed:" + System.identityHashCode(buffer);
    }

    private static String shaderInputParitySanitizeLabel(String value) {
        return value
            .replace(',', ';')
            .replace('{', '(')
            .replace('}', ')')
            .replace('[', '(')
            .replace(']', ')')
            .replace('"', '\'')
            .replace(' ', '_');
    }

    private static String shaderInputParitySemanticDetails(String name, VulkanicBufferSlice slice) {
        int semanticLength = switch (name) {
            case "Projection" -> 64;
            case "DynamicTransforms" -> 164;
            case "Fog" -> 40;
            case "Lighting" -> 32;
            case "LightmapInfo" -> 64;
            default -> 0;
        };

        if (semanticLength == 0) {
            return "";
        }

        java.nio.ByteBuffer data = shaderInputParityRead(slice, Math.min(semanticLength, slice.length()));
        if (data == null || data.capacity() < semanticLength) {
            return ",semantic=unavailable";
        }

        data.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        return switch (name) {
            case "Projection" -> ",semantic={ProjMat=" + shaderInputParityMat4(data, 0) + "}";
            case "DynamicTransforms" -> ",semantic={ModelViewMat=" + shaderInputParityMat4(data, 0)
                + ",ColorModulator=" + shaderInputParityVec4(data, 64)
                + ",ModelOffset=" + shaderInputParityVec3(data, 80)
                + ",TextureMat=" + shaderInputParityMat4(data, 96)
                + String.format(java.util.Locale.ROOT, ",LineWidth=%.8f}", data.getFloat(160));
            case "Fog" -> String.format(
                java.util.Locale.ROOT,
                ",semantic={FogColor=(%.8f,%.8f,%.8f,%.8f),FogEnvironmentalStart=%.8f,FogEnvironmentalEnd=%.8f,FogRenderDistanceStart=%.8f,FogRenderDistanceEnd=%.8f,FogSkyEnd=%.8f,FogCloudsEnd=%.8f}",
                data.getFloat(0),
                data.getFloat(4),
                data.getFloat(8),
                data.getFloat(12),
                data.getFloat(16),
                data.getFloat(20),
                data.getFloat(24),
                data.getFloat(28),
                data.getFloat(32),
                data.getFloat(36)
            );
            case "Lighting" -> String.format(
                java.util.Locale.ROOT,
                ",semantic={Light0_Direction=(%.8f,%.8f,%.8f),Light1_Direction=(%.8f,%.8f,%.8f)}",
                data.getFloat(0),
                data.getFloat(4),
                data.getFloat(8),
                data.getFloat(16),
                data.getFloat(20),
                data.getFloat(24)
            );
            case "LightmapInfo" -> String.format(
                java.util.Locale.ROOT,
                ",semantic={AmbientLight=%.8f,SkyFactor=%.8f,BlockLightFactor=%.8f,NightVisionScale=%.8f,DarknessScale=%.8f,DarkenWorldAmount=%.8f,GammaMinusDarkness=%.8f,SkyLightColor=(%.8f,%.8f,%.8f),LightColor=(%.8f,%.8f,%.8f)}",
                data.getFloat(0),
                data.getFloat(4),
                data.getFloat(8),
                data.getFloat(12),
                data.getFloat(16),
                data.getFloat(20),
                data.getFloat(24),
                data.getFloat(32),
                data.getFloat(36),
                data.getFloat(40),
                data.getFloat(48),
                data.getFloat(52),
                data.getFloat(56)
            );
            default -> "";
        };
    }

    private static String shaderInputParityMat4(java.nio.ByteBuffer data, int offset) {
        return String.format(
            java.util.Locale.ROOT,
            "[(%.8f,%.8f,%.8f,%.8f),(%.8f,%.8f,%.8f,%.8f),(%.8f,%.8f,%.8f,%.8f),(%.8f,%.8f,%.8f,%.8f)]",
            data.getFloat(offset),
            data.getFloat(offset + 4),
            data.getFloat(offset + 8),
            data.getFloat(offset + 12),
            data.getFloat(offset + 16),
            data.getFloat(offset + 20),
            data.getFloat(offset + 24),
            data.getFloat(offset + 28),
            data.getFloat(offset + 32),
            data.getFloat(offset + 36),
            data.getFloat(offset + 40),
            data.getFloat(offset + 44),
            data.getFloat(offset + 48),
            data.getFloat(offset + 52),
            data.getFloat(offset + 56),
            data.getFloat(offset + 60)
        );
    }

    private static String shaderInputParityVec4(java.nio.ByteBuffer data, int offset) {
        return String.format(
            java.util.Locale.ROOT,
            "(%.8f,%.8f,%.8f,%.8f)",
            data.getFloat(offset),
            data.getFloat(offset + 4),
            data.getFloat(offset + 8),
            data.getFloat(offset + 12)
        );
    }

    private static String shaderInputParityVec3(java.nio.ByteBuffer data, int offset) {
        return String.format(
            java.util.Locale.ROOT,
            "(%.8f,%.8f,%.8f)",
            data.getFloat(offset),
            data.getFloat(offset + 4),
            data.getFloat(offset + 8)
        );
    }

    private static String shaderInputParityPayloadHash(String name, VulkanicBufferSlice slice) {
        return switch (name) {
            case "DynamicTransforms" -> shaderInputParityHash(slice, new int[][] {
                {0, 64},   // mat4 ModelViewMat
                {64, 16},  // vec4 ColorModulator
                {80, 12},  // vec3 ModelOffset, excluding std140 padding
                {96, 64},  // mat4 TextureMat
                {160, 4}   // float LineWidth
            }, 160);
            case "Lighting" -> shaderInputParityHash(slice, new int[][] {
                {0, 12},   // vec3 Light0_Direction, excluding std140 padding
                {16, 12}   // vec3 Light1_Direction, excluding std140 padding
            }, 24);
            case "Projection" -> shaderInputParityHash(slice, Math.min(64, slice.length()));
            case "Fog" -> shaderInputParityHash(slice, Math.min(40, slice.length()));
            case "Globals" -> shaderInputParityHash(slice, Math.min(20, slice.length()));
            case "LightmapInfo" -> shaderInputParityHash(slice, new int[][] {
                {0, 28},   // seven scalar floats
                {32, 12},  // vec3 SkyLightColor, excluding std140 padding
                {48, 12}   // vec3 LightColor, excluding std140 padding
            }, 52);
            default -> shaderInputParityHash(slice, slice.length());
        };
    }

    private static String shaderInputParityHash(VulkanicBufferSlice slice, int length) {
        java.nio.ByteBuffer data = shaderInputParityRead(slice, length);
        if (data == null) {
            return "unavailable";
        }

        return shaderInputParityHash(data, length);
    }

    private static String shaderInputParityHash(VulkanicBufferSlice slice, int[][] spans, int semanticLength) {
        if (slice.buffer().isClosed() || semanticLength <= 0) {
            return "unavailable";
        }

        int requiredLength = 0;
        for (int[] span : spans) {
            requiredLength = Math.max(requiredLength, span[0] + span[1]);
        }
        java.nio.ByteBuffer source = shaderInputParityRead(slice, requiredLength);
        if (source == null) {
            return "unavailable";
        }

        java.nio.ByteBuffer semanticBytes = org.lwjgl.BufferUtils.createByteBuffer(semanticLength);
        for (int[] span : spans) {
            int start = span[0];
            int count = span[1];
            for (int index = 0; index < count; index++) {
                semanticBytes.put(source.get(start + index));
            }
        }
        semanticBytes.flip();
        return shaderInputParityHash(semanticBytes, semanticLength);
    }

    private static java.nio.ByteBuffer shaderInputParityRead(VulkanicBufferSlice slice, int length) {
        if (slice.buffer().isClosed() || length <= 0 || length > slice.length()) {
            return null;
        }

        if (slice.buffer() instanceof net.vulkanic.backends.opengl.OpenGLBuffer openGLBuffer) {
            try {
                java.nio.ByteBuffer data = org.lwjgl.BufferUtils.createByteBuffer(length)
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN);
                org.lwjgl.opengl.GL45.glGetNamedBufferSubData(openGLBuffer.getGlHandle(), slice.offset(), data);
                data.position(0);
                return data;
            } catch (RuntimeException ex) {
                return null;
            }
        }

        if (slice.buffer() instanceof net.vulkanic.backends.vulkan.VulkanBuffer vulkanBuffer) {
            java.nio.ByteBuffer shadowData = vulkanBuffer.diagnosticShadowRead(slice.offset(), length);
            if (shadowData != null) {
                return shadowData;
            }
        }

        try (VulkanicBuffer.MappedView mappedView = mapManagedBuffer(slice.buffer(), true, false)) {
            java.nio.ByteBuffer data = mappedView.data().duplicate();
            int start = slice.offset();
            int end = start + length;
            if (start < 0 || end > data.capacity()) {
                return null;
            }

            data.position(start);
            data.limit(end);
            java.nio.ByteBuffer copy = org.lwjgl.BufferUtils.createByteBuffer(length)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
            copy.put(data.slice());
            copy.flip();
            return copy;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String shaderInputParityHash(java.nio.ByteBuffer data, int length) {
        java.util.zip.CRC32 crc32 = new java.util.zip.CRC32();
        for (int index = 0; index < length; index++) {
            crc32.update(data.get(index) & 0xFF);
        }
        return "crc32:" + Long.toHexString(crc32.getValue()) + "/bytes:" + length;
    }

    /**
     * Resolves a {@link GpuBuffer} to the backend-specific {@link VulkanicBuffer} backing it.
     *
     * <p>In OpenGL: returns an {@code OpenGLBuffer} wrapping the GL buffer object name.
     * In Vulkan: looks up the backing {@code VulkanBuffer} from the legacy buffer registry.
     *
     * <p>This is the canonical call for shared render-encoder code that needs to produce
     * backend-neutral {@link VulkanicBufferSlice}s for {@link #bindPipelineResources} — it
     * removes the need for callers to branch on or cast to a specific backend type.
     *
     * @param gpuBuffer the GPU buffer to resolve
     * @return the backend-native buffer representation
     */
    public static VulkanicBuffer resolveVulkanicBuffer(GpuBuffer gpuBuffer) {
        if (gpuBuffer instanceof ShaderInputParityOpenGLLegacyGpuBuffer legacyBuffer) {
            return legacyBuffer.buffer;
        }
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        return directVulkanBackend != null
            ? directVulkanBackend.resolveVulkanicBuffer(gpuBuffer)
            : getBackend().resolveVulkanicBuffer(gpuBuffer);
    }

    /**
     * Returns the compiled {@link PipelineHandle} for the given render pipeline + descriptor,
     * or {@code null} if the active backend has not compiled a handle for them yet.
     *
     * <p>In OpenGL: always returns {@code null} (handles are owned by the GL command encoder).
     * In Vulkan: returns the cached pre-compiled {@code VulkanPipelineHandle} when available.
     *
     * <p>This lets code that holds a {@link net.blaze3d.pipeline.RenderPipeline} reference
     * obtain the matching backend pipeline handle without casting into backend internals.
     *
     * @param renderPipeline  the render pipeline identity
     * @param descriptor      the pipeline descriptor used at compile time
     * @return the compiled handle, or {@code null}
     */
    @Nullable
    public static PipelineHandle resolvePipelineHandle(RenderPipeline renderPipeline,
                                                       PipelineDescriptor descriptor) {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        return directVulkanBackend != null
            ? directVulkanBackend.resolvePipelineHandle(renderPipeline, descriptor)
            : getBackend().resolvePipelineHandle(renderPipeline, descriptor);
    }

    /**
     * Returns a compiled pipeline handle compatible with a specific framebuffer contract,
     * or {@code null} if the active backend has not compiled one yet.
     */
    @Nullable
    public static PipelineHandle resolvePipelineHandle(RenderPipeline renderPipeline,
                                                       PipelineDescriptor descriptor,
                                                       int framebuffer) {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        return directVulkanBackend != null
            ? directVulkanBackend.resolvePipelineHandle(renderPipeline, descriptor, framebuffer)
            : getBackend().resolvePipelineHandle(renderPipeline, descriptor, framebuffer);
    }

    /**
     * Returns a compiled pipeline handle compatible with an explicit render-target contract,
     * or {@code null} if the active backend has not compiled one yet.
     */
    @Nullable
    public static PipelineHandle resolvePipelineHandle(RenderPipeline renderPipeline,
                                                       PipelineDescriptor descriptor,
                                                       VulkanicRenderTargetDescriptor renderTarget) {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        return directVulkanBackend != null
            ? directVulkanBackend.resolvePipelineHandle(renderPipeline, descriptor, renderTarget)
            : getBackend().resolvePipelineHandle(renderPipeline, descriptor, renderTarget);
    }

    // =========================================================================
    // Phase 3e: Frame Lifecycle + Presentation
    // =========================================================================

    /**
     * Begins a backend frame lifecycle scope.
     *
     * <p>In OpenGL this is a no-op and returns {@code -1}.
     * In Vulkan this acquires the next swapchain image and returns its index.
     */
    public static int beginFrame() {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        if (directVulkanBackend != null) {
            return directVulkanBackend.beginFrame();
        }
        return getBackend().beginFrame();
    }

    /**
     * Ends a backend frame lifecycle scope.
     *
     * <p>In OpenGL this is a no-op.
     * In Vulkan this presents the currently acquired swapchain image.
     */
    public static void endFrame() {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        if (directVulkanBackend != null) {
            directVulkanBackend.endFrame();
            return;
        }
        getBackend().endFrame();
    }

    /**
     * Presents a color render target view to the active backend's screen/swapchain.
     */
    public static void presentTextureToScreen(CommandContext ctx, GpuTextureView textureView) {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        if (directVulkanBackend != null) {
            directVulkanBackend.presentTextureToScreen(ctx, textureView);
            return;
        }
        getBackend().presentTextureToScreen(ctx, textureView);
    }

    // =========================================================================
    // Phase 3d: Command Buffer Lifecycle
    // =========================================================================

    /**
     * Begins a new command buffer for recording rendering commands.
     *
     * <p>In OpenGL: Returns the singleton immediate-mode context.
     * In Vulkan: Allocates and begins a new VkCommandBuffer.
     *
     * @return a CommandContext for recording commands
     */
    public static CommandContext beginCommandBuffer() {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        if (directVulkanBackend != null) {
            return directVulkanBackend.beginCommandBuffer();
        }
        return getBackend().beginCommandBuffer();
    }

    /**
     * Submits a completed command buffer for GPU execution.
     *
     * <p>In OpenGL: No-op — commands already executed immediately.
     * In Vulkan: Calls vkQueueSubmit.
     *
     * @param ctx the command context returned by {@link #beginCommandBuffer()}
     */
    public static void submitCommandBuffer(CommandContext ctx) {
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        if (directVulkanBackend != null) {
            directVulkanBackend.submitCommandBuffer(ctx);
            return;
        }
        getBackend().submitCommandBuffer(ctx);
    }

    /**
     * Registers the GlDevice with the active backend (OpenGL backend only).
     *
     * <p>This is called from GlDevice's constructor once the device is initialized.
     * It allows the OpenGLBackend to delegate device-level operations
     * (pipeline compilation, resource management) to the GlDevice.
     *
     * <p>Has no effect if the current backend is not an OpenGLBackend.
     */
    public static void registerDevice(net.blaze3d.opengl.GlDevice device) {
        GraphicsBackend b = getBackend();
        if (b instanceof net.vulkanic.backends.opengl.OpenGLBackend openGLBackend) {
            openGLBackend.setGlDevice(device);
        }
    }

    // =========================================================================
    // Phase 3b: Render Pass
    // =========================================================================

    /**
     * Begins a render pass that targets the given color attachment.
     *
     * <p>Dispatches to {@link GraphicsBackend#beginRenderPass(CommandContext, java.util.function.Supplier, VulkanicTextureView, java.util.OptionalInt)}.
     *
     * <p>Typical usage with try-with-resources:
     * <pre>
     * CommandContext ctx = VulkanicAPI.beginCommandBuffer();
     * try (VulkanicRenderPass pass = VulkanicAPI.beginRenderPass(
     *         ctx, () -> "terrain", colorView, OptionalInt.of(0))) {
     *     pass.setPipeline(pipeline);
     *     pass.setVertexBuffer(0, vbo);
     *     pass.draw(0, 36);
     * }
     * VulkanicAPI.submitCommandBuffer(ctx);
     * </pre>
     *
     * @param ctx         command context (from {@link #beginCommandBuffer()})
     * @param label       debug label for profiling (may be null supplier)
     * @param colorTarget color attachment texture view
     * @param clearColor  if present, ARGB clear color applied before the first draw
     * @return an active {@link VulkanicRenderPass}
     */
    public static VulkanicRenderPass beginRenderPass(CommandContext ctx,
            java.util.function.Supplier<String> label,
            VulkanicTextureView colorTarget, java.util.OptionalInt clearColor) {
        return beginRenderPass(ctx,
            VulkanicRenderPassDescriptor.color(label, colorTarget, clearColor));
    }

    /**
     * Begins a render pass that targets a color and optional depth attachment.
     *
     * @param ctx         command context (from {@link #beginCommandBuffer()})
     * @param label       debug label (may be null supplier)
     * @param colorTarget color attachment texture view
     * @param clearColor  if present, ARGB clear color applied before the first draw
     * @param depthTarget depth attachment texture view (may be null — depth not used)
     * @param clearDepth  if present, depth value to clear the depth attachment with
     * @return an active {@link VulkanicRenderPass}
     */
    public static VulkanicRenderPass beginRenderPass(CommandContext ctx,
            java.util.function.Supplier<String> label,
            VulkanicTextureView colorTarget, java.util.OptionalInt clearColor,
            @org.jetbrains.annotations.Nullable VulkanicTextureView depthTarget,
            java.util.OptionalDouble clearDepth) {
        return beginRenderPass(ctx,
            VulkanicRenderPassDescriptor.colorAndDepth(
                label, colorTarget, clearColor, depthTarget, clearDepth));
    }

    /**
     * Begins a render pass using the attachment contract of an existing framebuffer.
     */
    public static VulkanicRenderPass beginRenderPass(
        CommandContext ctx,
        java.util.function.Supplier<String> label,
        int framebuffer
    ) {
        return beginRenderPass(ctx, label, framebuffer, true);
    }

    /**
     * Begins a render pass using the attachment contract of an existing framebuffer.
     */
    public static VulkanicRenderPass beginRenderPass(
        CommandContext ctx,
        java.util.function.Supplier<String> label,
        int framebuffer,
        boolean hasDepthTexture
    ) {
        traceShaderInputParityOrdering(
            "pass-begin",
            "vulkanic-beginRenderPass-framebuffer",
            "label=" + shaderInputParitySanitizeLabel(shaderInputParitySupplierLabel(label))
                + "|target=framebuffer:" + framebuffer
                + "|depth=" + hasDepthTexture
        );
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        if (directVulkanBackend != null) {
            return directVulkanBackend.beginRenderPass(ctx, label, framebuffer, hasDepthTexture);
        }
        return getBackend().beginRenderPass(ctx, label, framebuffer, hasDepthTexture);
    }

    /**
     * Begins a render pass using an explicit multi-attachment render-target contract.
     */
    public static VulkanicRenderPass beginRenderPass(
        CommandContext ctx,
        VulkanicRenderTargetDescriptor descriptor
    ) {
        traceShaderInputParityOrdering(
            "pass-begin",
            "vulkanic-beginRenderPass-targetDescriptor",
            "target=" + shaderInputParitySanitizeLabel(descriptor.debugSignature())
        );
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        if (directVulkanBackend != null) {
            return directVulkanBackend.beginRenderPass(ctx, descriptor);
        }
        return getBackend().beginRenderPass(ctx, descriptor);
    }

    /**
     * Begins a render pass using backend-agnostic attachment load/store metadata.
     *
     * @param ctx command context (from {@link #beginCommandBuffer()})
     * @param descriptor render-pass descriptor (attachments + load/store/clear semantics)
     * @return an active {@link VulkanicRenderPass}
     */
    public static VulkanicRenderPass beginRenderPass(CommandContext ctx,
            VulkanicRenderPassDescriptor descriptor) {
        traceShaderInputParityOrdering(
            "pass-begin",
            "vulkanic-beginRenderPass-descriptor",
            "target=" + shaderInputParitySanitizeLabel(shaderInputParityRenderPassDescriptorSignature(descriptor))
        );
        VulkanBackend directVulkanBackend = directVulkanBackendForImplementedMethods();
        if (directVulkanBackend != null) {
            return directVulkanBackend.beginRenderPass(ctx, descriptor);
        }
        return getBackend().beginRenderPass(ctx, descriptor);
    }

    public static void traceShaderInputParityOrdering(String operation, String source, String detail) {
        if (!shouldTraceShaderInputParityLog()) {
            return;
        }
        String normalizedOperation = shaderInputParitySanitizeLabel(shaderInputParityValueOrUnknown(operation));
        String normalizedSource = shaderInputParitySanitizeLabel(shaderInputParityValueOrUnknown(source));
        String normalizedDetail = shaderInputParitySanitizeLabel(shaderInputParityValueOrUnknown(detail));
        long ordinal = SHADER_INPUT_PARITY_ORDERING_ORDINAL.incrementAndGet();
        String semanticContext = shaderInputParitySemanticDrawContextFields();
        String poseContext = shaderInputParityDeterministicContextFields();
        String orderKey = shaderInputParityHashString(normalizedOperation + "|" + normalizedSource + "|" + normalizedDetail + "|" + poseContext);
        LOGGER.info(
            "ShaderInputParityOrdering backend={} operation={} source={} orderOrdinal={} orderKey={} detail={} {} {}",
            getActiveBackendType().name().toLowerCase(Locale.ROOT),
            normalizedOperation,
            normalizedSource,
            ordinal,
            orderKey,
            normalizedDetail,
            semanticContext,
            poseContext
        );
    }

    private static String shaderInputParitySupplierLabel(@Nullable java.util.function.Supplier<String> label) {
        if (label == null) {
            return "unknown";
        }
        try {
            String value = label.get();
            return value == null || value.isBlank() ? "unknown" : value;
        } catch (Throwable throwable) {
            return "unavailable:" + throwable.getClass().getSimpleName();
        }
    }

    private static String shaderInputParityRenderPassDescriptorSignature(VulkanicRenderPassDescriptor descriptor) {
        VulkanicRenderPassDescriptor.ColorAttachment color = descriptor.colorAttachment();
        VulkanicRenderPassDescriptor.DepthAttachment depth = descriptor.depthAttachment();
        return "label=" + shaderInputParitySupplierLabel(descriptor.label())
            + "|colorLoad=" + color.loadOp()
            + "|colorStore=" + color.storeOp()
            + "|colorInitial=" + color.initialUsage()
            + "|colorPass=" + color.passUsage()
            + "|colorFinal=" + color.finalUsage()
            + "|colorClear=" + color.clearColor().isPresent()
            + "|depth=" + (depth != null)
            + (depth == null
                ? ""
                : "|depthLoad=" + depth.loadOp()
                    + "|depthStore=" + depth.storeOp()
                    + "|depthInitial=" + depth.initialUsage()
                    + "|depthPass=" + depth.passUsage()
                    + "|depthFinal=" + depth.finalUsage()
                    + "|depthClear=" + depth.clearDepth().isPresent());
    }
}
