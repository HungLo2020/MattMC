package net.minecraft.hooks;

import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

/**
 * Hook interface for TextureAtlas sprite retrieval and upload events.
 * Allows mods to track sprite usage and atlas uploads.
 */
public interface TextureAtlasHooks {
    /**
     * Called after a sprite is retrieved from the texture atlas.
     * 
     * @param location The resource location of the sprite
     * @param sprite The sprite that was returned (may be null or missing sprite)
     */
    default void onSpriteRetrieved(ResourceLocation location, TextureAtlasSprite sprite) {}

    /**
     * Called after a texture atlas has been uploaded.
     * 
     * @param atlas The texture atlas that was uploaded
     * @param atlasLocation The resource location of the atlas
     * @param preparations The sprite loader preparations
     */
    default void onAtlasUpload(TextureAtlas atlas, ResourceLocation atlasLocation, SpriteLoader.Preparations preparations) {}
}
