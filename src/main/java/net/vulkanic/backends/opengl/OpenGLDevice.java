package net.vulkanic.backends.opengl;

import net.blaze3d.systems.GpuDevice;
import net.blaze3d.systems.RenderSystem;
import net.vulkanic.BackendType;
import net.vulkanic.VulkanicBuffer;
import net.vulkanic.VulkanicCommandBuffer;
import net.vulkanic.VulkanicDevice;
import net.vulkanic.VulkanicFramebuffer;
import net.vulkanic.VulkanicShader;
import net.vulkanic.VulkanicTexture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenGL implementation of VulkanicDevice.
 * 
 * This implementation wraps the existing Blaze3D rendering infrastructure,
 * providing a clean API while maintaining compatibility with the current system.
 */
public class OpenGLDevice implements VulkanicDevice {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenGLDevice.class);
    
    private final GpuDevice blaze3dDevice;
    
    public OpenGLDevice() {
        // Get the existing Blaze3D device from RenderSystem
        this.blaze3dDevice = RenderSystem.getDevice();
        if (this.blaze3dDevice == null) {
            throw new IllegalStateException("Blaze3D device is not initialized. Ensure RenderSystem is set up first.");
        }
        
        LOGGER.info("OpenGL backend initialized");
    }
    
    @Override
    public VulkanicCommandBuffer createCommandBuffer() {
        return new OpenGLCommandBuffer(this.blaze3dDevice);
    }
    
    @Override
    public VulkanicShader createShader(String vertexShaderSource, String fragmentShaderSource) {
        return new OpenGLShader(vertexShaderSource, fragmentShaderSource);
    }
    
    @Override
    public VulkanicBuffer createBuffer(int sizeInBytes) {
        return new OpenGLBuffer(sizeInBytes);
    }
    
    @Override
    public VulkanicTexture createTexture(int width, int height) {
        return new OpenGLTexture(width, height);
    }
    
    @Override
    public VulkanicFramebuffer createFramebuffer(int width, int height) {
        return new OpenGLFramebuffer(width, height);
    }
    
    @Override
    public BackendType getBackendType() {
        return BackendType.OPENGL;
    }
    
    @Override
    public String getBackendName() {
        return blaze3dDevice.getBackendName();
    }
    
    @Override
    public String getVendor() {
        return blaze3dDevice.getVendor();
    }
    
    @Override
    public String getRenderer() {
        return blaze3dDevice.getRenderer();
    }
    
    @Override
    public int getMaxTextureSize() {
        return blaze3dDevice.getMaxTextureSize();
    }
    
    @Override
    public void close() {
        LOGGER.info("OpenGL backend closed");
        // Note: We don't close the Blaze3D device as it's managed by RenderSystem
    }
}
