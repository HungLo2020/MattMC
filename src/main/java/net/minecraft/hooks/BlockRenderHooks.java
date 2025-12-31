package net.minecraft.hooks;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Hook interface for block rendering customizations.
 * Allows mods to override block rendering behavior without mixins.
 */
public interface BlockRenderHooks {
    /**
     * Called to determine if rendering should be skipped between two block states.
     * 
     * @param state The current block state
     * @param stateFrom The adjacent block state
     * @param direction The direction from the current block to the adjacent block
     * @param defaultResult The vanilla skip rendering result
     * @return Boolean override (null to use vanilla behavior, true/false to override)
     */
    default Boolean shouldSkipRendering(BlockState state, BlockState stateFrom, Direction direction, boolean defaultResult) {
        return null;
    }
}
