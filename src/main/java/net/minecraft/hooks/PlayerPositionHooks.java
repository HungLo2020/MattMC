package net.minecraft.hooks;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

/**
 * Hook interface for player position customizations.
 * Allows mods to override player position calculations.
 */
public interface PlayerPositionHooks {
    /**
     * Called to get the player's block position for chunk loading purposes.
     * 
     * @param player The local player
     * @param defaultPosition The vanilla block position (feet position)
     * @return BlockPos override (null to use vanilla behavior, non-null to override)
     */
    default BlockPos getPlayerBlockPositionForChunkLoading(LocalPlayer player, BlockPos defaultPosition) {
        return null;
    }
}
