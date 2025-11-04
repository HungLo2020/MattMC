package mattmc.world.level;

import mattmc.client.Minecraft;
import mattmc.world.level.chunk.Region;

import mattmc.world.level.block.Block;
import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;

import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;

/**
 * Manages an infinite world with dynamic chunk loading/unloading.
 * Similar to Minecraft's world management system.
 */
public class Level implements LevelAccessor {
    // Store chunks by their position (chunkX, chunkZ)
    private final Map<Long, LevelChunk> loadedChunks = new HashMap<>();
    
    // Render distance in chunks
    private int renderDistance = 8;
    
    // Last player chunk position for tracking when to load/unload
    private int lastPlayerChunkX = Integer.MAX_VALUE;
    private int lastPlayerChunkZ = Integer.MAX_VALUE;
    
    public Level() {
    }
    
    /**
     * Convert chunk coordinates to a unique long key for the map.
     */
    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long)chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
    
    /**
     * Get a chunk at the specified chunk coordinates.
     * If the chunk doesn't exist, it will be generated.
     */
    public LevelChunk getChunk(int chunkX, int chunkZ) {
        long key = chunkKey(chunkX, chunkZ);
        LevelChunk chunk = loadedChunks.get(key);
        
        if (chunk == null) {
            chunk = generateChunk(chunkX, chunkZ);
            loadedChunks.put(key, chunk);
        }
        
        return chunk;
    }
    
    /**
     * Get a chunk if it's loaded, or null if not.
     */
    public LevelChunk getChunkIfLoaded(int chunkX, int chunkZ) {
        return loadedChunks.get(chunkKey(chunkX, chunkZ));
    }
    
    /**
     * Generate a new chunk at the specified position.
     * Currently generates flat terrain at y=64.
     */
    private LevelChunk generateChunk(int chunkX, int chunkZ) {
        LevelChunk chunk = new LevelChunk(chunkX, chunkZ);
        // Generate flat terrain at y=64
        chunk.generateFlatTerrain(64);
        return chunk;
    }
    
    /**
     * Get a block at world coordinates.
     * @param worldX Level X coordinate (can be any value)
     * @param chunkY LevelChunk-local Y coordinate (0-383, same as Region interface)
     * @param worldZ Level Z coordinate (can be any value)
     */
    public Block getBlock(int worldX, int chunkY, int worldZ) {
        // Convert world coordinates to chunk coordinates
        int chunkX = Math.floorDiv(worldX, LevelChunk.WIDTH);
        int chunkZ = Math.floorDiv(worldZ, LevelChunk.DEPTH);
        
        // Get local coordinates within the chunk
        int localX = Math.floorMod(worldX, LevelChunk.WIDTH);
        int localZ = Math.floorMod(worldZ, LevelChunk.DEPTH);
        
        LevelChunk chunk = getChunkIfLoaded(chunkX, chunkZ);
        if (chunk == null) {
            return Blocks.AIR;
        }
        
        return chunk.getBlock(localX, chunkY, localZ);
    }
    
    /**
     * Set a block at world coordinates.
     * @param worldX Level X coordinate (can be any value)
     * @param chunkY LevelChunk-local Y coordinate (0-383, same as Region interface)
     * @param worldZ Level Z coordinate (can be any value)
     */
    public void setBlock(int worldX, int chunkY, int worldZ, Block block) {
        // Convert world coordinates to chunk coordinates
        int chunkX = Math.floorDiv(worldX, LevelChunk.WIDTH);
        int chunkZ = Math.floorDiv(worldZ, LevelChunk.DEPTH);
        
        // Get local coordinates within the chunk
        int localX = Math.floorMod(worldX, LevelChunk.WIDTH);
        int localZ = Math.floorMod(worldZ, LevelChunk.DEPTH);
        
        LevelChunk chunk = getChunk(chunkX, chunkZ);
        chunk.setBlock(localX, chunkY, localZ, block);
    }
    
    /**
     * Update the world based on player position.
     * Loads chunks near the player and unloads distant chunks.
     */
    public void updateChunksAroundPlayer(float playerX, float playerZ) {
        // Convert player position to chunk coordinates
        int playerChunkX = Math.floorDiv((int)playerX, LevelChunk.WIDTH);
        int playerChunkZ = Math.floorDiv((int)playerZ, LevelChunk.DEPTH);
        
        // Only update if player moved to a different chunk
        if (playerChunkX == lastPlayerChunkX && playerChunkZ == lastPlayerChunkZ) {
            return;
        }
        
        lastPlayerChunkX = playerChunkX;
        lastPlayerChunkZ = playerChunkZ;
        
        // Load chunks around player
        for (int dx = -renderDistance; dx <= renderDistance; dx++) {
            for (int dz = -renderDistance; dz <= renderDistance; dz++) {
                int chunkX = playerChunkX + dx;
                int chunkZ = playerChunkZ + dz;
                
                // Ensure chunk is loaded
                getChunk(chunkX, chunkZ);
            }
        }
        
        // Unload chunks that are too far away
        int unloadDistance = renderDistance + 2;
        Iterator<Map.Entry<Long, LevelChunk>> iterator = loadedChunks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, LevelChunk> entry = iterator.next();
            LevelChunk chunk = entry.getValue();
            
            int dx = Math.abs(chunk.chunkX() - playerChunkX);
            int dz = Math.abs(chunk.chunkZ() - playerChunkZ);
            
            if (dx > unloadDistance || dz > unloadDistance) {
                iterator.remove();
            }
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
     * Set the render distance in chunks.
     */
    public void setRenderDistance(int distance) {
        this.renderDistance = Math.max(2, Math.min(distance, 32));
    }
    
    /**
     * Get the current render distance.
     */
    public int getRenderDistance() {
        return renderDistance;
    }
}
