package mattmc.world.level;

import mattmc.world.level.block.Block;
import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for block operations in negative chunk coordinates.
 * Specifically tests chunks -1,0 and -2,0 where rendering issues have been reported.
 */
public class NegativeChunkTest {
    
    @Test
    public void testBlockPlacementInNegativeChunks() {
        Level level = new Level();
        
        // Test chunk (-1, 0) - world X: -16 to -1, world Z: 0 to 15
        // Place a block at world (-10, 64, 5)
        int worldX = -10;
        int worldZ = 5;
        int worldY = 64;
        int chunkY = LevelChunk.worldYToChunkY(worldY);
        
        // Set a stone block
        level.setBlock(worldX, chunkY, worldZ, Blocks.STONE);
        
        // Verify it was placed correctly
        Block retrieved = level.getBlock(worldX, chunkY, worldZ);
        assertEquals(Blocks.STONE, retrieved, "Block should be STONE at (" + worldX + ", " + chunkY + ", " + worldZ + ")");
        
        // Test chunk (-2, 0) - world X: -32 to -17, world Z: 0 to 15
        worldX = -25;
        worldZ = 10;
        worldY = 70;
        chunkY = LevelChunk.worldYToChunkY(worldY);
        
        level.setBlock(worldX, chunkY, worldZ, Blocks.DIRT);
        retrieved = level.getBlock(worldX, chunkY, worldZ);
        assertEquals(Blocks.DIRT, retrieved, "Block should be DIRT at (" + worldX + ", " + chunkY + ", " + worldZ + ")");
    }
    
    @Test
    public void testStackedBlocksInNegativeChunks() {
        Level level = new Level();
        
        // Test stacking blocks in chunk (-1, 0)
        int worldX = -5;
        int worldZ = 8;
        
        // Place blocks at multiple heights
        for (int worldY = 64; worldY <= 70; worldY++) {
            int chunkY = LevelChunk.worldYToChunkY(worldY);
            level.setBlock(worldX, chunkY, worldZ, Blocks.STONE);
        }
        
        // Verify all blocks were placed
        for (int worldY = 64; worldY <= 70; worldY++) {
            int chunkY = LevelChunk.worldYToChunkY(worldY);
            Block retrieved = level.getBlock(worldX, chunkY, worldZ);
            assertEquals(Blocks.STONE, retrieved, 
                "Block should be STONE at (" + worldX + ", " + worldY + ", " + worldZ + ")");
        }
    }
    
    @Test
    public void testChunkCoordinateCalculationForNegativeX() {
        // Verify Math.floorDiv and Math.floorMod work correctly for negative coordinates
        
        // World X = -1 should be in chunk -1, local X = 15
        int chunkX = Math.floorDiv(-1, LevelChunk.WIDTH);
        int localX = Math.floorMod(-1, LevelChunk.WIDTH);
        assertEquals(-1, chunkX, "Chunk X for world X = -1");
        assertEquals(15, localX, "Local X for world X = -1");
        
        // World X = -16 should be in chunk -1, local X = 0
        chunkX = Math.floorDiv(-16, LevelChunk.WIDTH);
        localX = Math.floorMod(-16, LevelChunk.WIDTH);
        assertEquals(-1, chunkX, "Chunk X for world X = -16");
        assertEquals(0, localX, "Local X for world X = -16");
        
        // World X = -17 should be in chunk -2, local X = 15
        chunkX = Math.floorDiv(-17, LevelChunk.WIDTH);
        localX = Math.floorMod(-17, LevelChunk.WIDTH);
        assertEquals(-2, chunkX, "Chunk X for world X = -17");
        assertEquals(15, localX, "Local X for world X = -17");
    }
    
    @Test
    public void testChunkDirtyFlagInNegativeChunks() {
        Level level = new Level();
        
        // Get chunk (-1, 0)
        LevelChunk chunk = level.getChunk(-1, 0);
        assertNotNull(chunk, "Chunk (-1, 0) should exist");
        
        // Initially, chunk might be dirty from generation
        // Set it to clean first
        chunk.setDirty(false);
        assertFalse(chunk.isDirty(), "Chunk should not be dirty initially");
        
        // Place a block
        int worldX = -10;
        int worldZ = 5;
        int chunkY = LevelChunk.worldYToChunkY(64);
        level.setBlock(worldX, chunkY, worldZ, Blocks.STONE);
        
        // Chunk should now be dirty
        assertTrue(chunk.isDirty(), "Chunk should be dirty after block placement");
    }
}
