package mattmc.world.level.lighting;

import mattmc.world.level.Level;
import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for the shadow removal bug where shadows persist after blocks are deleted.
 */
public class ShadowRemovalTest {
    
    private static final int TEST_Y = 200; // Above terrain
    private Level level;
    private LightPropagator propagator;
    
    @BeforeEach
    public void setup() {
        level = new Level();
        propagator = level.getLightPropagator();
        
        // Pre-load chunks
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                level.getChunk(x, z);
            }
        }
    }
    
    @Test
    public void testShadowsDisappearWhenBlockRemoved() {
        System.out.println("=== Shadow Removal Test ===");
        
        // Get initial skylight at ground level (should be 15 at Y=200 which is open to sky)
        int skylightBefore = getSkyLight(0, TEST_Y, 0);
        System.out.println("Skylight before placing block: " + skylightBefore);
        
        // Place a solid block above the ground at Y=201
        level.setBlock(0, TEST_Y + 1, 0, Blocks.STONE);
        propagator.updateBudget(100.0);
        
        // Check that shadow appears beneath the block at Y=200
        int skylightWithBlock = getSkyLight(0, TEST_Y, 0);
        System.out.println("Skylight after placing block above: " + skylightWithBlock);
        assertTrue(skylightWithBlock < skylightBefore, "Shadow should appear beneath block");
        
        // Now remove the block
        level.setBlock(0, TEST_Y + 1, 0, Blocks.AIR);
        propagator.updateBudget(100.0);
        
        // Check that shadow disappears
        int skylightAfterRemoval = getSkyLight(0, TEST_Y, 0);
        System.out.println("Skylight after removing block: " + skylightAfterRemoval);
        assertEquals(skylightBefore, skylightAfterRemoval, "Shadow should disappear when block is removed");
    }
    
    @Test
    public void testShadowsBelowBlockDisappearWhenBlockRemoved() {
        System.out.println("=== Shadow Below Block Removal Test ===");
        
        // Place a solid block at Y=201
        level.setBlock(0, TEST_Y + 1, 0, Blocks.STONE);
        propagator.updateBudget(100.0);
        
        // Check that there's low/no skylight beneath the block at Y=200
        int skylightWithBlock = getSkyLight(0, TEST_Y, 0);
        System.out.println("Skylight beneath block: " + skylightWithBlock);
        
        // Check neighboring positions have more light (not shadowed)
        int skylightEast = getSkyLight(1, TEST_Y, 0);
        int skylightWest = getSkyLight(-1, TEST_Y, 0);
        System.out.println("Skylight east of block: " + skylightEast);
        System.out.println("Skylight west of block: " + skylightWest);
        
        // Now remove the block
        level.setBlock(0, TEST_Y + 1, 0, Blocks.AIR);
        propagator.updateBudget(100.0);
        
        // Check that skylight returns to the shadowed position
        int skylightAfterRemoval = getSkyLight(0, TEST_Y, 0);
        System.out.println("Skylight after removing block: " + skylightAfterRemoval);
        
        // The skylight should now be at least 14 (lateral propagation from neighbors or from above)
        assertTrue(skylightAfterRemoval >= 14, 
            "Skylight should be restored after block removal, got " + skylightAfterRemoval);
    }
    
    private int getSkyLight(int worldX, int chunkY, int worldZ) {
        int chunkX = Math.floorDiv(worldX, LevelChunk.WIDTH);
        int chunkZ = Math.floorDiv(worldZ, LevelChunk.DEPTH);
        LevelChunk chunk = level.getChunkIfLoaded(chunkX, chunkZ);
        if (chunk == null) chunk = level.getChunk(chunkX, chunkZ);
        
        int localX = Math.floorMod(worldX, LevelChunk.WIDTH);
        int localZ = Math.floorMod(worldZ, LevelChunk.DEPTH);
        return chunk.getSkyLight(localX, chunkY, localZ);
    }
}
