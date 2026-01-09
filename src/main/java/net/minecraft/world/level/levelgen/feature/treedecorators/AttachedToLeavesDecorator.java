package net.minecraft.world.level.levelgen.feature.treedecorators;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

public class AttachedToLeavesDecorator extends TreeDecorator {
	public static final MapCodec<AttachedToLeavesDecorator> CODEC = RecordCodecBuilder.mapCodec(
		instance -> instance.group(
				Codec.floatRange(0.0F, 1.0F).fieldOf("probability").forGetter(attachedToLeavesDecorator -> attachedToLeavesDecorator.probability),
				Codec.intRange(0, 16).fieldOf("exclusion_radius_xz").forGetter(attachedToLeavesDecorator -> attachedToLeavesDecorator.exclusionRadiusXZ),
				Codec.intRange(0, 16).fieldOf("exclusion_radius_y").forGetter(attachedToLeavesDecorator -> attachedToLeavesDecorator.exclusionRadiusY),
				BlockStateProvider.CODEC.fieldOf("block_provider").forGetter(attachedToLeavesDecorator -> attachedToLeavesDecorator.blockProvider),
				Codec.intRange(1, 16).fieldOf("required_empty_blocks").forGetter(attachedToLeavesDecorator -> attachedToLeavesDecorator.requiredEmptyBlocks),
				ExtraCodecs.nonEmptyList(Direction.CODEC.listOf()).fieldOf("directions").forGetter(attachedToLeavesDecorator -> attachedToLeavesDecorator.directions)
			)
			.apply(instance, AttachedToLeavesDecorator::new)
	);
	protected final float probability;
	protected final int exclusionRadiusXZ;
	protected final int exclusionRadiusY;
	protected final BlockStateProvider blockProvider;
	protected final int requiredEmptyBlocks;
	protected final List<Direction> directions;

	public AttachedToLeavesDecorator(float f, int i, int j, BlockStateProvider blockStateProvider, int k, List<Direction> list) {
		this.probability = f;
		this.exclusionRadiusXZ = i;
		this.exclusionRadiusY = j;
		this.blockProvider = blockStateProvider;
		this.requiredEmptyBlocks = k;
		this.directions = list;
	}

	@Override
	public void place(TreeDecorator.Context context) {
		Set<BlockPos> set = new HashSet();
		RandomSource randomSource = context.random();
		BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

		for (BlockPos blockPos : Util.shuffledCopy(context.leaves(), randomSource)) {
			Direction direction = Util.getRandom(this.directions, randomSource);
			BlockPos blockPos2 = blockPos.relative(direction);
			if (!set.contains(blockPos2) && randomSource.nextFloat() < this.probability && this.hasRequiredEmptyBlocks(context, blockPos, direction)) {
				int minX = blockPos2.getX() - this.exclusionRadiusXZ;
				int maxX = blockPos2.getX() + this.exclusionRadiusXZ;
				int minY = blockPos2.getY() - this.exclusionRadiusY;
				int maxY = blockPos2.getY() + this.exclusionRadiusY;
				int minZ = blockPos2.getZ() - this.exclusionRadiusXZ;
				int maxZ = blockPos2.getZ() + this.exclusionRadiusXZ;

				for (int x = minX; x <= maxX; x++) {
					for (int y = minY; y <= maxY; y++) {
						for (int z = minZ; z <= maxZ; z++) {
							mutableBlockPos.set(x, y, z);
							set.add(mutableBlockPos.immutable());
						}
					}
				}

				context.setBlock(blockPos2, this.blockProvider.getState(randomSource, blockPos2));
			}
		}
	}

	private boolean hasRequiredEmptyBlocks(TreeDecorator.Context context, BlockPos blockPos, Direction direction) {
		BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
		for (int i = 1; i <= this.requiredEmptyBlocks; i++) {
			mutableBlockPos.set(
				blockPos.getX() + direction.getStepX() * i,
				blockPos.getY() + direction.getStepY() * i,
				blockPos.getZ() + direction.getStepZ() * i
			);
			if (!context.isAir(mutableBlockPos)) {
				return false;
			}
		}

		return true;
	}

	@Override
	protected TreeDecoratorType<?> type() {
		return TreeDecoratorType.ATTACHED_TO_LEAVES;
	}
}
