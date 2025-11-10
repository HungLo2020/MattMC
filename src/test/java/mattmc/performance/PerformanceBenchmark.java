package mattmc.performance;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Simple performance benchmarking utility for measuring the impact of code changes.
 * Measures execution time and memory allocation for different operations.
 */
public class PerformanceBenchmark {
    
    public static class BenchmarkResult {
        public final String name;
        public final long avgTimeNanos;
        public final long minTimeNanos;
        public final long maxTimeNanos;
        public final long totalMemoryAllocated;
        public final int iterations;
        
        public BenchmarkResult(String name, long avgTimeNanos, long minTimeNanos, 
                             long maxTimeNanos, long totalMemoryAllocated, int iterations) {
            this.name = name;
            this.avgTimeNanos = avgTimeNanos;
            this.minTimeNanos = minTimeNanos;
            this.maxTimeNanos = maxTimeNanos;
            this.totalMemoryAllocated = totalMemoryAllocated;
            this.iterations = iterations;
        }
        
        public double getAvgTimeMs() {
            return avgTimeNanos / 1_000_000.0;
        }
        
        public double getMinTimeMs() {
            return minTimeNanos / 1_000_000.0;
        }
        
        public double getMaxTimeMs() {
            return maxTimeNanos / 1_000_000.0;
        }
        
        public double getTotalMemoryMB() {
            return totalMemoryAllocated / (1024.0 * 1024.0);
        }
        
        @Override
        public String toString() {
            return String.format("%s: avg=%.3fms (min=%.3fms, max=%.3fms), memory=%.2fMB, iterations=%d",
                    name, getAvgTimeMs(), getMinTimeMs(), getMaxTimeMs(), getTotalMemoryMB(), iterations);
        }
    }
    
    /**
     * Run a benchmark with the specified number of iterations.
     * Performs warmup iterations before actual measurement.
     */
    public static BenchmarkResult run(String name, int warmupIterations, int measureIterations, 
                                     Runnable operation) {
        // Warmup phase
        for (int i = 0; i < warmupIterations; i++) {
            operation.run();
        }
        
        // Force GC before measurement
        System.gc();
        System.gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Measurement phase
        long minTime = Long.MAX_VALUE;
        long maxTime = Long.MIN_VALUE;
        long totalTime = 0;
        long initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        
        for (int i = 0; i < measureIterations; i++) {
            long start = System.nanoTime();
            operation.run();
            long end = System.nanoTime();
            
            long elapsed = end - start;
            totalTime += elapsed;
            minTime = Math.min(minTime, elapsed);
            maxTime = Math.max(maxTime, elapsed);
        }
        
        long finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long memoryUsed = Math.max(0, finalMemory - initialMemory);
        long avgTime = totalTime / measureIterations;
        
        return new BenchmarkResult(name, avgTime, minTime, maxTime, memoryUsed, measureIterations);
    }
    
    /**
     * Compare two benchmark results and return the performance improvement ratio.
     */
    public static double calculateImprovement(BenchmarkResult before, BenchmarkResult after) {
        return (double) before.avgTimeNanos / after.avgTimeNanos;
    }
    
    /**
     * Print comparison between before and after results.
     */
    public static String compareResults(BenchmarkResult before, BenchmarkResult after) {
        double improvement = calculateImprovement(before, after);
        double memoryImprovement = (double) before.totalMemoryAllocated / 
                                   Math.max(1, after.totalMemoryAllocated);
        
        return String.format(
            "BEFORE: %s\n" +
            "AFTER:  %s\n" +
            "IMPROVEMENT: %.2fx faster, %.2fx less memory",
            before, after, improvement, memoryImprovement
        );
    }
}
