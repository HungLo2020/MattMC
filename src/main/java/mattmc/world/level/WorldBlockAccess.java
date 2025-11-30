package mattmc.world.level;

import mattmc.world.level.block.Block;
import mattmc.registries.Blocks;
import mattmc.world.level.block.state.BlockState;
import mattmc.world.level.chunk.ChunkManager;
import mattmc.world.level.chunk.LevelChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides unified block access across chunks.
 * Extracted from Level.java to improve modularity and maintainability.
 * 
 * Responsibilities:
 * - Convert world coordinates to chunk coordinates
 * - Get/set blocks at world coordinates
 * - Handle cross-chunk block queries
 * - Coordinate block state access
 */
public class WorldBlockAccess {
    private static final Logger logger = LoggerFactory.getLogger(WorldBlockAccess.class);
    
    private final ChunkManager chunkManager;
    
    /**
     * Create a WorldBlockAccess with a chunk manager.
     */
    public WorldBlockAccess(ChunkManager chunkManager) {
        this.chunkManager = chunkManager;
    }
    
    /**
     * Get a block at world coordinates.
     * @param worldX Level X coordinate (can be any value)
     * @param chunkY LevelChunk-local Y coordinate (0-383)
     * @param worldZ Level Z coordinate (can be any value)
     * @return The block at the specified position, or AIR if chunk not loaded
     */
    public Block getBlock(int worldX, int chunkY, int worldZ) {
        // Convert world coordinates to chunk coordinates
        int chunkX = Math.floorDiv(worldX, LevelChunk.WIDTH);
        int chunkZ = Math.floorDiv(worldZ, LevelChunk.DEPTH);
        
        // Get local coordinates within the chunk
        int localX = Math.floorMod(worldX, LevelChunk.WIDTH);
        int localZ = Math.floorMod(worldZ, LevelChunk.DEPTH);
        
        LevelChunk chunk = chunkManager.getChunk(chunkX, chunkZ);
        if (chunk == null) {
            return Blocks.AIR;
        }
        
        return chunk.getBlock(localX, chunkY, localZ);
    }
    
    /**
     * Get a blockstate at world coordinates.
     * @param worldX Level X coordinate
     * @param chunkY LevelChunk-local Y coordinate
     * @param worldZ Level Z coordinate
     * @return The blockstate at the specified position, or null if chunk not loaded
     */
    public BlockState getBlockState(int worldX, int chunkY, int worldZ) {
        // Convert world coordinates to chunk coordinates
        int chunkX = Math.floorDiv(worldX, LevelChunk.WIDTH);
        int chunkZ = Math.floorDiv(worldZ, LevelChunk.DEPTH);
        
        // Get local coordinates within the chunk
        int localX = Math.floorMod(worldX, LevelChunk.WIDTH);
        int localZ = Math.floorMod(worldZ, LevelChunk.DEPTH);
        
        LevelChunk chunk = chunkManager.getChunk(chunkX, chunkZ);
        if (chunk == null) {
            return null;
        }
        
        return chunk.getBlockState(localX, chunkY, localZ);
    }
    
    /**
     * Set a block at world coordinates.
     * @param worldX Level X coordinate
     * @param chunkY LevelChunk-local Y coordinate
     * @param worldZ Level Z coordinate
     * @param block The block to set
     * @param state The block state (can be null)
     * @param chunkGetter Callback to get or create a chunk at coordinates
     * @return The old block that was replaced
     */
    public Block setBlock(int worldX, int chunkY, int worldZ, Block block, BlockState state,
                          ChunkGetter chunkGetter) {
        // Convert world coordinates to chunk coordinates
        int chunkX = Math.floorDiv(worldX, LevelChunk.WIDTH);
        int chunkZ = Math.floorDiv(worldZ, LevelChunk.DEPTH);
        
        // Get local coordinates within the chunk
        int localX = Math.floorMod(worldX, LevelChunk.WIDTH);
        int localZ = Math.floorMod(worldZ, LevelChunk.DEPTH);
        
        LevelChunk chunk = chunkGetter.getChunk(chunkX, chunkZ);
        Block oldBlock = chunk.getBlock(localX, chunkY, localZ);
        chunk.setBlock(localX, chunkY, localZ, block, state);
        
        return oldBlock;
    }
    
