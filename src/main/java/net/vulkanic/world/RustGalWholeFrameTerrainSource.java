package net.vulkanic.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
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
	/**
	 * Publishing a completed section copies its immutable layers into the Rust
	 * asset registry. Draining every worker at once turns an eight-worker burst
	 * into a long render-thread stall, so admit a small, deterministic number per
	 * frame while the previous complete meshes remain drawable.
	 */
	private static final int MAX_COMPLETED_BUILDS_PER_FRAME = Math.max(1,
		Integer.getInteger("mattmc.dev.rustGalWorldMaterial.terrainCompletedBuildsPerFrame", 2));
	private static volatile boolean wholeFrameSurfaceQueueDrained;
	private static volatile boolean wholeFrameTerrainQueueDrained;
	/** Capture-only state explaining the current explicit CPU-source queue state. */
	private static volatile String wholeFrameTerrainQueueSummary = "uninitialized";
	private static volatile String lastWholeFrameTerrainFailure = "none";
	private static volatile long wholeFrameTerrainFailureCount;
	/** Monotonic, CPU-only resource epoch requested by the terrain semantic owner. */
	private static volatile long resourceReloadEpoch;
	private long lastQueueLogNanos;
	private boolean lastLoggedQueueDrained;
	private long lastLoggedFailureCount;
	private long observedResourceReloadEpoch;
    private ClientLevel level;
    private ClonedChunkSectionCache sectionCache;
	private ChunkBuilder workerBuilder;
    private final Long2ObjectOpenHashMap<RenderSection> sections = new Long2ObjectOpenHashMap<>();
	/** Maintained with {@link #replaceRetainedSection}; diagnostics must not scan the resident cache per frame. */
	private int retainedGeometryCount;
	private final LongLinkedOpenHashSet queued = new LongLinkedOpenHashSet();
	private final ArrayDeque<SectionPos> pending = new ArrayDeque<>();
	/**
	 * The read side of the semantic portal BFS.  Keeping this separate from
	 * {@link #propagationPending} makes newly discovered neighbors observable
	 * only on the following wave, matching Sodium's occlusion traversal without
	 * importing its renderer-owned queue or render lists.
	 */
	private final LongArrayFIFOQueue propagationRead = new LongArrayFIFOQueue();
	private final LongArrayFIFOQueue propagationPending = new LongArrayFIFOQueue();
	private final LongOpenHashSet propagationQueued = new LongOpenHashSet();
	private final Long2IntOpenHashMap incomingDirections = new Long2IntOpenHashMap();
	private final Long2IntOpenHashMap propagatedIncomingDirections = new Long2IntOpenHashMap();
	private final LongOpenHashSet inFlight = new LongOpenHashSet();
	private final LongOpenHashSet invalidatedInFlight = new LongOpenHashSet();
	private final LongOpenHashSet invalidatedPending = new LongOpenHashSet();
	/** Dirty resident sections waiting for their packet-backed chunk to return. */
	private final LongOpenHashSet deferredInvalidations = new LongOpenHashSet();
	private final LongOpenHashSet unavailableSections = new LongOpenHashSet();
	private final ConcurrentLinkedQueue<CompletedBuild> completedBuilds = new ConcurrentLinkedQueue<>();
	private long lastCameraSection = Long.MIN_VALUE;
	/** Radius is semantic selection state: option changes must not retain a prior frontier. */
	private int lastHorizontalRadius = -1;
	/** Complete CPU visibility input used by the resident portal traversal. */
	private long lastVisibilitySignature = Long.MIN_VALUE;
	/** Visibility input represented by the current, possibly incomplete portal graph. */
	private long traversalVisibilitySignature = Long.MIN_VALUE;
	/** Final visible section snapshot for an unchanged, fully drained frame. */
	private ArrayList<RenderSection> cachedVisibleSections = new ArrayList<>();
	private LongOpenHashSet cachedVisibleKeys = new LongOpenHashSet();
	private LongOpenHashSet cachedPortalVisibleKeys = new LongOpenHashSet();
	/** Whether the cached portal-only subset was populated for capture diagnostics. */
	private boolean cachedPortalVisibilityDiagnosticValid;
	/**
	 * Last visibility domain actually published to Rust. A replacement portal
	 * search is assembled incrementally while section builds complete; retaining
	 * this CPU identity set prevents visibility reconciliation from withdrawing
	 * resident meshes merely because the replacement search is incomplete.
	 */
	private LongOpenHashSet publishedVisibleKeys = new LongOpenHashSet();
	/** One-frame debounce for a complete empty replacement of a non-empty domain. */
	private int consecutiveDrainedEmptySelections;
	private long cachedVisibleSignature = Long.MIN_VALUE;
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
	/** Reused CPU buffers for one portal BFS wave; native visibility is batched once per wave. */
	private RenderSection[] visibilityBatchSections = new RenderSection[256];
	private int[] visibilityBatchIncoming = new int[256];
	private int[] visibilityBatchOutgoing = new int[256];
	private int completedBuildsConsumedThisFrame;
    private int buildFrame;
	private long emptySnapshotBuilds;
	private long meshlessOutputBuilds;
	private String emptySnapshotSample = "none";
	private String meshlessOutputSample = "none";

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
		if (!System.getProperty("mattmc.dev.rustGalWorldMaterial.terrainParticleScenario", "").isBlank()) {
			return 1;
		}
		// Vanilla terrain is the near-field correctness source and must become
		// available promptly even while DH is generating. Keep this bounded pool
		// independent of optional far-terrain work; the audit knob can still
		// measure smaller pools explicitly without changing ownership.
		return Math.max(1, Math.min(MAX_SEMANTIC_MESH_WORKERS,
			Integer.getInteger("mattmc.dev.rustGalWorldMaterial.terrainMeshWorkers",
				MAX_SEMANTIC_MESH_WORKERS)));
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
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.static-terrain.source-select");
		this.completedBuildsConsumedThisFrame = 0;
		this.viewport = viewportProvider.sodium$createViewport();
		this.terrainSelectionDistance = terrainSelectionDistance;
		long visibilitySignature = frustum instanceof net.minecraft.client.renderer.culling.Frustum vanillaFrustum
			? vanillaFrustum.semanticSignature() ^ Float.floatToIntBits(terrainSelectionDistance)
			: Long.MIN_VALUE;
		if (this.observedResourceReloadEpoch != resourceReloadEpoch) {
			this.resetForResourceReload();
			this.observedResourceReloadEpoch = resourceReloadEpoch;
		}
		SectionPos cameraSection = SectionPos.of(camera.getPosition());
		long cameraKey = cameraSection.asLong();
		int horizontalRadius = this.configuredHorizontalRadius();
		if (cameraKey != this.lastCameraSection || horizontalRadius != this.lastHorizontalRadius) {
			this.cachedVisibleSignature = Long.MIN_VALUE;
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
				this.seedVisibilityFrontier(cameraSection, frustum);
				this.traversalVisibilitySignature = visibilitySignature;
				// All resident waves can be traversed immediately. Publishing only one
				// wave here made section-boundary movement erase most terrain for several
				// frames even though its immutable meshes were still resident.
		} else if (visibilitySignature != this.traversalVisibilitySignature) {
			// Camera visibility is independent of worker readiness. Rebuild the graph
			// from resident immutable sections immediately; queued and in-flight nodes
			// are rediscovered as frontier leaves without cancelling their CPU work.
			this.resetVisibilityFrontier();
			this.seedVisibilityFrontier(cameraSection, frustum);
			this.traversalVisibilitySignature = visibilitySignature;
		} else if (!wholeFrameTerrainQueueDrained) {
			// Revisit the current bootstrap domain so transiently unavailable seed
			// sections can never be mistaken for a settled terrain source.
			this.seedVisibilityFrontier(cameraSection, frustum);
		}
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.static-terrain.source-frontier");
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.static-terrain.frontier-invalidation");
		this.reactivateDeferredInvalidations();
		this.admitInvalidatedSections();
		this.retryUnavailableSections(frustum);
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.static-terrain.frontier-invalidation");
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.static-terrain.frontier-completions");
		this.drainCompletedBuilds(frustum);
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.static-terrain.frontier-completions");
		if (this.visibilityGraphDirty) {
			this.cachedVisibleSignature = Long.MIN_VALUE;
			// Do not wait for every worker job in the view to finish. A newly built
			// portal can expose an already-resident branch immediately; retaining
			// the old frontier until global quiescence permanently loses that branch
			// under continuous chunk/light updates. This is semantic CPU selection
			// only and deliberately does not touch Sodium render lists or backend state.
			this.resetVisibilityFrontier();
			this.seedVisibilityFrontier(cameraSection, frustum);
			this.visibilityGraphDirty = false;
		}
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.static-terrain.frontier-traversal");
		this.drainVisibilityFrontierFully(frustum);
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.static-terrain.frontier-traversal");
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.static-terrain.frontier-scheduling");
		this.scheduleBuilds(camera);
		// A worker may finish while the first bounded scheduling pass is still
		// assembling the frame. Consume that completion immediately so its
		// visibility frontier can admit the next ring without waiting for a
		// completely unrelated render frame. Keep this as one additional pass:
		// producer work remains explicitly bounded and the settled gate still
		// requires the queues to be empty after the pass.
		this.drainCompletedBuilds(frustum);
		this.drainVisibilityFrontierFully(frustum);
		this.scheduleBuilds(camera);
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.static-terrain.frontier-scheduling");
		net.minecraft.client.dev.GraphicsFrameBenchmark.recordCounterSample(
			"world.static-terrain.completed-builds-consumed", this.completedBuildsConsumedThisFrame);
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.static-terrain.source-frontier");
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
		if (wholeFrameTerrainQueueDrained
			&& !RustGalTerrainRenderer.finishResourceReloadIfReady()) {
			// CPU work is complete, but the bounded native uploader has not accepted
			// every replacement mesh yet. Continue presenting the old complete domain.
			wholeFrameTerrainQueueDrained = false;
		}
		boolean terrainDiagnosticsActive = StaticTerrainParityDiagnostics.isEnabled()
			|| net.minecraft.client.dev.DeterministicCameraCapture.isActiveForDiagnostics();
		if (terrainDiagnosticsActive) {
			wholeFrameTerrainQueueSummary = "pending=" + this.pending.size()
				+ ",horizontalRadius=" + this.configuredHorizontalRadius()
				+ ",terrainSelectionDistance=" + this.terrainSelectionDistance
				+ ",scheduledJobs=" + this.workerBuilder.getScheduledJobCount()
				+ ",busyWorkers=" + this.workerBuilder.getBusyThreadCount()
				+ ",workerThreads=" + this.workerBuilder.getTotalThreadCount()
				+ ",frontier=" + (this.propagationRead.size() + this.propagationPending.size())
				+ ",inFlight=" + this.inFlight.size()
				+ ",invalidatedPending=" + this.invalidatedPending.size()
				+ ",deferredInvalidations=" + this.deferredInvalidations.size()
				+ ",invalidatedSample=" + this.invalidatedPendingSample()
				+ ",invalidatedInFlight=" + this.invalidatedInFlight.size()
				+ ",completed=" + this.completedBuilds.size()
				+ ",retained=" + this.sections.size()
				+ ",retainedGeometry=" + this.retainedGeometryCount
				+ ",retainedEmpty=" + (this.sections.size() - this.retainedGeometryCount)
				+ ",emptySnapshots=" + this.emptySnapshotBuilds
				+ ",meshlessOutputs=" + this.meshlessOutputBuilds
				+ ",emptySnapshotSample=" + this.emptySnapshotSample
				+ ",meshlessOutputSample=" + this.meshlessOutputSample
				+ ",unavailable=" + this.unavailableSections.size()
				+ ",failureCount=" + wholeFrameTerrainFailureCount
				+ ",lastFailure=" + lastWholeFrameTerrainFailure
				+ ",drained=" + wholeFrameTerrainQueueDrained;
			long queueLogNanos = System.nanoTime();
			boolean queueDrainChanged = wholeFrameTerrainQueueDrained != this.lastLoggedQueueDrained;
			boolean failureChanged = wholeFrameTerrainFailureCount != this.lastLoggedFailureCount;
			if (queueDrainChanged || failureChanged || queueLogNanos - this.lastQueueLogNanos >= 1_000_000_000L) {
				this.lastQueueLogNanos = queueLogNanos;
				this.lastLoggedQueueDrained = wholeFrameTerrainQueueDrained;
				this.lastLoggedFailureCount = wholeFrameTerrainFailureCount;
				System.out.println("[MattMC graphics audit] Rust whole-frame terrain source "
					+ wholeFrameTerrainQueueSummary);
			}
		}
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.static-terrain.source-select");
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.static-terrain.visible-list");
		boolean reuseVisibleSnapshot = wholeFrameTerrainQueueDrained
			&& visibilitySignature == this.lastVisibilitySignature
			&& visibilitySignature == this.cachedVisibleSignature
			&& (!terrainDiagnosticsActive || this.cachedPortalVisibilityDiagnosticValid);
		net.minecraft.client.dev.GraphicsFrameBenchmark.recordCounterSample(
			"world.static-terrain.visible-cache-hit", reuseVisibleSnapshot ? 1L : 0L);
		ArrayList<RenderSection> visibleSections;
		LongOpenHashSet visibleKeys;
		LongOpenHashSet portalVisibleKeys;
		if (reuseVisibleSnapshot) {
			visibleSections = this.cachedVisibleSections;
			visibleKeys = this.cachedVisibleKeys;
			portalVisibleKeys = this.cachedPortalVisibleKeys;
		} else {
			visibleSections = this.cachedVisibleSections;
			visibleKeys = this.cachedVisibleKeys;
			portalVisibleKeys = this.cachedPortalVisibleKeys;
			visibleSections.clear();
			visibleKeys.clear();
			portalVisibleKeys.clear();
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
				&& (bootstrapRoot || this.isVisible(
					section.getChunkX(), section.getChunkY(), section.getChunkZ(), frustum))) {
				visibleSections.add(section);
				visibleKeys.add(entry.getLongKey());
			}
			}
			if (terrainDiagnosticsActive) {
				portalVisibleKeys.addAll(visibleKeys);
				this.cachedPortalVisibilityDiagnosticValid = true;
			} else {
				this.cachedPortalVisibilityDiagnosticValid = false;
			}
			this.addNearbyVisibleSections(visibleSections, visibleKeys);
			int selectedBeforeRetention = visibleSections.size();
			boolean drainedEmptyReplacement = wholeFrameTerrainQueueDrained
				&& selectedBeforeRetention == 0 && !this.publishedVisibleKeys.isEmpty();
			this.consecutiveDrainedEmptySelections = drainedEmptyReplacement
				? this.consecutiveDrainedEmptySelections + 1
				: 0;
			boolean deferEmptyReplacement = this.consecutiveDrainedEmptySelections == 1;
			if (!wholeFrameTerrainQueueDrained || deferEmptyReplacement) {
				this.retainPublishedVisibleSections(visibleSections, visibleKeys, frustum);
			}
			net.minecraft.client.dev.GraphicsFrameBenchmark.recordCounterSample(
				"world.static-terrain.empty-domain-retained-sections",
				deferEmptyReplacement ? visibleSections.size() - selectedBeforeRetention : 0L);
			this.publishedVisibleKeys.clear();
			this.publishedVisibleKeys.addAll(visibleKeys);
			// A deferred empty replacement is deliberately provisional. Caching the
			// retained list here prevents the next identical frame from rebuilding
			// the raw portal selection, turning the intended one-frame debounce into
			// permanent stale terrain for a stationary camera. Force one more CPU
			// selection so either recovered portals replace it or the repeated empty
			// result authoritatively withdraws it.
			this.cachedVisibleSignature = deferEmptyReplacement
				? Long.MIN_VALUE
				: visibilitySignature;
		}

			// Capture-only receipt of the final Rust-owned CPU visibility domain.
		// Wait for the source's existing settled state so startup-empty samples
		// cannot displace the comparable capture-phase observation.  This does
		// not provide the renderer with a list or influence admission.
		if (wholeFrameTerrainQueueDrained && terrainDiagnosticsActive) {
			StaticTerrainParityDiagnostics.recordWholeFrameVisibleSections(
				visibleSections, camera.getPosition().x(), camera.getPosition().y(), camera.getPosition().z(),
				viewportWidth, viewportHeight, portalVisibleKeys::contains
			);
		}
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.static-terrain.visible-list");
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.static-terrain.semantic-submit");
		RustGalTerrainRenderer.enqueueWholeFrameTerrainSections(visibleSections, camera, viewportWidth, viewportHeight);
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.static-terrain.semantic-submit");
			if (wholeFrameTerrainQueueDrained) {
				this.lastVisibilitySignature = visibilitySignature;
			}
	    }

	/**
	 * Keep the last complete/presented CPU selection while a replacement portal
	 * graph is still being built. Keys are resolved back through the current
	 * section cache so updated immutable mesh semantics replace old values, and
	 * window/frustum filtering prevents stale camera domains from leaking across
	 * movement. A complete empty result is deferred for one frame so a transient
	 * false drain cannot erase the world; a repeated empty result is authoritative.
	 */
	private void retainPublishedVisibleSections(ArrayList<RenderSection> visibleSections,
			LongOpenHashSet visibleKeys, Frustum frustum) {
		for (long key : this.publishedVisibleKeys) {
			if (visibleKeys.contains(key)) {
				continue;
			}
			RenderSection section = this.sections.get(key);
			boolean bootstrapRoot = key == this.lastCameraSection;
			if (section != null && section.getFlags() != 0
					&& this.isInsideCurrentWindow(section.getChunkX(), section.getChunkZ())
					&& (bootstrapRoot || this.isVisible(
						section.getChunkX(), section.getChunkY(), section.getChunkZ(), frustum))) {
				visibleSections.add(section);
				visibleKeys.add(key);
			}
		}
	}

	/** True once all client-resident nearby surface sections have been attempted. */
	public static boolean isWholeFrameSurfaceQueueDrained() {
		return wholeFrameSurfaceQueueDrained;
	}

	private RenderSection replaceRetainedSection(long key, RenderSection section) {
		RenderSection previous = this.sections.put(key, section);
		if (previous != null && previous.getFlags() != 0) {
			this.retainedGeometryCount--;
		}
		if (section != null && section.getFlags() != 0) {
			this.retainedGeometryCount++;
		}
		return previous;
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
			&& !this.deferredInvalidations.contains(key)
			&& !this.unavailableSections.contains(key);
	}

    public void invalidate(int sectionX, int sectionY, int sectionZ) {
		this.invalidate(sectionX, sectionY, sectionZ, false);
	}

	/** A newly populated section may make a previously absent chunk edge traversable. */
	public void onSectionBecomingNonEmpty(int sectionX, int sectionY, int sectionZ) {
		this.invalidate(sectionX, sectionY, sectionZ, true);
	}

	private void invalidate(int sectionX, int sectionY, int sectionZ, boolean discoverUnowned) {
        if (this.level == null || this.sectionCache == null) {
            return;
        }
		long key = SectionPos.asLong(sectionX, sectionY, sectionZ);
		this.sectionCache.invalidate(sectionX, sectionY, sectionZ);
		SectionPos section = SectionPos.of(sectionX, sectionY, sectionZ);
		if (!this.isInsideCurrentWindow(section)) {
			return;
		}
		boolean owned = this.ownsSectionLifecycle(key);
		if (!owned && !discoverUnowned) {
			// Light/chunk padding notifications cover many coordinates the portal
			// graph never selected. Their clone data must be fresh if later admitted,
			// but they do not change the currently published visibility domain.
			return;
		}
		// Deterministic readiness is observed before the next render enqueue. Do
		// not leave a previous-frame "drained" receipt visible across a newly
		// dirty semantic section, or capture can photograph the route while its
		// replacement CPU mesh is still outstanding.
		wholeFrameSurfaceQueueDrained = false;
		wholeFrameTerrainQueueDrained = false;
		net.minecraft.client.dev.DeterministicCameraCapture.invalidateRustWholeFrameTerrainReadiness();
		if (!owned) {
			// A newly non-empty section is the explicit discovery signal for a
			// previously unowned chunk edge. Reconsider resident portals without
			// bypassing their admission policy by building this coordinate directly.
			this.cachedVisibleSignature = Long.MIN_VALUE;
			this.visibilityGraphDirty = true;
			return;
		}
		if (discoverUnowned) {
			this.cachedVisibleSignature = Long.MIN_VALUE;
			this.visibilityGraphDirty = true;
		}
		// Keep the last accepted immutable section and Rust mesh visible until its
		// replacement has completed and can be published as one transaction.
		this.unavailableSections.remove(key);
		this.deferredInvalidations.remove(key);
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

	/** True when this source has admitted the section into its CPU lifecycle. */
	private boolean ownsSectionLifecycle(long key) {
		return this.sections.containsKey(key)
			|| RustGalTerrainRenderer.hasSectionAsset(key)
			|| this.queued.contains(key)
			|| this.inFlight.contains(key)
			|| this.invalidatedPending.contains(key)
			|| this.invalidatedInFlight.contains(key)
			|| this.deferredInvalidations.contains(key)
			|| this.unavailableSections.contains(key)
			|| this.incomingDirections.containsKey(key)
			|| this.propagatedIncomingDirections.containsKey(key);
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
		this.retainedGeometryCount = 0;
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
		this.deferredInvalidations.clear();
		this.unavailableSections.clear();
		this.destroyCompletedBuilds();
		this.lastCameraSection = Long.MIN_VALUE;
		this.lastHorizontalRadius = -1;
		this.lastVisibilitySignature = Long.MIN_VALUE;
		this.traversalVisibilitySignature = Long.MIN_VALUE;
		this.cachedVisibleSignature = Long.MIN_VALUE;
		this.cachedVisibleSections.clear();
			this.cachedVisibleKeys.clear();
			this.cachedPortalVisibleKeys.clear();
			this.cachedPortalVisibilityDiagnosticValid = false;
			this.publishedVisibleKeys.clear();
		this.consecutiveDrainedEmptySelections = 0;
		this.viewport = null;
		this.visibilityGraphDirty = false;
        this.buildFrame = 0;
		this.emptySnapshotBuilds = 0L;
		this.meshlessOutputBuilds = 0L;
		this.emptySnapshotSample = "none";
		this.meshlessOutputSample = "none";
		this.level = null;
    }

	private void resetForResourceReload() {
		// Renderer publication is a separate two-phase transaction. Clear only this
		// CPU source generation here; the last completed Rust-owned mesh/atlas set
		// remains drawable until finishResourceReloadIfReady() commits its successor.
		this.sections.clear();
		this.retainedGeometryCount = 0;
		this.queued.clear();
		this.pending.clear();
		this.propagationRead.clear();
		this.propagationPending.clear();
		this.propagationQueued.clear();
		this.incomingDirections.clear();
		this.propagatedIncomingDirections.clear();
		this.invalidatedInFlight.addAll(this.inFlight);
		this.invalidatedPending.clear();
		this.deferredInvalidations.clear();
		this.unavailableSections.clear();
		this.lastCameraSection = Long.MIN_VALUE;
		this.lastHorizontalRadius = -1;
		this.lastVisibilitySignature = Long.MIN_VALUE;
		this.traversalVisibilitySignature = Long.MIN_VALUE;
		this.cachedVisibleSignature = Long.MIN_VALUE;
		this.cachedVisibleSections.clear();
			this.cachedVisibleKeys.clear();
			this.cachedPortalVisibleKeys.clear();
			this.cachedPortalVisibilityDiagnosticValid = false;
			this.publishedVisibleKeys.clear();
		this.consecutiveDrainedEmptySelections = 0;
		this.visibilityGraphDirty = false;
		wholeFrameSurfaceQueueDrained = false;
		wholeFrameTerrainQueueDrained = false;
	}

	/**
	 * Keeps one bounded transition ring outside the nominal section radius.
	 * Sodium's distance predicate includes a one-block model-overhang envelope,
	 * so immediately evicting at the nominal radius can retire a still-visible
	 * edge section when the camera crosses a section boundary. The exact block
	 * distance and frustum tests remain the admission gate; this ring changes
	 * residency lifetime, not the configured visible distance.
	 */
	private int configuredHorizontalRadius() {
		return Math.max(1, Minecraft.getInstance().options.getEffectiveRenderDistance()) + 1;
	}

	/**
	 * Seeds the same CPU visibility domain as Sodium. A camera inside the build
	 * height starts from its containing section. Above or below the build height,
	 * Sodium starts from every frustum-visible section on the nearest boundary
	 * plane in diamond-spiral order; rejecting the out-of-height camera section
	 * without those seeds makes an otherwise loaded world publish an empty set.
	 */
	private void seedVisibilityFrontier(SectionPos cameraSection, Frustum frustum) {
		int cameraY = cameraSection.getY();
		if (cameraY < this.level.getMinSectionY()) {
			this.seedOutsideWorldHeight(cameraSection, this.level.getMinSectionY(), GraphDirection.DOWN, frustum);
		} else if (cameraY > this.level.getMaxSectionY()) {
			this.seedOutsideWorldHeight(cameraSection, this.level.getMaxSectionY(), GraphDirection.UP, frustum);
		} else {
			this.admitSection(cameraSection, GraphDirectionSet.NONE, true, frustum);
		}
	}

	private void seedOutsideWorldHeight(SectionPos cameraSection, int boundaryY, int incomingDirection,
			Frustum frustum) {
		int originX = cameraSection.getX();
		int originZ = cameraSection.getZ();
		int radius = Math.max(0, Mth.floor(this.terrainSelectionDistance / 16.0F));
		this.admitOutsideWorldSeed(originX, boundaryY, originZ, incomingDirection, frustum);
		for (int layer = 1; layer <= radius; layer++) {
			for (int z = -layer; z < layer; z++) {
				int x = Math.abs(z) - layer;
				this.admitOutsideWorldSeed(originX + x, boundaryY, originZ + z, incomingDirection, frustum);
			}
			for (int z = layer; z > -layer; z--) {
				int x = layer - Math.abs(z);
				this.admitOutsideWorldSeed(originX + x, boundaryY, originZ + z, incomingDirection, frustum);
			}
		}
		for (int layer = radius + 1; layer <= 2 * radius; layer++) {
			int inner = layer - radius;
			for (int z = -radius; z <= -inner; z++) {
				this.admitOutsideWorldSeed(originX - z - layer, boundaryY, originZ + z, incomingDirection, frustum);
			}
			for (int z = inner; z <= radius; z++) {
				this.admitOutsideWorldSeed(originX + z - layer, boundaryY, originZ + z, incomingDirection, frustum);
			}
			for (int z = radius; z >= inner; z--) {
				this.admitOutsideWorldSeed(originX + layer - z, boundaryY, originZ + z, incomingDirection, frustum);
			}
			for (int z = -inner; z >= -radius; z--) {
				this.admitOutsideWorldSeed(originX + layer + z, boundaryY, originZ + z, incomingDirection, frustum);
			}
		}
	}

	private void admitOutsideWorldSeed(int sectionX, int sectionY, int sectionZ, int incomingDirection,
			Frustum frustum) {
		this.admitSection(sectionX, sectionY, sectionZ,
			GraphDirectionSet.of(incomingDirection), false, frustum);
	}

	/**
	 * Starts at the camera section and expands through the immutable portal
	 * visibility data generated by the same CPU mesher used for the semantic
	 * terrain payload. This deliberately avoids Sodium's OpenGL-owned render
	 * lists while avoiding the raw-frustum vertical-volume over-admission.
	 */
	private void admitSection(SectionPos section, int incoming, boolean origin, Frustum frustum) {
		if (section == null) {
			StaticTerrainParityDiagnostics.recordPortalAdmission("rust-whole-frame", Long.MIN_VALUE, incoming,
				origin, false, false, false, false, false, false, false, false);
			return;
		}
		this.admitSection(section.getX(), section.getY(), section.getZ(), incoming, origin, frustum);
	}

	private void admitSection(int sectionX, int sectionY, int sectionZ, int incoming, boolean origin,
			Frustum frustum) {
		// The camera's containing section is necessarily part of the visible
		// semantic frame.  Keep it as the bootstrap root even when a captured or
		// numerically marginal frustum rejects its boundary AABB; propagation and
		// all non-root sections still use the exact frustum test below.
		boolean withinBuildHeight = !this.level.isOutsideBuildHeight(SectionPos.sectionToBlockCoord(sectionY));
		boolean loaded = this.isChunkLoaded(sectionX, sectionZ);
		boolean insideWindow = this.isInsideCurrentWindow(sectionX, sectionZ);
		boolean visible = origin || this.isVisible(sectionX, sectionY, sectionZ, frustum);
		long key = SectionPos.asLong(sectionX, sectionY, sectionZ);
		StaticTerrainParityDiagnostics.recordPortalAdmission("rust-whole-frame", key, incoming,
			origin, withinBuildHeight, loaded, insideWindow, visible, this.sections.containsKey(key),
			this.queued.contains(key), this.inFlight.contains(key), this.unavailableSections.contains(key));
		if (!withinBuildHeight || !insideWindow || !visible) {
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
		if (!loaded) {
			// ClientLevel#hasChunk intentionally reports true for generic client
			// block access, while ClientChunkCache can still hold no packet-backed
			// chunk at this coordinate. Keep the portal input pending until the real
			// chunk arrives; treating the fallback EmptyLevelChunk as authoritative
			// air permanently erases terrain loaded later in the same startup.
			this.unavailableSections.add(key);
			return;
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
			this.pending.addLast(SectionPos.of(sectionX, sectionY, sectionZ));
			this.recordPortalBuildLifecycle(key, "enqueued");
		}
	}

	private boolean isCandidate(SectionPos section, Frustum frustum) {
		return this.isCandidate(section, frustum, false);
	}

	private boolean isCandidate(SectionPos section, Frustum frustum, boolean bootstrapOrigin) {
		return section != null
			&& !this.level.isOutsideBuildHeight(section.minBlockY())
			&& this.isChunkLoaded(section.getX(), section.getZ())
			&& this.isInsideCurrentWindow(section)
			&& (bootstrapOrigin || this.isVisible(section, frustum));
	}

	private boolean isChunkLoaded(int chunkX, int chunkZ) {
		return this.level != null && this.level.getChunkSource().hasChunk(chunkX, chunkZ);
	}

	private void requestPropagation(long key) {
		if (this.propagationQueued.add(key)) {
			this.propagationPending.enqueue(key);
		}
	}

	/** Clears only frame-local traversal state; resident CPU meshes remain owned by this source. */
	private void resetVisibilityFrontier() {
		this.propagationRead.clear();
		this.propagationPending.clear();
		this.propagationQueued.clear();
		this.incomingDirections.clear();
		this.propagatedIncomingDirections.clear();
	}

	private void admitInvalidatedSections() {
		var iterator = this.invalidatedPending.iterator();
		while (iterator.hasNext()) {
			long key = iterator.nextLong();
			SectionPos section = SectionPos.of(key);
			if (!this.isInsideCurrentWindow(section)) {
				this.queued.remove(key);
				this.deferredInvalidations.remove(key);
				iterator.remove();
				continue;
			}
			if (this.level.isOutsideBuildHeight(section.minBlockY())) {
				this.queued.remove(key);
				iterator.remove();
				continue;
			}
			if (!this.isChunkLoaded(section.getX(), section.getZ())) {
				// Keep the accepted mesh drawable while its packet-backed chunk is
				// unavailable. A resident section inside the current window must not be
				// filtered by the instantaneous raw frustum: the published portal domain
				// can still draw it, and doing so would retain stale blocks or lighting.
				this.queued.remove(key);
				this.deferredInvalidations.add(key);
				iterator.remove();
				continue;
			}
			this.unavailableSections.remove(key);
			// `queued` is the ownership gate for the pending CPU build. Calling
			// admitSection after claiming that gate drops the work: its own enqueue
			// guard quite correctly refuses the already-queued key. Invalidated,
			// visible sections must therefore enter the same pending deque directly.
			// Loaded resident sections enter the same pending deque regardless of a
			// transient raw-frustum result. Their accumulated portal inputs are
			// applied when the immutable build result becomes resident.
			this.pending.addLast(section);
			this.recordPortalBuildLifecycle(key, "invalidated-enqueued");
			iterator.remove();
		}
	}

	private void reactivateDeferredInvalidations() {
		var iterator = this.deferredInvalidations.iterator();
		while (iterator.hasNext()) {
			long key = iterator.nextLong();
			SectionPos section = SectionPos.of(key);
			if (!this.isInsideCurrentWindow(section)) {
				iterator.remove();
				continue;
			}
			if (this.isChunkLoaded(section.getX(), section.getZ())) {
				this.invalidatedPending.add(key);
				iterator.remove();
			}
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
			if (!this.isChunkLoaded(section.getX(), section.getZ())
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
			while (!this.propagationPending.isEmpty()) {
				long queuedSection = this.propagationPending.dequeueLong();
				this.propagationQueued.remove(queuedSection);
				this.propagationRead.enqueue(queuedSection);
			}
		}
		Viewport currentViewport = this.viewport;
		if (currentViewport == null) {
			return;
		}
		int batchCount = 0;
		while (!this.propagationRead.isEmpty()) {
			long key = this.propagationRead.dequeueLong();
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
			if ((incoming & originBit) != 0) {
				int outgoing = OcclusionCuller.getVisibilityConnections(
					section.getVisibilityData(), GraphDirectionSet.NONE, false);
				this.propagateVisibility(section, incomingDirections, outgoing, currentViewport, frustum);
				continue;
			}
			this.ensureVisibilityBatchCapacity(batchCount + 1);
			this.visibilityBatchSections[batchCount] = section;
			this.visibilityBatchIncoming[batchCount] = incomingDirections;
			batchCount++;
		}
		OcclusionCuller.getVisibilityConnectionsForCameraBatch(
			this.visibilityBatchSections,
			this.visibilityBatchIncoming,
			currentViewport,
			batchCount,
			this.visibilityBatchOutgoing
		);
		for (int index = 0; index < batchCount; index++) {
			RenderSection section = this.visibilityBatchSections[index];
			this.propagateVisibility(section, this.visibilityBatchIncoming[index],
				this.visibilityBatchOutgoing[index], currentViewport, frustum);
			this.visibilityBatchSections[index] = null;
		}
	}

	private void propagateVisibility(RenderSection section, int incomingDirections, int outgoing,
			Viewport currentViewport, Frustum frustum) {
		int sectionX = section.getChunkX();
		int sectionY = section.getChunkY();
		int sectionZ = section.getChunkZ();
		long key = section.getPositionAsLong();
		var transform = currentViewport.getTransform();
		int outgoingBeforeOutwardMask = outgoing;
		// Mirror Sodium's CPU occlusion traversal: once a portal path has
		// moved away from the camera section, never walk it back toward the
		// camera. Besides preventing redundant graph walks, this is essential
		// to preserve Sodium's visible-section domain without touching its GL
		// render lists or render device.
		outgoing &= this.outwardDirections(sectionX, sectionY, sectionZ);
		int outgoingAfterOutwardMask = outgoing;
		// Sodium applies the region graph's adjacent-mask before admitting a
		// neighbor. This source owns no Java render region, so derive only the
		// structural build-height/window constraint here. Packet availability is
		// handled by admitSection, which retains a missing portal neighbor in the
		// unavailable set until its chunk arrives. Masking unloaded chunks here
		// lets readiness report a false drain while the client view is streaming.
		int adjacentMask = this.structuralAdjacentMask(sectionX, sectionY, sectionZ);
		outgoing &= adjacentMask;
		StaticTerrainParityDiagnostics.recordPortalTraversal("rust-whole-frame", key, this.lastCameraSection,
			incomingDirections, outgoingBeforeOutwardMask, outgoingAfterOutwardMask, adjacentMask, outgoing,
			section.getVisibilityData(),
			transform.x - section.getCenterX(), transform.y - section.getCenterY(), transform.z - section.getCenterZ());
		for (int direction = 0; direction < GraphDirection.COUNT; direction++) {
			if (!GraphDirectionSet.contains(outgoing, direction)) {
				continue;
			}
			this.admitSection(
				sectionX + GraphDirection.x(direction),
				sectionY + GraphDirection.y(direction),
				sectionZ + GraphDirection.z(direction),
				GraphDirectionSet.of(GraphDirection.opposite(direction)), false, frustum);
		}
	}

	private void ensureVisibilityBatchCapacity(int capacity) {
		if (capacity <= this.visibilityBatchSections.length) {
			return;
		}
		int next = Math.max(capacity, this.visibilityBatchSections.length << 1);
		this.visibilityBatchSections = java.util.Arrays.copyOf(this.visibilityBatchSections, next);
		this.visibilityBatchIncoming = java.util.Arrays.copyOf(this.visibilityBatchIncoming, next);
		this.visibilityBatchOutgoing = java.util.Arrays.copyOf(this.visibilityBatchOutgoing, next);
	}

	/** CPU-only structural equivalent of Sodium RenderSection#getAdjacentMask(). */
	private int structuralAdjacentMask(int sectionX, int sectionY, int sectionZ) {
		int mask = GraphDirectionSet.NONE;
		for (int direction = 0; direction < GraphDirection.COUNT; direction++) {
			int neighborX = sectionX + GraphDirection.x(direction);
			int neighborY = sectionY + GraphDirection.y(direction);
			int neighborZ = sectionZ + GraphDirection.z(direction);
			if (!this.level.isOutsideBuildHeight(SectionPos.sectionToBlockCoord(neighborY))
				&& this.isInsideCurrentWindow(neighborX, neighborZ)) {
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

	private int outwardDirections(int sectionX, int sectionY, int sectionZ) {
		if (this.lastCameraSection == Long.MIN_VALUE) {
			return GraphDirectionSet.NONE;
		}
		int originX = SectionPos.x(this.lastCameraSection);
		int originY = SectionPos.y(this.lastCameraSection);
		int originZ = SectionPos.z(this.lastCameraSection);
		int directions = GraphDirectionSet.NONE;
		directions |= sectionX <= originX ? 1 << GraphDirection.WEST : 0;
		directions |= sectionX >= originX ? 1 << GraphDirection.EAST : 0;
		directions |= sectionY <= originY ? 1 << GraphDirection.DOWN : 0;
		directions |= sectionY >= originY ? 1 << GraphDirection.UP : 0;
		directions |= sectionZ <= originZ ? 1 << GraphDirection.NORTH : 0;
		directions |= sectionZ >= originZ ? 1 << GraphDirection.SOUTH : 0;
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
			int sectionX = section.getChunkX();
			int sectionZ = section.getChunkZ();
			if (Math.abs(sectionX - center.getX()) <= horizontalRadius
					&& Math.abs(sectionZ - center.getZ()) <= horizontalRadius) {
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
			RustGalTerrainRenderer.removeSection(
				sectionX, section.getChunkY(), sectionZ, "cpu-source-window-evicted");
			this.incomingDirections.remove(key);
			this.propagatedIncomingDirections.remove(key);
			this.deferredInvalidations.remove(key);
			this.unavailableSections.remove(key);
			if (section.getFlags() != 0) {
				this.retainedGeometryCount--;
			}
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
		var deferredIterator = this.deferredInvalidations.iterator();
		while (deferredIterator.hasNext()) {
			SectionPos position = SectionPos.of(deferredIterator.nextLong());
			if (Math.abs(position.getX() - center.getX()) > horizontalRadius
					|| Math.abs(position.getZ() - center.getZ()) > horizontalRadius) {
				deferredIterator.remove();
			}
		}
	}

	private boolean isVisible(SectionPos section, Frustum frustum) {
		return section != null && this.isVisible(section.getX(), section.getY(), section.getZ(), frustum);
	}

	private boolean isVisible(int sectionX, int sectionY, int sectionZ, Frustum frustum) {
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
		return this.isWithinRenderDistance(currentViewport, sectionX, sectionY, sectionZ)
			&& currentViewport.isBoxVisible(
			SectionPos.sectionToBlockCoord(sectionX) + 8,
			SectionPos.sectionToBlockCoord(sectionY) + 8,
			SectionPos.sectionToBlockCoord(sectionZ) + 8,
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
	private boolean isWithinRenderDistance(Viewport viewport, int sectionX, int sectionY, int sectionZ) {
		var camera = viewport.getTransform();
		int originX = SectionPos.sectionToBlockCoord(sectionX) - camera.intX;
		int originY = SectionPos.sectionToBlockCoord(sectionY) - camera.intY;
		int originZ = SectionPos.sectionToBlockCoord(sectionZ) - camera.intZ;
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
			SectionPos sectionPos = this.pollNearestPending();
			if (sectionPos == null) {
				return;
			}
			this.queued.remove(sectionPos.asLong());
			this.scheduleBuild(sectionPos, camera);
		}
	}

	private SectionPos pollNearestPending() {
		if (this.pending.isEmpty() || this.lastCameraSection == Long.MIN_VALUE) {
			return this.pending.pollFirst();
		}
		// Once the near-field bootstrap has a substantial resident set, preserve
		// FIFO portal order. Empty outer sections then drain synchronously instead
		// of paying a full pending-queue scan for work the player cannot see.
		if (this.sections.size() >= 256) {
			return this.pending.pollFirst();
		}
		int cameraX = SectionPos.x(this.lastCameraSection);
		int cameraY = SectionPos.y(this.lastCameraSection);
		int cameraZ = SectionPos.z(this.lastCameraSection);
		SectionPos nearest = null;
		long nearestDistance = Long.MAX_VALUE;
		for (SectionPos candidate : this.pending) {
			long dx = candidate.getX() - cameraX;
			long dy = candidate.getY() - cameraY;
			long dz = candidate.getZ() - cameraZ;
			long distance = dx * dx + dy * dy + dz * dz;
			if (distance < nearestDistance) {
				nearest = candidate;
				nearestDistance = distance;
			}
		}
		if (nearest != null) this.pending.removeFirstOccurrence(nearest);
		return nearest;
	}

	private void scheduleBuild(SectionPos sectionPos, Camera camera) {
		long key = sectionPos.asLong();
		if (!this.inFlight.add(key)) {
			return;
		}
		this.recordPortalBuildLifecycle(key, "dispatched");
		if (!this.isChunkLoaded(sectionPos.getX(), sectionPos.getZ())) {
			this.completeUnavailableBuild(sectionPos);
			this.recordPortalBuildLifecycle(key, "chunk-unavailable");
			return;
		}
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
				new Vector3d(cameraPos.x(), cameraPos.y(), cameraPos.z()), renderContext,
				SortBehavior.DYNAMIC_DEFER_NEARBY_ZERO_FRAMES, true);
		this.workerBuilder.scheduleTask(task, true, result -> this.completedBuilds.add(new CompletedBuild(sectionPos, section, result)), false);
    }

	private void drainCompletedBuilds(Frustum frustum) {
		CompletedBuild completed;
		while (this.completedBuildsConsumedThisFrame < MAX_COMPLETED_BUILDS_PER_FRAME
				&& (completed = this.completedBuilds.poll()) != null) {
			this.completedBuildsConsumedThisFrame++;
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
				if (output.info.flags == 0) {
					this.meshlessOutputBuilds++;
					if ("none".equals(this.meshlessOutputSample)) {
						this.meshlessOutputSample = sectionPosString(completed.sectionPos());
					}
				}
				completed.section().setIncomingDirections(this.incomingDirections.get(key) & GraphDirectionSet.ALL);
				RenderSection previous = this.replaceRetainedSection(key, completed.section());
				this.acceptSectionVisibilityChange(previous, completed.section());
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
				if (output.translucentData != null) {
					output.translucentData.close();
				}
				output.destroy();
			}
		}
	}

	private boolean isInsideCurrentWindow(SectionPos section) {
		return section != null && this.isInsideCurrentWindow(section.getX(), section.getZ());
	}

	private boolean isInsideCurrentWindow(int sectionX, int sectionZ) {
		if (this.lastCameraSection == Long.MIN_VALUE) {
			return false;
		}
		int horizontalRadius = this.configuredHorizontalRadius();
		return Math.abs(sectionX - SectionPos.x(this.lastCameraSection)) <= horizontalRadius
			&& Math.abs(sectionZ - SectionPos.z(this.lastCameraSection)) <= horizontalRadius;
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
		this.emptySnapshotBuilds++;
		if ("none".equals(this.emptySnapshotSample)) {
			this.emptySnapshotSample = sectionPosString(sectionPos);
		}
		this.inFlight.remove(key);
		RenderSection section = new RenderSection(null, sectionPos.getX(), sectionPos.getY(), sectionPos.getZ());
		section.setInfo(BuiltSectionInfo.EMPTY);
		section.setIncomingDirections(this.incomingDirections.get(key) & GraphDirectionSet.ALL);
		RenderSection previous = this.replaceRetainedSection(key, section);
		this.acceptSectionVisibilityChange(previous, section);
		this.unavailableSections.remove(key);
		this.requestPropagation(key);
		this.recordPortalBuildLifecycle(key, "accepted-empty");
	}

	/**
	 * A first build continues the existing frontier through its accumulated
	 * incoming portals and does not invalidate already traversed nodes. Only an
	 * accepted replacement whose occlusion connectivity changed can alter paths
	 * through the resident graph and therefore requires a full traversal reset.
	 * Every replacement invalidates the visible-object cache so a settled frame
	 * cannot retain the superseded RenderSection instance.
	 */
	private void acceptSectionVisibilityChange(RenderSection previous, RenderSection replacement) {
		this.cachedVisibleSignature = Long.MIN_VALUE;
		if (previous != null && previous.getVisibilityData() != replacement.getVisibilityData()) {
			this.visibilityGraphDirty = true;
		}
	}

	private static String sectionPosString(SectionPos section) {
		return section.getX() + ":" + section.getY() + ":" + section.getZ();
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
