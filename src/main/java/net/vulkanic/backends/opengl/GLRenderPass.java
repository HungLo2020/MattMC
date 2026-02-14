package net.vulkanic.backends.opengl;

import net.vulkanic.RenderPass;
import net.vulkanic.RenderPassDesc;

/**
 * OpenGL implementation of RenderPass interface.
 * 
 * In OpenGL, this stores framebuffer configuration and clear operations.
 */
public class GLRenderPass implements RenderPass {
    
    private final long handle;
    private final RenderPassDesc desc;
    
    public GLRenderPass(long handle, RenderPassDesc desc) {
        this.handle = handle;
        this.desc = desc;
    }
    
    @Override
    public long getHandle() {
        return handle;
    }
    
    // OpenGL-specific accessor
    public RenderPassDesc getDesc() {
        return desc;
    }
}
