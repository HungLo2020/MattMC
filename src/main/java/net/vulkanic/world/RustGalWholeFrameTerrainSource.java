package net.vulkanic.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.SectionPos;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.sodium.client.render.chunk.RenderSection;
import net.sodium.client.render.StaticTerrainParityDiagnostics;
import net.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.sodium.client.render.chunk.data.BuiltSectionInfo;
import net.sodium.client.render.chunk.compile.executor.ChunkBuilder;
import net.sodium.client.render.chunk.compile.executor.ChunkJobResult;
import net.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask;
import net.sodium.client.render.chunk.translucent_sorting.SortBehavior;
import net.sodium.client.render.chunk.vertex.format.ChunkMeshFormats;
import net.sodium.client.render.chunk.occlusion.GraphDirection;
import net.sodium.client.render.chunk.occlusion.GraphDirectionSet;
import net.sodium.client.render.chunk.occlusion.OcclusionCuller;
import net.sodium.client.render.viewport.Viewport;
import net.sodium.client.render.viewport.ViewportProvider;
import net.sodium.client.world.LevelSlice;
import net.sodium.client.world.cloned.ChunkRenderContext;
import net.sodium.client.world.cloned.ClonedChunkSectionCache;
import org.joml.Vector3d;

/**
 * CPU-only semantic terrain producer for Rust whole-frame Vulkan.  This owns
 * no GL device, region, command list, or renderer.  It reuses Sodium's pure
 * meshing task solely to turn immutable level snapshots into mesh data, then
 * copies that data through VulkanicGAL and immediately releases the CPU buffer.
 */
public final class RustGalWholeFrameTerrainSource {
	private static final int MAX_SEMANTIC_MESH_WORKERS = 8;
	private static volatile boolean wholeFrameSurfaceQueueDrained;
	private static volatile boolean wholeFrameTerrainQueueDrained;
	/** Capture-only state explaining the current explicit CPU-source queue state. */
	private static volatile String wholeFrameTerrainQueueSummary = "uninitialized";
	private static volatile String lastWholeFrameTerrainFailure = "none";
	private static volatile long wholeFrameTerrainFailureCount;
	/** Monotonic, CPU-only resource epoch requested by the terrain semantic owner. */
	private static volatile long resourceReloadEpoch;
	private String lastLoggedQueueSummary = "";
	private long observedResourceReloadEpoch;
    private ClientLevel level;
    private ClonedChunkSectionCache sectionCache;
	private ChunkBuilder workerBuilder;
    private final Long2ObjectOpenHashMap<RenderSection> sections = new Long2ObjectOpenHashMap<>();
	private final LongLinkedOpenHashSet queued = new LongLinkedOpenHashSet();
	private final ArrayDeque<SectionPos> pending = new ArrayDeque<>();
	/**
	 * The read side of the semantic portal BFS.  Keeping this separate from
	 * {@link #propagationPending} makes newly discovered neighbors observable
	 * only on the following wave, matching Sodium's occlusion traversal without
	 * importing its renderer-owned queue or render lists.
	 */
	private final ArrayDeque<SectionPos> propagationRead = new ArrayDeque<>();
	private final ArrayDeque<SectionPos> propagationPending = new ArrayDeque<>();
	private final LongOpenHashSet propagationQueued = new LongOpenHashSet();
	private final Long2IntOpenHashMap incomingDirections = new Long2IntOpenHashMap();
	private final Long2IntOpenHashMap propagatedIncomingDirections = new Long2IntOpenHashMap();
	private final LongOpenHashSet inFlight = new LongOpenHashSet();
	private final LongOpenHashSet invalidatedInFlight = new LongOpenHashSet();
	private final LongOpenHashSet invalidatedPending = new LongOpenHashSet();
	private final LongOpenHashSet unavailableSections = new LongOpenHashSet();
	private final ConcurrentLinkedQueue<CompletedBuild> completedBuilds = new ConcurrentLinkedQueue<>();
	private long lastCameraSection = Long.MIN_VALUE;
	/** Radius is semantic selection state: option changes must not retain a prior frontier. */
	private int lastHorizontalRadius = -1;
	/** Current-frame CPU culling semantics; never a renderer or GPU resource. */
	private Viewport viewport;
	/**
	 * Frame-local terrain selection distance extracted from vanilla fog semantics.
	 * This is ordinary CPU culling policy, not a renderer-owned fog buffer.
	 */
	private float terrainSelectionDistance;
	/**
	 * A completed immutable section may replace the temporary all-closed result
	 * observed by an earlier traversal wave.  Sodium starts a fresh visibility
	 * search every frame, so the independent source must likewise invalidate
	 * only its CPU portal frontier when that semantic input changes.
	 */
	private boolean visibilityGraphDirty;
    private int buildFrame;

    public void setLevel(ClientLevel level) {
        if (this.level == level && this.workerBuilder != null && this.sectionCache != null) {
            return;
        }
        this.destroy();
        this.level = level;
        if (level != null) {
            this.sectionCache = new ClonedChunkSectionCache(level);
			// Frozen Sodium's vanilla compact stream bakes ambient occlusion and
			// directional face shade into RGB. The direct Rust terrain program
			// consumes that complete semantic colour directly, so its independent
			// CPU producer must use the same contract rather than leave AO in alpha.
			// This remains an explicit vanilla policy and does not consult Iris.
			this.workerBuilder = new ChunkBuilder(level, ChunkMeshFormats.COMPACT, false, semanticMeshWorkerCount());
		}
	}

