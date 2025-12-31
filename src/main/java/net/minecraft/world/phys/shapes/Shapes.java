package net.minecraft.world.phys.shapes;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import com.google.common.math.DoubleMath;
import com.google.common.math.IntMath;
import com.mojang.math.OctahedralGroup;
import com.mojang.math.Quadrant;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import net.minecraft.Util;
import net.minecraft.core.AxisCycle;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class Shapes {
	public static final double EPSILON = 1.0E-7;
	public static final double BIG_EPSILON = 1.0E-6;
	private static final VoxelShape BLOCK = Util.make(() -> {
		DiscreteVoxelShape discreteVoxelShape = new BitSetDiscreteVoxelShape(1, 1, 1);
		discreteVoxelShape.fill(0, 0, 0);
		return new CubeVoxelShape(discreteVoxelShape);
	});
	private static final Vec3 BLOCK_CENTER = new Vec3(0.5, 0.5, 0.5);
	public static final VoxelShape INFINITY = box(
		Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY
	);
	private static final VoxelShape EMPTY = new ArrayVoxelShape(
		new BitSetDiscreteVoxelShape(0, 0, 0), new DoubleArrayList(new double[]{0.0}), new DoubleArrayList(new double[]{0.0}), new DoubleArrayList(new double[]{0.0})
	);

	public static VoxelShape empty() {
		return EMPTY;
	}

	public static VoxelShape block() {
		return BLOCK;
	}

	public static VoxelShape box(double d, double e, double f, double g, double h, double i) {
		if (!(d > g) && !(e > h) && !(f > i)) {
			return create(d, e, f, g, h, i);
		} else {
			throw new IllegalArgumentException("The min values need to be smaller or equals to the max values");
		}
	}

	public static VoxelShape create(double d, double e, double f, double g, double h, double i) {
		if (!(g - d < 1.0E-7) && !(h - e < 1.0E-7) && !(i - f < 1.0E-7)) {
			int j = findBits(d, g);
			int k = findBits(e, h);
			int l = findBits(f, i);
			if (j < 0 || k < 0 || l < 0) {
				return new ArrayVoxelShape(
					BLOCK.shape, DoubleArrayList.wrap(new double[]{d, g}), DoubleArrayList.wrap(new double[]{e, h}), DoubleArrayList.wrap(new double[]{f, i})
				);
			} else if (j == 0 && k == 0 && l == 0) {
				return block();
			} else {
				int m = 1 << j;
				int n = 1 << k;
				int o = 1 << l;
				BitSetDiscreteVoxelShape bitSetDiscreteVoxelShape = BitSetDiscreteVoxelShape.withFilledBounds(
					m, n, o, (int)Math.round(d * m), (int)Math.round(e * n), (int)Math.round(f * o), (int)Math.round(g * m), (int)Math.round(h * n), (int)Math.round(i * o)
				);
				return new CubeVoxelShape(bitSetDiscreteVoxelShape);
			}
		} else {
			return empty();
		}
	}

	public static VoxelShape create(AABB aABB) {
		return create(aABB.minX, aABB.minY, aABB.minZ, aABB.maxX, aABB.maxY, aABB.maxZ);
	}

	@VisibleForTesting
	protected static int findBits(double d, double e) {
		if (!(d < -1.0E-7) && !(e > 1.0000001)) {
			for (int i = 0; i <= 3; i++) {
				int j = 1 << i;
				double f = d * j;
				double g = e * j;
				boolean bl = Math.abs(f - Math.round(f)) < 1.0E-7 * j;
				boolean bl2 = Math.abs(g - Math.round(g)) < 1.0E-7 * j;
				if (bl && bl2) {
					return i;
				}
			}

			return -1;
		} else {
			return -1;
		}
	}

	protected static long lcm(int i, int j) {
		return (long)i * (j / IntMath.gcd(i, j));
	}

	public static VoxelShape or(VoxelShape voxelShape, VoxelShape voxelShape2) {
		return join(voxelShape, voxelShape2, BooleanOp.OR);
	}

	public static VoxelShape or(VoxelShape voxelShape, VoxelShape... voxelShapes) {
		return (VoxelShape)Arrays.stream(voxelShapes).reduce(voxelShape, Shapes::or);
	}

	public static VoxelShape join(VoxelShape voxelShape, VoxelShape voxelShape2, BooleanOp booleanOp) {
		return joinUnoptimized(voxelShape, voxelShape2, booleanOp).optimize();
	}

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
				BitSetDiscreteVoxelShape bitSetDiscreteVoxelShape = BitSetDiscreteVoxelShape.join(
					voxelShape.shape, voxelShape2.shape, indexMerger, indexMerger2, indexMerger3, booleanOp
				);
				return (VoxelShape)(indexMerger instanceof DiscreteCubeMerger && indexMerger2 instanceof DiscreteCubeMerger && indexMerger3 instanceof DiscreteCubeMerger
					? new CubeVoxelShape(bitSetDiscreteVoxelShape)
					: new ArrayVoxelShape(bitSetDiscreteVoxelShape, indexMerger.getList(), indexMerger2.getList(), indexMerger3.getList()));
			}
		}
	}

	public static boolean joinIsNotEmpty(VoxelShape voxelShape, VoxelShape voxelShape2, BooleanOp booleanOp) {
		if (booleanOp.apply(false, false)) {
			throw (IllegalArgumentException)Util.pauseInIde(new IllegalArgumentException());
		} else {
			boolean bl = voxelShape.isEmpty();
			boolean bl2 = voxelShape2.isEmpty();
			if (!bl && !bl2) {
				if (voxelShape == voxelShape2) {
					return booleanOp.apply(true, true);
				} else {
					boolean bl3 = booleanOp.apply(true, false);
					boolean bl4 = booleanOp.apply(false, true);

					for (Direction.Axis axis : AxisCycle.AXIS_VALUES) {
						if (voxelShape.max(axis) < voxelShape2.min(axis) - 1.0E-7) {
							return bl3 || bl4;
						}

						if (voxelShape2.max(axis) < voxelShape.min(axis) - 1.0E-7) {
							return bl3 || bl4;
						}
					}

					IndexMerger indexMerger = createIndexMerger(1, voxelShape.getCoords(Direction.Axis.X), voxelShape2.getCoords(Direction.Axis.X), bl3, bl4);
					IndexMerger indexMerger2 = createIndexMerger(
						indexMerger.size() - 1, voxelShape.getCoords(Direction.Axis.Y), voxelShape2.getCoords(Direction.Axis.Y), bl3, bl4
					);
					IndexMerger indexMerger3 = createIndexMerger(
						(indexMerger.size() - 1) * (indexMerger2.size() - 1), voxelShape.getCoords(Direction.Axis.Z), voxelShape2.getCoords(Direction.Axis.Z), bl3, bl4
					);
					return joinIsNotEmpty(indexMerger, indexMerger2, indexMerger3, voxelShape.shape, voxelShape2.shape, booleanOp);
				}
			} else {
				return booleanOp.apply(!bl, !bl2);
			}
		}
	}

	private static boolean joinIsNotEmpty(
		IndexMerger indexMerger,
		IndexMerger indexMerger2,
		IndexMerger indexMerger3,
		DiscreteVoxelShape discreteVoxelShape,
		DiscreteVoxelShape discreteVoxelShape2,
		BooleanOp booleanOp
	) {
		return !indexMerger.forMergedIndexes(
			(i, j, k) -> indexMerger2.forMergedIndexes(
				(kx, l, m) -> indexMerger3.forMergedIndexes(
					(mx, n, o) -> !booleanOp.apply(discreteVoxelShape.isFullWide(i, kx, mx), discreteVoxelShape2.isFullWide(j, l, n))
				)
			)
		);
	}

	public static double collide(Direction.Axis axis, AABB aABB, Iterable<VoxelShape> iterable, double d) {
		for (VoxelShape voxelShape : iterable) {
			if (Math.abs(d) < 1.0E-7) {
				return 0.0;
			}

			d = voxelShape.collide(axis, aABB, d);
		}

		return d;
	}

	public static boolean blockOccludes(VoxelShape voxelShape, VoxelShape voxelShape2, Direction direction) {
		if (voxelShape == block() && voxelShape2 == block()) {
			return true;
		} else if (voxelShape2.isEmpty()) {
			return false;
		} else {
			Direction.Axis axis = direction.getAxis();
			Direction.AxisDirection axisDirection = direction.getAxisDirection();
			VoxelShape voxelShape3 = axisDirection == Direction.AxisDirection.POSITIVE ? voxelShape : voxelShape2;
			VoxelShape voxelShape4 = axisDirection == Direction.AxisDirection.POSITIVE ? voxelShape2 : voxelShape;
			BooleanOp booleanOp = axisDirection == Direction.AxisDirection.POSITIVE ? BooleanOp.ONLY_FIRST : BooleanOp.ONLY_SECOND;
			return DoubleMath.fuzzyEquals(voxelShape3.max(axis), 1.0, 1.0E-7)
				&& DoubleMath.fuzzyEquals(voxelShape4.min(axis), 0.0, 1.0E-7)
				&& !joinIsNotEmpty(new SliceShape(voxelShape3, axis, voxelShape3.shape.getSize(axis) - 1), new SliceShape(voxelShape4, axis, 0), booleanOp);
		}
	}

	public static boolean mergedFaceOccludes(VoxelShape voxelShape, VoxelShape voxelShape2, Direction direction) {
		if (voxelShape != block() && voxelShape2 != block()) {
			Direction.Axis axis = direction.getAxis();
			Direction.AxisDirection axisDirection = direction.getAxisDirection();
			VoxelShape voxelShape3 = axisDirection == Direction.AxisDirection.POSITIVE ? voxelShape : voxelShape2;
			VoxelShape voxelShape4 = axisDirection == Direction.AxisDirection.POSITIVE ? voxelShape2 : voxelShape;
			if (!DoubleMath.fuzzyEquals(voxelShape3.max(axis), 1.0, 1.0E-7)) {
				voxelShape3 = empty();
			}

			if (!DoubleMath.fuzzyEquals(voxelShape4.min(axis), 0.0, 1.0E-7)) {
				voxelShape4 = empty();
			}

			return !joinIsNotEmpty(
				block(),
				joinUnoptimized(new SliceShape(voxelShape3, axis, voxelShape3.shape.getSize(axis) - 1), new SliceShape(voxelShape4, axis, 0), BooleanOp.OR),
				BooleanOp.ONLY_FIRST
			);
		} else {
			return true;
		}
	}

	public static boolean faceShapeOccludes(VoxelShape voxelShape, VoxelShape voxelShape2) {
		if (voxelShape == block() || voxelShape2 == block()) {
			return true;
		} else {
			return voxelShape.isEmpty() && voxelShape2.isEmpty()
				? false
				: !joinIsNotEmpty(block(), joinUnoptimized(voxelShape, voxelShape2, BooleanOp.OR), BooleanOp.ONLY_FIRST);
		}
	}

	@VisibleForTesting
	protected static IndexMerger createIndexMerger(int i, DoubleList doubleList, DoubleList doubleList2, boolean bl, boolean bl2) {
		int j = doubleList.size() - 1;
		int k = doubleList2.size() - 1;
		if (doubleList instanceof CubePointRange && doubleList2 instanceof CubePointRange) {
			long l = lcm(j, k);
			if (i * l <= 256L) {
				return new DiscreteCubeMerger(j, k);
			}
		}

		if (doubleList.getDouble(j) < doubleList2.getDouble(0) - 1.0E-7) {
			return new NonOverlappingMerger(doubleList, doubleList2, false);
		} else if (doubleList2.getDouble(k) < doubleList.getDouble(0) - 1.0E-7) {
			return new NonOverlappingMerger(doubleList2, doubleList, true);
		} else {
			return (IndexMerger)(j == k && Objects.equals(doubleList, doubleList2)
				? new IdenticalMerger(doubleList)
				: new IndirectMerger(doubleList, doubleList2, bl, bl2));
		}
	}

	public static VoxelShape rotate(VoxelShape voxelShape, OctahedralGroup octahedralGroup) {
		return rotate(voxelShape, octahedralGroup, BLOCK_CENTER);
	}

	public static VoxelShape rotate(VoxelShape voxelShape, OctahedralGroup octahedralGroup, Vec3 vec3) {
		if (octahedralGroup == OctahedralGroup.IDENTITY) {
			return voxelShape;
		} else {
			DiscreteVoxelShape discreteVoxelShape = voxelShape.shape.rotate(octahedralGroup);
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
					makeAxis(doubleList, bl4, vec3.get(axis), vec3.x),
					makeAxis(doubleList2, bl5, vec3.get(axis2), vec3.y),
					makeAxis(doubleList3, bl6, vec3.get(axis3), vec3.z)
				);
			}
		}
	}

	@VisibleForTesting
	static DoubleList makeAxis(DoubleList doubleList, boolean bl, double d, double e) {
		if (!bl && d == e) {
			return doubleList;
		} else {
			int i = doubleList.size();
			DoubleList doubleList2 = new DoubleArrayList(i);
			int j = bl ? -1 : 1;

			for (int k = bl ? i - 1 : 0; k >= 0 && k < i; k += j) {
				doubleList2.add(e + j * (doubleList.getDouble(k) - d));
			}

			return doubleList2;
		}
	}

	public static boolean equal(VoxelShape voxelShape, VoxelShape voxelShape2) {
		return !joinIsNotEmpty(voxelShape, voxelShape2, BooleanOp.NOT_SAME);
	}

	public static Map<Direction.Axis, VoxelShape> rotateHorizontalAxis(VoxelShape voxelShape) {
		return rotateHorizontalAxis(voxelShape, BLOCK_CENTER);
	}

	public static Map<Direction.Axis, VoxelShape> rotateHorizontalAxis(VoxelShape voxelShape, Vec3 vec3) {
		return Maps.newEnumMap(
			Map.of(Direction.Axis.Z, voxelShape, Direction.Axis.X, rotate(voxelShape, OctahedralGroup.fromXYAngles(Quadrant.R0, Quadrant.R90), vec3))
		);
	}

	public static Map<Direction.Axis, VoxelShape> rotateAllAxis(VoxelShape voxelShape) {
		return rotateAllAxis(voxelShape, BLOCK_CENTER);
	}

	public static Map<Direction.Axis, VoxelShape> rotateAllAxis(VoxelShape voxelShape, Vec3 vec3) {
		return Maps.newEnumMap(
			Map.of(
				Direction.Axis.Z,
				voxelShape,
				Direction.Axis.X,
				rotate(voxelShape, OctahedralGroup.fromXYAngles(Quadrant.R0, Quadrant.R90), vec3),
				Direction.Axis.Y,
				rotate(voxelShape, OctahedralGroup.fromXYAngles(Quadrant.R90, Quadrant.R0), vec3)
			)
		);
	}

	public static Map<Direction, VoxelShape> rotateHorizontal(VoxelShape voxelShape) {
		return rotateHorizontal(voxelShape, BLOCK_CENTER);
	}

	public static Map<Direction, VoxelShape> rotateHorizontal(VoxelShape voxelShape, Vec3 vec3) {
		return Maps.newEnumMap(
			Map.of(
				Direction.NORTH,
				voxelShape,
				Direction.EAST,
				rotate(voxelShape, OctahedralGroup.fromXYAngles(Quadrant.R0, Quadrant.R90), vec3),
				Direction.SOUTH,
				rotate(voxelShape, OctahedralGroup.fromXYAngles(Quadrant.R0, Quadrant.R180), vec3),
				Direction.WEST,
				rotate(voxelShape, OctahedralGroup.fromXYAngles(Quadrant.R0, Quadrant.R270), vec3)
			)
		);
	}

	public static Map<Direction, VoxelShape> rotateAll(VoxelShape voxelShape) {
		return rotateAll(voxelShape, BLOCK_CENTER);
	}

	public static Map<Direction, VoxelShape> rotateAll(VoxelShape voxelShape, Vec3 vec3) {
		return Maps.newEnumMap(
			Map.of(
				Direction.NORTH,
				voxelShape,
				Direction.EAST,
				rotate(voxelShape, OctahedralGroup.fromXYAngles(Quadrant.R0, Quadrant.R90), vec3),
				Direction.SOUTH,
				rotate(voxelShape, OctahedralGroup.fromXYAngles(Quadrant.R0, Quadrant.R180), vec3),
				Direction.WEST,
				rotate(voxelShape, OctahedralGroup.fromXYAngles(Quadrant.R0, Quadrant.R270), vec3),
				Direction.UP,
				rotate(voxelShape, OctahedralGroup.fromXYAngles(Quadrant.R270, Quadrant.R0), vec3),
				Direction.DOWN,
				rotate(voxelShape, OctahedralGroup.fromXYAngles(Quadrant.R90, Quadrant.R0), vec3)
			)
		);
	}

	public static Map<AttachFace, Map<Direction, VoxelShape>> rotateAttachFace(VoxelShape voxelShape) {
		return Map.of(
			AttachFace.WALL,
			rotateHorizontal(voxelShape),
			AttachFace.FLOOR,
			rotateHorizontal(rotate(voxelShape, OctahedralGroup.fromXYAngles(Quadrant.R270, Quadrant.R0))),
			AttachFace.CEILING,
			rotateHorizontal(rotate(voxelShape, OctahedralGroup.fromXYAngles(Quadrant.R90, Quadrant.R180)))
		);
	}

	public interface DoubleLineConsumer {
		void consume(double d, double e, double f, double g, double h, double i);
	}
}
