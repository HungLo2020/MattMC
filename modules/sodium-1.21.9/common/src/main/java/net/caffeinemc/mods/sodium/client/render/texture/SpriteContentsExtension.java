package net.caffeinemc.mods.sodium.client.render.texture;

import net.caffeinemc.mods.sodium.client.hooks.SodiumSpriteContentsHook;
import net.minecraft.client.renderer.texture.SpriteContents;

public interface SpriteContentsExtension {
    void sodium$setActive(boolean value);
    boolean sodium$isActive();

    boolean sodium$hasAnimation();
    
    static boolean hasAnimation(SpriteContents spriteContents) {
        return SodiumSpriteContentsHook.hasAnimation(spriteContents);
    }
    
    static boolean isActive(SpriteContents spriteContents) {
        return SodiumSpriteContentsHook.isActive(spriteContents);
    }
    
    static void setActive(SpriteContents spriteContents, boolean active) {
        SodiumSpriteContentsHook.setActive(spriteContents, active);
    }
}
