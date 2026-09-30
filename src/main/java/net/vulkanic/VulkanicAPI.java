package net.vulkanic;

import net.blaze3d.ProjectionType;
import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.buffers.GpuBufferSlice;
import net.blaze3d.pipeline.CompiledRenderPipeline;
import net.blaze3d.pipeline.RenderPipeline;
import net.blaze3d.platform.GLX;
import net.blaze3d.platform.NativeImage;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.TimeSource.NanoTimeSource;
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Main entry point for the Vulkanic Graphics Abstraction Layer.
 * Provides a unified API for graphics operations that can be backed by different graphics APIs.
 */
public class VulkanicAPI {
    public record FramebufferProbeSnapshot(
        byte[] rgba,
        float[] depth,
        int readFramebuffer,
        int drawFramebuffer,
        int currentProgram,
        String viewport,
        boolean depthTest,
        boolean blend,
        boolean scissor
    ) {
        public FramebufferProbeSnapshot {
            rgba = java.util.Arrays.copyOf(rgba, rgba.length);
            depth = java.util.Arrays.copyOf(depth, depth.length);
        }
    }

    private static final String LWJGL_STACK_SIZE_PROPERTY = "org.lwjgl.system.stackSize";
    private static final org.slf4j.Logger LOGGER = net.logging.LogUtils.getLogger();
    private static final int VULKAN_LWJGL_STACK_SIZE_KB = 512;
    @Nullable
    private static GraphicsBackendType backendType;
    @Nullable
    private static final VulkanicCompatibilityState compatibilityState = new VulkanicCompatibilityState();

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
     * Selects the backend that Rust VulkanicGAL renders with. Java owns only
     * the GLFW window and a CPU-only semantic device; every frame goes through
     * the Rust whole-frame shell, on a Rust windowed Vulkan context or on the
     * borrowed GLFW OpenGL context.
     */
    public static synchronized void initialize(GraphicsBackendType selectedBackendType) {
        if (backendType != null) {
            return;
        }
        if (selectedBackendType == GraphicsBackendType.VULKAN) {
            ensureVulkanLwjglStackSize();
        }
        backendType = selectedBackendType;
    }

    /** Selects the backend from the {@code graphics_backend} option value. */
    public static synchronized void initializeFromOptionsValue(@Nullable String configuredValue) {
        initialize(backendTypeFromOptionsValue(configuredValue));
    }

