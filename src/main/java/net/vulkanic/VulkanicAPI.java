package net.vulkanic;

import net.vulkanic.backends.opengl.OpenGLBackend;
import net.vulkanic.backends.opengl.OpenGLCommandContext;

/**
 * Main entry point for the Vulkanic Graphics Abstraction Layer.
 * Provides a unified API for graphics operations that can be backed by different graphics APIs.
 */
public class VulkanicAPI {
    private static GraphicsBackend backend;
    private static final CommandContext CTX = OpenGLCommandContext.IMMEDIATE;
    
    // Functional interfaces for debug callbacks
    @FunctionalInterface
    public interface DebugMessageCallback {
        void invoke(int source, int type, int id, int severity, String message);
    }
    
    @FunctionalInterface
    public interface DebugMessageCallbackARB {
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
    public static final int GL_MAX_TEXTURE_IMAGE_UNITS = 0x8872;
    public static final int GL_MAX_DRAW_BUFFERS = 0x8824;
    public static final int GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS = 0x90DD;
    
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
    public static final int GL_TEXTURE_3D = 0x806F;
    public static final int GL_TEXTURE_RECTANGLE = 0x84F5;
    
    // OpenGL Constants - Query Names
    public static final int GL_SHADING_LANGUAGE_VERSION = 0x8B8C;
    public static final int GL_EXTENSIONS = 0x1F03;
    public static final int GL_NUM_EXTENSIONS = 0x821D;
    
    // OpenGL Constants - Debug Capabilities
    public static final int GL_DEBUG_OUTPUT_SYNCHRONOUS = 0x8242;
    public static final int GL_CONTEXT_FLAGS = 0x821E;
    public static final int GL_DEBUG_OUTPUT = 0x92E0;
    
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
    
    // OpenGL Constants - Culling
    public static final int GL_CULL_FACE = 0x0B44;
    
    // OpenGL Constants - Tests
    public static final int GL_DEPTH_TEST = 0x0B71;
    public static final int GL_SCISSOR_TEST = 0x0C11;
    
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
    public static final int GL_UNPACK_ROW_LENGTH = 0x0CF2;
    public static final int GL_UNPACK_SKIP_ROWS = 0x0CF3;
    public static final int GL_UNPACK_SKIP_PIXELS = 0x0CF4;
    public static final int GL_UNPACK_ALIGNMENT = 0x0CF5;
    
    // OpenGL Constants - Clear Bits
    public static final int GL_COLOR_BUFFER_BIT = 0x00004000;
    public static final int GL_DEPTH_BUFFER_BIT = 0x00000100;
    
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
     * Backend types supported by Vulkanic.
     */
    public enum BackendType {
        OPENGL,
        VULKAN  // Future implementation
    }
    
    /**
     * Initialize the Vulkanic API with the default backend (OpenGL).
     */
    public static void initialize() {
        initialize(BackendType.OPENGL);
    }
    
