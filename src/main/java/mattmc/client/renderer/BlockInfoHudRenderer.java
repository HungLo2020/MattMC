package mattmc.client.renderer;

import mattmc.client.gui.components.TextRenderer;

import static org.lwjgl.opengl.GL11.*;

/**
 * Renders block info HUD at the top center of the screen.
 * Similar to TooltipRenderer but designed for in-game HUD display.
 * Shows the name of the block the player is looking at.
 */
public class BlockInfoHudRenderer {
    // Increased padding for larger box (50% larger than tooltip)
    private static final float HUD_PADDING = 20.25f;  // 50% larger than tooltip (13.5 * 1.5)
    private static final float HUD_CORNER_RADIUS = 13.5f;  // 50% larger than tooltip (9 * 1.5)
    private static final float HUD_OFFSET_Y = 20f;  // Distance from top of screen
    private static final float TEXT_SCALE = 1.8f;  // Same as tooltip
    
    // Colors - matching tooltip style
    private static final float BG_GRAY = 0.3f;
    private static final float BG_ALPHA = 0.08f;  // Very subtle gray tint to show blur through
    private static final float BORDER_R = 0.3f;
    private static final float BORDER_G = 0.5f;
    private static final float BORDER_B = 1.0f;
    private static final float BORDER_ALPHA = 1.0f;
    private static final float BORDER_WIDTH = 6f;
    
    private final TooltipBlurEffect blurEffect;
    
    public BlockInfoHudRenderer() {
        this.blurEffect = new TooltipBlurEffect();
    }
    
    /**
     * Render block info HUD at the top center of the screen.
     * Assumes the projection matrix is already set to screen coordinates.
     * @param text The block name to display
     * @param screenWidth Screen width in pixels
     * @param screenHeight Screen height in pixels
     */
    public void renderBlockInfo(String text, int screenWidth, int screenHeight) {
        if (text == null || text.isEmpty()) {
            return;
        }
        
        // Calculate text dimensions
        float textWidth = TextRenderer.getTextWidth(text, TEXT_SCALE);
        float textHeight = TextRenderer.getTextHeight(text, TEXT_SCALE);
        
        // Calculate HUD box dimensions
        float boxWidth = textWidth + HUD_PADDING * 2;
        float boxHeight = textHeight + HUD_PADDING * 2;
        
        // Calculate position at top center of screen
        float hudX = (screenWidth - boxWidth) / 2f;
        float hudY = HUD_OFFSET_Y;
        
        // Apply blur to the HUD region BEFORE drawing the HUD
        blurEffect.applyRegionalBlur(hudX, hudY, boxWidth, boxHeight, screenWidth, screenHeight);
        
        // Draw blue border with rounded corners
        drawRoundedRectBorder(hudX, hudY, boxWidth, boxHeight, HUD_CORNER_RADIUS, 
                             BORDER_WIDTH, BORDER_R, BORDER_G, BORDER_B, BORDER_ALPHA);
        
        // Draw text
        glColor4f(1f, 1f, 1f, 1f);
        TextRenderer.drawText(text, hudX + HUD_PADDING, hudY + HUD_PADDING, TEXT_SCALE);
    }
    
    /**
     * Draw a rounded rectangle border (outline).
     * Draws lines connecting the four rounded corners.
     */
    private void drawRoundedRectBorder(float x, float y, float width, float height, float radius, 
                                       float borderWidth, float r, float g, float b, float a) {
        glColor4f(r, g, b, a);
        glLineWidth(borderWidth);
        
        // Clamp radius to not exceed half the smaller dimension
        radius = Math.min(radius, Math.min(width, height) / 2f);
        
        int segments = 32; // More segments for very smooth corners
        
        glBegin(GL_LINE_STRIP);
        
        // Top-left corner (starting from left side going to top)
        for (int i = 0; i <= segments; i++) {
            float angle = (float) Math.PI * 1.0f + ((float) Math.PI * 0.5f * i / segments);
            glVertex2f(x + radius + radius * (float) Math.cos(angle), 
                      y + radius + radius * (float) Math.sin(angle));
        }
        
        // Top-right corner (from top going to right)
        for (int i = 0; i <= segments; i++) {
            float angle = (float) Math.PI * 1.5f + ((float) Math.PI * 0.5f * i / segments);
            glVertex2f(x + width - radius + radius * (float) Math.cos(angle), 
                      y + radius + radius * (float) Math.sin(angle));
        }
        
        // Bottom-right corner (from right going to bottom)
        for (int i = 0; i <= segments; i++) {
            float angle = (float) Math.PI * 0.0f + ((float) Math.PI * 0.5f * i / segments);
            glVertex2f(x + width - radius + radius * (float) Math.cos(angle), 
                      y + height - radius + radius * (float) Math.sin(angle));
        }
        
        // Bottom-left corner (from bottom going to left)
        for (int i = 0; i <= segments; i++) {
            float angle = (float) Math.PI * 0.5f + ((float) Math.PI * 0.5f * i / segments);
            glVertex2f(x + radius + radius * (float) Math.cos(angle), 
                      y + height - radius + radius * (float) Math.sin(angle));
        }
        
        // Close the loop back to start
        float angle = (float) Math.PI * 1.0f;
        glVertex2f(x + radius + radius * (float) Math.cos(angle), 
                  y + radius + radius * (float) Math.sin(angle));
        
        glEnd();
        
        glLineWidth(1f); // Reset line width
    }
    
    /**
     * Clean up resources.
     */
    public void close() {
        if (blurEffect != null) {
            blurEffect.close();
        }
    }
}
