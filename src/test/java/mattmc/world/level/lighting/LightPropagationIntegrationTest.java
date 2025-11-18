package mattmc.world.level.lighting;

import mattmc.world.level.Level;
import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test to verify lighting works correctly after the
 * singleton refactor fix. Tests multiple scenarios to ensure
 * worldLightManager is properly set on all chunks.
 */
public class LightPropagationIntegrationTest {
    
    @Test
    public void testLightPropagatesInSingleChunk() {
        Level level = new Level();
        LevelChunk chunk = level.getChunk(0, 0);
        
        int testY = LevelChunk.worldYToChunkY(100);
        
        // Place a torch in the air
        chunk.setBlock(7, testY, 7, Blocks.TORCH);
        
        // Verify torch emits light
        int torchLight = chunk.getBlockLightI(7, testY, 7);
        assertTrue(torchLight > 0, "Torch should emit light, got: " + torchLight);
        
        // Verify light propagates to neighbors
        int neighborLight = chunk.getBlockLightI(7, testY, 8);
        assertTrue(neighborLight > 0, "Light should propagate to neighbor, got: " + neighborLight);
        assertTrue(neighborLight < torchLight, "Neighbor light should be attenuated");
    }
    
    @Test
    public void testSkylightPropagatesWhenBreakingBlocks() {
        Level level = new Level();
        LevelChunk chunk = level.getChunk(0, 0);
        
        int testY = LevelChunk.worldYToChunkY(80);
        
        // Place a stone block in the sky
        chunk.setBlock(7, testY, 7, Blocks.STONE);
        
        // Verify stone blocks skylight
        assertEquals(0, chunk.getSkyLight(7, testY, 7), 
            "Stone should block skylight");
        
        // Break the stone (replace with air)
        chunk.setBlock(7, testY, 7, Blocks.AIR);
        
        // Verify skylight is restored
        int skylight = chunk.getSkyLight(7, testY, 7);
        assertTrue(skylight > 0, 
            "Air should have skylight after breaking stone, got: " + skylight);
    }
    
    @Test
    public void testLightingWorksAcrossMultipleChunks() {
        Level level = new Level();
        
        // Get multiple chunks
        LevelChunk chunk1 = level.getChunk(0, 0);
        LevelChunk chunk2 = level.getChunk(1, 0);
        LevelChunk chunk3 = level.getChunk(0, 1);
        
        // Verify all chunks can update lighting
        int testY = LevelChunk.worldYToChunkY(90);
        
        chunk1.setBlock(7, testY, 7, Blocks.TORCH);
        chunk2.setBlock(7, testY, 7, Blocks.TORCH);
        chunk3.setBlock(7, testY, 7, Blocks.TORCH);
        
        assertTrue(chunk1.getBlockLightI(7, testY, 7) > 0, 
            "Chunk 1 should have light from torch");
        assertTrue(chunk2.getBlockLightI(7, testY, 7) > 0, 
            "Chunk 2 should have light from torch");
        assertTrue(chunk3.getBlockLightI(7, testY, 7) > 0, 
            "Chunk 3 should have light from torch");
    }
    
    @Test
    public void testRemovingLightSourceRemovesLight() {
        Level level = new Level();
        LevelChunk chunk = level.getChunk(0, 0);
        
        int testY = LevelChunk.worldYToChunkY(95);
        
        // Place a torch
        chunk.setBlock(7, testY, 7, Blocks.TORCH);
        
        // Verify light exists
        assertTrue(chunk.getBlockLightI(7, testY, 7) > 0, "Torch should emit light");
        assertTrue(chunk.getBlockLightI(7, testY, 8) > 0, "Light should propagate");
        
        // Remove the torch
        chunk.setBlock(7, testY, 7, Blocks.AIR);
        
        // Verify light is removed
        assertEquals(0, chunk.getBlockLightI(7, testY, 7), 
            "Light should be removed at torch position");
        assertEquals(0, chunk.getBlockLightI(7, testY, 8), 
            "Propagated light should be removed");
    }
    
    @Test
    public void testLightingTriggersAfterWorldLightManagerSet() {
        // This test specifically validates that the worldLightManager is set
        // on chunks created via Level, which was the bug we fixed
        Level level = new Level();
        LevelChunk chunk = level.getChunk(0, 0);
        
        int testY = LevelChunk.worldYToChunkY(85);
        
        // Place and remove a torch - this would fail if worldLightManager is null
        chunk.setBlock(7, testY, 7, Blocks.TORCH);
        assertTrue(chunk.getBlockLightI(7, testY, 7) > 0, 
            "Torch should emit light (worldLightManager is set)");
        
        chunk.setBlock(7, testY, 7, Blocks.AIR);
        assertEquals(0, chunk.getBlockLightI(7, testY, 7), 
            "Light should be removed (worldLightManager is set)");
    }
}
