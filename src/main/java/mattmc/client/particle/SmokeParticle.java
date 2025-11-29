package mattmc.client.particle;

import mattmc.core.particles.SimpleParticleType;
import mattmc.world.level.Level;

/**
 * Smoke particle implementation.
 * 
 * <p>Smoke particles float upward, grow in size, and use animated sprites.
 * This mirrors Minecraft's SmokeParticle (extends BaseAshSmokeParticle) behavior exactly.
 */
public class SmokeParticle extends TextureSheetParticle {
    
    private final SpriteSet sprites;
    
    protected SmokeParticle(Level level, double x, double y, double z, 
                           double xSpeed, double ySpeed, double zSpeed,
                           float quadSizeMultiplier, SpriteSet sprites) {
        // Call 6-parameter constructor so xd/yd/zd get random initial values
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.sprites = sprites;
        
        // Matches Minecraft's BaseAshSmokeParticle constructor exactly
        // Parameters from SmokeParticle: speedMultipliers=0.1, rColMultiplier=0.3, lifetime=8, gravity=-0.1, hasPhysics=true
        this.friction = 0.96f;
        this.gravity = -0.1f; // Negative gravity = floats up
        this.speedUpWhenYMotionIsBlocked = true;
        
        // Multiply initial random velocity by speed multiplier (0.1), then add speed parameter
        // xd/yd/zd were set to random values by base Particle constructor
        this.xd = this.xd * 0.1 + xSpeed;
        this.yd = this.yd * 0.1 + ySpeed;
        this.zd = this.zd * 0.1 + zSpeed;
        
        // Gray color with random variation - matches Minecraft exactly
        // Color = random * 0.3 (rColMultiplier)
        float colorValue = random.nextFloat() * 0.3f;
        this.rCol = colorValue;
        this.gCol = colorValue;
        this.bCol = colorValue;
        
        // Scale - matches Minecraft exactly
        this.quadSize *= 0.75f * quadSizeMultiplier;
        
        // Lifetime formula matches Minecraft exactly:
        // lifetime = (int)(baseLifetime / (random * 0.8 + 0.2) * sizeMultiplier)
        this.lifetime = (int) (8.0 / (random.nextFloat() * 0.8 + 0.2) * quadSizeMultiplier);
        this.lifetime = Math.max(this.lifetime, 1);
        
        // Physics enabled for smoke particles
        this.hasPhysics = true;
        
        // Pick initial sprite based on age
        this.setSpriteFromAge(sprites);
    }
    
    @Override
    public void tick() {
        super.tick();
        // Animate through sprites based on age - matches Minecraft exactly
        this.setSpriteFromAge(sprites);
    }
    
    @Override
    public ParticleRenderType getRenderType() {
        // Minecraft uses PARTICLE_SHEET_OPAQUE for smoke particles
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }
    
    @Override
    public float getQuadSize(float partialTicks) {
        // Matches Minecraft's BaseAshSmokeParticle.getQuadSize exactly
        // Size grows from 0 to full over the particle's lifetime (clamped at 1)
        float progress = ((float) this.age + partialTicks) / (float) this.lifetime * 32.0f;
        progress = Math.max(0.0f, Math.min(progress, 1.0f)); // Clamp 0-1
        return this.quadSize * progress;
    }
    
    /**
     * Provider factory for smoke particles.
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
            return new SmokeParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, 1.0f, sprites);
        }
    }
}
