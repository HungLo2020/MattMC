package net.vulkanic.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.sodium.client.render.StaticTerrainParityDiagnostics;
import net.sodium.client.render.chunk.RenderSection;
import net.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.sodium.client.render.chunk.compile.executor.ChunkBuilder;
import net.sodium.client.render.chunk.compile.executor.ChunkJobResult;
import net.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask;
import net.sodium.client.render.chunk.data.BuiltSectionInfo;
import net.sodium.client.render.chunk.map.ChunkTracker;
import net.sodium.client.render.chunk.map.ChunkTrackerHolder;
import net.sodium.client.render.chunk.translucent_sorting.SortBehavior;
import net.sodium.client.render.chunk.vertex.format.ChunkMeshFormats;
import net.sodium.client.world.LevelSlice;
import net.sodium.client.world.cloned.ChunkRenderContext;
import net.sodium.client.world.cloned.ClonedChunkSectionCache;
import org.joml.Vector3d;

/**
 * CPU-only semantic terrain producer for Rust whole-frame Vulkan. It owns no
 * GL device, region, command list or renderer.
 *
 * <p>Section lifecycle and visibility follow Frozen's Sodium exactly: every
 * section of a ready column (Sodium's {@link ChunkTracker}: the chunk and its
 * eight neighbours carry block and light data) exists in the Rust section
 * graph; all-air sections are built immediately as empty; every other section
 * needs a build. Each frame Rust selects the camera pass's sections in Sodium's
 * visit order ({@link RustSectionGraph}); visited sections that need a build
 * are dispatched to Sodium's pure meshing task, and visited built sections with
 * geometry form the visible list.
 */
public final class RustGalWholeFrameTerrainSource {
	private static final int MAX_SEMANTIC_MESH_WORKERS = 8;
	private static final int BACKLOG_THRESHOLD = 64;
	private static final int MAX_BACKLOG_COMPLETED_BUILDS_PER_FRAME = 8;
	/**
	 * Completed builds published per frame. Publishing decodes the mesh on the
	 * render thread, so a bounded number keeps frames smooth; a large backlog
	 * drains faster.
	 */
	private static final int MAX_COMPLETED_BUILDS_PER_FRAME = Math.max(1,
		Integer.getInteger("mattmc.dev.rustGalWorldMaterial.terrainCompletedBuildsPerFrame", 2));
	private static volatile boolean wholeFrameSurfaceQueueDrained;
	private static volatile boolean wholeFrameTerrainQueueDrained;
	private static volatile String wholeFrameTerrainQueueSummary = "uninitialized";
	private static volatile String lastWholeFrameTerrainFailure = "none";
	private static volatile long wholeFrameTerrainFailureCount;
	private static volatile long resourceReloadEpoch;

	private ClientLevel level;
	private ClonedChunkSectionCache sectionCache;
	private ChunkBuilder workerBuilder;
	private boolean workerSeparateAo;
	private RustSectionGraph graph;
	private long observedResourceReloadEpoch;
	private int buildFrame;

