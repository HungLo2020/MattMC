package net.vulkanic;

/**
 * Represents a command buffer for recording rendering commands.
 * 
 * In Vulkan: Maps to VkCommandBuffer
 * In OpenGL: Represents immediate-mode execution context
 * 
 * Command buffers allow recording rendering commands for later execution.
 * In OpenGL backend, commands execute immediately (immediate mode).
 * In Vulkan backend, commands are recorded and submitted as a batch.
 * 
 * Extends CommandContext to maintain compatibility with existing code
 * that uses CommandContext for dynamic state commands.
 */
public interface CommandBuffer extends CommandContext {
    
    /**
     * Checks if this command buffer is currently recording commands.
     * 
     * @return true if recording, false otherwise
     */
    boolean isRecording();
}
