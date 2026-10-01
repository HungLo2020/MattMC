package net.minecraft.world.level.levelgen.feature;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import net.minecraft.util.Mth;
import net.minecraft.util.NativeLibraryLoader;
import net.minecraft.util.RandomSource;

/** Ordered XYZ spans. Leases preserve buffers across nested world/RNG callbacks. */
final class NativeOreGeometry {
    private static final FunctionDescriptor DESCRIPTOR = FunctionDescriptor.of(ValueLayout.JAVA_LONG,
        ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT);
    private static final MethodHandle VEIN = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_ore_vein", DESCRIPTOR);
    // Rust validates a strict bound before scanning. No retained pointers,
    // allocation, blocking or callbacks while these heap arrays are pinned.
    private static final MethodHandle SMALL = NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_ore_vein_small", DESCRIPTOR,
        Linker.Option.critical(true));
    private static final MemorySegment[] SHAPES = shapes();
    private static final ThreadLocal<Spans> BUFFERS = ThreadLocal.withInitial(Spans::new);

    private static MemorySegment[] shapes() {
        var result = new MemorySegment[65];
        for (int size = 1; size <= 64; size++) result[size] = shape(size);
        return result;
    }

    private static MemorySegment shape(int size) {
        var data = Arena.ofAuto().allocate(size * 16L, 8);
        for (int q = 0; q < size; q++) {
            float fraction = (float)q / size;
            data.setAtIndex(ValueLayout.JAVA_DOUBLE, q * 2L, (double)fraction);
            data.setAtIndex(ValueLayout.JAVA_DOUBLE, q * 2L + 1, (double)(Mth.sin((float)Math.PI * fraction) + 1.0F));
        }
        return data.asReadOnly();
    }

    static final class Spans implements AutoCloseable {
        int[] values = new int[4096];
        int length;
        private double[] samples = new double[70];
        private MemorySegment heapSamples = MemorySegment.ofArray(samples);
        private MemorySegment heapOutput = MemorySegment.ofArray(values);
        private MemorySegment nativeSamples = Arena.ofAuto().allocate(70 * 8L, 8);
        private MemorySegment nativeOutput = Arena.ofAuto().allocate(4096 * 4L, 4);
        private boolean inUse;
        private Spans next;

        private void ensureSamples(int length) {
            if (samples.length >= length) return;
            var newSamples = new double[length];
            var newHeap = MemorySegment.ofArray(newSamples);
            var newNative = Arena.ofAuto().allocate(length * 8L, 8);
            samples = newSamples;
            heapSamples = newHeap;
            nativeSamples = newNative;
        }

        private void growOutput(int length) {
            var newValues = new int[length];
            var newHeap = MemorySegment.ofArray(newValues);
            var newNative = Arena.ofAuto().allocate(length * 4L, 4);
            values = newValues;
            heapOutput = newHeap;
            nativeOutput = newNative;
        }

        @Override public void close() { inUse = false; }
    }

    private static Spans acquire() {
        var buffer = BUFFERS.get();
        while (buffer.inUse) {
            if (buffer.next == null) buffer.next = new Spans();
            buffer = buffer.next;
        }
        buffer.inUse = true;
        buffer.length = 0;
        return buffer;
    }

    static Spans vein(RandomSource random, int size, double x0, double x1, double y0, double y1,
                      double z0, double z1, int minX, int minY, int minZ) {
        if (size < 0) throw new NegativeArraySizeException(Integer.toString(size));
        var buffer = acquire();
        boolean complete = false;
        try {
            if (size == 0) { complete = true; return buffer; }
            buffer.ensureSamples(Math.addExact(size, 6));
            var samples = buffer.samples;
            samples[0] = x0; samples[1] = x1; samples[2] = y0; samples[3] = y1; samples[4] = z0; samples[5] = z1;
            // Reserve the lease before RNG callbacks, which may recursively place features.
            for (int q = 0; q < size; q++) samples[q + 6] = random.nextDouble();
            var shape = size <= 64 ? SHAPES[size] : shape(size);
            long length;
            try {
                length = size <= 16 ? (long)SMALL.invokeExact(buffer.heapSamples, shape, size,
                    buffer.heapOutput, buffer.values.length, minX, minY, minZ) : -2;
                if (length == -2) length = fillRegular(buffer, shape, size, minX, minY, minZ);
            } catch (RuntimeException | Error e) { throw e; }
            catch (Throwable t) { throw new IllegalStateException("Native ore geometry failed", t); }
            checkLength(length);
            // A validated small call emits at most 16 * 5 * 5 spans (1600 ints).
            if (length > buffer.values.length) throw new IllegalStateException("Small ore geometry bound exceeded");
            buffer.length = (int)length;
            complete = true;
            return buffer;
        } finally {
            if (!complete) buffer.close();
        }
    }

    private static long fillRegular(Spans buffer, MemorySegment shape, int size, int x, int y, int z) throws Throwable {
        MemorySegment.copy(buffer.heapSamples, 0, buffer.nativeSamples, 0, (size + 6L) * 8);
        long length;
        while (true) {
            length = (long)VEIN.invokeExact(buffer.nativeSamples, shape, size,
                buffer.nativeOutput, buffer.values.length, x, y, z);
            checkLength(length);
            if (length <= buffer.values.length) break;
            // Allocate all replacements before publishing any capacity change.
            buffer.growOutput((int)length);
        }
        MemorySegment.copy(buffer.nativeOutput, 0, buffer.heapOutput, 0, length * 4);
        return length;
    }

    private static void checkLength(long length) {
        if (length < 0 || length > Integer.MAX_VALUE - 8 || length % 4 != 0)
            throw new IllegalStateException("Invalid ore geometry length: " + length);
    }
}
