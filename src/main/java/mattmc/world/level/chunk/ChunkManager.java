package mattmc.world.level.chunk;

import mattmc.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Manages loaded chunks and their lifecycle.
 * Extracted from Level.java to improve modularity and maintainability.
 * 
 * Responsibilities:
 * - Store and retrieve loaded chunks
 * - Track chunk loading/unloading
 * - Notify listeners when chunks are unloaded
 * - Calculate chunk distance for unloading
 */
public class ChunkManager {
    private static final Logger logger = LoggerFactory.getLogger(ChunkManager.class);
    
    // Store chunks by their position (chunkX, chunkZ)
    private final Map<Long, LevelChunk> loadedChunks = new HashMap<>();
    
    // Listener for chunk unload events (used by renderer to clean up caches)
    private Level.ChunkUnloadListener unloadListener;
    
    /**
     * Get a chunk if it's loaded, or null if not.
     */
    public LevelChunk getChunk(int chunkX, int chunkZ) {
        return loadedChunks.get(chunkKey(chunkX, chunkZ));
    }
    
    /**
     * Add a chunk to the loaded chunks map.
     */
    public void addChunk(LevelChunk chunk) {
        if (chunk == null) {
            logger.warn("Attempted to add null chunk");
            return;
        }
        
        long key = chunkKey(chunk.chunkX(), chunk.chunkZ());
        loadedChunks.put(key, chunk);
    }
    
    /**
     * Check if a chunk is loaded.
     */
    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        return loadedChunks.containsKey(chunkKey(chunkX, chunkZ));
    }
    
    /**
     * Remove a chunk from loaded chunks.
     * Returns the removed chunk, or null if it wasn't loaded.
     */
    public LevelChunk removeChunk(int chunkX, int chunkZ) {
        long key = chunkKey(chunkX, chunkZ);
        LevelChunk chunk = loadedChunks.remove(key);
        
        // Notify listener if chunk was removed
        if (chunk != null && unloadListener != null) {
            unloadListener.onChunkUnload(chunk);
        }
        
        return chunk;
    }
    
    /**
     * Unload chunks that are outside the specified distance from the player.
     * Saves chunks before unloading and notifies listeners.
     * 
     * @param playerChunkX Player's chunk X coordinate
     * @param playerChunkZ Player's chunk Z coordinate
     * @param unloadDistance Maximum distance in chunks before unloading
     * @param saveCallback Callback to save chunks before unloading
     */
    public void unloadChunksOutsideRadius(int playerChunkX, int playerChunkZ, int unloadDistance, 
                                          ChunkSaveCallback saveCallback) {
        Iterator<Map.Entry<Long, LevelChunk>> iterator = loadedChunks.entrySet().iterator();
        int unloadedCount = 0;
        
        while (iterator.hasNext()) {
            Map.Entry<Long, LevelChunk> entry = iterator.next();
            LevelChunk chunk = entry.getValue();
            
            int dx = Math.abs(chunk.chunkX() - playerChunkX);
            int dz = Math.abs(chunk.chunkZ() - playerChunkZ);
            
            if (dx > unloadDistance || dz > unloadDistance) {
                // Save chunk before unloading if callback is provided
                if (saveCallback != null) {
                    saveCallback.saveChunk(chunk);
                }
                
                // Notify listener before removing
                if (unloadListener != null) {
                    unloadListener.onChunkUnload(chunk);
                }
                
                iterator.remove();
                unloadedCount++;
            }
        }
        
        if (unloadedCount > 0) {
            logger.debug("Unloaded {} chunks outside radius {}", unloadedCount, unloadDistance);
        }
    }
    
    /**
     * Get all currently loaded chunks.
     */
    public Iterable<LevelChunk> getLoadedChunks() {
        return loadedChunks.values();
    }
    
    /**
     * Get the number of loaded chunks.
     */
    public int getLoadedChunkCount() {
        return loadedChunks.size();
    }
    
    /**
     * Set a listener for chunk unload events.
     */
    public void setUnloadListener(Level.ChunkUnloadListener listener) {
        this.unloadListener = listener;
    }
    
    /**
     * Generate a unique key for chunk coordinates.
     * Uses bit packing to combine two 32-bit coordinates into one 64-bit long.
     */
    private static long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
    
    /**
     * Callback interface for saving chunks before unloading.
     */
    public interface ChunkSaveCallback {
        void saveChunk(LevelChunk chunk);
    }
}
