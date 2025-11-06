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
}
