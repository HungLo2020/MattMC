package net.caffeinemc.mods.sodium.client.util;

import net.minecraft.client.renderer.GameRenderer;

public interface FogStorage {
    FogParameters sodium$getFogParameters();
    
    /**
     * Static helper to get fog parameters from GameRenderer.
     * Uses hook instead of mixin casting.
     */
    static FogParameters getFogParameters(GameRenderer gameRenderer) {
        return net.caffeinemc.mods.sodium.fabric.SodiumFogRenderHook.getFogParameters();
    }
}
