package net.vulkanic;

import net.vulkanic.backends.opengl.OpenGLBackend;
import net.vulkanic.backends.opengl.OpenGLCommandContext;

import java.nio.FloatBuffer;

/**
 * Main entry point for the Vulkanic Graphics Abstraction Layer.
 * Provides a unified API for graphics operations that can be backed by different graphics APIs.
 */
public class VulkanicAPI {
    private static GraphicsBackend backend;
    private static final CommandContext CTX = OpenGLCommandContext.IMMEDIATE;
    
    /**
     * Gets the immediate-mode command context for the current backend.
     * 
     * This is the primary way for game code to obtain a CommandContext for use with
     * VulkanicAPI methods. Game code should NEVER directly import or instantiate
     * backend-specific command contexts (e.g., OpenGLCommandContext).
     * 
     * @return CommandContext for immediate-mode rendering (OpenGL) or default context (Vulkan)
     */
    public static CommandContext getImmediateContext() {
        return CTX;
    }
    
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
    // NOTE: This method is now defined at the top of the class (line 25)
    // Removed duplicate definition
    
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
     * Updates a rectangular region of a 2D texture with new pixel data (pointer version).
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>{@code
     * CommandContext ctx = VulkanicAPI.getImmediateContext();
     * // Update 64x64 region at offset (128, 128)
     * VulkanicAPI.transferTexture2DSubregion(ctx, GL_TEXTURE_2D, 0, 128, 128, 64, 64,
     *     GL_RGBA, GL_UNSIGNED_BYTE, pixelDataPtr);
     * }</pre>
     * 
     * In OpenGL: Maps to glTexSubImage2D() with pointer
     * In Vulkan: Maps to vkCmdCopyBufferToImage() with staging buffer
     * 
     * @param ctx Command context for recording this command
     * @param tgt Texture target (e.g., GL_TEXTURE_2D)
     * @param lvl Mipmap level to update
     * @param xoff X offset into texture
     * @param yoff Y offset into texture
     * @param w Width of region to update
     * @param h Height of region to update
     * @param fmt Pixel data format (e.g., GL_RGBA)
     * @param typ Pixel data type (e.g., GL_UNSIGNED_BYTE)
     * @param pix Native pointer to pixel data
     */
    public static void transferTexture2DSubregion(CommandContext ctx, int tgt, int lvl, int xoff, int yoff, 
                                                  int w, int h, int fmt, int typ, long pix) {
        getBackend().transferTexture2DSubregion(ctx, tgt, lvl, xoff, yoff, w, h, fmt, typ, pix);
    }
    
