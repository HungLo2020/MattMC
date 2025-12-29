package net.minecraft.hooks;

import net.minecraft.client.GraphicsStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Hook interface for graphics and rendering quality configuration.
 * Mods can implement this to override graphics quality settings without using mixins.
 */
public interface GraphicsConfigHooks {
    /**
     * Called to determine if fancy graphics should be used instead of the vanilla setting.
     * Return null to use vanilla behavior, or a Boolean to override.
     *
     * @param vanillaFancy The vanilla fancy graphics setting
     * @return null for vanilla behavior, true/false to override
     */
    @Nullable
    default Boolean shouldUseFancyGraphics(boolean vanillaFancy) {
        return null;
    }

    /**
     * Called to determine weather quality setting.
     * Return null to use vanilla behavior, or a Boolean to override.
     *
     * @param vanillaFancy The vanilla fancy graphics setting
     * @param graphicsMode The current graphics mode
     * @return null for vanilla behavior, true/false to override
     */
    @Nullable
    default Boolean getWeatherQuality(boolean vanillaFancy, GraphicsStatus graphicsMode) {
        return null;
    }

    /**
     * Called to determine leaves quality setting.
     * Return null to use vanilla behavior, or a Boolean to override.
     *
     * @param vanillaFancy The vanilla fancy graphics setting  
     * @param graphicsMode The current graphics mode
     * @return null for vanilla behavior, true/false to override
     */
    @Nullable
    default Boolean getLeavesQuality(boolean vanillaFancy, GraphicsStatus graphicsMode) {
        return null;
    }

    /**
     * Called to determine if vignette effect should be enabled.
     * Return null to use vanilla behavior, or a Boolean to override.
     *
     * @param vanillaFancy The vanilla fancy graphics setting
     * @return null for vanilla behavior, true/false to override
     */
    @Nullable
    default Boolean shouldEnableVignette(boolean vanillaFancy) {
        return null;
    }
}
