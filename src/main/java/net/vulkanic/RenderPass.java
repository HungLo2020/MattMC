package net.vulkanic;

/**
 * Defines a render pass with attachments and operations.
 * 
 * In Vulkan: Maps to VkRenderPass
 * In OpenGL: Represents framebuffer configuration and clear operations
 * 
 * A render pass describes:
 * - Color attachments (render targets)
 * - Depth/stencil attachment
 * - Load operations (clear, load, don't care)
 * - Store operations (store, don't care)
 * - Clear values
 */
public interface RenderPass {
    
    /**
     * Gets the backend-specific handle for this render pass.
     * 
     * @return Backend-specific render pass handle
     */
    long getHandle();
}
