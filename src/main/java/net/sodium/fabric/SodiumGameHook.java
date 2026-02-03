package net.sodium.fabric;

import net.sodium.client.checks.ResourcePackScanner;
import net.minecraft.client.Minecraft;
import net.minecraft.hooks.GameHooks;

/**
 * Sodium's implementation of GameHooks.
 * This replaces the mixin-based approach for hooking into game lifecycle events.
 */
public class SodiumGameHook implements GameHooks {
    @Override
    public void onGameInitialized(Minecraft minecraft) {
        // Check for problematic core shader resource packs
        // This was previously done via @Inject mixin in MinecraftMixin.postInit()
        ResourcePackScanner.checkIfCoreShaderLoaded(minecraft.getResourceManager());
    }

    @Override
    public void beforeRunTick(Minecraft minecraft, boolean tick) {
        // GPU synchronization - wait for previous frames to complete
        // This was previously done via @Inject mixin in MinecraftMixin.preRender()
        SodiumGpuSyncHelper.beforeFrameTick();
    }

    @Override
    public void afterRunTick(Minecraft minecraft, boolean tick) {
        // GPU synchronization - create fence for this frame
        // This was previously done via @Inject mixin in MinecraftMixin.postRender()
        SodiumGpuSyncHelper.afterFrameTick();
    }

    @Override
    public void afterResourceReload(Minecraft minecraft) {
        // Check for problematic core shader resource packs after reload
        // This was previously done via @Inject mixin in MinecraftMixin.postResourceReload()
        ResourcePackScanner.checkIfCoreShaderLoaded(minecraft.getResourceManager());
    }
}