    /**
     * Get a block at chunk-local coordinates, checking neighboring chunks if necessary.
     * Used for cross-chunk face culling.
     * 
     * @param chunk The base chunk
     * @param localX Local X coordinate (can be outside 0-15 range)
     * @param localY Local Y coordinate
     * @param localZ Local Z coordinate (can be outside 0-15 range)
     * @return The block at the specified position, or AIR if chunk not loaded
     */
    public Block getBlockAcrossChunks(LevelChunk chunk, int localX, int localY, int localZ) {
        // Validate chunk reference
        if (chunk == null) {
            return Blocks.AIR;
        }
        
        // Check Y bounds first
        if (localY < 0 || localY >= LevelChunk.HEIGHT) {
            return Blocks.AIR;
        }
        
        // Add sanity check for coordinates - they shouldn't be more than 2 chunks away
        // This prevents integer overflow issues and catches potential bugs
        if (Math.abs(localX) > LevelChunk.WIDTH * 2 || Math.abs(localZ) > LevelChunk.DEPTH * 2) {
            logger.warn("Suspicious coordinates in getBlockAcrossChunks: chunk({}, {}), local({}, {}, {})", 
                       chunk.chunkX(), chunk.chunkZ(), localX, localY, localZ);
            return Blocks.AIR;
        }
        
        // If within chunk bounds, use direct chunk access
        if (localX >= 0 && localX < LevelChunk.WIDTH && localZ >= 0 && localZ < LevelChunk.DEPTH) {
            return chunk.getBlock(localX, localY, localZ);
        }
        
        // Calculate which neighboring chunk to query
        int targetChunkX = chunk.chunkX();
        int targetChunkZ = chunk.chunkZ();
        int targetLocalX = localX;
        int targetLocalZ = localZ;
        
        // Adjust for X boundary crossing
        if (localX < 0) {
            targetChunkX--;
            targetLocalX = LevelChunk.WIDTH + localX; // localX is negative, so this adds
        } else if (localX >= LevelChunk.WIDTH) {
            targetChunkX++;
            targetLocalX = localX - LevelChunk.WIDTH;
        }
        
        // Adjust for Z boundary crossing
        if (localZ < 0) {
            targetChunkZ--;
            targetLocalZ = LevelChunk.DEPTH + localZ; // localZ is negative, so this adds
        } else if (localZ >= LevelChunk.DEPTH) {
            targetChunkZ++;
            targetLocalZ = localZ - LevelChunk.DEPTH;
        }
        
        // Get the neighboring chunk if it's loaded
        LevelChunk neighborChunk = chunkManager.getChunk(targetChunkX, targetChunkZ);
        if (neighborChunk == null) {
            // Neighboring chunk not loaded - assume air for now
            return Blocks.AIR;
        }
        
        return neighborChunk.getBlock(targetLocalX, localY, targetLocalZ);
    }
    
