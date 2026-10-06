package net.minecraft.client.renderer;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import net.blaze3d.buffers.GpuBufferSlice;
import net.blaze3d.framegraph.FramePass;
import net.blaze3d.pipeline.RenderTarget;
import net.blaze3d.platform.Lighting;
import net.blaze3d.resource.GraphicsResourceAllocator;
import net.blaze3d.resource.ResourceHandle;
import net.blaze3d.systems.RenderPass;
import net.blaze3d.systems.RenderSystem;
import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.VertexConsumer;
import net.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.SortedSet;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.PrioritizeChunkUpdates;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.blockentity.state.BedRenderState;
import net.minecraft.client.renderer.blockentity.state.BannerRenderState;
import net.minecraft.client.renderer.blockentity.state.BeaconRenderState;
import net.minecraft.client.renderer.blockentity.state.BrushableBlockRenderState;
import net.minecraft.client.renderer.blockentity.state.CampfireRenderState;
import net.minecraft.client.renderer.blockentity.state.CondiutRenderState;
import net.minecraft.client.renderer.blockentity.state.CopperGolemStatueRenderState;
import net.minecraft.client.renderer.blockentity.state.BellRenderState;
import net.minecraft.client.renderer.blockentity.state.EndPortalRenderState;
import net.minecraft.client.renderer.blockentity.state.SpawnerRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityWithBoundingBoxRenderState;
import net.minecraft.client.renderer.blockentity.state.TestInstanceRenderState;
import net.minecraft.client.renderer.blockentity.state.ShelfRenderState;
import net.minecraft.client.renderer.blockentity.state.VaultRenderState;
import net.minecraft.client.renderer.blockentity.state.PistonHeadRenderState;
import net.minecraft.client.renderer.blockentity.state.ShulkerBoxRenderState;
import net.minecraft.client.renderer.blockentity.state.SkullBlockRenderState;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.TranslucencyPointOfView;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.client.renderer.debug.GameTestBlockHighlightRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.ExperienceOrbRenderState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.BlockBreakingRenderState;
import net.minecraft.client.renderer.state.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.client.renderer.state.ParticlesRenderState;
import net.minecraft.client.renderer.state.SkyRenderState;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.BlockDestructionProgress;
import net.vulkanic.VulkanicAPI;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.ARGB;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.sodium.client.render.viewport.ViewportProvider;
import net.sodium.client.util.FogParameters;
import net.sodium.client.util.FlawlessFrames;
import net.sodium.fabric.SodiumFogRenderHook;
import net.vulkanic.world.RustGalWholeFrameTerrainSource;
import net.voxelmap.VoxelConstants;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class LevelRenderer implements ResourceManagerReloadListener, AutoCloseable {
	private final java.util.List<net.minecraft.client.renderer.entity.state.EntityRenderState> rustShadowOnlyEntityStates = new java.util.ArrayList<>();
	private final java.util.List<Entity> rustShadowCandidateEntities = new java.util.ArrayList<>();
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final ResourceLocation TRANSPARENCY_POST_CHAIN_ID = ResourceLocation.withDefaultNamespace("transparency");
	private static final ResourceLocation ENTITY_OUTLINE_POST_CHAIN_ID = ResourceLocation.withDefaultNamespace("entity_outline");
	public static final int SECTION_SIZE = 16;
	public static final int HALF_SECTION_SIZE = 8;
	public static final int NEARBY_SECTION_DISTANCE_IN_BLOCKS = 32;
	private static final int MINIMUM_TRANSPARENT_SORT_COUNT = 15;
	private static final boolean BLOCK_OUTLINE_DIAGNOSTICS = Boolean.getBoolean("mattmc.dev.blockOutlineDiagnostics");
	private static final String BLOCK_OUTLINE_DIAGNOSTIC_SCENARIO = System.getProperty("mattmc.dev.rustGalWorldOutline.scenario", "").trim();
	private static final String BLOCK_OUTLINE_DIAGNOSTIC_STYLE = System.getProperty("mattmc.dev.rustGalWorldOutline.style", "").trim();
	private static int blockOutlineDiagnosticLogs;
	private static final boolean BLOCK_OUTLINE_FRAMEBUFFER_DIAGNOSTICS = Boolean.getBoolean("mattmc.dev.blockOutlineFramebufferDiagnostics");
	private static final int BLOCK_OUTLINE_FRAMEBUFFER_DIAGNOSTIC_LIMIT = Math.max(
		0,
		Integer.getInteger("mattmc.dev.blockOutlineFramebufferDiagnostics.maxFrames", 24)
	);
	private static int blockOutlineFramebufferDiagnosticLogs;
	private static int blockCrackFramebufferDiagnosticLogs;
	private static final boolean[] blockCrackFramebufferDiagnosticStages = new boolean[10];
	private final Minecraft minecraft;
	public final EntityRenderDispatcher entityRenderDispatcher; // Made public for Iris shadow rendering
	private final BlockEntityRenderDispatcher blockEntityRenderDispatcher;
	public RenderBuffers renderBuffers; // Made public for Iris shadow rendering (already non-final)
	private final SkyRenderer skyRenderer = new SkyRenderer();
	private final CloudRenderer cloudRenderer = new CloudRenderer();
	private final WorldBorderRenderer worldBorderRenderer = new WorldBorderRenderer();
	private final WeatherEffectRenderer weatherEffectRenderer = new WeatherEffectRenderer();
	private final ParticlesRenderState particlesRenderState = new ParticlesRenderState();
	public final DebugRenderer debugRenderer = new DebugRenderer();
	public final GameTestBlockHighlightRenderer gameTestBlockHighlightRenderer = new GameTestBlockHighlightRenderer();
	@Nullable
	public ClientLevel level; // Already public
	private final SectionOcclusionGraph sectionOcclusionGraph = new SectionOcclusionGraph();
	private final ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections = new ObjectArrayList<>(10000);
	private final LongOpenHashSet rustExtractedBlockEntityPositions = new LongOpenHashSet(512);
	private final ObjectArrayList<SectionRenderDispatcher.RenderSection> nearbyVisibleSections = new ObjectArrayList<>(50);
	@Nullable
	private ViewArea viewArea;
	private int ticks;
	private final Int2ObjectMap<BlockDestructionProgress> destroyingBlocks = new Int2ObjectOpenHashMap<>();
	public final Long2ObjectMap<SortedSet<BlockDestructionProgress>> destructionProgress = new Long2ObjectOpenHashMap<>(); // Made public for Iris shadow rendering
	@Nullable
	private RenderTarget entityOutlineTarget;
	private final LevelTargetBundle targets = new LevelTargetBundle();
	private int lastCameraSectionX = Integer.MIN_VALUE;
	private int lastCameraSectionY = Integer.MIN_VALUE;
	private int lastCameraSectionZ = Integer.MIN_VALUE;
	private double prevCamX = Double.MIN_VALUE;
	private double prevCamY = Double.MIN_VALUE;
	private double prevCamZ = Double.MIN_VALUE;
	private double prevCamRotX = Double.MIN_VALUE;
	private double prevCamRotY = Double.MIN_VALUE;
	@Nullable
	private SectionRenderDispatcher sectionRenderDispatcher;
	private int lastViewDistance = -1;
	private boolean captureFrustum;
	@Nullable
	private Frustum capturedFrustum;
	@Nullable
	private BlockPos lastTranslucentSortBlockPos;
	private int translucencyResortIterationIndex;
	private final LevelRenderState levelRenderState;
	private final SubmitNodeStorage submitNodeStorage;
	// Isolated real entity callback output for the Rust-owned name-tag stream.
	// It never reaches Java feature rendering.
	private final SubmitNodeStorage rustWorldTextSubmitNodeStorage = new SubmitNodeStorage();
	private final FeatureRenderDispatcher featureRenderDispatcher;
	
	private boolean disableFrustumCulling;
	private boolean warned;
	@Nullable
	private BlockOutlineFramebufferProbe pendingBlockOutlineFramebufferProbe;
	@Nullable
	private BlockCrackFramebufferProbe pendingBlockCrackFramebufferProbe;
	private final Matrix4f blockOutlineProbeProjection = new Matrix4f();
	
	// Sodium: From LevelRendererMixin - fields for Sodium world renderer integration
	private final RustGalWholeFrameTerrainSource rustGalWholeFrameTerrainSource = new RustGalWholeFrameTerrainSource();

	public LevelRenderer(
		Minecraft minecraft,
		EntityRenderDispatcher entityRenderDispatcher,
		BlockEntityRenderDispatcher blockEntityRenderDispatcher,
		RenderBuffers renderBuffers,
		LevelRenderState levelRenderState,
		FeatureRenderDispatcher featureRenderDispatcher
	) {
		this.minecraft = minecraft;
		this.entityRenderDispatcher = entityRenderDispatcher;
		this.blockEntityRenderDispatcher = blockEntityRenderDispatcher;
		this.renderBuffers = renderBuffers;
		this.submitNodeStorage = featureRenderDispatcher.getSubmitNodeStorage();
		this.levelRenderState = levelRenderState;
		this.featureRenderDispatcher = featureRenderDispatcher;
	}

	public void close() {
		if (this.entityOutlineTarget != null) {
			this.entityOutlineTarget.destroyBuffers();
		}

		this.skyRenderer.close();
		this.cloudRenderer.close();
	}

	/** Retires the pre-selection Java outline target before Rust owns the frame. */
	public void ensureRustSemanticRoute() {
		if (this.entityOutlineTarget != null) {
			this.entityOutlineTarget.destroyBuffers();
			this.entityOutlineTarget = null;
		}
	}
	
	// Iris: Helper method to disable fabulous graphics when shaders are enabled
	private void disableFabulousGraphicsIfNeeded() {
		// Rust owns shader-pack admission for whole-frame Vulkan.  Do not query
		// Iris's Java lifecycle while resource reload is constructing semantic
		// world state. Selection is fenced too, before the whole-frame shell has
		// necessarily been activated.
		return;

	}

	public void onResourceManagerReload(ResourceManager resourceManager) {
		// Iris: Disable fabulous graphics when shaders are enabled
		disableFabulousGraphicsIfNeeded();
		
		this.initOutline();
		this.skyRenderer.initTextures();
	}

	public void initOutline() {
		if (this.entityOutlineTarget != null) {
			this.entityOutlineTarget.destroyBuffers();
		}
		// Rust owns the outline mask, intermediate targets, and post effect for
		// whole-frame Vulkan. Do not allocate a Java target or expose it through
		// the frame graph after Vulkan selection, even before the shell activates.
		this.entityOutlineTarget = null;
		return;

	}

	public void doEntityOutline() {
	}

	protected boolean shouldShowEntityOutlines() {
		return !this.minecraft.gameRenderer.isPanoramicMode()
			&& this.minecraft.player != null;
	}

	public void setLevel(@Nullable ClientLevel clientLevel) {
		this.lastCameraSectionX = Integer.MIN_VALUE;
		this.lastCameraSectionY = Integer.MIN_VALUE;
		this.lastCameraSectionZ = Integer.MIN_VALUE;
		this.level = clientLevel;
		if (clientLevel != null) {
		} else {
			this.entityRenderDispatcher.resetCamera();
			if (this.viewArea != null) {
				this.viewArea.releaseAllBuffers();
				this.viewArea = null;
			}

			if (this.sectionRenderDispatcher != null) {
				this.sectionRenderDispatcher.dispose();
			}

			this.sectionRenderDispatcher = null;
			this.sectionOcclusionGraph.waitAndReset(null);
			this.clearVisibleSections();
		}

		this.gameTestBlockHighlightRenderer.clear();
		
		// Sodium: Update renderer when world changes
		this.rustGalWholeFrameTerrainSource.setLevel(clientLevel);
	}

	private void clearVisibleSections() {
		this.visibleSections.clear();
		this.nearbyVisibleSections.clear();
	}

	public void allChanged() {
		// Rust owns semantic world invalidation and terrain lifetime for this
		// route.  Do not recreate Java section dispatchers or ViewArea buffers.
		return;

	}

	public void resize(int i, int j) {
		this.needsUpdate();
		if (this.entityOutlineTarget != null) {
			this.entityOutlineTarget.resize(i, j);
		}
	}

	@Nullable
	public String getSectionStatistics() {
		return "Rust terrain: " + this.rustGalWholeFrameTerrainSource.sectionCount() + " sections, "
			+ RustGalWholeFrameTerrainSource.wholeFrameTerrainQueueSummary();
	}

	@Nullable
	public SectionRenderDispatcher getSectionRenderDispatcher() {
		return this.sectionRenderDispatcher;
	}

	public double getTotalSections() {
		return this.viewArea == null ? 0.0 : this.viewArea.sections.length;
	}

	public double getLastViewDistance() {
		return this.lastViewDistance;
	}

	public int countRenderedSections() {
		return 0;
	}

	@Nullable
	public String getEntityStatistics() {
		return this.level == null
			? null
			: "E: " + this.levelRenderState.entityRenderStates.size() + "/" + this.level.getEntityCount() + ", SD: " + this.level.getServerSimulationDistance();
	}

	public void cullTerrain(Camera camera, Frustum frustum, boolean spectator) { // Made public for Iris shadow rendering
	// Vanilla's visibility graph is CPU semantic state.  Sodium's terrain
	// setup scope initializes a Java GL render device, which cannot coexist
	// with the Rust-owned presenter.  Rust terrain admission is separately
	// verified by the whole-frame execution correlation gate.
	this.applyFrustum(frustum);
	return;
	}

	public static Frustum offsetFrustum(Frustum frustum) {
		return new Frustum(frustum).offsetToFullyIncludeCameraCube(8);
	}

	private void applyFrustum(Frustum frustum) {
		if (!Minecraft.getInstance().isSameThread()) {
			throw new IllegalStateException("applyFrustum called from wrong thread: " + Thread.currentThread().getName());
		} else {
			this.clearVisibleSections();
			this.sectionOcclusionGraph.addSectionsInFrustum(frustum, this.visibleSections, this.nearbyVisibleSections);
		}
	}

	public void addRecentlyCompiledSection(SectionRenderDispatcher.RenderSection renderSection) {
		this.sectionOcclusionGraph.schedulePropagationFrom(renderSection);
	}

	/**
	 * Advances the client-owned light snapshot before the Rust whole-frame source
	 * clones terrain sections. This is simulation/state progression only: it
	 * creates no Java render resources, render lists, or GPU commands.
	 */
	public void advanceRustWholeFrameLightState() {
		if (this.level == null) {
			return;
		}
		// Vanilla intentionally spreads this queue across ordinary render frames.
		// The Rust source instead snapshots a complete visible terrain volume, so
		// let an already-queued burst reach one coherent CPU light state before it
		// starts those immutable builds.  The cap preserves a bounded render-thread
		// cost if networking continues to enqueue work concurrently.
		final int maxDrainPasses = 64;
		int drainPasses = 0;
		boolean pendingQueueBefore = this.level.hasPendingLightUpdates();
		while (drainPasses < maxDrainPasses && this.level.hasPendingLightUpdates()) {
			this.level.pollLightUpdates();
			drainPasses++;
		}
		boolean pendingQueueAfter = this.level.hasPendingLightUpdates();
		var lightEngine = this.level.getChunkSource().getLightEngine();
		boolean lightEngineWorkBefore = lightEngine.hasLightWork();
		if (lightEngineWorkBefore) {
			lightEngine.runLightUpdates();
		}
		net.minecraft.client.dev.GraphicsFrameBenchmark.recordCounterSample(
			"rust-gal.light-state.queue-pending-before", pendingQueueBefore ? 1L : 0L
		);
		net.minecraft.client.dev.GraphicsFrameBenchmark.recordCounterSample(
			"rust-gal.light-state.queue-drain-passes", drainPasses
		);
		net.minecraft.client.dev.GraphicsFrameBenchmark.recordCounterSample(
			"rust-gal.light-state.queue-pending-after", pendingQueueAfter ? 1L : 0L
		);
		net.minecraft.client.dev.GraphicsFrameBenchmark.recordCounterSample(
			"rust-gal.light-state.engine-work-before", lightEngineWorkBefore ? 1L : 0L
		);
		net.minecraft.client.dev.GraphicsFrameBenchmark.recordCounterSample(
			"rust-gal.light-state.engine-work-after", lightEngine.hasLightWork() ? 1L : 0L
		);
	}

	private Frustum prepareCullFrustum(Matrix4f matrix4f, Matrix4f matrix4f2, Vec3 vec3) {
		
		Frustum frustum;
		if (this.capturedFrustum != null && !this.captureFrustum) {
			frustum = this.capturedFrustum;
		} else {
			frustum = new Frustum(matrix4f, matrix4f2);
			frustum.prepare(vec3.x(), vec3.y(), vec3.z());
		}

		if (this.captureFrustum) {
			this.capturedFrustum = frustum;
			this.captureFrustum = false;
		}

		return frustum;
	}

	private void extractVisibleEntities(Camera camera, Frustum frustum, DeltaTracker deltaTracker, LevelRenderState levelRenderState) {
		Vec3 vec3 = camera.getPosition();
		double d = vec3.x();
		double e = vec3.y();
		double f = vec3.z();
		TickRateManager tickRateManager = this.minecraft.level.tickRateManager();
		boolean bl = this.shouldShowEntityOutlines();
		Entity.setViewScale(Mth.clamp(this.minecraft.options.getEffectiveRenderDistance() / 8.0, 1.0, 2.5) * this.minecraft.options.entityDistanceScaling().get());

		// Iris skip-all-rendering is compatibility-only. Rust Vulkan needs the
		// complete semantic entity set for explicit admission and must not query
		// Iris pipeline state while extracting the frame.
		Iterable<Entity> entities;
		entities = this.level.entitiesForRendering();

		for (Entity entity : entities) {
			boolean shouldRenderEntity = this.entityRenderDispatcher.shouldRender(entity, frustum, d, e, f) || entity.hasIndirectPassenger(this.minecraft.player);
			boolean probeFallingBlock = entity instanceof net.minecraft.world.entity.item.FallingBlockEntity
				&& net.minecraft.client.dev.DeterministicCameraCapture.isFallingBlockSequenceActive();
			boolean compiledSection = false;
			boolean extractedForProbe = false;
			if (shouldRenderEntity) {
				BlockPos blockPos = entity.blockPosition();
				compiledSection = this.level.isOutsideBuildHeight(blockPos.getY()) || this.isSectionCompiled(blockPos);
				// Java's entity extraction historically waited for the nearby terrain
				// section to become compiled because the same pass immediately emitted
				// Java terrain and entity draws. Rust whole-frame ownership separates
				// those semantic streams; dropping an otherwise visible entity while a
				// section is still compiling would make the Rust frame incomplete and
				// cannot be repaired by a later backend pass. Let the real entity
				// producer reach its semantic callsite, where unsupported model state
				// remains an explicit admission failure rather than a hidden omission.
				if ((entity != camera.getEntity() || camera.isDetached() || camera.getEntity() instanceof LivingEntity && ((LivingEntity)camera.getEntity()).isSleeping())
					&& (!(entity instanceof LocalPlayer) || camera.getEntity() == entity)) {
					if (entity.tickCount == 0) {
						entity.xOld = entity.getX();
						entity.yOld = entity.getY();
						entity.zOld = entity.getZ();
					}

					float g = deltaTracker.getGameTimeDeltaPartialTick(!tickRateManager.isEntityFrozen(entity));
					EntityRenderState entityRenderState = this.extractEntity(entity, g);
					levelRenderState.entityRenderStates.add(entityRenderState);
					extractedForProbe = true;
					if (entityRenderState.appearsGlowing() && bl) {
						levelRenderState.haveGlowingEntities = true;
					}
				}
			}
			if (probeFallingBlock) {
				net.minecraft.client.dev.DeterministicCameraCapture.recordFallingBlockExtractionProbe(shouldRenderEntity, compiledSection, extractedForProbe);
			}
		}
	}

	/**
	 * Copies bounded off-camera candidates. Rust applies the selected pack's
	 * culling; asking it first (from copied culling facts) skips extracting
	 * entities the shadow pass rejects.
	 */
	private void extractRustShadowCandidates(Camera camera) {
		this.rustShadowOnlyEntityStates.clear();
		this.rustShadowCandidateEntities.clear();
		if (!net.vulkanic.gui.RustGalFrameCoordinator.isRustShaderExecutionActive()) return;
		var cameraEntities = new it.unimi.dsi.fastutil.ints.IntOpenHashSet();
		for (EntityRenderState state : this.levelRenderState.entityRenderStates) cameraEntities.add(state.entityId);
		var tickRates = this.level.tickRateManager();
		var dispatcherCamera = this.entityRenderDispatcher.camera;
		var query = dispatcherCamera == null ? null : net.vulkanic.gui.RustGalFrameCoordinator.entityShadowQuery();
		if (query != null) query.clear();
		for (Entity entity : this.level.entitiesForRendering()) {
			if (cameraEntities.contains(entity.getId())) continue;
			if (entity.tickCount == 0) {
				entity.xOld=entity.getX(); entity.yOld=entity.getY(); entity.zOld=entity.getZ();
			}
			// Only ELIGIBLE states are kept below; skip extracting the rest.
			var renderer = this.entityRenderDispatcher.getRenderer(entity);
			if (dispatcherCamera != null && !renderer.rustShadowCullingEligible(entity, dispatcherCamera.getPosition()))
				continue;
			this.rustShadowCandidateEntities.add(entity);
			if (query != null) query.add(renderer.copyEntityCulling(entity, dispatcherCamera.getPosition()));
		}
		Vec3 submitCamera = this.levelRenderState.cameraRenderState.pos;
		boolean decided = query != null && submitCamera != null
			&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.selectEntityShadowCandidates(
				query, submitCamera.x, submitCamera.y, submitCamera.z);
		for (int index = 0; index < this.rustShadowCandidateEntities.size(); index++) {
			if (decided && !query.admitted(index)) continue;
			Entity entity = this.rustShadowCandidateEntities.get(index);
			float delta = this.minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(!tickRates.isEntityFrozen(entity));
			EntityRenderState state = this.entityRenderDispatcher.extractEntity(entity,delta);
			var inputs = state.rustEntityCulling;
			if (inputs != null && (inputs.flags() & net.vulkanic.bridge.VulkanicGalBridge.WorldEntityCullingRecord.ELIGIBLE) != 0)
				addRustShadowOnlyEntity(state);
		}
		this.rustShadowCandidateEntities.clear();
		// Frozen's player-only branch extracts these states with the ordinary
		// partial tick and skips the entity frustum/compiled-section predicates.
		// Carry a separate CPU role; Java never reads shadowEntities/shadowPlayer.
		LocalPlayer player = this.minecraft.player;
		if (player != null) {
			float delta = this.minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
			addRustPlayerOnlyVariant(player,delta);
			if (player.getVehicle() != null) addRustPlayerOnlyVariant(player.getVehicle(),delta);
		}
	}

	private void addRustPlayerOnlyVariant(Entity entity,float delta) {
		EntityRenderState state = this.entityRenderDispatcher.extractEntity(entity,delta);
		if (state.rustEntityCulling == null) throw new IllegalStateException("player shadow candidate lacks copied gameplay bounds");
		state.rustEntityCulling = state.rustEntityCulling.asPlayerOnlyVariant();
		addRustShadowOnlyEntity(state);
	}

	private void addRustShadowOnlyEntity(EntityRenderState state) {
		if (this.rustShadowOnlyEntityStates.size() >= 4096)
			throw new IllegalStateException("shadow-only entity candidates exceeded bounded capacity 4096");
		this.rustShadowOnlyEntityStates.add(stripRustShadowOnlySideStreams(state));
	}

	private static EntityRenderState stripRustShadowOnlySideStreams(EntityRenderState state) {
		// These camera overlays have no shadow role in the current transport.
		// Other unported shadow geometry is recorded explicitly during capture.
		state.outlineColor = 0;
		state.displayFireAnimation = false;
		state.nameTag = null;
		state.leashStates = null;
		state.hitboxesRenderState = null;
		state.serverHitboxesRenderState = null;
		state.shadowPieces.clear();
		return state;
	}

	private void submitRustShadowOnlyEntities(PoseStack poseStack, LevelRenderState levelRenderState,
			SubmitNodeCollector submitNodeCollector) {
		if (this.rustShadowOnlyEntityStates.isEmpty()) {
			return;
		}
		Vec3 vec3 = levelRenderState.cameraRenderState.pos;
		net.vulkanic.world.RustGalWorldPrimitiveRenderer.beginShadowOnlyEntityCapture();
		try {
			for (EntityRenderState entityRenderState : this.rustShadowOnlyEntityStates) {
				this.entityRenderDispatcher.submitSemantic(entityRenderState, levelRenderState.cameraRenderState,
					entityRenderState.x - vec3.x(), entityRenderState.y - vec3.y(), entityRenderState.z - vec3.z(),
					poseStack, this.submitNodeStorage);
			}
		} finally {
			net.vulkanic.world.RustGalWorldPrimitiveRenderer.endShadowOnlyEntityCapture();
		}
	}

	/** Collects debug subscriptions that append to the world-text semantic stream. */
	public void collectRustNeighborUpdateSemantics(Camera camera, SubmitNodeStorage geometry) {
		this.debugRenderer.collectRustNeighborUpdateSemantics(camera, geometry, this.rustWorldTextSubmitNodeStorage);
	}

	/** Collects game-event debug geometry and labels into semantic streams. */
	public void collectRustGameEventListenerSemantics(Camera camera, SubmitNodeStorage geometry) {
		this.debugRenderer.collectRustGameEventListenerSemantics(camera, geometry, this.rustWorldTextSubmitNodeStorage);
	}

	/** Collects pathfinding debug geometry and labels into semantic streams. */
	public void collectRustPathfindingSemantics(Camera camera, SubmitNodeStorage geometry) {
		this.debugRenderer.collectRustPathfindingSemantics(camera, geometry, this.rustWorldTextSubmitNodeStorage);
	}

	/** Collects water debug geometry and amount labels into semantic streams. */
	public void collectRustWaterSemantics(Camera camera, SubmitNodeStorage geometry) {
		this.debugRenderer.collectRustWaterSemantics(camera, geometry, this.rustWorldTextSubmitNodeStorage);
	}

	/** Collects light debug labels into the semantic world-text stream. */
	public void collectRustLightSemantics(Camera camera) {
		this.debugRenderer.collectRustLightSemantics(camera, this.rustWorldTextSubmitNodeStorage);
	}

	/** Collects periodic chunk diagnostics into the semantic world-text stream. */
	public void collectRustChunkSemantics(Camera camera) {
		this.debugRenderer.collectRustChunkSemantics(camera, this.rustWorldTextSubmitNodeStorage);
	}

	/** Collects goal-selector debug labels into the semantic world-text stream. */
	public void collectRustGoalSelectorSemantics(Camera camera) {
		this.debugRenderer.collectRustGoalSelectorSemantics(camera, this.rustWorldTextSubmitNodeStorage);
	}

	/** Collects raid centers and labels into semantic world streams. */
	public void collectRustRaidSemantics(Camera camera, SubmitNodeStorage geometry) {
		this.debugRenderer.collectRustRaidSemantics(camera, geometry, this.rustWorldTextSubmitNodeStorage);
	}

	/** Collects POI and ghost-POI diagnostics into semantic world streams. */
	public void collectRustPoiSemantics(Camera camera, SubmitNodeStorage geometry) {
		this.debugRenderer.collectRustPoiSemantics(camera, geometry, this.rustWorldTextSubmitNodeStorage);
	}

	/** Collects brain-debug labels into the semantic world-text stream. */
	public void collectRustBrainSemantics(Camera camera) {
		this.debugRenderer.collectRustBrainSemantics(camera, this.rustWorldTextSubmitNodeStorage);
	}

	/** Collects bee diagnostics into semantic world streams. */
	public void collectRustBeeSemantics(Camera camera, SubmitNodeStorage geometry) {
		this.debugRenderer.collectRustBeeSemantics(camera, geometry, this.rustWorldTextSubmitNodeStorage);
	}

	/** Collects octree diagnostics using the frame's explicit frustum. */
	public void collectRustOctreeSemantics(Camera camera, SubmitNodeStorage geometry, net.minecraft.client.renderer.culling.Frustum frustum) {
		this.debugRenderer.collectRustOctreeSemantics(camera, geometry, this.rustWorldTextSubmitNodeStorage, frustum);
	}

	public void enqueueRustGalIndexedMeshFeaturesForWholeFrame(Camera camera, DeltaTracker deltaTracker, Matrix4f viewMatrix, Matrix4f projectionMatrix) {
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.indexed-mesh.route-policy");
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.indexed-mesh.route-policy");
		if (this.level == null) {
			return;
		}

		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.indexed-mesh.dispatcher-prepare");
		this.entityRenderDispatcher.prepare(camera, this.minecraft.crosshairPickEntity);
		this.blockEntityRenderDispatcher.prepare(camera);
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.indexed-mesh.dispatcher-prepare");
		Vec3 cameraPos = camera.getPosition();
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.indexed-mesh.frustum-prepare");
		// Frustum composes its second matrix with its first, so the vanilla
		// render-level contract is model-view followed by projection. Keep the
		// semantic whole-frame collector on that same order; reversing it creates
		// a degenerate CPU visibility set even though the GPU matrices are valid.
		Frustum frustum = this.prepareCullFrustum(viewMatrix, projectionMatrix, cameraPos);
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.indexed-mesh.frustum-prepare");
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.indexed-mesh.real-state-extraction");
		this.levelRenderState.reset();
		if (!net.minecraft.client.dev.DeterministicCameraCapture.suppressWorldEntitiesForCapture()) {
			this.extractVisibleEntities(camera, frustum, deltaTracker, this.levelRenderState);
			this.extractVisibleBlockEntities(camera, deltaTracker.getGameTimeDeltaPartialTick(false), this.levelRenderState);
		}
		// This frame's camera search applied to the camera-pass entities only.
		net.vulkanic.world.RustGalWholeFrameTerrainSource.closeEntityCulling();
		// Independent of the camera pass (and of capture entity suppression),
		// exactly like Iris's ShadowRenderer player extraction.
		this.extractRustShadowCandidates(camera);
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.indexed-mesh.real-state-extraction");
		PoseStack poseStack = new PoseStack();
		boolean selectedSourceCoverage = net.vulkanic.world.RustGalWorldPrimitiveRenderer.requiresSelectedSourceFeatureCoverage();
		WorldFeatureCoverageCollector unsupportedFeatureCoverage = selectedSourceCoverage
			? new WorldFeatureCoverageCollector()
			: null;

		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.indexed-mesh.piston-extract");
		this.ensurePistonMovingBlocksPresentForWholeFrame(camera, deltaTracker.getGameTimeDeltaPartialTick(false));
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.indexed-mesh.piston-extract");

		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.indexed-mesh.entity-traversal");
		this.submitSelectedWholeFrameMeshEntities(
			poseStack, true, true, true, true, true, true, true,
			true, true, true, true
		);
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.indexed-mesh.entity-traversal");
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.indexed-mesh.world-text-submit");
			this.submitWholeFrameWorldText(poseStack, this.levelRenderState, true);
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.indexed-mesh.world-text-submit");
		this.featureRenderDispatcher.collectRustHitboxSemantics(this.submitNodeStorage);
		// Game-test markers are debug geometry outside the entity submit lists;
		// copy them before feature dispatch so Rust owns both their box and label.
		this.gameTestBlockHighlightRenderer.collectRustSemantics(
			poseStack, this.submitNodeStorage, this.rustWorldTextSubmitNodeStorage
		);
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.indexed-mesh.model-block-entity-traversal");
		this.submitSelectedWholeFrameModelBlockEntities(poseStack, this.levelRenderState);
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.indexed-mesh.model-block-entity-traversal");

		// The normal extraction above has already built the real render states.
		// Re-submit only unported families into a count-only collector so the
		// selected Rust route can reject incomplete ownership without retaining
		// Java renderer objects or executing a Java feature draw.
		if (selectedSourceCoverage) {
			net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.indexed-mesh.feature-coverage");
			this.collectUnsupportedWholeFrameFeatures(
				poseStack,
				unsupportedFeatureCoverage,
				true,
				true,
				true,
				true,
				true,
				true,
				true,
				true
			);
			net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.indexed-mesh.feature-coverage");
		}

		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.indexed-mesh.piston-submit");
		this.submitPistonMovingBlocksForWholeFrame(poseStack, this.levelRenderState, this.submitNodeStorage);
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.indexed-mesh.piston-submit");

		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.indexed-mesh.world-text-extract");
		this.featureRenderDispatcher.collectRustWorldTextSemanticsForWholeFrame(this.rustWorldTextSubmitNodeStorage);
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.indexed-mesh.world-text-extract");

		// This happens after real Java submit extraction but before the
		// block-only dispatcher clears the queue. Rust receives counts only and
		// rejects selected-source ownership when another feature family exists.
		net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueWorldFeatureCoverage(
			selectedSourceCoverage
				? this.submitNodeStorage.worldFeatureCoverageSnapshot().plus(unsupportedFeatureCoverage.snapshot())
				: this.submitNodeStorage.worldFeatureCoverageSnapshot()
		);
		if (selectedSourceCoverage) {
			net.vulkanic.world.RustGalWorldPrimitiveRenderer.recordSelectedSourceCoverageDiagnostics(
				unsupportedFeatureCoverage.diagnosticSamples()
			);
		}

		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.indexed-mesh.block-feature-dispatch");
		this.featureRenderDispatcher.renderBlockFeaturesOnly();
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.indexed-mesh.block-feature-dispatch");
	}

	private void submitSelectedWholeFrameMeshEntities(
		PoseStack poseStack,
		boolean blockDisplays,
		boolean fallingBlocks,
		boolean primedTnt,
		boolean arrows,
		boolean experienceOrbs,
		boolean itemEntities,
		boolean modelMeshes,
		boolean entityShadows,
		boolean entityFlames,
		boolean entityLeashes,
		boolean debugLines
	) {
		Vec3 cameraPos = this.levelRenderState.cameraRenderState.pos;
		for (EntityRenderState entityRenderState : this.levelRenderState.entityRenderStates) {
			boolean selected = blockDisplays
				&& entityRenderState instanceof net.minecraft.client.renderer.entity.state.BlockDisplayEntityRenderState
				|| fallingBlocks && entityRenderState instanceof net.minecraft.client.renderer.entity.state.FallingBlockRenderState
				|| primedTnt && entityRenderState instanceof net.minecraft.client.renderer.entity.state.TntRenderState
				|| arrows && entityRenderState instanceof net.minecraft.client.renderer.entity.state.ArrowRenderState
				|| experienceOrbs && entityRenderState instanceof ExperienceOrbRenderState
				|| itemEntities && entityRenderState instanceof ItemEntityRenderState
				|| entityShadows || entityFlames || entityLeashes || debugLines
			// Re-submit every extracted entity state through the semantic dispatcher.
			// Each renderer decides its own bounded Rust admission; unsupported
			// callbacks are counted by the coverage replay below and never become a
			// Java draw. Restricting this traversal to a hand-maintained state list
			// silently omitted otherwise-admitted vanilla model families (including
			// entityRenderState instanceof net.minecraft.client.renderer.entity.state.ZombieRenderState).
			// The broad modelMeshes admission includes entityRenderState instanceof net.minecraft.client.renderer.entity.state.CowRenderState
			// and entityRenderState instanceof net.minecraft.client.renderer.entity.state.PigRenderState.
			// Animated and translucent models remain semantic coverage until their
			// explicit per-frame contracts are admitted; this includes
			// instanceof net.minecraft.client.renderer.entity.state.LlamaSpitRenderState.
			|| modelMeshes;
			if (!selected) {
				continue;
			}
			this.entityRenderDispatcher.submitSemantic(
				entityRenderState,
				this.levelRenderState.cameraRenderState,
				entityRenderState.x - cameraPos.x,
				entityRenderState.y - cameraPos.y,
				entityRenderState.z - cameraPos.z,
				poseStack,
				this.submitNodeStorage
			);
		}
		if (modelMeshes) {
			this.submitRustShadowOnlyEntities(poseStack, this.levelRenderState, this.submitNodeStorage);
		}
	}

	/**
	 * Replays entity and block-entity callbacks for world text. Producers retain
	 * text placement and ordering; the collector also forwards explicitly
	 * admitted special geometry, so only proven duplicate producer work can be
	 * skipped here.
	 */
	private void submitWholeFrameWorldText(PoseStack poseStack, LevelRenderState levelRenderState, boolean modelMeshes) {
		this.rustWorldTextSubmitNodeStorage.clear();
		Vec3 cameraPos = levelRenderState.cameraRenderState.pos;
		WorldTextSubmitSemanticCollector collector = new WorldTextSubmitSemanticCollector(this.rustWorldTextSubmitNodeStorage);
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.indexed-mesh.world-text.waypoints");
		net.voxelmap.VoxelConstants.submitRustWaypointSemantics(collector, poseStack, this.minecraft.gameRenderer.getMainCamera());
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.indexed-mesh.world-text.waypoints");
		int nameTagsAfterWaypoints = collector.nameTagCallbackCount();
		int textAfterWaypoints = collector.textCallbackCount();
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.indexed-mesh.world-text.entities");
		int duplicatePaintingsSkipped = 0;
		for (EntityRenderState entityRenderState : levelRenderState.entityRenderStates) {
			if (modelMeshes
				&& entityRenderState.getClass() == net.minecraft.client.renderer.entity.state.PaintingRenderState.class
				&& entityRenderState.nameTag == null
				&& this.entityRenderDispatcher.getRenderer(entityRenderState).getClass() == net.minecraft.client.renderer.entity.PaintingRenderer.class) {
				// The primary entity pass already owns every painting quad. A
				// name-free vanilla painting emits no world text; replaying it here
				// re-generates and enqueues the same six faces per tile.
				duplicatePaintingsSkipped++;
				continue;
			}
			this.entityRenderDispatcher.submitSemantic(
					entityRenderState,
					levelRenderState.cameraRenderState,
					entityRenderState.x - cameraPos.x,
					entityRenderState.y - cameraPos.y,
				entityRenderState.z - cameraPos.z,
				poseStack,
					collector
				);
		}
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.indexed-mesh.world-text.entities");
		net.minecraft.client.dev.GraphicsFrameBenchmark.recordCounterSample("world.indexed-mesh.world-text.duplicate-paintings-skipped", duplicatePaintingsSkipped);
		int nameTagsAfterEntities = collector.nameTagCallbackCount();
		int textAfterEntities = collector.textCallbackCount();
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.indexed-mesh.world-text.block-entities");
		for (BlockEntityRenderState blockEntityRenderState : levelRenderState.blockEntityRenderStates) {
			BlockPos blockPos = blockEntityRenderState.blockPos;
			poseStack.pushPose();
			poseStack.translate(blockPos.getX() - cameraPos.x, blockPos.getY() - cameraPos.y, blockPos.getZ() - cameraPos.z);
			this.blockEntityRenderDispatcher.submitSemantic(blockEntityRenderState, poseStack, collector, levelRenderState.cameraRenderState);
			poseStack.popPose();
		}
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.indexed-mesh.world-text.block-entities");
		net.minecraft.client.dev.GraphicsFrameBenchmark.recordCounterSample("world.indexed-mesh.world-text.waypoint-callbacks", textAfterWaypoints + nameTagsAfterWaypoints);
		net.minecraft.client.dev.GraphicsFrameBenchmark.recordCounterSample("world.indexed-mesh.world-text.entity-callbacks", nameTagsAfterEntities - nameTagsAfterWaypoints + textAfterEntities - textAfterWaypoints);
		net.minecraft.client.dev.GraphicsFrameBenchmark.recordCounterSample("world.indexed-mesh.world-text.block-entity-callbacks", collector.textCallbackCount() - textAfterEntities + collector.nameTagCallbackCount() - nameTagsAfterEntities);
		net.vulkanic.world.RustGalWorldPrimitiveRenderer.recordWorldTextTraversal(
				levelRenderState.entityRenderStates.size(), collector.nameTagCallbackCount(), collector.textCallbackCount()
			);
	}

	/**
	 * The source-route coverage pass must use the same bounded producer family
	 * as the real Rust submission pass. Structural mesh eligibility alone is
	 * insufficient here: an animated or otherwise unselected producer can have
	 * extractable geometry without a valid per-frame Rust contract.
	 */
	private static boolean isSelectedWholeFrameModelSemantic(
		net.minecraft.client.model.Model<?> model,
		Object state,
		@Nullable net.minecraft.client.renderer.texture.TextureAtlasSprite sprite
	) {
		// The real semantic submitter records an exact model/texture receipt for
		// every atlas-backed family. Use that receipt before the compatibility list
		// below so newly admitted Rust model families cannot be rejected merely
		// because this coverage replay has not learned their Java state class yet.
		if (model != null && sprite != null
			&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(model, sprite)) {
			return true;
		}
		return model != null
			&& model.getClass() == net.minecraft.client.model.ArmorStandModel.class
			&& state instanceof net.minecraft.client.renderer.entity.state.ArmorStandRenderState armorStandRenderState
			&& !armorStandRenderState.isMarker
			&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
				model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/armorstand/wood.png")
			)
			|| model instanceof net.minecraft.client.model.CatModel
				&& state instanceof net.minecraft.client.renderer.entity.state.CatRenderState catRenderState
				&& catRenderState.texture != null
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, catRenderState.texture
				)
			|| model instanceof net.minecraft.client.model.WolfModel
				&& state instanceof net.minecraft.client.renderer.entity.state.WolfRenderState wolfRenderState
				&& !wolfRenderState.isBaby
				&& wolfRenderState.texture != null
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, wolfRenderState.texture
				)
			|| model instanceof net.minecraft.client.model.VillagerModel
				&& state instanceof net.minecraft.client.renderer.entity.state.VillagerRenderState
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/villager/villager.png")
				)
			|| model instanceof net.minecraft.client.model.VillagerModel
				&& state instanceof net.minecraft.client.renderer.entity.state.VillagerRenderState
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/wandering_trader.png")
				)
			|| model instanceof net.minecraft.client.model.GuardianModel
				&& state instanceof net.minecraft.client.renderer.entity.state.GuardianRenderState
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/guardian.png")
				)
			|| model instanceof net.minecraft.client.model.GuardianModel
				&& state instanceof net.minecraft.client.renderer.entity.state.GuardianRenderState
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/guardian/guardian_elder.png")
				)
			|| model instanceof net.minecraft.client.model.SnowGolemModel
				&& state instanceof net.minecraft.client.renderer.entity.state.SnowGolemRenderState
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/snow_golem.png")
				)
			|| model instanceof net.minecraft.client.model.IronGolemModel
				&& state instanceof net.minecraft.client.renderer.entity.state.IronGolemRenderState
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/iron_golem/iron_golem.png")
				)
			|| model instanceof net.minecraft.client.model.SkeletonModel
				&& state instanceof net.minecraft.client.renderer.entity.state.SkeletonRenderState skeletonRenderState
				&& !skeletonRenderState.isBaby
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/skeleton/skeleton.png")
				)
			|| model instanceof net.minecraft.client.model.SkeletonModel
				&& state instanceof net.minecraft.client.renderer.entity.state.SkeletonRenderState witherSkeletonRenderState
				&& !witherSkeletonRenderState.isBaby
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/skeleton/wither_skeleton.png")
				)
			|| model != null
				&& model.getClass() == net.minecraft.client.model.ChickenModel.class
				&& state instanceof net.minecraft.client.renderer.entity.state.ChickenRenderState chickenRenderState
				&& chickenRenderState.variant != null
				&& !chickenRenderState.isBaby
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, chickenRenderState.variant.modelAndTexture().asset().texturePath()
				)
			|| model instanceof net.minecraft.client.model.CowModel
				&& state instanceof net.minecraft.client.renderer.entity.state.CowRenderState cowRenderState
				&& cowRenderState.variant != null
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, cowRenderState.variant.modelAndTexture().asset().texturePath()
				)
			|| model instanceof net.minecraft.client.model.PigModel
				&& state instanceof net.minecraft.client.renderer.entity.state.PigRenderState pigRenderState
				&& pigRenderState.variant != null
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, pigRenderState.variant.modelAndTexture().asset().texturePath()
				)
			|| model instanceof net.minecraft.client.model.CreeperModel
				&& state instanceof net.minecraft.client.renderer.entity.state.CreeperRenderState creeperRenderState
				&& !creeperRenderState.isPowered
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/creeper/creeper.png")
				)
			|| model instanceof net.minecraft.client.model.FoxModel
				&& state instanceof net.minecraft.client.renderer.entity.state.FoxRenderState foxRenderState
				&& !foxRenderState.isBaby
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, foxRenderState.variant == net.minecraft.world.entity.animal.Fox.Variant.RED
						? net.minecraft.resources.ResourceLocation.withDefaultNamespace(
							foxRenderState.isSleeping ? "textures/entity/fox/fox_sleep.png" : "textures/entity/fox/fox.png")
						: net.minecraft.resources.ResourceLocation.withDefaultNamespace(
							foxRenderState.isSleeping ? "textures/entity/fox/snow_fox_sleep.png" : "textures/entity/fox/snow_fox.png")
				)
			|| model instanceof net.minecraft.client.model.SpiderModel
				&& state instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/spider/spider.png")
				)
			|| model instanceof net.minecraft.client.model.SpiderModel
				&& state instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/spider/cave_spider.png")
				)
			|| model != null
				&& model.getClass() == net.minecraft.client.model.HappyGhastModel.class
				&& state instanceof net.minecraft.client.renderer.entity.state.HappyGhastRenderState happyGhastRenderState
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model,
					happyGhastRenderState.isBaby
						? net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/ghast/happy_ghast_baby.png")
						: net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/ghast/happy_ghast.png")
				)
			|| model != null
				&& model.getClass() == net.minecraft.client.model.GhastModel.class
				&& state instanceof net.minecraft.client.renderer.entity.state.GhastRenderState ghastRenderState
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, ghastRenderState.isCharging
						? net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/ghast/ghast_shooting.png")
						: net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/ghast/ghast.png")
				)
			|| model instanceof net.minecraft.client.model.BlazeModel
				&& state instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/blaze.png")
				)
			|| model != null
				&& model.getClass() == net.minecraft.client.model.WitchModel.class
				&& state instanceof net.minecraft.client.renderer.entity.state.WitchRenderState
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/witch.png")
				)
			|| model != null
				&& model.getClass() == net.minecraft.client.model.EndermanModel.class
				&& state instanceof net.minecraft.client.renderer.entity.state.EndermanRenderState
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/enderman/enderman.png")
				)
			|| model instanceof net.minecraft.client.model.EndermiteModel
				&& state instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/endermite.png")
				)
			|| model instanceof net.minecraft.client.model.SilverfishModel
				&& state instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/silverfish.png")
				)
			|| model instanceof net.minecraft.client.model.BatModel
				&& state instanceof net.minecraft.client.renderer.entity.state.BatRenderState
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/bat.png")
				)
			|| model instanceof net.minecraft.client.model.CodModel
				&& state instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/fish/cod.png")
				)
			|| model instanceof net.minecraft.client.model.SalmonModel
				&& state instanceof net.minecraft.client.renderer.entity.state.SalmonRenderState
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/fish/salmon.png")
				)
			|| (model instanceof net.minecraft.client.model.PufferfishBigModel
				|| model instanceof net.minecraft.client.model.PufferfishMidModel
				|| model instanceof net.minecraft.client.model.PufferfishSmallModel)
				&& state instanceof net.minecraft.client.renderer.entity.state.PufferfishRenderState
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/fish/pufferfish.png")
				)
			|| model instanceof net.minecraft.client.model.TadpoleModel
				&& state instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/tadpole/tadpole.png")
				)
			|| model instanceof net.minecraft.client.model.OcelotModel
				&& state instanceof net.minecraft.client.renderer.entity.state.FelineRenderState
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/cat/ocelot.png")
				)
			|| model instanceof net.minecraft.client.model.PolarBearModel
				&& state instanceof net.minecraft.client.renderer.entity.state.PolarBearRenderState polarBearRenderState
				&& !polarBearRenderState.isBaby
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/bear/polarbear.png")
				)
			|| model instanceof net.minecraft.client.model.DolphinModel
				&& state instanceof net.minecraft.client.renderer.entity.state.DolphinRenderState
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/dolphin.png")
				)
			|| model instanceof net.minecraft.client.model.TurtleModel
				&& state instanceof net.minecraft.client.renderer.entity.state.TurtleRenderState turtleRenderState
				&& !turtleRenderState.isBaby
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/turtle/big_sea_turtle.png")
				)
			|| model instanceof net.minecraft.client.model.PandaModel
				&& state instanceof net.minecraft.client.renderer.entity.state.PandaRenderState pandaRenderState
				&& !pandaRenderState.isBaby
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.vulkanic.world.RustGalWorldPrimitiveRenderer.vanillaPandaTextureIdentity(pandaRenderState)
				)
			|| model instanceof net.minecraft.client.model.BeeModel
				&& state instanceof net.minecraft.client.renderer.entity.state.BeeRenderState beeRenderState
				&& !beeRenderState.isBaby
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.vulkanic.world.RustGalWorldPrimitiveRenderer.vanillaBeeTextureIdentity(beeRenderState)
				)
			|| model instanceof net.minecraft.client.model.AxolotlModel
				&& state instanceof net.minecraft.client.renderer.entity.state.AxolotlRenderState axolotlRenderState
				&& !axolotlRenderState.isBaby
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.vulkanic.world.RustGalWorldPrimitiveRenderer.vanillaAxolotlTextureIdentity(axolotlRenderState)
				)
			|| model instanceof net.minecraft.client.model.FrogModel
				&& state instanceof net.minecraft.client.renderer.entity.state.FrogRenderState frogRenderState
				&& frogRenderState.texture != null
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, frogRenderState.texture
				)
			|| model instanceof net.minecraft.client.model.SquidModel
				&& state instanceof net.minecraft.client.renderer.entity.state.SquidRenderState squidRenderState
				&& !squidRenderState.isBaby
				&& (net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/squid/squid.png")
				) || net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/squid/glow_squid.png")
				))
			|| model instanceof net.minecraft.client.model.GoatModel
				&& state instanceof net.minecraft.client.renderer.entity.state.GoatRenderState goatRenderState
				&& !goatRenderState.isBaby
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/goat/goat.png")
				)
			|| model instanceof net.minecraft.client.model.AllayModel
				&& state instanceof net.minecraft.client.renderer.entity.state.AllayRenderState
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/allay/allay.png")
				)
			|| model instanceof net.minecraft.client.model.IllagerModel
				&& state instanceof net.minecraft.client.renderer.entity.state.EvokerRenderState
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/illager/evoker.png")
				)
			|| model instanceof net.minecraft.client.model.IllagerModel
				&& state instanceof net.minecraft.client.renderer.entity.state.IllagerRenderState
				&& (net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/illager/vindicator.png")
				) || net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/illager/pillager.png")
				) || net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/illager/illusioner.png")
				))
			|| model instanceof net.minecraft.client.model.ParrotModel
				&& state instanceof net.minecraft.client.renderer.entity.state.ParrotRenderState parrotRenderState
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, switch (parrotRenderState.variant) {
						case RED_BLUE -> net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/parrot/parrot_red_blue.png");
						case BLUE -> net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/parrot/parrot_blue.png");
						case GREEN -> net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/parrot/parrot_green.png");
						case YELLOW_BLUE -> net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/parrot/parrot_yellow_blue.png");
						case GRAY -> net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/parrot/parrot_grey.png");
					}
				)
			|| (model instanceof net.minecraft.client.model.TropicalFishModelA
				|| model instanceof net.minecraft.client.model.TropicalFishModelB)
				&& state instanceof net.minecraft.client.renderer.entity.state.TropicalFishRenderState tropicalFishRenderState
				&& (net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.vulkanic.world.RustGalWorldPrimitiveRenderer.vanillaTropicalFishTextureIdentity(tropicalFishRenderState)
				) || net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.vulkanic.world.RustGalWorldPrimitiveRenderer.vanillaTropicalFishPatternTextureIdentity(tropicalFishRenderState)
				))
			|| model instanceof net.minecraft.client.model.RavagerModel
				&& state instanceof net.minecraft.client.renderer.entity.state.RavagerRenderState
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/illager/ravager.png")
				)
			|| model instanceof net.minecraft.client.model.VexModel
				&& state instanceof net.minecraft.client.renderer.entity.state.VexRenderState vexRenderState
				&& (net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/illager/vex.png")
				) || net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/illager/vex_charging.png")
				))
			|| model instanceof net.minecraft.client.model.SlimeModel
				&& state instanceof net.minecraft.client.renderer.entity.state.SlimeRenderState slimeRenderState
				&& slimeRenderState.size > 0
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/slime/slime.png")
				)
			|| model instanceof net.minecraft.client.model.LavaSlimeModel
				&& state instanceof net.minecraft.client.renderer.entity.state.SlimeRenderState
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/slime/magmacube.png")
				)
			|| model instanceof net.minecraft.client.model.PhantomModel
				&& state instanceof net.minecraft.client.renderer.entity.state.PhantomRenderState
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/phantom.png")
				)
			|| model instanceof net.minecraft.client.model.WardenModel
				&& state instanceof net.minecraft.client.renderer.entity.state.WardenRenderState
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/warden/warden.png")
				)
			|| model instanceof net.minecraft.client.model.WitherBossModel
				&& state instanceof net.minecraft.client.renderer.entity.state.WitherRenderState witherRenderState
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, witherRenderState.invulnerableTicks > 0.0F && !witherRenderState.isPowered
						? net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/wither/wither_invulnerable.png")
						: net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/wither/wither.png")
				)
			|| model instanceof net.minecraft.client.model.DrownedModel
				&& state instanceof net.minecraft.client.renderer.entity.state.ZombieRenderState
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/zombie/drowned.png")
				)
			|| model instanceof net.minecraft.client.model.CreakingModel
				&& state instanceof net.minecraft.client.renderer.entity.state.CreakingRenderState
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/creaking/creaking.png")
				)
			|| model instanceof net.minecraft.client.model.BreezeModel
				&& state instanceof net.minecraft.client.renderer.entity.state.BreezeRenderState
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/breeze/breeze.png")
				)
			|| model instanceof net.minecraft.client.model.CopperGolemModel
				&& state instanceof net.minecraft.client.renderer.entity.state.CopperGolemRenderState copperGolemRenderState
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.world.entity.animal.coppergolem.CopperGolemOxidationLevels
						.getOxidationLevel(copperGolemRenderState.weathering).texture()
				)
			|| model instanceof net.minecraft.client.model.StriderModel
				&& state instanceof net.minecraft.client.renderer.entity.state.StriderRenderState striderRenderState
				&& !striderRenderState.isBaby
				&& (net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/strider/strider.png")
				) || net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/strider/strider_cold.png")
				))
			|| model instanceof net.minecraft.client.model.HoglinModel
				&& state instanceof net.minecraft.client.renderer.entity.state.HoglinRenderState hoglinRenderState
				&& !hoglinRenderState.isBaby
				&& (net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/hoglin/hoglin.png")
				) || net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/hoglin/zoglin.png")
				))
			|| model instanceof net.minecraft.client.model.CamelModel
				&& state instanceof net.minecraft.client.renderer.entity.state.CamelRenderState camelRenderState
				&& !camelRenderState.isBaby
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/camel/camel.png")
				)
			|| (model instanceof net.minecraft.client.model.PiglinModel
				|| model instanceof net.minecraft.client.model.ZombifiedPiglinModel)
				&& state instanceof net.minecraft.client.renderer.entity.state.HumanoidRenderState
				&& (net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/piglin/piglin.png")
				) || net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/piglin/piglin_brute.png")
				) || net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/piglin/zombified_piglin.png")
				))
			|| model instanceof net.minecraft.client.model.SkeletonModel
				&& state instanceof net.minecraft.client.renderer.entity.state.SkeletonRenderState strayRenderState
				&& !strayRenderState.isBaby
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/skeleton/stray.png")
				)
			|| model instanceof net.minecraft.client.model.BoggedModel
				&& state instanceof net.minecraft.client.renderer.entity.state.BoggedRenderState boggedRenderState
				&& !boggedRenderState.isBaby
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/skeleton/bogged.png")
				)
			|| model instanceof net.minecraft.client.model.GiantZombieModel
				&& state instanceof net.minecraft.client.renderer.entity.state.ZombieRenderState
				&& (net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/zombie/zombie.png")
				) || net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/zombie/husk.png")
				))
			|| model instanceof net.minecraft.client.model.ArmadilloModel
				&& state instanceof net.minecraft.client.renderer.entity.state.ArmadilloRenderState armadilloRenderState
				&& !armadilloRenderState.isBaby
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/armadillo.png")
				)
			|| model instanceof net.minecraft.client.model.SnifferModel
				&& state instanceof net.minecraft.client.renderer.entity.state.SnifferRenderState snifferRenderState
				&& !snifferRenderState.isBaby
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/sniffer/sniffer.png")
				)
			|| model != null
				&& model.getClass() == net.minecraft.client.model.animal.nautilus.NautilusModel.class
				&& state instanceof net.minecraft.client.renderer.entity.state.NautilusRenderState nautilusRenderState
				&& !nautilusRenderState.isBaby
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, nautilusRenderState.variant == null
						? net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/nautilus/nautilus.png")
						: nautilusRenderState.variant.modelAndTexture().asset().texturePath()
				)
			|| model instanceof net.minecraft.client.model.HorseModel
				&& state instanceof net.minecraft.client.renderer.entity.state.HorseRenderState horseRenderState
				&& !horseRenderState.isBaby
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, switch (horseRenderState.variant) {
						case WHITE -> net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/horse/horse_white.png");
						case CREAMY -> net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/horse/horse_creamy.png");
						case CHESTNUT -> net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/horse/horse_chestnut.png");
						case BROWN -> net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/horse/horse_brown.png");
						case BLACK -> net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/horse/horse_black.png");
						case GRAY -> net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/horse/horse_gray.png");
						case DARK_BROWN -> net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/horse/horse_darkbrown.png");
					}
				)
			|| model instanceof net.minecraft.client.model.DonkeyModel
				&& state instanceof net.minecraft.client.renderer.entity.state.DonkeyRenderState donkeyRenderState
				&& !donkeyRenderState.isBaby
				&& (net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/horse/donkey.png")
				) || net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/horse/mule.png")
				))
			|| model instanceof net.minecraft.client.model.LlamaModel
				&& state instanceof net.minecraft.client.renderer.entity.state.LlamaRenderState llamaRenderState
				&& !llamaRenderState.isBaby
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, switch (llamaRenderState.variant) {
						case CREAMY -> net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/llama/creamy.png");
						case WHITE -> net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/llama/white.png");
						case BROWN -> net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/llama/brown.png");
						case GRAY -> net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/llama/gray.png");
						default -> null;
					}
				)
			|| model != null
				&& model.getClass() == net.minecraft.client.model.RabbitModel.class
				&& state instanceof net.minecraft.client.renderer.entity.state.RabbitRenderState rabbitRenderState
				&& !rabbitRenderState.isBaby
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.vulkanic.world.RustGalWorldPrimitiveRenderer.vanillaRabbitTextureIdentity(rabbitRenderState)
				)
			|| model != null
				&& model.getClass() == net.minecraft.client.model.ZombieModel.class
				&& state instanceof net.minecraft.client.renderer.entity.state.ZombieRenderState
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/zombie/zombie.png")
				)
			|| model != null
				&& model.getClass() == net.minecraft.client.model.ZombieVillagerModel.class
				&& state instanceof net.minecraft.client.renderer.entity.state.ZombieVillagerRenderState
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/zombie_villager/zombie_villager.png")
				)
			|| model != null
				&& model.getClass() == net.minecraft.client.model.SheepModel.class
				&& state instanceof net.minecraft.client.renderer.entity.state.SheepRenderState sheepRenderState
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(
					model, net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/sheep/sheep.png")
				)
			|| model instanceof net.minecraft.client.model.ChestModel && state instanceof Float
			// BedRenderer uses Model.Simple with Unit state. The texture identity
			// distinguishes this already-supported model from signs and the other
			// Model.Simple producers that still require separate Rust contracts.
			|| model instanceof net.minecraft.client.model.Model.Simple
				&& state == net.minecraft.util.Unit.INSTANCE
				&& sprite != null
				&& sprite.contents().name().getPath().startsWith("entity/bed/")
			|| model instanceof net.minecraft.client.model.BookModel
				&& state instanceof net.minecraft.client.model.BookModel.State
				&& sprite != null
				&& sprite.contents().name().getPath().contains("enchanting_table_book")
			|| model instanceof net.minecraft.client.model.BellModel
				&& state instanceof net.minecraft.client.model.BellModel.State
				&& sprite != null
				&& sprite.contents().name().getPath().contains("bell/bell_body")
			|| model instanceof net.minecraft.client.renderer.blockentity.ShulkerBoxRenderer.ShulkerBoxModel
				&& state instanceof Float
				&& sprite != null
				&& sprite.contents().name().getPath().startsWith("entity/shulker/")
			|| model instanceof net.minecraft.client.model.SkullModelBase
				&& state instanceof SkullBlockRenderState skullState
				&& skullState.textureIdentity != null
			|| model instanceof net.minecraft.client.model.BannerModel
				&& state == net.minecraft.util.Unit.INSTANCE
				&& sprite != null
				&& (sprite.contents().name().getPath().equals("entity/banner_base")
					|| sprite.contents().name().getPath().startsWith("entity/banner/"))
			|| model instanceof net.minecraft.client.model.BannerFlagModel
				&& state instanceof Float
				&& sprite != null
				&& (sprite.contents().name().getPath().equals("entity/banner_base")
					|| sprite.contents().name().getPath().startsWith("entity/banner/"))
			|| model instanceof net.minecraft.client.model.CopperGolemStatueModel
				&& state instanceof CopperGolemStatueRenderState statueState
				&& statueState.textureIdentity != null
			|| model instanceof net.minecraft.client.model.LeashKnotModel
				&& state instanceof net.minecraft.client.renderer.entity.state.EntityRenderState leashState
				&& leashState.outlineColor == 0
			|| model instanceof net.minecraft.client.model.ShulkerBulletModel
				&& state instanceof net.minecraft.client.renderer.entity.state.ShulkerBulletRenderState bulletState
				&& bulletState.outlineColor == 0
			|| model instanceof net.minecraft.client.model.Model.Simple
				&& state == net.minecraft.util.Unit.INSTANCE
				&& sprite != null
				&& sprite.contents().name().getPath().startsWith("entity/signs/");
	}

	/**
	 * Replays only block-entity families with an explicit semantic producer
	 * (indexed model parts/items, beacon/end-portal materials, or nested entity
	 * submits). Eligibility remains at each submit callsite, so this collector
	 * cannot manufacture geometry or retain renderer state.
	 */
	private void submitSelectedWholeFrameModelBlockEntities(PoseStack poseStack, LevelRenderState levelRenderState) {
		Vec3 cameraPos = levelRenderState.cameraRenderState.pos;
		for (BlockEntityRenderState blockEntityRenderState : levelRenderState.blockEntityRenderStates) {
			if (!(blockEntityRenderState instanceof net.minecraft.client.renderer.blockentity.state.ChestRenderState)
				&& !(blockEntityRenderState instanceof BedRenderState)
				&& !(blockEntityRenderState instanceof BellRenderState)
				&& !(blockEntityRenderState instanceof BeaconRenderState)
				&& !(blockEntityRenderState instanceof EndPortalRenderState)
				&& !(blockEntityRenderState instanceof net.minecraft.client.renderer.blockentity.state.EndGatewayRenderState)
				&& !(blockEntityRenderState instanceof CondiutRenderState)
				&& !(blockEntityRenderState instanceof SpawnerRenderState)
				&& !(blockEntityRenderState instanceof BlockEntityWithBoundingBoxRenderState)
				&& !(blockEntityRenderState instanceof TestInstanceRenderState)
				&& !(blockEntityRenderState instanceof CampfireRenderState)
				&& !(blockEntityRenderState instanceof BrushableBlockRenderState)
				&& !(blockEntityRenderState instanceof ShelfRenderState)
				&& !(blockEntityRenderState instanceof VaultRenderState)
				&& !(blockEntityRenderState instanceof ShulkerBoxRenderState)
				&& !(blockEntityRenderState instanceof net.minecraft.client.renderer.blockentity.state.DecoratedPotRenderState)
				&& !(blockEntityRenderState instanceof net.minecraft.client.renderer.blockentity.state.EnchantTableRenderState)
				&& !(blockEntityRenderState instanceof net.minecraft.client.renderer.blockentity.state.LecternRenderState)
				&& !(blockEntityRenderState instanceof net.minecraft.client.renderer.blockentity.state.SignRenderState)
				&& !(blockEntityRenderState instanceof SkullBlockRenderState)
				&& !(blockEntityRenderState instanceof BannerRenderState)
				&& !(blockEntityRenderState instanceof CopperGolemStatueRenderState)) {
				continue;
			}
			BlockPos blockPos = blockEntityRenderState.blockPos;
			poseStack.pushPose();
			poseStack.translate(blockPos.getX() - cameraPos.x, blockPos.getY() - cameraPos.y, blockPos.getZ() - cameraPos.z);
			this.blockEntityRenderDispatcher.submitSemantic(
				blockEntityRenderState,
				poseStack,
				this.submitNodeStorage,
				levelRenderState.cameraRenderState
			);
			poseStack.popPose();
		}
	}

	private void collectUnsupportedWholeFrameFeatures(
		PoseStack poseStack,
		WorldFeatureCoverageCollector coverage,
		boolean blockDisplays,
		boolean fallingBlocks,
		boolean pistons,
		boolean arrows,
		boolean primedTnt,
		boolean experienceOrbs,
		boolean itemEntities,
		boolean modelMeshes
	) {
		Vec3 cameraPos = this.levelRenderState.cameraRenderState.pos;
		for (EntityRenderState entityRenderState : this.levelRenderState.entityRenderStates) {
			boolean handledByRust = blockDisplays
				&& entityRenderState instanceof net.minecraft.client.renderer.entity.state.BlockDisplayEntityRenderState
				|| fallingBlocks && entityRenderState instanceof net.minecraft.client.renderer.entity.state.FallingBlockRenderState
				|| arrows && entityRenderState instanceof net.minecraft.client.renderer.entity.state.ArrowRenderState
				|| primedTnt && entityRenderState instanceof net.minecraft.client.renderer.entity.state.TntRenderState
				|| experienceOrbs && entityRenderState instanceof ExperienceOrbRenderState
				|| itemEntities && entityRenderState instanceof ItemEntityRenderState;
			// entityRenderState instanceof net.minecraft.client.renderer.entity.state.ZombieRenderState
			// and every other model family are intentionally not structurally exempted
			// here; coverage must observe the same-frame receipt.
			if (handledByRust) {
				continue;
			}
			this.entityRenderDispatcher.submitSemantic(
				entityRenderState,
				this.levelRenderState.cameraRenderState,
				entityRenderState.x - cameraPos.x,
				entityRenderState.y - cameraPos.y,
				entityRenderState.z - cameraPos.z,
				poseStack,
				coverage
			);
		}

		for (BlockEntityRenderState blockEntityRenderState : this.levelRenderState.blockEntityRenderStates) {
			if (pistons && blockEntityRenderState instanceof PistonHeadRenderState) {
				continue;
			}
			BlockPos blockPos = blockEntityRenderState.blockPos;
			poseStack.pushPose();
			poseStack.translate(blockPos.getX() - cameraPos.x, blockPos.getY() - cameraPos.y, blockPos.getZ() - cameraPos.z);
			this.blockEntityRenderDispatcher.submitSemantic(blockEntityRenderState, poseStack, coverage, this.levelRenderState.cameraRenderState);
			poseStack.popPose();
		}
	}

	/**
	 * Forwards only copied vanilla name-tag and ordinary text submissions. This
	 * collector is a semantic extraction sink, not a secondary renderer or
	 * feature route.
	 */
	private static final class WorldTextSubmitSemanticCollector implements SubmitNodeCollector {
		private final SubmitNodeStorage target;
		private final int order;
		private final WorldTextSubmissionCounts counts;

		private WorldTextSubmitSemanticCollector(SubmitNodeStorage target) {
			this(target, 0, new WorldTextSubmissionCounts());
		}

		private WorldTextSubmitSemanticCollector(SubmitNodeStorage target, int order, WorldTextSubmissionCounts counts) {
			this.target = target;
			this.order = order;
			this.counts = counts;
		}

		@Override
		public OrderedSubmitNodeCollector order(int order) {
			return new WorldTextSubmitSemanticCollector(this.target, order, this.counts);
		}

		@Override
		public boolean isSemanticCoverageOnly() {
			return true;
		}

		@Override
		public void submitNameTag(
			PoseStack poseStack,
			@Nullable Vec3 offset,
			int packedLight,
			net.minecraft.network.chat.Component text,
			boolean seeThrough,
			int width,
			double distance,
			net.minecraft.client.renderer.state.CameraRenderState cameraRenderState
		) {
			this.counts.nameTagCallbacks++;
			this.target.submitNameTag(poseStack, offset, packedLight, text, seeThrough, width, distance, cameraRenderState);
		}

		@Override
		public void submitNameTagSemantic(
			PoseStack poseStack, @Nullable Vec3 offset, int packedLight,
			net.minecraft.network.chat.Component text, boolean seeThrough, int width,
			double distance, net.minecraft.client.renderer.state.CameraRenderState cameraRenderState
		) {
			this.counts.nameTagCallbacks++;
			this.target.submitNameTagSemantic(poseStack, offset, packedLight, text, seeThrough, width, distance, cameraRenderState);
		}

		private int nameTagCallbackCount() {
			return this.counts.nameTagCallbacks;
		}

		private int textCallbackCount() {
			return this.counts.textCallbacks;
		}

		@Override public void submitHitbox(PoseStack poseStack, EntityRenderState state, net.minecraft.client.renderer.entity.state.HitboxesRenderState hitboxes) {}
		@Override public void submitShadow(PoseStack poseStack, float radius, List<EntityRenderState.ShadowPiece> pieces) {}
		@Override public void submitText(PoseStack poseStack, float x, float y, net.minecraft.util.FormattedCharSequence text, boolean shadow, net.minecraft.client.gui.Font.DisplayMode mode, int color, int backgroundColor, int packedLight, int packedOverlay) {
			this.counts.textCallbacks++;
			this.target.submitTextSemantic(this.order, poseStack, x, y, text, shadow, mode, color, backgroundColor, packedLight, packedOverlay);
		}
		@Override public void submitTextSemantic(PoseStack poseStack, float x, float y, net.minecraft.util.FormattedCharSequence text, boolean shadow, net.minecraft.client.gui.Font.DisplayMode mode, int color, int backgroundColor, int packedLight, int packedOverlay) {
			this.counts.textCallbacks++;
			this.target.submitTextSemantic(this.order, poseStack, x, y, text, shadow, mode, color, backgroundColor, packedLight, packedOverlay);
		}
		@Override public boolean submitColoredQuads(PoseStack poseStack, RenderType renderType, float[] vertices, float[] uvs, int[] colors, int lightCoords) {
			return this.target.submitColoredQuads(poseStack, renderType, vertices, uvs, colors, lightCoords);
		}
		@Override public boolean submitTexturedQuad(PoseStack poseStack, RenderType renderType, net.minecraft.resources.ResourceLocation textureIdentity, float[] vertices, float[] uvs, int color, int lightCoords) {
			return net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueTexturedQuad(poseStack.last().pose(), textureIdentity, vertices, uvs, color, lightCoords);
		}
		@Override public boolean submitTranslucentTexturedQuad(PoseStack poseStack, RenderType renderType, net.minecraft.resources.ResourceLocation textureIdentity, float[] vertices, float[] uvs, int color, int lightCoords) {
			return net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueTranslucentTexturedQuad(poseStack.last().pose(), textureIdentity, vertices, uvs, color, lightCoords);
		}
		@Override public boolean submitTexturedQuads(PoseStack poseStack, RenderType renderType, net.minecraft.resources.ResourceLocation textureIdentity, float[] vertices, float[] uvs, int[] colors, int lightCoords) {
			return true;
		}
		@Override public void submitFlame(PoseStack poseStack, EntityRenderState state, org.joml.Quaternionf rotation) {}
		@Override public void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leash) {}
		@Override public <S> void submitModel(net.minecraft.client.model.Model<? super S> model, S state, PoseStack poseStack, RenderType type, int light, int overlay, int color, @Nullable net.minecraft.client.renderer.texture.TextureAtlasSprite sprite, int outlineColor, @Nullable net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumbling) {}
		@Override public void submitModelPart(net.minecraft.client.model.geom.ModelPart part, PoseStack poseStack, RenderType type, int light, int overlay, @Nullable net.minecraft.client.renderer.texture.TextureAtlasSprite sprite, boolean sheeted, boolean foil, int outlineColor, @Nullable net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumbling, int tint) {}
		@Override public void submitBlock(PoseStack poseStack, BlockState state, int light, int overlay, int outlineColor) {}
		@Override public void submitMovingBlock(PoseStack poseStack, net.minecraft.client.renderer.block.MovingBlockRenderState state) {}
		@Override public void submitBlockModel(PoseStack poseStack, RenderType type, net.minecraft.client.renderer.block.model.BlockStateModel model, float red, float green, float blue, int light, int overlay, int outlineColor) {}
		@Override public void submitItem(PoseStack poseStack, net.minecraft.world.item.ItemDisplayContext context, int light, int overlay, int outlineColor, int[] tints, List<net.minecraft.client.renderer.block.model.BakedQuad> quads, RenderType type, net.minecraft.client.renderer.item.ItemStackRenderState.FoilType foil) {}
		@Override public void submitCustomGeometry(PoseStack poseStack, RenderType type, SubmitNodeCollector.CustomGeometryRenderer renderer) {}
		@Override public void submitParticleGroup(SubmitNodeCollector.ParticleGroupRenderer renderer) {}
		@Override public <S> void submitModelOutlineSemanticTexture(net.minecraft.client.model.Model<? super S> model, S state,
			PoseStack poseStack, RenderType material, int light, net.minecraft.resources.ResourceLocation textureIdentity, int outlineColor) {
			// Text-only replay must never enqueue a second body or outline.
		}
		@Override public <S> void submitModelSemanticTexture(net.minecraft.client.model.Model<? super S> model, S state, PoseStack poseStack, RenderType type, int light, int overlay, int color, net.minecraft.resources.ResourceLocation textureIdentity, int outlineColor, @Nullable net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumbling) {
			// End Crystal is a complete, explicitly admitted model family in the
			// Rust whole-frame route. Forward only that state from this text replay;
			// every other entity model remains intentionally discarded here so the
			// text traversal cannot duplicate ordinary entity geometry.
			if (state instanceof net.minecraft.client.renderer.entity.state.EndCrystalRenderState
					|| state instanceof net.minecraft.client.renderer.blockentity.state.CondiutRenderState) {
				this.target.submitModelSemanticTexture(model, state, poseStack, type, light, overlay, color, textureIdentity, outlineColor, crumbling);
				// The storage submission above is the same explicit model ABI used by
				// the primary entity collector. Record a family receipt here as well,
				// because this text replay intentionally has no model-route diagnostic
				// list of its own.
				if (state instanceof net.minecraft.client.renderer.entity.state.EndCrystalRenderState) {
					net.minecraft.client.dev.GraphicsFrameBenchmark.recordSubmittedWorkIdentity("model-mesh", "rust-vulkan-whole-frame:end-crystal");
					net.minecraft.client.dev.DeterministicCameraCapture.recordSubmittedWorkIdentity("model-mesh", "rust-vulkan-whole-frame:end-crystal");
				} else {
					net.minecraft.client.dev.GraphicsFrameBenchmark.recordSubmittedWorkIdentity("model-mesh", "rust-vulkan-whole-frame:conduit");
					net.minecraft.client.dev.DeterministicCameraCapture.recordSubmittedWorkIdentity("model-mesh", "rust-vulkan-whole-frame:conduit");
				}
				// The deterministic conduit fixture is admitted only through the same
				// semantic model-mesh route: "conduit".equals(MODEL_MESH_SCENARIO).
			}
		}
		@Override public boolean submitCrystalBeam(PoseStack poseStack, RenderType type, net.minecraft.resources.ResourceLocation textureIdentity, float[] vertices, float[] uvs, int[] colors, int lightCoords) {
			return this.target.order(this.order).submitCrystalBeam(poseStack, type, textureIdentity, vertices, uvs, colors, lightCoords);
		}
	}

	private static final class WorldTextSubmissionCounts {
		private int nameTagCallbacks;
		private int textCallbacks;
	}

	/**
	 * Records feature-family semantics while deliberately discarding renderer
	 * objects, render types, callbacks, Iris state, and native handles. It is
	 * used only to make missing Rust frontends explicit before route admission.
	 */
	private static final class WorldFeatureCoverageCollector implements SubmitNodeCollector {
		@Override public <S> void submitModelOutlineSemanticTexture(net.minecraft.client.model.Model<? super S> model, S state,
			PoseStack poseStack, RenderType material, int light, net.minecraft.resources.ResourceLocation textureIdentity, int outlineColor) {
			if (textureIdentity == null || !net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(model, textureIdentity)) {
				this.modelSubmits++;
			}
		}
		private static final int MAX_DIAGNOSTIC_SAMPLES = 8;
		private int modelSubmits;
		private int modelPartSubmits;
		private int blockModelSubmits;
		private int ordinaryBlockSubmits;
		private int itemSubmits;
		private int customGeometrySubmits;
		private int shadowSubmits;
		private int flameSubmits;
		private int nameTagSubmits;
		private int textSubmits;
		private int hitboxSubmits;
		private int leashSubmits;
		private int particleGroupSubmits;
		private final List<String> diagnosticSamples = new ArrayList<>();

		@Override
		public OrderedSubmitNodeCollector order(int order) {
			return this;
		}

		@Override
		public boolean isSemanticCoverageOnly() {
			return true;
		}

		@Override
		public void submitHitbox(PoseStack poseStack, EntityRenderState entityRenderState, net.minecraft.client.renderer.entity.state.HitboxesRenderState hitboxesRenderState) {
		}

		@Override
		public void submitShadow(PoseStack poseStack, float radius, List<EntityRenderState.ShadowPiece> pieces) {
		}

		@Override
		public void submitNameTag(PoseStack poseStack, @Nullable Vec3 offset, int packedLight, net.minecraft.network.chat.Component text, boolean seeThrough, int width, double distance, net.minecraft.client.renderer.state.CameraRenderState cameraRenderState) {
		}

		@Override
		public void submitText(PoseStack poseStack, float x, float y, net.minecraft.util.FormattedCharSequence text, boolean shadow, net.minecraft.client.gui.Font.DisplayMode displayMode, int color, int backgroundColor, int packedLight, int packedOverlay) {
		}

		@Override
		public void submitFlame(PoseStack poseStack, EntityRenderState entityRenderState, org.joml.Quaternionf rotation) {
		}

		@Override
		public void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leashState) {
		}

		@Override
		public <S> void submitModelSemanticTexture(net.minecraft.client.model.Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, int light, int overlay, int color, net.minecraft.resources.ResourceLocation textureIdentity, int outlineColor, @Nullable net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
			if (model instanceof net.minecraft.client.model.TridentModel
				&& state == net.minecraft.util.Unit.INSTANCE
				&& net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/trident.png").equals(textureIdentity)
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.isStandaloneModelMeshEligible(model, renderType, textureIdentity, overlay, outlineColor, crumblingOverlay)) {
				return;
			}
			// Direct-texture model callsites prove ownership with the exact copied
			// texture identity recorded by the real selected traversal. Do not infer
			// admission from model class alone: a second texture or an unavailable
			// extraction must remain visible to the coverage counter.
			if (textureIdentity != null
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(model, textureIdentity)) {
				return;
			}
			if (state instanceof EntityRenderState
				&& textureIdentity != null
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.isStandaloneModelMeshEligible(
					model, renderType, textureIdentity, overlay, outlineColor, crumblingOverlay)) {
				return;
			}
			this.submitModel(model, state, poseStack, renderType, light, overlay, color, null, outlineColor, crumblingOverlay);
		}

		@Override
		public <S> void submitModel(net.minecraft.client.model.Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, int light, int overlay, int color, @Nullable net.minecraft.client.renderer.texture.TextureAtlasSprite sprite, int outlineColor, @Nullable net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
			if (model instanceof net.minecraft.client.model.ArrowModel
				&& state instanceof net.minecraft.client.renderer.entity.state.ArrowRenderState arrowState
				&& net.vulkanic.world.RustGalWorldPrimitiveRenderer.isVanillaArrowStateEligible(arrowState)) {
				return;
			}
			String ineligibility = net.vulkanic.world.RustGalWorldPrimitiveRenderer.modelMeshIneligibilityReason(
				model, renderType, sprite, overlay, outlineColor, crumblingOverlay
			);
			if (isSelectedWholeFrameModelSemantic(model, state, sprite)
				&& (sprite == null || net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasCurrentFrameRustModelMeshDecision(model, sprite))) {
				return;
			}
			this.modelSubmits++;
			this.recordDiagnosticSample(
				"model",
				model == null ? "null" : model.getClass().getName(),
				state == null ? "null" : state.getClass().getName(),
				(renderType == null ? "null" : renderType.toString())
					+ ";eligibility=" + (ineligibility == null ? "rust-route-unavailable" : ineligibility)
			);
		}

		private void recordDiagnosticSample(String family, String model, String state, String renderType) {
			if (this.diagnosticSamples.size() >= MAX_DIAGNOSTIC_SAMPLES) {
				return;
			}
			this.diagnosticSamples.add(
				"family=" + family
					+ ",model=" + model.replace(' ', '_')
					+ ",state=" + state.replace(' ', '_')
					+ ",render_type=" + renderType.replace(' ', '_')
			);
		}

		List<String> diagnosticSamples() {
			return List.copyOf(this.diagnosticSamples);
		}

		@Override
		public void submitModelPart(net.minecraft.client.model.geom.ModelPart modelPart, PoseStack poseStack, RenderType renderType, int light, int overlay, @Nullable net.minecraft.client.renderer.texture.TextureAtlasSprite sprite, boolean sheeted, boolean foil, int outlineColor, @Nullable net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumblingOverlay, int tint) {
			if (net.vulkanic.world.RustGalWorldPrimitiveRenderer.isModelPartMeshEligible(
				modelPart, renderType, sprite, overlay, sheeted, foil, outlineColor, crumblingOverlay
			)) {
				return;
			}
			this.modelPartSubmits++;
		}

		@Override
		public void submitBlock(PoseStack poseStack, BlockState blockState, int light, int overlay, int outlineColor) {
			boolean rustAdmitted = (blockState != null)
				&& blockState.getRenderShape() == net.minecraft.world.level.block.RenderShape.MODEL
				&& (net.minecraft.client.renderer.ItemBlockRenderTypes.getChunkRenderType(blockState)
					== net.minecraft.client.renderer.chunk.ChunkSectionLayer.SOLID
					|| net.minecraft.client.renderer.ItemBlockRenderTypes.getChunkRenderType(blockState)
					== net.minecraft.client.renderer.chunk.ChunkSectionLayer.CUTOUT
					|| net.minecraft.client.renderer.ItemBlockRenderTypes.getChunkRenderType(blockState)
					== net.minecraft.client.renderer.chunk.ChunkSectionLayer.CUTOUT_MIPPED);
			if (!rustAdmitted) {
				this.ordinaryBlockSubmits++;
			}
		}

		@Override
		public void submitMovingBlock(PoseStack poseStack, net.minecraft.client.renderer.block.MovingBlockRenderState movingBlockRenderState) {
			BlockState state = movingBlockRenderState == null ? null : movingBlockRenderState.blockState;
			net.minecraft.client.renderer.RenderType movingType = state == null ? null
				: net.minecraft.client.renderer.ItemBlockRenderTypes.getMovingBlockRenderType(state);
			boolean rustAdmitted = (state != null
				&& state.getRenderShape() == net.minecraft.world.level.block.RenderShape.MODEL
				&& !net.minecraft.client.Minecraft.getInstance().getModelManager().specialBlockModelRenderer().get().hasRenderer(state.getBlock()))
				&& (movingType == net.minecraft.client.renderer.RenderType.solid()
					|| movingType == net.minecraft.client.renderer.RenderType.cutout()
					|| movingType == net.minecraft.client.renderer.RenderType.cutoutMipped());
			if (!rustAdmitted) {
				this.ordinaryBlockSubmits++;
			}
		}

		@Override
		public void submitBlockModel(PoseStack poseStack, RenderType renderType, net.minecraft.client.renderer.block.model.BlockStateModel blockStateModel, float red, float green, float blue, int light, int overlay, int outlineColor) {
			if (net.vulkanic.world.RustGalWorldPrimitiveRenderer.isBlockModelMeshSemanticallyEligible(blockStateModel, renderType, overlay)) {
				return;
			}
			this.blockModelSubmits++;
		}

		@Override
		public void submitItem(PoseStack poseStack, net.minecraft.world.item.ItemDisplayContext displayContext, int light, int overlay, int outlineColor, int[] tintLayers, List<net.minecraft.client.renderer.block.model.BakedQuad> quads, RenderType renderType, net.minecraft.client.renderer.item.ItemStackRenderState.FoilType foilType) {
			String ineligibility = net.vulkanic.world.RustGalWorldPrimitiveRenderer.itemEntityMeshIneligibility(
				displayContext, light, overlay, outlineColor, tintLayers, quads, renderType, foilType
			);
			boolean eligible = ineligibility == null;
			boolean firstPersonSemantic = displayContext != null && displayContext.firstPerson();
			if ((!eligible && !firstPersonSemantic)) {
				this.itemSubmits++;
			}
		}

		@Override
		public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, SubmitNodeCollector.CustomGeometryRenderer renderer) {
			// These exact callback families are copied into Rust semantic streams by
			// the real dispatcher. Do not count them as missing coverage during the
			// pre-dispatch audit; arbitrary callback types remain fail-closed below.
			if (net.minecraft.client.renderer.SubmitNodeCollection.isRustLineGeometryRenderType(renderType)) {
				return;
			}
			if (net.minecraft.client.renderer.SubmitNodeCollection.isRustProceduralQuadGeometryRenderType(renderType)) {
				return;
			}
			this.customGeometrySubmits++;
		}

		@Override
		public boolean submitGuardianBeam(PoseStack poseStack, RenderType renderType, net.minecraft.resources.ResourceLocation textureIdentity, float[] vertices, float[] uvs, int[] colors, int lightCoords) {
			return true;
		}

		@Override
		public boolean submitCrystalBeam(PoseStack poseStack, RenderType renderType, net.minecraft.resources.ResourceLocation textureIdentity, float[] vertices, float[] uvs, int[] colors, int lightCoords) {
			return true;
		}

		@Override
		public boolean submitTexturedQuad(PoseStack poseStack, RenderType renderType, net.minecraft.resources.ResourceLocation textureIdentity, float[] vertices, float[] uvs, int color, int lightCoords) {
			return net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueTexturedQuad(
				poseStack.last().pose(), textureIdentity, vertices, uvs, color, lightCoords);
		}

		@Override
		public boolean submitTranslucentTexturedQuad(PoseStack poseStack, RenderType renderType, net.minecraft.resources.ResourceLocation textureIdentity, float[] vertices, float[] uvs, int color, int lightCoords) {
			return net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueTranslucentTexturedQuad(
				poseStack.last().pose(), textureIdentity, vertices, uvs, color, lightCoords);
		}

		@Override
		public boolean submitTexturedQuads(PoseStack poseStack, RenderType renderType, net.minecraft.resources.ResourceLocation textureIdentity, float[] vertices, float[] uvs, int[] colors, int lightCoords) {
			return true;
		}

		@Override
		public boolean submitLineSegments(PoseStack poseStack, float[] endpoints, int color, float lineWidth) {
			return true;
		}

		@Override
		public boolean submitColoredQuads(PoseStack poseStack, RenderType renderType, float[] vertices, float[] uvs, int[] colors, int lightCoords) {
			return true;
		}

		@Override
		public void submitParticleGroup(SubmitNodeCollector.ParticleGroupRenderer renderer) {
			boolean rustAdmitted = renderer instanceof net.minecraft.client.renderer.state.QuadParticleRenderState quad
				&& quad.rustGalUnsupportedLayerCount() == 0;
			if (!rustAdmitted) {
				this.particleGroupSubmits++;
			}
		}

		SubmitNodeCollection.WorldFeatureCoverageSnapshot snapshot() {
			return new SubmitNodeCollection.WorldFeatureCoverageSnapshot(
				this.modelSubmits, this.modelPartSubmits, this.blockModelSubmits, this.ordinaryBlockSubmits,
				this.itemSubmits, this.customGeometrySubmits, this.shadowSubmits, this.flameSubmits,
				this.nameTagSubmits, this.textSubmits, this.hitboxSubmits, this.leashSubmits,
				this.particleGroupSubmits
			);
		}
	}

	public void enqueueRustGalStaticTerrainForWholeFrame(Camera camera, Matrix4f viewMatrix, Matrix4f projectionMatrix,
			FogParameters fogParameters, boolean useFogOcclusion) {
		if (this.level == null || this.minecraft.player == null) {
			return;
		}
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.static-terrain.cull");
		Vec3 cameraPos = camera.getPosition();
		// Keep the CPU terrain source on vanilla's model-view/projection frustum
		// contract. Frustum internally computes projection * model-view.
		Frustum frustum = this.prepareCullFrustum(viewMatrix, projectionMatrix, cameraPos);
		this.cullTerrain(camera, frustum, this.minecraft.player.isSpectator());
		net.sodium.client.render.StaticTerrainParityDiagnostics.recordTransformProbe(
			"rust-vulkan-whole-frame-enqueue",
			"solid",
			cameraPos.x(), cameraPos.y(), cameraPos.z(),
			camera.getYRot(), camera.getXRot(),
			viewMatrix, projectionMatrix,
			this.minecraft.getWindow().getWidth(), this.minecraft.getWindow().getHeight(),
			true
		);
		net.sodium.client.render.StaticTerrainParityDiagnostics.recordTransformProbe(
			"rust-vulkan-whole-frame-enqueue",
			"cutout",
			cameraPos.x(), cameraPos.y(), cameraPos.z(),
			camera.getYRot(), camera.getXRot(),
			viewMatrix, projectionMatrix,
			this.minecraft.getWindow().getWidth(), this.minecraft.getWindow().getHeight(),
			true
		);
		net.sodium.client.render.StaticTerrainParityDiagnostics.recordAppearanceSourceProbe(
			"rust-vulkan-whole-frame-enqueue", "solid"
		);
		net.sodium.client.render.StaticTerrainParityDiagnostics.recordAppearanceSourceProbe(
			"rust-vulkan-whole-frame-enqueue", "cutout"
		);
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.static-terrain.cull");
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.static-terrain.visible-submit");
		boolean auditDisableDhFogOcclusion = Boolean.parseBoolean(
			System.getenv().getOrDefault("MATTMC_RUST_DH_DISABLE_VANILLA_FOG_OCCLUSION", "false"))
			&& Boolean.parseBoolean(System.getenv().getOrDefault("MATTMC_GRAPHICS_AUDIT", "false"));
		boolean effectiveFogOcclusion = useFogOcclusion
			&& !(auditDisableDhFogOcclusion);
		this.rustGalWholeFrameTerrainSource.enqueue(
			camera,
			frustum,
			this.minecraft.getWindow().getWidth(),
			this.minecraft.getWindow().getHeight(),
			RustGalWholeFrameTerrainSource.terrainSelectionDistance(
				this.minecraft.options.getEffectiveRenderDistance() * 16.0f,
				fogParameters == null ? 0.0f : fogParameters.alpha(),
				fogParameters == null ? 0.0f : fogParameters.renderEnd(),
				effectiveFogOcclusion
			)
		);
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.static-terrain.visible-submit");
	}

	/**
	 * Collects vanilla's weather render state for the Rust whole-frame route.
	 * This deliberately shares the normal extraction implementation and only
	 * contributes semantic rain/snow columns to the current Rust frame.
	 */
	public void enqueueRustGalWeatherForWholeFrame(Camera camera, float partialTick) {
		if ((this.level == null) || camera == null) {
			return;
		}
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.weather.extract");
		Vec3 cameraPos = camera.getPosition();
		int weatherRendererTicks = net.minecraft.client.dev.DeterministicCameraCapture.weatherRendererTicksForCapture(this.ticks);
		float weatherPartialTick = net.minecraft.client.dev.DeterministicCameraCapture.weatherPartialTickForCapture(partialTick);
		// The normal LevelRenderer extraction resets this reusable state as part
		// of LevelRenderState.reset(). The whole-frame shell bypasses that path,
		// so reset only the weather-owned semantic state before rebuilding it.
		this.levelRenderState.weatherRenderState.reset();
		this.weatherEffectRenderer.extractRenderState(
			this.level,
			weatherRendererTicks,
			weatherPartialTick,
			cameraPos,
			this.levelRenderState.weatherRenderState
		);
		net.minecraft.client.dev.DeterministicCameraCapture.recordWeatherSemanticFingerprint(
			WeatherEffectRenderer.weatherSemanticFingerprint(this.levelRenderState.weatherRenderState, cameraPos)
		);
		net.minecraft.client.dev.DeterministicCameraCapture.recordWeatherRendererTicks(weatherRendererTicks);
		net.minecraft.client.dev.DeterministicCameraCapture.recordWeatherRendererPartialTick(weatherPartialTick);
		net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueWorldWeather(
			this.levelRenderState.weatherRenderState,
			cameraPos,
			Minecraft.useShaderTransparency()
		);
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.weather.extract");
	}

	/**
	 * Collects the same vanilla sky state used by the normal level renderer for
	 * the Rust-owned whole-frame route. This is semantic extraction only: the
	 * Rust shader runtime selects and executes any source-defined sky writer.
	 */
	public void enqueueRustGalSkyForWholeFrame(Camera camera, float partialTick) {
		if ((this.level == null) || camera == null) {
			return;
		}
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.sky.extract");
		Vec3 cameraPos = camera.getPosition();
		this.skyRenderer.extractRenderState(this.level, partialTick, cameraPos, this.levelRenderState.skyRenderState);
		FogType fogType = camera.getFluidInCamera();
		net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueWorldSky(
			this.levelRenderState.skyRenderState,
			fogType != FogType.POWDER_SNOW && fogType != FogType.LAVA && !this.doesMobEffectBlockSky(camera),
			camera
		);
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.sky.extract");
	}

	/** Collects copied vanilla cloud-face semantics for the Rust whole-frame route. */
	public void enqueueRustGalCloudsForWholeFrame(Camera camera, float partialTick) {
		if ((this.level == null) || camera == null) {
			return;
		}
		if (net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasDistantHorizonsPrivateClouds()) {
			return;
		}
		CloudStatus cloudStatus = this.minecraft.options.getCloudsType();
		Optional<Integer> cloudHeight = this.level.dimensionType().cloudHeight();
		if (cloudStatus == CloudStatus.OFF || cloudHeight.isEmpty()) {
			return;
		}
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.clouds.extract");
		this.cloudRenderer.enqueueRustGalClouds(
			this.level.getCloudColor(partialTick),
			cloudStatus,
			cloudHeight.get().intValue() + 0.33F,
			camera.getPosition(),
			net.minecraft.client.dev.DeterministicCameraCapture.cloudTimeForCapture(this.ticks + partialTick)
		);
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.clouds.extract");
	}

	public void enqueueRustGalBlockDisplaysForWholeFrame(Camera camera, DeltaTracker deltaTracker, Matrix4f viewMatrix, Matrix4f projectionMatrix) {
		this.enqueueRustGalIndexedMeshFeaturesForWholeFrame(camera, deltaTracker, viewMatrix, projectionMatrix);
	}

	public void extractVisibleBlockEntities(Camera camera, float f, LevelRenderState levelRenderState) { // Made public for Iris shadow rendering
		LongOpenHashSet extractedBlockEntityPositions = this.rustExtractedBlockEntityPositions;
		extractedBlockEntityPositions.clear();
		PoseStack poseStack = new PoseStack();
		for (SectionRenderDispatcher.RenderSection section : this.visibleSections) {
			if (!(section.getSectionMesh() instanceof CompiledSectionMesh compiled)) continue;
			for (BlockEntity blockEntity : compiled.getRenderableBlockEntities()) {
				this.extractWholeFrameBlockEntity(blockEntity, poseStack, camera, f, levelRenderState);
				extractedBlockEntityPositions.add(blockEntity.getBlockPos().asLong());
			}
		}
		// Java's terrain compiler historically supplied the only block-entity
		// visibility list. Rust semantic block-entity producers are independent
		// of that GPU-oriented readiness state, so a newly placed or still
		// compiling block entity must reach the real dispatcher as soon as its
		// loaded chunk is available. This is a bounded CPU scan over the active
		// render-distance window; renderer shouldRender() and Rust route admission
		// remain the final semantic filters. It never invokes a Java draw or
		// retains a renderer/native handle.
		int centerChunkX = SectionPos.blockToSectionCoord(Mth.floor(camera.getPosition().x));
		int centerChunkZ = SectionPos.blockToSectionCoord(Mth.floor(camera.getPosition().z));
		int radius = this.minecraft.options.getEffectiveRenderDistance() + 1;
		// Each block entity belongs to one chunk, so the scan can only repeat
		// positions the section pass already extracted.
		boolean deduplicate = !extractedBlockEntityPositions.isEmpty();
		for (int chunkZ = centerChunkZ - radius; chunkZ <= centerChunkZ + radius; chunkZ++) {
			for (int chunkX = centerChunkX - radius; chunkX <= centerChunkX + radius; chunkX++) {
				LevelChunk chunk = this.level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
				if (chunk == null) continue;
				for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
					if (deduplicate && extractedBlockEntityPositions.contains(blockEntity.getBlockPos().asLong())) continue;
					this.extractWholeFrameBlockEntity(blockEntity, poseStack, camera, f, levelRenderState);
				}
			}
		}
		return;

	}

	private void extractWholeFrameBlockEntity(BlockEntity blockEntity, PoseStack poseStack, Camera camera, float partialTick, LevelRenderState levelRenderState) {
		BlockPos blockPos = blockEntity.getBlockPos();
		SortedSet<BlockDestructionProgress> progress = this.destructionProgress.get(blockPos.asLong());
		net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumbling = null;
		if (progress != null && !progress.isEmpty()) {
			poseStack.pushPose();
			poseStack.translate(blockPos.getX() - camera.position().x, blockPos.getY() - camera.position().y, blockPos.getZ() - camera.position().z);
			crumbling = new net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay(progress.last().getProgress(), poseStack.last());
			poseStack.popPose();
		}
		BlockEntityRenderState state = this.blockEntityRenderDispatcher.tryExtractRenderState(blockEntity, partialTick, crumbling);
		if (state != null) levelRenderState.blockEntityRenderStates.add(state);
	}

	private void submitPistonMovingBlocksForWholeFrame(PoseStack poseStack, LevelRenderState levelRenderState, SubmitNodeStorage submitNodeStorage) {
		Vec3 vec3 = levelRenderState.cameraRenderState.pos;
		double d = vec3.x();
		double e = vec3.y();
		double f = vec3.z();

		for (BlockEntityRenderState blockEntityRenderState : levelRenderState.blockEntityRenderStates) {
			if (!(blockEntityRenderState instanceof net.minecraft.client.renderer.blockentity.state.PistonHeadRenderState)) {
				continue;
			}
			BlockPos blockPos = blockEntityRenderState.blockPos;
			poseStack.pushPose();
			poseStack.translate(blockPos.getX() - d, blockPos.getY() - e, blockPos.getZ() - f);
			this.blockEntityRenderDispatcher.submitSemantic(blockEntityRenderState, poseStack, submitNodeStorage, levelRenderState.cameraRenderState);
			poseStack.popPose();
		}
	}

	private void ensurePistonMovingBlocksPresentForWholeFrame(Camera camera, float partialTick) {
		long startedNanos = System.nanoTime();
		// The normal level extraction has already populated this shared semantic
		// frame. Re-extracting here used to replace it for the piston shell,
		// which made ordinary block-entity coverage invisible and could append
		// duplicate visible states. Only the bounded fallback scan may add a
		// piston state that the normal visibility extraction did not include.
		int visiblePistonStates = 0;
		for (BlockEntityRenderState blockEntityRenderState : this.levelRenderState.blockEntityRenderStates) {
			if (blockEntityRenderState instanceof PistonHeadRenderState) {
				visiblePistonStates++;
			}
		}
		if (visiblePistonStates > 0) {
			net.vulkanic.world.RustGalWorldPrimitiveRenderer.recordMovingBlockShellScan(
				"rust-vulkan-whole-frame",
				visiblePistonStates,
				false,
				0,
				0,
				visiblePistonStates,
				visiblePistonStates,
				System.nanoTime() - startedNanos
			);
			return;
		}

		Vec3 cameraPos = camera.getPosition();
		int centerChunkX = SectionPos.blockToSectionCoord(Mth.floor(cameraPos.x()));
		int centerChunkZ = SectionPos.blockToSectionCoord(Mth.floor(cameraPos.z()));
		int radius = this.minecraft.options.getEffectiveRenderDistance() + 1;
		int chunksScanned = 0;
		int blockEntitiesInspected = 0;
		int pistonEntitiesFound = 0;
		int pistonStatesExtracted = 0;
		for (int chunkZ = centerChunkZ - radius; chunkZ <= centerChunkZ + radius; chunkZ++) {
			for (int chunkX = centerChunkX - radius; chunkX <= centerChunkX + radius; chunkX++) {
				LevelChunk chunk = this.level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
				if (chunk == null) {
					continue;
				}
				chunksScanned++;
				for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
					blockEntitiesInspected++;
					if (!(blockEntity instanceof PistonMovingBlockEntity pistonMovingBlockEntity)) {
						continue;
					}
					pistonEntitiesFound++;
					PistonHeadRenderState renderState = this.blockEntityRenderDispatcher.tryExtractRenderState(
						pistonMovingBlockEntity,
						partialTick,
						null
					);
					if (renderState != null) {
						this.levelRenderState.blockEntityRenderStates.add(renderState);
						pistonStatesExtracted++;
					}
				}
			}
		}
		net.vulkanic.world.RustGalWorldPrimitiveRenderer.recordMovingBlockShellScan(
			"rust-vulkan-whole-frame",
			visiblePistonStates,
			true,
			chunksScanned,
			blockEntitiesInspected,
			pistonEntitiesFound,
			pistonStatesExtracted,
			System.nanoTime() - startedNanos
		);
	}

	public void enqueueRustGalBlockBreakingCracks(Camera camera) {
		this.collectBlockDestroyAnimation(camera, this.levelRenderState);
		net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueBlockBreakingCracks(this.levelRenderState.blockBreakingRenderStates, camera);
	}

	public void enqueueRustGalWorldBorder(Camera camera) {
		if (this.level == null) {
			return;
		}
		Vec3 vec3 = camera.getPosition();
		int renderDistance = this.minecraft.options.getEffectiveRenderDistance() * 16;
		float depthFar = this.minecraft.gameRenderer.getDepthFar();
		this.worldBorderRenderer.extract(this.level.getWorldBorder(), vec3, renderDistance, this.levelRenderState.worldBorderRenderState);
		boolean accepted = net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueWorldBorder(
			this.levelRenderState.worldBorderRenderState, vec3, renderDistance, depthFar
		);
		if (!accepted
			&& this.levelRenderState.worldBorderRenderState.alpha > 0.0) {
			throw new IllegalStateException(
				"Rust whole-frame world-border route rejected visible semantic work; Java Vulkan fallback is unavailable"
			);
		}
	}

	private void collectBlockDestroyAnimation(Camera camera, LevelRenderState levelRenderState) {
		Vec3 vec3 = camera.getPosition();
		double d = vec3.x();
		double e = vec3.y();
		double f = vec3.z();
		levelRenderState.blockBreakingRenderStates.clear();

		for (Entry<SortedSet<BlockDestructionProgress>> entry : this.destructionProgress.long2ObjectEntrySet()) {
			BlockPos blockPos = BlockPos.of(entry.getLongKey());
			if (!(blockPos.distToCenterSqr(d, e, f) > 1024.0)) {
				SortedSet<BlockDestructionProgress> sortedSet = (SortedSet<BlockDestructionProgress>)entry.getValue();
				if (sortedSet != null && !sortedSet.isEmpty()) {
					int i = ((BlockDestructionProgress)sortedSet.last()).getProgress();
					levelRenderState.blockBreakingRenderStates.add(new BlockBreakingRenderState(this.level, blockPos, i));
				}
			}
		}
	}

	@Nullable
	private BlockOutlineFramebufferProbe createBlockOutlineFramebufferProbe(
		BlockOutlineRenderState blockOutlineRenderState,
		PoseStack poseStack,
		Vec3 cameraPos,
		boolean translucentPass
	) {
		return null;
	}

	@Nullable
	private BlockCrackFramebufferProbe createBlockCrackFramebufferProbe(List<BlockBreakingRenderState> states) {
		return null;
	}

	@Nullable
	private FramebufferProbePoint projectBlockProbePoint(
		Matrix4fc modelView,
		Matrix4fc projection,
		int width,
		int height,
		FramebufferProbeCrop crop,
		double x,
		double y,
		double z
	) {
		Vector4f clip = new Vector4f((float)x, (float)y, (float)z, 1.0F).mul(modelView).mul(projection);
		if (!Float.isFinite(clip.x()) || !Float.isFinite(clip.y()) || !Float.isFinite(clip.w()) || Math.abs(clip.w()) < 1.0E-5F) {
			return null;
		}
		int screenX = Math.round((clip.x() / clip.w() * 0.5F + 0.5F) * width);
		int screenY = Math.round((clip.y() / clip.w() * 0.5F + 0.5F) * height);
		if (screenX < crop.x() || screenY < crop.y() || screenX >= crop.x() + crop.width() || screenY >= crop.y() + crop.height()) {
			return null;
		}
		float depth = Mth.clamp(clip.z() / clip.w() * 0.5F + 0.5F, 0.0F, 1.0F);
		return new FramebufferProbePoint(screenX - crop.x(), screenY - crop.y(), depth);
	}

	private static final class ProjectedBounds {
		private float minX = Float.POSITIVE_INFINITY;
		private float minY = Float.POSITIVE_INFINITY;
		private float maxX = Float.NEGATIVE_INFINITY;
		private float maxY = Float.NEGATIVE_INFINITY;

		void include(float x, float y) {
			this.minX = Math.min(this.minX, x);
			this.minY = Math.min(this.minY, y);
			this.maxX = Math.max(this.maxX, x);
			this.maxY = Math.max(this.maxY, y);
		}

		boolean valid() {
			return Float.isFinite(this.minX) && Float.isFinite(this.minY) && Float.isFinite(this.maxX) && Float.isFinite(this.maxY);
		}
	}

	private record FramebufferProbeCrop(int x, int y, int width, int height, String source) {
		String describe() {
			return this.x + "," + this.y + "," + this.width + "x" + this.height + ":" + this.source;
		}
	}

	private record BlockOutlineFramebufferProbe(
		String blockPos,
		boolean highContrast,
		boolean translucentPass,
		FramebufferProbeCrop crop,
		ProjectedOutlineEdgeSamples edgeSamples,
		FramebufferProbeSample beforeDraw,
		@Nullable FramebufferProbeSample afterDraw
	) {
		BlockOutlineFramebufferProbe(String blockPos, boolean highContrast, boolean translucentPass, FramebufferProbeCrop crop, ProjectedOutlineEdgeSamples edgeSamples, FramebufferProbeSample beforeDraw) {
			this(blockPos, highContrast, translucentPass, crop, edgeSamples, beforeDraw, null);
		}

	}

	private record BlockCrackFramebufferProbe(
		String blockPos,
		int stageIndex,
		int faceCount,
		FramebufferProbeCrop crop,
		FramebufferProbeSample beforeDraw,
		@Nullable FramebufferProbeSample afterDraw
	) {
		BlockCrackFramebufferProbe(String blockPos, int stageIndex, int faceCount, FramebufferProbeCrop crop, FramebufferProbeSample beforeDraw) {
			this(blockPos, stageIndex, faceCount, crop, beforeDraw, null);
		}

	}

		private record FramebufferProbeSample(
			byte[] rgba,
			float[] depth,
			int readFramebuffer,
		int drawFramebuffer,
		int currentProgram,
		String viewport,
		boolean depthTest,
		boolean blend,
		boolean scissor,
		int sampleWidth,
		int sampleHeight
	) {

			int changedProbePointsFrom(FramebufferProbeSample before, List<FramebufferProbePoint> points) {
				int changed = 0;
				int width = Math.max(1, this.sampleWidth);
			for (FramebufferProbePoint point : points) {
				int index = (point.y() * width + point.x()) * 4;
				if (index < 0 || index + 3 >= this.rgba.length || index + 3 >= before.rgba.length) {
					continue;
				}
				int delta = 0;
				for (int channel = 0; channel < 4; channel++) {
					delta = Math.max(delta, Math.abs((this.rgba[index + channel] & 0xFF) - (before.rgba[index + channel] & 0xFF)));
				}
				if (delta >= 16) {
					changed++;
				}
				}
				return changed;
			}

		}

	private record FramebufferProbeDelta(int changedPixels, int maxChannelDelta, long sumChannelDelta) {
	}

	private record FramebufferProbePoint(int x, int y, float depth) {
	}

	private record ProjectedOutlineEdgeSamples(List<FramebufferProbePoint> visible, List<FramebufferProbePoint> hidden) {
		String summary(FramebufferProbeSample sample, FramebufferProbeSample before) {
			return "visibleTotal=" + this.visible.size()
				+ ",visibleChanged=" + sample.changedProbePointsFrom(before, this.visible)
				+ ",hiddenTotal=" + this.hidden.size()
				+ ",hiddenChanged=" + sample.changedProbePointsFrom(before, this.hidden);
		}
	}

	private EntityRenderState extractEntity(Entity entity, float f) {
		return this.entityRenderDispatcher.extractEntity(entity, f);
	}

	public void endFrame() {
		return;
	}

	public void captureFrustum() {
		this.captureFrustum = true;
	}

	public void killFrustum() {
		this.capturedFrustum = null;
	}

	public void tick(Camera camera) {
		if (this.level.tickRateManager().runsNormally()) {
			this.ticks++;
		}

		this.weatherEffectRenderer.tickRainParticles(this.level, camera, this.ticks, this.minecraft.options.particles().get());
		this.removeBlockBreakingProgress();
	}

	private void removeBlockBreakingProgress() {
		if (this.ticks % 20 == 0) {
			Iterator<BlockDestructionProgress> iterator = this.destroyingBlocks.values().iterator();

			while (iterator.hasNext()) {
				BlockDestructionProgress blockDestructionProgress = (BlockDestructionProgress)iterator.next();
				int i = blockDestructionProgress.getUpdatedRenderTick();
				if (this.ticks - i > 400) {
					iterator.remove();
					this.removeProgress(blockDestructionProgress);
				}
			}
		}
	}

	private void removeProgress(BlockDestructionProgress blockDestructionProgress) {
		long l = blockDestructionProgress.getPos().asLong();
		Set<BlockDestructionProgress> set = (Set<BlockDestructionProgress>)this.destructionProgress.get(l);
		set.remove(blockDestructionProgress);
		if (set.isEmpty()) {
			this.destructionProgress.remove(l);
		}
	}

	public boolean doesMobEffectBlockSky(Camera camera) { // Made public for Iris sky rendering
		return !(camera.getEntity() instanceof LivingEntity livingEntity)
			? false
			: livingEntity.hasEffect(MobEffects.BLINDNESS) || livingEntity.hasEffect(MobEffects.DARKNESS);
	}

	public void blockChanged(BlockGetter blockGetter, BlockPos blockPos, BlockState blockState, BlockState blockState2, int i) {
		this.setBlockDirty(blockPos, (i & 8) != 0);
	}

	private void setBlockDirty(BlockPos blockPos, boolean bl) {
		for (int i = blockPos.getZ() - 1; i <= blockPos.getZ() + 1; i++) {
			for (int j = blockPos.getX() - 1; j <= blockPos.getX() + 1; j++) {
				for (int k = blockPos.getY() - 1; k <= blockPos.getY() + 1; k++) {
					this.setSectionDirty(SectionPos.blockToSectionCoord(j), SectionPos.blockToSectionCoord(k), SectionPos.blockToSectionCoord(i), bl);
				}
			}
		}
	}

	public void setBlocksDirty(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
		this.setSectionRangeDirty(
			SectionPos.blockToSectionCoord(minX), SectionPos.blockToSectionCoord(minY), SectionPos.blockToSectionCoord(minZ),
			SectionPos.blockToSectionCoord(maxX), SectionPos.blockToSectionCoord(maxY), SectionPos.blockToSectionCoord(maxZ)
		);
		return;

	}

	public void setBlockDirty(BlockPos blockPos, BlockState blockState, BlockState blockState2) {
		if (this.minecraft.getModelManager().requiresRender(blockState, blockState2)) {
			this.setBlockDirty(blockPos, true);
			return;

		}
	}

	public void setSectionDirtyWithNeighbors(int x, int y, int z) {
		this.setSectionRangeDirty(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1);
		return;

	}

	public void setSectionRangeDirty(int i, int j, int k, int l, int m, int n) {
		for (int o = k; o <= n; o++) {
			for (int p = i; p <= l; p++) {
				for (int q = j; q <= m; q++) {
					this.setSectionDirty(p, q, o);
				}
			}
		}
	}

	public void setSectionDirty(int i, int j, int k) {
		this.setSectionDirty(i, j, k, false);
	}

	private void setSectionDirty(int x, int y, int z, boolean important) {
		if (this.viewArea != null) this.viewArea.setDirty(x, y, z, important);
		this.rustGalWholeFrameTerrainSource.invalidate(x, y, z);
		return;

	}

	public void onSectionBecomingNonEmpty(long l) {
		// The independent source may have completed this section as empty
		// before the client chunk packet populated it. Invalidate that immutable
		// CPU snapshot through the normal replacement transaction; otherwise
		// the all-open placeholder remains cached forever and the real terrain
		// is never meshed. The last accepted non-empty generation, when one
		// exists, stays published until its replacement is ready.
		this.rustGalWholeFrameTerrainSource.onSectionBecomingNonEmpty(
			SectionPos.x(l), SectionPos.y(l), SectionPos.z(l));
		return;
	}

	public void destroyBlockProgress(int i, BlockPos blockPos, int j) {
		if (j >= 0 && j < 10) {
			BlockDestructionProgress blockDestructionProgress = this.destroyingBlocks.get(i);
			if (blockDestructionProgress != null) {
				this.removeProgress(blockDestructionProgress);
			}

			if (blockDestructionProgress == null
				|| blockDestructionProgress.getPos().getX() != blockPos.getX()
				|| blockDestructionProgress.getPos().getY() != blockPos.getY()
				|| blockDestructionProgress.getPos().getZ() != blockPos.getZ()) {
				blockDestructionProgress = new BlockDestructionProgress(i, blockPos);
				this.destroyingBlocks.put(i, blockDestructionProgress);
			}

			blockDestructionProgress.setProgress(j);
			blockDestructionProgress.updateTick(this.ticks);
			this.destructionProgress
				.computeIfAbsent(blockDestructionProgress.getPos().asLong(), (Long2ObjectFunction<? extends SortedSet<BlockDestructionProgress>>)(l -> Sets.newTreeSet()))
				.add(blockDestructionProgress);
		} else {
			BlockDestructionProgress blockDestructionProgressx = this.destroyingBlocks.remove(i);
			if (blockDestructionProgressx != null) {
				this.removeProgress(blockDestructionProgressx);
			}
		}
	}

	public boolean hasRenderedAllSections() {
		return false;
	}

	public void onChunkReadyToRender(ChunkPos chunkPos) {
		this.sectionOcclusionGraph.onChunkReadyToRender(chunkPos);
	}

	public void needsUpdate() {
		this.sectionOcclusionGraph.invalidate();
		this.cloudRenderer.markForRebuild();
		// Rust owns terrain scheduling for the whole-frame route. Keep the CPU
		// invalidation above, but never reopen Sodium's Java terrain scheduler.
		return;
	}

	public static int getLightColor(BlockAndTintGetter blockAndTintGetter, BlockPos blockPos) {
		return getLightColor(LevelRenderer.BrightnessGetter.DEFAULT, blockAndTintGetter, blockAndTintGetter.getBlockState(blockPos), blockPos);
	}

	public static int getLightColor(
		LevelRenderer.BrightnessGetter brightnessGetter, BlockAndTintGetter blockAndTintGetter, BlockState blockState, BlockPos blockPos
	) {
		if (blockState.emissiveRendering(blockAndTintGetter, blockPos)) {
			return 15728880;
		} else {
			int i = brightnessGetter.packedBrightness(blockAndTintGetter, blockPos);
			int j = LightTexture.block(i);
			int k = blockState.getLightEmission();
			if (j < k) {
				int l = LightTexture.sky(i);
				return LightTexture.pack(k, l);
			} else {
				return i;
			}
		}
	}

	public boolean isSectionCompiled(BlockPos blockPos) {
		if (this.rustGalWholeFrameTerrainSource.isSectionReady(blockPos)) {
			return true;
		}
		// The loading screen prevents the world render loop from running, so the
		// semantic producer cannot build its first section until that screen has
		// closed. Use the already-loaded immutable chunk snapshot only as the
		// bootstrap gate; the Rust source remains responsible for producing the
		// actual section data before any world draw is admitted.
		if (this.level != null && this.level.hasChunk(
			blockPos.getX() >> 4, blockPos.getZ() >> 4)) {
			return true;
		}
		SectionRenderDispatcher.RenderSection section = this.viewArea == null ? null : this.viewArea.getRenderSectionAt(blockPos);
		return section != null && section.getSectionMesh() != CompiledSectionMesh.UNCOMPILED;

	}

	@Nullable
	public RenderTarget entityOutlineTarget() {
		return this.targets.entityOutline != null ? this.targets.entityOutline.get() : null;
	}

	@Nullable
	public RenderTarget getTranslucentTarget() {
		return this.targets.translucent != null ? this.targets.translucent.get() : null;
	}

	@Nullable
	public RenderTarget getItemEntityTarget() {
		return this.targets.itemEntity != null ? this.targets.itemEntity.get() : null;
	}

	@Nullable
	public RenderTarget getParticlesTarget() {
		return this.targets.particles != null ? this.targets.particles.get() : null;
	}

	@Nullable
	public RenderTarget getWeatherTarget() {
		return this.targets.weather != null ? this.targets.weather.get() : null;
	}

	@Nullable
	public RenderTarget getCloudsTarget() {
		return this.targets.clouds != null ? this.targets.clouds.get() : null;
	}

	@VisibleForDebug
	public ObjectArrayList<SectionRenderDispatcher.RenderSection> getVisibleSections() {
		return this.visibleSections;
	}

	@VisibleForDebug
	public SectionOcclusionGraph getSectionOcclusionGraph() {
		return this.sectionOcclusionGraph;
	}

	@Nullable
	public Frustum getCapturedFrustum() {
		return this.capturedFrustum;
	}

	public CloudRenderer getCloudRenderer() {
		return this.cloudRenderer;
	}

	public WorldBorderRenderer getWorldBorderRenderer() {
		return this.worldBorderRenderer;
	}

	public SkyRenderer getSkyRenderer() {
		return this.skyRenderer;
	}

	@FunctionalInterface
	@Environment(EnvType.CLIENT)
	public interface BrightnessGetter {
		LevelRenderer.BrightnessGetter DEFAULT = (blockAndTintGetter, blockPos) -> {
			int i = blockAndTintGetter.getBrightness(LightLayer.SKY, blockPos);
			int j = blockAndTintGetter.getBrightness(LightLayer.BLOCK, blockPos);
			return Brightness.pack(j, i);
		};

		int packedBrightness(BlockAndTintGetter blockAndTintGetter, BlockPos blockPos);
	}
}
