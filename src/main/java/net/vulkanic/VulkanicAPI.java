package net.vulkanic;

import net.vulkanic.backends.opengl.OpenGLBackend;

/**
 * Main entry point for the Vulkanic Graphics Abstraction Layer.
 * Provides a unified API for graphics operations that can be backed by different graphics APIs.
 */
public class VulkanicAPI {
    private static GraphicsBackend backend;
    
    // OpenGL Constants - Buffer Targets
    public static final int GL_COPY_READ_BUFFER = 0x8F36;
    public static final int GL_COPY_WRITE_BUFFER = 0x8F37;
    public static final int GL_SHADER_STORAGE_BUFFER = 0x90D2;
    
    // OpenGL Constants - String Names
    public static final int GL_VENDOR = 0x1F00;
    public static final int GL_RENDERER = 0x1F01;
    public static final int GL_VERSION = 0x1F02;
    
    // OpenGL Constants - Sync
    public static final int GL_SYNC_GPU_COMMANDS_COMPLETE = 0x9117;
    
    // OpenGL Constants - Primitive Types
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
    public static final int GL_TEXTURE_BASE_LEVEL = 0x813C;  // 33084
    public static final int GL_TEXTURE_MAX_LEVEL = 0x813D;   // 33085
    
    // OpenGL Constants - Framebuffer/Buffer
    public static final int GL_FRAMEBUFFER = 0x8D40;     // 36160
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
    
    // OpenGL Constants - Debug Severity Levels
    public static final int GL_DEBUG_SEVERITY_HIGH = 0x9146;
    public static final int GL_DEBUG_SEVERITY_MEDIUM = 0x9147;
    public static final int GL_DEBUG_SEVERITY_LOW = 0x9148;
    public static final int GL_DEBUG_SEVERITY_NOTIFICATION = 0x826B;
    
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
    
    // Convenience methods that delegate to the backend
    
    public static void bindTexture(int textureId) {
        getBackend().bindTexture(textureId);
    }
    
    public static void bindTexture(int target, int textureId) {
        getBackend().bindTexture(target, textureId);
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
}
