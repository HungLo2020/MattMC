package net.minecraft.client.renderer.shaders;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manager for shader pipeline instances.
 * 
 * <p>This class manages the active shader pipeline and provides switching between
 * vanilla and Iris pipelines based on {@link ShaderRenderingConfig} settings.</p>
 * 
 * <p><b>Pipeline Selection Logic:</b>
 * <ul>
 *   <li>If {@link ShaderRenderingConfig#isShaderPacksEnabled()} returns true → Use Iris pipeline</li>
 *   <li>Otherwise → Use vanilla pipeline (default)</li>
 * </ul>
 * </p>
 * 
 * <p><b>Zero Regression Strategy:</b> Defaults to vanilla pipeline. Iris pipeline is only
 * activated when explicitly enabled via configuration.</p>
 * 
 * @since Iris Integration Step 2
 * @see ShaderPipeline
 * @see ShaderRenderingConfig
 */
public class ShaderPipelineManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShaderPipelineManager.class);
    
    private static ShaderPipeline activePipeline = null;
    private static ShaderPipeline vanillaPipeline = null;
    private static ShaderPipeline irisPipeline = null;
    
    private static boolean initialized = false;
    
    /**
     * Initializes the shader pipeline manager.
     * This should be called during game initialization.
     * 
     * @param vanilla the vanilla shader pipeline instance
     * @param iris the Iris shader pipeline instance (may be stub)
     */
    public static void initialize(ShaderPipeline vanilla, ShaderPipeline iris) {
        if (initialized) {
            LOGGER.warn("ShaderPipelineManager already initialized, reinitializing...");
            cleanup();
        }
        
        vanillaPipeline = vanilla;
        irisPipeline = iris;
        activePipeline = vanillaPipeline; // Default to vanilla
        
        LOGGER.info("Shader pipeline manager initialized - active pipeline: vanilla");
        initialized = true;
    }
    
    /**
     * Gets the currently active shader pipeline.
     * 
     * @return the active pipeline, or null if not initialized
     */
    public static ShaderPipeline getActivePipeline() {
        if (!initialized) {
            LOGGER.warn("ShaderPipelineManager not initialized, returning null");
            return null;
        }
        return activePipeline;
    }
    
    /**
     * Selects the appropriate pipeline based on current configuration.
     * This should be called each frame before rendering to ensure the correct pipeline is active.
     * 
     * <p>If shader packs are enabled, switches to Iris pipeline. Otherwise, uses vanilla pipeline.</p>
     */
    public static void selectPipeline() {
        if (!initialized) {
            LOGGER.error("Cannot select pipeline - manager not initialized");
            return;
        }
        
        boolean shouldUseIris = ShaderRenderingConfig.isShaderPacksEnabled();
        ShaderPipeline desired = shouldUseIris ? irisPipeline : vanillaPipeline;
        
        if (activePipeline != desired) {
            switchPipeline(desired);
        }
    }
    
    /**
     * Switches to a different shader pipeline.
     * 
     * <p>This method:
     * <ul>
     *   <li>Cleans up the current pipeline</li>
     *   <li>Activates the new pipeline</li>
     *   <li>Initializes the new pipeline</li>
     *   <li>Logs the switch</li>
     * </ul>
     * </p>
     * 
     * @param newPipeline the pipeline to switch to
     */
    public static void switchPipeline(ShaderPipeline newPipeline) {
        if (!initialized) {
            LOGGER.error("Cannot switch pipeline - manager not initialized");
            return;
        }
        
        if (newPipeline == null) {
            LOGGER.error("Cannot switch to null pipeline");
            return;
        }
        
        if (activePipeline == newPipeline) {
            LOGGER.debug("Pipeline already active, no switch needed");
            return;
        }
        
        String oldName = getPipelineName(activePipeline);
        String newName = getPipelineName(newPipeline);
        
        LOGGER.info("Switching shader pipeline: {} -> {}", oldName, newName);
        
        // Cleanup old pipeline
        if (activePipeline != null) {
            try {
                activePipeline.cleanup();
            } catch (Exception e) {
                LOGGER.error("Error cleaning up {} pipeline", oldName, e);
            }
        }
        
        // Activate new pipeline
        activePipeline = newPipeline;
        
        // Initialize new pipeline
        try {
            activePipeline.initialize();
            LOGGER.info("Successfully switched to {} pipeline", newName);
        } catch (Exception e) {
            LOGGER.error("Error initializing {} pipeline, reverting to vanilla", newName, e);
            activePipeline = vanillaPipeline;
            activePipeline.initialize();
        }
    }
    
    /**
     * Cleans up the shader pipeline manager.
     * This should be called during game shutdown.
     */
    public static void cleanup() {
        if (!initialized) {
            return;
        }
        
        LOGGER.info("Cleaning up shader pipeline manager");
        
        if (activePipeline != null) {
            try {
                activePipeline.cleanup();
            } catch (Exception e) {
                LOGGER.error("Error cleaning up active pipeline", e);
            }
        }
        
        activePipeline = null;
        vanillaPipeline = null;
        irisPipeline = null;
        initialized = false;
    }
    
    /**
     * Checks if the manager is initialized.
     * 
     * @return true if initialized
     */
    public static boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Gets a human-readable name for a pipeline.
     * 
     * @param pipeline the pipeline
     * @return the pipeline name
     */
    private static String getPipelineName(ShaderPipeline pipeline) {
        if (pipeline == null) {
            return "null";
        } else if (pipeline instanceof VanillaShaderPipeline) {
            return "vanilla";
        } else if (pipeline instanceof IrisShaderPipeline) {
            return "iris";
        } else {
            return "unknown";
        }
    }
}
