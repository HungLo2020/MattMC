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
    
    // Depth state operations
    void enableDepthTest();
    void disableDepthTest();
    void setDepthFunc(int func);
    void setDepthMask(boolean mask);
    
    // Blend state operations
    void enableBlend();
    void disableBlend();
    void setBlendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha);
    
    // Cull state operations
    void enableCull();
    void disableCull();
    
    // Color operations
    void setColorMask(boolean red, boolean green, boolean blue, boolean alpha);
    
    // Texture operations
    void setActiveTexture(int textureUnit);
    
    void submit();
}
