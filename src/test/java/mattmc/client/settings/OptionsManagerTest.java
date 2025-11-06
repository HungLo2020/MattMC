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
}
