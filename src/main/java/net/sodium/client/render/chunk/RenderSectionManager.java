package net.sodium.client.render.chunk;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMaps;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.*;
import net.sodium.api.texture.SpriteUtil;
import net.sodium.client.SodiumClientMod;
import net.sodium.client.gl.device.CommandList;
import net.sodium.client.gl.device.RenderDevice;
import net.sodium.client.render.chunk.compile.BuilderTaskOutput;
import net.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.sodium.client.render.chunk.compile.ChunkSortOutput;
import net.sodium.client.render.chunk.compile.estimation.*;
import net.sodium.client.render.chunk.compile.estimation.*;
import net.sodium.client.render.chunk.compile.executor.ChunkBuilder;
import net.sodium.client.render.chunk.compile.executor.ChunkJobCollector;
import net.sodium.client.render.chunk.compile.executor.ChunkJobResult;
import net.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask;
import net.sodium.client.render.chunk.compile.tasks.ChunkBuilderSortingTask;
import net.sodium.client.render.chunk.compile.tasks.ChunkBuilderTask;
import net.sodium.client.render.chunk.data.BuiltSectionInfo;
import net.sodium.client.render.chunk.lists.*;
import net.sodium.client.render.chunk.lists.*;
import net.sodium.client.render.chunk.occlusion.GraphDirection;
import net.sodium.client.render.chunk.occlusion.OcclusionCuller;
import net.sodium.client.render.chunk.region.RenderRegion;
import net.sodium.client.render.chunk.region.RenderRegionManager;
import net.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.sodium.client.render.chunk.translucent_sorting.SortBehavior;
import net.sodium.client.render.chunk.translucent_sorting.SortBehavior.PriorityMode;
import net.sodium.client.render.chunk.translucent_sorting.data.DynamicTopoData;
import net.sodium.client.render.chunk.translucent_sorting.data.NoData;
import net.sodium.client.render.chunk.translucent_sorting.data.TranslucentData;
import net.sodium.client.render.chunk.translucent_sorting.trigger.CameraMovement;
import net.sodium.client.render.chunk.translucent_sorting.trigger.SortTriggering;
import net.sodium.client.render.chunk.tree.RemovableMultiForest;
import net.sodium.client.render.util.RenderAsserts;
import net.sodium.client.render.viewport.CameraTransform;
import net.sodium.client.render.viewport.Viewport;
import net.sodium.client.services.PlatformRuntimeInformation;
import net.sodium.client.util.FogParameters;
import net.sodium.api.util.MathUtil;
import net.sodium.client.world.LevelSlice;
import net.sodium.client.world.cloned.ChunkRenderContext;
import net.sodium.client.world.cloned.ClonedChunkSectionCache;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3dc;
import net.vulkanic.VulkanicAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