    /**
     * Initialize the Vulkanic API with a specific backend.
     * @param backendType The backend type to use
     */
    public static synchronized void initialize(BackendType backendType) {
        if (backend == null) {
            switch (backendType) {
                case OPENGL:
                    backend = new OpenGLBackend();
                    break;
                case VULKAN:
                    throw new UnsupportedOperationException("Vulkan backend not yet implemented");
            }
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
     *     CommandContext ctx = VulkanicAPI.getImmediateContext(); // or beginCommandBuffer() for Vulkan
     *     
     *     // Reuse for multiple operations
     *     VulkanicAPI.setDynamicViewport(ctx, ...);
     *     VulkanicAPI.setDynamicScissor(ctx, ...);
     *     VulkanicAPI.bindPipeline(ctx, ...);
     *     VulkanicAPI.drawIndexed(ctx, ...);
     *     </pre>
     * </li>
     * <li><b>Low-level utilities (GlStateManager):</b> Calling getImmediateContext() internally
     *     is acceptable since they're OpenGL-specific and called from framework code we don't control.</li>
     * </ul>
     * 
     * <p>This is a convenience method for migrating code to use CommandContext parameters
     * without changing the immediate execution model during the transition period.</p>
     * 
     * @return Immediate-mode command context (OpenGL singleton)
     */
    public static CommandContext getImmediateContext() {
        // For now, we only have OpenGL backend, so return OpenGL immediate context
        // When Vulkan backend is added, this would check backend type
        return OpenGLCommandContext.IMMEDIATE;
    }
    
    // Context operations
    /**
     * Gets the current graphics context (platform-specific).
     * On Windows, this returns the WGL context handle.
     * Returns 0 or NULL if no context is current.
     */
    @Deprecated
    public static long getGraphicsContext() {
        return getBackend().getGraphicsContext();
    }
    
    // Convenience methods that delegate to the backend
    
    @Deprecated
    public static void bindTexture(int textureId) {
        getBackend().bindTexture(textureId);
    }
    
    @Deprecated
    public static void bindTexture(int target, int textureId) {
        getBackend().bindTexture(target, textureId);
    }
    
    @Deprecated
    public static void generateMipmap(int target) {
        getBackend().generateMipmap(target);
    }
    
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
    
    @Deprecated
    public static void setColorWriteMask(boolean r, boolean g, boolean b, boolean a) {
        getBackend().setColorWriteMask(r, g, b, a);
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
     * Clears the specified buffers to their clear values.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.clear(ctx, GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
     * </pre>
     * 
     * In OpenGL: Maps to glClear()
     * In Vulkan: Maps to vkCmdClearAttachments() or part of vkCmdBeginRenderPass()
     * 
     * @param ctx Command context for recording this command
     * @param mask Bitwise OR of buffer masks (e.g., GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT)
     */
    public static void clear(CommandContext ctx, int mask) {
        getBackend().clear(ctx, mask);
    }
    
    /**
     * Draws primitives using vertex array data.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.drawArrays(ctx, GL_TRIANGLES, 0, vertexCount);
     * </pre>
     * 
     * In OpenGL: Maps to glDrawArrays()
     * In Vulkan: Maps to vkCmdDraw()
     * 
     * @param ctx Command context for recording this command
     * @param mode Primitive topology (e.g., GL_TRIANGLES, GL_LINES)
     * @param first Starting vertex index in the vertex buffer
     * @param count Number of vertices to draw
     */
    public static void drawArrays(CommandContext ctx, int mode, int first, int count) {
        getBackend().drawArrays(ctx, mode, first, count);
    }
    
    /**
     * Draws indexed primitives using vertex array data and an index buffer.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.drawElements(ctx, GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0);
     * </pre>
     * 
     * In OpenGL: Maps to glDrawElements()
     * In Vulkan: Maps to vkCmdDrawIndexed()
     * 
     * @param ctx Command context for recording this command
     * @param mode Primitive topology (e.g., GL_TRIANGLES, GL_LINES)
     * @param count Number of indices to draw
     * @param type Data type of indices (e.g., GL_UNSIGNED_INT, GL_UNSIGNED_SHORT)
     * @param indices Offset in bytes from the start of the index buffer, or pointer to index data
     */
    public static void drawElements(CommandContext ctx, int mode, int count, int type, long indices) {
        getBackend().drawElements(ctx, mode, count, type, indices);
    }
    
    /**
     * Binds a shader program for subsequent rendering operations.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.bindShaderProgram(ctx, programId);
     * </pre>
     * 
     * In OpenGL: Maps to glUseProgram()
     * In Vulkan: Handled by vkCmdBindPipeline() with pre-compiled shader modules
     * 
     * @param ctx Command context for recording this command
     * @param programId The shader program ID to bind
     */
    public static void bindShaderProgram(CommandContext ctx, int programId) {
        getBackend().bindShaderProgram(ctx, programId);
    }
    
    /**
     * Sets the depth write mask (whether depth values are written to the depth buffer).
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.setDepthWriteMask(ctx, true); // Enable depth writes
     * </pre>
     * 
     * In OpenGL: Maps to glDepthMask()
     * In Vulkan: Part of pipeline state (depthWriteEnable)
     * 
     * @param ctx Command context for recording this command
     * @param enabled true to enable depth writes, false to disable
     */
    public static void setDepthWriteMask(CommandContext ctx, boolean enabled) {
        getBackend().setDepthWriteMask(ctx, enabled);
    }
    
    /**
     * Sets the color write mask (which color channels can be written to the framebuffer).
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.setColorWriteMask(ctx, true, true, true, false); // RGB only
     * </pre>
     * 
     * In OpenGL: Maps to glColorMask()
     * In Vulkan: Part of pipeline state (colorWriteMask)
     * 
     * @param ctx Command context for recording this command
     * @param r true to enable red channel writes
     * @param g true to enable green channel writes
     * @param b true to enable blue channel writes
     * @param a true to enable alpha channel writes
     */
    public static void setColorWriteMask(CommandContext ctx, boolean r, boolean g, boolean b, boolean a) {
        getBackend().setColorWriteMask(ctx, r, g, b, a);
    }
    
    /**
     * Sets the depth comparison function.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.setDepthFunc(ctx, GL_LESS); // Standard depth testing
     * </pre>
     * 
     * In OpenGL: Maps to glDepthFunc()
     * In Vulkan: Part of pipeline state (depthCompareOp)
     * 
     * @param ctx Command context for recording this command
     * @param func The depth comparison function (e.g., GL_LESS, GL_LEQUAL, GL_ALWAYS)
     */
    public static void setDepthFunc(CommandContext ctx, int func) {
        getBackend().setDepthFunc(ctx, func);
    }
    
    /**
     * Sets the blend function for color blending.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.setBlendFunc(ctx, GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO);
     * </pre>
     * 
     * In OpenGL: Maps to glBlendFuncSeparate()
     * In Vulkan: Part of pipeline state (VkPipelineColorBlendAttachmentState)
     * 
     * @param ctx Command context for recording this command
     * @param srcRgb Source RGB blend factor (e.g., GL_SRC_ALPHA)
     * @param dstRgb Destination RGB blend factor (e.g., GL_ONE_MINUS_SRC_ALPHA)
     * @param srcAlpha Source alpha blend factor
     * @param dstAlpha Destination alpha blend factor
     */
    public static void setBlendFunc(CommandContext ctx, int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        getBackend().setBlendFunc(ctx, srcRgb, dstRgb, srcAlpha, dstAlpha);
    }
    
    /**
     * Binds a buffer object to a target binding point.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.bindBuffer(ctx, GL_ARRAY_BUFFER, vertexBufferId);
     * </pre>
     * 
     * In OpenGL: Maps to glBindBuffer()
     * In Vulkan: Buffers are bound via vkCmdBindVertexBuffers() or descriptor sets
     * 
     * @param ctx Command context for recording this command
     * @param target The buffer binding target (e.g., GL_ARRAY_BUFFER, GL_ELEMENT_ARRAY_BUFFER)
     * @param buffer The buffer object ID to bind
     */
    public static void bindBuffer(CommandContext ctx, int target, int buffer) {
        getBackend().bindBuffer(ctx, target, buffer);
    }
    
    /**
     * Enables blending for rendering operations.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.enableBlend(ctx);
     * </pre>
     * 
     * In OpenGL: Maps to glEnable(GL_BLEND)
     * In Vulkan: Part of pipeline state (blendEnable)
     * 
     * @param ctx Command context for recording this command
     */
    public static void enableBlend(CommandContext ctx) {
        getBackend().enableBlend(ctx);
    }
    
    /**
     * Disables blending for rendering operations.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.disableBlend(ctx);
     * </pre>
     * 
     * In OpenGL: Maps to glDisable(GL_BLEND)
     * In Vulkan: Part of pipeline state (blendEnable)
     * 
     * @param ctx Command context for recording this command
     */
    public static void disableBlend(CommandContext ctx) {
        getBackend().disableBlend(ctx);
    }
    
    /**
     * Enables a generic OpenGL capability.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.enable(ctx, GL_DEPTH_TEST);
     * </pre>
     * 
     * In OpenGL: Maps to glEnable(cap)
     * In Vulkan: Most capabilities map to pipeline state or dynamic state
     * 
     * @param ctx Command context for recording this command
     * @param cap The capability to enable (e.g., GL_DEPTH_TEST, GL_CULL_FACE)
     */
    public static void enable(CommandContext ctx, int cap) {
        getBackend().enable(ctx, cap);
    }
    
    /**
     * Disables a generic OpenGL capability.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.disable(ctx, GL_DEPTH_TEST);
     * </pre>
     * 
     * In OpenGL: Maps to glDisable(cap)
     * In Vulkan: Most capabilities map to pipeline state or dynamic state
     * 
     * @param ctx Command context for recording this command
     * @param cap The capability to disable (e.g., GL_DEPTH_TEST, GL_CULL_FACE)
     */
    public static void disable(CommandContext ctx, int cap) {
        getBackend().disable(ctx, cap);
    }
    
    /**
     * Sets the active texture unit for subsequent texture operations.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.activateTextureUnit(ctx, GL_TEXTURE0);
     * </pre>
     * 
     * In OpenGL: Maps to glActiveTexture(unit)
     * In Vulkan: Texture units are abstracted through descriptor sets
     * 
     * @param ctx Command context for recording this command
     * @param unit The texture unit to activate (e.g., GL_TEXTURE0)
     */
    public static void activateTextureUnit(CommandContext ctx, int unit) {
        getBackend().activateTextureUnit(ctx, unit);
    }
    
    /**
     * Generates mipmaps for a texture target.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.generateMipmap(ctx, GL_TEXTURE_2D);
     * </pre>
     * 
     * In OpenGL: Maps to glGenerateMipmap(target)
     * In Vulkan: Handled through image layout transitions and vkCmdBlitImage
     * 
     * @param ctx Command context for recording this command
     * @param target The texture target (e.g., GL_TEXTURE_2D)
     */
    public static void generateMipmap(CommandContext ctx, int target) {
        getBackend().generateMipmap(ctx, target);
    }
    
    /**
     * Binds a texture to the currently active texture unit.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.bindTexture(ctx, textureId);
     * </pre>
     * 
     * In OpenGL: Maps to glBindTexture(GL_TEXTURE_2D, textureId)
     * In Vulkan: Textures are bound through descriptor sets
     * 
     * @param ctx Command context for recording this command
     * @param textureId The texture ID to bind
     */
    public static void bindTexture(CommandContext ctx, int textureId) {
        getBackend().bindTexture(ctx, textureId);
    }
    
    /**
     * Binds a texture to a specific target on the currently active texture unit.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.bindTexture(ctx, GL_TEXTURE_2D, textureId);
     * </pre>
     * 
     * In OpenGL: Maps to glBindTexture(target, textureId)
     * In Vulkan: Textures are bound through descriptor sets
     * 
     * @param ctx Command context for recording this command
     * @param target The texture target (e.g., GL_TEXTURE_2D)
     * @param textureId The texture ID to bind
     */
    public static void bindTexture(CommandContext ctx, int target, int textureId) {
        getBackend().bindTexture(ctx, target, textureId);
    }
    
    /**
     * Sets pixel storage modes for texture upload operations.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.setPixelStoreMode(ctx, GL_UNPACK_ALIGNMENT, 1);
     * </pre>
     * 
     * In OpenGL: Maps to glPixelStorei(pname, value)
     * In Vulkan: Handled through buffer copy parameters
     * 
     * @param ctx Command context for recording this command
     * @param pname The pixel storage parameter name
     * @param value The value to set
     */
    public static void setPixelStoreMode(CommandContext ctx, int pname, int value) {
        getBackend().setPixelStoreMode(ctx, pname, value);
    }
    
    /**
     * Binds a framebuffer object to a framebuffer target.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.attachFramebuffer(ctx, GL_FRAMEBUFFER, fboId);
     * </pre>
     * 
     * In OpenGL: Maps to glBindFramebuffer(target, fbo)
     * In Vulkan: Framebuffers are bound through render pass begin
     * 
     * @param ctx Command context for recording this command
     * @param target The framebuffer target
     * @param fbo The framebuffer object ID to bind (0 for default framebuffer)
     */
    public static void attachFramebuffer(CommandContext ctx, int target, int fbo) {
        getBackend().attachFramebuffer(ctx, target, fbo);
    }
    
    /**
     * Attaches a texture to a framebuffer attachment point.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.attachTextureToFramebuffer(ctx, GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, 
     *                                            GL_TEXTURE_2D, textureId, 0);
     * </pre>
     * 
     * In OpenGL: Maps to glFramebufferTexture2D(target, attachment, textarget, texture, level)
     * In Vulkan: Textures are attached during framebuffer creation
     * 
     * @param ctx Command context for recording this command
     * @param target The framebuffer target (e.g., GL_FRAMEBUFFER)
     * @param attachment The attachment point (e.g., GL_COLOR_ATTACHMENT0)
     * @param textarget The texture target (e.g., GL_TEXTURE_2D)
     * @param texture The texture ID to attach
     * @param level The mipmap level to attach
     */
    public static void attachTextureToFramebuffer(CommandContext ctx, int target, int attachment, int textarget, int texture, int level) {
        getBackend().attachTextureToFramebuffer(ctx, target, attachment, textarget, texture, level);
    }
    
    /**
     * Sets a texture parameter.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.configureTextureParameter(ctx, GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
     * </pre>
     * 
     * In OpenGL: Maps to glTexParameteri(target, pname, param)
     * In Vulkan: Texture parameters are set through sampler objects
     * 
     * @param ctx Command context for recording this command
     * @param target The texture target (e.g., GL_TEXTURE_2D)
     * @param pname The parameter name (e.g., GL_TEXTURE_MIN_FILTER)
     * @param param The parameter value
     */
    public static void configureTextureParameter(CommandContext ctx, int target, int pname, int param) {
        getBackend().configureTextureParameter(ctx, target, pname, param);
    }
    
    /**
     * Deletes a texture object.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.removeTexture(ctx, textureId);
     * </pre>
     * 
     * In OpenGL: Maps to glDeleteTextures()
     * In Vulkan: Maps to vkDestroyImage/vkDestroyImageView
     * 
     * @param ctx Command context for recording this command
     * @param texture The texture ID to delete
     */
    public static void removeTexture(CommandContext ctx, int texture) {
        getBackend().removeTexture(ctx, texture);
    }
    
    /**
     * Sets the polygon rasterization mode.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.configurePolygonMode(ctx, GL_FRONT_AND_BACK, GL_LINE);
     * </pre>
     * 
     * In OpenGL: Maps to glPolygonMode(face, mode)
     * In Vulkan: Part of pipeline state (polygonMode in VkPipelineRasterizationStateCreateInfo)
     * 
     * @param ctx Command context for recording this command
     * @param face Which faces to apply to (e.g., GL_FRONT_AND_BACK)
     * @param mode The rasterization mode (e.g., GL_FILL, GL_LINE, GL_POINT)
     */
    public static void configurePolygonMode(CommandContext ctx, int face, int mode) {
        getBackend().configurePolygonMode(ctx, face, mode);
    }
    
    /**
     * Creates a new texture object.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     int textureId = VulkanicAPI.createTexture(ctx);
     * </pre>
     * 
     * In OpenGL: Maps to glGenTextures()
     * In Vulkan: Maps to vkCreateImage() and vkCreateImageView()
     * 
     * @param ctx Command context for recording this command
     * @return The newly created texture ID
     */
    public static int createTexture(CommandContext ctx) {
        return getBackend().createTexture(ctx);
    }
    
    /**
     * Sets the polygon offset for depth value calculations.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.configurePolygonOffset(ctx, 1.0f, 1.0f);
     * </pre>
     * 
     * In OpenGL: Maps to glPolygonOffset(factor, units)
     * In Vulkan: Part of pipeline state (depthBias* in VkPipelineRasterizationStateCreateInfo)
     * 
     * @param ctx Command context for recording this command
     * @param factor Scale factor for depth slope
     * @param units Constant depth offset value
     */
    public static void configurePolygonOffset(CommandContext ctx, float factor, float units) {
        getBackend().configurePolygonOffset(ctx, factor, units);
    }
    
    /**
     * Sets the logical operation for framebuffer blending.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.configureLogicOp(ctx, GL_XOR);
     * </pre>
     * 
     * In OpenGL: Maps to glLogicOp(opcode)
     * In Vulkan: Part of pipeline state (logicOp in VkPipelineColorBlendStateCreateInfo)
     * 
     * @param ctx Command context for recording this command
     * @param opcode The logical operation code (e.g., GL_COPY, GL_XOR)
     */
    public static void configureLogicOp(CommandContext ctx, int opcode) {
        getBackend().configureLogicOp(ctx, opcode);
    }
    
    /**
     * Sets the clear value for the depth buffer.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.setClearDepthValue(ctx, 1.0);
     * </pre>
     * 
     * In OpenGL: Maps to glClearDepth(depth)
     * In Vulkan: Clear values are specified in vkCmdBeginRenderPass()
     * 
     * @param ctx Command context for recording this command
     * @param depth The depth clear value (typically 1.0 for far plane)
     */
    public static void setClearDepthValue(CommandContext ctx, double depth) {
        getBackend().setClearDepthValue(ctx, depth);
    }
    
    /**
     * Sets the clear value for the color buffer.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.setClearColorValue(ctx, 0.0f, 0.0f, 0.0f, 1.0f);
     * </pre>
     * 
     * In OpenGL: Maps to glClearColor(r, g, b, a)
     * In Vulkan: Clear values are specified in vkCmdBeginRenderPass()
     * 
     * @param ctx Command context for recording this command
     * @param red Red component (0.0 to 1.0)
     * @param green Green component (0.0 to 1.0)
     * @param blue Blue component (0.0 to 1.0)
     * @param alpha Alpha component (0.0 to 1.0)
     */
    public static void setClearColorValue(CommandContext ctx, float red, float green, float blue, float alpha) {
        getBackend().setClearColorValue(ctx, red, green, blue, alpha);
    }
    
    /**
     * Selects which color buffer to draw to.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.selectDrawBuffer(ctx, GL_BACK);
     * </pre>
     * 
     * In OpenGL: Maps to glDrawBuffer(mode)
     * In Vulkan: Specified in render pass creation
     * 
     * @param ctx Command context for recording this command
     * @param mode The draw buffer mode (e.g., GL_BACK, GL_FRONT)
     */
    public static void selectDrawBuffer(CommandContext ctx, int mode) {
        getBackend().selectDrawBuffer(ctx, mode);
    }
    
    /**
     * Allocates a new buffer object.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     int bufferID = VulkanicAPI.allocateBufferObject(ctx);
     * </pre>
     * 
     * In OpenGL: Maps to glGenBuffers()
     * In Vulkan: Maps to vkCreateBuffer()
     * 
     * @param ctx Command context for recording this command
     * @return The newly created buffer object ID
     */
    public static int allocateBufferObject(CommandContext ctx) {
        return getBackend().allocateBufferObject(ctx);
    }
    
    /**
     * Releases a buffer object.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.releaseBufferObject(ctx, bufferID);
     * </pre>
     * 
     * In OpenGL: Maps to glDeleteBuffers()
     * In Vulkan: Maps to vkDestroyBuffer()
     * 
     * @param ctx Command context for recording this command
     * @param buf The buffer object ID to release
     */
    public static void releaseBufferObject(CommandContext ctx, int buf) {
        getBackend().releaseBufferObject(ctx, buf);
    }
    
    /**
     * Creates a new vertex array object (VAO).
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     int vaoID = VulkanicAPI.createVertexArrayObject(ctx);
     * </pre>
     * 
     * In OpenGL: Maps to glGenVertexArrays()
     * In Vulkan: No direct equivalent (state is part of pipeline)
     * 
     * @param ctx Command context for recording this command
     * @return The newly created vertex array object ID
     */
    public static int createVertexArrayObject(CommandContext ctx) {
        return getBackend().createVertexArrayObject(ctx);
    }
    
    /**
     * Generates a new framebuffer object.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     int fboID = VulkanicAPI.generateFramebufferObject(ctx);
     * </pre>
     * 
     * In OpenGL: Maps to glGenFramebuffers()
     * In Vulkan: Maps to vkCreateFramebuffer()
     * 
     * @param ctx Command context for recording this command
     * @return The newly created framebuffer object ID
     */
    public static int generateFramebufferObject(CommandContext ctx) {
        return getBackend().generateFramebufferObject(ctx);
    }
    
    /**
     * Destroys a framebuffer object.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.destroyFramebufferObject(ctx, fboID);
     * </pre>
     * 
     * In OpenGL: Maps to glDeleteFramebuffers()
     * In Vulkan: Maps to vkDestroyFramebuffer()
     * 
     * @param ctx Command context for recording this command
     * @param fbo The framebuffer object ID to destroy
     */
    public static void destroyFramebufferObject(CommandContext ctx, int fbo) {
        getBackend().destroyFramebufferObject(ctx, fbo);
    }
    
    /**
     * Binds a vertex array object (VAO).
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.selectVertexArray(ctx, vaoID);
     * </pre>
     * 
     * In OpenGL: Maps to glBindVertexArray()
     * In Vulkan: No direct equivalent (state is part of pipeline)
     * 
     * @param ctx Command context for recording this command
     * @param vao The vertex array object ID to bind
     */
    public static void selectVertexArray(CommandContext ctx, int vao) {
        getBackend().selectVertexArray(ctx, vao);
    }
    
    /**
     * Fills a buffer with data.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     ByteBuffer data = ...;
     *     VulkanicAPI.fillBufferWithData(ctx, GL_ARRAY_BUFFER, data, GL_STATIC_DRAW);
     * </pre>
     * 
     * In OpenGL: Maps to glBufferData()
     * In Vulkan: Maps to vkCmdUpdateBuffer() or memory mapping
     * 
     * @param ctx Command context for recording this command
     * @param tgt The buffer binding target (e.g., GL_ARRAY_BUFFER)
     * @param dat The data to upload
     * @param usg Usage hint for the buffer (e.g., GL_STATIC_DRAW)
     */
    public static void fillBufferWithData(CommandContext ctx, int tgt, java.nio.ByteBuffer dat, int usg) {
        getBackend().fillBufferWithData(ctx, tgt, dat, usg);
    }
    
    /**
     * Allocates buffer storage with a specified size.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.fillBufferWithSize(ctx, GL_ARRAY_BUFFER, 1024, GL_DYNAMIC_DRAW);
     * </pre>
     * 
     * In OpenGL: Maps to glBufferData() with null data
     * In Vulkan: Maps to vkCreateBuffer() with appropriate size
     * 
     * @param ctx Command context for recording this command
     * @param tgt The buffer binding target (e.g., GL_ARRAY_BUFFER)
     * @param sz The size in bytes to allocate
     * @param usg Usage hint for the buffer (e.g., GL_DYNAMIC_DRAW)
     */
    public static void fillBufferWithSize(CommandContext ctx, int tgt, long sz, int usg) {
        getBackend().fillBufferWithSize(ctx, tgt, sz, usg);
    }
    
    /**
     * Checks for graphics API errors.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     int error = VulkanicAPI.checkForErrors(ctx);
     *     if (error != 0) {
     *         // Handle error
     *     }
     * </pre>
     * 
     * In OpenGL: Maps to glGetError()
     * In Vulkan: Maps to validation layer queries
     * 
     * @param ctx Command context for recording this command
     * @return The error code, or NO_ERROR (0) if no error occurred
     */
    public static int checkForErrors(CommandContext ctx) {
        return getBackend().checkForErrors(ctx);
    }
    
    /**
     * Updates a subset of buffer data.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     ByteBuffer updateData = ...;
     *     VulkanicAPI.fillBufferSubregion(ctx, GL_ARRAY_BUFFER, 256, updateData);
     * </pre>
     * 
     * In OpenGL: Maps to glBufferSubData()
     * In Vulkan: Maps to vkCmdUpdateBuffer() or staging buffer copy
     * 
     * @param ctx Command context for recording this command
     * @param tgt The buffer binding target (e.g., GL_ARRAY_BUFFER)
     * @param off Offset in bytes from the start of the buffer
     * @param dat The data to upload
     */
    public static void fillBufferSubregion(CommandContext ctx, int tgt, long off, java.nio.ByteBuffer dat) {
        getBackend().fillBufferSubregion(ctx, tgt, off, dat);
    }
    
    /**
     * Maps a region of buffer memory for CPU access.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     ByteBuffer mapped = VulkanicAPI.mapBufferRegion(ctx, GL_ARRAY_BUFFER, 0, 1024, GL_MAP_WRITE_BIT);
     *     // Write to mapped buffer
     *     VulkanicAPI.unmapBufferData(ctx, GL_ARRAY_BUFFER);
     * </pre>
     * 
     * In OpenGL: Maps to glMapBufferRange()
     * In Vulkan: Maps to vkMapMemory()
     * 
     * @param ctx Command context for recording this command
     * @param tgt The buffer binding target (e.g., GL_ARRAY_BUFFER)
     * @param off Offset in bytes from the start of the buffer
     * @param len Length in bytes of the region to map
     * @param acc Access flags (e.g., GL_MAP_READ_BIT, GL_MAP_WRITE_BIT)
     * @return A ByteBuffer providing access to the mapped memory region
     */
    public static java.nio.ByteBuffer mapBufferRegion(CommandContext ctx, int tgt, int off, int len, int acc) {
        return getBackend().mapBufferRegion(ctx, tgt, off, len, acc);
    }
    
    /**
     * Unmaps previously mapped buffer memory.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     ByteBuffer mapped = VulkanicAPI.mapBufferRegion(ctx, GL_ARRAY_BUFFER, 0, 1024, GL_MAP_WRITE_BIT);
     *     // Write to mapped buffer
     *     VulkanicAPI.unmapBufferData(ctx, GL_ARRAY_BUFFER);
     * </pre>
     * 
     * In OpenGL: Maps to glUnmapBuffer()
     * In Vulkan: Maps to vkUnmapMemory()
     * 
     * @param ctx Command context for recording this command
     * @param tgt The buffer binding target (e.g., GL_ARRAY_BUFFER)
     */
    public static void unmapBufferData(CommandContext ctx, int tgt) {
        getBackend().unmapBufferData(ctx, tgt);
    }
    
    /**
     * Copies a rectangular region from one framebuffer to another (blit operation).
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     // Copy entire framebuffer content with scaling
     *     VulkanicAPI.copyFramebufferRegion(ctx, 0, 0, 1920, 1080, 0, 0, 1280, 720, 
     *                                       GL_COLOR_BUFFER_BIT, GL_LINEAR);
     * </pre>
     * 
     * In OpenGL: Maps to glBlitFramebuffer()
     * In Vulkan: Maps to vkCmdBlitImage()
     * 
     * @param ctx Command context for recording this command
     * @param srcX0 Source rectangle minimum X coordinate
     * @param srcY0 Source rectangle minimum Y coordinate
     * @param srcX1 Source rectangle maximum X coordinate
     * @param srcY1 Source rectangle maximum Y coordinate
     * @param dstX0 Destination rectangle minimum X coordinate
     * @param dstY0 Destination rectangle minimum Y coordinate
     * @param dstX1 Destination rectangle maximum X coordinate
     * @param dstY1 Destination rectangle maximum Y coordinate
     * @param msk Bit mask indicating which buffers to copy (GL_COLOR_BUFFER_BIT, etc.)
     * @param flt Filter mode for scaling (GL_NEAREST or GL_LINEAR)
     */
    public static void copyFramebufferRegion(CommandContext ctx, int srcX0, int srcY0, int srcX1, int srcY1, 
                                             int dstX0, int dstY0, int dstX1, int dstY1, int msk, int flt) {
        getBackend().copyFramebufferRegion(ctx, srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, msk, flt);
    }
    
    /**
     * Uploads 2D texture image data.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     ByteBuffer pixels = ...;
     *     VulkanicAPI.transferTexture2DImage(ctx, GL_TEXTURE_2D, 0, GL_RGBA8, 
     *                                        256, 256, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
     * </pre>
     * 
     * In OpenGL: Maps to glTexImage2D()
     * In Vulkan: Maps to vkCmdCopyBufferToImage() with staging buffer
     * 
     * @param ctx Command context for recording this command
     * @param tgt Texture target (e.g., GL_TEXTURE_2D)
     * @param lvl Mipmap level (0 for base level)
     * @param intfmt Internal format (e.g., GL_RGBA8)
     * @param w Width in pixels
     * @param h Height in pixels
     * @param bdr Border width (must be 0 in modern OpenGL)
     * @param fmt Pixel data format (e.g., GL_RGBA)
     * @param typ Pixel data type (e.g., GL_UNSIGNED_BYTE)
     * @param pix Buffer containing pixel data, or null to allocate without initializing
     */
    public static void transferTexture2DImage(CommandContext ctx, int tgt, int lvl, int intfmt, int w, int h, 
                                              int bdr, int fmt, int typ, java.nio.ByteBuffer pix) {
        getBackend().transferTexture2DImage(ctx, tgt, lvl, intfmt, w, h, bdr, fmt, typ, pix);
    }
    
    /**
     * Creates a shader object of the specified type (CommandContext-aware).
     * 
     * Example usage:
     * <pre>{@code
     * CommandContext ctx = OpenGLCommandContext.IMMEDIATE;
     * int vertexShader = VulkanicAPI.constructShaderObject(ctx, GL_VERTEX_SHADER);
     * int fragmentShader = VulkanicAPI.constructShaderObject(ctx, GL_FRAGMENT_SHADER);
     * }</pre>
     * 
     * @param ctx Command context for shader creation
     * @param shaderType Type of shader (e.g., GL_VERTEX_SHADER, GL_FRAGMENT_SHADER)
     * @return Shader object ID/handle
     */
    public static int constructShaderObject(CommandContext ctx, int shaderType) {
        return getBackend().constructShaderObject(ctx, shaderType);
    }
    
    /**
     * Deletes a shader object and frees its resources (CommandContext-aware).
     * 
     * Example usage:
     * <pre>{@code
     * CommandContext ctx = OpenGLCommandContext.IMMEDIATE;
     * VulkanicAPI.disposeShaderObject(ctx, shaderId);
     * }</pre>
     * 
     * @param ctx Command context for resource tracking
     * @param shader Shader object ID to delete
     */
    public static void disposeShaderObject(CommandContext ctx, int shader) {
        getBackend().disposeShaderObject(ctx, shader);
    }
    
    /**
     * Compiles the shader source code (CommandContext-aware).
     * 
     * Example usage:
     * <pre>{@code
     * CommandContext ctx = OpenGLCommandContext.IMMEDIATE;
     * VulkanicAPI.compileShaderSource(ctx, shaderId);
     * }</pre>
     * 
     * @param ctx Command context for compilation pipeline
     * @param shader Shader object ID to compile
     */
    public static void compileShaderSource(CommandContext ctx, int shader) {
        getBackend().compileShaderSource(ctx, shader);
    }
    
    /**
     * Creates a program object for linking shaders (CommandContext-aware).
     * 
     * Example usage:
     * <pre>{@code
     * CommandContext ctx = OpenGLCommandContext.IMMEDIATE;
     * int program = VulkanicAPI.constructProgramObject(ctx);
     * }</pre>
     * 
     * @param ctx Command context for pipeline creation
     * @return Program/Pipeline object ID/handle
     */
    public static int constructProgramObject(CommandContext ctx) {
        return getBackend().constructProgramObject(ctx);
    }
    
    /**
     * Deletes a program object and frees its resources (CommandContext-aware).
     * 
     * Example usage:
     * <pre>{@code
     * CommandContext ctx = OpenGLCommandContext.IMMEDIATE;
     * VulkanicAPI.disposeProgramObject(ctx, programId);
     * }</pre>
     * 
     * @param ctx Command context for resource tracking
     * @param program Program/Pipeline object ID to delete
     */
    public static void disposeProgramObject(CommandContext ctx, int program) {
        getBackend().disposeProgramObject(ctx, program);
    }
    
    @Deprecated
    public static void setPixelStoreMode(int pname, int value) {
        getBackend().setPixelStoreMode(pname, value);
    }
    
    @Deprecated
    public static void attachFramebuffer(int target, int fbo) {
        getBackend().attachFramebuffer(target, fbo);
    }
    
    @Deprecated
    public static void attachTextureToFramebuffer(int target, int attachment, int textarget, int texture, int level) {
        getBackend().attachTextureToFramebuffer(target, attachment, textarget, texture, level);
    }
    
    @Deprecated
    public static void attachBuffer(int target, int buffer) {
        getBackend().attachBuffer(target, buffer);
    }
    
    // Direct State Access buffer operations
    @Deprecated
    public static int createBufferDSA() {
        return getBackend().createBufferDSA();
    }
    
    @Deprecated
    public static void namedBufferDataDSA(int buffer, long size, int usage) {
        getBackend().namedBufferDataDSA(buffer, size, usage);
    }
    
    @Deprecated
    public static void namedBufferDataDSA(int buffer, java.nio.ByteBuffer data, int usage) {
        getBackend().namedBufferDataDSA(buffer, data, usage);
    }
    
    @Deprecated
    public static void namedBufferSubDataDSA(int buffer, long offset, java.nio.ByteBuffer data) {
        getBackend().namedBufferSubDataDSA(buffer, offset, data);
    }
    
    @Deprecated
    public static void namedBufferStorageDSA(int buffer, long size, int flags) {
        getBackend().namedBufferStorageDSA(buffer, size, flags);
    }
    
    @Deprecated
    public static void namedBufferStorageDSA(int buffer, java.nio.ByteBuffer data, int flags) {
        getBackend().namedBufferStorageDSA(buffer, data, flags);
    }
    
    @Deprecated
    public static java.nio.ByteBuffer mapNamedBufferRangeDSA(int buffer, long offset, long length, int access) {
        return getBackend().mapNamedBufferRangeDSA(buffer, offset, length, access);
    }
    
    @Deprecated
    public static void unmapNamedBufferDSA(int buffer) {
        getBackend().unmapNamedBufferDSA(buffer);
    }
    
    @Deprecated
    public static void flushMappedNamedBufferRangeDSA(int buffer, long offset, long length) {
        getBackend().flushMappedNamedBufferRangeDSA(buffer, offset, length);
    }
    
    @Deprecated
    public static void copyNamedBufferSubDataDSA(int readBuffer, int writeBuffer, long readOffset, long writeOffset, long size) {
        getBackend().copyNamedBufferSubDataDSA(readBuffer, writeBuffer, readOffset, writeOffset, size);
    }
    
    // Direct State Access framebuffer operations
    @Deprecated
    public static int createFramebufferDSA() {
        return getBackend().createFramebufferDSA();
    }
    
    @Deprecated
    public static void namedFramebufferTextureDSA(int framebuffer, int attachment, int texture, int level) {
        getBackend().namedFramebufferTextureDSA(framebuffer, attachment, texture, level);
    }
    
    @Deprecated
    public static void blitNamedFramebufferDSA(int readFramebuffer, int drawFramebuffer, int srcX0, int srcY0, int srcX1, int srcY1,
                                                int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        getBackend().blitNamedFramebufferDSA(readFramebuffer, drawFramebuffer, srcX0, srcY0, srcX1, srcY1,
                                              dstX0, dstY0, dstX1, dstY1, mask, filter);
    }
    
    @Deprecated
    public static void activateTextureUnit(int unit) {
        getBackend().activateTextureUnit(unit);
    }
    
    @Deprecated
    public static void configureTextureParameter(int target, int pname, int param) {
        getBackend().configureTextureParameter(target, pname, param);
    }
    
    @Deprecated
    public static int createTexture() {
        return getBackend().createTexture();
    }
    
    @Deprecated
    public static void removeTexture(int texture) {
        getBackend().removeTexture(texture);
    }
    
    @Deprecated
    public static void configurePolygonMode(int face, int mode) {
        getBackend().configurePolygonMode(face, mode);
    }
    
    @Deprecated
    public static void configurePolygonOffset(float factor, float units) {
        getBackend().configurePolygonOffset(factor, units);
    }
    
    @Deprecated
    public static void configureLogicOp(int opcode) {
        getBackend().configureLogicOp(opcode);
    }
    
    @Deprecated
    public static void configureBlendFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        getBackend().configureBlendFunc(srcRgb, dstRgb, srcAlpha, dstAlpha);
    }
    
    @Deprecated
    public static int checkForErrors() {
        return getBackend().checkForErrors();
    }
    
    @Deprecated
    public static void transferTexture2DImage(int tgt, int lvl, int intfmt, int w, int h, int bdr, int fmt, int typ, java.nio.ByteBuffer pix) {
        getBackend().transferTexture2DImage(tgt, lvl, intfmt, w, h, bdr, fmt, typ, pix);
    }
    
    @Deprecated
    public static void transferTexture2DSubregion(int tgt, int lvl, int xoff, int yoff, int w, int h, int fmt, int typ, long pix) {
        getBackend().transferTexture2DSubregion(tgt, lvl, xoff, yoff, w, h, fmt, typ, pix);
    }
    
    @Deprecated
    public static void transferTexture2DSubregionBuf(int tgt, int lvl, int xoff, int yoff, int w, int h, int fmt, int typ, java.nio.ByteBuffer pix) {
        getBackend().transferTexture2DSubregionBuf(tgt, lvl, xoff, yoff, w, h, fmt, typ, pix);
    }
    
    @Deprecated
    public static int allocateBufferObject() {
        return getBackend().allocateBufferObject();
    }
    
    @Deprecated
    public static void releaseBufferObject(int buf) {
        getBackend().releaseBufferObject(buf);
    }
    
    @Deprecated
    public static void fillBufferWithData(int tgt, java.nio.ByteBuffer dat, int usg) {
        getBackend().fillBufferWithData(tgt, dat, usg);
    }
    
    @Deprecated
    public static void fillBufferWithSize(int tgt, long sz, int usg) {
        getBackend().fillBufferWithSize(tgt, sz, usg);
    }
    
    @Deprecated
    public static void fillBufferSubregion(int tgt, long off, java.nio.ByteBuffer dat) {
        getBackend().fillBufferSubregion(tgt, off, dat);
    }
    
    @Deprecated
    public static int createVertexArrayObject() {
        return getBackend().createVertexArrayObject();
    }
    
    @Deprecated
    public static void selectVertexArray(int vao) {
        getBackend().selectVertexArray(vao);
    }
    
    @Deprecated
    public static java.nio.ByteBuffer mapBufferRegion(int tgt, int off, int len, int acc) {
        return getBackend().mapBufferRegion(tgt, off, len, acc);
    }
    
    @Deprecated
    public static void unmapBufferData(int tgt) {
        getBackend().unmapBufferData(tgt);
    }
    
    @Deprecated
    public static int generateFramebufferObject() {
        return getBackend().generateFramebufferObject();
    }
    
    @Deprecated
    public static void destroyFramebufferObject(int fbo) {
        getBackend().destroyFramebufferObject(fbo);
    }
    
    @Deprecated
    public static void copyFramebufferRegion(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int msk, int flt) {
        getBackend().copyFramebufferRegion(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, msk, flt);
    }
    
    @Deprecated
    public static void linkProgramBinary(int program) {
        getBackend().linkProgramBinary(program);
    }
    
    @Deprecated
    public static void attachShaderToProgram(int program, int shader) {
        getBackend().attachShaderToProgram(program, shader);
    }
    
    @Deprecated
    public static int queryProgramParameter(int program, int pname) {
        return getBackend().queryProgramParameter(program, pname);
    }
    
    @Deprecated
    public static int queryShaderParameter(int shader, int pname) {
        return getBackend().queryShaderParameter(shader, pname);
    }
    
    @Deprecated
    public static void configureVertexAttribute(int index, int size, int type, boolean normalized, int stride, long pointer) {
        getBackend().configureVertexAttribute(index, size, type, normalized, stride, pointer);
    }
    
    @Deprecated
    public static void configureVertexAttributeInteger(int index, int size, int type, int stride, long pointer) {
        getBackend().configureVertexAttributeInteger(index, size, type, stride, pointer);
    }
    
    @Deprecated
    public static void activateVertexAttribute(int index) {
        getBackend().activateVertexAttribute(index);
    }
    
    @Deprecated
    public static void deactivateVertexAttribute(int index) {
        getBackend().deactivateVertexAttribute(index);
    }
    
    @Deprecated
    public static void setVertexAttribDivisor(int index, int divisor) {
        getBackend().setVertexAttribDivisor(index, divisor);
    }
    
    @Deprecated
    public static String retrieveProgramInfoLog(int program) {
        return getBackend().retrieveProgramInfoLog(program);
    }
    
    @Deprecated
    public static String retrieveShaderInfoLog(int shader) {
        return getBackend().retrieveShaderInfoLog(shader);
    }
    
    @Deprecated
    public static int locateUniformVariable(int program, CharSequence name) {
        return getBackend().locateUniformVariable(program, name);
    }
    
    @Deprecated
    public static void assignUniformInteger(int location, int value) {
        getBackend().assignUniformInteger(location, value);
    }
    
    @Deprecated
    public static void bindAttributeLocation(int program, int index, CharSequence name) {
        getBackend().bindAttributeLocation(program, index, name);
    }
    
    @Deprecated
    public static long createFenceSync(int condition, int flags) {
        return getBackend().createFenceSync(condition, flags);
    }
    
    @Deprecated
    public static int waitForSync(long sync, int flags, long timeout) {
        return getBackend().waitForSync(sync, flags, timeout);
    }
    
    @Deprecated
    public static void destroySync(long sync) {
        getBackend().destroySync(sync);
    }
    
    @Deprecated
    public static void clearTexImage(int texture, int level, int format, int type, int[] data) {
        getBackend().clearTexImage(texture, level, format, type, data);
    }
    
    @Deprecated
    public static void setMaxShaderCompilerThreads(int count) {
        getBackend().setMaxShaderCompilerThreads(count);
    }
    
    @Deprecated
    public static GraphicsCapabilities getGraphicsCapabilities() {
        return getBackend().getGraphicsCapabilities();
    }
    
    @Deprecated
    public static int queryIntegerState(int pname) {
        return getBackend().queryIntegerState(pname);
    }
    
    @Deprecated
    public static String queryStringInfo(int name) {
        return getBackend().queryStringInfo(name);
    }
    
    @Deprecated
    public static int pollErrorCode() {
        return getBackend().pollErrorCode();
    }
    
    @Deprecated
    public static void readFramebufferPixels(int x, int y, int width, int height, int format, int type, long pixels) {
        getBackend().readFramebufferPixels(x, y, width, height, format, type, pixels);
    }
    
    @Deprecated
    public static int queryTextureLevelParameter(int target, int level, int pname) {
        return getBackend().queryTextureLevelParameter(target, level, pname);
    }
    
    @Deprecated
    public static void uploadShaderSource(int shader, long pointerBufferAddress, int stringCount, long lengthsPointer) {
        getBackend().uploadShaderSource(shader, pointerBufferAddress, stringCount, lengthsPointer);
    }
    
    @Deprecated
    public static int locateUniformBlock(int program, String uniformBlockName) {
        return getBackend().locateUniformBlock(program, uniformBlockName);
    }
    
    @Deprecated
    public static void bindUniformBlock(int program, int uniformBlockIndex, int uniformBlockBinding) {
        getBackend().bindUniformBlock(program, uniformBlockIndex, uniformBlockBinding);
    }
    
    @Deprecated
    public static String retrieveActiveUniformBlockName(int program, int uniformBlockIndex) {
        return getBackend().retrieveActiveUniformBlockName(program, uniformBlockIndex);
    }
    
    @Deprecated
    public static int generateQueryObject() {
        return getBackend().generateQueryObject();
    }
    
    @Deprecated
    public static void initiateQuery(int target, int id) {
        getBackend().initiateQuery(target, id);
    }
    
    @Deprecated
    public static void concludeQuery(int target) {
        getBackend().concludeQuery(target);
    }
    
    @Deprecated
    public static void disposeQueryObject(int id) {
        getBackend().disposeQueryObject(id);
    }
    
    @Deprecated
    public static int retrieveQueryObjectInt(int id, int pname) {
        return getBackend().retrieveQueryObjectInt(id, pname);
    }
    
    @Deprecated
    public static long retrieveQueryObjectInt64(int id, int pname) {
        return getBackend().retrieveQueryObjectInt64(id, pname);
    }
    
    @Deprecated
    public static void labelDebugObject(int identifier, int name, String label) {
        getBackend().labelDebugObject(identifier, name, label);
    }
    
    @Deprecated
    public static void enterDebugGroup(int source, int id, CharSequence message) {
        getBackend().enterDebugGroup(source, id, message);
    }
    
    @Deprecated
    public static void exitDebugGroup() {
        getBackend().exitDebugGroup();
    }
    
    @Deprecated
    public static void labelObjectExt(int type, int object, String label) {
        getBackend().labelObjectExt(type, object, label);
    }
    
    @Deprecated
    public static boolean supportsKhrDebug() {
        return getBackend().supportsKhrDebug();
    }
    
    @Deprecated
    public static boolean supportsArbDebugOutput() {
        return getBackend().supportsArbDebugOutput();
    }
    
    @Deprecated
    public static void setupKhrDebugSystem(int verbosityLevel, boolean synchronous, java.util.function.Consumer<String> messageHandler) {
        getBackend().setupKhrDebugSystem(verbosityLevel, synchronous, messageHandler);
    }
    
    @Deprecated
    public static void setupArbDebugSystem(int verbosityLevel, boolean synchronous, java.util.function.Consumer<String> messageHandler) {
        getBackend().setupArbDebugSystem(verbosityLevel, synchronous, messageHandler);
    }
    
    @Deprecated
    public static boolean hasBufferStorageExtension() {
        return getBackend().hasBufferStorageExtension();
    }
    
    @Deprecated
    public static boolean hasVertexAttribBindingExtension() {
        return getBackend().hasVertexAttribBindingExtension();
    }
    
    @Deprecated
    public static void attachVertexBuffer(int bindingIndex, int buffer, long offset, int stride) {
        getBackend().attachVertexBuffer(bindingIndex, buffer, offset, stride);
    }
    
    @Deprecated
    public static void specifyVertexAttribFormat(int attribIndex, int size, int type, boolean normalized, int relativeOffset) {
        getBackend().specifyVertexAttribFormat(attribIndex, size, type, normalized, relativeOffset);
    }
    
    @Deprecated
    public static void specifyVertexAttribIFormat(int attribIndex, int size, int type, int relativeOffset) {
        getBackend().specifyVertexAttribIFormat(attribIndex, size, type, relativeOffset);
    }
    
    @Deprecated
    public static void associateVertexAttrib(int attribIndex, int bindingIndex) {
        getBackend().associateVertexAttrib(attribIndex, bindingIndex);
    }
    
    @Deprecated
    public static void setClearDepthValue(double depth) {
        getBackend().setClearDepthValue(depth);
    }
    
    @Deprecated
    public static void setClearColorValue(float red, float green, float blue, float alpha) {
        getBackend().setClearColorValue(red, green, blue, alpha);
    }
    
    @Deprecated
    public static void selectDrawBuffer(int mode) {
        getBackend().selectDrawBuffer(mode);
    }
    
    @Deprecated
    public static void renderIndexedInstancedWithBase(int mode, int count, int type, long indices, int instanceCount, int baseVertex) {
        getBackend().renderIndexedInstancedWithBase(mode, count, type, indices, instanceCount, baseVertex);
    }
    
    @Deprecated
    public static void renderIndexedWithBase(int mode, int count, int type, long indices, int baseVertex) {
        getBackend().renderIndexedWithBase(mode, count, type, indices, baseVertex);
    }
    
    @Deprecated
    public static void renderIndexedInstanced(int mode, int count, int type, long indices, int instanceCount) {
        getBackend().renderIndexedInstanced(mode, count, type, indices, instanceCount);
    }
    
    @Deprecated
    public static void renderArraysInstanced(int mode, int first, int count, int instanceCount) {
        getBackend().renderArraysInstanced(mode, first, count, instanceCount);
    }
    
    @Deprecated
    public static void attachUniformBufferRange(int target, int index, int buffer, long offset, long size) {
        getBackend().attachUniformBufferRange(target, index, buffer, offset, size);
    }
    
    @Deprecated
    public static void attachBufferToTexture(int target, int internalFormat, int buffer) {
        getBackend().attachBufferToTexture(target, internalFormat, buffer);
    }
    
    @Deprecated
    public static void assignUniformFloat(int location, float value) {
        getBackend().assignUniformFloat(location, value);
    }
    
    @Deprecated
    public static void assignUniformFloat2(int location, float x, float y) {
        getBackend().assignUniformFloat2(location, x, y);
    }
    
    @Deprecated
    public static void assignUniformFloat2v(int location, float[] value) {
        getBackend().assignUniformFloat2v(location, value);
    }
    
    @Deprecated
    public static void assignUniformFloat3(int location, float x, float y, float z) {
        getBackend().assignUniformFloat3(location, x, y, z);
    }
    
    @Deprecated
    public static void assignUniformFloat3v(int location, float[] value) {
        getBackend().assignUniformFloat3v(location, value);
    }
    
    @Deprecated
    public static void assignUniformFloat4(int location, float x, float y, float z, float w) {
        getBackend().assignUniformFloat4(location, x, y, z, w);
    }
    
    @Deprecated
    public static void assignUniformFloat4v(int location, float[] value) {
        getBackend().assignUniformFloat4v(location, value);
    }
    
    @Deprecated
    public static void assignUniformMatrix4f(int location, java.nio.FloatBuffer matrix) {
        getBackend().assignUniformMatrix4f(location, matrix);
    }
    
    @Deprecated
    public static void bindUniformBufferBase(int bindingPoint, int bufferId) {
        getBackend().bindUniformBufferBase(bindingPoint, bufferId);
    }
    
    @Deprecated
    public static void bindFragmentDataLocation(int program, int colorNumber, CharSequence name) {
        getBackend().bindFragmentDataLocation(program, colorNumber, name);
    }
    
    @Deprecated
    public static int querySyncStatus(long sync, int pname, java.nio.IntBuffer length) {
        return getBackend().querySyncStatus(sync, pname, length);
    }
    
    @Deprecated
    public static GraphicsCapabilities obtainGraphicsCapabilities() {
        return getBackend().obtainGraphicsCapabilities();
    }
    
    @Deprecated
    public static GraphicsCapabilities initializeGraphicsCapabilities() {
        return getBackend().initializeGraphicsCapabilities();
    }
    
    @Deprecated
    public static boolean checkFunctionAvailable(String functionName) {
        return getBackend().checkFunctionAvailable(functionName);
    }
    
    @Deprecated
    public static void copyBufferSubData(int readTarget, int writeTarget, long readOffset, long writeOffset, long size) {
        getBackend().copyBufferSubData(readTarget, writeTarget, readOffset, writeOffset, size);
    }
    
    @Deprecated
    public static void deleteVertexArray(int vertexArray) {
        getBackend().deleteVertexArray(vertexArray);
    }
    
    @Deprecated
    public static void flushMappedBufferRange(int target, long offset, long length) {
        getBackend().flushMappedBufferRange(target, offset, length);
    }
    
    @Deprecated
    public static void createBufferStorage(int target, long size, int flags) {
        getBackend().createBufferStorage(target, size, flags);
    }
    
    @Deprecated
    public static void createBufferStorage(int target, java.nio.ByteBuffer data, int flags) {
        getBackend().createBufferStorage(target, data, flags);
    }
    
    @Deprecated
    public static void multiDrawElementsBaseVertex(int mode, long pCount, int type, long pIndices, int drawCount, long pBaseVertex) {
        getBackend().multiDrawElementsBaseVertex(mode, pCount, type, pIndices, drawCount, pBaseVertex);
    }
    
    @Deprecated
    public static void assignUniformMatrix4fv(int location, boolean transpose, java.nio.FloatBuffer value) {
        getBackend().assignUniformMatrix4fv(location, transpose, value);
    }
    
    @Deprecated
    public static String queryString(int name) {
        return getBackend().queryString(name);
    }
    
    @Deprecated
    public static String queryStringIndexed(int name, int index) {
        return getBackend().queryStringIndexed(name, index);
    }
    
    @Deprecated
    public static void uploadShaderSourceNative(int shader, int count, long strings, long length) {
        getBackend().uploadShaderSourceNative(shader, count, strings, length);
    }
    
    @Deprecated
    public static void glCopyTexSubImage2D(int target, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
        getBackend().glCopyTexSubImage2D(target, level, xoffset, yoffset, x, y, width, height);
    }
    
    // Debug object labeling (KHRDebug/GL43)
    @Deprecated
    public static void labelObject(int identifier, int name, String label) {
        getBackend().labelObject(identifier, name, label);
    }
    
    // Debug group push/pop (KHRDebug/GL43)
    @Deprecated
    public static void pushDebugGroup(int source, int id, String message) {
        getBackend().pushDebugGroup(source, id, message);
    }
    
    @Deprecated
    public static void popDebugGroup() {
        getBackend().popDebugGroup();
    }
    
    // Additional methods for IrisRenderSystem migration
    
    @Deprecated
    public static void glGetIntegerv(int pname, int[] params) {
        getBackend().glGetIntegerv(pname, params);
    }
    
    @Deprecated
    public static void glGetFloatv(int pname, float[] params) {
        getBackend().glGetFloatv(pname, params);
    }
    
    @Deprecated
    public static void glTexImage1D(int target, int level, int internalformat, int width, int border, int format, int type, java.nio.ByteBuffer pixels) {
        getBackend().glTexImage1D(target, level, internalformat, width, border, format, type, pixels);
    }
    
    @Deprecated
    public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, java.nio.ByteBuffer pixels) {
        getBackend().glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
    }
    
    @Deprecated
    public static void glTexImage3D(int target, int level, int internalformat, int width, int height, int depth, int border, int format, int type, java.nio.ByteBuffer pixels) {
        getBackend().glTexImage3D(target, level, internalformat, width, height, depth, border, format, type, pixels);
    }
    
    @Deprecated
    public static void glUniformMatrix4fv(int location, boolean transpose, java.nio.FloatBuffer matrix) {
        getBackend().glUniformMatrix4fv(location, transpose, matrix);
    }
    
    @Deprecated
    public static void glUniformMatrix4fv(int location, boolean transpose, float[] matrix) {
        getBackend().glUniformMatrix4fv(location, transpose, matrix);
    }
    
    @Deprecated
    public static void glCopyTexImage2D(int target, int level, int internalFormat, int x, int y, int width, int height, int border) {
        getBackend().glCopyTexImage2D(target, level, internalFormat, x, y, width, height, border);
    }
    
    @Deprecated
    public static void glUniform1f(int location, float v0) {
        getBackend().glUniform1f(location, v0);
    }
    
    @Deprecated
    public static void glUniform2f(int location, float v0, float v1) {
        getBackend().glUniform2f(location, v0, v1);
    }
    
    @Deprecated
    public static void glUniform2i(int location, int v0, int v1) {
        getBackend().glUniform2i(location, v0, v1);
    }
    
    @Deprecated
    public static void glUniform3f(int location, float v0, float v1, float v2) {
        getBackend().glUniform3f(location, v0, v1, v2);
    }
    
    @Deprecated
    public static void glUniform3i(int location, int v0, int v1, int v2) {
        getBackend().glUniform3i(location, v0, v1, v2);
    }
    
    @Deprecated
    public static void glUniform4f(int location, float v0, float v1, float v2, float v3) {
        getBackend().glUniform4f(location, v0, v1, v2, v3);
    }
    
    @Deprecated
    public static void glUniform4i(int location, int v0, int v1, int v2, int v3) {
        getBackend().glUniform4i(location, v0, v1, v2, v3);
    }
    
    @Deprecated
    public static void glTexParameteriv(int target, int pname, int[] params) {
        getBackend().glTexParameteriv(target, pname, params);
    }
    
    @Deprecated
    public static void glTexParameteri(int target, int pname, int param) {
        getBackend().glTexParameteri(target, pname, param);
    }
    
    @Deprecated
    public static void glTexParameterf(int target, int pname, float param) {
        getBackend().glTexParameterf(target, pname, param);
    }
    
    @Deprecated
    public static String glGetProgramInfoLog(int program) {
        return getBackend().glGetProgramInfoLog(program);
    }
    
    @Deprecated
    public static String glGetShaderInfoLog(int shader) {
        return getBackend().glGetShaderInfoLog(shader);
    }
    
    @Deprecated
    public static void glDrawBuffers(int[] buffers) {
        getBackend().glDrawBuffers(buffers);
    }
    
    @Deprecated
    public static void glReadBuffer(int buffer) {
        getBackend().glReadBuffer(buffer);
    }
    
    @Deprecated
    public static void glClearBufferfv(int buffer, int drawbuffer, float[] values) {
        getBackend().glClearBufferfv(buffer, drawbuffer, values);
    }
    
    @Deprecated
    public static void glClearBufferiv(int buffer, int drawbuffer, int[] values) {
        getBackend().glClearBufferiv(buffer, drawbuffer, values);
    }
    
    @Deprecated
    public static void glClearBufferuiv(int buffer, int drawbuffer, int[] values) {
        getBackend().glClearBufferuiv(buffer, drawbuffer, values);
    }
    
    @Deprecated
    public static String glGetActiveUniform(int program, int index, int size, java.nio.IntBuffer type, java.nio.IntBuffer name) {
        return getBackend().glGetActiveUniform(program, index, size, type, name);
    }
    
    @Deprecated
    public static void glReadPixels(int x, int y, int width, int height, int format, int type, float[] pixels) {
        getBackend().glReadPixels(x, y, width, height, format, type, pixels);
    }
    
    @Deprecated
    public static void glBufferData(int target, float[] data, int usage) {
        getBackend().glBufferData(target, data, usage);
    }
    
    @Deprecated
    public static void glBufferData(int target, int[] data, int usage) {
        getBackend().glBufferData(target, data, usage);
    }
    
    @Deprecated
    public static void glBufferData(int target, java.nio.ByteBuffer data, int usage) {
        getBackend().glBufferData(target, data, usage);
    }
    
    @Deprecated
    public static void glBufferData(int target, long size, int usage) {
        getBackend().glBufferData(target, size, usage);
    }
    
    @Deprecated
    public static void glBufferSubData(int target, long offset, java.nio.ByteBuffer data) {
        getBackend().glBufferSubData(target, offset, data);
    }
    
    @Deprecated
    public static void glBufferStorage(int target, long size, int flags) {
        getBackend().glBufferStorage(target, size, flags);
    }
    
    @Deprecated
    public static void glBufferStorage(int target, java.nio.ByteBuffer data, int flags) {
        getBackend().glBufferStorage(target, data, flags);
    }
    
    @Deprecated
    public static java.nio.ByteBuffer glMapBufferRange(int target, long offset, long length, int access) {
        return getBackend().glMapBufferRange(target, offset, length, access);
    }
    
    @Deprecated
    public static boolean glUnmapBuffer(int target) {
        return getBackend().glUnmapBuffer(target);
    }
    
    @Deprecated
    public static boolean glIsBuffer(int buffer) {
        return getBackend().glIsBuffer(buffer);
    }
    
    @Deprecated
    public static void glBindBufferBase(int target, int index, int buffer) {
        getBackend().glBindBufferBase(target, index, buffer);
    }
    
    @Deprecated
    public static void glVertexAttrib4f(int index, float v0, float v1, float v2, float v3) {
        getBackend().glVertexAttrib4f(index, v0, v1, v2, v3);
    }
    
    @Deprecated
    public static void glDetachShader(int program, int shader) {
        getBackend().glDetachShader(program, shader);
    }
    
    @Deprecated
    public static void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        getBackend().glFramebufferTexture2D(target, attachment, textarget, texture, level);
    }
    
    @Deprecated
    public static void glFramebufferTexture(int target, int attachment, int texture, int level) {
        getBackend().glFramebufferTexture(target, attachment, texture, level);
    }
    
    @Deprecated
    public static int glGetTexParameteri(int target, int pname) {
        return getBackend().glGetTexParameteri(target, pname);
    }
    
    @Deprecated
    public static void glBindImageTexture(int unit, int texture, int level, boolean layered, int layer, int access, int format) {
        getBackend().glBindImageTexture(unit, texture, level, layered, layer, access, format);
    }
    
    @Deprecated
    public static int glGetMaxImageUnits() {
        return getBackend().glGetMaxImageUnits();
    }
    
    @Deprecated
    public static void glGenBuffers(int[] buffers) {
        getBackend().glGenBuffers(buffers);
    }
    
    @Deprecated
    public static void glClearBufferSubData(int target, int internalformat, long offset, long size, int format, int type, int[] data) {
        getBackend().glClearBufferSubData(target, internalformat, offset, size, format, type, data);
    }
    
    @Deprecated
    public static void glGetProgramiv(int program, int pname, int[] params) {
        getBackend().glGetProgramiv(program, pname, params);
    }
    
    @Deprecated
    public static void glDispatchCompute(int workX, int workY, int workZ) {
        getBackend().glDispatchCompute(workX, workY, workZ);
    }
    
    @Deprecated
    public static void glMemoryBarrier(int barriers) {
        getBackend().glMemoryBarrier(barriers);
    }
    
    @Deprecated
    public static void glDisablei(int target, int index) {
        getBackend().glDisablei(target, index);
    }
    
    @Deprecated
    public static void glEnablei(int target, int index) {
        getBackend().glEnablei(target, index);
    }
    
    @Deprecated
    public static void glBlendFunc(int sfactor, int dfactor) {
        getBackend().glBlendFunc(sfactor, dfactor);
    }
    
    @Deprecated
    public static void glBlendFuncSeparatei(int buffer, int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        getBackend().glBlendFuncSeparatei(buffer, srcRGB, dstRGB, srcAlpha, dstAlpha);
    }
    
    @Deprecated
    public static int glGetUniformBlockIndex(int program, String uniformBlockName) {
        return getBackend().glGetUniformBlockIndex(program, uniformBlockName);
    }
    
    @Deprecated
    public static void glUniformBlockBinding(int program, int uniformBlockIndex, int uniformBlockBinding) {
        getBackend().glUniformBlockBinding(program, uniformBlockIndex, uniformBlockBinding);
    }
    
    @Deprecated
    public static int glGenSamplers() {
        return getBackend().glGenSamplers();
    }
    
    @Deprecated
    public static void glDeleteSamplers(int sampler) {
        getBackend().glDeleteSamplers(sampler);
    }
    
    @Deprecated
    public static void glBindSampler(int unit, int sampler) {
        getBackend().glBindSampler(unit, sampler);
    }
    
    @Deprecated
    public static void glBindSamplers(int first, int[] samplers) {
        getBackend().glBindSamplers(first, samplers);
    }
    
    @Deprecated
    public static void glSamplerParameteri(int sampler, int pname, int param) {
        getBackend().glSamplerParameteri(sampler, pname, param);
    }
    
    @Deprecated
    public static void glSamplerParameterf(int sampler, int pname, float param) {
        getBackend().glSamplerParameterf(sampler, pname, param);
    }
    
    @Deprecated
    public static void glSamplerParameteriv(int sampler, int pname, int[] params) {
        getBackend().glSamplerParameteriv(sampler, pname, params);
    }
    
    @Deprecated
    public static int glGetInteger(int pname) {
        return getBackend().glGetInteger(pname);
    }
    
    @Deprecated
    public static void glDeleteBuffers(int buffer) {
        getBackend().glDeleteBuffers(buffer);
    }
    
    @Deprecated
    public static void glPolygonMode(int face, int mode) {
        getBackend().glPolygonMode(face, mode);
    }
    
    @Deprecated
    public static void glViewport(int x, int y, int width, int height) {
        getBackend().glViewport(x, y, width, height);
    }
    
    @Deprecated
    public static void glDispatchComputeIndirect(long offset) {
        getBackend().glDispatchComputeIndirect(offset);
    }
    
    @Deprecated
    public static void glBindBuffer(int target, int buffer) {
        getBackend().glBindBuffer(target, buffer);
    }
    
    @Deprecated
    public static String glGetStringi(int name, int index) {
        return getBackend().glGetStringi(name, index);
    }
    
    @Deprecated
    public static void glCopyImageSubData(int srcName, int srcTarget, int srcLevel, int srcX, int srcY, int srcZ, int dstName, int dstTarget, int dstLevel, int dstX, int dstY, int dstZ, int width, int height, int depth) {
        getBackend().glCopyImageSubData(srcName, srcTarget, srcLevel, srcX, srcY, srcZ, dstName, dstTarget, dstLevel, dstX, dstY, dstZ, width, height, depth);
    }
    
    @Deprecated
    public static int glCheckFramebufferStatus(int target) {
        return getBackend().glCheckFramebufferStatus(target);
    }
    
    @Deprecated
    public static void glUniformMatrix3fv(int location, boolean transpose, java.nio.FloatBuffer value) {
        getBackend().glUniformMatrix3fv(location, transpose, value);
    }
    
    @Deprecated
    public static void glUniformMatrix3fv(int location, boolean transpose, float[] value) {
        getBackend().glUniformMatrix3fv(location, transpose, value);
    }
    
    @Deprecated
    public static void glClearColor(float r, float g, float b, float a) {
        getBackend().glClearColor(r, g, b, a);
    }
    
    @Deprecated
    public static int glGetAttribLocation(int program, CharSequence name) {
        return getBackend().glGetAttribLocation(program, name);
    }
    
    @Deprecated
    public static void glGenerateMipmap(int target) {
        getBackend().glGenerateMipmap(target);
    }
    
    @Deprecated
    public static void glBlitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        getBackend().glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }
    
