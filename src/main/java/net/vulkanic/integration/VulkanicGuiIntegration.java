package net.vulkanic.integration;

import net.vulkanic.Vulkanic;
import net.vulkanic.VulkanicCommandBuffer;
import net.vulkanic.VulkanicDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration layer that routes actual game rendering through Vulkanic.
 * 
 * This class wraps common rendering operations and routes them through
 * the Vulkanic abstraction layer instead of direct OpenGL calls.
 */
public class VulkanicGuiIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger(VulkanicGuiIntegration.class);
    
    private static boolean enabled = false;
    private static long clearCallCount = 0;
    private static long viewportCallCount = 0;
    
    /**
     * Enables Vulkanic integration for GUI rendering.
     */
    public static void enable() {
        if (!Vulkanic.isInitialized()) {
            LOGGER.warn("Cannot enable Vulkanic GUI integration: Vulkanic not initialized");
            return;
        }
        
        enabled = true;
        LOGGER.info("✅ Vulkanic GUI integration ENABLED - Game rendering now using Vulkanic abstraction layer");
    }
    
    /**
     * Disables Vulkanic integration.
     */
    public static void disable() {
        enabled = false;
        LOGGER.info("Vulkanic GUI integration disabled");
    }
    
    /**
     * Checks if Vulkanic integration is enabled.
     */
    public static boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Wraps a clear operation to go through Vulkanic.
     * This is called from actual game code instead of direct GL calls.
     * 
     * @param r Red component (0.0-1.0)
     * @param g Green component (0.0-1.0)
     * @param b Blue component (0.0-1.0)
     * @param a Alpha component (0.0-1.0)
     */
    public static void clearColor(float r, float g, float b, float a) {
        if (!enabled) {
            return; // Let normal rendering path handle it
        }
        
        try {
            VulkanicDevice device = Vulkanic.getDevice();
            VulkanicCommandBuffer cmd = device.createCommandBuffer();
            
            // Route clear through Vulkanic instead of direct OpenGL
            cmd.clear(r, g, b, a);
            cmd.submit();
            
            clearCallCount++;
            
            if (clearCallCount % 60 == 0) {
                LOGGER.info("🎨 Vulkanic clear call #{} - Color: ({}, {}, {}, {})", 
                    clearCallCount, r, g, b, a);
            }
        } catch (Exception e) {
            LOGGER.error("Error in Vulkanic clear operation", e);
        }
    }
    
    /**
     * Wraps a viewport operation to go through Vulkanic.
     * 
     * @param x X offset
     * @param y Y offset
     * @param width Width
     * @param height Height
     */
    public static void setViewport(int x, int y, int width, int height) {
        if (!enabled) {
            return;
        }
        
        try {
            VulkanicDevice device = Vulkanic.getDevice();
            VulkanicCommandBuffer cmd = device.createCommandBuffer();
            
            // Route viewport through Vulkanic
            cmd.setViewport(x, y, width, height);
            cmd.submit();
            
            viewportCallCount++;
            
            if (viewportCallCount <= 5) {
                LOGGER.info("📐 Vulkanic viewport #{}: {}x{} at ({}, {})", 
                    viewportCallCount, width, height, x, y);
            }
        } catch (Exception e) {
            LOGGER.error("Error in Vulkanic viewport operation", e);
        }
    }
    
    /**
     * Logs statistics about Vulkanic usage.
     */
    public static void logStats() {
        if (!enabled) {
            LOGGER.info("Vulkanic GUI integration: DISABLED");
            return;
        }
        
        LOGGER.info("=== Vulkanic GUI Integration Stats ===");
        LOGGER.info("Status: ENABLED");
        LOGGER.info("Clear calls routed through Vulkanic: {}", clearCallCount);
        LOGGER.info("Viewport calls routed through Vulkanic: {}", viewportCallCount);
        LOGGER.info("Game rendering using: Vulkanic abstraction layer");
        LOGGER.info("OpenGL backend: Active");
        LOGGER.info("======================================");
    }
    
    /**
     * Resets statistics.
     */
    public static void resetStats() {
        clearCallCount = 0;
        viewportCallCount = 0;
    }
}
