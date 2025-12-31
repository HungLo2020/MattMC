package net.caffeinemc.mods.sodium.fabric;

import net.minecraft.hooks.RenderHooks;

/**
 * Sodium's implementation of RenderHooks.
 * This replaces the mixin-based workarounds for rendering system issues.
 */
public class SodiumRenderHook implements RenderHooks {
    @Override
    public boolean shouldSkipFirstPollEvents() {
        // Skip the first pollEvents() call in flipFrame to fix a bug where Minecraft polls events twice
        // This workaround was previously implemented in workarounds.event_loop.RenderSystemMixin
        return true;
    }
}