	/** Ready columns (chunk positions) mirrored into the graph. */
	private final LongOpenHashSet readyColumns = new LongOpenHashSet();
	/** Built sections of ready columns, including empty ones. */
	private final Long2ObjectOpenHashMap<RenderSection> sections = new Long2ObjectOpenHashMap<>();
	/** The subset of {@link #sections} with geometry (flags != 0). */
	private final Long2ObjectOpenHashMap<RenderSection> geometrySections = new Long2ObjectOpenHashMap<>();
	/** Built sections with off-screen block entities, in first-build order (Sodium's set). */
	private final it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<RenderSection> globalBlockEntitySections =
		new it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<>();
	/** Sections of ready columns whose current build is missing or stale. */
	private final LongOpenHashSet needsBuild = new LongOpenHashSet();
	/** Rebuilds of already-built sections (block edits): dispatched first. */
	private final LongOpenHashSet urgentRebuilds = new LongOpenHashSet();
	private final LongOpenHashSet inFlight = new LongOpenHashSet();
	/** In-flight builds whose result is stale (edited or column removed). */
	private final LongOpenHashSet staleInFlight = new LongOpenHashSet();
	private final ConcurrentLinkedQueue<CompletedBuild> completedBuilds = new ConcurrentLinkedQueue<>();
	private final float[] cullingMatrix = new float[16];
	private final ArrayList<RenderSection> visibleSections = new ArrayList<>();
	private final LongOpenHashSet visibleKeys = new LongOpenHashSet();
	/** Whether the graph holds every published section mesh row. */
	private boolean graphMeshesPublished;
	/** Every section the current camera search visited, as Sodium's last-visible frame. */
	private final LongOpenHashSet visitedKeySet = new LongOpenHashSet();
	private boolean visitedKeySetCurrent;
	/** The source whose search belongs to the frame now extracting entities. */
	private static volatile RustGalWholeFrameTerrainSource entityCullingSource;
	// The volume of a section multiplied by the number of sections to be checked at most.
	private static final double MAX_ENTITY_CHECK_VOLUME = 16 * 16 * 16 * 15;
	private final LongArrayList buildRequests = new LongArrayList();
	private final SectionKeyOrder sectionKeyOrder = new SectionKeyOrder();
	private int completedBuildsConsumedThisFrame;
	private long lastQueueLogNanos;
	private boolean lastLoggedQueueDrained;
	private long lastLoggedFailureCount;

	public void setLevel(ClientLevel level) {
		if (this.level == level && this.workerBuilder != null && this.sectionCache != null) {
			return;
		}
		this.destroy();
		this.level = level;
		if (level != null) {
			this.sectionCache = new ClonedChunkSectionCache(level);
			// Keep the compact stride while matching the copied pack's AO policy.
			this.workerSeparateAo = RustGalTerrainRenderer.copiedShaderPackSeparateAo();
			this.workerBuilder = new ChunkBuilder(level, ChunkMeshFormats.COMPACT, this.workerSeparateAo,
				semanticMeshWorkerCount());
			this.graph = new RustSectionGraph(level.getMinSectionY(), level.getMaxSectionY());
			this.graphMeshesPublished = false;
		}
	}

	/**
	 * The terrain-particle capture is a narrow material fixture, not a terrain
	 * throughput benchmark; bound its peak construction memory.
	 */
	private static int semanticMeshWorkerCount() {
		if (!System.getProperty("mattmc.dev.rustGalWorldMaterial.terrainParticleScenario", "").isBlank()) {
			return 1;
		}
		return Math.max(1, Math.min(MAX_SEMANTIC_MESH_WORKERS,
			Integer.getInteger("mattmc.dev.rustGalWorldMaterial.terrainMeshWorkers", MAX_SEMANTIC_MESH_WORKERS)));
	}

