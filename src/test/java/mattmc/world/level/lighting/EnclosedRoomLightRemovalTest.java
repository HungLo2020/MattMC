package mattmc.world.level.lighting;

import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test light removal in an enclosed room scenario as reported by the user.
 * When a block light is placed in a perfectly dark enclosed room and then removed,
 * the light should completely go away.
 */
public class EnclosedRoomLightRemovalTest {
    
    private WorldLightManager worldLightManager;
    
    @BeforeEach
    public void setup() {
        worldLightManager = new WorldLightManager();
    }
    
    @Test
    public void testLightRemovalInEnclosedRoom() {
        LevelChunk chunk = new LevelChunk(0, 0);
        chunk.setWorldLightManager(worldLightManager);
        
        // Create an enclosed room (5x5x5) with walls
        // Floor at Y=64, ceiling at Y=68, walls around
        int floorY = LevelChunk.worldYToChunkY(64);
        int ceilingY = LevelChunk.worldYToChunkY(68);
        
        // Build floor and ceiling
        for (int x = 5; x <= 10; x++) {
            for (int z = 5; z <= 10; z++) {
                chunk.setBlock(x, floorY, z, Blocks.STONE);
                chunk.setBlock(x, ceilingY, z, Blocks.STONE);
            }
        }
        
        // Build walls
        for (int y = floorY + 1; y < ceilingY; y++) {
            for (int x = 5; x <= 10; x++) {
                chunk.setBlock(x, y, 5, Blocks.STONE); // North wall
                chunk.setBlock(x, y, 10, Blocks.STONE); // South wall
            }
            for (int z = 6; z <= 9; z++) {
                chunk.setBlock(5, y, z, Blocks.STONE); // West wall
                chunk.setBlock(10, y, z, Blocks.STONE); // East wall
            }
        }
        
        // Place a torch in the center of the room
        int centerY = floorY + 2;
        chunk.setBlock(7, centerY, 7, Blocks.TORCH);
        
        System.out.println("=== After placing torch in enclosed room ===");
        
        // Check light at torch position and nearby
        int torchR = chunk.getBlockLightR(7, centerY, 7);
        int torchG = chunk.getBlockLightG(7, centerY, 7);
        int torchB = chunk.getBlockLightB(7, centerY, 7);
        
        System.out.println("At torch (7," + (centerY + LevelChunk.MIN_Y) + ",7): R=" + torchR + " G=" + torchG + " B=" + torchB);
        
        // Check neighbors
        int neighbor1R = chunk.getBlockLightR(8, centerY, 7);
        int neighbor1G = chunk.getBlockLightG(8, centerY, 7);
        int neighbor1B = chunk.getBlockLightB(8, centerY, 7);
        
        System.out.println("At neighbor (8," + (centerY + LevelChunk.MIN_Y) + ",7): R=" + neighbor1R + " G=" + neighbor1G + " B=" + neighbor1B);
        
        // Verify torch emits light
        assertTrue(torchR > 0 || torchG > 0 || torchB > 0, "Torch should emit light");
        assertTrue(neighbor1R > 0 || neighbor1G > 0 || neighbor1B > 0, "Neighbor should receive light");
        
        // Now remove the torch
        System.out.println("\n=== Removing torch ===");
        chunk.setBlock(7, centerY, 7, Blocks.AIR);
        
        System.out.println("\n=== After removing torch ===");
        
        // Check if light is removed at torch position
        int afterRemovalR = chunk.getBlockLightR(7, centerY, 7);
        int afterRemovalG = chunk.getBlockLightG(7, centerY, 7);
        int afterRemovalB = chunk.getBlockLightB(7, centerY, 7);
        
        System.out.println("At former torch (7," + (centerY + LevelChunk.MIN_Y) + ",7): R=" + afterRemovalR + " G=" + afterRemovalG + " B=" + afterRemovalB);
        
        // Check neighbors
        int neighbor1AfterR = chunk.getBlockLightR(8, centerY, 7);
        int neighbor1AfterG = chunk.getBlockLightG(8, centerY, 7);
        int neighbor1AfterB = chunk.getBlockLightB(8, centerY, 7);
        
        System.out.println("At neighbor (8," + (centerY + LevelChunk.MIN_Y) + ",7): R=" + neighbor1AfterR + " G=" + neighbor1AfterG + " B=" + neighbor1AfterB);
        
        // Verify all light is removed
        assertEquals(0, afterRemovalR, "Red light should be removed from torch position");
        assertEquals(0, afterRemovalG, "Green light should be removed from torch position");
        assertEquals(0, afterRemovalB, "Blue light should be removed from torch position");
        
        assertEquals(0, neighbor1AfterR, "Red light should be removed from neighbor");
        assertEquals(0, neighbor1AfterG, "Green light should be removed from neighbor");
        assertEquals(0, neighbor1AfterB, "Blue light should be removed from neighbor");
        
        // Check a few more positions in the room to ensure light is gone everywhere
        for (int x = 6; x <= 8; x++) {
            for (int z = 6; z <= 8; z++) {
                int r = chunk.getBlockLightR(x, centerY, z);
                int g = chunk.getBlockLightG(x, centerY, z);
                int b = chunk.getBlockLightB(x, centerY, z);
                assertEquals(0, r, "Red light should be removed at (" + x + "," + centerY + "," + z + ")");
                assertEquals(0, g, "Green light should be removed at (" + x + "," + centerY + "," + z + ")");
                assertEquals(0, b, "Blue light should be removed at (" + x + "," + centerY + "," + z + ")");
            }
        }
        
        System.out.println("\n✓ All light successfully removed from enclosed room");
    }
}
