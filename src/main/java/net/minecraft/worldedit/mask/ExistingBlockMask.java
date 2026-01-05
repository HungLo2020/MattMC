package net.minecraft.worldedit.mask;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.worldedit.extent.Extent;
import net.minecraft.worldedit.math.BlockVector3;

/**
 * Mask that matches any non-air blocks
 */
public class ExistingBlockMask implements Mask {
    private final Extent extent;
    
    public ExistingBlockMask(Extent extent) {
        this.extent = extent;
    }
    
    @Override
    public boolean test(Extent extent, BlockVector3 position) {
        BlockState state = extent.getBlock(position);
        return state != null && !state.isAir();
    }
}
