package net.minecraft.hooks;

import net.minecraft.client.color.block.BlockColor;
import net.minecraft.world.level.block.Block;

/**
 * Hook interface for tracking block color provider registrations.
 * Allows mods to track which blocks have color providers registered
 * and which vanilla color providers have been overridden.
 */
public interface BlockColorHooks {
    /**
     * Called when a block color provider is being registered.
     * 
     * @param provider The color provider being registered
     * @param block The block to register the provider for
     * @param isReplacement true if this replaces an existing provider, false if it's the first registration
     */
    default void onBlockColorRegistered(BlockColor provider, Block block, boolean isReplacement) {
    }
}