    /**
     * Normalizes a backend option value from {@code options.txt}.
     *
     * <p>Accepted values are {@code opengl} and {@code vulkan} (case-insensitive).
     * Any missing/unknown value falls back to {@code opengl}.
     */
    public static String normalizeBackendOptionValue(@Nullable String configuredValue) {
        if (Boolean.getBoolean("mattmc.dev.rustGalVulkanWholeFrame")) {
            return "vulkan";
        }
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
    
    /** The selected backend; OpenGL until startup selects one. */
    public static GraphicsBackendType getActiveBackendType() {
        return backendType == null ? GraphicsBackendType.OPENGL : backendType;
    }

    /**
     * Presenter choice for the Rust route: a Rust-owned windowed Vulkan
     * context, or Rust VulkanicGAL on the borrowed GLFW OpenGL context.
     */
    public static boolean usesRustVulkanPresenter() {
        return backendType == GraphicsBackendType.VULKAN;
    }

    public static long prepareRendererBootstrapWindowHandle(long mainWindowHandle) {
        if (!usesRustVulkanPresenter()) {
            // Rust borrows this context and loads its own GL entry points.
            GLFW.glfwMakeContextCurrent(mainWindowHandle);
        }
        return mainWindowHandle;
    }

    public static GpuDevice createRendererDevice() {
        return new net.vulkanic.bridge.RustSemanticGpuDevice();
    }

    public static void onRendererDeviceInitialized(long mainWindowHandle) {
        if (mainWindowHandle != MemoryUtil.NULL) {
            GLFW.glfwShowWindow(mainWindowHandle);
            GLFW.glfwFocusWindow(mainWindowHandle);
        }
    }

    // Context operations
    
    // Convenience methods that delegate to the backend
    
    


    /**
     * Returns the framebuffer color-attachment enum for a zero-based attachment index.
     */
    public static int colorAttachment(int colorAttachmentIndex) {
        return GL_COLOR_ATTACHMENT0 + colorAttachmentIndex;
    }

    // Direct State Access buffer operations
    // CommandContext versions of DSA operations
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    

    
    
    
    
    

    public interface ShaderInputParityScope extends AutoCloseable {
        @Override
        void close();
    }

    
    
    
    
    
    
    
    

    /**
     * Executes queued fenced callbacks whose GPU fences have signaled.
     */
    public static void executePendingFenceTasks() {
        if (!PENDING_FENCED_TASKS.isEmpty()) {
            throw new IllegalStateException("Java GPU fenced callbacks were queued while Rust owns whole-frame Vulkan completion");
        }
        return;
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

    public static void assertOnRenderThread() {
        if (!isOnRenderThread()) {
            throw constructThreadException();
        }
    }

    private static IllegalStateException constructThreadException() {
        return new IllegalStateException("Rendersystem called from wrong thread");
    }

    public static void setDevice(GpuDevice gpuDevice) {
        assertOnRenderThread();
        if (!(gpuDevice instanceof net.vulkanic.bridge.RustSemanticGpuDevice)) {
            throw new IllegalStateException(
                "Selected Vulkan accepts only the Rust Vulkan semantic device; Java GPU devices are unavailable"
            );
        }
        device = gpuDevice;
    }

    public static void registerGlfwWindowHandleForVulkanSurface(long windowHandle) {
        if (windowHandle != MemoryUtil.NULL) {
            registeredGlfwWindowHandleForVulkanSurface = windowHandle;
        }
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

    /** Capability queries answered by the semantic device Rust renders behind. */
    public static java.util.List<String> getBackendOptionalFeatureNames() {
        // Crash reports can ask before the device exists.
        GpuDevice gpuDevice = tryGetDevice();
        return gpuDevice == null ? java.util.List.of() : gpuDevice.getOptionalFeatureNames();
    }

    public static int getBackendMaxTextureSize() {
        return getDevice().getMaxTextureSize();
    }

    public static GpuDevice.GpuDeviceInfo getBackendDeviceInfo() {
        return getDevice().getDeviceInfo();
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

	/** Releases Java-owned shared index buffers before Rust owns Vulkan frames. */
	public static void ensureRustSemanticRoute() {
		sharedSequential.releaseRustSemanticRoute();
		sharedSequentialQuad.releaseRustSemanticRoute();
		sharedSequentialLines.releaseRustSemanticRoute();
		projectionMatrixBuffer = null;
		savedProjectionMatrixBuffer = null;
		shaderFog = null;
		shaderLightDirections = null;
		globalSettingsUniform = null;
		outputColorTextureOverride = null;
		outputDepthTextureOverride = null;
		projectionMatrixLabels.clear();
		// Do not touch Iris classes on this boundary.  The selected Rust Vulkan
		// route intentionally skips Iris GPU lifecycle initialization; invoking
		// Iris cleanup here would trigger IrisRenderSystem class initialization,
		// whose static sampler state asks for Java Vulkan command context and
		// crashes before the first Rust-owned frame.  Rust-owned shader-pack/PBR
		// resources are released by their copied semantic generation instead.
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
				throw new IllegalStateException(
					"Java sequential index-buffer allocation is unavailable on the Rust Vulkan route"
				);
            }
        }

		private void releaseRustSemanticRoute() {
			if (this.buffer != null) {
				this.buffer.close();
				this.buffer = null;
			}
			this.indexCount = 0;
		}

        public VertexFormat.IndexType type() {
            return this.type;
        }

        public interface IndexGenerator {
            void accept(it.unimi.dsi.fastutil.ints.IntConsumer intConsumer, int i);
        }
    }

    public static void labelProjectionMatrix(GpuBufferSlice gpuBufferSlice, String label) {
        if (gpuBufferSlice == null || label == null || label.isBlank()) {
            return;
        }

        projectionMatrixLabels.put(gpuBufferSlice, label);
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

    public static void lineWidth(float f) {
        assertOnRenderThread();
        shaderLineWidth = f;
    }

    @Nullable
    public static GpuTextureView getOutputColorTextureOverride() {
        assertOnRenderThread();
        return outputColorTextureOverride;
    }

    @Nullable
    public static GpuTextureView getOutputDepthTextureOverride() {
        assertOnRenderThread();
        return outputDepthTextureOverride;
    }

    @Nullable
    public static GpuBufferSlice getShaderFog() {
        return shaderFog;
    }

    @Nullable
    public static GpuBufferSlice getShaderLights() {
        return shaderLightDirections;
    }

    @Nullable
    public static GpuBuffer getGlobalSettingsUniform() {
        return globalSettingsUniform;
    }

	/**
	 * The whole-frame route publishes only its non-rendering semantic startup
	 * device to shared Java diagnostics and allocation code.  Calling the Java
	 * Vulkan backend after Rust owns presentation would create a second route.
	 */
	@Nullable
	private static GpuDevice wholeFrameSemanticDevice() {
		return device;
	}

    

    
    
    // Additional methods for IrisRenderSystem migration
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    

    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    // DSA (Direct State Access) methods - ARB versions
    
    
    
    
    
    
    
    
    
    
    
    

    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    // GL-style wrapper methods for backward compatibility
    // These delegate to the abstracted methods above
    
    
    
    
    
    
    
    
    
    
    
    
    // GL43+ Vertex Attribute methods
    
    
    
    
    // VAO methods
    
    
    
    
    // GL.getCapabilities() and GLUtil support
    
    // Capability checking methods (to avoid casting GLCapabilities outside backends/opengl)
    
    
    
    // Additional GL query and state methods
    
    
    

    
    
    // CommandContext-aware methods for Batch 23
    
    // =========================================================================
    // Phase 3a: Buffer Lifecycle — high-level managed buffer operations
    // =========================================================================

    // =========================================================================
    // Phase 3b: Texture Lifecycle — high-level managed texture operations
    // =========================================================================

    // =========================================================================
    // Phase 3c: Pipeline Objects
    // =========================================================================

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

    // =========================================================================
    // Phase 3e: Frame Lifecycle + Presentation
    // =========================================================================

    // =========================================================================
    // Phase 3d: Command Buffer Lifecycle
    // =========================================================================

    // =========================================================================
    // Phase 3b: Render Pass
    // =========================================================================


}
