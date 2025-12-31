package net.caffeinemc.mods.sodium.fabric;

import net.caffeinemc.mods.sodium.client.render.texture.SpriteFinderCache;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.hooks.TextureAtlasHooks;
import net.minecraft.resources.ResourceLocation;
import net.sodium.api.texture.SpriteUtil;

/**
 * Sodium implementation of TextureAtlasHooks.
 * Marks sprites as active when retrieved from the texture atlas and resets sprite finder on upload.
 */
public class SodiumTextureAtlasHook implements TextureAtlasHooks {
    @Override
    public void onSpriteRetrieved(ResourceLocation location, TextureAtlasSprite sprite) {
        if (sprite != null) {
            SpriteUtil.INSTANCE.markSpriteActive(sprite);
        }
    }

    @Override
    public void onAtlasUpload(TextureAtlas atlas, ResourceLocation atlasLocation, SpriteLoader.Preparations preparations) {
        // Reset sprite finder when the block atlas is uploaded
        if (atlasLocation.equals(TextureAtlas.LOCATION_BLOCKS)) {
            SpriteFinderCache.resetSpriteFinder();
        }
    }
}
