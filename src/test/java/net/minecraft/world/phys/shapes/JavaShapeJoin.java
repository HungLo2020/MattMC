package net.minecraft.world.phys.shapes;

import static net.minecraft.world.phys.shapes.Shapes.*;
import net.minecraft.core.Direction;
import net.minecraft.Util;

/** Literal original public caller; only the joined-grid target names the original oracle. */
final class JavaShapeJoin {
	public static VoxelShape joinUnoptimized(VoxelShape voxelShape, VoxelShape voxelShape2, BooleanOp booleanOp) {
		if (booleanOp.apply(false, false)) {
			throw (IllegalArgumentException)Util.pauseInIde(new IllegalArgumentException());
		} else if (voxelShape == voxelShape2) {
			return booleanOp.apply(true, true) ? voxelShape : empty();
		} else {
			boolean bl = booleanOp.apply(true, false);
			boolean bl2 = booleanOp.apply(false, true);
			if (voxelShape.isEmpty()) {
				return bl2 ? voxelShape2 : empty();
			} else if (voxelShape2.isEmpty()) {
				return bl ? voxelShape : empty();
			} else {
				IndexMerger indexMerger = createIndexMerger(1, voxelShape.getCoords(Direction.Axis.X), voxelShape2.getCoords(Direction.Axis.X), bl, bl2);
				IndexMerger indexMerger2 = createIndexMerger(
					indexMerger.size() - 1, voxelShape.getCoords(Direction.Axis.Y), voxelShape2.getCoords(Direction.Axis.Y), bl, bl2
				);
				IndexMerger indexMerger3 = createIndexMerger(
					(indexMerger.size() - 1) * (indexMerger2.size() - 1), voxelShape.getCoords(Direction.Axis.Z), voxelShape2.getCoords(Direction.Axis.Z), bl, bl2
				);
				BitSetDiscreteVoxelShape bitSetDiscreteVoxelShape = JavaVoxelJoin.join(
					voxelShape.shape, voxelShape2.shape, indexMerger, indexMerger2, indexMerger3, booleanOp
				);
				return (VoxelShape)(indexMerger instanceof DiscreteCubeMerger && indexMerger2 instanceof DiscreteCubeMerger && indexMerger3 instanceof DiscreteCubeMerger
					? new CubeVoxelShape(bitSetDiscreteVoxelShape)
					: new ArrayVoxelShape(bitSetDiscreteVoxelShape, indexMerger.getList(), indexMerger2.getList(), indexMerger3.getList()));
			}
		}
	}

}