    /**
     * Updates a rectangular region of a 2D texture with new pixel data (ByteBuffer version).
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>{@code
     * CommandContext ctx = VulkanicAPI.getImmediateContext();
     * ByteBuffer pixelData = ...;
     * VulkanicAPI.transferTexture2DSubregionBuf(ctx, GL_TEXTURE_2D, 0, 0, 0, width, height,
     *     GL_RGBA, GL_UNSIGNED_BYTE, pixelData);
     * }</pre>
     * 
     * In OpenGL: Maps to glTexSubImage2D() with ByteBuffer
     * In Vulkan: Maps to vkCmdCopyBufferToImage() with staging buffer
     * 
     * @param ctx Command context for recording this command
     * @param tgt Texture target (e.g., GL_TEXTURE_2D)
     * @param lvl Mipmap level to update
     * @param xoff X offset into texture
     * @param yoff Y offset into texture
     * @param w Width of region to update
     * @param h Height of region to update
     * @param fmt Pixel data format (e.g., GL_RGBA)
     * @param typ Pixel data type (e.g., GL_UNSIGNED_BYTE)
     * @param pix ByteBuffer containing pixel data
     */
    public static void transferTexture2DSubregionBuf(CommandContext ctx, int tgt, int lvl, int xoff, int yoff, 
                                                     int w, int h, int fmt, int typ, java.nio.ByteBuffer pix) {
        getBackend().transferTexture2DSubregionBuf(ctx, tgt, lvl, xoff, yoff, w, h, fmt, typ, pix);
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
    
    /**
     * Uploads GLSL shader source code to a shader object using CommandContext.
     * 
     * <p><b>Usage Example:</b></p>
     * <pre>{@code
     * CommandContext ctx = OpenGLCommandContext.IMMEDIATE;
     * int shaderId = VulkanicAPI.constructShaderObject(ctx, GL_VERTEX_SHADER);
     * VulkanicAPI.uploadShaderSource(ctx, shaderId, pointerAddress, 1, 0);
     * VulkanicAPI.compileShaderSource(ctx, shaderId);
     * }</pre>
     * 
     * @param ctx Command context for resource management
     * @param shader Shader object ID
     * @param pointerBufferAddress Native pointer to array of source string pointers
     * @param stringCount Number of source strings
     * @param lengthsPointer Native pointer to array of string lengths
     */
    public static void uploadShaderSource(CommandContext ctx, int shader, long pointerBufferAddress, int stringCount, long lengthsPointer) {
        getBackend().uploadShaderSource(ctx, shader, pointerBufferAddress, stringCount, lengthsPointer);
    }
    
    /**
     * Uploads GLSL shader source code to a shader object using CommandContext (native version).
     * 
     * <p><b>Usage Example:</b></p>
     * <pre>{@code
     * CommandContext ctx = OpenGLCommandContext.IMMEDIATE;
     * int shaderId = VulkanicAPI.constructShaderObject(ctx, GL_VERTEX_SHADER);
     * VulkanicAPI.uploadShaderSourceNative(ctx, shaderId, 1, stringsPtr, 0);
     * VulkanicAPI.compileShaderSource(ctx, shaderId);
     * }</pre>
     * 
     * @param ctx Command context for resource management
     * @param shader Shader object ID
     * @param count Number of source strings
     * @param strings Native pointer to array of source string pointers
     * @param length Native pointer to array of string lengths
     */
    public static void uploadShaderSourceNative(CommandContext ctx, int shader, int count, long strings, long length) {
        getBackend().uploadShaderSourceNative(ctx, shader, count, strings, length);
    }
    
    /**
     * Attaches a compiled shader object to a program object using CommandContext.
     * 
     * <p><b>Usage Example:</b></p>
     * <pre>{@code
     * CommandContext ctx = OpenGLCommandContext.IMMEDIATE;
     * int programId = VulkanicAPI.constructProgramObject(ctx);
     * int vertId = // ... create and compile vertex shader
     * int fragId = // ... create and compile fragment shader
     * VulkanicAPI.attachShaderToProgram(ctx, programId, vertId);
     * VulkanicAPI.attachShaderToProgram(ctx, programId, fragId);
     * VulkanicAPI.linkProgramBinary(ctx, programId);
     * }</pre>
     * 
     * @param ctx Command context for pipeline management
     * @param program Program object ID
     * @param shader Compiled shader object ID to attach
     */
    public static void attachShaderToProgram(CommandContext ctx, int program, int shader) {
        getBackend().attachShaderToProgram(ctx, program, shader);
    }
    
    /**
     * Links all attached shaders into an executable program using CommandContext.
     * 
     * <p><b>Usage Example:</b></p>
     * <pre>{@code
     * CommandContext ctx = OpenGLCommandContext.IMMEDIATE;
     * int programId = VulkanicAPI.constructProgramObject(ctx);
     * // ... attach shaders
     * VulkanicAPI.linkProgramBinary(ctx, programId);
     * // Check link status
     * }</pre>
     * 
     * @param ctx Command context for pipeline creation
     * @param program Program object ID to link
     */
    public static void linkProgramBinary(CommandContext ctx, int program) {
        getBackend().linkProgramBinary(ctx, program);
    }
    
    /**
     * Detaches a shader object from a program object using CommandContext.
     * 
     * <p><b>Usage Example:</b></p>
     * <pre>{@code
     * CommandContext ctx = OpenGLCommandContext.IMMEDIATE;
     * // After linking...
     * VulkanicAPI.glDetachShader(ctx, programId, vertId);
     * VulkanicAPI.glDetachShader(ctx, programId, fragId);
     * VulkanicAPI.disposeShaderObject(ctx, vertId);
     * VulkanicAPI.disposeShaderObject(ctx, fragId);
     * }</pre>
     * 
     * @param ctx Command context for resource management
     * @param program Program object ID
     * @param shader Shader object ID to detach
     */
    public static void glDetachShader(CommandContext ctx, int program, int shader) {
        getBackend().glDetachShader(ctx, program, shader);
    }
    
    /**
     * Binds a vertex attribute variable name to a specific attribute index.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.bindAttributeLocation(ctx, programID, 0, "vertexPosition");
     * </pre>
     * 
     * In OpenGL: Maps to glBindAttribLocation()
     * In Vulkan: Attribute locations are specified in SPIR-V shader code
     * 
     * @param ctx Command context for recording this command
     * @param program The program object ID
     * @param index The attribute index to bind to
     * @param name The name of the vertex attribute variable
     */
    public static void bindAttributeLocation(CommandContext ctx, int program, int index, CharSequence name) {
        getBackend().bindAttributeLocation(ctx, program, index, name);
    }
    
    /**
     * Queries the location of a vertex attribute variable in a linked program.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     int location = VulkanicAPI.getAttributeLocation(ctx, programID, "vertexPosition");
     * </pre>
     * 
     * In OpenGL: Maps to glGetAttribLocation()
     * In Vulkan: Attribute locations are defined in SPIR-V
     * 
     * @param ctx Command context for recording this command
     * @param program The linked program object ID
     * @param name The name of the vertex attribute variable
     * @return The attribute location/index, or -1 if not found
     */
    public static int getAttributeLocation(CommandContext ctx, int program, CharSequence name) {
        return getBackend().getAttributeLocation(ctx, program, name);
    }
    
    /**
     * Queries the location of a uniform variable in a linked program.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     int location = VulkanicAPI.locateUniformVariable(ctx, programID, "modelMatrix");
     * </pre>
     * 
     * In OpenGL: Maps to glGetUniformLocation()
     * In Vulkan: Uniforms are in descriptor sets
     * 
     * @param ctx Command context for recording this command
     * @param program The linked program object ID
     * @param name The name of the uniform variable
     * @return The uniform location, or -1 if not found
     */
    public static int locateUniformVariable(CommandContext ctx, int program, CharSequence name) {
        return getBackend().locateUniformVariable(ctx, program, name);
    }
    
    /**
     * Sets the value of a single integer uniform variable.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.assignUniformInteger(ctx, location, 42);
     * </pre>
     * 
     * In OpenGL: Maps to glUniform1i()
     * In Vulkan: Push constants or descriptor updates
     * 
     * @param ctx Command context for recording this command
     * @param location The uniform location
     * @param value The integer value to assign
     */
    public static void assignUniformInteger(CommandContext ctx, int location, int value) {
        getBackend().assignUniformInteger(ctx, location, value);
    }
    
    /**
     * Sets the value of a single float uniform variable.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.assignUniformFloat(ctx, location, 1.5f);
     * </pre>
     * 
     * In OpenGL: Maps to glUniform1f()
     * In Vulkan: Push constants or descriptor updates
     * 
     * @param ctx Command context for recording this command
     * @param location The uniform location
     * @param value The float value to assign
     */
    public static void assignUniformFloat(CommandContext ctx, int location, float value) {
        getBackend().assignUniformFloat(ctx, location, value);
    }
    
    /**
     * Sets the value of a 3-component float vector uniform.
     * 
     * Example usage:
     * <pre>{@code
     * CommandContext ctx = OpenGLCommandContext.IMMEDIATE;
     * int location = locateUniformVariable(ctx, program, "lightPosition");
     * assignUniformFloat3(ctx, location, 1.0f, 2.0f, 3.0f);
     * }</pre>
     * 
     * In OpenGL: Maps to glUniform3f()
     * In Vulkan: Push constants or descriptor updates
     * 
     * @param ctx Command context for recording this command
     * @param location The uniform location
     * @param x The x component
     * @param y The y component
     * @param z The z component
     */
    public static void assignUniformFloat3(CommandContext ctx, int location, float x, float y, float z) {
        getBackend().assignUniformFloat3(ctx, location, x, y, z);
    }
    
    /**
     * Sets the value of a 3-component integer vector uniform.
     * 
     * Example usage:
     * <pre>{@code
     * CommandContext ctx = OpenGLCommandContext.IMMEDIATE;
     * int location = locateUniformVariable(ctx, program, "gridSize");
     * assignUniformInteger3(ctx, location, 16, 16, 16);
     * }</pre>
     * 
     * In OpenGL: Maps to glUniform3i()
     * In Vulkan: Push constants or descriptor updates
     * 
     * @param ctx Command context for recording this command
     * @param location The uniform location
     * @param x The x component
     * @param y The y component
     * @param z The z component
     */
    public static void assignUniformInteger3(CommandContext ctx, int location, int x, int y, int z) {
        getBackend().assignUniformInteger3(ctx, location, x, y, z);
    }
    
    /**
     * Sets the value of a 4-component float vector uniform.
     * 
     * Example usage:
     * <pre>{@code
     * CommandContext ctx = OpenGLCommandContext.IMMEDIATE;
     * int location = locateUniformVariable(ctx, program, "color");
     * assignUniformFloat4(ctx, location, 1.0f, 0.5f, 0.0f, 1.0f);
     * }</pre>
     * 
     * In OpenGL: Maps to glUniform4f()
     * In Vulkan: Push constants or descriptor updates
     * 
     * @param ctx Command context for recording this command
     * @param location The uniform location
     * @param x The x component
     * @param y The y component
     * @param z The z component
     * @param w The w component
     */
    public static void assignUniformFloat4(CommandContext ctx, int location, float x, float y, float z, float w) {
        getBackend().assignUniformFloat4(ctx, location, x, y, z, w);
    }
    
    /**
     * Sets the value of a 4x4 matrix uniform.
     * 
     * Example usage:
     * <pre>{@code
     * CommandContext ctx = OpenGLCommandContext.IMMEDIATE;
     * int location = locateUniformVariable(ctx, program, "projectionMatrix");
     * FloatBuffer matrixBuffer = ...; // 16 floats
     * assignUniformMatrix4(ctx, location, false, matrixBuffer);
     * }</pre>
     * 
     * In OpenGL: Maps to glUniformMatrix4fv()
     * In Vulkan: Push constants or descriptor updates
     * 
     * @param ctx Command context for recording this command
     * @param location The uniform location
     * @param transpose Whether to transpose the matrix
     * @param value Buffer containing 16 floats
     */
    public static void assignUniformMatrix4(CommandContext ctx, int location, boolean transpose, java.nio.FloatBuffer value) {
        getBackend().assignUniformMatrix4(ctx, location, transpose, value);
    }
    
    /**
     * Enables a vertex attribute array.
     * 
     * Example usage:
     * <pre>{@code
     * CommandContext ctx = OpenGLCommandContext.IMMEDIATE;
     * activateVertexAttributeArray(ctx, 0);  // Enable position attribute
     * activateVertexAttributeArray(ctx, 1);  // Enable normal attribute
     * }</pre>
     * 
     * In OpenGL: Maps to glEnableVertexAttribArray()
     * In Vulkan: Vertex attributes are enabled as part of pipeline state
     * 
     * @param ctx Command context for recording this command
     * @param index The vertex attribute index to enable
     */
    public static void activateVertexAttributeArray(CommandContext ctx, int index) {
        getBackend().activateVertexAttributeArray(ctx, index);
    }
    
    /**
     * Sets a 2-component float vector uniform (vec2).
     * 
     * Example usage:
     * <pre>
     * CommandContext ctx = OpenGLCommandContext.IMMEDIATE;
     * int location = locateUniformVariable(ctx, programId, "uvOffset");
     * assignUniformFloat2(ctx, location, 0.5f, 0.5f);  // Set UV offset
     * </pre>
     * 
     * @param ctx Command context for recording this command
     * @param location The location of the uniform variable
     * @param x The first component (x coordinate)
     * @param y The second component (y coordinate)
     */
    public static void assignUniformFloat2(CommandContext ctx, int location, float x, float y) {
        getBackend().assignUniformFloat2(ctx, location, x, y);
    }
    
    /**
     * Sets a 2-component integer vector uniform (ivec2).
     * 
     * Example usage:
     * <pre>
     * CommandContext ctx = OpenGLCommandContext.IMMEDIATE;
     * int location = locateUniformVariable(ctx, programId, "gridSize");
     * assignUniformInteger2(ctx, location, 16, 16);  // Set 16x16 grid
     * </pre>
     * 
     * @param ctx Command context for recording this command
     * @param location The location of the uniform variable
     * @param x The first component
     * @param y The second component
     */
    public static void assignUniformInteger2(CommandContext ctx, int location, int x, int y) {
        getBackend().assignUniformInteger2(ctx, location, x, y);
    }
    
    /**
     * Copies a rectangular region from the framebuffer to a texture.
     * 
     * Example usage:
     * <pre>
     * CommandContext ctx = OpenGLCommandContext.IMMEDIATE;
     * // Copy from framebuffer (0,0) to texture at (0,0), size 256x256
     * copyTexture2DSubImage(ctx, GL_TEXTURE_2D, 0, 0, 0, 0, 0, 256, 256);
     * </pre>
     * 
     * @param ctx Command context for recording this command
     * @param target Texture target (e.g., GL_TEXTURE_2D)
     * @param level Mipmap level
     * @param xoffset X offset into the texture
     * @param yoffset Y offset into the texture
     * @param x X position in the framebuffer
     * @param y Y position in the framebuffer
     * @param width Width of the region
     * @param height Height of the region
     */
    public static void copyTexture2DSubImage(CommandContext ctx, int target, int level, int xoffset, int yoffset, 
                                             int x, int y, int width, int height) {
        getBackend().copyTexture2DSubImage(ctx, target, level, xoffset, yoffset, x, y, width, height);
    }
    
    /**
     * Reads pixel data from the framebuffer into CPU memory.
     * 
     * Example usage:
     * <pre>
     * CommandContext ctx = OpenGLCommandContext.IMMEDIATE;
     * float[] pixels = new float[width * height * 4];  // RGBA
     * readPixelsFromFramebuffer(ctx, 0, 0, width, height, GL_RGBA, GL_FLOAT, pixels);
     * </pre>
     * 
     * @param ctx Command context for recording this command
     * @param x X position of the first pixel
     * @param y Y position of the first pixel
     * @param width Width of the pixel rectangle
     * @param height Height of the pixel rectangle
     * @param format Pixel format (e.g., GL_RGBA)
     * @param type Data type (e.g., GL_FLOAT)
     * @param pixels Array to store the pixel data
     */
    public static void readPixelsFromFramebuffer(CommandContext ctx, int x, int y, int width, int height, 
                                                 int format, int type, float[] pixels) {
        getBackend().readPixelsFromFramebuffer(ctx, x, y, width, height, format, type, pixels);
    }
    
    /**
     * Inserts a memory barrier to ensure memory operations complete before subsequent commands.
     * 
     * Example usage:
     * <pre>{@code
     * CommandContext ctx = OpenGLCommandContext.IMMEDIATE;
     * // After image store operations in compute shader
     * setMemoryBarrier(ctx, GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
     * 
     * // Or use multiple barriers
     * setMemoryBarrier(ctx, GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL_SHADER_STORAGE_BARRIER_BIT);
     * }</pre>
     * 
     * @param ctx Command context for recording this command
     * @param barriers Bitfield of barrier types
     */
    public static void setMemoryBarrier(CommandContext ctx, int barriers) {
        getBackend().setMemoryBarrier(ctx, barriers);
    }
    
    /**
     * Clears a floating-point framebuffer attachment.
     * 
     * Example usage:
     * <pre>{@code
     * CommandContext ctx = OpenGLCommandContext.IMMEDIATE;
     * // Clear color buffer 0 to red
     * clearFloatBuffer(ctx, GL_COLOR, 0, new float[]{1.0f, 0.0f, 0.0f, 1.0f});
     * 
     * // Clear depth buffer to 1.0
     * clearFloatBuffer(ctx, GL_DEPTH, 0, new float[]{1.0f});
     * }</pre>
     * 
     * @param ctx Command context for recording this command
     * @param buffer The buffer to clear (GL_COLOR, GL_DEPTH)
     * @param drawbuffer The draw buffer index (for GL_COLOR)
     * @param values Array of float values to clear with
     */
    public static void clearFloatBuffer(CommandContext ctx, int buffer, int drawbuffer, float[] values) {
        getBackend().clearFloatBuffer(ctx, buffer, drawbuffer, values);
    }
    
    /**
     * Clears an integer framebuffer attachment.
     * 
     * Example usage:
     * <pre>{@code
     * CommandContext ctx = OpenGLCommandContext.IMMEDIATE;
     * // Clear integer color buffer to specific value
     * clearIntegerBuffer(ctx, GL_COLOR, 0, new int[]{255, 128, 64, 255});
     * 
     * // Clear stencil buffer to 0
     * clearIntegerBuffer(ctx, GL_STENCIL, 0, new int[]{0});
     * }</pre>
     * 
     * @param ctx Command context for recording this command
     * @param buffer The buffer to clear (GL_COLOR, GL_STENCIL)
     * @param drawbuffer The draw buffer index (for GL_COLOR)
     * @param values Array of integer values to clear with
     */
    public static void clearIntegerBuffer(CommandContext ctx, int buffer, int drawbuffer, int[] values) {
        getBackend().clearIntegerBuffer(ctx, buffer, drawbuffer, values);
    }
    
    /**
     * Configures the data format and location for an integer vertex attribute (no normalization).
     * 
     * Example usage:
     * <pre>{@code
     * CommandContext ctx = OpenGLCommandContext.IMMEDIATE;
     * // Integer attribute for bone indices (no normalization)
     * configureVertexAttributeIntegerPointer(ctx, 3, 4, GL_UNSIGNED_INT, 32, 12);
     * }</pre>
     * 
     * @param ctx Command context for recording this command
     * @param index The index of the vertex attribute
     * @param size Number of components (1-4)
     * @param type Data type (GL_INT, GL_UNSIGNED_INT, etc.)
     * @param stride Byte offset between consecutive attributes
     * @param pointer Offset of the first component
     */
    public static void configureVertexAttributeIntegerPointer(CommandContext ctx, int index, int size, int type,
                                                              int stride, long pointer) {
        getBackend().configureVertexAttributeIntegerPointer(ctx, index, size, type, stride, pointer);
    }
    
    /**
     * Sets the viewport for rendering (static/non-dynamic version).
     * 
     * Example usage:
     * <pre>
     * CommandContext ctx = OpenGLCommandContext.IMMEDIATE;
     * setStaticViewport(ctx, 0, 0, 1920, 1080);  // Full HD viewport
     * </pre>
     * 
     * @param ctx Command context for recording this command
     * @param x X coordinate of the lower-left corner
     * @param y Y coordinate of the lower-left corner
     * @param width Width of the viewport
     * @param height Height of the viewport
     */
    public static void setStaticViewport(CommandContext ctx, int x, int y, int width, int height) {
        getBackend().setStaticViewport(ctx, x, y, width, height);
    }
    
    /**
     * Configures the data format and location for a vertex attribute.
     * 
     * Example usage:
     * <pre>{@code
     * CommandContext ctx = OpenGLCommandContext.IMMEDIATE;
     * configureVertexAttributePointer(ctx, 0, 3, GL_FLOAT, false, 0, 0);  // Position attribute
     * }</pre>
     * 
     * @param ctx Command context for recording this command
     * @param index The index of the vertex attribute
     * @param size Number of components (1-4)
     * @param type Data type (GL_FLOAT, GL_INT, etc.)
     * @param normalized Whether to normalize fixed-point data
     * @param stride Byte offset between consecutive attributes
     * @param pointer Offset of the first component
     */
    public static void configureVertexAttributePointer(CommandContext ctx, int index, int size, int type,
                                                      boolean normalized, int stride, long pointer) {
        getBackend().configureVertexAttributePointer(ctx, index, size, type, normalized, stride, pointer);
    }
    
    /**
     * Disables a vertex attribute array.
     * 
     * Example usage:
     * <pre>{@code
     * CommandContext ctx = OpenGLCommandContext.IMMEDIATE;
     * deactivateVertexAttributeArray(ctx, 0);  // Disable attribute 0
     * }</pre>
     * 
     * @param ctx Command context for recording this command
     * @param index The index of the vertex attribute to disable
     */
    public static void deactivateVertexAttributeArray(CommandContext ctx, int index) {
        getBackend().deactivateVertexAttributeArray(ctx, index);
    }
    
    /**
     * Sets a 3x3 matrix uniform value (mat3).
     * 
     * Example usage:
     * <pre>{@code
     * CommandContext ctx = OpenGLCommandContext.IMMEDIATE;
     * FloatBuffer matrix = BufferUtils.createFloatBuffer(9);
     * // Fill matrix with normal transformation
     * assignUniformMatrix3(ctx, location, false, matrix);
     * }</pre>
     * 
     * @param ctx Command context for recording this command
     * @param location Location of the uniform variable
     * @param transpose Whether to transpose the matrix
     * @param value FloatBuffer containing 9 floats
     */
    public static void assignUniformMatrix3(CommandContext ctx, int location, boolean transpose, FloatBuffer value) {
        getBackend().assignUniformMatrix3(ctx, location, transpose, value);
    }
    
    /**
     * Sets a 3x3 matrix uniform value from an array (mat3).
     * 
     * Example usage:
     * <pre>{@code
     * CommandContext ctx = OpenGLCommandContext.IMMEDIATE;
     * float[] matrix = new float[9];
     * // Fill matrix with normal transformation
     * assignUniformMatrix3Array(ctx, location, false, matrix);
     * }</pre>
     * 
     * @param ctx Command context for recording this command
     * @param location Location of the uniform variable
     * @param transpose Whether to transpose the matrix
     * @param value Float array containing 9 floats
     */
    public static void assignUniformMatrix3Array(CommandContext ctx, int location, boolean transpose, float[] value) {
        getBackend().assignUniformMatrix3Array(ctx, location, transpose, value);
    }
    
    /**
     * Sets the blend equation for color blending.
     * 
     * Example usage:
     * <pre>{@code
     * CommandContext ctx = OpenGLCommandContext.IMMEDIATE;
     * setBlendEquation(ctx, GL_FUNC_ADD);  // Standard additive blending
     * }</pre>
     * 
     * @param ctx Command context for recording this command
     * @param mode The blend equation mode (GL_FUNC_ADD, GL_FUNC_SUBTRACT, etc.)
     */
    public static void setBlendEquation(CommandContext ctx, int mode) {
        getBackend().setBlendEquation(ctx, mode);
    }
    
    /**
     * Queries a shader parameter value.
     * 
     * Example usage:
     * <pre>{@code
     * int shader = constructShaderObject(CTX, GL_VERTEX_SHADER);
     * uploadShaderSource(CTX, shader, source, ...);
     * compileShaderSource(CTX, shader);
     * int status = queryShaderParameter(CTX, shader, GL_COMPILE_STATUS);
     * if (status == 0) {
     *     String log = retrieveShaderInfoLog(CTX, shader);
     *     System.err.println("Shader compilation failed: " + log);
     * }
     * }</pre>
     * 
     * @param ctx Command context for recording this command
     * @param shader The shader object ID
     * @param pname The parameter to query (e.g., GL_COMPILE_STATUS, GL_SHADER_TYPE)
     * @return The requested parameter value
     */
    public static int queryShaderParameter(CommandContext ctx, int shader, int pname) {
        return getBackend().queryShaderParameter(ctx, shader, pname);
    }
    
    /**
     * Retrieves the shader info log.
     * 
     * Example usage:
     * <pre>{@code
     * if (queryShaderParameter(CTX, shader, GL_COMPILE_STATUS) == 0) {
     *     String log = retrieveShaderInfoLog(CTX, shader);
     *     System.err.println("Compilation errors:\n" + log);
     * }
     * }</pre>
     * 
     * @param ctx Command context for recording this command
     * @param shader The shader object ID
     * @return The shader info log string
     */
    public static String retrieveShaderInfoLog(CommandContext ctx, int shader) {
        return getBackend().retrieveShaderInfoLog(ctx, shader);
    }
    
    /**
     * Binds a vertex array object (VAO).
     * 
     * Example usage:
     * <pre>{@code
     * int vao = createVertexArrayObject(CTX);
     * bindVertexArray(CTX, vao);
     * configureVertexAttributePointer(CTX, 0, 3, GL_FLOAT, false, 0, 0);
     * activateVertexAttributeArray(CTX, 0);
     * // ... render ...
     * bindVertexArray(CTX, 0); // Unbind
     * }</pre>
     * 
     * @param ctx Command context for recording this command
     * @param array The vertex array object ID to bind (0 to unbind)
     */
    public static void bindVertexArray(CommandContext ctx, int array) {
        getBackend().bindVertexArray(ctx, array);
    }
    
    /**
     * Creates multiple buffer objects.
     * 
     * Example usage:
     * <pre>{@code
     * int[] buffers = new int[3];
     * createBufferObjects(CTX, buffers);
     * // buffers[0], buffers[1], buffers[2] now contain buffer IDs
     * }</pre>
     * 
     * @param ctx Command context for recording this command
     * @param buffers Array to receive the generated buffer IDs
     */
    public static void createBufferObjects(CommandContext ctx, int[] buffers) {
        getBackend().createBufferObjects(ctx, buffers);
    }
    
    /**
     * Creates a single buffer object.
     * 
     * Example usage:
     * <pre>{@code
     * int buffer = createSingleBufferObject(CTX);
     * bindBuffer(CTX, GL_ARRAY_BUFFER, buffer);
     * fillBufferWithData(CTX, GL_ARRAY_BUFFER, data, GL_STATIC_DRAW);
     * }</pre>
     * 
     * @param ctx Command context for recording this command
     * @return The generated buffer object ID
     */
    public static int createSingleBufferObject(CommandContext ctx) {
        return getBackend().createSingleBufferObject(ctx);
    }
    
    /**
     * Configures a vertex attribute array with the specified format.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.configureVertexAttribute(ctx, 0, 3, GL_FLOAT, false, 12, 0);
     * </pre>
     * 
     * In OpenGL: Maps to glVertexAttribPointer()
     * In Vulkan: Maps to VkVertexInputAttributeDescription (baked into pipeline)
     * 
     * @param ctx Command context for recording this command
     * @param index The index of the vertex attribute to configure
     * @param size The number of components per vertex attribute (1-4)
     * @param type The data type of each component (e.g., GL_FLOAT, GL_INT)
     * @param normalized Whether fixed-point data should be normalized
     * @param stride Byte offset between consecutive vertex attributes
     * @param pointer Offset of the first component in the buffer
     */
    public static void configureVertexAttribute(CommandContext ctx, int index, int size, int type, boolean normalized, int stride, long pointer) {
        getBackend().configureVertexAttribute(ctx, index, size, type, normalized, stride, pointer);
    }
    
    /**
     * Configures a vertex attribute array with integer type (no normalization).
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.configureVertexAttributeInteger(ctx, 1, 4, GL_INT, 16, 0);
     * </pre>
     * 
     * In OpenGL: Maps to glVertexAttribIPointer()
     * In Vulkan: Maps to VkVertexInputAttributeDescription (baked into pipeline)
     * 
     * @param ctx Command context for recording this command
     * @param index The index of the vertex attribute to configure
     * @param size The number of components per vertex attribute (1-4)
     * @param type The data type of each component (e.g., GL_INT, GL_UNSIGNED_INT)
     * @param stride Byte offset between consecutive vertex attributes
     * @param pointer Offset of the first component in the buffer
     */
    public static void configureVertexAttributeInteger(CommandContext ctx, int index, int size, int type, int stride, long pointer) {
        getBackend().configureVertexAttributeInteger(ctx, index, size, type, stride, pointer);
    }
    
    /**
     * Enables a vertex attribute array for rendering.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.activateVertexAttribute(ctx, 0);
     * </pre>
     * 
     * In OpenGL: Maps to glEnableVertexAttribArray()
     * In Vulkan: Vertex input bindings are defined in pipeline state
     * 
     * @param ctx Command context for recording this command
     * @param index The index of the vertex attribute to enable
     */
    public static void activateVertexAttribute(CommandContext ctx, int index) {
        getBackend().activateVertexAttribute(ctx, index);
    }
    
    /**
     * Disables a vertex attribute array.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.deactivateVertexAttribute(ctx, 0);
     * </pre>
     * 
     * In OpenGL: Maps to glDisableVertexAttribArray()
     * In Vulkan: Vertex input bindings are defined in pipeline state
     * 
     * @param ctx Command context for recording this command
     * @param index The index of the vertex attribute to disable
     */
    public static void deactivateVertexAttribute(CommandContext ctx, int index) {
        getBackend().deactivateVertexAttribute(ctx, index);
    }
    
    /**
     * Sets the instance divisor for a vertex attribute.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.setVertexAttribDivisor(ctx, 3, 1);
     * </pre>
     * 
     * In OpenGL: Maps to glVertexAttribDivisor()
     * In Vulkan: Maps to VkVertexInputBindingDescription.inputRate
     * 
     * @param ctx Command context for recording this command
     * @param index The index of the vertex attribute
     * @param divisor The number of instances that will pass between updates (0 = per-vertex)
     */
    public static void setVertexAttribDivisor(CommandContext ctx, int index, int divisor) {
        getBackend().setVertexAttribDivisor(ctx, index, divisor);
    }
    
    /**
     * Sets a vec2 uniform variable from a float array.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     float[] texSize = {1024.0f, 768.0f};
     *     VulkanicAPI.assignUniformFloat2v(ctx, uniformLoc, texSize);
     * </pre>
     * 
     * In OpenGL: Maps to glUniform2fv()
     * In Vulkan: Maps to updating descriptor sets or push constants
     * 
     * @param ctx Command context for recording this command
     * @param location The uniform location (from locateUniformVariable)
     * @param value Array containing at least 2 float values (x, y)
     */
    public static void assignUniformFloat2v(CommandContext ctx, int location, float[] value) {
        getBackend().assignUniformFloat2v(ctx, location, value);
    }
    
    /**
     * Sets a vec3 uniform variable from a float array.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     float[] color = {1.0f, 0.5f, 0.2f};
     *     VulkanicAPI.assignUniformFloat3v(ctx, uniformLoc, color);
     * </pre>
     * 
     * In OpenGL: Maps to glUniform3fv()
     * In Vulkan: Maps to updating descriptor sets or push constants
     * 
     * @param ctx Command context for recording this command
     * @param location The uniform location (from locateUniformVariable)
     * @param value Array containing at least 3 float values (x, y, z)
     */
    public static void assignUniformFloat3v(CommandContext ctx, int location, float[] value) {
        getBackend().assignUniformFloat3v(ctx, location, value);
    }
    
    /**
     * Sets a vec4 uniform variable from a float array.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     float[] color = {1.0f, 0.5f, 0.2f, 1.0f};
     *     VulkanicAPI.assignUniformFloat4v(ctx, uniformLoc, color);
     * </pre>
     * 
     * In OpenGL: Maps to glUniform4fv()
     * In Vulkan: Maps to updating descriptor sets or push constants
     * 
     * @param ctx Command context for recording this command
     * @param location The uniform location (from locateUniformVariable)
     * @param value Array containing at least 4 float values (x, y, z, w)
     */
    public static void assignUniformFloat4v(CommandContext ctx, int location, float[] value) {
        getBackend().assignUniformFloat4v(ctx, location, value);
    }
    
    /**
     * Sets a mat4 uniform variable from a FloatBuffer.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     FloatBuffer matrixBuffer = ... // 16 floats
     *     VulkanicAPI.assignUniformMatrix4f(ctx, uniformLoc, matrixBuffer);
     * </pre>
     * 
     * In OpenGL: Maps to glUniformMatrix4fv()
     * In Vulkan: Maps to updating descriptor sets or push constants
     * 
     * @param ctx Command context for recording this command
     * @param location The uniform location (from locateUniformVariable)
     * @param matrix Buffer containing 16 float values in column-major order
     */
    public static void assignUniformMatrix4f(CommandContext ctx, int location, java.nio.FloatBuffer matrix) {
        getBackend().assignUniformMatrix4f(ctx, location, matrix);
    }
    
    /**
     * Sets a mat4 uniform variable with optional transpose.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     FloatBuffer matrixBuffer = ... // 16 floats
     *     VulkanicAPI.assignUniformMatrix4fv(ctx, uniformLoc, false, matrixBuffer);
     * </pre>
     * 
     * In OpenGL: Maps to glUniformMatrix4fv()
     * In Vulkan: Maps to updating descriptor sets or push constants
     * 
     * @param ctx Command context for recording this command
     * @param location The uniform location (from locateUniformVariable)
     * @param transpose Whether to transpose the matrix
     * @param value Buffer containing 16 float values
     */
    public static void assignUniformMatrix4fv(CommandContext ctx, int location, boolean transpose, java.nio.FloatBuffer value) {
        getBackend().assignUniformMatrix4fv(ctx, location, transpose, value);
    }
    
    /**
     * Locates a uniform block by name in a shader program.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     int blockIndex = VulkanicAPI.locateUniformBlock(ctx, programId, "Matrices");
     * </pre>
     * 
     * In OpenGL: Maps to glGetUniformBlockIndex()
     * In Vulkan: Uniform blocks map to descriptor set layouts
     * 
     * @param ctx Command context for recording this command
     * @param program The shader program ID
     * @param uniformBlockName The name of the uniform block in the shader
     * @return The uniform block index, or -1 if not found
     */
    public static int locateUniformBlock(CommandContext ctx, int program, String uniformBlockName) {
        return getBackend().locateUniformBlock(ctx, program, uniformBlockName);
    }
    
    /**
     * Binds a uniform block to a binding point.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.bindUniformBlock(ctx, programId, blockIndex, 0);
     * </pre>
     * 
     * In OpenGL: Maps to glUniformBlockBinding()
     * In Vulkan: Maps to descriptor set binding configuration
     * 
     * @param ctx Command context for recording this command
     * @param program The shader program ID
     * @param uniformBlockIndex The uniform block index (from locateUniformBlock)
     * @param uniformBlockBinding The binding point to associate with
     */
    public static void bindUniformBlock(CommandContext ctx, int program, int uniformBlockIndex, int uniformBlockBinding) {
        getBackend().bindUniformBlock(ctx, program, uniformBlockIndex, uniformBlockBinding);
    }
    
    /**
     * Attaches a range of a buffer to a uniform buffer binding point.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.attachUniformBufferRange(ctx, GL_UNIFORM_BUFFER, 0, bufferId, 0, 256);
     * </pre>
     * 
     * In OpenGL: Maps to glBindBufferRange(GL_UNIFORM_BUFFER, ...)
     * In Vulkan: Maps to descriptor set updates with buffer info
     * 
     * @param ctx Command context for recording this command
     * @param target The buffer target (GL_UNIFORM_BUFFER)
     * @param index The binding point index
     * @param buffer The buffer object ID
     * @param offset Offset into the buffer in bytes
     * @param size Size of the buffer range in bytes
     */
    public static void attachUniformBufferRange(CommandContext ctx, int target, int index, int buffer, long offset, long size) {
        getBackend().attachUniformBufferRange(ctx, target, index, buffer, offset, size);
    }
    
    /**
     * Allocates immutable buffer storage with specific usage flags.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.glBufferStorage(ctx, GL_ARRAY_BUFFER, 1024 * 1024, 
     *         GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT | GL_MAP_WRITE_BIT);
     * </pre>
     * 
     * In OpenGL: Maps to glBufferStorage()
     * In Vulkan: Maps to vkCreateBuffer() with appropriate usage flags
     * 
     * @param ctx Command context for recording this command
     * @param target Buffer binding target (e.g., GL_ARRAY_BUFFER)
     * @param size Size of the buffer in bytes
     * @param flags Storage flags (e.g., GL_MAP_PERSISTENT_BIT | GL_MAP_WRITE_BIT)
     */
    public static void glBufferStorage(CommandContext ctx, int target, long size, int flags) {
        getBackend().glBufferStorage(ctx, target, size, flags);
    }
    
    /**
     * Allocates immutable buffer storage with initial data.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     ByteBuffer vertexData = ...;
     *     VulkanicAPI.glBufferStorage(ctx, GL_ARRAY_BUFFER, vertexData, 0);
     * </pre>
     * 
     * In OpenGL: Maps to glBufferStorage()
     * In Vulkan: Maps to vkCreateBuffer() followed by vkCmdCopyBuffer()
     * 
     * @param ctx Command context for recording this command
     * @param target Buffer binding target (e.g., GL_ARRAY_BUFFER)
     * @param data ByteBuffer containing initial data
     * @param flags Storage flags (e.g., GL_DYNAMIC_STORAGE_BIT)
     */
    public static void glBufferStorage(CommandContext ctx, int target, java.nio.ByteBuffer data, int flags) {
        getBackend().glBufferStorage(ctx, target, data, flags);
    }
    
    /**
     * Maps a range of buffer memory for CPU access.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     ByteBuffer mapped = VulkanicAPI.glMapBufferRange(ctx, GL_ARRAY_BUFFER, 0, 1024, 
     *         GL_MAP_WRITE_BIT | GL_MAP_INVALIDATE_BUFFER_BIT);
     *     mapped.putFloat(1.0f);
     * </pre>
     * 
     * In OpenGL: Maps to glMapBufferRange()
     * In Vulkan: Maps to vkMapMemory()
     * 
     * @param ctx Command context for recording this command
     * @param target Buffer binding target (e.g., GL_ARRAY_BUFFER)
     * @param offset Offset into the buffer in bytes
     * @param length Length of the range to map in bytes
     * @param access Access flags (e.g., GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT)
     * @return ByteBuffer representing the mapped memory region
     */
    public static java.nio.ByteBuffer glMapBufferRange(CommandContext ctx, int target, long offset, long length, int access) {
        return getBackend().glMapBufferRange(ctx, target, offset, length, access);
    }
    
    /**
     * Dispatches compute shader work groups.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.glDispatchCompute(ctx, 16, 16, 1);
     * </pre>
     * 
     * In OpenGL: Maps to glDispatchCompute()
     * In Vulkan: Maps to vkCmdDispatch()
     * 
     * @param ctx Command context for recording this command
     * @param workX Number of work groups in X dimension
     * @param workY Number of work groups in Y dimension
     * @param workZ Number of work groups in Z dimension
     */
    public static void glDispatchCompute(CommandContext ctx, int workX, int workY, int workZ) {
        getBackend().glDispatchCompute(ctx, workX, workY, workZ);
    }
    
    /**
     * Attaches a 2D texture to a framebuffer attachment point.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.glFramebufferTexture2D(ctx, GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
     *         GL_TEXTURE_2D, colorTexture, 0);
     * </pre>
     * 
     * In OpenGL: Maps to glFramebufferTexture2D()
     * In Vulkan: Specified in VkFramebufferCreateInfo during framebuffer creation
     * 
     * @param ctx Command context for recording this command
     * @param target Framebuffer target (e.g., GL_FRAMEBUFFER)
     * @param attachment Attachment point (e.g., GL_COLOR_ATTACHMENT0, GL_DEPTH_ATTACHMENT)
     * @param textarget Texture target (e.g., GL_TEXTURE_2D, GL_TEXTURE_CUBE_MAP_POSITIVE_X)
     * @param texture Texture object ID
     * @param level Mipmap level to attach
     */
    public static void glFramebufferTexture2D(CommandContext ctx, int target, int attachment, int textarget, int texture, int level) {
        getBackend().glFramebufferTexture2D(ctx, target, attachment, textarget, texture, level);
    }
    
    /**
     * Binds a texture to an image unit for shader image load/store operations.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.glBindImageTexture(ctx, 0, texture, 0, false, 0, GL_WRITE_ONLY, GL_RGBA8);
     * </pre>
     * 
     * In OpenGL: Maps to glBindImageTexture()
     * In Vulkan: Maps to descriptor set updates with VK_DESCRIPTOR_TYPE_STORAGE_IMAGE
     * 
     * @param ctx Command context for recording this command
     * @param unit Image unit index
     * @param texture Texture object ID
     * @param level Mipmap level
     * @param layered Whether to bind the entire texture array
     * @param layer Specific layer to bind (if not layered)
     * @param access Access mode (e.g., GL_READ_ONLY, GL_WRITE_ONLY, GL_READ_WRITE)
     * @param format Internal format (e.g., GL_RGBA8)
     */
    public static void glBindImageTexture(CommandContext ctx, int unit, int texture, int level, boolean layered, int layer, int access, int format) {
        getBackend().glBindImageTexture(ctx, unit, texture, level, layered, layer, access, format);
    }
    
    /**
     * Binds a sampler object to a texture unit.
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>
     *     CommandContext ctx = VulkanicAPI.getImmediateContext();
     *     VulkanicAPI.glBindSampler(ctx, 0, samplerObject);
     * </pre>
     * 
     * In OpenGL: Maps to glBindSampler()
     * In Vulkan: Samplers are specified in descriptor set layouts
     * 
     * @param ctx Command context for recording this command
     * @param unit Texture unit index
     * @param sampler Sampler object ID (0 to unbind)
     */
    public static void glBindSampler(CommandContext ctx, int unit, int sampler) {
        getBackend().glBindSampler(ctx, unit, sampler);
    }
    
    // ================================================================================
    // DEPRECATED METHODS - Legacy API without CommandContext
    // ================================================================================
    
    
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
        return mapNamedBufferRangeDSA(getImmediateContext(), buffer, offset, length, access);
    }
    
