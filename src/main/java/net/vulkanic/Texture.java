package net.vulkanic;

/**
 * Represents a texture/image resource.
 * 
 * In Vulkan: Maps to VkImage
 * In OpenGL: Wraps a GL texture object
 * 
 * Textures store image data on the GPU such as:
 * - 2D textures (albedo, normal maps, etc.)
 * - Cubemaps
 * - 3D textures
 * - Array textures
 */
public interface Texture {
    
    /**
     * Gets the backend-specific handle for this texture.
     * 
     * @return Backend-specific texture handle
     */
    long getHandle();
    
    /**
     * Gets the width of this texture in pixels.
     * 
     * @return Texture width
     */
    int getWidth();
    
    /**
     * Gets the height of this texture in pixels.
     * 
     * @return Texture height
     */
    int getHeight();
    
    /**
     * Gets the format of this texture.
     * 
     * @return Texture format
     */
    Format getFormat();
}
