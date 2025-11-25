package mattmc.client.renderer.backend.opengl.gui.components;

import mattmc.client.MattMC;

import static org.lwjgl.opengl.GL11.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenGL-specific text rendering implementation using TrueType fonts.
 * 
 * <p><b>INTERNAL USE ONLY:</b> This class is part of the OpenGL backend implementation
 * and should NOT be used directly by code outside the backend/opengl package.
 * Use {@link mattmc.client.renderer.gui.components.TextRenderer} instead for
 * API-agnostic text rendering, or use the {@link mattmc.client.renderer.backend.RenderBackend}
 * interface methods directly.
 * 
 * <p>This class provides the actual OpenGL implementation of text rendering
 * using TrueType fonts baked into a texture atlas.
 * 
 * @since OpenGL Backend - Internal Implementation
 * @see mattmc.client.renderer.gui.components.TextRenderer API-agnostic wrapper
 */
public final class OpenGLTextRenderer {
    private static final Logger logger = LoggerFactory.getLogger(OpenGLTextRenderer.class);

    
    private static TrueTypeFont font;
    private static final String FONT_PATH = "/assets/fonts/MattMC.ttf";
    
    private OpenGLTextRenderer() {} // Prevent instantiation
    
    /**
     * Initialize the text renderer with the MattMC font.
     * This is automatically called on first use, but can be called explicitly
     * at application startup for better error handling.
     */
    public static void init() {
        if (font == null) {
            try {
                font = TrueTypeFont.load(FONT_PATH);
            } catch (RuntimeException e) {
                logger.error("Failed to load font: {}", FONT_PATH, e);
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
        // Apply scaling - font is baked at 16px, scale directly
        glTranslatef(x, y, 0);
        glScalef(scale, scale, 1f);
        // Offset y by ascent to make y coordinate represent top of text (like STBEasyFont)
        // instead of baseline
        font.drawText(text, 0, font.getAscent());
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
        
        // Font is baked at 16px, scale directly
        return font.getTextWidth(text) * scale;
    }
    
    /**
     * Get the height of text at a given scale.
     * @param text Text to measure
     * @param scale Text scale
     * @return Height in pixels
     */
    public static float getTextHeight(String text, float scale) {
        if (font == null) init();
        
        // Font is baked at 16px, scale directly
        return font.getTextHeight() * scale;
    }
}
