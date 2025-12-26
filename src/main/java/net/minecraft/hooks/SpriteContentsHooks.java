package net.minecraft.hooks;

import net.minecraft.client.renderer.texture.SpriteContents;

/**
 * Hook interface for sprite contents events.
 * Allows mods to track sprite initialization and manage sprite state.
 */
public interface SpriteContentsHooks {
    /**
     * Called when sprite contents are created/initialized.
     * 
     * @param spriteContents The sprite contents being initialized
     * @param hasAnimation Whether the sprite has animation
     */
    default void onSpriteContentsInit(SpriteContents spriteContents, boolean hasAnimation) {
    }
}
