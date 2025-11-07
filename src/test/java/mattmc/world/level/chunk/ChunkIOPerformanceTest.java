package mattmc.world.level.chunk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance tests for chunk I/O operations.
 * These tests verify that chunk saving/loading is efficient.
 */
public class ChunkIOPerformanceTest {
    
    @Test
    public void testChunkSavePerformance(@TempDir Path tempDir) throws IOException {
        RegionFileCache cache = new RegionFileCache(tempDir);
        
        int numChunks = 100;
        long startTime = System.currentTimeMillis();
        
        // Save 100 chunks
        for (int i = 0; i < numChunks; i++) {
            LevelChunk chunk = new LevelChunk(i, 0);
            chunk.generateFlatTerrain(64);
            
            Map<String, Object> chunkNBT = ChunkNBT.toNBT(chunk);
            RegionFile regionFile = cache.getRegionFile(i, 0);
            regionFile.writeChunk(i, 0, chunkNBT);
        }
        
        cache.flush();
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        System.out.println("Saved " + numChunks + " chunks in " + duration + "ms (" + (duration / (double)numChunks) + "ms per chunk)");
        
        // Chunks should save in reasonable time (less than 50ms per chunk on average)
        assertTrue(duration < numChunks * 50, "Chunk saving should be efficient");
        
        cache.close();
    }
    
    @Test
    public void testChunkLoadPerformance(@TempDir Path tempDir) throws IOException {
        RegionFileCache cache = new RegionFileCache(tempDir);
        
        int numChunks = 100;
        
        // First, save chunks
        for (int i = 0; i < numChunks; i++) {
            LevelChunk chunk = new LevelChunk(i, 0);
            chunk.generateFlatTerrain(64);
            
            Map<String, Object> chunkNBT = ChunkNBT.toNBT(chunk);
            RegionFile regionFile = cache.getRegionFile(i, 0);
            regionFile.writeChunk(i, 0, chunkNBT);
        }
        cache.flush();
        
        // Now load them and measure time
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < numChunks; i++) {
            RegionFile regionFile = cache.getRegionFile(i, 0);
            Map<String, Object> chunkNBT = regionFile.readChunk(i, 0);
            LevelChunk chunk = ChunkNBT.fromNBT(chunkNBT);
            assertNotNull(chunk);
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        System.out.println("Loaded " + numChunks + " chunks in " + duration + "ms (" + (duration / (double)numChunks) + "ms per chunk)");
        
        // Chunks should load in reasonable time (less than 30ms per chunk on average)
        assertTrue(duration < numChunks * 30, "Chunk loading should be efficient");
        
        cache.close();
    }
    
    @Test
    public void testRegionFileReopenPerformance(@TempDir Path tempDir) throws IOException {
        int numOperations = 100;
        Path regionPath = tempDir.resolve("r.0.0.mca");
        
        // Test without caching (reopening every time)
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < numOperations; i++) {
            try (RegionFile regionFile = new RegionFile(regionPath, 0, 0)) {
                Map<String, Object> chunkData = new HashMap<>();
                chunkData.put("xPos", i);
                chunkData.put("zPos", 0);
                regionFile.writeChunk(i, 0, chunkData);
            }
        }
        
        long withoutCacheDuration = System.currentTimeMillis() - startTime;
        
        // Clean up the file
        java.nio.file.Files.deleteIfExists(regionPath);
        
        // Test with caching
        RegionFileCache cache = new RegionFileCache(tempDir);
        startTime = System.currentTimeMillis();
        
        for (int i = 0; i < numOperations; i++) {
            RegionFile regionFile = cache.getRegionFile(i, 0);
            Map<String, Object> chunkData = new HashMap<>();
            chunkData.put("xPos", i);
            chunkData.put("zPos", 0);
            regionFile.writeChunk(i, 0, chunkData);
        }
        cache.flush();
        
        long withCacheDuration = System.currentTimeMillis() - startTime;
        
        System.out.println("Without cache: " + withoutCacheDuration + "ms, With cache: " + withCacheDuration + "ms");
        System.out.println("Cache speedup: " + (withoutCacheDuration / (double)withCacheDuration) + "x");
        
        // Caching should provide significant performance improvement
        assertTrue(withCacheDuration < withoutCacheDuration, "Caching should improve performance");
        
        cache.close();
    }
}
