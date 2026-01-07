package net.minecraft.resources;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for timing performance tests with statistical analysis.
 * Provides methods to measure operation timing and report statistics.
 */
public class PerformanceTimer {
    
    private final List<Long> measurements = new ArrayList<>();
    private long startTime;
    
    /**
     * Starts timing an operation.
     */
    public void start() {
        startTime = System.nanoTime();
    }
    
    /**
     * Stops timing and records the measurement.
     * @return Duration in nanoseconds
     */
    public long stop() {
        long duration = System.nanoTime() - startTime;
        measurements.add(duration);
        return duration;
    }
    
    /**
     * Times a runnable operation and records the measurement.
     * @param operation The operation to time
     * @return Duration in nanoseconds
     */
    public long time(Runnable operation) {
        start();
        operation.run();
        return stop();
    }
    
    /**
     * Gets the average duration of all measurements.
     * @return Average duration in nanoseconds
     */
    public double getAverage() {
        if (measurements.isEmpty()) return 0.0;
        return measurements.stream().mapToLong(Long::longValue).average().orElse(0.0);
    }
    
    /**
     * Gets the minimum duration of all measurements.
     * @return Minimum duration in nanoseconds
     */
    public long getMin() {
        if (measurements.isEmpty()) return 0L;
        return Collections.min(measurements);
    }
    
    /**
     * Gets the maximum duration of all measurements.
     * @return Maximum duration in nanoseconds
     */
    public long getMax() {
        if (measurements.isEmpty()) return 0L;
        return Collections.max(measurements);
    }
    
    /**
     * Gets the median duration of all measurements.
     * @return Median duration in nanoseconds
     */
    public double getMedian() {
        if (measurements.isEmpty()) return 0.0;
        List<Long> sorted = new ArrayList<>(measurements);
        Collections.sort(sorted);
        int size = sorted.size();
        if (size % 2 == 0) {
            return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
        } else {
            return sorted.get(size / 2);
        }
    }
    
    /**
     * Gets the 95th percentile duration.
     * @return 95th percentile in nanoseconds
     */
    public long getP95() {
        if (measurements.isEmpty()) return 0L;
        List<Long> sorted = new ArrayList<>(measurements);
        Collections.sort(sorted);
        int index = (int) Math.ceil(0.95 * sorted.size()) - 1;
        return sorted.get(Math.max(0, index));
    }
    
    /**
     * Gets the 99th percentile duration.
     * @return 99th percentile in nanoseconds
     */
    public long getP99() {
        if (measurements.isEmpty()) return 0L;
        List<Long> sorted = new ArrayList<>(measurements);
        Collections.sort(sorted);
        int index = (int) Math.ceil(0.99 * sorted.size()) - 1;
        return sorted.get(Math.max(0, index));
    }
    
    /**
     * Gets the total number of measurements.
     * @return Number of measurements
     */
    public int getCount() {
        return measurements.size();
    }
    
    /**
     * Clears all measurements.
     */
    public void reset() {
        measurements.clear();
    }
    
    /**
     * Adds a measurement directly (in nanoseconds).
     * @param nanos Duration in nanoseconds
     */
    public void addMeasurement(long nanos) {
        measurements.add(nanos);
    }
    
    /**
     * Formats a duration in nanoseconds to a human-readable string.
     * @param nanos Duration in nanoseconds
     * @return Formatted string with appropriate unit
     */
    public static String formatDuration(long nanos) {
        if (nanos < 1_000) {
            return String.format("%d ns", nanos);
        } else if (nanos < 1_000_000) {
            return String.format("%.2f μs", nanos / 1_000.0);
        } else if (nanos < 1_000_000_000) {
            return String.format("%.2f ms", nanos / 1_000_000.0);
        } else {
            return String.format("%.2f s", nanos / 1_000_000_000.0);
        }
    }
    
    /**
     * Formats a duration in nanoseconds to a human-readable string.
     * @param nanos Duration in nanoseconds (as double)
     * @return Formatted string with appropriate unit
     */
    public static String formatDuration(double nanos) {
        return formatDuration((long) nanos);
    }
    
    /**
     * Prints a summary of all measurements.
     * @param label Label for this set of measurements
     */
    public void printSummary(String label) {
        if (measurements.isEmpty()) {
            System.out.printf("%s: No measurements recorded%n", label);
            return;
        }
        
        System.out.printf("%s Performance Summary:%n", label);
        System.out.printf("  Iterations: %,d%n", getCount());
        System.out.printf("  Average:    %s%n", formatDuration(getAverage()));
        System.out.printf("  Median:     %s%n", formatDuration(getMedian()));
        System.out.printf("  Min:        %s%n", formatDuration(getMin()));
        System.out.printf("  Max:        %s%n", formatDuration(getMax()));
        System.out.printf("  P95:        %s%n", formatDuration(getP95()));
        System.out.printf("  P99:        %s%n", formatDuration(getP99()));
    }
    
    /**
     * Performs warmup iterations before actual measurements.
     * @param warmupIterations Number of warmup iterations
     * @param operation The operation to warm up
     */
    public static void warmup(int warmupIterations, Runnable operation) {
        for (int i = 0; i < warmupIterations; i++) {
            operation.run();
        }
    }
}
