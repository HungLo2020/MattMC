package net.vulkanic;

/**
 * Represents an immutable graphics pipeline state object.
 * 
 * In Vulkan: Maps directly to VkPipeline
 * In OpenGL: Represents a collection of state that will be applied together
 * 
 * A pipeline encapsulates:
 * - Shader stages (vertex, fragment, etc.)
 * - Rasterization state (cull mode, polygon mode)
 * - Blend state (blend factors, blend operations)
 * - Depth/stencil state (depth test, depth write)
 * - Vertex input layout
 * 
 * Pipelines are immutable once created. To change state, create a new pipeline.
 */
public interface Pipeline {
    
    /**
     * Gets the backend-specific handle for this pipeline.
     * 
     * @return Backend-specific pipeline handle
     */
    long getHandle();
    
    /**
     * Gets a human-readable debug name for this pipeline.
     * Useful for debugging and profiling.
     * 
     * @return Debug name, or null if not set
     */
    String getDebugName();
}
