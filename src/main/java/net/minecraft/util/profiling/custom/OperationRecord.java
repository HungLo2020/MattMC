package net.minecraft.util.profiling.custom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Records timing information for a specific operation.
 */
public class OperationRecord {
    private final String operation;
    private long totalTime;
    private long callCount;
    private long minTime;
    private long maxTime;
    private final List<Long> samples;

    public OperationRecord(String operation) {
        this.operation = operation;
        this.totalTime = 0;
        this.callCount = 0;
        this.minTime = Long.MAX_VALUE;
        this.maxTime = Long.MIN_VALUE;
        this.samples = new ArrayList<>();
    }

    public synchronized void addSample(long durationNanos) {
        totalTime += durationNanos;
        callCount++;
        minTime = Math.min(minTime, durationNanos);
        maxTime = Math.max(maxTime, durationNanos);
        samples.add(durationNanos);
    }

    public String getOperation() {
        return operation;
    }

    public long getTotalTime() {
        return totalTime;
    }

    public long getCallCount() {
        return callCount;
    }

    public long getMinTime() {
        return minTime == Long.MAX_VALUE ? 0 : minTime;
    }

    public long getMaxTime() {
        return maxTime == Long.MIN_VALUE ? 0 : maxTime;
    }

    public double getAvgTime() {
        return callCount > 0 ? (double) totalTime / callCount : 0.0;
    }

    public List<Long> getSamples() {
        return new ArrayList<>(samples);
    }

    public long getPercentile(double percentile) {
        if (samples.isEmpty()) {
            return 0;
        }
        List<Long> sorted = new ArrayList<>(samples);
        Collections.sort(sorted);
        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }
}
