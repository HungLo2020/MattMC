package net.vulkanic.world;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import net.minecraft.util.NativeLibraryLoader;

/** Downcalls into the Rust static-terrain publication registry
 * ({@code worldrender/terrain/publication.rs}, exported by
 * {@code bridge/world/terrain_publication.rs}): the mesh each section's solid,
 * cutout and translucent layer publishes, and the camera section graph's copy
 * of those rows. Called on the render thread. */
final class RustTerrainPublication {
	static final int FLAG_CAMERA_SORTED = 1;

	private static final int OK = 0;
	private static final int COLLISION = 1;

	private static final Linker.Option CRITICAL = Linker.Option.critical(true);
	private static final MethodHandle PUBLISH = NativeLibraryLoader.downcallHandle("mattmc_rust",
		"mattmc_terrain_publish_layer", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG,
			ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.ADDRESS),
		CRITICAL);
	private static final MethodHandle REMOVE = NativeLibraryLoader.downcallHandle("mattmc_rust",
		"mattmc_terrain_remove_layer", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
	private static final MethodHandle REPLACE = NativeLibraryLoader.downcallHandle("mattmc_rust",
		"mattmc_terrain_publication_replace", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
			ValueLayout.JAVA_INT, ValueLayout.ADDRESS), CRITICAL);
	private static final MethodHandle CLEAR = NativeLibraryLoader.downcallHandle("mattmc_rust",
		"mattmc_terrain_publication_clear", FunctionDescriptor.ofVoid());
	private static final MethodHandle SYNC_GRAPH = NativeLibraryLoader.downcallHandle("mattmc_rust",
		"mattmc_terrain_publication_sync_graph", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
			ValueLayout.JAVA_INT));
	private static final MethodHandle ROWS = NativeLibraryLoader.downcallHandle("mattmc_rust",
		"mattmc_terrain_publication_rows", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
			ValueLayout.JAVA_INT, ValueLayout.ADDRESS), CRITICAL);

	private RustTerrainPublication() {
	}

	private static IllegalStateException failure(String operation, Throwable error) {
		return new IllegalStateException("Rust terrain publication " + operation + " failed", error);
	}

	private static void check(int status, String operation) {
		if (status < 0) {
			throw new IllegalStateException("Rust terrain publication " + operation + " rejected with status " + status);
		}
	}

	/** Publishes a section layer's mesh; on a key another section layer
	 * publishes, returns that holder as [key, section, slot] and changes nothing. */
	static long[] publish(long section, int slot, long meshKey, long meshGeneration, int flags) {
		long[] collision = new long[3];
		int status;
		try {
			status = (int)PUBLISH.invokeExact(section, slot, meshKey, meshGeneration, flags, MemorySegment.ofArray(collision));
		} catch (Throwable error) {
			throw failure("publish", error);
		}
		check(status, "publish");
		return status == COLLISION ? collision : null;
	}

	static void remove(long section, int slot) {
		int status;
		try {
			status = (int)REMOVE.invokeExact(section, slot);
		} catch (Throwable error) {
			throw failure("remove", error);
		}
		check(status, "remove");
	}

	/** Replaces every row with {@code count} (section, slot, key, generation,
	 * flags) layers; on a duplicate key returns its first holder as [key,
	 * section, slot] and changes nothing. */
	static long[] replace(long[] layers, int count) {
		long[] collision = new long[3];
		int status;
		try {
			status = (int)REPLACE.invokeExact(MemorySegment.ofArray(layers), count, MemorySegment.ofArray(collision));
		} catch (Throwable error) {
			throw failure("replace", error);
		}
		check(status, "replace");
		return status == COLLISION ? collision : null;
	}

	static void clear() {
		try {
			CLEAR.invokeExact();
		} catch (Throwable error) {
			throw failure("clear", error);
		}
	}

	/** Applies the rows changed since the last sync to {@code graph}; after a
	 * reset, or with {@code republish} for a new graph, every row. */
	static void syncGraph(MemorySegment graph, boolean republish) {
		int status;
		try {
			status = (int)SYNC_GRAPH.invokeExact(graph, republish ? 1 : 0);
		} catch (Throwable error) {
			throw failure("graph sync", error);
		}
		check(status, "graph sync");
	}

	/** Writes each of the first {@code count} sections' row to {@code out} as
	 * [solid key, generation, cutout key, generation, translucent key,
	 * generation]; zeros for no layer. */
	static void rows(long[] sections, int count, long[] out) {
		int status;
		try {
			status = (int)ROWS.invokeExact(MemorySegment.ofArray(sections), count, MemorySegment.ofArray(out));
		} catch (Throwable error) {
			throw failure("rows", error);
		}
		check(status, "rows");
	}
}
