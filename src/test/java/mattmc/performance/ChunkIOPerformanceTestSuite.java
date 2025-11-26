package mattmc.performance;

import mattmc.world.level.chunk.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance tests for chunk I/O operations.
 * 
 * These tests measure:
 * - Chunk serialization speed
 * - Chunk deserialization speed
 * - Region file read/write performance
 * - Batch save/load performance
 * - Region file cache effectiveness
 */
@DisplayName("Chunk I/O Performance Tests")
public class ChunkIOPerformanceTestSuite extends PerformanceTestBase {
    
    @TempDir
    Path tempDir;
    
    private RegionFileCache regionCache;
    
    @BeforeEach
    void setUp() throws IOException {
        regionCache = new RegionFileCache(tempDir);
    }
    
    @Test
    @DisplayName("Chunk serialization (toNBT) should be fast")
    void testChunkSerializationSpeed() {
        LevelChunk chunk = new LevelChunk(0, 0);
        chunk.generateFlatTerrain(64);
        
        PerformanceResult result = measureOperationWithResult(
            "Chunk Serialization",
            100, 10,
            () -> ChunkNBT.toNBT(chunk)
        );
        
        System.out.println(result);
        
        // Serialization should complete in under 20ms per chunk
        double avgTime = result.getAvgTimePerIterationMs();
        assertTrue(avgTime < 20.0,
            "Chunk serialization took " + avgTime + "ms, expected < 20ms");
    }
    
    @Test
    @DisplayName("Chunk deserialization (fromNBT) should be fast")
    void testChunkDeserializationSpeed() {
        LevelChunk chunk = new LevelChunk(0, 0);
        chunk.generateFlatTerrain(64);
        Map<String, Object> nbt = ChunkNBT.toNBT(chunk);
        
        PerformanceResult result = measureOperationWithResult(
            "Chunk Deserialization",
            100, 10,
            () -> ChunkNBT.fromNBT(nbt)
        );
        
        System.out.println(result);
        
        // Deserialization should complete in under 20ms per chunk
        double avgTime = result.getAvgTimePerIterationMs();
        assertTrue(avgTime < 20.0,
            "Chunk deserialization took " + avgTime + "ms, expected < 20ms");
    }
    
