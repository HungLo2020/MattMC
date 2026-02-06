package net.vulkanic;

import net.vulkanic.backends.opengl.OpenGLBackend;

/**
 * Main entry point for the Vulkanic Graphics Abstraction Layer.
 * Provides a unified API for graphics operations that can be backed by different graphics APIs.
 */
public class VulkanicAPI {
    private static GraphicsBackend backend;
    
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
}
