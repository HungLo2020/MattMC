package net.sodium.fabric;

import net.sodium.client.SodiumClientMod;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.hooks.BlockRenderHooks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Sodium implementation of BlockRenderHooks.
 * Provides custom block rendering behavior for Sodium's quality settings.
 */
public class SodiumBlockRenderHook implements BlockRenderHooks {
    @Override
    public Boolean shouldSkipRendering(BlockState state, BlockState stateFrom, Direction direction, boolean defaultResult) {
        // Only override for leaves blocks
        if (!(state.getBlock() instanceof LeavesBlock)) {
            return null; // Use vanilla behavior for non-leaves
        }
        
        // Check Sodium's leaves quality setting
        boolean useFancyLeaves = SodiumClientMod.options().quality.leavesQuality.isFancy(
            Minecraft.getInstance().options.graphicsMode().get()
        );
        
        if (useFancyLeaves) {
            // Fancy mode: use vanilla behavior
            return null;
        } else {
            // Fast mode: skip rendering if adjacent block is also leaves
            if (stateFrom.getBlock() instanceof LeavesBlock) {
                return true;
            }
            return null; // Otherwise use vanilla behavior
        }
    }
}
