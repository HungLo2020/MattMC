package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.BlockStateConfiguration;

public class BlockBlobFeature extends Feature<BlockStateConfiguration> {
	public BlockBlobFeature(Codec<BlockStateConfiguration> codec) {
		super(codec);
	}

	@Override
	public boolean place(FeaturePlaceContext<BlockStateConfiguration> featurePlaceContext) {
		BlockPos blockPos = featurePlaceContext.origin();
		WorldGenLevel worldGenLevel = featurePlaceContext.level();
		RandomSource randomSource = featurePlaceContext.random();

		BlockStateConfiguration blockStateConfiguration;
		for (blockStateConfiguration = featurePlaceContext.config(); blockPos.getY() > worldGenLevel.getMinY() + 3; blockPos = blockPos.below()) {
			if (!worldGenLevel.isEmptyBlock(blockPos.below())) {
				BlockState blockState = worldGenLevel.getBlockState(blockPos.below());
				if (isDirt(blockState) || isStone(blockState)) {
					break;
				}
			}
		}

		if (blockPos.getY() <= worldGenLevel.getMinY() + 3) {
			return false;
		} else {
			BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
			for (int i = 0; i < 3; i++) {
				int j = randomSource.nextInt(2);
				int k = randomSource.nextInt(2);
				int l = randomSource.nextInt(2);
				float f = (j + k + l) * 0.333F + 0.5F;

				int minX = blockPos.getX() - j;
				int maxX = blockPos.getX() + j;
				int minY = blockPos.getY() - k;
				int maxY = blockPos.getY() + k;
				int minZ = blockPos.getZ() - l;
				int maxZ = blockPos.getZ() + l;

				for (int x = minX; x <= maxX; x++) {
					for (int y = minY; y <= maxY; y++) {
						for (int z = minZ; z <= maxZ; z++) {
							mutableBlockPos.set(x, y, z);
							if (mutableBlockPos.distSqr(blockPos) <= f * f) {
								worldGenLevel.setBlock(mutableBlockPos, blockStateConfiguration.state, 3);
							}
						}
					}
				}

				blockPos = blockPos.offset(-1 + randomSource.nextInt(2), -randomSource.nextInt(2), -1 + randomSource.nextInt(2));
			}

			return true;
		}
	}
}
