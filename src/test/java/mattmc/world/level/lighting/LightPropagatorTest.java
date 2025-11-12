package mattmc.world.level.lighting;

import mattmc.world.level.Level;
import mattmc.world.level.block.Block;
import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the incremental BFS light propagation system.
 */
public class LightPropagatorTest {
    
    private Level level;
    private LightPropagator propagator;
    
    @BeforeEach
    public void setup() {
        level = new Level();
        propagator = level.getLightPropagator();
    }
    
    @Test
    public void testTorchPlacement() {
        // Place a torch at (0, 64, 0)
        level.setBlock(0, 64, 0, Blocks.TORCH);
        
        // Process light updates
        propagator.updateBudget(100.0); // Large budget to process everything
        
        // Check that the torch emits light level 14
        int lightAtTorch = getBlockLight(0, 64, 0);
        assertEquals(14, lightAtTorch, "Torch should emit light level 14");
        
        // Check that light propagates to adjacent blocks (should be 13 = 14 - 1)
        int lightEast = getBlockLight(1, 64, 0);
        assertEquals(13, lightEast, "Light should propagate east with attenuation (14-1=13)");
        
        int lightWest = getBlockLight(-1, 64, 0);
        assertEquals(13, lightWest, "Light should propagate west with attenuation (14-1=13)");
        
        int lightNorth = getBlockLight(0, 64, -1);
        assertEquals(13, lightNorth, "Light should propagate north with attenuation (14-1=13)");
        
        int lightSouth = getBlockLight(0, 64, 1);
        assertEquals(13, lightSouth, "Light should propagate south with attenuation (14-1=13)");
        
        int lightUp = getBlockLight(0, 65, 0);
        assertEquals(13, lightUp, "Light should propagate up with attenuation (14-1=13)");
        
        int lightDown = getBlockLight(0, 63, 0);
        assertEquals(13, lightDown, "Light should propagate down with attenuation (14-1=13)");
        
        System.out.println("[LightPropagator] Torch placement test passed:");
        System.out.println("  - Light at torch (0,64,0): " + lightAtTorch);
        System.out.println("  - Light 1 block away: " + lightEast);
    }
    
    @Test
    public void testTorchRemoval() {
        // Place a torch and let it propagate
        level.setBlock(0, 64, 0, Blocks.TORCH);
        propagator.updateBudget(100.0);
        
        int lightBefore = getBlockLight(1, 64, 0);
        assertTrue(lightBefore > 0, "Light should exist before torch removal");
        
        // Remove the torch
        level.setBlock(0, 64, 0, Blocks.AIR);
        propagator.updateBudget(100.0);
        
        // Check that light is removed
        int lightAtTorch = getBlockLight(0, 64, 0);
        assertEquals(0, lightAtTorch, "Light should be removed at torch position");
        
        int lightNearby = getBlockLight(1, 64, 0);
        assertEquals(0, lightNearby, "Light should be removed from nearby blocks");
        
        System.out.println("[LightPropagator] Torch removal test passed");
    }
    
    @Test
    public void testLightAttenuation() {
        // Place a torch and check attenuation over distance
        level.setBlock(0, 64, 0, Blocks.TORCH);
        propagator.updateBudget(100.0);
        
        // Light should attenuate by 1 per block
        for (int distance = 0; distance <= 14; distance++) {
            int light = getBlockLight(distance, 64, 0);
            int expected = Math.max(0, 14 - distance);
            assertEquals(expected, light, 
                "Light at distance " + distance + " should be " + expected);
        }
        
        // At distance 15, there should be no light
        int lightFar = getBlockLight(15, 64, 0);
        assertEquals(0, lightFar, "Light should not reach distance 15");
        
        System.out.println("[LightPropagator] Light attenuation test passed");
    }
    
