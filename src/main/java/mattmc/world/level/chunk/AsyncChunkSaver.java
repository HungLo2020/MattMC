package mattmc.world.level.chunk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Asynchronous chunk saver to prevent lag spikes when saving chunks.
 * Based on Minecraft Java Edition's approach of queuing chunk saves
 * and processing them on a background thread.
 */
public class AsyncChunkSaver {
    private static final Logger logger = LoggerFactory.getLogger(AsyncChunkSaver.class);
    
    // Reasonable capacity limit to prevent unbounded memory growth
    private static final int SAVE_QUEUE_CAPACITY = 1000;
    
    private final ExecutorService executor;
    private final BlockingQueue<ChunkSaveTask> saveQueue;
    private final RegionFileCache regionCache;
    private volatile boolean shutdown = false;
    private final AtomicInteger activeTasks = new AtomicInteger(0);
    
    private static class ChunkSaveTask {
        final int chunkX;
        final int chunkZ;
        final Map<String, Object> chunkData;
        
        ChunkSaveTask(int chunkX, int chunkZ, Map<String, Object> chunkData) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.chunkData = chunkData;
        }
    }
    
    public AsyncChunkSaver(RegionFileCache regionCache) {
        this.regionCache = regionCache;
        this.saveQueue = new LinkedBlockingQueue<>(SAVE_QUEUE_CAPACITY);
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Chunk-Saver");
            t.setDaemon(true);
            return t;
        });
        
        // Start background worker thread
        executor.submit(this::processSaveQueue);
    }
    
    /**
     * Queue a chunk for asynchronous saving.
     * Returns immediately without blocking.
     * If the queue is full, logs a warning and blocks until space is available.
     */
    public void saveChunkAsync(int chunkX, int chunkZ, Map<String, Object> chunkData) {
        if (shutdown) {
            return;
        }
        
        ChunkSaveTask task = new ChunkSaveTask(chunkX, chunkZ, chunkData);
        
        // Try to add without blocking first
        if (!saveQueue.offer(task)) {
            // Queue is full, log warning and wait
            logger.warn("Save queue full ({}), waiting for space to save chunk ({}, {})", 
                       SAVE_QUEUE_CAPACITY, chunkX, chunkZ);
            try {
                saveQueue.put(task); // This will block until space is available
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Interrupted while waiting to queue chunk save", e);
            }
        }
    }
    
    /**
     * Background worker that processes the save queue.
     */
    private void processSaveQueue() {
        while (!shutdown || !saveQueue.isEmpty()) {
            try {
                ChunkSaveTask task = saveQueue.poll(100, TimeUnit.MILLISECONDS);
                if (task != null) {
                    activeTasks.incrementAndGet();
                    try {
                        saveChunkToRegion(task);
                    } catch (Exception e) {
                        // Catch any exception during save to prevent thread death
                        logger.error("Unexpected error saving chunk ({}, {}): {}", 
                                   task.chunkX, task.chunkZ, e.getMessage(), e);
                    } finally {
                        activeTasks.decrementAndGet();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // Catch any other exception to prevent thread death
                logger.error("Unexpected error in save queue processing: {}", e.getMessage(), e);
            }
        }
        logger.info("Save queue processing thread stopped");
    }
    
    /**
     * Save a chunk to its region file.
     */
    private void saveChunkToRegion(ChunkSaveTask task) {
        try {
            RegionFile regionFile = regionCache.getRegionFile(task.chunkX, task.chunkZ);
            regionFile.writeChunk(task.chunkX, task.chunkZ, task.chunkData);
        } catch (IOException e) {
            logger.error("Failed to save chunk ({}, {}): {}", task.chunkX, task.chunkZ, e.getMessage(), e);
        }
    }
    
    /**
     * Flush all pending saves and wait for completion.
     */
    public void flush() {
        // Wait for queue to drain and active tasks to complete
        // Use exponential backoff to reduce CPU usage while waiting
        // Add timeout to prevent infinite loops if background thread dies
        long startTime = System.currentTimeMillis();
        long timeoutMs = 60000; // 60 second timeout
        long sleepTime = 1;
        
        while (!saveQueue.isEmpty() || activeTasks.get() > 0) {
            try {
                Thread.sleep(sleepTime);
                sleepTime = Math.min(sleepTime * 2, 100); // Cap at 100ms
                
                // Check for timeout
                if (System.currentTimeMillis() - startTime > timeoutMs) {
                    logger.error("Flush timeout after 60 seconds. Queue size: {}, Active tasks: {}", 
                               saveQueue.size(), activeTasks.get());
                    logger.error("Background thread may have died. Forcing flush to complete.");
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        // Flush region cache
        try {
            regionCache.flush();
        } catch (IOException e) {
            logger.error("Error flushing region cache: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Shutdown the saver and wait for pending saves to complete.
     */
    public void shutdown() {
        shutdown = true;
        flush();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Get the number of chunks waiting to be saved.
     */
    public int getPendingSaveCount() {
        return saveQueue.size();
    }
}
