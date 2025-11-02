package MattMC.world;

/**
 * Interface for accessing blocks in a world.
 * Implemented by both Region (legacy) and World (infinite) systems.
 */
public interface WorldAccess {
    /**
     * Get a block at the specified coordinates.
     * For Region: coordinates are region-local (0-511 for X/Z)
     * For World: coordinates are world coordinates (can be any value)
     */
    Block getBlock(int x, int y, int z);
    
    /**
     * Set a block at the specified coordinates.
     * For Region: coordinates are region-local (0-511 for X/Z)
     * For World: coordinates are world coordinates (can be any value)
     */
    void setBlock(int x, int y, int z, Block block);
}
