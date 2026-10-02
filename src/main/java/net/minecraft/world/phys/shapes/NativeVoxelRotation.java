package net.minecraft.world.phys.shapes;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.BitSet;
import net.math.OctahedralGroup;
import net.minecraft.core.Direction;
import net.minecraft.util.NativeLibraryLoader;

/** Whole packed rotation; Java retains group semantics and original result ownership. */
final class NativeVoxelRotation {
    static final int MIN_CELLS = 512;
    static final int MAX_CELLS = 65536;
    private static final MethodHandle ROTATE = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_voxel_rotate",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);
    private NativeVoxelRotation() {}
    private static final class Scratch {
        final Arena arena = Arena.ofAuto();
        final MemorySegment input = arena.allocate(1024 * 8, 8), output = arena.allocate(1024 * 8, 8);
        final MemorySegment bounds = arena.allocate(6 * 4, 4);
        final int[] limits = new int[6];
        boolean inUse;
    }

    /** Null selects the untouched original loop before any custom-grid callbacks. */
    static BitSetDiscreteVoxelShape rotate(DiscreteVoxelShape source, OctahedralGroup group) {
        if (source.getClass() != BitSetDiscreteVoxelShape.class || group == null || group == OctahedralGroup.IDENTITY) return null;
        long cells = (long)source.xSize * source.ySize * source.zSize;
        var grid = (BitSetDiscreteVoxelShape)source;
        int storedBits = grid.storage.length();
        if (source.xSize < 1 || source.ySize < 1 || source.zSize < 1
            || source.xSize > 256 || source.ySize > 256 || source.zSize > 256
            || cells < MIN_CELLS || cells > MAX_CELLS || storedBits < 0 || storedBits > MAX_CELLS) return null;
        Direction.Axis x = group.permute(Direction.Axis.X), y = group.permute(Direction.Axis.Y), z = group.permute(Direction.Axis.Z);
        int nx = x.choose(source.xSize, source.ySize, source.zSize);
        int ny = y.choose(source.xSize, source.ySize, source.zSize);
        int nz = z.choose(source.xSize, source.ySize, source.zSize);
        boolean a = group.inverts(x), b = group.inverts(y), c = group.inverts(z);
        int control = x.ordinal() | y.ordinal() << 2 | z.ordinal() << 4;
        if (x.choose(a,b,c)) control |= 1 << 6;
        if (y.choose(a,b,c)) control |= 1 << 7;
        if (z.choose(a,b,c)) control |= 1 << 8;
        var result = new BitSetDiscreteVoxelShape(nx, ny, nz);
        var scratch = SCRATCH.get();
        if (scratch.inUse) return null;
        scratch.inUse = true;
        try {
            long[] words = grid.storage.toLongArray();
            MemorySegment.copy(MemorySegment.ofArray(words), 0, scratch.input, 0, words.length * 8L);
            int length = ((int)cells + 63) / 64;
            int status = (int)ROTATE.invokeExact(scratch.input, words.length, source.xSize, source.ySize, source.zSize,
                control, scratch.output, length, scratch.bounds);
            if (status != 0) throw new IllegalStateException("Invalid native voxel rotation: " + status);
            MemorySegment.copy(scratch.bounds, 0, MemorySegment.ofArray(scratch.limits), 0, 24);
            // Empty native output is already represented by the original fresh
            // constructor. Keep the downcall, but avoid redundant zero arrays/BitSets.
            if (scratch.limits[3] == 0) return result;
            long[] output = new long[length];
            MemorySegment.copy(scratch.output, 0, MemorySegment.ofArray(output), 0, length * 8L);
            result.storage.or(BitSet.valueOf(output));
            int[] bounds = scratch.limits;
            result.xMin=bounds[0]; result.yMin=bounds[1]; result.zMin=bounds[2];
            result.xMax=bounds[3]; result.yMax=bounds[4]; result.zMax=bounds[5];
            return result;
        } catch (RuntimeException | Error e) { throw e; }
        catch (Throwable e) { throw new IllegalStateException("Native voxel rotation failed", e); }
        finally { scratch.inUse=false; }
    }
}
