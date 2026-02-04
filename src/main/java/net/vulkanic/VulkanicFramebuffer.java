package net.vulkanic;

/**
 * Represents a framebuffer for rendering to textures.
 * 
 * Framebuffers can have color and depth attachments that can be rendered to
 * and then used as textures in subsequent rendering passes.
 */
public interface VulkanicFramebuffer {
    /**
     * Gets the color texture attached to this framebuffer.
     * 
     * @return the color texture, or null if none
     */
    VulkanicTexture getColorTexture();
    
    /**
     * Gets the depth texture attached to this framebuffer.
     * 
     * @return the depth texture, or null if none
     */
    VulkanicTexture getDepthTexture();
    
    /**
     * Gets the width of the framebuffer.
     * 
     * @return the framebuffer width
     */
    int getWidth();
    
    /**
     * Gets the height of the framebuffer.
     * 
     * @return the framebuffer height
     */
    int getHeight();
    
    /**
     * Releases resources associated with this framebuffer.
     */
    void close();
}
