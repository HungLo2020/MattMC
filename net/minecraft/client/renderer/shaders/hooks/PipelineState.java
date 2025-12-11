package net.minecraft.client.renderer.shaders.hooks;

import net.minecraft.client.renderer.shaders.pipeline.WorldRenderingPipeline;

/**
 * Tracks pipeline activation state.
 * Following IRIS pipeline activation pattern.
 */
public class PipelineState {
    private boolean isActive = false;
    private WorldRenderingPipeline activePipeline = null;
    
    /**
     * Activates the pipeline.
     */
    public void activate(WorldRenderingPipeline pipeline) {
        if (pipeline == null) {
            throw new IllegalArgumentException("Pipeline cannot be null");
        }
        this.activePipeline = pipeline;
        this.isActive = true;
    }
    
    /**
     * Deactivates the pipeline.
     */
    public void deactivate() {
        this.isActive = false;
        this.activePipeline = null;
    }
    
    /**
     * Checks if a pipeline is currently active.
     */
    public boolean isActive() {
        return isActive;
    }
    
    /**
     * Gets the active pipeline.
     */
    public WorldRenderingPipeline getActivePipeline() {
        return activePipeline;
    }
    
    /**
     * Resets pipeline state.
     */
    public void reset() {
        isActive = false;
        activePipeline = null;
    }
}
