package mattmc.world.level.chunk;

import mattmc.world.level.block.Block;
import mattmc.world.level.block.Blocks;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Manages light propagation in a 3D voxel grid using BFS (Breadth-First Search).
 * 
 * Light system:
 * - Light levels range from 0 (dark) to 15 (max brightness)
 * - Two types of light: skylight and block light (emitted by blocks like torches)
 * - Light propagates with attenuation: neighbor_light = max(0, current_light - 1)
 * - Skylight is initialized for topmost air blocks exposed to sky
 * - Block light is initialized for light-emitting blocks (e.g., torches)
 * 
 * BFS propagation algorithm:
 * 1. Initialize sources (skylight columns, light-emitting blocks)
 * 2. Add all sources to BFS queue
 * 3. Process queue: for each position, check 6 neighbors
 * 4. If neighbor_light < current_light - 1, update neighbor and add to queue
 * 5. Continue until queue is empty
 */
public class LightEngine {
    
    private static final int MAX_LIGHT_LEVEL = 15;
    private static final int MIN_LIGHT_LEVEL = 0;
    
    /**
     * Position in 3D space for BFS queue.
     */
    private static class LightNode {
        final int x, y, z;
        final int lightLevel;
        final boolean isSkyLight;
        
        LightNode(int x, int y, int z, int lightLevel, boolean isSkyLight) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.lightLevel = lightLevel;
            this.isSkyLight = isSkyLight;
        }
    }
    
    /**
     * Interface for accessing chunks during light propagation.
     */
    public interface ChunkAccess {
        LevelChunk getChunk(int chunkX, int chunkZ);
        Block getBlock(int worldX, int chunkY, int worldZ);
    }
    
    /**
     * Initialize and propagate all lights for a chunk.
     * This includes both skylight and block light.
     * 
     * @param chunk The chunk to initialize
     * @param chunkAccess Access to neighboring chunks
     */
    public static void initializeChunkLighting(LevelChunk chunk, ChunkAccess chunkAccess) {
        // Initialize light storage if needed
        chunk.ensureLightArraysExist();
        
        // Clear existing light
        chunk.clearAllLight();
        
        Queue<LightNode> queue = new ArrayDeque<>();
        
        // Step 1: Initialize skylight from top down
        initializeSkylight(chunk, queue);
        
        // Step 2: Initialize block-emitted lights
        initializeBlockLight(chunk, queue);
        
        // Step 3: BFS propagate all light
        propagateLight(chunk, chunkAccess, queue);
    }
    
    /**
     * Initialize skylight for columns exposed to sky.
     * Sets skylight to max (15) for topmost air blocks.
     */
    private static void initializeSkylight(LevelChunk chunk, Queue<LightNode> queue) {
        for (int x = 0; x < LevelChunk.WIDTH; x++) {
            for (int z = 0; z < LevelChunk.DEPTH; z++) {
                // Find topmost non-air block by scanning from top down
                int topSolidY = -1;
                for (int y = LevelChunk.HEIGHT - 1; y >= 0; y--) {
                    Block block = chunk.getBlock(x, y, z);
                    if (block.isOpaque()) {
                        topSolidY = y;
                        break;
                    }
                }
                
                // Skylight propagates down through air blocks from top
                // Start from above the topmost solid block (or from the top if no solid blocks)
                int startY = topSolidY >= 0 ? topSolidY + 1 : LevelChunk.HEIGHT - 1;
                
                // All air blocks from startY down to the first opaque block get skylight
                for (int y = startY; y >= 0; y--) {
                    Block block = chunk.getBlock(x, y, z);
                    
                    if (block.isAir()) {
                        // Set skylight to max and add to propagation queue
                        chunk.setSkyLight(x, y, z, MAX_LIGHT_LEVEL);
                        queue.add(new LightNode(x, y, z, MAX_LIGHT_LEVEL, true));
                    } else if (block.isOpaque()) {
                        // Opaque block stops skylight propagation down this column
                        break;
                    }
                }
            }
        }
    }
    
    /**
     * Initialize block light from light-emitting blocks.
     */
    private static void initializeBlockLight(LevelChunk chunk, Queue<LightNode> queue) {
        for (int x = 0; x < LevelChunk.WIDTH; x++) {
            for (int y = 0; y < LevelChunk.HEIGHT; y++) {
                for (int z = 0; z < LevelChunk.DEPTH; z++) {
                    Block block = chunk.getBlock(x, y, z);
                    int emission = block.getLightEmission();
                    
                    if (emission > 0) {
                        // Set block light to emission level and add to queue
                        chunk.setBlockLight(x, y, z, emission);
                        queue.add(new LightNode(x, y, z, emission, false));
                    }
                }
            }
        }
    }
    
    /**
     * Propagate light using BFS algorithm.
     * Light attenuates by 1 per block distance.
     */
    private static void propagateLight(LevelChunk chunk, ChunkAccess chunkAccess, Queue<LightNode> queue) {
        // 6 neighbor offsets (up, down, north, south, west, east)
        int[][] neighbors = {
            {0, 1, 0},   // up
            {0, -1, 0},  // down
            {0, 0, -1},  // north
            {0, 0, 1},   // south
            {-1, 0, 0},  // west
            {1, 0, 0}    // east
        };
        
        while (!queue.isEmpty()) {
            LightNode node = queue.poll();
            
            // Calculate attenuated light level for neighbors
            int neighborLight = node.lightLevel - 1;
            if (neighborLight <= 0) {
                continue; // Light too weak to propagate
            }
            
            // Check all 6 neighbors
            for (int[] offset : neighbors) {
                int nx = node.x + offset[0];
                int ny = node.y + offset[1];
                int nz = node.z + offset[2];
                
                // Get neighbor block (may be in different chunk)
                Block neighborBlock = getBlockAt(chunk, chunkAccess, nx, ny, nz);
                
                // Skip if neighbor is opaque (doesn't let light through)
                if (neighborBlock.isOpaque()) {
                    continue;
                }
                
                // Get current light level at neighbor
                int currentLight = getLightAt(chunk, chunkAccess, nx, ny, nz, node.isSkyLight);
                
                // Update if new light is brighter
                if (neighborLight > currentLight) {
                    setLightAt(chunk, chunkAccess, nx, ny, nz, neighborLight, node.isSkyLight);
                    queue.add(new LightNode(nx, ny, nz, neighborLight, node.isSkyLight));
                }
            }
        }
    }
    
    /**
     * Update light at a specific position when a block changes.
     * This is called when a block is placed or broken.
     */
    public static void updateLightAt(LevelChunk chunk, ChunkAccess chunkAccess, 
                                     int localX, int localY, int localZ) {
        // For now, we'll do a local re-initialization
        // A full implementation would do incremental updates (light removal + propagation)
        // but that's more complex and this simpler approach works for most cases
        
        Queue<LightNode> queue = new ArrayDeque<>();
        
        // Check if the block emits light
        Block block = chunk.getBlock(localX, localY, localZ);
        int emission = block.getLightEmission();
        
        // Clear light at this position
        chunk.setSkyLight(localX, localY, localZ, 0);
        chunk.setBlockLight(localX, localY, localZ, 0);
        
        // If block emits light, set it and propagate
        if (emission > 0) {
            chunk.setBlockLight(localX, localY, localZ, emission);
            queue.add(new LightNode(localX, localY, localZ, emission, false));
        }
        
        // Check if skylight should reach this position
        // This is a simplified check - a full implementation would trace from sky
        if (block.isAir()) {
            // Check if there's a light source above
            int lightAbove = getLightAt(chunk, chunkAccess, localX, localY + 1, localZ, true);
            if (lightAbove > 0) {
                int newSkyLight = lightAbove - 1;
                chunk.setSkyLight(localX, localY, localZ, newSkyLight);
                queue.add(new LightNode(localX, localY, localZ, newSkyLight, true));
            }
        }
        
        // Propagate the changes
        propagateLight(chunk, chunkAccess, queue);
    }
    
    /**
     * Get block at position (handles cross-chunk access).
     */
    private static Block getBlockAt(LevelChunk baseChunk, ChunkAccess chunkAccess, 
                                    int localX, int localY, int localZ) {
        // Check Y bounds
        if (localY < 0 || localY >= LevelChunk.HEIGHT) {
            return Blocks.AIR;
        }
        
        // If within base chunk, use direct access
        if (localX >= 0 && localX < LevelChunk.WIDTH && 
            localZ >= 0 && localZ < LevelChunk.DEPTH) {
            return baseChunk.getBlock(localX, localY, localZ);
        }
        
        // Calculate world coordinates
        int worldX = baseChunk.chunkX() * LevelChunk.WIDTH + localX;
        int worldZ = baseChunk.chunkZ() * LevelChunk.DEPTH + localZ;
        
        return chunkAccess.getBlock(worldX, localY, worldZ);
    }
    
    /**
     * Get light level at position (handles cross-chunk access).
     */
    private static int getLightAt(LevelChunk baseChunk, ChunkAccess chunkAccess, 
                                  int localX, int localY, int localZ, boolean isSkyLight) {
        // Check Y bounds
        if (localY < 0 || localY >= LevelChunk.HEIGHT) {
            return 0;
        }
        
        // If within base chunk, use direct access
        if (localX >= 0 && localX < LevelChunk.WIDTH && 
            localZ >= 0 && localZ < LevelChunk.DEPTH) {
            return isSkyLight ? baseChunk.getSkyLight(localX, localY, localZ)
                             : baseChunk.getBlockLight(localX, localY, localZ);
        }
        
        // Calculate chunk coordinates for neighbor
        int offsetX = localX < 0 ? -1 : (localX >= LevelChunk.WIDTH ? 1 : 0);
        int offsetZ = localZ < 0 ? -1 : (localZ >= LevelChunk.DEPTH ? 1 : 0);
        
        LevelChunk neighborChunk = chunkAccess.getChunk(
            baseChunk.chunkX() + offsetX,
            baseChunk.chunkZ() + offsetZ
        );
        
        if (neighborChunk == null) {
            return 0;
        }
        
        // Convert to neighbor's local coordinates
        int neighborLocalX = Math.floorMod(localX, LevelChunk.WIDTH);
        int neighborLocalZ = Math.floorMod(localZ, LevelChunk.DEPTH);
        
        return isSkyLight ? neighborChunk.getSkyLight(neighborLocalX, localY, neighborLocalZ)
                         : neighborChunk.getBlockLight(neighborLocalX, localY, neighborLocalZ);
    }
    
    /**
     * Set light level at position (handles cross-chunk access).
     */
    private static void setLightAt(LevelChunk baseChunk, ChunkAccess chunkAccess, 
                                   int localX, int localY, int localZ, 
                                   int lightLevel, boolean isSkyLight) {
        // Check Y bounds
        if (localY < 0 || localY >= LevelChunk.HEIGHT) {
            return;
        }
        
        // If within base chunk, use direct access
        if (localX >= 0 && localX < LevelChunk.WIDTH && 
            localZ >= 0 && localZ < LevelChunk.DEPTH) {
            if (isSkyLight) {
                baseChunk.setSkyLight(localX, localY, localZ, lightLevel);
            } else {
                baseChunk.setBlockLight(localX, localY, localZ, lightLevel);
            }
            return;
        }
        
        // Calculate chunk coordinates for neighbor
        int offsetX = localX < 0 ? -1 : (localX >= LevelChunk.WIDTH ? 1 : 0);
        int offsetZ = localZ < 0 ? -1 : (localZ >= LevelChunk.DEPTH ? 1 : 0);
        
        LevelChunk neighborChunk = chunkAccess.getChunk(
            baseChunk.chunkX() + offsetX,
            baseChunk.chunkZ() + offsetZ
        );
        
        if (neighborChunk == null) {
            return;
        }
        
        // Convert to neighbor's local coordinates
        int neighborLocalX = Math.floorMod(localX, LevelChunk.WIDTH);
        int neighborLocalZ = Math.floorMod(localZ, LevelChunk.DEPTH);
        
        if (isSkyLight) {
            neighborChunk.setSkyLight(neighborLocalX, localY, neighborLocalZ, lightLevel);
        } else {
            neighborChunk.setBlockLight(neighborLocalX, localY, neighborLocalZ, lightLevel);
        }
    }
}
