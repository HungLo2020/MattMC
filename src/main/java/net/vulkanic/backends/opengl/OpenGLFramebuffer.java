package net.vulkanic.backends.opengl;

import net.vulkanic.VulkanicFramebuffer;
import net.vulkanic.VulkanicTexture;

/**
 * OpenGL implementation of VulkanicFramebuffer.
 * 
 * Placeholder implementation - will be filled in during Phase 2.
 */
public class OpenGLFramebuffer implements VulkanicFramebuffer {
    private final int width;
    private final int height;
    private final VulkanicTexture colorTexture;
    private final VulkanicTexture depthTexture;
    
    public OpenGLFramebuffer(int width, int height) {
        this.width = width;
        this.height = height;
        // TODO: Create framebuffer and attachments using Blaze3D
        this.colorTexture = null; // Placeholder
        this.depthTexture = null; // Placeholder
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
        // TODO: Implement framebuffer cleanup
    }
}
