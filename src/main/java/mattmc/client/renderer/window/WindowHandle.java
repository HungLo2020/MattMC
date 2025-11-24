package mattmc.client.renderer.window;

/**
 * Backend-agnostic interface for window operations.
 * 
 * <p>This interface abstracts away the details of how the window is implemented
 * (e.g., GLFW for OpenGL, platform-specific for Vulkan), allowing game logic to
 * access window properties without depending on OpenGL-specific implementations.
 * 
 * <p><b>Architecture:</b> This allows GUI/screen code to be defined outside the
 * backend/ directory while the actual window management remains in the backend.
 * 
 * @see mattmc.client.renderer.backend.opengl.Window OpenGL implementation
 */
public interface WindowHandle {
    
    /**
     * Get the native window handle.
     * <p>For GLFW, this is the long returned by glfwCreateWindow.
     * This is needed for setting up input callbacks and coordinate conversion.
     * 
     * @return the native window handle
     */
    long handle();
    
    /**
     * Get the current window width in pixels.
     * @return the window width
     */
    int width();
    
    /**
     * Get the current window height in pixels.
     * @return the window height
     */
    int height();
}
