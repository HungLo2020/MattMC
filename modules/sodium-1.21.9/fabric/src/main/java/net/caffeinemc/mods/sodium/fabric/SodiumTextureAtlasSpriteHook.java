package net.caffeinemc.mods.sodium.fabric;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.hooks.TextureAtlasSpriteHooks;
import net.sodium.api.texture.SpriteUtil;

/**
 * Sodium implementation of TextureAtlasSpriteHooks.
 * Overrides UV shrink ratio to return 0.0f for optimized terrain rendering and tracks sprite wrapping.
 */
public class SodiumTextureAtlasSpriteHook implements TextureAtlasSpriteHooks {
    @Override
    public Float overrideUvShrinkRatio(TextureAtlasSprite sprite, float defaultRatio) {
        // Vanilla tries to apply a bias to texture coordinates to avoid texture bleeding (see FaceBakery#bakeQuad).
        // This is counterproductive with Sodium's terrain rendering, since the bias is applied in the shader instead.
        // Override this method instead of adjusting its return value in FaceBakery as other mods may use it to
        // manually apply the bias.
        return 0.0f;
    }

    @Override
    public void onSpriteWrap(TextureAtlasSprite sprite, VertexConsumer vertexConsumer) {
        // Mark sprite as active when it wraps a vertex consumer
        SpriteUtil.INSTANCE.markSpriteActive(sprite);
    }
}