    /**
     * Mark adjacent chunks as dirty if the block is at a chunk boundary.
     * This is needed because adjacent chunks may have faces that need to be culled/unculled.
     * 
     * @param worldX World X coordinate
     * @param worldZ World Z coordinate
     */
    public void markAdjacentChunksDirtyIfOnBoundary(int worldX, int worldZ) {
        // Convert to chunk and local coordinates
        int chunkX = Math.floorDiv(worldX, LevelChunk.WIDTH);
        int chunkZ = Math.floorDiv(worldZ, LevelChunk.DEPTH);
        int localX = Math.floorMod(worldX, LevelChunk.WIDTH);
        int localZ = Math.floorMod(worldZ, LevelChunk.DEPTH);
        
        if (localX == 0) {
            // Block is at the western edge, mark western neighbor as dirty
            LevelChunk westChunk = chunkManager.getChunk(chunkX - 1, chunkZ);
            if (westChunk != null) {
                westChunk.setDirty(true);
            }
        } else if (localX == LevelChunk.WIDTH - 1) {
            // Block is at the eastern edge, mark eastern neighbor as dirty
            LevelChunk eastChunk = chunkManager.getChunk(chunkX + 1, chunkZ);
            if (eastChunk != null) {
                eastChunk.setDirty(true);
            }
        }
        
        if (localZ == 0) {
            // Block is at the northern edge, mark northern neighbor as dirty
            LevelChunk northChunk = chunkManager.getChunk(chunkX, chunkZ - 1);
            if (northChunk != null) {
                northChunk.setDirty(true);
            }
        } else if (localZ == LevelChunk.DEPTH - 1) {
            // Block is at the southern edge, mark southern neighbor as dirty
            LevelChunk southChunk = chunkManager.getChunk(chunkX, chunkZ + 1);
            if (southChunk != null) {
                southChunk.setDirty(true);
            }
        }
    }
    
    /**
     * Get skylight level at a position across chunk boundaries.
     * Similar to getBlockAcrossChunks but for skylight values.
     * 
     * @param chunk The current chunk
     * @param localX Chunk-local X coordinate (can be outside 0-15 range)
     * @param localY Chunk-local Y coordinate (0-383)
     * @param localZ Chunk-local Z coordinate (can be outside 0-15 range)
     * @return Skylight level (0-15), or 15 if chunk not loaded or out of bounds
     */
    public int getSkyLightAcrossChunks(LevelChunk chunk, int localX, int localY, int localZ) {
        // Validate chunk reference
        if (chunk == null) {
            return 15; // Default to full skylight
        }
        
        // Check Y bounds first
        if (localY < 0 || localY >= LevelChunk.HEIGHT) {
            return 15; // Out of world bounds: full skylight
        }
        
        // Sanity check for coordinates
        if (Math.abs(localX) > LevelChunk.WIDTH * 2 || Math.abs(localZ) > LevelChunk.DEPTH * 2) {
            logger.warn("Suspicious coordinates in getSkyLightAcrossChunks: chunk({}, {}), local({}, {}, {})", 
                       chunk.chunkX(), chunk.chunkZ(), localX, localY, localZ);
            return 15; // Default to full skylight
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
            // Neighboring chunk not loaded - assume full skylight
            return 15;
        }
        
        return neighborChunk.getSkyLight(targetLocalX, localY, targetLocalZ);
    }
    
    /**
     * Get blocklight level at a position across chunk boundaries.
     * Similar to getBlockAcrossChunks but for blocklight values.
     * 
     * @param chunk The current chunk
     * @param localX Chunk-local X coordinate (can be outside 0-15 range)
     * @param localY Chunk-local Y coordinate (0-383)
     * @param localZ Chunk-local Z coordinate (can be outside 0-15 range)
     * @return Blocklight level (0-15), or 0 if chunk not loaded or out of bounds
     */
    public int getBlockLightAcrossChunks(LevelChunk chunk, int localX, int localY, int localZ) {
        // Validate chunk reference
        if (chunk == null) {
            return 0; // Default to no blocklight
        }
        
        // Check Y bounds first
        if (localY < 0 || localY >= LevelChunk.HEIGHT) {
            return 0; // Out of world bounds: no blocklight
        }
        
        // Sanity check for coordinates
        if (Math.abs(localX) > LevelChunk.WIDTH * 2 || Math.abs(localZ) > LevelChunk.DEPTH * 2) {
            logger.warn("Suspicious coordinates in getBlockLightAcrossChunks: chunk({}, {}), local({}, {}, {})", 
                       chunk.chunkX(), chunk.chunkZ(), localX, localY, localZ);
            return 0; // Default to no blocklight
        }
        
        // If within chunk bounds, use direct chunk access
        if (localX >= 0 && localX < LevelChunk.WIDTH && localZ >= 0 && localZ < LevelChunk.DEPTH) {
            return chunk.getBlockLightI(localX, localY, localZ);
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
            // Neighboring chunk not loaded - assume no blocklight
            return 0;
        }
        
        return neighborChunk.getBlockLightI(targetLocalX, localY, targetLocalZ);
    }
    
