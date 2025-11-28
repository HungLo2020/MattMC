package mattmc.world.entity.player;

import mattmc.client.MattMC;

import mattmc.world.item.BlockItem;
import mattmc.world.item.Item;
import mattmc.world.item.ItemStack;
import mattmc.world.item.Items;
import mattmc.world.level.block.Block;
import mattmc.world.level.block.Blocks;
import mattmc.world.level.block.SlabBlock;
import mattmc.world.level.chunk.ChunkUtils;
import mattmc.world.level.chunk.LevelChunk;
import mattmc.world.level.LevelAccessor;

/**
 * Handles block breaking and placing mechanics.
 * Similar to MattMC's PlayerInteractionManager class.
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
     * Handles special cases like slab-to-slab placement to form double slabs.
     * @param block The block to place
     */
    public void placeBlock(Block block) {
        BlockHitResult hit = raycastBlock();
        if (hit == null) {
            return;
        }
        
        // Check if we can combine slabs
        if (block instanceof SlabBlock) {
            SlabBlock slabToPlace = (SlabBlock) block;
            
            // Check if the hit block is the same type of slab
            Block hitBlock = world.getBlock(hit.x, hit.y, hit.z);
            if (hitBlock instanceof SlabBlock && hitBlock.getIdentifier() != null 
                && hitBlock.getIdentifier().equals(block.getIdentifier())) {
                
                SlabBlock existingSlab = (SlabBlock) hitBlock;
                mattmc.world.level.block.state.BlockState existingState = world.getBlockState(hit.x, hit.y, hit.z);
                
                // Check if we can combine these slabs into a double slab
                if (existingSlab.canBeReplacedBy(existingState, hit.hitFace, hit.hitY)) {
                    // Replace with double slab
                    mattmc.world.level.block.state.BlockState doubleState = slabToPlace.getDoubleSlabState();
                    world.setBlock(hit.x, hit.y, hit.z, block, doubleState);
                    return;
                }
            }
        }
        
        // Normal placement in adjacent position
        if (hit.adjacentY >= 0 && hit.adjacentY < LevelChunk.HEIGHT) {
            // Place block at the adjacent position (the face we hit)
            Block existing = world.getBlock(hit.adjacentX, hit.adjacentY, hit.adjacentZ);
            if (existing.isAir()) {
                // Get placement state from block based on player position and hit face
                mattmc.world.level.block.state.BlockState state = block.getPlacementState(
                    player.getX(), player.getY(), player.getZ(),
                    hit.adjacentX, hit.adjacentY, hit.adjacentZ,
                    hit.hitFace,
                    hit.hitX, hit.hitY, hit.hitZ
                );
                
                // Place block with state
                world.setBlock(hit.adjacentX, hit.adjacentY, hit.adjacentZ, block, state);
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
     * Pick the block the player is looking at and add it to their inventory.
     * Implements Minecraft's middle-click pick block behavior:
     * 1. If the item is already in the hotbar, switch to that slot
     * 2. If the item is in the main inventory, swap it with the current hotbar slot
     * 3. If the item is not in inventory and hotbar is full, move current slot to inventory first
     * 
     * @return true if a block was picked successfully
     */
    public boolean pickBlock() {
        BlockHitResult hit = raycastBlock();
        if (hit == null) {
            return false;
        }
        
        Block block = world.getBlock(hit.x, hit.y, hit.z);
        if (block == null || block.isAir()) {
            return false;
        }
        
        // Find the corresponding BlockItem for this block using the Items registry
        String blockId = block.getIdentifier();
        if (blockId == null) {
            return false;
        }
        
        // Look up the item dynamically from the registry using the block's identifier
        Item item = Items.getItem(blockId);
        if (item == null) {
            return false; // No corresponding item for this block
        }
        
        // Verify it's a BlockItem (items that can be placed as blocks)
        if (!(item instanceof BlockItem)) {
            return false;
        }
        
        // Create an item stack for the picked block
        ItemStack stack = new ItemStack(item, 1);
        
        // Use Minecraft-style pick item behavior
        player.getInventory().setPickedItem(stack);
        return true;
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
        int lastBlockY = ChunkUtils.worldToLocalY((int) Math.floor(rayY));
        int lastBlockZ = (int) Math.floor(rayZ);
        
        for (int i = 0; i < steps; i++) {
            rayX += dirX * stepSize;
            rayY += dirY * stepSize;
            rayZ += dirZ * stepSize;
            
            int blockX = (int) Math.floor(rayX);
            int blockY = (int) Math.floor(rayY);
            int blockZ = (int) Math.floor(rayZ);
            
            // Convert world Y to chunk Y
            int chunkY = ChunkUtils.worldToLocalY(blockY);
            
            // Check if Y is valid
            if (chunkY >= 0 && chunkY < LevelChunk.HEIGHT) {
                Block block = world.getBlock(blockX, chunkY, blockZ);
                if (!block.isAir()) {
                    // Found a solid block - return it and the last air position
                    // Pass the exact ray hit position (rayX, rayY, rayZ)
                    int hitFace = BlockHitResult.determineHitFace(blockX, chunkY, blockZ, lastBlockX, lastBlockY, lastBlockZ);
                    return new BlockHitResult(blockX, chunkY, blockZ, lastBlockX, lastBlockY, lastBlockZ, hitFace, rayX, rayY, rayZ);
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
        public final int hitFace;              // Face that was hit (0=bottom, 1=top, 2=north, 3=south, 4=west, 5=east)
        public final float hitX, hitY, hitZ;   // Exact position where the ray hit the block
        
        public BlockHitResult(int x, int y, int z, int adjX, int adjY, int adjZ, int hitFace, float hitX, float hitY, float hitZ) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.adjacentX = adjX;
            this.adjacentY = adjY;
            this.adjacentZ = adjZ;
            this.hitFace = hitFace;
            this.hitX = hitX;
            this.hitY = hitY;
            this.hitZ = hitZ;
        }
        
        // Legacy constructor for compatibility
        public BlockHitResult(int x, int y, int z, int adjX, int adjY, int adjZ) {
            this(x, y, z, adjX, adjY, adjZ, determineHitFace(x, y, z, adjX, adjY, adjZ), adjX + 0.5f, adjY + 0.5f, adjZ + 0.5f);
        }
        
        public static int determineHitFace(int x, int y, int z, int adjX, int adjY, int adjZ) {
            // Determine which face based on difference
            if (adjY < y) return 0; // bottom
            if (adjY > y) return 1; // top
            if (adjZ < z) return 2; // north
            if (adjZ > z) return 3; // south
            if (adjX < x) return 4; // west
            if (adjX > x) return 5; // east
            return 1; // default to top
        }
    }
}
