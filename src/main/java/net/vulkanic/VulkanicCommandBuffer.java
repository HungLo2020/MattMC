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
    
    // === Phase 5: Shader/Program Operations ===
    void shaderSource(int shader, CharSequence[] source);
    void compileShader(int shader);
    int getShaderi(int shader, int pname);
    String getShaderInfoLog(int shader, int maxLength);
    void deleteShader(int shader);
    void attachShader(int program, int shader);
    void linkProgram(int program);
    int getProgrami(int program, int pname);
    String getProgramInfoLog(int program, int maxLength);
    void deleteProgram(int program);
    void useProgram(int program);
    
    // Uniform operations
    int getUniformLocation(int program, CharSequence name);
    void uniform1i(int location, int value);
    
    // Attribute operations
    void bindAttribLocation(int program, int index, CharSequence name);
    
    // === Phase 5: Buffer Operations ===
    void bindBuffer(int target, int buffer);
    void bufferData(int target, java.nio.ByteBuffer data, int usage);
    void bufferData(int target, long size, int usage);
    void bufferSubData(int target, int offset, java.nio.ByteBuffer data);
    java.nio.ByteBuffer mapBufferRange(int target, int offset, int length, int access);
    void unmapBuffer(int target);
    void deleteBuffer(int buffer);
    
    // === Phase 5: VAO Operations ===
    void bindVertexArray(int array);
    
    // === Phase 5: Framebuffer Operations ===
    void bindFramebuffer(int target, int framebuffer);
    void framebufferTexture2D(int target, int attachment, int textarget, int texture, int level);
    void blitFramebuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter);
    void deleteFramebuffer(int framebuffer);
    
    // === Phase 6: Query & State Operations ===
    int getError();
    String getString(int name);
    int getInteger(int pname);
    void getIntegerv(int pname, int[] params);
    int getTexLevelParameteri(int target, int level, int pname);
    boolean isEnabled(int cap);
    int getFramebufferAttachmentParameteri(int target, int attachment, int pname);
    boolean isFramebuffer(int framebuffer);
    boolean isTexture(int texture);
    boolean isBuffer(int buffer);
    boolean isVertexArray(int array);
    boolean isProgram(int program);
    void blendFuncSeparate(int sfactorRGB, int dfactorRGB, int sfactorAlpha, int dfactorAlpha);
    void blendEquationSeparate(int modeRGB, int modeAlpha);
    
    // Sync operations
    long fenceSync(int condition, int flags);
    int clientWaitSync(long sync, int flags, long timeout);
    void deleteSync(long sync);
    
    // Generic state operations
    void glEnable(int cap);
    void glDisable(int cap);
    void drawBuffer(int buffer);
    void glClearColor(float r, float g, float b, float a);
    void glClearDepth(double depth);
    
    void submit();
}
