package MattMC.renderer;

import static org.lwjgl.opengl.GL11.*;

/**
 * Handles rendering of UI elements.
 * Similar to Minecraft's GuiIngame class.
 */
public class UIRenderer {
    private final FontRenderer fontRenderer;
    
    public UIRenderer() {
        this.fontRenderer = new FontRenderer();
    }
    
    /**
     * Draw crosshair in the center of the screen.
     */
    public void drawCrosshair(int screenWidth, int screenHeight) {
        // Switch to 2D orthographic projection
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, screenWidth, screenHeight, 0, -1, 1);
        
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        
        // Draw white crosshair
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
        
        // Restore matrices
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }
    
    /**
     * Draw debug info overlay (F3 menu).
     * Shows game name, FPS, and player coordinates.
     */
    public void drawDebugInfo(int screenWidth, int screenHeight, int fps, float playerX, float playerY, float playerZ) {
        // Switch to 2D orthographic projection
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, screenWidth, screenHeight, 0, -1, 1);
        
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        
        // Enable blending for text
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        float x = 5f;
        float y = 10f; // Moved down slightly from 5f to 10f
        float scale = 1.0f;
        float lineHeight = fontRenderer.getTextHeight(scale) + 2f;
        
        // Draw game name
        fontRenderer.drawText("MattMC", x, y, scale, 0xFFFFFF);
        y += lineHeight;
        
        // Draw FPS
        fontRenderer.drawText("FPS: " + fps, x, y, scale, 0xFFFFFF);
        y += lineHeight;
        
        // Draw player coordinates
        String coords = String.format("X: %.2f Y: %.2f Z: %.2f", playerX, playerY, playerZ);
        fontRenderer.drawText(coords, x, y, scale, 0xFFFFFF);
        
        glDisable(GL_BLEND);
        
        // Restore matrices
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }
    
    /**
     * Draw command overlay (chat input).
     * Shows command input box at bottom of screen.
     */
    public void drawCommandOverlay(int screenWidth, int screenHeight, String commandText, String resultMessage) {
        // Switch to 2D orthographic projection
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, screenWidth, screenHeight, 0, -1, 1);
        
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        
        // Enable blending
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        float scale = 1.0f;
        float padding = 10f;
        float boxHeight = fontRenderer.getTextHeight(scale) + padding * 2;
        float boxY = screenHeight - boxHeight - padding;
        
        // Draw semi-transparent background box
        glColor4f(0f, 0f, 0f, 0.5f);
        glBegin(GL_QUADS);
        glVertex2f(padding, boxY);
        glVertex2f(screenWidth - padding, boxY);
        glVertex2f(screenWidth - padding, boxY + boxHeight);
        glVertex2f(padding, boxY + boxHeight);
        glEnd();
        
        // Draw command text
        String displayText = "/" + commandText;
        fontRenderer.drawText(displayText, padding * 2, boxY + padding, scale, 0xFFFFFF);
        
        // Draw result message above if present
        if (resultMessage != null && !resultMessage.isEmpty()) {
            float msgY = boxY - padding - fontRenderer.getTextHeight(scale);
            fontRenderer.drawText(resultMessage, padding * 2, msgY, scale, 0xFFFF00);
        }
        
        glDisable(GL_BLEND);
        
        // Restore matrices
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }
    
    /**
     * Clean up resources.
     */
    public void cleanup() {
        fontRenderer.cleanup();
    }
}
