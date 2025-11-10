package mattmc.world.level.access;

import mattmc.world.level.block.Block;
import mattmc.world.level.block.state.BlockState;
import mattmc.world.level.chunk.LevelChunk;
import mattmc.world.level.chunk.management.ChunkManager;

/**
 * Provides unified block access across chunks.
 */
public class WorldBlockAccess {
    private final ChunkManager chunkManager;
    
    public WorldBlockAccess(ChunkManager chunkManager) {
        this.chunkManager = chunkManager;
    }
    
    /**
     * Get a block at world coordinates.
     * @param worldX World X coordinate
     * @param worldY World Y coordinate (chunk-relative, 0-383)
     * @param worldZ World Z coordinate
     * @return The block, or null if chunk not loaded or coordinates invalid
     */
    public Block getBlock(int worldX, int worldY, int worldZ) {
        int chunkX = worldX >> 4;
        int chunkZ = worldZ >> 4;
        
        LevelChunk chunk = chunkManager.getChunk(chunkX, chunkZ);
        if (chunk == null) {
            return null;
        }
        
        int localX = worldX & 15;
        int localZ = worldZ & 15;
        
        if (worldY < 0 || worldY >= 384) {
            return null;
        }
        
        return chunk.getBlock(localX, worldY, localZ);
    }
    
    /**
     * Get a block state at world coordinates.
     */
    public BlockState getBlockState(int worldX, int worldY, int worldZ) {
        int chunkX = worldX >> 4;
        int chunkZ = worldZ >> 4;
        
        LevelChunk chunk = chunkManager.getChunk(chunkX, chunkZ);
        if (chunk == null) {
            return null;
        }
        
        int localX = worldX & 15;
        int localZ = worldZ & 15;
        
        if (worldY < 0 || worldY >= 384) {
            return null;
        }
        
        return chunk.getBlockState(localX, worldY, localZ);
    }
    
    /**
     * Set a block at world coordinates.
     */
    public void setBlock(int worldX, int worldY, int worldZ, Block block) {
        setBlock(worldX, worldY, worldZ, block, null);
    }
    
    /**
     * Set a block with state at world coordinates.
     */
    public void setBlock(int worldX, int worldY, int worldZ, Block block, BlockState state) {
        int chunkX = worldX >> 4;
        int chunkZ = worldZ >> 4;
        
        LevelChunk chunk = chunkManager.getChunk(chunkX, chunkZ);
        if (chunk == null) {
            return;
        }
        
        int localX = worldX & 15;
        int localZ = worldZ & 15;
        
        if (worldY < 0 || worldY >= 384) {
            return;
        }
        
        if (state != null) {
            chunk.setBlock(localX, worldY, localZ, block, state);
        } else {
            chunk.setBlock(localX, worldY, localZ, block);
        }
    }
    
    /**
     * Get a block from an adjacent chunk.
     * Used for face visibility checks across chunk boundaries.
     */
    public Block getBlockAcrossChunks(LevelChunk chunk, int localX, int localY, int localZ) {
        // If coordinates are within this chunk, use it directly
        if (localX >= 0 && localX < 16 && localZ >= 0 && localZ < 16) {
            return chunk.getBlock(localX, localY, localZ);
        }
        
        // Calculate world coordinates and look up in adjacent chunk
        int worldX = chunk.chunkX() * 16 + localX;
        int worldZ = chunk.chunkZ() * 16 + localZ;
        
        return getBlock(worldX, localY, worldZ);
    }
}
