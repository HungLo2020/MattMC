package mattmc.world.level.lighting;

import mattmc.registries.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify torch light removal works correctly.
 */
public class TorchRemovalTest {
    
    private WorldLightManager worldLightManager;
    
    @BeforeEach
    public void setup() {
        worldLightManager = new WorldLightManager();
    }
    
    @Test
    public void testSimpleTorchRemoval() {
        LevelChunk chunk = new LevelChunk(0, 0);
        chunk.setWorldLightManager(worldLightManager);
        
        // Place a torch
        chunk.setBlock(8, 100, 8, Blocks.TORCH);
        
        System.out.println("After placing torch:");
        System.out.println("  At torch: R=" + chunk.getBlockLightR(8, 100, 8) + 
                           " G=" + chunk.getBlockLightG(8, 100, 8) + 
                           " B=" + chunk.getBlockLightB(8, 100, 8));
        System.out.println("  Neighbor: R=" + chunk.getBlockLightR(9, 100, 8) + 
                           " G=" + chunk.getBlockLightG(9, 100, 8) + 
                           " B=" + chunk.getBlockLightB(9, 100, 8));
        
        // Verify torch emits light
        assertTrue(chunk.getBlockLightR(8, 100, 8) > 0, "Torch should emit red light");
        assertTrue(chunk.getBlockLightG(8, 100, 8) > 0, "Torch should emit green light");
        
        // Remove the torch
        chunk.setBlock(8, 100, 8, Blocks.AIR);
        
        System.out.println("\nAfter removing torch:");
        System.out.println("  At torch: R=" + chunk.getBlockLightR(8, 100, 8) + 
                           " G=" + chunk.getBlockLightG(8, 100, 8) + 
                           " B=" + chunk.getBlockLightB(8, 100, 8));
        System.out.println("  Neighbor: R=" + chunk.getBlockLightR(9, 100, 8) + 
                           " G=" + chunk.getBlockLightG(9, 100, 8) + 
                           " B=" + chunk.getBlockLightB(9, 100, 8));
        
        // Verify light is completely removed
        assertEquals(0, chunk.getBlockLightR(8, 100, 8), "Red light should be removed");
        assertEquals(0, chunk.getBlockLightG(8, 100, 8), "Green light should be removed");
        assertEquals(0, chunk.getBlockLightB(8, 100, 8), "Blue light should be removed");
        
        // Verify neighbor light is also removed
        assertEquals(0, chunk.getBlockLightR(9, 100, 8), "Red light should be removed from neighbor");
        assertEquals(0, chunk.getBlockLightG(9, 100, 8), "Green light should be removed from neighbor");
        assertEquals(0, chunk.getBlockLightB(9, 100, 8), "Blue light should be removed from neighbor");
    }
}
