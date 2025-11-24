package mattmc.client.renderer;

import mattmc.client.renderer.backend.RenderBackend;
import mattmc.client.renderer.backend.DrawCommand;

/**
 * Backend-agnostic crosshair renderer.
 * 
 * <p>This class is responsible for determining <em>what</em> to draw (the crosshair)
 * and coordinating with the backend to actually render it. It contains NO OpenGL-specific
 * code and works purely through the {@link RenderBackend} abstraction.
 * 
 * <p><b>Architecture:</b> This is a "coordinator" class that:
 * <ul>
 *   <li>Uses {@link UIRenderLogic} to build draw commands (what to draw)</li>
 *   <li>Submits commands to the {@link RenderBackend} (how to draw)</li>
 *   <li>Contains no graphics API-specific code (OpenGL, Vulkan, etc.)</li>
 * </ul>
 * 
 * <p><b>Abstraction Layer:</b> This class lives outside the backend/ directory
 * and can be safely used by any code in the application. It depends only on the
 * backend interface, not on any specific implementation.
 */
public class CrosshairRenderer {
    
    private final UIRenderLogic logic;
    private final CommandBuffer buffer;
    private RenderBackend backend;
    
    /**
     * Create a new crosshair renderer.
     */
    public CrosshairRenderer() {
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
     * Render the crosshair in the center of the screen.
     * 
     * <p>This method is completely backend-agnostic. It builds draw commands
     * describing what to draw and submits them to the backend for rendering.
     * 
     * @param screenWidth screen width in pixels
     * @param screenHeight screen height in pixels
     * @throws IllegalStateException if backend has not been set
     */
    public void render(int screenWidth, int screenHeight) {
        if (backend == null) {
            throw new IllegalStateException("Backend must be set before calling render()");
        }
        
        // Build draw commands using backend-agnostic logic
        buffer.clear();
        logic.buildCrosshairCommands(screenWidth, screenHeight, buffer);
        
        // Submit commands to backend
        // Note: Frame management (beginFrame/endFrame) should be handled by the caller
        // at a higher level, not per-component
        for (DrawCommand cmd : buffer.getCommands()) {
            backend.submit(cmd);
        }
    }
}
