package net.caffeinemc.mods.sodium.fabric;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.hooks.TextureAtlasHooks;
import net.minecraft.resources.ResourceLocation;
import net.sodium.api.texture.SpriteUtil;

/**
 * Sodium implementation of TextureAtlasHooks.
 * Marks sprites as active when retrieved from the texture atlas.
 */
public class SodiumTextureAtlasHook implements TextureAtlasHooks {
    @Override
    public void onSpriteRetrieved(ResourceLocation location, TextureAtlasSprite sprite) {
        if (sprite != null) {
            SpriteUtil.INSTANCE.markSpriteActive(sprite);
        }
    }
}
