package net.minecraft.client.renderer.shaders.targets;

import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.client.renderer.shaders.texture.InternalTextureFormat;
import net.minecraft.client.renderer.shaders.texture.PixelFormat;
import net.minecraft.client.renderer.shaders.texture.PixelType;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

/**
 * Represents a single render target (colortex) with main and alt textures.
 * Based on IRIS 1.21.9 RenderTarget.java structure.
 * 
 * Each render target has two textures for ping-pong rendering:
 * - mainTexture: Primary texture
 * - altTexture: Alternative texture for double buffering
 */
public class RenderTarget {
    private static final Logger LOGGER = LoggerFactory.getLogger(RenderTarget.class);
    private static final ByteBuffer NULL_BUFFER = null;
    
    private final InternalTextureFormat internalFormat;
    private final PixelFormat format;
    private final PixelType type;
    private final int mainTexture;
    private final int altTexture;
    private int width;
    private int height;
    private boolean isValid;
    private String name;

    public RenderTarget(Builder builder) {
        this.isValid = true;
        
        this.name = builder.name;
        this.internalFormat = builder.internalFormat;
        this.format = builder.format;
        this.type = builder.type;
        
        this.width = builder.width;
        this.height = builder.height;
        
        // Generate OpenGL textures
        this.mainTexture = GlStateManager._genTexture();
        this.altTexture = GlStateManager._genTexture();
        
        boolean isPixelFormatInteger = builder.internalFormat.getPixelFormat().isInteger();
        setupTexture(mainTexture, builder.width, builder.height, !isPixelFormatInteger, false);
        setupTexture(altTexture, builder.width, builder.height, !isPixelFormatInteger, true);
        
        // Clean up after ourselves
        GlStateManager._bindTexture(0);
        
        LOGGER.debug("Created render target: {} ({}x{}, format={})", name, width, height, internalFormat);
    }

    public static Builder builder() {
        return new Builder();
    }

    private void setupTexture(int texture, int width, int height, boolean allowsLinear, boolean alt) {
        resizeTexture(texture, width, height, alt);
        
        // Set texture parameters
        GlStateManager._bindTexture(texture);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, 
            allowsLinear ? GL11C.GL_LINEAR : GL11C.GL_NEAREST);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, 
            allowsLinear ? GL11C.GL_LINEAR : GL11C.GL_NEAREST);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL13C.GL_CLAMP_TO_EDGE);
        GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL13C.GL_CLAMP_TO_EDGE);
    }

    private void resizeTexture(int texture, int width, int height, boolean alt) {
        GlStateManager._bindTexture(texture);
        GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, internalFormat.getGlFormat(), width, height, 
            0, format.getGlFormat(), type.getGlFormat(), NULL_BUFFER);
        
        LOGGER.debug("Resized texture {} to {}x{} ({})", 
            name + (alt ? " alt" : " main"), width, height, internalFormat);
    }

    public void resize(int width, int height) {
        requireValid();
        
        this.width = width;
        this.height = height;
        
        resizeTexture(mainTexture, width, height, false);
        resizeTexture(altTexture, width, height, true);
    }

    public InternalTextureFormat getInternalFormat() {
        return internalFormat;
    }

    public int getMainTexture() {
        requireValid();
        return mainTexture;
    }

    public int getAltTexture() {
        requireValid();
        return altTexture;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String getName() {
        return name;
    }

    public boolean isValid() {
        return isValid;
    }

    public void destroy() {
        if (!isValid) {
            return;
        }
        
        isValid = false;
        GlStateManager._deleteTexture(mainTexture);
        GlStateManager._deleteTexture(altTexture);
        
        LOGGER.debug("Destroyed render target: {}", name);
    }

    private void requireValid() {
        if (!isValid) {
            throw new IllegalStateException("Attempted to use destroyed render target: " + name);
        }
    }

    /**
     * Builder for RenderTarget following IRIS pattern.
     */
    public static class Builder {
        private String name = "unnamed";
        private int width = 1920;
        private int height = 1080;
        private InternalTextureFormat internalFormat = InternalTextureFormat.RGBA8;
        private PixelFormat format = PixelFormat.RGBA;
        private PixelType type = PixelType.UNSIGNED_BYTE;

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setDimensions(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder setInternalFormat(InternalTextureFormat internalFormat) {
            this.internalFormat = internalFormat;
            return this;
        }

        public Builder setPixelFormat(PixelFormat format) {
            this.format = format;
            return this;
        }

        public Builder setPixelType(PixelType type) {
            this.type = type;
            return this;
        }

        public RenderTarget build() {
            return new RenderTarget(this);
        }
    }
}
