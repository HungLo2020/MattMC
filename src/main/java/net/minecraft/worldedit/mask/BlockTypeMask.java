package net.minecraft.worldedit.mask;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.worldedit.extent.Extent;
import net.minecraft.worldedit.math.BlockVector3;

/**
 * A mask that matches every state belonging to one block type.
 */
public class BlockTypeMask implements Mask {
    private final Block block;

    public BlockTypeMask(Block block) {
        this.block = block;
    }

    @Override
    public boolean test(Extent extent, BlockVector3 position) {
        return test(extent.getBlock(position));
    }

    @Override
    public boolean test(BlockState state) {
        return state != null && state.is(block);
    }
}
