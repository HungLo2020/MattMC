package net.minecraft.world.level.levelgen;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.List;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;

/** Native terrain-cell evaluation. Geometry is refreshed for every fill so moved
 * structure boxes remain observable. No cached world/chunk snapshots. */
final class NativeBeardifier {
    private static final MethodHandle CELL = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_beardifier_cell",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MemorySegment KERNEL = kernel();
    private static MemorySegment kernel() {
        var values = Beardifier.nativeKernel();
        var data = Arena.ofAuto().allocate(values.length * 4L, 4);
        MemorySegment.copy(MemorySegment.ofArray(values), 0, data, 0, data.byteSize());
        return data.asReadOnly();
    }
    private static final class Scratch {
        MemorySegment geometry = MemorySegment.NULL;
        final MemorySegment output = Arena.ofAuto().allocate(4096 * 8L, 8);
        void ensure(long bytes) {
            if (geometry.byteSize() < bytes) geometry = Arena.ofAuto().allocate(bytes, 4);
        }
    }
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);
    private final List<Beardifier.Rigid> pieces;
    private final List<JigsawJunction> junctions;
    private final BoundingBox bounds;
    private NativeBeardifier(List<Beardifier.Rigid> pieces, List<JigsawJunction> junctions, BoundingBox bounds) {
        this.pieces = pieces; this.junctions = junctions; this.bounds = bounds;
    }
    static NativeBeardifier create(List<Beardifier.Rigid> pieces, List<JigsawJunction> junctions, BoundingBox bounds) {
        if (bounds == null || bounds.getClass() != BoundingBox.class || pieces.size() > 65536 || junctions.size() > 65536) return null;
        for (var p : pieces) if (p.box().getClass() != BoundingBox.class) return null;
        for (var j : junctions) if (j.getClass() != JigsawJunction.class) return null;
        return new NativeBeardifier(pieces, junctions, bounds);
    }
    boolean fill(double[] output, NoiseChunk chunk) {
        int width = chunk.cellWidth(), height = chunk.cellHeight();
        if (width < 1 || width > 64 || height < 1 || height > 4096
            || output.length > 4096 || (long)width * width * height != output.length) return false;
        fillCell(output, chunk.blockX() - chunk.inCellX, chunk.blockY() - chunk.inCellY,
            chunk.blockZ() - chunk.inCellZ, width, height);
        chunk.forIndex(output.length - 1);
        chunk.arrayIndex = output.length;
        return true;
    }
    // Package-private for focused ABI/differential testing. Production uses fill.
    void fillCell(double[] output, int x, int y, int z, int width, int height) {
        if (output.length > 4096 || width < 1 || width > 64 || height < 1 || height > 4096
            || (long)width * width * height != output.length) throw new IllegalArgumentException("Beardifier cell shape");
        // Most vertical cells miss the affected structure bounds. Avoid even
        // marshalling for this exact zero result. Wrapped coordinates use Rust.
        long endX=(long)x+width-1, endY=(long)y+height-1, endZ=(long)z+width-1;
        if (endX<=Integer.MAX_VALUE && endY<=Integer.MAX_VALUE && endZ<=Integer.MAX_VALUE
            && (endX<bounds.minX() || x>bounds.maxX() || endY<bounds.minY() || y>bounds.maxY()
                || endZ<bounds.minZ() || z>bounds.maxZ())) {
            java.util.Arrays.fill(output,0.0);
            return;
        }
        int count = 8 + pieces.size() * 8 + junctions.size() * 3;
        var scratch = SCRATCH.get();
        scratch.ensure(count * 4L);
        var geometry = scratch.geometry;
        geometry.setAtIndex(ValueLayout.JAVA_INT, 0, pieces.size());
        geometry.setAtIndex(ValueLayout.JAVA_INT, 1, junctions.size());
        box(geometry, 2, bounds);
        int at = 8;
        for (var p : pieces) {
            box(geometry, at, p.box());
            geometry.setAtIndex(ValueLayout.JAVA_INT, at + 6, p.groundLevelDelta());
            geometry.setAtIndex(ValueLayout.JAVA_INT, at + 7, p.terrainAdjustment().ordinal());
            at += 8;
        }
        for (var j : junctions) {
            geometry.setAtIndex(ValueLayout.JAVA_INT, at++, j.getSourceX());
            geometry.setAtIndex(ValueLayout.JAVA_INT, at++, j.getSourceGroundY());
            geometry.setAtIndex(ValueLayout.JAVA_INT, at++, j.getSourceZ());
        }
        try {
            int status = (int)CELL.invokeExact(geometry, (long)count, KERNEL, scratch.output, (long)output.length, x, y, z, width, height);
            if (status != 0) throw new IllegalStateException("Native beardifier status " + status);
        } catch (RuntimeException | Error e) { throw e; }
        catch (Throwable t) { throw new IllegalStateException("Native beardifier call failed", t); }
        MemorySegment.copy(scratch.output, 0, MemorySegment.ofArray(output), 0, output.length * 8L);
    }
    private static void box(MemorySegment output, int at, BoundingBox b) {
        output.setAtIndex(ValueLayout.JAVA_INT, at, b.minX());
        output.setAtIndex(ValueLayout.JAVA_INT, at + 1, b.minY());
        output.setAtIndex(ValueLayout.JAVA_INT, at + 2, b.minZ());
        output.setAtIndex(ValueLayout.JAVA_INT, at + 3, b.maxX());
        output.setAtIndex(ValueLayout.JAVA_INT, at + 4, b.maxY());
        output.setAtIndex(ValueLayout.JAVA_INT, at + 5, b.maxZ());
    }
}
