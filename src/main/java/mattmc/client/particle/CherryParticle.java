package mattmc.client.particle;

import mattmc.core.particles.SimpleParticleType;
import mattmc.world.level.Level;

/**
 * Cherry blossom falling leaves particle.
 * 
 * <p>These particles have a slow falling motion with a gentle horizontal sway
 * and rotation, simulating falling cherry blossom petals.
 * 
 * <p>Mirrors Minecraft's CherryParticle behavior.
 */
public class CherryParticle extends TextureSheetParticle {
    
    private static final float ACCELERATION_SCALE = 0.0025f;
    private static final int INITIAL_LIFETIME = 300;
    private static final float FALL_ACC = 0.25f;
    private static final float WIND_BIG = 2.0f;
    
    private float rotSpeed;
    private final float particleRandom;
    private final float spinAcceleration;
    
    protected CherryParticle(Level level, double x, double y, double z, SpriteSet spriteSet) {
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
        
        // Cherry blossom pink color
        this.rCol = 1.0f;
        this.gCol = 0.9f;
        this.bCol = 0.95f;
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
            // Calculate wind effect based on lifetime
            float f = (float) (INITIAL_LIFETIME - this.lifetime);
            float f1 = Math.min(f / (float) INITIAL_LIFETIME, 1.0f);
            
            // Sinusoidal horizontal movement (swaying)
            double windX = Math.cos(Math.toRadians(this.particleRandom * 60.0f)) * WIND_BIG * Math.pow(f1, 1.25);
            double windZ = Math.sin(Math.toRadians(this.particleRandom * 60.0f)) * WIND_BIG * Math.pow(f1, 1.25);
            
            this.xd += windX * ACCELERATION_SCALE;
            this.zd += windZ * ACCELERATION_SCALE;
            this.yd -= this.gravity;
            
            // Update rotation
            this.rotSpeed += this.spinAcceleration / 20.0f;
            this.oRoll = this.roll;
            this.roll += this.rotSpeed / 20.0f;
            
            // Apply movement
            this.move(this.xd, this.yd, this.zd);
            
            // Remove if on ground or stuck
            if (this.onGround || (this.lifetime < (INITIAL_LIFETIME - 1) && (this.xd == 0.0 || this.zd == 0.0))) {
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
     * Provider factory for cherry particles.
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
            return new CherryParticle(level, x, y, z, sprites);
        }
    }
}
