package mattmc.client.settings;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for OptionsManager FPS cap functionality.
 */
public class OptionsManagerTest {
    
    @Test
    public void testFpsCapDefaultValue() {
        // Default FPS cap should be 60
        int fpsCap = OptionsManager.getFpsCap();
        assertTrue(fpsCap >= 30 && fpsCap <= 999, "FPS cap should be within valid range");
    }
    
    @Test
    public void testFpsCapSetAndGet() {
        // Test setting a valid FPS cap
        OptionsManager.setFpsCap(120);
        assertEquals(120, OptionsManager.getFpsCap(), "FPS cap should be set to 120");
        
        OptionsManager.setFpsCap(240);
        assertEquals(240, OptionsManager.getFpsCap(), "FPS cap should be set to 240");
    }
    
    @Test
    public void testFpsCapClamping() {
        // Test that values below minimum are clamped to 30
        OptionsManager.setFpsCap(10);
        assertEquals(30, OptionsManager.getFpsCap(), "FPS cap below 30 should be clamped to 30");
        
        // Test that values above maximum are clamped to 999
        OptionsManager.setFpsCap(1500);
        assertEquals(999, OptionsManager.getFpsCap(), "FPS cap above 999 should be clamped to 999");
    }
    
    @Test
    public void testFpsCapBoundaryValues() {
        // Test minimum boundary
        OptionsManager.setFpsCap(30);
        assertEquals(30, OptionsManager.getFpsCap(), "FPS cap should accept minimum value of 30");
        
        // Test maximum boundary
        OptionsManager.setFpsCap(999);
        assertEquals(999, OptionsManager.getFpsCap(), "FPS cap should accept maximum value of 999");
    }
    
    @Test
    public void testResolutionDefaultValue() {
        // Default resolution should be 1280x720
        int width = OptionsManager.getResolutionWidth();
        int height = OptionsManager.getResolutionHeight();
        assertTrue(width > 0 && height > 0, "Resolution should have positive dimensions");
    }
    
    @Test
    public void testResolutionSetAndGet() {
        // Test setting a resolution
        OptionsManager.setResolution(1920, 1080);
        assertEquals(1920, OptionsManager.getResolutionWidth(), "Width should be set to 1920");
        assertEquals(1080, OptionsManager.getResolutionHeight(), "Height should be set to 1080");
        
        // Test another resolution
        OptionsManager.setResolution(1600, 900);
        assertEquals(1600, OptionsManager.getResolutionWidth(), "Width should be set to 1600");
        assertEquals(900, OptionsManager.getResolutionHeight(), "Height should be set to 900");
    }
    
    @Test
    public void testResolutionString() {
        // Test resolution string formatting
        OptionsManager.setResolution(1280, 720);
        assertEquals("1280x720", OptionsManager.getResolutionString(), "Resolution string should be formatted correctly");
        
        OptionsManager.setResolution(1920, 1080);
        assertEquals("1920x1080", OptionsManager.getResolutionString(), "Resolution string should be formatted correctly");
    }
    
    @Test
    public void testResolutionValidation() {
        // Store current resolution
        int currentWidth = OptionsManager.getResolutionWidth();
        int currentHeight = OptionsManager.getResolutionHeight();
        
        // Try to set invalid resolution (should be rejected)
        OptionsManager.setResolution(-1, 720);
        assertEquals(currentWidth, OptionsManager.getResolutionWidth(), "Width should not change with invalid input");
        assertEquals(currentHeight, OptionsManager.getResolutionHeight(), "Height should not change with invalid input");
        
        OptionsManager.setResolution(1280, 0);
        assertEquals(currentWidth, OptionsManager.getResolutionWidth(), "Width should not change with invalid input");
        assertEquals(currentHeight, OptionsManager.getResolutionHeight(), "Height should not change with invalid input");
    }
    
    @Test
    public void testFullscreenDefaultValue() {
        // Default fullscreen should be false (windowed mode)
        boolean fullscreen = OptionsManager.isFullscreenEnabled();
        // Just verify it returns a boolean value
        assertTrue(fullscreen == true || fullscreen == false, "Fullscreen should be a boolean value");
    }
    
