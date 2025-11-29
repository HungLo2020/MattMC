package mattmc.client.particle;

import mattmc.core.particles.SimpleParticleType;
import mattmc.world.level.Level;

/**
 * Falling leaves particle with RGB tinting support.
 * 
 * <p>This particle uses grayscale leaf textures (leaves_0 through leaves_11) that
 * can be tinted with any RGB color. This allows different tree types to have
 * falling leaves particles in their own colors.
 * 
 * <p>The particle behavior is similar to cherry blossom petals: slow falling motion
 * with a gentle horizontal sway and rotation.
 */
public class FallingLeavesParticle extends TextureSheetParticle {
    
    /** Scale factor for wind acceleration application. */
    private static final float ACCELERATION_SCALE = 0.0025f;
    
    /** How long the particle lives (in ticks). */
    private static final int INITIAL_LIFETIME = 300;
    
    /** Maximum wind strength multiplier. */
    private static final float WIND_BIG = 2.0f;
    
    /** Frequency of wind oscillation (in degrees). */
    private static final float WIND_FREQUENCY_DEGREES = 60.0f;
    
    /** Power curve for wind acceleration over time. */
    private static final float WIND_ACCELERATION_POWER = 1.25f;
    
    /** Number of ticks per second for time conversion. */
    private static final float TICKS_PER_SECOND = 20.0f;
    
    private float rotSpeed;
    private final float particleRandom;
    private final float spinAcceleration;
    
    /**
     * Create a falling leaves particle with RGB tinting.
     * 
     * @param level the level the particle is in
     * @param x spawn X position
     * @param y spawn Y position
     * @param z spawn Z position
     * @param spriteSet the sprite set for the particle
     * @param red red color component (0.0-1.0)
     * @param green green color component (0.0-1.0)
     * @param blue blue color component (0.0-1.0)
     */
    protected FallingLeavesParticle(Level level, double x, double y, double z, SpriteSet spriteSet,
                                    float red, float green, float blue) {
        super(level, x, y, z);
        
        // Pick a random sprite from the sprite set
        this.pickSprite(spriteSet);
        
        // Random rotation direction and speed
        this.rotSpeed = (float) Math.toRadians(random.nextBoolean() ? -30.0 : 30.0);
        this.particleRandom = random.nextFloat();
        this.spinAcceleration = (float) Math.toRadians(random.nextBoolean() ? -5.0 : 5.0);
        
        // Long lifetime for slow falling effect
        this.lifetime = INITIAL_LIFETIME;
        
        // Very slow gravity for floating effect
        this.gravity = 7.5E-4f;
        
        // Random size variation
        float size = random.nextBoolean() ? 0.05f : 0.075f;
        this.quadSize = size;
        this.setSize(size, size);
        
        // No air friction (friction = 1.0 means velocity is preserved)
        this.friction = 1.0f;
        
        // No physics collision needed for leaves
        this.hasPhysics = false;
        
        // Apply the tint color
        this.rCol = red;
        this.gCol = green;
        this.bCol = blue;
    }
    
    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }
    
    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        
        if (this.lifetime-- <= 0) {
            this.remove();
            return;
        }
        
        if (!this.removed) {
            // Calculate wind effect based on lifetime progression
            float elapsedTime = (float) (INITIAL_LIFETIME - this.lifetime);
            float progressRatio = Math.min(elapsedTime / (float) INITIAL_LIFETIME, 1.0f);
            
            // Sinusoidal horizontal movement (swaying)
            double windStrength = WIND_BIG * Math.pow(progressRatio, WIND_ACCELERATION_POWER);
            double windX = Math.cos(Math.toRadians(this.particleRandom * WIND_FREQUENCY_DEGREES)) * windStrength;
            double windZ = Math.sin(Math.toRadians(this.particleRandom * WIND_FREQUENCY_DEGREES)) * windStrength;
            
            this.xd += windX * ACCELERATION_SCALE;
            this.zd += windZ * ACCELERATION_SCALE;
            this.yd -= this.gravity;
            
            // Update rotation (convert per-second values to per-tick)
            this.rotSpeed += this.spinAcceleration / TICKS_PER_SECOND;
            this.oRoll = this.roll;
            this.roll += this.rotSpeed / TICKS_PER_SECOND;
            
            // Apply movement
            this.move(this.xd, this.yd, this.zd);
            
            // Remove if on ground or stuck (no horizontal movement after first tick)
            boolean hasLanded = this.onGround;
            boolean isStuck = this.lifetime < (INITIAL_LIFETIME - 1) && (this.xd == 0.0 || this.zd == 0.0);
            if (hasLanded || isStuck) {
                this.remove();
            }
            
            // Apply friction
            if (!this.removed) {
                this.xd *= this.friction;
                this.yd *= this.friction;
                this.zd *= this.friction;
            }
        }
    }
    
    /**
     * Provider factory for falling leaves particles with a specific tint color.
     * 
     * <p>This provider creates particles tinted with the specified RGB color.
     * The tint is applied to the grayscale leaf textures.
     */
    public static class ColoredProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final float red;
        private final float green;
        private final float blue;
        
        /**
         * Create a provider with the specified tint color.
         * 
         * @param sprites the sprite set for the particle
         * @param red red color component (0.0-1.0)
         * @param green green color component (0.0-1.0)
         * @param blue blue color component (0.0-1.0)
         */
        public ColoredProvider(SpriteSet sprites, float red, float green, float blue) {
            this.sprites = sprites;
            this.red = red;
            this.green = green;
            this.blue = blue;
        }
        
        @Override
        public Particle createParticle(SimpleParticleType options, Level level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            return new FallingLeavesParticle(level, x, y, z, sprites, red, green, blue);
        }
    }
    
    /**
     * Provider factory for falling leaves particles with default white color.
     * 
     * <p>This provider creates particles with no tinting (white), useful for
     * registration where the sprite set is needed but no color is specified.
     */
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        
        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }
        
        @Override
        public Particle createParticle(SimpleParticleType options, Level level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            return new FallingLeavesParticle(level, x, y, z, sprites, 1.0f, 1.0f, 1.0f);
        }
    }
}
