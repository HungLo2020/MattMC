package mattmc.client.renderer;

import mattmc.client.renderer.backend.DrawCommand;
import mattmc.client.renderer.backend.RenderBackend;
import mattmc.client.renderer.backend.opengl.gui.components.TextRenderer;
import mattmc.util.MathUtils;

/**
 * Renders tooltips for inventory items.
 * Displays a small semi-transparent box with blue border and item name.
 * 
 * <p>Fully backend-agnostic - uses RenderBackend interface for all rendering operations.
 */
public class TooltipRenderer {
    private static final float TOOLTIP_PADDING = 13.5f;
    private static final float TOOLTIP_CORNER_RADIUS = 9f;
    private static final float TOOLTIP_OFFSET_X = 18f;
    private static final float TOOLTIP_OFFSET_Y = -18f;
    private static final float TEXT_SCALE = 1.8f;
    
    // Colors
    private static final float BORDER_R = 0.3f;
    private static final float BORDER_G = 0.5f;
    private static final float BORDER_B = 1.0f;
    private static final float BORDER_ALPHA = 1.0f;
    private static final float BORDER_WIDTH = 6f;
    
    private RenderBackend backend;
    private final UIRenderLogic logic = new UIRenderLogic();
    
    public TooltipRenderer() {
    }
    
    /**
     * Set the render backend to use for rendering.
     * 
     * @param backend the render backend
     */
    public void setBackend(RenderBackend backend) {
        this.backend = backend;
    }
    
    /**
     * Render a tooltip at the specified mouse position.
     * @param text The text to display
     * @param mouseFBX Mouse X position in framebuffer coordinates
     * @param mouseFBY Mouse Y position in framebuffer coordinates
     * @param screenWidth Screen width in pixels
     * @param screenHeight Screen height in pixels
     */
    public void renderTooltip(String text, float mouseFBX, float mouseFBY, int screenWidth, int screenHeight) {
        if (text == null || text.isEmpty() || backend == null) {
            return;
        }
        
        // Calculate text dimensions
        float textWidth = TextRenderer.getTextWidth(text, TEXT_SCALE);
        float textHeight = TextRenderer.getTextHeight(text, TEXT_SCALE);
        
        // Calculate tooltip box dimensions
        float boxWidth = textWidth + TOOLTIP_PADDING * 2;
        float boxHeight = textHeight + TOOLTIP_PADDING * 2;
        
        // Calculate tooltip position (above and to the right of cursor)
        float tooltipX = mouseFBX + TOOLTIP_OFFSET_X;
        float tooltipY = mouseFBY + TOOLTIP_OFFSET_Y - boxHeight;
        
        // Clamp to screen bounds
        tooltipX = MathUtils.clamp(tooltipX, 0, screenWidth - boxWidth);
        tooltipY = MathUtils.clamp(tooltipY, 0, screenHeight - boxHeight);
        
        // Setup 2D projection
        backend.setup2DProjection(screenWidth, screenHeight);
        
        backend.beginFrame();
        
        // Apply blur to the tooltip region BEFORE drawing the tooltip
        backend.applyRegionalBlur(tooltipX, tooltipY, boxWidth, boxHeight, screenWidth, screenHeight);
        
        // Draw blue border with rounded corners
        backend.drawRoundedRectBorder(tooltipX, tooltipY, boxWidth, boxHeight, TOOLTIP_CORNER_RADIUS, 
                                     BORDER_WIDTH, BORDER_R, BORDER_G, BORDER_B, BORDER_ALPHA);
        
        // Reset GL color to white before drawing text so it appears white not blue
        backend.resetColor();
        
        // Draw the text (positioned with padding)
        float textX = tooltipX + TOOLTIP_PADDING;
        float textY = tooltipY + TOOLTIP_PADDING;
        TextRenderer.drawText(text, textX, textY, TEXT_SCALE);
        
        backend.endFrame();
        
        // Restore projection
        backend.restore2DProjection();
    }
}