	/**
	 * The terrain-particle capture is a narrow material fixture, not a terrain
	 * throughput benchmark. Its loading transition otherwise keeps eight large
	 * cloned-section builds resident alongside the semantic GUI grid and can
	 * exceed the process RSS budget. Keep the same source and mesh semantics but
	 * bound peak construction memory for that explicitly named fixture.
	 */
	private static int semanticMeshWorkerCount() {
		return System.getProperty("mattmc.dev.rustGalWorldMaterial.terrainParticleScenario", "").isBlank()
			? MAX_SEMANTIC_MESH_WORKERS
			: 1;
	}

	public void enqueue(Camera camera, Frustum frustum, int viewportWidth, int viewportHeight,
			float terrainSelectionDistance) {
		if (!WorldRenderRoutePolicy.currentStaticTerrainRoute().usesRustWholeFrameVulkan()
				|| this.level == null || camera == null || frustum == null) {
			return;
		}
		// Backend selection can settle after LevelRenderer's initial level-change
		// callback. Lazily establish the CPU-only source here rather than silently
		// presenting a Rust frame with no terrain producer attached.
		if (this.workerBuilder == null || this.sectionCache == null) {
			this.setLevel(this.level);
		}
		if (this.workerBuilder == null || this.sectionCache == null) {
			return;
		}
		if (!(frustum instanceof ViewportProvider viewportProvider)) {
			throw new IllegalStateException("Rust whole-frame terrain requires Sodium viewport semantics");
		}
		this.viewport = viewportProvider.sodium$createViewport();
		this.terrainSelectionDistance = terrainSelectionDistance;
		if (this.observedResourceReloadEpoch != resourceReloadEpoch) {
			this.resetForResourceReload();
			this.observedResourceReloadEpoch = resourceReloadEpoch;
		}
		SectionPos cameraSection = SectionPos.of(camera.getPosition());
		long cameraKey = cameraSection.asLong();
		int horizontalRadius = this.configuredHorizontalRadius();
		boolean refreshResidentVisibility = false;
		if (cameraKey != this.lastCameraSection || horizontalRadius != this.lastHorizontalRadius) {
			wholeFrameSurfaceQueueDrained = false;
			wholeFrameTerrainQueueDrained = false;
			this.lastCameraSection = cameraKey;
			this.lastHorizontalRadius = horizontalRadius;
			this.pending.clear();
			this.queued.clear();
			this.propagationRead.clear();
			this.propagationPending.clear();
			this.propagationQueued.clear();
			this.incomingDirections.clear();
			this.propagatedIncomingDirections.clear();
			this.unavailableSections.clear();
			this.evictOutsideWindow(cameraSection, horizontalRadius);
			this.admitSection(cameraSection, GraphDirectionSet.NONE, true, frustum);
		} else if (this.canRefreshResidentVisibility()) {
			// Frozen re-evaluates visibility from its resident section graph every
			// frame. Once this independent producer has no outstanding CPU work,
			// do the equivalent from its own immutable section cache rather than
			// retaining the traversal order created while snapshots arrived.
			this.resetVisibilityFrontier();
			this.admitSection(cameraSection, GraphDirectionSet.NONE, true, frustum);
			refreshResidentVisibility = true;
		} else {
			// Revisit the origin's completed CPU section so a transiently unavailable
			// camera section can never be mistaken for a settled terrain source.
			this.admitSection(cameraSection, GraphDirectionSet.NONE, true, frustum);
		}
		this.admitInvalidatedSections(frustum);
		this.retryUnavailableSections(frustum);
		this.drainCompletedBuilds(frustum);
		if (this.visibilityGraphDirty) {
			// Do not wait for every worker job in the view to finish. A newly built
			// portal can expose an already-resident branch immediately; retaining
			// the old frontier until global quiescence permanently loses that branch
			// under continuous chunk/light updates. This is semantic CPU selection
			// only and deliberately does not touch Sodium render lists or backend state.
			this.resetVisibilityFrontier();
			this.admitSection(cameraSection, GraphDirectionSet.NONE, true, frustum);
			this.visibilityGraphDirty = false;
			refreshResidentVisibility = true;
		}
		if (refreshResidentVisibility) {
			this.drainVisibilityFrontierFully(frustum);
		} else {
			this.drainVisibilityFrontier(frustum);
		}
		this.scheduleBuilds(camera);
		// A worker may finish while the first bounded scheduling pass is still
		// assembling the frame. Consume that completion immediately so its
		// visibility frontier can admit the next ring without waiting for a
		// completely unrelated render frame. Keep this as one additional pass:
		// producer work remains explicitly bounded and the settled gate still
		// requires the queues to be empty after the pass.
		this.drainCompletedBuilds(frustum);
		if (refreshResidentVisibility) {
			this.drainVisibilityFrontierFully(frustum);
		} else {
			this.drainVisibilityFrontier(frustum);
		}
		this.scheduleBuilds(camera);
		this.sectionCache.cleanup();
		wholeFrameSurfaceQueueDrained = this.pending.isEmpty()
			&& this.inFlight.isEmpty()
			&& this.invalidatedPending.isEmpty()
			&& this.invalidatedInFlight.isEmpty();
		wholeFrameTerrainQueueDrained = wholeFrameSurfaceQueueDrained
			&& this.propagationRead.isEmpty()
			&& this.propagationPending.isEmpty()
			&& this.completedBuilds.isEmpty()
			&& this.unavailableSections.isEmpty();
		wholeFrameTerrainQueueSummary = "pending=" + this.pending.size()
			+ ",scheduledJobs=" + this.workerBuilder.getScheduledJobCount()
			+ ",busyWorkers=" + this.workerBuilder.getBusyThreadCount()
			+ ",workerThreads=" + this.workerBuilder.getTotalThreadCount()
			+ ",frontier=" + (this.propagationRead.size() + this.propagationPending.size())
			+ ",inFlight=" + this.inFlight.size()
			+ ",invalidatedPending=" + this.invalidatedPending.size()
			+ ",invalidatedSample=" + this.invalidatedPendingSample()
			+ ",invalidatedInFlight=" + this.invalidatedInFlight.size()
			+ ",completed=" + this.completedBuilds.size()
			+ ",retained=" + this.sections.size()
			+ ",unavailable=" + this.unavailableSections.size()
			+ ",failureCount=" + wholeFrameTerrainFailureCount
			+ ",lastFailure=" + lastWholeFrameTerrainFailure
			+ ",drained=" + wholeFrameTerrainQueueDrained;
		if (!wholeFrameTerrainQueueSummary.equals(this.lastLoggedQueueSummary)) {
			this.lastLoggedQueueSummary = wholeFrameTerrainQueueSummary;
			System.out.println("[MattMC graphics audit] Rust whole-frame terrain source "
				+ wholeFrameTerrainQueueSummary);
		}
		var visibleSections = new ArrayList<RenderSection>(this.propagatedIncomingDirections.size() + 26);
		var visibleKeys = new LongOpenHashSet(this.propagatedIncomingDirections.size() + 26);
		for (var entry : this.sections.long2ObjectEntrySet()) {
			// `sections` is a bounded CPU mesh cache, not the render domain. A
			// cached section may remain frustum-visible after the camera moves but
			// be occluded from the current portal traversal. Submit only sections
			// reached by the current semantic visibility graph; this is the same
			// culling input Sodium's render lists represent, without borrowing its
			// GL-owned regions or command state.
			if (!this.propagatedIncomingDirections.containsKey(entry.getLongKey())) {
				continue;
			}
			RenderSection section = entry.getValue();
			// Sodium visits the camera-containing root before applying any
			// frustum gate to outward traversal. `admitSection` preserves that
			// bootstrap rule, so submission must preserve it too: a numerically
			// marginal root AABB cannot be allowed to disappear between CPU build
			// admission and the explicit VulkanicGAL draw domain.
			boolean bootstrapRoot = entry.getLongKey() == this.lastCameraSection;
			if (section != null && section.getFlags() != 0
				&& (bootstrapRoot || this.isVisible(section.getPosition(), frustum))) {
				visibleSections.add(section);
				visibleKeys.add(entry.getLongKey());
			}
		}
		// Preserve the final portal-selected set before the separately modeled
		// nearby-section enlargement. The predicate is diagnostic-only and is
		// consumed only by the bounded capture receipt below.
		var portalVisibleKeys = new LongOpenHashSet(visibleKeys);
		this.addNearbyVisibleSections(visibleSections, visibleKeys);
		// Capture-only receipt of the final Rust-owned CPU visibility domain.
		// Wait for the source's existing settled state so startup-empty samples
		// cannot displace the comparable capture-phase observation.  This does
		// not provide the renderer with a list or influence admission.
		if (wholeFrameTerrainQueueDrained) {
			StaticTerrainParityDiagnostics.recordWholeFrameVisibleSections(
				visibleSections, camera.getPosition().x(), camera.getPosition().y(), camera.getPosition().z(),
				viewportWidth, viewportHeight, portalVisibleKeys::contains
			);
		}
		RustGalTerrainRenderer.enqueueWholeFrameTerrainSections(visibleSections, camera, viewportWidth, viewportHeight);
    }

