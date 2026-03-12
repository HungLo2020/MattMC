package net.vulkanic;

import net.blaze3d.ProjectionType;
import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.buffers.GpuBufferSlice;
import net.blaze3d.platform.GLX;
import net.blaze3d.systems.GpuDevice;
import net.blaze3d.systems.RenderPass;
import net.blaze3d.systems.ScissorState;
import net.blaze3d.textures.GpuTexture;
import net.blaze3d.textures.GpuTextureView;
import net.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.DynamicUniforms;
import net.minecraft.util.Mth;
import net.minecraft.util.TimeSource.NanoTimeSource;
import net.vulkanic.backends.opengl.OpenGLBackend;
import net.vulkanic.backends.vulkan.VulkanBackend;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.glfw.GLFWErrorCallbackI;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Main entry point for the Vulkanic Graphics Abstraction Layer.
 * Provides a unified API for graphics operations that can be backed by different graphics APIs.
 */
public class VulkanicAPI {
    private static GraphicsBackend backend;
    @Nullable
    private static VulkanBackend rawVulkanBackend;

    private static final boolean IS_MACOS = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("mac");
    @Nullable
    private static Thread renderThread;
    @Nullable
    private static GpuDevice device;
    private static final ThreadLocal<java.util.ArrayDeque<CommandContext>> CONTEXT_STACK = ThreadLocal.withInitial(java.util.ArrayDeque::new);
    private static ProjectionType projectionType = ProjectionType.PERSPECTIVE;
    private static ProjectionType savedProjectionType = ProjectionType.PERSPECTIVE;
    @Nullable
    private static GpuBufferSlice projectionMatrixBuffer;
    @Nullable
    private static GpuBufferSlice savedProjectionMatrixBuffer;
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
    public static final int GL_SAMPLER_1D_SHADOW = 0x8B61;
    public static final int GL_SAMPLER_2D_SHADOW = 0x8B62;
    public static final int GL_UNSIGNED_INT_SAMPLER_2D = 0x8DD2;
    public static final int GL_UNSIGNED_INT_SAMPLER_3D = 0x8DD3;
    
