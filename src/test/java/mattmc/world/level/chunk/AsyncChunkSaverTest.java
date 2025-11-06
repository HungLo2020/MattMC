package mattmc.world.level.chunk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AsyncChunkSaver to verify asynchronous chunk saving behavior.
 */
public class AsyncChunkSaverTest {
    
    @Test
    public void testAsyncSaveChunk(@TempDir Path tempDir) throws IOException, InterruptedException {
        RegionFileCache cache = new RegionFileCache(tempDir);
        AsyncChunkSaver saver = new AsyncChunkSaver(cache);
        
        // Create test chunk data
        Map<String, Object> chunkData = new HashMap<>();
        chunkData.put("xPos", 0);
        chunkData.put("zPos", 0);
        chunkData.put("DataVersion", 1);
        
        // Queue chunk for async save
        saver.saveChunkAsync(0, 0, chunkData);
        
        // Flush to ensure save completes
        saver.flush();
        
        // Verify chunk was saved
        RegionFile regionFile = cache.getRegionFile(0, 0);
        assertTrue(regionFile.hasChunk(0, 0), "Chunk should be saved after flush");
        
        saver.shutdown();
        cache.close();
    }
    
    @Test
    public void testMultipleAsyncSaves(@TempDir Path tempDir) throws IOException, InterruptedException {
        RegionFileCache cache = new RegionFileCache(tempDir);
        AsyncChunkSaver saver = new AsyncChunkSaver(cache);
        
        // Queue multiple chunks
        for (int i = 0; i < 10; i++) {
            Map<String, Object> chunkData = new HashMap<>();
            chunkData.put("xPos", i);
            chunkData.put("zPos", 0);
            chunkData.put("DataVersion", 1);
            
            saver.saveChunkAsync(i, 0, chunkData);
        }
        
        // Flush to ensure all saves complete
        saver.flush();
        
        // Verify all chunks were saved
        RegionFile regionFile = cache.getRegionFile(0, 0);
        for (int i = 0; i < 10; i++) {
            assertTrue(regionFile.hasChunk(i, 0), "Chunk " + i + " should be saved");
        }
        
        saver.shutdown();
        cache.close();
    }
    
    @Test
    public void testPendingSaveCount(@TempDir Path tempDir) throws IOException, InterruptedException {
        RegionFileCache cache = new RegionFileCache(tempDir);
        AsyncChunkSaver saver = new AsyncChunkSaver(cache);
        
        // Initially should be 0
        assertEquals(0, saver.getPendingSaveCount(), "Should have no pending saves initially");
        
        // Queue some chunks
        for (int i = 0; i < 5; i++) {
            Map<String, Object> chunkData = new HashMap<>();
            chunkData.put("xPos", i);
            chunkData.put("zPos", 0);
            
            saver.saveChunkAsync(i, 0, chunkData);
        }
        
        // Should have pending saves (or they may have completed already)
        // Just verify it's not negative
        assertTrue(saver.getPendingSaveCount() >= 0, "Pending save count should not be negative");
        
        // Flush and check again
        saver.flush();
        assertEquals(0, saver.getPendingSaveCount(), "Should have no pending saves after flush");
        
        saver.shutdown();
        cache.close();
    }
    
    @Test
    public void testShutdownFlushes(@TempDir Path tempDir) throws IOException {
        RegionFileCache cache = new RegionFileCache(tempDir);
        AsyncChunkSaver saver = new AsyncChunkSaver(cache);
        
        // Queue a chunk
        Map<String, Object> chunkData = new HashMap<>();
        chunkData.put("xPos", 0);
        chunkData.put("zPos", 0);
        
        saver.saveChunkAsync(0, 0, chunkData);
        
        // Shutdown should flush pending saves
        saver.shutdown();
        
        // Verify chunk was saved
        RegionFile regionFile = cache.getRegionFile(0, 0);
        assertTrue(regionFile.hasChunk(0, 0), "Chunk should be saved after shutdown");
        
        cache.close();
    }
}
