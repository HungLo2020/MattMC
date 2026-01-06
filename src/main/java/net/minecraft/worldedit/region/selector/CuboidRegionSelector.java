package net.minecraft.worldedit.region.selector;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.worldedit.math.BlockVector3;
import net.minecraft.worldedit.region.CuboidRegion;
import net.minecraft.worldedit.region.IncompleteRegionException;
import net.minecraft.worldedit.region.Region;
import net.minecraft.worldedit.region.RegionSelector;

/**
 * Selector for cuboid regions defined by two corner points.
 */
public class CuboidRegionSelector implements RegionSelector {
    private final ServerLevel world;
    private BlockVector3 pos1;
    private BlockVector3 pos2;
    
    public CuboidRegionSelector(ServerLevel world) {
        this.world = world;
    }
    
    public CuboidRegionSelector(ServerLevel world, BlockVector3 pos1, BlockVector3 pos2) {
        this.world = world;
        this.pos1 = pos1;
        this.pos2 = pos2;
    }
    
    @Override
    public boolean selectPrimary(BlockVector3 position) {
        if (pos1 != null && pos1.equals(position)) {
            return false;
        }
        pos1 = position;
        return true;
    }
    
    @Override
    public boolean selectSecondary(BlockVector3 position) {
        if (pos2 != null && pos2.equals(position)) {
            return false;
        }
        pos2 = position;
        return true;
    }
    
    @Override
    public Region getRegion() throws IncompleteRegionException {
        if (pos1 == null || pos2 == null) {
            throw new IncompleteRegionException("Please make both positions first");
        }
        return new CuboidRegion(pos1, pos2);
    }
    
    @Override
    public boolean isPrimaryPositionSet() {
        return pos1 != null;
    }
    
    @Override
    public boolean isSecondaryPositionSet() {
        return pos2 != null;
    }
    
    @Override
    public BlockVector3 getPrimaryPosition() {
        return pos1;
    }
    
    @Override
    public BlockVector3 getSecondaryPosition() {
        return pos2;
    }
    
    @Override
    public void clear() {
        pos1 = null;
        pos2 = null;
    }
    
    @Override
    public void explainPrimarySelection(ServerPlayer player, BlockVector3 position) {
        if (pos1 != null && pos2 != null) {
            try {
                Region region = getRegion();
                player.sendSystemMessage(Component.literal(
                    String.format("First position set to %s (%d blocks).",
                        position, region.getVolume())
                ));
            } catch (IncompleteRegionException e) {
                // Should not happen
            }
        } else {
            player.sendSystemMessage(Component.literal(
                String.format("First position set to %s.", position)
            ));
        }
    }
    
    @Override
    public void explainSecondarySelection(ServerPlayer player, BlockVector3 position) {
        if (pos1 != null && pos2 != null) {
            try {
                Region region = getRegion();
                player.sendSystemMessage(Component.literal(
                    String.format("Second position set to %s (%d blocks).",
                        position, region.getVolume())
                ));
            } catch (IncompleteRegionException e) {
                // Should not happen
            }
        } else {
            player.sendSystemMessage(Component.literal(
                String.format("Second position set to %s.", position)
            ));
        }
    }
    
    @Override
    public String getTypeName() {
        return "cuboid";
    }
}
