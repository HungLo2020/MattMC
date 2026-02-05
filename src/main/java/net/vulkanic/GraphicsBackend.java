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
}
