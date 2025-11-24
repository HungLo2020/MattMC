package mattmc.world.level.chunk;

import mattmc.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ChunkUtils coordinate conversions and operations.
 */
public class ChunkUtilsTest {
    
    // === Constants Tests ===
    
    @Test
    public void testConstants() {
        assertEquals(16, ChunkUtils.CHUNK_WIDTH);
        assertEquals(16, ChunkUtils.CHUNK_DEPTH);
        assertEquals(384, ChunkUtils.CHUNK_HEIGHT);
        assertEquals(16, ChunkUtils.SECTION_HEIGHT);
        assertEquals(-64, ChunkUtils.MIN_Y);
        assertEquals(319, ChunkUtils.MAX_Y);
    }
    
    // === Coordinate Conversion Tests ===
    
    @Test
    public void testWorldToChunkX() {
        assertEquals(0, ChunkUtils.worldToChunkX(0));
        assertEquals(0, ChunkUtils.worldToChunkX(15));
        assertEquals(1, ChunkUtils.worldToChunkX(16));
        assertEquals(1, ChunkUtils.worldToChunkX(31));
        assertEquals(2, ChunkUtils.worldToChunkX(32));
        
        // Negative coordinates
        assertEquals(-1, ChunkUtils.worldToChunkX(-1));
        assertEquals(-1, ChunkUtils.worldToChunkX(-16));
        assertEquals(-2, ChunkUtils.worldToChunkX(-17));
    }
    
    @Test
    public void testWorldToChunkZ() {
        assertEquals(0, ChunkUtils.worldToChunkZ(0));
        assertEquals(0, ChunkUtils.worldToChunkZ(15));
        assertEquals(1, ChunkUtils.worldToChunkZ(16));
        assertEquals(1, ChunkUtils.worldToChunkZ(31));
        
        // Negative coordinates
        assertEquals(-1, ChunkUtils.worldToChunkZ(-1));
        assertEquals(-1, ChunkUtils.worldToChunkZ(-16));
        assertEquals(-2, ChunkUtils.worldToChunkZ(-17));
    }
    
    @Test
    public void testChunkToWorldX() {
        assertEquals(0, ChunkUtils.chunkToWorldX(0));
        assertEquals(16, ChunkUtils.chunkToWorldX(1));
        assertEquals(32, ChunkUtils.chunkToWorldX(2));
        assertEquals(-16, ChunkUtils.chunkToWorldX(-1));
        assertEquals(-32, ChunkUtils.chunkToWorldX(-2));
    }
    
    @Test
    public void testChunkToWorldZ() {
        assertEquals(0, ChunkUtils.chunkToWorldZ(0));
        assertEquals(16, ChunkUtils.chunkToWorldZ(1));
        assertEquals(32, ChunkUtils.chunkToWorldZ(2));
        assertEquals(-16, ChunkUtils.chunkToWorldZ(-1));
        assertEquals(-32, ChunkUtils.chunkToWorldZ(-2));
    }
    
    @Test
    public void testWorldToLocalX() {
        assertEquals(0, ChunkUtils.worldToLocalX(0));
        assertEquals(15, ChunkUtils.worldToLocalX(15));
        assertEquals(0, ChunkUtils.worldToLocalX(16));
        assertEquals(1, ChunkUtils.worldToLocalX(17));
        assertEquals(5, ChunkUtils.worldToLocalX(37));
        
        // Negative coordinates
        assertEquals(15, ChunkUtils.worldToLocalX(-1));
        assertEquals(0, ChunkUtils.worldToLocalX(-16));
        assertEquals(15, ChunkUtils.worldToLocalX(-17));
    }
    
    @Test
    public void testWorldToLocalZ() {
        assertEquals(0, ChunkUtils.worldToLocalZ(0));
        assertEquals(15, ChunkUtils.worldToLocalZ(15));
        assertEquals(0, ChunkUtils.worldToLocalZ(16));
        assertEquals(1, ChunkUtils.worldToLocalZ(17));
        
        // Negative coordinates
        assertEquals(15, ChunkUtils.worldToLocalZ(-1));
        assertEquals(0, ChunkUtils.worldToLocalZ(-16));
        assertEquals(15, ChunkUtils.worldToLocalZ(-17));
    }
    
