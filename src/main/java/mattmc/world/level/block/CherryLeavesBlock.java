package mattmc.world.level.block;

import mattmc.world.level.Level;

import java.util.Random;

/**
 * Cherry leaves block that spawns falling cherry blossom particles.
 * 
 * <p>Cherry leaves have a 10% chance per animateTick to spawn a falling petal
 * particle below them if there's air below.
 * 
 * <p>Mirrors Minecraft's CherryLeavesBlock.
 */
public class CherryLeavesBlock extends LeavesBlock {
    
    /**
     * Create cherry leaves without tinting.
     */
    public CherryLeavesBlock() {
        super(-1);
    }
    
    /**
     * Internal constructor used during registration.
     */
    CherryLeavesBlock(String identifier) {
        super(-1, identifier);
    }
    
    /**
     * Cherry leaves spawn particles and should indicate this.
     */
    @Override
    public boolean hasRandomTick() {
        return true;
    }
    
    /**
     * Called periodically client-side to spawn falling cherry blossom particles.
     * Mirrors Minecraft's CherryLeavesBlock.animateTick.
     * 
     * <p>Only spawns a particle 10% of the time, and only if there's no solid
     * block directly below (so petals can fall).
     */
    @Override
    public void animateTick(Level level, int x, int y, int z, Random random,
                           ParticleSpawner particleSpawner) {
        // Only 10% chance to spawn a particle (like Minecraft)
        if (random.nextInt(10) != 0) {
            return;
        }
        
        // Check if there's space below for the particle to fall
        Block blockBelow = level.getBlock(x, y - 1, z);
        if (blockBelow != null && blockBelow.isSolid()) {
            return;  // Don't spawn if there's a solid block below
        }
        
        // Spawn particle below the block, at a random position within the block bounds
        double px = x + random.nextDouble();
        double py = y - 0.05;  // Slightly below the block (like Minecraft's spawnParticleBelow)
        double pz = z + random.nextDouble();
        
        // Cherry leaves particles fall slowly with a slight horizontal drift
        particleSpawner.spawn("cherry_leaves", px, py, pz, 0.0, 0.0, 0.0);
    }
}