	/** True once all client-resident nearby surface sections have been attempted. */
	public static boolean isWholeFrameSurfaceQueueDrained() {
		return wholeFrameSurfaceQueueDrained;
	}

	/** Capture diagnostic only: identifies a bounded sample of the semantic
	 * sections currently preventing a terrain-settled receipt. */
	private String invalidatedPendingSample() {
		if (this.invalidatedPending.isEmpty()) {
			return "none";
		}
		StringBuilder sample = new StringBuilder();
		var iterator = this.invalidatedPending.iterator();
		for (int count = 0; iterator.hasNext() && count < 4; count++) {
			SectionPos section = SectionPos.of(iterator.nextLong());
			if (count > 0) {
				sample.append('|');
			}
			sample.append(section.getX()).append(':').append(section.getY()).append(':').append(section.getZ());
		}
		return sample.toString();
	}

	/**
	 * True only after every currently visible nearby section—surface and
	 * vertical—has either produced a retained CPU mesh or completed as empty.
	 * Deterministic parity capture uses this stronger condition so it never
	 * presents a partial semantic terrain set as a settled frame.
	 */
	public static boolean isWholeFrameTerrainQueueDrained() {
		return wholeFrameTerrainQueueDrained;
	}

	/** Diagnostic companion to {@link #isWholeFrameTerrainQueueDrained()}. */
	public static String wholeFrameTerrainQueueSummary() {
		return wholeFrameTerrainQueueSummary;
	}

	/**
	 * Invalidates copied terrain payloads after an atlas/resource replacement.
	 * The next semantic enqueue rebuilds from level snapshots; it never asks a
	 * Java renderer or an Iris runtime to restore a GPU mesh.
	 */
	static void requestResourceReload() {
		resourceReloadEpoch++;
	}

	/**
	 * Reports readiness for the client loading gate from the Rust semantic
	 * source itself. A section is ready only after its CPU build has been
	 * consumed (including an explicitly empty section); queued or in-flight
	 * sections remain unavailable. This never exposes a Java mesh or backend
	 * handle to the loading path.
	 */
	public boolean isSectionReady(BlockPos blockPos) {
		if (blockPos == null || this.level == null) {
			return false;
		}
		long key = SectionPos.asLong(
			SectionPos.blockToSectionCoord(blockPos.getX()),
			SectionPos.blockToSectionCoord(blockPos.getY()),
			SectionPos.blockToSectionCoord(blockPos.getZ())
		);
		return this.sections.containsKey(key)
			&& !this.inFlight.contains(key)
			&& !this.queued.contains(key)
			&& !this.pending.contains(SectionPos.of(key))
			&& !this.invalidatedInFlight.contains(key)
			&& !this.invalidatedPending.contains(key)
			&& !this.unavailableSections.contains(key);
	}

