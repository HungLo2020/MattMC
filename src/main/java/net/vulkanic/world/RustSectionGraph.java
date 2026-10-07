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
	/** {@code FfiSectionMeshes}: x, y, z, flags, keys[3], generations[3]. */
	private static final long MESHES_BYTES = 64;
	/** {@code FfiTerrainSelectionParams}: camera[3] f64, depth policies[3], layer ordinals[3], shadow, max shadow. */
	private static final long SELECTION_PARAMS_BYTES = 56;
	/** {@code FfiTerrainSelectionView}: ten 8-byte fields. */
	private static final long SELECTION_VIEW_BYTES = 80;
	/** {@code SelectedLayer}: section key, layer, reserved. */
	static final long SELECTED_LAYER_BYTES = 16;

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

	private static final MethodHandle SET_MESHES = NativeLibraryLoader.downcallHandle("mattmc_rust",
		"mattmc_sodium_section_graph_set_meshes",
		FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
			ValueLayout.JAVA_INT));
	private static final MethodHandle SELECT_TERRAIN = NativeLibraryLoader.downcallHandle("mattmc_rust",
		"mattmc_sodium_section_graph_select_terrain",
		FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

	private final Arena arena = Arena.ofConfined();
	private MemorySegment graph;
	private final MemorySegment camera = this.arena.allocate(ValueLayout.JAVA_DOUBLE, 3);
	private final MemorySegment matrix = this.arena.allocate(ValueLayout.JAVA_FLOAT, 16);
	private final MemorySegment count = this.arena.allocate(ValueLayout.JAVA_INT);
	private MemorySegment removed = MemorySegment.NULL;
	private MemorySegment added = MemorySegment.NULL;
	private MemorySegment infos = MemorySegment.NULL;
	private MemorySegment visits = MemorySegment.NULL;
	private MemorySegment meshRows = MemorySegment.NULL;
	private final MemorySegment selectionParams = this.arena.allocate(SELECTION_PARAMS_BYTES, 8);
	private final MemorySegment selectionView = this.arena.allocate(SELECTION_VIEW_BYTES, 8);
	private int meshRowCount;
	private boolean clearMeshes;
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

	/**
	 * Queues a section's published layer meshes (solid, cutout, translucent;
	 * zero keys are absent layers, all-zero keys clear the section).
	 */
	void setMeshes(int x, int y, int z, boolean translucentCameraSorted, long solidKey, long solidGeneration,
			long cutoutKey, long cutoutGeneration, long translucentKey, long translucentGeneration) {
		this.meshRows = ensure(this.meshRows, (this.meshRowCount + 1) * MESHES_BYTES);
		long base = this.meshRowCount * MESHES_BYTES;
		this.meshRows.set(ValueLayout.JAVA_INT, base, x);
		this.meshRows.set(ValueLayout.JAVA_INT, base + 4, y);
		this.meshRows.set(ValueLayout.JAVA_INT, base + 8, z);
		this.meshRows.set(ValueLayout.JAVA_INT, base + 12, translucentCameraSorted ? 1 : 0);
		this.meshRows.set(ValueLayout.JAVA_LONG, base + 16, solidKey);
		this.meshRows.set(ValueLayout.JAVA_LONG, base + 24, cutoutKey);
		this.meshRows.set(ValueLayout.JAVA_LONG, base + 32, translucentKey);
		this.meshRows.set(ValueLayout.JAVA_LONG, base + 40, solidGeneration);
		this.meshRows.set(ValueLayout.JAVA_LONG, base + 48, cutoutGeneration);
		this.meshRows.set(ValueLayout.JAVA_LONG, base + 56, translucentGeneration);
		this.meshRowCount++;
	}

	/** Drops every published mesh row before the queued ones apply. */
	void clearMeshes() {
		this.clearMeshes = true;
		this.meshRowCount = 0;
	}

	/** Applies every queued change, in queue order (removals, additions, infos, mesh rows). */
	void flush() {
		if (this.clearMeshes || this.meshRowCount != 0) {
			int status;
			try {
				status = (int) SET_MESHES.invokeExact(this.graph, this.clearMeshes ? 1 : 0, this.meshRows,
					this.meshRowCount);
			} catch (Throwable throwable) {
				throw new IllegalStateException("Rust section graph mesh update failed", throwable);
			}
			if (status != OK) {
				throw new IllegalStateException("Rust section graph mesh update rejected with status " + status);
			}
			this.clearMeshes = false;
			this.meshRowCount = 0;
		}
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

	/**
	 * The frame's static terrain selected from the latest camera selection.
	 * Segments view graph-owned memory valid until the next selection.
	 */
	record TerrainSelection(MemorySegment sections, MemorySegment sectionLayers, int sectionCount,
			MemorySegment casters, int casterCount, MemorySegment animated, int animatedCount,
			long layerProbes, long layerSubmissions, long fingerprint) {
		long sectionKey(int index) {
			return this.sectionLayers.get(ValueLayout.JAVA_LONG, index * SELECTED_LAYER_BYTES);
		}

		int layer(int index) {
			return this.sectionLayers.get(ValueLayout.JAVA_INT, index * SELECTED_LAYER_BYTES + 8);
		}

		int animatedX(int index) {
			return this.animated.get(ValueLayout.JAVA_INT, index * 12L);
		}

		int animatedY(int index) {
			return this.animated.get(ValueLayout.JAVA_INT, index * 12L + 4);
		}

		int animatedZ(int index) {
			return this.animated.get(ValueLayout.JAVA_INT, index * 12L + 8);
		}
	}

	/**
	 * Builds the frame's static terrain from the latest {@link #select}: camera
	 * layers in draw order, off-camera shadow casters when requested, and the
	 * sections whose animated sprites the frame uses.
	 */
	TerrainSelection selectTerrain(double cameraX, double cameraY, double cameraZ, int[] depthPolicies,
			int[] layerOrdinals, boolean shadowCandidates, int maxShadowCandidates, long sectionBytes, long casterBytes) {
		this.selectionParams.set(ValueLayout.JAVA_DOUBLE, 0, cameraX);
		this.selectionParams.set(ValueLayout.JAVA_DOUBLE, 8, cameraY);
		this.selectionParams.set(ValueLayout.JAVA_DOUBLE, 16, cameraZ);
		for (int layer = 0; layer < 3; layer++) {
			this.selectionParams.set(ValueLayout.JAVA_INT, 24 + layer * 4L, depthPolicies[layer]);
			this.selectionParams.set(ValueLayout.JAVA_INT, 36 + layer * 4L, layerOrdinals[layer]);
		}
		this.selectionParams.set(ValueLayout.JAVA_INT, 48, shadowCandidates ? 1 : 0);
		this.selectionParams.set(ValueLayout.JAVA_INT, 52, maxShadowCandidates);
		int status;
		try {
			status = (int) SELECT_TERRAIN.invokeExact(this.graph, this.selectionParams, this.selectionView);
		} catch (Throwable throwable) {
			throw new IllegalStateException("Rust terrain selection failed", throwable);
		}
		if (status != OK) {
			throw new IllegalStateException("Rust terrain selection rejected with status " + status);
		}
		MemorySegment view = this.selectionView;
		int sectionCount = Math.toIntExact(view.get(ValueLayout.JAVA_LONG, 16));
		int casterCount = Math.toIntExact(view.get(ValueLayout.JAVA_LONG, 32));
		int animatedCount = Math.toIntExact(view.get(ValueLayout.JAVA_LONG, 48));
		return new TerrainSelection(
			view.get(ValueLayout.ADDRESS, 0).reinterpret(sectionCount * sectionBytes),
			view.get(ValueLayout.ADDRESS, 8).reinterpret(sectionCount * SELECTED_LAYER_BYTES),
			sectionCount,
			view.get(ValueLayout.ADDRESS, 24).reinterpret(casterCount * casterBytes),
			casterCount,
			view.get(ValueLayout.ADDRESS, 40).reinterpret(animatedCount * 12L),
			animatedCount,
			view.get(ValueLayout.JAVA_LONG, 56),
			view.get(ValueLayout.JAVA_LONG, 64),
			view.get(ValueLayout.JAVA_LONG, 72));
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
