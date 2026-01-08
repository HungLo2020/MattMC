package net.minecraft.util.profiling.custom;

import net.minecraft.commands.CommandSourceStack;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Container for all data collected during a profiling session.
 */
public class ProfilingSession {
    private final UUID sessionId;
    private final long startTime;
    private long endTime;
    private final CommandSourceStack initiator;
    
    // Thread data
    private Map<Long, ThreadRecord> threads;
    
    // Operation data
    private Map<String, OperationRecord> mainThreadOperations;
    private Map<String, OperationRecord> renderThreadOperations;
    private Map<String, OperationRecord> otherOperations;
    
    // Aggregate statistics
    private int totalTicks;
    private int totalFrames;
    private double avgTickTime;
    private double avgFrameTime;
    private long totalSamples;

    public ProfilingSession(UUID sessionId, long startTime, CommandSourceStack initiator) {
        this.sessionId = sessionId;
        this.startTime = startTime;
        this.endTime = -1;
        this.initiator = initiator;
        this.threads = new HashMap<>();
        this.mainThreadOperations = new HashMap<>();
        this.renderThreadOperations = new HashMap<>();
        this.otherOperations = new HashMap<>();
        this.totalTicks = 0;
        this.totalFrames = 0;
        this.avgTickTime = 0.0;
        this.avgFrameTime = 0.0;
        this.totalSamples = 0;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public void setThreadRecords(Map<Long, ThreadRecord> threads) {
        this.threads = threads;
    }

    public void setMainThreadOperations(Map<String, OperationRecord> operations) {
        this.mainThreadOperations = operations;
    }

    public void setRenderThreadOperations(Map<String, OperationRecord> operations) {
        this.renderThreadOperations = operations;
    }

    public void setOtherOperations(Map<String, OperationRecord> operations) {
        this.otherOperations = operations;
    }

    public void setTotalTicks(int totalTicks) {
        this.totalTicks = totalTicks;
    }

    public void setTotalFrames(int totalFrames) {
        this.totalFrames = totalFrames;
    }

    public void setAvgTickTime(double avgTickTime) {
        this.avgTickTime = avgTickTime;
    }

    public void setAvgFrameTime(double avgFrameTime) {
        this.avgFrameTime = avgFrameTime;
    }

    public void setTotalSamples(long totalSamples) {
        this.totalSamples = totalSamples;
    }

    // Getters
    public UUID getSessionId() {
        return sessionId;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public long getDuration() {
        return endTime - startTime;
    }

    public CommandSourceStack getInitiator() {
        return initiator;
    }

    public Map<Long, ThreadRecord> getThreads() {
        return threads;
    }

    public Map<String, OperationRecord> getMainThreadOperations() {
        return mainThreadOperations;
    }

    public Map<String, OperationRecord> getRenderThreadOperations() {
        return renderThreadOperations;
    }

    public Map<String, OperationRecord> getOtherOperations() {
        return otherOperations;
    }

    public int getTotalTicks() {
        return totalTicks;
    }

    public int getTotalFrames() {
        return totalFrames;
    }

    public double getAvgTickTime() {
        return avgTickTime;
    }

    public double getAvgFrameTime() {
        return avgFrameTime;
    }

    public long getTotalSamples() {
        return totalSamples;
    }
}
