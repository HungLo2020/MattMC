package mattmc.world.level.chunk;

import mattmc.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ChunkUtils, specifically the isSectionEmpty method
 * that was causing invisible block rendering bugs.
 */
public class ChunkUtilsTest {
    
    @Test
    public void testIsSectionEmptyWithFullyEmptySection() {
        LevelChunk chunk = new LevelChunk(0, 0);
        
        // Section should be empty (all blocks are AIR by default)
        assertTrue(ChunkUtils.isSectionEmpty(chunk, 0, 16), 
            "Empty section should be detected as empty");
    }
    
    @Test
    public void testIsSectionEmptyWithSingleBlock() {
        LevelChunk chunk = new LevelChunk(0, 0);
        
        // Place a single block in the middle of a section at a non-corner position
        // This is the bug case: old implementation only sampled corners and would miss this
        chunk.setBlock(5, 10, 7, Blocks.STONE);
        
        // Section should NOT be empty
        assertFalse(ChunkUtils.isSectionEmpty(chunk, 0, 16), 
            "Section with a single block should not be detected as empty");
    }
    
    @Test
    public void testIsSectionEmptyWithBlockAtCorner() {
        LevelChunk chunk = new LevelChunk(0, 0);
        
        // Place a block at a corner (which old implementation would have caught)
        chunk.setBlock(0, 0, 0, Blocks.STONE);
        
        // Section should NOT be empty
        assertFalse(ChunkUtils.isSectionEmpty(chunk, 0, 16), 
            "Section with a block at corner should not be detected as empty");
    }
    
    @Test
    public void testIsSectionEmptyWithMultipleSections() {
        LevelChunk chunk = new LevelChunk(0, 0);
        
        // Place blocks in different sections
        chunk.setBlock(8, 10, 8, Blocks.STONE);  // Section 0 (Y 0-15)
        chunk.setBlock(8, 25, 8, Blocks.DIRT);   // Section 1 (Y 16-31)
        chunk.setBlock(8, 130, 8, Blocks.GRASS_BLOCK); // Section 8 (Y 128-143)
        
        // Check each section
        assertFalse(ChunkUtils.isSectionEmpty(chunk, 0, 16), 
            "Section 0 should not be empty (has STONE)");
        assertFalse(ChunkUtils.isSectionEmpty(chunk, 16, 32), 
            "Section 1 should not be empty (has DIRT)");
        assertFalse(ChunkUtils.isSectionEmpty(chunk, 128, 144), 
            "Section 8 should not be empty (has GRASS_BLOCK)");
        
        // Check an actually empty section
        assertTrue(ChunkUtils.isSectionEmpty(chunk, 48, 64), 
            "Section 3 should be empty");
    }
    
    @Test
    public void testIsSectionEmptyBoundaryConditions() {
        LevelChunk chunk = new LevelChunk(0, 0);
        
        // Test at the very start of a section
        chunk.setBlock(0, 16, 0, Blocks.STONE);
        assertFalse(ChunkUtils.isSectionEmpty(chunk, 16, 32), 
            "Section should not be empty with block at startY");
        
        // Test at the very end of a section
        LevelChunk chunk2 = new LevelChunk(0, 0);
        chunk2.setBlock(15, 31, 15, Blocks.STONE);
        assertFalse(ChunkUtils.isSectionEmpty(chunk2, 16, 32), 
            "Section should not be empty with block at endY-1");
    }
    
    @Test
    public void testIsSectionEmptyEveryPosition() {
        // Test that a block at ANY position in the section is detected
        for (int testX = 0; testX < LevelChunk.WIDTH; testX++) {
            for (int testY = 0; testY < 16; testY++) {
                for (int testZ = 0; testZ < LevelChunk.DEPTH; testZ++) {
                    LevelChunk chunk = new LevelChunk(0, 0);
                    chunk.setBlock(testX, testY, testZ, Blocks.STONE);
                    
                    assertFalse(ChunkUtils.isSectionEmpty(chunk, 0, 16), 
                        String.format("Section should not be empty with block at (%d, %d, %d)", 
                            testX, testY, testZ));
                }
            }
        }
    }
    
    @Test
    public void testChunkKeyWithNegativeCoordinates() {
        // Test chunk key generation with negative coordinates
        long key1 = ChunkUtils.chunkKey(-1, 0);
        long key2 = ChunkUtils.chunkKey(-2, 0);
        long key3 = ChunkUtils.chunkKey(0, 0);
        
        // Keys should be unique
        assertNotEquals(key1, key2, "Different chunk coordinates should produce different keys");
        assertNotEquals(key1, key3, "Different chunk coordinates should produce different keys");
        assertNotEquals(key2, key3, "Different chunk coordinates should produce different keys");
        
        // Same coordinates should produce same key
        assertEquals(key1, ChunkUtils.chunkKey(-1, 0), "Same coordinates should produce same key");
    }
}