    // DSA (Direct State Access) methods - ARB versions
    
    @Deprecated
    public static void glGenerateTextureMipmap(int texture) {
        getBackend().glGenerateTextureMipmap(texture);
    }
    
    @Deprecated
    public static void glTextureParameteri(int texture, int pname, int param) {
        getBackend().glTextureParameteri(texture, pname, param);
    }
    
    @Deprecated
    public static void glTextureParameterf(int texture, int pname, float param) {
        getBackend().glTextureParameterf(texture, pname, param);
    }
    
    @Deprecated
    public static void glTextureParameteriv(int texture, int pname, int[] params) {
        getBackend().glTextureParameteriv(texture, pname, params);
    }
    
    @Deprecated
    public static void glNamedFramebufferReadBuffer(int framebuffer, int mode) {
        getBackend().glNamedFramebufferReadBuffer(framebuffer, mode);
    }
    
    @Deprecated
    public static void glNamedFramebufferDrawBuffers(int framebuffer, int[] bufs) {
        getBackend().glNamedFramebufferDrawBuffers(framebuffer, bufs);
    }
    
    @Deprecated
    public static void glClearNamedFramebufferfv(int framebuffer, int buffer, int drawbuffer, float[] value) {
        getBackend().glClearNamedFramebufferfv(framebuffer, buffer, drawbuffer, value);
    }
    
