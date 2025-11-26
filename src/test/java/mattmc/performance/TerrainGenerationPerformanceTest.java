package mattmc.performance;

import mattmc.world.level.chunk.LevelChunk;
import mattmc.world.level.levelgen.WorldGenerator;
import mattmc.world.level.lighting.WorldLightManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance tests for terrain generation.
 * 
 * These tests measure:
 * - Single chunk generation time
 * - Batch chunk generation performance
 * - Noise calculation overhead
 * - Memory allocation during generation
 * - Thread utilization during generation
 */
@DisplayName("Terrain Generation Performance Tests")
public class TerrainGenerationPerformanceTest extends PerformanceTestBase {
    
    private WorldGenerator worldGenerator;
    private WorldLightManager worldLightManager;
    
    @BeforeEach
    void setUp() {
        worldGenerator = new WorldGenerator(12345L);
        worldLightManager = new WorldLightManager();
    }
    
    @Test
    @DisplayName("Single chunk generation should complete within time limit")
    void testSingleChunkGenerationTime() {
        PerformanceResult result = measureOperation(
            "Single Chunk Generation",
            100, // iterations
            10,  // warmup
            () -> {
                LevelChunk chunk = new LevelChunk(0, 0);
                worldGenerator.generateChunkTerrain(chunk, worldLightManager);
            }
        );
        
        System.out.println(result);
        
        // Single chunk should generate in under 50ms on average
        double avgTime = result.getAvgTimePerIterationMs();
        assertTrue(avgTime < 50.0, 
            "Single chunk generation took " + avgTime + "ms, expected < 50ms");
    }
    
    @Test
    @DisplayName("Batch chunk generation should be efficient")
    void testBatchChunkGenerationTime() {
        final int BATCH_SIZE = 25; // 5x5 chunk area
        
        PerformanceResult result = measureOperation(
            "Batch Chunk Generation (25 chunks)",
            10, // iterations
            2,  // warmup
            () -> {
                for (int x = 0; x < 5; x++) {
                    for (int z = 0; z < 5; z++) {
                        LevelChunk chunk = new LevelChunk(x, z);
                        worldGenerator.generateChunkTerrain(chunk, worldLightManager);
                    }
                }
            }
        );
        
        System.out.println(result);
        
        // 25 chunks should generate in under 1 second
        double avgTime = result.getAvgTimePerIterationMs();
        assertTrue(avgTime < 1000.0, 
            "Batch chunk generation took " + avgTime + "ms, expected < 1000ms");
        
        // Per-chunk time should remain consistent
        double perChunkTime = avgTime / BATCH_SIZE;
        assertTrue(perChunkTime < 50.0,
            "Per-chunk generation time was " + perChunkTime + "ms, expected < 50ms");
    }
    
    @Test
    @DisplayName("Terrain height calculation should be fast")
    void testTerrainHeightCalculationSpeed() {
        final int SAMPLES = 10000;
        
        PerformanceResult result = measureOperationWithResult(
            "Terrain Height Calculation (10000 samples)",
            10, // iterations
            5,  // warmup
            () -> {
                int sum = 0;
                for (int i = 0; i < SAMPLES; i++) {
                    int x = (i * 7) % 10000;
                    int z = (i * 13) % 10000;
                    sum += worldGenerator.getTerrainHeight(x, z);
                }
                return sum;
            }
        );
        
        System.out.println(result);
        
        // 10000 height samples should take less than 100ms
        double avgTime = result.getAvgTimePerIterationMs();
        double perSampleMicros = (avgTime * 1000) / SAMPLES;
        
        assertTrue(avgTime < 100.0,
            "Height calculation took " + avgTime + "ms for " + SAMPLES + " samples");
        
        System.out.println("Per-sample time: " + perSampleMicros + " μs");
    }
    
    @Test
    @DisplayName("Chunk generation memory usage should be reasonable")
    void testChunkGenerationMemoryUsage() {
        forceGC();
        long initialMemory = getHeapMemoryUsed();
        
        // Generate 100 chunks
        LevelChunk[] chunks = new LevelChunk[100];
        for (int i = 0; i < 100; i++) {
            chunks[i] = new LevelChunk(i % 10, i / 10);
            worldGenerator.generateChunkTerrain(chunks[i], worldLightManager);
        }
        
        long finalMemory = getHeapMemoryUsed();
        double memoryUsedMB = (finalMemory - initialMemory) / (1024.0 * 1024.0);
        
        System.out.println("Memory used for 100 chunks: " + memoryUsedMB + " MB");
        System.out.println("Memory per chunk: " + (memoryUsedMB / 100) + " MB");
        
        // Each chunk should use less than 10MB on average
        assertTrue(memoryUsedMB / 100 < 10.0,
            "Memory per chunk was " + (memoryUsedMB / 100) + " MB, expected < 10MB");
        
        // Prevent optimization
        preventOptimization(chunks);
    }
    
    @Test
    @DisplayName("Different seeds should have consistent generation time")
    void testGenerationTimeConsistencyAcrossSeeds() {
        double[] times = new double[5];
        
        for (int seedOffset = 0; seedOffset < 5; seedOffset++) {
            WorldGenerator gen = new WorldGenerator(12345L + seedOffset * 1000);
            
            PerformanceResult result = measureOperation(
                "Seed " + (12345L + seedOffset * 1000),
                50,
                5,
                () -> {
                    LevelChunk chunk = new LevelChunk(0, 0);
                    gen.generateChunkTerrain(chunk, worldLightManager);
                }
            );
            
            times[seedOffset] = result.getAvgTimePerIterationMs();
        }
        
        // Calculate variance
        double sum = 0;
        for (double t : times) sum += t;
        double mean = sum / times.length;
        
        double variance = 0;
        for (double t : times) variance += (t - mean) * (t - mean);
        variance /= times.length;
        
        double stdDev = Math.sqrt(variance);
        double coefficientOfVariation = (stdDev / mean) * 100;
        
        System.out.println("Generation times across seeds:");
        for (int i = 0; i < times.length; i++) {
            System.out.printf("  Seed %d: %.3f ms%n", i, times[i]);
        }
        System.out.printf("Mean: %.3f ms, StdDev: %.3f ms, CV: %.1f%%%n", mean, stdDev, coefficientOfVariation);
        
        // Coefficient of variation should be less than 20%
        assertTrue(coefficientOfVariation < 20.0,
            "Generation time variance too high: CV=" + coefficientOfVariation + "%");
    }
    
    @Test
    @DisplayName("Far coordinates should not significantly impact generation time")
    void testFarCoordinateGenerationPerformance() {
        // Near origin
        PerformanceResult nearResult = measureOperation(
            "Near Origin (0,0)",
            50, 5,
            () -> {
                LevelChunk chunk = new LevelChunk(0, 0);
                worldGenerator.generateChunkTerrain(chunk, worldLightManager);
            }
        );
        
        // Far from origin (but within valid range)
        PerformanceResult farResult = measureOperation(
            "Far from Origin (10000, 10000)",
            50, 5,
            () -> {
                LevelChunk chunk = new LevelChunk(10000, 10000);
                worldGenerator.generateChunkTerrain(chunk, worldLightManager);
            }
        );
        
        System.out.println(nearResult);
        System.out.println(farResult);
        
        // Far coordinates should be at most 2x slower than near
        double ratio = farResult.getAvgTimePerIterationMs() / nearResult.getAvgTimePerIterationMs();
        assertTrue(ratio < 2.0,
            "Far coordinate generation was " + ratio + "x slower than near, expected < 2x");
    }
}
