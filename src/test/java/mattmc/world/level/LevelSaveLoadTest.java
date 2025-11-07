package mattmc.world.level;

import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for Level save/load functionality.
 * Verifies that chunks can be saved and loaded correctly through the Level class.
 */
public class LevelSaveLoadTest {
    
    @Test
    public void testSaveAndLoadWorld(@TempDir Path tempDir) throws IOException {
        // Create a level with world directory
        Level level = new Level();
        level.setWorldDirectory(tempDir);
        level.setSeed(12345L);
        
        // Load/generate some chunks and modify them
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                LevelChunk chunk = level.getChunk(x, z);
                // Set a unique block in each chunk for testing
                chunk.setBlock(8, 64, 8, Blocks.STONE);
            }
        }
        
        // Trigger chunk unloading by moving player far away
        // This should save the chunks
        level.updateChunksAroundPlayer(1000, 1000);
        
        // Shutdown to flush all saves
        level.shutdown();
        
        // Create a new level instance and load the same world
        Level level2 = new Level();
        level2.setWorldDirectory(tempDir);
        level2.setSeed(12345L);
        
        // Load the chunks and verify they have the same data
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                LevelChunk chunk = level2.getChunk(x, z);
                assertNotNull(chunk, "Chunk should be loadable");
                assertEquals(Blocks.STONE, chunk.getBlock(8, 64, 8), 
                           "Saved block should be preserved in chunk (" + x + ", " + z + ")");
            }
        }
        
        level2.shutdown();
    }
    
    @Test
    public void testWorldPersistenceAfterMultipleSaves(@TempDir Path tempDir) throws IOException {
        Level level = new Level();
        level.setWorldDirectory(tempDir);
        
        // First save
        LevelChunk chunk = level.getChunk(0, 0);
        chunk.setBlock(0, 64, 0, Blocks.STONE);
        level.updateChunksAroundPlayer(1000, 1000); // Trigger save
        level.shutdown();
        
        // Load and modify
        Level level2 = new Level();
        level2.setWorldDirectory(tempDir);
        LevelChunk chunk2 = level2.getChunk(0, 0);
        assertEquals(Blocks.STONE, chunk2.getBlock(0, 64, 0));
        chunk2.setBlock(1, 64, 1, Blocks.DIRT);
        level2.updateChunksAroundPlayer(1000, 1000); // Trigger save
        level2.shutdown();
        
        // Load again and verify both blocks
        Level level3 = new Level();
        level3.setWorldDirectory(tempDir);
        LevelChunk chunk3 = level3.getChunk(0, 0);
        assertEquals(Blocks.STONE, chunk3.getBlock(0, 64, 0), "First block should persist");
        assertEquals(Blocks.DIRT, chunk3.getBlock(1, 64, 1), "Second block should persist");
        level3.shutdown();
    }
    
    @Test
    public void testChunkCachingImprovesPerfornance(@TempDir Path tempDir) throws IOException {
        Level level = new Level();
        level.setWorldDirectory(tempDir);
        
        // Save some chunks first
        for (int i = 0; i < 10; i++) {
            LevelChunk chunk = level.getChunk(i, 0);
            chunk.setBlock(0, 64, 0, Blocks.STONE);
        }
        level.updateChunksAroundPlayer(1000, 1000);
        level.shutdown();
        
        // Load chunks and measure time
        Level level2 = new Level();
        level2.setWorldDirectory(tempDir);
        
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) {
            LevelChunk chunk = level2.getChunk(i, 0);
            assertNotNull(chunk);
        }
        long duration = System.currentTimeMillis() - startTime;
        
        System.out.println("Loaded 10 chunks in " + duration + "ms with caching");
        
        // Should be reasonably fast (less than 100ms for 10 chunks)
        assertTrue(duration < 100, "Chunk loading with cache should be fast");
        
        level2.shutdown();
    }
}
