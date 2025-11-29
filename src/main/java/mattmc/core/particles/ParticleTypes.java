package mattmc.core.particles;

import mattmc.util.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Registry for particle types.
 * 
 * <p>All particle types must be registered here before they can be used.
 * This mirrors Minecraft's BuiltInRegistries.PARTICLE_TYPE.
 */
public final class ParticleTypes {
    private static final Logger logger = LoggerFactory.getLogger(ParticleTypes.class);
    
    private static final Map<ResourceLocation, ParticleType<?>> REGISTRY = new HashMap<>();
    private static final Map<ParticleType<?>, ResourceLocation> REVERSE_REGISTRY = new HashMap<>();
    
    // Built-in particle types
    public static final SimpleParticleType SMOKE = register("smoke", new SimpleParticleType(false));
    public static final SimpleParticleType FLAME = register("flame", new SimpleParticleType(false));
    public static final SimpleParticleType POOF = register("poof", new SimpleParticleType(true));
    public static final SimpleParticleType EXPLOSION = register("explosion", new SimpleParticleType(true));
    public static final SimpleParticleType HEART = register("heart", new SimpleParticleType(false));
    public static final SimpleParticleType CRIT = register("crit", new SimpleParticleType(false));
    public static final SimpleParticleType BLOCK = register("block", new SimpleParticleType(false));
    public static final SimpleParticleType CHERRY_LEAVES = register("cherry_leaves", new SimpleParticleType(false));
    public static final SimpleParticleType FALLING_LEAVES = register("falling_leaves", new SimpleParticleType(false));
    
    private ParticleTypes() {} // Prevent instantiation
    
    /**
     * Register a particle type with a resource location.
     * 
     * @param path the path part of the resource location (namespace defaults to "mattmc")
     * @param type the particle type to register
     * @param <T> the particle type class
     * @return the registered particle type
     */
    public static <T extends ParticleType<?>> T register(String path, T type) {
        return register(new ResourceLocation(path), type);
    }
    
    /**
     * Register a particle type with a full resource location.
     * 
     * @param key the resource location key
     * @param type the particle type to register
     * @param <T> the particle type class
     * @return the registered particle type
     */
    public static <T extends ParticleType<?>> T register(ResourceLocation key, T type) {
        if (REGISTRY.containsKey(key)) {
            throw new IllegalArgumentException("Particle type already registered: " + key);
        }
        REGISTRY.put(key, type);
        REVERSE_REGISTRY.put(type, key);
        logger.debug("Registered particle type: {}", key);
        return type;
    }
    
    /**
     * Get a particle type by its resource location.
     * 
     * @param key the resource location
     * @return the particle type, or null if not found
     */
    public static ParticleType<?> get(ResourceLocation key) {
        return REGISTRY.get(key);
    }
    
    /**
     * Get the resource location for a particle type.
     * 
     * @param type the particle type
     * @return the resource location, or null if not registered
     */
    public static ResourceLocation getKey(ParticleType<?> type) {
        return REVERSE_REGISTRY.get(type);
    }
    
    /**
     * Get all registered particle type keys.
     */
    public static Set<ResourceLocation> getRegisteredKeys() {
        return REGISTRY.keySet();
    }
    
    /**
     * Check if a particle type is registered.
     */
    public static boolean contains(ResourceLocation key) {
        return REGISTRY.containsKey(key);
    }
}
