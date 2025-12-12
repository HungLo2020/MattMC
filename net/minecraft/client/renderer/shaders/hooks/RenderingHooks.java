package net.minecraft.client.renderer.shaders.hooks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.shaders.core.ShaderSystem;
import net.minecraft.client.renderer.shaders.pipeline.PipelineManager;
import net.minecraft.client.renderer.shaders.pipeline.ShaderRenderingPipeline;
import net.minecraft.client.renderer.shaders.pipeline.VanillaRenderingPipeline;
import net.minecraft.client.renderer.shaders.pipeline.WorldRenderingPhase;
import net.minecraft.client.renderer.shaders.pipeline.WorldRenderingPipeline;
import net.minecraft.client.renderer.shaders.uniform.providers.CapturedRenderingState;
import org.joml.Matrix4f;
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
    
    private static WorldRenderingPipeline activePipeline = null;
    private static final PhaseTracker phaseTracker = new PhaseTracker();
    private static final PipelineState pipelineState = new PipelineState();
    
    /**
     * Called at the start of world rendering.
     * IRIS: MixinLevelRenderer renderLevel() HEAD injection
     * 
     * @param modelView The model-view matrix
     * @param projection The projection matrix
     * @param tickDelta The tick delta (partial tick)
     */
    public static void onWorldRenderStart(Matrix4f modelView, Matrix4f projection, float tickDelta) {
        // Capture rendering state for uniforms (IRIS pattern)
        CapturedRenderingState.INSTANCE.setGbufferModelView(modelView);
        CapturedRenderingState.INSTANCE.setGbufferProjection(projection);
        CapturedRenderingState.INSTANCE.setTickDelta(tickDelta);
        
        // Get pipeline from PipelineManager (IRIS pattern: Iris.getPipelineManager().preparePipeline())
        ShaderSystem system = ShaderSystem.getInstance();
        
        if (system.isInitialized() && system.getPipelineManager() != null) {
            String currentDimension = getCurrentDimension();
            WorldRenderingPipeline pipeline = system.getPipelineManager().preparePipeline(currentDimension);
            
            if (pipeline != null && !(pipeline instanceof VanillaRenderingPipeline)) {
                activePipeline = pipeline;
                
                // Begin level rendering (IRIS pattern)
                pipeline.beginLevelRendering();
                pipeline.setPhase(WorldRenderingPhase.NONE);
                
                phaseTracker.beginWorldRendering();
                pipelineState.activate(pipeline);
            } else {
                activePipeline = null;
            }
        } else {
            activePipeline = null;
        }
    }
    
    /**
     * Gets the current dimension ID.
     * IRIS: Iris.getCurrentDimension() pattern
     */
    private static String getCurrentDimension() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            return mc.level.dimension().location().toString();
        }
        return "minecraft:overworld";
    }
    
    /**
     * Renders shadow maps before main world rendering.
     * IRIS: ShadowRenderer.renderShadows() pattern
     */
    private static void renderShadows() {
        if (activePipeline == null) {
            return;
        }
        
        // Cast to ShaderRenderingPipeline to access shadow renderer
        if (activePipeline instanceof ShaderRenderingPipeline shaderPipeline) {
            net.minecraft.client.renderer.shaders.shadows.ShadowRenderer shadowRenderer = 
                shaderPipeline.getShadowRenderer();
            
            if (shadowRenderer != null && shadowRenderer.areShadowsEnabled()) {
                // Begin shadow rendering
                shadowRenderer.beginShadowRender(0.0f);  // Sun angle would come from world
                
                // Shadow pass rendering happens through normal rendering callbacks
                // with the shadow state active
                shadowRenderer.renderShadowPass();
                
                // End shadow rendering and run composites
                shadowRenderer.endShadowRender();
            }
        }
    }
    
    /**
     * Called at the end of world rendering.
     * IRIS: MixinLevelRenderer renderLevel() RETURN injection
     */
    public static void onWorldRenderEnd() {
        if (activePipeline != null) {
            // Finalize level rendering (IRIS pattern)
            activePipeline.finalizeLevelRendering();
            
            pipelineState.deactivate();
            phaseTracker.endWorldRendering();
            
            LOGGER.debug("Shader pipeline finalized for rendering");
            
            // Clear active pipeline after frame is done
            activePipeline = null;
        }
    }
    
    /**
     * Called before terrain rendering begins.
     * IRIS: Before renderChunkLayer() calls
     */
    public static void onBeginTerrainRendering() {
        if (activePipeline != null) {
            phaseTracker.setPhase(WorldRenderingPhase.TERRAIN_SOLID);
            activePipeline.setPhase(WorldRenderingPhase.TERRAIN_SOLID);
            
            // Cast to ShaderRenderingPipeline to call terrain methods
            if (activePipeline instanceof ShaderRenderingPipeline shaderPipeline) {
                shaderPipeline.beginTerrainRendering();
            }
        }
    }
    
    /**
     * Called after terrain rendering ends.
     * IRIS: After renderChunkLayer() calls
     */
    public static void onEndTerrainRendering() {
        if (activePipeline != null) {
            // Cast to ShaderRenderingPipeline to call terrain methods
            if (activePipeline instanceof ShaderRenderingPipeline shaderPipeline) {
                shaderPipeline.endTerrainRendering();
            }
            
            phaseTracker.setPhase(WorldRenderingPhase.NONE);
            activePipeline.setPhase(WorldRenderingPhase.NONE);
        }
    }
    
    /**
     * Called before translucent rendering begins.
     * IRIS: Before translucent renderChunkLayer() call
     */
    public static void onBeginTranslucentRendering() {
        if (activePipeline != null) {
            phaseTracker.setPhase(WorldRenderingPhase.TERRAIN_TRANSLUCENT);
            activePipeline.setPhase(WorldRenderingPhase.TERRAIN_TRANSLUCENT);
            
            // Cast to ShaderRenderingPipeline to call translucent methods
            if (activePipeline instanceof ShaderRenderingPipeline shaderPipeline) {
                shaderPipeline.beginTranslucentRendering();
            }
        }
    }
    
    /**
     * Called after translucent rendering ends.
     * IRIS: After translucent renderChunkLayer() call
     */
    public static void onEndTranslucentRendering() {
        if (activePipeline != null) {
            // Cast to ShaderRenderingPipeline to call translucent methods
            if (activePipeline instanceof ShaderRenderingPipeline shaderPipeline) {
                shaderPipeline.endTranslucentRendering();
            }
            
            phaseTracker.setPhase(WorldRenderingPhase.NONE);
            activePipeline.setPhase(WorldRenderingPhase.NONE);
        }
    }
    
    /**
     * Sets the active shader pipeline.
     * Called by ShaderSystemLifecycle during initialization.
     */
    public static void setActivePipeline(WorldRenderingPipeline pipeline) {
        activePipeline = pipeline;
        LOGGER.debug("Active shader pipeline set: {}", pipeline != null ? "enabled" : "disabled");
    }
    
    /**
     * Gets the active shader pipeline.
     */
    public static WorldRenderingPipeline getActivePipeline() {
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
