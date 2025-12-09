package net.minecraft.client.renderer.shader.gbuffer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;

/**
 * Manages G-buffers for deferred rendering.
 * Provides multiple render targets (MRT) for storing geometry data.
 */
@Environment(EnvType.CLIENT)
public class GBufferManager implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_COLOR_ATTACHMENTS = 8;
    
    private int framebufferId = -1;
    private final int[] colorTextures = new int[MAX_COLOR_ATTACHMENTS];
    private int depthTexture = -1;
    private int width;
    private int height;
    private boolean initialized = false;
    
    /**
     * Initializes the G-buffer with the specified dimensions.
     */
    public void initialize(int width, int height) {
        RenderSystem.assertOnRenderThread();
        
        if (initialized) {
            close();
        }
        
        this.width = width;
        this.height = height;
        
        // Create framebuffer
        framebufferId = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferId);
        
        // Create color textures (colortex0-7)
        for (int i = 0; i < MAX_COLOR_ATTACHMENTS; i++) {
            colorTextures[i] = createColorTexture(width, height);
            GL30.glFramebufferTexture2D(
                GL30.GL_FRAMEBUFFER,
                GL30.GL_COLOR_ATTACHMENT0 + i,
                GL11.GL_TEXTURE_2D,
                colorTextures[i],
                0
            );
        }
        
        // Create depth texture
        depthTexture = createDepthTexture(width, height);
        GL30.glFramebufferTexture2D(
            GL30.GL_FRAMEBUFFER,
            GL30.GL_DEPTH_ATTACHMENT,
            GL11.GL_TEXTURE_2D,
            depthTexture,
            0
        );
        
        // Check framebuffer status
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            LOGGER.error("G-buffer framebuffer is not complete: {}", status);
            close();
            return;
        }
        
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        initialized = true;
        LOGGER.info("G-buffer initialized with dimensions {}x{}", width, height);
    }
    
    /**
     * Creates a color texture for G-buffer storage.
     */
    private int createColorTexture(int width, int height) {
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        
        // Use RGBA16F for HDR rendering
        GL11.glTexImage2D(
            GL11.GL_TEXTURE_2D,
            0,
            GL30.GL_RGBA16F,
            width,
            height,
            0,
            GL11.GL_RGBA,
            GL11.GL_FLOAT,
            0
        );
        
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
        
        return texture;
    }
    
    /**
     * Creates a depth texture for G-buffer.
     */
    private int createDepthTexture(int width, int height) {
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        
        GL11.glTexImage2D(
            GL11.GL_TEXTURE_2D,
            0,
            GL30.GL_DEPTH_COMPONENT24,
            width,
            height,
            0,
            GL11.GL_DEPTH_COMPONENT,
            GL11.GL_FLOAT,
            0
        );
        
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
        
        return texture;
    }
    
    /**
     * Binds the G-buffer for rendering.
     */
    public void bind() {
        RenderSystem.assertOnRenderThread();
        if (initialized) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferId);
            
            // Enable all color attachments for MRT
            int[] drawBuffers = new int[MAX_COLOR_ATTACHMENTS];
            for (int i = 0; i < MAX_COLOR_ATTACHMENTS; i++) {
                drawBuffers[i] = GL30.GL_COLOR_ATTACHMENT0 + i;
            }
            GL30.glDrawBuffers(drawBuffers);
        }
    }
    
    /**
     * Unbinds the G-buffer.
     */
    public void unbind() {
        RenderSystem.assertOnRenderThread();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }
    
    /**
     * Binds a color texture for reading.
     */
    public void bindColorTexture(int index, int textureUnit) {
        if (index >= 0 && index < MAX_COLOR_ATTACHMENTS) {
            org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0 + textureUnit);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTextures[index]);
        }
    }
    
    /**
     * Binds the depth texture for reading.
     */
    public void bindDepthTexture(int textureUnit) {
        org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0 + textureUnit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTexture);
    }
    
    /**
     * Resizes the G-buffer.
     */
    public void resize(int newWidth, int newHeight) {
        if (newWidth != width || newHeight != height) {
            initialize(newWidth, newHeight);
        }
    }
    
    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        
        if (framebufferId != -1) {
            GL30.glDeleteFramebuffers(framebufferId);
            framebufferId = -1;
        }
        
        for (int i = 0; i < MAX_COLOR_ATTACHMENTS; i++) {
            if (colorTextures[i] != 0) {
                GL11.glDeleteTextures(colorTextures[i]);
                colorTextures[i] = 0;
            }
        }
        
        if (depthTexture != -1) {
            GL11.glDeleteTextures(depthTexture);
            depthTexture = -1;
        }
        
        initialized = false;
    }
}
