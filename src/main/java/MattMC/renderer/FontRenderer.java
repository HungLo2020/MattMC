package MattMC.renderer;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTAlignedQuad;
import org.lwjgl.stb.STBTTBakedChar;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.stb.STBTruetype.*;
import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * Renders text using TrueType fonts via STB TrueType library.
 * Loads the Minecraft.ttf font for all in-game text rendering.
 */
public class FontRenderer {
    private static final int BITMAP_W = 512;
    private static final int BITMAP_H = 512;
    private static final float FONT_HEIGHT = 24f;
    
    private final STBTTBakedChar.Buffer cdata;
    private final int fontTexture;
    private final FloatBuffer xBuffer;
    private final FloatBuffer yBuffer;
    
    /**
     * Create a font renderer with the Minecraft TTF font.
     */
    public FontRenderer() {
        cdata = STBTTBakedChar.malloc(96);
        
        try {
            // Load font from resources
            ByteBuffer ttf = loadFontFile("/assets/fonts/Minecraft.ttf");
            
            // Create bitmap
            ByteBuffer bitmap = BufferUtils.createByteBuffer(BITMAP_W * BITMAP_H);
            
            // Bake font bitmap
            STBTruetype.stbtt_BakeFontBitmap(ttf, FONT_HEIGHT, bitmap, BITMAP_W, BITMAP_H, 32, cdata);
            
            // Create OpenGL texture
            fontTexture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, fontTexture);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, BITMAP_W, BITMAP_H, 0, GL_ALPHA, GL_UNSIGNED_BYTE, bitmap);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to load font", e);
        }
        
        xBuffer = BufferUtils.createFloatBuffer(1);
        yBuffer = BufferUtils.createFloatBuffer(1);
    }
    
    /**
     * Load font file from resources into a ByteBuffer.
     */
    private ByteBuffer loadFontFile(String resourcePath) throws IOException {
        try (InputStream is = FontRenderer.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Font not found: " + resourcePath);
            }
            
            byte[] bytes = is.readAllBytes();
            ByteBuffer buffer = BufferUtils.createByteBuffer(bytes.length);
            buffer.put(bytes);
            buffer.flip();
            return buffer;
        }
    }
    
    /**
     * Draw text at the specified position with scale and RGB color.
     * 
     * @param text The text to draw
     * @param x X position
     * @param y Y position
     * @param scale Scale factor (1.0 = normal size)
     * @param rgb Color as 0xRRGGBB
     */
    public void drawText(String text, float x, float y, float scale, int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        
        // Enable alpha blending for proper text rendering
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, fontTexture);
        
        // Use glColor with alpha to modulate the texture color
        glColor4f(r, g, b, 1f);
        
        glPushMatrix();
        glTranslatef(x, y, 0f);
        glScalef(scale, scale, 1f);
        
        xBuffer.clear();
        yBuffer.clear();
        xBuffer.put(0, 0f);
        yBuffer.put(0, 0f);
        
        try (MemoryStack stack = stackPush()) {
            STBTTAlignedQuad quad = STBTTAlignedQuad.malloc(stack);
            
            glBegin(GL_QUADS);
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c < 32 || c >= 128) continue; // Only printable ASCII
                
                stbtt_GetBakedQuad(cdata, BITMAP_W, BITMAP_H, c - 32, xBuffer, yBuffer, quad, true);
                
                glTexCoord2f(quad.s0(), quad.t0());
                glVertex2f(quad.x0(), quad.y0());
                
                glTexCoord2f(quad.s1(), quad.t0());
                glVertex2f(quad.x1(), quad.y0());
                
                glTexCoord2f(quad.s1(), quad.t1());
                glVertex2f(quad.x1(), quad.y1());
                
                glTexCoord2f(quad.s0(), quad.t1());
                glVertex2f(quad.x0(), quad.y1());
            }
            glEnd();
        }
        
        glPopMatrix();
        
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_BLEND);
    }
    
    /**
     * Get the width of the text when rendered at scale 1.0.
     */
    public float getTextWidth(String text, float scale) {
        xBuffer.clear();
        yBuffer.clear();
        xBuffer.put(0, 0f);
        yBuffer.put(0, 0f);
        
        try (MemoryStack stack = stackPush()) {
            STBTTAlignedQuad quad = STBTTAlignedQuad.malloc(stack);
            
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c < 32 || c >= 128) continue;
                stbtt_GetBakedQuad(cdata, BITMAP_W, BITMAP_H, c - 32, xBuffer, yBuffer, quad, true);
            }
        }
        
        return xBuffer.get(0) * scale;
    }
    
    /**
     * Get the height of text at the given scale.
     */
    public float getTextHeight(float scale) {
        return FONT_HEIGHT * scale;
    }
    
    /**
     * Clean up resources.
     */
    public void cleanup() {
        cdata.free();
        glDeleteTextures(fontTexture);
    }
}
