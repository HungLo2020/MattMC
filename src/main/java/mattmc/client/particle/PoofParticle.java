package mattmc.client.particle;

import mattmc.core.particles.SimpleParticleType;
import mattmc.world.level.Level;

/**
 * Poof particle (explosion cloud) implementation.
 * 
 * <p>Poof particles are short-lived, expand and fade quickly.
 * Used for explosions and similar effects.
 * 
 * <p>This mirrors Minecraft's ExplodeParticle behavior.
 */
public class PoofParticle extends TextureSheetParticle {
    
    private final SpriteSet sprites;
    
    protected PoofParticle(Level level, double x, double y, double z,
                          double xSpeed, double ySpeed, double zSpeed,
                          SpriteSet sprites) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.sprites = sprites;
        
        // Set velocity
        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;
        
        // Gray/white color
        float brightness = 0.7f + random.nextFloat() * 0.3f;
        this.rCol = brightness;
        this.gCol = brightness;
        this.bCol = brightness;
        
        // Scale
        this.quadSize *= 0.6f + random.nextFloat() * 0.4f;
        
        // Short lifetime
        this.lifetime = (int) (6.0f / (random.nextFloat() * 0.5f + 0.5f)) + 1;
        
        // No gravity, just drift
        this.gravity = 0.0f;
        this.friction = 0.94f;
        this.hasPhysics = false;
        
        // Pick initial sprite
        this.setSpriteFromAge(sprites);
    }
    
    @Override
    public void tick() {
        super.tick();
        // Animate through sprites
        this.setSpriteFromAge(sprites);
    }
    
    @Override
    public float getQuadSize(float partialTicks) {
        // Expand then shrink
        float progress = ((float) this.age + partialTicks) / (float) this.lifetime;
        float scale = 1.0f - progress;
        // Start small, expand, then shrink
        if (progress < 0.3f) {
            scale = progress / 0.3f;
        }
        return this.quadSize * Math.max(0.1f, scale);
    }
    
    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }
    
    /**
     * Provider factory for poof particles.
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
            return new PoofParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
        }
    }
}
