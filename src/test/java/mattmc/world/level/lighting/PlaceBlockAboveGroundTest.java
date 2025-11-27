package mattmc.world.level.lighting;

import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to reproduce the bug where placing a block above ground causes 
 * the whole game to lose light as if the sun disappeared.
 */
public class PlaceBlockAboveGroundTest {

    @Test
    public void testPlaceBlockAboveGroundDoesNotRemoveAllLight() {
        LevelChunk chunk = new LevelChunk(0, 0);
        WorldLightManager worldLightManager = new WorldLightManager();
        chunk.setWorldLightManager(worldLightManager);
        
        int surfaceY = LevelChunk.worldYToChunkY(64);
        
        // Create a grass surface at y=64
        System.out.println("=== STEP 1: Create grass surface at y=64 ===");
        for (int x = 0; x < LevelChunk.WIDTH; x++) {
            for (int z = 0; z < LevelChunk.DEPTH; z++) {
                chunk.setBlock(x, surfaceY, z, Blocks.GRASS_BLOCK);
            }
        }
        
        // Initialize skylight
        SkylightEngine skylightEngine = new SkylightEngine();
        skylightEngine.initializeChunkSkylight(chunk);
        
        System.out.println("\nSkylight at various positions BEFORE placing block:");
        printSkylightRow(chunk, surfaceY + 1, 8);  // Just above surface
        printSkylightRow(chunk, surfaceY + 2, 8);  // 2 blocks above surface
        printSkylightRow(chunk, surfaceY + 5, 8);  // 5 blocks above surface
        
        // Check initial conditions
        int initialAbove = chunk.getSkyLight(8, surfaceY + 1, 8);
        int initialFarAway = chunk.getSkyLight(0, surfaceY + 1, 0);
        assertEquals(15, initialAbove, "Initial skylight above surface should be 15");
        assertEquals(15, initialFarAway, "Initial skylight at far position should be 15");
        
        System.out.println("\n=== STEP 2: Place a stone block ABOVE the ground at (8, 66, 8) ===");
        int placeY = surfaceY + 2;  // 2 blocks above surface (y=66 in world coords)
        chunk.setBlock(8, placeY, 8, Blocks.STONE);
        
        System.out.println("\nSkylight at various positions AFTER placing block:");
        printSkylightRow(chunk, surfaceY + 1, 8);  // Just above surface
        printSkylightRow(chunk, surfaceY + 2, 8);  // 2 blocks above surface (where stone was placed)
        printSkylightRow(chunk, surfaceY + 5, 8);  // 5 blocks above surface
        
        // Check if the neighboring blocks lost their light incorrectly
        System.out.println("\n=== Verification ===");
        
        // Check skylight at positions that should NOT have been affected
        int aboveBlock = chunk.getSkyLight(8, placeY + 1, 8);
        int nextToBlock = chunk.getSkyLight(9, placeY, 8);
        int belowBlock = chunk.getSkyLight(8, placeY - 1, 8);
        int farAway = chunk.getSkyLight(0, placeY, 0);
        
        System.out.println("Skylight 1 block above the placed stone: " + aboveBlock);
        System.out.println("Skylight next to the placed stone: " + nextToBlock);
        System.out.println("Skylight below the placed stone: " + belowBlock);
        System.out.println("Skylight far away (0,0): " + farAway);
        
        // These should all be 15 (full skylight) since they're above the surface
        assertEquals(15, aboveBlock, "Block above should have full skylight (15)");
        assertEquals(15, nextToBlock, "Block next to stone should have full skylight (15)");
        assertEquals(15, farAway, "Block far away should have full skylight (15)");
    }
    
    @Test
    public void testPlaceBlockInOpenAirDoesNotAffectDistantBlocks() {
        LevelChunk chunk = new LevelChunk(0, 0);
        WorldLightManager worldLightManager = new WorldLightManager();
        chunk.setWorldLightManager(worldLightManager);
        
        // Don't put any ground - all air, so full skylight everywhere
        SkylightEngine skylightEngine = new SkylightEngine();
        skylightEngine.initializeChunkSkylight(chunk);
        
        int testY = LevelChunk.worldYToChunkY(100);
        
        // Verify initial full skylight
        assertEquals(15, chunk.getSkyLight(0, testY, 0), "Initial full skylight at (0,0)");
        assertEquals(15, chunk.getSkyLight(15, testY, 15), "Initial full skylight at (15,15)");
        assertEquals(15, chunk.getSkyLight(8, testY, 8), "Initial full skylight at (8,8)");
        
        System.out.println("Placing a stone block at (8, " + testY + ", 8)");
        
        // Place a stone block in the air
        chunk.setBlock(8, testY, 8, Blocks.STONE);
        
        // Distant blocks should still have full skylight
        assertEquals(15, chunk.getSkyLight(0, testY, 0), "After placement: full skylight at (0,0)");
        assertEquals(15, chunk.getSkyLight(15, testY, 15), "After placement: full skylight at (15,15)");
        assertEquals(15, chunk.getSkyLight(0, testY + 1, 0), "After placement: full skylight at (0,+1,0)");
        
        // Block next to the stone might have slightly reduced light due to obstruction
        // but should still be > 0
        int nextTo = chunk.getSkyLight(9, testY, 8);
        assertTrue(nextTo >= 14, "Next to stone should have high skylight: " + nextTo);
    }
    
    private static void printSkylightRow(LevelChunk chunk, int y, int z) {
        System.out.print("Y=" + LevelChunk.chunkYToWorldY(y) + " (chunk y=" + y + "): ");
        for (int x = 0; x < 16; x++) {
            int light = chunk.getSkyLight(x, y, z);
            System.out.print(Integer.toHexString(light));
        }
        System.out.println();
    }
}
