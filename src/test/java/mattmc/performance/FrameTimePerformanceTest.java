package mattmc.performance;

import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import mattmc.world.level.lighting.SkylightEngine;
import mattmc.world.level.lighting.LightPropagator;
import mattmc.world.level.lighting.WorldLightManager;
import mattmc.world.level.levelgen.WorldGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance tests for simulated frame time measurements.
 * 
 * These tests measure frame time consistency for various operations
 * that would occur during gameplay:
 * - Block updates per frame
 * - Light updates per frame
 * - Chunk access patterns
 * 
 * Note: These tests simulate frame operations without actual rendering.
 */
@DisplayName("Frame Time Performance Tests")
public class FrameTimePerformanceTest extends PerformanceTestBase {
    
    private WorldLightManager worldLightManager;
    private LevelChunk[] testChunks;
    
    @BeforeEach
    void setUp() {
        worldLightManager = new WorldLightManager();
        
        // Pre-generate test chunks
        testChunks = new LevelChunk[9];
        WorldGenerator generator = new WorldGenerator(12345L);
        
        for (int i = 0; i < 9; i++) {
            testChunks[i] = new LevelChunk(i % 3 - 1, i / 3 - 1);
            generator.generateChunkTerrain(testChunks[i], worldLightManager);
        }
    }
    
