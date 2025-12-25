package net.minecraft.hooks;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * Hook interface for customizing TextureAtlasSprite behavior.
 * Allows mods to override UV shrink ratio calculation.
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
}
