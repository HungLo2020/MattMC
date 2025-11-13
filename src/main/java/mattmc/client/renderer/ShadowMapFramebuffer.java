package mattmc.client.renderer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Manages a framebuffer for shadow mapping.
 * Creates a depth-only framebuffer to render the scene from the light's perspective.
 */
public class ShadowMapFramebuffer {
    private static final Logger logger = LoggerFactory.getLogger(ShadowMapFramebuffer.class);
    
    private final int width;
    private final int height;
    private int framebufferId;
    private int depthTextureId;
    
    /**
     * Create a shadow map framebuffer with the specified resolution.
     * 
     * @param width Shadow map width (typically 1024, 2048, or 4096)
     * @param height Shadow map height (typically same as width)
     */
    public ShadowMapFramebuffer(int width, int height) {
        this.width = width;
        this.height = height;
        initialize();
    }
    
    /**
     * Initialize the framebuffer and depth texture.
     */
    private void initialize() {
        // Generate framebuffer
        framebufferId = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, framebufferId);
        
        // Create depth texture
        depthTextureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, depthTextureId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT, width, height, 0, 
                     GL_DEPTH_COMPONENT, GL_FLOAT, (java.nio.ByteBuffer) null);
        
        // Set texture parameters
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        
        // Attach depth texture to framebuffer
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthTextureId, 0);
        
        // We don't need a color buffer for shadow mapping
        glDrawBuffer(GL_NONE);
        glReadBuffer(GL_NONE);
        
        // Check framebuffer status
        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            logger.error("Shadow map framebuffer is not complete! Status: {}", status);
        } else {
            logger.info("Shadow map framebuffer created successfully ({}x{})", width, height);
        }
        
        // Unbind
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glBindTexture(GL_TEXTURE_2D, 0);
    }
    
    /**
     * Bind this framebuffer for rendering.
     * All subsequent rendering will go to the shadow map.
     * Note: You should set the viewport separately after binding.
     */
    public void bind() {
        glBindFramebuffer(GL_FRAMEBUFFER, framebufferId);
    }
    
    /**
     * Unbind this framebuffer and return to default framebuffer.
     */
    public void unbind() {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }
    
    /**
     * Clear the shadow map (depth buffer).
     */
    public void clear() {
        glClear(GL_DEPTH_BUFFER_BIT);
    }
    
    /**
     * Bind the depth texture for reading in shaders.
     * 
     * @param textureUnit The texture unit to bind to (e.g., GL_TEXTURE1)
     */
    public void bindDepthTexture(int textureUnit) {
        glActiveTexture(textureUnit);
        glBindTexture(GL_TEXTURE_2D, depthTextureId);
    }
    
    /**
     * Get the depth texture ID.
     */
    public int getDepthTextureId() {
        return depthTextureId;
    }
    
    /**
     * Get the framebuffer width.
     */
    public int getWidth() {
        return width;
    }
    
    /**
     * Get the framebuffer height.
     */
    public int getHeight() {
        return height;
    }
    
    /**
     * Clean up OpenGL resources.
     */
    public void cleanup() {
        if (depthTextureId != 0) {
            glDeleteTextures(depthTextureId);
            depthTextureId = 0;
        }
        if (framebufferId != 0) {
            glDeleteFramebuffers(framebufferId);
            framebufferId = 0;
        }
    }
}
