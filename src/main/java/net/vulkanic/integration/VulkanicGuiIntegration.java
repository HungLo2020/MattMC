package net.vulkanic.integration;

import net.vulkanic.Vulkanic;
import net.vulkanic.VulkanicCommandBuffer;
import net.vulkanic.VulkanicDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration layer that routes actual game rendering through Vulkanic.
 */
public class VulkanicGuiIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger(VulkanicGuiIntegration.class);
    
    private static boolean enabled = false;
    
    public static void enable() {
        if (!Vulkanic.isInitialized()) {
            LOGGER.error("Cannot enable Vulkanic: not initialized");
            return;
        }
        enabled = true;
    }
    
    public static void disable() {
        enabled = false;
    }
    
    public static boolean isEnabled() {
        return enabled;
    }
    
    public static void clearColor(float r, float g, float b, float a) {
        if (!enabled) return;
        
        try {
            VulkanicDevice device = Vulkanic.getDevice();
            VulkanicCommandBuffer cmd = device.createCommandBuffer();
            cmd.clear(r, g, b, a);
            cmd.submit();
        } catch (Exception e) {
            LOGGER.error("Vulkanic clear failed", e);
        }
    }
    
    public static void clearDepth(float depth) {
        if (!enabled) return;
        
        try {
            VulkanicDevice device = Vulkanic.getDevice();
            VulkanicCommandBuffer cmd = device.createCommandBuffer();
            cmd.clearDepth(depth);
            cmd.submit();
        } catch (Exception e) {
            LOGGER.error("Vulkanic clearDepth failed", e);
        }
    }
    
    public static void setViewport(int x, int y, int width, int height) {
        if (!enabled) return;
        
        try {
            VulkanicDevice device = Vulkanic.getDevice();
            VulkanicCommandBuffer cmd = device.createCommandBuffer();
            cmd.setViewport(x, y, width, height);
            cmd.submit();
        } catch (Exception e) {
            LOGGER.error("Vulkanic viewport failed", e);
        }
    }
}
