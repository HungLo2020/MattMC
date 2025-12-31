package net.minecraft.world.entity.ai.util;

import com.google.common.annotations.VisibleForTesting;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class RandomPos {
	private static final int RANDOM_POS_ATTEMPTS = 10;

	public static BlockPos generateRandomDirection(RandomSource randomSource, int i, int j) {
		int k = randomSource.nextInt(2 * i + 1) - i;
		int l = randomSource.nextInt(2 * j + 1) - j;
		int m = randomSource.nextInt(2 * i + 1) - i;
		return new BlockPos(k, l, m);
	}

	@Nullable
	public static BlockPos generateRandomDirectionWithinRadians(RandomSource randomSource, int i, int j, int k, double d, double e, double f) {
		double g = Mth.atan2(e, d) - (float) (Math.PI / 2);
		double h = g + (2.0F * randomSource.nextFloat() - 1.0F) * f;
		double l = Math.sqrt(randomSource.nextDouble()) * Mth.SQRT_OF_TWO * i;
		double m = -l * Math.sin(h);
		double n = l * Math.cos(h);
		if (!(Math.abs(m) > i) && !(Math.abs(n) > i)) {
			int o = randomSource.nextInt(2 * j + 1) - j + k;
			return BlockPos.containing(m, o, n);
		} else {
			return null;
		}
	}

	@VisibleForTesting
	public static BlockPos moveUpOutOfSolid(BlockPos blockPos, int i, Predicate<BlockPos> predicate) {
		if (!predicate.test(blockPos)) {
			return blockPos;
		} else {
			BlockPos.MutableBlockPos mutableBlockPos = blockPos.mutable().move(Direction.UP);

			while (mutableBlockPos.getY() <= i && predicate.test(mutableBlockPos)) {
				mutableBlockPos.move(Direction.UP);
			}

			return mutableBlockPos.immutable();
		}
	}

	@VisibleForTesting
	public static BlockPos moveUpToAboveSolid(BlockPos blockPos, int i, int j, Predicate<BlockPos> predicate) {
		if (i < 0) {
			throw new IllegalArgumentException("aboveSolidAmount was " + i + ", expected >= 0");
		} else if (!predicate.test(blockPos)) {
			return blockPos;
		} else {
			BlockPos.MutableBlockPos mutableBlockPos = blockPos.mutable().move(Direction.UP);

			while (mutableBlockPos.getY() <= j && predicate.test(mutableBlockPos)) {
				mutableBlockPos.move(Direction.UP);
			}

			int k = mutableBlockPos.getY();

			while (mutableBlockPos.getY() <= j && mutableBlockPos.getY() - k < i) {
				mutableBlockPos.move(Direction.UP);
				if (predicate.test(mutableBlockPos)) {
					mutableBlockPos.move(Direction.DOWN);
					break;
				}
			}

			return mutableBlockPos.immutable();
		}
	}

	@Nullable
	public static Vec3 generateRandomPos(PathfinderMob pathfinderMob, Supplier<BlockPos> supplier) {
		return generateRandomPos(supplier, pathfinderMob::getWalkTargetValue);
	}

	@Nullable
	public static Vec3 generateRandomPos(Supplier<BlockPos> supplier, ToDoubleFunction<BlockPos> toDoubleFunction) {
		double d = Double.NEGATIVE_INFINITY;
		BlockPos blockPos = null;

		for (int i = 0; i < 10; i++) {
			BlockPos blockPos2 = (BlockPos)supplier.get();
			if (blockPos2 != null) {
				double e = toDoubleFunction.applyAsDouble(blockPos2);
				if (e > d) {
					d = e;
					blockPos = blockPos2;
				}
			}
		}

		return blockPos != null ? Vec3.atBottomCenterOf(blockPos) : null;
	}

	public static BlockPos generateRandomPosTowardDirection(PathfinderMob pathfinderMob, int i, RandomSource randomSource, BlockPos blockPos) {
		int j = blockPos.getX();
		int k = blockPos.getZ();
		if (pathfinderMob.hasHome() && i > 1) {
			BlockPos blockPos2 = pathfinderMob.getHomePosition();
			if (pathfinderMob.getX() > blockPos2.getX()) {
				j -= randomSource.nextInt(i / 2);
			} else {
				j += randomSource.nextInt(i / 2);
			}

			if (pathfinderMob.getZ() > blockPos2.getZ()) {
				k -= randomSource.nextInt(i / 2);
			} else {
				k += randomSource.nextInt(i / 2);
			}
		}

		return BlockPos.containing(j + pathfinderMob.getX(), blockPos.getY() + pathfinderMob.getY(), k + pathfinderMob.getZ());
	}
}
