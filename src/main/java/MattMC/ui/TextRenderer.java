package MattMC.ui;

import static org.lwjgl.opengl.GL11.*;

/**
 * Utility class for rendering text using TrueType fonts.
 * Provides centered, scaled text rendering capabilities with the Minecraft font.
 */
public final class TextRenderer {
    
    private static TrueTypeFont font;
    private static final String FONT_PATH = "/assets/fonts/Minecraft.ttf";
    
    private TextRenderer() {} // Prevent instantiation
    
    /**
     * Initialize the text renderer with the Minecraft font.
     * Should be called once at application startup.
     */
    public static void init() {
        if (font == null) {
            font = TrueTypeFont.load(FONT_PATH);
        }
    }
    
    /**
     * Cleanup resources.
     * Should be called at application shutdown.
     */
    public static void cleanup() {
        if (font != null) {
            font.close();
            font = null;
        }
    }
    
    /**
     * Render text at a specific position with scale.
     * @param text Text to render
     * @param x X position (left)
     * @param y Y position (top)
     * @param scale Text scale
     */
    public static void drawText(String text, float x, float y, float scale) {
        if (font == null) init();
        
        float fontSize = 16.0f * scale;
        font.updateScale(fontSize);
        
        glPushMatrix();
        // Adjust Y position for baseline rendering
        float baselineY = y + font.getTextHeight() * 0.8f;
        font.drawText(text, x, baselineY);
        glPopMatrix();
    }
    
    /**
     * Render text centered horizontally at a position.
     * @param text Text to render
     * @param centerX Center X position
     * @param y Y position
     * @param scale Text scale
     */
    public static void drawCenteredText(String text, float centerX, float y, float scale) {
        if (font == null) init();
        
        float fontSize = 16.0f * scale;
        font.updateScale(fontSize);
        
        // Calculate text width
        float textWidth = font.getTextWidth(text);
        float x = centerX - textWidth / 2;
        
        drawText(text, x, y, scale);
    }
    
    /**
     * Get the width of text at a given scale.
     * @param text Text to measure
     * @param scale Text scale
     * @return Width in pixels
     */
    public static float getTextWidth(String text, float scale) {
        if (font == null) init();
        
        float fontSize = 16.0f * scale;
        font.updateScale(fontSize);
        
        return font.getTextWidth(text);
    }
    
    /**
     * Get the height of text at a given scale.
     * @param text Text to measure
     * @param scale Text scale
     * @return Height in pixels
     */
    public static float getTextHeight(String text, float scale) {
        if (font == null) init();
        
        float fontSize = 16.0f * scale;
        font.updateScale(fontSize);
        
        return font.getTextHeight();
    }
}