    public void invalidate(int sectionX, int sectionY, int sectionZ) {
        if (this.level == null || this.sectionCache == null) {
            return;
        }
		// Deterministic readiness is observed before the next render enqueue. Do
		// not leave a previous-frame "drained" receipt visible across a newly
		// dirty semantic section, or capture can photograph the route while its
		// replacement CPU mesh is still outstanding.
		wholeFrameSurfaceQueueDrained = false;
		wholeFrameTerrainQueueDrained = false;
		net.minecraft.client.dev.DeterministicCameraCapture.invalidateRustWholeFrameTerrainReadiness();
        long key = SectionPos.asLong(sectionX, sectionY, sectionZ);
        this.sectionCache.invalidate(sectionX, sectionY, sectionZ);
        RustGalTerrainRenderer.removeSection(sectionX, sectionY, sectionZ, "cpu-source-dirty");
        this.sections.remove(key);
		this.propagatedIncomingDirections.remove(key);
		this.unavailableSections.remove(key);
		SectionPos section = SectionPos.of(sectionX, sectionY, sectionZ);
		if (this.inFlight.contains(key)) {
			// The immutable snapshot may still be meshing on a worker. Suppress its
			// stale result and schedule a replacement once that worker completes.
			this.invalidatedInFlight.add(key);
			this.invalidatedPending.add(key);
		} else {
			this.invalidatedPending.add(key);
		}
		this.recordPortalBuildLifecycle(key, "invalidated");
    }

	public void destroy() {
		wholeFrameSurfaceQueueDrained = false;
		wholeFrameTerrainQueueDrained = false;
		wholeFrameTerrainQueueSummary = "destroyed";
		if (this.workerBuilder != null) {
			this.workerBuilder.shutdown();
		}
		this.workerBuilder = null;
        this.sectionCache = null;
        this.sections.clear();
        this.queued.clear();
		this.pending.clear();
		this.propagationRead.clear();
		this.propagationPending.clear();
		this.propagationQueued.clear();
		this.incomingDirections.clear();
		this.propagatedIncomingDirections.clear();
		this.inFlight.clear();
		this.invalidatedInFlight.clear();
		this.invalidatedPending.clear();
		this.unavailableSections.clear();
		this.destroyCompletedBuilds();
		this.lastCameraSection = Long.MIN_VALUE;
		this.lastHorizontalRadius = -1;
		this.viewport = null;
		this.visibilityGraphDirty = false;
        this.buildFrame = 0;
		this.level = null;
    }

	private void resetForResourceReload() {
		for (RenderSection section : this.sections.values()) {
			if (section == null) continue;
			SectionPos position = section.getPosition();
			RustGalTerrainRenderer.removeSection(
				position.getX(), position.getY(), position.getZ(), "cpu-source-resource-reload"
			);
		}
		this.sections.clear();
		this.queued.clear();
		this.pending.clear();
		this.propagationRead.clear();
		this.propagationPending.clear();
		this.propagationQueued.clear();
		this.incomingDirections.clear();
		this.propagatedIncomingDirections.clear();
		this.invalidatedInFlight.addAll(this.inFlight);
		this.invalidatedPending.clear();
		this.unavailableSections.clear();
		this.lastCameraSection = Long.MIN_VALUE;
		this.lastHorizontalRadius = -1;
		this.visibilityGraphDirty = false;
		wholeFrameSurfaceQueueDrained = false;
		wholeFrameTerrainQueueDrained = false;
	}

	/** Targets the configured client view distance without a fixed bootstrap cap. */
	private int configuredHorizontalRadius() {
		return Math.max(1, Minecraft.getInstance().options.getEffectiveRenderDistance());
	}

	/**
	 * Starts at the camera section and expands through the immutable portal
	 * visibility data generated by the same CPU mesher used for the semantic
	 * terrain payload. This deliberately avoids Sodium's OpenGL-owned render
	 * lists while avoiding the raw-frustum vertical-volume over-admission.
	 */
	private void admitSection(SectionPos section, int incoming, boolean origin, Frustum frustum) {
		// The camera's containing section is necessarily part of the visible
		// semantic frame.  Keep it as the bootstrap root even when a captured or
		// numerically marginal frustum rejects its boundary AABB; propagation and
		// all non-root sections still use the exact frustum test below.
		boolean withinBuildHeight = section != null && !this.level.isOutsideBuildHeight(section.minBlockY());
		boolean loaded = section != null && this.level.hasChunk(section.getX(), section.getZ());
		boolean insideWindow = section != null && this.isInsideCurrentWindow(section);
		boolean visible = section != null && (origin || this.isVisible(section, frustum));
		long key = section == null ? Long.MIN_VALUE : section.asLong();
		StaticTerrainParityDiagnostics.recordPortalAdmission("rust-whole-frame", key, incoming,
			origin, withinBuildHeight, loaded, insideWindow, visible, this.sections.containsKey(key),
			this.queued.contains(key), this.inFlight.contains(key), this.unavailableSections.contains(key));
		if (!withinBuildHeight || !loaded || !insideWindow || !visible) {
			return;
		}
		int previousIncoming = this.incomingDirections.get(key);
		int nextIncoming = previousIncoming | incoming;
		if (origin) {
			nextIncoming |= 1 << GraphDirection.COUNT;
		}
		if (nextIncoming != previousIncoming) {
			this.incomingDirections.put(key, nextIncoming);
		}
		if (this.sections.containsKey(key)) {
			this.sections.get(key).setIncomingDirections(nextIncoming & GraphDirectionSet.ALL);
			// Sodium's graph visit is single-shot for a visibility frame. Incoming
			// portals are merged while the node waits in its wave, but a route that
			// arrives after that wave has been consumed must not reopen the node.
			// Reopening it here discovers portal paths that Frozen never visits and
			// expands the semantic terrain domain. The independent source keeps the
			// merged value for diagnostics and future frontier resets, while only an
			// unvisited completed section is eligible to enter its first wave.
			if (!this.propagatedIncomingDirections.containsKey(key)) {
				this.requestPropagation(key);
			}
			return;
		}
		if (!this.inFlight.contains(key) && this.queued.add(key)) {
			this.pending.addLast(section);
			this.recordPortalBuildLifecycle(key, "enqueued");
		}
	}

