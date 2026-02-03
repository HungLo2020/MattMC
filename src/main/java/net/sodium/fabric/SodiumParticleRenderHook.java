package net.sodium.fabric;

import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.hooks.ParticleRenderHooks;
import net.sodium.api.texture.SpriteUtil;
import org.joml.Quaternionf;

/**
 * Sodium implementation of ParticleRenderHooks.
 * Tracks sprite usage for particle animation optimization.
 */
public class SodiumParticleRenderHook implements ParticleRenderHooks {
    @Override
    public void onParticleSpriteSet(Object particle, TextureAtlasSprite sprite) {
        // Track whether this sprite should be ticked based on whether it has animation
        if (particle instanceof SingleQuadParticle && sprite != null) {
            // Store the animation state (this will be used in onParticleQuadExtract)
            // Note: We rely on Sodium's SpriteUtil to determine if sprite has animation
        }
    }

    @Override
    public void onParticleQuadExtract(Object particle, TextureAtlasSprite sprite, QuadParticleRenderState quadParticleRenderState, Quaternionf quaternionf) {
        // Mark sprite as active if it has animation
        if (sprite != null && SpriteUtil.INSTANCE.hasAnimation(sprite)) {
            SpriteUtil.INSTANCE.markSpriteActive(sprite);
        }
    }
}
