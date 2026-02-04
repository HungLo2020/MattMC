package net.vulkanic.backends.opengl;

import net.vulkanic.VulkanicTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import java.nio.ByteBuffer;

/**
 * OpenGL implementation of VulkanicTexture.
 * 
 * Uses direct OpenGL texture objects for GPU texture management.
 */
public class OpenGLTexture implements VulkanicTexture {
    private final int textureId;
    private final int width;
    private final int height;
    
    /**
     * Creates a new OpenGL texture with the specified dimensions.
     * Uses RGBA8 format by default.
     * 
     * @param width the texture width
     * @param height the texture height
     */
    public OpenGLTexture(int width, int height) {
        this.width = width;
        this.height = height;
        this.textureId = GL11.glGenTextures();
        
        // Set up texture
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer)null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }
    
    @Override
    public void upload(ByteBuffer data, int width, int height) {
        if (width != this.width || height != this.height) {
            throw new IllegalArgumentException("Upload dimensions (" + width + "x" + height + 
                ") don't match texture dimensions (" + this.width + "x" + this.height + ")");
        }
        
        // Calculate expected data size for RGBA8 format
        int expectedSize = width * height * 4; // 4 bytes per pixel (RGBA)
        if (data.remaining() < expectedSize) {
            throw new IllegalArgumentException("Data buffer too small. Expected " + expectedSize + " bytes, got " + data.remaining());
        }
        
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, data);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }
    
    @Override
    public int getWidth() {
        return width;
    }
    
    @Override
    public int getHeight() {
        return height;
    }
    
    /**
     * Gets the OpenGL texture ID.
     * Package-private for use by other OpenGL backend classes.
     * 
     * @return the texture ID
     */
    int getTextureId() {
        return textureId;
    }
    
    @Override
    public void close() {
        if (textureId != 0) {
            GL11.glDeleteTextures(textureId);
        }
    }
}