    @Test
    public void testOpacityBlocksLight() {
        // Place a torch
        level.setBlock(0, 64, 0, Blocks.TORCH);
        
        // Place an opaque block next to it
        level.setBlock(1, 64, 0, Blocks.STONE);
        
        // Process updates
        propagator.updateBudget(100.0);
        
        // Light should not pass through the stone
        int lightBeyondStone = getBlockLight(2, 64, 0);
        assertEquals(0, lightBeyondStone, "Light should not pass through opaque block");
        
        // Light should still exist on the torch side
        int lightAtTorch = getBlockLight(0, 64, 0);
        assertEquals(14, lightAtTorch, "Torch should still emit light");
        
        System.out.println("[LightPropagator] Opacity blocks light test passed");
    }
    
    @Test
    public void testSkylightHole() {
        // Create a roof at y=100
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                level.setBlock(x, 100, z, Blocks.STONE);
            }
        }
        propagator.updateBudget(100.0);
        
        // Check that there's no skylight below the roof
        int skylightBelowRoof = getSkyLight(0, 99, 0);
        assertEquals(0, skylightBelowRoof, "Should be no skylight below roof initially");
        
        // Dig a hole in the roof
        level.setBlock(0, 100, 0, Blocks.AIR);
        propagator.updateBudget(100.0);
        
        // Check that skylight now exists below the hole
        int skylightAfterHole = getSkyLight(0, 99, 0);
        assertTrue(skylightAfterHole > 0, "Skylight should propagate through hole");
        
        System.out.println("[LightPropagator] Skylight hole test passed:");
        System.out.println("  - Skylight below hole: " + skylightAfterHole);
    }
    
    @Test
    public void testSkylightDownwardPropagation() {
        // Ensure there's open air from top to y=64
        // Skylight should propagate downward without attenuation
        level.setBlock(0, 100, 0, Blocks.AIR);
        propagator.enqueueSkylightFromSurface(0, LevelChunk.HEIGHT - 1, 0);
        propagator.updateBudget(100.0);
        
        // Check that skylight propagates downward with full brightness
        int skylightTop = getSkyLight(0, LevelChunk.HEIGHT - 1, 0);
        int skylightMid = getSkyLight(0, 100, 0);
        
        // Both should have maximum skylight (15)
        assertEquals(15, skylightTop, "Skylight at top should be 15");
        assertTrue(skylightMid >= 14, "Skylight should maintain brightness downward");
        
        System.out.println("[LightPropagator] Skylight downward propagation test passed");
    }
    
    @Test
    public void testSkylightLateralAttenuation() {
        // Place a skylight source
        propagator.enqueueSkylightFromSurface(0, 100, 0);
        propagator.updateBudget(100.0);
        
        // Skylight should attenuate laterally
        int skylightCenter = getSkyLight(0, 100, 0);
        int skylightAdjacent = getSkyLight(1, 100, 0);
        
        assertTrue(skylightCenter > skylightAdjacent, 
            "Skylight should attenuate laterally");
        assertEquals(skylightCenter - 1, skylightAdjacent,
            "Skylight should attenuate by 1 laterally");
        
        System.out.println("[LightPropagator] Skylight lateral attenuation test passed");
    }
    
    @Test
    public void testCrossChunkPropagation() {
        // Place a torch at chunk boundary (x=15 in chunk 0)
        level.setBlock(15, 64, 0, Blocks.TORCH);
        propagator.updateBudget(100.0);
        
        // Light should propagate across chunk boundary to x=16 (chunk 1)
        int lightInChunk0 = getBlockLight(15, 64, 0);
        int lightInChunk1 = getBlockLight(16, 64, 0);
        
        assertEquals(14, lightInChunk0, "Torch in chunk 0 should emit light 14");
        assertTrue(lightInChunk1 >= 13, "Light should propagate to chunk 1");
        
        System.out.println("[LightPropagator] Cross-chunk propagation test passed:");
        System.out.println("  - Light in chunk 0: " + lightInChunk0);
        System.out.println("  - Light in chunk 1: " + lightInChunk1);
    }
    
    @Test
    public void testNoInfiniteLoops() {
        // Place multiple torches in a pattern that could cause issues
        level.setBlock(0, 64, 0, Blocks.TORCH);
        level.setBlock(2, 64, 0, Blocks.TORCH);
        level.setBlock(0, 64, 2, Blocks.TORCH);
        level.setBlock(2, 64, 2, Blocks.TORCH);
        
        // Process with a limited budget - should complete without hanging
        long startTime = System.currentTimeMillis();
        propagator.updateBudget(50.0);
        long elapsed = System.currentTimeMillis() - startTime;
        
        assertTrue(elapsed < 1000, "Light propagation should complete quickly");
        
        // Verify light levels are reasonable
        int lightCenter = getBlockLight(1, 64, 1);
        assertTrue(lightCenter >= 13, "Center should be well-lit by surrounding torches");
        
        System.out.println("[LightPropagator] No infinite loops test passed");
        System.out.println("  - Processing time: " + elapsed + "ms");
    }
    
    @Test
    public void testIncrementalProcessing() {
        // Place a torch
        level.setBlock(0, 64, 0, Blocks.TORCH);
        
        // Process with very small budgets
        int iterations = 0;
        while (propagator.hasPendingUpdates() && iterations < 1000) {
            propagator.updateBudget(0.1); // Very small budget
            iterations++;
        }
        
        assertTrue(iterations > 1, "Should take multiple iterations with small budget");
        assertFalse(propagator.hasPendingUpdates(), "All updates should eventually complete");
        
        // Verify final light state is correct
        int lightAtTorch = getBlockLight(0, 64, 0);
        assertEquals(14, lightAtTorch, "Final light level should be correct");
        
        System.out.println("[LightPropagator] Incremental processing test passed:");
        System.out.println("  - Iterations needed: " + iterations);
    }
    
    @Test
    public void testMultipleLightSources() {
        // Place two torches near each other
        level.setBlock(0, 64, 0, Blocks.TORCH);
        level.setBlock(0, 64, 2, Blocks.TORCH);
        propagator.updateBudget(100.0);
        
        // The block between them should have light from both sources
        int lightBetween = getBlockLight(0, 64, 1);
        assertTrue(lightBetween >= 13, "Block between torches should be well-lit");
        
        // Remove one torch
        level.setBlock(0, 64, 0, Blocks.AIR);
        propagator.updateBudget(100.0);
        
        // The light should still exist from the other torch
        int lightAfterRemoval = getBlockLight(0, 64, 1);
        assertTrue(lightAfterRemoval >= 13, "Light from remaining torch should persist");
        
        System.out.println("[LightPropagator] Multiple light sources test passed");
    }
    
    // Helper methods
    
    private int getBlockLight(int worldX, int chunkY, int worldZ) {
        int chunkX = Math.floorDiv(worldX, LevelChunk.WIDTH);
        int chunkZ = Math.floorDiv(worldZ, LevelChunk.DEPTH);
        LevelChunk chunk = level.getChunkIfLoaded(chunkX, chunkZ);
        if (chunk == null) {
            // Chunk not loaded, load it
            chunk = level.getChunk(chunkX, chunkZ);
        }
        
        int localX = Math.floorMod(worldX, LevelChunk.WIDTH);
        int localZ = Math.floorMod(worldZ, LevelChunk.DEPTH);
        return chunk.getBlockLight(localX, chunkY, localZ);
    }
    
    private int getSkyLight(int worldX, int chunkY, int worldZ) {
        int chunkX = Math.floorDiv(worldX, LevelChunk.WIDTH);
        int chunkZ = Math.floorDiv(worldZ, LevelChunk.DEPTH);
        LevelChunk chunk = level.getChunkIfLoaded(chunkX, chunkZ);
        if (chunk == null) {
            // Chunk not loaded, load it
            chunk = level.getChunk(chunkX, chunkZ);
        }
        
        int localX = Math.floorMod(worldX, LevelChunk.WIDTH);
        int localZ = Math.floorMod(worldZ, LevelChunk.DEPTH);
        return chunk.getSkyLight(localX, chunkY, localZ);
    }
}
