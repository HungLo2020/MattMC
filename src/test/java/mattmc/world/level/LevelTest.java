package mattmc.world.level;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Level class, specifically render distance functionality.
 */
public class LevelTest {
    
    @Test
    public void testDefaultRenderDistance() {
        // Create a new level and check default render distance
        Level level = new Level();
        int renderDistance = level.getRenderDistance();
        // Default should be 8 as set in Level constructor
        assertEquals(8, renderDistance, "Default render distance should be 8");
    }
    
    @Test
    public void testSetRenderDistance() {
        Level level = new Level();
        
        // Test setting render distance
        level.setRenderDistance(16);
        assertEquals(16, level.getRenderDistance(), "Render distance should be set to 16");
        
        level.setRenderDistance(4);
        assertEquals(4, level.getRenderDistance(), "Render distance should be set to 4");
    }
    
    @Test
    public void testRenderDistanceClamping() {
        Level level = new Level();
        
        // Test minimum clamping (should clamp to 2)
        level.setRenderDistance(1);
        assertEquals(2, level.getRenderDistance(), "Render distance below 2 should be clamped to 2");
        
        // Test maximum clamping (should clamp to 32)
        level.setRenderDistance(100);
        assertEquals(32, level.getRenderDistance(), "Render distance above 32 should be clamped to 32");
    }
    
    @Test
    public void testWorldGeneratorInitialization() {
        // Verify that world generator is properly initialized in constructor
        Level level = new Level();
        
        // This shouldn't throw any exceptions
        long seed = level.getSeed();
        assertEquals(0L, seed, "Default seed should be 0");
    }
    
    @Test
    public void testSetSeed() {
        Level level = new Level();
        
        // Test setting a new seed
        long newSeed = 12345L;
        level.setSeed(newSeed);
        assertEquals(newSeed, level.getSeed(), "Seed should be set to " + newSeed);
    }
}