    // OpenGL Constants - Image Types (ARB_shader_image_load_store)
    public static final int GL_IMAGE_1D = 0x904C;
    public static final int GL_IMAGE_2D = 0x904D;
    public static final int GL_IMAGE_3D = 0x904E;
    public static final int GL_IMAGE_1D_ARRAY = 0x9052;
    public static final int GL_IMAGE_2D_ARRAY = 0x9053;
    public static final int GL_INT_IMAGE_1D = 0x9057;
    public static final int GL_INT_IMAGE_2D = 0x9058;
    public static final int GL_INT_IMAGE_3D = 0x9059;
    public static final int GL_UNSIGNED_INT_IMAGE_1D = 0x9062;
    public static final int GL_UNSIGNED_INT_IMAGE_2D = 0x9063;
    public static final int GL_UNSIGNED_INT_IMAGE_3D = 0x9064;
    
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
                    rawVulkanBackend = new VulkanBackend();
                    backend = createFailFastVulkanProxy(rawVulkanBackend);
            }

			readFramebufferBinding = 0;
			drawFramebufferBinding = 0;
        }
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

    /**
     * Gets the currently active backend identity.
     */
    public static GraphicsBackendType getActiveBackendType() {
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

    private static GraphicsBackend createFailFastVulkanProxy(VulkanBackend vulkanBackend) {
        java.util.Map<Method, java.util.Optional<Method>> methodCache = new ConcurrentHashMap<>();

        return (GraphicsBackend) Proxy.newProxyInstance(
            GraphicsBackend.class.getClassLoader(),
            new Class<?>[]{GraphicsBackend.class},
            (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return method.invoke(vulkanBackend, args);
                }

                java.util.Optional<Method> backendMethod = methodCache.computeIfAbsent(method, key -> {
                    try {
                        return java.util.Optional.of(VulkanBackend.class.getMethod(key.getName(), key.getParameterTypes()));
                    } catch (NoSuchMethodException ignored) {
                        return java.util.Optional.empty();
                    }
                });

                if (backendMethod.isEmpty()) {
                    throw new IllegalStateException(
                        "Vulkan backend selected but method '" + method.getName() + "' is not implemented natively; "
                            + "OpenGL fallback is intentionally blocked.");
                }

                try {
                    return backendMethod.get().invoke(vulkanBackend, args);
                } catch (InvocationTargetException exception) {
                    throw exception.getTargetException();
                }
            }
        );
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
        return getBackend().getCurrentCommandContext();
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
        getBackend().clearBuffers(ctx, mask);
    }

    public static void clearColorBuffer(CommandContext ctx) {
        clearBuffers(ctx, GL_COLOR_BUFFER_BIT);
    }

    public static void clearDepthBuffer(CommandContext ctx) {
        clearBuffers(ctx, GL_DEPTH_BUFFER_BIT);
    }

    public static void clearColorAndDepthBuffers(CommandContext ctx) {
        clearBuffers(ctx, GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    /**
     * Clears buffers and drains the error queue on macOS for compatibility with legacy GL behavior.
     */
    public static void clearBuffersWithMacosWorkaround(CommandContext ctx, int mask) {
        getBackend().clearBuffers(ctx, mask);
        if (IS_MACOS) {
            getBackend().getError(ctx);
        }
    }

    public static void clearColorBufferWithMacosWorkaround(CommandContext ctx) {
        clearBuffersWithMacosWorkaround(ctx, GL_COLOR_BUFFER_BIT);
    }

    public static void clearDepthBufferWithMacosWorkaround(CommandContext ctx) {
        clearBuffersWithMacosWorkaround(ctx, GL_DEPTH_BUFFER_BIT);
    }

    public static void clearColorAndDepthBuffersWithMacosWorkaround(CommandContext ctx) {
        clearBuffersWithMacosWorkaround(ctx, GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
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
        getBackend().bindTexture2D(ctx, textureId);
    }

    public static void bindTexture(CommandContext ctx, VulkanicTextureTarget target, int textureId) {
        getBackend().bindTexture(ctx, target, textureId);
    }
    
    public static void bindTexture(CommandContext ctx, int target, int textureId) {
        VulkanicTextureTarget.fromLegacyGlTarget(target)
            .ifPresentOrElse(
                typedTarget -> bindTexture(ctx, typedTarget, textureId),
                () -> getBackend().bindTexture(ctx, target, textureId)
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
        getBackend().bindSampler(ctx, unit, sampler);
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
                getBackend().bindFramebuffer(ctx, target, fbo);
                readFramebufferBinding = fbo;
            }
            return;
        }

        if (target == GL_DRAW_FRAMEBUFFER) {
            if (drawFramebufferBinding != fbo) {
                getBackend().bindFramebuffer(ctx, target, fbo);
                drawFramebufferBinding = fbo;
            }
            return;
        }

        if (target == GL_FRAMEBUFFER) {
            if (readFramebufferBinding != fbo || drawFramebufferBinding != fbo) {
                getBackend().bindFramebuffer(ctx, target, fbo);
                readFramebufferBinding = fbo;
                drawFramebufferBinding = fbo;
            }
            return;
        }

        getBackend().bindFramebuffer(ctx, target, fbo);
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
                () -> getBackend().bindBuffer(ctx, target, buffer)
            );
    }

    public static void bindBuffer(CommandContext ctx, VulkanicBufferTarget target, int buffer) {
        getBackend().bindBuffer(ctx, target, buffer);
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
                () -> getBackend().bindBufferBase(ctx, target, index, buffer)
            );
    }

    public static void bindBufferBase(CommandContext ctx, VulkanicBufferTarget target, int index, int buffer) {
        getBackend().bindBufferBase(ctx, target.toLegacyGlTarget(), index, buffer);
    }
    
    /**
     * Sets the active texture unit.
     * 
     * @param ctx Command context for recording this command
     * @param unit The texture unit to activate
     */
    public static void setActiveTextureUnit(CommandContext ctx, int unit) {
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
                        () -> getBackend().setTextureParameter(ctx, target, pname, param)
                    ),
                () -> getBackend().setTextureParameter(ctx, target, pname, param)
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
                () -> getBackend().setTextureParameter(ctx, target, pname.toLegacyGlPName(), param)
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
        getBackend().setTextureParameter(ctx, target, pname, param);
    }

    public static void setTextureParameter(
        CommandContext ctx,
        VulkanicTextureTarget target,
        VulkanicTextureParameterName pname,
        VulkanicTextureParameterValue param
    ) {
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
        return getBackend().createBufferDSA(ctx);
    }
    
    public static void namedBufferDataDSA(CommandContext ctx, int buffer, long size, int usage) {
        getBackend().namedBufferDataDSA(ctx, buffer, size, usage);
    }
    
    public static void namedBufferDataDSA(CommandContext ctx, int buffer, java.nio.ByteBuffer data, int usage) {
        getBackend().namedBufferDataDSA(ctx, buffer, data, usage);
    }
    
    public static void namedBufferSubDataDSA(CommandContext ctx, int buffer, long offset, java.nio.ByteBuffer data) {
        getBackend().namedBufferSubDataDSA(ctx, buffer, offset, data);
    }
    
    public static void namedBufferStorageDSA(CommandContext ctx, int buffer, long size, int flags) {
        getBackend().namedBufferStorageDSA(ctx, buffer, size, flags);
    }
    
    public static void namedBufferStorageDSA(CommandContext ctx, int buffer, java.nio.ByteBuffer data, int flags) {
        getBackend().namedBufferStorageDSA(ctx, buffer, data, flags);
    }
    
    public static java.nio.ByteBuffer mapNamedBufferRangeDSA(CommandContext ctx, int buffer, long offset, long length, int access) {
        return getBackend().mapNamedBufferRangeDSA(ctx, buffer, offset, length, access);
    }
    
    // CommandContext versions of DSA operations
    /**
     * Unmaps a previously mapped buffer using Direct State Access (DSA).
     * @param ctx Command context for recording this command
     * @param buffer The buffer object to unmap
     */
    public static void unmapNamedBufferDSA(CommandContext ctx, int buffer) {
        getBackend().unmapNamedBufferDSA(ctx, buffer);
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
        getBackend().copyNamedBufferSubDataDSA(ctx, readBuffer, writeBuffer, readOffset, writeOffset, size);
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
                () -> getBackend().drawArrays(ctx, mode, first, count)
            );
    }

    /**
     * Draws array primitives using a backend-neutral primitive mode.
     */
    public static void drawArrays(CommandContext ctx, VulkanicPrimitiveMode mode, int first, int count) {
        getBackend().drawArrays(ctx, mode.toGlModeConstant(), first, count);
    }
    
    public static void drawElements(CommandContext ctx, int mode, int count, int type, long indices) {
        java.util.Optional<VulkanicPrimitiveMode> typedMode = VulkanicPrimitiveMode.fromLegacyGlConstant(mode);
        java.util.Optional<VulkanicIndexType> typedIndexType = VulkanicIndexType.fromLegacyGlConstant(type);
        if (typedMode.isPresent() && typedIndexType.isPresent()) {
            drawElements(ctx, typedMode.get(), count, typedIndexType.get(), indices);
            return;
        }

        getBackend().drawElements(ctx, mode, count, type, indices);
    }

    /**
     * Draws indexed primitives using a backend-agnostic index type.
     */
    public static void drawElements(CommandContext ctx, int mode, int count, VulkanicIndexType indexType, long indices) {
        VulkanicPrimitiveMode.fromLegacyGlConstant(mode)
            .ifPresentOrElse(
                typedMode -> drawElements(ctx, typedMode, count, indexType, indices),
                () -> getBackend().drawElements(ctx, mode, count, indexType.toGlTypeConstant(), indices)
            );
    }

    /**
     * Draws indexed primitives using a backend-neutral primitive mode with legacy index-type constant.
     */
    public static void drawElements(CommandContext ctx, VulkanicPrimitiveMode mode, int count, int type, long indices) {
        VulkanicIndexType.fromLegacyGlConstant(type)
            .ifPresentOrElse(
                typedIndexType -> drawElements(ctx, mode, count, typedIndexType, indices),
                () -> getBackend().drawElements(ctx, mode.toGlModeConstant(), count, type, indices)
            );
    }

    /**
     * Draws indexed primitives using backend-neutral primitive and index types.
     */
    public static void drawElements(CommandContext ctx, VulkanicPrimitiveMode mode, int count, VulkanicIndexType indexType, long indices) {
        getBackend().drawElements(ctx, mode.toGlModeConstant(), count, indexType.toGlTypeConstant(), indices);
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
        getBackend().uploadTexture2D(ctx, target, level, internalFormat, width, height, border, format, type, pixels);
    }

    /**
     * Uploads a 2D texture image to the currently bound 2D texture target.
     */
    public static void uploadTexture2D(CommandContext ctx, int level, int internalFormat, int width, int height,
                                       int border, int format, int type, java.nio.ByteBuffer pixels) {
        getBackend().uploadTexture2D(ctx, GL_TEXTURE_2D, level, internalFormat, width, height, border, format, type, pixels);
    }
    
    public static void uploadTexture2DSubImage(CommandContext ctx, int target, int level, int xOffset, int yOffset, 
                                                int width, int height, int format, int type, long pixels) {
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
        getBackend().uploadTexture2DSubImage(ctx, GL_TEXTURE_2D, level, xOffset, yOffset, width, height, format, type, pixels);
    }
    
    public static void uploadTexture2DSubImage(CommandContext ctx, int target, int level, int xOffset, int yOffset, 
                                                int width, int height, int format, int type, java.nio.ByteBuffer pixels) {
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
    
    public static void compileShader(CommandContext ctx, int shader) {
        getBackend().compileShader(ctx, shader);
    }
    
    public static int createShaderProgram(CommandContext ctx) {
        return getBackend().createShaderProgram(ctx);
    }
    
    
    public static void deleteShader(CommandContext ctx, int shader) {
        getBackend().deleteShader(ctx, shader);
    }
    
    
    
    
    public static void deleteProgram(CommandContext ctx, int program) {
        getBackend().deleteProgram(ctx, program);
    }
    
    
    public static void attachShader(CommandContext ctx, int program, int shader) {
        getBackend().attachShader(ctx, program, shader);
    }
    
    public static void detachShader(CommandContext ctx, int program, int shader) {
        getBackend().detachShader(ctx, program, shader);
    }
    
    public static void linkProgram(CommandContext ctx, int program) {
        getBackend().linkProgram(ctx, program);
    }
    
    public static int getProgramParameter(CommandContext ctx, int program, int pname) {
        java.util.Optional<VulkanicProgramParameterName> typedPName = VulkanicProgramParameterName.fromLegacyGlPName(pname);
        if (typedPName.isPresent()) {
            return getProgramParameter(ctx, program, typedPName.get());
        }
        return getBackend().getProgramParameter(ctx, program, pname);
    }

    public static int getProgramParameter(CommandContext ctx, int program, VulkanicProgramParameterName pname) {
        return getBackend().getProgramParameter(ctx, program, pname.toLegacyGlPName());
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
    
    public static int getShaderParameter(CommandContext ctx, int shader, int pname) {
        java.util.Optional<VulkanicShaderParameterName> typedPName = VulkanicShaderParameterName.fromLegacyGlPName(pname);
        if (typedPName.isPresent()) {
            return getShaderParameter(ctx, shader, typedPName.get());
        }
        return getBackend().getShaderParameter(ctx, shader, pname);
    }

    public static int getShaderParameter(CommandContext ctx, int shader, VulkanicShaderParameterName pname) {
        return getBackend().getShaderParameter(ctx, shader, pname.toLegacyGlPName());
    }

    /**
     * Returns true when a shader currently reports COMPILE_STATUS success.
     */
    public static boolean isShaderCompileSuccessful(CommandContext ctx, int shader) {
        return isLegacyGlBooleanTrue(getShaderParameter(ctx, shader, VulkanicShaderParameterName.COMPILE_STATUS));
    }
    
    public static String getProgramInfoLog(CommandContext ctx, int program) {
        return getBackend().getProgramInfoLog(ctx, program);
    }
    
    
    
    
    
    
    public static String getShaderInfoLog(CommandContext ctx, int shader) {
        return getBackend().getShaderInfoLog(ctx, shader);
    }
    
    public static int getUniformLocation(CommandContext ctx, int program, CharSequence name) {
        return getBackend().getUniformLocation(ctx, program, name);
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
        }

        return location;
    }
    
    public static int getAttributeLocation(CommandContext ctx, int program, CharSequence name) {
        return getBackend().getAttributeLocation(ctx, program, name);
    }
    
    public static void setUniform1i(CommandContext ctx, int location, int value) {
        getBackend().setUniform1i(ctx, location, value);
    }
    
    public static void setUniform1f(CommandContext ctx, int location, float value) {
        getBackend().setUniform1f(ctx, location, value);
    }
    
    public static void setUniform2f(CommandContext ctx, int location, float v0, float v1) {
        getBackend().setUniform2f(ctx, location, v0, v1);
    }
    
    public static void setUniform3i(CommandContext ctx, int location, int v0, int v1, int v2) {
        getBackend().setUniform3i(ctx, location, v0, v1, v2);
    }
    
    public static void setUniform4f(CommandContext ctx, int location, float v0, float v1, float v2, float v3) {
        getBackend().setUniform4f(ctx, location, v0, v1, v2, v3);
    }
    
    public static void setUniform4i(CommandContext ctx, int location, int v0, int v1, int v2, int v3) {
        getBackend().setUniform4i(ctx, location, v0, v1, v2, v3);
    }
    
    public static void setUniformMatrix3fv(CommandContext ctx, int location, boolean transpose, java.nio.FloatBuffer matrix) {
        getBackend().setUniformMatrix3fv(ctx, location, transpose, matrix);
    }
    
    public static void setUniformMatrix3fv(CommandContext ctx, int location, boolean transpose, float[] matrix) {
        getBackend().setUniformMatrix3fv(ctx, location, transpose, matrix);
    }
    
    public static void setUniformMatrix4fv(CommandContext ctx, int location, boolean transpose, java.nio.FloatBuffer matrix) {
        getBackend().setUniformMatrix4fv(ctx, location, transpose, matrix);
    }
    
    public static void setUniformMatrix4fv(CommandContext ctx, int location, boolean transpose, float[] matrix) {
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
        getBackend().setVertexAttribDivisor(ctx, index, divisor);
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
        double targetTime = lastDrawTime + 1.0 / fpsLimit;

        double currentTime;
        for (currentTime = GLFW.glfwGetTime(); currentTime < targetTime; currentTime = GLFW.glfwGetTime()) {
            GLFW.glfwWaitEventsTimeout(targetTime - currentTime);
        }

        lastDrawTime = currentTime;
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

        return getBackend().resolveTextureHandle(getCommandContext(), target);
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
        return getBackend().resolveFramebufferForTextures(getCommandContext(), colorTarget, depthTarget);
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
    
    public static void uploadShaderSource(CommandContext ctx, int shader, long pointerBufferAddress, int stringCount, long lengthsPointer) {
        getBackend().uploadShaderSource(ctx, shader, pointerBufferAddress, stringCount, lengthsPointer);
    }
    
    public static void uniformBlockBinding(CommandContext ctx, int program, int uniformBlockIndex, int uniformBlockBinding) {
        getBackend().uniformBlockBinding(ctx, program, uniformBlockIndex, uniformBlockBinding);
    }
    
    public static String retrieveActiveUniformBlockName(CommandContext ctx, int program, int uniformBlockIndex) {
        return getBackend().retrieveActiveUniformBlockName(ctx, program, uniformBlockIndex);
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
        getBackend().drawIndexedInstancedBaseVertex(ctx, mode, count, type, indices, instanceCount, baseVertex);
    }

    /**
     * Renders indexed primitives with instancing and a base vertex using a backend-neutral primitive mode.
     */
    public static void drawIndexedInstancedBaseVertex(CommandContext ctx, VulkanicPrimitiveMode mode, int count, int type, long indices, int instanceCount, int baseVertex) {
        VulkanicIndexType.fromLegacyGlConstant(type)
            .ifPresentOrElse(
                typedIndexType -> drawIndexedInstancedBaseVertex(ctx, mode, count, typedIndexType, indices, instanceCount, baseVertex),
                () -> getBackend().drawIndexedInstancedBaseVertex(ctx, mode.toGlModeConstant(), count, type, indices, instanceCount, baseVertex)
            );
    }

    /**
     * Renders indexed primitives with instancing and a base vertex using a backend-agnostic index type.
     */
    public static void drawIndexedInstancedBaseVertex(CommandContext ctx, int mode, int count, VulkanicIndexType indexType, long indices, int instanceCount, int baseVertex) {
        getBackend().drawIndexedInstancedBaseVertex(ctx, mode, count, indexType.toGlTypeConstant(), indices, instanceCount, baseVertex);
    }

    /**
     * Renders indexed primitives with instancing and a base vertex using backend-neutral primitive and index types.
     */
    public static void drawIndexedInstancedBaseVertex(CommandContext ctx, VulkanicPrimitiveMode mode, int count, VulkanicIndexType indexType, long indices, int instanceCount, int baseVertex) {
        getBackend().drawIndexedInstancedBaseVertex(ctx, mode.toGlModeConstant(), count, indexType.toGlTypeConstant(), indices, instanceCount, baseVertex);
    }
    
    /**
     * Renders indexed primitives with a base vertex offset.
     * @param ctx Command context
     */
    public static void drawIndexedBaseVertex(CommandContext ctx, int mode, int count, int type, long indices, int baseVertex) {
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
        getBackend().drawIndexedBaseVertex(ctx, mode, count, indexType.toGlTypeConstant(), indices, baseVertex);
    }

    /**
     * Renders indexed primitives with a base vertex offset using backend-neutral primitive and index types.
     */
    public static void drawIndexedBaseVertex(CommandContext ctx, VulkanicPrimitiveMode mode, int count, VulkanicIndexType indexType, long indices, int baseVertex) {
        getBackend().drawIndexedBaseVertex(ctx, mode.toGlModeConstant(), count, indexType.toGlTypeConstant(), indices, baseVertex);
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

        getBackend().drawIndexedInstanced(ctx, mode, count, type, indices, instanceCount);
    }

    /**
     * Renders indexed primitives with instancing using a backend-agnostic index type.
     */
    public static void drawIndexedInstanced(CommandContext ctx, int mode, int count, VulkanicIndexType indexType, long indices, int instanceCount) {
        VulkanicPrimitiveMode.fromLegacyGlConstant(mode)
            .ifPresentOrElse(
                typedMode -> drawIndexedInstanced(ctx, typedMode, count, indexType, indices, instanceCount),
                () -> getBackend().drawIndexedInstanced(ctx, mode, count, indexType.toGlTypeConstant(), indices, instanceCount)
            );
    }

    /**
     * Renders indexed primitives with instancing using backend-neutral primitive and index types.
     */
    public static void drawIndexedInstanced(CommandContext ctx, VulkanicPrimitiveMode mode, int count, VulkanicIndexType indexType, long indices, int instanceCount) {
        getBackend().drawIndexedInstanced(ctx, mode.toGlModeConstant(), count, indexType.toGlTypeConstant(), indices, instanceCount);
    }
    
    /**
     * Renders primitives using array data with instancing.
     * @param ctx Command context
     */
    public static void drawArraysInstanced(CommandContext ctx, int mode, int first, int count, int instanceCount) {
        getBackend().drawArraysInstanced(ctx, mode, first, count, instanceCount);
    }

    /**
     * Renders primitives using array data with instancing via backend-neutral primitive mode.
     */
    public static void drawArraysInstanced(CommandContext ctx, VulkanicPrimitiveMode mode, int first, int count, int instanceCount) {
        getBackend().drawArraysInstanced(ctx, mode.toGlModeConstant(), first, count, instanceCount);
    }
    
    /**
     * Binds a range of a buffer to a uniform buffer binding point.
     * @param ctx Command context
     */
    public static void bindUniformBufferRange(CommandContext ctx, int target, int index, int buffer, long offset, long size) {
        getBackend().bindUniformBufferRange(ctx, target, index, buffer, offset, size);
    }

    /**
     * Binds a range of a buffer to a uniform buffer binding point.
     */
    public static void bindUniformBufferRange(CommandContext ctx, int index, int buffer, long offset, long size) {
        getBackend().bindUniformBufferRange(ctx, GL_UNIFORM_BUFFER, index, buffer, offset, size);
    }
    
    /**
     * Attaches a buffer object to a texture buffer.
     * @param ctx Command context
     */
    public static void texBuffer(CommandContext ctx, int target, int internalFormat, int buffer) {
        getBackend().texBuffer(ctx, target, internalFormat, buffer);
    }

    /**
     * Attaches a buffer object to a texture buffer target.
     */
    public static void bindTextureBufferData(CommandContext ctx, int internalFormat, int buffer) {
        getBackend().texBuffer(ctx, GL_TEXTURE_BUFFER, internalFormat, buffer);
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
    
    
    public static String getActiveUniform(CommandContext ctx, int program, int index, int size, java.nio.IntBuffer type, java.nio.IntBuffer name) {
        return getBackend().getActiveUniform(ctx, program, index, size, type, name);
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
        getBackend().bindSamplers(ctx, first, samplers);
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
        getBackend().bindTextureUnit(ctx, unit, texture);
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
                () -> getBackend().setStencilFunc(ctx, func, ref, mask)
            );
    }

    public static void setStencilFunc(CommandContext ctx, VulkanicStencilCompareOp func, int ref, int mask) {
        getBackend().setStencilFunc(ctx, func, ref, mask);
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

        getBackend().setStencilFuncSeparate(ctx, face, func, ref, mask);
    }

    /**
     * Sets the stencil test function for a specific face using backend-neutral semantics.
     */
    public static void setStencilFuncSeparate(CommandContext ctx, VulkanicStencilFace face, VulkanicStencilCompareOp func, int ref, int mask) {
        getBackend().setStencilFuncSeparate(ctx, face, func, ref, mask);
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

        getBackend().setStencilOp(ctx, stencilFailOp, depthFailOp, depthPassOp);
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
        getBackend().setStencilOp(ctx, stencilFailOp, depthFailOp, depthPassOp);
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

        getBackend().setStencilOpSeparate(ctx, face, stencilFailOp, depthFailOp, depthPassOp);
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
        getBackend().setStencilOpSeparate(ctx, face, stencilFailOp, depthFailOp, depthPassOp);
    }

    /**
     * Sets the stencil write mask.
     */
    public static void setStencilWriteMask(CommandContext ctx, int mask) {
        getBackend().setStencilWriteMask(ctx, mask);
    }

    /**
     * Sets the stencil write mask for a specific face.
     */
    public static void setStencilWriteMaskSeparate(CommandContext ctx, int face, int mask) {
        VulkanicStencilFace.fromLegacyGlConstant(face)
            .ifPresentOrElse(
                typedFace -> setStencilWriteMaskSeparate(ctx, typedFace, mask),
                () -> getBackend().setStencilWriteMaskSeparate(ctx, face, mask)
            );
    }

    /**
     * Sets the stencil write mask for a specific face using backend-neutral semantics.
     */
    public static void setStencilWriteMaskSeparate(CommandContext ctx, VulkanicStencilFace face, int mask) {
        getBackend().setStencilWriteMaskSeparate(ctx, face, mask);
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

    // =========================================================================
    // Phase 3c: Pipeline Objects
    // =========================================================================

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
     * Convenience overload: creates a pipeline directly from a Blaze3D RenderPipeline.
     *
     * @param pipeline the RenderPipeline to compile
     * @return a PipelineHandle for the compiled pipeline
     */
    public static PipelineHandle createPipeline(net.blaze3d.pipeline.RenderPipeline pipeline) {
        return getBackend().createPipeline(PipelineDescriptor.fromRenderPipeline(pipeline));
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
        getBackend().bindPipelineResources(ctx, pipeline, descriptor, bindings);
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
     * Begins a render pass using backend-agnostic attachment load/store metadata.
     *
     * @param ctx command context (from {@link #beginCommandBuffer()})
     * @param descriptor render-pass descriptor (attachments + load/store/clear semantics)
     * @return an active {@link VulkanicRenderPass}
     */
    public static VulkanicRenderPass beginRenderPass(CommandContext ctx,
            VulkanicRenderPassDescriptor descriptor) {
        return getBackend().beginRenderPass(ctx, descriptor);
    }
}
