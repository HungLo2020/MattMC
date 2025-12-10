package net.minecraft.client.renderer.shaders.hooks;

import net.minecraft.client.renderer.shaders.pipeline.WorldRenderingPhase;

/**
 * Tracks the current world rendering phase.
 * Following IRIS WorldRenderingPhase pattern exactly.
 * 
 * @see net.irisshaders.iris.pipeline.WorldRenderingPhase (IRIS reference)
 */
public class PhaseTracker {
    private WorldRenderingPhase currentPhase = WorldRenderingPhase.NONE;
    private boolean isWorldRendering = false;
    
    /**
     * Begins world rendering.
     */
    public void beginWorldRendering() {
        isWorldRendering = true;
        currentPhase = WorldRenderingPhase.NONE;
    }
    
    /**
     * Ends world rendering.
     */
    public void endWorldRendering() {
        isWorldRendering = false;
        currentPhase = WorldRenderingPhase.NONE;
    }
    
    /**
     * Sets the current rendering phase.
     */
    public void setPhase(WorldRenderingPhase phase) {
        if (phase == null) {
            throw new IllegalArgumentException("Phase cannot be null");
        }
        this.currentPhase = phase;
    }
    
    /**
     * Gets the current rendering phase.
     */
    public WorldRenderingPhase getCurrentPhase() {
        return currentPhase;
    }
    
    /**
     * Checks if world rendering is currently active.
     */
    public boolean isWorldRendering() {
        return isWorldRendering;
    }
    
    /**
     * Resets phase tracking state.
     */
    public void reset() {
        currentPhase = WorldRenderingPhase.NONE;
        isWorldRendering = false;
    }
}
