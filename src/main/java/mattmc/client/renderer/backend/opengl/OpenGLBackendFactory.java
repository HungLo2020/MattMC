package mattmc.client.renderer.backend.opengl;

import mattmc.client.renderer.backend.RenderBackend;
import mattmc.client.renderer.backend.RenderBackendFactory;
import mattmc.client.renderer.window.WindowHandle;
import mattmc.client.renderer.WorldRenderer;

/**
 * OpenGL implementation of the render backend factory.
 * 
 * <p>This factory creates OpenGL-specific rendering infrastructure:
 * <ul>
 *   <li>Window: GLFW window with OpenGL context</li>
 *   <li>Backend: OpenGLRenderBackend for rendering operations</li>
 *   <li>WorldRenderer: LevelRenderer for world/chunk rendering</li>
 * </ul>
 * 
 * <p><b>INTERNAL USE:</b> This class should not be directly instantiated by code
 * outside the backend package. Use {@link RenderBackendFactory#createDefault()}
 * or {@link RenderBackendFactory#createOpenGL()} instead.
 * 
 * @since Rendering refactor - Backend abstraction
 */
public class OpenGLBackendFactory implements RenderBackendFactory {
    
    private Window window;
    
    @Override
    public WindowHandle createWindow(int width, int height, String title) {
        this.window = new Window(width, height, title);
        return window;
    }
    
    @Override
    public RenderBackend createBackend() {
        return new OpenGLRenderBackend();
    }
    
    @Override
    public WorldRenderer createWorldRenderer() {
        return new LevelRenderer();
    }
    
    @Override
    public AutoCloseable getWindowCloseable() {
        return window;
    }
}
