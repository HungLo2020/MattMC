package net.vulkanic.backends.opengl;

import net.blaze3d.textures.TextureFormat;
import net.vulkanic.VulkanicFramebuffer;
import net.vulkanic.VulkanicTexture;

/**
 * OpenGL implementation of VulkanicFramebuffer.
 * 
 * Creates a framebuffer with color and depth attachments using Blaze3D.
 */
public class OpenGLFramebuffer implements VulkanicFramebuffer {
    private final int width;
    private final int height;
    private final OpenGLTexture colorTexture;
    private final OpenGLTexture depthTexture;
    
    /**
     * Creates a new framebuffer with the specified dimensions.
     * Creates both a color attachment (RGBA8) and a depth attachment (DEPTH32).
     * 
     * @param width the framebuffer width
     * @param height the framebuffer height
     */
    public OpenGLFramebuffer(int width, int height) {
        this.width = width;
        this.height = height;
        
        // Create color attachment texture (RGBA8) with RENDER_ATTACHMENT usage
        this.colorTexture = new OpenGLTexture(width, height, TextureFormat.RGBA8);
        
        // Create depth attachment texture (DEPTH32) with RENDER_ATTACHMENT usage
        this.depthTexture = new OpenGLTexture(width, height, TextureFormat.DEPTH32);
    }
    
    @Override
    public VulkanicTexture getColorTexture() {
        return colorTexture;
    }
    
    @Override
    public VulkanicTexture getDepthTexture() {
        return depthTexture;
    }
    
    @Override
    public int getWidth() {
        return width;
    }
    
    @Override
    public int getHeight() {
        return height;
    }
    
    @Override
    public void close() {
        if (colorTexture != null) {
            colorTexture.close();
        }
        if (depthTexture != null) {
            depthTexture.close();
        }
    }
}
