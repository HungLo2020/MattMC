package mattmc.world.level.block;

import mattmc.world.level.Level;

import java.util.Random;

/**
 * Leaves block that spawns falling leaves particles with a configurable tint color.
 * 
 * <p>This block extends LeavesBlock to add particle spawning behavior similar to
 * cherry leaves. The particles use grayscale textures that are tinted with the
 * specified RGB color, allowing different tree types to have uniquely colored
 * falling leaves.
 * 
 * <p>The block has a 10% chance per animateTick to spawn a falling leaf particle
 * below it if there's air below.
 */
public class FallingLeavesBlock extends LeavesBlock {
    
    /** Red color component for particle tinting (0.0-1.0). */
    private final float particleRed;
    
    /** Green color component for particle tinting (0.0-1.0). */
    private final float particleGreen;
    
    /** Blue color component for particle tinting (0.0-1.0). */
    private final float particleBlue;
    
    /**
     * Create a falling leaves block with the specified particle tint color.
     * 
     * <p>The RGB values are used to tint the grayscale falling leaves particle
     * textures. For example, cherry leaves might use (1.0, 0.7, 0.8) for pink.
     * 
     * @param particleRed red component of the particle tint (0.0-1.0)
     * @param particleGreen green component of the particle tint (0.0-1.0)
     * @param particleBlue blue component of the particle tint (0.0-1.0)
     */
    public FallingLeavesBlock(float particleRed, float particleGreen, float particleBlue) {
        super(-1);  // No block tinting (uses texture as-is)
        this.particleRed = particleRed;
        this.particleGreen = particleGreen;
        this.particleBlue = particleBlue;
    }
    
    /**
     * Create a falling leaves block with block tinting and particle tinting.
     * 
     * @param blockTintColor the block tint color in ARGB format (negative value from MC format),
     *                       or -1 for no block tinting
     * @param particleRed red component of the particle tint (0.0-1.0)
     * @param particleGreen green component of the particle tint (0.0-1.0)
     * @param particleBlue blue component of the particle tint (0.0-1.0)
     */
    public FallingLeavesBlock(int blockTintColor, float particleRed, float particleGreen, float particleBlue) {
        super(blockTintColor);
        this.particleRed = particleRed;
        this.particleGreen = particleGreen;
        this.particleBlue = particleBlue;
    }
    
    /**
     * Internal constructor used during registration.
     */
    FallingLeavesBlock(int blockTintColor, float particleRed, float particleGreen, float particleBlue, String identifier) {
        super(blockTintColor, identifier);
        this.particleRed = particleRed;
        this.particleGreen = particleGreen;
        this.particleBlue = particleBlue;
    }
    
    /**
     * Get the red component of the particle tint color.
     * 
     * @return red component (0.0-1.0)
     */
    public float getParticleRed() {
        return particleRed;
    }
    
    /**
     * Get the green component of the particle tint color.
     * 
     * @return green component (0.0-1.0)
     */
    public float getParticleGreen() {
        return particleGreen;
    }
    
    /**
     * Get the blue component of the particle tint color.
     * 
     * @return blue component (0.0-1.0)
     */
    public float getParticleBlue() {
        return particleBlue;
    }
    
    /**
     * Falling leaves spawn particles and should indicate this.
     */
    @Override
    public boolean hasRandomTick() {
        return true;
    }
    
    /**
     * Called periodically client-side to spawn falling leaf particles.
     * 
     * <p>Only spawns a particle 10% of the time, and only if there's no solid
     * block directly below (so leaves can fall).
     * 
     * @param level the level the block is in
     * @param x block X position (world coordinates)
     * @param y block Y position (world coordinates)
     * @param z block Z position (world coordinates)
     * @param random random source
     * @param particleSpawner callback to spawn particles
     */
    @Override
    public void animateTick(Level level, int x, int y, int z, Random random,
                           ParticleSpawner particleSpawner) {
        // Only 10% chance to spawn a particle (like Minecraft)
        if (random.nextInt(10) != 0) {
            return;
        }
        
        // Check if there's space below for the particle to fall
        // Convert world Y to chunk-local Y for getBlock()
        int chunkYBelow = mattmc.world.level.chunk.ChunkUtils.worldToLocalY(y - 1);
        if (chunkYBelow < 0) {
            return;  // Below world minimum
        }
        
        Block blockBelow = level.getBlock(x, chunkYBelow, z);
        if (blockBelow != null && blockBelow.isSolid()) {
            return;  // Don't spawn if there's a solid block below
        }
        
        // Spawn particle below the block, at a random position within the block bounds
        double px = x + random.nextDouble();
        double py = y - 0.05;  // Slightly below the block
        double pz = z + random.nextDouble();
        
        // Spawn the falling_leaves particle
        // Note: The particle color is handled by the particle provider registered for this block type
        particleSpawner.spawn("falling_leaves", px, py, pz, 0.0, 0.0, 0.0);
    }
}
