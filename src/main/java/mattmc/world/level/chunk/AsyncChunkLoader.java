package mattmc.world.level.chunk;

import mattmc.client.renderer.chunk.ChunkMeshData;
import mattmc.client.renderer.chunk.ChunkMeshBuffer;
import mattmc.client.renderer.chunk.MeshBuilder;
import mattmc.client.renderer.chunk.VertexLightSampler;
import mattmc.client.renderer.texture.TextureAtlas;
import mattmc.client.renderer.block.BlockFaceCollector;
import mattmc.world.level.block.Block;
import mattmc.world.level.block.Blocks;
import mattmc.world.level.levelgen.WorldGenerator;
import mattmc.world.level.lighting.WorldLightManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/**
 * Manages asynchronous chunk loading, generation, and meshing.
 * 
 * This class coordinates background tasks to:
 * - Load chunks from disk on worker threads
 * - Generate chunk terrain on worker threads
 * - Build chunk meshes on worker threads
 * - Queue results for GPU upload on the render thread
 */
public class AsyncChunkLoader {
    private static final Logger logger = LoggerFactory.getLogger(AsyncChunkLoader.class);
    
    private final ChunkTaskExecutor executor;
    private final PriorityBlockingQueue<ChunkLoadTask> pendingTasks;
    // ISSUE-014 fix: Use ConcurrentHashMap.KeySetView instead of synchronized Set
    private final ConcurrentHashMap.KeySetView<Long, Boolean> tasksInProgress;
    private final Map<Long, Future<LevelChunk>> chunkFutures;
    private final Map<Long, Future<ChunkMeshData>> meshFutures;
    private final Queue<ChunkMeshData> completedMeshes;
    private final Map<Long, Future<ChunkMeshBuffer>> meshBufferFutures;
    private final Queue<ChunkMeshBuffer> completedMeshBuffers;
    
    // Budget: maximum operations per frame
    private static final int MAX_CHUNK_LOADS_PER_FRAME = 4;
    private static final int MAX_MESH_UPLOADS_PER_FRAME = 2;
    
    private Path worldDirectory;
    private RegionFileCache regionCache;
    private TextureAtlas textureAtlas;
    private WorldGenerator worldGenerator;
    private WorldLightManager worldLightManager;
    private BlockFaceCollector.ChunkNeighborAccessor neighborAccessor;
    private LightAccessor lightAccessor;
    
    /**
     * Interface for accessing light values across chunk boundaries.
     */
    public interface LightAccessor {
        int getSkyLight(LevelChunk chunk, int x, int y, int z);
        int getBlockLight(LevelChunk chunk, int x, int y, int z);
        
        // RGBI light methods for proper color and attenuation
        default int getBlockLightR(LevelChunk chunk, int x, int y, int z) {
            return getBlockLight(chunk, x, y, z); // Fallback to intensity for white light
        }
        
        default int getBlockLightG(LevelChunk chunk, int x, int y, int z) {
            return getBlockLight(chunk, x, y, z); // Fallback to intensity for white light
        }
        
        default int getBlockLightB(LevelChunk chunk, int x, int y, int z) {
            return getBlockLight(chunk, x, y, z); // Fallback to intensity for white light
        }
        
        default int getBlockLightI(LevelChunk chunk, int x, int y, int z) {
            return getBlockLight(chunk, x, y, z); // Fallback to intensity
        }
    }
    
    public AsyncChunkLoader() {
        this.executor = new ChunkTaskExecutor();
        this.pendingTasks = new PriorityBlockingQueue<>();
        // ISSUE-014 fix: Use lock-free concurrent set
        this.tasksInProgress = ConcurrentHashMap.newKeySet();
        this.chunkFutures = new ConcurrentHashMap<>();
        this.meshFutures = new ConcurrentHashMap<>();
        this.completedMeshes = new ConcurrentLinkedQueue<>();
        this.meshBufferFutures = new ConcurrentHashMap<>();
        this.completedMeshBuffers = new ConcurrentLinkedQueue<>();
    }
    
    public void setWorldDirectory(Path worldDirectory) {
        this.worldDirectory = worldDirectory;
    }
    
    public void setRegionCache(RegionFileCache regionCache) {
        this.regionCache = regionCache;
    }
    
    public void setTextureAtlas(TextureAtlas atlas) {
        this.textureAtlas = atlas;
    }
    
    public void setWorldGenerator(WorldGenerator generator) {
        this.worldGenerator = generator;
    }
    