	public void enqueue(Camera camera, Frustum frustum, int viewportWidth, int viewportHeight,
			float terrainSelectionDistance) {
		if (this.level == null || camera == null || frustum == null) {
			return;
		}
		// Backend selection can settle after LevelRenderer's initial level-change
		// callback; establish the CPU-only source lazily.
		if (this.workerBuilder == null || this.sectionCache == null) {
			this.setLevel(this.level);
		}
		if (this.workerBuilder == null || this.sectionCache == null) {
			return;
		}
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.static-terrain.source-select");
		this.completedBuildsConsumedThisFrame = 0;
		if (this.observedResourceReloadEpoch != resourceReloadEpoch) {
			this.resetForResourceReload();
			this.observedResourceReloadEpoch = resourceReloadEpoch;
		}
		this.applyChunkTrackerEvents();
		this.drainCompletedBuilds();

		net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
		boolean spectatorInSolidBlock = minecraft.player != null && minecraft.player.isSpectator()
			&& this.level.getBlockState(camera.getBlockPosition()).isSolidRender();
		boolean useOcclusionCulling = terrainOcclusionCullingEnabled(minecraft.smartCull, spectatorInSolidBlock);
		// Ordinary frames take their terrain from the graph's Rust selection;
		// diagnostic and receipt frames keep the Java visible list.
		boolean rustSelection = RustGalTerrainRenderer.wholeFrameTerrainSelectionEligible();
		this.selectVisible(frustum, terrainSelectionDistance, useOcclusionCulling, !rustSelection);
		this.scheduleBuilds(camera);
		net.minecraft.client.dev.GraphicsFrameBenchmark.recordCounterSample(
			"world.static-terrain.completed-builds-consumed", this.completedBuildsConsumedThisFrame);
		this.sectionCache.cleanup();
		this.updateDrainedState();
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.static-terrain.source-select");

		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.static-terrain.semantic-submit");
		if (rustSelection) {
			this.enqueueRustTerrainSelection(camera);
			net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.static-terrain.semantic-submit");
			return;
		}
		if (wholeFrameTerrainQueueDrained && (StaticTerrainParityDiagnostics.isEnabled()
				|| net.minecraft.client.dev.DeterministicCameraCapture.isActiveForDiagnostics())) {
			StaticTerrainParityDiagnostics.recordWholeFrameVisibleSections(
				this.visibleSections, camera.getPosition().x(), camera.getPosition().y(), camera.getPosition().z(),
				viewportWidth, viewportHeight, this.visibleKeys::contains);
		}
		ArrayList<RenderSection> shadowCandidates = new ArrayList<>();
		if (net.vulkanic.gui.RustGalFrameCoordinator.isRustShaderExecutionActive()) {
			// As in Frozen (Iris over Sodium's section tree), the shadow pass casts
			// only sections the camera traversal has already built; it never
			// schedules builds of its own. Rust applies the shadow-pass test.
			for (var entries = this.geometrySections.long2ObjectEntrySet().fastIterator(); entries.hasNext(); ) {
				var entry = entries.next();
				if (!this.visibleKeys.contains(entry.getLongKey())) {
					shadowCandidates.add(entry.getValue());
				}
			}
		}
		RustGalTerrainRenderer.enqueueWholeFrameTerrainSections(
			this.visibleSections, shadowCandidates, camera, viewportWidth, viewportHeight);
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.static-terrain.semantic-submit");
	}

	/**
	 * Mirrors Sodium's chunk readiness into the graph ({@code forEachEvent}:
	 * removals, then additions). An added column's all-air sections are built
	 * as empty at once; every other section needs a build.
	 */
	private void applyChunkTrackerEvents() {
		ChunkTracker tracker = ChunkTrackerHolder.get(this.level);
		if (tracker == null) {
			return;
		}
		tracker.forEachEvent(this::addColumn, this::removeColumn);
	}

	private void addColumn(int chunkX, int chunkZ) {
		if (!this.readyColumns.add(ChunkPos.asLong(chunkX, chunkZ))) {
			return;
		}
		this.graph.addColumn(chunkX, chunkZ);
		LevelChunkSection[] chunkSections = this.level.getChunk(chunkX, chunkZ).getSections();
		for (int sectionY = this.level.getMinSectionY(); sectionY <= this.level.getMaxSectionY(); sectionY++) {
			int index = this.level.getSectionIndexFromSectionY(sectionY);
			long key = SectionPos.asLong(chunkX, sectionY, chunkZ);
			LevelChunkSection chunkSection = index >= 0 && index < chunkSections.length ? chunkSections[index] : null;
			if (chunkSection == null || chunkSection.hasOnlyAir()) {
				this.acceptBuiltSection(key, emptySection(chunkX, sectionY, chunkZ));
			} else {
				this.needsBuild.add(key);
			}
		}
		wholeFrameSurfaceQueueDrained = false;
		wholeFrameTerrainQueueDrained = false;
	}

	private void removeColumn(int chunkX, int chunkZ) {
		if (!this.readyColumns.remove(ChunkPos.asLong(chunkX, chunkZ))) {
			return;
		}
		this.graph.removeColumn(chunkX, chunkZ);
		for (int sectionY = this.level.getMinSectionY(); sectionY <= this.level.getMaxSectionY(); sectionY++) {
			long key = SectionPos.asLong(chunkX, sectionY, chunkZ);
			RenderSection section = this.sections.remove(key);
			this.geometrySections.remove(key);
			this.globalBlockEntitySections.remove(key);
			if (section != null || RustGalTerrainRenderer.hasSectionAsset(key)) {
				RustGalTerrainRenderer.removeSection(chunkX, sectionY, chunkZ, "cpu-source-column-unready");
			}
			this.needsBuild.remove(key);
			this.urgentRebuilds.remove(key);
			if (this.inFlight.contains(key)) {
				this.staleInFlight.add(key);
			}
		}
	}

