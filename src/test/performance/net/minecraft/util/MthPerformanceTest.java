package net.minecraft.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

/**
 * Example JUnit-based performance test for the performance directory.
 * This demonstrates simple performance testing using JUnit's @Test.
 * For more sophisticated benchmarking, use JMH (see BlockPosBenchmark.java).
 */
@DisplayName("Mth Performance Tests")
class MthPerformanceTest {
    
    private static final int ITERATIONS = 100_000;
    
    @Test
    @DisplayName("should perform clamp operations quickly")
    void testClampPerformance() {
        long startTime = System.nanoTime();
        
        for (int i = 0; i < ITERATIONS; i++) {
            Mth.clamp(i, 0, 1000);
        }
        
        long duration = System.nanoTime() - startTime;
        double avgNanos = duration / (double) ITERATIONS;
        
        System.out.printf("Clamp: %,d iterations in %.2f ms (avg: %.2f ns/op)%n",
            ITERATIONS, duration / 1_000_000.0, avgNanos);
    }
    
    @Test
    @DisplayName("should perform sqrt operations quickly")
    void testSqrtPerformance() {
        long startTime = System.nanoTime();
        
        for (int i = 0; i < ITERATIONS; i++) {
            Mth.sqrt((float) i);
        }
        
        long duration = System.nanoTime() - startTime;
        double avgNanos = duration / (double) ITERATIONS;
        
        System.out.printf("Sqrt: %,d iterations in %.2f ms (avg: %.2f ns/op)%n",
            ITERATIONS, duration / 1_000_000.0, avgNanos);
    }
    
    @Test
    @DisplayName("should perform floor operations quickly")
    void testFloorPerformance() {
        long startTime = System.nanoTime();
        
        for (int i = 0; i < ITERATIONS; i++) {
            Mth.floor(i * 1.5);
        }
        
        long duration = System.nanoTime() - startTime;
        double avgNanos = duration / (double) ITERATIONS;
        
        System.out.printf("Floor: %,d iterations in %.2f ms (avg: %.2f ns/op)%n",
            ITERATIONS, duration / 1_000_000.0, avgNanos);
    }
}
