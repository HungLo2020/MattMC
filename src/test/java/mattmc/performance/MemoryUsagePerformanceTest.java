package mattmc.performance;

import mattmc.registries.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance tests for memory usage patterns.
 * 
 * These tests measure:
 * - Heap memory allocation rates
 * - Object creation overhead
 * - Memory pressure during operations
 * - GC impact on performance
 */
@DisplayName("Memory Usage Performance Tests")
public class MemoryUsagePerformanceTest extends PerformanceTestBase {
    
    @Test
    @DisplayName("Chunk memory usage should be predictable")
    void testChunkMemoryUsage() {
        forceGC();
        long baselineMemory = getHeapMemoryUsed();
        
        // Create chunks and measure memory growth
        LevelChunk[] chunks = new LevelChunk[100];
        long[] memoryAfterEach = new long[100];
        
        for (int i = 0; i < 100; i++) {
            chunks[i] = new LevelChunk(i % 10, i / 10);
            chunks[i].generateFlatTerrain(64);
            memoryAfterEach[i] = getHeapMemoryUsed() - baselineMemory;
        }
        
        // Calculate average memory per chunk
        double totalMemoryMB = memoryAfterEach[99] / (1024.0 * 1024.0);
        double avgPerChunkMB = totalMemoryMB / 100;
        
        System.out.println("Total memory for 100 chunks: " + totalMemoryMB + " MB");
        System.out.println("Average memory per chunk: " + avgPerChunkMB + " MB");
        
        // Analyze growth pattern
        long[] deltas = new long[99];
        for (int i = 0; i < 99; i++) {
            deltas[i] = memoryAfterEach[i + 1] - memoryAfterEach[i];
        }
        
        // Calculate variance
        double avgDelta = 0;
        for (long d : deltas) avgDelta += d;
        avgDelta /= deltas.length;
        
        double variance = 0;
        for (long d : deltas) variance += (d - avgDelta) * (d - avgDelta);
        variance /= deltas.length;
        double stdDev = Math.sqrt(variance);
        
        double avgDeltaMB = avgDelta / (1024.0 * 1024.0);
        double stdDevMB = stdDev / (1024.0 * 1024.0);
        
        System.out.println("Average delta: " + avgDeltaMB + " MB");
        System.out.println("Std deviation: " + stdDevMB + " MB");
        
        // Per-chunk memory should be consistent (low variance)
        // Memory should be under 10MB per chunk
        assertTrue(avgPerChunkMB < 10.0,
            "Average memory per chunk was " + avgPerChunkMB + " MB, expected < 10MB");
        
        preventOptimization(chunks);
    }
    
    @Test
    @DisplayName("Block operations should not cause excessive allocations")
    void testBlockOperationAllocations() {
        LevelChunk chunk = new LevelChunk(0, 0);
        chunk.generateFlatTerrain(64);
        
        forceGC();
        long initialMemory = getHeapMemoryUsed();
        
        // Perform many block operations
        int operations = 100000;
        for (int i = 0; i < operations; i++) {
            int x = i % 16;
            int y = (i / 16) % LevelChunk.HEIGHT;
            int z = (i / (16 * LevelChunk.HEIGHT)) % 16;
            
            // Read and write operations
            chunk.getBlock(x, y, z);
            chunk.setBlock(x, y, z, Blocks.STONE);
            chunk.setBlock(x, y, z, Blocks.AIR);
        }
        
        forceGC();
        long finalMemory = getHeapMemoryUsed();
        double memoryDeltaBytes = finalMemory - initialMemory;
        double bytesPerOperation = memoryDeltaBytes / operations;
        
        System.out.println("Memory delta after " + operations + " operations: " + 
                          (memoryDeltaBytes / (1024.0 * 1024.0)) + " MB");
        System.out.println("Bytes per operation: " + bytesPerOperation);
        
        // Should not allocate more than 10 bytes per operation on average
        // (accounting for some internal bookkeeping)
        assertTrue(bytesPerOperation < 100,
            "Bytes per operation was " + bytesPerOperation + ", expected < 100");
    }
    
