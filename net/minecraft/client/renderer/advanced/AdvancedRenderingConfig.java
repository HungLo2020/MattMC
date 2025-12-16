package net.minecraft.client.renderer.advanced;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/**
 * Configuration system for advanced rendering features.
 * 
 * <p>This class manages the toggle between vanilla and Sodium-optimized rendering paths.
 * The advanced rendering system is disabled by default to preserve vanilla behavior.</p>
 * 
 * <p><b>Implementation Note:</b> This is part of STEP7-8PLAN.md Step 1, creating the
 * foundation for switchable rendering paths during Sodium integration.</p>
 * 
 * @since Step 7-8 Integration
 */
public class AdvancedRenderingConfig {
    private static boolean enabled = false;
    
    /**
     * Checks if advanced rendering (Sodium-optimized paths) should be used.
     * 
     * <p>Returns true only if both:</p>
     * <ul>
     *   <li>The system flag is enabled</li>
     *   <li>The user option enableAdvancedRendering is true</li>
     * </ul>
     * 
     * @return true if advanced rendering should be active, false for vanilla rendering
     */
    public static boolean isEnabled() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.options == null) {
            return false;
        }
        
        Options options = minecraft.options;
        Boolean optionValue = options.enableAdvancedRendering().get();
        
        return enabled && (optionValue != null && optionValue);
    }
    
    /**
     * Sets the system-level enable flag for advanced rendering.
     * 
     * <p>This flag gates access to Sodium-optimized rendering. Even if set to true,
     * the user option must also be enabled for advanced rendering to activate.</p>
     * 
     * @param value true to allow advanced rendering, false to force vanilla
     */
    public static void setEnabled(boolean value) {
        enabled = value;
    }
    
    /**
     * Gets the current system-level enable flag.
     * 
     * @return true if system allows advanced rendering, false otherwise
     */
    public static boolean isSystemEnabled() {
        return enabled;
    }
}
