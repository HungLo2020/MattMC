package net.matt.quantize.modules.entities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public interface LaysEggs {

    boolean hasEgg();

    void setHasEgg(boolean hasEgg);

    BlockState createEggBlockState();

    default void onLayEggTick(BlockPos belowEgg, int time) {
    }

}
