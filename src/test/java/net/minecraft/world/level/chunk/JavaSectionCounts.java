package net.minecraft.world.level.chunk;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/** Original recount, mutation and boolean accessors from fffe4a073. */
final class JavaSectionCounts {
    private short nonEmptyBlockCount;
    private short tickingBlockCount;
    private short tickingFluidCount;
    private final PalettedContainer<BlockState> states;
    JavaSectionCounts(PalettedContainer<BlockState> states) { this.states = states; recalcBlockCounts(); }
    long rawCounts() { return (long)Short.toUnsignedInt(nonEmptyBlockCount) | ((long)Short.toUnsignedInt(tickingBlockCount) << 16) | ((long)Short.toUnsignedInt(tickingFluidCount) << 32); }
	public BlockState setBlockState(int i, int j, int k, BlockState blockState) {
		return this.setBlockState(i, j, k, blockState, true);
	}

	public BlockState setBlockState(int i, int j, int k, BlockState blockState, boolean bl) {
		BlockState blockState2;
		if (bl) {
			blockState2 = this.states.getAndSet(i, j, k, blockState);
		} else {
			blockState2 = this.states.getAndSetUnchecked(i, j, k, blockState);
		}

		FluidState fluidState = blockState2.getFluidState();
		FluidState fluidState2 = blockState.getFluidState();
		if (!blockState2.isAir()) {
			this.nonEmptyBlockCount--;
			if (blockState2.isRandomlyTicking()) {
				this.tickingBlockCount--;
			}
		}

		if (!fluidState.isEmpty()) {
			this.tickingFluidCount--;
		}

		if (!blockState.isAir()) {
			this.nonEmptyBlockCount++;
			if (blockState.isRandomlyTicking()) {
				this.tickingBlockCount++;
			}
		}

		if (!fluidState2.isEmpty()) {
			this.tickingFluidCount++;
		}

		return blockState2;
	}

	public boolean hasOnlyAir() {
		return this.nonEmptyBlockCount == 0;
	}

	public boolean isRandomlyTicking() {
		return this.isRandomlyTickingBlocks() || this.isRandomlyTickingFluids();
	}

	public boolean isRandomlyTickingBlocks() {
		return this.tickingBlockCount > 0;
	}

	public boolean isRandomlyTickingFluids() {
		return this.tickingFluidCount > 0;
	}

	public void recalcBlockCounts() {
		class BlockCounter implements PalettedContainer.CountConsumer<BlockState> {
			public int nonEmptyBlockCount;
			public int tickingBlockCount;
			public int tickingFluidCount;

			public void accept(BlockState blockState, int i) {
				FluidState fluidState = blockState.getFluidState();
				if (!blockState.isAir()) {
					this.nonEmptyBlockCount += i;
					if (blockState.isRandomlyTicking()) {
						this.tickingBlockCount += i;
					}
				}

				if (!fluidState.isEmpty()) {
					this.nonEmptyBlockCount += i;
					if (fluidState.isRandomlyTicking()) {
						this.tickingFluidCount += i;
					}
				}
			}
		}

		BlockCounter lv = new BlockCounter();
		JavaPaletteHistogram.count(this.states, lv);
		this.nonEmptyBlockCount = (short)lv.nonEmptyBlockCount;
		this.tickingBlockCount = (short)lv.tickingBlockCount;
		this.tickingFluidCount = (short)lv.tickingFluidCount;
	}

}
