package net.minecraft.hooks;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

/**
 * Hook interface for TextureAtlas sprite retrieval events.
 * Allows mods to track sprite usage.
 */
public interface TextureAtlasHooks {
    /**
     * Called after a sprite is retrieved from the texture atlas.
     * 
     * @param location The resource location of the sprite
     * @param sprite The sprite that was returned (may be null or missing sprite)
     */
    default void onSpriteRetrieved(ResourceLocation location, TextureAtlasSprite sprite) {}
}
