package net.minecraft.util.profiling.custom;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Profiles operations on the render thread (client-side only).
 */
public class RenderThreadProfiler {
    private final Map<String, OperationRecord> operations;
    private int frameCount;
    private long totalFrameTime;

    public RenderThreadProfiler() {
        this.operations = new ConcurrentHashMap<>();
        this.frameCount = 0;
        this.totalFrameTime = 0;
    }

    public void recordOperation(String operation, long durationNanos) {
        operations.computeIfAbsent(operation, k -> new OperationRecord(k))
            .addSample(durationNanos);
    }

    public void recordFrame(long durationNanos) {
        frameCount++;
        totalFrameTime += durationNanos;
    }

    public Map<String, OperationRecord> getOperations() {
        return new HashMap<>(operations);
    }

    public int getFrameCount() {
        return frameCount;
    }

    public double getAvgFrameTime() {
        return frameCount > 0 ? (double) totalFrameTime / frameCount : 0.0;
    }
}
