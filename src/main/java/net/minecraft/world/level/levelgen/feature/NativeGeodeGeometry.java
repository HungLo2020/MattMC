package net.minecraft.world.level.levelgen.feature;

import com.mojang.datafixers.util.Pair;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/** Numeric fields only; lease spans original Java placement callbacks. */
final class NativeGeodeGeometry {
    private static final MethodHandle FIELDS = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_geode_fields",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
            ValueLayout.JAVA_INT, ValueLayout.JAVA_DOUBLE, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final ThreadLocal<Fields> SCRATCH = ThreadLocal.withInitial(Fields::new);
    private NativeGeodeGeometry() {}

    static final class Fields implements AutoCloseable {
        private Arena arena;
        private MemorySegment output;
        private double[] values = new double[0];
        private final Arena metadata = Arena.ofAuto();
        private final MemorySegment frame = metadata.allocate(24, 4), points = metadata.allocate(128 * 16, 4);
        private final int[] dimensions = new int[6], pointValues = new int[128 * 4];
        private boolean inUse;
        double body(int index) { return values[2 * index]; }
        double crack(int index) { return values[2 * index + 1]; }
        void ensure(int cells) {
            if (values.length >= cells * 2) return;
            // Publish together: a failed native allocation must leave the old
            // capacity usable, rather than exposing a larger heap with a small
            // native buffer on the next call.
            double[] nextValues = new double[cells * 2];
            Arena nextArena = Arena.ofAuto();
            MemorySegment nextOutput = nextArena.allocate(cells * 16L, 8);
            values = nextValues;
            arena = nextArena;
            output = nextOutput;
        }
        @Override public void close() { inUse = false; }
    }

    static Fields prepare(NormalNoise noise, BlockPos origin, int min, int max,
        List<Pair<BlockPos, Integer>> points, List<BlockPos> cracks, int crackOffset, double multiplier) {
        if (noise.getClass() != NormalNoise.class
            || (origin.getClass() != BlockPos.class && origin.getClass() != BlockPos.MutableBlockPos.class)
            || points.size() > 64 || cracks.size() > 64
            || !(multiplier >= 0.0 && multiplier <= 1.0) || crackOffset < 0) return null;
        for (var p : points) if (p.getFirst().getClass() != BlockPos.class || p.getSecond() < 0) return null;
        for (var p : cracks) if (p.getClass() != BlockPos.class) return null;
        int[] a = {origin.getX()+min, origin.getY()+min, origin.getZ()+min};
        int[] b = {origin.getX()+max, origin.getY()+max, origin.getZ()+max};
        long cells = 1;
        for (int i = 0; i < 3; i++) {
            long size = Math.abs((long)b[i] - a[i]) + 1;
            if (size > 64) return null;
            cells *= size;
        }
        // Tiny custom grids do not amortize packing/validation/FFM costs.
        if (cells < 512 || cells > 65536) return null;
        var fields = SCRATCH.get();
        if (fields.inUse) return null;
        fields.inUse = true;
        try {
            fields.ensure((int)cells);
            for (int i = 0; i < 3; i++) {
                fields.dimensions[i] = Math.min(a[i], b[i]);
                fields.dimensions[i+3] = (int)(Math.abs((long)b[i]-a[i])+1);
            }
            int at = 0;
            for (var p : points) at = put(fields.pointValues, at, p.getFirst(), p.getSecond());
            for (var p : cracks) at = put(fields.pointValues, at, p, crackOffset);
            MemorySegment.copy(MemorySegment.ofArray(fields.dimensions), 0, fields.frame, 0, 24);
            MemorySegment.copy(MemorySegment.ofArray(fields.pointValues), 0, fields.points, 0, at * 4L);
            var state = noise.nativeState();
            int length = (int)cells * 2;
            int result = (int)FIELDS.invokeExact(state.state(), state.state().byteSize(), fields.frame, 6,
                fields.points, points.size(), cracks.size(), multiplier, fields.output, length);
            if (result != 0) throw new IllegalStateException("Invalid native geode fields: " + result);
            MemorySegment.copy(fields.output, 0, MemorySegment.ofArray(fields.values), 0, length * 8L);
            return fields;
        } catch (RuntimeException | Error e) { fields.close(); throw e; }
        catch (Throwable e) { fields.close(); throw new IllegalStateException("Native geode fields failed", e); }
    }

    private static int put(int[] values, int at, BlockPos p, int offset) {
        values[at++] = p.getX(); values[at++] = p.getY(); values[at++] = p.getZ(); values[at++] = offset;
        return at;
    }
}
