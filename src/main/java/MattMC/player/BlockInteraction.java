package MattMC.player;

import MattMC.world.Block;
import MattMC.world.BlockType;
import MattMC.world.Chunk;

/**
 * Handles block breaking and placing mechanics.
 * Similar to Minecraft's PlayerInteractionManager class.
 */
public class BlockInteraction {
    private final Player player;
    private final Chunk chunk; // For now, single chunk. Later would be a World reference.
    
    // Maximum reach distance for block interaction
    private static final float MAX_REACH_DISTANCE = 5.0f;
    
    public BlockInteraction(Player player, Chunk chunk) {
        this.player = player;
        this.chunk = chunk;
    }
    
    /**
     * Attempt to break the block the player is looking at.
     */
    public void breakBlock() {
        BlockHitResult hit = raycastBlock();
        if (hit != null) {
            chunk.setBlock(hit.x, hit.y, hit.z, Block.AIR);
        }
    }
    
    /**
     * Attempt to place a block on the face the player is looking at.
     * @param blockType Type of block to place
     */
    public void placeBlock(BlockType blockType) {
        BlockHitResult hit = raycastBlock();
        if (hit != null && hit.adjacentX >= 0 && hit.adjacentY >= 0 && hit.adjacentZ >= 0) {
            // Place block at the adjacent position (the face we hit)
            Block existing = chunk.getBlock(hit.adjacentX, hit.adjacentY, hit.adjacentZ);
            if (existing.isAir()) {
                chunk.setBlock(hit.adjacentX, hit.adjacentY, hit.adjacentZ, new Block(blockType));
            }
        }
    }
    
    /**
     * Perform ray casting to find the block the player is looking at.
     * Returns null if no block is found within maxDistance.
     */
    private BlockHitResult raycastBlock() {
        float[] dir = player.getForwardVector();
        float dirX = dir[0];
        float dirY = dir[1];
        float dirZ = dir[2];
        
        // Start ray at player position
        float rayX = player.getX();
        float rayY = player.getY();
        float rayZ = player.getZ();
        
        // DDA algorithm for voxel traversal
        float stepSize = 0.1f;
        int steps = (int) (MAX_REACH_DISTANCE / stepSize);
        
        int lastBlockX = -1, lastBlockY = -1, lastBlockZ = -1;
        
        for (int i = 0; i < steps; i++) {
            rayX += dirX * stepSize;
            rayY += dirY * stepSize;
            rayZ += dirZ * stepSize;
            
            int blockX = (int) Math.floor(rayX);
            int blockY = (int) Math.floor(rayY);
            int blockZ = (int) Math.floor(rayZ);
            
            // Convert world Y to chunk Y
            int chunkY = Chunk.worldYToChunkY(blockY);
            
            // Check if within chunk bounds
            if (blockX >= 0 && blockX < Chunk.WIDTH && 
                chunkY >= 0 && chunkY < Chunk.HEIGHT && 
                blockZ >= 0 && blockZ < Chunk.DEPTH) {
                
                Block block = chunk.getBlock(blockX, chunkY, blockZ);
                if (!block.isAir()) {
                    // Found a solid block - return it and the last air position
                    return new BlockHitResult(blockX, chunkY, blockZ, lastBlockX, lastBlockY, lastBlockZ);
                }
            }
            
            lastBlockX = blockX;
            lastBlockY = chunkY;
            lastBlockZ = blockZ;
        }
        
        return null;
    }
    
    /**
     * Simple data class to hold ray cast hit result.
     */
    private static class BlockHitResult {
        final int x, y, z;              // Block that was hit
        final int adjacentX, adjacentY, adjacentZ;  // Adjacent air block (for placing)
        
        BlockHitResult(int x, int y, int z, int adjX, int adjY, int adjZ) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.adjacentX = adjX;
            this.adjacentY = adjY;
            this.adjacentZ = adjZ;
        }
    }
}
