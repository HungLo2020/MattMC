package net.minecraft.hooks;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * Hook interface for customizing TextureAtlasSprite behavior.
 * Allows mods to override UV shrink ratio calculation and track sprite wrap events.
 */
public interface TextureAtlasSpriteHooks {
    /**
     * Provide a custom UV shrink ratio.
     * If this returns a non-null value, it replaces the default calculation.
     *
     * @param sprite The texture atlas sprite
     * @param defaultRatio The default UV shrink ratio that would be calculated
     * @return Custom UV shrink ratio, or null to use default behavior
     */
    default Float overrideUvShrinkRatio(TextureAtlasSprite sprite, float defaultRatio) {
        return null;
    }

    /**
     * Called when a sprite wraps a vertex consumer.
     * Allows mods to track sprite usage.
     *
     * @param sprite The sprite being wrapped
     * @param vertexConsumer The vertex consumer being wrapped
     */
    default void onSpriteWrap(TextureAtlasSprite sprite, VertexConsumer vertexConsumer) {}
}
