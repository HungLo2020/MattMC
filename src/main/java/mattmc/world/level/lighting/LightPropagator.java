package mattmc.world.level.lighting;

import mattmc.world.level.Level;
import mattmc.world.level.block.Block;
import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;

/**
 * Incremental BFS light propagation system for both block light and skylight.
 * 
 * Uses integer ring buffers for performance and processes updates in fixed time budgets
 * to avoid stalling the game. Handles cross-chunk propagation by accessing neighboring chunks.
 * 
 * Algorithm:
 * - Block light: Attenuates by 1 per step, stops at opacity >= 1
 * - Skylight: Downward casts keep 15 until blocked, lateral spread attenuates
 */
public class LightPropagator {
    
    // Ring buffer for block light additions (world coordinates + light level)
    private static class LightNode {
        int x, y, z, level;
        
        LightNode(int x, int y, int z, int level) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.level = level;
        }
    }
    
    // Pre-allocated ring buffers for performance
    private static final int BUFFER_SIZE = 8192;
    private final LightNode[] blockLightAddQueue = new LightNode[BUFFER_SIZE];
    private final LightNode[] blockLightRemoveQueue = new LightNode[BUFFER_SIZE];
    private final LightNode[] skyLightAddQueue = new LightNode[BUFFER_SIZE];
    private final LightNode[] skyLightRemoveQueue = new LightNode[BUFFER_SIZE];
    
    private int blockAddHead = 0, blockAddTail = 0;
    private int blockRemoveHead = 0, blockRemoveTail = 0;
    private int skyAddHead = 0, skyAddTail = 0;
    private int skyRemoveHead = 0, skyRemoveTail = 0;
    
    private final Level level;
    
    // Directions for neighbor checking (6 directions: +X, -X, +Y, -Y, +Z, -Z)
    private static final int[][] DIRECTIONS = {
        {1, 0, 0}, {-1, 0, 0},  // East, West
        {0, 1, 0}, {0, -1, 0},  // Up, Down
        {0, 0, 1}, {0, 0, -1}   // South, North
    };
    
    public LightPropagator(Level level) {
        this.level = level;
        // Pre-allocate all nodes to avoid GC during updates
        for (int i = 0; i < BUFFER_SIZE; i++) {
            blockLightAddQueue[i] = new LightNode(0, 0, 0, 0);
            blockLightRemoveQueue[i] = new LightNode(0, 0, 0, 0);
            skyLightAddQueue[i] = new LightNode(0, 0, 0, 0);
            skyLightRemoveQueue[i] = new LightNode(0, 0, 0, 0);
        }
    }
    
    /**
     * Enqueue a block light addition (e.g., torch placement).
     * 
     * @param worldX World X coordinate
     * @param chunkY Chunk-local Y coordinate (0-383)
     * @param worldZ World Z coordinate
     * @param level Light level to set (0-15)
     */
    public void enqueueAddBlock(int worldX, int chunkY, int worldZ, int level) {
        if (level <= 0 || level > 15) return;
        
        int nextTail = (blockAddTail + 1) % BUFFER_SIZE;
        if (nextTail == blockAddHead) {
            // Buffer full, skip this update (will be recalculated when needed)
            return;
        }
        
        LightNode node = blockLightAddQueue[blockAddTail];
        node.x = worldX;
        node.y = chunkY;
        node.z = worldZ;
        node.level = level;
        blockAddTail = nextTail;
    }
    
    /**
     * Enqueue a block light removal (e.g., torch removal).
     * 
     * @param worldX World X coordinate
     * @param chunkY Chunk-local Y coordinate (0-383)
     * @param worldZ World Z coordinate
     */
    public void enqueueRemoveBlock(int worldX, int chunkY, int worldZ) {
        int nextTail = (blockRemoveTail + 1) % BUFFER_SIZE;
        if (nextTail == blockRemoveHead) {
            // Buffer full, skip this update
            return;
        }
        
        LightNode node = blockLightRemoveQueue[blockRemoveTail];
        node.x = worldX;
        node.y = chunkY;
        node.z = worldZ;
        node.level = getBlockLight(worldX, chunkY, worldZ);
        blockRemoveTail = nextTail;
    }
    
    /**
     * Enqueue skylight from surface (e.g., when digging hole to sky).
     * 
     * @param worldX World X coordinate
     * @param chunkY Chunk-local Y coordinate (0-383)
     * @param worldZ World Z coordinate
     */
    public void enqueueSkylightFromSurface(int worldX, int chunkY, int worldZ) {
        int nextTail = (skyAddTail + 1) % BUFFER_SIZE;
        if (nextTail == skyAddHead) {
            // Buffer full, skip this update
            return;
        }
        
        LightNode node = skyLightAddQueue[skyAddTail];
        node.x = worldX;
        node.y = chunkY;
        node.z = worldZ;
        node.level = 15;  // Full skylight
        skyAddTail = nextTail;
    }
    
    /**
     * Enqueue skylight removal (e.g., when placing roof).
     * 
     * @param worldX World X coordinate
     * @param chunkY Chunk-local Y coordinate (0-383)
     * @param worldZ World Z coordinate
     */
    public void enqueueRemoveSkylight(int worldX, int chunkY, int worldZ) {
        int nextTail = (skyRemoveTail + 1) % BUFFER_SIZE;
        if (nextTail == skyRemoveHead) {
            // Buffer full, skip this update
            return;
        }
        
        LightNode node = skyLightRemoveQueue[skyRemoveTail];
        node.x = worldX;
        node.y = chunkY;
        node.z = worldZ;
        node.level = getSkyLight(worldX, chunkY, worldZ);
        skyRemoveTail = nextTail;
    }
    
    /**
     * Process light updates with a time budget.
     * Processes a few thousand nodes per frame to avoid stalling the game.
     * 
     * @param msBudget Time budget in milliseconds
     */
    public void updateBudget(double msBudget) {
        long startTime = System.nanoTime();
        double budgetNanos = msBudget * 1_000_000;
        
        // Process removals first (to clear out old light)
        while (blockRemoveHead != blockRemoveTail) {
            if (System.nanoTime() - startTime > budgetNanos) return;
            processBlockLightRemoval();
        }
        
        while (skyRemoveHead != skyRemoveTail) {
            if (System.nanoTime() - startTime > budgetNanos) return;
            processSkyLightRemoval();
        }
        
        // Then process additions
        while (blockAddHead != blockAddTail) {
            if (System.nanoTime() - startTime > budgetNanos) return;
            processBlockLightAddition();
        }
        
        while (skyAddHead != skyAddTail) {
            if (System.nanoTime() - startTime > budgetNanos) return;
            processSkyLightAddition();
        }
    }
    
    /**
     * Process a single block light addition from the queue.
     */
    private void processBlockLightAddition() {
        LightNode node = blockLightAddQueue[blockAddHead];
        blockAddHead = (blockAddHead + 1) % BUFFER_SIZE;
        
        int currentLight = getBlockLight(node.x, node.y, node.z);
        if (node.level <= currentLight) return;
        
        setBlockLight(node.x, node.y, node.z, node.level);
        markChunkDirty(node.x, node.y, node.z);
        
        // Propagate to neighbors
        for (int[] dir : DIRECTIONS) {
            int nx = node.x + dir[0];
            int ny = node.y + dir[1];
            int nz = node.z + dir[2];
            
            if (ny < 0 || ny >= LevelChunk.HEIGHT) continue;
            
            Block block = getBlock(nx, ny, nz);
            if (block.isOpaque()) continue;
            
            int newLevel = node.level - 1;
            int neighborLight = getBlockLight(nx, ny, nz);
            if (newLevel > 0 && newLevel > neighborLight) {
                enqueueAddBlock(nx, ny, nz, newLevel);
            }
        }
    }
    
    /**
     * Process a single block light removal from the queue.
     * Uses flood-fill to remove light, then re-propagates from light sources.
     */
    private void processBlockLightRemoval() {
        LightNode node = blockLightRemoveQueue[blockRemoveHead];
        blockRemoveHead = (blockRemoveHead + 1) % BUFFER_SIZE;
        
        int currentLight = getBlockLight(node.x, node.y, node.z);
        setBlockLight(node.x, node.y, node.z, 0);
        markChunkDirty(node.x, node.y, node.z);
        
        // Propagate removal to neighbors
        for (int[] dir : DIRECTIONS) {
            int nx = node.x + dir[0];
            int ny = node.y + dir[1];
            int nz = node.z + dir[2];
            
            if (ny < 0 || ny >= LevelChunk.HEIGHT) continue;
            
            int neighborLight = getBlockLight(nx, ny, nz);
            Block block = getBlock(nx, ny, nz);
            
            if (neighborLight > 0) {
                if (neighborLight < node.level) {
                    // This neighbor was lit by the removed light
                    enqueueRemoveBlock(nx, ny, nz);
                } else if (block.getLightEmission() > 0) {
                    // This neighbor is a light source, re-propagate
                    enqueueAddBlock(nx, ny, nz, block.getLightEmission());
                }
            }
        }
    }
    
    /**
     * Process a single skylight addition from the queue.
     */
    private void processSkyLightAddition() {
        LightNode node = skyLightAddQueue[skyAddHead];
        skyAddHead = (skyAddHead + 1) % BUFFER_SIZE;
        
        int currentLight = getSkyLight(node.x, node.y, node.z);
        if (node.level <= currentLight) return;
        
        setSkyLight(node.x, node.y, node.z, node.level);
        markChunkDirty(node.x, node.y, node.z);
        
        // Propagate to neighbors
        for (int[] dir : DIRECTIONS) {
            int nx = node.x + dir[0];
            int ny = node.y + dir[1];
            int nz = node.z + dir[2];
            
            if (ny < 0 || ny >= LevelChunk.HEIGHT) continue;
            
            Block block = getBlock(nx, ny, nz);
            if (block.isOpaque()) continue;
            
            int newLevel;
            if (dir[1] == -1) {
                // Downward propagation keeps full brightness
                newLevel = node.level;
            } else {
                // Lateral propagation attenuates
                newLevel = node.level - 1;
            }
            
            if (newLevel > 0 && newLevel > getSkyLight(nx, ny, nz)) {
                enqueueSkylightFromSurface(nx, ny, nz);
            }
        }
    }
    
    /**
     * Process a single skylight removal from the queue.
     */
    private void processSkyLightRemoval() {
        LightNode node = skyLightRemoveQueue[skyRemoveHead];
        skyRemoveHead = (skyRemoveHead + 1) % BUFFER_SIZE;
        
        setSkyLight(node.x, node.y, node.z, 0);
        markChunkDirty(node.x, node.y, node.z);
        
        // Propagate removal to neighbors
        for (int[] dir : DIRECTIONS) {
            int nx = node.x + dir[0];
            int ny = node.y + dir[1];
            int nz = node.z + dir[2];
            
            if (ny < 0 || ny >= LevelChunk.HEIGHT) continue;
            
            int neighborLight = getSkyLight(nx, ny, nz);
            
            if (neighborLight > 0) {
                if (neighborLight < node.level || (dir[1] == -1 && neighborLight == node.level)) {
                    // This neighbor was lit by the removed light
                    enqueueRemoveSkylight(nx, ny, nz);
                } else {
                    // Re-propagate from this neighbor
                    enqueueSkylightFromSurface(nx, ny, nz);
                }
            }
        }
    }
    
    // Helper methods to access level data
    
    private int getBlockLight(int worldX, int chunkY, int worldZ) {
        int chunkX = Math.floorDiv(worldX, LevelChunk.WIDTH);
        int chunkZ = Math.floorDiv(worldZ, LevelChunk.DEPTH);
        LevelChunk chunk = level.getChunkIfLoaded(chunkX, chunkZ);
        if (chunk == null) return 0;
        
        int localX = Math.floorMod(worldX, LevelChunk.WIDTH);
        int localZ = Math.floorMod(worldZ, LevelChunk.DEPTH);
        return chunk.getBlockLight(localX, chunkY, localZ);
    }
    
    private void setBlockLight(int worldX, int chunkY, int worldZ, int lightLevel) {
        int chunkX = Math.floorDiv(worldX, LevelChunk.WIDTH);
        int chunkZ = Math.floorDiv(worldZ, LevelChunk.DEPTH);
        LevelChunk chunk = level.getChunkIfLoaded(chunkX, chunkZ);
        if (chunk == null) return;
        
        int localX = Math.floorMod(worldX, LevelChunk.WIDTH);
        int localZ = Math.floorMod(worldZ, LevelChunk.DEPTH);
        chunk.setBlockLight(localX, chunkY, localZ, lightLevel);
    }
    
    private int getSkyLight(int worldX, int chunkY, int worldZ) {
        int chunkX = Math.floorDiv(worldX, LevelChunk.WIDTH);
        int chunkZ = Math.floorDiv(worldZ, LevelChunk.DEPTH);
        LevelChunk chunk = level.getChunkIfLoaded(chunkX, chunkZ);
        if (chunk == null) return 0;
        
        int localX = Math.floorMod(worldX, LevelChunk.WIDTH);
        int localZ = Math.floorMod(worldZ, LevelChunk.DEPTH);
        return chunk.getSkyLight(localX, chunkY, localZ);
    }
    
    private void setSkyLight(int worldX, int chunkY, int worldZ, int lightLevel) {
        int chunkX = Math.floorDiv(worldX, LevelChunk.WIDTH);
        int chunkZ = Math.floorDiv(worldZ, LevelChunk.DEPTH);
        LevelChunk chunk = level.getChunkIfLoaded(chunkX, chunkZ);
        if (chunk == null) return;
        
        int localX = Math.floorMod(worldX, LevelChunk.WIDTH);
        int localZ = Math.floorMod(worldZ, LevelChunk.DEPTH);
        chunk.setSkyLight(localX, chunkY, localZ, lightLevel);
    }
    
    private Block getBlock(int worldX, int chunkY, int worldZ) {
        Block block = level.getBlock(worldX, chunkY, worldZ);
        return block != null ? block : Blocks.AIR;
    }
    
    private void markChunkDirty(int worldX, int chunkY, int worldZ) {
        int chunkX = Math.floorDiv(worldX, LevelChunk.WIDTH);
        int chunkZ = Math.floorDiv(worldZ, LevelChunk.DEPTH);
        LevelChunk chunk = level.getChunkIfLoaded(chunkX, chunkZ);
        if (chunk != null) {
            chunk.setDirty(true);
        }
    }
    
    /**
     * Check if there are pending light updates.
     * 
     * @return true if there are queued updates
     */
    public boolean hasPendingUpdates() {
        return blockAddHead != blockAddTail || 
               blockRemoveHead != blockRemoveTail ||
               skyAddHead != skyAddTail ||
               skyRemoveHead != skyRemoveTail;
    }
    
    /**
     * Get the total number of pending light updates across all queues.
     * 
     * @return Total number of pending updates
     */
    public int getPendingUpdateCount() {
        int blockAddCount = (blockAddTail - blockAddHead + BUFFER_SIZE) % BUFFER_SIZE;
        int blockRemoveCount = (blockRemoveTail - blockRemoveHead + BUFFER_SIZE) % BUFFER_SIZE;
        int skyAddCount = (skyAddTail - skyAddHead + BUFFER_SIZE) % BUFFER_SIZE;
        int skyRemoveCount = (skyRemoveTail - skyRemoveHead + BUFFER_SIZE) % BUFFER_SIZE;
        return blockAddCount + blockRemoveCount + skyAddCount + skyRemoveCount;
    }
}
