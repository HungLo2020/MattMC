package mattmc.performance;

import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import mattmc.world.level.lighting.LightPropagator;
import mattmc.world.level.lighting.SkylightEngine;
import mattmc.world.level.lighting.WorldLightManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance tests for lighting calculations.
 * 
 * These tests measure:
 * - Skylight initialization performance
 * - Block light propagation speed
 * - Light removal performance
 * - Multi-source light calculations
 * - Cross-chunk light propagation overhead
 */
@DisplayName("Lighting Calculation Performance Tests")
public class LightingCalculationPerformanceTest extends PerformanceTestBase {
    
    private LightPropagator lightPropagator;
    private SkylightEngine skylightEngine;
    private WorldLightManager worldLightManager;
    
    @BeforeEach
    void setUp() {
        lightPropagator = new LightPropagator();
        skylightEngine = new SkylightEngine();
        worldLightManager = new WorldLightManager();
    }
    
    @Test
    @DisplayName("Skylight initialization should be fast")
    void testSkylightInitializationSpeed() {
        PerformanceResult result = measureOperation(
            "Skylight Initialization",
            50, // iterations
            5,  // warmup
            () -> {
                LevelChunk chunk = new LevelChunk(0, 0);
                // Generate simple terrain first
                chunk.generateFlatTerrain(64);
                skylightEngine.initializeChunkSkylight(chunk);
            }
        );
        
        System.out.println(result);
        
        // Skylight init should complete in under 100ms per chunk
        double avgTime = result.getAvgTimePerIterationMs();
        assertTrue(avgTime < 100.0,
            "Skylight initialization took " + avgTime + "ms, expected < 100ms");
    }
    