    @Test
    @DisplayName("Light storage should be memory efficient")
    void testLightStorageMemoryEfficiency() {
        forceGC();
        long baselineMemory = getHeapMemoryUsed();
        
        // Create chunk with full light data
        LevelChunk chunk = new LevelChunk(0, 0);
        chunk.generateFlatTerrain(64);
        
        // Set light values for all blocks
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < LevelChunk.HEIGHT; y++) {
                    chunk.setSkyLight(x, y, z, 15);
                    chunk.setBlockLightRGBI(x, y, z, 7, 7, 7, 7);
                }
            }
        }
        
        long withLightMemory = getHeapMemoryUsed();
        double lightMemoryMB = (withLightMemory - baselineMemory) / (1024.0 * 1024.0);
        
        // Calculate theoretical minimum
        // Each block needs: 4 bits skylight + 16 bits RGBI = 20 bits = 2.5 bytes
        // 16x16x384 blocks = 98304 blocks
        // Minimum = 98304 * 2.5 = 245,760 bytes = 0.234 MB
        double theoreticalMinMB = 98304 * 2.5 / (1024.0 * 1024.0);
        double overhead = lightMemoryMB / theoreticalMinMB;
        
        System.out.println("Light storage memory: " + lightMemoryMB + " MB");
        System.out.println("Theoretical minimum: " + theoreticalMinMB + " MB");
        System.out.println("Overhead factor: " + overhead + "x");
        
        // Should have less than 100x overhead
        assertTrue(overhead < 100.0,
            "Light storage overhead was " + overhead + "x, expected < 100x");
        
        preventOptimization(chunk);
    }
    
    @Test
    @DisplayName("Memory should be released after chunk unload")
    void testMemoryReleaseAfterChunkUnload() {
        forceGC();
        long initialMemory = getHeapMemoryUsed();
        
        // Create many chunks
        {
            LevelChunk[] chunks = new LevelChunk[50];
            for (int i = 0; i < 50; i++) {
                chunks[i] = new LevelChunk(i, 0);
                chunks[i].generateFlatTerrain(64);
            }
            
            long withChunksMemory = getHeapMemoryUsed();
            double chunksMemoryMB = (withChunksMemory - initialMemory) / (1024.0 * 1024.0);
            System.out.println("Memory with 50 chunks: " + chunksMemoryMB + " MB");
        }
        
        // Force GC to release chunk memory
        forceGC();
        forceGC();
        
        long afterReleaseMemory = getHeapMemoryUsed();
        double releasedMemoryMB = (initialMemory - afterReleaseMemory) / (1024.0 * 1024.0);
        double remainingDeltaMB = (afterReleaseMemory - initialMemory) / (1024.0 * 1024.0);
        
        System.out.println("Memory delta after release: " + remainingDeltaMB + " MB");
        
        // Most memory should be released (within 50MB of initial)
        assertTrue(Math.abs(remainingDeltaMB) < 50.0,
            "Memory not properly released, delta was " + remainingDeltaMB + " MB");
    }
    
    @Test
    @DisplayName("High allocation rate should not cause long GC pauses")
    void testGCPausesDuringHighAllocation() {
        // Track GC activity using MXBeans
        List<java.lang.management.GarbageCollectorMXBean> gcBeans = 
            ManagementFactory.getGarbageCollectorMXBeans();
        
        long initialGcCount = 0;
        long initialGcTime = 0;
        for (var gc : gcBeans) {
            initialGcCount += gc.getCollectionCount();
            initialGcTime += gc.getCollectionTime();
        }
        
        long startTime = System.nanoTime();
        
        // Perform operations that allocate heavily
        for (int i = 0; i < 100; i++) {
            LevelChunk chunk = new LevelChunk(i, 0);
            chunk.generateFlatTerrain(64);
            
            // Force some allocations
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    chunk.getBlock(x, 64, z);
                }
            }
        }
        
        long endTime = System.nanoTime();
        double totalTimeMs = (endTime - startTime) / 1_000_000.0;
        
        long finalGcCount = 0;
        long finalGcTime = 0;
        for (var gc : gcBeans) {
            finalGcCount += gc.getCollectionCount();
            finalGcTime += gc.getCollectionTime();
        }
        
        long gcCollections = finalGcCount - initialGcCount;
        long gcTimeMs = finalGcTime - initialGcTime;
        double gcPercentage = (gcTimeMs / totalTimeMs) * 100;
        
        System.out.println("Total time: " + totalTimeMs + " ms");
        System.out.println("GC collections: " + gcCollections);
        System.out.println("GC time: " + gcTimeMs + " ms");
        System.out.println("GC percentage: " + gcPercentage + "%");
        
        // GC should not consume more than 20% of execution time
        assertTrue(gcPercentage < 20.0,
            "GC consumed " + gcPercentage + "% of execution time, expected < 20%");
    }
    
    @Test
    @DisplayName("Memory pools should not be exhausted during normal operation")
    void testMemoryPoolUsage() {
        List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
        
        // Perform memory-intensive operations
        for (int i = 0; i < 50; i++) {
            LevelChunk chunk = new LevelChunk(i, 0);
            chunk.generateFlatTerrain(64);
            preventOptimization(chunk);
        }
        
        System.out.println("Memory Pool Status:");
        boolean anyExhausted = false;
        
        for (MemoryPoolMXBean pool : pools) {
            if (pool.getType() == java.lang.management.MemoryType.HEAP) {
                java.lang.management.MemoryUsage usage = pool.getUsage();
                double usedMB = usage.getUsed() / (1024.0 * 1024.0);
                double maxMB = usage.getMax() > 0 ? usage.getMax() / (1024.0 * 1024.0) : -1;
                double usagePercent = maxMB > 0 ? (usedMB / maxMB) * 100 : -1;
                
                System.out.printf("  %s: %.2f MB / %.2f MB (%.1f%%)%n", 
                                pool.getName(), usedMB, maxMB, usagePercent);
                
                // Check if any pool is over 90% used
                if (usagePercent > 90) {
                    anyExhausted = true;
                }
            }
        }
        
        assertFalse(anyExhausted,
            "One or more memory pools exceeded 90% usage");
    }
    
    @Test
    @DisplayName("String allocations during operations should be minimized")
    void testStringAllocationMinimization() {
        LevelChunk chunk = new LevelChunk(0, 0);
        chunk.generateFlatTerrain(64);
        
        forceGC();
        long initialMemory = getHeapMemoryUsed();
        
        // Operations that might create strings
        for (int i = 0; i < 10000; i++) {
            chunk.chunkX();
            chunk.chunkZ();
            chunk.getBlock(i % 16, 64, i % 16);
            chunk.isDirty();
        }
        
        forceGC();
        long finalMemory = getHeapMemoryUsed();
        double memoryDeltaKB = (finalMemory - initialMemory) / 1024.0;
        
        System.out.println("Memory delta after 10000 operations: " + memoryDeltaKB + " KB");
        
        // Should not allocate significant memory for simple operations
        assertTrue(Math.abs(memoryDeltaKB) < 1024,
            "Memory delta was " + memoryDeltaKB + " KB, expected < 1024 KB");
    }
}