    /**
     * Set the world light manager for light initialization.
     */
    public void setWorldLightManager(WorldLightManager worldLightManager) {
        this.worldLightManager = worldLightManager;
    }
    
    /**
     * Set the chunk neighbor accessor for cross-chunk face culling.
     */
    public void setNeighborAccessor(BlockFaceCollector.ChunkNeighborAccessor accessor) {
        this.neighborAccessor = accessor;
    }
    
    /**
     * Set the light accessor for cross-chunk light sampling.
     */
    public void setLightAccessor(LightAccessor accessor) {
        this.lightAccessor = accessor;
    }
    
    /**
     * Request a chunk to be loaded/generated.
     * Returns immediately, chunk will be loaded in the background.
     * 
     * @param playerYaw Player's yaw angle in degrees for frustum prioritization
     */
    public void requestChunk(int chunkX, int chunkZ, double playerX, double playerZ, float playerYaw) {
        long key = ChunkUtils.chunkKey(chunkX, chunkZ);
        
        // ISSUE-014 fix: Use lock-free atomic operations instead of synchronized block
        // Skip if already loading, meshing, or in progress
        // Use add() which returns false if already present (atomic operation)
        if (!tasksInProgress.add(key)) {
            return; // Already in progress
        }
        
        // Double-check if futures exist (rare race condition)
        if (chunkFutures.containsKey(key) || meshFutures.containsKey(key)) {
            tasksInProgress.remove(key); // Remove from set since we're not actually processing
            return;
        }
        
        // Calculate priority (distance from player)
        double dx = (chunkX * LevelChunk.WIDTH + 8) - playerX;
        double dz = (chunkZ * LevelChunk.DEPTH + 8) - playerZ;
        double distanceSquared = dx * dx + dz * dz;
        
        // Boost priority for chunks in view frustum
        // Calculate angle to chunk center from player
        double angleToChunk = Math.toDegrees(Math.atan2(dx, dz));
        double angleDiff = Math.abs(normalizeAngle(angleToChunk - playerYaw));
        
        // Chunks within 90 degrees of view direction get priority boost
        if (angleDiff < 90) {
            // Reduce effective distance for chunks in view (higher priority)
            double frustumBoost = 1.0 - (angleDiff / 180.0); // 0.5 to 1.0
            distanceSquared *= (1.0 - frustumBoost * 0.5); // Up to 50% distance reduction
        }
        
        // Add to priority queue
        ChunkLoadTask task = new ChunkLoadTask(chunkX, chunkZ, distanceSquared, 
                                               ChunkLoadTask.TaskType.GENERATION);
        pendingTasks.offer(task);
    }
    
    /**
     * Normalize angle to -180 to 180 range.
     */
    private double normalizeAngle(double angle) {
        while (angle > 180) angle -= 360;
        while (angle < -180) angle += 360;
        return angle;
    }
    
    /**
     * Process pending chunk load tasks up to the budget limit.
     * Call this once per frame.
     */
    public void processPendingTasks() {
        int processed = 0;
        
        while (processed < MAX_CHUNK_LOADS_PER_FRAME && !pendingTasks.isEmpty()) {
            ChunkLoadTask task = pendingTasks.poll();
            if (task == null) break;
            
            long key = ChunkUtils.chunkKey(task.getChunkX(), task.getChunkZ());
            
            // Submit chunk loading task
            Future<LevelChunk> future = executor.submit(() -> loadOrGenerateChunk(task.getChunkX(), task.getChunkZ()));
            chunkFutures.put(key, future);
            
            processed++;
        }
    }
    
    /**
     * Check for completed chunks and mesh them.
     * Returns completed chunks ready to be added to the world.
     */
    public List<LevelChunk> collectCompletedChunks() {
        List<LevelChunk> completed = new ArrayList<>();
        Iterator<Map.Entry<Long, Future<LevelChunk>>> iterator = chunkFutures.entrySet().iterator();
        
        while (iterator.hasNext()) {
            Map.Entry<Long, Future<LevelChunk>> entry = iterator.next();
            Future<LevelChunk> future = entry.getValue();
            
            if (future.isDone()) {
                long key = entry.getKey();
                try {
                    LevelChunk chunk = future.get();
                    completed.add(chunk);
                    
                    // logger.debug("Chunk loaded/generated: ({}, {}), starting mesh building...", chunk.chunkX(), chunk.chunkZ());
                    
                    // Start mesh building for this chunk (both old and new formats)
                    Future<ChunkMeshData> meshFuture = executor.submit(() -> buildChunkMesh(chunk));
                    meshFutures.put(key, meshFuture);
                    
                    // Also build VBO mesh buffer
                    Future<ChunkMeshBuffer> meshBufferFuture = executor.submit(() -> buildChunkMeshBuffer(chunk));
                    meshBufferFutures.put(key, meshBufferFuture);
                    // logger.debug("Submitted mesh buffer build task for chunk ({}, {})", chunk.chunkX(), chunk.chunkZ());
                    
                } catch (InterruptedException | ExecutionException e) {
                    logger.error("Failed to load chunk: {}", e.getMessage(), e);
                }
                
                iterator.remove();
                // ISSUE-014 fix: Use lock-free remove operation
                tasksInProgress.remove(key);
            }
        }
        
        return completed;
    }
    
