package net.vulkanic.backends.opengl;

import net.vulkanic.VulkanicFramebuffer;
import net.vulkanic.VulkanicTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * OpenGL implementation of VulkanicFramebuffer.
 * 
 * Creates a framebuffer with color and depth attachments using direct OpenGL calls.
 */
public class OpenGLFramebuffer implements VulkanicFramebuffer {
    private final int fboId;
    private final int width;
    private final int height;
    private final OpenGLTexture colorTexture;
    private final int depthRenderbufferId;
    
    /**
     * Creates a new framebuffer with the specified dimensions.
     * Creates both a color attachment (RGBA8 texture) and a depth attachment (renderbuffer).
     * 
     * @param width the framebuffer width
     * @param height the framebuffer height
     */
    public OpenGLFramebuffer(int width, int height) {
        this.width = width;
        this.height = height;
        
        // Create framebuffer object
        this.fboId = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboId);
        
        // Create color attachment texture
        this.colorTexture = new OpenGLTexture(width, height);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, 
            GL11.GL_TEXTURE_2D, colorTexture.getTextureId(), 0);
        
        // Create depth attachment renderbuffer
        this.depthRenderbufferId = GL30.glGenRenderbuffers();
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthRenderbufferId);
        GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH_COMPONENT, width, height);
        GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, 
            GL30.GL_RENDERBUFFER, depthRenderbufferId);
        
        // Check framebuffer completeness
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Framebuffer is not complete: " + status);
        }
        
        // Unbind
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0);
    }
    
    @Override
    public VulkanicTexture getColorTexture() {
        return colorTexture;
    }
    
    @Override
    public VulkanicTexture getDepthTexture() {
        // We use a renderbuffer for depth, not a texture
        // Return null for now (could create a depth texture if needed)
        return null;
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
     * Gets the OpenGL framebuffer ID.
     * Package-private for use by other OpenGL backend classes.
     * 
     * @return the framebuffer ID
     */
    int getFboId() {
        return fboId;
    }
    
    @Override
    public void close() {
        if (colorTexture != null) {
            colorTexture.close();
        }
        if (depthRenderbufferId != 0) {
            GL30.glDeleteRenderbuffers(depthRenderbufferId);
        }
        if (fboId != 0) {
            GL30.glDeleteFramebuffers(fboId);
        }
    }
}
