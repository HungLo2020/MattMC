package net.minecraft.world.phys.shapes;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Ordered outside-ray query. Java retains ownership, prechecks and compatibility. */
final class NativeVoxelRaycast {
    static final int MIN_CELLS = 512;
    private static final MethodHandle CLIP = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_voxel_ray_packet",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle BOX = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_voxel_ray_box_packet",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);
    private static final Direction[] DIRECTIONS = Direction.values();

    private NativeVoxelRaycast() {}

    private static final class Scratch {
        final Arena arena = Arena.ofAuto();
        final MemorySegment packet = arena.allocate(14504, 8);
        final MemorySegment words = packet.asSlice(0, 8192);
        final MemorySegment coordinates = packet.asSlice(8192, 6168);
        final double[] coordinateValues = new double[3 * 257];
        final MemorySegment coordinateSegment = MemorySegment.ofArray(coordinateValues);
        final MemorySegment ray = packet.asSlice(14360, 72);
        final double[] rayValues = new double[9];
        final MemorySegment raySegment = MemorySegment.ofArray(rayValues);
        final MemorySegment bounds = packet.asSlice(14432, 48);
        final MemorySegment output = packet.asSlice(14480, 24);
        boolean inUse;
    }

    /** null declines; Optional.empty is a handled miss. Only called after original inside precheck. */
    static Optional<BlockHitResult> clip(VoxelShape source, Vec3 start, Vec3 end, BlockPos position) {
        return clip(source, start, end, position, null);
    }

    /** A proof about the original probe lets pure built-in shapes skip other axis searches. */
    static Optional<BlockHitResult> clipFromProbe(VoxelShape source, Vec3 start, Vec3 end, BlockPos position, Vec3 probe) {
        return clip(source, start, end, position, probe);
    }

    private static Optional<BlockHitResult> clip(VoxelShape source, Vec3 start, Vec3 end, BlockPos position, Vec3 probe) {
        if (!supportedInvocation(source, start, end, position)) return null;
        var grid = (BitSetDiscreteVoxelShape)source.shape;
        if (!supportedGrid(grid)) return null;
        int nx = grid.xSize, ny = grid.ySize, nz = grid.zSize;
        var x = source.getCoords(Direction.Axis.X);
        var y = source.getCoords(Direction.Axis.Y);
        var z = source.getCoords(Direction.Axis.Z);
        if (!supported(x, nx) || !supported(y, ny) || !supported(z, nz)) return null;
        if (probe != null && inside(source, grid, probe, position, x, y, z))
            return Optional.of(new BlockHitResult(probe,
                Direction.getApproximateNearest(end.subtract(start)).getOpposite(), position, true));
        Scratch scratch = SCRATCH.get();
        if (scratch.inUse) return null;
        return execute(scratch, grid, start, end, position, x, y, z);
    }

    private static boolean inside(VoxelShape source, BitSetDiscreteVoxelShape grid, Vec3 probe, BlockPos position,
        DoubleList x, DoubleList y, DoubleList z) {
        if (outside(x, grid.xSize, probe.x - position.getX()) || outside(y, grid.ySize, probe.y - position.getY())
            || outside(z, grid.zSize, probe.z - position.getZ())) return false;
        return grid.isFullWide(source.findIndex(Direction.Axis.X, probe.x - position.getX()),
            source.findIndex(Direction.Axis.Y, probe.y - position.getY()),
            source.findIndex(Direction.Axis.Z, probe.z - position.getZ()));
    }

    private static boolean supportedInvocation(VoxelShape source, Vec3 start, Vec3 end, BlockPos position) {
        if (start == null || end == null || position == null || start.getClass() != Vec3.class || end.getClass() != Vec3.class
            || (position.getClass() != BlockPos.class && position.getClass() != BlockPos.MutableBlockPos.class)
            || (source.getClass() != ArrayVoxelShape.class && source.getClass() != CubeVoxelShape.class)
            || source.shape.getClass() != BitSetDiscreteVoxelShape.class) return false;
        return true;
    }

    private static boolean supportedGrid(BitSetDiscreteVoxelShape grid) {
        if (grid.storage.getClass() != java.util.BitSet.class) return false;
        int nx = grid.xSize, ny = grid.ySize, nz = grid.zSize;
        long cells = (long)nx * ny * nz;
        if (nx < 1 || ny < 1 || nz < 1 || nx > 256 || ny > 256 || nz > 256
            || cells < MIN_CELLS || cells > 65536 || grid.storage.length() > 65536) return false;
        return true;
    }

    private static Optional<BlockHitResult> execute(Scratch scratch, BitSetDiscreteVoxelShape grid,
        Vec3 start, Vec3 end, BlockPos position, DoubleList x, DoubleList y, DoubleList z) {
        boolean full = grid.storage.nextClearBit(0) >= (long)grid.xSize * grid.ySize * grid.zSize;
        return full || isCuboid(grid, (long)grid.xSize * grid.ySize * grid.zSize)
            ? executeBox(scratch, grid, full, start, end, position, x, y, z)
            : executeGrid(scratch, grid, start, end, position, x, y, z);
    }

    // Distinct hot methods keep the grid call's compilation profile independent
    // of frequently queried full boxes. Both have the same ownership guard.
    private static Optional<BlockHitResult> executeBox(Scratch scratch, BitSetDiscreteVoxelShape grid,
        boolean full, Vec3 start, Vec3 end, BlockPos position, DoubleList x, DoubleList y, DoubleList z) {
        try {
            prepare(scratch, grid, start, end, position);
            return result(scratch, clipBox(scratch, grid, full, x, y, z), position);
        } catch (RuntimeException | Error e) { throw e; }
        catch (Throwable e) { throw new IllegalStateException("Native voxel ray intersection failed", e); }
        finally { scratch.inUse = false; }
    }

    private static Optional<BlockHitResult> executeGrid(Scratch scratch, BitSetDiscreteVoxelShape grid,
        Vec3 start, Vec3 end, BlockPos position, DoubleList x, DoubleList y, DoubleList z) {
        try {
            prepare(scratch, grid, start, end, position);
            return result(scratch, clipGrid(scratch, grid.storage, grid.xSize, grid.ySize, grid.zSize, x, y, z), position);
        } catch (RuntimeException | Error e) { throw e; }
        catch (Throwable e) { throw new IllegalStateException("Native voxel ray intersection failed", e); }
        finally { scratch.inUse = false; }
    }

    private static void prepare(Scratch scratch, BitSetDiscreteVoxelShape grid,
        Vec3 start, Vec3 end, BlockPos position) {
        scratch.inUse = true;
        // Preserve original source-capacity trimming. A fresh toLongArray
        // is the occupancy snapshot; the unused clone may be eliminated.
        grid.storage.clone();
        writeRay(scratch, start, end, position);
    }

    private static Optional<BlockHitResult> result(Scratch scratch, int status, BlockPos position) {
        if (status == -2) return null;
        if (status == 0) return Optional.empty();
        if (status < 1 || status > 6) throw new IllegalStateException("Invalid native ray intersection status: " + status);
        var point = new Vec3(scratch.output.get(ValueLayout.JAVA_DOUBLE, 0),
            scratch.output.get(ValueLayout.JAVA_DOUBLE, 8), scratch.output.get(ValueLayout.JAVA_DOUBLE, 16));
        return Optional.of(new BlockHitResult(point, DIRECTIONS[status - 1], position, false));
    }

    private static void writeRay(Scratch s, Vec3 start, Vec3 end, BlockPos position) {
        double[] v = s.rayValues;
        v[0] = start.x; v[1] = start.y; v[2] = start.z;
        v[3] = end.x; v[4] = end.y; v[5] = end.z;
        v[6] = position.getX(); v[7] = position.getY(); v[8] = position.getZ();
        MemorySegment.copy(s.raySegment, 0, s.ray, 0, 72);
    }

    private static int clipBox(Scratch s, BitSetDiscreteVoxelShape grid, boolean full,
        DoubleList x, DoubleList y, DoubleList z) throws Throwable {
        s.bounds.set(ValueLayout.JAVA_DOUBLE, 0, x.getDouble(full ? 0 : grid.xMin));
        s.bounds.set(ValueLayout.JAVA_DOUBLE, 8, y.getDouble(full ? 0 : grid.yMin));
        s.bounds.set(ValueLayout.JAVA_DOUBLE, 16, z.getDouble(full ? 0 : grid.zMin));
        s.bounds.set(ValueLayout.JAVA_DOUBLE, 24, x.getDouble(full ? grid.xSize : grid.xMax));
        s.bounds.set(ValueLayout.JAVA_DOUBLE, 32, y.getDouble(full ? grid.ySize : grid.yMax));
        s.bounds.set(ValueLayout.JAVA_DOUBLE, 40, z.getDouble(full ? grid.zSize : grid.zMax));
        return (int)BOX.invokeExact(s.packet);
    }

    private static int clipGrid(Scratch s, java.util.BitSet snapshot, int nx, int ny, int nz,
        DoubleList x, DoubleList y, DoubleList z) throws Throwable {
        int cubes = copy(x, nx, s.coordinateValues, 0) | copy(y, ny, s.coordinateValues, nx + 1) << 1
            | copy(z, nz, s.coordinateValues, nx + ny + 2) << 2;
        int coordinateLength = nx + ny + nz + 3;
        if (cubes != 7) MemorySegment.copy(s.coordinateSegment, 0, s.coordinates, 0, coordinateLength * 8L);
        long[] words = snapshot.toLongArray();
        MemorySegment.copy(MemorySegment.ofArray(words), 0, s.words, 0, words.length * 8L);
        return (int)CLIP.invokeExact(s.packet, words.length, nx, ny, nz, coordinateLength, cubes);
    }

    /** For Array shapes every binary-search predicate is then true or false.
     * Cube findIndex's clamped floor has the same outside result. Endpoint
     * hints never suffice: inspect every array value, including NaNs/reversals.
     * Exact supported classes have no observable coordinate getter callbacks. */
    private static boolean outside(DoubleList coords, int parts, double value) {
        if (coords.getClass() == CubePointRange.class) return value < 0.0 || value >= 1.0;
        double[] values = ((DoubleArrayList)coords).elements();
        if (value < values[0]) {
            for (int i = 1; i <= parts; i++) if (!(value < values[i])) return false;
            return true;
        }
        if (value >= values[parts]) {
            for (int i = 0; i < parts; i++) if (!(value >= values[i])) return false;
            return true;
        }
        return false;
    }

    private static boolean supported(DoubleList list, int parts) {
        return list.getClass() == DoubleArrayList.class ? ((DoubleArrayList)list).size() == parts + 1
            : list.getClass() == CubePointRange.class && ((CubePointRange)list).size() == parts + 1;
    }

    private static int copy(DoubleList list, int parts, double[] target, int offset) {
        if (list.getClass() == CubePointRange.class) return 1;
        System.arraycopy(((DoubleArrayList)list).elements(), 0, target, offset, parts + 1);
        return 0;
    }

    /** Exact occupancy proof. Stale stored bounds never restrict the general traversal. */
    private static boolean isCuboid(BitSetDiscreteVoxelShape g, long cells) {
        int dx = g.xMax - g.xMin, dy = g.yMax - g.yMin, dz = g.zMax - g.zMin;
        if (g.xMin < 0 || g.yMin < 0 || g.zMin < 0 || g.xMin >= g.xMax || g.yMin >= g.yMax || g.zMin >= g.zMax
            || dx < 1 || dy < 1 || dz < 1 || g.xMax > g.xSize || g.yMax > g.ySize || g.zMax > g.zSize
            || (long)dx * dy > 256) return false;
        long volume = (long)dx * dy * dz;
        if (volume >= cells || g.storage.cardinality() != volume) return false;
        return cuboidRows(g);
    }

    private static boolean cuboidRows(BitSetDiscreteVoxelShape g) {
        if (g.zMin == 0 && g.zMax == g.zSize) {
            for (int x = g.xMin; x < g.xMax; x++) {
                if (g.storage.nextClearBit(g.getIndex(x, g.yMin, 0)) < g.getIndex(x, g.yMax, 0)) return false;
            }
        } else {
            for (int x = g.xMin; x < g.xMax; x++) for (int y = g.yMin; y < g.yMax; y++) {
                if (g.storage.nextClearBit(g.getIndex(x, y, g.zMin)) < g.getIndex(x, y, g.zMax)) return false;
            }
        }
        return true;
    }
}
