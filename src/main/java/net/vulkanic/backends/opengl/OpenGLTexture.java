package net.vulkanic.backends.opengl;

import net.blaze3d.platform.NativeImage;
import net.blaze3d.systems.CommandEncoder;
import net.blaze3d.systems.RenderSystem;
import net.blaze3d.textures.GpuTexture;
import net.blaze3d.textures.TextureFormat;
import net.vulkanic.VulkanicTexture;
import java.nio.ByteBuffer;

/**
 * OpenGL implementation of VulkanicTexture.
 * 
 * Wraps Blaze3D's GpuTexture to provide the Vulkanic texture interface.
 */
public class OpenGLTexture implements VulkanicTexture {
    private GpuTexture gpuTexture;
    private final int width;
    private final int height;
    
    /**
     * Creates a new OpenGL texture with the specified dimensions.
     * Uses RGBA8 format by default.
     * 
     * @param width the texture width
     * @param height the texture height
     */
    public OpenGLTexture(int width, int height) {
        this(width, height, TextureFormat.RGBA8);
    }
    
    /**
     * Creates a new OpenGL texture with the specified dimensions and format.
     * Package-private for use by OpenGLFramebuffer.
     * 
     * @param width the texture width
     * @param height the texture height
     * @param format the texture format
     */
    OpenGLTexture(int width, int height, TextureFormat format) {
        this.width = width;
        this.height = height;
        
        // Create GPU texture using Blaze3D device
        // Usage flags: TEXTURE_BINDING for sampling, COPY_DST for uploading
        int usage = GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST;
        this.gpuTexture = RenderSystem.getDevice().createTexture(
            "VulkanicTexture",
            usage,
            format,
            width,
            height,
            1,  // depthOrLayers
            1   // mipLevels
        );
    }
    
    @Override
    public void upload(ByteBuffer data, int width, int height) {
        if (width != this.width || height != this.height) {
            throw new IllegalArgumentException("Upload dimensions (" + width + "x" + height + 
                ") don't match texture dimensions (" + this.width + "x" + this.height + ")");
        }
        
        // Calculate expected data size for RGBA8 format
        int expectedSize = width * height * 4; // 4 bytes per pixel (RGBA)
        if (data.remaining() < expectedSize) {
            throw new IllegalArgumentException("Data buffer too small. Expected " + expectedSize + " bytes, got " + data.remaining());
        }
        
        // Use command encoder to write data to the texture
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToTexture(
            gpuTexture,
            data,
            NativeImage.Format.RGBA,
            0,  // mipLevel
            0, 0,  // x, y offset
            width, height,  // width, height
            1   // depthOrLayers
        );
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
     * Gets the underlying Blaze3D GPU texture.
     * Package-private for use by other OpenGL backend classes.
     * 
     * @return the GPU texture
     */
    GpuTexture getGpuTexture() {
        return gpuTexture;
    }
    
    /**
     * Sets the underlying GPU texture.
     * Package-private for use by OpenGLFramebuffer.
     * 
     * @param gpuTexture the new GPU texture
     */
    void setGpuTexture(GpuTexture gpuTexture) {
        this.gpuTexture = gpuTexture;
    }
    
    @Override
    public void close() {
        if (gpuTexture != null) {
            gpuTexture.close();
        }
    }
}
