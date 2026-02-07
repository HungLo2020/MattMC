package net.vulkanic;

import net.vulkanic.backends.opengl.OpenGLBackend;

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
    public static final int GL_COPY_READ_BUFFER = 0x8F36;
    public static final int GL_COPY_WRITE_BUFFER = 0x8F37;
    public static final int GL_SHADER_STORAGE_BUFFER = 0x90D2;
    
    // OpenGL Constants - Buffer Usage
    public static final int GL_STATIC_DRAW = 0x88E4;
    public static final int GL_DYNAMIC_DRAW = 0x88E8;
    
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
    public static final int GL_LINE = 0x1B01;
    public static final int GL_FILL = 0x1B02;
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
    public static final int GL_INT = 0x1404;
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
    
    public static void bindTexture(int textureId) {
        getBackend().bindTexture(textureId);
    }
    
    public static void bindTexture(int target, int textureId) {
        getBackend().bindTexture(target, textureId);
    }
    
    public static void generateMipmap(int target) {
        getBackend().generateMipmap(target);
    }
    
    public static void viewport(int x, int y, int width, int height) {
        getBackend().viewport(x, y, width, height);
    }
    
    public static void clear(int mask) {
        getBackend().clear(mask);
    }
    
    public static void enableBlend() {
        getBackend().enableBlend();
    }
    
    public static void disableBlend() {
        getBackend().disableBlend();
    }
    
    public static void useProgram(int programId) {
        getBackend().useProgram(programId);
    }
    
    public static void enable(int cap) {
        getBackend().enable(cap);
    }
    
    public static void disable(int cap) {
        getBackend().disable(cap);
    }
    
    public static void setDepthTestFunction(int func) {
        getBackend().setDepthTestFunction(func);
    }
    
    public static void setDepthWriteEnabled(boolean enabled) {
        getBackend().setDepthWriteEnabled(enabled);
    }
    
    public static void setColorWriteMask(boolean r, boolean g, boolean b, boolean a) {
        getBackend().setColorWriteMask(r, g, b, a);
    }
    
    public static void setScissorBox(int x, int y, int w, int h) {
        getBackend().setScissorBox(x, y, w, h);
    }
    
    public static void setPixelStoreMode(int pname, int value) {
        getBackend().setPixelStoreMode(pname, value);
    }
    
    public static void attachFramebuffer(int target, int fbo) {
        getBackend().attachFramebuffer(target, fbo);
    }
    
    public static void attachTextureToFramebuffer(int target, int attachment, int textarget, int texture, int level) {
        getBackend().attachTextureToFramebuffer(target, attachment, textarget, texture, level);
    }
    
    public static void attachBuffer(int target, int buffer) {
        getBackend().attachBuffer(target, buffer);
    }
    
    // Direct State Access buffer operations
    public static int createBufferDSA() {
        return getBackend().createBufferDSA();
    }
    
    public static void namedBufferDataDSA(int buffer, long size, int usage) {
        getBackend().namedBufferDataDSA(buffer, size, usage);
    }
    
    public static void namedBufferDataDSA(int buffer, java.nio.ByteBuffer data, int usage) {
        getBackend().namedBufferDataDSA(buffer, data, usage);
    }
    
    public static void namedBufferSubDataDSA(int buffer, long offset, java.nio.ByteBuffer data) {
        getBackend().namedBufferSubDataDSA(buffer, offset, data);
    }
    
    public static void namedBufferStorageDSA(int buffer, long size, int flags) {
        getBackend().namedBufferStorageDSA(buffer, size, flags);
    }
    
    public static void namedBufferStorageDSA(int buffer, java.nio.ByteBuffer data, int flags) {
        getBackend().namedBufferStorageDSA(buffer, data, flags);
    }
    
    public static java.nio.ByteBuffer mapNamedBufferRangeDSA(int buffer, long offset, long length, int access) {
        return getBackend().mapNamedBufferRangeDSA(buffer, offset, length, access);
    }
    
    public static void unmapNamedBufferDSA(int buffer) {
        getBackend().unmapNamedBufferDSA(buffer);
    }
    
    public static void flushMappedNamedBufferRangeDSA(int buffer, long offset, long length) {
        getBackend().flushMappedNamedBufferRangeDSA(buffer, offset, length);
    }
    
    public static void copyNamedBufferSubDataDSA(int readBuffer, int writeBuffer, long readOffset, long writeOffset, long size) {
        getBackend().copyNamedBufferSubDataDSA(readBuffer, writeBuffer, readOffset, writeOffset, size);
    }
    
    // Direct State Access framebuffer operations
    public static int createFramebufferDSA() {
        return getBackend().createFramebufferDSA();
    }
    
    public static void namedFramebufferTextureDSA(int framebuffer, int attachment, int texture, int level) {
        getBackend().namedFramebufferTextureDSA(framebuffer, attachment, texture, level);
    }
    
    public static void blitNamedFramebufferDSA(int readFramebuffer, int drawFramebuffer, int srcX0, int srcY0, int srcX1, int srcY1,
                                                int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        getBackend().blitNamedFramebufferDSA(readFramebuffer, drawFramebuffer, srcX0, srcY0, srcX1, srcY1,
                                              dstX0, dstY0, dstX1, dstY1, mask, filter);
    }
    
    public static void activateTextureUnit(int unit) {
        getBackend().activateTextureUnit(unit);
    }
    
    public static void configureTextureParameter(int target, int pname, int param) {
        getBackend().configureTextureParameter(target, pname, param);
    }
    
    public static int createTexture() {
        return getBackend().createTexture();
    }
    
    public static void removeTexture(int texture) {
        getBackend().removeTexture(texture);
    }
    
    public static void configurePolygonMode(int face, int mode) {
        getBackend().configurePolygonMode(face, mode);
    }
    
    public static void configurePolygonOffset(float factor, float units) {
        getBackend().configurePolygonOffset(factor, units);
    }
    
    public static void configureLogicOp(int opcode) {
        getBackend().configureLogicOp(opcode);
    }
    
    public static void drawPrimitiveArrays(int mode, int first, int count) {
        getBackend().drawPrimitiveArrays(mode, first, count);
    }

    public static void drawIndexedElements(int mode, int count, int type, long indices) {
        getBackend().drawIndexedElements(mode, count, type, indices);
    }
    
    public static void configureBlendFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        getBackend().configureBlendFunc(srcRgb, dstRgb, srcAlpha, dstAlpha);
    }
    
    public static int checkForErrors() {
        return getBackend().checkForErrors();
    }
    
    public static void transferTexture2DImage(int tgt, int lvl, int intfmt, int w, int h, int bdr, int fmt, int typ, java.nio.ByteBuffer pix) {
        getBackend().transferTexture2DImage(tgt, lvl, intfmt, w, h, bdr, fmt, typ, pix);
    }
    
    public static void transferTexture2DSubregion(int tgt, int lvl, int xoff, int yoff, int w, int h, int fmt, int typ, long pix) {
        getBackend().transferTexture2DSubregion(tgt, lvl, xoff, yoff, w, h, fmt, typ, pix);
    }
    
    public static void transferTexture2DSubregionBuf(int tgt, int lvl, int xoff, int yoff, int w, int h, int fmt, int typ, java.nio.ByteBuffer pix) {
        getBackend().transferTexture2DSubregionBuf(tgt, lvl, xoff, yoff, w, h, fmt, typ, pix);
    }
    
    public static int allocateBufferObject() {
        return getBackend().allocateBufferObject();
    }
    
    public static void releaseBufferObject(int buf) {
        getBackend().releaseBufferObject(buf);
    }
    
    public static void fillBufferWithData(int tgt, java.nio.ByteBuffer dat, int usg) {
        getBackend().fillBufferWithData(tgt, dat, usg);
    }
    
    public static void fillBufferWithSize(int tgt, long sz, int usg) {
        getBackend().fillBufferWithSize(tgt, sz, usg);
    }
    
    public static void fillBufferSubregion(int tgt, long off, java.nio.ByteBuffer dat) {
        getBackend().fillBufferSubregion(tgt, off, dat);
    }
    
    public static int createVertexArrayObject() {
        return getBackend().createVertexArrayObject();
    }
    
    public static void selectVertexArray(int vao) {
        getBackend().selectVertexArray(vao);
    }
    
    public static java.nio.ByteBuffer mapBufferRegion(int tgt, int off, int len, int acc) {
        return getBackend().mapBufferRegion(tgt, off, len, acc);
    }
    
    public static void unmapBufferData(int tgt) {
        getBackend().unmapBufferData(tgt);
    }
    
    public static int generateFramebufferObject() {
        return getBackend().generateFramebufferObject();
    }
    
    public static void destroyFramebufferObject(int fbo) {
        getBackend().destroyFramebufferObject(fbo);
    }
    
    public static void copyFramebufferRegion(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int msk, int flt) {
        getBackend().copyFramebufferRegion(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, msk, flt);
    }
    
    public static int constructShaderObject(int shaderType) {
        return getBackend().constructShaderObject(shaderType);
    }
    
    public static void disposeShaderObject(int shader) {
        getBackend().disposeShaderObject(shader);
    }
    
    public static void compileShaderSource(int shader) {
        getBackend().compileShaderSource(shader);
    }
    
    public static int constructProgramObject() {
        return getBackend().constructProgramObject();
    }
    
    public static void disposeProgramObject(int program) {
        getBackend().disposeProgramObject(program);
    }
    
    public static void linkProgramBinary(int program) {
        getBackend().linkProgramBinary(program);
    }
    
    public static void attachShaderToProgram(int program, int shader) {
        getBackend().attachShaderToProgram(program, shader);
    }
    
    public static int queryProgramParameter(int program, int pname) {
        return getBackend().queryProgramParameter(program, pname);
    }
    
    public static int queryShaderParameter(int shader, int pname) {
        return getBackend().queryShaderParameter(shader, pname);
    }
    
    public static void configureVertexAttribute(int index, int size, int type, boolean normalized, int stride, long pointer) {
        getBackend().configureVertexAttribute(index, size, type, normalized, stride, pointer);
    }
    
    public static void configureVertexAttributeInteger(int index, int size, int type, int stride, long pointer) {
        getBackend().configureVertexAttributeInteger(index, size, type, stride, pointer);
    }
    
    public static void activateVertexAttribute(int index) {
        getBackend().activateVertexAttribute(index);
    }
    
    public static void deactivateVertexAttribute(int index) {
        getBackend().deactivateVertexAttribute(index);
    }
    
    public static void setVertexAttribDivisor(int index, int divisor) {
        getBackend().setVertexAttribDivisor(index, divisor);
    }
    
    public static String retrieveProgramInfoLog(int program) {
        return getBackend().retrieveProgramInfoLog(program);
    }
    
    public static String retrieveShaderInfoLog(int shader) {
        return getBackend().retrieveShaderInfoLog(shader);
    }
    
    public static int locateUniformVariable(int program, CharSequence name) {
        return getBackend().locateUniformVariable(program, name);
    }
    
    public static void assignUniformInteger(int location, int value) {
        getBackend().assignUniformInteger(location, value);
    }
    
    public static void bindAttributeLocation(int program, int index, CharSequence name) {
        getBackend().bindAttributeLocation(program, index, name);
    }
    
    public static long createFenceSync(int condition, int flags) {
        return getBackend().createFenceSync(condition, flags);
    }
    
    public static int waitForSync(long sync, int flags, long timeout) {
        return getBackend().waitForSync(sync, flags, timeout);
    }
    
    public static void destroySync(long sync) {
        getBackend().destroySync(sync);
    }
    
    public static void clearTexImage(int texture, int level, int format, int type, int[] data) {
        getBackend().clearTexImage(texture, level, format, type, data);
    }
    
    public static void setMaxShaderCompilerThreads(int count) {
        getBackend().setMaxShaderCompilerThreads(count);
    }
    
    public static GraphicsCapabilities getGraphicsCapabilities() {
        return getBackend().getGraphicsCapabilities();
    }
    
    public static int queryIntegerState(int pname) {
        return getBackend().queryIntegerState(pname);
    }
    
    public static String queryStringInfo(int name) {
        return getBackend().queryStringInfo(name);
    }
    
    public static int pollErrorCode() {
        return getBackend().pollErrorCode();
    }
    
    public static void readFramebufferPixels(int x, int y, int width, int height, int format, int type, long pixels) {
        getBackend().readFramebufferPixels(x, y, width, height, format, type, pixels);
    }
    
    public static int queryTextureLevelParameter(int target, int level, int pname) {
        return getBackend().queryTextureLevelParameter(target, level, pname);
    }
    
    public static void uploadShaderSource(int shader, long pointerBufferAddress, int stringCount, long lengthsPointer) {
        getBackend().uploadShaderSource(shader, pointerBufferAddress, stringCount, lengthsPointer);
    }
    
    public static int locateUniformBlock(int program, String uniformBlockName) {
        return getBackend().locateUniformBlock(program, uniformBlockName);
    }
    
    public static void bindUniformBlock(int program, int uniformBlockIndex, int uniformBlockBinding) {
        getBackend().bindUniformBlock(program, uniformBlockIndex, uniformBlockBinding);
    }
    
    public static String retrieveActiveUniformBlockName(int program, int uniformBlockIndex) {
        return getBackend().retrieveActiveUniformBlockName(program, uniformBlockIndex);
    }
    
    public static int generateQueryObject() {
        return getBackend().generateQueryObject();
    }
    
    public static void initiateQuery(int target, int id) {
        getBackend().initiateQuery(target, id);
    }
    
    public static void concludeQuery(int target) {
        getBackend().concludeQuery(target);
    }
    
    public static void disposeQueryObject(int id) {
        getBackend().disposeQueryObject(id);
    }
    
    public static int retrieveQueryObjectInt(int id, int pname) {
        return getBackend().retrieveQueryObjectInt(id, pname);
    }
    
    public static long retrieveQueryObjectInt64(int id, int pname) {
        return getBackend().retrieveQueryObjectInt64(id, pname);
    }
    
    public static void labelDebugObject(int identifier, int name, String label) {
        getBackend().labelDebugObject(identifier, name, label);
    }
    
    public static void enterDebugGroup(int source, int id, CharSequence message) {
        getBackend().enterDebugGroup(source, id, message);
    }
    
    public static void exitDebugGroup() {
        getBackend().exitDebugGroup();
    }
    
    public static void labelObjectExt(int type, int object, String label) {
        getBackend().labelObjectExt(type, object, label);
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
    
    public static void attachVertexBuffer(int bindingIndex, int buffer, long offset, int stride) {
        getBackend().attachVertexBuffer(bindingIndex, buffer, offset, stride);
    }
    
    public static void specifyVertexAttribFormat(int attribIndex, int size, int type, boolean normalized, int relativeOffset) {
        getBackend().specifyVertexAttribFormat(attribIndex, size, type, normalized, relativeOffset);
    }
    
    public static void specifyVertexAttribIFormat(int attribIndex, int size, int type, int relativeOffset) {
        getBackend().specifyVertexAttribIFormat(attribIndex, size, type, relativeOffset);
    }
    
    public static void associateVertexAttrib(int attribIndex, int bindingIndex) {
        getBackend().associateVertexAttrib(attribIndex, bindingIndex);
    }
    
    public static void setClearDepthValue(double depth) {
        getBackend().setClearDepthValue(depth);
    }
    
    public static void setClearColorValue(float red, float green, float blue, float alpha) {
        getBackend().setClearColorValue(red, green, blue, alpha);
    }
    
    public static void selectDrawBuffer(int mode) {
        getBackend().selectDrawBuffer(mode);
    }
    
    public static void renderIndexedInstancedWithBase(int mode, int count, int type, long indices, int instanceCount, int baseVertex) {
        getBackend().renderIndexedInstancedWithBase(mode, count, type, indices, instanceCount, baseVertex);
    }
    
    public static void renderIndexedWithBase(int mode, int count, int type, long indices, int baseVertex) {
        getBackend().renderIndexedWithBase(mode, count, type, indices, baseVertex);
    }
    
    public static void renderIndexedInstanced(int mode, int count, int type, long indices, int instanceCount) {
        getBackend().renderIndexedInstanced(mode, count, type, indices, instanceCount);
    }
    
    public static void renderArraysInstanced(int mode, int first, int count, int instanceCount) {
        getBackend().renderArraysInstanced(mode, first, count, instanceCount);
    }
    
    public static void attachUniformBufferRange(int target, int index, int buffer, long offset, long size) {
        getBackend().attachUniformBufferRange(target, index, buffer, offset, size);
    }
    
    public static void attachBufferToTexture(int target, int internalFormat, int buffer) {
        getBackend().attachBufferToTexture(target, internalFormat, buffer);
    }
    
    public static void assignUniformFloat(int location, float value) {
        getBackend().assignUniformFloat(location, value);
    }
    
    public static void assignUniformFloat2(int location, float x, float y) {
        getBackend().assignUniformFloat2(location, x, y);
    }
    
    public static void assignUniformFloat2v(int location, float[] value) {
        getBackend().assignUniformFloat2v(location, value);
    }
    
    public static void assignUniformFloat3(int location, float x, float y, float z) {
        getBackend().assignUniformFloat3(location, x, y, z);
    }
    
    public static void assignUniformFloat3v(int location, float[] value) {
        getBackend().assignUniformFloat3v(location, value);
    }
    
    public static void assignUniformFloat4(int location, float x, float y, float z, float w) {
        getBackend().assignUniformFloat4(location, x, y, z, w);
    }
    
    public static void assignUniformFloat4v(int location, float[] value) {
        getBackend().assignUniformFloat4v(location, value);
    }
    
    public static void assignUniformMatrix4f(int location, java.nio.FloatBuffer matrix) {
        getBackend().assignUniformMatrix4f(location, matrix);
    }
    
    public static void bindUniformBufferBase(int bindingPoint, int bufferId) {
        getBackend().bindUniformBufferBase(bindingPoint, bufferId);
    }
    
    public static void bindFragmentDataLocation(int program, int colorNumber, CharSequence name) {
        getBackend().bindFragmentDataLocation(program, colorNumber, name);
    }
    
    public static int querySyncStatus(long sync, int pname, java.nio.IntBuffer length) {
        return getBackend().querySyncStatus(sync, pname, length);
    }
    
    public static GraphicsCapabilities obtainGraphicsCapabilities() {
        return getBackend().obtainGraphicsCapabilities();
    }
    
    public static GraphicsCapabilities initializeGraphicsCapabilities() {
        return getBackend().initializeGraphicsCapabilities();
    }
    
    public static boolean checkFunctionAvailable(String functionName) {
        return getBackend().checkFunctionAvailable(functionName);
    }
    
    public static void copyBufferSubData(int readTarget, int writeTarget, long readOffset, long writeOffset, long size) {
        getBackend().copyBufferSubData(readTarget, writeTarget, readOffset, writeOffset, size);
    }
    
    public static void deleteVertexArray(int vertexArray) {
        getBackend().deleteVertexArray(vertexArray);
    }
    
    public static void flushMappedBufferRange(int target, long offset, long length) {
        getBackend().flushMappedBufferRange(target, offset, length);
    }
    
    public static void createBufferStorage(int target, long size, int flags) {
        getBackend().createBufferStorage(target, size, flags);
    }
    
    public static void createBufferStorage(int target, java.nio.ByteBuffer data, int flags) {
        getBackend().createBufferStorage(target, data, flags);
    }
    
    public static void multiDrawElementsBaseVertex(int mode, long pCount, int type, long pIndices, int drawCount, long pBaseVertex) {
        getBackend().multiDrawElementsBaseVertex(mode, pCount, type, pIndices, drawCount, pBaseVertex);
    }
    
    public static void assignUniformMatrix4fv(int location, boolean transpose, java.nio.FloatBuffer value) {
        getBackend().assignUniformMatrix4fv(location, transpose, value);
    }
    
    public static String queryString(int name) {
        return getBackend().queryString(name);
    }
    
    public static String queryStringIndexed(int name, int index) {
        return getBackend().queryStringIndexed(name, index);
    }
    
    public static void uploadShaderSourceNative(int shader, int count, long strings, long length) {
        getBackend().uploadShaderSourceNative(shader, count, strings, length);
    }
    
    public static void glCopyTexSubImage2D(int target, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
        getBackend().glCopyTexSubImage2D(target, level, xoffset, yoffset, x, y, width, height);
    }
    
    // Debug object labeling (KHRDebug/GL43)
    public static void labelObject(int identifier, int name, String label) {
        getBackend().labelObject(identifier, name, label);
    }
    
    // Debug group push/pop (KHRDebug/GL43)
    public static void pushDebugGroup(int source, int id, String message) {
        getBackend().pushDebugGroup(source, id, message);
    }
    
    public static void popDebugGroup() {
        getBackend().popDebugGroup();
    }
    
    // Additional methods for IrisRenderSystem migration
    
    public static void glGetIntegerv(int pname, int[] params) {
        getBackend().glGetIntegerv(pname, params);
    }
    
    public static void glGetFloatv(int pname, float[] params) {
        getBackend().glGetFloatv(pname, params);
    }
    
    public static void glTexImage1D(int target, int level, int internalformat, int width, int border, int format, int type, java.nio.ByteBuffer pixels) {
        getBackend().glTexImage1D(target, level, internalformat, width, border, format, type, pixels);
    }
    
    public static void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format, int type, java.nio.ByteBuffer pixels) {
        getBackend().glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
    }
    
    public static void glTexImage3D(int target, int level, int internalformat, int width, int height, int depth, int border, int format, int type, java.nio.ByteBuffer pixels) {
        getBackend().glTexImage3D(target, level, internalformat, width, height, depth, border, format, type, pixels);
    }
    
    public static void glUniformMatrix4fv(int location, boolean transpose, java.nio.FloatBuffer matrix) {
        getBackend().glUniformMatrix4fv(location, transpose, matrix);
    }
    
    public static void glUniformMatrix4fv(int location, boolean transpose, float[] matrix) {
        getBackend().glUniformMatrix4fv(location, transpose, matrix);
    }
    
    public static void glCopyTexImage2D(int target, int level, int internalFormat, int x, int y, int width, int height, int border) {
        getBackend().glCopyTexImage2D(target, level, internalFormat, x, y, width, height, border);
    }
    
    public static void glUniform1f(int location, float v0) {
        getBackend().glUniform1f(location, v0);
    }
    
    public static void glUniform2f(int location, float v0, float v1) {
        getBackend().glUniform2f(location, v0, v1);
    }
    
    public static void glUniform2i(int location, int v0, int v1) {
        getBackend().glUniform2i(location, v0, v1);
    }
    
    public static void glUniform3f(int location, float v0, float v1, float v2) {
        getBackend().glUniform3f(location, v0, v1, v2);
    }
    
    public static void glUniform3i(int location, int v0, int v1, int v2) {
        getBackend().glUniform3i(location, v0, v1, v2);
    }
    
    public static void glUniform4f(int location, float v0, float v1, float v2, float v3) {
        getBackend().glUniform4f(location, v0, v1, v2, v3);
    }
    
    public static void glUniform4i(int location, int v0, int v1, int v2, int v3) {
        getBackend().glUniform4i(location, v0, v1, v2, v3);
    }
    
    public static void glTexParameteriv(int target, int pname, int[] params) {
        getBackend().glTexParameteriv(target, pname, params);
    }
    
    public static void glTexParameteri(int target, int pname, int param) {
        getBackend().glTexParameteri(target, pname, param);
    }
    
    public static void glTexParameterf(int target, int pname, float param) {
        getBackend().glTexParameterf(target, pname, param);
    }
    
    public static String glGetProgramInfoLog(int program) {
        return getBackend().glGetProgramInfoLog(program);
    }
    
    public static String glGetShaderInfoLog(int shader) {
        return getBackend().glGetShaderInfoLog(shader);
    }
    
    public static void glDrawBuffers(int[] buffers) {
        getBackend().glDrawBuffers(buffers);
    }
    
    public static void glReadBuffer(int buffer) {
        getBackend().glReadBuffer(buffer);
    }
    
    public static void glClearBufferfv(int buffer, int drawbuffer, float[] values) {
        getBackend().glClearBufferfv(buffer, drawbuffer, values);
    }
    
    public static void glClearBufferiv(int buffer, int drawbuffer, int[] values) {
        getBackend().glClearBufferiv(buffer, drawbuffer, values);
    }
    
    public static void glClearBufferuiv(int buffer, int drawbuffer, int[] values) {
        getBackend().glClearBufferuiv(buffer, drawbuffer, values);
    }
    
    public static String glGetActiveUniform(int program, int index, int size, java.nio.IntBuffer type, java.nio.IntBuffer name) {
        return getBackend().glGetActiveUniform(program, index, size, type, name);
    }
    
    public static void glReadPixels(int x, int y, int width, int height, int format, int type, float[] pixels) {
        getBackend().glReadPixels(x, y, width, height, format, type, pixels);
    }
    
    public static void glBufferData(int target, float[] data, int usage) {
        getBackend().glBufferData(target, data, usage);
    }
    
    public static void glBufferData(int target, int[] data, int usage) {
        getBackend().glBufferData(target, data, usage);
    }
    
    public static void glBufferStorage(int target, long size, int flags) {
        getBackend().glBufferStorage(target, size, flags);
    }
    
    public static void glBindBufferBase(int target, int index, int buffer) {
        getBackend().glBindBufferBase(target, index, buffer);
    }
    
    public static void glVertexAttrib4f(int index, float v0, float v1, float v2, float v3) {
        getBackend().glVertexAttrib4f(index, v0, v1, v2, v3);
    }
    
    public static void glDetachShader(int program, int shader) {
        getBackend().glDetachShader(program, shader);
    }
    
    public static void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        getBackend().glFramebufferTexture2D(target, attachment, textarget, texture, level);
    }
    
    public static void glFramebufferTexture(int target, int attachment, int texture, int level) {
        getBackend().glFramebufferTexture(target, attachment, texture, level);
    }
    
    public static int glGetTexParameteri(int target, int pname) {
        return getBackend().glGetTexParameteri(target, pname);
    }
    
    public static void glBindImageTexture(int unit, int texture, int level, boolean layered, int layer, int access, int format) {
        getBackend().glBindImageTexture(unit, texture, level, layered, layer, access, format);
    }
    
    public static int glGetMaxImageUnits() {
        return getBackend().glGetMaxImageUnits();
    }
    
    public static void glGenBuffers(int[] buffers) {
        getBackend().glGenBuffers(buffers);
    }
    
    public static void glClearBufferSubData(int target, int internalformat, long offset, long size, int format, int type, int[] data) {
        getBackend().glClearBufferSubData(target, internalformat, offset, size, format, type, data);
    }
    
    public static void glGetProgramiv(int program, int pname, int[] params) {
        getBackend().glGetProgramiv(program, pname, params);
    }
    
    public static void glDispatchCompute(int workX, int workY, int workZ) {
        getBackend().glDispatchCompute(workX, workY, workZ);
    }
    
    public static void glMemoryBarrier(int barriers) {
        getBackend().glMemoryBarrier(barriers);
    }
    
    public static void glDisablei(int target, int index) {
        getBackend().glDisablei(target, index);
    }
    
    public static void glEnablei(int target, int index) {
        getBackend().glEnablei(target, index);
    }
    
    public static void glBlendFuncSeparatei(int buffer, int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        getBackend().glBlendFuncSeparatei(buffer, srcRGB, dstRGB, srcAlpha, dstAlpha);
    }
    
    public static int glGetUniformBlockIndex(int program, String uniformBlockName) {
        return getBackend().glGetUniformBlockIndex(program, uniformBlockName);
    }
    
    public static void glUniformBlockBinding(int program, int uniformBlockIndex, int uniformBlockBinding) {
        getBackend().glUniformBlockBinding(program, uniformBlockIndex, uniformBlockBinding);
    }
    
    public static int glGenSamplers() {
        return getBackend().glGenSamplers();
    }
    
    public static void glDeleteSamplers(int sampler) {
        getBackend().glDeleteSamplers(sampler);
    }
    
    public static void glBindSampler(int unit, int sampler) {
        getBackend().glBindSampler(unit, sampler);
    }
    
    public static void glBindSamplers(int first, int[] samplers) {
        getBackend().glBindSamplers(first, samplers);
    }
    
    public static void glSamplerParameteri(int sampler, int pname, int param) {
        getBackend().glSamplerParameteri(sampler, pname, param);
    }
    
    public static void glSamplerParameterf(int sampler, int pname, float param) {
        getBackend().glSamplerParameterf(sampler, pname, param);
    }
    
    public static void glSamplerParameteriv(int sampler, int pname, int[] params) {
        getBackend().glSamplerParameteriv(sampler, pname, params);
    }
    
    public static int glGetInteger(int pname) {
        return getBackend().glGetInteger(pname);
    }
    
    public static void glDeleteBuffers(int buffer) {
        getBackend().glDeleteBuffers(buffer);
    }
    
    public static void glPolygonMode(int face, int mode) {
        getBackend().glPolygonMode(face, mode);
    }
    
    public static void glViewport(int x, int y, int width, int height) {
        getBackend().glViewport(x, y, width, height);
    }
    
    public static void glDispatchComputeIndirect(long offset) {
        getBackend().glDispatchComputeIndirect(offset);
    }
    
    public static void glBindBuffer(int target, int buffer) {
        getBackend().glBindBuffer(target, buffer);
    }
    
    public static String glGetStringi(int name, int index) {
        return getBackend().glGetStringi(name, index);
    }
    
    public static void glCopyImageSubData(int srcName, int srcTarget, int srcLevel, int srcX, int srcY, int srcZ, int dstName, int dstTarget, int dstLevel, int dstX, int dstY, int dstZ, int width, int height, int depth) {
        getBackend().glCopyImageSubData(srcName, srcTarget, srcLevel, srcX, srcY, srcZ, dstName, dstTarget, dstLevel, dstX, dstY, dstZ, width, height, depth);
    }
    
    public static int glCheckFramebufferStatus(int target) {
        return getBackend().glCheckFramebufferStatus(target);
    }
    
    public static void glUniformMatrix3fv(int location, boolean transpose, java.nio.FloatBuffer value) {
        getBackend().glUniformMatrix3fv(location, transpose, value);
    }
    
    public static void glUniformMatrix3fv(int location, boolean transpose, float[] value) {
        getBackend().glUniformMatrix3fv(location, transpose, value);
    }
    
    public static void glClearColor(float r, float g, float b, float a) {
        getBackend().glClearColor(r, g, b, a);
    }
    
    public static int glGetAttribLocation(int program, String name) {
        return getBackend().glGetAttribLocation(program, name);
    }
    
    public static void glGenerateMipmap(int target) {
        getBackend().glGenerateMipmap(target);
    }
    
    public static void glBlitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        getBackend().glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }
    
    // DSA (Direct State Access) methods - ARB versions
    
    public static void glGenerateTextureMipmap(int texture) {
        getBackend().glGenerateTextureMipmap(texture);
    }
    
    public static void glTextureParameteri(int texture, int pname, int param) {
        getBackend().glTextureParameteri(texture, pname, param);
    }
    
    public static void glTextureParameterf(int texture, int pname, float param) {
        getBackend().glTextureParameterf(texture, pname, param);
    }
    
    public static void glTextureParameteriv(int texture, int pname, int[] params) {
        getBackend().glTextureParameteriv(texture, pname, params);
    }
    
    public static void glNamedFramebufferReadBuffer(int framebuffer, int mode) {
        getBackend().glNamedFramebufferReadBuffer(framebuffer, mode);
    }
    
    public static void glNamedFramebufferDrawBuffers(int framebuffer, int[] bufs) {
        getBackend().glNamedFramebufferDrawBuffers(framebuffer, bufs);
    }
    
    public static void glClearNamedFramebufferfv(int framebuffer, int buffer, int drawbuffer, float[] value) {
        getBackend().glClearNamedFramebufferfv(framebuffer, buffer, drawbuffer, value);
    }
    
    public static void glClearNamedFramebufferiv(int framebuffer, int buffer, int drawbuffer, int[] value) {
        getBackend().glClearNamedFramebufferiv(framebuffer, buffer, drawbuffer, value);
    }
    
    public static void glClearNamedFramebufferuiv(int framebuffer, int buffer, int drawbuffer, int[] value) {
        getBackend().glClearNamedFramebufferuiv(framebuffer, buffer, drawbuffer, value);
    }
    
    public static int glGetTextureParameteri(int texture, int pname) {
        return getBackend().glGetTextureParameteri(texture, pname);
    }
    
    public static void glCopyTextureSubImage2D(int texture, int level, int xoffset, int yoffset, int x, int y, int width, int height) {
        getBackend().glCopyTextureSubImage2D(texture, level, xoffset, yoffset, x, y, width, height);
    }
    
    public static void glBindTextureUnit(int unit, int texture) {
        getBackend().glBindTextureUnit(unit, texture);
    }
    
    public static int glCreateBuffers() {
        return getBackend().glCreateBuffers();
    }
    
    public static void glNamedBufferData(int buffer, float[] data, int usage) {
        getBackend().glNamedBufferData(buffer, data, usage);
    }
    
    public static void glBlitNamedFramebuffer(int readFramebuffer, int drawFramebuffer, int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        getBackend().glBlitNamedFramebuffer(readFramebuffer, drawFramebuffer, srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }
    
    public static void glNamedFramebufferTexture(int framebuffer, int attachment, int texture, int level) {
        getBackend().glNamedFramebufferTexture(framebuffer, attachment, texture, level);
    }
    
    public static int glCreateFramebuffers() {
        return getBackend().glCreateFramebuffers();
    }
    
    public static int glCreateTextures(int target) {
        return getBackend().glCreateTextures(target);
    }
    
    // Additional rendering operations
    public static void glDrawElements(int mode, int count, int type, long indices) {
        getBackend().glDrawElements(mode, count, type, indices);
    }
    
    public static void glBlendEquation(int mode) {
        getBackend().glBlendEquation(mode);
    }
    
    public static void glClearDepth(double depth) {
        getBackend().glClearDepth(depth);
    }
    
    public static int glGetFramebufferAttachmentParameteri(int target, int attachment, int pname) {
        return getBackend().glGetFramebufferAttachmentParameteri(target, attachment, pname);
    }
    
    // Debug callback control methods (low-level callback control methods only)
    // Note: The high-level setup methods below use Vulkanic functional interfaces
    public static void glDebugMessageControl(int source, int type, int severity, int[] ids, boolean enabled) {
        getBackend().glDebugMessageControl(source, type, severity, ids, enabled);
    }
    
    public static void glDebugMessageControlKHR(int source, int type, int severity, int[] ids, boolean enabled) {
        getBackend().glDebugMessageControlKHR(source, type, severity, ids, enabled);
    }
    
    public static void glDebugMessageControlARB(int source, int type, int severity, int[] ids, boolean enabled) {
        getBackend().glDebugMessageControlARB(source, type, severity, ids, enabled);
    }
    
    public static void glDebugMessageEnableAMD(int category, int severity, int[] ids, boolean enabled) {
        getBackend().glDebugMessageEnableAMD(category, severity, ids, enabled);
    }
    
    // High-level debug callback wrapper methods that accept functional interfaces
    public static void setupDebugMessageCallback(DebugMessageCallback callback) {
        getBackend().setupDebugMessageCallback(callback);
    }
    
    public static void setupDebugMessageCallbackKHR(DebugMessageCallback callback) {
        getBackend().setupDebugMessageCallbackKHR(callback);
    }
    
    public static void setupDebugMessageCallbackARB(DebugMessageCallbackARB callback) {
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
    
    /**
     * Binds a vertex attribute to a specific location in a shader program.
     * Wrapper for bindAttributeLocation.
     */
    public static void glBindAttribLocation(int program, int index, CharSequence name) {
        bindAttributeLocation(program, index, name);
    }
    
    /**
     * Configures a vertex attribute pointer.
     * Wrapper for configureVertexAttribute.
     */
    public static void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long pointer) {
        configureVertexAttribute(index, size, type, normalized, stride, pointer);
    }
    
    /**
     * Enables a vertex attribute array.
     * Wrapper for activateVertexAttribute.
     */
    public static void glEnableVertexAttribArray(int index) {
        activateVertexAttribute(index);
    }
    
    /**
     * Creates a new shader program object.
     * Wrapper for constructProgramObject.
     */
    public static int glCreateProgram() {
        return constructProgramObject();
    }
    
    /**
     * Attaches a shader to a program.
     * Wrapper for attachShaderToProgram.
     */
    public static void glAttachShader(int program, int shader) {
        attachShaderToProgram(program, shader);
    }
    
    /**
     * Links a program object.
     * Wrapper for linkProgramBinary.
     */
    public static void glLinkProgram(int program) {
        linkProgramBinary(program);
    }
    
    /**
     * Returns a parameter from a program object.
     * Wrapper for queryProgramParameter.
     */
    public static int glGetProgrami(int program, int pname) {
        return queryProgramParameter(program, pname);
    }
    
    /**
     * Installs a program object as part of current rendering state.
     * Wrapper for useProgram.
     */
    public static void glUseProgram(int program) {
        useProgram(program);
    }
    
    /**
     * Deletes a program object.
     * Wrapper for disposeProgramObject.
     */
    public static void glDeleteProgram(int program) {
        disposeProgramObject(program);
    }
    
    /**
     * Returns the location of a uniform variable.
     * Wrapper for locateUniformVariable.
     */
    public static int glGetUniformLocation(int program, CharSequence name) {
        return locateUniformVariable(program, name);
    }
    
    /**
     * Sets the value of a uniform variable.
     * Wrapper for assignUniformInteger.
     */
    public static void glUniform1i(int location, int value) {
        assignUniformInteger(location, value);
    }
    
    // GL43+ Vertex Attribute methods
    
    /**
     * Binds a buffer to a vertex buffer bind point (GL43+).
     */
    public static void glBindVertexBuffer(int bindingindex, int buffer, long offset, int stride) {
        getBackend().bindVertexBuffer(bindingindex, buffer, offset, stride);
    }
    
    /**
     * Specifies the organization of vertex arrays (GL43+).
     */
    public static void glVertexAttribFormat(int attribindex, int size, int type, boolean normalized, int relativeoffset) {
        getBackend().vertexAttribFormat(attribindex, size, type, normalized, relativeoffset);
    }
    
    /**
     * Specifies the organization of vertex arrays for integer data (GL43+).
     */
    public static void glVertexAttribIFormat(int attribindex, int size, int type, int relativeoffset) {
        getBackend().vertexAttribIFormat(attribindex, size, type, relativeoffset);
    }
    
    /**
     * Associates a vertex attribute and a vertex buffer binding (GL43+).
     */
    public static void glVertexAttribBinding(int attribindex, int bindingindex) {
        getBackend().vertexAttribBinding(attribindex, bindingindex);
    }
    
    /**
     * Disables a generic vertex attribute array.
     */
    public static void glDisableVertexAttribArray(int index) {
        deactivateVertexAttribute(index);
    }
    
    /**
     * Defines an array of generic vertex attribute data with integer data.
     * Wrapper for configureVertexAttributeInteger.
     */
    public static void glVertexAttribIPointer(int index, int size, int type, int stride, long pointer) {
        configureVertexAttributeInteger(index, size, type, stride, pointer);
    }
    
    // VAO methods
    
    /**
     * Generates vertex array object names.
     */
    public static int glGenVertexArrays() {
        return getBackend().genVertexArrays();
    }
    
    /**
     * Binds a vertex array object.
     */
    public static void glBindVertexArray(int array) {
        getBackend().bindVertexArray(array);
    }
    
    /**
     * Deletes vertex array objects.
     */
    public static void glDeleteVertexArrays(int array) {
        getBackend().deleteVertexArrays(array);
    }
    
    // GL.getCapabilities() and GLUtil support
    
    /**
     * Gets the OpenGL capabilities for the current context.
     * Returns a platform-specific capabilities object.
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
}