	private boolean isCandidate(SectionPos section, Frustum frustum) {
		return this.isCandidate(section, frustum, false);
	}

	private boolean isCandidate(SectionPos section, Frustum frustum, boolean bootstrapOrigin) {
		return section != null
			&& !this.level.isOutsideBuildHeight(section.minBlockY())
			&& this.level.hasChunk(section.getX(), section.getZ())
			&& this.isInsideCurrentWindow(section)
			&& (bootstrapOrigin || this.isVisible(section, frustum));
	}

	private void requestPropagation(long key) {
		if (this.propagationQueued.add(key)) {
			this.propagationPending.addLast(SectionPos.of(key));
		}
	}

	/**
	 * A visibility refresh is safe only after the source has consumed every
	 * pending CPU result. In-flight work keeps its original portal inputs until
	 * it becomes resident, so no build request can be dropped by the refresh.
	 */
	private boolean canRefreshResidentVisibility() {
		return this.pending.isEmpty()
			&& this.queued.isEmpty()
			&& this.inFlight.isEmpty()
			&& this.completedBuilds.isEmpty()
			&& this.propagationRead.isEmpty()
			&& this.propagationPending.isEmpty()
			&& this.invalidatedPending.isEmpty()
			&& this.invalidatedInFlight.isEmpty()
			&& this.unavailableSections.isEmpty();
	}

	/** Clears only frame-local traversal state; resident CPU meshes remain owned by this source. */
	private void resetVisibilityFrontier() {
		this.propagationRead.clear();
		this.propagationPending.clear();
		this.propagationQueued.clear();
		this.incomingDirections.clear();
		this.propagatedIncomingDirections.clear();
	}

	private void admitInvalidatedSections(Frustum frustum) {
		var iterator = this.invalidatedPending.iterator();
		while (iterator.hasNext()) {
			long key = iterator.nextLong();
			SectionPos section = SectionPos.of(key);
			// A dirty section that has left the current camera window or frustum is
			// no longer part of this frame's readiness domain. Drop the stale
			// invalidation here; ordinary visibility traversal will re-admit it if
			// the camera later makes it relevant again. Retaining it forever would
			// keep the whole-frame queue non-drained despite no pending or in-flight
			// work.
			if (!this.isInsideCurrentWindow(section) || !this.isVisible(section, frustum)) {
				this.queued.remove(key);
				iterator.remove();
				continue;
			}
			if (!this.isCandidate(section, frustum)) {
				// Candidates outside the build height have a loaded horizontal chunk
				// but can never produce a semantic terrain section.  They must leave
				// the readiness domain just like unloaded or out-of-view sections;
				// otherwise light updates for padding sections keep capture and normal
				// source convergence permanently unsettled.
				this.queued.remove(key);
				iterator.remove();
				continue;
			}
			this.unavailableSections.remove(key);
			// `queued` is the ownership gate for the pending CPU build. Calling
			// admitSection after claiming that gate drops the work: its own enqueue
			// guard quite correctly refuses the already-queued key. Invalidated,
			// visible sections must therefore enter the same pending deque directly.
			// Their accumulated portal inputs remain in incomingDirections and are
			// applied when the immutable build result becomes resident.
			this.pending.addLast(section);
			this.recordPortalBuildLifecycle(key, "invalidated-enqueued");
			iterator.remove();
		}
	}

	/**
	 * A cloned section can be temporarily unavailable while an adjacent client
	 * chunk finishes arriving. Keep that condition visible to readiness, then
	 * re-admit the exact same semantic frontier node once its snapshot becomes
	 * available; never replace it with an empty or surface-only substitute.
	 */
	private void retryUnavailableSections(Frustum frustum) {
		var iterator = this.unavailableSections.iterator();
		while (iterator.hasNext()) {
			long key = iterator.nextLong();
			SectionPos section = SectionPos.of(key);
			if (!this.isInsideCurrentWindow(section) || !this.isVisible(section, frustum)) {
				iterator.remove();
				continue;
			}
			if (!this.level.hasChunk(section.getX(), section.getZ())
				|| this.sections.containsKey(key)
				|| this.inFlight.contains(key)
				|| !this.queued.add(key)) {
				continue;
			}
			this.pending.addLast(section);
		}
	}

