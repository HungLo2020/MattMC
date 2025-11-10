package mattmc.world.level.chunk;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages background thread pool for chunk generation and meshing tasks.
 * Provides thread-safe task submission and prioritization.
 * 
 * This executor handles CPU-intensive operations like:
 * - Chunk terrain generation
 * - Block face culling and mesh building
 * - Chunk disk I/O
 * 
 * OpenGL operations remain on the render thread.
 * 
 * ISSUE-010 fix: Improved thread count calculation for better performance across
 * different hardware configurations.
 */
public class ChunkTaskExecutor {
    private static final int THREAD_COUNT = calculateOptimalThreadCount();
    
    /**
     * Calculate optimal thread count based on available processors.
     * Chunk loading is I/O bound, so we don't need one thread per core.
     * Uses adaptive formula for different hardware configurations.
     */
    private static int calculateOptimalThreadCount() {
        int cores = Runtime.getRuntime().availableProcessors();
        
        // Use 50-75% of available cores, capped at 8 threads
        // Chunk loading is I/O bound, so we don't need excessive threads
        if (cores <= 2) {
            return 2; // Minimum for responsiveness on low-end systems
        } else if (cores <= 4) {
            return cores - 1; // 3 threads on quad core
        } else if (cores <= 8) {
            return cores / 2 + 1; // 4-5 threads on 6-8 cores
        } else {
            return 8; // Cap at 8 threads for chunk work to avoid contention
        }
    }
    
    private final ExecutorService executor;
    private final AtomicInteger activeTasks = new AtomicInteger(0);
    
    public ChunkTaskExecutor() {
        this.executor = Executors.newFixedThreadPool(THREAD_COUNT, new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "ChunkWorker-" + threadNumber.getAndIncrement());
                thread.setDaemon(true);
                thread.setPriority(Thread.NORM_PRIORITY - 1);
                return thread;
            }
        });
    }
    
    /**
     * Submit a task to be executed on a background thread.
     * Returns a Future that completes when the task finishes.
     */
    public <T> Future<T> submit(Callable<T> task) {
        activeTasks.incrementAndGet();
        return executor.submit(() -> {
            try {
                return task.call();
            } finally {
                activeTasks.decrementAndGet();
            }
        });
    }
    
    /**
     * Submit a runnable task to be executed on a background thread.
     */
    public Future<?> submit(Runnable task) {
        activeTasks.incrementAndGet();
        return executor.submit(() -> {
            try {
                task.run();
            } finally {
                activeTasks.decrementAndGet();
            }
        });
    }
    
    /**
     * Get the number of currently active tasks.
     */
    public int getActiveTasks() {
        return activeTasks.get();
    }
    
    /**
     * Shutdown the executor and wait for all tasks to complete.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Check if the executor is still running.
     */
    public boolean isRunning() {
        return !executor.isShutdown();
    }
}
