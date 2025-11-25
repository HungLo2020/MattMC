package mattmc.client.renderer.backend;

import mattmc.client.renderer.window.WindowHandle;
import mattmc.client.renderer.WorldRenderer;
import mattmc.client.renderer.item.ItemRenderer;

/**
 * Factory for creating render backends and associated windows.
 * 
 * <p>This factory provides a way to create rendering infrastructure without
 * directly importing backend-specific classes (like OpenGL types) in bootstrap code.
 * This enables future support for multiple rendering backends (Vulkan, etc.)
 * by centralizing backend selection logic here.
 * 
 * <p><b>Usage:</b>
 * <pre>
 * // Create the default backend (currently OpenGL)
 * RenderBackendFactory factory = RenderBackendFactory.createDefault();
 * WindowHandle window = factory.createWindow(1280, 720, "My Game");
 * RenderBackend backend = factory.createBackend();
 * </pre>
 * 
 * <p><b>Future Extension:</b> When adding Vulkan support, add a method like
 * {@code createVulkan()} that returns a Vulkan-configured factory.
 * 
 * @since Rendering refactor - Backend abstraction
 */
public interface RenderBackendFactory {
    
    /**
     * Create a window using the appropriate backend implementation.
     * 
     * <p>The window implementation depends on the backend type:
     * <ul>
     *   <li><b>OpenGL:</b> Creates a GLFW window with OpenGL context</li>
     *   <li><b>Vulkan (future):</b> Would create a GLFW window with Vulkan surface</li>
     * </ul>
     * 
     * @param width initial window width in pixels
     * @param height initial window height in pixels
     * @param title window title
     * @return a backend-agnostic window handle
     */
    WindowHandle createWindow(int width, int height, String title);
    
    /**
     * Create a render backend for the configured graphics API.
     * 
     * <p>The backend implementation depends on the factory type:
     * <ul>
     *   <li><b>OpenGL:</b> Creates OpenGLRenderBackend</li>
     *   <li><b>Vulkan (future):</b> Would create VulkanRenderBackend</li>
     * </ul>
     * 
     * @return a render backend instance
     */
    RenderBackend createBackend();
    
    /**
     * Create a world renderer for the configured graphics API.
     * 
     * <p>The world renderer implementation depends on the factory type:
     * <ul>
     *   <li><b>OpenGL:</b> Creates LevelRenderer (OpenGL implementation)</li>
     *   <li><b>Vulkan (future):</b> Would create VulkanLevelRenderer</li>
     * </ul>
     * 
     * @return a world renderer instance
     */
    WorldRenderer createWorldRenderer();
    
    /**
     * Get the item renderer for the configured graphics API.
     * 
     * <p>The item renderer implementation depends on the factory type:
     * <ul>
     *   <li><b>OpenGL:</b> Returns OpenGLItemRenderer instance</li>
     *   <li><b>Vulkan (future):</b> Would return VulkanItemRenderer</li>
     * </ul>
     * 
     * @return the item renderer instance
     */
    ItemRenderer getItemRenderer();
    
    /**
     * Get a closeable wrapper for the window that handles proper resource cleanup.
     * 
     * <p>The returned AutoCloseable should be used in a try-with-resources block
     * to ensure proper cleanup of window resources (e.g., GLFW termination).
     * 
     * @return the window as an AutoCloseable, or null if createWindow() hasn't been called
     */
    AutoCloseable getWindowCloseable();
    
    /**
     * Create the default render backend factory.
     * 
     * <p>Currently this returns an OpenGL factory. In the future, this could
     * be configured via system properties, config files, or command-line arguments
     * to select between OpenGL, Vulkan, or other backends.
     * 
     * @return the default (OpenGL) backend factory
     */
    static RenderBackendFactory createDefault() {
        return new mattmc.client.renderer.backend.opengl.OpenGLBackendFactory();
    }
    
    /**
     * Create an OpenGL render backend factory.
     * 
     * @return an OpenGL backend factory
     */
    static RenderBackendFactory createOpenGL() {
        return new mattmc.client.renderer.backend.opengl.OpenGLBackendFactory();
    }
}
