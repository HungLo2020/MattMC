package net.caffeinemc.mods.sodium.client.render.viewport;

import net.caffeinemc.mods.sodium.client.hooks.SodiumFrustumHook;
import net.minecraft.client.renderer.culling.Frustum;

public interface ViewportProvider {
    Viewport sodium$createViewport();
    
    /**
     * Static helper to create viewport from Frustum.
     * Uses hook instead of mixin casting.
     */
    static Viewport createViewport(Frustum frustum) {
        return SodiumFrustumHook.createViewport(frustum);
    }
}
