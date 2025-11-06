package mattmc.world.level;

import mattmc.client.Minecraft;
import mattmc.world.level.chunk.Region;

import mattmc.world.level.block.Block;
import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import mattmc.world.level.chunk.ChunkNBT;
import mattmc.world.level.chunk.RegionFile;
import mattmc.world.level.chunk.RegionFileCache;
import mattmc.world.level.chunk.AsyncChunkLoader;
import mattmc.world.level.chunk.AsyncChunkSaver;
import mattmc.world.level.chunk.ChunkUtils;
import mattmc.world.level.levelgen.WorldGenerator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.List;

/**
 * Manages an infinite world with dynamic chunk loading/unloading.
 * Similar to Minecraft's world management system.
 * 
 * Now uses asynchronous chunk loading and saving to prevent lag spikes.
 * Uses region file caching for better I/O performance.
 */
public class Level implements LevelAccessor {
    // Store chunks by their position (chunkX, chunkZ)
    private final Map<Long, LevelChunk> loadedChunks = new HashMap<>();
    
    // Async chunk loader for background loading/generation
    private final AsyncChunkLoader asyncLoader;
    
    // Region file cache for efficient chunk I/O
    private RegionFileCache regionCache;
    
    // Async chunk saver for background saving
    private AsyncChunkSaver asyncSaver;
    
    // World generator for noise-based terrain
    private WorldGenerator worldGenerator;
    
    // Listener for chunk unload events (used by renderer to clean up caches)
    private ChunkUnloadListener unloadListener;
    
    // Render distance in chunks
    private int renderDistance = 8;
    
    /**
     * Listener interface for chunk unload events.
     */
    public interface ChunkUnloadListener {
        void onChunkUnload(LevelChunk chunk);
    }
    
    // Last player chunk position for tracking when to load/unload
    private int lastPlayerChunkX = Integer.MAX_VALUE;
    private int lastPlayerChunkZ = Integer.MAX_VALUE;
    
    // World save directory (null if world is not being saved)
    private Path worldDirectory = null;
    
    public Level() {
        this.asyncLoader = new AsyncChunkLoader();
        // Initialize with a default seed (will be updated when world is loaded/created)
        this.worldGenerator = new WorldGenerator(0L);
        this.asyncLoader.setWorldGenerator(worldGenerator);
    }
    
    /**
     * Set the world seed for terrain generation.
     * This should be called when creating or loading a world.
     */
    public void setSeed(long seed) {
        this.worldGenerator = new WorldGenerator(seed);
        this.asyncLoader.setWorldGenerator(worldGenerator);
    }
    
    /**
     * Get the current world seed.
     */
    public long getSeed() {
        return worldGenerator.getSeed();
    }
    
    /**
     * Set the world directory for saving chunks.
     * This should be set when a world is loaded or created.
     */
    public void setWorldDirectory(Path worldDirectory) {
        this.worldDirectory = worldDirectory;
        this.asyncLoader.setWorldDirectory(worldDirectory);
        
        // Initialize region cache and async saver
        if (worldDirectory != null) {
            try {
                Path regionDir = worldDirectory.resolve("region");
                java.nio.file.Files.createDirectories(regionDir);
                
                // Close old cache/saver if they exist
                if (regionCache != null) {
                    regionCache.close();
                }
                if (asyncSaver != null) {
                    asyncSaver.shutdown();
                }
                
                // Create new cache and saver
                regionCache = new RegionFileCache(regionDir);
                asyncSaver = new AsyncChunkSaver(regionCache);
                
                // Pass region cache to async loader for efficient loading
                asyncLoader.setRegionCache(regionCache);
            } catch (IOException e) {
                System.err.println("Failed to initialize region cache: " + e.getMessage());
            }
        }
    }
    
    /**
     * Get the world directory.
     */
    public Path getWorldDirectory() {
        return worldDirectory;
    }
    
    /**
     * Convert chunk coordinates to a unique long key for the map.
     */
    private static long chunkKey(int chunkX, int chunkZ) {
        return ChunkUtils.chunkKey(chunkX, chunkZ);
    }
    
