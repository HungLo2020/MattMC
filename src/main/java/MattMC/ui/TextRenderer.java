package MattMC.ui;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBEasyFont;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * Utility class for rendering text using STBEasyFont.
 * Provides centered, scaled text rendering capabilities.
 */
public final class TextRenderer {
    
    private static final ByteBuffer fontBuffer = BufferUtils.createByteBuffer(16 * 4096);
    
    private TextRenderer() {} // Prevent instantiation
    
    /**
     * Render text at a specific position with scale.
     * @param text Text to render
     * @param x X position (bottom-left)
     * @param y Y position (bottom-left)
     * @param scale Text scale
     */
    public static void drawText(String text, float x, float y, float scale) {
        glPushMatrix();
        glTranslatef(x, y, 0);
        glScalef(scale, scale, 1f);
        
        fontBuffer.clear();
        int quads = STBEasyFont.stb_easy_font_print(0, 0, text, null, fontBuffer);
        
        glEnableClientState(GL_VERTEX_ARRAY);
        glVertexPointer(2, GL_FLOAT, 16, fontBuffer);
        glDrawArrays(GL_QUADS, 0, quads * 4);
        glDisableClientState(GL_VERTEX_ARRAY);
        
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
        try (MemoryStack stack = stackPush()) {
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            STBEasyFont.stb_easy_font_width(text);
            
            // Calculate text width
            float textWidth = STBEasyFont.stb_easy_font_width(text) * scale;
            float x = centerX - textWidth / 2;
            
            drawText(text, x, y, scale);
        }
    }
    
    /**
     * Get the width of text at a given scale.
     * @param text Text to measure
     * @param scale Text scale
     * @return Width in pixels
     */
    public static float getTextWidth(String text, float scale) {
        return STBEasyFont.stb_easy_font_width(text) * scale;
    }
    
    /**
     * Get the height of text at a given scale.
     * @param text Text to measure
     * @param scale Text scale
     * @return Height in pixels
     */
    public static float getTextHeight(String text, float scale) {
        return STBEasyFont.stb_easy_font_height(text) * scale;
    }
}
