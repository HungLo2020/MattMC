package net.minecraft.client.renderer.shaders.targets;

import net.minecraft.client.renderer.shaders.framebuffer.GlFramebuffer;
import net.minecraft.client.renderer.shaders.texture.InternalTextureFormat;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages G-buffers (color textures) for shader rendering.
 * Based on IRIS 1.21.9 RenderTargets.java structure.
 * 
 * Provides colortex0-15 (16 render targets) for shader pack use.
 * Each target can have custom format and dimensions based on shader pack settings.
 */
public class GBufferManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(GBufferManager.class);
    private static final int MAX_RENDER_TARGETS = 16;  // colortex0 through colortex15
    
    private final RenderTarget[] targets;
    private final Map<Integer, RenderTargetSettings> targetSettings;
    private int cachedWidth;
    private int cachedHeight;
    private boolean destroyed;

    public GBufferManager(int width, int height, Map<Integer, RenderTargetSettings> settings) {
        this.targets = new RenderTarget[MAX_RENDER_TARGETS];
        this.targetSettings = new HashMap<>(settings);
        this.cachedWidth = width;
        this.cachedHeight = height;
        this.destroyed = false;
        
        LOGGER.info("Created GBufferManager ({}x{}) with {} configured targets", 
            width, height, settings.size());
    }

    /**
     * Gets a render target by index, creating it if it doesn't exist.
     * Following IRIS RenderTargets.getOrCreate() pattern.
     */
    public RenderTarget getOrCreate(int index) {
        if (destroyed) {
            throw new IllegalStateException("Attempted to use destroyed GBufferManager");
        }
        
        if (index < 0 || index >= MAX_RENDER_TARGETS) {
            throw new IllegalArgumentException("Render target index out of range: " + index);
        }
        
        if (targets[index] != null) {
            return targets[index];
        }
        
        create(index);
        return targets[index];
    }

    /**
     * Gets a render target by index without creating it.
     * Returns null if the target doesn't exist.
     */
    public RenderTarget get(int index) {
        if (destroyed) {
            throw new IllegalStateException("Attempted to use destroyed GBufferManager");
        }
        
        if (index < 0 || index >= MAX_RENDER_TARGETS) {
            return null;
        }
        
        return targets[index];
    }

    /**
     * Creates a render target at the specified index.
     * Following IRIS RenderTargets.create() pattern.
     */
    private void create(int index) {
        RenderTargetSettings settings = targetSettings.getOrDefault(
            index, 
            new RenderTargetSettings(InternalTextureFormat.RGBA8)
        );
        
        targets[index] = RenderTarget.builder()
            .setDimensions(cachedWidth, cachedHeight)
            .setName("colortex" + index)
            .setInternalFormat(settings.internalFormat)
            .setPixelFormat(settings.internalFormat.getPixelFormat())
            .build();
        
        LOGGER.debug("Created render target colortex{} with format {}", index, settings.internalFormat);
    }

    /**
     * Resizes all allocated render targets.
     * Following IRIS RenderTargets.resizeIfNeeded() pattern.
     */
    public boolean resizeIfNeeded(int newWidth, int newHeight) {
        if (destroyed) {
            throw new IllegalStateException("Attempted to resize destroyed GBufferManager");
        }
        
        boolean sizeChanged = newWidth != cachedWidth || newHeight != cachedHeight;
        
        if (sizeChanged) {
            cachedWidth = newWidth;
            cachedHeight = newHeight;
            
            for (int i = 0; i < targets.length; i++) {
                if (targets[i] != null) {
                    targets[i].resize(newWidth, newHeight);
                }
            }
            
            LOGGER.info("Resized G-buffers to {}x{}", newWidth, newHeight);
        }
        
        return sizeChanged;
    }

    /**
     * Returns the number of render targets (always 16 for colortex0-15).
     */
    public int getRenderTargetCount() {
        return MAX_RENDER_TARGETS;
    }

    /**
     * Returns the current width of the G-buffers.
     */
    public int getCurrentWidth() {
        return cachedWidth;
    }

    /**
     * Returns the current height of the G-buffers.
     */
    public int getCurrentHeight() {
        return cachedHeight;
    }

    /**
     * Destroys all allocated render targets and releases OpenGL resources.
     * Following IRIS RenderTargets.destroy() pattern.
     */
    public void destroy() {
        if (destroyed) {
            return;
        }
        
        destroyed = true;
        
        for (int i = 0; i < targets.length; i++) {
            if (targets[i] != null) {
                targets[i].destroy();
                targets[i] = null;
            }
        }
        
        LOGGER.info("Destroyed GBufferManager");
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    /**
     * Creates a framebuffer for clearing the specified buffers.
     * Following IRIS RenderTargets.createClearFramebuffer() pattern.
     * 
     * @param main If true, use main textures; if false, use alternate textures
     * @param bufferIndices Indices of buffers to attach (e.g., 0, 1, 2 for colortex0-2)
     * @return Configured framebuffer ready for clearing
     */
    public GlFramebuffer createClearFramebuffer(boolean main, int[] bufferIndices) {
        if (destroyed) {
            throw new IllegalStateException("Attempted to use destroyed GBufferManager");
        }

        GlFramebuffer framebuffer = new GlFramebuffer();

        // Attach each buffer as a color attachment
        for (int i = 0; i < bufferIndices.length; i++) {
            int bufferIndex = bufferIndices[i];
            RenderTarget target = getOrCreate(bufferIndex);
            
            // Choose main or alternate texture based on parameter
            int texture = main ? target.getMainTexture() : target.getAltTexture();
            
            // Attach to framebuffer
            framebuffer.addColorAttachment(i, texture);
        }

        // Configure draw buffers
        int[] drawBuffers = new int[bufferIndices.length];
        for (int i = 0; i < bufferIndices.length; i++) {
            drawBuffers[i] = GL30.GL_COLOR_ATTACHMENT0 + i;
        }
        framebuffer.drawBuffers(drawBuffers);

        return framebuffer;
    }

    /**
     * Settings for a render target.
     * Based on IRIS PackRenderTargetDirectives.RenderTargetSettings
     */
    public static class RenderTargetSettings {
        private final InternalTextureFormat internalFormat;

        public RenderTargetSettings(InternalTextureFormat internalFormat) {
            this.internalFormat = internalFormat;
        }

        public InternalTextureFormat getInternalFormat() {
            return internalFormat;
        }
    }
}
