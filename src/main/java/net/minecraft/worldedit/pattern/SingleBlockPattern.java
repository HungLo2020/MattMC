package net.minecraft.worldedit.pattern;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.worldedit.math.BlockVector3;

/**
 * A simple pattern that always returns the same block.
 */
public class SingleBlockPattern implements Pattern {
    private final BlockState block;
    
    public SingleBlockPattern(BlockState block) {
        this.block = block;
    }
    
    @Override
    public BlockState apply(BlockVector3 position) {
        return block;
    }
}
