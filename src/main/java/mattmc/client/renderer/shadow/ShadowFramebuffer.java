package mattmc.client.renderer.shadow;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL32.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages a framebuffer object (FBO) for shadow map rendering.
 * Each cascade uses a separate texture layer in the depth texture array.
 */
public class ShadowFramebuffer implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ShadowFramebuffer.class);
    
    private final int fboId;
    private final int depthTextureId;
    private final int resolution;
    private final int numCascades;
    
    /**
     * Create a shadow framebuffer with the specified resolution and cascade count.
     * 
     * @param resolution Shadow map resolution (e.g., 1024, 1536, 2048)
     * @param numCascades Number of cascades (typically 3-4)
     */
    public ShadowFramebuffer(int resolution, int numCascades) {
        this.resolution = resolution;
        this.numCascades = numCascades;
        
        // Create depth texture array
        depthTextureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D_ARRAY, depthTextureId);
        
        // Allocate storage for all cascades
        glTexImage3D(GL_TEXTURE_2D_ARRAY, 0, GL_DEPTH_COMPONENT24, 
                     resolution, resolution, numCascades, 
                     0, GL_DEPTH_COMPONENT, GL_FLOAT, (float[]) null);
        
        // Set texture parameters for shadow sampling
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        
        glBindTexture(GL_TEXTURE_2D_ARRAY, 0);
        
        // Create FBO
        fboId = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fboId);
        
        // Attach depth texture array to FBO
        glFramebufferTexture(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, depthTextureId, 0);
        
        // We only need depth, no color attachment
        glDrawBuffer(GL_NONE);
        glReadBuffer(GL_NONE);
        
        // Check FBO completeness
        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            logger.error("Shadow framebuffer is not complete! Status: {}", status);
            throw new RuntimeException("Shadow framebuffer creation failed");
        }
        
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        
        logger.info("Created shadow framebuffer: {}x{}, {} cascades", resolution, resolution, numCascades);
    }
    
    /**
     * Bind this framebuffer for rendering to a specific cascade.
     * 
     * @param cascadeIndex The cascade to render to (0 to numCascades-1)
     */
    public void bindForCascade(int cascadeIndex) {
        glBindFramebuffer(GL_FRAMEBUFFER, fboId);
        
        // Attach the specific layer of the texture array
        glFramebufferTextureLayer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, depthTextureId, 0, cascadeIndex);
        
        glViewport(0, 0, resolution, resolution);
        glClear(GL_DEPTH_BUFFER_BIT);
    }
    
    /**
     * Unbind the framebuffer and restore default framebuffer.
     */
    public void unbind() {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }
    
    /**
     * Bind the shadow map texture array for reading in shaders.
     * 
     * @param textureUnit The texture unit to bind to (e.g., 1)
     */
    public void bindTexture(int textureUnit) {
        glActiveTexture(GL_TEXTURE0 + textureUnit);
        glBindTexture(GL_TEXTURE_2D_ARRAY, depthTextureId);
    }
    
    public int getResolution() {
        return resolution;
    }
    
    public int getNumCascades() {
        return numCascades;
    }
    
    public int getDepthTextureId() {
        return depthTextureId;
    }
    
    @Override
    public void close() {
        glDeleteFramebuffers(fboId);
        glDeleteTextures(depthTextureId);
    }
}
