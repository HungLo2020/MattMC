package com.github.alexmodguy.alexscaves.server.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class AmbersolBlock extends Block {

    public AmbersolBlock(Properties properties) {
        super(properties);
    }

    public static BlockPos fillWithLights(BlockPos current, LevelAccessor level) {
        current = current.below();
        while (current.getY() > level.getMinY() && AmbersolLightBlock.testSkylight(level, level.getBlockState(current), current)) {
            if (level.getBlockState(current).isAir()) {
                level.setBlock(current, net.minecraft.world.level.block.Blocks.AMBERSOL_LIGHT.defaultBlockState(), 3);
            }
            current = current.below();
        }
        return current;
    }

    @Override
    public BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess tickAccess, BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        fillWithLights(pos, (LevelAccessor) level);
        return super.updateShape(state, level, tickAccess, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel serverLevel, BlockPos pos, RandomSource randomSource) {
        fillWithLights(pos, serverLevel);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource randomSource) {
        fillWithLights(pos, level);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity livingEntity, ItemStack stack) {
        fillWithLights(pos, level);
    }
}
