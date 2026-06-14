package net.minecraft.worldedit.mask;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.worldedit.extent.Extent;
import net.minecraft.worldedit.math.BlockVector3;

import java.util.Set;

/**
 * A mask that matches any block state from a fixed set.
 */
public class BlockSetMask implements Mask {
    private final Set<BlockState> blocks;

    public BlockSetMask(Set<BlockState> blocks) {
        this.blocks = Set.copyOf(blocks);
    }

    @Override
    public boolean test(Extent extent, BlockVector3 position) {
        return test(extent.getBlock(position));
    }

    @Override
    public boolean test(BlockState state) {
        return blocks.contains(state);
    }

    int getEntryCount() {
        return blocks.size();
    }
}
