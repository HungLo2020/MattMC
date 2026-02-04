package net.vulkanic;

/**
 * Represents a command buffer for recording GPU rendering commands.
 * 
 * Commands are recorded in order and executed when the buffer is submitted.
 * This interface abstracts the underlying graphics API's command recording mechanism.
 */
public interface VulkanicCommandBuffer {
    /**
     * Begins a render pass, binding a framebuffer as the render target.
     * 
     * @param framebuffer the framebuffer to render to
     */
    void beginRenderPass(VulkanicFramebuffer framebuffer);
    
    /**
     * Ends the current render pass.
     */
    void endRenderPass();
    
    /**
     * Binds a shader pipeline for subsequent draw calls.
     * 
     * @param shader the shader to use
     */
    void bindShader(VulkanicShader shader);
    
    /**
     * Binds a vertex buffer for subsequent draw calls.
     * 
     * @param buffer the vertex buffer to bind
     */
    void bindVertexBuffer(VulkanicBuffer buffer);
    
    /**
     * Binds an index buffer for subsequent indexed draw calls.
     * 
     * @param buffer the index buffer to bind
     */
    void bindIndexBuffer(VulkanicBuffer buffer);
    
    /**
     * Binds a texture to a texture unit.
     * 
     * @param unit the texture unit (0-15)
     * @param texture the texture to bind
     */
    void bindTexture(int unit, VulkanicTexture texture);
    
    /**
     * Draws primitives using the currently bound vertex buffer.
     * 
     * @param vertexCount the number of vertices to draw
     */
    void draw(int vertexCount);
    
    /**
     * Draws primitives using the currently bound index buffer.
     * 
     * @param indexCount the number of indices to draw
     */
    void drawIndexed(int indexCount);
    
    /**
     * Clears the currently bound framebuffer.
     * 
     * @param r red component (0.0-1.0)
     * @param g green component (0.0-1.0)
     * @param b blue component (0.0-1.0)
     * @param a alpha component (0.0-1.0)
     */
    void clear(float r, float g, float b, float a);
    
    /**
     * Sets the viewport for rendering.
     * 
     * @param x the x offset
     * @param y the y offset
     * @param width the viewport width
     * @param height the viewport height
     */
    void setViewport(int x, int y, int width, int height);
    
    /**
     * Submits the recorded commands for execution.
     */
    void submit();
}
