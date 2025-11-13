package mattmc.world.level.lighting;

import mattmc.world.level.chunk.ChunkManager;
import mattmc.world.level.chunk.LevelChunk;

/**
 * Provides cross-chunk light sampling.
 * Extracted from Level.java to improve modularity and maintainability.
 * 
 * Responsibilities:
 * - Get skylight at world coordinates
 * - Get block light at world coordinates
 * - Handle cross-chunk skylight queries
 * - Handle cross-chunk block light queries
 * - Coordinate conversion for light access
 */
public class LightAccessor {
    
    private final ChunkManager chunkManager;
    
    /**
     * Create a LightAccessor with a chunk manager.
     */
    public LightAccessor(ChunkManager chunkManager) {
        this.chunkManager = chunkManager;
    }
    
    /**
     * Get skylight at world coordinates.
     * @param worldX World X coordinate
     * @param worldY World Y coordinate (0-383)
     * @param worldZ World Z coordinate
     * @return Skylight level (0-15), or 0 if chunk not loaded
     */
    public int getSkyLight(int worldX, int worldY, int worldZ) {
        int chunkX = Math.floorDiv(worldX, LevelChunk.WIDTH);
        int chunkZ = Math.floorDiv(worldZ, LevelChunk.DEPTH);
        LevelChunk chunk = chunkManager.getChunk(chunkX, chunkZ);
        if (chunk == null) {
            return 0; // Underground default
        }
        
        int localX = Math.floorMod(worldX, LevelChunk.WIDTH);
        int localZ = Math.floorMod(worldZ, LevelChunk.DEPTH);
        return chunk.getSkyLight(localX, worldY, localZ);
    }
    
    /**
     * Get block light at world coordinates.
     * @param worldX World X coordinate
     * @param worldY World Y coordinate (0-383)
     * @param worldZ World Z coordinate
     * @return Block light level (0-15), or 0 if chunk not loaded
     */
    public int getBlockLight(int worldX, int worldY, int worldZ) {
        int chunkX = Math.floorDiv(worldX, LevelChunk.WIDTH);
        int chunkZ = Math.floorDiv(worldZ, LevelChunk.DEPTH);
        LevelChunk chunk = chunkManager.getChunk(chunkX, chunkZ);
        if (chunk == null) {
            return 0;
        }
        
        int localX = Math.floorMod(worldX, LevelChunk.WIDTH);
        int localZ = Math.floorMod(worldZ, LevelChunk.DEPTH);
        return chunk.getBlockLight(localX, worldY, localZ);
    }
    
    /**
     * Get skylight at chunk-local coordinates, checking neighboring chunks if necessary.
     * Used for cross-chunk light sampling.
     * 
     * @param chunk The base chunk
     * @param localX Local X coordinate (can be outside 0-15 range)
     * @param localY Local Y coordinate
     * @param localZ Local Z coordinate (can be outside 0-15 range)
     * @return Skylight level (0-15), or 0 if out of bounds
     */
    public int getSkyLightAcrossChunks(LevelChunk chunk, int localX, int localY, int localZ) {
        // Validate chunk reference
        if (chunk == null) {
            return 0; // Underground default
        }
        
        // Check Y bounds first
        if (localY < 0 || localY >= LevelChunk.HEIGHT) {
            return 0; // Underground default
        }
        
        // Add sanity check for coordinates
        if (Math.abs(localX) > LevelChunk.WIDTH * 2 || Math.abs(localZ) > LevelChunk.DEPTH * 2) {
            return 0; // Underground default
        }
        
        // If within chunk bounds, use direct chunk access
        if (localX >= 0 && localX < LevelChunk.WIDTH && localZ >= 0 && localZ < LevelChunk.DEPTH) {
            return chunk.getSkyLight(localX, localY, localZ);
        }
        
        // Calculate which neighboring chunk to query
        int targetChunkX = chunk.chunkX();
        int targetChunkZ = chunk.chunkZ();
        int targetLocalX = localX;
        int targetLocalZ = localZ;
        
        // Adjust for X boundary crossing
        if (localX < 0) {
            targetChunkX--;
            targetLocalX = LevelChunk.WIDTH + localX;
        } else if (localX >= LevelChunk.WIDTH) {
            targetChunkX++;
            targetLocalX = localX - LevelChunk.WIDTH;
        }
        
        // Adjust for Z boundary crossing
        if (localZ < 0) {
            targetChunkZ--;
            targetLocalZ = LevelChunk.DEPTH + localZ;
        } else if (localZ >= LevelChunk.DEPTH) {
            targetChunkZ++;
            targetLocalZ = localZ - LevelChunk.DEPTH;
        }
        
        // Get the neighboring chunk if it's loaded
        LevelChunk neighborChunk = chunkManager.getChunk(targetChunkX, targetChunkZ);
        if (neighborChunk == null) {
            // Neighboring chunk not loaded - use the edge value from the current chunk
            // This prevents shadow grid patterns at chunk boundaries
            // Clamp coordinates to the current chunk's edge
            int edgeX = Math.max(0, Math.min(LevelChunk.WIDTH - 1, localX));
            int edgeZ = Math.max(0, Math.min(LevelChunk.DEPTH - 1, localZ));
            return chunk.getSkyLight(edgeX, localY, edgeZ);
        }
        
        return neighborChunk.getSkyLight(targetLocalX, localY, targetLocalZ);
    }
    
