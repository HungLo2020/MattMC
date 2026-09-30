package net.minecraft.world.level.levelgen.synth;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import net.minecraft.util.NativeLibraryLoader;

/** Immutable, GC-owned ABI state. No Rust allocation/handle registry and no Java callbacks.
 * Permutations and coefficients originate in the unchanged Java seed constructors.
 */
final class NativeNoise {
	static final int HEADER = 80;
	static final int OCTAVE = 1320;
	static final int MAX_BATCH = 256;
	private static final MethodHandle VALIDATE = bind("validate", FunctionDescriptor.of(ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS, ValueLayout.JAVA_LONG), false);
	private static final MethodHandle EVAL = bind("eval", FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE,
		ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
		ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_INT), true, false);
	private static final MethodHandle SIMPLEX2 = bind("simplex2", FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE,
		ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE), true, false);
    private static final MethodHandle END_ISLAND = bind("end_island", FunctionDescriptor.of(ValueLayout.JAVA_FLOAT,
        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT), true, false);

	private static final MethodHandle BATCH = bind("batch", FunctionDescriptor.of(ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
		ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_INT), true);
	private static final MethodHandle DERIVATIVE = bind("derivative", FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE,
		ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
		ValueLayout.ADDRESS), true);
	private static final MethodHandle EVAL_SLOW = bind("eval", FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE,
		ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE,
		ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_INT), false);
	private static final MethodHandle BATCH_SLOW = bind("batch", FunctionDescriptor.of(ValueLayout.JAVA_INT,
		ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
		ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_INT), false);
	final MemorySegment state;
	final boolean critical;
	final double[] amplitudes;

	private static MethodHandle bind(String suffix, FunctionDescriptor descriptor, boolean critical) {
		return bind(suffix, descriptor, critical, true);
	}

	private static MethodHandle bind(String suffix, FunctionDescriptor descriptor, boolean critical, boolean heapAccess) {
		return NativeLibraryLoader.downcallHandle("mattmc_rust", "mattmc_noise_" + suffix, descriptor,
			critical ? new Linker.Option[]{Linker.Option.critical(heapAccess)} : new Linker.Option[0]);
	}

	static MemorySegment allocate(int kind, int n1, int n2, int n3, double... params) {
		int count = Math.addExact(Math.addExact(n1, n2), n3);
		if (n1 < 0 || n2 < 0 || n3 < 0) throw new IllegalArgumentException("Negative noise octave count");
		MemorySegment data = Arena.ofAuto().allocate(HEADER + (long)OCTAVE * count, 8);
		data.set(ValueLayout.JAVA_INT, 0, kind);
		data.set(ValueLayout.JAVA_INT, 4, n1);
		data.set(ValueLayout.JAVA_INT, 8, n2);
		data.set(ValueLayout.JAVA_INT, 12, n3);
		for (int i = 0; i < params.length; i++) data.set(ValueLayout.JAVA_DOUBLE, 16 + i * 8L, params[i]);
		return data;
	}

	NativeNoise(MemorySegment data) {
		try {
			int status = (int)VALIDATE.invokeExact(data, data.byteSize());
			if (status != 0) throw new IllegalArgumentException("Invalid native noise state: " + status);
		} catch (Throwable t) { throw failure(t); }
		state = data.asReadOnly();
		critical = (data.byteSize() - HEADER) / OCTAVE <= 64;
		amplitudes = new double[(int)((data.byteSize() - HEADER) / OCTAVE)];
		for (int i = 0; i < amplitudes.length; i++)
			amplitudes[i] = data.get(ValueLayout.JAVA_DOUBLE, HEADER + (long)i * OCTAVE + 24);
	}

	static void octave(MemorySegment data, int index, double x, double y, double z, double amplitude, byte[] p) {
		long offset = HEADER + (long)index * OCTAVE;
		data.set(ValueLayout.JAVA_DOUBLE, offset, x);
		data.set(ValueLayout.JAVA_DOUBLE, offset + 8, y);
		data.set(ValueLayout.JAVA_DOUBLE, offset + 16, z);
		data.set(ValueLayout.JAVA_DOUBLE, offset + 24, amplitude);
		data.set(ValueLayout.JAVA_LONG, offset + 32, 1);
		MemorySegment.copy(p, 0, data, ValueLayout.JAVA_BYTE, offset + 40, 256);
		for (int i = 0; i < 256; i++) {
			int permutation = p[i] & 255;
			// Low byte: permutation. High byte: precomputed Simplex gradient index.
			data.set(ValueLayout.JAVA_INT, offset + 296 + i * 4L, permutation | ((permutation % 12) << 8));
		}
	}

	double sample(double x, double y, double z) {
		if (!critical) return sampleSlow(x, y, z, 0.0, 0.0, 0);
		try { return (double)EVAL.invokeExact(state, x, y, z, 0.0, 0.0, 0); }
		catch (Throwable t) { throw failure(t); }
	}

	double sample(double x, double y, double z, double yScale, double yMax, int flags) {
		if (!critical) return sampleSlow(x, y, z, yScale, yMax, flags);
		try { return (double)EVAL.invokeExact(state, x, y, z, yScale, yMax, flags); }
		catch (Throwable t) { throw failure(t); }
	}

	private double sampleSlow(double x, double y, double z, double yScale, double yMax, int flags) {
		try { return (double)EVAL_SLOW.invokeExact(state, x, y, z, yScale, yMax, flags); }
		catch (Throwable t) { throw failure(t); }
	}

	double simplex2(double x, double y) {
		try { return (double)SIMPLEX2.invokeExact(state, x, y); }
		catch (Throwable t) { throw failure(t); }
	}

    float endIslandHeight(int x, int z) {
        try { return (float)END_ISLAND.invokeExact(state, x, z); }
        catch (Throwable t) { throw failure(t); }
    }

	double derivative(double x, double y, double z, double[] accumulated) {
		if (accumulated.length < 3) throw new IllegalArgumentException("Three derivative components required");
		try { return (double)DERIVATIVE.invokeExact(state, x, y, z, MemorySegment.ofArray(accumulated)); }
		catch (Throwable t) { throw failure(t); }
	}

	/** Packed xyz triples; no staging copy. Large requests are split to bound GC pinning. */
	void batch(double[] xyz, double[] output, double yScale, double yMax, int flags) {
        batch(xyz, output, output.length, yScale, yMax, flags);
    }
    void batch(double[] xyz, double[] output, int size, double yScale, double yMax, int flags) {
		if (size < 0 || size > output.length || xyz == output || xyz.length < (long)size * 3) throw new IllegalArgumentException("Invalid noise buffers");
		if (!critical) { batchSlow(xyz, output, size, yScale, yMax, flags); return; }
		MemorySegment input = MemorySegment.ofArray(xyz);
		MemorySegment result = MemorySegment.ofArray(output);
		try {
			for (int offset = 0; offset < size; offset += MAX_BATCH) {
				int count = Math.min(MAX_BATCH, size - offset);
				int status = (int)BATCH.invokeExact(state, input.asSlice(offset * 24L, count * 24L),
					result.asSlice(offset * 8L, count * 8L), count, yScale, yMax, flags);
				if (status != 0) throw new IllegalStateException("Native noise batch failed: " + status);
			}
		} catch (Throwable t) { throw failure(t); }
	}

	// Unusually large custom configurations must not hold a critical downcall
	// for an unbounded time. Ordinary downcalls require off-heap argument buffers.
	private void batchSlow(double[] xyz, double[] output, int size, double yScale, double yMax, int flags) {
		try (Arena arena = Arena.ofConfined()) {
			var input = arena.allocate(MAX_BATCH * 24L, 8);
			var result = arena.allocate(MAX_BATCH * 8L, 8);
			for (int offset = 0; offset < size; offset += MAX_BATCH) {
				int count = Math.min(MAX_BATCH, size - offset);
				MemorySegment.copy(xyz, offset * 3, input, ValueLayout.JAVA_DOUBLE, 0, count * 3);
				int status = (int)BATCH_SLOW.invokeExact(state, input, result, count, yScale, yMax, flags);
				if (status != 0) throw new IllegalStateException("Native noise batch failed: " + status);
				MemorySegment.copy(result, ValueLayout.JAVA_DOUBLE, 0, output, offset, count);
			}
		} catch (Throwable t) { throw failure(t); }
	}

	private static RuntimeException failure(Throwable t) {
		if (t instanceof Error e) throw e;
		if (t instanceof RuntimeException e) return e;
		return new IllegalStateException("Rust noise evaluation failed", t);
	}
}
