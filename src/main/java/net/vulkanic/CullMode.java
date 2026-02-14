package net.vulkanic;

/**
 * Defines face culling modes.
 * 
 * Backend-agnostic enum that replaces GL_CULL_FACE configuration.
 */
public enum CullMode {
    /**
     * No face culling - all faces rendered
     */
    NONE,
    
    /**
     * Cull front-facing triangles
     */
    FRONT,
    
    /**
     * Cull back-facing triangles (most common)
     */
    BACK,
    
    /**
     * Cull both front and back faces (nothing rendered)
     */
    FRONT_AND_BACK
}
