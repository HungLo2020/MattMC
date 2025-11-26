package mattmc.performance;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Base class for performance tests providing common utilities for measuring:
 * - Execution time (nanoseconds/milliseconds)
 * - Memory usage (heap and non-heap)
 * - CPU time per thread
 * - Thread count and utilization
 * 
 * All performance tests should extend this class to ensure consistent measurement.
 * 
 * Performance results are automatically recorded to a dedicated report at:
 * build/reports/performance/performance-report.html
 */
@ExtendWith(PerformanceReportExtension.class)
public abstract class PerformanceTestBase {
    
    protected static final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    protected static final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    protected static final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
    
    /**
     * Detailed result from a performance measurement.
     */
    public static class PerformanceResult {
        public final String testName;
        public final long executionTimeNanos;
        public final long heapMemoryUsedBytes;
        public final long heapMemoryDeltaBytes;
        public final int threadCount;
        public final long cpuTimeNanos;
        public final int iterations;
        
        public PerformanceResult(String testName, long executionTimeNanos, 
                                long heapMemoryUsedBytes, long heapMemoryDeltaBytes,
                                int threadCount, long cpuTimeNanos, int iterations) {
            this.testName = testName;
            this.executionTimeNanos = executionTimeNanos;
            this.heapMemoryUsedBytes = heapMemoryUsedBytes;
            this.heapMemoryDeltaBytes = heapMemoryDeltaBytes;
            this.threadCount = threadCount;
            this.cpuTimeNanos = cpuTimeNanos;
            this.iterations = iterations;
        }
        
        public double getExecutionTimeMs() {
            return executionTimeNanos / 1_000_000.0;
        }
        
        public double getHeapMemoryMB() {
            return heapMemoryUsedBytes / (1024.0 * 1024.0);
        }
        
        public double getHeapMemoryDeltaMB() {
            return heapMemoryDeltaBytes / (1024.0 * 1024.0);
        }
        
        public double getCpuTimeMs() {
            return cpuTimeNanos / 1_000_000.0;
        }
        
        public double getAvgTimePerIterationMs() {
            return iterations > 0 ? getExecutionTimeMs() / iterations : 0;
        }
        
        @Override
        public String toString() {
            return String.format(
                "%s:\n" +
                "  Execution Time: %.3f ms (%.3f ms/iteration for %d iterations)\n" +
                "  Heap Memory: %.2f MB (delta: %+.2f MB)\n" +
                "  Thread Count: %d\n" +
                "  CPU Time: %.3f ms",
                testName, getExecutionTimeMs(), getAvgTimePerIterationMs(), iterations,
                getHeapMemoryMB(), getHeapMemoryDeltaMB(),
                threadCount, getCpuTimeMs()
            );
        }
    }
    
    /**
     * Frame time metrics for rendering performance.
     */
    public static class FrameTimeMetrics {
        public final double avgFrameTimeMs;
        public final double minFrameTimeMs;
        public final double maxFrameTimeMs;
        public final double percentile95Ms;
        public final double percentile99Ms;
        public final double fps;
        public final int frameCount;
        public final List<Double> frameTimesMs;
        
        public FrameTimeMetrics(List<Double> frameTimesMs) {
            this.frameTimesMs = new ArrayList<>(frameTimesMs);
            this.frameCount = frameTimesMs.size();
            
            if (frameCount == 0) {
                avgFrameTimeMs = minFrameTimeMs = maxFrameTimeMs = 0;
                percentile95Ms = percentile99Ms = 0;
                fps = 0;
                return;
            }
            
            List<Double> sorted = new ArrayList<>(frameTimesMs);
            sorted.sort(Double::compareTo);
            
            double sum = 0;
            double min = Double.MAX_VALUE;
            double max = Double.MIN_VALUE;
            
            for (double ft : frameTimesMs) {
                sum += ft;
                min = Math.min(min, ft);
                max = Math.max(max, ft);
            }
            
            this.avgFrameTimeMs = sum / frameCount;
            this.minFrameTimeMs = min;
            this.maxFrameTimeMs = max;
            this.percentile95Ms = sorted.get((int)(frameCount * 0.95));
            this.percentile99Ms = sorted.get((int)(frameCount * 0.99));
            this.fps = 1000.0 / avgFrameTimeMs;
        }
        
