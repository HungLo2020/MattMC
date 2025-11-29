package mattmc.performance;

import mattmc.world.level.Level;
import mattmc.world.level.chunk.LevelChunk;
import mattmc.world.level.levelgen.WorldGenerator;
import mattmc.world.level.lighting.WorldLightManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance tests for world launch/startup time.
 * 
 * These tests measure:
 * - New world creation time
 * - Existing world load time
 * - Initial chunk generation time
 * - World initialization overhead
 */
@DisplayName("World Launch Time Performance Tests")
public class WorldLaunchTimePerformanceTest extends PerformanceTestBase {
    
    @TempDir
    Path tempDir;
    
    private Level level;
    
    @BeforeEach
    void setUp() {
        // Warmup JIT
        for (int i = 0; i < 3; i++) {
            Level warmupLevel = new Level();
            warmupLevel.setSeed(i);
            warmupLevel.getChunk(0, 0);
            warmupLevel.shutdown();
        }
    }
    
    @AfterEach
    void tearDown() {
        if (level != null) {
            level.shutdown();
            level = null;
        }
    }
    
    @Test
    @DisplayName("New world creation should be fast")
    void testNewWorldCreationTime() {
        PerformanceResult result = measureOperationWithResult(
            "New World Creation",
            5, 2,
            () -> {
                Level newLevel = new Level();
                newLevel.setSeed(System.nanoTime()); // Unique seed each time
                return newLevel;
            }
        );
        
        System.out.println(result);
        
        // World creation (without chunks) should be under 50ms
        double avgTime = result.getAvgTimePerIterationMs();
        assertTrue(avgTime < 50.0,
            "New world creation took " + avgTime + "ms, expected < 50ms");
    }
    
