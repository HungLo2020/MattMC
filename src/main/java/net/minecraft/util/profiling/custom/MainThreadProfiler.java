package net.minecraft.util.profiling.custom;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Profiles operations on the main/server thread.
 */
public class MainThreadProfiler {
    private final Map<String, OperationRecord> operations;
    private int tickCount;
    private long totalTickTime;

    public MainThreadProfiler() {
        this.operations = new ConcurrentHashMap<>();
        this.tickCount = 0;
        this.totalTickTime = 0;
    }

    public void recordOperation(String operation, long durationNanos) {
        operations.computeIfAbsent(operation, k -> new OperationRecord(k))
            .addSample(durationNanos);
    }

    public void recordTick(long durationNanos) {
        tickCount++;
        totalTickTime += durationNanos;
    }

    public Map<String, OperationRecord> getOperations() {
        return new HashMap<>(operations);
    }

    public int getTickCount() {
        return tickCount;
    }

    public double getAvgTickTime() {
        return tickCount > 0 ? (double) totalTickTime / tickCount : 0.0;
    }
}
