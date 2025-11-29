package mattmc.client.particle;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import mattmc.core.particles.ParticleOptions;
import mattmc.core.particles.ParticleType;
import mattmc.core.particles.ParticleTypes;
import mattmc.util.ResourceLocation;
import mattmc.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Central manager for particles.
 * 
 * <p>The ParticleEngine is responsible for:
 * <ul>
 *   <li>Managing particle providers (factories)</li>
 *   <li>Creating particles from ParticleOptions</li>
 *   <li>Ticking all active particles</li>
 *   <li>Rendering particles grouped by render type</li>
 *   <li>Loading particle definitions from JSON</li>
 * </ul>
 * 
 * <p>Mirrors Minecraft's ParticleEngine.
 */
public class ParticleEngine {
    private static final Logger logger = LoggerFactory.getLogger(ParticleEngine.class);
    private static final Gson GSON = new GsonBuilder().create();
    private static final int MAX_PARTICLES_PER_LAYER = 16384;
    
    /** The level this particle engine belongs to. */
    private Level level;
    
    /** Particles grouped by render type for efficient batched rendering. */
    private final Map<ParticleRenderType, Queue<Particle>> particles = new EnumMap<>(ParticleRenderType.class);
    
    /** Pending particles to be added (thread-safe queue). */
    private final Queue<Particle> particlesToAdd = new ArrayDeque<>();
    
    /** Registered particle providers, keyed by particle type resource location. */
    private final Map<ResourceLocation, ParticleProvider<?>> providers = new HashMap<>();
    
    /** Sprite sets for particles, keyed by particle type resource location. */
    private final Map<ResourceLocation, MutableSpriteSet> spriteSets = new HashMap<>();
    
    /** Particle atlas for texture lookups. */
    private ParticleAtlas particleAtlas;
    
    /**
     * Create a new particle engine.
     * 
     * @param level the level to spawn particles in
     */
    public ParticleEngine(Level level) {
        this.level = level;
        
        // Initialize particle queues for each render type
        for (ParticleRenderType type : ParticleRenderType.values()) {
            particles.put(type, new ArrayDeque<>());
        }
    }
    
    /**
     * Set the level for this particle engine.
     */
    public void setLevel(Level level) {
        this.level = level;
        clearParticles();
    }
    
    /**
     * Set the particle atlas for texture lookups.
     */
    public void setParticleAtlas(ParticleAtlas atlas) {
        this.particleAtlas = atlas;
    }
    
    /**
     * Get the particle atlas.
     */
    public ParticleAtlas getParticleAtlas() {
        return particleAtlas;
    }
    
    /**
     * Register a particle provider for a particle type.
     * 
     * @param type the particle type
     * @param provider the provider factory
     */
    public <T extends ParticleOptions> void register(ParticleType<T> type, ParticleProvider<T> provider) {
        ResourceLocation key = ParticleTypes.getKey(type);
        if (key != null) {
            providers.put(key, provider);
            logger.debug("Registered particle provider for: {}", key);
        } else {
            logger.warn("Cannot register provider for unregistered particle type: {}", type);
        }
    }
    
    /**
     * Register a particle provider that uses sprites.
     * Creates a sprite set for the particle type.
     */
    public <T extends ParticleOptions> void register(ParticleType<T> type, 
            java.util.function.Function<SpriteSet, ParticleProvider<T>> providerFactory) {
        ResourceLocation key = ParticleTypes.getKey(type);
        if (key != null) {
            MutableSpriteSet spriteSet = new MutableSpriteSet();
            spriteSets.put(key, spriteSet);
            providers.put(key, providerFactory.apply(spriteSet));
            logger.debug("Registered sprite-based particle provider for: {}", key);
        } else {
            logger.warn("Cannot register provider for unregistered particle type: {}", type);
        }
    }
    
    /**
     * Load particle definitions from JSON files and bind sprites.
     * Call this after the particle atlas is loaded.
     */
    public void loadParticleDefinitions() {
        if (particleAtlas == null) {
            logger.warn("Cannot load particle definitions: particle atlas not set");
            return;
        }
        
        for (ResourceLocation key : spriteSets.keySet()) {
            loadParticleDefinition(key);
        }
    }
    
    /**
     * Load a single particle definition and bind its sprites.
     */
    private void loadParticleDefinition(ResourceLocation particleId) {
        String jsonPath = "/assets/" + particleId.getNamespace() + "/particles/" + particleId.getPath() + ".json";
        
        try (InputStream is = getClass().getResourceAsStream(jsonPath)) {
            if (is == null) {
                logger.debug("No particle definition found at: {}", jsonPath);
                return;
            }
            
            JsonObject json = GSON.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), JsonObject.class);
            ParticleDescription description = ParticleDescription.fromJson(json);
            
            // Convert texture resource locations to sprites
            List<ParticleSprite> sprites = new ArrayList<>();
            for (ResourceLocation textureLocation : description.getTextures()) {
                ParticleSprite sprite = particleAtlas.getSprite(textureLocation);
                if (sprite != null) {
                    sprites.add(sprite);
                } else {
                    logger.warn("Missing particle sprite: {} for particle: {}", textureLocation, particleId);
                    // Add missing texture sprite as fallback
                    sprites.add(particleAtlas.getMissingSprite());
                }
            }
            