    /**
     * Collect completed mesh data ready for GPU upload.
     * Limited by budget to spread GPU uploads across frames.
     */
    public List<ChunkMeshData> collectCompletedMeshes() {
        // First, check for newly completed meshes
        Iterator<Map.Entry<Long, Future<ChunkMeshData>>> iterator = meshFutures.entrySet().iterator();
        
        while (iterator.hasNext()) {
            Map.Entry<Long, Future<ChunkMeshData>> entry = iterator.next();
            Future<ChunkMeshData> future = entry.getValue();
            
            if (future.isDone()) {
                try {
                    ChunkMeshData meshData = future.get();
                    completedMeshes.offer(meshData);
                } catch (InterruptedException | ExecutionException e) {
                    logger.error("Failed to build mesh: {}", e.getMessage(), e);
                }
                iterator.remove();
            }
        }
        
        // Return up to budget limit for this frame
        // Pre-allocate with maximum possible size to avoid resizing
        List<ChunkMeshData> result = new ArrayList<>(MAX_MESH_UPLOADS_PER_FRAME);
        for (int i = 0; i < MAX_MESH_UPLOADS_PER_FRAME && !completedMeshes.isEmpty(); i++) {
            ChunkMeshData meshData = completedMeshes.poll();
            if (meshData != null) {
                result.add(meshData);
            }
        }
        
        return result;
    }
    
    /**
     * Load chunk from disk or generate it.
     * Runs on background thread.
     */
    private LevelChunk loadOrGenerateChunk(int chunkX, int chunkZ) {
        // Try loading from disk first
        if (worldDirectory != null) {
            LevelChunk chunk = loadChunkFromDisk(chunkX, chunkZ);
            if (chunk != null) {
                return chunk;
            }
        }
        
        // Generate new chunk
        return generateChunk(chunkX, chunkZ);
    }
    
    /**
     * Load chunk from disk.
     * Runs on background thread.
     */
    private LevelChunk loadChunkFromDisk(int chunkX, int chunkZ) {
        // Use region cache if available, otherwise fall back to direct file access
        if (regionCache != null) {
            try {
                RegionFile regionFile = regionCache.getRegionFile(chunkX, chunkZ);
                if (regionFile.hasChunk(chunkX, chunkZ)) {
                    Map<String, Object> chunkNBT = regionFile.readChunk(chunkX, chunkZ);
                    if (chunkNBT != null) {
                        LevelChunk chunk = ChunkNBT.fromNBT(chunkNBT);
                        // Initialize skylight for loaded chunks with BFS propagation
                        if (worldLightManager != null) {
                            worldLightManager.initializeChunkSkylight(chunk);
                        }
                        return chunk;
                    }
                }
            } catch (IOException e) {
                logger.error("Failed to load chunk ({}, {}) from cache: {}", chunkX, chunkZ, e.getMessage(), e);
            }
            return null;
        }
        
        // Fallback to direct file access (legacy)
        if (worldDirectory == null) {
            return null;
        }
        
        try {
            Path regionDir = worldDirectory.resolve("region");
            if (!java.nio.file.Files.exists(regionDir)) {
                return null;
            }
            
            int[] regionCoords = RegionFile.getRegionCoords(chunkX, chunkZ);
            int regionX = regionCoords[0];
            int regionZ = regionCoords[1];
            
            Path regionFilePath = regionDir.resolve(String.format("r.%d.%d.mca", regionX, regionZ));
            
            if (!java.nio.file.Files.exists(regionFilePath)) {
                return null;
            }
            
            try (RegionFile regionFile = new RegionFile(regionFilePath, regionX, regionZ)) {
                if (!regionFile.hasChunk(chunkX, chunkZ)) {
                    return null;
                }
                
                Map<String, Object> chunkNBT = regionFile.readChunk(chunkX, chunkZ);
                if (chunkNBT != null) {
                    LevelChunk chunk = ChunkNBT.fromNBT(chunkNBT);
                    // Initialize skylight for loaded chunks with BFS propagation
                    if (worldLightManager != null) {
                        worldLightManager.initializeChunkSkylight(chunk);
                    }
                    return chunk;
                }
            }
        } catch (IOException e) {
            logger.error("Failed to load chunk ({}, {}) from disk: {}", chunkX, chunkZ, e.getMessage(), e);
        }
        
        return null;
    }
    
