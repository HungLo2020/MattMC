package mattmc.world.entity.player;

import mattmc.client.Minecraft;

import mattmc.world.level.block.Block;
import mattmc.world.level.block.Blocks;
import mattmc.world.level.chunk.LevelChunk;
import mattmc.world.level.LevelAccessor;

/**
 * Handles block breaking and placing mechanics.
 * Similar to Minecraft's PlayerInteractionManager class.
 */
public class BlockInteraction {
    private final LocalPlayer player;
    private final LevelAccessor world;
    
    // Maximum reach distance for block interaction
    private static final float MAX_REACH_DISTANCE = 5.0f;
    
    public BlockInteraction(LocalPlayer player, LevelAccessor world) {
        this.player = player;
        this.world = world;
    }
    
    /**
     * Attempt to break the block the player is looking at.
     */
    public void breakBlock() {
        BlockHitResult hit = raycastBlock();
        if (hit != null) {
            world.setBlock(hit.x, hit.y, hit.z, Blocks.AIR);
        }
    }
    
    /**
     * Attempt to place a block on the face the player is looking at.
     * @param block The block to place
     */
    public void placeBlock(Block block) {
        BlockHitResult hit = raycastBlock();
        if (hit != null && hit.adjacentY >= 0 && hit.adjacentY < LevelChunk.HEIGHT) {
            // Place block at the adjacent position (the face we hit)
            Block existing = world.getBlock(hit.adjacentX, hit.adjacentY, hit.adjacentZ);
            if (existing.isAir()) {
                world.setBlock(hit.adjacentX, hit.adjacentY, hit.adjacentZ, block);
            }
        }
    }
    
    /**
     * Get the block the player is currently looking at.
     * Returns null if no block is found within reach distance.
     */
    public BlockHitResult getTargetedBlock() {
        return raycastBlock();
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
        
        // Start ray at player's eyes (camera position), not feet
        float rayX = player.getX();
        float rayY = player.getEyeY();
        float rayZ = player.getZ();
        
        // DDA algorithm for voxel traversal
        float stepSize = 0.1f;
        int steps = (int) (MAX_REACH_DISTANCE / stepSize);
        
        // Initialize last block position to the starting position
        int lastBlockX = (int) Math.floor(rayX);
        int lastBlockY = LevelChunk.worldYToChunkY((int) Math.floor(rayY));
        int lastBlockZ = (int) Math.floor(rayZ);
        
        for (int i = 0; i < steps; i++) {
            rayX += dirX * stepSize;
            rayY += dirY * stepSize;
            rayZ += dirZ * stepSize;
            
            int blockX = (int) Math.floor(rayX);
            int blockY = (int) Math.floor(rayY);
            int blockZ = (int) Math.floor(rayZ);
            
            // Convert world Y to chunk Y
            int chunkY = LevelChunk.worldYToChunkY(blockY);
            
            // Check if Y is valid
            if (chunkY >= 0 && chunkY < LevelChunk.HEIGHT) {
                Block block = world.getBlock(blockX, chunkY, blockZ);
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
    public static class BlockHitResult {
        public final int x, y, z;              // Block that was hit
        public final int adjacentX, adjacentY, adjacentZ;  // Adjacent air block (for placing)
        
        public BlockHitResult(int x, int y, int z, int adjX, int adjY, int adjZ) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.adjacentX = adjX;
            this.adjacentY = adjY;
            this.adjacentZ = adjZ;
        }
    }
}
