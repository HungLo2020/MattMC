package mattmc.client.renderer;

import mattmc.client.renderer.backend.DrawCommand;
import mattmc.client.renderer.backend.RenderBackend;

/**
 * Backend-agnostic lighting debug renderer.
 * 
 * <p>This class is responsible for determining <em>what</em> to draw (lighting debug stats)
 * and coordinating with the backend to actually render it. It contains NO OpenGL-specific
 * code and works purely through the {@link RenderBackend} abstraction.
 * 
 * <p><b>Architecture:</b> This is a "coordinator" class that:
 * <ul>
 *   <li>Uses {@link UIRenderLogic} to build draw commands (what to draw)</li>
 *   <li>Submits commands to the {@link RenderBackend} (how to draw)</li>
 *   <li>Delegates projection setup to the backend (backend-agnostic)</li>
 * </ul>
 */
public class LightingDebugRenderer {
    
    private final UIRenderLogic logic;
    private final CommandBuffer buffer;
    private RenderBackend backend;
    
    /**
     * Create a new lighting debug renderer.
     */
    public LightingDebugRenderer() {
        this.logic = new UIRenderLogic();
        this.buffer = new CommandBuffer();
    }
    
    /**
     * Set the render backend to use for rendering.
     * 
     * @param backend the backend to use (must not be null)
     * @throws IllegalArgumentException if backend is null
     */
    public void setBackend(RenderBackend backend) {
        if (backend == null) {
            throw new IllegalArgumentException("Backend cannot be null");
        }
        this.backend = backend;
    }
    
    /**
     * Draw lighting debug overlay showing relight scheduler statistics.
     * Displays backlog size, nodes processed, and time spent.
     * 
     * <p>This method is completely backend-agnostic. It delegates projection setup,
     * builds draw commands, and submits them to the backend for rendering.
     * 
     * @param screenWidth Screen width in pixels
     * @param screenHeight Screen height in pixels
     * @param backlogSize Number of pending light updates
     * @param nodesProcessed Number of nodes processed last frame
     * @param timeSpent Time spent in milliseconds last frame
     * @throws IllegalStateException if backend has not been set
     */
    public void render(int screenWidth, int screenHeight, int backlogSize, 
                       int nodesProcessed, double timeSpent) {
        if (backend == null) {
            throw new IllegalStateException("Backend must be set before calling render()");
        }
        
        // Setup 2D projection for UI rendering (delegated to backend)
        backend.setup2DProjection(screenWidth, screenHeight);
        
        // Build draw commands using backend-agnostic logic
        buffer.clear();
        logic.beginFrame(); // Clear text registry and set this instance as current
        logic.buildLightingDebugCommands(screenWidth, screenHeight, backlogSize, 
                                         nodesProcessed, timeSpent, buffer);
        
        // Submit commands to backend with frame management
        backend.beginFrame();
        for (DrawCommand cmd : buffer.getCommands()) {
            backend.submit(cmd);
        }
        backend.endFrame();
        
        // Restore projection (delegated to backend)
        backend.restore2DProjection();
    }
}
