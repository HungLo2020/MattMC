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
     * This is automatically called on first use, but can be called explicitly
     * at application startup for better error handling.
     */
    public static void init() {
        if (font == null) {
            try {
                font = TrueTypeFont.load(FONT_PATH);
            } catch (Exception e) {
                System.err.println("Failed to load font: " + FONT_PATH);
                e.printStackTrace();
                throw new RuntimeException("Font initialization failed", e);
            }
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
        
        glPushMatrix();
        // Apply scaling - font is baked at 32px, scale relative to that
        float renderScale = scale * 32.0f / font.getFontSize();
        glTranslatef(x, y, 0);
        glScalef(renderScale, renderScale, 1f);
        font.drawText(text, 0, 0);
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
        
        // Calculate text width at the desired scale
        float textWidth = getTextWidth(text, scale);
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
        
        // Font is baked at 32px, scale relative to that
        float renderScale = scale * 32.0f / font.getFontSize();
        return font.getTextWidth(text) * renderScale;
    }
    
    /**
     * Get the height of text at a given scale.
     * @param text Text to measure
     * @param scale Text scale
     * @return Height in pixels
     */
    public static float getTextHeight(String text, float scale) {
        if (font == null) init();
        
        // Font is baked at 32px, scale relative to that
        float renderScale = scale * 32.0f / font.getFontSize();
        return font.getTextHeight() * renderScale;
    }
}
