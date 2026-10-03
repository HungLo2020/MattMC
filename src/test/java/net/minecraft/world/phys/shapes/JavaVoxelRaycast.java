package net.minecraft.world.phys.shapes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Literal public query pinned to Git 8b9173b39; second oracle additionally uses original Java boxes. */
final class JavaVoxelRaycast {
	@Nullable
	static BlockHitResult clip(VoxelShape source, Vec3 vec3, Vec3 vec32, BlockPos blockPos) {
		if (source.isEmpty()) {
			return null;
		} else {
			Vec3 vec33 = vec32.subtract(vec3);
			if (vec33.lengthSqr() < 1.0E-7) {
				return null;
			} else {
				Vec3 vec34 = vec3.add(vec33.scale(0.001));
				return source.shape
						.isFullWide(
							source.findIndex(Direction.Axis.X, vec34.x - blockPos.getX()),
							source.findIndex(Direction.Axis.Y, vec34.y - blockPos.getY()),
							source.findIndex(Direction.Axis.Z, vec34.z - blockPos.getZ())
						)
					? new BlockHitResult(vec34, Direction.getApproximateNearest(vec33.x, vec33.y, vec33.z).getOpposite(), blockPos, true)
					: AABB.clip(source.toAabbs(), vec3, vec32, blockPos);
			}
		}
	}


	@Nullable
	static BlockHitResult vanilla(VoxelShape source, Vec3 vec3, Vec3 vec32, BlockPos blockPos) {
		if (source.isEmpty()) {
			return null;
		} else {
			Vec3 vec33 = vec32.subtract(vec3);
			if (vec33.lengthSqr() < 1.0E-7) {
				return null;
			} else {
				Vec3 vec34 = vec3.add(vec33.scale(0.001));
				return source.shape
						.isFullWide(
							source.findIndex(Direction.Axis.X, vec34.x - blockPos.getX()),
							source.findIndex(Direction.Axis.Y, vec34.y - blockPos.getY()),
							source.findIndex(Direction.Axis.Z, vec34.z - blockPos.getZ())
						)
					? new BlockHitResult(vec34, Direction.getApproximateNearest(vec33.x, vec33.y, vec33.z).getOpposite(), blockPos, true)
					: AABB.clip(JavaVoxelBoxList.toAabbs(source), vec3, vec32, blockPos);
			}
		}
	}

}
