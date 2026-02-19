package net.vulkanic;

import net.vulkanic.backends.opengl.OpenGLBackend;
import net.vulkanic.backends.opengl.OpenGLCommandContext;

/**
 * Main entry point for the Vulkanic Graphics Abstraction Layer.
 * Provides a unified API for graphics operations that can be backed by different graphics APIs.
 */
public class VulkanicAPI {
    private static GraphicsBackend backend;
    
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
        getBackend().setIndexedEnabled(ctx, capability, index, enabled);
    }
    
    /**
     * Sets the face culling mode.
     * 
     * @param ctx Command context for recording this command
     * @param mode The face culling mode
     */
    public static void setCullFaceMode(CommandContext ctx, int mode) {
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
        getBackend().setCapabilityEnabled(ctx, cap, enabled);
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
    
    public static void bindTexture(CommandContext ctx, int target, int textureId) {
        getBackend().bindTexture(ctx, target, textureId);
    }
    
    public static void bindSampler(CommandContext ctx, int unit, int sampler) {
        getBackend().bindSampler(ctx, unit, sampler);
    }
    
    @Deprecated
    public static void bindTexture(int target, int textureId) {
        bindTexture(getImmediateContext(), target, textureId);
    }
    
    @Deprecated
    public static void generateMipmap(int target) {
        generateTextureMipmap(getImmediateContext(), target);
    }
    
    /**
     * Sets the depth test comparison function.
     * 
     * @param ctx Command context for recording this command
     * @param func The depth comparison function
     */
    public static void setDepthTest(CommandContext ctx, int func) {
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
        getBackend().bindFramebuffer(ctx, target, fbo);
    }
    
    /**
     * Binds a buffer object to a target.
     * 
     * @param ctx Command context for recording this command
     * @param target The buffer target
     * @param buffer The buffer object ID
     */
    public static void bindBuffer(CommandContext ctx, int target, int buffer) {
        getBackend().bindBuffer(ctx, target, buffer);
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
        getBackend().bindBufferBase(ctx, target, index, buffer);
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
     * Sets a texture parameter.
     * 
     * @param ctx Command context for recording this command
     * @param target The texture target
     * @param pname The parameter name
     * @param param The parameter value
     */
    public static void setTextureParameter(CommandContext ctx, int target, int pname, int param) {
        getBackend().setTextureParameter(ctx, target, pname, param);
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
        return getBackend().getInteger(ctx, pname);
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
        getBackend().setPolygonMode(ctx, face, mode);
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
    
    @Deprecated
    public static int createBufferDSA() {
        return createBufferDSA(getImmediateContext());
    }
    
    @Deprecated
    public static void namedBufferDataDSA(int buffer, long size, int usage) {
        namedBufferDataDSA(getImmediateContext(), buffer, size, usage);
    }
    
    @Deprecated
    public static void namedBufferDataDSA(int buffer, java.nio.ByteBuffer data, int usage) {
        namedBufferDataDSA(getImmediateContext(), buffer, data, usage);
    }
    
    @Deprecated
    public static void namedBufferSubDataDSA(int buffer, long offset, java.nio.ByteBuffer data) {
        namedBufferSubDataDSA(getImmediateContext(), buffer, offset, data);
    }
    
    @Deprecated
    public static void namedBufferStorageDSA(int buffer, long size, int flags) {
        namedBufferStorageDSA(getImmediateContext(), buffer, size, flags);
    }
    
    @Deprecated
    public static void namedBufferStorageDSA(int buffer, java.nio.ByteBuffer data, int flags) {
        namedBufferStorageDSA(getImmediateContext(), buffer, data, flags);
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
    public static void namedFramebufferTextureDSA(int framebuffer, int attachment, int texture, int level) {
        getBackend().namedFramebufferTextureDSA(framebuffer, attachment, texture, level);
    }
    
    @Deprecated
    public static void blitNamedFramebufferDSA(int readFramebuffer, int drawFramebuffer, int srcX0, int srcY0, int srcX1, int srcY1,
                                                int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        getBackend().blitNamedFramebufferDSA(readFramebuffer, drawFramebuffer, srcX0, srcY0, srcX1, srcY1,
                                              dstX0, dstY0, dstX1, dstY1, mask, filter);
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
        getBackend().drawArrays(ctx, mode, first, count);
    }
    
    public static void drawElements(CommandContext ctx, int mode, int count, int type, long indices) {
        getBackend().drawElements(ctx, mode, count, type, indices);
    }
    
    public static void setBlendFunction(CommandContext ctx, int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        getBackend().setBlendFunction(ctx, srcRgb, dstRgb, srcAlpha, dstAlpha);
    }
    
    public static void setBlendEquation(CommandContext ctx, int mode) {
        getBackend().setBlendEquation(ctx, mode);
    }
    
    public static void setDepthFunc(CommandContext ctx, int func) {
        getBackend().setDepthFunc(ctx, func);
    }
    
    public static void setReadBuffer(CommandContext ctx, int buffer) {
        getBackend().setReadBuffer(ctx, buffer);
    }
    
    
    public static int getError(CommandContext ctx) {
        return getBackend().getError(ctx);
    }
    
    @Deprecated
    public static int checkForErrors() {
        return getBackend().getError(getImmediateContext());
    }
    
    public static void uploadTexture2D(CommandContext ctx, int target, int level, int internalFormat, int width, int height, 
                                        int border, int format, int type, java.nio.ByteBuffer pixels) {
        getBackend().uploadTexture2D(ctx, target, level, internalFormat, width, height, border, format, type, pixels);
    }
    
    public static void uploadTexture2DSubImage(CommandContext ctx, int target, int level, int xOffset, int yOffset, 
                                                int width, int height, int format, int type, long pixels) {
        getBackend().uploadTexture2DSubImage(ctx, target, level, xOffset, yOffset, width, height, format, type, pixels);
    }
    
    public static void uploadTexture2DSubImage(CommandContext ctx, int target, int level, int xOffset, int yOffset, 
                                                int width, int height, int format, int type, java.nio.ByteBuffer pixels) {
        getBackend().uploadTexture2DSubImage(ctx, target, level, xOffset, yOffset, width, height, format, type, pixels);
    }
    
    @Deprecated
    public static void transferTexture2DImage(int tgt, int lvl, int intfmt, int w, int h, int bdr, int fmt, int typ, java.nio.ByteBuffer pix) {
        uploadTexture2D(getImmediateContext(), tgt, lvl, intfmt, w, h, bdr, fmt, typ, pix);
    }
    
    @Deprecated
    public static void transferTexture2DSubregion(int tgt, int lvl, int xoff, int yoff, int w, int h, int fmt, int typ, long pix) {
        uploadTexture2DSubImage(getImmediateContext(), tgt, lvl, xoff, yoff, w, h, fmt, typ, pix);
    }
    
    @Deprecated
    public static void transferTexture2DSubregionBuf(int tgt, int lvl, int xoff, int yoff, int w, int h, int fmt, int typ, java.nio.ByteBuffer pix) {
        uploadTexture2DSubImage(getImmediateContext(), tgt, lvl, xoff, yoff, w, h, fmt, typ, pix);
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
    
    @Deprecated
    public static int allocateBufferObject() {
        return createBuffer(getImmediateContext());
    }
    
    @Deprecated
    public static void releaseBufferObject(int buf) {
        deleteBuffer(getImmediateContext(), buf);
    }
    
    @Deprecated
    public static void fillBufferWithData(int tgt, java.nio.ByteBuffer dat, int usg) {
        bufferData(getImmediateContext(), tgt, dat, usg);
    }
    
    @Deprecated
    public static void fillBufferWithSize(int tgt, long sz, int usg) {
        bufferData(getImmediateContext(), tgt, sz, usg);
    }
    
    public static void bufferSubData(CommandContext ctx, int target, long offset, java.nio.ByteBuffer data) {
        getBackend().bufferSubData(ctx, target, offset, data);
    }
    
    public static void bufferStorage(CommandContext ctx, int target, long size, int flags) {
        getBackend().bufferStorage(ctx, target, size, flags);
    }
    
    public static void bufferStorage(CommandContext ctx, int target, java.nio.ByteBuffer data, int flags) {
        getBackend().bufferStorage(ctx, target, data, flags);
    }
    
    public static void copyBufferSubData(CommandContext ctx, int readTarget, int writeTarget, long readOffset, long writeOffset, long size) {
        getBackend().copyBufferSubData(ctx, readTarget, writeTarget, readOffset, writeOffset, size);
    }
    
    public static void flushMappedBufferRange(CommandContext ctx, int target, long offset, long length) {
        getBackend().flushMappedBufferRange(ctx, target, offset, length);
    }
    
    @Deprecated
    public static void fillBufferSubregion(int tgt, long off, java.nio.ByteBuffer dat) {
        bufferSubData(getImmediateContext(), tgt, off, dat);
    }
    
    public static int createVertexArray(CommandContext ctx) {
        return getBackend().createVertexArray(ctx);
    }
    
    public static void bindVertexArray(CommandContext ctx, int vao) {
        getBackend().bindVertexArray(ctx, vao);
    }
    
    @Deprecated
    public static int createVertexArrayObject() {
        return createVertexArray(getImmediateContext());
    }
    
    @Deprecated
    public static void selectVertexArray(int vao) {
        bindVertexArray(getImmediateContext(), vao);
    }
    
    public static java.nio.ByteBuffer mapBuffer(CommandContext ctx, int target, long offset, long length, int access) {
        return getBackend().mapBuffer(ctx, target, offset, length, access);
    }
    
    @Deprecated
    public static java.nio.ByteBuffer mapBufferRegion(int tgt, int off, int len, int acc) {
        return mapBuffer(getImmediateContext(), tgt, off, len, acc);
    }
    
    public static void unmapBuffer(CommandContext ctx, int target) {
        getBackend().unmapBuffer(ctx, target);
    }
    
    @Deprecated
    public static void unmapBufferData(int tgt) {
        unmapBuffer(getImmediateContext(), tgt);
    }
    
    @Deprecated
    public static int generateFramebufferObject() {
        return createFramebuffer(getImmediateContext());
    }
    
    public static void deleteFramebuffer(CommandContext ctx, int fbo) {
        getBackend().deleteFramebuffer(ctx, fbo);
    }
    
    @Deprecated
    public static void destroyFramebufferObject(int fbo) {
        deleteFramebuffer(getImmediateContext(), fbo);
    }
    
    public static void blitFramebuffer(CommandContext ctx, int srcX0, int srcY0, int srcX1, int srcY1, 
                                       int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        getBackend().blitFramebuffer(ctx, srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }
    
    @Deprecated
    public static void copyFramebufferRegion(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int msk, int flt) {
        blitFramebuffer(getImmediateContext(), srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, msk, flt);
    }
    
    public static int createShader(CommandContext ctx, int shaderType) {
        return getBackend().createShader(ctx, shaderType);
    }
    
    public static void compileShader(CommandContext ctx, int shader) {
        getBackend().compileShader(ctx, shader);
    }
    
    public static int createShaderProgram(CommandContext ctx) {
        return getBackend().createShaderProgram(ctx);
    }
    
    @Deprecated
    public static int constructShaderObject(int shaderType) {
        return createShader(getImmediateContext(), shaderType);
    }
    
    public static void deleteShader(CommandContext ctx, int shader) {
        getBackend().deleteShader(ctx, shader);
    }
    
    @Deprecated
    public static void disposeShaderObject(int shader) {
        deleteShader(getImmediateContext(), shader);
    }
    
    @Deprecated
    public static void compileShaderSource(int shader) {
        compileShader(getImmediateContext(), shader);
    }
    
    @Deprecated
    public static int constructProgramObject() {
        return createShaderProgram(getImmediateContext());
    }
    
    public static void deleteProgram(CommandContext ctx, int program) {
        getBackend().deleteProgram(ctx, program);
    }
    
    @Deprecated
    public static void disposeProgramObject(int program) {
        deleteProgram(getImmediateContext(), program);
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
        return getBackend().getProgramParameter(ctx, program, pname);
    }
    
    public static int getShaderParameter(CommandContext ctx, int shader, int pname) {
        return getBackend().getShaderParameter(ctx, shader, pname);
    }
    
    public static String getProgramInfoLog(CommandContext ctx, int program) {
        return getBackend().getProgramInfoLog(ctx, program);
    }
    
    @Deprecated
    public static void linkProgramBinary(int program) {
        linkProgram(getImmediateContext(), program);
    }
    
    @Deprecated
    public static void attachShaderToProgram(int program, int shader) {
        attachShader(getImmediateContext(), program, shader);
    }
    
    @Deprecated
    public static int queryProgramParameter(int program, int pname) {
        return getProgramParameter(getImmediateContext(), program, pname);
    }
    
    @Deprecated
    public static int queryShaderParameter(int shader, int pname) {
        return getShaderParameter(getImmediateContext(), shader, pname);
    }
    
    @Deprecated
    public static String retrieveProgramInfoLog(int program) {
        return getProgramInfoLog(getImmediateContext(), program);
    }
    
    public static String getShaderInfoLog(CommandContext ctx, int shader) {
        return getBackend().getShaderInfoLog(ctx, shader);
    }
    
    public static int getUniformLocation(CommandContext ctx, int program, CharSequence name) {
        return getBackend().getUniformLocation(ctx, program, name);
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
    
    public static void enableVertexAttribArray(CommandContext ctx, int index) {
        getBackend().enableVertexAttribArray(ctx, index);
    }
    
    public static void bindVertexBuffer(CommandContext ctx, int bindingindex, int buffer, long offset, int stride) {
        getBackend().bindVertexBuffer(ctx, bindingindex, buffer, offset, stride);
    }
    
    @Deprecated
    public static void configureVertexAttribute(int index, int size, int type, boolean normalized, int stride, long pointer) {
        setVertexAttribPointer(getImmediateContext(), index, size, type, normalized, stride, pointer);
    }
    
    public static void setVertexAttribIPointer(CommandContext ctx, int index, int size, int type, int stride, long pointer) {
        getBackend().setVertexAttribIPointer(ctx, index, size, type, stride, pointer);
    }
    
    @Deprecated
    public static void configureVertexAttributeInteger(int index, int size, int type, int stride, long pointer) {
        setVertexAttribIPointer(getImmediateContext(), index, size, type, stride, pointer);
    }
    
    @Deprecated
    public static void activateVertexAttribute(int index) {
        enableVertexAttribArray(getImmediateContext(), index);
    }
    
    public static void disableVertexAttribArray(CommandContext ctx, int index) {
        getBackend().disableVertexAttribArray(ctx, index);
    }
    
    @Deprecated
    public static void deactivateVertexAttribute(int index) {
        disableVertexAttribArray(getImmediateContext(), index);
    }
    
    public static void setVertexAttribDivisor(CommandContext ctx, int index, int divisor) {
        getBackend().setVertexAttribDivisor(ctx, index, divisor);
    }
    
    @Deprecated
    public static void setVertexAttribDivisor(int index, int divisor) {
        setVertexAttribDivisor(getImmediateContext(), index, divisor);
    }
    
    @Deprecated
    public static String retrieveShaderInfoLog(int shader) {
        return getShaderInfoLog(getImmediateContext(), shader);
    }
    
    @Deprecated
    public static int locateUniformVariable(int program, CharSequence name) {
        return getUniformLocation(getImmediateContext(), program, name);
    }
    
    @Deprecated
    public static void assignUniformInteger(int location, int value) {
        setUniform1i(getImmediateContext(), location, value);
    }
    
    public static void setAttributeLocation(CommandContext ctx, int program, int index, CharSequence name) {
        getBackend().setAttributeLocation(ctx, program, index, name);
    }
    
    @Deprecated
    public static void bindAttributeLocation(int program, int index, CharSequence name) {
        setAttributeLocation(getImmediateContext(), program, index, name);
    }
    
    public static long createFenceSync(CommandContext ctx, int condition, int flags) {
        return getBackend().createFenceSync(ctx, condition, flags);
    }
    
    @Deprecated
    public static long createFenceSync(int condition, int flags) {
        return createFenceSync(getImmediateContext(), condition, flags);
    }
    
    @Deprecated
    public static int waitForSync(long sync, int flags, long timeout) {
        return getBackend().waitForSync(sync, flags, timeout);
    }
    
    public static void destroySync(CommandContext ctx, long sync) {
        getBackend().destroySync(ctx, sync);
    }
    
    @Deprecated
    public static void destroySync(long sync) {
        destroySync(getImmediateContext(), sync);
    }
    
    public static void clearTexImage(CommandContext ctx, int texture, int level, int format, int type, int[] data) {
        getBackend().clearTexImage(ctx, texture, level, format, type, data);
    }
    
    @Deprecated
    public static void clearTexImage(int texture, int level, int format, int type, int[] data) {
        clearTexImage(getImmediateContext(), texture, level, format, type, data);
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
    
    public static void uniformBlockBinding(CommandContext ctx, int program, int uniformBlockIndex, int uniformBlockBinding) {
        getBackend().uniformBlockBinding(ctx, program, uniformBlockIndex, uniformBlockBinding);
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
    
    public static void labelDebugObject(CommandContext ctx, int identifier, int name, String label) {
        getBackend().labelDebugObject(ctx, identifier, name, label);
    }
    
    @Deprecated
    public static void labelDebugObject(int identifier, int name, String label) {
        labelDebugObject(getImmediateContext(), identifier, name, label);
    }
    
    public static void enterDebugGroup(CommandContext ctx, int source, int id, CharSequence message) {
        getBackend().enterDebugGroup(ctx, source, id, message);
    }
    
    @Deprecated
    public static void enterDebugGroup(int source, int id, CharSequence message) {
        enterDebugGroup(getImmediateContext(), source, id, message);
    }
    
    public static void exitDebugGroup(CommandContext ctx) {
        getBackend().exitDebugGroup(ctx);
    }
    
    @Deprecated
    public static void exitDebugGroup() {
        exitDebugGroup(getImmediateContext());
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
        copyBufferSubData(getImmediateContext(), readTarget, writeTarget, readOffset, writeOffset, size);
    }
    
    @Deprecated
    public static void deleteVertexArray(int vertexArray) {
        getBackend().deleteVertexArray(vertexArray);
    }
    
    @Deprecated
    public static void flushMappedBufferRange(int target, long offset, long length) {
        flushMappedBufferRange(getImmediateContext(), target, offset, length);
    }
    
    @Deprecated
    public static void createBufferStorage(int target, long size, int flags) {
        bufferStorage(getImmediateContext(), target, size, flags);
    }
    
    @Deprecated
    public static void createBufferStorage(int target, java.nio.ByteBuffer data, int flags) {
        bufferStorage(getImmediateContext(), target, data, flags);
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
        copyTexSubImage2D(getImmediateContext(), target, level, xoffset, yoffset, x, y, width, height);
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
    
    public static void getIntegerv(CommandContext ctx, int pname, int[] params) {
        getBackend().getIntegerv(ctx, pname, params);
    }
    
    @Deprecated
    public static void glGetIntegerv(int pname, int[] params) {
        getIntegerv(getImmediateContext(), pname, params);
    }
    
    public static void getFloatv(CommandContext ctx, int pname, float[] params) {
        getBackend().getFloatv(ctx, pname, params);
    }
    
    @Deprecated
    public static void glGetFloatv(int pname, float[] params) {
        getFloatv(getImmediateContext(), pname, params);
    }
    
    public static void uploadTexture1D(CommandContext ctx, int target, int level, int internalformat, int width, int border, int format, int type, java.nio.ByteBuffer pixels) {
        getBackend().uploadTexture1D(ctx, target, level, internalformat, width, border, format, type, pixels);
    }
    
    @Deprecated
    public static void glTexImage1D(int target, int level, int internalformat, int width, int border, int format, int type, java.nio.ByteBuffer pixels) {
        uploadTexture1D(getImmediateContext(), target, level, internalformat, width, border, format, type, pixels);
    }
    
    @Deprecated
    public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, java.nio.ByteBuffer pixels) {
        getBackend().glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
    }
    
    public static void uploadTexture3D(CommandContext ctx, int target, int level, int internalformat, int width, int height, int depth, int border, int format, int type, java.nio.ByteBuffer pixels) {
        getBackend().uploadTexture3D(ctx, target, level, internalformat, width, height, depth, border, format, type, pixels);
    }
    
    @Deprecated
    public static void glTexImage3D(int target, int level, int internalformat, int width, int height, int depth, int border, int format, int type, java.nio.ByteBuffer pixels) {
        uploadTexture3D(getImmediateContext(), target, level, internalformat, width, height, depth, border, format, type, pixels);
    }
    
    @Deprecated
    public static void glUniformMatrix4fv(int location, boolean transpose, java.nio.FloatBuffer matrix) {
        getBackend().glUniformMatrix4fv(location, transpose, matrix);
    }
    
    @Deprecated
    public static void glUniformMatrix4fv(int location, boolean transpose, float[] matrix) {
        getBackend().glUniformMatrix4fv(location, transpose, matrix);
    }
    
    public static void copyTexImage2D(CommandContext ctx, int target, int level, int internalFormat, int x, int y, int width, int height, int border) {
        getBackend().copyTexImage2D(ctx, target, level, internalFormat, x, y, width, height, border);
    }
    
    @Deprecated
    public static void glCopyTexImage2D(int target, int level, int internalFormat, int x, int y, int width, int height, int border) {
        copyTexImage2D(getImmediateContext(), target, level, internalFormat, x, y, width, height, border);
    }
    
    @Deprecated
    public static void glUniform1f(int location, float v0) {
        getBackend().glUniform1f(location, v0);
    }
    
    @Deprecated
    public static void glUniform2f(int location, float v0, float v1) {
        setUniform2f(getImmediateContext(), location, v0, v1);
    }
    
    public static void setUniform2i(CommandContext ctx, int location, int v0, int v1) {
        getBackend().setUniform2i(ctx, location, v0, v1);
    }
    
    @Deprecated
    public static void glUniform2i(int location, int v0, int v1) {
        setUniform2i(getImmediateContext(), location, v0, v1);
    }
    
    @Deprecated
    public static void glUniform3f(int location, float v0, float v1, float v2) {
        setUniform3f(getImmediateContext(), location, v0, v1, v2);
    }
    
    @Deprecated
    public static void glUniform3i(int location, int v0, int v1, int v2) {
        setUniform3i(getImmediateContext(), location, v0, v1, v2);
    }
    
    @Deprecated
    public static void glUniform4f(int location, float v0, float v1, float v2, float v3) {
        setUniform4f(getImmediateContext(), location, v0, v1, v2, v3);
    }
    
    @Deprecated
    public static void glUniform4i(int location, int v0, int v1, int v2, int v3) {
        setUniform4i(getImmediateContext(), location, v0, v1, v2, v3);
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
        getBackend().texParameteriv(ctx, target, pname, params);
    }
    
    @Deprecated
    public static void glTexParameteriv(int target, int pname, int[] params) {
        texParameteriv(getImmediateContext(), target, pname, params);
    }
    
    @Deprecated
    public static void glTexParameteri(int target, int pname, int param) {
        texParameteri(getImmediateContext(), target, pname, param);
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
        getBackend().texParameteri(ctx, target, pname, param);
    }
    
    @Deprecated
    public static void glTexParameterf(int target, int pname, float param) {
        texParameterf(getImmediateContext(), target, pname, param);
    }
    
    @Deprecated
    public static String glGetProgramInfoLog(int program) {
        return getProgramInfoLog(getImmediateContext(), program);
    }
    
    @Deprecated
    public static String glGetShaderInfoLog(int shader) {
        return getShaderInfoLog(getImmediateContext(), shader);
    }
    
    @Deprecated
    public static void glDrawBuffers(int[] buffers) {
        drawBuffers(getImmediateContext(), buffers);
    }
    
    @Deprecated
    public static void glReadBuffer(int buffer) {
        setReadBuffer(getImmediateContext(), buffer);
    }
    
    public static void clearBufferfv(CommandContext ctx, int buffer, int drawbuffer, float[] values) {
        getBackend().clearBufferfv(ctx, buffer, drawbuffer, values);
    }
    
    @Deprecated
    public static void glClearBufferfv(int buffer, int drawbuffer, float[] values) {
        clearBufferfv(getImmediateContext(), buffer, drawbuffer, values);
    }
    
    public static void clearBufferiv(CommandContext ctx, int buffer, int drawbuffer, int[] values) {
        getBackend().clearBufferiv(ctx, buffer, drawbuffer, values);
    }
    
    @Deprecated
    public static void glClearBufferiv(int buffer, int drawbuffer, int[] values) {
        clearBufferiv(getImmediateContext(), buffer, drawbuffer, values);
    }
    
    public static void clearBufferuiv(CommandContext ctx, int buffer, int drawbuffer, int[] values) {
        getBackend().clearBufferuiv(ctx, buffer, drawbuffer, values);
    }
    
    @Deprecated
    public static void glClearBufferuiv(int buffer, int drawbuffer, int[] values) {
        clearBufferuiv(getImmediateContext(), buffer, drawbuffer, values);
    }
    
    public static String getActiveUniform(CommandContext ctx, int program, int index, int size, java.nio.IntBuffer type, java.nio.IntBuffer name) {
        return getBackend().getActiveUniform(ctx, program, index, size, type, name);
    }
    
    @Deprecated
    public static String glGetActiveUniform(int program, int index, int size, java.nio.IntBuffer type, java.nio.IntBuffer name) {
        return getActiveUniform(getImmediateContext(), program, index, size, type, name);
    }
    
    public static void readPixels(CommandContext ctx, int x, int y, int width, int height, int format, int type, float[] pixels) {
        getBackend().readPixels(ctx, x, y, width, height, format, type, pixels);
    }
    
    @Deprecated
    public static void glReadPixels(int x, int y, int width, int height, int format, int type, float[] pixels) {
        readPixels(getImmediateContext(), x, y, width, height, format, type, pixels);
    }
    
    @Deprecated
    public static void glBufferData(int target, float[] data, int usage) {
        bufferData(getImmediateContext(), target, data, usage);
    }
    
    @Deprecated
    public static void glBufferData(int target, int[] data, int usage) {
        bufferData(getImmediateContext(), target, data, usage);
    }
    
    @Deprecated
    public static void glBufferData(int target, java.nio.ByteBuffer data, int usage) {
        bufferData(getImmediateContext(), target, data, usage);
    }
    
    @Deprecated
    public static void glBufferData(int target, long size, int usage) {
        bufferData(getImmediateContext(), target, size, usage);
    }
    
    @Deprecated
    public static void glBufferSubData(int target, long offset, java.nio.ByteBuffer data) {
        bufferSubData(getImmediateContext(), target, offset, data);
    }
    
    @Deprecated
    public static void glBufferStorage(int target, long size, int flags) {
        bufferStorage(getImmediateContext(), target, size, flags);
    }
    
    @Deprecated
    public static void glBufferStorage(int target, java.nio.ByteBuffer data, int flags) {
        bufferStorage(getImmediateContext(), target, data, flags);
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
        return isBuffer(getImmediateContext(), buffer);
    }
    
    @Deprecated
    public static void glBindBufferBase(int target, int index, int buffer) {
        getBackend().glBindBufferBase(target, index, buffer);
    }
    
    public static void setVertexAttrib4f(CommandContext ctx, int index, float v0, float v1, float v2, float v3) {
        getBackend().setVertexAttrib4f(ctx, index, v0, v1, v2, v3);
    }
    
    @Deprecated
    public static void glVertexAttrib4f(int index, float v0, float v1, float v2, float v3) {
        setVertexAttrib4f(getImmediateContext(), index, v0, v1, v2, v3);
    }
    
    @Deprecated
    public static void glDetachShader(int program, int shader) {
        detachShader(getImmediateContext(), program, shader);
    }
    
    @Deprecated
    public static void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        framebufferTexture2D(getImmediateContext(), target, attachment, textarget, texture, level);
    }
    
    @Deprecated
    public static void glFramebufferTexture(int target, int attachment, int texture, int level) {
        getBackend().glFramebufferTexture(target, attachment, texture, level);
    }
    
    @Deprecated
    public static int glGetTexParameteri(int target, int pname) {
        return getTexParameteri(getImmediateContext(), target, pname);
    }
    
    public static void bindImageTexture(CommandContext ctx, int unit, int texture, int level, boolean layered, int layer, int access, int format) {
        getBackend().bindImageTexture(ctx, unit, texture, level, layered, layer, access, format);
    }
    
    @Deprecated
    public static void glBindImageTexture(int unit, int texture, int level, boolean layered, int layer, int access, int format) {
        bindImageTexture(getImmediateContext(), unit, texture, level, layered, layer, access, format);
    }
    
    public static int getMaxImageUnits(CommandContext ctx) {
        return getBackend().getMaxImageUnits(ctx);
    }
    
    @Deprecated
    public static int glGetMaxImageUnits() {
        return getMaxImageUnits(getImmediateContext());
    }
    
    public static void createBuffers(CommandContext ctx, int[] buffers) {
        getBackend().createBuffers(ctx, buffers);
    }
    
    @Deprecated
    public static void glGenBuffers(int[] buffers) {
        createBuffers(getImmediateContext(), buffers);
    }
    
    public static void clearBufferSubData(CommandContext ctx, int target, int internalformat, long offset, long size, int format, int type, int[] data) {
        getBackend().clearBufferSubData(ctx, target, internalformat, offset, size, format, type, data);
    }
    
    @Deprecated
    public static void glClearBufferSubData(int target, int internalformat, long offset, long size, int format, int type, int[] data) {
        clearBufferSubData(getImmediateContext(), target, internalformat, offset, size, format, type, data);
    }
    
    public static void getProgramiv(CommandContext ctx, int program, int pname, int[] params) {
        getBackend().getProgramiv(ctx, program, pname, params);
    }
    
    @Deprecated
    public static void glGetProgramiv(int program, int pname, int[] params) {
        getProgramiv(getImmediateContext(), program, pname, params);
    }
    
    @Deprecated
    public static void glDispatchCompute(int workX, int workY, int workZ) {
        dispatchCompute(getImmediateContext(), workX, workY, workZ);
    }
    
    public static void memoryBarrier(CommandContext ctx, int barriers) {
        getBackend().memoryBarrier(ctx, barriers);
    }
    
    @Deprecated
    public static void glMemoryBarrier(int barriers) {
        memoryBarrier(getImmediateContext(), barriers);
    }
    
    @Deprecated
    public static void glDisablei(int target, int index) {
        setIndexedEnabled(getImmediateContext(), target, index, false);
    }
    
    @Deprecated
    public static void glEnablei(int target, int index) {
        setIndexedEnabled(getImmediateContext(), target, index, true);
    }
    
    @Deprecated
    public static void glBlendFunc(int sfactor, int dfactor) {
        blendFunc(getImmediateContext(), sfactor, dfactor);
    }
    
    public static void blendFuncSeparatei(CommandContext ctx, int buffer, int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        getBackend().blendFuncSeparatei(ctx, buffer, srcRGB, dstRGB, srcAlpha, dstAlpha);
    }
    
    @Deprecated
    public static void glBlendFuncSeparatei(int buffer, int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        blendFuncSeparatei(getImmediateContext(), buffer, srcRGB, dstRGB, srcAlpha, dstAlpha);
    }
    
    @Deprecated
    public static int glGetUniformBlockIndex(int program, String uniformBlockName) {
        return getUniformBlockIndex(getImmediateContext(), program, uniformBlockName);
    }
    
    @Deprecated
    public static void glUniformBlockBinding(int program, int uniformBlockIndex, int uniformBlockBinding) {
        getBackend().glUniformBlockBinding(program, uniformBlockIndex, uniformBlockBinding);
    }
    
    public static int createSampler(CommandContext ctx) {
        return getBackend().createSampler(ctx);
    }
    
    @Deprecated
    public static int glGenSamplers() {
        return createSampler(getImmediateContext());
    }
    
    public static void deleteSampler(CommandContext ctx, int sampler) {
        getBackend().deleteSampler(ctx, sampler);
    }
    
    @Deprecated
    public static void glDeleteSamplers(int sampler) {
        deleteSampler(getImmediateContext(), sampler);
    }
    
    @Deprecated
    public static void glBindSampler(int unit, int sampler) {
        getBackend().glBindSampler(unit, sampler);
    }
    
    public static void bindSamplers(CommandContext ctx, int first, int[] samplers) {
        getBackend().bindSamplers(ctx, first, samplers);
    }
    
    @Deprecated
    public static void glBindSamplers(int first, int[] samplers) {
        bindSamplers(getImmediateContext(), first, samplers);
    }
    
    public static void setSamplerParameteri(CommandContext ctx, int sampler, int pname, int param) {
        getBackend().setSamplerParameteri(ctx, sampler, pname, param);
    }
    
    @Deprecated
    public static void glSamplerParameteri(int sampler, int pname, int param) {
        setSamplerParameteri(getImmediateContext(), sampler, pname, param);
    }
    
    public static void setSamplerParameterf(CommandContext ctx, int sampler, int pname, float param) {
        getBackend().setSamplerParameterf(ctx, sampler, pname, param);
    }
    
    @Deprecated
    public static void glSamplerParameterf(int sampler, int pname, float param) {
        setSamplerParameterf(getImmediateContext(), sampler, pname, param);
    }
    
    public static void setSamplerParameteriv(CommandContext ctx, int sampler, int pname, int[] params) {
        getBackend().setSamplerParameteriv(ctx, sampler, pname, params);
    }
    
    @Deprecated
    public static void glSamplerParameteriv(int sampler, int pname, int[] params) {
        setSamplerParameteriv(getImmediateContext(), sampler, pname, params);
    }
    
    @Deprecated
    public static int glGetInteger(int pname) {
        return getInteger(getImmediateContext(), pname);
    }
    
    @Deprecated
    public static void glDeleteBuffers(int buffer) {
        deleteBuffer(getImmediateContext(), buffer);
    }
    
    @Deprecated
    public static void glPolygonMode(int face, int mode) {
        setPolygonMode(getImmediateContext(), face, mode);
    }
    
    @Deprecated
    public static void glViewport(int x, int y, int width, int height) {
        setViewport(getImmediateContext(), x, y, width, height);
    }
    
    public static void dispatchComputeIndirect(CommandContext ctx, long offset) {
        getBackend().dispatchComputeIndirect(ctx, offset);
    }
    
    @Deprecated
    public static void glDispatchComputeIndirect(long offset) {
        dispatchComputeIndirect(getImmediateContext(), offset);
    }
    
    @Deprecated
    public static void glBindBuffer(int target, int buffer) {
        bindBuffer(getImmediateContext(), target, buffer);
    }
    
    public static String getString(CommandContext ctx, int name, int index) {
        return getBackend().getString(ctx, name, index);
    }
    
    @Deprecated
    public static String glGetStringi(int name, int index) {
        return getString(getImmediateContext(), name, index);
    }
    
    public static void copyImageSubData(CommandContext ctx, int srcName, int srcTarget, int srcLevel, int srcX, int srcY, int srcZ, 
                                        int dstName, int dstTarget, int dstLevel, int dstX, int dstY, int dstZ, 
                                        int width, int height, int depth) {
        getBackend().copyImageSubData(ctx, srcName, srcTarget, srcLevel, srcX, srcY, srcZ, 
                                      dstName, dstTarget, dstLevel, dstX, dstY, dstZ, 
                                      width, height, depth);
    }
    
    @Deprecated
    public static void glCopyImageSubData(int srcName, int srcTarget, int srcLevel, int srcX, int srcY, int srcZ, int dstName, int dstTarget, int dstLevel, int dstX, int dstY, int dstZ, int width, int height, int depth) {
        copyImageSubData(getImmediateContext(), srcName, srcTarget, srcLevel, srcX, srcY, srcZ, 
                        dstName, dstTarget, dstLevel, dstX, dstY, dstZ, width, height, depth);
    }
    
    public static int checkFramebufferStatus(CommandContext ctx, int target) {
        return getBackend().checkFramebufferStatus(ctx, target);
    }
    
    @Deprecated
    public static int glCheckFramebufferStatus(int target) {
        return checkFramebufferStatus(getImmediateContext(), target);
    }
    
    @Deprecated
    public static void glUniformMatrix3fv(int location, boolean transpose, java.nio.FloatBuffer value) {
        setUniformMatrix3fv(getImmediateContext(), location, transpose, value);
    }
    
    @Deprecated
    public static void glUniformMatrix3fv(int location, boolean transpose, float[] value) {
        setUniformMatrix3fv(getImmediateContext(), location, transpose, value);
    }
    
    @Deprecated
    public static void glClearColor(float r, float g, float b, float a) {
        setClearColor(getImmediateContext(), r, g, b, a);
    }
    
    @Deprecated
    public static int glGetAttribLocation(int program, CharSequence name) {
        return getAttributeLocation(getImmediateContext(), program, name);
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
    
    @Deprecated
    public static void glGenerateMipmap(int target) {
        generateMipmap(getImmediateContext(), target);
    }
    
    @Deprecated
    public static void glBlitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        blitFramebuffer(getImmediateContext(), srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }
    
    // DSA (Direct State Access) methods - ARB versions
    
    public static void generateTextureMipmapDSA(CommandContext ctx, int texture) {
        getBackend().generateTextureMipmapDSA(ctx, texture);
    }
    
    @Deprecated
    public static void glGenerateTextureMipmap(int texture) {
        generateTextureMipmapDSA(getImmediateContext(), texture);
    }
    
    public static void textureParameteri(CommandContext ctx, int texture, int pname, int param) {
        getBackend().textureParameteri(ctx, texture, pname, param);
    }
    
    @Deprecated
    public static void glTextureParameteri(int texture, int pname, int param) {
        textureParameteri(getImmediateContext(), texture, pname, param);
    }
    
    public static void textureParameterf(CommandContext ctx, int texture, int pname, float param) {
        getBackend().textureParameterf(ctx, texture, pname, param);
    }
    
    @Deprecated
    public static void glTextureParameterf(int texture, int pname, float param) {
        textureParameterf(getImmediateContext(), texture, pname, param);
    }
    
    public static void textureParameteriv(CommandContext ctx, int texture, int pname, int[] params) {
        getBackend().textureParameteriv(ctx, texture, pname, params);
    }
    
    @Deprecated
    public static void glTextureParameteriv(int texture, int pname, int[] params) {
        textureParameteriv(getImmediateContext(), texture, pname, params);
    }
    
    public static void namedFramebufferReadBuffer(CommandContext ctx, int framebuffer, int mode) {
        getBackend().namedFramebufferReadBuffer(ctx, framebuffer, mode);
    }
    
    @Deprecated
    public static void glNamedFramebufferReadBuffer(int framebuffer, int mode) {
        namedFramebufferReadBuffer(getImmediateContext(), framebuffer, mode);
    }
    
    public static void namedFramebufferDrawBuffers(CommandContext ctx, int framebuffer, int[] bufs) {
        getBackend().namedFramebufferDrawBuffers(ctx, framebuffer, bufs);
    }
    
    @Deprecated
    public static void glNamedFramebufferDrawBuffers(int framebuffer, int[] bufs) {
        namedFramebufferDrawBuffers(getImmediateContext(), framebuffer, bufs);
    }
    
    public static void clearNamedFramebufferfv(CommandContext ctx, int framebuffer, int buffer, int drawbuffer, float[] value) {
        getBackend().clearNamedFramebufferfv(ctx, framebuffer, buffer, drawbuffer, value);
    }
    
    @Deprecated
    public static void glClearNamedFramebufferfv(int framebuffer, int buffer, int drawbuffer, float[] value) {
        clearNamedFramebufferfv(getImmediateContext(), framebuffer, buffer, drawbuffer, value);
    }
    
    public static void clearNamedFramebufferiv(CommandContext ctx, int framebuffer, int buffer, int drawbuffer, int[] value) {
        getBackend().clearNamedFramebufferiv(ctx, framebuffer, buffer, drawbuffer, value);
    }
    
    @Deprecated
    public static void glClearNamedFramebufferiv(int framebuffer, int buffer, int drawbuffer, int[] value) {
        clearNamedFramebufferiv(getImmediateContext(), framebuffer, buffer, drawbuffer, value);
    }
    
    public static void clearNamedFramebufferuiv(CommandContext ctx, int framebuffer, int buffer, int drawbuffer, int[] value) {
        getBackend().clearNamedFramebufferuiv(ctx, framebuffer, buffer, drawbuffer, value);
    }
    
    @Deprecated
    public static void glClearNamedFramebufferuiv(int framebuffer, int buffer, int drawbuffer, int[] value) {
        clearNamedFramebufferuiv(getImmediateContext(), framebuffer, buffer, drawbuffer, value);
    }
    
    public static int getTextureParameteri(CommandContext ctx, int texture, int pname) {
        return getBackend().getTextureParameteri(ctx, texture, pname);
    }
    
    @Deprecated
    public static int glGetTextureParameteri(int texture, int pname) {
        return getTextureParameteri(getImmediateContext(), texture, pname);
    }
    
    /**
     * Copies a portion of a read framebuffer to a texture subregion using Direct State Access.
     * See {@link GraphicsBackend#copyTextureSubImage2D(CommandContext, int, int, int, int, int, int, int, int)}
     */
    public static void copyTextureSubImage2D(CommandContext ctx, int texture, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
        getBackend().copyTextureSubImage2D(ctx, texture, level, xoffset, yoffset, x, y, width, height);
    }
    
    @Deprecated
    public static void glCopyTextureSubImage2D(int texture, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
        copyTextureSubImage2D(getImmediateContext(), texture, level, xoffset, yoffset, x, y, width, height);
    }
    
    /**
     * Binds a texture to a specified texture unit using Direct State Access.
     * See {@link GraphicsBackend#bindTextureUnit(CommandContext, int, int)}
     */
    public static void bindTextureUnit(CommandContext ctx, int unit, int texture) {
        getBackend().bindTextureUnit(ctx, unit, texture);
    }
    
    @Deprecated
    public static void glBindTextureUnit(int unit, int texture) {
        bindTextureUnit(getImmediateContext(), unit, texture);
    }
    
    /**
     * Creates a new buffer object using Direct State Access.
     * See {@link GraphicsBackend#createBuffers(CommandContext)}
     */
    public static int createBuffers(CommandContext ctx) {
        return getBackend().createBuffers(ctx);
    }
    
    @Deprecated
    public static int glCreateBuffers() {
        return createBuffers(getImmediateContext());
    }
    
    /**
     * Uploads float array data to a named buffer using Direct State Access.
     * See {@link GraphicsBackend#namedBufferData(CommandContext, int, float[], int)}
     */
    public static void namedBufferData(CommandContext ctx, int buffer, float[] data, int usage) {
        getBackend().namedBufferData(ctx, buffer, data, usage);
    }
    
    @Deprecated
    public static void glNamedBufferData(int buffer, float[] data, int usage) {
        namedBufferData(getImmediateContext(), buffer, data, usage);
    }
    
    /**
     * Copies a rectangular region between two named framebuffers using Direct State Access.
     * See {@link GraphicsBackend#blitNamedFramebuffer(CommandContext, int, int, int, int, int, int, int, int, int, int, int, int)}
     */
    public static void blitNamedFramebuffer(CommandContext ctx, int readFramebuffer, int drawFramebuffer, int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        getBackend().blitNamedFramebuffer(ctx, readFramebuffer, drawFramebuffer, srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }
    
    @Deprecated
    public static void glBlitNamedFramebuffer(int readFramebuffer, int drawFramebuffer, int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        blitNamedFramebuffer(getImmediateContext(), readFramebuffer, drawFramebuffer, srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
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
    
    @Deprecated
    public static void glNamedFramebufferTexture(int framebuffer, int attachment, int texture, int level) {
        namedFramebufferTexture(getImmediateContext(), framebuffer, attachment, texture, level);
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
    
    @Deprecated
    public static int glCreateFramebuffers() {
        return createFramebuffers(getImmediateContext());
    }
    
    @Deprecated
    public static int glGenFramebuffers() {
        return getBackend().generateFramebufferObject();
    }
    
    @Deprecated
    public static void glDeleteFramebuffers(int framebuffer) {
        getBackend().destroyFramebufferObject(framebuffer);
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
    
    @Deprecated
    public static int glCreateTextures(int target) {
        return createTextures(getImmediateContext(), target);
    }
    
    // Additional rendering operations
    @Deprecated
    public static void glDrawElements(int mode, int count, int type, long indices) {
        drawElements(getImmediateContext(), mode, count, type, indices);
    }
    
    @Deprecated
    public static void glBlendEquation(int mode) {
        setBlendEquation(getImmediateContext(), mode);
    }
    
    @Deprecated
    public static void glClearDepth(double depth) {
        getBackend().glClearDepth(depth);
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
    
    @Deprecated
    public static int glGetFramebufferAttachmentParameteri(int target, int attachment, int pname) {
        return getFramebufferAttachmentParameteri(getImmediateContext(), target, attachment, pname);
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
     * Wrapper for enableVertexAttribArray with immediate context.
     */
    @Deprecated
    public static void glEnableVertexAttribArray(int index) {
        enableVertexAttribArray(getImmediateContext(), index);
    }
    
    /**
     * Creates a new shader program object.
     * Wrapper for constructProgramObject.
     */
    @Deprecated
    public static int glCreateProgram() {
        return constructProgramObject();
    }
    
    /**
     * Attaches a shader to a program.
     * Wrapper for attachShader with immediate context.
     */
    @Deprecated
    public static void glAttachShader(int program, int shader) {
        attachShader(getImmediateContext(), program, shader);
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
        CommandContext ctx = getImmediateContext();
        bindShaderProgram(ctx, program);
    }
    
    /**
     * Deletes a program object.
     * Wrapper for disposeProgramObject.
     */
    @Deprecated
    public static void glDeleteProgram(int program) {
        disposeProgramObject(program);
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
     * Wrapper for bindVertexBuffer with immediate context.
     */
    @Deprecated
    public static void glBindVertexBuffer(int bindingindex, int buffer, long offset, int stride) {
        bindVertexBuffer(getImmediateContext(), bindingindex, buffer, offset, stride);
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
        bindVertexArray(getImmediateContext(), array);
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
        return isEnabled(getImmediateContext(), cap);
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
     * Wrapper for isTexture with immediate context.
     */
    @Deprecated
    public static boolean glIsTexture(int texture) {
        return isTexture(getImmediateContext(), texture);
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
        setCullFaceMode(getImmediateContext(), mode);
    }
    
    /**
     * Generates a single texture name.
     */
    @Deprecated
    public static int glGenTextures() {
        return createTexture2D(getImmediateContext());
    }
    
    /**
     * Binds a named texture to a texturing target.
     */
    @Deprecated
    public static void glBindTexture(int target, int texture) {
        getBackend().bindTexture(target, texture);
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
}
