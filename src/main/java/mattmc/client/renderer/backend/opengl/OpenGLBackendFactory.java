package mattmc.client.renderer.backend.opengl;

import mattmc.client.renderer.backend.RenderBackend;
import mattmc.client.renderer.backend.RenderBackendFactory;
import mattmc.client.renderer.window.WindowHandle;
import mattmc.client.renderer.WorldRenderer;
import mattmc.client.renderer.level.LevelRenderer;

/**
 * OpenGL implementation of the render backend factory.
 * 
 * <p>This factory creates OpenGL-specific rendering infrastructure:
 * <ul>
 *   <li>Window: GLFW window with OpenGL context</li>
 *   <li>Backend: OpenGLRenderBackend for rendering operations</li>
 *   <li>WorldRenderer: Backend-agnostic LevelRenderer with OpenGL mesh manager</li>
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
    private OpenGLRenderBackend backend;
    private OpenGLChunkMeshManager meshManager;
    
    @Override
    public WindowHandle createWindow(int width, int height, String title) {
        this.window = new Window(width, height, title);
        return window;
    }
    
    @Override
    public RenderBackend createBackend() {
        this.backend = new OpenGLRenderBackend();
        return backend;
    }
    
    @Override
    public WorldRenderer createWorldRenderer() {
        // Create the mesh manager and backend-agnostic LevelRenderer
        this.meshManager = new OpenGLChunkMeshManager();
        
        // Ensure backend is created
        if (backend == null) {
            backend = new OpenGLRenderBackend();
        }
        
        return new LevelRenderer(meshManager, backend);
    }
    
    @Override
    public AutoCloseable getWindowCloseable() {
        return window;
    }
}
