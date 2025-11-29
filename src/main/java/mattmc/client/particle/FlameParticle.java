package mattmc.client.particle;

import mattmc.core.particles.SimpleParticleType;
import mattmc.world.level.Level;

/**
 * Flame particle implementation.
 * 
 * <p>Flame particles are bright, float upward, and shrink over time.
 * They ignore world lighting and are always fully bright.
 * 
 * <p>This mirrors Minecraft's FlameParticle (extends RisingParticle) behavior exactly.
 */
public class FlameParticle extends TextureSheetParticle {
    
    protected FlameParticle(Level level, double x, double y, double z,
                           double xSpeed, double ySpeed, double zSpeed) {
        // Call 6-parameter constructor so xd/yd/zd get random initial values
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        
        // Matches Minecraft's RisingParticle constructor exactly
        this.friction = 0.96f;
        
        // Apply small random multiplier to initial velocity, then add speed parameter
        // xd/yd/zd were set to random values by base Particle constructor
        this.xd = this.xd * 0.01 + xSpeed;
        this.yd = this.yd * 0.01 + ySpeed;
        this.zd = this.zd * 0.01 + zSpeed;
        
        // Add small random position offset (not velocity) - matches Minecraft exactly
        this.x += (random.nextFloat() - random.nextFloat()) * 0.05;
        this.y += (random.nextFloat() - random.nextFloat()) * 0.05;
        this.z += (random.nextFloat() - random.nextFloat()) * 0.05;
        
        // Lifetime formula matches Minecraft exactly: (int)(8.0 / (random * 0.8 + 0.2)) + 4
        // This gives range of approximately 6-44 ticks
        this.lifetime = (int) (8.0 / (random.nextDouble() * 0.8 + 0.2)) + 4;
        
        // No gravity for flame particles - they just drift with friction
        this.gravity = 0.0f;
        this.hasPhysics = false;
    }
    
    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }
    
    @Override
    public void move(double x, double y, double z) {
        // Matches Minecraft's FlameParticle.move exactly
        // Simply moves without collision detection by updating bounding box position
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
        // Matches Minecraft's FlameParticle.getLightColor exactly
        // Adds additional brightness based on particle age
        float progress = ((float) this.age + partialTicks) / (float) this.lifetime;
        progress = Math.max(0.0f, Math.min(progress, 1.0f)); // Clamp 0-1
        
        int baseLight = super.getLightColor(partialTicks);
        int blockLight = baseLight & 255;
        int skyLight = (baseLight >> 16) & 255;
        
        // Add brightness based on age (emissive effect)
        blockLight += (int) (progress * 15.0f * 16.0f);
        if (blockLight > 240) {
            blockLight = 240;
        }
        
        return blockLight | (skyLight << 16);
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
