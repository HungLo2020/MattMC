package net.minecraft.world.level.levelgen.feature.trunkplacers;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;

public class OffshootTrunkPlacer extends TrunkPlacer {
	public static final MapCodec<OffshootTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec(
		instance -> trunkPlacerParts(instance).apply(instance, OffshootTrunkPlacer::new)
	);

	public OffshootTrunkPlacer(int i, int j, int k) {
		super(i, j, k);
	}

	@Override
	protected TrunkPlacerType<?> type() {
		return TrunkPlacerType.OFFSHOOT_TRUNK_PLACER;
	}

	@Override
	public List<FoliagePlacer.FoliageAttachment> placeTrunk(
		LevelSimulatedReader levelSimulatedReader,
		BiConsumer<BlockPos, BlockState> biConsumer,
		RandomSource randomSource,
		int i,
		BlockPos blockPos,
		TreeConfiguration treeConfiguration
	) {
		setDirtAt(levelSimulatedReader, biConsumer, randomSource, blockPos.below(), treeConfiguration);

		// Place the main trunk
		for (int j = 0; j < i; j++) {
			this.placeLog(levelSimulatedReader, biConsumer, randomSource, blockPos.above(j), treeConfiguration);
		}

		// Add 0-1 random horizontal offshoots (only for trees with height >= 5)
		// Offshoots should be below the foliage (top - 3 blocks) and at least 2 blocks above ground
		if (i >= 5) {
			int offshootCount = randomSource.nextInt(2); // 0 or 1 offshoots
			
			// Calculate safe height range: top minus 3 blocks to avoid foliage
			int maxOffshootHeight = i - 3;
			
			for (int k = 0; k < offshootCount; k++) {
				// Random height for the offshoot (between 2 and top - 3)
				int offshootHeight = 2 + randomSource.nextInt(Math.max(1, maxOffshootHeight - 2));
				
				// Random cardinal direction
				Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(randomSource);
				
				// Place a single horizontal offshoot block with proper axis orientation
				BlockPos offshootPos = blockPos.above(offshootHeight).relative(direction);
				this.placeLog(
					levelSimulatedReader,
					biConsumer,
					randomSource,
					offshootPos,
					treeConfiguration,
					state -> state.setValue(RotatedPillarBlock.AXIS, direction.getAxis())
				);
			}
		}

		// Return only the main trunk top for foliage placement (no foliage on offshoots)
		return ImmutableList.of(new FoliagePlacer.FoliageAttachment(blockPos.above(i), 0, false));
	}
}