	private static RenderSection emptySection(int x, int y, int z) {
		RenderSection section = new RenderSection(null, x, y, z);
		section.setInfo(BuiltSectionInfo.EMPTY);
		return section;
	}

	/** Records a section's newest accepted build in Java and in the graph. */
	private void acceptBuiltSection(long key, RenderSection section) {
		this.sections.put(key, section);
		if (section.getFlags() != 0) {
			this.geometrySections.put(key, section);
		} else {
			this.geometrySections.remove(key);
		}
		if (section.getGlobalBlockEntities() != null) {
			this.globalBlockEntitySections.put(key, section);
		} else {
			this.globalBlockEntitySections.remove(key);
		}
		this.graph.setInfo(section.getChunkX(), section.getChunkY(), section.getChunkZ(), true,
			section.getFlags(), section.getVisibilityData());
	}

	/** Frozen's camera-pass selection and the visible list it implies. */
	private void selectVisible(Frustum frustum, float searchDistance, boolean useOcclusionCulling,
			boolean javaVisibleList) {
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.static-terrain.visible-list");
		RustGalTerrainRenderer.drainGraphMeshRows(this.graph, !this.graphMeshesPublished);
		this.graphMeshesPublished = true;
		frustum.copyCullingMatrix(this.cullingMatrix);
		this.graph.select(frustum.cameraX(), frustum.cameraY(), frustum.cameraZ(), this.cullingMatrix,
			searchDistance, useOcclusionCulling);
		this.visibleSections.clear();
		this.visibleKeys.clear();
		this.buildRequests.clear();
		this.visitedKeySetCurrent = false;
		entityCullingSource = this;
		if (!javaVisibleList && this.needsBuild.isEmpty()) {
			// Rust selects the frame's terrain; with nothing to build, Java
			// needs no per-section pass over the visits.
			net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.static-terrain.visible-list");
			return;
		}
		int urgent = 0;
		for (int index = 0, count = this.graph.visitCount(); index < count; index++) {
			long key = SectionPos.asLong(this.graph.visitX(index), this.graph.visitY(index), this.graph.visitZ(index));
			if (this.needsBuild.contains(key) && !this.inFlight.contains(key)) {
				// Block edits are important rebuilds; everything else keeps visit order.
				if (this.urgentRebuilds.contains(key)) {
					this.buildRequests.add(urgent++, key);
				} else {
					this.buildRequests.add(key);
				}
			}
			if (javaVisibleList && this.graph.visitBuilt(index) && this.graph.visitFlags(index) != 0) {
				RenderSection section = this.sections.get(key);
				if (section != null && this.visibleKeys.add(key)) {
					this.visibleSections.add(section);
				}
			}
		}
		// Canonical order: an identical visible set must reach Rust identically.
		this.sectionKeyOrder.sort(this.visibleSections, RenderSection::getPositionAsLong);
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.static-terrain.visible-list");
	}

	/**
	 * The frame's static terrain from the graph's Rust selection: the camera
	 * layers and shadow casters Java's compact producer built, and the
	 * animated sprites of the sections it drew.
	 */
	private void enqueueRustTerrainSelection(Camera camera) {
		boolean shadowCandidates = net.vulkanic.gui.RustGalFrameCoordinator.isRustShaderExecutionActive();
		RustSectionGraph.TerrainSelection selection = this.graph.selectTerrain(
			camera.getPosition().x(), camera.getPosition().y(), camera.getPosition().z(),
			RustGalTerrainRenderer.selectionDepthPolicies(), RustGalTerrainRenderer.SELECTION_LAYER_ORDINALS,
			shadowCandidates, RustGalTerrainRenderer.MAX_SELECTION_SHADOW_CANDIDATES,
			net.vulkanic.bridge.VulkanicGalBridge.Struct.STATIC_TERRAIN_SECTION.byteSize(),
			net.vulkanic.bridge.VulkanicGalBridge.Struct.STATIC_TERRAIN_SHADOW_CASTER.byteSize());
		for (int index = 0; index < selection.animatedCount(); index++) {
			RenderSection section = this.sections.get(SectionPos.asLong(
				selection.animatedX(index), selection.animatedY(index), selection.animatedZ(index)));
			if (section != null) {
				RustGalTerrainRenderer.recordAnimatedSpriteUse(section);
			}
		}
		RustGalTerrainRenderer.enqueueWholeFrameTerrainSelection(selection, camera);
	}

