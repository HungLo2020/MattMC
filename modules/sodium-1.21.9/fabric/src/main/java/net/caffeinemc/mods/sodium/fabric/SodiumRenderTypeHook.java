package net.caffeinemc.mods.sodium.fabric;

import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.hooks.RenderTypeHooks;

/**
 * Sodium implementation of RenderTypeHooks.
 * Provides custom render type behavior based on Sodium's quality settings.
 */
public class SodiumRenderTypeHook implements RenderTypeHooks {
    private static boolean leavesCutout = false;
    
    @Override
    public void onSetFancyGraphics(boolean fancyGraphicsOrBetter) {
        // Update leaves cutout setting based on Sodium's configuration
        GraphicsStatus mode = fancyGraphicsOrBetter ? GraphicsStatus.FANCY : GraphicsStatus.FAST;
        leavesCutout = SodiumClientMod.options().quality.leavesQuality.isFancy(mode);
    }
    
    @Override
    public Boolean shouldUseCutoutRendering(boolean vanillaCutout) {
        // Override vanilla cutout with Sodium's leaves setting
        return leavesCutout;
    }
}
