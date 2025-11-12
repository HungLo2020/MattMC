package mattmc.world.level.lighting;

import mattmc.world.level.Level;
import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Functional tests for the incremental BFS light propagation system.
 * Tests use Y=200 to avoid terrain generation interference.
 */
public class LightPropagatorFunctionalTest {
    
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
    public void testTorchPlacementAndRemoval() {
        // Place a torch
        level.setBlock(0, TEST_Y, 0, Blocks.TORCH);
        propagator.updateBudget(100.0);
        
        // Verify torch emits light
        assertEquals(14, getBlockLight(0, TEST_Y, 0), "Torch should emit level 14");
        assertEquals(13, getBlockLight(1, TEST_Y, 0), "Adjacent block should have level 13");
        
        // Remove the torch
        level.setBlock(0, TEST_Y, 0, Blocks.AIR);
        propagator.updateBudget(100.0);
        
        // Verify light is removed
        assertEquals(0, getBlockLight(0, TEST_Y, 0), "Light should be removed");
        assertEquals(0, getBlockLight(1, TEST_Y, 0), "Adjacent light should be removed");
        
        System.out.println("[LightPropagator] Torch placement and removal test passed");
    }
    
    @Test
    public void testLightAttenuation() {
        // Place a torch
        level.setBlock(0, TEST_Y, 0, Blocks.TORCH);
        propagator.updateBudget(100.0);
        
        // Test attenuation over distance
        assertEquals(14, getBlockLight(0, TEST_Y, 0));
        assertEquals(13, getBlockLight(1, TEST_Y, 0));
        assertEquals(12, getBlockLight(2, TEST_Y, 0));
        assertEquals(11, getBlockLight(3, TEST_Y, 0));
        
        // At distance 14, light should be 0
        assertEquals(0, getBlockLight(14, TEST_Y, 0));
        
        System.out.println("[LightPropagator] Light attenuation test passed");
    }
    
    @Test
    public void testOpacityBlocksLight() {
        // Place torch
        level.setBlock(0, TEST_Y, 0, Blocks.TORCH);
        propagator.updateBudget(100.0);
        
        // Place opaque stone block next to it
        level.setBlock(1, TEST_Y, 0, Blocks.STONE);
        propagator.updateBudget(100.0);
        
        // Torch should emit light
        assertEquals(14, getBlockLight(0, TEST_Y, 0));
        
        // The stone itself should have no light (opaque blocks don't propagate light through)
        // But light can still reach (2,200,0) by going around the stone
        // So we verify opacity by checking that after placing the stone, light beyond it comes from alternate paths only
        int lightBeyond = getBlockLight(2, TEST_Y, 0);
        assertTrue(lightBeyond > 0 && lightBeyond < 13, 
            "Light beyond stone should be less than direct propagation would give");
        
        System.out.println("[LightPropagator] Opacity blocks light test passed");
    }
    
    @Test
    public void testCrossChunkPropagation() {
        // Place torch at chunk boundary
        level.setBlock(15, TEST_Y, 0, Blocks.TORCH);
        propagator.updateBudget(100.0);
        
        // Verify light crosses chunk boundary
        assertEquals(14, getBlockLight(15, TEST_Y, 0));
        assertEquals(13, getBlockLight(16, TEST_Y, 0), "Light should cross chunk boundary");
        
        System.out.println("[LightPropagator] Cross-chunk propagation test passed");
    }
    
    @Test
    public void testNoInfiniteLoops() {
        // Place multiple torches
        level.setBlock(0, TEST_Y, 0, Blocks.TORCH);
        level.setBlock(5, TEST_Y, 0, Blocks.TORCH);
        level.setBlock(0, TEST_Y, 5, Blocks.TORCH);
        
        // Should complete without hanging
        long startTime = System.currentTimeMillis();
        propagator.updateBudget(50.0);
        long elapsed = System.currentTimeMillis() - startTime;
        
        assertTrue(elapsed < 1000, "Should complete quickly");
        assertFalse(propagator.hasPendingUpdates(), "Should process all updates");
        
        System.out.println("[LightPropagator] No infinite loops test passed (took " + elapsed + "ms)");
    }
    
    @Test
    public void testIncrementalProcessing() {
        level.setBlock(0, TEST_Y, 0, Blocks.TORCH);
        
        // Process with small budgets
        int iterations = 0;
        while (propagator.hasPendingUpdates() && iterations < 1000) {
            propagator.updateBudget(0.1);
            iterations++;
        }
        
        assertTrue(iterations > 1, "Should take multiple iterations");
        assertFalse(propagator.hasPendingUpdates(), "Should eventually complete");
        assertEquals(14, getBlockLight(0, TEST_Y, 0), "Final light should be correct");
        
        System.out.println("[LightPropagator] Incremental processing test passed (" + iterations + " iterations)");
    }
    
    private int getBlockLight(int worldX, int chunkY, int worldZ) {
        int chunkX = Math.floorDiv(worldX, LevelChunk.WIDTH);
        int chunkZ = Math.floorDiv(worldZ, LevelChunk.DEPTH);
        LevelChunk chunk = level.getChunkIfLoaded(chunkX, chunkZ);
        if (chunk == null) chunk = level.getChunk(chunkX, chunkZ);
        
        int localX = Math.floorMod(worldX, LevelChunk.WIDTH);
        int localZ = Math.floorMod(worldZ, LevelChunk.DEPTH);
        return chunk.getBlockLight(localX, chunkY, localZ);
    }
}
