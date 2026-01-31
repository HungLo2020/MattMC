package net.alexscaves.server.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Predicate;

public class AmbersolLightBlock extends Block {

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public AmbersolLightBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(WATERLOGGED, Boolean.valueOf(false)));
    }

    public boolean propagatesSkylightDown(BlockState state, BlockGetter getter, BlockPos blockPos) {
        return true;
    }

    public float getShadeBrightness(BlockState state, BlockGetter getter, BlockPos blockPos) {
        return 1.0F;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter getter, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    public BlockPos getTopOfColumn(BlockPos current, LevelReader levelReader, Predicate<BlockState> predicate) {
        while (current.getY() < levelReader.getMaxY() && predicate.test(levelReader.getBlockState(current))) {
            current = current.above();
        }
        return current;
    }

    public BlockPos getTopOfColumnLight(BlockPos current, LevelReader levelReader) {
        while (current.getY() < levelReader.getMaxY() && testSkylight(levelReader, levelReader.getBlockState(current), current)) {
            current = current.above();
        }
        return current;
    }

    public static boolean testSkylight(LevelReader levelReader, BlockState blockState, BlockPos current) {
        return blockState.propagatesSkylightDown();
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos top = getTopOfColumnLight(pos, level);
        return level.getBlockState(top).is(net.minecraft.world.level.block.Blocks.AMBERSOL);
    }

    @Override
    public BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess tickAccess, BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        if (state.getValue(WATERLOGGED)) {
            tickAccess.getFluidTicks().schedule(tickAccess.createTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level)));
        }
        if (((LevelAccessor) level).getBlockState(pos.below()).getBlock() != this) {
            BlockPos top = getTopOfColumn(pos, level, state2 -> !state2.is(net.minecraft.world.level.block.Blocks.AMBERSOL));
            tickAccess.getBlockTicks().schedule(tickAccess.createTick(top, net.minecraft.world.level.block.Blocks.AMBERSOL, 3));
        }
        if (!state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, tickAccess, pos, direction, neighborPos, neighborState, random);
    }
}
