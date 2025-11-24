package mattmc.client.renderer;

import static org.lwjgl.opengl.GL11.*;

/**
 * Renders the crosshair in the center of the screen.
 * 
 * <p><b>Stage 4 Refactored:</b> This renderer now uses the backend architecture.
 * It delegates to {@link UIRenderLogic} to build draw commands, then submits them
 * to the {@link RenderBackend}.
 * 
 * <p>This class still handles the 2D projection setup (which is rendering infrastructure,
 * not game logic), but the actual crosshair rendering is done through the backend.
 */
public class CrosshairRenderer {
    
    private final UIRenderLogic logic;
    private final CommandBuffer buffer;
    private RenderBackend backend = null;
    
    /**
     * Create a new crosshair renderer.
     */
    public CrosshairRenderer() {
        this.logic = new UIRenderLogic();
        this.buffer = new CommandBuffer();
    }
    
    /**
     * Set the render backend to use for rendering (Stage 4).
     * 
     * @param backend the backend to use, or null to use legacy rendering
     */
    public void setBackend(RenderBackend backend) {
        this.backend = backend;
    }
    
    /**
     * Draw crosshair in the center of the screen using backend architecture.
     * 
     * @param screenWidth screen width
     * @param screenHeight screen height
     */
    public void render(int screenWidth, int screenHeight) {
        // Use backend if available (Stage 4)
        if (backend != null) {
            // Setup 2D projection for UI rendering (infrastructure)
            UIRenderHelper.setup2DProjection(screenWidth, screenHeight);
            
            // Stage 4: Build commands using logic layer (no GL calls in logic)
            buffer.clear();
            logic.buildCrosshairCommands(screenWidth, screenHeight, buffer);
            
            // Submit commands to backend
            for (DrawCommand cmd : buffer.getCommands()) {
                backend.submit(cmd);
            }
            
            // Restore projection (infrastructure)
            UIRenderHelper.restore2DProjection();
        } else {
            // Legacy fallback
            renderLegacy(screenWidth, screenHeight);
        }
    }
    
    /**
     * Legacy render method for compatibility.
     * This method is deprecated and will be removed once backend is always provided.
     * 
     * @param screenWidth screen width
     * @param screenHeight screen height
     * @deprecated Backend should always be provided via setBackend()
     */
    @Deprecated
    private void renderLegacy(int screenWidth, int screenHeight) {
        // Fallback to direct rendering if no backend provided
        // This maintains compatibility during transition
        UIRenderHelper.setup2DProjection(screenWidth, screenHeight);
        
        // Draw white crosshair (original implementation)
        float centerX = screenWidth / 2f;
        float centerY = screenHeight / 2f;
        float size = 10f;
        float thickness = 2f;
        
        glColor4f(1f, 1f, 1f, 1f);
        glBegin(GL_QUADS);
        // Horizontal line
        glVertex2f(centerX - size, centerY - thickness/2);
        glVertex2f(centerX + size, centerY - thickness/2);
        glVertex2f(centerX + size, centerY + thickness/2);
        glVertex2f(centerX - size, centerY + thickness/2);
        // Vertical line
        glVertex2f(centerX - thickness/2, centerY - size);
        glVertex2f(centerX + thickness/2, centerY - size);
        glVertex2f(centerX + thickness/2, centerY + size);
        glVertex2f(centerX - thickness/2, centerY + size);
        glEnd();
        
        // Restore projection
        UIRenderHelper.restore2DProjection();
    }
}
