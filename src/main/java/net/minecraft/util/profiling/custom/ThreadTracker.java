package net.minecraft.util.profiling.custom;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Tracks all threads in the JVM during a profiling session.
 */
public class ThreadTracker {
    private final ThreadMXBean threadBean;
    private final Map<Long, ThreadRecord> threads;
    private final ScheduledExecutorService scanner;
    private volatile boolean running;

    public ThreadTracker() {
        this.threadBean = ManagementFactory.getThreadMXBean();
        this.threads = new HashMap<>();
        this.scanner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ProfilerThreadScanner");
            t.setDaemon(true);
            return t;
        });
        this.running = false;

        // Enable CPU time measurement if supported
        if (threadBean.isThreadCpuTimeSupported()) {
            threadBean.setThreadCpuTimeEnabled(true);
        }
        
        // Enable contention monitoring if supported
        if (threadBean.isThreadContentionMonitoringSupported()) {
            threadBean.setThreadContentionMonitoringEnabled(true);
        }
    }

    public void start() {
        running = true;
        
        // Initial scan
        scanThreads();
        
        // Schedule periodic scans every 100ms
        scanner.scheduleAtFixedRate(
            this::scanThreads,
            100,
            100,
            TimeUnit.MILLISECONDS
        );
    }

    public void stop() {
        running = false;
        scanner.shutdown();
        try {
            scanner.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Final scan and mark terminated threads
        scanThreads();
        markTerminatedThreads();
    }

    private void scanThreads() {
        if (!running) return;

        long[] threadIds = threadBean.getAllThreadIds();
        ThreadInfo[] threadInfos = threadBean.getThreadInfo(threadIds, 0);

        for (int i = 0; i < threadIds.length; i++) {
            long id = threadIds[i];
            ThreadInfo info = threadInfos[i];

            if (info == null) continue;

            ThreadRecord record = threads.get(id);
            if (record == null) {
                // New thread discovered
                synchronized (threads) {
                    record = new ThreadRecord(id, info.getThreadName());
                    threads.put(id, record);
                }
            }

            // Update thread statistics
            if (threadBean.isThreadCpuTimeSupported()) {
                long cpuTime = threadBean.getThreadCpuTime(id);
                if (cpuTime != -1) {
                    record.updateCpuTime(cpuTime);
                }
                
                long userTime = threadBean.getThreadUserTime(id);
                if (userTime != -1) {
                    record.updateUserTime(userTime);
                }
            }
            
            if (threadBean.isThreadContentionMonitoringSupported()) {
                record.updateWaitTime(info.getWaitedTime());
                record.updateBlockTime(info.getBlockedTime());
            }

            record.recordState(info.getThreadState());
            
            // Add snapshot
            if (threadBean.isThreadCpuTimeSupported()) {
                long cpuTime = threadBean.getThreadCpuTime(id);
                long userTime = threadBean.getThreadUserTime(id);
                if (cpuTime != -1 && userTime != -1) {
                    record.addSnapshot(new ThreadRecord.ThreadSnapshot(
                        System.nanoTime(),
                        info.getThreadState(),
                        cpuTime,
                        userTime
                    ));
                }
            }
        }
    }

    private void markTerminatedThreads() {
        long now = System.nanoTime();
        Set<Long> currentThreadIds = new HashSet<>();
        for (long id : threadBean.getAllThreadIds()) {
            currentThreadIds.add(id);
        }

        synchronized (threads) {
            for (Map.Entry<Long, ThreadRecord> entry : threads.entrySet()) {
                if (!currentThreadIds.contains(entry.getKey()) && !entry.getValue().isTerminated()) {
                    entry.getValue().markTerminated(now);
                }
            }
        }
    }

    public Map<Long, ThreadRecord> getRecords() {
        synchronized (threads) {
            return new HashMap<>(threads);
        }
    }
}
