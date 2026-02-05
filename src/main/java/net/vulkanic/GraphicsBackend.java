package net.vulkanic;

/**
 * Interface for graphics backend implementations.
 * This interface defines the contract that all backends (OpenGL, Vulkan) must implement.
 */
public interface GraphicsBackend {
    
    /**
     * Bind a texture to the current texture unit.
     * @param textureId The texture ID (backend-specific)
     */
    void bindTexture(int textureId);
    
    /**
     * Set the viewport for rendering.
     * @param x The x coordinate of the viewport
     * @param y The y coordinate of the viewport
     * @param width The width of the viewport
     * @param height The height of the viewport
     */
    void viewport(int x, int y, int width, int height);
    
    /**
     * Clear the specified buffers.
     * @param mask Buffer mask (backend-specific, e.g., color, depth, or stencil buffers)
     */
    void clear(int mask);
    
    /**
     * Enable blending.
     */
    void enableBlend();
    
    /**
     * Disable blending.
     */
    void disableBlend();
    
    /**
     * Set the active shader program.
     * @param programId The shader program ID
     */
    void useProgram(int programId);
}
