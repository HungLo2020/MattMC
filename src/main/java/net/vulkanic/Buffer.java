package net.vulkanic;

/**
 * Represents a GPU buffer resource.
 * 
 * In Vulkan: Maps to VkBuffer
 * In OpenGL: Wraps a GL buffer object
 * 
 * Buffers store data on the GPU such as:
 * - Vertex data
 * - Index data
 * - Uniform data
 * - Storage data
 */
public interface Buffer {
    
    /**
     * Gets the backend-specific handle for this buffer.
     * 
     * @return Backend-specific buffer handle
     */
    long getHandle();
    
    /**
     * Gets the size of this buffer in bytes.
     * 
     * @return Buffer size in bytes
     */
    long getSize();
    
    /**
     * Gets the usage flags for this buffer.
     * 
     * @return Buffer usage
     */
    BufferUsage getUsage();
}
