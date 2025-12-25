package net.caffeinemc.mods.sodium.fabric;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.hooks.TextureAtlasSpriteHooks;

/**
 * Sodium implementation of TextureAtlasSpriteHooks.
 * Overrides UV shrink ratio to return 0.0f for optimized terrain rendering.
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
}