    @Test
    @DisplayName("Chunk iteration should maintain consistent frame times")
    void testChunkIterationFrameTimes() {
        List<Double> frameTimes = new ArrayList<>();
        
        // Warmup iterations for JIT compilation
        for (int warmup = 0; warmup < 20; warmup++) {
            for (LevelChunk chunk : testChunks) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = 0; y < 128; y++) {
                            chunk.getBlock(x, y, z);
                        }
                    }
                }
            }
        }
        
        // Measure 100 frames
        for (int frame = 0; frame < 100; frame++) {
            long startTime = System.nanoTime();
            
            // Iterate through chunks like renderer would
            for (LevelChunk chunk : testChunks) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = 0; y < 128; y++) {
                            chunk.getBlock(x, y, z);
                        }
                    }
                }
            }
            
            long endTime = System.nanoTime();
            frameTimes.add((endTime - startTime) / 1_000_000.0);
        }
        
        FrameTimeMetrics metrics = new FrameTimeMetrics(frameTimes);
        System.out.println(metrics);
        
        // 99th percentile should be less than 5x average (allowing for GC pauses)
        // Note: CI environments may have more variability due to shared resources
        double spikeRatio = metrics.percentile99Ms / metrics.avgFrameTimeMs;
        assertTrue(spikeRatio < 5.0,
            "Frame time spike ratio was " + spikeRatio + ", expected < 5.0");
    }
    
    @Test
    @DisplayName("Block updates should not cause frame time spikes")
    void testBlockUpdateFrameTimes() {
        LevelChunk chunk = testChunks[4]; // Center chunk
        chunk.setWorldLightManager(worldLightManager);
        
        List<Double> frameTimes = new ArrayList<>();
        int y = LevelChunk.worldYToChunkY(70);
        
        // Simulate 100 frames with block updates
        for (int frame = 0; frame < 100; frame++) {
            long startTime = System.nanoTime();
            
            // Typical frame: read some blocks, modify 1-5 blocks
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    chunk.getBlock(x, y, z);
                }
            }
            
            // Place/remove a few blocks
            int numUpdates = (frame % 5) + 1;
            for (int i = 0; i < numUpdates; i++) {
                int x = (frame + i * 3) % 16;
                int z = (frame + i * 7) % 16;
                chunk.setBlock(x, y, z, Blocks.STONE);
            }
            
            long endTime = System.nanoTime();
            frameTimes.add((endTime - startTime) / 1_000_000.0);
        }
        
        FrameTimeMetrics metrics = new FrameTimeMetrics(frameTimes);
        System.out.println(metrics);
        
        // Average frame time should be under 16ms (60 FPS target)
        assertTrue(metrics.avgFrameTimeMs < 16.0,
            "Average frame time was " + metrics.avgFrameTimeMs + "ms, expected < 16ms");
    }
    
    @Test
    @DisplayName("Light propagation should not cause major frame drops")
    void testLightPropagationFrameTimes() {
        LevelChunk chunk = new LevelChunk(0, 0);
        chunk.generateFlatTerrain(64);
        chunk.setWorldLightManager(worldLightManager);
        
        LightPropagator propagator = new LightPropagator();
        int y = LevelChunk.worldYToChunkY(70);
        
        List<Double> frameTimes = new ArrayList<>();
        
        // Simulate 100 frames with light changes
        for (int frame = 0; frame < 100; frame++) {
            long startTime = System.nanoTime();
            
            // Every few frames, add/remove a light source
            if (frame % 10 == 0) {
                int x = (frame / 10) % 16;
                int z = ((frame / 10) * 3) % 16;
                propagator.addBlockLightRGB(chunk, x, y, z, 14, 14, 14);
            } else if (frame % 10 == 5) {
                int x = ((frame - 5) / 10) % 16;
                int z = (((frame - 5) / 10) * 3) % 16;
                propagator.removeBlockLight(chunk, x, y, z);
            }
            
            // Normal frame work: read light values
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    chunk.getSkyLight(x, y, z);
                    chunk.getBlockLightI(x, y, z);
                }
            }
            
            long endTime = System.nanoTime();
            frameTimes.add((endTime - startTime) / 1_000_000.0);
        }
        
        FrameTimeMetrics metrics = new FrameTimeMetrics(frameTimes);
        System.out.println(metrics);
        
        // 95th percentile should be under 16ms
        assertTrue(metrics.percentile95Ms < 16.0,
            "95th percentile frame time was " + metrics.percentile95Ms + "ms, expected < 16ms");
    }
    
    @Test
    @DisplayName("Frame times should be stable over extended period")
    void testLongTermFrameTimeStability() {
        List<Double> frameTimes = new ArrayList<>();
        LevelChunk chunk = testChunks[4];
        int y = LevelChunk.worldYToChunkY(64);
        
        // Simulate 500 frames (about 8 seconds at 60 FPS)
        for (int frame = 0; frame < 500; frame++) {
            long startTime = System.nanoTime();
            
            // Mixed workload
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    chunk.getBlock(x, y, z);
                }
            }
            
            if (frame % 20 == 0) {
                // Occasional block update
                chunk.setBlock(frame % 16, y, (frame / 16) % 16, Blocks.DIRT);
            }
            
            long endTime = System.nanoTime();
            frameTimes.add((endTime - startTime) / 1_000_000.0);
        }
        
        // Split into early and late frames
        List<Double> earlyFrames = frameTimes.subList(0, 100);
        List<Double> lateFrames = frameTimes.subList(400, 500);
        
        FrameTimeMetrics earlyMetrics = new FrameTimeMetrics(earlyFrames);
        FrameTimeMetrics lateMetrics = new FrameTimeMetrics(lateFrames);
        
        System.out.println("Early frames (0-100):");
        System.out.println(earlyMetrics);
        System.out.println("\nLate frames (400-500):");
        System.out.println(lateMetrics);
        
        // Late frames should not be significantly slower than early frames
        double degradationRatio = lateMetrics.avgFrameTimeMs / earlyMetrics.avgFrameTimeMs;
        assertTrue(degradationRatio < 1.5,
            "Frame time degradation ratio was " + degradationRatio + ", expected < 1.5");
    }
    
    @Test
    @DisplayName("FPS should meet target during typical operations")
    void testTargetFPS() {
        LevelChunk chunk = testChunks[4];
        int y = LevelChunk.worldYToChunkY(64);
        
        List<Double> frameTimes = new ArrayList<>();
        
        // Simulate 60 frames (1 second at 60 FPS)
        for (int frame = 0; frame < 60; frame++) {
            long startTime = System.nanoTime();
            
            // Typical frame operations
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    chunk.getBlock(x, y, z);
                    chunk.getSkyLight(x, y, z);
                }
            }
            
            long endTime = System.nanoTime();
            frameTimes.add((endTime - startTime) / 1_000_000.0);
        }
        
        FrameTimeMetrics metrics = new FrameTimeMetrics(frameTimes);
        System.out.println(metrics);
        
        // Should achieve at least 60 FPS
        assertTrue(metrics.fps >= 60.0,
            "FPS was " + metrics.fps + ", expected >= 60");
    }
    
    @Test
    @DisplayName("Frame time jitter should be minimal")
    void testFrameTimeJitter() {
        LevelChunk chunk = testChunks[4];
        int y = LevelChunk.worldYToChunkY(64);
        
        List<Double> frameTimes = new ArrayList<>();
        
        // Simulate 100 frames
        for (int frame = 0; frame < 100; frame++) {
            long startTime = System.nanoTime();
            
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    chunk.getBlock(x, y, z);
                }
            }
            
            long endTime = System.nanoTime();
            frameTimes.add((endTime - startTime) / 1_000_000.0);
        }
        
        // Calculate jitter (frame-to-frame variance)
        List<Double> deltas = new ArrayList<>();
        for (int i = 1; i < frameTimes.size(); i++) {
            deltas.add(Math.abs(frameTimes.get(i) - frameTimes.get(i - 1)));
        }
        
        double avgDelta = deltas.stream().mapToDouble(d -> d).average().orElse(0);
        double maxDelta = deltas.stream().mapToDouble(d -> d).max().orElse(0);
        
        FrameTimeMetrics metrics = new FrameTimeMetrics(frameTimes);
        
        System.out.println(metrics);
        System.out.println("Average frame-to-frame jitter: " + avgDelta + " ms");
        System.out.println("Max frame-to-frame jitter: " + maxDelta + " ms");
        
        // Average jitter should be less than 5ms
        assertTrue(avgDelta < 5.0,
            "Average jitter was " + avgDelta + "ms, expected < 5ms");
    }
    
    @Test
    @DisplayName("Heavy load should not cause complete frame drops")
    void testHeavyLoadFrameDrops() {
        LevelChunk chunk = new LevelChunk(0, 0);
        chunk.setWorldLightManager(worldLightManager);
        
        LightPropagator propagator = new LightPropagator();
        int y = LevelChunk.worldYToChunkY(64);
        
        List<Double> frameTimes = new ArrayList<>();
        
        // Simulate 50 frames with heavy load
        for (int frame = 0; frame < 50; frame++) {
            long startTime = System.nanoTime();
            
            // Heavy work: multiple light sources
            for (int i = 0; i < 10; i++) {
                int x = (frame + i * 2) % 16;
                int z = (frame * 3 + i) % 16;
                propagator.addBlockLightRGB(chunk, x, y, z, 14, 14, 14);
            }
            
            // Read all light values
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int dy = -5; dy <= 5; dy++) {
                        int ty = y + dy;
                        if (ty >= 0 && ty < LevelChunk.HEIGHT) {
                            chunk.getBlockLightI(x, ty, z);
                        }
                    }
                }
            }
            
            // Clean up
            for (int i = 0; i < 10; i++) {
                int x = (frame + i * 2) % 16;
                int z = (frame * 3 + i) % 16;
                propagator.removeBlockLight(chunk, x, y, z);
            }
            
            long endTime = System.nanoTime();
            frameTimes.add((endTime - startTime) / 1_000_000.0);
        }
        
        FrameTimeMetrics metrics = new FrameTimeMetrics(frameTimes);
        System.out.println(metrics);
        
        // Even under heavy load, should not exceed 100ms (10 FPS minimum)
        assertTrue(metrics.maxFrameTimeMs < 100.0,
            "Max frame time was " + metrics.maxFrameTimeMs + "ms, expected < 100ms");
    }
}
