package net.minecraft.client.renderer.shaders.hooks;

import net.minecraft.client.renderer.shaders.pipeline.ShaderRenderingPipeline;
import net.minecraft.client.renderer.shaders.pipeline.WorldRenderingPhase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rendering hooks for LevelRenderer integration.
 * Following IRIS MixinLevelRenderer pattern exactly.
 * 
 * Hook points:
 * 1. onWorldRenderStart() - Start of renderLevel()
 * 2. onWorldRenderEnd() - End of renderLevel()
 * 3. onBeginTerrainRendering() - Before terrain pass
 * 4. onEndTerrainRendering() - After terrain pass
 * 5. onBeginTranslucentRendering() - Before translucent pass
 * 6. onEndTranslucentRendering() - After translucent pass
 * 
 * @see net.irisshaders.iris.mixin.MixinLevelRenderer (IRIS reference)
 */
public class RenderingHooks {
    private static final Logger LOGGER = LoggerFactory.getLogger(RenderingHooks.class);
    
    private static ShaderRenderingPipeline activePipeline = null;
    private static final PhaseTracker phaseTracker = new PhaseTracker();
    private static final PipelineState pipelineState = new PipelineState();
    
    /**
     * Called at the start of world rendering.
     * IRIS: MixinLevelRenderer renderLevel() HEAD injection
     */
    public static void onWorldRenderStart() {
        if (activePipeline != null) {
            phaseTracker.beginWorldRendering();
            pipelineState.activate(activePipeline);
            activePipeline.beginWorldRendering();
        }
    }
    
    /**
     * Called at the end of world rendering.
     * IRIS: MixinLevelRenderer renderLevel() RETURN injection
     */
    public static void onWorldRenderEnd() {
        if (activePipeline != null) {
            activePipeline.finishWorldRendering();
            pipelineState.deactivate();
            phaseTracker.endWorldRendering();
        }
    }
    
    /**
     * Called before terrain rendering begins.
     * IRIS: Before renderChunkLayer() calls
     */
    public static void onBeginTerrainRendering() {
        if (activePipeline != null) {
            phaseTracker.setPhase(WorldRenderingPhase.TERRAIN_SOLID);
            activePipeline.beginTerrainRendering();
        }
    }
    
    /**
     * Called after terrain rendering ends.
     * IRIS: After renderChunkLayer() calls
     */
    public static void onEndTerrainRendering() {
        if (activePipeline != null) {
            activePipeline.endTerrainRendering();
            phaseTracker.setPhase(WorldRenderingPhase.NONE);
        }
    }
    
    /**
     * Called before translucent rendering begins.
     * IRIS: Before translucent renderChunkLayer() call
     */
    public static void onBeginTranslucentRendering() {
        if (activePipeline != null) {
            phaseTracker.setPhase(WorldRenderingPhase.TRANSLUCENT_TERRAIN);
            activePipeline.beginTranslucentRendering();
        }
    }
    
    /**
     * Called after translucent rendering ends.
     * IRIS: After translucent renderChunkLayer() call
     */
    public static void onEndTranslucentRendering() {
        if (activePipeline != null) {
            activePipeline.endTranslucentRendering();
            phaseTracker.setPhase(WorldRenderingPhase.NONE);
        }
    }
    
    /**
     * Sets the active shader pipeline.
     * Called by ShaderSystemLifecycle during initialization.
     */
    public static void setActivePipeline(ShaderRenderingPipeline pipeline) {
        activePipeline = pipeline;
        LOGGER.debug("Active shader pipeline set: {}", pipeline != null ? "enabled" : "disabled");
    }
    
    /**
     * Gets the active shader pipeline.
     */
    public static ShaderRenderingPipeline getActivePipeline() {
        return activePipeline;
    }
    
    /**
     * Gets the phase tracker.
     */
    public static PhaseTracker getPhaseTracker() {
        return phaseTracker;
    }
    
    /**
     * Gets the pipeline state.
     */
    public static PipelineState getPipelineState() {
        return pipelineState;
    }
    
    /**
     * Checks if a shader pipeline is currently active.
     */
    public static boolean isPipelineActive() {
        return activePipeline != null && pipelineState.isActive();
    }
    
    /**
     * Resets all rendering state.
     * Called during shader reload or cleanup.
     */
    public static void reset() {
        if (pipelineState.isActive()) {
            onWorldRenderEnd();
        }
        activePipeline = null;
        phaseTracker.reset();
        pipelineState.reset();
        LOGGER.debug("Rendering hooks reset");
    }
}
