package net.minecraft.world.level.levelgen.feature;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class EndPodiumFeature extends Feature<NoneFeatureConfiguration> {
	public static final int PODIUM_RADIUS = 4;
	public static final int PODIUM_PILLAR_HEIGHT = 4;
	public static final int RIM_RADIUS = 1;
	public static final float CORNER_ROUNDING = 0.5F;
	private static final BlockPos END_PODIUM_LOCATION = BlockPos.ZERO;
	private final boolean active;

	public static BlockPos getLocation(BlockPos blockPos) {
		return END_PODIUM_LOCATION.offset(blockPos);
	}

	public EndPodiumFeature(boolean bl) {
		super(NoneFeatureConfiguration.CODEC);
		this.active = bl;
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> featurePlaceContext) {
		BlockPos blockPos = featurePlaceContext.origin();
		WorldGenLevel worldGenLevel = featurePlaceContext.level();
		BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

		int minX = blockPos.getX() - 4;
		int maxX = blockPos.getX() + 4;
		int minY = blockPos.getY() - 1;
		int maxY = blockPos.getY() + 32;
		int minZ = blockPos.getZ() - 4;
		int maxZ = blockPos.getZ() + 4;

		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					mutableBlockPos.set(x, y, z);
					boolean bl = mutableBlockPos.closerThan(blockPos, 2.5);
					if (bl || mutableBlockPos.closerThan(blockPos, 3.5)) {
						if (y < blockPos.getY()) {
							if (bl) {
								this.setBlock(worldGenLevel, mutableBlockPos, Blocks.BEDROCK.defaultBlockState());
							} else if (y < blockPos.getY()) {
								if (this.active) {
									this.dropPreviousAndSetBlock(worldGenLevel, mutableBlockPos, Blocks.END_STONE);
								} else {
									this.setBlock(worldGenLevel, mutableBlockPos, Blocks.END_STONE.defaultBlockState());
								}
							}
						} else if (y > blockPos.getY()) {
							if (this.active) {
								this.dropPreviousAndSetBlock(worldGenLevel, mutableBlockPos, Blocks.AIR);
							} else {
								this.setBlock(worldGenLevel, mutableBlockPos, Blocks.AIR.defaultBlockState());
							}
						} else if (!bl) {
							this.setBlock(worldGenLevel, mutableBlockPos, Blocks.BEDROCK.defaultBlockState());
						} else if (this.active) {
							this.dropPreviousAndSetBlock(worldGenLevel, mutableBlockPos.immutable(), Blocks.END_PORTAL);
						} else {
							this.setBlock(worldGenLevel, mutableBlockPos, Blocks.AIR.defaultBlockState());
						}
					}
				}
			}
		}

		for (int i = 0; i < 4; i++) {
			this.setBlock(worldGenLevel, blockPos.above(i), Blocks.BEDROCK.defaultBlockState());
		}

		BlockPos blockPos3 = blockPos.above(2);

		for (Direction direction : Direction.Plane.HORIZONTAL) {
			this.setBlock(worldGenLevel, blockPos3.relative(direction), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, direction));
		}

		return true;
	}

	private void dropPreviousAndSetBlock(WorldGenLevel worldGenLevel, BlockPos blockPos, Block block) {
		if (!worldGenLevel.getBlockState(blockPos).is(block)) {
			worldGenLevel.destroyBlock(blockPos, true, null);
			this.setBlock(worldGenLevel, blockPos, block.defaultBlockState());
		}
	}
}
