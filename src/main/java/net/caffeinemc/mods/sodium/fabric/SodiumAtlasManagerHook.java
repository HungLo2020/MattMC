package net.caffeinemc.mods.sodium.fabric;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.hooks.AtlasManagerHooks;
import net.sodium.api.texture.SpriteUtil;

/**
 * Sodium implementation of AtlasManagerHooks.
 * Marks sprites as active when retrieved from the atlas manager.
 */
public class SodiumAtlasManagerHook implements AtlasManagerHooks {
    @Override
    public void onSpriteRetrieved(Material material, TextureAtlasSprite sprite) {
        // This is used to catch the fire sprite when an entity is on fire and there's no fire blocks in the scene
        if (sprite != null) {
            SpriteUtil.INSTANCE.markSpriteActive(sprite);
        }
    }
}
