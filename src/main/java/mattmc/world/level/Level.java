package mattmc.world.level;

import mattmc.client.Minecraft;
import mattmc.client.renderer.block.BlockFaceCollector;
import mattmc.world.level.chunk.Region;

import mattmc.world.level.block.Block;
import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.ChunkManager;
import mattmc.world.level.chunk.LevelChunk;
import mattmc.world.level.chunk.ChunkNBT;
import mattmc.world.level.chunk.RegionFile;
import mattmc.world.level.chunk.RegionFileCache;
import mattmc.world.level.chunk.AsyncChunkLoader;
import mattmc.world.level.chunk.AsyncChunkSaver;
import mattmc.world.level.chunk.ChunkUtils;
import mattmc.world.level.levelgen.WorldGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Manages an infinite world with dynamic chunk loading/unloading.
 * Similar to Minecraft's world management system.
 * 
 * Now uses asynchronous chunk loading and saving to prevent lag spikes.
 * Uses region file caching for better I/O performance.
 */
public class Level implements LevelAccessor {
    private static final Logger logger = LoggerFactory.getLogger(Level.class);
    
    // Chunk manager for handling loaded chunks lifecycle
    private final ChunkManager chunkManager = new ChunkManager();
    
    // World block access for unified block operations across chunks
    private final WorldBlockAccess blockAccess;
    
    // Async chunk loader for background loading/generation
    private final AsyncChunkLoader asyncLoader;
    
    // Region file cache for efficient chunk I/O
    private RegionFileCache regionCache;
    
    // Async chunk saver for background saving
    private AsyncChunkSaver asyncSaver;
    
    // World generator for noise-based terrain
    private WorldGenerator worldGenerator;
    
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
    
    // Day/night cycle
    private final DayCycle dayCycle = new DayCycle();
    