    @Test
    public void testWorldToLocalY() {
        // Valid range
        assertEquals(0, ChunkUtils.worldToLocalY(-64));
        assertEquals(1, ChunkUtils.worldToLocalY(-63));
        assertEquals(64, ChunkUtils.worldToLocalY(0));
        assertEquals(319, ChunkUtils.worldToLocalY(255));
        assertEquals(383, ChunkUtils.worldToLocalY(319));
        
        // Out of bounds
        assertEquals(-1, ChunkUtils.worldToLocalY(-65));
        assertEquals(-1, ChunkUtils.worldToLocalY(320));
        assertEquals(-1, ChunkUtils.worldToLocalY(400));
    }
    
    @Test
    public void testLocalToWorldY() {
        assertEquals(-64, ChunkUtils.localToWorldY(0));
        assertEquals(-63, ChunkUtils.localToWorldY(1));
        assertEquals(0, ChunkUtils.localToWorldY(64));
        assertEquals(255, ChunkUtils.localToWorldY(319));
        assertEquals(319, ChunkUtils.localToWorldY(383));
    }
    
    @Test
    public void testWorldYRoundTrip() {
        // Test that converting world Y to local and back gives the same value
        for (int worldY = -64; worldY <= 319; worldY++) {
            int localY = ChunkUtils.worldToLocalY(worldY);
            assertEquals(worldY, ChunkUtils.localToWorldY(localY), 
                "Round trip failed for world Y: " + worldY);
        }
    }
    
    // === Section Calculation Tests ===
    
    @Test
    public void testGetSectionIndex() {
        assertEquals(0, ChunkUtils.getSectionIndex(0));
        assertEquals(0, ChunkUtils.getSectionIndex(15));
        assertEquals(1, ChunkUtils.getSectionIndex(16));
        assertEquals(1, ChunkUtils.getSectionIndex(31));
        assertEquals(2, ChunkUtils.getSectionIndex(32));
        assertEquals(23, ChunkUtils.getSectionIndex(383));
    }
    
    @Test
    public void testGetSectionLocalY() {
        assertEquals(0, ChunkUtils.getSectionLocalY(0));
        assertEquals(15, ChunkUtils.getSectionLocalY(15));
        assertEquals(0, ChunkUtils.getSectionLocalY(16));
        assertEquals(1, ChunkUtils.getSectionLocalY(17));
        assertEquals(5, ChunkUtils.getSectionLocalY(37));
        assertEquals(15, ChunkUtils.getSectionLocalY(383));
    }
    
    @Test
    public void testWorldYToSectionIndex() {
        // Valid range
        assertEquals(0, ChunkUtils.worldYToSectionIndex(-64));
        assertEquals(0, ChunkUtils.worldYToSectionIndex(-49));
        assertEquals(1, ChunkUtils.worldYToSectionIndex(-48));
        assertEquals(4, ChunkUtils.worldYToSectionIndex(0));
        assertEquals(23, ChunkUtils.worldYToSectionIndex(319));
        
        // Out of bounds
        assertEquals(-1, ChunkUtils.worldYToSectionIndex(-65));
        assertEquals(-1, ChunkUtils.worldYToSectionIndex(320));
    }
    
    // === Validation Tests ===
    
    @Test
    public void testIsValidWorldY() {
        assertTrue(ChunkUtils.isValidWorldY(-64));
        assertTrue(ChunkUtils.isValidWorldY(-63));
        assertTrue(ChunkUtils.isValidWorldY(0));
        assertTrue(ChunkUtils.isValidWorldY(255));
        assertTrue(ChunkUtils.isValidWorldY(319));
        
        assertFalse(ChunkUtils.isValidWorldY(-65));
        assertFalse(ChunkUtils.isValidWorldY(320));
        assertFalse(ChunkUtils.isValidWorldY(-1000));
        assertFalse(ChunkUtils.isValidWorldY(1000));
    }
    
