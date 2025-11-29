package mattmc.client.particle;

import mattmc.util.ResourceLocation;

/**
 * Interface for accessing the particle texture atlas.
 * 
 * <p>This abstraction allows the particle system to work with
 * different backend implementations of the particle atlas.
 */
public interface ParticleAtlas {
    
    /**
     * Get a sprite from the atlas by resource location.
     * 
     * @param location the texture resource location (e.g., "mattmc:flame")
     * @return the sprite, or null if not found
     */
    ParticleSprite getSprite(ResourceLocation location);
    
    /**
     * Get the missing/placeholder sprite.
     * Used when a requested texture is not found.
     * 
     * @return the missing sprite
     */
    ParticleSprite getMissingSprite();
    
    /**
     * Get the OpenGL texture ID for the particle atlas.
     * Backend-specific; used by OpenGL renderer.
     * 
     * @return the texture ID
     */
    int getAtlasTextureId();
    
    /**
     * Bind the particle atlas texture for rendering.
     */
    void bind();
}
