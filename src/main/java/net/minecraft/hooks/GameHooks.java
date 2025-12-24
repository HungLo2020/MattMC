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
    void onGameInitialized(Minecraft minecraft);
}