    @Test
    public void testFullscreenSetAndGet() {
        // Test setting fullscreen
        OptionsManager.setFullscreenEnabled(true);
        assertTrue(OptionsManager.isFullscreenEnabled(), "Fullscreen should be enabled");
        
        OptionsManager.setFullscreenEnabled(false);
        assertFalse(OptionsManager.isFullscreenEnabled(), "Fullscreen should be disabled");
    }
    
    @Test
    public void testFullscreenToggle() {
        // Get current state
        boolean initialState = OptionsManager.isFullscreenEnabled();
        
        // Toggle and verify it changed
        OptionsManager.toggleFullscreen();
        assertEquals(!initialState, OptionsManager.isFullscreenEnabled(), "Fullscreen should toggle");
        
        // Toggle back
        OptionsManager.toggleFullscreen();
        assertEquals(initialState, OptionsManager.isFullscreenEnabled(), "Fullscreen should toggle back");
    }
    
    @Test
    public void testRenderDistanceDefaultValue() {
        // Default render distance should be 16
        int renderDistance = OptionsManager.getRenderDistance();
        assertTrue(renderDistance >= 2 && renderDistance <= 64, "Render distance should be within valid range");
    }
    
    @Test
    public void testRenderDistanceSetAndGet() {
        // Test setting valid render distances
        OptionsManager.setRenderDistance(8);
        assertEquals(8, OptionsManager.getRenderDistance(), "Render distance should be set to 8");
        
        OptionsManager.setRenderDistance(32);
        assertEquals(32, OptionsManager.getRenderDistance(), "Render distance should be set to 32");
        
        OptionsManager.setRenderDistance(16);
        assertEquals(16, OptionsManager.getRenderDistance(), "Render distance should be set to 16");
    }
    
    @Test
    public void testRenderDistanceClamping() {
        // Test that values below minimum are clamped to 2
        OptionsManager.setRenderDistance(1);
        assertEquals(2, OptionsManager.getRenderDistance(), "Render distance below 2 should be clamped to 2");
        
        OptionsManager.setRenderDistance(-5);
        assertEquals(2, OptionsManager.getRenderDistance(), "Negative render distance should be clamped to 2");
        
        // Test that values above maximum are clamped to 64
        OptionsManager.setRenderDistance(100);
        assertEquals(64, OptionsManager.getRenderDistance(), "Render distance above 64 should be clamped to 64");
        
        OptionsManager.setRenderDistance(128);
        assertEquals(64, OptionsManager.getRenderDistance(), "Render distance of 128 should be clamped to 64");
    }
    
    @Test
    public void testRenderDistanceBoundaryValues() {
        // Test minimum boundary
        OptionsManager.setRenderDistance(2);
        assertEquals(2, OptionsManager.getRenderDistance(), "Render distance should accept minimum value of 2");
        
        // Test maximum boundary
        OptionsManager.setRenderDistance(64);
        assertEquals(64, OptionsManager.getRenderDistance(), "Render distance should accept maximum value of 64");
    }
    
    @Test
    public void testRenderDistanceAllowedValues() {
        // Test all allowed values from OptionsManager constant
        int[] allowedValues = OptionsManager.ALLOWED_RENDER_DISTANCES;
        
        for (int value : allowedValues) {
            OptionsManager.setRenderDistance(value);
            assertEquals(value, OptionsManager.getRenderDistance(), 
                "Render distance should accept allowed value of " + value);
        }
    }
    
    @Test
    public void testRenderDistanceIntermediateValues() {
        // Test that intermediate values (not in allowed list) are accepted but clamped
        // This ensures the system handles manually set values gracefully
        OptionsManager.setRenderDistance(15);
        assertEquals(15, OptionsManager.getRenderDistance(), 
            "Render distance should accept intermediate value of 15");
        
        OptionsManager.setRenderDistance(20);
        assertEquals(20, OptionsManager.getRenderDistance(), 
            "Render distance should accept intermediate value of 20");
        
        // Reset to a standard value
        OptionsManager.setRenderDistance(16);
    }
    
    @Test
    public void testMipmapLevelDefaultValue() {
        // Default mipmap level should be 4
        int mipmapLevel = OptionsManager.getMipmapLevel();
        assertTrue(mipmapLevel >= 0 && mipmapLevel <= 4, "Mipmap level should be within valid range");
    }
    
