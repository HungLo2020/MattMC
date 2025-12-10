package net.minecraft.client.renderer.shaders.pipeline;

/**
 * Represents a shader rendering pipeline that can be activated and used for world rendering.
 * 
 * Based on IRIS IrisRenderingPipeline interface pattern.
 * Reference: frnsrc/Iris-1.21.9/.../pipeline/IrisRenderingPipeline.java
 */
public interface ShaderRenderingPipeline {
    
    /**
     * Prepares the pipeline for a new frame of world rendering.
     * Called at the start of each frame before any rendering occurs.
     */
    void beginWorldRendering();
    
    /**
     * Called before terrain rendering begins.
     * Sets up framebuffers and programs for opaque terrain rendering.
     */
    void beginTerrainRendering();
    
    /**
     * Called after terrain rendering ends.
     * Performs any cleanup needed after terrain pass.
     */
    void endTerrainRendering();
    
    /**
     * Called before translucent rendering begins.
     * Sets up framebuffers and programs for translucent rendering.
     */
    void beginTranslucentRendering();
    
    /**
     * Called after translucent rendering ends.
     * Performs any cleanup needed after translucent pass.
     */
    void endTranslucentRendering();
    
    /**
     * Transitions to a specific rendering phase.
     * 
     * @param phase The rendering phase to transition to
     */
    void setPhase(WorldRenderingPhase phase);
    
    /**
     * Finalizes the current frame and executes composite/final passes.
     * Called at the end of each frame after all geometry is rendered.
     */
    void finishWorldRendering();
    
    /**
     * Destroys this pipeline and releases all OpenGL resources.
     * Called when the pipeline is no longer needed.
     */
    void destroy();
    
    /**
     * Returns whether this pipeline is currently active and ready for rendering.
     * 
     * @return true if the pipeline is active, false otherwise
     */
    boolean isActive();
}
