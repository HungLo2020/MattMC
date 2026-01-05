package net.minecraft.worldedit.region;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.worldedit.math.BlockVector3;

/**
 * Manages the selection of a region.
 */
public interface RegionSelector {
    /**
     * Select the primary position.
     * @return true if the selection was changed
     */
    boolean selectPrimary(BlockVector3 position);
    
    /**
     * Select the secondary position.
     * @return true if the selection was changed
     */
    boolean selectSecondary(BlockVector3 position);
    
    /**
     * Get the selected region.
     * @throws IncompleteRegionException if the selection is incomplete
     */
    Region getRegion() throws IncompleteRegionException;
    
    /**
     * Check if the primary position is set.
     */
    boolean isPrimaryPositionSet();
    
    /**
     * Check if the secondary position is set.
     */
    boolean isSecondaryPositionSet();
    
    /**
     * Get the primary position.
     */
    BlockVector3 getPrimaryPosition();
    
    /**
     * Get the secondary position.
     */
    BlockVector3 getSecondaryPosition();
    
    /**
     * Clear the selection.
     */
    void clear();
    
    /**
     * Explain the primary selection to the player.
     */
    void explainPrimarySelection(ServerPlayer player, BlockVector3 position);
    
    /**
     * Explain the secondary selection to the player.
     */
    void explainSecondarySelection(ServerPlayer player, BlockVector3 position);
    
    /**
     * Get a description of this selector type.
     */
    String getTypeName();
}
