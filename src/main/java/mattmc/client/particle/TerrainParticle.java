package mattmc.client.particle;

import mattmc.world.level.Level;
import mattmc.world.level.block.Block;

/**
 * Particle that uses a block's texture, for block breaking effects.
 * 
 * <p>Terrain particles render using the block texture atlas rather than
 * the particle atlas. They sample a random portion of the block texture
 * to create variation.
 * 
 * <p>This mirrors Minecraft's TerrainParticle.
 */
public class TerrainParticle extends TextureSheetParticle {
    
    private final Block block;
    private final float uo; // U offset for texture sampling
    private final float vo; // V offset for texture sampling
    
    // Block texture UV coordinates
    private float blockU0;
    private float blockV0;
    private float blockU1;
    private float blockV1;
    
    /**
     * Create a terrain particle for a block.
     * 
     * @param level the level
     * @param x spawn X
     * @param y spawn Y
     * @param z spawn Z
     * @param xSpeed initial X velocity
     * @param ySpeed initial Y velocity
     * @param zSpeed initial Z velocity
     * @param block the block to use texture from
     */
    public TerrainParticle(Level level, double x, double y, double z,
                          double xSpeed, double ySpeed, double zSpeed, Block block) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.block = block;
        
        // Random offset within the texture (0-3)
        this.uo = random.nextFloat() * 3.0f;
        this.vo = random.nextFloat() * 3.0f;
        
        // Darker color to simulate lighting
        this.rCol = 0.6f;
        this.gCol = 0.6f;
        this.bCol = 0.6f;
        
        // Smaller quad size for terrain particles
        this.quadSize /= 2.0f;
        
        // Physics
        this.gravity = 1.0f;
        this.friction = 0.98f;
        this.hasPhysics = true;
        
        // Lifetime
        this.lifetime = (int) (4.0f / (random.nextFloat() * 0.9f + 0.1f));
    }
    
    /**
     * Set the block texture UV coordinates.
     * Call this after construction to set the texture coordinates from the block atlas.
     */
    public TerrainParticle setBlockTextureUVs(float u0, float v0, float u1, float v1) {
        this.blockU0 = u0;
        this.blockV0 = v0;
        this.blockU1 = u1;
        this.blockV1 = v1;
        return this;
    }
    
    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.TERRAIN_SHEET;
    }
    
    @Override
    protected float getU0() {
        // Sample a 1/4 section of the texture with offset
        float uRange = blockU1 - blockU0;
        return blockU0 + (uo / 4.0f) * uRange;
    }
    
    @Override
    protected float getU1() {
        float uRange = blockU1 - blockU0;
        return blockU0 + ((uo + 1.0f) / 4.0f) * uRange;
    }
    
    @Override
    protected float getV0() {
        float vRange = blockV1 - blockV0;
        return blockV0 + (vo / 4.0f) * vRange;
    }
    
    @Override
    protected float getV1() {
        float vRange = blockV1 - blockV0;
        return blockV0 + ((vo + 1.0f) / 4.0f) * vRange;
    }
    
    @Override
    protected int getLightColor(float partialTicks) {
        // Sample light at particle position
        // For now, return a reasonable default
        int blockLight = 15;
        int skyLight = 15;
        return (skyLight << 4) | (blockLight << 20);
    }
    
    /**
     * Get the block this particle is for.
     */
    public Block getBlock() {
        return block;
    }
}