    /**
     * Generate a new chunk.
     * Runs on background thread.
     */
    private LevelChunk generateChunk(int chunkX, int chunkZ) {
        LevelChunk chunk = new LevelChunk(chunkX, chunkZ);
        
        // If no world generator is set, generate flat terrain as fallback
        if (worldGenerator == null) {
            chunk.generateFlatTerrain(64);
            return chunk;
        }
        
        // Use WorldGenerator to fill terrain
        worldGenerator.generateChunkTerrain(chunk, worldLightManager);
        return chunk;
    }
    
    /**
     * Build mesh data for a chunk.
     * Runs on background thread.
     */
    private ChunkMeshData buildChunkMesh(LevelChunk chunk) {
        BlockFaceCollector collector = collectChunkFaces(chunk);
        return new ChunkMeshData(chunk.chunkX(), chunk.chunkZ(), collector);
    }
    
    /**
     * Build mesh buffer for a chunk (VBO/VAO format).
     * Runs on background thread.
     */
    private ChunkMeshBuffer buildChunkMeshBuffer(LevelChunk chunk) {
        // Null safety check for textureAtlas
        if (textureAtlas == null) {
            logger.warn("Cannot build mesh buffer: texture atlas not initialized for chunk ({}, {})", chunk.chunkX(), chunk.chunkZ());
            // Return an empty mesh buffer to gracefully handle the case
            return new ChunkMeshBuffer(chunk.chunkX(), chunk.chunkZ(), new float[0], new int[0]);
        }
        
        BlockFaceCollector collector = collectChunkFaces(chunk);
        MeshBuilder meshBuilder = new MeshBuilder(textureAtlas);
        
        // Set light accessor for cross-chunk light sampling if available
        if (lightAccessor != null) {
            meshBuilder.setLightAccessor(new VertexLightSampler.ChunkLightAccessor() {
                @Override
                public int getSkyLightAcrossChunks(LevelChunk chunk, int x, int y, int z) {
                    return lightAccessor.getSkyLight(chunk, x, y, z);
                }
                
                @Override
                public int getBlockLightAcrossChunks(LevelChunk chunk, int x, int y, int z) {
                    return lightAccessor.getBlockLight(chunk, x, y, z);
                }
                
                @Override
                public int[] getBlockLightRGBAcrossChunks(LevelChunk chunk, int x, int y, int z) {
                    // Get RGBI values from the light accessor
                    int r = lightAccessor.getBlockLightR(chunk, x, y, z);
                    int g = lightAccessor.getBlockLightG(chunk, x, y, z);
                    int b = lightAccessor.getBlockLightB(chunk, x, y, z);
                    int intensity = lightAccessor.getBlockLightI(chunk, x, y, z);
                    
                    // If no light, return early
                    if (intensity == 0) {
                        return new int[] {0, 0, 0};
                    }
                    
                    // Scale RGB by intensity ratio to properly attenuate light
                    int maxRGB = Math.max(r, Math.max(g, b));
                    
                    // If maxRGB is 0 but intensity is not, use intensity as white light
                    if (maxRGB == 0) {
                        return new int[] {intensity, intensity, intensity};
                    }
                    
                    // Scale RGB by the intensity ratio
                    float scale = (float) intensity / maxRGB;
                    int scaledR = Math.round(r * scale);
                    int scaledG = Math.round(g * scale);
                    int scaledB = Math.round(b * scale);
                    
                    return new int[] {scaledR, scaledG, scaledB};
                }
            });
        }
        
        return meshBuilder.build(chunk.chunkX(), chunk.chunkZ(), collector);
    }
    
