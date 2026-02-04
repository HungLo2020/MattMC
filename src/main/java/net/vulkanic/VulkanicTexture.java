package net.vulkanic;

import java.nio.ByteBuffer;

/**
 * Represents a GPU texture resource.
 * 
 * Textures can store image data and be sampled in shaders.
 */
public interface VulkanicTexture {
    /**
     * Uploads image data to the texture.
     * 
     * @param data the pixel data
     * @param width the image width
     * @param height the image height
     */
    void upload(ByteBuffer data, int width, int height);
    
    /**
     * Gets the width of the texture.
     * 
     * @return the texture width
     */
    int getWidth();
    
    /**
     * Gets the height of the texture.
     * 
     * @return the texture height
     */
    int getHeight();
    
    /**
     * Releases resources associated with this texture.
     */
    void close();
}