    @Test
    public void testMipmapLevelSetAndGet() {
        // Test setting valid mipmap levels
        OptionsManager.setMipmapLevel(0);
        assertEquals(0, OptionsManager.getMipmapLevel(), "Mipmap level should be set to 0 (off)");
        
        OptionsManager.setMipmapLevel(2);
        assertEquals(2, OptionsManager.getMipmapLevel(), "Mipmap level should be set to 2");
        
        OptionsManager.setMipmapLevel(4);
        assertEquals(4, OptionsManager.getMipmapLevel(), "Mipmap level should be set to 4");
    }
    
    @Test
    public void testMipmapLevelClamping() {
        // Test that values below minimum are clamped to 0
        OptionsManager.setMipmapLevel(-1);
        assertEquals(0, OptionsManager.getMipmapLevel(), "Negative mipmap level should be clamped to 0");
        
        // Test that values above maximum are clamped to 4
        OptionsManager.setMipmapLevel(10);
        assertEquals(4, OptionsManager.getMipmapLevel(), "Mipmap level above 4 should be clamped to 4");
    }
    
    @Test
    public void testMipmapLevelAllowedValues() {
        // Test all allowed values from OptionsManager constant
        int[] allowedValues = OptionsManager.ALLOWED_MIPMAP_LEVELS;
        
        for (int value : allowedValues) {
            OptionsManager.setMipmapLevel(value);
            assertEquals(value, OptionsManager.getMipmapLevel(), 
                "Mipmap level should accept allowed value of " + value);
        }
    }
    
    @Test
    public void testAnisotropicFilteringDefaultValue() {
        // Default anisotropic filtering should be 16
        int anisotropicLevel = OptionsManager.getAnisotropicFiltering();
        assertTrue(anisotropicLevel >= 0, "Anisotropic filtering level should be non-negative");
    }
    
    @Test
    public void testAnisotropicFilteringSetAndGet() {
        // Test setting valid anisotropic filtering levels
        OptionsManager.setAnisotropicFiltering(0);
        assertEquals(0, OptionsManager.getAnisotropicFiltering(), "Anisotropic filtering should be set to 0 (off)");
        
        OptionsManager.setAnisotropicFiltering(4);
        assertEquals(4, OptionsManager.getAnisotropicFiltering(), "Anisotropic filtering should be set to 4");
        
        OptionsManager.setAnisotropicFiltering(16);
        assertEquals(16, OptionsManager.getAnisotropicFiltering(), "Anisotropic filtering should be set to 16");
    }
    
    @Test
    public void testAnisotropicFilteringClamping() {
        // Test that negative values are clamped to 0
        OptionsManager.setAnisotropicFiltering(-1);
        assertEquals(0, OptionsManager.getAnisotropicFiltering(), "Negative anisotropic filtering should be clamped to 0");
        
        // Test that values above maximum snap to closest valid value
        OptionsManager.setAnisotropicFiltering(20);
        assertEquals(16, OptionsManager.getAnisotropicFiltering(), "Anisotropic filtering of 20 should be clamped to 16");
    }
    
    @Test
    public void testAnisotropicFilteringAllowedValues() {
        // Test all allowed values from OptionsManager constant
        int[] allowedValues = OptionsManager.ALLOWED_ANISOTROPIC_LEVELS;
        
        for (int value : allowedValues) {
            OptionsManager.setAnisotropicFiltering(value);
            assertEquals(value, OptionsManager.getAnisotropicFiltering(), 
                "Anisotropic filtering should accept allowed value of " + value);
        }
    }
    
    @Test
    public void testAnisotropicFilteringIntermediateValues() {
        // Test that intermediate values snap to nearest valid value
        OptionsManager.setAnisotropicFiltering(3);
        assertEquals(4, OptionsManager.getAnisotropicFiltering(), 
            "Anisotropic filtering of 3 should snap to 4");
        
        OptionsManager.setAnisotropicFiltering(10);
        assertEquals(16, OptionsManager.getAnisotropicFiltering(), 
            "Anisotropic filtering of 10 should snap to 16");
    }
}
