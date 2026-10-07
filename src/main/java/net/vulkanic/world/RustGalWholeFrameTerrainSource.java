package net.vulkanic.world;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
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
 * needs a build. The graph ({@link RustSectionGraph}) owns that bookkeeping:
 * column readiness, which sections need a build or have one in flight, stale
 * results, block-entity sections, animated sprites and the visit set entity
 * culling tests. Each frame Rust selects the camera pass's sections in Sodium's
 * visit order and lists the build requests; Java dispatches them to Sodium's
 * meshing task, reports results, and keeps only the Java objects (block
 * entities, sprites) the graph's lists name.
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

	/**
	 * Built sections with geometry (flags != 0); read only by the diagnostic
	 * Java visible list. Which sections are ready, need a build, are in
	 * flight or visible lives in {@link #graph}.
	 */
	private final Long2ObjectOpenHashMap<RenderSection> geometrySections = new Long2ObjectOpenHashMap<>();
	/** Built sections holding block entities (the Java objects the graph's lists name). */
	private final Long2ObjectOpenHashMap<RenderSection> blockEntitySections = new Long2ObjectOpenHashMap<>();
	/** Dense ids of animated sprites; the graph stores each section's ids. */
	private final it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap<net.minecraft.client.renderer.texture.TextureAtlasSprite>
		animatedSpriteIds = new it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap<>();
	private final ArrayList<net.minecraft.client.renderer.texture.TextureAtlasSprite> animatedSprites = new ArrayList<>();
	private int[] spriteScratch = new int[16];
	/** Builds in flight: the graph's count at selection plus this frame's dispatches. */
	private int inFlightCount;
	private final ConcurrentLinkedQueue<CompletedBuild> completedBuilds = new ConcurrentLinkedQueue<>();
	private final float[] cullingMatrix = new float[16];
	private final ArrayList<RenderSection> visibleSections = new ArrayList<>();
	private final LongOpenHashSet visibleKeys = new LongOpenHashSet();
	/** Whether the graph holds every published section mesh row. */
	private boolean graphMeshesPublished;
	/** The source whose search belongs to the frame now extracting entities. */
	private static volatile RustGalWholeFrameTerrainSource entityCullingSource;
	// The volume of a section multiplied by the number of sections to be checked at most.
	private static final double MAX_ENTITY_CHECK_VOLUME = 16 * 16 * 16 * 15;
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
			if (level.getSectionsCount() > 64) {
				throw new IllegalStateException("Rust section graph columns hold at most 64 sections");
			}
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
		LevelChunkSection[] chunkSections = this.level.getChunk(chunkX, chunkZ).getSections();
		long nonAirMask = 0L;
		for (int sectionY = this.level.getMinSectionY(); sectionY <= this.level.getMaxSectionY(); sectionY++) {
			int index = this.level.getSectionIndexFromSectionY(sectionY);
			LevelChunkSection chunkSection = index >= 0 && index < chunkSections.length ? chunkSections[index] : null;
			if (chunkSection != null && !chunkSection.hasOnlyAir()) {
				nonAirMask |= 1L << (sectionY - this.level.getMinSectionY());
			}
		}
		if (this.graph.addColumn(chunkX, chunkZ, nonAirMask)) {
			wholeFrameSurfaceQueueDrained = false;
			wholeFrameTerrainQueueDrained = false;
		}
	}

	private void removeColumn(int chunkX, int chunkZ) {
		if (!this.graph.removeColumn(chunkX, chunkZ)) {
			return;
		}
		for (int sectionY = this.level.getMinSectionY(); sectionY <= this.level.getMaxSectionY(); sectionY++) {
			long key = SectionPos.asLong(chunkX, sectionY, chunkZ);
			this.geometrySections.remove(key);
			this.blockEntitySections.remove(key);
			if (RustGalTerrainRenderer.hasSectionAsset(key)) {
				RustGalTerrainRenderer.removeSection(chunkX, sectionY, chunkZ, "cpu-source-column-unready");
			}
		}
	}

	/** Records an accepted empty build (all air, or nothing to mesh). */
	private void acceptEmptySection(int x, int y, int z) {
		long key = SectionPos.asLong(x, y, z);
		this.geometrySections.remove(key);
		this.blockEntitySections.remove(key);
		this.graph.acceptBuild(x, y, z, BuiltSectionInfo.EMPTY.flags, BuiltSectionInfo.EMPTY.visibilityData,
			false, this.spriteScratch, 0);
	}

	/** Records a section's newest accepted build in Java and in the graph. */
	private void acceptBuiltSection(long key, RenderSection section) {
		if (section.getFlags() != 0) {
			this.geometrySections.put(key, section);
		} else {
			this.geometrySections.remove(key);
		}
		boolean globals = section.getGlobalBlockEntities() != null;
		if (globals || section.getCulledBlockEntities() != null) {
			this.blockEntitySections.put(key, section);
		} else {
			this.blockEntitySections.remove(key);
		}
		var sprites = section.getAnimatedSprites();
		int spriteCount = sprites == null ? 0 : sprites.length;
		if (spriteCount > this.spriteScratch.length) {
			this.spriteScratch = new int[Math.max(spriteCount, this.spriteScratch.length * 2)];
		}
		for (int index = 0; index < spriteCount; index++) {
			this.spriteScratch[index] = this.animatedSpriteId(sprites[index]);
		}
		this.graph.acceptBuild(section.getChunkX(), section.getChunkY(), section.getChunkZ(), section.getFlags(),
			section.getVisibilityData(), globals, this.spriteScratch, spriteCount);
	}

	private int animatedSpriteId(net.minecraft.client.renderer.texture.TextureAtlasSprite sprite) {
		int id = this.animatedSpriteIds.getOrDefault(sprite, -1);
		if (id < 0) {
			id = this.animatedSprites.size();
			this.animatedSprites.add(sprite);
			this.animatedSpriteIds.put(sprite, id);
		}
		return id;
	}

	/** Frozen's camera-pass selection and the visible list it implies. */
	private void selectVisible(Frustum frustum, float searchDistance, boolean useOcclusionCulling,
			boolean javaVisibleList) {
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.static-terrain.visible-list");
		RustGalTerrainRenderer.drainGraphMeshRows(this.graph, !this.graphMeshesPublished);
		this.graphMeshesPublished = true;
		frustum.copyCullingMatrix(this.cullingMatrix);
		// Rust records the search's build requests, block-entity sections and
		// entity-culling visit set; Java reads visits only for the diagnostic list.
		this.graph.select(frustum.cameraX(), frustum.cameraY(), frustum.cameraZ(), this.cullingMatrix,
			searchDistance, useOcclusionCulling, javaVisibleList);
		this.inFlightCount = this.graph.inFlightCount();
		this.visibleSections.clear();
		this.visibleKeys.clear();
		entityCullingSource = this;
		if (javaVisibleList) {
			for (int index = 0, count = this.graph.visitCount(); index < count; index++) {
				if (!this.graph.visitBuilt(index) || this.graph.visitFlags(index) == 0) {
					continue;
				}
				long key = SectionPos.asLong(this.graph.visitX(index), this.graph.visitY(index), this.graph.visitZ(index));
				RenderSection section = this.geometrySections.get(key);
				if (section != null && this.visibleKeys.add(key)) {
					this.visibleSections.add(section);
				}
			}
			// Canonical order: an identical visible set must reach Rust identically.
			this.sectionKeyOrder.sort(this.visibleSections, RenderSection::getPositionAsLong);
		}
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
			RustGalTerrainRenderer.selectionReceiptsRequested(),
			net.vulkanic.bridge.VulkanicGalBridge.Struct.STATIC_TERRAIN_SECTION.byteSize(),
			net.vulkanic.bridge.VulkanicGalBridge.Struct.STATIC_TERRAIN_SHADOW_CASTER.byteSize());
		// Sprite use is a set per animation tick (never reset mid-frame); Rust
		// lists each animated sprite of the drawn sections once.
		for (int index = 0; index < selection.animatedCount(); index++) {
			var sprite = this.animatedSprites.get(selection.animatedSprite(index));
			RustGalWorldPrimitiveRenderer.recordAtlasSpriteUse(
				sprite.semanticAnimationResource(), sprite.atlasLocation(), sprite.contents().name());
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
		for (int index = 0, count = graph.blockEntitySectionCount(); index < count; index++) {
			RenderSection section = source.blockEntitySections.get(graph.blockEntitySection(index));
			var blockEntities = section == null ? null : section.getCulledBlockEntities();
			if (blockEntities != null) {
				for (var blockEntity : blockEntities) {
					consumer.accept(blockEntity);
				}
			}
		}
		for (int index = 0, count = graph.globalBlockEntitySectionCount(); index < count; index++) {
			RenderSection section = source.blockEntitySections.get(graph.globalBlockEntitySection(index));
			var blockEntities = section == null ? null : section.getGlobalBlockEntities();
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
		return this.graph.boxVisible(
			SectionPos.posToSectionCoord(x1 - 0.5D), SectionPos.posToSectionCoord(y1 - 0.5D),
			SectionPos.posToSectionCoord(z1 - 0.5D), SectionPos.posToSectionCoord(x2 + 0.5D),
			SectionPos.posToSectionCoord(y2 + 0.5D), SectionPos.posToSectionCoord(z2 + 0.5D));
	}

	private void scheduleBuilds(Camera camera) {
		int capacity = Math.max(1, this.workerBuilder.getTotalThreadCount() * 2);
		for (int index = 0; index < this.graph.buildRequestCount() && this.inFlightCount < capacity; index++) {
			this.scheduleBuild(SectionPos.of(this.graph.buildRequest(index)), camera);
		}
	}

	private void scheduleBuild(SectionPos sectionPos, Camera camera) {
		ChunkRenderContext renderContext;
		try {
			renderContext = LevelSlice.prepare(this.level, sectionPos, this.sectionCache);
		} catch (RuntimeException error) {
			this.recordTerrainFailure("prepare", sectionPos, error);
			// Stays needing a build; retried when visited again.
			return;
		}
		if (renderContext == null) {
			// LevelSlice reports an empty section with null.
			this.acceptEmptySection(sectionPos.getX(), sectionPos.getY(), sectionPos.getZ());
			return;
		}
		this.graph.buildStarted(sectionPos.getX(), sectionPos.getY(), sectionPos.getZ());
		this.inFlightCount++;
		RenderSection section = new RenderSection(null, sectionPos.getX(), sectionPos.getY(), sectionPos.getZ());
		Vec3 cameraPos = camera.getPosition();
		ChunkBuilderMeshingTask task = new ChunkBuilderMeshingTask(section, ++this.buildFrame,
			new Vector3d(cameraPos.x(), cameraPos.y(), cameraPos.z()), renderContext,
			SortBehavior.DYNAMIC_DEFER_NEARBY_ZERO_FRAMES, true);
		this.workerBuilder.scheduleTask(task, true,
			result -> this.completedBuilds.add(new CompletedBuild(sectionPos, section, result)), false);
	}

	private void drainCompletedBuilds() {
		long counts = this.graph.buildCounts();
		int publishLimit = (int) (counts >>> 32) + (int) counts > BACKLOG_THRESHOLD
			? Math.max(MAX_COMPLETED_BUILDS_PER_FRAME, MAX_BACKLOG_COMPLETED_BUILDS_PER_FRAME)
			: MAX_COMPLETED_BUILDS_PER_FRAME;
		CompletedBuild completed;
		while (this.completedBuildsConsumedThisFrame < publishLimit
				&& (completed = this.completedBuilds.poll()) != null) {
			this.completedBuildsConsumedThisFrame++;
			long key = completed.sectionPos().asLong();
			// Stale when edited, reloaded or its column unloaded meanwhile.
			boolean stale = this.graph.finishBuild(completed.sectionPos().getX(), completed.sectionPos().getY(),
				completed.sectionPos().getZ());
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
				if (stale) {
					continue;
				}
				completed.section().setInfo(output.info);
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
		boolean visibleWorkPending = this.graph.buildRequestCount() > 0;
		wholeFrameSurfaceQueueDrained = !visibleWorkPending && this.inFlightCount == 0;
		wholeFrameTerrainQueueDrained = wholeFrameSurfaceQueueDrained && this.completedBuilds.isEmpty();
		if (wholeFrameTerrainQueueDrained && !RustGalTerrainRenderer.finishResourceReloadIfReady()) {
			// CPU work is complete, but the bounded native uploader has not accepted
			// every replacement mesh yet. Continue presenting the old complete domain.
			wholeFrameTerrainQueueDrained = false;
		}
		if (StaticTerrainParityDiagnostics.isEnabled()
				|| net.minecraft.client.dev.DeterministicCameraCapture.isActiveForDiagnostics()) {
			long counts = this.graph.buildCounts();
			wholeFrameTerrainQueueSummary = "visitRequests=" + this.graph.buildRequestCount()
				+ ",needsBuild=" + (counts >>> 32)
				+ ",inFlight=" + (int) counts
				+ ",completed=" + this.completedBuilds.size()
				+ ",retainedGeometry=" + this.geometrySections.size()
				+ ",blockEntitySections=" + this.blockEntitySections.size()
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

	/** Built sections with geometry. */
	public int sectionCount() {
		return this.geometrySections.size();
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
		if (blockPos == null || this.level == null || this.graph == null) {
			return false;
		}
		return this.graph.sectionReady(SectionPos.blockToSectionCoord(blockPos.getX()),
			SectionPos.blockToSectionCoord(blockPos.getY()), SectionPos.blockToSectionCoord(blockPos.getZ()));
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
		if (this.level == null || this.sectionCache == null || this.graph == null) {
			return;
		}
		this.sectionCache.invalidate(sectionX, sectionY, sectionZ);
		if (!this.graph.scheduleRebuild(sectionX, sectionY, sectionZ)) {
			return;
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
		this.geometrySections.clear();
		this.blockEntitySections.clear();
		this.animatedSpriteIds.clear();
		this.animatedSprites.clear();
		this.inFlightCount = 0;
		this.destroyCompletedBuilds();
		this.visibleSections.clear();
		this.visibleKeys.clear();
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
		boolean replacePool = requestedSeparateAo != this.workerSeparateAo;
		if (replacePool) {
			this.workerBuilder.shutdown();
			// Cancelled jobs of the retired pool deliver no completion.
			this.destroyCompletedBuilds();
			this.workerSeparateAo = requestedSeparateAo;
			this.workerBuilder = new ChunkBuilder(this.level, ChunkMeshFormats.COMPACT, requestedSeparateAo,
				semanticMeshWorkerCount());
		}
		this.graph.reloadResources(replacePool);
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
