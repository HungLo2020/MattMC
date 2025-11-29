package mattmc.core.particles;

/**
 * A simple particle type that carries no additional data.
 * 
 * <p>This class both extends ParticleType and implements ParticleOptions,
 * allowing it to act as both the type and the options for simple particles.
 * This is the most common case for particles like smoke, flame, etc.
 * 
 * <p>Mirrors Minecraft's SimpleParticleType.
 */
public class SimpleParticleType extends ParticleType<SimpleParticleType> implements ParticleOptions {
    
    /**
     * Create a new simple particle type.
     * 
     * @param overrideLimiter if true, this particle ignores the particle limiter
     */
    public SimpleParticleType(boolean overrideLimiter) {
        super(overrideLimiter);
    }
    
    @Override
    public ParticleType<?> getType() {
        return this;
    }
    
    @Override
    public String writeToString() {
        // Will be overridden when registered to include the registry key
        return this.getClass().getSimpleName();
    }
}