public class RenderSectionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RenderSectionManager.class);
    private static final float NEARBY_REBUILD_DISTANCE = Mth.square(16.0f);
    private static final float IMMEDIATE_PRESENT_DISTANCE = Mth.square(64.0f);
    private static final float NEARBY_SORT_DISTANCE = Mth.square(25.0f);
    private static final int FALLBACK_VISIT_LIMIT = 2048;
    private static final int MIN_NEARBY_INITIAL_BUILD_QUEUE = 32;
    private static final int MIN_NEARBY_RENDERABLE_SECTIONS = 96;
    private static int vulkanTerrainCollectorProbeCount;

    private static final float FRAME_DURATION_UPLOAD_FRACTION = 0.1f;
    private static final long MIN_UPLOAD_DURATION_BUDGET = 2_000_000L; // 2ms

    private final ChunkBuilder builder;

    private final RenderRegionManager regions;
    private final ClonedChunkSectionCache sectionCache;

    private final Long2ReferenceMap<RenderSection> sectionByPosition = new Long2ReferenceOpenHashMap<>();

    private final ConcurrentLinkedDeque<ChunkJobResult<? extends BuilderTaskOutput>> buildResults = new ConcurrentLinkedDeque<>();
    private final JobDurationEstimator jobDurationEstimator = new JobDurationEstimator();
    private final MeshTaskSizeEstimator meshTaskSizeEstimator;
    private final UploadDurationEstimator jobUploadDurationEstimator = new UploadDurationEstimator();
    private ChunkJobCollector lastBlockingCollector;
    private int thisFrameBlockingTasks;
    private int nextFrameBlockingTasks;
    private int deferredTasks;

    private final ChunkRenderer chunkRenderer;

    private final ClientLevel level;

    private final ReferenceSet<RenderSection> sectionsWithGlobalEntities = new ReferenceOpenHashSet<>();

    private final OcclusionCuller occlusionCuller;

    private final int renderDistance;
    private final SortBehavior sortBehavior;

    private final SortTriggering sortTriggering;

    @NotNull
    private SortedRenderLists renderLists;
    
    // Iris: Shadow rendering support - separate render lists for shadow pass
    @NotNull
    private SortedRenderLists shadowRenderLists = SortedRenderLists.empty();
    private boolean shadowNeedsRenderListUpdate = true;
    private boolean renderListStateIsShadow = false;
    
    private SectionCollector sectionCollector;
    private SectionCollector lastSectionCollector;

    @NotNull
    private Map<TaskQueueType, ArrayDeque<RenderSection>> taskLists;
    
    // Iris: Shadow rendering support - separate task lists for shadow pass
    @NotNull
    private Map<TaskQueueType, ArrayDeque<RenderSection>> shadowTaskLists = new java.util.EnumMap<>(TaskQueueType.class);

    private int frame;
    private long lastFrameDuration = -1;
    private long averageFrameDuration = -1;
    private long lastFrameAtTime = System.nanoTime();
    private static final float FRAME_DURATION_UPDATE_RATIO = 0.05f;

    private boolean needsGraphUpdate = true;
    private int lastUpdatedFrame;

    private @Nullable Vector3dc cameraPosition;

    private final RemovableMultiForest renderableSectionTree;

    public RenderSectionManager(ClientLevel level, int renderDistance, SortBehavior sortBehavior, CommandList commandList) {
        this.meshTaskSizeEstimator = new MeshTaskSizeEstimator(level);

        // Iris: From MixinRenderSectionManager - use extended vertex format
        this.chunkRenderer = new DefaultChunkRenderer(RenderDevice.instance(), net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings.INSTANCE.getVertexFormat());

        this.level = level;
        // Iris: From MixinRenderSectionManager - use extended vertex format for builder
        this.builder = new ChunkBuilder(level, net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings.INSTANCE.getVertexFormat());

        this.renderDistance = renderDistance;
        this.sortBehavior = sortBehavior;

        if (this.sortBehavior != SortBehavior.OFF) {
            this.sortTriggering = new SortTriggering();
        } else {
            this.sortTriggering = null;
        }

        this.regions = new RenderRegionManager(commandList);
        this.sectionCache = new ClonedChunkSectionCache(this.level);

        this.renderLists = SortedRenderLists.empty();
        this.occlusionCuller = new OcclusionCuller(Long2ReferenceMaps.unmodifiable(this.sectionByPosition), this.level);

        this.renderableSectionTree = new RemovableMultiForest(renderDistance);

        this.taskLists = new EnumMap<>(TaskQueueType.class);

        for (var type : TaskQueueType.values()) {
            this.taskLists.put(type, new ArrayDeque<>());
        }
        
        // Iris: Initialize shadow task lists
        for (var type : TaskQueueType.values()) {
            this.shadowTaskLists.put(type, new ArrayDeque<>());
        }
    }

    public void prepareFrame(Vector3dc cameraPosition) {
        var now = System.nanoTime();
        this.lastFrameDuration = now - this.lastFrameAtTime;
        this.lastFrameAtTime = now;
        if (this.averageFrameDuration == -1) {
            this.averageFrameDuration = this.lastFrameDuration;
        } else {
            this.averageFrameDuration = MathUtil.exponentialMovingAverage(this.averageFrameDuration, this.lastFrameDuration, FRAME_DURATION_UPDATE_RATIO);
        }
        this.averageFrameDuration = Mth.clamp(this.averageFrameDuration, 1_000_100, 100_000_000);

        this.frame += 1;

        this.cameraPosition = cameraPosition;
    }

    public void endFrame() {
        this.chunkRenderer.endFrame();
    }

    public void update(Camera camera, Viewport viewport, FogParameters fogParameters, boolean spectator) {
        this.lastUpdatedFrame += 1;

        this.needsGraphUpdate = this.createTerrainRenderList(camera, viewport, fogParameters, this.lastUpdatedFrame, spectator);
    }

    private boolean createTerrainRenderList(Camera camera, Viewport viewport, FogParameters fogParameters, int frame, boolean spectator) {
        // Iris: Handle shadow rendering state transitions
        if (!net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
            if (this.renderListStateIsShadow) {
                // Swap back to regular render lists
                for (var region : this.regions.getLoadedRegions()) {
                    ((net.irisshaders.iris.mixinterface.ShadowRenderRegion) region).swapToRegularRenderList();
                }
                this.renderListStateIsShadow = false;
            }
        } else {
            if (this.shadowNeedsRenderListUpdate) {
                if (!this.renderListStateIsShadow) {
                    // Swap to shadow render lists
                    for (var region : this.regions.getLoadedRegions()) {
                        ((net.irisshaders.iris.mixinterface.ShadowRenderRegion) region).swapToShadowRenderList();
                    }
                    this.renderListStateIsShadow = true;
                }
            }
        }
        
        this.resetRenderLists();

        final var searchDistance = this.getSearchDistance(fogParameters);
        final var useOcclusionCulling = this.shouldUseOcclusionCulling(camera, spectator);

	    var importantRebuildQueueType = SodiumClientMod.options().performance.chunkBuildDeferMode.getImportantRebuildQueueType();
	    var importantSortQueueType = this.sortBehavior != SortBehavior.OFF
	        ? this.sortBehavior.getDeferMode().getImportantRebuildQueueType()
	        : importantRebuildQueueType;
        
        // Iris: Disable occlusion culling for shadows
        boolean iris$disableOcclusion = net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered();
        
        if (iris$disableOcclusion || this.isOutOfGraph(viewport.getChunkCoord())) {
            var visitor = new TreeSectionCollector(frame, importantRebuildQueueType, importantSortQueueType, this.sectionByPosition);
            this.renderableSectionTree.prepareForTraversal();
            this.renderableSectionTree.traverse(visitor, viewport, searchDistance);

            this.sectionCollector = visitor;
        } else {
            var visitor = new OcclusionSectionCollector(frame, importantRebuildQueueType, importantSortQueueType);
            this.occlusionCuller.findVisible(visitor, viewport, searchDistance, useOcclusionCulling, frame);

            this.sectionCollector = visitor;
        }
        this.lastSectionCollector = null;

        // Iris: Use shadow or regular task lists based on current state
        if (net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
            this.shadowTaskLists = this.sectionCollector.getTaskLists();
        } else {
            if (VulkanicAPI.isVulkanBackendSelected()) {
                this.seedCollectorIfStarved(this.sectionCollector, viewport);
                this.logVulkanTerrainCollectorProbe(viewport, this.sectionCollector);
            }
            this.taskLists = this.sectionCollector.getTaskLists();
        }

        // when there were sections with pending updates that were skipped because they already had a task running,
        // it needs to revisit them to schedule the remaining pending updates.
        // since not all tasks necessarily change the section info to trigger a graph update,
        // without this pending updates might be missed when the camera is stationary
        return this.sectionCollector.needsRevisitForPendingUpdates();
    }

    private void logVulkanTerrainCollectorProbe(Viewport viewport, SectionCollector collector) {
        if (vulkanTerrainCollectorProbeCount >= 24) {
            return;
        }

        var renderLists = collector.getUnsortedRenderLists();
        int renderRegionCount = renderLists.size();
        int renderSectionCount = 0;
        int geometrySectionCount = 0;

        for (var renderList : renderLists) {
            renderSectionCount += renderList.size();
            geometrySectionCount += renderList.getSectionsWithGeometryCount();
        }

        var taskLists = collector.getTaskLists();
        int initialBuildCount = taskLists.get(TaskQueueType.INITIAL_BUILD).size();
        int zeroDeferCount = taskLists.get(TaskQueueType.ZERO_FRAME_DEFER).size();
        int oneDeferCount = taskLists.get(TaskQueueType.ONE_FRAME_DEFER).size();
        int alwaysDeferCount = taskLists.get(TaskQueueType.ALWAYS_DEFER).size();

        if (renderRegionCount > 2 && this.frame % 60 != 0) {
            return;
        }

        vulkanTerrainCollectorProbeCount++;
        LOGGER.info(
            "Vulkan terrain collector probe#{} frame={} outOfGraph={} renderRegions={} renderSections={} geometrySections={} initialBuild={} zeroDefer={} oneDefer={} alwaysDefer={} cameraChunk=({}, {}, {})",
            vulkanTerrainCollectorProbeCount,
            this.frame,
            this.isOutOfGraph(viewport.getChunkCoord()),
            renderRegionCount,
            renderSectionCount,
            geometrySectionCount,
            initialBuildCount,
            zeroDeferCount,
            oneDeferCount,
            alwaysDeferCount,
            viewport.getChunkCoord().getX(),
            viewport.getChunkCoord().getY(),
            viewport.getChunkCoord().getZ()
        );
    }

    private void seedCollectorIfStarved(SectionCollector collector, Viewport viewport) {
        if (this.cameraPosition == null) {
            return;
        }

        var taskLists = collector.getTaskLists();
        int renderableSectionCount = this.countRenderableSections(collector);
        boolean hasQueuedTasks = !taskLists.get(TaskQueueType.ZERO_FRAME_DEFER).isEmpty()
                || !taskLists.get(TaskQueueType.ONE_FRAME_DEFER).isEmpty()
                || !taskLists.get(TaskQueueType.ALWAYS_DEFER).isEmpty()
                || !taskLists.get(TaskQueueType.INITIAL_BUILD).isEmpty();

        if (renderableSectionCount == 0 && !hasQueuedTasks) {
            this.seedNearbySections(collector, viewport);
            return;
        }

        this.ensureNearbyRenderCoverage(collector, viewport, renderableSectionCount);
        this.ensureNearbyInitialBuildTasks(collector, taskLists, viewport);
    }

    private int countRenderableSections(SectionCollector collector) {
        int renderableSectionCount = 0;

        for (var renderList : collector.getUnsortedRenderLists()) {
            renderableSectionCount += renderList.getSectionsWithGeometryCount();
        }

        return renderableSectionCount;
    }

    private void seedNearbySections(SectionCollector collector, Viewport viewport) {
        float distanceLimit = this.getRenderDistance();
        float distanceLimitSq = distanceLimit * distanceLimit;
        ArrayList<RenderSection> candidates = new ArrayList<>();

        for (var section : this.sectionByPosition.values()) {
            if (section.isDisposed()) {
                continue;
            }

            int pendingUpdate = section.getPendingUpdate();
            if (section.getFlags() == 0 && pendingUpdate == 0) {
                continue;
            }

            if (!this.isSectionWithinFallbackRange(section, distanceLimit, distanceLimitSq)) {
                continue;
            }

            if (!OcclusionCuller.isWithinNearbySectionFrustum(viewport, section)) {
                continue;
            }

            if (section.getLastVisibleFrame() == this.lastUpdatedFrame) {
                continue;
            }

            candidates.add(section);
        }

        if (candidates.isEmpty()) {
            return;
        }

        candidates.sort(Comparator.comparingDouble(section -> section.getSquaredDistance(
                (float) this.cameraPosition.x(),
                (float) this.cameraPosition.y(),
                (float) this.cameraPosition.z())));

        int visited = 0;

        for (var section : candidates) {
            section.setLastVisibleFrame(this.lastUpdatedFrame);
            collector.visit(section);
            visited++;

            if (visited >= FALLBACK_VISIT_LIMIT) {
                break;
            }
        }
    }

    private void ensureNearbyRenderCoverage(SectionCollector collector, Viewport viewport, int renderableSectionCount) {
        if (renderableSectionCount >= MIN_NEARBY_RENDERABLE_SECTIONS) {
            return;
        }

        float distanceLimit = this.getRenderDistance();
        float distanceLimitSq = distanceLimit * distanceLimit;
        ArrayList<RenderSection> candidates = new ArrayList<>();

        for (var section : this.sectionByPosition.values()) {
            if (section.isDisposed() || !section.isBuilt()) {
                continue;
            }

            if ((section.getFlags() & RenderSectionFlags.MASK_HAS_BLOCK_GEOMETRY) == 0) {
                continue;
            }

            if (section.getLastVisibleFrame() == this.lastUpdatedFrame) {
                continue;
            }

            if (!this.isSectionWithinFallbackRange(section, distanceLimit, distanceLimitSq)) {
                continue;
            }

            if (!OcclusionCuller.isWithinNearbySectionFrustum(viewport, section)) {
                continue;
            }

            candidates.add(section);
        }

        if (candidates.isEmpty()) {
            return;
        }

        candidates.sort(Comparator.comparingDouble(section -> section.getSquaredDistance(
                (float) this.cameraPosition.x(),
                (float) this.cameraPosition.y(),
                (float) this.cameraPosition.z())));

        for (var section : candidates) {
            section.setLastVisibleFrame(this.lastUpdatedFrame);
            collector.visit(section);
            renderableSectionCount++;

            if (renderableSectionCount >= MIN_NEARBY_RENDERABLE_SECTIONS) {
                break;
            }
        }
    }

    private void ensureNearbyInitialBuildTasks(SectionCollector collector, Map<TaskQueueType, ArrayDeque<RenderSection>> taskLists, Viewport viewport) {
        var initialBuildQueue = taskLists.get(TaskQueueType.INITIAL_BUILD);
        int targetQueueSize = Math.min(MIN_NEARBY_INITIAL_BUILD_QUEUE, TaskQueueType.INITIAL_BUILD.queueSizeLimit());

        if (initialBuildQueue.size() >= targetQueueSize) {
            return;
        }

        float distanceLimit = this.getRenderDistance();
        float distanceLimitSq = distanceLimit * distanceLimit;
        ArrayList<RenderSection> candidates = new ArrayList<>();

        for (var section : this.sectionByPosition.values()) {
            if (section.isDisposed() || section.getRunningJob() != null) {
                continue;
            }

            if (!ChunkUpdateTypes.isInitialBuild(section.getPendingUpdate())) {
                continue;
            }

            if (initialBuildQueue.contains(section)) {
                continue;
            }

            if (!this.isSectionWithinFallbackRange(section, distanceLimit, distanceLimitSq)) {
                continue;
            }

            if (!OcclusionCuller.isWithinNearbySectionFrustum(viewport, section)) {
                continue;
            }

            candidates.add(section);
        }

        if (candidates.isEmpty()) {
            return;
        }

        candidates.sort(Comparator.comparingDouble(section -> section.getSquaredDistance(
                (float) this.cameraPosition.x(),
                (float) this.cameraPosition.y(),
                (float) this.cameraPosition.z())));

        int visited = 0;
        for (var section : candidates) {
            section.setLastVisibleFrame(this.lastUpdatedFrame);
            collector.visit(section);
            visited++;

            if (initialBuildQueue.size() >= targetQueueSize || visited >= FALLBACK_VISIT_LIMIT) {
                break;
            }
        }

    }

    private boolean isSectionWithinFallbackRange(RenderSection section, float distanceLimit, float distanceLimitSq) {
        float dx = section.getCenterX() - (float) this.cameraPosition.x();
        float dy = section.getCenterY() - (float) this.cameraPosition.y();
        float dz = section.getCenterZ() - (float) this.cameraPosition.z();

        return (dx * dx) + (dz * dz) < distanceLimitSq && Math.abs(dy) < distanceLimit;
    }

    public void finalizeRenderLists(Viewport viewport) {
        if (this.sectionCollector != null) {
            // Iris: Use shadow render lists when rendering shadows
            if (net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
                this.shadowRenderLists = this.sectionCollector.createRenderLists(viewport);
            } else {
                this.renderLists = this.sectionCollector.createRenderLists(viewport);
            }
            this.lastSectionCollector = this.sectionCollector;
            this.sectionCollector = null;
        }
    }

    private boolean isOutOfGraph(SectionPos pos) {
        var sectionY = pos.getY();
        if (this.level.getMinSectionY() > sectionY || sectionY > this.level.getMaxSectionY()) {
            return false;
        }

        RenderSection section = this.sectionByPosition.get(pos.asLong());
        return section == null || !section.isBuilt();
    }

    private float getSearchDistance(FogParameters fogParameters) {
        float distance;

        // Vulkan terrain currently has backend-specific fog state differences, so avoid culling chunks from fog distance.
        boolean useFogOcclusion = net.irisshaders.iris.Iris.getCurrentPack().isPresent()
            || VulkanicAPI.isVulkanBackendSelected()
            ? false 
            : SodiumClientMod.options().performance.useFogOcclusion;

        if (useFogOcclusion) {
            distance = this.getEffectiveRenderDistance(fogParameters);
        } else {
            distance = this.getRenderDistance();
        }

        return distance;
    }

    private boolean shouldUseOcclusionCulling(Camera camera, boolean spectator) {
        final boolean useOcclusionCulling;
        BlockPos origin = camera.getBlockPosition();

        if (spectator && this.level.getBlockState(origin)
                .isSolidRender()) {
            useOcclusionCulling = false;
        } else {
            useOcclusionCulling = Minecraft.getInstance().smartCull;
        }
        return useOcclusionCulling;
    }

    public void beforeSectionUpdates() {
        this.renderableSectionTree.ensureCapacity(this.getRenderDistance());
    }

    private void resetRenderLists() {
        // Iris: Reset shadow or regular render lists based on current state
        if (net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
            this.shadowRenderLists = SortedRenderLists.empty();
        } else {
            this.renderLists = SortedRenderLists.empty();
        }

        // Iris: Use shadow or regular task lists based on current state
        var lists = net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered() ? this.shadowTaskLists : this.taskLists;
        for (var list : lists.values()) {
            list.clear();
        }
    }

    public void onSectionAdded(int x, int y, int z) {
        long key = SectionPos.asLong(x, y, z);

        if (this.sectionByPosition.containsKey(key)) {
            return;
        }

        RenderRegion region = this.regions.createForChunk(x, y, z);

        RenderSection renderSection = new RenderSection(region, x, y, z);
        region.addSection(renderSection);

        this.sectionByPosition.put(key, renderSection);

        ChunkAccess chunk = this.level.getChunk(x, z);
        LevelChunkSection section = chunk.getSections()[this.level.getSectionIndexFromSectionY(y)];

        if (section.hasOnlyAir()) {
            this.updateSectionInfo(renderSection, BuiltSectionInfo.EMPTY);
        } else {
            this.renderableSectionTree.add(renderSection);
            renderSection.setPendingUpdate(ChunkUpdateTypes.INITIAL_BUILD, this.lastFrameAtTime);
        }

        this.connectNeighborNodes(renderSection);

        // force update to schedule build task
        this.markGraphDirty();
    }

    public void onSectionRemoved(int x, int y, int z) {
        // Iris: Notify shadow render list needs update
        this.shadowNeedsRenderListUpdate = true;
        
        long sectionPos = SectionPos.asLong(x, y, z);
        RenderSection section = this.sectionByPosition.remove(sectionPos);

        if (section == null) {
            return;
        }

        this.renderableSectionTree.remove(x, y, z);

        if (section.getTranslucentData() != null) {
            this.sortTriggering.removeSection(section.getTranslucentData(), sectionPos);
        }

        RenderRegion region = section.getRegion();

        if (region != null) {
            region.removeSection(section);
        }

        this.disconnectNeighborNodes(section);
        this.updateSectionInfo(section, null);

        section.delete();

        // force update to remove section from render lists
        this.markGraphDirty();
    }

    public void renderLayer(ChunkRenderMatrices matrices, TerrainRenderPass pass, double x, double y, double z, FogParameters fogParameters) {
        RenderDevice device = RenderDevice.instance();
        CommandList commandList = device.createCommandList();

        // Iris: Use shadow or regular render lists based on current state
        var lists = net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered() 
            ? this.shadowRenderLists 
            : this.renderLists;
        this.chunkRenderer.render(matrices, commandList, lists, pass, new CameraTransform(x, y, z), fogParameters, this.sortBehavior != SortBehavior.OFF);

        commandList.flush();
    }

    public void tickVisibleRenders() {
        Iterator<ChunkRenderList> it = this.renderLists.iterator();

        while (it.hasNext()) {
            ChunkRenderList renderList = it.next();

            var region = renderList.getRegion();
            var iterator = renderList.sectionsWithSpritesIterator();

            if (iterator == null) {
                continue;
            }

            while (iterator.hasNext()) {
                var section = region.getSection(iterator.nextByteAsInt());

                if (section == null) {
                    continue;
                }

                var sprites = section.getAnimatedSprites();

                if (sprites == null) {
                    continue;
                }

                for (TextureAtlasSprite sprite : sprites) {
                    SpriteUtil.INSTANCE.markSpriteActive(sprite);
                }
            }
        }
    }

    public boolean isSectionVisible(int x, int y, int z) {
        RenderSection render = this.getRenderSection(x, y, z);

        if (render == null) {
            return false;
        }

        return render.getLastVisibleFrame() == this.lastUpdatedFrame;
    }

    public void uploadChunks() {
        // Iris: Do not upload chunks during shadow pass
        if (net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
            return;
        }
        
        var results = this.collectChunkBuildResults();

        if (results.isEmpty()) {
            return;
        }

        // only mark as needing a graph update if the uploads could have changed the graph
        // (sort results never change the graph)
        // generally there's no sort results without a camera movement, which would also trigger
        // a graph update, but it can sometimes happen because of async task execution
        this.needsGraphUpdate |= this.processChunkBuildResults(results);

        for (var result : results) {
            result.destroy();
        }
    }

    private boolean sectionVisible(RenderSection section) {
        // unloaded sections are considered visible as to not be an impossible requirement for immediate presentation
        return section == null || section.getLastVisibleFrame() == this.lastUpdatedFrame;
    }

    private boolean isSectionImmediatePresentationCandidate(RenderSection section) {
        if (this.cameraPosition == null) {
            return false;
        }
        var distanceSquared = section.getSquaredDistance(
                (float) this.cameraPosition.x(),
                (float) this.cameraPosition.y(),
                (float) this.cameraPosition.z()
        );
        
        if (distanceSquared < NEARBY_REBUILD_DISTANCE) {
            return true;
        }
        
        return distanceSquared < IMMEDIATE_PRESENT_DISTANCE &&
                // check that visible or adjacent to a visible section
                (this.sectionVisible(section)
                        || this.sectionVisible(section.adjacentDown)
                        || this.sectionVisible(section.adjacentUp)
                        || this.sectionVisible(section.adjacentNorth)
                        || this.sectionVisible(section.adjacentSouth)
                        || this.sectionVisible(section.adjacentWest)
                        || this.sectionVisible(section.adjacentEast));
    }

    private boolean processChunkBuildResults(ArrayList<BuilderTaskOutput> results) {
        var filtered = filterChunkBuildResults(results);

        var start = System.nanoTime();
        this.regions.uploadResults(RenderDevice.instance().createCommandList(), filtered);
        var uploadDuration = System.nanoTime() - start;

        boolean touchedSectionInfo = false;
        long totalUploadSize = 0;
        for (var result : filtered) {
            var resultSize = result.getResultSize();
            var job = result.render.getRunningJob();

            TranslucentData oldData = result.render.getTranslucentData();
            if (result instanceof ChunkBuildOutput chunkBuildOutput) {
                var prevFlags = result.render.getFlags();

                touchedSectionInfo |= this.updateSectionInfo(result.render, chunkBuildOutput.info);

                // if result was blocking (or is approximately visible) and section is now newly renderable, force render it since it's probably a newly uncovered chunk.
                // This also fixes flickering issues with pistons moving blocks and switching between being a mesh and a BE.
                if (job != null
                        && (job.isBlocking() || this.isSectionImmediatePresentationCandidate(result.render))
                        && RenderSectionFlags.renderingMoreTypesNow(prevFlags, chunkBuildOutput.info.flags)) {
                    // if there is currently no section collector since there was no graph traversal,
                    // reuse the previous section collector and use it to generate new extended render lists
                    if (this.sectionCollector == null) {
                        this.sectionCollector = this.lastSectionCollector;
                    }
                    this.sectionCollector.visit(result.render);
                }

                result.render.setLastMeshResultSize(resultSize);
                this.meshTaskSizeEstimator.addData(this.meshTaskSizeEstimator.resultForSection(result.render, resultSize));

                if (chunkBuildOutput.translucentData != null) {
                    this.sortTriggering.integrateTranslucentData(oldData, chunkBuildOutput.translucentData, this.cameraPosition, this::scheduleSort);

                    // a rebuild always generates new translucent data which means applyTriggerChanges isn't necessary
                    result.render.setTranslucentData(chunkBuildOutput.translucentData);
                }
            } else if (result instanceof ChunkSortOutput sortOutput
                    && sortOutput.getDynamicSorter() != null
                    && result.render.getTranslucentData() instanceof DynamicTopoData data) {
                this.sortTriggering.applyTriggerChanges(data, sortOutput.getDynamicSorter(), result.render.getPosition(), this.cameraPosition);
            }

            // clear the running job if this job is the most recent submitted job for this section
            if (job != null && result.submitTime >= result.render.getLastSubmittedFrame()) {
                result.render.setRunningJob(null);
            }

            result.render.setLastUploadFrame(result.submitTime);

            totalUploadSize += resultSize;
        }

        this.meshTaskSizeEstimator.updateModels();

        // insert and update the upload duration estimator with the total upload size,
        // since we don't know which task took how long and the time it takes to upload is not independent between tasks
        // we take the average size and duration
        if (!filtered.isEmpty()) {
            this.jobUploadDurationEstimator.addData(new UploadDuration(uploadDuration / filtered.size(), totalUploadSize / filtered.size()));
            this.jobUploadDurationEstimator.updateModels();
        }

        return touchedSectionInfo;
    }

    private boolean updateSectionInfo(RenderSection render, BuiltSectionInfo info) {
        // Iris: Notify shadow render list needs update
        this.shadowNeedsRenderListUpdate = true;
        
        if (info == null || !RenderSectionFlags.needsRender(info.flags)) {
            this.renderableSectionTree.remove(render);
        } else {
            this.renderableSectionTree.add(render);
        }

        var infoChanged = render.setInfo(info);

        if (info == null || ArrayUtils.isEmpty(info.globalBlockEntities)) {
            return this.sectionsWithGlobalEntities.remove(render) || infoChanged;
        } else {
            return this.sectionsWithGlobalEntities.add(render) || infoChanged;
        }
    }

    private static List<BuilderTaskOutput> filterChunkBuildResults(ArrayList<BuilderTaskOutput> outputs) {
        var map = new Reference2ReferenceLinkedOpenHashMap<RenderSection, BuilderTaskOutput>();

        for (var output : outputs) {
            // throw out outdated or duplicate outputs
            if (output.render.isDisposed() || output.render.getLastUploadFrame() > output.submitTime) {
                continue;
            }

            var render = output.render;
            var previous = map.get(render);

            if (previous == null || previous.submitTime < output.submitTime) {
                map.put(render, output);
            }
        }

        return new ArrayList<>(map.values());
    }

    private ArrayList<BuilderTaskOutput> collectChunkBuildResults() {
        ArrayList<BuilderTaskOutput> results = new ArrayList<>();

        ChunkJobResult<? extends BuilderTaskOutput> result;

        while ((result = this.buildResults.poll()) != null) {
            results.add(result.unwrap());
            var jobEffort = result.getJobEffort();
            if (jobEffort != null) {
                this.jobDurationEstimator.addData(jobEffort);
            }
        }

        this.jobDurationEstimator.updateModels();

        return results;
    }

    public void cleanupAndFlip() {
        this.sectionCache.cleanup();
        this.regions.update();
    }

    public void updateChunks(boolean updateImmediately) {
        // Iris: Do not update chunks during shadow pass
        if (net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
            return;
        }

        this.thisFrameBlockingTasks = 0;
        this.nextFrameBlockingTasks = 0;
        this.deferredTasks = 0;

        var thisFrameBlockingCollector = this.lastBlockingCollector;
        this.lastBlockingCollector = null;
        if (thisFrameBlockingCollector == null) {
            thisFrameBlockingCollector = new ChunkJobCollector(this.buildResults::add);
        }

        if (updateImmediately) {
            // for a perfect frame where everything is finished use the last frame's blocking collector
            // and add all tasks to it so that they're waited on
            this.submitSectionTasks(thisFrameBlockingCollector, thisFrameBlockingCollector, thisFrameBlockingCollector, UnlimitedResourceBudget.INSTANCE);

            this.thisFrameBlockingTasks = thisFrameBlockingCollector.getSubmittedTaskCount();
            thisFrameBlockingCollector.awaitCompletion(this.builder);
        } else {
            var remainingDuration = this.builder.getTotalRemainingDuration(this.averageFrameDuration);

            // an estimator is used estimate task duration and limit the execution time to the available worker capacity.
            // separately, tasks are limited by their estimated upload size and duration.
            var uploadBudget = new LimitedResourceBudget(
                    Math.max((long) (this.averageFrameDuration * FRAME_DURATION_UPLOAD_FRACTION), MIN_UPLOAD_DURATION_BUDGET),
                    this.regions.getStagingBuffer().getUploadSizeLimit(this.averageFrameDuration));

            var nextFrameBlockingCollector = new ChunkJobCollector(this.buildResults::add);
            var deferredCollector = new ChunkJobCollector(remainingDuration, this.buildResults::add);

            this.submitSectionTasks(thisFrameBlockingCollector, nextFrameBlockingCollector, deferredCollector, uploadBudget);

            this.thisFrameBlockingTasks = thisFrameBlockingCollector.getSubmittedTaskCount();
            this.nextFrameBlockingTasks = nextFrameBlockingCollector.getSubmittedTaskCount();
            this.deferredTasks = deferredCollector.getSubmittedTaskCount();

            // wait on this frame's blocking collector which contains the important tasks from this frame
            // and semi-important tasks from the last frame
            thisFrameBlockingCollector.awaitCompletion(this.builder);

            // store the semi-important collector to wait on it in the next frame
            this.lastBlockingCollector = nextFrameBlockingCollector;
        }
    }

    private void submitSectionTasks(
            ChunkJobCollector importantCollector, ChunkJobCollector semiImportantCollector, ChunkJobCollector deferredCollector, UploadResourceBudget uploadBudget) {
        submitSectionTasks(importantCollector, uploadBudget, TaskQueueType.ZERO_FRAME_DEFER);
        submitSectionTasks(semiImportantCollector, uploadBudget, TaskQueueType.ONE_FRAME_DEFER);
        submitSectionTasks(deferredCollector, uploadBudget, TaskQueueType.ALWAYS_DEFER);
        submitSectionTasks(deferredCollector, uploadBudget, TaskQueueType.INITIAL_BUILD);
    }

    private void submitSectionTasks(ChunkJobCollector collector, UploadResourceBudget uploadBudget, TaskQueueType queueType) {
        // Iris: Use shadow or regular task lists based on current state
        var lists = net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered() 
            ? this.shadowTaskLists 
            : this.taskLists;
        var taskList = lists.get(queueType);

        // submit tasks as long as there's tasks available, the collector has worker thread budget, and there's enough upload budget left 
        while (!taskList.isEmpty() && collector.hasBudgetRemaining() && (uploadBudget.isAvailable() || queueType.allowsUnlimitedUploadDuration())) {
            RenderSection section = taskList.poll();

            if (section == null) {
                break;
            }

            // don't schedule tasks for sections that don't need it anymore,
            // since the pending update it cleared when a task is started, this includes
            // sections for which there's a currently running task.
            var pendingUpdate = section.getPendingUpdate();
            if (pendingUpdate != 0) {
                submitSectionTask(collector, section, pendingUpdate, uploadBudget, queueType == TaskQueueType.ZERO_FRAME_DEFER);
            }
        }
    }

    private void submitSectionTask(ChunkJobCollector collector, @NotNull RenderSection section, int type, UploadResourceBudget uploadBudget, boolean blocking) {
        if (section.isDisposed()) {
            return;
        }

        ChunkBuilderTask<? extends BuilderTaskOutput> task;
        if (ChunkUpdateTypes.isInitialBuild(type) || ChunkUpdateTypes.isRebuild(type)) {
            task = this.createRebuildTask(section, this.frame);

            if (task == null) {
                // if the section is empty or doesn't exist submit this null-task to set the
                // built flag on the render section.
                // It's important to use a NoData instead of null translucency data here in
                // order for it to clear the old data from the translucency sorting system.
                // This doesn't apply to sorting tasks as that would result in the section being
                // marked as empty just because it was scheduled to be sorted and its dynamic
                // data has since been removed. In that case simply nothing is done as the
                // rebuild that must have happened in the meantime includes new non-dynamic
                // index data.
                TranslucentData translucentData = null;
                if (this.sortBehavior != SortBehavior.OFF) {
                    translucentData = NoData.forEmptySection(section.getPosition());
                }
                var result = ChunkJobResult.successfully(new ChunkBuildOutput(
                        section, this.frame, translucentData,
                        BuiltSectionInfo.EMPTY, Collections.emptyMap()));
                this.buildResults.add(result);

                section.setRunningJob(null);
            }
        } else { // implies it's a type of sort task
            task = this.createSortTask(section, this.frame);

            if (task == null) {
                // when a sort task is null it means the render section has no dynamic data and
                // doesn't need to be sorted. Nothing needs to be done.
                section.clearPendingUpdate();
                return;
            }
        }

        if (task != null) {
            var job = this.builder.scheduleTask(task, ChunkUpdateTypes.isImportant(type), collector::onJobFinished, blocking);
            collector.addSubmittedJob(job);

            // consume upload budget in size and duration using estimates
            uploadBudget.consume(job.getEstimatedUploadDuration(), job.getEstimatedSize());

            section.setRunningJob(job);
        }

        section.setLastSubmittedFrame(this.frame);
        section.clearPendingUpdate();
    }

    public @Nullable ChunkBuilderMeshingTask createRebuildTask(RenderSection render, int frame) {
        ChunkRenderContext context = LevelSlice.prepare(this.level, render.getPosition(), this.sectionCache);

        if (context == null) {
            return null;
        }

        var task = new ChunkBuilderMeshingTask(render, frame, this.cameraPosition, context, this.sortBehavior, ChunkUpdateTypes.isRebuildWithSort(render.getPendingUpdate()));
        task.calculateEstimations(this.jobDurationEstimator, this.meshTaskSizeEstimator, this.jobUploadDurationEstimator);
        return task;
    }

    public ChunkBuilderSortingTask createSortTask(RenderSection render, int frame) {
        var task = ChunkBuilderSortingTask.createTask(render, frame, this.cameraPosition);
        if (task != null) {
            task.calculateEstimations(this.jobDurationEstimator, this.meshTaskSizeEstimator, this.jobUploadDurationEstimator);
        }
        return task;
    }

    public void processGFNIMovement(CameraMovement movement) {
        if (this.sortTriggering != null) {
            this.sortTriggering.triggerSections(this::scheduleSort, movement);
        }
    }

    public void markGraphDirty() {
        this.needsGraphUpdate = true;
    }

    public boolean needsUpdate() {
        // Iris: Notify that shadow render list needs update when camera changes
        this.shadowNeedsRenderListUpdate = true;
        return this.needsGraphUpdate;
    }

    public ChunkBuilder getBuilder() {
        return this.builder;
    }

    public void destroy() {
        this.builder.shutdown(); // stop all the workers, and cancel any tasks

        for (var result : this.collectChunkBuildResults()) {
            result.destroy(); // delete resources for any pending tasks (including those that were cancelled)
        }

        for (var section : this.sectionByPosition.values()) {
            section.delete();
        }

        this.sectionsWithGlobalEntities.clear();
        this.resetRenderLists();

        try (CommandList commandList = RenderDevice.instance().createCommandList()) {
            this.regions.delete(commandList);
            this.chunkRenderer.delete(commandList);
        }
    }

    public int getTotalSections() {
        return this.sectionByPosition.size();
    }

    public int getVisibleChunkCount() {
        var sections = 0;
        // Iris: Use shadow or regular render lists based on current state
        var lists = net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered() 
            ? this.shadowRenderLists 
            : this.renderLists;
        var iterator = lists.iterator();

        while (iterator.hasNext()) {
            var renderList = iterator.next();
            sections += renderList.getSectionsWithGeometryCount();
        }

        return sections;
    }

    private boolean upgradePendingUpdate(RenderSection section, int updateType) {
        if (updateType == 0) {
            return false;
        }

        var current = section.getPendingUpdate();
        var joined = ChunkUpdateTypes.join(current, updateType);

        if (joined == current) {
            return false;
        }

        section.setPendingUpdate(joined, this.lastFrameAtTime);

        // mark graph as dirty so that it picks up the section's pending task
        this.markGraphDirty();

        return true;
    }

	public void scheduleSort(long sectionPos, boolean isDirectTrigger) {
	    if (this.sortBehavior == SortBehavior.OFF) {
	        return;
	    }

	    RenderSection section = this.sectionByPosition.get(sectionPos);

        if (section != null) {
            int pendingUpdate = ChunkUpdateTypes.SORT;
            var priorityMode = this.sortBehavior.getPriorityMode();
            if (priorityMode == PriorityMode.NEARBY && this.shouldPrioritizeTask(section, NEARBY_SORT_DISTANCE) || priorityMode == PriorityMode.ALL) {
                pendingUpdate = ChunkUpdateTypes.join(pendingUpdate, ChunkUpdateTypes.IMPORTANT);
            }

            if (this.upgradePendingUpdate(section, pendingUpdate)) {
                section.prepareTrigger(isDirectTrigger);
            }
        }
    }

    public void scheduleRebuild(int x, int y, int z, boolean playerChanged) {
        RenderAsserts.validateCurrentThread();

        this.sectionCache.invalidate(x, y, z);

        RenderSection section = this.sectionByPosition.get(SectionPos.asLong(x, y, z));

        if (section != null && section.isBuilt()) {
            int pendingUpdate;

            if (playerChanged && this.shouldPrioritizeTask(section, NEARBY_REBUILD_DISTANCE)) {
                pendingUpdate = ChunkUpdateTypes.join(ChunkUpdateTypes.REBUILD, ChunkUpdateTypes.IMPORTANT);
            } else {
                pendingUpdate = ChunkUpdateTypes.REBUILD;
            }

            this.upgradePendingUpdate(section, pendingUpdate);
        }
    }

    private boolean shouldPrioritizeTask(RenderSection section, float distance) {
        return this.cameraPosition != null && section.getSquaredDistance(
                (float) this.cameraPosition.x(),
                (float) this.cameraPosition.y(),
                (float) this.cameraPosition.z()
        ) < distance;
    }

    private float getEffectiveRenderDistance(FogParameters fogParameters) {
        var alpha = fogParameters.alpha();
        var distance = fogParameters.renderEnd();

        var renderDistance = this.getRenderDistance();

        // The fog must be fully opaque in order to skip rendering of chunks behind it
        if (!Mth.equal(alpha, 1.0f)) {
            return renderDistance;
        }

        return Math.min(renderDistance, distance + 0.5f);
    }

    private float getRenderDistance() {
        return this.renderDistance * 16.0f;
    }

    private void connectNeighborNodes(RenderSection render) {
        for (int direction = 0; direction < GraphDirection.COUNT; direction++) {
            RenderSection adj = this.getRenderSection(render.getChunkX() + GraphDirection.x(direction),
                    render.getChunkY() + GraphDirection.y(direction),
                    render.getChunkZ() + GraphDirection.z(direction));

            if (adj != null) {
                adj.setAdjacentNode(GraphDirection.opposite(direction), render);
                render.setAdjacentNode(direction, adj);
            }
        }
    }

    private void disconnectNeighborNodes(RenderSection render) {
        for (int direction = 0; direction < GraphDirection.COUNT; direction++) {
            RenderSection adj = render.getAdjacent(direction);

            if (adj != null) {
                adj.setAdjacentNode(GraphDirection.opposite(direction), null);
                render.setAdjacentNode(direction, null);
            }
        }
    }

    private RenderSection getRenderSection(int x, int y, int z) {
        return this.sectionByPosition.get(SectionPos.asLong(x, y, z));
    }

    public Collection<String> getDebugStrings() {
        List<String> list = new ArrayList<>();

        int count = 0;

        long geometryDeviceUsed = 0;
        long geometryDeviceAllocated = 0;
        long indexDeviceUsed = 0;
        long indexDeviceAllocated = 0;

        for (var region : this.regions.getLoadedRegions()) {
            var resources = region.getResources();

            if (resources == null) {
                continue;
            }

            var geometryArena = resources.getGeometryArena();
            geometryDeviceUsed += geometryArena.getDeviceUsedMemory();
            geometryDeviceAllocated += geometryArena.getDeviceAllocatedMemory();

            var indexArena = resources.getIndexArena();
            indexDeviceUsed += indexArena.getDeviceUsedMemory();
            indexDeviceAllocated += indexArena.getDeviceAllocatedMemory();

            count++;
        }

        list.add(String.format("Pools: Geometry %d/%d MiB, Index %d/%d MiB (%d buffers)",
                MathUtil.toMib(geometryDeviceUsed), MathUtil.toMib(geometryDeviceAllocated),
                MathUtil.toMib(indexDeviceUsed), MathUtil.toMib(indexDeviceAllocated), count));
        list.add(String.format("Transfer Queue: %s", this.regions.getStagingBuffer().toString()));

        list.add(String.format("Chunk Builder: Schd=%02d | Busy=%02d (%04d%%) | Total=%02d",
                this.builder.getScheduledJobCount(), this.builder.getBusyThreadCount(), (int) (this.builder.getBusyFraction(this.lastFrameDuration) * 100), this.builder.getTotalThreadCount())
        );

        list.add(String.format("Tasks: N0=%03d | N1=%03d | Def=%03d, Recv=%03d",
                this.thisFrameBlockingTasks, this.nextFrameBlockingTasks, this.deferredTasks, this.buildResults.size())
        );

        if (PlatformRuntimeInformation.getInstance().isDevelopmentEnvironment()) {
            var meshTaskParameters = this.jobDurationEstimator.toString(ChunkBuilderMeshingTask.class);
            var sortTaskParameters = this.jobDurationEstimator.toString(ChunkBuilderSortingTask.class);
            var uploadDurationParameters = this.jobUploadDurationEstimator.toString(null);
            list.add(String.format("Duration: Mesh %s, Sort %s, Upload %s", meshTaskParameters, sortTaskParameters, uploadDurationParameters));

            var sizeEstimates = new ReferenceArrayList<String>();
            for (var type : MeshResultSize.SectionCategory.values()) {
                sizeEstimates.add(String.format("%s=%s", type, this.meshTaskSizeEstimator.toString(type)));
            }
            list.add(String.format("Size: %s", String.join(", ", sizeEstimates)));
        }

        if (this.sortBehavior != SortBehavior.OFF) {
            this.sortTriggering.addDebugStrings(list, this.sortBehavior);
        } else {
            list.add("TS OFF");
        }

        return list;
    }

    public @NotNull SortedRenderLists getRenderLists() {
        // Iris: Return shadow or regular render lists based on current state
        return net.irisshaders.iris.shadows.ShadowRenderingState.areShadowsCurrentlyBeingRendered() 
            ? this.shadowRenderLists 
            : this.renderLists;
    }

    public boolean isSectionBuilt(int x, int y, int z) {
        var section = this.getRenderSection(x, y, z);
        return section != null && section.isBuilt();
    }

    public void onChunkAdded(int x, int z) {
        int totalBefore = this.sectionByPosition.size();
        int nonAirSections = 0;

        for (int y = this.level.getMinSectionY(); y <= this.level.getMaxSectionY(); y++) {
            ChunkAccess chunk = this.level.getChunk(x, z);
            LevelChunkSection section = chunk.getSections()[this.level.getSectionIndexFromSectionY(y)];
            if (!section.hasOnlyAir()) {
                nonAirSections++;
            }
            this.onSectionAdded(x, y, z);
        }

    }

    public void onChunkRemoved(int x, int z) {
        for (int y = this.level.getMinSectionY(); y <= this.level.getMaxSectionY(); y++) {
            this.onSectionRemoved(x, y, z);
        }
    }

    public Collection<RenderSection> getSectionsWithGlobalEntities() {
        return ReferenceSets.unmodifiable(this.sectionsWithGlobalEntities);
    }
}
