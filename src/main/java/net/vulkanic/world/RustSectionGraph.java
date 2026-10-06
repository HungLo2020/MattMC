package net.vulkanic.world;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import net.minecraft.util.NativeLibraryLoader;

/**
 * Rust-owned chunk-section graph with Frozen's camera-pass selection
 * (Sodium's occlusion culler and section tree). Java reports column
 * readiness and build results; Rust answers which sections the camera pass
 * visits, in Frozen's visit order. Render thread only.
 */
final class RustSectionGraph implements AutoCloseable {
	private static final int OK = 0;
	private static final int ERR_CAPACITY = -3;
	/** {@code FfiSectionGraphInfo}: x, y, z, built, flags, reserved, visibility. */
	private static final long INFO_BYTES = 32;
	/** {@code FfiSectionGraphVisit}: x, y, z, built, flags. */
	private static final long VISIT_BYTES = 20;

	private static final MethodHandle CREATE = NativeLibraryLoader.downcallHandle("mattmc_rust",
		"mattmc_sodium_section_graph_create",
		FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
	private static final MethodHandle DESTROY = NativeLibraryLoader.downcallHandle("mattmc_rust",
		"mattmc_sodium_section_graph_destroy",
		FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
	private static final MethodHandle UPDATE = NativeLibraryLoader.downcallHandle("mattmc_rust",
		"mattmc_sodium_section_graph_update",
		FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
			ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
	private static final MethodHandle SELECT = NativeLibraryLoader.downcallHandle("mattmc_rust",
		"mattmc_sodium_section_graph_select",
		FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
			ValueLayout.JAVA_FLOAT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
			ValueLayout.ADDRESS));

	private final Arena arena = Arena.ofConfined();
	private MemorySegment graph;
	private final MemorySegment camera = this.arena.allocate(ValueLayout.JAVA_DOUBLE, 3);
	private final MemorySegment matrix = this.arena.allocate(ValueLayout.JAVA_FLOAT, 16);
	private final MemorySegment count = this.arena.allocate(ValueLayout.JAVA_INT);
	private MemorySegment removed = MemorySegment.NULL;
	private MemorySegment added = MemorySegment.NULL;
	private MemorySegment infos = MemorySegment.NULL;
	private MemorySegment visits = MemorySegment.NULL;
	private int removedCount;
	private int addedCount;
	private int infoCount;
	private int visitCapacity;
	private int visitCount;

	RustSectionGraph(int minSectionY, int maxSectionY) {
		try {
			this.graph = (MemorySegment) CREATE.invokeExact(minSectionY, maxSectionY);
		} catch (Throwable throwable) {
			throw new IllegalStateException("Rust section graph creation failed", throwable);
		}
		if (this.graph.equals(MemorySegment.NULL)) {
			throw new IllegalStateException("Rust section graph rejected section range " + minSectionY + ".." + maxSectionY);
		}
	}

	/** Queues a column that stopped being ready (applied before additions). */
	void removeColumn(int x, int z) {
		this.removed = ensure(this.removed, (this.removedCount + 1) * 8L);
		this.removed.set(ValueLayout.JAVA_INT, this.removedCount * 8L, x);
		this.removed.set(ValueLayout.JAVA_INT, this.removedCount * 8L + 4, z);
		this.removedCount++;
	}

	/** Queues a newly ready column: all its sections, unbuilt. */
	void addColumn(int x, int z) {
		this.added = ensure(this.added, (this.addedCount + 1) * 8L);
		this.added.set(ValueLayout.JAVA_INT, this.addedCount * 8L, x);
		this.added.set(ValueLayout.JAVA_INT, this.addedCount * 8L + 4, z);
		this.addedCount++;
	}

	/** Queues a section's build information; {@code built=false} clears it. */
	void setInfo(int x, int y, int z, boolean built, int flags, long visibility) {
		this.infos = ensure(this.infos, (this.infoCount + 1) * INFO_BYTES);
		long base = this.infoCount * INFO_BYTES;
		this.infos.set(ValueLayout.JAVA_INT, base, x);
		this.infos.set(ValueLayout.JAVA_INT, base + 4, y);
		this.infos.set(ValueLayout.JAVA_INT, base + 8, z);
		this.infos.set(ValueLayout.JAVA_INT, base + 12, built ? 1 : 0);
		this.infos.set(ValueLayout.JAVA_INT, base + 16, built ? flags : 0);
		this.infos.set(ValueLayout.JAVA_INT, base + 20, 0);
		this.infos.set(ValueLayout.JAVA_LONG, base + 24, built ? visibility : 0L);
		this.infoCount++;
	}

	/** Applies every queued change, in queue order (removals, additions, infos). */
	void flush() {
		if (this.removedCount == 0 && this.addedCount == 0 && this.infoCount == 0) {
			return;
		}
		int status;
		try {
			status = (int) UPDATE.invokeExact(this.graph, this.removed, this.removedCount, this.added,
				this.addedCount, this.infos, this.infoCount);
		} catch (Throwable throwable) {
			throw new IllegalStateException("Rust section graph update failed", throwable);
		}
		if (status != OK) {
			throw new IllegalStateException("Rust section graph update rejected with status " + status);
		}
		this.removedCount = 0;
		this.addedCount = 0;
		this.infoCount = 0;
	}

	/**
	 * Runs Frozen's camera-pass selection; afterwards {@link #visitCount()}
	 * sections are readable in visit order.
	 */
	void select(double cameraX, double cameraY, double cameraZ, float[] cullingMatrix, float searchDistance,
			boolean useOcclusionCulling) {
		this.flush();
		this.camera.set(ValueLayout.JAVA_DOUBLE, 0, cameraX);
		this.camera.set(ValueLayout.JAVA_DOUBLE, 8, cameraY);
		this.camera.set(ValueLayout.JAVA_DOUBLE, 16, cameraZ);
		MemorySegment.copy(cullingMatrix, 0, this.matrix, ValueLayout.JAVA_FLOAT, 0, 16);
		while (true) {
			int status;
			try {
				status = (int) SELECT.invokeExact(this.graph, this.camera, this.matrix, searchDistance,
					useOcclusionCulling ? 1 : 0, this.visits, this.visitCapacity, this.count);
			} catch (Throwable throwable) {
				throw new IllegalStateException("Rust section graph selection failed", throwable);
			}
			int required = this.count.get(ValueLayout.JAVA_INT, 0);
			if (status == ERR_CAPACITY) {
				this.visitCapacity = Math.max(required, this.visitCapacity * 2);
				this.visits = this.arena.allocate(this.visitCapacity * VISIT_BYTES, 8);
				continue;
			}
			if (status != OK) {
				throw new IllegalStateException("Rust section graph selection rejected with status " + status);
			}
			this.visitCount = required;
			return;
		}
	}

	int visitCount() {
		return this.visitCount;
	}

	int visitX(int index) {
		return this.visits.get(ValueLayout.JAVA_INT, index * VISIT_BYTES);
	}

	int visitY(int index) {
		return this.visits.get(ValueLayout.JAVA_INT, index * VISIT_BYTES + 4);
	}

	int visitZ(int index) {
		return this.visits.get(ValueLayout.JAVA_INT, index * VISIT_BYTES + 8);
	}

	boolean visitBuilt(int index) {
		return this.visits.get(ValueLayout.JAVA_INT, index * VISIT_BYTES + 12) != 0;
	}

	int visitFlags(int index) {
		return this.visits.get(ValueLayout.JAVA_INT, index * VISIT_BYTES + 16);
	}

	private MemorySegment ensure(MemorySegment segment, long bytes) {
		if (!segment.equals(MemorySegment.NULL) && segment.byteSize() >= bytes) {
			return segment;
		}
		long capacity = Math.max(bytes, segment.equals(MemorySegment.NULL) ? 4096 : segment.byteSize() * 2);
		MemorySegment grown = this.arena.allocate(capacity, 8);
		if (!segment.equals(MemorySegment.NULL)) {
			MemorySegment.copy(segment, 0, grown, 0, segment.byteSize());
		}
		return grown;
	}

	@Override
	public void close() {
		if (!this.graph.equals(MemorySegment.NULL)) {
			try {
				DESTROY.invokeExact(this.graph);
			} catch (Throwable throwable) {
				throw new IllegalStateException("Rust section graph destruction failed", throwable);
			}
			this.graph = MemorySegment.NULL;
		}
		this.arena.close();
	}
}
