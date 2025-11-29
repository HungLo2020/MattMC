package mattmc.client.particle;

import mattmc.core.particles.SimpleParticleType;
import mattmc.world.level.Level;

/**
 * Flame particle implementation.
 * 
 * <p>Flame particles are bright, float upward, and shrink over time.
 * They ignore world lighting and are always fully bright.
 * 
 * <p>This mirrors Minecraft's FlameParticle behavior.
 */
public class FlameParticle extends TextureSheetParticle {
    
    protected FlameParticle(Level level, double x, double y, double z,
                           double xSpeed, double ySpeed, double zSpeed) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        
        // Set velocity with upward bias
        this.xd = xSpeed + (random.nextFloat() - 0.5f) * 0.1f;
        this.yd = ySpeed + random.nextFloat() * 0.2f;
        this.zd = zSpeed + (random.nextFloat() - 0.5f) * 0.1f;
        
        // Bright orange/yellow color
        this.rCol = 1.0f;
        this.gCol = 0.8f + random.nextFloat() * 0.2f;
        this.bCol = 0.3f + random.nextFloat() * 0.3f;
        
        // Scale
        this.quadSize *= 0.5f + random.nextFloat() * 0.5f;
        
        // Lifetime
        this.lifetime = (int) (6.0f / (random.nextFloat() * 0.5f + 0.5f)) + 2;
        
        // Physics - no gravity, just float
        this.gravity = 0.0f;
        this.friction = 0.96f;
        this.hasPhysics = false;
    }
    
    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }
    
    @Override
    public void move(double x, double y, double z) {
        // Override to skip collision detection
        this.x += x;
        this.y += y;
        this.z += z;
    }
    
    @Override
    public float getQuadSize(float partialTicks) {
        // Shrink over lifetime
        float progress = ((float) this.age + partialTicks) / (float) this.lifetime;
        return this.quadSize * (1.0f - progress * progress * 0.5f);
    }
    
    @Override
    protected int getLightColor(float partialTicks) {
        // Always fully bright (emissive)
        float progress = ((float) this.age + partialTicks) / (float) this.lifetime;
        progress = Math.min(progress, 1.0f);
        
        // Max brightness with some scaling based on age
        int brightness = (int) (15.0f * 16.0f);
        return brightness | (brightness << 16);
    }
    
    /**
     * Provider factory for flame particles.
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
            FlameParticle particle = new FlameParticle(level, x, y, z, xSpeed, ySpeed, zSpeed);
            particle.pickSprite(sprites);
            return particle;
        }
    }
    
    /**
     * Provider factory for small flame particles.
     */
    public static class SmallFlameProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        
        public SmallFlameProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }
        
        @Override
        public Particle createParticle(SimpleParticleType options, Level level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            FlameParticle particle = new FlameParticle(level, x, y, z, xSpeed, ySpeed, zSpeed);
            particle.pickSprite(sprites);
            particle.scale(0.5f);
            return particle;
        }
    }
}
