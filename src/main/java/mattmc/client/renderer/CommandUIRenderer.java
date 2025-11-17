package mattmc.client.renderer;

import static org.lwjgl.opengl.GL11.*;

/**
 * Renders command-related UI elements including the command overlay and feedback messages.
 */
public class CommandUIRenderer {
    
    // Constants for command feedback positioning and sizing
    private static final int FEEDBACK_Y_OFFSET = 120; // Distance from bottom of screen
    private static final int CHAR_WIDTH_ESTIMATE = 8; // Approximate character width in pixels
    
    /**
     * Draw command overlay at bottom of screen (like Minecraft).
     * Shows command input box with cursor.
     */
    public void renderCommandOverlay(int screenWidth, int screenHeight, String commandText) {
        UIRenderHelper.setup2DProjection(screenWidth, screenHeight);
        
        // Enable blending for semi-transparent overlay
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        // Draw command input box at bottom of screen
        int boxHeight = 50;
        int boxY = screenHeight - boxHeight - 20;
        int boxX = 20;
        int boxWidth = screenWidth - 40;
        
        // Draw semi-transparent background
        UIRenderHelper.setColor(0x000000, 0.7f);
        UIRenderHelper.fillRect(boxX, boxY, boxWidth, boxHeight);
        
        // Draw border
        UIRenderHelper.setColor(0xFFFFFF, 1.0f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(boxX, boxY);
        glVertex2f(boxX + boxWidth, boxY);
        glVertex2f(boxX + boxWidth, boxY + boxHeight);
        glVertex2f(boxX, boxY + boxHeight);
        glEnd();
        
        // Draw command text with blinking cursor
        String displayText = commandText + "_";
        UIRenderHelper.drawText(displayText, boxX + 10, boxY + 15, 1.5f, 0xFFFFFF);
        
        glDisable(GL_BLEND);
        
        UIRenderHelper.restore2DProjection();
    }
    
    /**
     * Draw command feedback message above the hotbar area.
     * This message appears independently of the command input overlay and fades after a few seconds.
     * Similar to Minecraft's action bar messages.
     * 
     * @param screenWidth Screen width
     * @param screenHeight Screen height
     * @param message The feedback message to display
     */
    public void renderCommandFeedback(int screenWidth, int screenHeight, String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        
        UIRenderHelper.setup2DProjection(screenWidth, screenHeight);
        
        // Enable blending for semi-transparent text background
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        // Position message above the hotbar/command area (centered horizontally)
        int messageY = screenHeight - FEEDBACK_Y_OFFSET;
        
        // Draw semi-transparent background behind text
        int padding = 8;
        float textScale = 1.2f;
        // Estimate text width (rough approximation using character width estimate)
        int textWidth = (int)(message.length() * CHAR_WIDTH_ESTIMATE * textScale);
        int bgX = (screenWidth - textWidth) / 2 - padding;
        int bgY = messageY - padding;
        int bgWidth = textWidth + padding * 2;
        int bgHeight = (int)(16 * textScale) + padding * 2;
        
        // Draw background
        UIRenderHelper.setColor(0x000000, 0.6f);
        UIRenderHelper.fillRect(bgX, bgY, bgWidth, bgHeight);
        
        // Draw text centered
        int textX = (screenWidth - textWidth) / 2;
        UIRenderHelper.drawText(message, textX, messageY, textScale, 0xFFFFFF);
        
        glDisable(GL_BLEND);
        
        UIRenderHelper.restore2DProjection();
    }
}
