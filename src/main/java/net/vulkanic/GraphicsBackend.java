package net.vulkanic;

/**
 * Interface for graphics backend implementations.
 * This interface defines the contract that all backends (OpenGL, Vulkan) must implement.
 */
public interface GraphicsBackend {
    
    void bindTexture(int textureId);
    void viewport(int x, int y, int width, int height);
    void clear(int mask);
    void enableBlend();
    void disableBlend();
    void useProgram(int programId);
    void enable(int cap);
    void disable(int cap);
    
    // Depth operations
    void setDepthTestFunction(int func);
    void setDepthWriteEnabled(boolean enabled);
    
    // Color operations
    void setColorWriteMask(boolean r, boolean g, boolean b, boolean a);
    
    // Scissor operations
    void setScissorBox(int x, int y, int w, int h);
    
    // Pixel operations
    void setPixelStoreMode(int pname, int value);
    
    // Framebuffer operations
    void attachFramebuffer(int target, int fbo);
    void attachTextureToFramebuffer(int target, int attachment, int textarget, int texture, int level);
    
    // Buffer operations  
    void attachBuffer(int target, int buffer);
    
    // Texture unit and parameter operations
    void activateTextureUnit(int unit);
    void configureTextureParameter(int target, int pname, int param);
    int createTexture();
    void removeTexture(int texture);
    
    // Polygon rendering operations
    void configurePolygonMode(int face, int mode);
    void configurePolygonOffset(float factor, float units);
    void configureLogicOp(int opcode);
    
    // Drawing operations
    void drawPrimitiveArrays(int mode, int first, int count);
    void configureBlendFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha);
    
    // Error checking
    int checkForErrors();
}
