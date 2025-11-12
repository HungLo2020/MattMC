package mattmc.world.level.chunk;

import mattmc.world.level.Level;
import mattmc.world.level.block.Block;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Minecraft-style lighting engine that handles light propagation.
 * 
 * Implements two types of lighting:
 * - Sky Light: Natural light from the sky (0-15), affected by day/night cycle
 * - Block Light: Light from torches, lava, etc. (0-15)
 * 
 * Light propagates from light sources, decreasing by 1 per block traveled.
 * Uses flood-fill algorithm for efficient light updates.
 */
public class LightEngine {
    
    /**
     * Position in 3D space for light propagation queue.
     */
    private static class LightNode {
        final int x, y, z;
        final int lightLevel;
        
        LightNode(int x, int y, int z, int lightLevel) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.lightLevel = lightLevel;
        }
    }
    
    /**
     * Calculate initial sky light for a chunk.
     * Sky light starts at 15 at the top and propagates downward.
     * Blocks that are not transparent reduce sky light.
     * 
     * @param chunk The chunk to calculate sky light for
     */
    public static void calculateSkyLight(LevelChunk chunk) {
        // For each column (x, z), propagate sky light from top down
        for (int x = 0; x < LevelChunk.WIDTH; x++) {
            for (int z = 0; z < LevelChunk.DEPTH; z++) {
                int currentLight = 15; // Sky starts at full brightness
                
                // Propagate from top to bottom
                for (int y = LevelChunk.HEIGHT - 1; y >= 0; y--) {
                    Block block = chunk.getBlock(x, y, z);
                    
                    if (block.isAir()) {
                        // Air lets light through
                        chunk.setSkyLight(x, y, z, currentLight);
                    } else if (!block.isSolid()) {
                        // Transparent blocks like glass reduce light slightly
                        chunk.setSkyLight(x, y, z, currentLight);
                        currentLight = Math.max(0, currentLight - 1);
                    } else {
                        // Solid blocks block sky light completely
                        chunk.setSkyLight(x, y, z, 0);
                        currentLight = 0;
                    }
                }
            }
        }
    }
    
    /**
     * Calculate block light for a chunk.
     * Block light comes from light-emitting blocks like torches.
     * 
     * @param chunk The chunk to calculate block light for
     */
    public static void calculateBlockLight(LevelChunk chunk) {
        // Reset all block light to 0
        for (int x = 0; x < LevelChunk.WIDTH; x++) {
            for (int y = 0; y < LevelChunk.HEIGHT; y++) {
                for (int z = 0; z < LevelChunk.DEPTH; z++) {
                    chunk.setBlockLight(x, y, z, 0);
                }
            }
        }
        
        // Find all light sources and propagate light from them
        Queue<LightNode> lightQueue = new ArrayDeque<>();
        
        for (int x = 0; x < LevelChunk.WIDTH; x++) {
            for (int y = 0; y < LevelChunk.HEIGHT; y++) {
                for (int z = 0; z < LevelChunk.DEPTH; z++) {
                    Block block = chunk.getBlock(x, y, z);
                    int emission = block.getLightEmission();
                    
                    if (emission > 0) {
                        chunk.setBlockLight(x, y, z, emission);
                        lightQueue.add(new LightNode(x, y, z, emission));
                    }
                }
            }
        }
        
        // Propagate light using flood-fill
        propagateBlockLight(chunk, lightQueue);
    }
    
    /**
     * Propagate block light from light sources using flood-fill algorithm.
     * Light decreases by 1 for each block traveled.
     * 
     * @param chunk The chunk to propagate light in
     * @param lightQueue Queue of light sources to propagate from
     */
    private static void propagateBlockLight(LevelChunk chunk, Queue<LightNode> lightQueue) {
        int[][] directions = {
            {1, 0, 0}, {-1, 0, 0},  // X axis
            {0, 1, 0}, {0, -1, 0},  // Y axis
            {0, 0, 1}, {0, 0, -1}   // Z axis
        };
        
        while (!lightQueue.isEmpty()) {
            LightNode node = lightQueue.poll();
            
            // Propagate to all 6 neighbors
            for (int[] dir : directions) {
                int nx = node.x + dir[0];
                int ny = node.y + dir[1];
                int nz = node.z + dir[2];
                
                // Check bounds
                if (nx < 0 || nx >= LevelChunk.WIDTH || 
                    ny < 0 || ny >= LevelChunk.HEIGHT || 
                    nz < 0 || nz >= LevelChunk.DEPTH) {
                    continue;
                }
                
                // Calculate new light level (decrease by 1)
                int newLight = node.lightLevel - 1;
                if (newLight <= 0) {
                    continue;
                }
                
                // Only update if this would increase the light level
                int currentLight = chunk.getBlockLight(nx, ny, nz);
                if (newLight > currentLight) {
                    chunk.setBlockLight(nx, ny, nz, newLight);
                    lightQueue.add(new LightNode(nx, ny, nz, newLight));
                }
            }
        }
    }
    
    /**
     * Update lighting for a chunk (both sky and block light).
     * 
     * @param chunk The chunk to update lighting for
     */
    public static void updateLighting(LevelChunk chunk) {
        calculateSkyLight(chunk);
        calculateBlockLight(chunk);
    }
    
    /**
     * Update lighting when a block is placed or removed.
     * This is more efficient than recalculating the entire chunk.
     * 
     * @param level The level containing the block
     * @param worldX World X coordinate
     * @param worldY World Y coordinate
     * @param worldZ World Z coordinate
     */
    public static void updateLightingAt(Level level, int worldX, int worldY, int worldZ) {
        // Get the chunk containing this position
        int chunkX = Math.floorDiv(worldX, LevelChunk.WIDTH);
        int chunkZ = Math.floorDiv(worldZ, LevelChunk.DEPTH);
        
        LevelChunk chunk = level.getChunk(chunkX, chunkZ);
        if (chunk == null) {
            return;
        }
        
        // Convert to chunk-local coordinates
        int localX = worldX - (chunkX * LevelChunk.WIDTH);
        int localZ = worldZ - (chunkZ * LevelChunk.DEPTH);
        int localY = LevelChunk.worldYToChunkY(worldY);
        
        // For now, just recalculate the entire chunk
        // TODO: Implement more efficient localized light updates
        updateLighting(chunk);
        
        // Mark chunk as dirty for re-rendering
        chunk.setDirty(true);
    }
}
