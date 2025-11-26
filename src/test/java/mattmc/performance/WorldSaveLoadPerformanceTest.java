package mattmc.performance;

import mattmc.world.level.Level;
import mattmc.world.level.chunk.LevelChunk;
import mattmc.world.level.chunk.RegionFileCache;
import mattmc.world.level.chunk.ChunkNBT;
import mattmc.world.level.chunk.RegionFile;
import org.junit.jupiter.api.AfterEach;
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
 * Performance tests for world save and load operations.
 * 
 * These tests measure:
 * - World save time for various world sizes
 * - World load time
 * - Save/load with different chunk counts
 * - Incremental save performance
 */
@DisplayName("World Save/Load Performance Tests")
public class WorldSaveLoadPerformanceTest extends PerformanceTestBase {
    
    @TempDir
    Path tempDir;
    
    private Level level;
    
    @BeforeEach
    void setUp() {
        level = new Level();
        level.setSeed(12345L);
    }
    
    @AfterEach
    void tearDown() {
        if (level != null) {
            level.shutdown();
        }
    }
    
    @Test
    @DisplayName("Small world save should be fast (25 chunks)")
    void testSmallWorldSaveTime() throws IOException {
        Path worldDir = tempDir.resolve("small_world");
        Files.createDirectories(worldDir);
        level.setWorldDirectory(worldDir);
        
        // Load 25 chunks (5x5 area)
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                level.getChunk(x, z);
            }
        }
        
        PerformanceResult result = measureOperation(
            "Small World Save (25 chunks)",
            5, 1,
            () -> {
                // Simulate save by converting all chunks to NBT
                for (LevelChunk chunk : level.getLoadedChunks()) {
                    ChunkNBT.toNBT(chunk);
                }
            }
        );
        
        System.out.println(result);
        System.out.println("Loaded chunk count: " + level.getLoadedChunkCount());
        
        // 25 chunks should save in under 1 second
        double avgTime = result.getAvgTimePerIterationMs();
        assertTrue(avgTime < 1000.0,
            "Small world save took " + avgTime + "ms, expected < 1000ms");
    }
    
    @Test
    @DisplayName("Medium world save should complete in reasonable time (100 chunks)")
    void testMediumWorldSaveTime() throws IOException {
        Path worldDir = tempDir.resolve("medium_world");
        Files.createDirectories(worldDir);
        level.setWorldDirectory(worldDir);
        
        // Load 100 chunks (10x10 area)
        for (int x = 0; x < 10; x++) {
            for (int z = 0; z < 10; z++) {
                level.getChunk(x, z);
            }
        }
        
        PerformanceResult result = measureOperation(
            "Medium World Save (100 chunks)",
            3, 1,
            () -> {
                for (LevelChunk chunk : level.getLoadedChunks()) {
                    ChunkNBT.toNBT(chunk);
                }
            }
        );
        
        System.out.println(result);
        System.out.println("Loaded chunk count: " + level.getLoadedChunkCount());
        
        double perChunkTime = result.getAvgTimePerIterationMs() / 100;
        System.out.println("Per-chunk save time: " + perChunkTime + " ms");
        
        // 100 chunks should save in under 3 seconds
        double avgTime = result.getAvgTimePerIterationMs();
        assertTrue(avgTime < 3000.0,
            "Medium world save took " + avgTime + "ms, expected < 3000ms");
    }
    
    @Test
    @DisplayName("World load should be fast")
    void testWorldLoadTime() throws IOException {
        Path worldDir = tempDir.resolve("load_test_world");
        Path regionDir = worldDir.resolve("region");
        Files.createDirectories(regionDir);
        
        // Create and save 50 chunks first
        RegionFileCache cache = new RegionFileCache(regionDir);
        for (int i = 0; i < 50; i++) {
            LevelChunk chunk = new LevelChunk(i % 10, i / 10);
            chunk.generateFlatTerrain(64);
            Map<String, Object> nbt = ChunkNBT.toNBT(chunk);
            RegionFile regionFile = cache.getRegionFile(chunk.chunkX(), chunk.chunkZ());
            regionFile.writeChunk(chunk.chunkX(), chunk.chunkZ(), nbt);
        }
        cache.flush();
        cache.close();
        
        // Now measure load time
        PerformanceResult result = measureOperationWithResult(
            "World Load (50 chunks)",
            3, 1,
            () -> {
                Level testLevel = new Level();
                testLevel.setSeed(12345L);
                testLevel.setWorldDirectory(worldDir);
                
                // Load chunks
                for (int i = 0; i < 50; i++) {
                    testLevel.getChunk(i % 10, i / 10);
                }
                
                int count = testLevel.getLoadedChunkCount();
                testLevel.shutdown();
                return count;
            }
        );
        
        System.out.println(result);
        
        double perChunkTime = result.getAvgTimePerIterationMs() / 50;
        System.out.println("Per-chunk load time: " + perChunkTime + " ms");
        
        // 50 chunks should load in under 3 seconds
        double avgTime = result.getAvgTimePerIterationMs();
        assertTrue(avgTime < 3000.0,
            "World load took " + avgTime + "ms, expected < 3000ms");
    }
    
    @Test
    @DisplayName("Incremental save should be efficient")
    void testIncrementalSaveTime() throws IOException {
        Path worldDir = tempDir.resolve("incremental_world");
        Files.createDirectories(worldDir);
        level.setWorldDirectory(worldDir);
        
        // Load initial chunks
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                level.getChunk(x, z);
            }
        }
        
        // Full save baseline
        PerformanceResult fullSave = measureOperation(
            "Full Save (25 chunks)",
            5, 1,
            () -> {
                for (LevelChunk chunk : level.getLoadedChunks()) {
                    ChunkNBT.toNBT(chunk);
                }
            }
        );
        
        // Mark only a few chunks as dirty and measure incremental save
        int dirtyCount = 5;
        PerformanceResult incrementalSave = measureOperation(
            "Incremental Save (5 dirty chunks)",
            10, 2,
            () -> {
                int saved = 0;
                for (LevelChunk chunk : level.getLoadedChunks()) {
                    if (saved < dirtyCount) {
                        ChunkNBT.toNBT(chunk);
                        saved++;
                    }
                }
            }
        );
        
        System.out.println(fullSave);
        System.out.println(incrementalSave);
        
        // Incremental save should be proportionally faster
        double expectedRatio = 25.0 / 5.0; // 5x faster
        double actualRatio = fullSave.getAvgTimePerIterationMs() / incrementalSave.getAvgTimePerIterationMs();
        
        System.out.printf("Expected speedup: %.1fx, Actual speedup: %.2fx%n", expectedRatio, actualRatio);
        
        // Should be at least 2x faster (accounting for overhead)
        assertTrue(actualRatio > 2.0,
            "Incremental save speedup was only " + actualRatio + "x, expected > 2x");
    }
    
    @Test
    @DisplayName("Save memory usage should be bounded")
    void testSaveMemoryUsage() throws IOException {
        Path worldDir = tempDir.resolve("memory_test_world");
        Files.createDirectories(worldDir);
        level.setWorldDirectory(worldDir);
        
        // Load chunks
        for (int x = 0; x < 10; x++) {
            for (int z = 0; z < 10; z++) {
                level.getChunk(x, z);
            }
        }
        
        forceGC();
        long initialMemory = getHeapMemoryUsed();
        
        // Perform save operation multiple times
        for (int i = 0; i < 10; i++) {
            for (LevelChunk chunk : level.getLoadedChunks()) {
                Map<String, Object> nbt = ChunkNBT.toNBT(chunk);
                preventOptimization(nbt);
            }
        }
        
        forceGC();
        long finalMemory = getHeapMemoryUsed();
        double memoryDeltaMB = (finalMemory - initialMemory) / (1024.0 * 1024.0);
        
        System.out.println("Memory delta after 10 save cycles: " + memoryDeltaMB + " MB");
        
        // Should not accumulate more than 100MB after 10 save cycles
        assertTrue(Math.abs(memoryDeltaMB) < 100.0,
            "Memory delta was " + memoryDeltaMB + " MB, expected < 100MB");
    }
    
    @Test
    @DisplayName("Save performance should be consistent")
    void testSavePerformanceConsistency() throws IOException {
        Path worldDir = tempDir.resolve("consistency_world");
        Files.createDirectories(worldDir);
        level.setWorldDirectory(worldDir);
        
        // Load chunks
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                level.getChunk(x, z);
            }
        }
        
        double[] times = new double[10];
        
        for (int i = 0; i < 10; i++) {
            long start = System.nanoTime();
            for (LevelChunk chunk : level.getLoadedChunks()) {
                ChunkNBT.toNBT(chunk);
            }
            long end = System.nanoTime();
            times[i] = (end - start) / 1_000_000.0;
        }
        
        // Calculate statistics
        double sum = 0;
        for (double t : times) sum += t;
        double mean = sum / times.length;
        
        double variance = 0;
        for (double t : times) variance += (t - mean) * (t - mean);
        variance /= times.length;
        double stdDev = Math.sqrt(variance);
        double cv = (stdDev / mean) * 100;
        
        System.out.println("Save times across 10 iterations:");
        for (int i = 0; i < times.length; i++) {
            System.out.printf("  Iteration %d: %.3f ms%n", i + 1, times[i]);
        }
        System.out.printf("Mean: %.3f ms, StdDev: %.3f ms, CV: %.1f%%%n", mean, stdDev, cv);
        
        // Coefficient of variation should be less than 30%
        assertTrue(cv < 30.0,
            "Save performance variance too high: CV=" + cv + "%");
    }
}
