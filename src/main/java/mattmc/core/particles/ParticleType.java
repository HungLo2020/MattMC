package mattmc.core.particles;

import mattmc.util.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Base class for particle types.
 * 
 * <p>This class represents a registered particle type, similar to Minecraft's ParticleType.
 * Each particle type can have associated options and is identified by a ResourceLocation.
 * 
 * @param <T> the type of particle options this particle type uses
 */
public abstract class ParticleType<T extends ParticleOptions> {
    private final boolean overrideLimiter;
    
    /**
     * Create a new particle type.
     * 
     * @param overrideLimiter if true, this particle type ignores the particle limiter setting
     */
    protected ParticleType(boolean overrideLimiter) {
        this.overrideLimiter = overrideLimiter;
    }
    
    /**
     * Whether this particle type overrides the particle limiter.
     * 
     * <p>Some particles (like explosions) should always show even when
     * particle settings are reduced.
     */
    public boolean getOverrideLimiter() {
        return overrideLimiter;
    }
}