    @Test
    @DisplayName("Region file write should be efficient")
    void testRegionFileWriteSpeed() throws IOException {
        LevelChunk chunk = new LevelChunk(0, 0);
        chunk.generateFlatTerrain(64);
        Map<String, Object> nbt = ChunkNBT.toNBT(chunk);
        
        PerformanceResult result = measureOperation(
            "Region File Write",
            50, 5,
            () -> {
                try {
                    RegionFile regionFile = regionCache.getRegionFile(0, 0);
                    regionFile.writeChunk(0, 0, nbt);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        );
        
        System.out.println(result);
        
        // Write should complete in under 50ms per chunk
        double avgTime = result.getAvgTimePerIterationMs();
        assertTrue(avgTime < 50.0,
            "Region file write took " + avgTime + "ms, expected < 50ms");
    }
    
    @Test
    @DisplayName("Region file read should be efficient")
    void testRegionFileReadSpeed() throws IOException {
        // First write a chunk
        LevelChunk chunk = new LevelChunk(0, 0);
        chunk.generateFlatTerrain(64);
        Map<String, Object> nbt = ChunkNBT.toNBT(chunk);
        RegionFile regionFile = regionCache.getRegionFile(0, 0);
        regionFile.writeChunk(0, 0, nbt);
        regionCache.flush();
        
        PerformanceResult result = measureOperationWithResult(
            "Region File Read",
            100, 10,
            () -> {
                try {
                    RegionFile rf = regionCache.getRegionFile(0, 0);
                    return rf.readChunk(0, 0);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        );
        
        System.out.println(result);
        
        // Read should complete in under 30ms per chunk
        double avgTime = result.getAvgTimePerIterationMs();
        assertTrue(avgTime < 30.0,
            "Region file read took " + avgTime + "ms, expected < 30ms");
    }
    
    @Test
    @DisplayName("Batch chunk save should scale efficiently")
    void testBatchChunkSaveScaling() throws IOException {
        final int BATCH_SIZE = 100;
        
        // Generate chunks
        LevelChunk[] chunks = new LevelChunk[BATCH_SIZE];
        for (int i = 0; i < BATCH_SIZE; i++) {
            chunks[i] = new LevelChunk(i % 32, i / 32);
            chunks[i].generateFlatTerrain(64);
        }
        
        PerformanceResult result = measureOperation(
            "Batch Chunk Save (100 chunks)",
            5, 1,
            () -> {
                try {
                    for (int i = 0; i < BATCH_SIZE; i++) {
                        Map<String, Object> nbt = ChunkNBT.toNBT(chunks[i]);
                        RegionFile regionFile = regionCache.getRegionFile(chunks[i].chunkX(), chunks[i].chunkZ());
                        regionFile.writeChunk(chunks[i].chunkX(), chunks[i].chunkZ(), nbt);
                    }
                    regionCache.flush();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        );
        
        System.out.println(result);
        
        double perChunkTime = result.getAvgTimePerIterationMs() / BATCH_SIZE;
        System.out.println("Per-chunk save time: " + perChunkTime + " ms");
        
        // Per-chunk time should be under 50ms
        assertTrue(perChunkTime < 50.0,
            "Per-chunk save took " + perChunkTime + "ms, expected < 50ms");
    }
    
    @Test
    @DisplayName("Batch chunk load should scale efficiently")
    void testBatchChunkLoadScaling() throws IOException {
        final int BATCH_SIZE = 100;
        
        // First save chunks
        for (int i = 0; i < BATCH_SIZE; i++) {
            LevelChunk chunk = new LevelChunk(i % 32, i / 32);
            chunk.generateFlatTerrain(64);
            Map<String, Object> nbt = ChunkNBT.toNBT(chunk);
            RegionFile regionFile = regionCache.getRegionFile(chunk.chunkX(), chunk.chunkZ());
            regionFile.writeChunk(chunk.chunkX(), chunk.chunkZ(), nbt);
        }
        regionCache.flush();
        
        PerformanceResult result = measureOperationWithResult(
            "Batch Chunk Load (100 chunks)",
            5, 1,
            () -> {
                LevelChunk[] loadedChunks = new LevelChunk[BATCH_SIZE];
                try {
                    for (int i = 0; i < BATCH_SIZE; i++) {
                        int chunkX = i % 32;
                        int chunkZ = i / 32;
                        RegionFile regionFile = regionCache.getRegionFile(chunkX, chunkZ);
                        Map<String, Object> nbt = regionFile.readChunk(chunkX, chunkZ);
                        loadedChunks[i] = ChunkNBT.fromNBT(nbt);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return loadedChunks;
            }
        );
        
        System.out.println(result);
        
        double perChunkTime = result.getAvgTimePerIterationMs() / BATCH_SIZE;
        System.out.println("Per-chunk load time: " + perChunkTime + " ms");
        
        // Per-chunk time should be under 30ms
        assertTrue(perChunkTime < 30.0,
            "Per-chunk load took " + perChunkTime + "ms, expected < 30ms");
    }
    
    @Test
    @DisplayName("Region file cache should improve performance")
    void testRegionFileCacheEffectiveness() throws IOException {
        // Create chunks to save
        final int NUM_CHUNKS = 50;
        
        // Test without cache (creating new files each time)
        Path noCachePath = tempDir.resolve("no_cache");
        Files.createDirectories(noCachePath);
        
        long startNoCache = System.nanoTime();
        for (int i = 0; i < NUM_CHUNKS; i++) {
            LevelChunk chunk = new LevelChunk(i, 0);
            chunk.generateFlatTerrain(64);
            Map<String, Object> nbt = ChunkNBT.toNBT(chunk);
            
            Path regionPath = noCachePath.resolve("r.0.0.mca");
            try (RegionFile regionFile = new RegionFile(regionPath, 0, 0)) {
                regionFile.writeChunk(i, 0, nbt);
            }
        }
        long noCacheTime = System.nanoTime() - startNoCache;
        
        // Test with cache
        Path cachePath = tempDir.resolve("with_cache");
        Files.createDirectories(cachePath);
        RegionFileCache testCache = new RegionFileCache(cachePath);
        
        long startWithCache = System.nanoTime();
        for (int i = 0; i < NUM_CHUNKS; i++) {
            LevelChunk chunk = new LevelChunk(i, 0);
            chunk.generateFlatTerrain(64);
            Map<String, Object> nbt = ChunkNBT.toNBT(chunk);
            
            RegionFile regionFile = testCache.getRegionFile(i, 0);
            regionFile.writeChunk(i, 0, nbt);
        }
        testCache.flush();
        testCache.close();
        long withCacheTime = System.nanoTime() - startWithCache;
        
        double noCacheMs = noCacheTime / 1_000_000.0;
        double withCacheMs = withCacheTime / 1_000_000.0;
        double speedup = noCacheMs / withCacheMs;
        
        System.out.printf("Without cache: %.3f ms%n", noCacheMs);
        System.out.printf("With cache: %.3f ms%n", withCacheMs);
        System.out.printf("Cache speedup: %.2fx%n", speedup);
        
        // Cache should provide at least 1.0x speedup (not slower)
        // Note: In CI environments, cache benefit may be reduced due to disk caching
        assertTrue(speedup > 1.0,
            "Cache was slower than no-cache: speedup was " + speedup + "x, expected > 1.0x");
    }
    
    @Test
    @DisplayName("Chunk with light data should not significantly slow serialization")
    void testChunkWithLightDataSerializationSpeed() {
        LevelChunk chunkNoLight = new LevelChunk(0, 0);
        chunkNoLight.generateFlatTerrain(64);
        
        LevelChunk chunkWithLight = new LevelChunk(1, 0);
        chunkWithLight.generateFlatTerrain(64);
        
        // Add light data
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 128; y++) {
                    chunkWithLight.setSkyLight(x, y, z, 15);
                    chunkWithLight.setBlockLightRGBI(x, y, z, 7, 7, 7, 7);
                }
            }
        }
        
        PerformanceResult noLightResult = measureOperationWithResult(
            "Serialization without light",
            50, 5,
            () -> ChunkNBT.toNBT(chunkNoLight)
        );
        
        PerformanceResult withLightResult = measureOperationWithResult(
            "Serialization with light",
            50, 5,
            () -> ChunkNBT.toNBT(chunkWithLight)
        );
        
        System.out.println(noLightResult);
        System.out.println(withLightResult);
        
        double ratio = withLightResult.getAvgTimePerIterationMs() / noLightResult.getAvgTimePerIterationMs();
        System.out.printf("Light data serialization overhead: %.2fx%n", ratio);
        
        // Light data should add at most 3x overhead
        assertTrue(ratio < 3.0,
            "Light data added " + ratio + "x overhead, expected < 3x");
    }
}
