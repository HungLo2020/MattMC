package com.github.alexmodguy.alexscaves.server.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.SwimNodeEvaluator;

public class AllFluidsNodeEvaluator extends SwimNodeEvaluator {

    public AllFluidsNodeEvaluator(boolean breaching) {
        super(breaching);
    }

    @Override
    public PathType getPathType(PathfindingContext context, int x, int y, int z) {
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

        for (int i = x; i < x + entityWidth; ++i) {
            for (int j = y; j < y + entityHeight; ++j) {
                for (int k = z; k < z + entityDepth; ++k) {
                    FluidState fluidstate = context.level().getFluidState(blockpos$mutableblockpos.set(i, j, k));
                    BlockState blockstate = context.getBlockState(blockpos$mutableblockpos.set(i, j, k));

                    if (fluidstate.isEmpty() && !blockstate.isAir()) {
                        return PathType.BLOCKED;
                    }
                }
            }
        }
        BlockState blockstate1 = context.getBlockState(blockpos$mutableblockpos);
        return blockstate1.isAir() ? PathType.BREACH : !blockstate1.getFluidState().isEmpty() ? PathType.WATER : PathType.BLOCKED;
    }
}
