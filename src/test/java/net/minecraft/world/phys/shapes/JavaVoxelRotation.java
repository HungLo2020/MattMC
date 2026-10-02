package net.minecraft.world.phys.shapes;

import net.math.OctahedralGroup;
import net.minecraft.core.Direction;

/** Literal original rotation, with only the receiver made explicit. */
final class JavaVoxelRotation {
	static DiscreteVoxelShape rotate(DiscreteVoxelShape source, OctahedralGroup octahedralGroup) {
		if (octahedralGroup == OctahedralGroup.IDENTITY) {
			return source;
		} else {
			Direction.Axis axis = octahedralGroup.permute(Direction.Axis.X);
			Direction.Axis axis2 = octahedralGroup.permute(Direction.Axis.Y);
			Direction.Axis axis3 = octahedralGroup.permute(Direction.Axis.Z);
			int i = axis.choose(source.xSize, source.ySize, source.zSize);
			int j = axis2.choose(source.xSize, source.ySize, source.zSize);
			int k = axis3.choose(source.xSize, source.ySize, source.zSize);
			boolean bl = octahedralGroup.inverts(axis);
			boolean bl2 = octahedralGroup.inverts(axis2);
			boolean bl3 = octahedralGroup.inverts(axis3);
			boolean bl4 = axis.choose(bl, bl2, bl3);
			boolean bl5 = axis2.choose(bl, bl2, bl3);
			boolean bl6 = axis3.choose(bl, bl2, bl3);
			DiscreteVoxelShape discreteVoxelShape = new BitSetDiscreteVoxelShape(i, j, k);

			for (int l = 0; l < source.xSize; l++) {
				for (int m = 0; m < source.ySize; m++) {
					for (int n = 0; n < source.zSize; n++) {
						if (source.isFull(l, m, n)) {
							int o = axis.choose(l, m, n);
							int p = axis2.choose(l, m, n);
							int q = axis3.choose(l, m, n);
							discreteVoxelShape.fill(bl4 ? i - 1 - o : o, bl5 ? j - 1 - p : p, bl6 ? k - 1 - q : q);
						}
					}
				}
			}

			return discreteVoxelShape;
		}
	}

}
