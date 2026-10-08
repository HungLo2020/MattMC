package net.vulkanic.world;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import net.minecraft.util.NativeLibraryLoader;

/** Downcalls into Rust's static-terrain mesh residency
 * ({@code worldrender/terrain/residency.rs}, exported by
 * {@code bridge/world/terrain_residency.rs}): each terrain mesh's registered,
 * acknowledged and uploaded generation, and the asset waiting for upload with
 * its place in the world-mesh upload order. Callers hold
 * {@link RustGalWorldPrimitiveRenderer}'s lock. */
final class RustTerrainResidency {
	/** {@link #accepted}: the key is not a terrain mesh. */
	static final int NOT_TERRAIN = -2;
	/** {@link #accepted}: the generation was not uploaded. */
	static final int REJECTED = -1;

	private static final Linker.Option CRITICAL = Linker.Option.critical(true);
	private static final MethodHandle PENDING_GENERATION = bind("pending_generation", false,
		FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
	private static final MethodHandle REGISTER = bind("register", true,
		FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG,
			ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
	private static final MethodHandle COUNTS = bind("counts", true,
		FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
	private static final MethodHandle CONTAINS = bind("contains", false,
		FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
	private static final MethodHandle CANDIDATES = bind("candidates", true,
		FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
			ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
	private static final MethodHandle ACKNOWLEDGE = bind("acknowledge", true,
		FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
	private static final MethodHandle REMOVE = bind("remove", false,
		FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
	private static final MethodHandle ACCEPTED = bind("accepted", true,
		FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS,
			ValueLayout.JAVA_INT));
	private static final MethodHandle GENERATION_MATCHES = bind("generation_matches", false,
		FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));

	private static int[] textureScratch = new int[64];

	private RustTerrainResidency() {
	}

	private static MethodHandle bind(String name, boolean critical, FunctionDescriptor descriptor) {
		String symbol = "mattmc_terrain_residency_" + name;
		return critical
			? NativeLibraryLoader.downcallHandle("mattmc_rust", symbol, descriptor, CRITICAL)
			: NativeLibraryLoader.downcallHandle("mattmc_rust", symbol, descriptor);
	}

	private static IllegalStateException failure(String operation, Throwable error) {
		return new IllegalStateException("Rust terrain residency " + operation + " failed", error);
	}

	/** The generation of the asset waiting for upload, or 0. */
	static long pendingGeneration(long meshKey) {
		try {
			return (long)PENDING_GENERATION.invokeExact(meshKey);
		} catch (Throwable error) {
			throw failure("pending generation", error);
		}
	}

	/** Registers a generation and its textures; unless it is an identical
	 * rebuild, the asset assembly staged under {@code stagedMeshKey} waits for
	 * upload, queued with {@code sequence} if the mesh is not dirty yet.
	 * Returns whether it was an identical rebuild. */
	static boolean register(long meshKey, long meshGeneration, long stagedMeshKey, int[] textureIds, long sequence) {
		int status;
		try {
			status = (int)REGISTER.invokeExact(meshKey, meshGeneration, stagedMeshKey, MemorySegment.ofArray(textureIds),
				textureIds.length, sequence);
		} catch (Throwable error) {
			throw failure("registration", error);
		}
		if (status < 0) {
			throw new IllegalStateException("Rust terrain mesh " + meshKey + " generation " + meshGeneration
				+ " was not staged by assembly (status " + status + ")");
		}
		return status == 1;
	}

	static final int COUNT_RESIDENCIES = 0;
	static final int COUNT_WAITING = 1;
	static final int COUNT_DIRTY = 2;
	static final int COUNT_DIRTY_BYTES = 3;
	static final int COUNT_UPLOADED = 4;

	/** Indexed by the {@code COUNT_} constants. */
	static long[] counts() {
		long[] counts = new long[5];
		try {
			int status = (int)COUNTS.invokeExact(MemorySegment.ofArray(counts));
			if (status != 0) throw new IllegalStateException("Rust terrain residency counts rejected: " + status);
		} catch (RuntimeException error) {
			throw error;
		} catch (Throwable error) {
			throw failure("counts", error);
		}
		return counts;
	}

	static boolean contains(long meshKey) {
		try {
			return (int)CONTAINS.invokeExact(meshKey) != 0;
		} catch (Throwable error) {
			throw failure("membership", error);
		}
	}

	/** Upload candidates: the per-record instances' dirty assets, then up to
	 * {@code limit} more from the dirty queue, as [sequence or -1, key,
	 * generation, payload bytes] each. */
	static long[] candidates(long[] instances, int instanceCount, long[] protectedGenerations, int protectedCount, int limit) {
		long[] out = new long[(instanceCount + limit) * 4];
		int count;
		try {
			count = (int)CANDIDATES.invokeExact(MemorySegment.ofArray(instances), instanceCount,
				MemorySegment.ofArray(protectedGenerations), protectedCount, limit, MemorySegment.ofArray(out));
		} catch (Throwable error) {
			throw failure("candidates", error);
		}
		if (count < 0) throw new IllegalStateException("Rust terrain residency candidates rejected: " + count);
		return java.util.Arrays.copyOf(out, count * 4);
	}

	/** The accepted update carried these (key, generation) assets; returns,
	 * per asset, whether its registered generation was the one uploaded. */
	static boolean[] acknowledge(long[] uploaded, int count) {
		int[] released = new int[count];
		try {
			int status = (int)ACKNOWLEDGE.invokeExact(MemorySegment.ofArray(uploaded), count, MemorySegment.ofArray(released));
			if (status != 0) throw new IllegalStateException("Rust terrain residency acknowledgement rejected: " + status);
		} catch (RuntimeException error) {
			throw error;
		} catch (Throwable error) {
			throw failure("acknowledgement", error);
		}
		boolean[] flags = new boolean[count];
		for (int index = 0; index < count; index++) flags[index] = released[index] != 0;
		return flags;
	}

	/** Withdraws a terrain mesh: the generation to retire, or 0 when it was
	 * neither registered nor waiting. */
	static long remove(long meshKey) {
		try {
			return (long)REMOVE.invokeExact(meshKey);
		} catch (Throwable error) {
			throw failure("removal", error);
		}
	}

	/** {@link #NOT_TERRAIN}, {@link #REJECTED}, or the uploaded generation's
	 * texture ids. */
	static int[] accepted(long meshKey, long meshGeneration) {
		try {
			int count = (int)ACCEPTED.invokeExact(meshKey, meshGeneration, MemorySegment.ofArray(textureScratch),
				textureScratch.length);
			if (count > textureScratch.length) {
				textureScratch = new int[count];
				count = (int)ACCEPTED.invokeExact(meshKey, meshGeneration, MemorySegment.ofArray(textureScratch),
					textureScratch.length);
			}
			return count == NOT_TERRAIN ? NOT_TERRAIN_RESULT : count == REJECTED ? REJECTED_RESULT
				: java.util.Arrays.copyOf(textureScratch, count);
		} catch (Throwable error) {
			throw failure("acceptance", error);
		}
	}

	static final int[] NOT_TERRAIN_RESULT = new int[0];
	static final int[] REJECTED_RESULT = new int[0];

	/** Whether {@code meshGeneration} is the waiting asset's, or else the registered one's. */
	static boolean generationMatches(long meshKey, long meshGeneration) {
		try {
			return (int)GENERATION_MATCHES.invokeExact(meshKey, meshGeneration) != 0;
		} catch (Throwable error) {
			throw failure("generation", error);
		}
	}
}
