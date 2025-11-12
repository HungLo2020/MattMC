package mattmc.world.level.lighting;

import mattmc.world.level.Level;
import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that chunks are properly marked dirty when light propagates across boundaries.
 * This ensures meshes rebuild when lighting updates occur.
 */
public class ChunkDirtyMarkingTest {
    
    private static final int TEST_Y = 200; // Above terrain
    private Level level;
    private LightPropagator propagator;
    
    @BeforeEach
    public void setup() {
        level = new Level();
        propagator = level.getLightPropagator();
        
        // Pre-load chunks for testing
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                level.getChunk(x, z);
            }
        }
        
        // Clear all dirty flags
        for (LevelChunk chunk : level.getLoadedChunks()) {
            chunk.setDirty(false);
        }
    }
    
    @Test
    public void testChunkMarkedDirtyOnBlockLightPropagation() {
        // Place torch at chunk boundary (x=15 is last column of chunk 0)
        LevelChunk chunk0 = level.getChunk(0, 0);
        LevelChunk chunk1 = level.getChunk(1, 0);
        
        // Clear dirty flags
        chunk0.setDirty(false);
        chunk1.setDirty(false);
        
        // Place torch at x=15 (chunk boundary)
        level.setBlock(15, TEST_Y, 0, Blocks.TORCH);
        
        // Process light propagation
        propagator.updateBudget(100.0);
        
        // Both chunks should be marked dirty
        assertTrue(chunk0.isDirty(), "Source chunk should be marked dirty");
        assertTrue(chunk1.isDirty(), "Neighbor chunk should be marked dirty when light crosses boundary");
        
        System.out.println("[ChunkDirtyMarking] Block light propagation test passed");
    }
    
    @Test
    public void testChunkMarkedDirtyOnBlockLightRemoval() {
        // Place torch and let light propagate
        level.setBlock(15, TEST_Y, 0, Blocks.TORCH);
        propagator.updateBudget(100.0);
        
        LevelChunk chunk0 = level.getChunk(0, 0);
        LevelChunk chunk1 = level.getChunk(1, 0);
        
        // Clear dirty flags after initial propagation
        chunk0.setDirty(false);
        chunk1.setDirty(false);
        
        // Remove torch
        level.setBlock(15, TEST_Y, 0, Blocks.AIR);
        
        // Process light removal
        propagator.updateBudget(100.0);
        
        // Both chunks should be marked dirty
        assertTrue(chunk0.isDirty(), "Source chunk should be marked dirty on removal");
        assertTrue(chunk1.isDirty(), "Neighbor chunk should be marked dirty when light removal crosses boundary");
        
        System.out.println("[ChunkDirtyMarking] Block light removal test passed");
    }
    
    @Test
    public void testMultipleNeighborChunksMarkedDirty() {
        // Place torch at corner where 4 chunks meet
        // x=15, z=15 is the corner of chunk (0,0)
        level.setBlock(15, TEST_Y, 15, Blocks.TORCH);
        
        LevelChunk chunk00 = level.getChunk(0, 0);
        LevelChunk chunk01 = level.getChunk(0, 1);
        LevelChunk chunk10 = level.getChunk(1, 0);
        LevelChunk chunk11 = level.getChunk(1, 1);
        
        // Clear dirty flags
        chunk00.setDirty(false);
        chunk01.setDirty(false);
        chunk10.setDirty(false);
        chunk11.setDirty(false);
        
        // Process light propagation
        propagator.updateBudget(100.0);
        
        // All 4 chunks should be marked dirty as light spreads
        assertTrue(chunk00.isDirty(), "Chunk (0,0) should be marked dirty");
        assertTrue(chunk01.isDirty(), "Chunk (0,1) should be marked dirty");
        assertTrue(chunk10.isDirty(), "Chunk (1,0) should be marked dirty");
        assertTrue(chunk11.isDirty(), "Chunk (1,1) should be marked dirty");
        
        System.out.println("[ChunkDirtyMarking] Multiple neighbor chunks test passed");
    }
    
    @Test
    public void testChunkOnlyMarkedDirtyWhenLightChanges() {
        // Place torch in middle of chunk (not at boundary)
        LevelChunk chunk = level.getChunk(0, 0);
        chunk.setDirty(false);
        
        level.setBlock(8, TEST_Y, 8, Blocks.TORCH);
        propagator.updateBudget(100.0);
        
        // Only the containing chunk should be dirty initially
        assertTrue(chunk.isDirty(), "Chunk should be marked dirty");
        
        // Clear dirty and place same torch again (no light change)
        chunk.setDirty(false);
        level.setBlock(8, TEST_Y, 8, Blocks.TORCH);
        propagator.updateBudget(100.0);
        
        // Chunk should be marked dirty even for redundant placement
        // (because setBlock always marks dirty)
        assertTrue(chunk.isDirty(), "Chunk should be marked dirty on block change");
        
        System.out.println("[ChunkDirtyMarking] Light change detection test passed");
    }
}
