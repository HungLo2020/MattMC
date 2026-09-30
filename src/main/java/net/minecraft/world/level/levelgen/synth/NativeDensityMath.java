package net.minecraft.world.level.levelgen.synth;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import net.minecraft.util.NativeLibraryLoader;

/** Rust operations shared by fused programs and Java's context/cache traversal.
 * Java supplies operands and preserves visitation order; it has no alternate math implementation.
 */
public final class NativeDensityMath {
    private NativeDensityMath() {}
    private static final int BATCH_SIZE = 256;
    private static final MethodHandle MATH = bind("math", FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE,
        ValueLayout.JAVA_INT, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE), true, false);
    private static final MethodHandle BRANCH = bind("branch", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE), true, false);
    private static final MethodHandle MASK = bind("mask", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE), true, true);
    private static final MethodHandle MATH_BATCH = bind("math_batch", FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE), true, true);
    private static final FunctionDescriptor NOISE_DESCRIPTOR = FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE,
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
        ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE);
    private static final FunctionDescriptor NOISE_BATCH_DESCRIPTOR = FunctionDescriptor.of(ValueLayout.JAVA_INT,
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
        ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE);
    private static final MethodHandle NOISE = bind("noise", NOISE_DESCRIPTOR, true, false);
    private static final MethodHandle NOISE_SLOW = bind("noise", NOISE_DESCRIPTOR, false, false);
    private static final MethodHandle NOISE_BATCH = bind("noise_batch", NOISE_BATCH_DESCRIPTOR, true, true);
    private static final MethodHandle NOISE_BATCH_SLOW = bind("noise_batch", NOISE_BATCH_DESCRIPTOR, false, false);

    private static MethodHandle bind(String suffix, FunctionDescriptor descriptor, boolean critical, boolean heap) {
        return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_density_" + suffix, descriptor,
            critical ? new Linker.Option[]{Linker.Option.critical(heap)} : new Linker.Option[0]);
    }

    public static double apply(int op, double a, double b, double p, double q) {
        try { return (double) MATH.invokeExact(op, a, b, p, q); }
        catch (Throwable t) { throw failure(t); }
    }

    /** 0 evaluates the right child, 1 retains the input, 2 returns positive zero. */
    public static int branch(int op, double a, double p, double q) {
        try { return (int) BRANCH.invokeExact(op, a, p, q); }
        catch (Throwable t) { throw failure(t); }
    }

    public static void mask(int op, double[] values, byte[] mask, double p, double q) {
        if (mask.length < values.length) throw new IllegalArgumentException("Density mask length");
        MemorySegment source = MemorySegment.ofArray(values);
        MemorySegment target = MemorySegment.ofArray(mask);
        try {
            for (int offset = 0; offset < values.length; offset += BATCH_SIZE) {
                int count = Math.min(BATCH_SIZE, values.length - offset);
                int status = (int) MASK.invokeExact(op, source.asSlice(offset * 8L, count * 8L),
                    target.asSlice(offset, count), count, p, q);
                if (status != 0) throw new IllegalStateException("Native density mask: " + status);
            }
        } catch (Throwable t) { throw failure(t); }
    }

    public static boolean inRange(double a, double min, double max) {
        return apply(22, a, 0.0, min, max) != 0.0;
    }

    public static void applyArray(int op, double[] values, double[] right, double p, double q) {
        if (right != null && right.length < values.length) throw new IllegalArgumentException("Density operand length");
        MemorySegment a = MemorySegment.ofArray(values);
        MemorySegment b = right == null ? MemorySegment.NULL : MemorySegment.ofArray(right);
        try {
            for (int offset = 0; offset < values.length; offset += BATCH_SIZE) {
                int count = Math.min(BATCH_SIZE, values.length - offset);
                MemorySegment operand = right == null ? b : b.asSlice(offset * 8L, count * 8L);
                int status = (int) MATH_BATCH.invokeExact(op, a.asSlice(offset * 8L, count * 8L),
                    operand, count, p, q);
                if (status != 0) throw new IllegalStateException("Native density batch: " + status);
            }
        } catch (Throwable t) { throw failure(t); }
    }

    public static double noise(NormalNoise noise, int op, double x, double y, double z,
                               double sx, double sy, double sz, double p, double q) {
        NativeNoise nativeNoise = noise == null ? null : noise.nativeNoise();
        MemorySegment state = nativeNoise == null ? MemorySegment.NULL : nativeNoise.state;
        if (nativeNoise != null && !nativeNoise.critical) {
            return noiseSlow(state, op, x, y, z, sx, sy, sz, p, q);
        }
        try { return (double) NOISE.invokeExact(state, op, x, y, z, sx, sy, sz, p, q); }
        catch (Throwable t) { throw failure(t); }
    }

    private static double noiseSlow(MemorySegment state, int op, double x, double y, double z,
                                    double sx, double sy, double sz, double p, double q) {
        try { return (double) NOISE_SLOW.invokeExact(state, op, x, y, z, sx, sy, sz, p, q); }
        catch (Throwable t) { throw failure(t); }
    }

    /** Input records are raw xyz followed by three shift/input values. */
    public static void noiseArray(NormalNoise noise, int op, double[] input, double[] output, double p, double q) {
        noiseArray(noise,op,input,output,output.length,p,q);
    }
    public static void noiseArray(NormalNoise noise, int op, double[] input, double[] output, int size, double p, double q) {
        if (size<0 || size>output.length || input == output || input.length < (long) size * 6) throw new IllegalArgumentException("Density noise buffers");
        NativeNoise nativeNoise = noise == null ? null : noise.nativeNoise();
        MemorySegment state = nativeNoise == null ? MemorySegment.NULL : nativeNoise.state;
        if (nativeNoise != null && !nativeNoise.critical) {
            noiseArraySlow(state, op, input, output, size, p, q);
            return;
        }
        MemorySegment source = MemorySegment.ofArray(input);
        MemorySegment target = MemorySegment.ofArray(output);
        try {
            for (int offset = 0; offset < size; offset += BATCH_SIZE) {
                int count = Math.min(BATCH_SIZE, size - offset);
                int status = (int) NOISE_BATCH.invokeExact(state, op, source.asSlice(offset * 48L, count * 48L),
                    target.asSlice(offset * 8L, count * 8L), count, p, q);
                if (status != 0) throw new IllegalStateException("Native density noise batch: " + status);
            }
        } catch (Throwable t) { throw failure(t); }
    }

    private static void noiseArraySlow(MemorySegment state, int op, double[] input, double[] output, int size, double p, double q) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment source = arena.allocate(BATCH_SIZE * 48L, 8);
            MemorySegment target = arena.allocate(BATCH_SIZE * 8L, 8);
            for (int offset = 0; offset < size; offset += BATCH_SIZE) {
                int count = Math.min(BATCH_SIZE, size - offset);
                MemorySegment.copy(input, offset * 6, source, ValueLayout.JAVA_DOUBLE, 0, count * 6);
                int status = (int) NOISE_BATCH_SLOW.invokeExact(state, op, source, target, count, p, q);
                if (status != 0) throw new IllegalStateException("Native density noise batch: " + status);
                MemorySegment.copy(target, ValueLayout.JAVA_DOUBLE, 0, output, offset, count);
            }
        } catch (Throwable t) { throw failure(t); }
    }

    private static RuntimeException failure(Throwable t) {
        if (t instanceof Error e) throw e;
        if (t instanceof RuntimeException e) return e;
        return new IllegalStateException("Rust density evaluation failed", t);
    }
}
