package net.caffeinemc.mods.sodium.client.hooks;

import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.hooks.SpriteContentsHooks;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Sodium's implementation of SpriteContentsHooks.
 * Tracks sprite animation state for optimization purposes.
 */
public class SodiumSpriteContentsHook implements SpriteContentsHooks {
    private static final SodiumSpriteContentsHook INSTANCE = new SodiumSpriteContentsHook();
    
    // Use WeakHashMap to avoid memory leaks
    private final Map<SpriteContents, Boolean> hasAnimation = new WeakHashMap<>();
    private final Map<SpriteContents, Boolean> isActive = new WeakHashMap<>();

    private SodiumSpriteContentsHook() {
    }

    public static SodiumSpriteContentsHook getInstance() {
        return INSTANCE;
    }

    @Override
    public void onSpriteContentsInit(SpriteContents spriteContents, boolean hasAnimation) {
        this.hasAnimation.put(spriteContents, hasAnimation);
        this.isActive.put(spriteContents, false); // Start inactive
    }

    /**
     * Check if sprite contents has animation.
     * Used by SpriteContentsExtension interface.
     */
    public static boolean hasAnimation(SpriteContents spriteContents) {
        Boolean result = INSTANCE.hasAnimation.get(spriteContents);
        return result != null && result;
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
