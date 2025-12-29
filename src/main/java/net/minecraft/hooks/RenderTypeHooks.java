package net.minecraft.hooks;

import org.jetbrains.annotations.Nullable;

/**
 * Hook interface for render type customizations.
 * Allows mods to override render type behavior (e.g., leaves cutout rendering).
 */
public interface RenderTypeHooks {
    /**
     * Called when fancy graphics mode is set.
     * Allows mods to customize rendering based on graphics mode.
     * 
     * @param fancyGraphicsOrBetter Whether fancy graphics or better is enabled
     */
    default void onSetFancyGraphics(boolean fancyGraphicsOrBetter) {}
    
    /**
     * Called to determine if leaves should use cutout rendering.
     * 
     * @param vanillaCutout The vanilla cutout state
     * @return Boolean override (null to use vanilla behavior, true/false to override)
     */
    @Nullable
    default Boolean shouldUseCutoutRendering(boolean vanillaCutout) {
        return null;
    }
}
