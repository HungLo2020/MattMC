package mattmc.world.level.chunk;

import mattmc.client.renderer.chunk.ChunkMeshData;
import mattmc.client.renderer.block.BlockFaceCollector;
import mattmc.world.level.block.Block;

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
    private final ChunkTaskExecutor executor;
    private final PriorityBlockingQueue<ChunkLoadTask> pendingTasks;
    private final Set<Long> tasksInProgress;
    private final Map<Long, Future<LevelChunk>> chunkFutures;
    private final Map<Long, Future<ChunkMeshData>> meshFutures;
    private final Queue<ChunkMeshData> completedMeshes;
    
    // Budget: maximum operations per frame
    private static final int MAX_CHUNK_LOADS_PER_FRAME = 4;
    private static final int MAX_MESH_UPLOADS_PER_FRAME = 2;
    
    private Path worldDirectory;
    
    public AsyncChunkLoader() {
        this.executor = new ChunkTaskExecutor();
        this.pendingTasks = new PriorityBlockingQueue<>();
        this.tasksInProgress = Collections.synchronizedSet(new HashSet<>());
        this.chunkFutures = new ConcurrentHashMap<>();
        this.meshFutures = new ConcurrentHashMap<>();
        this.completedMeshes = new ConcurrentLinkedQueue<>();
    }
    
    public void setWorldDirectory(Path worldDirectory) {
        this.worldDirectory = worldDirectory;
    }
    
    /**
     * Request a chunk to be loaded/generated.
     * Returns immediately, chunk will be loaded in the background.
     */
    public void requestChunk(int chunkX, int chunkZ, double playerX, double playerZ) {
        long key = chunkKey(chunkX, chunkZ);
        
        // Skip if already loading or loaded
        synchronized (tasksInProgress) {
            if (tasksInProgress.contains(key) || chunkFutures.containsKey(key)) {
                return;
            }
            tasksInProgress.add(key);
        }
        
        // Calculate priority (distance from player)
        double dx = (chunkX * LevelChunk.WIDTH + 8) - playerX;
        double dz = (chunkZ * LevelChunk.DEPTH + 8) - playerZ;
        double distanceSquared = dx * dx + dz * dz;
        
        // Add to priority queue
        ChunkLoadTask task = new ChunkLoadTask(chunkX, chunkZ, distanceSquared, 
                                               ChunkLoadTask.TaskType.GENERATION);
        pendingTasks.offer(task);
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
            
            long key = chunkKey(task.getChunkX(), task.getChunkZ());
            
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
                try {
                    LevelChunk chunk = future.get();
                    completed.add(chunk);
                    
                    // Start mesh building for this chunk
                    long key = entry.getKey();
                    Future<ChunkMeshData> meshFuture = executor.submit(() -> buildChunkMesh(chunk));
                    meshFutures.put(key, meshFuture);
                    
                } catch (InterruptedException | ExecutionException e) {
                    System.err.println("Failed to load chunk: " + e.getMessage());
                }
                
                iterator.remove();
                long key = entry.getKey();
                synchronized (tasksInProgress) {
                    tasksInProgress.remove(key);
                }
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
                    System.err.println("Failed to build mesh: " + e.getMessage());
                }
                iterator.remove();
            }
        }
        
        // Return up to budget limit for this frame
        List<ChunkMeshData> result = new ArrayList<>();
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
                    return ChunkNBT.fromNBT(chunkNBT);
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to load chunk (" + chunkX + ", " + chunkZ + ") from disk: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Generate a new chunk.
     * Runs on background thread.
     */
    private LevelChunk generateChunk(int chunkX, int chunkZ) {
        LevelChunk chunk = new LevelChunk(chunkX, chunkZ);
        chunk.generateFlatTerrain(64);
        return chunk;
    }
    
    /**
     * Build mesh data for a chunk.
     * Runs on background thread.
     */
    private ChunkMeshData buildChunkMesh(LevelChunk chunk) {
        BlockFaceCollector collector = new BlockFaceCollector();
        
        // Iterate through all blocks and collect visible faces
        for (int sectionIndex = 0; sectionIndex < 24; sectionIndex++) {
            int sectionStartY = sectionIndex * 16;
            int sectionEndY = Math.min(sectionStartY + 16, LevelChunk.HEIGHT);
            
            // Quick check: is this section empty?
            if (isSectionEmpty(chunk, sectionStartY, sectionEndY)) {
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
        
        return new ChunkMeshData(chunk.chunkX(), chunk.chunkZ(), collector);
    }
    
    /**
     * Check if a section is empty.
     */
    private boolean isSectionEmpty(LevelChunk chunk, int startY, int endY) {
        int midY = (startY + endY) / 2;
        
        if (!chunk.getBlock(0, startY, 0).isAir()) return false;
        if (!chunk.getBlock(15, startY, 15).isAir()) return false;
        if (!chunk.getBlock(0, midY, 0).isAir()) return false;
        if (!chunk.getBlock(15, midY, 15).isAir()) return false;
        if (!chunk.getBlock(0, endY - 1, 0).isAir()) return false;
        if (!chunk.getBlock(15, endY - 1, 15).isAir()) return false;
        if (!chunk.getBlock(8, midY, 8).isAir()) return false;
        
        return true;
    }
    
    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long)chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
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
}