	private void drainVisibilityFrontier(Frustum frustum) {
		// Snapshot one BFS wave. Neighbor admissions below always enter the write
		// side, so their incoming portal directions are merged before the next
		// wave evaluates visibility. The old immediate drain accidentally made
		// traversal order part of the visible terrain domain.
		if (this.propagationRead.isEmpty()) {
			SectionPos queuedSection;
			while ((queuedSection = this.propagationPending.pollFirst()) != null) {
				this.propagationQueued.remove(queuedSection.asLong());
				this.propagationRead.addLast(queuedSection);
			}
		}
		SectionPos sectionPos;
		while ((sectionPos = this.propagationRead.pollFirst()) != null) {
			long key = sectionPos.asLong();
			RenderSection section = this.sections.get(key);
			if (section == null || !section.isBuilt()) {
				continue;
			}
			int incoming = this.incomingDirections.get(key);
			int alreadyPropagated = this.propagatedIncomingDirections.get(key);
			if (incoming == alreadyPropagated) {
				continue;
			}
			this.propagatedIncomingDirections.put(key, incoming);
			int directionMask = GraphDirectionSet.ALL;
			int originBit = 1 << GraphDirection.COUNT;
			int incomingDirections = incoming & directionMask;
			Viewport currentViewport = this.viewport;
			if (currentViewport == null) continue;
			var transform = currentViewport.getTransform();
			int outgoing = (incoming & originBit) != 0
				? OcclusionCuller.getVisibilityConnections(section.getVisibilityData(), GraphDirectionSet.NONE, false)
				: OcclusionCuller.getVisibilityConnectionsForCamera(section.getVisibilityData(), incomingDirections,
					transform.x - (sectionPos.minBlockX() + 8), transform.y - (sectionPos.minBlockY() + 8), transform.z - (sectionPos.minBlockZ() + 8));
			int outgoingBeforeOutwardMask = outgoing;
			// Mirror Sodium's CPU occlusion traversal: once a portal path has
			// moved away from the camera section, never walk it back toward the
			// camera. Besides preventing redundant graph walks, this is essential
			// to preserve Sodium's visible-section domain without touching its GL
			// render lists or render device.
			outgoing &= this.outwardDirections(sectionPos);
			int outgoingAfterOutwardMask = outgoing;
			// Sodium applies the region graph's adjacent-mask before admitting a
			// neighbor. This source owns no Java render region, so derive the same
			// structural constraint from its own CPU-resident/window domain instead
			// of treating every coordinate as a linked graph node.
			int adjacentMask = this.residentAdjacentMask(sectionPos);
			outgoing &= adjacentMask;
			StaticTerrainParityDiagnostics.recordPortalTraversal("rust-whole-frame", key, this.lastCameraSection,
				incomingDirections, outgoingBeforeOutwardMask, outgoingAfterOutwardMask, adjacentMask, outgoing,
				section.getVisibilityData(),
				transform.x - section.getCenterX(), transform.y - section.getCenterY(), transform.z - section.getCenterZ());
			for (int direction = 0; direction < GraphDirection.COUNT; direction++) {
				if (!GraphDirectionSet.contains(outgoing, direction)) {
					continue;
				}
				SectionPos neighbor = SectionPos.of(
					sectionPos.getX() + GraphDirection.x(direction),
					sectionPos.getY() + GraphDirection.y(direction),
					sectionPos.getZ() + GraphDirection.z(direction)
				);
				this.admitSection(neighbor, GraphDirectionSet.of(GraphDirection.opposite(direction)), false, frustum);
			}
		}
	}

	/** CPU-only equivalent of Sodium RenderSection#getAdjacentMask(). */
	private int residentAdjacentMask(SectionPos section) {
		int mask = GraphDirectionSet.NONE;
		for (int direction = 0; direction < GraphDirection.COUNT; direction++) {
			SectionPos neighbor = SectionPos.of(
				section.getX() + GraphDirection.x(direction),
				section.getY() + GraphDirection.y(direction),
				section.getZ() + GraphDirection.z(direction)
			);
			if (!this.level.isOutsideBuildHeight(neighbor.minBlockY())
				&& this.level.hasChunk(neighbor.getX(), neighbor.getZ())
				&& this.isInsideCurrentWindow(neighbor)) {
				mask |= GraphDirectionSet.of(direction);
			}
		}
		return mask;
	}

	/** Drains every BFS wave for a quiescent resident graph, matching Frozen's frame-local search. */
	private void drainVisibilityFrontierFully(Frustum frustum) {
		while (!this.propagationRead.isEmpty() || !this.propagationPending.isEmpty()) {
			this.drainVisibilityFrontier(frustum);
		}
	}

	private int outwardDirections(SectionPos section) {
		if (this.lastCameraSection == Long.MIN_VALUE) {
			return GraphDirectionSet.NONE;
		}
		int originX = SectionPos.x(this.lastCameraSection);
		int originY = SectionPos.y(this.lastCameraSection);
		int originZ = SectionPos.z(this.lastCameraSection);
		int directions = GraphDirectionSet.NONE;
		directions |= section.getX() <= originX ? 1 << GraphDirection.WEST : 0;
		directions |= section.getX() >= originX ? 1 << GraphDirection.EAST : 0;
		directions |= section.getY() <= originY ? 1 << GraphDirection.DOWN : 0;
		directions |= section.getY() >= originY ? 1 << GraphDirection.UP : 0;
		directions |= section.getZ() <= originZ ? 1 << GraphDirection.NORTH : 0;
		directions |= section.getZ() >= originZ ? 1 << GraphDirection.SOUTH : 0;
		return directions;
	}

	private void evictOutsideWindow(SectionPos center, int horizontalRadius) {
		var iterator = this.sections.long2ObjectEntrySet().iterator();
		while (iterator.hasNext()) {
			var entry = iterator.next();
			RenderSection section = entry.getValue();
			if (section == null) {
				iterator.remove();
				continue;
			}
			SectionPos position = section.getPosition();
			if (Math.abs(position.getX() - center.getX()) <= horizontalRadius
					&& Math.abs(position.getZ() - center.getZ()) <= horizontalRadius) {
				continue;
			}
			long key = entry.getLongKey();
			// Eviction must cancel queued CPU work before it can enter a worker;
			// in-flight work is discarded by the completion window/generation check.
			this.queued.remove(key);
			this.pending.removeIf(candidate -> candidate.asLong() == key);
			if (this.inFlight.contains(key)) {
				this.invalidatedInFlight.add(key);
			}
			RustGalTerrainRenderer.removeSection(position.getX(), position.getY(), position.getZ(), "cpu-source-window-evicted");
			this.incomingDirections.remove(key);
			this.propagatedIncomingDirections.remove(key);
			this.unavailableSections.remove(key);
			iterator.remove();
		}
		// Sections still waiting for a first build are not in `sections`, so sweep
		// the pending frontier separately; otherwise a camera jump can dispatch
		// stale work after the retained-map eviction above has completed.
		this.pending.removeIf(position -> {
			boolean outside = Math.abs(position.getX() - center.getX()) > horizontalRadius
					|| Math.abs(position.getZ() - center.getZ()) > horizontalRadius;
			if (outside) {
				this.queued.remove(position.asLong());
			}
			return outside;
		});
	}

