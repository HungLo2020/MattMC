package mattmc.client.particle;

import mattmc.core.particles.SimpleParticleType;
import mattmc.world.level.Level;

/**
 * Smoke particle implementation.
 * 
 * <p>Smoke particles float upward and fade out over time.
 * This mirrors Minecraft's SmokeParticle behavior.
 */
public class SmokeParticle extends TextureSheetParticle {
    
    private final SpriteSet sprites;
    
    protected SmokeParticle(Level level, double x, double y, double z, 
                           double xSpeed, double ySpeed, double zSpeed,
                           float quadSizeMultiplier, SpriteSet sprites) {
        super(level, x, y, z, 0, 0, 0);
        this.sprites = sprites;
        
        // Set velocity with some randomness
        this.xd = xSpeed + (random.nextFloat() - 0.5f) * 0.1f;
        this.yd = ySpeed + random.nextFloat() * 0.1f + 0.1f; // Always float upward
        this.zd = zSpeed + (random.nextFloat() - 0.5f) * 0.1f;
        
        // Gray color with variation
        float brightness = 0.6f + random.nextFloat() * 0.2f;
        this.rCol = brightness;
        this.gCol = brightness;
        this.bCol = brightness;
        
        // Scale
        this.quadSize *= 0.75f * quadSizeMultiplier;
        
        // Lifetime
        this.lifetime = (int) (8.0f / (random.nextFloat() * 0.8f + 0.2f));
        
        // Physics
        this.gravity = -0.1f; // Float upward
        this.friction = 0.96f;
        this.hasPhysics = false;
        
        // Pick initial sprite
        this.setSpriteFromAge(sprites);
    }
    
    @Override
    public void tick() {
        super.tick();
        // Animate through sprites based on age
        this.setSpriteFromAge(sprites);
        
        // Fade out
        this.alpha = 1.0f - ((float) age / (float) lifetime);
    }
    
    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
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
