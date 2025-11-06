package mattmc.world.level.chunk;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Asynchronous chunk saver to prevent lag spikes when saving chunks.
 * Based on Minecraft Java Edition's approach of queuing chunk saves
 * and processing them on a background thread.
 */
public class AsyncChunkSaver {
    private final ExecutorService executor;
    private final BlockingQueue<ChunkSaveTask> saveQueue;
    private final RegionFileCache regionCache;
    private volatile boolean shutdown = false;
    
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
        this.saveQueue = new LinkedBlockingQueue<>();
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
     */
    public void saveChunkAsync(int chunkX, int chunkZ, Map<String, Object> chunkData) {
        if (shutdown) {
            return;
        }
        
        ChunkSaveTask task = new ChunkSaveTask(chunkX, chunkZ, chunkData);
        saveQueue.offer(task);
    }
    
    /**
     * Background worker that processes the save queue.
     */
    private void processSaveQueue() {
        while (!shutdown || !saveQueue.isEmpty()) {
            try {
                ChunkSaveTask task = saveQueue.poll(100, TimeUnit.MILLISECONDS);
                if (task != null) {
                    saveChunkToRegion(task);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    /**
     * Save a chunk to its region file.
     */
    private void saveChunkToRegion(ChunkSaveTask task) {
        try {
            RegionFile regionFile = regionCache.getRegionFile(task.chunkX, task.chunkZ);
            regionFile.writeChunk(task.chunkX, task.chunkZ, task.chunkData);
        } catch (IOException e) {
            System.err.println("Failed to save chunk (" + task.chunkX + ", " + task.chunkZ + "): " + e.getMessage());
        }
    }
    
    /**
     * Flush all pending saves and wait for completion.
     */
    public void flush() {
        // Wait for queue to drain
        while (!saveQueue.isEmpty()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        // Flush region cache
        try {
            regionCache.flush();
        } catch (IOException e) {
            System.err.println("Error flushing region cache: " + e.getMessage());
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
