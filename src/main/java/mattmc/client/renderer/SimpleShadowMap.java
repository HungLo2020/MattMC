package mattmc.client.renderer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Simple shadow map framebuffer for basic shadow rendering.
 * Creates a depth-only framebuffer to render the scene from the sun's perspective.
 */
public class SimpleShadowMap {
    private static final Logger logger = LoggerFactory.getLogger(SimpleShadowMap.class);
    
    private final int width;
    private final int height;
    private int framebufferId;
    private int depthTextureId;
    
    public SimpleShadowMap(int width, int height) {
        this.width = width;
        this.height = height;
        initialize();
    }
    
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
        
        // Note: Shadow comparison mode not used - we do manual depth comparison in shader
        // This gives us more control over the shadow calculation
        
        // Attach depth texture to framebuffer
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthTextureId, 0);
        
        // We don't need a color buffer
        glDrawBuffer(GL_NONE);
        glReadBuffer(GL_NONE);
        
        // Check framebuffer status
        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            logger.error("Shadow map framebuffer is not complete! Status: {}", status);
        } else {
            logger.info("Shadow map framebuffer created ({}x{})", width, height);
        }
        
        // Unbind
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glBindTexture(GL_TEXTURE_2D, 0);
    }
    
    public void bind() {
        glBindFramebuffer(GL_FRAMEBUFFER, framebufferId);
    }
    
    public void unbind() {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }
    
    public void clear() {
        glClear(GL_DEPTH_BUFFER_BIT);
    }
    
    public void bindDepthTexture(int textureUnit) {
        glActiveTexture(textureUnit);
        glBindTexture(GL_TEXTURE_2D, depthTextureId);
    }
    
    public int getDepthTextureId() {
        return depthTextureId;
    }
    
    public int getWidth() {
        return width;
    }
    
    public int getHeight() {
        return height;
    }
    
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
