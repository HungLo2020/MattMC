package net.vulkanic;

/**
 * Defines shader stages in the graphics pipeline.
 * 
 * Backend-agnostic enum that maps to both Vulkan and OpenGL shader stages.
 */
public enum ShaderStage {
    /**
     * Vertex shader stage - processes vertices
     */
    VERTEX,
    
    /**
     * Fragment/Pixel shader stage - processes fragments/pixels
     */
    FRAGMENT,
    
    /**
     * Geometry shader stage - generates additional geometry
     */
    GEOMETRY,
    
    /**
     * Compute shader stage - general purpose computation
     */
    COMPUTE,
    
    /**
     * Tessellation control shader stage
     */
    TESSELLATION_CONTROL,
    
    /**
     * Tessellation evaluation shader stage
     */
    TESSELLATION_EVALUATION
}
