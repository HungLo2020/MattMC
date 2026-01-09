package net.minecraft.world.level.levelgen.feature;

import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BuddingAmethystBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.GeodeBlockSettings;
import net.minecraft.world.level.levelgen.GeodeCrackSettings;
import net.minecraft.world.level.levelgen.GeodeLayerSettings;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.feature.configurations.GeodeConfiguration;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.material.FluidState;

public class GeodeFeature extends Feature<GeodeConfiguration> {
	private static final Direction[] DIRECTIONS = Direction.values();

	public GeodeFeature(Codec<GeodeConfiguration> codec) {
		super(codec);
	}

	@Override
	public boolean place(FeaturePlaceContext<GeodeConfiguration> featurePlaceContext) {
		GeodeConfiguration geodeConfiguration = featurePlaceContext.config();
		RandomSource randomSource = featurePlaceContext.random();
		BlockPos blockPos = featurePlaceContext.origin();
		WorldGenLevel worldGenLevel = featurePlaceContext.level();
		int i = geodeConfiguration.minGenOffset;
		int j = geodeConfiguration.maxGenOffset;
		List<Pair<BlockPos, Integer>> list = Lists.<Pair<BlockPos, Integer>>newLinkedList();
		int k = geodeConfiguration.distributionPoints.sample(randomSource);
		WorldgenRandom worldgenRandom = new WorldgenRandom(new LegacyRandomSource(worldGenLevel.getSeed()));
		NormalNoise normalNoise = NormalNoise.create(worldgenRandom, -4, 1.0);
		List<BlockPos> list2 = Lists.<BlockPos>newLinkedList();
		double d = (double)k / geodeConfiguration.outerWallDistance.getMaxValue();
		GeodeLayerSettings geodeLayerSettings = geodeConfiguration.geodeLayerSettings;
		GeodeBlockSettings geodeBlockSettings = geodeConfiguration.geodeBlockSettings;
		GeodeCrackSettings geodeCrackSettings = geodeConfiguration.geodeCrackSettings;
		double e = 1.0 / Math.sqrt(geodeLayerSettings.filling);
		double f = 1.0 / Math.sqrt(geodeLayerSettings.innerLayer + d);
		double g = 1.0 / Math.sqrt(geodeLayerSettings.middleLayer + d);
		double h = 1.0 / Math.sqrt(geodeLayerSettings.outerLayer + d);
		double l = 1.0 / Math.sqrt(geodeCrackSettings.baseCrackSize + randomSource.nextDouble() / 2.0 + (k > 3 ? d : 0.0));
		boolean bl = randomSource.nextFloat() < geodeCrackSettings.generateCrackChance;
		int m = 0;

		for (int n = 0; n < k; n++) {
			int o = geodeConfiguration.outerWallDistance.sample(randomSource);
			int p = geodeConfiguration.outerWallDistance.sample(randomSource);
			int q = geodeConfiguration.outerWallDistance.sample(randomSource);
			BlockPos blockPos2 = blockPos.offset(o, p, q);
			BlockState blockState = worldGenLevel.getBlockState(blockPos2);
			if (blockState.isAir() || blockState.is(geodeBlockSettings.invalidBlocks)) {
				if (++m > geodeConfiguration.invalidBlocksThreshold) {
					return false;
				}
			}

			list.add(Pair.of(blockPos2, geodeConfiguration.pointOffset.sample(randomSource)));
		}

		if (bl) {
			int n = randomSource.nextInt(4);
			int o = k * 2 + 1;
			if (n == 0) {
				list2.add(blockPos.offset(o, 7, 0));
				list2.add(blockPos.offset(o, 5, 0));
				list2.add(blockPos.offset(o, 1, 0));
			} else if (n == 1) {
				list2.add(blockPos.offset(0, 7, o));
				list2.add(blockPos.offset(0, 5, o));
				list2.add(blockPos.offset(0, 1, o));
			} else if (n == 2) {
				list2.add(blockPos.offset(o, 7, o));
				list2.add(blockPos.offset(o, 5, o));
				list2.add(blockPos.offset(o, 1, o));
			} else {
				list2.add(blockPos.offset(0, 7, 0));
				list2.add(blockPos.offset(0, 5, 0));
				list2.add(blockPos.offset(0, 1, 0));
			}
		}

		List<BlockPos> list3 = Lists.<BlockPos>newArrayList();
		Predicate<BlockState> predicate = isReplaceable(geodeConfiguration.geodeBlockSettings.cannotReplace);
		BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos scratchPos = new BlockPos.MutableBlockPos();

		int minX = blockPos.getX() + i;
		int maxX = blockPos.getX() + j;
		int minY = blockPos.getY() + i;
		int maxY = blockPos.getY() + j;
		int minZ = blockPos.getZ() + i;
		int maxZ = blockPos.getZ() + j;

		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					mutableBlockPos.set(x, y, z);
					double r = normalNoise.getValue(x, y, z) * geodeConfiguration.noiseMultiplier;
					double s = 0.0;
					double t = 0.0;

					for (Pair<BlockPos, Integer> pair : list) {
						s += Mth.invSqrt(mutableBlockPos.distSqr(pair.getFirst()) + pair.getSecond().intValue()) + r;
					}

					for (BlockPos blockPos4 : list2) {
						t += Mth.invSqrt(mutableBlockPos.distSqr(blockPos4) + geodeCrackSettings.crackPointOffset) + r;
					}

					if (!(s < h)) {
						if (bl && t >= l && s < e) {
							this.safeSetBlock(worldGenLevel, mutableBlockPos, Blocks.AIR.defaultBlockState(), predicate);

							for (Direction direction : DIRECTIONS) {
								scratchPos.setWithOffset(mutableBlockPos, direction);
								FluidState fluidState = worldGenLevel.getFluidState(scratchPos);
								if (!fluidState.isEmpty()) {
									worldGenLevel.scheduleTick(scratchPos, fluidState.getType(), 0);
								}
							}
						} else if (s >= e) {
							this.safeSetBlock(worldGenLevel, mutableBlockPos, geodeBlockSettings.fillingProvider.getState(randomSource, mutableBlockPos), predicate);
						} else if (s >= f) {
							boolean bl2 = randomSource.nextFloat() < geodeConfiguration.useAlternateLayer0Chance;
							if (bl2) {
								this.safeSetBlock(worldGenLevel, mutableBlockPos, geodeBlockSettings.alternateInnerLayerProvider.getState(randomSource, mutableBlockPos), predicate);
							} else {
								this.safeSetBlock(worldGenLevel, mutableBlockPos, geodeBlockSettings.innerLayerProvider.getState(randomSource, mutableBlockPos), predicate);
							}

							if ((!geodeConfiguration.placementsRequireLayer0Alternate || bl2) && randomSource.nextFloat() < geodeConfiguration.usePotentialPlacementsChance) {
								list3.add(mutableBlockPos.immutable());
							}
						} else if (s >= g) {
							this.safeSetBlock(worldGenLevel, mutableBlockPos, geodeBlockSettings.middleLayerProvider.getState(randomSource, mutableBlockPos), predicate);
						} else if (s >= h) {
							this.safeSetBlock(worldGenLevel, mutableBlockPos, geodeBlockSettings.outerLayerProvider.getState(randomSource, mutableBlockPos), predicate);
						}
					}
				}
			}
		}

		List<BlockState> list4 = geodeBlockSettings.innerPlacements;

		for (BlockPos blockPos2 : list3) {
			BlockState blockState = Util.getRandom(list4, randomSource);

			for (Direction direction2 : DIRECTIONS) {
				if (blockState.hasProperty(BlockStateProperties.FACING)) {
					blockState = blockState.setValue(BlockStateProperties.FACING, direction2);
				}

				scratchPos.setWithOffset(blockPos2, direction2);
				BlockState blockState2 = worldGenLevel.getBlockState(scratchPos);
				if (blockState.hasProperty(BlockStateProperties.WATERLOGGED)) {
					blockState = blockState.setValue(BlockStateProperties.WATERLOGGED, blockState2.getFluidState().isSource());
				}

				if (BuddingAmethystBlock.canClusterGrowAtState(blockState2)) {
					this.safeSetBlock(worldGenLevel, scratchPos, blockState, predicate);
					break;
				}
			}
		}

		return true;
	}
}
