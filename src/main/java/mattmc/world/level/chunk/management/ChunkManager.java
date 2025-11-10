package mattmc.world.level.chunk.management;

import mattmc.world.level.chunk.LevelChunk;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages loaded chunks and their lifecycle.
 */
public class ChunkManager {
    private final Map<Long, LevelChunk> loadedChunks = new HashMap<>();
    private ChunkUnloadListener unloadListener;
    
    /**
     * Get a loaded chunk.
     * @return The chunk, or null if not loaded
     */
    public LevelChunk getChunk(int chunkX, int chunkZ) {
        long key = packChunkPos(chunkX, chunkZ);
        return loadedChunks.get(key);
    }
    
    /**
     * Add a chunk to loaded chunks.
     */
    public void addChunk(LevelChunk chunk) {
        long key = packChunkPos(chunk.chunkX(), chunk.chunkZ());
        loadedChunks.put(key, chunk);
    }
    
    /**
     * Remove a chunk from loaded chunks.
     * @return The removed chunk, or null if not found
     */
    public LevelChunk removeChunk(int chunkX, int chunkZ) {
        long key = packChunkPos(chunkX, chunkZ);
        LevelChunk removed = loadedChunks.remove(key);
        
        if (removed != null && unloadListener != null) {
            unloadListener.onChunkUnload(removed);
        }
        
        return removed;
    }
    
    /**
     * Get all loaded chunks.
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
     * Unload chunks that are outside the render distance.
     */
    public void unloadChunksOutsideRadius(int centerX, int centerZ, int radius) {
        loadedChunks.entrySet().removeIf(entry -> {
            LevelChunk chunk = entry.getValue();
            int dx = Math.abs(chunk.chunkX() - centerX);
            int dz = Math.abs(chunk.chunkZ() - centerZ);
            
            if (dx > radius || dz > radius) {
                if (unloadListener != null) {
                    unloadListener.onChunkUnload(chunk);
                }
                return true;
            }
            return false;
        });
    }
    
    /**
     * Set the listener for chunk unload events.
     */
    public void setUnloadListener(ChunkUnloadListener listener) {
        this.unloadListener = listener;
    }
    
    /**
     * Pack chunk coordinates into a long key.
     */
    private long packChunkPos(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
    
    /**
     * Listener interface for chunk unload events.
     */
    public interface ChunkUnloadListener {
        void onChunkUnload(LevelChunk chunk);
    }
}