        @Override
        public String toString() {
            return String.format(
                "Frame Time Metrics (%d frames):\n" +
                "  Average: %.3f ms (%.1f FPS)\n" +
                "  Min: %.3f ms, Max: %.3f ms\n" +
                "  95th percentile: %.3f ms\n" +
                "  99th percentile: %.3f ms",
                frameCount, avgFrameTimeMs, fps,
                minFrameTimeMs, maxFrameTimeMs,
                percentile95Ms, percentile99Ms
            );
        }
    }
    
    /**
     * Measure the performance of a single operation.
     */
    protected PerformanceResult measureOperation(String name, Runnable operation) {
        return measureOperation(name, 1, 0, operation);
    }
    
    /**
     * Measure the performance of an operation with warmup and iterations.
     * Results are automatically recorded to the performance report.
     */
    protected PerformanceResult measureOperation(String name, int iterations, 
                                                  int warmupIterations, Runnable operation) {
        // Warmup phase
        for (int i = 0; i < warmupIterations; i++) {
            operation.run();
        }
        
        // Force GC before measurement
        forceGC();
        
        // Record initial state
        MemoryUsage heapBefore = memoryBean.getHeapMemoryUsage();
        long initialHeap = heapBefore.getUsed();
        int initialThreadCount = threadBean.getThreadCount();
        long initialCpuTime = getCurrentThreadCpuTime();
        
        // Run the operation
        long startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            operation.run();
        }
        long endTime = System.nanoTime();
        
        // Record final state
        long finalCpuTime = getCurrentThreadCpuTime();
        int finalThreadCount = threadBean.getThreadCount();
        MemoryUsage heapAfter = memoryBean.getHeapMemoryUsage();
        long finalHeap = heapAfter.getUsed();
        
        PerformanceResult result = new PerformanceResult(
            name,
            endTime - startTime,
            finalHeap,
            finalHeap - initialHeap,
            Math.max(initialThreadCount, finalThreadCount),
            finalCpuTime - initialCpuTime,
            iterations
        );
        
        // Record to performance report
        PerformanceReportGenerator.recordResult(result);
        
