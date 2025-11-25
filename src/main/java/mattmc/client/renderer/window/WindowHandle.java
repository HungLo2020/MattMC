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
    
    /**
     * Set the window size.
     * @param width new width in pixels
     * @param height new height in pixels
     */
    void setSize(int width, int height);
    
    /**
     * Set fullscreen mode.
     * @param fullscreen true to enable fullscreen, false for windowed
     */
    void setFullscreen(boolean fullscreen);
    
    /**
     * Apply FPS cap settings (enable/disable VSync based on settings).
     */
    void applyFpsCapSetting();
    
    /**
     * Check if the window should close.
     * 
     * <p>This method should also poll for window events (e.g., input events).
     * 
     * @return true if the window should close
     */
    boolean shouldClose();
    
    /**
     * Swap the front and back buffers (present the frame).
     * 
     * <p>This method should be called after rendering each frame to display
     * the rendered content.
     */
    void swap();
}
