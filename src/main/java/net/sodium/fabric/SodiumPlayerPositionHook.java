package net.sodium.fabric;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.hooks.PlayerPositionHooks;

/**
 * Sodium implementation of PlayerPositionHooks.
 * Fixes chunk loading by using eye position instead of feet position.
 */
public class SodiumPlayerPositionHook implements PlayerPositionHooks {
    @Override
    public BlockPos getPlayerBlockPositionForChunkLoading(LocalPlayer player, BlockPos defaultPosition) {
        // Use eye position instead of feet position for chunk loading
        // This solves a problem where the loading screen can become stuck waiting for the chunk
        // at the player's feet to load, when it is determined to not be visible due to the true 
        // location of the player's eyes.
        return BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
    }
}