        return result;
    }
    
    /**
     * Measure operation that returns a result (prevents dead code elimination).
     * Results are automatically recorded to the performance report.
     */
    protected <T> PerformanceResult measureOperationWithResult(String name, int iterations,
                                                                int warmupIterations, 
                                                                Supplier<T> operation) {
        // Warmup phase
        for (int i = 0; i < warmupIterations; i++) {
            T result = operation.get();
            preventOptimization(result);
        }
        
        // Force GC before measurement
        forceGC();
        
        // Record initial state
        MemoryUsage heapBefore = memoryBean.getHeapMemoryUsage();
        long initialHeap = heapBefore.getUsed();
        int initialThreadCount = threadBean.getThreadCount();
        long initialCpuTime = getCurrentThreadCpuTime();
        
        // Run the operation
        long startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            T result = operation.get();
            preventOptimization(result);
        }
        long endTime = System.nanoTime();
        
        // Record final state
        long finalCpuTime = getCurrentThreadCpuTime();
        int finalThreadCount = threadBean.getThreadCount();
        MemoryUsage heapAfter = memoryBean.getHeapMemoryUsage();
        long finalHeap = heapAfter.getUsed();
        
        PerformanceResult perfResult = new PerformanceResult(
            name,
            endTime - startTime,
            finalHeap,
            finalHeap - initialHeap,
            Math.max(initialThreadCount, finalThreadCount),
            finalCpuTime - initialCpuTime,
            iterations
        );
        
        // Record to performance report
        PerformanceReportGenerator.recordResult(perfResult);
        
        return perfResult;
    }
    
    /**
     * Simulate frame timing measurement.
     * Results are automatically recorded to the performance report.
     */
    protected FrameTimeMetrics measureFrameTimes(String testName, int frameCount, Runnable frameOperation) {
        List<Double> frameTimes = new ArrayList<>(frameCount);
        
        // Warmup frames
        for (int i = 0; i < 10; i++) {
            frameOperation.run();
        }
        
        // Measure frames
        for (int i = 0; i < frameCount; i++) {
            long start = System.nanoTime();
            frameOperation.run();
            long end = System.nanoTime();
            frameTimes.add((end - start) / 1_000_000.0);
        }
        
        FrameTimeMetrics metrics = new FrameTimeMetrics(frameTimes);
        
        // Record to performance report
        PerformanceReportGenerator.recordResult(testName, metrics);
        
        return metrics;
    }
    
    /**
     * Simulate frame timing measurement (legacy method without auto-recording).
     */
    protected FrameTimeMetrics measureFrameTimes(int frameCount, Runnable frameOperation) {
        List<Double> frameTimes = new ArrayList<>(frameCount);
        
        // Warmup frames
        for (int i = 0; i < 10; i++) {
            frameOperation.run();
        }
        
        // Measure frames
        for (int i = 0; i < frameCount; i++) {
            long start = System.nanoTime();
            frameOperation.run();
            long end = System.nanoTime();
            frameTimes.add((end - start) / 1_000_000.0);
        }
        
        return new FrameTimeMetrics(frameTimes);
    }
    
    /**
     * Get current thread's CPU time if available.
     */
    protected long getCurrentThreadCpuTime() {
        if (threadBean.isCurrentThreadCpuTimeSupported()) {
            return threadBean.getCurrentThreadCpuTime();
        }
        return 0;
    }
    
    /**
     * Get total CPU time across all threads.
     */
    protected long getTotalThreadCpuTime() {
        long total = 0;
        if (threadBean.isThreadCpuTimeSupported()) {
            for (long threadId : threadBean.getAllThreadIds()) {
                long cpuTime = threadBean.getThreadCpuTime(threadId);
                if (cpuTime > 0) {
                    total += cpuTime;
                }
            }
        }
        return total;
    }
    
    /**
     * Get current heap memory usage in bytes.
     */
    protected long getHeapMemoryUsed() {
        return memoryBean.getHeapMemoryUsage().getUsed();
    }
    
    /**
     * Get current non-heap memory usage in bytes.
     */
    protected long getNonHeapMemoryUsed() {
        return memoryBean.getNonHeapMemoryUsage().getUsed();
    }
    
    /**
     * Force garbage collection before measurement.
     */
    protected void forceGC() {
        System.gc();
        System.gc();
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Prevent JIT optimization from removing code.
     */
    protected static volatile Object optimizationSink;
    
    protected static void preventOptimization(Object value) {
        if (System.nanoTime() == 0) {
            optimizationSink = value;
        }
    }
    
    /**
     * Print a formatted performance report.
     */
    protected void printReport(String title, PerformanceResult... results) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("PERFORMANCE REPORT: " + title);
        System.out.println("=".repeat(60));
        for (PerformanceResult result : results) {
            System.out.println(result);
            System.out.println("-".repeat(40));
        }
        System.out.println();
    }
    
    /**
     * Assert that execution time is within acceptable bounds.
     */
    protected void assertTimeWithinBounds(PerformanceResult result, double maxTimeMs, String message) {
        double actualTime = result.getExecutionTimeMs();
        if (actualTime > maxTimeMs) {
            throw new AssertionError(String.format(
                "%s: Expected time <= %.3f ms but was %.3f ms",
                message, maxTimeMs, actualTime
            ));
        }
    }
    
    /**
     * Assert that memory delta is within acceptable bounds.
     */
    protected void assertMemoryWithinBounds(PerformanceResult result, double maxDeltaMB, String message) {
        double actualDelta = result.getHeapMemoryDeltaMB();
        if (actualDelta > maxDeltaMB) {
            throw new AssertionError(String.format(
                "%s: Expected memory delta <= %.2f MB but was %.2f MB",
                message, maxDeltaMB, actualDelta
            ));
        }
    }
}