    @Deprecated
    public static void glClearNamedFramebufferiv(int framebuffer, int buffer, int drawbuffer, int[] value) {
        getBackend().glClearNamedFramebufferiv(framebuffer, buffer, drawbuffer, value);
    }
    
    @Deprecated
    public static void glClearNamedFramebufferuiv(int framebuffer, int buffer, int drawbuffer, int[] value) {
        getBackend().glClearNamedFramebufferuiv(framebuffer, buffer, drawbuffer, value);
    }
    
    @Deprecated
    public static int glGetTextureParameteri(int texture, int pname) {
        return getBackend().glGetTextureParameteri(texture, pname);
    }
    
    @Deprecated
    public static void glCopyTextureSubImage2D(int texture, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
        getBackend().glCopyTextureSubImage2D(texture, level, xoffset, yoffset, x, y, width, height);
    }
    
    @Deprecated
    public static void glBindTextureUnit(int unit, int texture) {
        getBackend().glBindTextureUnit(unit, texture);
    }
    
    @Deprecated
    public static int glCreateBuffers() {
        return getBackend().glCreateBuffers();
    }
    
    @Deprecated
    public static void glNamedBufferData(int buffer, float[] data, int usage) {
        getBackend().glNamedBufferData(buffer, data, usage);
    }
    
