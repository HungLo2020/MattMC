package net.vulkanic;

/**
 * Abstraction for command recording context.
 * 
 * This interface provides a unified way to record graphics commands that works
 * with both OpenGL and Vulkan backends:
 * 
 * - OpenGL: Uses an immediate-mode singleton context (commands execute immediately)
 * - Vulkan: Wraps a VkCommandBuffer for deferred recording and submission
 * 
 * This abstraction is the foundation for making the Vulkanic API truly backend-agnostic.
 * All rendering commands should eventually take a CommandContext parameter to support
 * Vulkan's command buffer model while remaining compatible with OpenGL.
 * 
 * Example usage:
 * <pre>
 * CommandContext ctx = VulkanicAPI.getCommandContext(); // OpenGL singleton today
 * VulkanicAPI.setDynamicViewport(ctx, 0, 0, 1920, 1080);
 * VulkanicAPI.drawIndexed(ctx, indexCount, 1, 0, 0, 0);
 * </pre>
 * 
 * In the future, Vulkan backend will use:
 * <pre>
 * CommandContext ctx = VulkanicAPI.beginCommandBuffer();
 * VulkanicAPI.setDynamicViewport(ctx, 0, 0, 1920, 1080);
 * VulkanicAPI.drawIndexed(ctx, indexCount, 1, 0, 0, 0);
 * VulkanicAPI.submitCommandBuffer(ctx);
 * </pre>
 */
public interface CommandContext {
    
    /**
     * Returns true if this is an immediate-mode context (OpenGL).
     * Returns false if this is a deferred recording context (Vulkan).
     */
    boolean isImmediate();
    
    /**
     * Gets the backend-specific handle for this context.
     * 
     * - OpenGL: Returns 0 (no handle needed)
     * - Vulkan: Returns VkCommandBuffer handle
     * 
     * @return Backend-specific handle
     */
    long getHandle();
    
    /**
     * Returns a human-readable debug name for this context.
     * Useful for debugging and validation.
     */
    String getDebugName();
}
