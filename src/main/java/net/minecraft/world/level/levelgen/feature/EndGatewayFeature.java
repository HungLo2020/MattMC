package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.level.levelgen.feature.configurations.EndGatewayConfiguration;

public class EndGatewayFeature extends Feature<EndGatewayConfiguration> {
	public EndGatewayFeature(Codec<EndGatewayConfiguration> codec) {
		super(codec);
	}

	@Override
	public boolean place(FeaturePlaceContext<EndGatewayConfiguration> featurePlaceContext) {
		BlockPos blockPos = featurePlaceContext.origin();
		WorldGenLevel worldGenLevel = featurePlaceContext.level();
		EndGatewayConfiguration endGatewayConfiguration = featurePlaceContext.config();
		BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

		int originX = blockPos.getX();
		int originY = blockPos.getY();
		int originZ = blockPos.getZ();

		for (int x = originX - 1; x <= originX + 1; x++) {
			for (int y = originY - 2; y <= originY + 2; y++) {
				for (int z = originZ - 1; z <= originZ + 1; z++) {
					mutableBlockPos.set(x, y, z);
					boolean bl = x == originX;
					boolean bl2 = y == originY;
					boolean bl3 = z == originZ;
					boolean bl4 = Math.abs(y - originY) == 2;
					if (bl && bl2 && bl3) {
						BlockPos blockPos3 = mutableBlockPos.immutable();
						this.setBlock(worldGenLevel, blockPos3, Blocks.END_GATEWAY.defaultBlockState());
						endGatewayConfiguration.getExit().ifPresent(blockPos2x -> {
							if (worldGenLevel.getBlockEntity(blockPos3) instanceof TheEndGatewayBlockEntity theEndGatewayBlockEntity) {
								theEndGatewayBlockEntity.setExitPosition(blockPos2x, endGatewayConfiguration.isExitExact());
							}
						});
					} else if (bl2) {
						this.setBlock(worldGenLevel, mutableBlockPos, Blocks.AIR.defaultBlockState());
					} else if (bl4 && bl && bl3) {
						this.setBlock(worldGenLevel, mutableBlockPos, Blocks.BEDROCK.defaultBlockState());
					} else if ((bl || bl3) && !bl4) {
						this.setBlock(worldGenLevel, mutableBlockPos, Blocks.BEDROCK.defaultBlockState());
					} else {
						this.setBlock(worldGenLevel, mutableBlockPos, Blocks.AIR.defaultBlockState());
					}
				}
			}
		}

		return true;
	}
}