	private boolean isVisible(SectionPos section, Frustum frustum) {
		// Reuse Sodium's CPU viewport predicate verbatim. It includes the model
		// overhang/precision extent, camera split coordinates, its cylindrical
		// render-distance test, and its exact frustum convention; no GL render
		// list or backend object is involved.
		Viewport currentViewport = this.viewport;
		if (currentViewport == null) {
			if (!(frustum instanceof ViewportProvider viewportProvider)) {
				return false;
			}
			currentViewport = viewportProvider.sodium$createViewport();
		}
		return this.isWithinRenderDistance(currentViewport, section)
			&& currentViewport.isBoxVisible(
			section.minBlockX() + 8, section.minBlockY() + 8, section.minBlockZ() + 8,
			OcclusionCuller.CHUNK_SECTION_SIZE,
			OcclusionCuller.CHUNK_SECTION_SIZE,
			OcclusionCuller.CHUNK_SECTION_SIZE
		);
	}

	/**
	 * Mirrors Sodium's viewport-distance predicate for the semantic CPU source.
	 * The +/- one-block envelope is required because block-model geometry may
	 * extend past its owning section. This is selection policy over immutable
	 * camera/section data, not a Java renderer or OpenGL-state dependency.
	 */
	private boolean isWithinRenderDistance(Viewport viewport, SectionPos section) {
		var camera = viewport.getTransform();
		int originX = section.minBlockX() - camera.intX;
		int originY = section.minBlockY() - camera.intY;
		int originZ = section.minBlockZ() - camera.intZ;
		float deltaX = this.nearestToZero(originX - 1, originX + 17) - camera.fracX;
		float deltaY = this.nearestToZero(originY - 1, originY + 17) - camera.fracY;
		float deltaZ = this.nearestToZero(originZ - 1, originZ + 17) - camera.fracZ;
		float distance = this.terrainSelectionDistance;
		return ((deltaX * deltaX) + (deltaZ * deltaZ)) < (distance * distance)
			&& Math.abs(deltaY) < distance;
	}

	/**
	 * Frozen Sodium skips only terrain that lies beyond fully opaque fog. Keep
	 * that user-facing CPU policy explicit at the semantic callsite instead of
	 * borrowing Sodium's renderer-owned selection state.
	 */
	public static float terrainSelectionDistance(float renderDistance, float fogAlpha, float fogEnd,
			boolean useFogOcclusion) {
		if (!useFogOcclusion || !Mth.equal(fogAlpha, 1.0f)) {
			return renderDistance;
		}
		return Math.min(renderDistance, fogEnd + 0.5f);
	}

	private int nearestToZero(int min, int max) {
		if (min > 0) {
			return min;
		}
		if (max < 0) {
			return max;
		}
		return 0;
	}

