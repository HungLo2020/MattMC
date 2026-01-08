package net.minecraft.util.profiling.custom;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Records detailed information about a single thread during profiling.
 */
public class ThreadRecord {
    private final long threadId;
    private final String name;
    private String purpose;
    private final long createdAt;
    private long terminatedAt;
    private long totalCpuTime;
    private long totalUserTime;
    private long totalWaitTime;
    private long totalBlockTime;
    private final Map<Thread.State, Long> stateTimeMap;
    private final List<ThreadSnapshot> snapshots;
    private Thread.State lastState;
    private long lastStateTime;

    public ThreadRecord(long threadId, String name) {
        this.threadId = threadId;
        this.name = name;
        this.purpose = inferPurpose(name);
        this.createdAt = System.nanoTime();
        this.terminatedAt = -1;
        this.totalCpuTime = 0;
        this.totalUserTime = 0;
        this.totalWaitTime = 0;
        this.totalBlockTime = 0;
        this.stateTimeMap = new EnumMap<>(Thread.State.class);
        this.snapshots = new ArrayList<>();
        this.lastState = Thread.State.NEW;
        this.lastStateTime = System.nanoTime();
    }

    private String inferPurpose(String threadName) {
        if (threadName.contains("Server thread")) return "Main server tick loop";
        if (threadName.contains("Render thread")) return "Client rendering loop";
        if (threadName.contains("Netty")) return "Network I/O (Netty event loops)";
        if (threadName.contains("Worker-Main")) return "Background task processing (ForkJoinPool)";
        if (threadName.contains("IO-Worker")) return "File I/O operations";
        if (threadName.contains("Download")) return "Resource downloads";
        if (threadName.contains("Chunk")) return "Chunk processing";
        if (threadName.contains("Sound")) return "Audio processing";
        return "General purpose thread";
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public void updateCpuTime(long cpuTime) {
        this.totalCpuTime = cpuTime;
    }

    public void updateUserTime(long userTime) {
        this.totalUserTime = userTime;
    }

    public void updateWaitTime(long waitTime) {
        this.totalWaitTime = waitTime;
    }

    public void updateBlockTime(long blockTime) {
        this.totalBlockTime = blockTime;
    }

    public void recordState(Thread.State state) {
        long now = System.nanoTime();
        if (lastState != null && lastState != state) {
            long duration = now - lastStateTime;
            stateTimeMap.merge(lastState, duration, Long::sum);
        }
        lastState = state;
        lastStateTime = now;
    }

    public void markTerminated(long terminationTime) {
        this.terminatedAt = terminationTime;
        // Record final state duration
        if (lastState != null) {
            long duration = terminationTime - lastStateTime;
            stateTimeMap.merge(lastState, duration, Long::sum);
        }
    }

    public void addSnapshot(ThreadSnapshot snapshot) {
        snapshots.add(snapshot);
    }

    // Getters
    public long getThreadId() {
        return threadId;
    }

    public String getName() {
        return name;
    }

    public String getPurpose() {
        return purpose;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getTerminatedAt() {
        return terminatedAt;
    }

    public boolean isTerminated() {
        return terminatedAt != -1;
    }

    public long getLifetime(long currentTime) {
        long endTime = isTerminated() ? terminatedAt : currentTime;
        return endTime - createdAt;
    }

    public long getTotalCpuTime() {
        return totalCpuTime;
    }

    public long getTotalUserTime() {
        return totalUserTime;
    }

    public long getTotalWaitTime() {
        return totalWaitTime;
    }

    public long getTotalBlockTime() {
        return totalBlockTime;
    }

    public Map<Thread.State, Long> getStateTimeMap() {
        return new EnumMap<>(stateTimeMap);
    }

    public List<ThreadSnapshot> getSnapshots() {
        return new ArrayList<>(snapshots);
    }

    /**
     * Snapshot of thread state at a point in time.
     */
    public static class ThreadSnapshot {
        private final long timestamp;
        private final Thread.State state;
        private final long cpuTime;
        private final long userTime;

        public ThreadSnapshot(long timestamp, Thread.State state, long cpuTime, long userTime) {
            this.timestamp = timestamp;
            this.state = state;
            this.cpuTime = cpuTime;
            this.userTime = userTime;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public Thread.State getState() {
            return state;
        }

        public long getCpuTime() {
            return cpuTime;
        }

        public long getUserTime() {
            return userTime;
        }
    }
}
