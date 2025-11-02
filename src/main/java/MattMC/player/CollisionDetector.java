package MattMC.player;

import MattMC.world.Block;
import MattMC.world.Chunk;
import MattMC.world.WorldAccess;

/**
 * Handles collision detection for the player against the world blocks.
 * Uses axis-aligned bounding box (AABB) collision detection.
 */
public class CollisionDetector {
    private final WorldAccess world;
    
    public CollisionDetector(WorldAccess world) {
        this.world = world;
    }
    
    /**
     * Check if a player hitbox at the given position collides with any blocks.
     * @param x Player center X position
     * @param y Player feet Y position
     * @param z Player center Z position
     * @return true if collision detected
     */
    public boolean checkCollision(float x, float y, float z) {
        // Player feet are at y, head at y + HEIGHT
        // Check all blocks that could intersect with player hitbox
        
        float minX = x - PlayerPhysics.PLAYER_WIDTH / 2;
        float maxX = x + PlayerPhysics.PLAYER_WIDTH / 2;
        float minY = y;
        float maxY = y + PlayerPhysics.PLAYER_HEIGHT;
        float minZ = z - PlayerPhysics.PLAYER_DEPTH / 2;
        float maxZ = z + PlayerPhysics.PLAYER_DEPTH / 2;
        
        // Check all potential collision blocks
        int startX = (int) Math.floor(minX);
        int endX = (int) Math.floor(maxX);
        int startY = (int) Math.floor(minY);
        int endY = (int) Math.floor(maxY);
        int startZ = (int) Math.floor(minZ);
        int endZ = (int) Math.floor(maxZ);
        
        for (int bx = startX; bx <= endX; bx++) {
            for (int by = startY; by <= endY; by++) {
                for (int bz = startZ; bz <= endZ; bz++) {
                    // Convert world Y to chunk Y
                    int chunkY = Chunk.worldYToChunkY(by);
                    
                    // Check if block coordinates are valid
                    if (chunkY >= 0 && chunkY < Chunk.HEIGHT) {
                        Block block = world.getBlock(bx, chunkY, bz);
                        if (!block.isAir()) {
                            // Solid block found - collision!
                            return true;
                        }
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Handle vertical collision and adjust Y position to prevent clipping.
     * @param x Player X position
     * @param z Player Z position
     * @param targetY Target Y position
     * @param falling true if moving downward, false if moving upward
     * @return adjusted Y position
     */
    public float handleVerticalCollision(float x, float z, float targetY, boolean falling) {
        // Check if target position collides
        if (checkCollision(x, targetY, z)) {
            // Adjust position to prevent clipping
            if (falling) {
                // Find ground level
                int blockY = (int) Math.floor(targetY);
                return blockY + 1.0f; // Stand on top of block
            } else {
                // Hit ceiling
                int blockY = (int) Math.floor(targetY + PlayerPhysics.PLAYER_HEIGHT);
                return blockY - PlayerPhysics.PLAYER_HEIGHT; // Stop at ceiling
            }
        }
        
        return targetY;
    }
    
    /**
     * Find the highest solid block at given X,Z position to spawn player on top.
     * Ensures spawn position has at least 2 blocks of air above for player headroom.
     */
    public static float findSpawnHeight(WorldAccess world, float x, float z) {
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        
        // Search from top down
        for (int worldY = Chunk.MAX_Y; worldY >= Chunk.MIN_Y; worldY--) {
            int chunkY = Chunk.worldYToChunkY(worldY);
            
            // Check if this is a solid block
            Block block = world.getBlock(blockX, chunkY, blockZ);
            if (!block.isAir()) {
                // Check if there's enough headroom (at least 2 blocks of air above)
                int spawnWorldY = worldY + 1;
                int spawnChunkY = Chunk.worldYToChunkY(spawnWorldY);
                int headChunkY = Chunk.worldYToChunkY(spawnWorldY + 1);
                
                if (spawnChunkY >= 0 && spawnChunkY < Chunk.HEIGHT && 
                    headChunkY >= 0 && headChunkY < Chunk.HEIGHT) {
                    Block aboveBlock = world.getBlock(blockX, spawnChunkY, blockZ);
                    Block headBlock = world.getBlock(blockX, headChunkY, blockZ);
                    
                    if (aboveBlock.isAir() && headBlock.isAir()) {
                        // Found valid spawn location - on top of solid block with air above
                        return spawnWorldY;
                    }
                }
            }
        }
        
        // No solid block found or no valid spawn location - default to surface level
        // This should rarely happen with proper terrain generation
        return 65f;
    }
}