            // Bind sprites to the sprite set
            MutableSpriteSet spriteSet = spriteSets.get(particleId);
            if (spriteSet != null && !sprites.isEmpty()) {
                spriteSet.rebind(sprites);
                logger.debug("Loaded {} sprites for particle: {}", sprites.size(), particleId);
            }
            
        } catch (Exception e) {
            logger.error("Failed to load particle definition: {}", particleId, e);
        }
    }
    
    /**
     * Create and add a particle.
     * 
     * @param options the particle options
     * @param x spawn X position
     * @param y spawn Y position
     * @param z spawn Z position
     * @param xSpeed initial X velocity
     * @param ySpeed initial Y velocity
     * @param zSpeed initial Z velocity
     * @return the created particle, or null if creation failed
     */
    @SuppressWarnings("unchecked")
    public <T extends ParticleOptions> Particle createParticle(T options, double x, double y, double z,
                                                                double xSpeed, double ySpeed, double zSpeed) {
        if (level == null) {
            return null;
        }
        
        ResourceLocation key = ParticleTypes.getKey(options.getType());
        if (key == null) {
            logger.warn("Unknown particle type: {}", options.getType());
            return null;
        }
        
        ParticleProvider<T> provider = (ParticleProvider<T>) providers.get(key);
        if (provider == null) {
            logger.debug("No provider registered for particle type: {}", key);
            return null;
        }
        
        Particle particle = provider.createParticle(options, level, x, y, z, xSpeed, ySpeed, zSpeed);
        if (particle != null) {
            add(particle);
        }
        return particle;
    }
    
    /**
     * Add a particle directly.
     */
    public void add(Particle particle) {
        particlesToAdd.add(particle);
    }
    
    /**
     * Tick all particles.
     */
    public void tick() {
        // Add pending particles
        Particle particle;
        while ((particle = particlesToAdd.poll()) != null) {
            Queue<Particle> queue = particles.get(particle.getRenderType());
            if (queue.size() < MAX_PARTICLES_PER_LAYER) {
                queue.add(particle);
            }
        }
        
        // Tick each particle queue
        for (Queue<Particle> queue : particles.values()) {
            tickParticleList(queue);
        }
    }
    
    private void tickParticleList(Queue<Particle> queue) {
        Iterator<Particle> iter = queue.iterator();
        while (iter.hasNext()) {
            Particle particle = iter.next();
            try {
                particle.tick();
            } catch (Exception e) {
                logger.error("Error ticking particle: {}", particle, e);
                particle.remove();
            }
            
            if (!particle.isAlive()) {
                iter.remove();
            }
        }
    }
    
    /**
     * Render all particles.
     * 
     * @param builder the vertex builder for rendering
     * @param cameraX camera X position
     * @param cameraY camera Y position
     * @param cameraZ camera Z position
     * @param partialTicks interpolation factor
     * @param renderTypeConsumer callback to set up render state for each type
     */
    public void render(ParticleVertexBuilder builder, double cameraX, double cameraY, double cameraZ,
                       float partialTicks, java.util.function.Consumer<ParticleRenderType> renderTypeConsumer) {
        // Render in order: terrain, opaque, lit, translucent, custom
        for (ParticleRenderType type : new ParticleRenderType[] {
                ParticleRenderType.TERRAIN_SHEET,
                ParticleRenderType.PARTICLE_SHEET_OPAQUE,
                ParticleRenderType.PARTICLE_SHEET_LIT,
                ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT,
                ParticleRenderType.CUSTOM
        }) {
            Queue<Particle> queue = particles.get(type);
            if (queue == null || queue.isEmpty()) {
                continue;
            }
            
            if (type == ParticleRenderType.NO_RENDER) {
                continue;
            }
            
            // Let the consumer set up render state
            renderTypeConsumer.accept(type);
            
            // Begin vertex building for this render type
            builder.begin();
            
            // Render all particles of this type
            for (Particle particle : queue) {
                try {
                    particle.render(builder, cameraX, cameraY, cameraZ, partialTicks);
                } catch (Exception e) {
                    logger.error("Error rendering particle: {}", particle, e);
                }
            }
            
            // End and flush vertices
            builder.end();
        }
    }
    
    /**
     * Get all particles for a specific render type.
     */
    public Iterable<Particle> getParticles(ParticleRenderType type) {
        return particles.getOrDefault(type, new ArrayDeque<>());
    }
    
    /**
     * Get the total number of active particles.
     */
    public int countParticles() {
        int count = 0;
        for (Queue<Particle> queue : particles.values()) {
            count += queue.size();
        }
        return count;
    }
    
    /**
     * Get a formatted string of the particle count.
     */
    public String countParticlesString() {
        return String.valueOf(countParticles());
    }
    
    /**
     * Clear all particles.
     */
    public void clearParticles() {
        for (Queue<Particle> queue : particles.values()) {
            queue.clear();
        }
        particlesToAdd.clear();
    }
    
    /**
     * Get the sprite set for a particle type.
     * 
     * @param particleId the resource location of the particle type
     * @return the sprite set, or null if not found
     */
    public SpriteSet getSpriteSet(ResourceLocation particleId) {
        return spriteSets.get(particleId);
    }
    
    /**
     * Mutable sprite set that can be rebound when resources are reloaded.
     */
    public static class MutableSpriteSet implements SpriteSet {
        private List<ParticleSprite> sprites = new ArrayList<>();
        
        @Override
        public ParticleSprite get(int age, int lifetime) {
            if (sprites.isEmpty()) return null;
            int index = age * (sprites.size() - 1) / Math.max(1, lifetime);
            return sprites.get(Math.min(index, sprites.size() - 1));
        }
        
        @Override
        public ParticleSprite get(Random random) {
            if (sprites.isEmpty()) return null;
            return sprites.get(random.nextInt(sprites.size()));
        }
        
        /**
         * Rebind this sprite set with new sprites.
         */
        public void rebind(List<ParticleSprite> newSprites) {
            this.sprites = new ArrayList<>(newSprites);
        }
    }
}
