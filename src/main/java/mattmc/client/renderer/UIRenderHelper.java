package mattmc.client.renderer;

import mattmc.client.gui.components.TextRenderer;

import static org.lwjgl.opengl.GL11.*;

/**
 * Helper class containing common UI rendering utilities.
 * Provides methods for drawing text, shapes, and color management.
 */
public class UIRenderHelper {
    
    /**
     * Helper method to draw text at the specified position.
     */
    public static void drawText(String text, float x, float y, float scale, int rgb) {
        setColor(rgb, 1f);
        TextRenderer.drawText(text, x, y, scale);
    }
    
    /**
     * Helper method to draw text right-aligned at the specified position.
     */
    public static void drawTextRightAligned(String text, float x, float y, float scale, int rgb) {
        // Get accurate text width
        float textWidth = TextRenderer.getTextWidth(text, scale);
        
        // Draw text at position adjusted for right alignment
        setColor(rgb, 1f);
        TextRenderer.drawText(text, x - textWidth, y, scale);
    }
    
    /**
     * Helper method to fill a rectangle.
     */
    public static void fillRect(int x, int y, int w, int h) {
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + w, y);
        glVertex2f(x + w, y + h);
        glVertex2f(x, y + h);
        glEnd();
    }
    
    /**
     * Helper method to set color from RGB integer.
     */
    public static void setColor(int rgb, float a) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        glColor4f(r, g, b, a);
    }
    
    /**
     * Setup 2D orthographic projection for UI rendering.
     */
    public static void setup2DProjection(int screenWidth, int screenHeight) {
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, screenWidth, screenHeight, 0, -1, 1);
        
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
    }
    
    /**
     * Restore previous projection after 2D rendering.
     */
    public static void restore2DProjection() {
        glPopMatrix();
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
    }
}
