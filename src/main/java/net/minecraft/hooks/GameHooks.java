package net.minecraft.hooks;

import net.minecraft.client.Minecraft;

/**
 * Hook interface for game lifecycle events.
 * Mods can implement this interface to hook into various game events without using mixins.
 */
public interface GameHooks {
    /**
     * Called when the game has been initialized and initial screens are being built.
     * This is called after the game has finished loading but before the main menu is shown.
     *
     * @param minecraft The Minecraft instance
     */
    default void onGameInitialized(Minecraft minecraft) {}

    /**
     * Called at the start of each tick/frame, before any rendering or game logic.
     * Replaces @Inject(method = "runTick", at = @At("HEAD"))
     *
     * @param minecraft The Minecraft instance
     * @param tick Whether this is a game tick (true) or just a render frame (false)
     */
    default void beforeRunTick(Minecraft minecraft, boolean tick) {}

    /**
     * Called at the end of each tick/frame, after all rendering and game logic.
     * Replaces @Inject(method = "runTick", at = @At("RETURN"))
     *
     * @param minecraft The Minecraft instance
     * @param tick Whether this was a game tick (true) or just a render frame (false)
     */
    default void afterRunTick(Minecraft minecraft, boolean tick) {}

    /**
     * Called after resource packs are reloaded.
     * Replaces @Inject(method = "reloadResourcePacks()...", at = @At("TAIL"))
     *
     * @param minecraft The Minecraft instance
     */
    default void afterResourceReload(Minecraft minecraft) {}
}
