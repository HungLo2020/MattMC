package net.vulkanic.world;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import net.minecraft.util.NativeLibraryLoader;

/**
 * Rust-owned chunk-section graph with Frozen's camera-pass selection
 * (Sodium's occlusion culler and section tree) and the terrain source's
 * bookkeeping: ready columns, which sections need a build or have one in
 * flight, block-entity sections, animated sprites per section and the visit
 * set entity culling tests. Java drives the meshing workers and reports
 * their results. Render thread only.
 */
final class RustSectionGraph implements AutoCloseable {
	private static final int OK = 0;
	private static final int ERR_CAPACITY = -3;
	/** {@code FfiSectionBuild}: x, y, z, flags, visibility, global, sprite count, sprites. */
	private static final long BUILD_BYTES = 40;
	/** {@code FfiSectionGraphFrame}: nine 8-byte fields. */
	private static final long FRAME_BYTES = 72;
	private static final int OP_SCHEDULE_REBUILD = 0;
	private static final int OP_BUILD_STARTED = 1;
	private static final int OP_FINISH_BUILD = 2;
	private static final int OP_SECTION_READY = 3;
	/** {@code FfiSectionGraphVisit}: x, y, z, built, flags. */
	private static final long VISIT_BYTES = 20;
	/** {@code FfiSectionMeshes}: x, y, z, flags, keys[3], generations[3]. */
	private static final long MESHES_BYTES = 64;
	/** {@code FfiTerrainSelectionParams}: camera[3] f64, depth policies[3], layer ordinals[3], shadow, max shadow, receipts, reserved. */
	private static final long SELECTION_PARAMS_BYTES = 64;
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
	private static final MethodHandle ADD_COLUMN = NativeLibraryLoader.downcallHandle("mattmc_rust",
		"mattmc_sodium_section_graph_add_column",
		FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
			ValueLayout.JAVA_LONG));
	private static final MethodHandle REMOVE_COLUMN = NativeLibraryLoader.downcallHandle("mattmc_rust",
		"mattmc_sodium_section_graph_remove_column",
		FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
	private static final MethodHandle SECTION_OP = NativeLibraryLoader.downcallHandle("mattmc_rust",
		"mattmc_sodium_section_graph_section_op",
		FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
			ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
	private static final MethodHandle ACCEPT_BUILD = NativeLibraryLoader.downcallHandle("mattmc_rust",
		"mattmc_sodium_section_graph_accept_build",
		FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
	private static final MethodHandle RELOAD = NativeLibraryLoader.downcallHandle("mattmc_rust",
		"mattmc_sodium_section_graph_reload",
		FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
	private static final MethodHandle BOX_VISIBLE = NativeLibraryLoader.downcallHandle("mattmc_rust",
		"mattmc_sodium_section_graph_box_visible",
		FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
			ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
	private static final MethodHandle BUILD_COUNTS = NativeLibraryLoader.downcallHandle("mattmc_rust",
		"mattmc_sodium_section_graph_build_counts",
		FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
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
	private final MemorySegment frame = this.arena.allocate(FRAME_BYTES, 8);
	private final MemorySegment build = this.arena.allocate(BUILD_BYTES, 8);
	private MemorySegment sprites = MemorySegment.NULL;
	private MemorySegment visits = MemorySegment.NULL;
	private MemorySegment meshRows = MemorySegment.NULL;
	private final MemorySegment selectionParams = this.arena.allocate(SELECTION_PARAMS_BYTES, 8);
	private final MemorySegment selectionView = this.arena.allocate(SELECTION_VIEW_BYTES, 8);
	private int meshRowCount;
	private boolean clearMeshes;
	private int visitCapacity;
	private int visitCount;
	private MemorySegment buildRequests = MemorySegment.NULL;
	private int buildRequestCount;
	private MemorySegment blockEntitySections = MemorySegment.NULL;
	private int blockEntitySectionCount;
	private MemorySegment globalBlockEntitySections = MemorySegment.NULL;
	private int globalBlockEntitySectionCount;
	private int needsBuildCount;
	private int inFlightCount;

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

	/**
	 * A column became ready: sections with a set {@code nonAirMask} bit
	 * ({@code y - minSectionY}) need a build, the rest are built as empty.
	 * False when it was already ready.
	 */
	boolean addColumn(int x, int z, long nonAirMask) {
		try {
			return check((int) ADD_COLUMN.invokeExact(this.graph, x, z, nonAirMask), "add column") == 1;
		} catch (Throwable throwable) {
			throw failure("add column", throwable);
		}
	}

	/** A column stopped being ready; its in-flight builds become stale. False when it was not ready. */
	boolean removeColumn(int x, int z) {
		try {
			return check((int) REMOVE_COLUMN.invokeExact(this.graph, x, z), "remove column") == 1;
		} catch (Throwable throwable) {
			throw failure("remove column", throwable);
		}
	}

	/** Sodium's {@code scheduleRebuild}; true when the section belongs to a ready column. */
	boolean scheduleRebuild(int x, int y, int z) {
		return this.sectionOp(OP_SCHEDULE_REBUILD, x, y, z) == 1;
	}

	/** A requested section was handed to a meshing worker. */
	void buildStarted(int x, int y, int z) {
		this.sectionOp(OP_BUILD_STARTED, x, y, z);
	}

	/** A worker delivered a build; true when it is stale (edited, reloaded or unloaded meanwhile). */
	boolean finishBuild(int x, int y, int z) {
		return this.sectionOp(OP_FINISH_BUILD, x, y, z) == 1;
	}

	/** Whether the section's current build was accepted and no rebuild is outstanding. */
	boolean sectionReady(int x, int y, int z) {
		return this.sectionOp(OP_SECTION_READY, x, y, z) == 1;
	}

	private int sectionOp(int op, int x, int y, int z) {
		try {
			return check((int) SECTION_OP.invokeExact(this.graph, op, x, y, z), "section operation");
		} catch (Throwable throwable) {
			throw failure("section operation", throwable);
		}
	}

	/** Accepts a section's newest build (an empty one included) with its animated sprite ids. */
	void acceptBuild(int x, int y, int z, int flags, long visibility, boolean globalBlockEntities,
			int[] spriteIds, int spriteCount) {
		if (spriteCount > 0) {
			this.sprites = ensure(this.sprites, spriteCount * 4L);
			MemorySegment.copy(spriteIds, 0, this.sprites, ValueLayout.JAVA_INT, 0, spriteCount);
		}
		this.build.set(ValueLayout.JAVA_INT, 0, x);
		this.build.set(ValueLayout.JAVA_INT, 4, y);
		this.build.set(ValueLayout.JAVA_INT, 8, z);
		this.build.set(ValueLayout.JAVA_INT, 12, flags);
		this.build.set(ValueLayout.JAVA_LONG, 16, visibility);
		this.build.set(ValueLayout.JAVA_INT, 24, globalBlockEntities ? 1 : 0);
		this.build.set(ValueLayout.JAVA_INT, 28, spriteCount);
		this.build.set(ValueLayout.ADDRESS, 32, spriteCount > 0 ? this.sprites : MemorySegment.NULL);
		try {
			check((int) ACCEPT_BUILD.invokeExact(this.graph, this.build), "accept build");
		} catch (Throwable throwable) {
			throw failure("accept build", throwable);
		}
	}

	/** Resource reload; {@code cancelInFlight} when the worker pool was replaced. */
	void reloadResources(boolean cancelInFlight) {
		try {
			check((int) RELOAD.invokeExact(this.graph, cancelInFlight ? 1 : 0), "reload");
		} catch (Throwable throwable) {
			throw failure("reload", throwable);
		}
	}

	/** Whether the latest search visited any section of the inclusive section-coordinate box. */
	boolean boxVisible(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		try {
			return check((int) BOX_VISIBLE.invokeExact(this.graph, minX, minY, minZ, maxX, maxY, maxZ),
				"box visibility") == 1;
		} catch (Throwable throwable) {
			throw failure("box visibility", throwable);
		}
	}

	/** Current counts: {@code needsBuild << 32 | inFlight}. */
	long buildCounts() {
		try {
			return (long) BUILD_COUNTS.invokeExact(this.graph);
		} catch (Throwable throwable) {
			throw failure("build counts", throwable);
		}
	}

	private static int check(int status, String operation) {
		if (status < 0) {
			throw new IllegalStateException("Rust section graph " + operation + " rejected with status " + status);
		}
		return status;
	}

	private static IllegalStateException failure(String operation, Throwable throwable) {
		return throwable instanceof IllegalStateException state ? state
			: new IllegalStateException("Rust section graph " + operation + " failed", throwable);
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

	/** Applies the queued mesh rows. */
	void flush() {
		if (!this.clearMeshes && this.meshRowCount == 0) {
			return;
		}
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

	/**
	 * Runs Frozen's camera-pass selection and records its bookkeeping (build
	 * requests, block-entity sections, entity-culling visit set). With
	 * {@code copyVisits} the visited sections are also readable in visit order.
	 */
	void select(double cameraX, double cameraY, double cameraZ, float[] cullingMatrix, float searchDistance,
			boolean useOcclusionCulling, boolean copyVisits) {
		this.flush();
		this.camera.set(ValueLayout.JAVA_DOUBLE, 0, cameraX);
		this.camera.set(ValueLayout.JAVA_DOUBLE, 8, cameraY);
		this.camera.set(ValueLayout.JAVA_DOUBLE, 16, cameraZ);
		MemorySegment.copy(cullingMatrix, 0, this.matrix, ValueLayout.JAVA_FLOAT, 0, 16);
		if (copyVisits && this.visits.equals(MemorySegment.NULL)) {
			this.visitCapacity = 4096;
			this.visits = this.arena.allocate(this.visitCapacity * VISIT_BYTES, 8);
		}
		while (true) {
			// invokeExact needs a statically typed MemorySegment, not a conditional.
			MemorySegment out = copyVisits ? this.visits : MemorySegment.NULL;
			int status;
			try {
				status = (int) SELECT.invokeExact(this.graph, this.camera, this.matrix, searchDistance,
					useOcclusionCulling ? 1 : 0, out, copyVisits ? this.visitCapacity : 0, this.frame);
			} catch (Throwable throwable) {
				throw new IllegalStateException("Rust section graph selection failed", throwable);
			}
			int required = Math.toIntExact(this.frame.get(ValueLayout.JAVA_LONG, 64));
			if (status == ERR_CAPACITY) {
				this.visitCapacity = Math.max(required, this.visitCapacity * 2);
				this.visits = this.arena.allocate(this.visitCapacity * VISIT_BYTES, 8);
				continue;
			}
			if (status != OK) {
				throw new IllegalStateException("Rust section graph selection rejected with status " + status);
			}
			this.visitCount = copyVisits ? required : 0;
			MemorySegment view = this.frame;
			this.buildRequestCount = Math.toIntExact(view.get(ValueLayout.JAVA_LONG, 8));
			this.buildRequests = view.get(ValueLayout.ADDRESS, 0).reinterpret(this.buildRequestCount * 8L);
			this.blockEntitySectionCount = Math.toIntExact(view.get(ValueLayout.JAVA_LONG, 24));
			this.blockEntitySections = view.get(ValueLayout.ADDRESS, 16).reinterpret(this.blockEntitySectionCount * 8L);
			this.globalBlockEntitySectionCount = Math.toIntExact(view.get(ValueLayout.JAVA_LONG, 40));
			this.globalBlockEntitySections = view.get(ValueLayout.ADDRESS, 32)
				.reinterpret(this.globalBlockEntitySectionCount * 8L);
			this.needsBuildCount = Math.toIntExact(view.get(ValueLayout.JAVA_LONG, 48));
			this.inFlightCount = Math.toIntExact(view.get(ValueLayout.JAVA_LONG, 56));
			return;
		}
	}

	/** Section keys to build after the latest selection: block edits first, otherwise visit order. */
	int buildRequestCount() {
		return this.buildRequestCount;
	}

	long buildRequest(int index) {
		return this.buildRequests.get(ValueLayout.JAVA_LONG, index * 8L);
	}

	/** Visited built sections with culled block entities, in visit order. */
	int blockEntitySectionCount() {
		return this.blockEntitySectionCount;
	}

	long blockEntitySection(int index) {
		return this.blockEntitySections.get(ValueLayout.JAVA_LONG, index * 8L);
	}

	/** Built sections with off-screen block entities, in first-build order. */
	int globalBlockEntitySectionCount() {
		return this.globalBlockEntitySectionCount;
	}

	long globalBlockEntitySection(int index) {
		return this.globalBlockEntitySections.get(ValueLayout.JAVA_LONG, index * 8L);
	}

	/** Sections needing a build, at the latest selection. */
	int needsBuildCount() {
		return this.needsBuildCount;
	}

	/** Builds in flight, at the latest selection. */
	int inFlightCount() {
		return this.inFlightCount;
	}

	/** Visited sections copied by the latest {@code select(..., copyVisits=true)}. */
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

		/** The frame's animated sprite ids, each once in first-use order. */
		int animatedSprite(int index) {
			return this.animated.get(ValueLayout.JAVA_INT, index * 4L);
		}
	}

	/**
	 * Builds the frame's static terrain from the latest {@link #select}: camera
	 * layers in draw order, off-camera shadow casters when requested, and the
	 * animated sprites the frame uses. {@code receipts} computes the layer
	 * fingerprint for diagnostics.
	 */
	TerrainSelection selectTerrain(double cameraX, double cameraY, double cameraZ, int[] depthPolicies,
			int[] layerOrdinals, boolean shadowCandidates, int maxShadowCandidates, boolean receipts,
			long sectionBytes, long casterBytes) {
		this.selectionParams.set(ValueLayout.JAVA_DOUBLE, 0, cameraX);
		this.selectionParams.set(ValueLayout.JAVA_DOUBLE, 8, cameraY);
		this.selectionParams.set(ValueLayout.JAVA_DOUBLE, 16, cameraZ);
		for (int layer = 0; layer < 3; layer++) {
			this.selectionParams.set(ValueLayout.JAVA_INT, 24 + layer * 4L, depthPolicies[layer]);
			this.selectionParams.set(ValueLayout.JAVA_INT, 36 + layer * 4L, layerOrdinals[layer]);
		}
		this.selectionParams.set(ValueLayout.JAVA_INT, 48, shadowCandidates ? 1 : 0);
		this.selectionParams.set(ValueLayout.JAVA_INT, 52, maxShadowCandidates);
		this.selectionParams.set(ValueLayout.JAVA_INT, 56, receipts ? 1 : 0);
		this.selectionParams.set(ValueLayout.JAVA_INT, 60, 0);
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
			view.get(ValueLayout.ADDRESS, 40).reinterpret(animatedCount * 4L),
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