    /**
     * Get block light R value across chunk boundaries.
     * Returns the red component of block light (0-15).
     */
    public int getBlockLightRAcrossChunks(LevelChunk chunk, int localX, int localY, int localZ) {
        // Validate chunk reference
        if (chunk == null) {
            return 0;
        }
        
        // Check Y bounds first
        if (localY < 0 || localY >= LevelChunk.HEIGHT) {
            return 0;
        }
        
        // If within chunk bounds, use direct chunk access
        if (localX >= 0 && localX < LevelChunk.WIDTH && localZ >= 0 && localZ < LevelChunk.DEPTH) {
            return chunk.getBlockLightR(localX, localY, localZ);
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
            return 0;
        }
        
        return neighborChunk.getBlockLightR(targetLocalX, localY, targetLocalZ);
    }
    
    /**
     * Get block light G value across chunk boundaries.
     * Returns the green component of block light (0-15).
     */
    public int getBlockLightGAcrossChunks(LevelChunk chunk, int localX, int localY, int localZ) {
        // Validate chunk reference
        if (chunk == null) {
            return 0;
        }
        
        // Check Y bounds first
        if (localY < 0 || localY >= LevelChunk.HEIGHT) {
            return 0;
        }
        
        // If within chunk bounds, use direct chunk access
        if (localX >= 0 && localX < LevelChunk.WIDTH && localZ >= 0 && localZ < LevelChunk.DEPTH) {
            return chunk.getBlockLightG(localX, localY, localZ);
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
            return 0;
        }
        
        return neighborChunk.getBlockLightG(targetLocalX, localY, targetLocalZ);
    }
    
    /**
     * Get block light B value across chunk boundaries.
     * Returns the blue component of block light (0-15).
     */
    public int getBlockLightBAcrossChunks(LevelChunk chunk, int localX, int localY, int localZ) {
        // Validate chunk reference
        if (chunk == null) {
            return 0;
        }
        
        // Check Y bounds first
        if (localY < 0 || localY >= LevelChunk.HEIGHT) {
            return 0;
        }
        
        // If within chunk bounds, use direct chunk access
        if (localX >= 0 && localX < LevelChunk.WIDTH && localZ >= 0 && localZ < LevelChunk.DEPTH) {
            return chunk.getBlockLightB(localX, localY, localZ);
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
            return 0;
        }
        
        return neighborChunk.getBlockLightB(targetLocalX, localY, targetLocalZ);
    }
    
    /**
     * Get block light I (intensity) value across chunk boundaries.
     * Returns the intensity component of block light (0-15).
     */
    public int getBlockLightIAcrossChunks(LevelChunk chunk, int localX, int localY, int localZ) {
        // Validate chunk reference
        if (chunk == null) {
            return 0;
        }
        
        // Check Y bounds first
        if (localY < 0 || localY >= LevelChunk.HEIGHT) {
            return 0;
        }
        
        // If within chunk bounds, use direct chunk access
        if (localX >= 0 && localX < LevelChunk.WIDTH && localZ >= 0 && localZ < LevelChunk.DEPTH) {
            return chunk.getBlockLightI(localX, localY, localZ);
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
            return 0;
        }
        
        return neighborChunk.getBlockLightI(targetLocalX, localY, targetLocalZ);
    }
    
    /**
     * Callback interface for getting or creating chunks.
     */
    public interface ChunkGetter {
        LevelChunk getChunk(int chunkX, int chunkZ);
    }
}
