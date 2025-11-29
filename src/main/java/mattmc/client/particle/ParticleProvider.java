package mattmc.client.particle;

import mattmc.core.particles.ParticleOptions;
import mattmc.world.level.Level;

/**
 * Factory interface for creating particles.
 * 
 * <p>Each particle type has an associated provider that knows how to create
 * the appropriate Particle instance from ParticleOptions and spawn parameters.
 * 
 * <p>Mirrors Minecraft's ParticleProvider interface.
 * 
 * @param <T> the type of particle options this provider handles
 */
@FunctionalInterface
public interface ParticleProvider<T extends ParticleOptions> {
    
    /**
     * Create a particle from the given options and spawn parameters.
     * 
     * @param options the particle options/data
     * @param level the level to spawn in
     * @param x spawn X position
     * @param y spawn Y position
     * @param z spawn Z position
     * @param xSpeed initial X velocity
     * @param ySpeed initial Y velocity
     * @param zSpeed initial Z velocity
     * @return the created particle, or null if the particle should not spawn
     */
    Particle createParticle(T options, Level level, double x, double y, double z, 
                           double xSpeed, double ySpeed, double zSpeed);
    
    /**
     * Simplified provider that automatically picks a sprite.
     * Used for particles that need a sprite from a SpriteSet.
     */
    interface Sprite<T extends ParticleOptions> {
        /**
         * Create a TextureSheetParticle.
         * The engine will call pickSprite() on the result.
         */
        TextureSheetParticle createParticle(T options, Level level, double x, double y, double z,
                                            double xSpeed, double ySpeed, double zSpeed);
    }
}
