package net.vulkanic;

/**
 * Command buffer for recording rendering commands.
 */
public interface VulkanicCommandBuffer {
    void beginRenderPass(VulkanicFramebuffer framebuffer);
    void endRenderPass();
    void bindShader(VulkanicShader shader);
    void bindVertexBuffer(VulkanicBuffer buffer);
    void bindIndexBuffer(VulkanicBuffer buffer);
    void bindTexture(int unit, VulkanicTexture texture);
    void draw(int vertexCount);
    void drawIndexed(int indexCount);
    void clear(float r, float g, float b, float a);
    void clearDepth(float depth);
    void clearColorAndDepth(float r, float g, float b, float a, float depth);
    
    /**
     * Clear buffers using OpenGL's current clear color/depth state.
     * This is the proper way to clear when glClearColor has already been called.
     * @param bufferBits GL buffer bits (GL_COLOR_BUFFER_BIT, GL_DEPTH_BUFFER_BIT, etc.)
     */
    void clearBuffers(int bufferBits);
    
    void setViewport(int x, int y, int width, int height);
    void setScissor(int x, int y, int width, int height);
    void enableScissorTest();
    void disableScissorTest();
    void submit();
}