    /**
     * Collect visible faces from a chunk.
     * Runs on background thread.
     */
    private BlockFaceCollector collectChunkFaces(LevelChunk chunk) {
        BlockFaceCollector collector = new BlockFaceCollector();
        
        // Set the neighbor accessor for cross-chunk face culling
        collector.setNeighborAccessor(neighborAccessor);
        
        // Iterate through all blocks and collect visible faces
        for (int sectionIndex = 0; sectionIndex < 24; sectionIndex++) {
            int sectionStartY = sectionIndex * 16;
            int sectionEndY = Math.min(sectionStartY + 16, LevelChunk.HEIGHT);
            
            // Quick check: is this section empty?
            if (ChunkUtils.isSectionEmpty(chunk, sectionStartY, sectionEndY)) {
                continue;
            }
            
            for (int x = 0; x < LevelChunk.WIDTH; x++) {
                for (int y = sectionStartY; y < sectionEndY; y++) {
                    for (int z = 0; z < LevelChunk.DEPTH; z++) {
                        Block block = chunk.getBlock(x, y, z);
                        if (block.isAir()) continue;
                        
                        float wx = x;
                        float wy = LevelChunk.chunkYToWorldY(y);
                        float wz = z;
                        
                        collector.collectBlockFaces(wx, wy, wz, block, chunk, x, y, z);
                    }
                }
            }
        }
        
        return collector;
    }
    
    /**
     * Request a mesh rebuild for a dirty chunk.
     * Call this when blocks in the chunk are modified.
     */
    public void requestChunkMeshRebuild(LevelChunk chunk) {
        if (textureAtlas == null) {
            logger.warn("Cannot rebuild mesh: texture atlas not set for chunk ({}, {})", chunk.chunkX(), chunk.chunkZ());
            return;
        }
        
        long key = ChunkUtils.chunkKey(chunk.chunkX(), chunk.chunkZ());
        
        // Cancel any existing mesh build task for this chunk
        Future<ChunkMeshBuffer> existingFuture = meshBufferFutures.remove(key);
        if (existingFuture != null && !existingFuture.isDone()) {
            existingFuture.cancel(false);
        }
        
        // Submit new mesh build task
        Future<ChunkMeshBuffer> meshBufferFuture = executor.submit(() -> buildChunkMeshBuffer(chunk));
        meshBufferFutures.put(key, meshBufferFuture);
        // logger.debug("Requested mesh rebuild for chunk ({}, {})", chunk.chunkX(), chunk.chunkZ());
    }
    
    public void shutdown() {
        executor.shutdown();
    }
    
    public int getPendingTaskCount() {
        return pendingTasks.size();
    }
    
    public int getActiveTaskCount() {
        return executor.getActiveTasks();
    }
    
    /**
     * Collect completed mesh buffers ready for GPU upload (VBO/VAO format).
     * Limited by budget to spread GPU uploads across frames.
     */
    public List<ChunkMeshBuffer> collectCompletedMeshBuffers() {
        // First, check for newly completed mesh buffers
        Iterator<Map.Entry<Long, Future<ChunkMeshBuffer>>> iterator = meshBufferFutures.entrySet().iterator();
        
        int completed = 0;
        while (iterator.hasNext()) {
            Map.Entry<Long, Future<ChunkMeshBuffer>> entry = iterator.next();
            Future<ChunkMeshBuffer> future = entry.getValue();
            
            if (future.isDone()) {
                try {
                    ChunkMeshBuffer meshBuffer = future.get();
                    completedMeshBuffers.offer(meshBuffer);
                    completed++;
                    // logger.debug("Mesh buffer completed for chunk, queued for upload. Queue size: {}", completedMeshBuffers.size());
                } catch (InterruptedException | ExecutionException e) {
                    logger.error("Failed to build mesh buffer: {}", e.getMessage(), e);
                }
                iterator.remove();
            }
        }
        
        if (completed > 0) {
            // logger.debug("Collected {} completed mesh buffers this frame", completed);
        }
        
        // Return up to budget limit for this frame
        // Pre-allocate with maximum possible size to avoid resizing
        List<ChunkMeshBuffer> result = new ArrayList<>(MAX_MESH_UPLOADS_PER_FRAME);
        for (int i = 0; i < MAX_MESH_UPLOADS_PER_FRAME && !completedMeshBuffers.isEmpty(); i++) {
            ChunkMeshBuffer meshBuffer = completedMeshBuffers.poll();
            if (meshBuffer != null) {
                result.add(meshBuffer);
            }
        }
        
        return result;
    }
}
