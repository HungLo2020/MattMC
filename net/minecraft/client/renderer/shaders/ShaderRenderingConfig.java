package net.minecraft.client.renderer.shaders;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration system for Iris shader rendering features.
 * Provides centralized control for shader pack loading, shadow rendering, and post-processing effects.
 * 
 * <p>This class follows the pattern established by AdvancedRenderingConfig for Sodium integration,
 * providing feature flags that can be toggled without restarting the game.</p>
 * 
 * <p><b>Zero Regression Strategy:</b> All features disabled by default. Vanilla rendering is preserved
 * until shader features are explicitly enabled.</p>
 * 
 * @see net.minecraft.client.renderer.advanced.AdvancedRenderingConfig
 */
public class ShaderRenderingConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShaderRenderingConfig.class);
    
    // Internal feature flags - can be toggled programmatically
    private static boolean enableShaderPacksFlag = false;
    private static boolean enableShadowsFlag = false;
    private static boolean enablePostProcessingFlag = false;
    
    /**
     * Checks if shader packs are enabled.
     * This checks both the internal flag and the user's Options setting.
     * 
     * @return true if shader packs should be loaded and used for rendering
     */
    public static boolean isShaderPacksEnabled() {
        Options options = getOptions();
        if (options == null) {
            return false;
        }
        
        // Check both internal flag and Options setting
        boolean optionEnabled = options.enableShaderPacks().get();
        boolean result = enableShaderPacksFlag && optionEnabled;
        
        return result;
    }
    
    /**
     * Checks if shadow rendering is enabled.
     * This checks both the internal flag and whether the current shader pack supports shadows.
     * 
     * @return true if shadow passes should be rendered
     */
    public static boolean isShadowsEnabled() {
        Options options = getOptions();
        if (options == null) {
            return false;
        }
        
        // Check internal flag, Options setting, and shader pack capability
        boolean optionEnabled = options.enableShadows().get();
        boolean result = enableShadowsFlag && optionEnabled;
        
        // TODO: In future steps, also check if loaded shader pack supports shadows
        // result = result && ShaderPackManager.currentPackSupportsShadows();
        
        return result;
    }
    
    /**
     * Checks if post-processing effects are enabled.
     * This checks both the internal flag and whether the current shader pack supports post-processing.
     * 
     * @return true if post-processing passes should be rendered
     */
    public static boolean isPostProcessingEnabled() {
        Options options = getOptions();
        if (options == null) {
            return false;
        }
        
        // Check internal flag, Options setting, and shader pack capability
        boolean optionEnabled = options.enablePostProcessing().get();
        boolean result = enablePostProcessingFlag && optionEnabled;
        
        // TODO: In future steps, also check if loaded shader pack supports post-processing
        // result = result && ShaderPackManager.currentPackSupportsPostProcessing();
        
        return result;
    }
    
    /**
     * Sets whether shader packs should be enabled.
     * This is for programmatic control of the feature flag.
     * 
     * @param enabled true to enable shader packs
     */
    public static void setShaderPacksEnabled(boolean enabled) {
        if (enableShaderPacksFlag != enabled) {
            LOGGER.info("Shader packs feature flag changed: {} -> {}", enableShaderPacksFlag, enabled);
            enableShaderPacksFlag = enabled;
        }
    }
    
    /**
     * Sets whether shadow rendering should be enabled.
     * This is for programmatic control of the feature flag.
     * 
     * @param enabled true to enable shadows
     */
    public static void setShadowsEnabled(boolean enabled) {
        if (enableShadowsFlag != enabled) {
            LOGGER.info("Shadows feature flag changed: {} -> {}", enableShadowsFlag, enabled);
            enableShadowsFlag = enabled;
        }
    }
    
    /**
     * Sets whether post-processing should be enabled.
     * This is for programmatic control of the feature flag.
     * 
     * @param enabled true to enable post-processing
     */
    public static void setPostProcessingEnabled(boolean enabled) {
        if (enablePostProcessingFlag != enabled) {
            LOGGER.info("Post-processing feature flag changed: {} -> {}", enablePostProcessingFlag, enabled);
            enablePostProcessingFlag = enabled;
        }
    }
    
    /**
     * Gets the current Minecraft Options instance.
     * 
     * @return the Options instance, or null if Minecraft is not initialized
     */
    private static Options getOptions() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null ? minecraft.options : null;
    }
    
    /**
     * Initializes the shader rendering configuration.
     * This should be called during game initialization.
     */
    public static void initialize() {
        LOGGER.info("Initializing Iris shader rendering configuration");
        
        // All flags default to false for zero regression
        enableShaderPacksFlag = false;
        enableShadowsFlag = false;
        enablePostProcessingFlag = false;
        
        LOGGER.info("Shader rendering configuration initialized - all features disabled by default");
    }
}