    @Deprecated
    public static void unmapNamedBufferDSA(int buffer) {
        unmapNamedBufferDSA(getImmediateContext(), buffer);
    }
    
    @Deprecated
    public static void flushMappedNamedBufferRangeDSA(int buffer, long offset, long length) {
        flushMappedNamedBufferRangeDSA(getImmediateContext(), buffer, offset, length);
    }
    
    @Deprecated
    public static void copyNamedBufferSubDataDSA(int readBuffer, int writeBuffer, long readOffset, long writeOffset, long size) {
        copyNamedBufferSubDataDSA(getImmediateContext(), readBuffer, writeBuffer, readOffset, writeOffset, size);
    }
    
    // Direct State Access framebuffer operations
    @Deprecated
    public static int createFramebufferDSA() {
        return createFramebufferDSA(getImmediateContext());
    }
    
    @Deprecated
    public static void namedFramebufferTextureDSA(int framebuffer, int attachment, int texture, int level) {
        namedFramebufferTextureDSA(getImmediateContext(), framebuffer, attachment, texture, level);
    }
    
    @Deprecated
    public static void blitNamedFramebufferDSA(int readFramebuffer, int drawFramebuffer, int srcX0, int srcY0, int srcX1, int srcY1,
                                                int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        blitNamedFramebufferDSA(getImmediateContext(), readFramebuffer, drawFramebuffer, srcX0, srcY0, srcX1, srcY1,
                                              dstX0, dstY0, dstX1, dstY1, mask, filter);
    }
    
    
    @Deprecated
    public static void configureTextureParameter(int target, int pname, int param) {
        getBackend().configureTextureParameter(target, pname, param);
    }
    
    
    @Deprecated
    public static void transferTexture2DImage(int tgt, int lvl, int intfmt, int w, int h, int bdr, int fmt, int typ, java.nio.ByteBuffer pix) {
        getBackend().transferTexture2DImage(tgt, lvl, intfmt, w, h, bdr, fmt, typ, pix);
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
    public static java.nio.ByteBuffer mapBufferRegion(int tgt, int off, int len, int acc) {
        return getBackend().mapBufferRegion(tgt, off, len, acc);
    }
    
    @Deprecated
    public static void unmapBufferData(int tgt) {
        getBackend().unmapBufferData(tgt);
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
    public static void assignUniformFloat3(int location, float x, float y, float z) {
        getBackend().assignUniformFloat3(location, x, y, z);
    }
    
    @Deprecated
    public static void assignUniformFloat4(int location, float x, float y, float z, float w) {
        getBackend().assignUniformFloat4(location, x, y, z, w);
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
        glGetIntegerv(CTX, pname, params);
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
    public static void glTexParameterf(int target, int pname, float param) {
        getBackend().glTexParameterf(target, pname, param);
    }
    
    @Deprecated
    public static String glGetProgramInfoLog(int program) {
        return glGetProgramInfoLog(CTX, program);
    }
    
    @Deprecated
    public static String glGetShaderInfoLog(int shader) {
        return glGetShaderInfoLog(CTX, shader);
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
    public static void glClearBufferuiv(int buffer, int drawbuffer, int[] values) {
        getBackend().glClearBufferuiv(buffer, drawbuffer, values);
    }
    
    @Deprecated
    public static String glGetActiveUniform(int program, int index, int size, java.nio.IntBuffer type, java.nio.IntBuffer name) {
        return getBackend().glGetActiveUniform(program, index, size, type, name);
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
        return glGetInteger(CTX, pname);
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
        return glGetStringi(CTX, name, index);
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
        return getBackend().generateFramebufferObject(CTX);
    }
    
    @Deprecated
    public static void glDeleteFramebuffers(int framebuffer) {
        getBackend().destroyFramebufferObject(CTX, framebuffer);
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
        bindAttributeLocation(getImmediateContext(), program, index, name);
    }
    
    /**
     * Configures a vertex attribute pointer.
     * Wrapper for configureVertexAttribute.
     */
    @Deprecated
    public static void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long pointer) {
        configureVertexAttribute(CTX, index, size, type, normalized, stride, pointer);
    }
    
    /**
     * Enables a vertex attribute array.
     * Wrapper for activateVertexAttribute.
     */
    @Deprecated
    public static void glEnableVertexAttribArray(int index) {
        activateVertexAttribute(CTX, index);
    }
    
    /**
     * Returns a parameter from a program object.
     * Wrapper for queryProgramParameter.
     */
    @Deprecated
    public static int glGetProgrami(int program, int pname) {
        return queryProgramParameter(OpenGLCommandContext.IMMEDIATE, program, pname);
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
        return locateUniformVariable(CTX, program, name);
    }
    
    /**
     * Sets the value of a uniform variable.
     * Wrapper for assignUniformInteger.
     */
    @Deprecated
    public static void glUniform1i(int location, int value) {
        assignUniformInteger(CTX, location, value);
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
        deactivateVertexAttribute(CTX, index);
    }
    
    /**
     * Defines an array of generic vertex attribute data with integer data (GL20+).
     * Specifies the data format for integer vertex attributes.
     */
    @Deprecated
    public static void glVertexAttribIPointer(int index, int size, int type, int stride, long pointer) {
        configureVertexAttributeInteger(CTX, index, size, type, stride, pointer);
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
    
    // ===========================
    // Phase 12: Shader Query & State Retrieval Methods (CommandContext-aware)
    // ===========================
    
    /**
     * Queries a shader program parameter.
     * 
     * @param ctx Command recording context
     * @param program Shader program ID
     * @param pname Parameter name (e.g., GL_LINK_STATUS, GL_DELETE_STATUS, GL_VALIDATE_STATUS)
     * @return The queried parameter value
     * 
     * Example usage:
     * <pre>{@code
     * int linkStatus = VulkanicAPI.queryProgramParameter(CTX, programId, GL_LINK_STATUS);
     * if (linkStatus == GL_FALSE) {
     *     String log = VulkanicAPI.retrieveProgramInfoLog(CTX, programId);
     *     throw new RuntimeException("Link failed: " + log);
     * }
     * }</pre>
     */
    public static int queryProgramParameter(CommandContext ctx, int program, int pname) {
        return getBackend().queryProgramParameter(ctx, program, pname);
    }
    
    /**
     * Retrieves the information log for a shader program.
     * 
     * @param ctx Command recording context
     * @param program Shader program ID
     * @return The program info log as a string
     * 
     * Example usage:
     * <pre>{@code
     * String log = VulkanicAPI.retrieveProgramInfoLog(CTX, programId);
     * if (!log.isEmpty()) {
     *     System.err.println("Program log: " + log);
     * }
     * }</pre>
     */
    public static String retrieveProgramInfoLog(CommandContext ctx, int program) {
        return getBackend().retrieveProgramInfoLog(ctx, program);
    }
    
    /**
     * Queries an integer state value from the graphics API.
     * 
     * @param ctx Command recording context
     * @param pname Parameter name (e.g., GL_CURRENT_PROGRAM, GL_VERTEX_ARRAY_BINDING)
     * @return The queried integer value
     * 
     * Example usage:
     * <pre>{@code
     * int currentProgram = VulkanicAPI.queryIntegerState(CTX, GL_CURRENT_PROGRAM);
     * int boundVAO = VulkanicAPI.queryIntegerState(CTX, GL_VERTEX_ARRAY_BINDING);
     * }</pre>
     */
    public static int queryIntegerState(CommandContext ctx, int pname) {
        return getBackend().queryIntegerState(ctx, pname);
    }
    
    /**
     * Activates a shader program for use in rendering operations.
     * 
     * Note: This is a convenience wrapper around bindShaderProgram(ctx, program).
     * 
     * @param ctx Command recording context
     * @param program Shader program ID to activate (0 to unbind)
     * 
     * Example usage:
     * <pre>{@code
     * VulkanicAPI.activateShaderProgram(CTX, myProgramId);
     * // Perform draw calls
     * VulkanicAPI.activateShaderProgram(CTX, 0); // Unbind
     * }</pre>
     */
    public static void activateShaderProgram(CommandContext ctx, int program) {
        getBackend().activateShaderProgram(ctx, program);
    }
    
    /**
     * Deletes a shader program and releases its resources.
     * 
     * Note: This is a convenience wrapper around disposeProgramObject(ctx, program).
     * 
     * @param ctx Command recording context
     * @param program Shader program ID to delete
     * 
     * Example usage:
     * <pre>{@code
     * VulkanicAPI.destroyShaderProgram(CTX, oldProgramId);
     * }</pre>
     */
    public static void destroyShaderProgram(CommandContext ctx, int program) {
        getBackend().destroyShaderProgram(ctx, program);
    }
    
    // Phase 14: Additional resource management and state query methods
    
    /**
     * Deletes a vertex array object and releases its resources.
     * 
     * @param ctx Command recording context
     * @param array Vertex array object ID to delete
     * 
     * Example usage:
     * <pre>{@code
     * VulkanicAPI.deleteVertexArray(CTX, vaoId);
     * }</pre>
     */
    public static void deleteVertexArray(CommandContext ctx, int array) {
        getBackend().deleteVertexArray(ctx, array);
    }
    
    /**
     * Queries floating-point state values.
     * 
     * @param ctx Command recording context
     * @param pname Parameter name (e.g., GL_COLOR_CLEAR_VALUE)
     * @param params Array to receive the queried values
     * 
     * Example usage:
     * <pre>{@code
     * float[] clearColor = new float[4];
     * VulkanicAPI.queryFloatState(CTX, GL_COLOR_CLEAR_VALUE, clearColor);
     * }</pre>
     */
    public static void queryFloatState(CommandContext ctx, int pname, float[] params) {
        getBackend().queryFloatState(ctx, pname, params);
    }
    
    /**
     * Specifies which color buffer to read from.
     * 
     * @param ctx Command recording context
     * @param mode Color buffer to read from (e.g., GL_COLOR_ATTACHMENT0)
     * 
     * Example usage:
     * <pre>{@code
     * VulkanicAPI.setReadBuffer(CTX, GL_COLOR_ATTACHMENT0);
     * }</pre>
     */
    public static void setReadBuffer(CommandContext ctx, int mode) {
        getBackend().setReadBuffer(ctx, mode);
    }
    
    /**
     * Specifies which color buffers to draw into.
     * 
     * @param ctx Command recording context
     * @param bufs Array of buffer constants
     * 
     * Example usage:
     * <pre>{@code
     * int[] drawBuffers = {GL_COLOR_ATTACHMENT0, GL_COLOR_ATTACHMENT1};
     * VulkanicAPI.setDrawBuffers(CTX, drawBuffers);
     * }</pre>
     */
    public static void setDrawBuffers(CommandContext ctx, int[] bufs) {
        getBackend().setDrawBuffers(ctx, bufs);
    }
    
    /**
     * Creates a fence sync object for GPU-CPU synchronization.
     * 
     * @param ctx Command recording context
     * @param condition Must be GL_SYNC_GPU_COMMANDS_COMPLETE
     * @param flags Currently unused, must be 0
     * @return Handle to the sync object (0 on failure)
     * 
     * Example usage:
     * <pre>{@code
     * long fence = VulkanicAPI.createFenceSync(CTX, GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
     * // Later: check if fence is signaled
     * }</pre>
     */
    public static long createFenceSync(CommandContext ctx, int condition, int flags) {
        return getBackend().createFenceSync(ctx, condition, flags);
    }
    
    /**
     * Waits for a sync object to become signaled (CommandContext-aware, Vulkan-compatible).
     * 
     * This method blocks the client until the sync object becomes signaled or the timeout expires.
     * Used for GPU-CPU synchronization to ensure GPU operations complete before CPU accesses results.
     * 
     * OpenGL: Uses glClientWaitSync() to wait for fence object
     * Vulkan: Will use vkWaitForFences() or vkGetFenceStatus()
     * 
     * @param ctx The command context for synchronization
     * @param sync The sync object handle (from createFenceSync)
     * @param flags Flags controlling wait behavior (e.g., GL_SYNC_FLUSH_COMMANDS_BIT = 1)
     * @param timeout Maximum time to wait in nanoseconds (use Long.MAX_VALUE for infinite wait)
     * @return Status value (GL_ALREADY_SIGNALED, GL_TIMEOUT_EXPIRED, GL_CONDITION_SATISFIED, or GL_WAIT_FAILED)
     * 
     * Example usage:
     * <pre>{@code
     * long fence = VulkanicAPI.createFenceSync(CTX, GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
     * int result = VulkanicAPI.waitForSync(CTX, fence, GL_SYNC_FLUSH_COMMANDS_BIT, 1_000_000_000L);
     * if (result == GL_CONDITION_SATISFIED) {
     *     // GPU work is complete, safe to access results
     * }
     * }</pre>
     */
    public static int waitForSync(CommandContext ctx, long sync, int flags, long timeout) {
        return getBackend().waitForSync(ctx, sync, flags, timeout);
    }
    
    /**
     * Gets the name of the graphics backend currently in use.
     * 
     * This method returns a human-readable string identifying which graphics API is being used.
     * This is useful for displaying in debug screens or for diagnostic purposes.
     * 
     * @return The name of the graphics backend (e.g., "OpenGL", "Vulkan")
     * 
     * Example usage:
     * <pre>{@code
     * String backendName = VulkanicAPI.getBackendName();
     * System.out.println("Using " + backendName + " backend");
     * }</pre>
     */
    public static String getBackendName() {
        return getBackend().getBackendName();
    }
    
    // Phase 18: Info query and capability methods with CommandContext
    
    /**
     * Query string information from the graphics driver (CommandContext-aware).
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>{@code
     * CommandContext ctx = VulkanicAPI.getImmediateContext();
     * String version = VulkanicAPI.queryStringInfo(ctx, GL_VERSION);
     * String vendor = VulkanicAPI.queryStringInfo(ctx, GL_VENDOR);
     * String renderer = VulkanicAPI.queryStringInfo(ctx, GL_RENDERER);
     * System.out.println(vendor + " " + renderer + " " + version);
     * }</pre>
     * 
     * In OpenGL: Maps to glGetString()
     * In Vulkan: Maps to querying VkPhysicalDeviceProperties
     * 
     * @param ctx Command context
     * @param pname The name of the string to query (e.g., GL_VERSION, GL_VENDOR, GL_RENDERER)
     * @return The requested string or null if not available
     */
    public static String queryStringInfo(CommandContext ctx, int pname) {
        return getBackend().queryStringInfo(ctx, pname);
    }
    
    /**
     * Get the OpenGL capabilities object (CommandContext-aware).
     * 
     * This is a Vulkan-compatible method that requires an explicit CommandContext.
     * For OpenGL, use getImmediateContext() to get the context.
     * 
     * Example usage:
     * <pre>{@code
     * CommandContext ctx = VulkanicAPI.getImmediateContext();
     * Object caps = VulkanicAPI.getGLCapabilities(ctx);
     * // Use implementation-specific code to query capabilities
     * }</pre>
     * 
     * In OpenGL: Returns the LWJGL GLCapabilities object
     * In Vulkan: Returns equivalent capability information
     * 
     * @param ctx Command context
     * @return The capabilities object (implementation-specific)
     */
    public static Object getGLCapabilities(CommandContext ctx) {
        return getBackend().getGLCapabilities(ctx);
    }
    
    /**
     * Query an integer state value from the graphics driver (CommandContext-aware).
     * 
     * Example usage:
     * <pre>{@code
     * CommandContext ctx = VulkanicAPI.getImmediateContext();
     * int maxTextureSize = VulkanicAPI.glGetInteger(ctx, GL_MAX_TEXTURE_SIZE);
     * int maxDrawBuffers = VulkanicAPI.glGetInteger(ctx, GL_MAX_DRAW_BUFFERS);
     * }</pre>
     * 
     * In OpenGL: Maps to glGetIntegerv() for single value
     * In Vulkan: Maps to querying device/instance properties
     * 
     * @param ctx Command context
     * @param pname The parameter name to query
     * @return The queried integer value
     */
    public static int glGetInteger(CommandContext ctx, int pname) {
        return getBackend().glGetInteger(ctx, pname);
    }
    
    /**
     * Query multiple integer state values (CommandContext-aware).
     * 
     * Example usage:
     * <pre>{@code
     * CommandContext ctx = VulkanicAPI.getImmediateContext();
     * int[] viewport = new int[4];
     * VulkanicAPI.glGetIntegerv(ctx, GL_VIEWPORT, viewport);
     * }</pre>
     * 
     * In OpenGL: Maps to glGetIntegerv()
     * In Vulkan: Maps to querying device/instance properties
     * 
     * @param ctx Command context
     * @param pname The parameter name to query
     * @param params Array to receive the values
     */
    public static void glGetIntegerv(CommandContext ctx, int pname, int[] params) {
        getBackend().glGetIntegerv(ctx, pname, params);
    }
    
    /**
     * Query an indexed string from the graphics driver (CommandContext-aware).
     * 
     * Example usage:
     * <pre>{@code
     * CommandContext ctx = VulkanicAPI.getImmediateContext();
     * int numExts = VulkanicAPI.glGetInteger(ctx, GL_NUM_EXTENSIONS);
     * for (int i = 0; i < numExts; i++) {
     *     String ext = VulkanicAPI.glGetStringi(ctx, GL_EXTENSIONS, i);
     *     System.out.println("Extension: " + ext);
     * }
     * }</pre>
     * 
     * In OpenGL: Maps to glGetStringi()
     * In Vulkan: Maps to querying extension properties
     * 
     * @param ctx Command context
     * @param pname The parameter name to query
     * @param index The index of the string to retrieve
     * @return The indexed string or null if not available
     */
    public static String glGetStringi(CommandContext ctx, int pname, int index) {
        return getBackend().glGetStringi(ctx, pname, index);
    }
    
    /**
     * Get the program info log (CommandContext-aware).
     * 
     * This is a convenience wrapper around retrieveProgramInfoLog.
     * 
     * Example usage:
     * <pre>{@code
     * CommandContext ctx = VulkanicAPI.getImmediateContext();
     * String log = VulkanicAPI.glGetProgramInfoLog(ctx, programId);
     * if (!log.isEmpty()) {
     *     System.err.println("Program log: " + log);
     * }
     * }</pre>
     * 
     * @param ctx Command context
     * @param program Program object ID
     * @return The program info log string
     */
    public static String glGetProgramInfoLog(CommandContext ctx, int program) {
        return getBackend().glGetProgramInfoLog(ctx, program);
    }
    
    /**
     * Get the shader info log (CommandContext-aware).
     * 
     * This is a convenience wrapper around retrieveShaderInfoLog.
     * 
     * Example usage:
     * <pre>{@code
     * CommandContext ctx = VulkanicAPI.getImmediateContext();
     * String log = VulkanicAPI.glGetShaderInfoLog(ctx, shaderId);
     * if (!log.isEmpty()) {
     *     System.err.println("Shader log: " + log);
     * }
     * }</pre>
     * 
     * @param ctx Command context
     * @param shader Shader object ID
     * @return The shader info log string
     */
    public static String glGetShaderInfoLog(CommandContext ctx, int shader) {
        return getBackend().glGetShaderInfoLog(ctx, shader);
    }
    
    // ========================================================================
    // DSA (Direct State Access) Buffer Operations
    // ========================================================================
    
    /**
     * Create a buffer object using Direct State Access.
     * 
     * <p>DSA (Direct State Access) eliminates the bind-to-edit pattern,
     * which aligns better with Vulkan's explicit object model.</p>
     * 
     * <p><b>OpenGL:</b> Maps to glCreateBuffers() (GL 4.5+)</p>
     * <p><b>Vulkan:</b> Maps to vkCreateBuffer()</p>
     * 
     * <p><b>Example usage:</b>
     * <pre>{@code
     * CommandContext ctx = VulkanicAPI.getImmediateContext();
     * int bufferId = VulkanicAPI.createBufferDSA(ctx);
     * VulkanicAPI.namedBufferStorageDSA(ctx, bufferId, 1024, GL_DYNAMIC_STORAGE_BIT);
     * }</pre>
     * 
     * @param ctx Command context for recording this command
     * @return The newly created buffer object ID
     */
    public static int createBufferDSA(CommandContext ctx) {
        return getBackend().createBufferDSA(ctx);
    }
    
    /**
     * Allocate storage for a named buffer object (size only).
     * 
     * <p>Allocates mutable buffer storage with undefined data.
     * This is the DSA equivalent of glBufferData().</p>
     * 
     * <p><b>OpenGL:</b> Maps to glNamedBufferData() (GL 4.5+)</p>
     * <p><b>Vulkan:</b> Maps to vkAllocateMemory() + vkBindBufferMemory()</p>
     * 
     * <p><b>Example usage:</b>
     * <pre>{@code
     * CommandContext ctx = VulkanicAPI.getImmediateContext();
     * int bufferId = VulkanicAPI.createBufferDSA(ctx);
     * VulkanicAPI.namedBufferDataDSA(ctx, bufferId, 4096, GL_DYNAMIC_DRAW);
     * }</pre>
     * 
     * @param ctx Command context for recording this command
     * @param buffer Buffer object ID
     * @param size Size in bytes to allocate
     * @param usage Usage hint (GL_STATIC_DRAW, GL_DYNAMIC_DRAW, etc.)
     */
    public static void namedBufferDataDSA(CommandContext ctx, int buffer, long size, int usage) {
        getBackend().namedBufferDataDSA(ctx, buffer, size, usage);
    }
    
    /**
     * Allocate and upload data to a named buffer object.
     * 
     * <p>Allocates mutable buffer storage and uploads initial data.
     * This is the DSA equivalent of glBufferData() with data.</p>
     * 
     * <p><b>OpenGL:</b> Maps to glNamedBufferData() (GL 4.5+)</p>
     * <p><b>Vulkan:</b> Maps to vkAllocateMemory() + vkCmdUpdateBuffer()</p>
     * 
     * <p><b>Example usage:</b>
     * <pre>{@code
     * CommandContext ctx = VulkanicAPI.getImmediateContext();
     * ByteBuffer vertexData = ... // Your vertex data
     * int vbo = VulkanicAPI.createBufferDSA(ctx);
     * VulkanicAPI.namedBufferDataDSA(ctx, vbo, vertexData, GL_STATIC_DRAW);
     * }</pre>
     * 
     * @param ctx Command context for recording this command
     * @param buffer Buffer object ID
     * @param data Data to upload
     * @param usage Usage hint (GL_STATIC_DRAW, GL_DYNAMIC_DRAW, etc.)
     */
    public static void namedBufferDataDSA(CommandContext ctx, int buffer, java.nio.ByteBuffer data, int usage) {
        getBackend().namedBufferDataDSA(ctx, buffer, data, usage);
    }
    
    /**
     * Update a subregion of a named buffer object.
     * 
     * <p>Updates part of an existing buffer's data store.
     * This is the DSA equivalent of glBufferSubData().</p>
     * 
     * <p><b>OpenGL:</b> Maps to glNamedBufferSubData() (GL 4.5+)</p>
     * <p><b>Vulkan:</b> Maps to vkCmdUpdateBuffer() or staging buffer + vkCmdCopyBuffer()</p>
     * 
     * <p><b>Example usage:</b>
     * <pre>{@code
     * CommandContext ctx = VulkanicAPI.getImmediateContext();
     * ByteBuffer updatedData = ... // Your updated data
     * VulkanicAPI.namedBufferSubDataDSA(ctx, bufferId, 0, updatedData);
     * }</pre>
     * 
     * @param ctx Command context for recording this command
     * @param buffer Buffer object ID
     * @param offset Offset in bytes into the buffer
     * @param data Data to upload
     */
    public static void namedBufferSubDataDSA(CommandContext ctx, int buffer, long offset, java.nio.ByteBuffer data) {
        getBackend().namedBufferSubDataDSA(ctx, buffer, offset, data);
    }
    
    /**
     * Create immutable storage for a named buffer object (size only).
     * 
     * <p>Creates IMMUTABLE buffer storage. This is preferred for Vulkan
     * compatibility as Vulkan buffers are always immutable.</p>
     * 
     * <p><b>OpenGL:</b> Maps to glNamedBufferStorage() (GL 4.5+)</p>
     * <p><b>Vulkan:</b> Maps to vkCreateBuffer() with appropriate flags</p>
     * 
     * <p><b>Example usage:</b>
     * <pre>{@code
     * CommandContext ctx = VulkanicAPI.getImmediateContext();
     * int bufferId = VulkanicAPI.createBufferDSA(ctx);
     * // Create persistent mapped buffer
     * VulkanicAPI.namedBufferStorageDSA(ctx, bufferId, 8192, 
     *     GL_DYNAMIC_STORAGE_BIT | GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT);
     * }</pre>
     * 
     * @param ctx Command context for recording this command
     * @param buffer Buffer object ID
     * @param size Size in bytes to allocate
     * @param flags Storage flags (GL_DYNAMIC_STORAGE_BIT, GL_MAP_READ_BIT, etc.)
     */
    public static void namedBufferStorageDSA(CommandContext ctx, int buffer, long size, int flags) {
        getBackend().namedBufferStorageDSA(ctx, buffer, size, flags);
    }
    
    /**
     * Create immutable storage and upload data to a named buffer object.
     * 
     * <p>Creates IMMUTABLE buffer storage with initial data.
     * This is the preferred method for static data in Vulkan.</p>
     * 
     * <p><b>OpenGL:</b> Maps to glNamedBufferStorage() (GL 4.5+)</p>
     * <p><b>Vulkan:</b> Maps to vkCreateBuffer() + initial data upload</p>
     * 
     * <p><b>Example usage:</b>
     * <pre>{@code
     * CommandContext ctx = VulkanicAPI.getImmediateContext();
     * ByteBuffer staticData = ... // Your static data
     * int bufferId = VulkanicAPI.createBufferDSA(ctx);
     * VulkanicAPI.namedBufferStorageDSA(ctx, bufferId, staticData, 0);
     * }</pre>
     * 
     * @param ctx Command context for recording this command
     * @param buffer Buffer object ID
     * @param data Initial data to upload
     * @param flags Storage flags (GL_DYNAMIC_STORAGE_BIT, GL_MAP_WRITE_BIT, etc.)
     */
    public static void namedBufferStorageDSA(CommandContext ctx, int buffer, java.nio.ByteBuffer data, int flags) {
        getBackend().namedBufferStorageDSA(ctx, buffer, data, flags);
    }
    
    /**
     * Map a range of a named buffer object's data store.
     * 
     * <p>Maps buffer memory for direct CPU access.
     * This is the DSA equivalent of glMapBufferRange().</p>
     * 
     * <p><b>OpenGL:</b> Maps to glMapNamedBufferRange() (GL 4.5+)</p>
     * <p><b>Vulkan:</b> Maps to vkMapMemory()</p>
     * 
     * <p><b>Example usage:</b>
     * <pre>{@code
     * CommandContext ctx = VulkanicAPI.getImmediateContext();
     * ByteBuffer mapped = VulkanicAPI.mapNamedBufferRangeDSA(ctx, bufferId, 
     *     0, 1024, GL_MAP_WRITE_BIT);
     * if (mapped != null) {
     *     // Write data to mapped buffer
     *     mapped.putFloat(1.0f);
     *     VulkanicAPI.unmapNamedBufferDSA(ctx, bufferId);
     * }
     * }</pre>
     * 
     * @param ctx Command context for recording this command
     * @param buffer Buffer object ID
     * @param offset Offset in bytes into the buffer
     * @param length Length in bytes to map
     * @param access Access flags (GL_MAP_READ_BIT, GL_MAP_WRITE_BIT, etc.)
     * @return ByteBuffer mapped to the buffer's memory, or null on failure
     */
    public static java.nio.ByteBuffer mapNamedBufferRangeDSA(CommandContext ctx, int buffer, long offset, long length, int access) {
        return getBackend().mapNamedBufferRangeDSA(ctx, buffer, offset, length, access);
    }
    
    /**
     * Unmap a named buffer object's data store.
     * 
     * <p>Unmaps a previously mapped buffer.
     * This is the DSA equivalent of glUnmapBuffer().</p>
     * 
     * <p><b>OpenGL:</b> Maps to glUnmapNamedBuffer() (GL 4.5+)</p>
     * <p><b>Vulkan:</b> Maps to vkUnmapMemory()</p>
     * 
     * <p><b>Example usage:</b>
     * <pre>{@code
     * CommandContext ctx = VulkanicAPI.getImmediateContext();
     * ByteBuffer mapped = VulkanicAPI.mapNamedBufferRangeDSA(ctx, bufferId, 0, 1024, GL_MAP_WRITE_BIT);
     * // ... write to buffer ...
     * VulkanicAPI.unmapNamedBufferDSA(ctx, bufferId);
     * }</pre>
     * 
     * @param ctx Command context for recording this command
     * @param buffer Buffer object ID
     */
    public static void unmapNamedBufferDSA(CommandContext ctx, int buffer) {
        getBackend().unmapNamedBufferDSA(ctx, buffer);
    }
    
    /**
     * Flush modifications to a range of a mapped named buffer.
     * 
     * <p>Explicitly flushes modifications when using GL_MAP_FLUSH_EXPLICIT_BIT.
     * This is the DSA equivalent of glFlushMappedBufferRange().</p>
     * 
     * <p><b>OpenGL:</b> Maps to glFlushMappedNamedBufferRange() (GL 4.5+)</p>
     * <p><b>Vulkan:</b> Maps to vkFlushMappedMemoryRanges()</p>
     * 
     * <p><b>Example usage:</b>
     * <pre>{@code
     * CommandContext ctx = VulkanicAPI.getImmediateContext();
     * ByteBuffer mapped = VulkanicAPI.mapNamedBufferRangeDSA(ctx, bufferId, 
     *     0, 1024, GL_MAP_WRITE_BIT | GL_MAP_FLUSH_EXPLICIT_BIT);
     * // ... write to buffer ...
     * VulkanicAPI.flushMappedNamedBufferRangeDSA(ctx, bufferId, 0, 512);
     * VulkanicAPI.unmapNamedBufferDSA(ctx, bufferId);
     * }</pre>
     * 
     * @param ctx Command context for recording this command
     * @param buffer Buffer object ID
     * @param offset Offset in bytes into the mapped region
     * @param length Length in bytes to flush
     */
    public static void flushMappedNamedBufferRangeDSA(CommandContext ctx, int buffer, long offset, long length) {
        getBackend().flushMappedNamedBufferRangeDSA(ctx, buffer, offset, length);
    }
    
    /**
     * Copy data between named buffer objects.
     * 
     * <p>Copies buffer data without needing to bind them.
     * This is the DSA equivalent of glCopyBufferSubData().</p>
     * 
     * <p><b>OpenGL:</b> Maps to glCopyNamedBufferSubData() (GL 4.5+)</p>
     * <p><b>Vulkan:</b> Maps to vkCmdCopyBuffer()</p>
     * 
     * <p><b>Example usage:</b>
     * <pre>{@code
     * CommandContext ctx = VulkanicAPI.getImmediateContext();
     * VulkanicAPI.copyNamedBufferSubDataDSA(ctx, sourceBuffer, destBuffer, 0, 0, 1024);
     * }</pre>
     * 
     * @param ctx Command context for recording this command
     * @param readBuffer Source buffer object ID
     * @param writeBuffer Destination buffer object ID
     * @param readOffset Offset in bytes into the source buffer
     * @param writeOffset Offset in bytes into the destination buffer
     * @param size Number of bytes to copy
     */
    public static void copyNamedBufferSubDataDSA(CommandContext ctx, int readBuffer, int writeBuffer, long readOffset, long writeOffset, long size) {
        getBackend().copyNamedBufferSubDataDSA(ctx, readBuffer, writeBuffer, readOffset, writeOffset, size);
    }
    
    // ========================================================================
    // DSA (Direct State Access) Framebuffer Operations
    // ========================================================================
    
    /**
     * Create a framebuffer object using Direct State Access.
     * 
     * <p>Creates a framebuffer without needing to bind it first.</p>
     * 
     * <p><b>OpenGL:</b> Maps to glCreateFramebuffers() (GL 4.5+)</p>
     * <p><b>Vulkan:</b> Maps to vkCreateFramebuffer()</p>
     * 
     * <p><b>Example usage:</b>
     * <pre>{@code
     * CommandContext ctx = VulkanicAPI.getImmediateContext();
     * int fbo = VulkanicAPI.createFramebufferDSA(ctx);
     * VulkanicAPI.namedFramebufferTextureDSA(ctx, fbo, GL_COLOR_ATTACHMENT0, texId, 0);
     * }</pre>
     * 
     * @param ctx Command context for recording this command
     * @return The newly created framebuffer object ID
     */
    public static int createFramebufferDSA(CommandContext ctx) {
        return getBackend().createFramebufferDSA(ctx);
    }
    
    /**
     * Attach a texture to a named framebuffer object.
     * 
     * <p>Attaches a texture to a framebuffer without binding.
     * This is the DSA equivalent of glFramebufferTexture2D().</p>
     * 
     * <p><b>OpenGL:</b> Maps to glNamedFramebufferTexture() (GL 4.5+)</p>
     * <p><b>Vulkan:</b> Textures are specified during vkCreateFramebuffer()</p>
     * 
     * <p><b>Example usage:</b>
     * <pre>{@code
     * CommandContext ctx = VulkanicAPI.getImmediateContext();
     * int fbo = VulkanicAPI.createFramebufferDSA(ctx);
     * VulkanicAPI.namedFramebufferTextureDSA(ctx, fbo, GL_COLOR_ATTACHMENT0, colorTex, 0);
     * VulkanicAPI.namedFramebufferTextureDSA(ctx, fbo, GL_DEPTH_ATTACHMENT, depthTex, 0);
     * }</pre>
     * 
     * @param ctx Command context for recording this command
     * @param framebuffer Framebuffer object ID
     * @param attachment Attachment point (GL_COLOR_ATTACHMENT0, GL_DEPTH_ATTACHMENT, etc.)
     * @param texture Texture object ID to attach
     * @param level Mipmap level of the texture to attach
     */
    public static void namedFramebufferTextureDSA(CommandContext ctx, int framebuffer, int attachment, int texture, int level) {
        getBackend().namedFramebufferTextureDSA(ctx, framebuffer, attachment, texture, level);
    }
    
    /**
     * Blit (copy) pixels between named framebuffers.
     * 
     * <p>Copies a region between framebuffers with optional scaling and filtering.
     * This is the DSA equivalent of glBlitFramebuffer().</p>
     * 
     * <p><b>OpenGL:</b> Maps to glBlitNamedFramebuffer() (GL 4.5+)</p>
     * <p><b>Vulkan:</b> Maps to vkCmdBlitImage()</p>
     * 
     * <p><b>Example usage:</b>
     * <pre>{@code
     * CommandContext ctx = VulkanicAPI.getImmediateContext();
     * VulkanicAPI.blitNamedFramebufferDSA(ctx, srcFbo, dstFbo, 
     *     0, 0, 800, 600,  // Source region
     *     0, 0, 1920, 1080, // Destination region (upscaling)
     *     GL_COLOR_BUFFER_BIT, GL_LINEAR);
     * }</pre>
     * 
     * @param ctx Command context for recording this command
     * @param readFramebuffer Source framebuffer object ID
     * @param drawFramebuffer Destination framebuffer object ID
     * @param srcX0 Source region left X coordinate
     * @param srcY0 Source region bottom Y coordinate
     * @param srcX1 Source region right X coordinate
     * @param srcY1 Source region top Y coordinate
     * @param dstX0 Destination region left X coordinate
     * @param dstY0 Destination region bottom Y coordinate
     * @param dstX1 Destination region right X coordinate
     * @param dstY1 Destination region top Y coordinate
     * @param mask Buffer bit mask (GL_COLOR_BUFFER_BIT, GL_DEPTH_BUFFER_BIT, etc.)
     * @param filter Interpolation filter (GL_NEAREST or GL_LINEAR)
     */
    public static void blitNamedFramebufferDSA(CommandContext ctx, int readFramebuffer, int drawFramebuffer, 
                                               int srcX0, int srcY0, int srcX1, int srcY1,
                                               int dstX0, int dstY0, int dstX1, int dstY1, 
                                               int mask, int filter) {
        getBackend().blitNamedFramebufferDSA(ctx, readFramebuffer, drawFramebuffer, 
                                              srcX0, srcY0, srcX1, srcY1,
                                              dstX0, dstY0, dstX1, dstY1, 
                                              mask, filter);
    }
    
    // ========================================================================
    // Non-DSA Buffer Operations with CommandContext
    // ========================================================================
    
    /**
     * Copy data between bound buffer objects.
     * 
     * <p>Copies buffer data between buffers bound to specified targets.
     * For DSA version, use copyNamedBufferSubDataDSA().</p>
     * 
     * <p><b>OpenGL:</b> Maps to glCopyBufferSubData()</p>
     * <p><b>Vulkan:</b> Maps to vkCmdCopyBuffer()</p>
     * 
     * <p><b>Example usage:</b>
     * <pre>{@code
     * CommandContext ctx = VulkanicAPI.getImmediateContext();
     * VulkanicAPI.bindBuffer(ctx, GL_COPY_READ_BUFFER, sourceBuffer);
     * VulkanicAPI.bindBuffer(ctx, GL_COPY_WRITE_BUFFER, destBuffer);
     * VulkanicAPI.copyBufferSubData(ctx, GL_COPY_READ_BUFFER, GL_COPY_WRITE_BUFFER, 0, 0, 1024);
     * }</pre>
     * 
     * @param ctx Command context for recording this command
     * @param readTarget Source buffer binding target
     * @param writeTarget Destination buffer binding target
     * @param readOffset Offset in bytes into the source buffer
     * @param writeOffset Offset in bytes into the destination buffer
     * @param size Number of bytes to copy
     */
    public static void copyBufferSubData(CommandContext ctx, int readTarget, int writeTarget, long readOffset, long writeOffset, long size) {
        getBackend().copyBufferSubData(ctx, readTarget, writeTarget, readOffset, writeOffset, size);
    }
    
    /**
     * Flush modifications to a range of a mapped buffer.
     * 
     * <p>Explicitly flushes modifications for a buffer bound to the specified target.
     * For DSA version, use flushMappedNamedBufferRangeDSA().</p>
     * 
     * <p><b>OpenGL:</b> Maps to glFlushMappedBufferRange()</p>
     * <p><b>Vulkan:</b> Maps to vkFlushMappedMemoryRanges()</p>
     * 
     * <p><b>Example usage:</b>
     * <pre>{@code
     * CommandContext ctx = VulkanicAPI.getImmediateContext();
     * VulkanicAPI.bindBuffer(ctx, GL_ARRAY_BUFFER, bufferId);
     * ByteBuffer mapped = VulkanicAPI.mapBufferRegion(ctx, GL_ARRAY_BUFFER, 
     *     0, 1024, GL_MAP_WRITE_BIT | GL_MAP_FLUSH_EXPLICIT_BIT);
     * // ... write to buffer ...
     * VulkanicAPI.flushMappedBufferRange(ctx, GL_ARRAY_BUFFER, 0, 512);
     * VulkanicAPI.unmapBufferData(ctx, GL_ARRAY_BUFFER);
     * }</pre>
     * 
     * @param ctx Command context for recording this command
     * @param target Buffer binding target
     * @param offset Offset in bytes into the mapped region
     * @param length Length in bytes to flush
     */
    public static void flushMappedBufferRange(CommandContext ctx, int target, long offset, long length) {
        getBackend().flushMappedBufferRange(ctx, target, offset, length);
    }
}
