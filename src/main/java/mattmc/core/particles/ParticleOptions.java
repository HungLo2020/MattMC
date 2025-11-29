package mattmc.core.particles;

/**
 * Interface for particle data/options.
 * 
 * <p>Particle options carry additional data for a particle beyond just its type.
 * For simple particles that don't need extra data, use {@link SimpleParticleType}.
 * For particles that need data (like dust color, block state, etc.), implement this interface.
 * 
 * <p>This mirrors Minecraft's ParticleOptions interface structure.
 */
public interface ParticleOptions {
    /**
     * Get the particle type this options object belongs to.
     * 
     * @return the particle type
     */
    ParticleType<?> getType();
    
    /**
     * Convert this options to a string representation.
     * Used for commands and debugging.
     * 
     * @return string representation of the particle options
     */
    String writeToString();
}
