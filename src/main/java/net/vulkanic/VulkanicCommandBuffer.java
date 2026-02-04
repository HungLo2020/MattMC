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
    void bindTexture(int texture);
    void setTexParameter(int target, int pname, int param);
    void setPixelStore(int pname, int param);
    
    // Polygon offset operations
    void enablePolygonOffset();
    void disablePolygonOffset();
    void setPolygonOffset(float factor, float units);
    
    // Color logic operations
    void enableColorLogicOp();
    void disableColorLogicOp();
    void setLogicOp(int op);
    
    // Polygon mode operation
    void setPolygonMode(int face, int mode);
    
    // Draw operations
    void drawArrays(int mode, int first, int count);
    void drawElements(int mode, int count, int type, long indices);
    
    // Vertex attribute operations
    void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long pointer);
    void vertexAttribIPointer(int index, int size, int type, int stride, long pointer);
    void enableVertexAttribArray(int index);
    
    // Texture gen/delete operations
    int genTexture();
    void deleteTexture(int texture);
    
    // Texture image operations
    void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, java.nio.ByteBuffer pixels);
    void texSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, long pixels);
    void texSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, java.nio.ByteBuffer pixels);
    
    // Read pixels operation
    void readPixels(int x, int y, int width, int height, int format, int type, long pixels);
    
    void submit();
}
