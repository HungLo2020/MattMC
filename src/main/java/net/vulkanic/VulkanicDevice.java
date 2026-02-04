package net.vulkanic;

import org.jetbrains.annotations.Nullable;

/**
 * Represents a GPU device abstraction for the Vulkanic rendering system.
 * 
 * This interface provides methods for creating rendering resources and querying
 * device capabilities, independent of the underlying graphics API.
 */
public interface VulkanicDevice {
    /**
     * Creates a command buffer for recording rendering commands.
     * 
     * @return a new command buffer instance
     */
    VulkanicCommandBuffer createCommandBuffer();
    
    /**
     * Creates a shader program from source code.
     * 
     * @param vertexShaderSource the vertex shader source code
     * @param fragmentShaderSource the fragment shader source code
     * @return a compiled shader instance
     */
    VulkanicShader createShader(String vertexShaderSource, String fragmentShaderSource);
    
    /**
     * Creates a GPU buffer for vertex or uniform data.
     * 
     * @param sizeInBytes the size of the buffer in bytes
     * @return a new buffer instance
     */
    VulkanicBuffer createBuffer(int sizeInBytes);
    
    /**
     * Creates a texture resource.
     * 
     * @param width the texture width
     * @param height the texture height
     * @return a new texture instance
     */
    VulkanicTexture createTexture(int width, int height);
    
    /**
     * Creates a framebuffer for rendering to textures.
     * 
     * @param width the framebuffer width
     * @param height the framebuffer height
     * @return a new framebuffer instance
     */
    VulkanicFramebuffer createFramebuffer(int width, int height);
    
    /**
     * Gets the backend type being used by this device.
     * 
     * @return the backend type
     */
    BackendType getBackendType();
    
    /**
     * Gets the name of the backend implementation.
     * 
     * @return the backend name (e.g., "OpenGL 4.6", "Vulkan 1.3")
     */
    String getBackendName();
    
    /**
     * Gets the GPU vendor name.
     * 
     * @return the vendor name (e.g., "NVIDIA", "AMD", "Intel")
     */
    String getVendor();
    
    /**
     * Gets the GPU renderer name.
     * 
     * @return the renderer name (e.g., "GeForce RTX 4090")
     */
    String getRenderer();
    
    /**
     * Gets the maximum supported texture size.
     * 
     * @return the maximum texture size in pixels
     */
    int getMaxTextureSize();
    
    /**
     * Releases all resources associated with this device.
     */
    void close();
}
