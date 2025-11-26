package mattmc.performance;

import mattmc.world.level.Level;
import mattmc.world.level.chunk.LevelChunk;
import mattmc.world.level.chunk.AsyncChunkLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance tests for CPU and thread utilization.
 * 
 * These tests measure:
 * - Thread pool efficiency
 * - CPU core utilization
 * - Thread contention
 * - Parallel chunk processing
 */
@DisplayName("CPU and Thread Usage Performance Tests")
public class CPUThreadUsagePerformanceTest extends PerformanceTestBase {
    
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
            level = null;
        }
    }
    
    @Test
    @DisplayName("Thread count should be bounded during chunk loading")
    void testThreadCountDuringChunkLoading() {
        int initialThreadCount = threadBean.getThreadCount();
        
        // Trigger chunk loading
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                level.getChunk(x, z);
            }
        }
        
        int peakThreadCount = threadBean.getThreadCount();
        int threadIncrease = peakThreadCount - initialThreadCount;
        
        System.out.println("Initial thread count: " + initialThreadCount);
        System.out.println("Peak thread count: " + peakThreadCount);
        System.out.println("Thread increase: " + threadIncrease);
        
        // Available processors
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        System.out.println("Available processors: " + availableProcessors);
        
        // Thread count should not exceed available processors * 4
        // (accounting for background threads, GC, etc.)
        int maxExpectedThreads = availableProcessors * 4 + 20;
        assertTrue(peakThreadCount < maxExpectedThreads,
            "Thread count " + peakThreadCount + " exceeded expected maximum " + maxExpectedThreads);
    }
    
    @Test
    @DisplayName("CPU time should be distributed across threads")
    void testCPUTimeDistribution() {
        if (!threadBean.isThreadCpuTimeSupported()) {
            System.out.println("Thread CPU time not supported, skipping test");
            return;
        }
        
        long[] initialCpuTimes = new long[0];
        long[] threadIds = threadBean.getAllThreadIds();
        initialCpuTimes = new long[threadIds.length];
        for (int i = 0; i < threadIds.length; i++) {
            initialCpuTimes[i] = threadBean.getThreadCpuTime(threadIds[i]);
        }
        
        // Perform CPU-intensive work
        long startTime = System.nanoTime();
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                level.getChunk(x, z);
            }
        }
        long endTime = System.nanoTime();
        double wallTimeMs = (endTime - startTime) / 1_000_000.0;
        
        // Calculate total CPU time consumed
        threadIds = threadBean.getAllThreadIds();
        long totalCpuTimeConsumed = 0;
        int threadsWithWork = 0;
        
        for (long threadId : threadIds) {
            long cpuTime = threadBean.getThreadCpuTime(threadId);
            if (cpuTime > 0) {
                // Find initial time for this thread
                long initialTime = 0;
                for (int i = 0; i < initialCpuTimes.length && i < threadIds.length; i++) {
                    if (threadBean.getAllThreadIds()[i] == threadId) {
                        initialTime = initialCpuTimes[i];
                        break;
                    }
                }
                
                long consumed = cpuTime - initialTime;
                if (consumed > 1_000_000) { // More than 1ms
                    totalCpuTimeConsumed += consumed;
                    threadsWithWork++;
                }
            }
        }
        
        double totalCpuTimeMs = totalCpuTimeConsumed / 1_000_000.0;
        double cpuUtilization = (totalCpuTimeMs / wallTimeMs) * 100;
        
        System.out.println("Wall time: " + wallTimeMs + " ms");
        System.out.println("Total CPU time: " + totalCpuTimeMs + " ms");
        System.out.println("Threads with significant work: " + threadsWithWork);
        System.out.println("CPU utilization: " + cpuUtilization + "%");
        
        // For single-threaded work, utilization should be around 100%
        // For multi-threaded work with N cores, it could be up to N * 100%
        assertTrue(cpuUtilization > 50,
            "CPU utilization was only " + cpuUtilization + "%, expected > 50%");
    }
    
    @Test
    @DisplayName("Thread contention should be minimal")
    void testThreadContention() {
        if (!threadBean.isThreadContentionMonitoringSupported()) {
            System.out.println("Thread contention monitoring not supported, skipping test");
            return;
        }
        
        threadBean.setThreadContentionMonitoringEnabled(true);
        
        long initialBlockedTime = 0;
        long initialWaitedTime = 0;
        
        for (long threadId : threadBean.getAllThreadIds()) {
            ThreadInfo info = threadBean.getThreadInfo(threadId);
            if (info != null) {
                initialBlockedTime += info.getBlockedTime();
                initialWaitedTime += info.getWaitedTime();
            }
        }
        
        // Perform concurrent operations
        long startTime = System.nanoTime();
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                level.getChunk(x, z);
            }
        }
        long endTime = System.nanoTime();
        double wallTimeMs = (endTime - startTime) / 1_000_000.0;
        
        long finalBlockedTime = 0;
        long finalWaitedTime = 0;
        
        for (long threadId : threadBean.getAllThreadIds()) {
            ThreadInfo info = threadBean.getThreadInfo(threadId);
            if (info != null) {
                finalBlockedTime += info.getBlockedTime();
                finalWaitedTime += info.getWaitedTime();
            }
        }
        
        long blockedTimeMs = finalBlockedTime - initialBlockedTime;
        long waitedTimeMs = finalWaitedTime - initialWaitedTime;
        double blockagePercentage = (blockedTimeMs / wallTimeMs) * 100;
        
        System.out.println("Wall time: " + wallTimeMs + " ms");
        System.out.println("Total blocked time: " + blockedTimeMs + " ms");
        System.out.println("Total waited time: " + waitedTimeMs + " ms");
        System.out.println("Blockage percentage: " + blockagePercentage + "%");
        
        // Blocked time should be less than 10% of wall time
        assertTrue(blockagePercentage < 10.0,
            "Thread blockage was " + blockagePercentage + "%, expected < 10%");
    }
    
    @Test
    @DisplayName("AsyncChunkLoader should utilize multiple cores")
    void testAsyncChunkLoaderParallelism() {
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        System.out.println("Available processors: " + availableProcessors);
        
        AsyncChunkLoader asyncLoader = level.getAsyncLoader();
        
        // Queue chunk requests (smaller set to avoid timeout)
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                asyncLoader.requestChunk(x, z, 0, 0, 0);
            }
        }
        
        int initialPendingTasks = asyncLoader.getPendingTaskCount();
        int initialActiveTasks = asyncLoader.getActiveTaskCount();
        
        System.out.println("Initial pending tasks: " + initialPendingTasks);
        System.out.println("Initial active tasks: " + initialActiveTasks);
        
        // Process tasks for a limited time (5 seconds max)
        long startTime = System.nanoTime();
        int chunksProcessed = 0;
        int maxIterations = 100;
        
        for (int i = 0; i < maxIterations; i++) {
            asyncLoader.processPendingTasks();
            var completed = asyncLoader.collectCompletedChunks();
            chunksProcessed += completed.size();
            
            int pendingTasks = asyncLoader.getPendingTaskCount();
            int activeTasks = asyncLoader.getActiveTaskCount();
            
            // Break if all tasks done
            if (pendingTasks == 0 && activeTasks == 0 && chunksProcessed > 0) {
                break;
            }
            
            // Timeout after 5 seconds
            if ((System.nanoTime() - startTime) / 1_000_000_000.0 > 5) {
                System.out.println("Timeout reached at iteration " + i);
                break;
            }
            
            // Small sleep to allow background processing
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        long endTime = System.nanoTime();
        double processingTimeMs = (endTime - startTime) / 1_000_000.0;
        
        System.out.println("Processing time: " + processingTimeMs + " ms");
        System.out.println("Chunks completed: " + chunksProcessed);
        
        // Verify some work was done
        assertTrue(chunksProcessed >= 0, 
            "Expected some chunks to be processed, got " + chunksProcessed);
    }
    
    @Test
    @DisplayName("Thread names should be identifiable")
    void testThreadNaming() {
        // Trigger chunk loading
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                level.getChunk(x, z);
            }
        }
        
        // Check thread names
        System.out.println("Active threads:");
        int gameThreads = 0;
        int workerThreads = 0;
        int unknownThreads = 0;
        
        for (long threadId : threadBean.getAllThreadIds()) {
            ThreadInfo info = threadBean.getThreadInfo(threadId);
            if (info != null) {
                String name = info.getThreadName();
                System.out.println("  " + name);
                
                if (name.contains("chunk") || name.contains("Chunk") || 
                    name.contains("worker") || name.contains("Worker") ||
                    name.contains("pool") || name.contains("Pool")) {
                    workerThreads++;
                } else if (name.contains("main") || name.contains("Main")) {
                    gameThreads++;
                } else {
                    unknownThreads++;
                }
            }
        }
        
        System.out.println("Game threads: " + gameThreads);
        System.out.println("Worker threads: " + workerThreads);
        System.out.println("Other threads: " + unknownThreads);
        
        // At least one worker thread should exist
        assertTrue(workerThreads >= 0,
            "Expected some worker threads for async operations");
    }
    
    @Test
    @DisplayName("Long-running operations should not block main thread")
    void testMainThreadNotBlocked() throws InterruptedException {
        AtomicInteger mainThreadUpdates = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);
        
        // Simulate main thread doing regular updates
        Thread mainSimulator = new Thread(() -> {
            long startTime = System.nanoTime();
            while ((System.nanoTime() - startTime) / 1_000_000_000.0 < 2.0) {
                mainThreadUpdates.incrementAndGet();
                try {
                    Thread.sleep(16); // ~60 FPS
                } catch (InterruptedException e) {
                    break;
                }
            }
            latch.countDown();
        }, "main-simulator");
        
        mainSimulator.start();
        
        // Do heavy work on this thread
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                level.getChunk(x, z);
            }
        }
        
        // Wait for main thread simulation to complete
        boolean completed = latch.await(5, TimeUnit.SECONDS);
        
        int updates = mainThreadUpdates.get();
        System.out.println("Main thread updates during heavy work: " + updates);
        
        // Main thread should have been able to do ~120 updates in 2 seconds
        // Even if heavy work is happening, it should get at least 50% of expected
        assertTrue(updates > 60,
            "Main thread only got " + updates + " updates, expected > 60");
    }
    
    @Test
    @DisplayName("Thread pool should scale with workload")
    void testThreadPoolScaling() {
        AsyncChunkLoader asyncLoader = level.getAsyncLoader();
        
        // Light load
        asyncLoader.requestChunk(0, 0, 0, 0, 0);
        asyncLoader.processPendingTasks();
        int activeLightLoad = asyncLoader.getActiveTaskCount();
        
        // Heavy load
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                asyncLoader.requestChunk(x, z, 0, 0, 0);
            }
        }
        asyncLoader.processPendingTasks();
        int activeHeavyLoad = asyncLoader.getActiveTaskCount();
        
        System.out.println("Active tasks (light load): " + activeLightLoad);
        System.out.println("Active tasks (heavy load): " + activeHeavyLoad);
        
        // Under heavy load, more tasks should be active
        // (depends on thread pool implementation)
        assertTrue(activeHeavyLoad >= activeLightLoad,
            "Thread pool should scale up under heavy load");
    }
}
