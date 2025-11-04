package MattMC.ui;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTAlignedQuad;
import org.lwjgl.stb.STBTTBakedChar;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * TrueType font renderer using STBTrueType with bitmap baking.
 * Loads a TTF font and renders text using a pre-baked texture atlas.
 */
public final class TrueTypeFont implements AutoCloseable {
    
    private final STBTTFontinfo fontInfo;
    private final ByteBuffer fontData;
    private final int textureId;
    private final STBTTBakedChar.Buffer charData;
    private final int bitmapWidth = 512;
    private final int bitmapHeight = 512;
    private float fontSize;
    private int ascent;
    private int descent;
    private int lineGap;
    
    private TrueTypeFont(ByteBuffer fontData, STBTTFontinfo fontInfo, int textureId, STBTTBakedChar.Buffer charData, float fontSize) {
        this.fontData = fontData;
        this.fontInfo = fontInfo;
        this.textureId = textureId;
        this.charData = charData;
        this.fontSize = fontSize;
        updateMetrics();
    }
    
    /**
     * Load a TrueType font from classpath.
     * @param path Classpath resource path to the TTF file
     * @return TrueTypeFont instance
     */
    public static TrueTypeFont load(String path) {
        return load(path, 16.0f);
    }
    
    /**
     * Load a TrueType font from classpath with specified font size.
     * @param path Classpath resource path to the TTF file
     * @param fontSize Font size in pixels
     * @return TrueTypeFont instance
     */
    public static TrueTypeFont load(String path, float fontSize) {
        ByteBuffer fontData = readResourceToBuffer(path);
        if (fontData == null) {
            throw new RuntimeException("Missing font resource on classpath: " + path);
        }
        
        STBTTFontinfo fontInfo = STBTTFontinfo.create();
        if (!STBTruetype.stbtt_InitFont(fontInfo, fontData)) {
            throw new RuntimeException("Failed to initialize font: " + path);
        }
        
        // Bake font bitmap for ASCII characters (32-126)
        int bitmapWidth = 512;
        int bitmapHeight = 512;
        ByteBuffer bitmap = BufferUtils.createByteBuffer(bitmapWidth * bitmapHeight);
        
        STBTTBakedChar.Buffer charData = STBTTBakedChar.malloc(96); // 96 ASCII characters
        STBTruetype.stbtt_BakeFontBitmap(fontData, fontSize, bitmap, bitmapWidth, bitmapHeight, 32, charData);
        
        // Create texture from bitmap
        int texId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, bitmapWidth, bitmapHeight, 0, GL_ALPHA, GL_UNSIGNED_BYTE, bitmap);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glBindTexture(GL_TEXTURE_2D, 0);
        
        System.out.println("Loaded TrueType font: " + path + " at " + fontSize + "px");
        return new TrueTypeFont(fontData, fontInfo, texId, charData, fontSize);
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
     * Update font metrics.
     */
    private void updateMetrics() {
        float scale = STBTruetype.stbtt_ScaleForPixelHeight(fontInfo, fontSize);
        
        try (MemoryStack stack = stackPush()) {
            IntBuffer pAscent = stack.mallocInt(1);
            IntBuffer pDescent = stack.mallocInt(1);
            IntBuffer pLineGap = stack.mallocInt(1);
            
            STBTruetype.stbtt_GetFontVMetrics(fontInfo, pAscent, pDescent, pLineGap);
            this.ascent = (int)(pAscent.get(0) * scale);
            this.descent = (int)(pDescent.get(0) * scale);
            this.lineGap = (int)(pLineGap.get(0) * scale);
        }
    }
    
    /**
     * Update the font scale for rendering at a different size.
     * Note: This doesn't rebake the font, just changes the reference size for scaling calculations.
     * The actual scaling is done via glScale in TextRenderer.
     * @param pixelHeight Desired font height in pixels
     */
    public void updateScale(float pixelHeight) {
        // This method is kept for API compatibility but doesn't need to do anything
        // since scaling is handled by TextRenderer using glScale
    }
    
    /**
     * Get the width of text.
     * @param text Text to measure
     * @return Width in pixels
     */
    public float getTextWidth(String text) {
        float width = 0;
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < 32 || c >= 128) continue; // Skip non-ASCII
            
            int charIndex = c - 32;
            if (charIndex >= 0 && charIndex < charData.capacity()) {
                STBTTBakedChar charInfo = charData.get(charIndex);
                width += charInfo.xadvance();
            }
        }
        
        return width;
    }
    
    /**
     * Get the height of text.
     * @return Height in pixels
     */
    public float getTextHeight() {
        return ascent - descent;
    }
    
    /**
     * Render text at the specified position.
     * @param text Text to render
     * @param x X position (left)
     * @param y Y position (top)
     */
    public void drawText(String text, float x, float y) {
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        glBindTexture(GL_TEXTURE_2D, textureId);
        
        try (MemoryStack stack = stackPush()) {
            FloatBuffer xPos = stack.floats(x);
            FloatBuffer yPos = stack.floats(y);
            
            STBTTAlignedQuad quad = STBTTAlignedQuad.malloc(stack);
            
            glBegin(GL_QUADS);
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c < 32 || c >= 128) continue; // Skip non-ASCII
                
                int charIndex = c - 32;
                STBTruetype.stbtt_GetBakedQuad(charData, bitmapWidth, bitmapHeight, charIndex, xPos, yPos, quad, true);
                
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
        
        glDisable(GL_BLEND);
        glDisable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, 0);
    }
    
    public int getTextureId() {
        return textureId;
    }
    
    public float getFontSize() {
        return fontSize;
    }
    
    @Override
    public void close() {
        if (textureId != 0) {
            glDeleteTextures(textureId);
        }
        if (charData != null) {
            charData.free();
        }
    }
}
