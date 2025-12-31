package net.minecraft.hooks;

import net.minecraft.client.renderer.state.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.joml.Quaternionf;

/**
 * Hook interface for particle rendering events.
 * Allows mods to track particle sprite usage.
 */
public interface ParticleRenderHooks {
    /**
     * Called after a sprite is set on a particle.
     * 
     * @param particle The particle object
     * @param sprite The sprite that was set
     */
    default void onParticleSpriteSet(Object particle, TextureAtlasSprite sprite) {}
    
    /**
     * Called before a particle quad is extracted/rendered.
     * 
     * @param particle The particle object
     * @param sprite The sprite being used
     * @param quadParticleRenderState The render state
     * @param quaternionf The rotation quaternion
     */
    default void onParticleQuadExtract(Object particle, TextureAtlasSprite sprite, QuadParticleRenderState quadParticleRenderState, Quaternionf quaternionf) {}
}
