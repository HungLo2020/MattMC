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
}
