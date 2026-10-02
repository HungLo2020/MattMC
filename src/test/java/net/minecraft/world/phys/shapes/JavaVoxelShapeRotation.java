package net.minecraft.world.phys.shapes;

import net.math.OctahedralGroup;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import it.unimi.dsi.fastutil.doubles.DoubleList;

/** Complete original public caller; only the grid kernel call is redirected. */
final class JavaVoxelShapeRotation {
private static final Vec3 BLOCK_CENTER = new Vec3(0.5,0.5,0.5);
	public static VoxelShape rotate(VoxelShape voxelShape, OctahedralGroup octahedralGroup, Vec3 vec3) {
		if (octahedralGroup == OctahedralGroup.IDENTITY) {
			return voxelShape;
		} else {
			DiscreteVoxelShape discreteVoxelShape = JavaVoxelRotation.rotate(voxelShape.shape, octahedralGroup);
			if (voxelShape instanceof CubeVoxelShape && BLOCK_CENTER.equals(vec3)) {
				return new CubeVoxelShape(discreteVoxelShape);
			} else {
				Direction.Axis axis = octahedralGroup.permute(Direction.Axis.X);
				Direction.Axis axis2 = octahedralGroup.permute(Direction.Axis.Y);
				Direction.Axis axis3 = octahedralGroup.permute(Direction.Axis.Z);
				DoubleList doubleList = voxelShape.getCoords(axis);
				DoubleList doubleList2 = voxelShape.getCoords(axis2);
				DoubleList doubleList3 = voxelShape.getCoords(axis3);
				boolean bl = octahedralGroup.inverts(axis);
				boolean bl2 = octahedralGroup.inverts(axis2);
				boolean bl3 = octahedralGroup.inverts(axis3);
				boolean bl4 = axis.choose(bl, bl2, bl3);
				boolean bl5 = axis2.choose(bl, bl2, bl3);
				boolean bl6 = axis3.choose(bl, bl2, bl3);
				return new ArrayVoxelShape(
					discreteVoxelShape,
					Shapes.makeAxis(doubleList, bl4, vec3.get(axis), vec3.x),
					Shapes.makeAxis(doubleList2, bl5, vec3.get(axis2), vec3.y),
					Shapes.makeAxis(doubleList3, bl6, vec3.get(axis3), vec3.z)
				);
			}
		}
	}

}
