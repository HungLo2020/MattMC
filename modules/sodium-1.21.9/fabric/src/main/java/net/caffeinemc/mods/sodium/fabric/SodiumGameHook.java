package net.caffeinemc.mods.sodium.fabric;

import net.caffeinemc.mods.sodium.client.checks.ResourcePackScanner;
import net.minecraft.client.Minecraft;
import net.minecraft.hooks.GameHooks;

/**
 * Sodium's implementation of GameHooks.
 * This replaces the mixin-based approach for hooking into game initialization.
 */
public class SodiumGameHook implements GameHooks {
    @Override
    public void onGameInitialized(Minecraft minecraft) {
        // Check for problematic core shader resource packs
        // This was previously done via @Inject mixin in MinecraftMixin.postInit()
        ResourcePackScanner.checkIfCoreShaderLoaded(minecraft.getResourceManager());
    }
}
