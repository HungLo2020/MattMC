package net.caffeinemc.mods.sodium.client.hooks;

import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.hooks.SpriteContentsHooks;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Sodium's implementation of SpriteContentsHooks.
 * Tracks sprite active state for optimization purposes.
 */
public class SodiumSpriteContentsHook implements SpriteContentsHooks {
    private static final SodiumSpriteContentsHook INSTANCE = new SodiumSpriteContentsHook();
    
    // Use WeakHashMap to avoid memory leaks - only tracks active state
    private final Map<SpriteContents, Boolean> isActive = new WeakHashMap<>();

    private SodiumSpriteContentsHook() {
    }

    public static SodiumSpriteContentsHook getInstance() {
        return INSTANCE;
    }

    /**
     * Check if sprite contents has animation.
     * Used by SpriteContentsExtension interface.
     */
    public static boolean hasAnimation(SpriteContents spriteContents) {
        // Directly check if the sprite has an animated texture (public field)
        return spriteContents.animatedTexture != null;
    }

    /**
     * Check if sprite is active.
     * Used by SpriteContentsExtension interface.
     */
    public static boolean isActive(SpriteContents spriteContents) {
        Boolean result = INSTANCE.isActive.get(spriteContents);
        return result != null && result;
    }

    /**
     * Set sprite active state.
     * Used by SpriteContentsExtension interface.
     */
    public static void setActive(SpriteContents spriteContents, boolean active) {
        INSTANCE.isActive.put(spriteContents, active);
    }
}
