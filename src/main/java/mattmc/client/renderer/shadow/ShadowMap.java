package mattmc.client.renderer.shadow;

import org.lwjgl.BufferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Represents a single shadow map (depth texture + framebuffer).
 * Used for cascaded shadow mapping - one instance per cascade.
 */
public class ShadowMap {
    private static final Logger logger = LoggerFactory.getLogger(ShadowMap.class);
    
    private final int resolution;
    private int framebufferId;
    private int depthTextureId;
    
    // Shadow matrix: transforms world coords to shadow map texture coords
    private final FloatBuffer shadowMatrix = BufferUtils.createFloatBuffer(16);
    
    public ShadowMap(int resolution) {
        this.resolution = resolution;
        initialize();
    }
    
    private void initialize() {
        // Create depth texture
        depthTextureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, depthTextureId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT, resolution, resolution, 
                     0, GL_DEPTH_COMPONENT, GL_FLOAT, (FloatBuffer) null);
        
        // Shadow map texture parameters
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        
        // Enable depth comparison for hardware PCF
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL_COMPARE_REF_TO_TEXTURE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_FUNC, GL_LEQUAL);
        
        // Create framebuffer
        framebufferId = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, framebufferId);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthTextureId, 0);
        
        // We only need depth, no color attachment
        glDrawBuffer(GL_NONE);
        glReadBuffer(GL_NONE);
        
        // Check framebuffer status
        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            logger.error("Shadow map framebuffer incomplete! Status: {}", status);
        }
        
        // Unbind
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glBindTexture(GL_TEXTURE_2D, 0);
        
        logger.info("Shadow map created: {}x{} resolution", resolution, resolution);
    }
    
    /**
     * Bind this shadow map for rendering.
     */
    public void bind() {
        glBindFramebuffer(GL_FRAMEBUFFER, framebufferId);
        glViewport(0, 0, resolution, resolution);
        glClear(GL_DEPTH_BUFFER_BIT);
    }
    
    /**
     * Unbind shadow map framebuffer.
     */
    public void unbind() {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }
    
    /**
     * Bind the depth texture for reading in shaders.
     * @param textureUnit OpenGL texture unit (e.g., GL_TEXTURE1)
     */
    public void bindTexture(int textureUnit) {
        glActiveTexture(textureUnit);
        glBindTexture(GL_TEXTURE_2D, depthTextureId);
    }
    
    /**
     * Update the shadow matrix for this cascade.
     * Shadow matrix = bias * projection * view
     */
    public void updateShadowMatrix(float[] viewMatrix, float[] projMatrix) {
        // Bias matrix to convert from [-1,1] to [0,1] (NDC to texture coords)
        float[] biasMatrix = {
            0.5f, 0.0f, 0.0f, 0.0f,
            0.0f, 0.5f, 0.0f, 0.0f,
            0.0f, 0.0f, 0.5f, 0.0f,
            0.5f, 0.5f, 0.5f, 1.0f
        };
        
        // Compute: bias * proj * view
        float[] temp = new float[16];
        multiplyMatrices(projMatrix, viewMatrix, temp);
        float[] result = new float[16];
        multiplyMatrices(biasMatrix, temp, result);
        
        shadowMatrix.clear();
        shadowMatrix.put(result);
        shadowMatrix.flip();
    }
    
    /**
     * Get the shadow matrix for this cascade.
     */
    public FloatBuffer getShadowMatrix() {
        return shadowMatrix;
    }
    
    /**
     * Multiply two 4x4 matrices: result = a * b
     */
    private void multiplyMatrices(float[] a, float[] b, float[] result) {
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                result[i * 4 + j] = 
                    a[i * 4 + 0] * b[0 * 4 + j] +
                    a[i * 4 + 1] * b[1 * 4 + j] +
                    a[i * 4 + 2] * b[2 * 4 + j] +
                    a[i * 4 + 3] * b[3 * 4 + j];
            }
        }
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
    
    public int getResolution() {
        return resolution;
    }
    
    public int getDepthTextureId() {
        return depthTextureId;
    }
}
