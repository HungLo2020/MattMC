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
 */
public class ChunkTaskExecutor {
    private static final int THREAD_COUNT = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
    
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
