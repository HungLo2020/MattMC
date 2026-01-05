package net.minecraft.worldedit.mask;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.worldedit.extent.Extent;
import net.minecraft.worldedit.math.BlockVector3;

/**
 * A mask that matches a specific block type.
 */
public class BlockMask implements Mask {
    private final BlockState block;
    
    public BlockMask(BlockState block) {
        this.block = block;
    }
    
    @Override
    public boolean test(Extent extent, BlockVector3 position) {
        BlockState current = extent.getBlock(position);
        return current.equals(block);
    }
    
    @Override
    public boolean test(BlockState state) {
        return state.equals(block);
    }
}
