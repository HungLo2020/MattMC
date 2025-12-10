package net.minecraft.client.renderer.shaders.lifecycle;

import net.minecraft.client.renderer.shaders.core.ShaderSystem;
import net.minecraft.client.renderer.shaders.gl.ShaderRenderSystem;
import net.minecraft.client.renderer.shaders.pipeline.ShaderRenderingPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the shader system lifecycle through various initialization phases.
 * 
 * Based on IRIS Iris.java lifecycle management pattern.
 * Reference: frnsrc/Iris-1.21.9/.../iris/Iris.java:777-810 (onEarlyInitialize)
 * Reference: frnsrc/Iris-1.21.9/.../iris/Iris.java:119-133 (onRenderSystemInit)
 * Reference: frnsrc/Iris-1.21.9/.../iris/Iris.java:154-171 (onLoadingComplete)
 */
public class ShaderSystemLifecycle {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShaderSystemLifecycle.class);
    
    private static ShaderSystemLifecycle instance;
    private static boolean earlyInitialized = false;
    private static boolean renderSystemInitialized = false;
    private static boolean resourcesLoaded = false;
    
    private ShaderRenderingPipeline activePipeline;
    
    private ShaderSystemLifecycle() {
    }
    
    /**
     * Gets the singleton instance of the shader system lifecycle manager.
     */
    public static ShaderSystemLifecycle getInstance() {
        if (instance == null) {
            instance = new ShaderSystemLifecycle();
        }
        return instance;
    }
    
    /**
     * Phase 1: Early initialization before Minecraft Options are created.
     * This creates the shader system singleton and loads configuration.
     * 
     * Based on IRIS Iris.onEarlyInitialize()
     * Reference: frnsrc/Iris-1.21.9/.../iris/Iris.java:777-810
     */
    public void onEarlyInitialize() {
        if (earlyInitialized) {
            LOGGER.warn("onEarlyInitialize called multiple times, skipping");
            return;
        }
        
        LOGGER.info("Shader system early initialization phase started");
        
        try {
            // Initialize shader system singleton
            ShaderSystem.getInstance();
            
            earlyInitialized = true;
            LOGGER.info("Shader system early initialization completed successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize shader system early", e);
            throw new RuntimeException("Shader system early initialization failed", e);
        }
    }
    
    /**
     * Phase 2: Render system initialization after OpenGL context is created.
     * This initializes OpenGL-dependent components.
     * 
     * Based on IRIS Iris.onRenderSystemInit()
     * Reference: frnsrc/Iris-1.21.9/.../iris/Iris.java:119-133
     */
    public void onRenderSystemInit() {
        if (!earlyInitialized) {
            LOGGER.warn("onRenderSystemInit called before onEarlyInitialize, forcing early init");
            onEarlyInitialize();
        }
        
        if (renderSystemInitialized) {
            LOGGER.warn("onRenderSystemInit called multiple times, skipping");
            return;
        }
        
        LOGGER.info("Shader system render system initialization phase started");
        
        try {
            // Initialize OpenGL-dependent components
            ShaderRenderSystem.initRenderer();
            
            renderSystemInitialized = true;
            LOGGER.info("Shader system render system initialization completed successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to initialize shader render system", e);
            throw new RuntimeException("Shader render system initialization failed", e);
        }
    }
    
    /**
     * Phase 3: Post-resource loading initialization after resources are loaded.
     * This discovers and loads shader packs from resources.
     * 
     * Based on IRIS Iris.onLoadingComplete()
     * Reference: frnsrc/Iris-1.21.9/.../iris/Iris.java:154-171
     */
    public void onResourcesLoaded() {
        if (!earlyInitialized) {
            LOGGER.warn("onResourcesLoaded called before onEarlyInitialize");
            return;
        }
        
        if (!renderSystemInitialized) {
            LOGGER.warn("onResourcesLoaded called before onRenderSystemInit");
            return;
        }
        
        if (resourcesLoaded) {
            LOGGER.debug("onResourcesLoaded called again (resource reload)");
        }
        
        LOGGER.info("Shader system resources loaded phase started");
        
        try {
            // Note: Shader pack initialization happens via onResourceManagerReady
            // which is called separately by Minecraft's resource system
            // This phase just marks that resources are available
            
            resourcesLoaded = true;
            LOGGER.info("Shader system resources loaded phase completed successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to load shader system resources", e);
            // Don't throw here, allow game to continue without shaders
        }
    }
    
    /**
     * Called at the start of world rendering each frame.
     * Prepares the active pipeline for rendering.
     */
    public void onWorldRenderStart() {
        if (activePipeline != null && activePipeline.isActive()) {
            activePipeline.beginWorldRendering();
        }
    }
    
    /**
     * Called at the end of world rendering each frame.
     * Finalizes the active pipeline and executes post-processing.
     */
    public void onWorldRenderEnd() {
        if (activePipeline != null && activePipeline.isActive()) {
            activePipeline.finishWorldRendering();
        }
    }
    
    /**
     * Sets the active rendering pipeline.
     * 
     * @param pipeline The pipeline to activate, or null to deactivate
     */
    public void setActivePipeline(ShaderRenderingPipeline pipeline) {
        if (this.activePipeline != null && this.activePipeline != pipeline) {
            this.activePipeline.destroy();
        }
        this.activePipeline = pipeline;
        LOGGER.info("Active shader pipeline changed: {}", 
            pipeline != null ? pipeline.getClass().getSimpleName() : "null");
    }
    
    /**
     * Gets the currently active rendering pipeline.
     * 
     * @return The active pipeline, or null if none is active
     */
    public ShaderRenderingPipeline getActivePipeline() {
        return activePipeline;
    }
    
    /**
     * Returns whether early initialization has completed.
     */
    public static boolean isEarlyInitialized() {
        return earlyInitialized;
    }
    
    /**
     * Returns whether render system initialization has completed.
     */
    public static boolean isRenderSystemInitialized() {
        return renderSystemInitialized;
    }
    
    /**
     * Returns whether resources have been loaded.
     */
    public static boolean isResourcesLoaded() {
        return resourcesLoaded;
    }
}