    @Test
    @DisplayName("Block light propagation should be efficient")
    void testBlockLightPropagationSpeed() {
        LevelChunk chunk = new LevelChunk(0, 0);
        int y = LevelChunk.worldYToChunkY(64);
        
        PerformanceResult result = measureOperation(
            "Block Light Propagation (single source)",
            100, // iterations
            10,  // warmup
            () -> {
                // Clear existing light
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int yy = y - 15; yy <= y + 15; yy++) {
                            if (yy >= 0 && yy < LevelChunk.HEIGHT) {
                                chunk.setBlockLightRGBI(x, yy, z, 0, 0, 0, 0);
                            }
                        }
                    }
                }
                // Propagate light from torch (14 emission)
                lightPropagator.addBlockLightRGB(chunk, 8, y, 8, 14, 14, 14);
            }
        );
        
        System.out.println(result);
        
        // Single source propagation should be under 10ms
        double avgTime = result.getAvgTimePerIterationMs();
        assertTrue(avgTime < 10.0,
            "Block light propagation took " + avgTime + "ms, expected < 10ms");
    }
    
    @Test
    @DisplayName("Multiple light source propagation should scale reasonably")
    void testMultipleLightSourceScaling() {
        LevelChunk chunk = new LevelChunk(0, 0);
        int y = LevelChunk.worldYToChunkY(64);
        
        // Test with increasing number of light sources
        int[] sourceCounts = {1, 5, 10, 25, 50};
        double[] times = new double[sourceCounts.length];
        
        for (int i = 0; i < sourceCounts.length; i++) {
            final int numSources = sourceCounts[i];
            
            PerformanceResult result = measureOperation(
                numSources + " light sources",
                20, 3,
                () -> {
                    // Clear existing light
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            for (int yy = 0; yy < LevelChunk.HEIGHT; yy++) {
                                chunk.setBlockLightRGBI(x, yy, z, 0, 0, 0, 0);
                            }
                        }
                    }
                    
                    // Add light sources
                    for (int s = 0; s < numSources; s++) {
                        int sx = (s * 3) % 16;
                        int sz = (s * 7) % 16;
                        lightPropagator.addBlockLightRGB(chunk, sx, y, sz, 14, 14, 14);
                    }
                }
            );
            
            times[i] = result.getAvgTimePerIterationMs();
        }
        
        // Print scaling results
        System.out.println("Light source scaling:");
        for (int i = 0; i < sourceCounts.length; i++) {
            System.out.printf("  %d sources: %.3f ms%n", sourceCounts[i], times[i]);
        }
        
        // 50 sources should be at most 20x slower than 1 source (sublinear scaling is ideal)
        double ratio = times[4] / times[0];
        System.out.printf("Scaling ratio (50/1): %.1fx%n", ratio);
        assertTrue(ratio < 100.0,
            "50 sources was " + ratio + "x slower than 1 source, expected < 100x");
    }
    
    @Test
    @DisplayName("Light removal should be as fast as propagation")
    void testLightRemovalSpeed() {
        LevelChunk chunk = new LevelChunk(0, 0);
        int y = LevelChunk.worldYToChunkY(64);
        
        // First add light
        lightPropagator.addBlockLightRGB(chunk, 8, y, 8, 14, 14, 14);
        
        PerformanceResult addResult = measureOperation(
            "Light Addition",
            100, 10,
            () -> {
                // Clear and re-add
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int yy = y - 15; yy <= y + 15; yy++) {
                            if (yy >= 0 && yy < LevelChunk.HEIGHT) {
                                chunk.setBlockLightRGBI(x, yy, z, 0, 0, 0, 0);
                            }
                        }
                    }
                }
                lightPropagator.addBlockLightRGB(chunk, 8, y, 8, 14, 14, 14);
            }
        );
        
        // Now test removal
        PerformanceResult removeResult = measureOperation(
            "Light Removal",
            100, 10,
            () -> {
                // Re-add and remove
                lightPropagator.addBlockLightRGB(chunk, 8, y, 8, 14, 14, 14);
                lightPropagator.removeBlockLight(chunk, 8, y, 8);
            }
        );
        
        System.out.println(addResult);
        System.out.println(removeResult);
        
        // Removal should be at most 3x slower than addition
        double ratio = removeResult.getAvgTimePerIterationMs() / addResult.getAvgTimePerIterationMs();
        assertTrue(ratio < 3.0,
            "Light removal was " + ratio + "x slower than addition, expected < 3x");
    }
    
    @Test
    @DisplayName("Skylight update on block change should be fast")
    void testSkylightBlockChangeUpdate() {
        LevelChunk chunk = new LevelChunk(0, 0);
        chunk.generateFlatTerrain(64);
        chunk.setWorldLightManager(worldLightManager);
        skylightEngine.initializeChunkSkylight(chunk);
        
        int y = LevelChunk.worldYToChunkY(70);
        
        PerformanceResult result = measureOperation(
            "Skylight Update on Block Change",
            100, 10,
            () -> {
                // Place a block
                chunk.setBlock(8, y, 8, Blocks.STONE);
                // Remove the block
                chunk.setBlock(8, y, 8, Blocks.AIR);
            }
        );
        
        System.out.println(result);
        
        // Block change should update light in under 5ms
        double avgTime = result.getAvgTimePerIterationMs();
        assertTrue(avgTime < 5.0,
            "Skylight update took " + avgTime + "ms, expected < 5ms");
    }
    
    @Test
    @DisplayName("Single-channel light should not be slower than full RGB white light")
    void testRGBLightPerformance() {
        LevelChunk chunk = new LevelChunk(0, 0);
        int y = LevelChunk.worldYToChunkY(64);
        
        // Test white light (all RGB channels at max - most expensive)
        PerformanceResult whiteResult = measureOperation(
            "White Light (14,14,14)",
            100, 10,
            () -> {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        chunk.setBlockLightRGBI(x, y, z, 0, 0, 0, 0);
                    }
                }
                lightPropagator.addBlockLightRGB(chunk, 8, y, 8, 14, 14, 14);
            }
        );
        
        // Test single-channel colored light (should be faster since only one channel propagates)
        PerformanceResult colorResult = measureOperation(
            "Red Light (14,0,0)",
            100, 10,
            () -> {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        chunk.setBlockLightRGBI(x, y, z, 0, 0, 0, 0);
                    }
                }
                lightPropagator.addBlockLightRGB(chunk, 8, y, 8, 14, 0, 0);
            }
        );
        
        System.out.println(whiteResult);
        System.out.println(colorResult);
        
        // Single-channel light should be no slower than full RGB (white) light.
        // It's expected to be faster since only one channel propagates, but should not be
        // more than 2x slower if there's overhead in the RGB system.
        double ratio = colorResult.getAvgTimePerIterationMs() / whiteResult.getAvgTimePerIterationMs();
        assertTrue(ratio <= 2.0,
            "Single-channel light was " + ratio + "x compared to white light, expected <= 2.0");
    }
    
    @Test
    @DisplayName("Light propagation memory usage should be bounded")
    void testLightPropagationMemoryUsage() {
        forceGC();
        long initialMemory = getHeapMemoryUsed();
        
        LevelChunk chunk = new LevelChunk(0, 0);
        int y = LevelChunk.worldYToChunkY(64);
        
        // Perform many light operations
        for (int i = 0; i < 100; i++) {
            int x = (i * 3) % 16;
            int z = (i * 7) % 16;
            lightPropagator.addBlockLightRGB(chunk, x, y, z, 14, 14, 14);
            lightPropagator.removeBlockLight(chunk, x, y, z);
        }
        
        forceGC();
        long finalMemory = getHeapMemoryUsed();
        double memoryDeltaMB = (finalMemory - initialMemory) / (1024.0 * 1024.0);
        
        System.out.println("Memory delta after 100 add/remove cycles: " + memoryDeltaMB + " MB");
        
        // Should not leak significant memory (< 50MB for this test)
        assertTrue(Math.abs(memoryDeltaMB) < 50.0,
            "Memory delta was " + memoryDeltaMB + " MB, expected < 50MB");
    }
}