    /**
     * Get a chunk at the specified chunk coordinates.
     * If the chunk doesn't exist in memory, tries to load it from disk.
     * If it doesn't exist on disk, it will be generated.
     */
    public LevelChunk getChunk(int chunkX, int chunkZ) {
        long key = chunkKey(chunkX, chunkZ);
        LevelChunk chunk = loadedChunks.get(key);
        
        if (chunk == null) {
            // Try to load from disk first if world directory is set
            chunk = loadChunkFromDisk(chunkX, chunkZ);
            
            // If not found on disk, generate a new chunk
            if (chunk == null) {
                chunk = generateChunk(chunkX, chunkZ);
            }
            
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
     * Try to load a chunk from disk.
     * Returns null if the chunk doesn't exist on disk or if world directory is not set.
     */
    private LevelChunk loadChunkFromDisk(int chunkX, int chunkZ) {
        if (worldDirectory == null) {
            return null; // No save directory set
        }
        
        try {
            Path regionDir = worldDirectory.resolve("region");
            if (!java.nio.file.Files.exists(regionDir)) {
                return null; // No region directory yet
            }
            
            int[] regionCoords = RegionFile.getRegionCoords(chunkX, chunkZ);
            int regionX = regionCoords[0];
            int regionZ = regionCoords[1];
            
            Path regionFilePath = regionDir.resolve(String.format("r.%d.%d.mca", regionX, regionZ));
            
            if (!java.nio.file.Files.exists(regionFilePath)) {
                return null; // Region file doesn't exist
            }
            
            try (RegionFile regionFile = new RegionFile(regionFilePath, regionX, regionZ)) {
                if (!regionFile.hasChunk(chunkX, chunkZ)) {
                    return null; // Chunk not in region file
                }
                
                Map<String, Object> chunkNBT = regionFile.readChunk(chunkX, chunkZ);
                if (chunkNBT != null) {
                    return ChunkNBT.fromNBT(chunkNBT);
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to load chunk (" + chunkX + ", " + chunkZ + ") from disk: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Generate a new chunk at the specified position.
     * Uses noise-based terrain generation.
     */
    private LevelChunk generateChunk(int chunkX, int chunkZ) {
        LevelChunk chunk = new LevelChunk(chunkX, chunkZ);
        worldGenerator.generateChunkTerrain(chunk);
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
     * Save a single chunk to disk if the world directory is set.
     * This is called when chunks are unloaded to preserve modifications.
     * Now uses async saving to prevent lag spikes.
     */
    private void saveChunk(LevelChunk chunk) {
        if (worldDirectory == null || asyncSaver == null) {
            return; // No save directory set, skip saving
        }
        
        // Convert chunk to NBT and queue for async saving
        Map<String, Object> chunkNBT = ChunkNBT.toNBT(chunk);
        asyncSaver.saveChunkAsync(chunk.chunkX(), chunk.chunkZ(), chunkNBT);
    }
    
    /**
     * Update the world based on player position.
     * Loads chunks near the player and unloads distant chunks.
     * 
     * Now uses asynchronous loading to prevent lag spikes.
     */
    public void updateChunksAroundPlayer(float playerX, float playerZ) {
        updateChunksAroundPlayer(playerX, playerZ, 0f);
    }
    
    /**
     * Update the world based on player position and view direction.
     * Loads chunks near the player and unloads distant chunks.
     * Prioritizes chunks in the player's view frustum.
     * 
     * @param playerYaw Player's yaw angle in degrees for frustum-based prioritization
     */
    public void updateChunksAroundPlayer(float playerX, float playerZ, float playerYaw) {
        // Process pending async tasks
        asyncLoader.processPendingTasks();
        
        // Collect completed chunks from background threads
        List<LevelChunk> completedChunks = asyncLoader.collectCompletedChunks();
        for (LevelChunk chunk : completedChunks) {
            long key = chunkKey(chunk.chunkX(), chunk.chunkZ());
            loadedChunks.put(key, chunk);
        }
        
        // Convert player position to chunk coordinates
        int playerChunkX = Math.floorDiv((int)playerX, LevelChunk.WIDTH);
        int playerChunkZ = Math.floorDiv((int)playerZ, LevelChunk.DEPTH);
        
        // Request chunks around player (async)
        for (int dx = -renderDistance; dx <= renderDistance; dx++) {
            for (int dz = -renderDistance; dz <= renderDistance; dz++) {
                int chunkX = playerChunkX + dx;
                int chunkZ = playerChunkZ + dz;
                
                long key = chunkKey(chunkX, chunkZ);
                if (!loadedChunks.containsKey(key)) {
                    // Request async loading with frustum prioritization
                    asyncLoader.requestChunk(chunkX, chunkZ, playerX, playerZ, playerYaw);
                }
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
                // Always save chunks before unloading to preserve any modifications
                saveChunk(chunk);
                
                // Notify listener before removing
                if (unloadListener != null) {
                    unloadListener.onChunkUnload(chunk);
                }
                
                iterator.remove();
            }
        }
        
        lastPlayerChunkX = playerChunkX;
        lastPlayerChunkZ = playerChunkZ;
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
    
    /**
     * Get the async chunk loader for accessing loading stats.
     */
    public AsyncChunkLoader getAsyncLoader() {
        return asyncLoader;
    }
    
    /**
     * Set a listener for chunk unload events.
     * Used by the renderer to clean up caches.
     */
    public void setChunkUnloadListener(ChunkUnloadListener listener) {
        this.unloadListener = listener;
    }
    
    /**
     * Shutdown the async loader and wait for tasks to complete.
     * Call this when closing the world.
     */
    public void shutdown() {
        // Flush any pending chunk saves
        if (asyncSaver != null) {
            asyncSaver.shutdown();
        }
        
        // Close region cache
        if (regionCache != null) {
            try {
                regionCache.close();
            } catch (IOException e) {
                System.err.println("Error closing region cache: " + e.getMessage());
            }
        }
        
        asyncLoader.shutdown();
    }
}