    @Deprecated
    public static void glBlitNamedFramebuffer(int readFramebuffer, int drawFramebuffer, int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        getBackend().glBlitNamedFramebuffer(readFramebuffer, drawFramebuffer, srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }
    
    @Deprecated
    public static void glNamedFramebufferTexture(int framebuffer, int attachment, int texture, int level) {
        getBackend().glNamedFramebufferTexture(framebuffer, attachment, texture, level);
    }
    
    @Deprecated
    public static int glCreateFramebuffers() {
        return getBackend().glCreateFramebuffers();
    }
    
    @Deprecated
    public static int glGenFramebuffers() {
        return getBackend().generateFramebufferObject();
    }
    
    @Deprecated
    public static void glDeleteFramebuffers(int framebuffer) {
        getBackend().destroyFramebufferObject(framebuffer);
    }
    
    @Deprecated
    public static int glCreateTextures(int target) {
        return getBackend().glCreateTextures(target);
    }
    
    // Additional rendering operations
    @Deprecated
    public static void glDrawElements(int mode, int count, int type, long indices) {
        getBackend().glDrawElements(mode, count, type, indices);
    }
    
    @Deprecated
    public static void glBlendEquation(int mode) {
        getBackend().glBlendEquation(mode);
    }
    
    @Deprecated
    public static void glClearDepth(double depth) {
        getBackend().glClearDepth(depth);
    }
    
    @Deprecated
    public static int glGetFramebufferAttachmentParameteri(int target, int attachment, int pname) {
        return getBackend().glGetFramebufferAttachmentParameteri(target, attachment, pname);
    }
    
    // Debug callback control methods (low-level callback control methods only)
    // Note: The high-level setup methods below use Vulkanic functional interfaces
    @Deprecated
    public static void glDebugMessageControl(int source, int type, int severity, int[] ids, boolean enabled) {
        getBackend().glDebugMessageControl(source, type, severity, ids, enabled);
    }
    
    @Deprecated
    public static void glDebugMessageControlKHR(int source, int type, int severity, int[] ids, boolean enabled) {
        getBackend().glDebugMessageControlKHR(source, type, severity, ids, enabled);
    }
    
    @Deprecated
    public static void glDebugMessageControlARB(int source, int type, int severity, int[] ids, boolean enabled) {
        getBackend().glDebugMessageControlARB(source, type, severity, ids, enabled);
    }
    
    @Deprecated
    public static void glDebugMessageEnableAMD(int category, int severity, int[] ids, boolean enabled) {
        getBackend().glDebugMessageEnableAMD(category, severity, ids, enabled);
    }
    
    // High-level debug callback wrapper methods that accept functional interfaces
    @Deprecated
    public static void setupDebugMessageCallback(DebugMessageCallback callback) {
        getBackend().setupDebugMessageCallback(callback);
    }
    
    @Deprecated
    public static void setupDebugMessageCallbackKHR(DebugMessageCallback callback) {
        getBackend().setupDebugMessageCallbackKHR(callback);
    }
    
    @Deprecated
    public static void setupDebugMessageCallbackARB(DebugMessageCallbackARB callback) {
        getBackend().setupDebugMessageCallbackARB(callback);
    }
    
    @Deprecated
    public static void setupDebugMessageCallbackAMD(DebugMessageCallbackAMD callback) {
        getBackend().setupDebugMessageCallbackAMD(callback);
    }
    
    @Deprecated
    public static void clearDebugMessageCallback() {
        getBackend().clearDebugMessageCallback();
    }
    
    @Deprecated
    public static void clearDebugMessageCallbackKHR() {
        getBackend().clearDebugMessageCallbackKHR();
    }
    
    @Deprecated
    public static void clearDebugMessageCallbackARB() {
        getBackend().clearDebugMessageCallbackARB();
    }
    
    @Deprecated
    public static void clearDebugMessageCallbackAMD() {
        getBackend().clearDebugMessageCallbackAMD();
    }
    
    // GL-style wrapper methods for backward compatibility
    // These delegate to the abstracted methods above
    
    /**
     * Binds a vertex attribute to a specific location in a shader program.
     * Wrapper for bindAttributeLocation.
     */
    @Deprecated
    public static void glBindAttribLocation(int program, int index, CharSequence name) {
        bindAttributeLocation(program, index, name);
    }
    
    /**
     * Configures a vertex attribute pointer.
     * Wrapper for configureVertexAttribute.
     */
    @Deprecated
    public static void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long pointer) {
        configureVertexAttribute(index, size, type, normalized, stride, pointer);
    }
    
