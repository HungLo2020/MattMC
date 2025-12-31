package net.caffeinemc.mods.sodium.fabric;

import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.hooks.GraphicsConfigHooks;

/**
 * Sodium's implementation of GraphicsConfigHooks.
 * This replaces the mixin-based approach for overriding graphics quality settings.
 */
public class SodiumGraphicsConfigHook implements GraphicsConfigHooks {
    @Override
    public Boolean shouldEnableVignette(boolean vanillaFancy) {
        // Override vignette setting with Sodium's option
        // This was previously implemented in features.options.overlays.GuiMixin
        return SodiumClientMod.options().quality.enableVignette;
    }

    @Override
    public Boolean getWeatherQuality(boolean vanillaFancy, GraphicsStatus graphicsMode) {
        // Override weather quality with Sodium's setting
        // This was previously implemented in features.options.weather.LevelRendererMixin
        return SodiumClientMod.options().quality.weatherQuality.isFancy(graphicsMode);
    }

    @Override
    public Boolean getLeavesQuality(boolean vanillaFancy, GraphicsStatus graphicsMode) {
        // Override leaves quality with Sodium's setting
        // This will be used for leaves rendering (currently still using mixins)
        return SodiumClientMod.options().quality.leavesQuality.isFancy(graphicsMode);
    }
}
