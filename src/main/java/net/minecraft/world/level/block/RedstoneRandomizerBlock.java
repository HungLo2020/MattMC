package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;

public class RedstoneRandomizerBlock extends DiodeBlock {
	public static final MapCodec<RedstoneRandomizerBlock> CODEC = simpleCodec(RedstoneRandomizerBlock::new);
	public static final EnumProperty<OutputSide> OUTPUT_SIDE = EnumProperty.create("output", OutputSide.class);

	public RedstoneRandomizerBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OUTPUT_SIDE, OutputSide.LEFT).setValue(POWERED, false));
	}

	@Override
	public MapCodec<RedstoneRandomizerBlock> codec() {
		return CODEC;
	}

	@Override
	protected int getDelay(BlockState blockState) {
		return 2;
	}

	@Override
	public BlockState updateShape(
		BlockState blockState,
		LevelReader levelReader,
		ScheduledTickAccess scheduledTickAccess,
		BlockPos blockPos,
		Direction direction,
		BlockPos blockPos2,
		BlockState blockState2,
		RandomSource randomSource
	) {
		return direction == Direction.DOWN && !this.canSurviveOn(levelReader, blockPos2, blockState2)
			? Blocks.AIR.defaultBlockState()
			: super.updateShape(blockState, levelReader, scheduledTickAccess, blockPos, direction, blockPos2, blockState2, randomSource);
	}

	@Override
	protected void tick(BlockState blockState, ServerLevel serverLevel, BlockPos blockPos, RandomSource randomSource) {
		boolean powered = blockState.getValue(POWERED);
		boolean shouldPower = this.shouldTurnOn(serverLevel, blockPos, blockState);
		if (powered == shouldPower) {
			return;
		}

		BlockState nextState = shouldPower
			? blockState.setValue(POWERED, true).setValue(OUTPUT_SIDE, randomSource.nextBoolean() ? OutputSide.LEFT : OutputSide.RIGHT)
			: blockState.setValue(POWERED, false);
		serverLevel.setBlock(blockPos, nextState, Block.UPDATE_CLIENTS);
		this.updateOutputNeighbors(serverLevel, blockPos, blockState);
		this.updateOutputNeighbors(serverLevel, blockPos, nextState);
	}

	@Override
	protected int getSignal(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, Direction direction) {
		if (!blockState.getValue(POWERED)) {
			return 0;
		}

		return direction == this.getSignalQueryDirection(blockState) ? 15 : 0;
	}

	@Override
	protected int getDirectSignal(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, Direction direction) {
		return blockState.getSignal(blockGetter, blockPos, direction);
	}

	@Override
	protected void updateNeighborsInFront(Level level, BlockPos blockPos, BlockState blockState) {
		this.updateOutputNeighbors(level, blockPos, blockState);
	}

	private void updateOutputNeighbors(Level level, BlockPos blockPos, BlockState blockState) {
		this.updateOutputNeighbor(level, blockPos, this.getPhysicalOutputDirection(blockState, OutputSide.LEFT));
		this.updateOutputNeighbor(level, blockPos, this.getPhysicalOutputDirection(blockState, OutputSide.RIGHT));
	}

	private void updateOutputNeighbor(Level level, BlockPos blockPos, Direction physicalOutputDirection) {
		Direction signalQueryDirection = physicalOutputDirection.getOpposite();
		BlockPos outputPos = blockPos.relative(physicalOutputDirection);
		Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(level, signalQueryDirection, Direction.UP);
		level.neighborChanged(outputPos, this, orientation);
		level.updateNeighborsAtExceptFromFacing(outputPos, this, signalQueryDirection, orientation);
	}

	public Direction getPhysicalOutputDirection(BlockState blockState) {
		return this.getPhysicalOutputDirection(blockState, blockState.getValue(OUTPUT_SIDE));
	}

	public Direction getSignalQueryDirection(BlockState blockState) {
		return this.getPhysicalOutputDirection(blockState).getOpposite();
	}

	private Direction getPhysicalOutputDirection(BlockState blockState, OutputSide outputSide) {
		Direction inputDirection = blockState.getValue(FACING);
		return outputSide == OutputSide.LEFT ? inputDirection.getCounterClockWise() : inputDirection.getClockWise();
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, OUTPUT_SIDE, POWERED);
	}

	public enum OutputSide implements StringRepresentable {
		LEFT("left"),
		RIGHT("right");

		private final String name;

		OutputSide(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return this.name;
		}
	}
}