    /**
     * Enables a vertex attribute array.
     * Wrapper for activateVertexAttribute.
     */
    @Deprecated
    public static void glEnableVertexAttribArray(int index) {
        activateVertexAttribute(index);
    }
    
    /**
     * Creates a new shader program object.
     * Wrapper for constructProgramObject.
     */
    @Deprecated
    public static int glCreateProgram() {
        return constructProgramObject(CTX);
    }
    
    /**
     * Attaches a shader to a program.
     * Wrapper for attachShaderToProgram.
     */
    @Deprecated
    public static void glAttachShader(int program, int shader) {
        attachShaderToProgram(program, shader);
    }
    
    /**
     * Links a program object.
     * Wrapper for linkProgramBinary.
     */
    @Deprecated
    public static void glLinkProgram(int program) {
        linkProgramBinary(program);
    }
    
    /**
     * Returns a parameter from a program object.
     * Wrapper for queryProgramParameter.
     */
    @Deprecated
    public static int glGetProgrami(int program, int pname) {
        return queryProgramParameter(program, pname);
    }
    
    /**
     * Installs a program object as part of current rendering state.
     * Wrapper for bindShaderProgram.
     */
    @Deprecated
    public static void glUseProgram(int program) {
        bindShaderProgram(OpenGLCommandContext.IMMEDIATE, program);
    }
    
