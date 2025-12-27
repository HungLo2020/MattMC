package net.minecraft.hooks;

import net.minecraft.client.multiplayer.ClientLevel;
import org.jetbrains.annotations.Nullable;

/**
 * Hook interface for Minecraft client level management.
 * Allows mods to react to level load/unload events.
 */
public interface MinecraftLevelHooks {
    /**
     * Called when a client level is being updated in engines.
     * This fires when the level is changing (including to null).
     * 
     * @param newLevel The new level (may be null)
     */
    void onLevelUpdateInEngines(@Nullable ClientLevel newLevel);
}
