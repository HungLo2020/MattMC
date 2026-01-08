package net.minecraft.util.profiling.custom;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Map;
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
    private static ProfilerCollectorWrapper mainThreadWrapper;
    private static ProfilerCollectorWrapper renderThreadWrapper;
    private static Profiler.Scope mainThreadProfilerScope;
    private static Profiler.Scope renderThreadProfilerScope;

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

                // Initialize main thread profiler with wrapper
                mainThreadProfiler = new MainThreadProfiler();
                
                // Create wrapper for main thread - it will wrap the profiler when createProfiler() is called
                // We initialize it here so it's ready to use
                
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
                
                // Collect hierarchical data from wrapper
                if (mainThreadWrapper != null) {
                    Map<String, OperationRecord> mainOps = mainThreadWrapper.getOperations();
                    LOGGER.info("Main thread hierarchical operations collected: {} entries", mainOps.size());
                    currentSession.setMainThreadHierarchicalOperations(mainOps);
                } else {
                    LOGGER.warn("Main thread wrapper is null");
                }

                if (renderThreadProfiler != null) {
                    currentSession.setRenderThreadOperations(renderThreadProfiler.getOperations());
                    currentSession.setTotalFrames(renderThreadProfiler.getFrameCount());
                    currentSession.setAvgFrameTime(renderThreadProfiler.getAvgFrameTime());
                }
                
                // Collect hierarchical data from render wrapper
                if (renderThreadWrapper != null) {
                    Map<String, OperationRecord> renderOps = renderThreadWrapper.getOperations();
                    LOGGER.info("Render thread hierarchical operations collected: {} entries", renderOps.size());
                    currentSession.setRenderThreadHierarchicalOperations(renderOps);
                } else {
                    LOGGER.warn("Render thread wrapper is null, no hierarchical data collected for render thread");
                }

                // Generate reports (both text and HTML)
                ProfilerReportGenerator textGenerator = new ProfilerReportGenerator();
                Path textReportPath = textGenerator.generate(currentSession);
                
                HtmlProfilerReportGenerator htmlGenerator = new HtmlProfilerReportGenerator();
                Path htmlReportPath = htmlGenerator.generate(currentSession);

                LOGGER.info("Profiling session stopped: {}", currentSession.getSessionId());
                LOGGER.info("Text report: {}", textReportPath);
                LOGGER.info("HTML report: {}", htmlReportPath);

                return htmlReportPath; // Return HTML report path as primary
            } finally {
                // Cleanup
                currentSession = null;
                threadTracker = null;
                mainThreadProfiler = null;
                renderThreadProfiler = null;
                mainThreadWrapper = null;
                renderThreadWrapper = null;
            }
        }
    }

    public static boolean isRunning() {
        return currentSession != null;
    }
    
    /**
     * Get the profiler wrapper for the main thread.
     * This should be used to wrap the active profiler.
     */
    public static ProfilerCollectorWrapper getMainThreadWrapper() {
        return mainThreadWrapper;
    }
    
    /**
     * Wrap the main thread profiler with the collector wrapper.
     * Creates wrapper on first call if profiling session is active.
     */
    public static ProfilerFiller wrapMainThreadProfiler(ProfilerFiller profiler) {
        if (currentSession == null) {
            return profiler;
        }
        
        if (mainThreadWrapper == null) {
            mainThreadWrapper = new ProfilerCollectorWrapper(profiler);
            return mainThreadWrapper;
        }
        
        // Wrapper already exists, update its delegate if it's a wrapper
        // Just return the existing wrapper to maintain consistency
        return mainThreadWrapper;
    }
    
    /**
     * Wrap the render thread profiler with the collector wrapper.
     * Creates wrapper on first call if profiling session is active.
     */
    public static ProfilerFiller wrapRenderThreadProfiler(ProfilerFiller profiler) {
        if (currentSession == null) {
            return profiler;
        }
        
        if (renderThreadWrapper == null) {
            renderThreadWrapper = new ProfilerCollectorWrapper(profiler);
            return renderThreadWrapper;
        }
        
        // Wrapper already exists, return it to maintain consistency
        return renderThreadWrapper;
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
