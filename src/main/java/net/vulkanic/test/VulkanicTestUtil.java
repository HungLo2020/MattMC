package net.vulkanic.test;

import net.vulkanic.BackendType;
import net.vulkanic.Vulkanic;
import net.vulkanic.VulkanicDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple test utility to verify Vulkanic is working correctly.
 * 
 * This class provides methods to test the Vulkanic rendering abstraction layer
 * without requiring complex rendering setup.
 */
public class VulkanicTestUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(VulkanicTestUtil.class);
    
    /**
     * Tests basic Vulkanic initialization and device info.
     * This should be called after RenderSystem is initialized.
     * 
     * @return true if Vulkanic is working correctly
     */
    public static boolean testVulkanicBasics() {
        try {
            if (!Vulkanic.isInitialized()) {
                LOGGER.warn("Vulkanic is not initialized");
                return false;
            }
            
            VulkanicDevice device = Vulkanic.getDevice();
            
            // Log device information
            LOGGER.info("=== Vulkanic Device Information ===");
            LOGGER.info("Backend Type: {}", device.getBackendType().getDisplayName());
            LOGGER.info("Backend Name: {}", device.getBackendName());
            LOGGER.info("Vendor: {}", device.getVendor());
            LOGGER.info("Renderer: {}", device.getRenderer());
            LOGGER.info("Max Texture Size: {}", device.getMaxTextureSize());
            LOGGER.info("===================================");
            
            return true;
        } catch (Exception e) {
            LOGGER.error("Vulkanic test failed", e);
            return false;
        }
    }
    
    /**
     * Tests creating and destroying Vulkanic resources.
     * 
     * @return true if resource management works correctly
     */
    public static boolean testVulkanicResources() {
        try {
            if (!Vulkanic.isInitialized()) {
                LOGGER.warn("Vulkanic is not initialized");
                return false;
            }
            
            VulkanicDevice device = Vulkanic.getDevice();
            
            LOGGER.info("Testing Vulkanic resource creation...");
            
            // Test buffer creation
            var buffer = device.createBuffer(1024);
            LOGGER.info("Created buffer: {} bytes", buffer.getSize());
            buffer.close();
            LOGGER.info("Closed buffer successfully");
            
            // Test texture creation
            var texture = device.createTexture(256, 256);
            LOGGER.info("Created texture: {}x{}", texture.getWidth(), texture.getHeight());
            texture.close();
            LOGGER.info("Closed texture successfully");
            
            // Test framebuffer creation
            var framebuffer = device.createFramebuffer(800, 600);
            LOGGER.info("Created framebuffer: {}x{}", framebuffer.getWidth(), framebuffer.getHeight());
            framebuffer.close();
            LOGGER.info("Closed framebuffer successfully");
            
            LOGGER.info("All Vulkanic resource tests passed!");
            return true;
        } catch (Exception e) {
            LOGGER.error("Vulkanic resource test failed", e);
            return false;
        }
    }
    
    /**
     * Runs all Vulkanic tests.
     */
    public static void runAllTests() {
        LOGGER.info("Running Vulkanic integration tests...");
        
        boolean basicsPass = testVulkanicBasics();
        boolean resourcesPass = testVulkanicResources();
        
        if (basicsPass && resourcesPass) {
            LOGGER.info("✅ All Vulkanic tests PASSED");
        } else {
            LOGGER.error("❌ Some Vulkanic tests FAILED - basics: {}, resources: {}", 
                basicsPass, resourcesPass);
        }
    }
}
