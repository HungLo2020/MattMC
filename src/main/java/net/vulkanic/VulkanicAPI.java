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
}
