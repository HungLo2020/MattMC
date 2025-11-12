package mattmc.world.level.lighting;

import mattmc.world.level.Level;
import mattmc.world.level.block.Block;
import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for emissive blocks and light placement API.
 * Verifies that:
 * - Blocks with getLightEmission() > 0 emit light when placed
 * - Light propagates correctly from emissive blocks
 * - Light is removed when emissive blocks are broken
 * - Light gradients are smooth and follow expected attenuation
 */
public class EmissiveBlocksTest {
    
    private Level level;
    private LightPropagator propagator;
    
    @BeforeEach
    public void setup() {
        level = new Level();
        propagator = level.getLightPropagator();
        
        // Pre-load chunks to avoid chunk loading interfering with tests
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                level.getChunk(x, z);
            }
        }
    }
    
    @Test
    public void testTorchEmitsLight() {
        // Test that torch block has correct emission level
        assertEquals(14, Blocks.TORCH.getLightEmission(), 
            "Torch should have light emission level 14");
        
        // Place torch high up where there's air (above terrain generation)
        int torchY = 200;
        level.setBlock(0, torchY, 0, Blocks.TORCH);
        
        // Process all pending light updates
        while (propagator.hasPendingUpdates()) {
            propagator.updateBudget(100.0);
        }
        
        // Check light at torch position
        int lightAtTorch = getBlockLight(0, torchY, 0);
        assertEquals(14, lightAtTorch, "Torch should emit light level 14");
    }
    
    @Test
    public void testLightPropagatesFromTorch() {
        int torchY = 200;
        level.setBlock(0, torchY, 0, Blocks.TORCH);
        
        // Process all pending light updates
        while (propagator.hasPendingUpdates()) {
            propagator.updateBudget(100.0);
        }
        
        // Check light propagation with attenuation
        // Light should decrease by 1 per block distance
        assertEquals(14, getBlockLight(0, torchY, 0), "Light at torch");
        assertEquals(13, getBlockLight(1, torchY, 0), "Light 1 block away");
        assertEquals(12, getBlockLight(2, torchY, 0), "Light 2 blocks away");
        assertEquals(11, getBlockLight(3, torchY, 0), "Light 3 blocks away");
        
        // Check in different directions
        assertEquals(13, getBlockLight(-1, torchY, 0), "Light 1 block west");
        assertEquals(13, getBlockLight(0, torchY, 1), "Light 1 block south");
        assertEquals(13, getBlockLight(0, torchY, -1), "Light 1 block north");
        assertEquals(13, getBlockLight(0, torchY + 1, 0), "Light 1 block up");
        assertEquals(13, getBlockLight(0, torchY - 1, 0), "Light 1 block down");
    }
    
    @Test
    public void testSmoothLightGradient() {
        int torchY = 200;
        level.setBlock(0, torchY, 0, Blocks.TORCH);
        
        // Process all pending light updates
        while (propagator.hasPendingUpdates()) {
            propagator.updateBudget(100.0);
        }
        
        // Verify smooth gradient in a line
        int[] expectedLights = {14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0};
        for (int i = 0; i < expectedLights.length; i++) {
            int light = getBlockLight(i, torchY, 0);
            assertEquals(expectedLights[i], light, 
                String.format("Light at distance %d should be %d but was %d", i, expectedLights[i], light));
        }
    }
    
    @Test
    public void testRemovingTorchRemovesLight() {
        int torchY = 200;
        
        // Place torch
        level.setBlock(0, torchY, 0, Blocks.TORCH);
        while (propagator.hasPendingUpdates()) {
            propagator.updateBudget(100.0);
        }
        
        // Verify light is present
        assertTrue(getBlockLight(0, torchY, 0) > 0, "Light should be present after placing torch");
        assertTrue(getBlockLight(1, torchY, 0) > 0, "Light should propagate to adjacent blocks");
        
        // Remove torch (replace with air)
        level.setBlock(0, torchY, 0, Blocks.AIR);
        while (propagator.hasPendingUpdates()) {
            propagator.updateBudget(100.0);
        }
        
        // Verify light is removed
        assertEquals(0, getBlockLight(0, torchY, 0), "Light should be removed at torch position");
        assertEquals(0, getBlockLight(1, torchY, 0), "Light should be removed from adjacent blocks");
        assertEquals(0, getBlockLight(2, torchY, 0), "Light should be removed from all previously lit blocks");
    }
    
    @Test
    public void testMultipleTorchesOverlap() {
        int torchY = 200;
        
        // Place two torches 5 blocks apart
        level.setBlock(0, torchY, 0, Blocks.TORCH);
        level.setBlock(5, torchY, 0, Blocks.TORCH);
        
        // Process all pending light updates
        while (propagator.hasPendingUpdates()) {
            propagator.updateBudget(100.0);
        }
        
        // At midpoint (2.5 blocks from each torch), light should be max of both sources
        // Distance 2 from first torch: 14 - 2 = 12
        // Distance 3 from second torch: 14 - 3 = 11
        // Should take max: 12
        int midpointLight = getBlockLight(2, torchY, 0);
        assertTrue(midpointLight >= 11, 
            "Overlapping light should use maximum value, got " + midpointLight);
    }
    
    @Test
    public void testRowOfTorches() {
        int torchY = 200;
        
        // Place a row of torches
        for (int x = 0; x < 5; x++) {
            level.setBlock(x * 3, torchY, 0, Blocks.TORCH);
        }
        
        // Process all pending light updates
        while (propagator.hasPendingUpdates()) {
            propagator.updateBudget(100.0);
        }
        
        // Verify each torch emits correct light
        for (int x = 0; x < 5; x++) {
            int light = getBlockLight(x * 3, torchY, 0);
            assertEquals(14, light, "Torch at position " + (x * 3) + " should emit light level 14");
        }
        
        // Verify light between torches
        // Between torches at x=0 and x=3, position x=1 should have light from both
        int betweenLight = getBlockLight(1, torchY, 0);
        assertTrue(betweenLight >= 13, 
            "Light between torches should be bright, got " + betweenLight);
    }
    
    @Test
    public void testLightPropagationAcrossChunks() {
        int torchY = 200;
        
        // Place torch near chunk boundary (chunk width is 16)
        // Position at x=15 (near edge of chunk 0)
        level.setBlock(15, torchY, 0, Blocks.TORCH);
        
        // Process all pending light updates
        while (propagator.hasPendingUpdates()) {
            propagator.updateBudget(100.0);
        }
        
        // Check light propagates into next chunk (x=16 is in chunk 1)
        int lightInNextChunk = getBlockLight(16, torchY, 0);
        assertEquals(13, lightInNextChunk, 
            "Light should propagate across chunk boundary with normal attenuation");
        
        // Check further into next chunk
        int lightFurther = getBlockLight(17, torchY, 0);
        assertEquals(12, lightFurther, 
            "Light should continue to attenuate across chunk boundary");
    }
    
    @Test
    public void testBlockEmissionAPI() {
        // Test the API exists and works correctly for various blocks
        assertEquals(0, Blocks.AIR.getLightEmission(), "Air should not emit light");
        assertEquals(0, Blocks.STONE.getLightEmission(), "Stone should not emit light");
        assertEquals(0, Blocks.DIRT.getLightEmission(), "Dirt should not emit light");
        assertEquals(14, Blocks.TORCH.getLightEmission(), "Torch should emit light level 14");
        
        // Verify emission values are clamped to 0-15 range
        Block testBlock = new Block(false, 20); // Try to create block with invalid emission
        assertTrue(testBlock.getLightEmission() <= 15, 
            "Light emission should be clamped to maximum 15");
        assertTrue(testBlock.getLightEmission() >= 0, 
            "Light emission should be clamped to minimum 0");
    }
    
    /**
     * Helper method to get block light at world coordinates.
     */
    private int getBlockLight(int worldX, int chunkY, int worldZ) {
        int chunkX = Math.floorDiv(worldX, LevelChunk.WIDTH);
        int chunkZ = Math.floorDiv(worldZ, LevelChunk.DEPTH);
        LevelChunk chunk = level.getChunkIfLoaded(chunkX, chunkZ);
        if (chunk == null) {
            chunk = level.getChunk(chunkX, chunkZ);
        }
        
        int localX = Math.floorMod(worldX, LevelChunk.WIDTH);
        int localZ = Math.floorMod(worldZ, LevelChunk.DEPTH);
        return chunk.getBlockLight(localX, chunkY, localZ);
    }
}