	/**
	 * Mirrors Sodium's post-portal nearby-section pass.  Geometry may extend
	 * beyond its owning 16-block section, so an already-built neighbor needs to
	 * render when its enlarged bounds intersect the frustum even if portal
	 * traversal did not reach it.  This is pure semantic selection over this
	 * source's CPU section cache; it neither reads Sodium render lists nor owns
	 * backend state.
	 */
	private void addNearbyVisibleSections(ArrayList<RenderSection> visibleSections, LongOpenHashSet visibleKeys) {
		if (this.lastCameraSection == Long.MIN_VALUE || this.viewport == null) {
			return;
		}
		int originX = SectionPos.x(this.lastCameraSection);
		int originY = SectionPos.y(this.lastCameraSection);
		int originZ = SectionPos.z(this.lastCameraSection);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx == 0 && dy == 0 && dz == 0) {
						continue;
					}
					long key = SectionPos.asLong(originX + dx, originY + dy, originZ + dz);
					if (visibleKeys.contains(key)) {
						continue;
					}
					RenderSection section = this.sections.get(key);
					if (section == null || !section.isBuilt() || section.getFlags() == 0) {
						continue;
					}
					if (this.viewport.isBoxVisible(section.getCenterX(), section.getCenterY(), section.getCenterZ(),
							OcclusionCuller.CHUNK_SECTION_SIZE_NEARBY,
							OcclusionCuller.CHUNK_SECTION_SIZE_NEARBY,
							OcclusionCuller.CHUNK_SECTION_SIZE_NEARBY)) {
						visibleSections.add(section);
						visibleKeys.add(key);
					}
				}
			}
		}
	}


	private void scheduleBuilds(Camera camera) {
		int capacity = Math.max(1, this.workerBuilder.getTotalThreadCount() * 2);
		while (this.inFlight.size() < capacity) {
			SectionPos sectionPos = this.pending.pollFirst();
			if (sectionPos == null) {
				return;
			}
			this.queued.remove(sectionPos.asLong());
			this.scheduleBuild(sectionPos, camera);
		}
	}

	private void scheduleBuild(SectionPos sectionPos, Camera camera) {
		long key = sectionPos.asLong();
		if (!this.inFlight.add(key)) {
			return;
		}
		this.recordPortalBuildLifecycle(key, "dispatched");
        ChunkRenderContext renderContext;
		try {
			renderContext = LevelSlice.prepare(this.level, sectionPos, this.sectionCache);
		} catch (RuntimeException error) {
			this.recordTerrainFailure("prepare", sectionPos, error);
			this.completeUnavailableBuild(sectionPos);
			this.recordPortalBuildLifecycle(key, "prepare-failed");
            return;
        }
		if (renderContext == null) {
			// LevelSlice uses null to report an empty section, not a missing
			// snapshot. Preserve its semantic all-open visibility so the portal
			// frontier can continue through air without falsely blocking capture.
			this.completeEmptyBuild(sectionPos);
			this.recordPortalBuildLifecycle(key, "completed-empty");
            return;
        }
        RenderSection section = new RenderSection(null, sectionPos.getX(), sectionPos.getY(), sectionPos.getZ());
        Vec3 cameraPos = camera.getPosition();
		ChunkBuilderMeshingTask task = new ChunkBuilderMeshingTask(section, ++this.buildFrame,
				new Vector3d(cameraPos.x(), cameraPos.y(), cameraPos.z()), renderContext, SortBehavior.STATIC, true);
		this.workerBuilder.scheduleTask(task, true, result -> this.completedBuilds.add(new CompletedBuild(sectionPos, section, result)), false);
    }

	private void drainCompletedBuilds(Frustum frustum) {
		CompletedBuild completed;
		while ((completed = this.completedBuilds.poll()) != null) {
			long key = completed.sectionPos().asLong();
			this.inFlight.remove(key);
			this.recordPortalBuildLifecycle(key, "worker-completed");
			boolean insideCurrentWindow = this.isInsideCurrentWindow(completed.sectionPos());
			boolean stale = this.invalidatedInFlight.remove(key) || !insideCurrentWindow;
			ChunkBuildOutput output;
			try {
				output = completed.result().unwrap();
			} catch (RuntimeException error) {
				recordTerrainFailure("unwrap", completed.sectionPos(), error);
				this.unavailableSections.add(key);
				continue;
			}
			if (output == null) {
				this.unavailableSections.add(key);
				this.recordPortalBuildLifecycle(key, "output-null");
				continue;
			}
			try {
				if (stale) {
					this.recordPortalBuildLifecycle(key, "discarded-stale");
					continue;
				}
				completed.section().setInfo(output.info);
				completed.section().setIncomingDirections(this.incomingDirections.get(key) & GraphDirectionSet.ALL);
				this.sections.put(key, completed.section());
				this.visibilityGraphDirty = true;
				// This is capture-only provenance for the immutable CPU output. The
				// normal Sodium manager records it on its own path; the independent
				// whole-frame source must do the same before ownership transfers to
				// Rust, otherwise animated-sprite identities vanish from parity
				// receipts even though the semantic mesh contains them.
				StaticTerrainParityDiagnostics.recordChunkBuildOutput(output);
				if (!output.meshes.isEmpty()) {
					RustGalTerrainRenderer.acceptWholeFrameChunkBuildOutput(output);
				}
				this.unavailableSections.remove(key);
				this.requestPropagation(key);
				this.recordPortalBuildLifecycle(key, "accepted");
			} finally {
				output.destroy();
			}
		}
	}

	private boolean isInsideCurrentWindow(SectionPos section) {
		if (this.lastCameraSection == Long.MIN_VALUE) {
			return false;
		}
		int horizontalRadius = this.configuredHorizontalRadius();
		return Math.abs(section.getX() - SectionPos.x(this.lastCameraSection)) <= horizontalRadius
			&& Math.abs(section.getZ() - SectionPos.z(this.lastCameraSection)) <= horizontalRadius;
	}

	private void completeUnavailableBuild(SectionPos sectionPos) {
		long key = sectionPos.asLong();
		this.inFlight.remove(key);
		this.unavailableSections.add(key);
		this.recordPortalBuildLifecycle(key, "unavailable");
	}

	private void recordTerrainFailure(String phase, SectionPos sectionPos, RuntimeException error) {
		wholeFrameTerrainFailureCount++;
		String detail = error.getClass().getSimpleName() + ":" + String.valueOf(error.getMessage());
		if (detail.length() > 240) {
			detail = detail.substring(0, 240);
		}
		lastWholeFrameTerrainFailure = phase + "@" + sectionPos.asLong() + ":" + detail;
	}

	private void completeEmptyBuild(SectionPos sectionPos) {
		long key = sectionPos.asLong();
		this.inFlight.remove(key);
		RenderSection section = new RenderSection(null, sectionPos.getX(), sectionPos.getY(), sectionPos.getZ());
		section.setInfo(BuiltSectionInfo.EMPTY);
		section.setIncomingDirections(this.incomingDirections.get(key) & GraphDirectionSet.ALL);
		this.sections.put(key, section);
		this.visibilityGraphDirty = true;
		this.unavailableSections.remove(key);
		this.requestPropagation(key);
		this.recordPortalBuildLifecycle(key, "accepted-empty");
	}

	private void recordPortalBuildLifecycle(long key, String stage) {
		StaticTerrainParityDiagnostics.recordPortalBuildLifecycle("rust-whole-frame", key, stage,
			this.queued.contains(key), this.inFlight.contains(key), this.invalidatedPending.contains(key),
			this.invalidatedInFlight.contains(key), this.sections.containsKey(key), this.unavailableSections.contains(key));
	}

	private void destroyCompletedBuilds() {
		CompletedBuild completed;
		while ((completed = this.completedBuilds.poll()) != null) {
			try {
				ChunkBuildOutput output = completed.result().unwrap();
				if (output != null) {
					output.destroy();
				}
			} catch (RuntimeException ignored) {
				// Worker failure is already contained by the source's unavailable result path.
			}
		}
	}

	private record CompletedBuild(SectionPos sectionPos, RenderSection section, ChunkJobResult<ChunkBuildOutput> result) {
	}

}
