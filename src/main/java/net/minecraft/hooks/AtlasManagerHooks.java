package net.minecraft.hooks;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;

/**
 * Hook interface for AtlasManager sprite retrieval events.
 * Allows mods to track sprite usage.
 */
public interface AtlasManagerHooks {
    /**
     * Called after a sprite is retrieved from the atlas manager.
     * 
     * @param material The material requested
     * @param sprite The sprite that was returned (may be null)
     */
    default void onSpriteRetrieved(Material material, TextureAtlasSprite sprite) {}
}
