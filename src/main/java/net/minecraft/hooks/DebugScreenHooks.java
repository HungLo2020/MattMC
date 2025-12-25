package net.minecraft.hooks;

import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Hook interface for debug screen extensions.
 * Allows mods to add custom debug information to the F3 debug screen.
 */
public interface DebugScreenHooks {
    /**
     * Called when the debug memory entry is being displayed.
     * Allows mods to add additional memory-related debug information.
     * 
     * @param debugScreenDisplayer The debug screen displayer to add entries to
     * @param level The current level
     * @param levelChunk The level chunk at camera position
     * @param levelChunk2 The level chunk at target position
     */
    default void onDebugMemoryDisplay(DebugScreenDisplayer debugScreenDisplayer, Level level, 
                                     LevelChunk levelChunk, LevelChunk levelChunk2) {}
}
