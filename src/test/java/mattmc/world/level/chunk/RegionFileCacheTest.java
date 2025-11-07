package mattmc.world.level.chunk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RegionFileCache to verify caching behavior and performance.
 */
public class RegionFileCacheTest {
    
    @Test
    public void testCacheReusesRegionFile(@TempDir Path tempDir) throws IOException {
        RegionFileCache cache = new RegionFileCache(tempDir);
        
        // Request same region file multiple times
        RegionFile region1 = cache.getRegionFile(0, 0);
        RegionFile region2 = cache.getRegionFile(15, 15); // Same region (0, 0)
        
        // Should return the same instance
        assertSame(region1, region2, "Cache should return the same RegionFile instance for chunks in the same region");
        
        cache.close();
    }
    
    @Test
    public void testCacheDifferentRegions(@TempDir Path tempDir) throws IOException {
        RegionFileCache cache = new RegionFileCache(tempDir);
        
        // Request different region files
        RegionFile region1 = cache.getRegionFile(0, 0);  // Region (0, 0)
        RegionFile region2 = cache.getRegionFile(32, 0); // Region (1, 0)
        
        // Should return different instances
        assertNotSame(region1, region2, "Cache should return different RegionFile instances for different regions");
        
        cache.close();
    }
    
    @Test
    public void testCacheSize(@TempDir Path tempDir) throws IOException {
        RegionFileCache cache = new RegionFileCache(tempDir);
        
        // Add a few regions
        cache.getRegionFile(0, 0);
        cache.getRegionFile(32, 0);
        cache.getRegionFile(0, 32);
        
        assertEquals(3, cache.getCacheSize(), "Cache should contain 3 regions");
        
        cache.close();
    }
    
    @Test
    public void testWriteAndReadThroughCache(@TempDir Path tempDir) throws IOException {
        RegionFileCache cache = new RegionFileCache(tempDir);
        
        // Create test chunk data
        Map<String, Object> chunkData = new HashMap<>();
        chunkData.put("xPos", 5);
        chunkData.put("zPos", 10);
        chunkData.put("DataVersion", 1);
        
        // Write chunk through cache
        RegionFile regionFile = cache.getRegionFile(5, 10);
        regionFile.writeChunk(5, 10, chunkData);
        
        // Flush to ensure data is written
        cache.flush();
        
        // Read chunk back through cache
        Map<String, Object> readData = regionFile.readChunk(5, 10);
        
        assertNotNull(readData, "Chunk data should be readable");
        assertEquals(5, readData.get("xPos"), "Chunk X position should match");
        assertEquals(10, readData.get("zPos"), "Chunk Z position should match");
        
        cache.close();
    }
    
    @Test
    public void testFlushWritesHeaders(@TempDir Path tempDir) throws IOException {
        RegionFileCache cache = new RegionFileCache(tempDir);
        
        Map<String, Object> chunkData = new HashMap<>();
        chunkData.put("xPos", 0);
        chunkData.put("zPos", 0);
        
        RegionFile regionFile = cache.getRegionFile(0, 0);
        regionFile.writeChunk(0, 0, chunkData);
        
        // Flush should write pending changes
        cache.flush();
        
        // Close and reopen to verify persistence
        cache.close();
        
        RegionFileCache newCache = new RegionFileCache(tempDir);
        RegionFile newRegionFile = newCache.getRegionFile(0, 0);
        
        assertTrue(newRegionFile.hasChunk(0, 0), "Chunk should exist after flush and reopen");
        
        newCache.close();
    }
    
    @Test
    public void testCloseReleasesResources(@TempDir Path tempDir) throws IOException {
        RegionFileCache cache = new RegionFileCache(tempDir);
        
        // Add some regions
        cache.getRegionFile(0, 0);
        cache.getRegionFile(32, 0);
        
        // Close should clear cache
        cache.close();
        
        assertEquals(0, cache.getCacheSize(), "Cache should be empty after close");
    }
}