    /**
     * Deletes a program object.
     * Wrapper for disposeProgramObject.
     */
    @Deprecated
    public static void glDeleteProgram(int program) {
        disposeProgramObject(CTX, program);
    }
    
    /**
     * Returns the location of a uniform variable.
     * Wrapper for locateUniformVariable.
     */
    @Deprecated
    public static int glGetUniformLocation(int program, CharSequence name) {
        return locateUniformVariable(program, name);
    }
    
    /**
     * Sets the value of a uniform variable.
     * Wrapper for assignUniformInteger.
     */
    @Deprecated
    public static void glUniform1i(int location, int value) {
        assignUniformInteger(location, value);
    }
    
    // GL43+ Vertex Attribute methods
    
    /**
     * Binds a buffer to a vertex buffer bind point (GL43+).
     */
    @Deprecated
    public static void glBindVertexBuffer(int bindingindex, int buffer, long offset, int stride) {
        getBackend().bindVertexBuffer(bindingindex, buffer, offset, stride);
    }
    
    /**
     * Specifies the organization of vertex arrays (GL43+).
     */
    @Deprecated
    public static void glVertexAttribFormat(int attribindex, int size, int type, boolean normalized, int relativeoffset) {
        getBackend().vertexAttribFormat(attribindex, size, type, normalized, relativeoffset);
    }
    
