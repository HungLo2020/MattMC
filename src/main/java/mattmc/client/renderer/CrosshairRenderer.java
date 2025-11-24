package mattmc.client.renderer;

import mattmc.client.renderer.backend.RenderBackend;
import mattmc.client.renderer.backend.DrawCommand;
import mattmc.client.renderer.backend.opengl.UIRenderHelper;

/**
 * Crosshair renderer coordinator.
 * 
 * <p>This class is responsible for determining <em>what</em> to draw (the crosshair)
 * and coordinating with the backend to actually render it.
 * 
 * <p><b>Architecture:</b> This is a "coordinator" class that:
 * <ul>
 *   <li>Uses {@link UIRenderLogic} to build draw commands (what to draw)</li>
 *   <li>Submits commands to the {@link RenderBackend} (how to draw)</li>
 *   <li>Handles 2D projection setup (OpenGL infrastructure code)</li>
 * </ul>
 * 
 * <p><b>Note:</b> While this class lives outside backend/opengl/, it does use
 * {@link UIRenderHelper} for 2D projection setup, which is OpenGL-specific infrastructure.
 * This is necessary infrastructure code for UI rendering that would need to be abstracted
 * further for true multi-backend support.
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
     * <p>This method coordinates the rendering process by setting up the 2D projection,
     * building draw commands, and submitting them to the backend.
     * 
     * @param screenWidth screen width in pixels
     * @param screenHeight screen height in pixels
     * @throws IllegalStateException if backend has not been set
     */
    public void render(int screenWidth, int screenHeight) {
        if (backend == null) {
            throw new IllegalStateException("Backend must be set before calling render()");
        }
        
        // Setup 2D projection for UI rendering
        UIRenderHelper.setup2DProjection(screenWidth, screenHeight);
        
        // Build draw commands using backend-agnostic logic
        buffer.clear();
        logic.buildCrosshairCommands(screenWidth, screenHeight, buffer);
        
        // Submit commands to backend with frame management
        backend.beginFrame();
        for (DrawCommand cmd : buffer.getCommands()) {
            backend.submit(cmd);
        }
        backend.endFrame();
        
        // Restore projection
        UIRenderHelper.restore2DProjection();
    }
}
