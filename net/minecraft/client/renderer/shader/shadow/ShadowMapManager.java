package net.minecraft.client.renderer.shader.shadow;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;

/**
 * Manages shadow map textures and framebuffers.
 * Provides depth textures for shadow rendering.
 */
@Environment(EnvType.CLIENT)
public class ShadowMapManager implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int DEFAULT_SHADOW_MAP_SIZE = 2048;
    
    private int shadowFramebufferId = -1;
    private int shadowDepthTexture = -1;
    private int shadowMapSize = DEFAULT_SHADOW_MAP_SIZE;
    private boolean initialized = false;
    
    /**
     * Initializes the shadow map with the specified size.
     */
    public void initialize(int size) {
        RenderSystem.assertOnRenderThread();
        
        if (initialized) {
            close();
        }
        
        this.shadowMapSize = size;
        
        // Create framebuffer
        shadowFramebufferId = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, shadowFramebufferId);
        
        // Create depth texture for shadow map
        shadowDepthTexture = createShadowDepthTexture(size);
        GL30.glFramebufferTexture2D(
            GL30.GL_FRAMEBUFFER,
            GL30.GL_DEPTH_ATTACHMENT,
            GL11.GL_TEXTURE_2D,
            shadowDepthTexture,
            0
        );
        
        // Shadow maps don't need color attachment
        GL30.glDrawBuffer(GL11.GL_NONE);
        GL30.glReadBuffer(GL11.GL_NONE);
        
        // Check framebuffer status
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            LOGGER.error("Shadow map framebuffer is not complete: {}", status);
            close();
            return;
        }
        
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        initialized = true;
        LOGGER.info("Shadow map initialized with size {}x{}", size, size);
    }
    
    /**
     * Creates a depth texture for shadow mapping.
     */
    private int createShadowDepthTexture(int size) {
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        
        // Use high precision depth format
        GL11.glTexImage2D(
            GL11.GL_TEXTURE_2D,
            0,
            GL30.GL_DEPTH_COMPONENT24,
            size,
            size,
            0,
            GL11.GL_DEPTH_COMPONENT,
            GL11.GL_FLOAT,
            0
        );
        
        // Shadow map filtering
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        
        // Clamp to edge to avoid sampling outside shadow map
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
        
        // Enable shadow comparison mode for hardware PCF
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL14.GL_TEXTURE_COMPARE_MODE, org.lwjgl.opengl.GL14.GL_COMPARE_R_TO_TEXTURE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL14.GL_TEXTURE_COMPARE_FUNC, GL11.GL_LEQUAL);
        
        return texture;
    }
    
    /**
     * Binds the shadow map framebuffer for rendering.
     */
    public void bind() {
        RenderSystem.assertOnRenderThread();
        if (initialized) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, shadowFramebufferId);
            GL11.glViewport(0, 0, shadowMapSize, shadowMapSize);
        }
    }
    
    /**
     * Unbinds the shadow map framebuffer.
     */
    public void unbind() {
        RenderSystem.assertOnRenderThread();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }
    
    /**
     * Binds the shadow depth texture for reading.
     */
    public void bindShadowTexture(int textureUnit) {
        if (initialized) {
            org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0 + textureUnit);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, shadowDepthTexture);
        }
    }
    
    /**
     * Gets the shadow map size.
     */
    public int getShadowMapSize() {
        return shadowMapSize;
    }
    
    /**
     * Checks if the shadow map is initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }
    
    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        
        if (shadowFramebufferId != -1) {
            GL30.glDeleteFramebuffers(shadowFramebufferId);
            shadowFramebufferId = -1;
        }
        
        if (shadowDepthTexture != -1) {
            GL11.glDeleteTextures(shadowDepthTexture);
            shadowDepthTexture = -1;
        }
        
        initialized = false;
    }
}