	/**
	 * Frozen's entity culling ({@code SodiumWorldRenderer.isEntityVisible}):
	 * an entity is drawn only when its culling box touches a section the
	 * camera search visited this frame. Null when no search belongs to the
	 * frame being extracted, which keeps the plain frustum test.
	 */
	public static Boolean isEntityVisible(net.minecraft.client.renderer.entity.EntityRenderer<?, ?> renderer,
			net.minecraft.world.entity.Entity entity) {
		RustGalWholeFrameTerrainSource source = entityCullingSource;
		if (source == null || source.level == null) {
			return null;
		}
		if (net.minecraft.client.Minecraft.getInstance().shouldEntityAppearGlowing(entity) || entity.shouldShowName()) {
			return true;
		}
		@SuppressWarnings({"unchecked", "rawtypes"})
		net.minecraft.world.phys.AABB box = ((net.minecraft.client.renderer.entity.EntityRenderer)renderer).getBoundingBoxForCulling(entity);
		double volume = (box.maxX - box.minX) * (box.maxY - box.minY) * (box.maxZ - box.minZ);
		if (volume > MAX_ENTITY_CHECK_VOLUME) {
			return true;
		}
		return source.isBoxVisible(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
	}

	/**
	 * Frozen's block-entity extraction ({@code SodiumWorldRenderer.extractBlockEntities})
	 * over this frame's camera search: the culled block entities of each visited
	 * built section, then the global ones of every built section. False when
	 * no search belongs to the frame being extracted.
	 */
	public static boolean forEachVisibleBlockEntity(java.util.function.Consumer<net.minecraft.world.level.block.entity.BlockEntity> consumer) {
		RustGalWholeFrameTerrainSource source = entityCullingSource;
		if (source == null || source.level == null) {
			return false;
		}
		RustSectionGraph graph = source.graph;
		for (int index = 0, count = graph.visitCount(); index < count; index++) {
			if (!graph.visitBuilt(index)
					|| (graph.visitFlags(index) & net.sodium.client.render.chunk.RenderSectionFlags.MASK_HAS_BLOCK_ENTITIES) == 0) {
				continue;
			}
			RenderSection section = source.sections.get(
				SectionPos.asLong(graph.visitX(index), graph.visitY(index), graph.visitZ(index)));
			var blockEntities = section == null ? null : section.getCulledBlockEntities();
			if (blockEntities != null) {
				for (var blockEntity : blockEntities) {
					consumer.accept(blockEntity);
				}
			}
		}
		for (RenderSection section : source.globalBlockEntitySections.values()) {
			var blockEntities = section.getGlobalBlockEntities();
			if (blockEntities != null) {
				for (var blockEntity : blockEntities) {
					consumer.accept(blockEntity);
				}
			}
		}
		return true;
	}

	/** Ends the frame's entity extraction; later checks keep the frustum test. */
	public static void closeEntityCulling() {
		entityCullingSource = null;
	}

	private boolean isBoxVisible(double x1, double y1, double z1, double x2, double y2, double z2) {
		// Boxes outside the valid level height never map to a rendered section.
		if (y2 < this.level.getMinY() + 0.5D || y1 > this.level.getMaxY() - 0.5D) {
			return true;
		}
		if (!this.visitedKeySetCurrent) {
			this.visitedKeySet.clear();
			for (int index = 0, count = this.graph.visitCount(); index < count; index++) {
				this.visitedKeySet.add(SectionPos.asLong(
					this.graph.visitX(index), this.graph.visitY(index), this.graph.visitZ(index)));
			}
			this.visitedKeySetCurrent = true;
		}
		int minX = SectionPos.posToSectionCoord(x1 - 0.5D);
		int minY = SectionPos.posToSectionCoord(y1 - 0.5D);
		int minZ = SectionPos.posToSectionCoord(z1 - 0.5D);
		int maxX = SectionPos.posToSectionCoord(x2 + 0.5D);
		int maxY = SectionPos.posToSectionCoord(y2 + 0.5D);
		int maxZ = SectionPos.posToSectionCoord(z2 + 0.5D);
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				for (int y = minY; y <= maxY; y++) {
					if (this.visitedKeySet.contains(SectionPos.asLong(x, y, z))) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private void scheduleBuilds(Camera camera) {
		int capacity = Math.max(1, this.workerBuilder.getTotalThreadCount() * 2);
		for (int index = 0; index < this.buildRequests.size() && this.inFlight.size() < capacity; index++) {
			this.scheduleBuild(SectionPos.of(this.buildRequests.getLong(index)), camera);
		}
	}

	private void scheduleBuild(SectionPos sectionPos, Camera camera) {
		long key = sectionPos.asLong();
		if (!this.inFlight.add(key)) {
			return;
		}
		ChunkRenderContext renderContext;
		try {
			renderContext = LevelSlice.prepare(this.level, sectionPos, this.sectionCache);
		} catch (RuntimeException error) {
			this.recordTerrainFailure("prepare", sectionPos, error);
			// Stays needing a build; retried when visited again.
			this.inFlight.remove(key);
			return;
		}
		if (renderContext == null) {
			// LevelSlice reports an empty section with null.
			this.inFlight.remove(key);
			this.needsBuild.remove(key);
			this.urgentRebuilds.remove(key);
			this.acceptBuiltSection(key, emptySection(sectionPos.getX(), sectionPos.getY(), sectionPos.getZ()));
			return;
		}
		RenderSection section = new RenderSection(null, sectionPos.getX(), sectionPos.getY(), sectionPos.getZ());
		Vec3 cameraPos = camera.getPosition();
		ChunkBuilderMeshingTask task = new ChunkBuilderMeshingTask(section, ++this.buildFrame,
			new Vector3d(cameraPos.x(), cameraPos.y(), cameraPos.z()), renderContext,
			SortBehavior.DYNAMIC_DEFER_NEARBY_ZERO_FRAMES, true);
		this.workerBuilder.scheduleTask(task, true,
			result -> this.completedBuilds.add(new CompletedBuild(sectionPos, section, result)), false);
	}

	private void drainCompletedBuilds() {
		int publishLimit = this.needsBuild.size() + this.inFlight.size() > BACKLOG_THRESHOLD
			? Math.max(MAX_COMPLETED_BUILDS_PER_FRAME, MAX_BACKLOG_COMPLETED_BUILDS_PER_FRAME)
			: MAX_COMPLETED_BUILDS_PER_FRAME;
		CompletedBuild completed;
		while (this.completedBuildsConsumedThisFrame < publishLimit
				&& (completed = this.completedBuilds.poll()) != null) {
			this.completedBuildsConsumedThisFrame++;
			long key = completed.sectionPos().asLong();
			this.inFlight.remove(key);
			boolean stale = this.staleInFlight.remove(key);
			ChunkBuildOutput output;
			try {
				output = completed.result().unwrap();
			} catch (RuntimeException error) {
				this.recordTerrainFailure("unwrap", completed.sectionPos(), error);
				continue;
			}
			if (output == null) {
				continue;
			}
			try {
				if (stale || !this.readyColumns.contains(ChunkPos.asLong(completed.sectionPos().getX(),
						completed.sectionPos().getZ()))) {
					continue;
				}
				completed.section().setInfo(output.info);
				this.needsBuild.remove(key);
				this.urgentRebuilds.remove(key);
				this.acceptBuiltSection(key, completed.section());
				StaticTerrainParityDiagnostics.recordChunkBuildOutput(output);
				if (!output.meshes.isEmpty()) {
					RustGalTerrainRenderer.acceptWholeFrameChunkBuildOutput(output);
				}
			} finally {
				if (output.translucentData != null) {
					output.translucentData.close();
				}
				output.destroy();
			}
		}
	}

	private void updateDrainedState() {
		boolean visibleWorkPending = !this.buildRequests.isEmpty();
		wholeFrameSurfaceQueueDrained = !visibleWorkPending && this.inFlight.isEmpty();
		wholeFrameTerrainQueueDrained = wholeFrameSurfaceQueueDrained && this.completedBuilds.isEmpty();
		if (wholeFrameTerrainQueueDrained && !RustGalTerrainRenderer.finishResourceReloadIfReady()) {
			// CPU work is complete, but the bounded native uploader has not accepted
			// every replacement mesh yet. Continue presenting the old complete domain.
			wholeFrameTerrainQueueDrained = false;
		}
		if (StaticTerrainParityDiagnostics.isEnabled()
				|| net.minecraft.client.dev.DeterministicCameraCapture.isActiveForDiagnostics()) {
			wholeFrameTerrainQueueSummary = "visitRequests=" + this.buildRequests.size()
				+ ",needsBuild=" + this.needsBuild.size()
				+ ",urgentRebuilds=" + this.urgentRebuilds.size()
				+ ",inFlight=" + this.inFlight.size()
				+ ",completed=" + this.completedBuilds.size()
				+ ",readyColumns=" + this.readyColumns.size()
				+ ",retained=" + this.sections.size()
				+ ",retainedGeometry=" + this.geometrySections.size()
				+ ",visible=" + this.visibleSections.size()
				+ ",scheduledJobs=" + this.workerBuilder.getScheduledJobCount()
				+ ",busyWorkers=" + this.workerBuilder.getBusyThreadCount()
				+ ",failureCount=" + wholeFrameTerrainFailureCount
				+ ",lastFailure=" + lastWholeFrameTerrainFailure
				+ ",drained=" + wholeFrameTerrainQueueDrained;
			long now = System.nanoTime();
			if (wholeFrameTerrainQueueDrained != this.lastLoggedQueueDrained
					|| wholeFrameTerrainFailureCount != this.lastLoggedFailureCount
					|| now - this.lastQueueLogNanos >= 1_000_000_000L) {
				this.lastQueueLogNanos = now;
				this.lastLoggedQueueDrained = wholeFrameTerrainQueueDrained;
				this.lastLoggedFailureCount = wholeFrameTerrainFailureCount;
				System.out.println("[MattMC graphics audit] Rust whole-frame terrain source "
					+ wholeFrameTerrainQueueSummary);
			}
		}
	}

	public int sectionCount() {
		return this.sections.size();
	}

	public static boolean isWholeFrameTerrainQueueDrained() {
		return wholeFrameTerrainQueueDrained;
	}

	/** Diagnostic companion to {@link #isWholeFrameTerrainQueueDrained()}. */
	public static String wholeFrameTerrainQueueSummary() {
		return wholeFrameTerrainQueueSummary;
	}

	/** Rebuilds copied terrain after an atlas/resource replacement. */
	static void requestResourceReload() {
		resourceReloadEpoch++;
	}

	/**
	 * Readiness for the client loading gate: the section's current build has
	 * been consumed (an empty section counts) and no rebuild is outstanding.
	 */
	public boolean isSectionReady(BlockPos blockPos) {
		if (blockPos == null || this.level == null) {
			return false;
		}
		long key = SectionPos.asLong(SectionPos.blockToSectionCoord(blockPos.getX()),
			SectionPos.blockToSectionCoord(blockPos.getY()), SectionPos.blockToSectionCoord(blockPos.getZ()));
		return this.sections.containsKey(key) && !this.needsBuild.contains(key) && !this.inFlight.contains(key);
	}

	public void invalidate(int sectionX, int sectionY, int sectionZ) {
		this.scheduleRebuild(sectionX, sectionY, sectionZ);
	}

	public void onSectionBecomingNonEmpty(int sectionX, int sectionY, int sectionZ) {
		this.scheduleRebuild(sectionX, sectionY, sectionZ);
	}

	/**
	 * Sodium's {@code scheduleRebuild}: only sections of ready columns matter;
	 * a built section keeps drawing its accepted mesh until the replacement
	 * completes, and an unbuilt one already needs its first build.
	 */
	private void scheduleRebuild(int sectionX, int sectionY, int sectionZ) {
		if (this.level == null || this.sectionCache == null) {
			return;
		}
		this.sectionCache.invalidate(sectionX, sectionY, sectionZ);
		if (!this.readyColumns.contains(ChunkPos.asLong(sectionX, sectionZ))
				|| sectionY < this.level.getMinSectionY() || sectionY > this.level.getMaxSectionY()) {
			return;
		}
		long key = SectionPos.asLong(sectionX, sectionY, sectionZ);
		this.needsBuild.add(key);
		if (this.sections.containsKey(key)) {
			this.urgentRebuilds.add(key);
		}
		if (this.inFlight.contains(key)) {
			this.staleInFlight.add(key);
		}
		// A capture must not photograph a frame while a dirty section's
		// replacement CPU mesh is outstanding.
		wholeFrameSurfaceQueueDrained = false;
		wholeFrameTerrainQueueDrained = false;
		net.minecraft.client.dev.DeterministicCameraCapture.invalidateRustWholeFrameTerrainReadiness();
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
		if (this.graph != null) {
			this.graph.close();
			this.graph = null;
		}
		this.readyColumns.clear();
		this.sections.clear();
		this.geometrySections.clear();
		this.globalBlockEntitySections.clear();
		this.needsBuild.clear();
		this.urgentRebuilds.clear();
		this.inFlight.clear();
		this.staleInFlight.clear();
		this.destroyCompletedBuilds();
		this.visibleSections.clear();
		this.visibleKeys.clear();
		this.buildRequests.clear();
		this.buildFrame = 0;
		this.level = null;
	}

	/**
	 * Every built section with geometry needs a rebuild against the new
	 * resources. Accepted meshes stay drawable until their replacements are
	 * published (see {@link RustGalTerrainRenderer#finishResourceReloadIfReady()}).
	 */
	private void resetForResourceReload() {
		boolean requestedSeparateAo = RustGalTerrainRenderer.copiedShaderPackSeparateAo();
		if (requestedSeparateAo != this.workerSeparateAo) {
			this.workerBuilder.shutdown();
			// Cancelled jobs of the retired pool deliver no completion.
			this.destroyCompletedBuilds();
			this.inFlight.clear();
			this.staleInFlight.clear();
			this.workerSeparateAo = requestedSeparateAo;
			this.workerBuilder = new ChunkBuilder(this.level, ChunkMeshFormats.COMPACT, requestedSeparateAo,
				semanticMeshWorkerCount());
		}
		this.staleInFlight.addAll(this.inFlight);
		this.needsBuild.addAll(this.geometrySections.keySet());
		wholeFrameSurfaceQueueDrained = false;
		wholeFrameTerrainQueueDrained = false;
	}

	/** Frozen's smart-cull rule: spectators inside solid blocks see through terrain. */
	static boolean terrainOcclusionCullingEnabled(boolean smartCull, boolean spectatorInSolidBlock) {
		return smartCull && !spectatorInSolidBlock;
	}

	/**
	 * Frozen Sodium skips only terrain that lies beyond fully opaque fog. Keep
	 * that user-facing CPU policy explicit at the semantic callsite.
	 */
	public static float terrainSelectionDistance(float renderDistance, float fogAlpha, float fogEnd,
			boolean useFogOcclusion) {
		if (!useFogOcclusion || !Mth.equal(fogAlpha, 1.0f)) {
			return renderDistance;
		}
		return Math.min(renderDistance, fogEnd + 0.5f);
	}

	private void recordTerrainFailure(String phase, SectionPos sectionPos, RuntimeException error) {
		wholeFrameTerrainFailureCount++;
		String detail = error.getClass().getSimpleName() + ":" + String.valueOf(error.getMessage());
		if (detail.length() > 240) {
			detail = detail.substring(0, 240);
		}
		lastWholeFrameTerrainFailure = phase + "@" + sectionPos.asLong() + ":" + detail;
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
				// The worker failure has nothing left to release.
			}
		}
	}

	private record CompletedBuild(SectionPos sectionPos, RenderSection section, ChunkJobResult<ChunkBuildOutput> result) {
	}
}