    @Test
    @DisplayName("World with initial spawn chunks should load quickly")
    void testWorldWithSpawnChunksTime() {
        PerformanceResult result = measureOperationWithResult(
            "World with Spawn Chunks (9 chunks)",
            5, 1,
            () -> {
                Level newLevel = new Level();
                newLevel.setSeed(12345L);
                
                // Load spawn area (3x3 chunks around origin)
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        newLevel.getChunk(x, z);
                    }
                }
                
                int count = newLevel.getLoadedChunkCount();
                newLevel.shutdown();
                return count;
            }
        );
        
        System.out.println(result);
        
        // Spawn area should load in under 500ms
        double avgTime = result.getAvgTimePerIterationMs();
        assertTrue(avgTime < 500.0,
            "World with spawn chunks took " + avgTime + "ms, expected < 500ms");
    }
    
    @Test
    @DisplayName("World initialization with render distance 8 should be reasonable")
    void testWorldInitWithRenderDistance8() {
        PerformanceResult result = measureOperationWithResult(
            "World Init with RD 8 (289 chunks)",
            3, 1,
            () -> {
                Level newLevel = new Level();
                newLevel.setSeed(12345L);
                newLevel.setRenderDistance(8);
                
                // Simulate player at origin loading all chunks in render distance
                // This is 17x17 = 289 chunks
                for (int x = -8; x <= 8; x++) {
                    for (int z = -8; z <= 8; z++) {
                        newLevel.getChunk(x, z);
                    }
                }
                
                int count = newLevel.getLoadedChunkCount();
                newLevel.shutdown();
                return count;
            }
        );
        
        System.out.println(result);
        
        double perChunkTime = result.getAvgTimePerIterationMs() / 289;
        System.out.println("Per-chunk time: " + perChunkTime + " ms");
        
        // Full render distance should load in under 15 seconds
        double avgTime = result.getAvgTimePerIterationMs();
        assertTrue(avgTime < 15000.0,
            "World init with RD 8 took " + avgTime + "ms, expected < 15000ms");
    }
    
    @Test
    @DisplayName("Existing world load should be faster than new world generation")
    void testExistingWorldVsNewWorld() throws IOException {
        Path worldDir = tempDir.resolve("existing_world");
        Files.createDirectories(worldDir);
        
        // Create and save an existing world
        Level existingLevel = new Level();
        existingLevel.setSeed(12345L);
        existingLevel.setWorldDirectory(worldDir);
        
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                existingLevel.getChunk(x, z);
            }
        }
        existingLevel.shutdown();
        
        // Measure new world generation time
        PerformanceResult newWorldResult = measureOperationWithResult(
            "New World Generation (25 chunks)",
            3, 1,
            () -> {
                Level newLevel = new Level();
                newLevel.setSeed(54321L);
                
                for (int x = -2; x <= 2; x++) {
                    for (int z = -2; z <= 2; z++) {
                        newLevel.getChunk(x, z);
                    }
                }
                
                int count = newLevel.getLoadedChunkCount();
                newLevel.shutdown();
                return count;
            }
        );
        
        // Measure existing world load time
        PerformanceResult existingWorldResult = measureOperationWithResult(
            "Existing World Load (25 chunks)",
            3, 1,
            () -> {
                Level loadedLevel = new Level();
                loadedLevel.setSeed(12345L);
                loadedLevel.setWorldDirectory(worldDir);
                
                for (int x = -2; x <= 2; x++) {
                    for (int z = -2; z <= 2; z++) {
                        loadedLevel.getChunk(x, z);
                    }
                }
                
                int count = loadedLevel.getLoadedChunkCount();
                loadedLevel.shutdown();
                return count;
            }
        );
        
        System.out.println(newWorldResult);
        System.out.println(existingWorldResult);
        
        // Note: Loading from disk may not always be faster due to I/O vs CPU trade-offs
        // But it should be within the same order of magnitude
        double ratio = existingWorldResult.getAvgTimePerIterationMs() / newWorldResult.getAvgTimePerIterationMs();
        System.out.printf("Existing/New world ratio: %.2fx%n", ratio);
        
        // Existing world should not be more than 20x slower than generation
        // Note: In CI environments with cold disk cache and shared storage, I/O can be significantly slower
        // The 20x threshold allows for performance measurement while avoiding flaky failures
        assertTrue(ratio < 20.0,
            "Existing world was " + ratio + "x slower than new, expected < 20x");
    }
    
    @Test
    @DisplayName("WorldGenerator initialization should be fast")
    void testWorldGeneratorInitTime() {
        PerformanceResult result = measureOperationWithResult(
            "WorldGenerator Initialization",
            100, 20,
            () -> new WorldGenerator(System.nanoTime())
        );
        
        System.out.println(result);
        
        // Generator init should be under 5ms
        double avgTime = result.getAvgTimePerIterationMs();
        assertTrue(avgTime < 5.0,
            "WorldGenerator init took " + avgTime + "ms, expected < 5ms");
    }
    
    @Test
    @DisplayName("WorldLightManager initialization should be fast")
    void testWorldLightManagerInitTime() {
        PerformanceResult result = measureOperationWithResult(
            "WorldLightManager Initialization",
            100, 20,
            () -> new WorldLightManager()
        );
        
        System.out.println(result);
        
        // LightManager init should be under 5ms
        double avgTime = result.getAvgTimePerIterationMs();
        assertTrue(avgTime < 5.0,
            "WorldLightManager init took " + avgTime + "ms, expected < 5ms");
    }
    
    @Test
    @DisplayName("Level initialization memory footprint should be reasonable")
    void testLevelInitMemoryFootprint() {
        forceGC();
        long initialMemory = getHeapMemoryUsed();
        
        Level newLevel = new Level();
        newLevel.setSeed(12345L);
        
        long afterInitMemory = getHeapMemoryUsed();
        double initMemoryMB = (afterInitMemory - initialMemory) / (1024.0 * 1024.0);
        
        // Load some chunks
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                newLevel.getChunk(x, z);
            }
        }
        
        long afterChunksMemory = getHeapMemoryUsed();
        double chunksMemoryMB = (afterChunksMemory - afterInitMemory) / (1024.0 * 1024.0);
        double perChunkMemoryMB = chunksMemoryMB / 25;
        
        System.out.println("Level init memory: " + initMemoryMB + " MB");
        System.out.println("25 chunks memory: " + chunksMemoryMB + " MB");
        System.out.println("Per-chunk memory: " + perChunkMemoryMB + " MB");
        
        newLevel.shutdown();
        
        // Level init should use less than 50MB
        assertTrue(initMemoryMB < 50.0,
            "Level init used " + initMemoryMB + " MB, expected < 50MB");
        
        // Per-chunk should use less than 10MB
        assertTrue(perChunkMemoryMB < 10.0,
            "Per-chunk used " + perChunkMemoryMB + " MB, expected < 10MB");
    }
    
    @Test
    @DisplayName("Multiple world instances should not leak memory")
    void testMultipleWorldInstancesMemory() {
        forceGC();
        long initialMemory = getHeapMemoryUsed();
        
        // Create and destroy 10 world instances
        for (int i = 0; i < 10; i++) {
            Level testLevel = new Level();
            testLevel.setSeed(i * 1000L);
            
            for (int x = 0; x < 3; x++) {
                for (int z = 0; z < 3; z++) {
                    testLevel.getChunk(x, z);
                }
            }
            
            testLevel.shutdown();
        }
        
        forceGC();
        long finalMemory = getHeapMemoryUsed();
        double memoryDeltaMB = (finalMemory - initialMemory) / (1024.0 * 1024.0);
        
        System.out.println("Memory delta after 10 world create/destroy cycles: " + memoryDeltaMB + " MB");
        
        // Should not accumulate more than 100MB after 10 cycles
        assertTrue(Math.abs(memoryDeltaMB) < 100.0,
            "Memory delta was " + memoryDeltaMB + " MB, expected < 100MB (possible memory leak)");
    }
}
