package MattMC.ui;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * TrueType font renderer using STBTrueType.
 * Loads a TTF font and renders text as OpenGL textures.
 */
public final class TrueTypeFont implements AutoCloseable {
    
    private final STBTTFontinfo fontInfo;
    private final ByteBuffer fontData;
    private final int textureId;
    private final int bitmapWidth = 512;
    private final int bitmapHeight = 512;
    private float scale;
    private int ascent;
    private int descent;
    private int lineGap;
    
    private TrueTypeFont(ByteBuffer fontData, STBTTFontinfo fontInfo, int textureId) {
        this.fontData = fontData;
        this.fontInfo = fontInfo;
        this.textureId = textureId;
        updateScale(16.0f); // Default font size
    }
    
    /**
     * Load a TrueType font from classpath.
     * @param path Classpath resource path to the TTF file
     * @return TrueTypeFont instance
     */
    public static TrueTypeFont load(String path) {
        ByteBuffer fontData = readResourceToBuffer(path);
        if (fontData == null) {
            throw new RuntimeException("Missing font resource on classpath: " + path);
        }
        
        STBTTFontinfo fontInfo = STBTTFontinfo.create();
        if (!STBTruetype.stbtt_InitFont(fontInfo, fontData)) {
            throw new RuntimeException("Failed to initialize font: " + path);
        }
        
        // Create a texture for font bitmap cache
        int texId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texId);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glBindTexture(GL_TEXTURE_2D, 0);
        
        System.out.println("Loaded TrueType font: " + path);
        return new TrueTypeFont(fontData, fontInfo, texId);
    }
    
    private static ByteBuffer readResourceToBuffer(String path) {
        try (InputStream in = TrueTypeFont.class.getResourceAsStream(path)) {
            if (in == null) return null;
            byte[] tmp = in.readAllBytes();
            ByteBuffer buf = BufferUtils.createByteBuffer(tmp.length);
            buf.put(tmp).flip();
            return buf;
        } catch (Exception e) {
            throw new RuntimeException("Failed to read font resource: " + path, e);
        }
    }
    
    /**
     * Update the font scale for a specific pixel height.
     * @param pixelHeight Desired font height in pixels
     */
    public void updateScale(float pixelHeight) {
        this.scale = STBTruetype.stbtt_ScaleForPixelHeight(fontInfo, pixelHeight);
        
        try (MemoryStack stack = stackPush()) {
            IntBuffer pAscent = stack.mallocInt(1);
            IntBuffer pDescent = stack.mallocInt(1);
            IntBuffer pLineGap = stack.mallocInt(1);
            
            STBTruetype.stbtt_GetFontVMetrics(fontInfo, pAscent, pDescent, pLineGap);
            this.ascent = pAscent.get(0);
            this.descent = pDescent.get(0);
            this.lineGap = pLineGap.get(0);
        }
    }
    
    /**
     * Get the width of text at the current scale.
     * @param text Text to measure
     * @return Width in pixels
     */
    public float getTextWidth(String text) {
        float width = 0;
        
        try (MemoryStack stack = stackPush()) {
            IntBuffer pAdvancedWidth = stack.mallocInt(1);
            IntBuffer pLeftSideBearing = stack.mallocInt(1);
            
            int prevCodepoint = 0;
            for (int i = 0; i < text.length(); i++) {
                int codepoint = text.charAt(i);
                
                STBTruetype.stbtt_GetCodepointHMetrics(fontInfo, codepoint, pAdvancedWidth, pLeftSideBearing);
                width += pAdvancedWidth.get(0) * scale;
                
                if (prevCodepoint != 0) {
                    width += STBTruetype.stbtt_GetCodepointKernAdvance(fontInfo, prevCodepoint, codepoint) * scale;
                }
                
                prevCodepoint = codepoint;
            }
        }
        
        return width;
    }
    
    /**
     * Get the height of text at the current scale.
     * @return Height in pixels
     */
    public float getTextHeight() {
        return (ascent - descent) * scale;
    }
    
    /**
     * Render text at the specified position.
     * @param text Text to render
     * @param x X position (left)
     * @param y Y position (baseline)
     */
    public void drawText(String text, float x, float y) {
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        float xpos = x;
        
        try (MemoryStack stack = stackPush()) {
            IntBuffer pAdvancedWidth = stack.mallocInt(1);
            IntBuffer pLeftSideBearing = stack.mallocInt(1);
            IntBuffer x0 = stack.mallocInt(1);
            IntBuffer y0 = stack.mallocInt(1);
            IntBuffer x1 = stack.mallocInt(1);
            IntBuffer y1 = stack.mallocInt(1);
            
            int prevCodepoint = 0;
            
            for (int i = 0; i < text.length(); i++) {
                int codepoint = text.charAt(i);
                
                STBTruetype.stbtt_GetCodepointHMetrics(fontInfo, codepoint, pAdvancedWidth, pLeftSideBearing);
                STBTruetype.stbtt_GetCodepointBitmapBox(fontInfo, codepoint, scale, scale, x0, y0, x1, y1);
                
                int width = x1.get(0) - x0.get(0);
                int height = y1.get(0) - y0.get(0);
                
                if (width > 0 && height > 0) {
                    // Allocate bitmap for this character
                    ByteBuffer bitmap = BufferUtils.createByteBuffer(width * height);
                    STBTruetype.stbtt_MakeCodepointBitmap(fontInfo, bitmap, width, height, width, scale, scale, codepoint);
                    
                    // Upload to texture
                    glBindTexture(GL_TEXTURE_2D, textureId);
                    glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, width, height, 0, GL_ALPHA, GL_UNSIGNED_BYTE, bitmap);
                    
                    // Draw quad with texture
                    float x2 = xpos + x0.get(0);
                    float y2 = y + y0.get(0);
                    
                    glBegin(GL_QUADS);
                    glTexCoord2f(0, 0); glVertex2f(x2, y2);
                    glTexCoord2f(1, 0); glVertex2f(x2 + width, y2);
                    glTexCoord2f(1, 1); glVertex2f(x2 + width, y2 + height);
                    glTexCoord2f(0, 1); glVertex2f(x2, y2 + height);
                    glEnd();
                }
                
                xpos += pAdvancedWidth.get(0) * scale;
                
                if (prevCodepoint != 0) {
                    xpos += STBTruetype.stbtt_GetCodepointKernAdvance(fontInfo, prevCodepoint, codepoint) * scale;
                }
                
                prevCodepoint = codepoint;
            }
        }
        
        glDisable(GL_BLEND);
        glDisable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, 0);
    }
    
    public int getTextureId() {
        return textureId;
    }
    
    @Override
    public void close() {
        if (textureId != 0) {
            glDeleteTextures(textureId);
        }
    }
}