    public Level() {
        // Initialize block access with chunk manager
        this.blockAccess = new WorldBlockAccess(chunkManager);
        
        // Initialize world light manager with neighbor accessor
        mattmc.world.level.lighting.WorldLightManager.getInstance()
            .setNeighborAccessor(this::getChunkIfLoaded);
        
        this.asyncLoader = new AsyncChunkLoader();
        // Initialize with a default seed (will be updated when world is loaded/created)
        this.worldGenerator = new WorldGenerator(0L);
        this.asyncLoader.setWorldGenerator(worldGenerator);
        
        // Set the neighbor accessor for cross-chunk face culling
        BlockFaceCollector.ChunkNeighborAccessor neighborAccessor = blockAccess::getBlockAcrossChunks;
        this.asyncLoader.setNeighborAccessor(neighborAccessor);
        
        // Set the light accessor for cross-chunk light sampling
        AsyncChunkLoader.LightAccessor lightAccessor = new AsyncChunkLoader.LightAccessor() {
            @Override
            public int getSkyLight(LevelChunk chunk, int x, int y, int z) {
                return blockAccess.getSkyLightAcrossChunks(chunk, x, y, z);
            }
            
            @Override
            public int getBlockLight(LevelChunk chunk, int x, int y, int z) {
                return blockAccess.getBlockLightAcrossChunks(chunk, x, y, z);
            }
            
            @Override
            public int getBlockLightR(LevelChunk chunk, int x, int y, int z) {
                return blockAccess.getBlockLightRAcrossChunks(chunk, x, y, z);
            }
            
            @Override
            public int getBlockLightG(LevelChunk chunk, int x, int y, int z) {
                return blockAccess.getBlockLightGAcrossChunks(chunk, x, y, z);
            }
            
            @Override
            public int getBlockLightB(LevelChunk chunk, int x, int y, int z) {
                return blockAccess.getBlockLightBAcrossChunks(chunk, x, y, z);
            }
            
            @Override
            public int getBlockLightI(LevelChunk chunk, int x, int y, int z) {
                return blockAccess.getBlockLightIAcrossChunks(chunk, x, y, z);
            }
        };
        this.asyncLoader.setLightAccessor(lightAccessor);
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
                logger.error("Failed to initialize region cache: {}", e.getMessage(), e);
                // Reset to null to maintain consistency - saves won't work
                this.worldDirectory = null;
                this.asyncLoader.setWorldDirectory(null);
                throw new RuntimeException("Failed to initialize world save directory", e);
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
     * Get a chunk at the specified chunk coordinates.
     * If the chunk doesn't exist in memory, tries to load it from disk.
     * If it doesn't exist on disk, it will be generated.
     * 
     * ISSUE-012 fix: Added coordinate validation to prevent overflow at extreme coordinates.
     */
    public LevelChunk getChunk(int chunkX, int chunkZ) {
        // Validate chunk coordinates are within reasonable bounds
        // This prevents integer overflow and chunk key collisions at extreme coordinates
        final int MAX_CHUNK_COORD = 1_000_000;
        if (Math.abs(chunkX) > MAX_CHUNK_COORD || Math.abs(chunkZ) > MAX_CHUNK_COORD) {
            logger.error("Chunk coordinates out of bounds: ({}, {}). Max allowed: ±{}", 
                        chunkX, chunkZ, MAX_CHUNK_COORD);
            // Return an empty air chunk to prevent crashes
            return new LevelChunk(0, 0); // Fallback to origin
        }
        
        LevelChunk chunk = chunkManager.getChunk(chunkX, chunkZ);
        
        if (chunk == null) {
            // Try to load from disk first if world directory is set
            chunk = loadChunkFromDisk(chunkX, chunkZ);
            
            // If not found on disk, generate a new chunk
            if (chunk == null) {
                chunk = generateChunk(chunkX, chunkZ);
            }
            
            chunkManager.addChunk(chunk);
            
            // Process any deferred light updates for this chunk
            mattmc.world.level.lighting.WorldLightManager.getInstance()
                .processDeferredUpdates(chunk);
        }
        
        return chunk;
    }
    
    /**
     * Get a chunk if it's loaded, or null if not.
     */
    public LevelChunk getChunkIfLoaded(int chunkX, int chunkZ) {
        return chunkManager.getChunk(chunkX, chunkZ);
    }
    
    /**
     * Try to load a chunk from disk.
     * Returns null if the chunk doesn't exist on disk or if world directory is not set.
     * Uses region cache for better performance (Minecraft Java Edition approach).
     */
    private LevelChunk loadChunkFromDisk(int chunkX, int chunkZ) {
        if (worldDirectory == null || regionCache == null) {
            return null; // No save directory or cache set
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
            
            // Use cached region file instead of creating a new one
            RegionFile regionFile = regionCache.getRegionFile(chunkX, chunkZ);
            if (!regionFile.hasChunk(chunkX, chunkZ)) {
                return null; // Chunk not in region file
            }
            
            Map<String, Object> chunkNBT = regionFile.readChunk(chunkX, chunkZ);
            if (chunkNBT != null) {
                return ChunkNBT.fromNBT(chunkNBT);
            }
        } catch (IOException e) {
            logger.error("Failed to load chunk ({}, {}) from disk: {}", chunkX, chunkZ, e.getMessage(), e);
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
        return blockAccess.getBlock(worldX, chunkY, worldZ);
    }
    
    /**
     * Get a blockstate at world coordinates.
     */
    @Override
    public mattmc.world.level.block.state.BlockState getBlockState(int worldX, int chunkY, int worldZ) {
        return blockAccess.getBlockState(worldX, chunkY, worldZ);
    }
    
    /**
     * Set a block at world coordinates.
     * @param worldX Level X coordinate (can be any value)
     * @param chunkY LevelChunk-local Y coordinate (0-383, same as Region interface)
     * @param worldZ Level Z coordinate (can be any value)
     */
    public void setBlock(int worldX, int chunkY, int worldZ, Block block) {
        setBlock(worldX, chunkY, worldZ, block, null);
    }
    
    /**
     * Set a block with blockstate at world coordinates.
     */
    @Override
    public void setBlock(int worldX, int chunkY, int worldZ, Block block, mattmc.world.level.block.state.BlockState state) {
        blockAccess.setBlock(worldX, chunkY, worldZ, block, state, this::getChunk);
        
        // Mark adjacent chunks as dirty if the block is at a chunk boundary
        blockAccess.markAdjacentChunksDirtyIfOnBoundary(worldX, worldZ);
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
            chunkManager.addChunk(chunk);
        }
        
        // Convert player position to chunk coordinates
        int playerChunkX = Math.floorDiv((int)playerX, LevelChunk.WIDTH);
        int playerChunkZ = Math.floorDiv((int)playerZ, LevelChunk.DEPTH);
        
        // Request chunks around player (async)
        for (int dx = -renderDistance; dx <= renderDistance; dx++) {
            for (int dz = -renderDistance; dz <= renderDistance; dz++) {
                int chunkX = playerChunkX + dx;
                int chunkZ = playerChunkZ + dz;
                
                if (!chunkManager.isChunkLoaded(chunkX, chunkZ)) {
                    // Request async loading with frustum prioritization
                    asyncLoader.requestChunk(chunkX, chunkZ, playerX, playerZ, playerYaw);
                }
            }
        }
        
        // Unload chunks that are too far away
        int unloadDistance = renderDistance + 2;
        chunkManager.unloadChunksOutsideRadius(playerChunkX, playerChunkZ, unloadDistance, 
                                               this::saveChunk);
        
        lastPlayerChunkX = playerChunkX;
        lastPlayerChunkZ = playerChunkZ;
    }
    
    /**
     * Get all currently loaded chunks.
     */
    public Iterable<LevelChunk> getLoadedChunks() {
        return chunkManager.getLoadedChunks();
    }
    
    /**
     * Get the number of loaded chunks.
     */
    public int getLoadedChunkCount() {
        return chunkManager.getLoadedChunkCount();
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
     * Get the day/night cycle manager.
     */
    public DayCycle getDayCycle() {
        return dayCycle;
    }
    
    /**
     * Tick the world's day/night cycle.
     * Should be called once per game tick (20 TPS).
     */
    public void tickDayCycle() {
        dayCycle.tick();
    }
    
    /**
     * Process light updates with a time budget.
     * Should be called each frame to incrementally update lighting.
     * Uses the RelightScheduler for distance-based prioritization.
     * 
    /**
     * Set a listener for chunk unload events.
     * Used by the renderer to clean up caches.
     */
    public void setChunkUnloadListener(ChunkUnloadListener listener) {
        chunkManager.setUnloadListener(listener);
    }
    
    /**
     * Shutdown the async loader and wait for tasks to complete.
     * Call this when closing the world.
     */
    public void shutdown() {
        logger.info("Shutting down level...");
        
        // Save all loaded chunks before shutting down the async saver
        // Collect exceptions but continue trying to save all chunks
        int savedCount = 0;
        int failedCount = 0;
        for (LevelChunk chunk : chunkManager.getLoadedChunks()) {
            try {
                if (worldDirectory != null && asyncSaver != null) {
                    Map<String, Object> chunkNBT = ChunkNBT.toNBT(chunk);
                    asyncSaver.saveChunkAsync(chunk.chunkX(), chunk.chunkZ(), chunkNBT);
                    savedCount++;
                }
            } catch (Exception e) {
                logger.error("Error saving chunk ({}, {}) during shutdown: {}", 
                           chunk.chunkX(), chunk.chunkZ(), e.getMessage(), e);
                failedCount++;
            }
        }
        
        logger.info("Queued {} chunks for saving ({} failed)", savedCount, failedCount);
        
        // Flush any pending chunk saves
        if (asyncSaver != null) {
            try {
                asyncSaver.shutdown();
            } catch (Exception e) {
                logger.error("Error shutting down async saver: {}", e.getMessage(), e);
            }
        }
        
        // Close region cache
        if (regionCache != null) {
            try {
                regionCache.close();
            } catch (IOException e) {
                logger.error("Error closing region cache: {}", e.getMessage(), e);
            }
        }
        
        // Shutdown async loader
        try {
            asyncLoader.shutdown();
        } catch (Exception e) {
            logger.error("Error shutting down async loader: {}", e.getMessage(), e);
        }
        
        logger.info("Level shutdown complete");
    }
}