    @Test
    public void testIsValidLocalCoords() {
        // Valid coordinates
        assertTrue(ChunkUtils.isValidLocalCoords(0, 0, 0));
        assertTrue(ChunkUtils.isValidLocalCoords(15, 383, 15));
        assertTrue(ChunkUtils.isValidLocalCoords(8, 200, 8));
        
        // Invalid X
        assertFalse(ChunkUtils.isValidLocalCoords(-1, 0, 0));
        assertFalse(ChunkUtils.isValidLocalCoords(16, 0, 0));
        
        // Invalid Y
        assertFalse(ChunkUtils.isValidLocalCoords(0, -1, 0));
        assertFalse(ChunkUtils.isValidLocalCoords(0, 384, 0));
        
        // Invalid Z
        assertFalse(ChunkUtils.isValidLocalCoords(0, 0, -1));
        assertFalse(ChunkUtils.isValidLocalCoords(0, 0, 16));
    }
    
    // === Chunk Key Tests ===
    
    @Test
    public void testChunkKey() {
        long key1 = ChunkUtils.chunkKey(0, 0);
        long key2 = ChunkUtils.chunkKey(1, 0);
        long key3 = ChunkUtils.chunkKey(0, 1);
        long key4 = ChunkUtils.chunkKey(1, 1);
        
        // Keys should be unique
        assertNotEquals(key1, key2);
        assertNotEquals(key1, key3);
        assertNotEquals(key1, key4);
        assertNotEquals(key2, key3);
        assertNotEquals(key2, key4);
        assertNotEquals(key3, key4);
    }
    
    @Test
    public void testChunkKeyWithNegativeCoordinates() {
        long key1 = ChunkUtils.chunkKey(-1, 0);
        long key2 = ChunkUtils.chunkKey(-2, 0);
        long key3 = ChunkUtils.chunkKey(0, 0);
        
        // Keys should be unique
        assertNotEquals(key1, key2);
        assertNotEquals(key1, key3);
        assertNotEquals(key2, key3);
        
        // Same coordinates should produce same key
        assertEquals(key1, ChunkUtils.chunkKey(-1, 0));
    }
    
    @Test
    public void testChunkXFromKey() {
        assertEquals(0, ChunkUtils.chunkXFromKey(ChunkUtils.chunkKey(0, 0)));
        assertEquals(1, ChunkUtils.chunkXFromKey(ChunkUtils.chunkKey(1, 0)));
        assertEquals(100, ChunkUtils.chunkXFromKey(ChunkUtils.chunkKey(100, 50)));
        assertEquals(-1, ChunkUtils.chunkXFromKey(ChunkUtils.chunkKey(-1, 0)));
        assertEquals(-100, ChunkUtils.chunkXFromKey(ChunkUtils.chunkKey(-100, 50)));
    }
    
    @Test
    public void testChunkZFromKey() {
        assertEquals(0, ChunkUtils.chunkZFromKey(ChunkUtils.chunkKey(0, 0)));
        assertEquals(1, ChunkUtils.chunkZFromKey(ChunkUtils.chunkKey(0, 1)));
        assertEquals(50, ChunkUtils.chunkZFromKey(ChunkUtils.chunkKey(100, 50)));
        assertEquals(-1, ChunkUtils.chunkZFromKey(ChunkUtils.chunkKey(0, -1)));
        assertEquals(-50, ChunkUtils.chunkZFromKey(ChunkUtils.chunkKey(100, -50)));
    }
    
    @Test
    public void testChunkKeyRoundTrip() {
        // Test that creating a key and extracting coordinates gives back original values
        int[][] testCases = {
            {0, 0}, {1, 1}, {-1, -1}, {100, 50}, {-100, -50},
            {Integer.MAX_VALUE / 2, Integer.MAX_VALUE / 2},
            {Integer.MIN_VALUE / 2, Integer.MIN_VALUE / 2}
        };
        
        for (int[] coords : testCases) {
            long key = ChunkUtils.chunkKey(coords[0], coords[1]);
            assertEquals(coords[0], ChunkUtils.chunkXFromKey(key), 
                "X coordinate round trip failed for " + coords[0] + ", " + coords[1]);
            assertEquals(coords[1], ChunkUtils.chunkZFromKey(key), 
                "Z coordinate round trip failed for " + coords[0] + ", " + coords[1]);
        }
    }
    
    // === Section Empty Tests (existing) ===
    
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
}
