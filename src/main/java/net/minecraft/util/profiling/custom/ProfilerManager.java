package net.minecraft.util.profiling.custom;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Central coordinator for profiling sessions.
 */
public class ProfilerManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile ProfilingSession currentSession = null;
    private static final Object sessionLock = new Object();
    
    private static ThreadTracker threadTracker;
    private static MainThreadProfiler mainThreadProfiler;
    private static RenderThreadProfiler renderThreadProfiler;

    public static boolean start(CommandSourceStack initiator) {
        synchronized (sessionLock) {
            if (currentSession != null) {
                return false;
            }

            try {
                currentSession = new ProfilingSession(
                    UUID.randomUUID(),
                    System.nanoTime(),
                    initiator
                );

                // Initialize trackers
                threadTracker = new ThreadTracker();
                threadTracker.start();

                // Initialize main thread profiler
                mainThreadProfiler = new MainThreadProfiler();

                // Initialize render thread profiler (will be used if on client)
                renderThreadProfiler = new RenderThreadProfiler();

                LOGGER.info("Profiling session started: {}", currentSession.getSessionId());
                return true;
            } catch (Exception e) {
                LOGGER.error("Failed to start profiling session", e);
                currentSession = null;
                return false;
            }
        }
    }

    public static Path stop() throws Exception {
        synchronized (sessionLock) {
            if (currentSession == null) {
                throw new IllegalStateException("No profiling session active");
            }

            try {
                currentSession.setEndTime(System.nanoTime());

                // Collect final data
                threadTracker.stop();
                currentSession.setThreadRecords(threadTracker.getRecords());

                if (mainThreadProfiler != null) {
                    currentSession.setMainThreadOperations(mainThreadProfiler.getOperations());
                    currentSession.setTotalTicks(mainThreadProfiler.getTickCount());
                    currentSession.setAvgTickTime(mainThreadProfiler.getAvgTickTime());
                }

                if (renderThreadProfiler != null) {
                    currentSession.setRenderThreadOperations(renderThreadProfiler.getOperations());
                    currentSession.setTotalFrames(renderThreadProfiler.getFrameCount());
                    currentSession.setAvgFrameTime(renderThreadProfiler.getAvgFrameTime());
                }

                // Generate report
                ProfilerReportGenerator generator = new ProfilerReportGenerator();
                Path reportPath = generator.generate(currentSession);

                LOGGER.info("Profiling session stopped: {}, report: {}", 
                    currentSession.getSessionId(), reportPath);

                return reportPath;
            } finally {
                // Cleanup
                currentSession = null;
                threadTracker = null;
                mainThreadProfiler = null;
                renderThreadProfiler = null;
            }
        }
    }

    public static boolean isRunning() {
        return currentSession != null;
    }

    // Called by instrumented code to record operations
    public static void recordMainThreadOperation(String operation, long durationNanos) {
        if (mainThreadProfiler != null) {
            mainThreadProfiler.recordOperation(operation, durationNanos);
        }
    }

    public static void recordMainThreadTick(long durationNanos) {
        if (mainThreadProfiler != null) {
            mainThreadProfiler.recordTick(durationNanos);
        }
    }

    public static void recordRenderThreadOperation(String operation, long durationNanos) {
        if (renderThreadProfiler != null) {
            renderThreadProfiler.recordOperation(operation, durationNanos);
        }
    }

    public static void recordRenderThreadFrame(long durationNanos) {
        if (renderThreadProfiler != null) {
            renderThreadProfiler.recordFrame(durationNanos);
        }
    }
}
