package mattmc.client.renderer.chunk;

import mattmc.client.renderer.block.BlockFaceCollector;

/**
 * Container for chunk mesh data that can be prepared on a background thread
 * and then uploaded to GPU on the render thread.
 * 
 * This separates CPU-intensive mesh building from GPU upload operations.
 */
public class ChunkMeshData {
    private final int chunkX;
    private final int chunkZ;
    private final BlockFaceCollector faceCollector;
    private final long timestamp;
    
    /**
     * Create mesh data for a chunk.
     * 
     * @param chunkX Chunk X coordinate
     * @param chunkZ Chunk Z coordinate
     * @param faceCollector Collected block faces ready for rendering
     */
    public ChunkMeshData(int chunkX, int chunkZ, BlockFaceCollector faceCollector) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.faceCollector = faceCollector;
        this.timestamp = System.nanoTime();
    }
    
    public int getChunkX() {
        return chunkX;
    }
    
    public int getChunkZ() {
        return chunkZ;
    }
    
    public BlockFaceCollector getFaceCollector() {
        return faceCollector;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    /**
     * Get the age of this mesh data in milliseconds.
     */
    public long getAgeMs() {
        return (System.nanoTime() - timestamp) / 1_000_000;
    }
}