    /**
     * Get block light at chunk-local coordinates, checking neighboring chunks if necessary.
     * Used for cross-chunk light sampling.
     * 
     * @param chunk The base chunk
     * @param localX Local X coordinate (can be outside 0-15 range)
     * @param localY Local Y coordinate
     * @param localZ Local Z coordinate (can be outside 0-15 range)
     * @return Block light level (0-15), or 0 if out of bounds
     */
    public int getBlockLightAcrossChunks(LevelChunk chunk, int localX, int localY, int localZ) {
        // Validate chunk reference
        if (chunk == null) {
            return 0;
        }
        
        // Check Y bounds first
        if (localY < 0 || localY >= LevelChunk.HEIGHT) {
            return 0;
        }
        
        // Add sanity check for coordinates
        if (Math.abs(localX) > LevelChunk.WIDTH * 2 || Math.abs(localZ) > LevelChunk.DEPTH * 2) {
            return 0;
        }
        
        // If within chunk bounds, use direct chunk access
        if (localX >= 0 && localX < LevelChunk.WIDTH && localZ >= 0 && localZ < LevelChunk.DEPTH) {
            return chunk.getBlockLight(localX, localY, localZ);
        }
        
        // Calculate which neighboring chunk to query
        int targetChunkX = chunk.chunkX();
        int targetChunkZ = chunk.chunkZ();
        int targetLocalX = localX;
        int targetLocalZ = localZ;
        
        // Adjust for X boundary crossing
        if (localX < 0) {
            targetChunkX--;
            targetLocalX = LevelChunk.WIDTH + localX;
        } else if (localX >= LevelChunk.WIDTH) {
            targetChunkX++;
            targetLocalX = localX - LevelChunk.WIDTH;
        }
        
        // Adjust for Z boundary crossing
        if (localZ < 0) {
            targetChunkZ--;
            targetLocalZ = LevelChunk.DEPTH + localZ;
        } else if (localZ >= LevelChunk.DEPTH) {
            targetChunkZ++;
            targetLocalZ = localZ - LevelChunk.DEPTH;
        }
        
        // Get the neighboring chunk if it's loaded
        LevelChunk neighborChunk = chunkManager.getChunk(targetChunkX, targetChunkZ);
        if (neighborChunk == null) {
            // Neighboring chunk not loaded - use the edge value from the current chunk
            // This prevents shadow grid patterns at chunk boundaries
            // Clamp coordinates to the current chunk's edge
            int edgeX = Math.max(0, Math.min(LevelChunk.WIDTH - 1, localX));
            int edgeZ = Math.max(0, Math.min(LevelChunk.DEPTH - 1, localZ));
            return chunk.getBlockLight(edgeX, localY, edgeZ);
        }
        
        return neighborChunk.getBlockLight(targetLocalX, localY, targetLocalZ);
    }
}
