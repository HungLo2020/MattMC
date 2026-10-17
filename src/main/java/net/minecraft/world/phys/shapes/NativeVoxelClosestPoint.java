package net.minecraft.world.phys.shapes;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.phys.Vec3;

/** Complete numeric query; current inputs copied, independently owned Vec3 returned. */
final class NativeVoxelClosestPoint {
    static final int MIN_CELLS = 512;
    private static final MethodHandle FIND = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_voxel_closest_point",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
            ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS));
    private static final MethodHandle BOX = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_voxel_closest_box",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
            ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
            ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS));
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);
    private NativeVoxelClosestPoint() {}
    private static final class Scratch {
        final Arena arena = Arena.ofAuto();
        final MemorySegment words = arena.allocate(8192, 8);
        final MemorySegment coordinates = arena.allocate(3 * 257 * 8, 8);
        final double[] coordinateValues = new double[3 * 257];
        final MemorySegment coordinateSegment = MemorySegment.ofArray(coordinateValues);
        final MemorySegment output = arena.allocate(24, 8);
        boolean inUse;
    }

    /** null means original virtual/callback/IEEE compatibility path. */
    static Optional<Vec3> find(VoxelShape source, Vec3 point) {
        if (point == null || point.getClass() != Vec3.class || !Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)
            || (source.getClass() != ArrayVoxelShape.class && source.getClass() != CubeVoxelShape.class)
            || source.shape.getClass() != BitSetDiscreteVoxelShape.class) return null;
        var grid = (BitSetDiscreteVoxelShape)source.shape;
        int nx = grid.xSize, ny = grid.ySize, nz = grid.zSize;
        long cells = (long)nx * ny * nz;
        if (nx < 1 || ny < 1 || nz < 1 || nx > 256 || ny > 256 || nz > 256
            || cells < MIN_CELLS || cells > 65536 || grid.storage.length() < 0 || grid.storage.length() > 65536) return null;
        var x = source.getCoords(Direction.Axis.X);
        var y = source.getCoords(Direction.Axis.Y);
        var z = source.getCoords(Direction.Axis.Z);
        // No custom list reads, mutations, or exceptions can be moved across the snapshot.
        if (!supported(x, nx) || !supported(y, ny) || !supported(z, nz)) return null;
        Scratch s = SCRATCH.get();
        if (s.inUse) return null;
        // Exact occupancy proof, not a density heuristic or remembered result.
        boolean full = grid.storage.nextClearBit(0) >= cells;
        boolean cuboid = full || isCuboid(grid, cells);
        s.inUse = true;
        try {
            if (cuboid) {
                // Retain the original clone (including BitSet's source-capacity side effect).
                var snapshot = new BitSetDiscreteVoxelShape(grid);
                int result = (int)BOX.invokeExact(point.x, point.y, point.z,
                    x.getDouble(full ? 0 : grid.xMin), y.getDouble(full ? 0 : grid.yMin), z.getDouble(full ? 0 : grid.zMin),
                    x.getDouble(full ? nx : grid.xMax), y.getDouble(full ? ny : grid.yMax), z.getDouble(full ? nz : grid.zMax), s.output);
                return result(s, result, grid);
            }
            int cubes = copy(x, nx, s.coordinateValues, 0) | copy(y, ny, s.coordinateValues, nx + 1) << 1
                | copy(z, nz, s.coordinateValues, nx + ny + 2) << 2;
            if (cubes != 7) MemorySegment.copy(s.coordinateSegment, 0, s.coordinates, 0, (nx + ny + nz + 3) * 8L);
            // Keep the original BitSet clone and bounds reads; only the numeric consumer moves.
            var snapshot = new BitSetDiscreteVoxelShape(grid);
            long[] words = snapshot.storage.toLongArray();
            MemorySegment.copy(MemorySegment.ofArray(words), 0, s.words, 0, words.length * 8L);
            int result = (int)FIND.invokeExact(s.words, words.length, nx, ny, nz, s.coordinates,
                nx + ny + nz + 3, cubes, point.x, point.y, point.z, s.output);
            return result(s, result, grid);
        } catch (RuntimeException | Error e) { throw e; }
        catch (Throwable e) { throw new IllegalStateException("Native voxel closest point failed", e); }
        finally { s.inUse = false; }
    }
    /** Stored bounds are hints only: prove every bounded cell and the exact total. */
    private static boolean isCuboid(BitSetDiscreteVoxelShape g, long cells) {
        int dx=g.xMax-g.xMin, dy=g.yMax-g.yMin, dz=g.zMax-g.zMin;
        if(g.xMin<0 || g.yMin<0 || g.zMin<0 || g.xMin>=g.xMax || g.yMin>=g.yMax || g.zMin>=g.zMax
            || dx<1 || dy<1 || dz<1
            || g.xMax>g.xSize || g.yMax>g.ySize || g.zMax>g.zSize || (long)dx*dy>256) return false;
        long volume=(long)dx*dy*dz;
        // Missing cells in a full-grid bounding box cannot be a single cuboid.
        if(volume>=cells || g.storage.cardinality()!=volume) return false;
        if (g.zMin == 0 && g.zMax == g.zSize) {
            // Full-Z rows are contiguous: prove the whole Y block once per X.
            for (int x = g.xMin; x < g.xMax; x++) {
                int start = g.getIndex(x, g.yMin, 0), end = g.getIndex(x, g.yMax, 0);
                if (g.storage.nextClearBit(start) < end) return false;
            }
        } else {
            for (int x = g.xMin; x < g.xMax; x++) for (int y = g.yMin; y < g.yMax; y++) {
                int start = g.getIndex(x, y, g.zMin), end = g.getIndex(x, y, g.zMax);
                if (g.storage.nextClearBit(start) < end) return false;
            }
        }
        return true;
    }
    private static Optional<Vec3> result(Scratch s, int result, BitSetDiscreteVoxelShape grid) {
        if (result == -2) return null;
        // A logically empty grid with only out-of-range stored bits makes
        // the original nonempty caller throw Optional.of(null). Preserve it.
        if (result == 0) return grid.storage.isEmpty() ? Optional.empty() : null;
        if (result != 1) throw new IllegalStateException("Invalid native closest point status: " + result);
        return Optional.of(new Vec3(s.output.get(ValueLayout.JAVA_DOUBLE, 0),
            s.output.get(ValueLayout.JAVA_DOUBLE, 8), s.output.get(ValueLayout.JAVA_DOUBLE, 16)));
    }
    private static boolean supported(it.unimi.dsi.fastutil.doubles.DoubleList list, int size) {
        return (list.getClass() == DoubleArrayList.class || list.getClass() == CubePointRange.class) && list.size() == size + 1;
    }
    private static int copy(it.unimi.dsi.fastutil.doubles.DoubleList list, int size, double[] target, int offset) {
        if (list.getClass() == CubePointRange.class) return 1;
        System.arraycopy(((DoubleArrayList)list).elements(), 0, target, offset, size + 1);
        return 0;
    }
}
