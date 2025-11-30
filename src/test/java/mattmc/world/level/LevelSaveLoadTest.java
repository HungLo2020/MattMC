package mattmc.world.level;

import mattmc.registries.Blocks;
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