    /**
     * Specifies the organization of vertex arrays for integer data (GL43+).
     */
    @Deprecated
    public static void glVertexAttribIFormat(int attribindex, int size, int type, int relativeoffset) {
        getBackend().vertexAttribIFormat(attribindex, size, type, relativeoffset);
    }
    
    /**
     * Associates a vertex attribute and a vertex buffer binding (GL43+).
     */
    @Deprecated
    public static void glVertexAttribBinding(int attribindex, int bindingindex) {
        getBackend().vertexAttribBinding(attribindex, bindingindex);
    }
    
    /**
     * Disables a generic vertex attribute array.
     */
    @Deprecated
    public static void glDisableVertexAttribArray(int index) {
        deactivateVertexAttribute(index);
    }
    
    /**
     * Defines an array of generic vertex attribute data with integer data (GL20+).
     * Specifies the data format for integer vertex attributes.
     */
    @Deprecated
    public static void glVertexAttribIPointer(int index, int size, int type, int stride, long pointer) {
        configureVertexAttributeInteger(index, size, type, stride, pointer);
    }
    
    // VAO methods
    
    /**
     * Generates vertex array object names.
     */
    @Deprecated
    public static int glGenVertexArrays() {
        return getBackend().genVertexArrays();
    }
    
    /**
     * Binds a vertex array object.
     */
    @Deprecated
    public static void glBindVertexArray(int array) {
        getBackend().bindVertexArray(array);
    }
    
    /**
     * Deletes vertex array objects.
     */
    @Deprecated
    public static void glDeleteVertexArrays(int array) {
        getBackend().deleteVertexArrays(array);
    }
    
    // GL.getCapabilities() and GLUtil support
    
    /**
     * Gets the OpenGL capabilities for the current context.
     * Returns a platform-specific capabilities object that should be cast to the appropriate type.
     * For OpenGL backend, returns GLCapabilities from the LWJGL library.
     * 
     * @return Platform-specific capabilities object (cast to GLCapabilities for OpenGL backend)
     */
    @Deprecated
    public static Object getGLCapabilities() {
        return getBackend().getGLCapabilities();
    }
    
    /**
     * Sets up debug message callback using GLUtil-style callback.
     * @param stream The PrintStream to write debug messages to
     */
    @Deprecated
    public static void setupDebugMessageCallback(java.io.PrintStream stream) {
        getBackend().setupDebugMessageCallback(stream);
    }
    
    // Capability checking methods (to avoid casting GLCapabilities outside backends/opengl)
    
    /**
     * Checks if OpenGL 3.2 is supported.
     * @return true if OpenGL 3.2 is supported
     */
    @Deprecated
    public static boolean checkOpenGL32Support() {
        return getBackend().checkOpenGL32Support();
    }
    
    /**
     * Checks if OpenGL 3.3 is supported.
     * @return true if OpenGL 3.3 is supported
     */
    @Deprecated
    public static boolean checkOpenGL33Support() {
        return getBackend().checkOpenGL33Support();
    }
    
    /**
     * Checks if ARB_instanced_arrays extension is supported.
     * @return true if ARB_instanced_arrays is supported
     */
    @Deprecated
    public static boolean checkARBInstancedArraysSupport() {
        return getBackend().checkARBInstancedArraysSupport();
    }
    
    /**
     * Gets the function pointer for glNamedBufferData.
     * @return function pointer, or 0 if not available
     */
    @Deprecated
    public static long getNamedBufferDataPointer() {
        return getBackend().getNamedBufferDataPointer();
    }
    
    /**
     * Gets the function pointer for glBufferStorage.
     * @return function pointer, or 0 if not available
     */
    @Deprecated
    public static long getBufferStoragePointer() {
        return getBackend().getBufferStoragePointer();
    }
    
    /**
     * Gets the function pointer for glBindVertexBuffer.
     * @return function pointer, or 0 if not available
     */
    @Deprecated
    public static long getBindVertexBufferPointer() {
        return getBackend().getBindVertexBufferPointer();
    }
    
    /**
     * Gets the function pointer for glVertexAttribBinding.
     * @return function pointer, or 0 if not available
     */
    @Deprecated
    public static long getVertexAttribBindingPointer() {
        return getBackend().getVertexAttribBindingPointer();
    }
    
    /**
     * Gets capability information as a formatted string for debugging.
     * @return formatted capability information
     */
    @Deprecated
    public static String getCapabilityDebugInfo() {
        return "Your OpenGL support:\n" +
                "openGL version 3.2+: [" + checkOpenGL32Support() + "] <- REQUIRED\n" +
                "Vertex Attribute Buffer Binding: [" + (getVertexAttribBindingPointer() != 0) + "] <- optional improvement\n" +
                "Buffer Storage: [" + (getBufferStoragePointer() != 0) + "] <- optional improvement\n";
    }
    
    // Additional GL query and state methods
    
    /**
     * Tests whether a capability is enabled.
     */
    @Deprecated
    public static boolean glIsEnabled(int cap) {
        return getBackend().glIsEnabled(cap);
    }
    
    /**
     * Determines if a name corresponds to a framebuffer object.
     */
    @Deprecated
    public static boolean glIsFramebuffer(int framebuffer) {
        return getBackend().glIsFramebuffer(framebuffer);
    }
    
    /**
     * Determines if a name corresponds to a texture.
     */
    @Deprecated
    public static boolean glIsTexture(int texture) {
        return getBackend().glIsTexture(texture);
    }
    
    /**
     * Determines if a name corresponds to a vertex array object.
     */
    @Deprecated
    public static boolean glIsVertexArray(int array) {
        return getBackend().glIsVertexArray(array);
    }
    
    /**
     * Determines if a name corresponds to a program object.
     */
    @Deprecated
    public static boolean glIsProgram(int program) {
        return getBackend().glIsProgram(program);
    }
    
    /**
     * Sets the RGB blend equation and the alpha blend equation separately.
     */
    @Deprecated
    public static void glBlendEquationSeparate(int modeRGB, int modeAlpha) {
        getBackend().glBlendEquationSeparate(modeRGB, modeAlpha);
    }
    
    /**
     * Sets the stencil test function.
     */
    @Deprecated
    public static void glStencilFunc(int func, int ref, int mask) {
        getBackend().glStencilFunc(func, ref, mask);
    }
    
    /**
     * Specifies whether front- or back-facing polygons can be culled.
     */
    @Deprecated
    public static void glCullFace(int mode) {
        getBackend().glCullFace(mode);
    }
    
    /**
     * Generates a single texture name.
     */
    @Deprecated
    public static int glGenTextures() {
        return getBackend().glGenTextures();
    }
    
    /**
     * Binds a named texture to a texturing target.
     */
    @Deprecated
    public static void glBindTexture(int target, int texture) {
        getBackend().bindTexture(target, texture);
    }
}
