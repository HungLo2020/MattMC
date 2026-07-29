package net.minecraft.client.renderer;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import net.blaze3d.buffers.GpuBufferSlice;
import net.blaze3d.framegraph.FrameGraphBuilder;
import net.blaze3d.framegraph.FramePass;
import net.blaze3d.pipeline.RenderTarget;
import net.blaze3d.pipeline.TextureTarget;
import net.blaze3d.platform.Lighting;
import net.blaze3d.resource.GraphicsResourceAllocator;
import net.blaze3d.resource.RenderTargetDescriptor;
import net.blaze3d.resource.ResourceHandle;
import net.blaze3d.systems.RenderPass;
import net.blaze3d.systems.RenderSystem;
import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.SheetedDecalTextureGenerator;
import net.blaze3d.vertex.VertexConsumer;
import net.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
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
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.TranslucencyPointOfView;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.client.renderer.debug.GameTestBlockHighlightRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
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
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.sodium.client.gl.device.RenderDevice;
import net.sodium.client.render.SodiumWorldRenderer;
import net.sodium.client.render.chunk.ChunkRenderMatrices;
import net.sodium.client.render.viewport.ViewportProvider;
import net.sodium.client.util.FlawlessFrames;
import net.sodium.client.util.SodiumChunkSection;
import net.sodium.client.world.LevelRendererExtension;
import net.sodium.fabric.SodiumFogRenderHook;
import net.voxelmap.VoxelConstants;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class LevelRenderer implements ResourceManagerReloadListener, AutoCloseable, net.irisshaders.iris.shadows.CullingDataCache, LevelRendererExtension {
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
	private final FeatureRenderDispatcher featureRenderDispatcher;
	
	// Iris: From shadows.MixinLevelRenderer - fields for shadow culling data cache
	private ObjectArrayList<SectionRenderDispatcher.RenderSection> iris$savedRenderChunks = new ObjectArrayList<>(69696);
	private double iris$savedLastCameraPitch;
	private double iris$savedLastCameraYaw;
	
	// Iris: From MixinLevelRenderer (main) - fields for main Iris pipeline integration
	private net.irisshaders.iris.pipeline.WorldRenderingPipeline pipeline;
	private boolean disableFrustumCulling;
	private boolean warned;
	@Nullable
	private BlockOutlineFramebufferProbe pendingBlockOutlineFramebufferProbe;
	@Nullable
	private BlockCrackFramebufferProbe pendingBlockCrackFramebufferProbe;
	@Nullable
	private BlockOutlineRenderState pendingRustOpenGlPostIrisBlockOutline;
	@Nullable
	private Vec3 pendingRustOpenGlPostIrisBlockOutlineCamera;
	private boolean pendingRustOpenGlPostIrisBlockOutlineTranslucentPass;
	@Nullable
	private List<BlockBreakingRenderState> pendingRustOpenGlPostIrisBlockCracks;
	@Nullable
	private Camera pendingRustOpenGlPostIrisBlockCracksCamera;
	private final Matrix4f blockOutlineProbeProjection = new Matrix4f();
	
	// Sodium: From LevelRendererMixin - fields for Sodium world renderer integration
	private static final EnumMap<ChunkSectionLayer, List<RenderPass.Draw<GpuBufferSlice[]>>> SODIUM_STATIC_MAP = new EnumMap<>(ChunkSectionLayer.class);
	private SodiumWorldRenderer renderer;
	private ChunkRenderMatrices matrices;

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
		
		// Sodium: Initialize SodiumWorldRenderer
		this.renderer = new SodiumWorldRenderer(minecraft);
	}

	public void close() {
		if (this.entityOutlineTarget != null) {
			this.entityOutlineTarget.destroyBuffers();
		}

		this.skyRenderer.close();
		this.cloudRenderer.close();
	}
	
	// Iris: Helper method to disable fabulous graphics when shaders are enabled
	private void disableFabulousGraphicsIfNeeded() {
		net.minecraft.client.Options options = this.minecraft.options;
		
		if (!net.irisshaders.iris.Iris.getIrisConfig().areShadersEnabled()) {
			// Nothing to do here, shaders are disabled.
			return;
		}
		
		if (options.graphicsMode().get() == GraphicsStatus.FABULOUS) {
			// Disable fabulous graphics when shaders are enabled.
			options.graphicsMode().set(GraphicsStatus.FANCY);
		}
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

		this.entityOutlineTarget = new TextureTarget("Entity Outline", this.minecraft.getWindow().getWidth(), this.minecraft.getWindow().getHeight(), true);
	}

	@Nullable
	private PostChain getTransparencyChain() {
		if (!Minecraft.useShaderTransparency()) {
			return null;
		} else {
			PostChain postChain = this.minecraft.getShaderManager().getPostChain(TRANSPARENCY_POST_CHAIN_ID, LevelTargetBundle.SORTING_TARGETS);
			if (postChain == null) {
				this.minecraft.options.graphicsMode().set(GraphicsStatus.FANCY);
				this.minecraft.options.save();
			}

			return postChain;
		}
	}

	public void doEntityOutline() {
		if (this.shouldShowEntityOutlines()) {
			this.entityOutlineTarget.blitAndBlendToTexture(this.minecraft.getMainRenderTarget().getColorTextureView());
		}
	}

	protected boolean shouldShowEntityOutlines() {
		return !this.minecraft.gameRenderer.isPanoramicMode() && this.entityOutlineTarget != null && this.minecraft.player != null;
	}

	public void setLevel(@Nullable ClientLevel clientLevel) {
		this.lastCameraSectionX = Integer.MIN_VALUE;
		this.lastCameraSectionY = Integer.MIN_VALUE;
		this.lastCameraSectionZ = Integer.MIN_VALUE;
		this.level = clientLevel;
		if (clientLevel != null) {
			this.allChanged();
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
		RenderDevice.enterManagedCode();
		try {
			this.renderer.setLevel(clientLevel);
		} finally {
			RenderDevice.exitManagedCode();
		}
	}

	private void clearVisibleSections() {
		this.visibleSections.clear();
		this.nearbyVisibleSections.clear();
	}

	public void allChanged() {
		// Iris: Disable fabulous graphics when shaders are enabled
		disableFabulousGraphicsIfNeeded();
		
		if (this.level != null) {
			this.level.clearTintCaches();
			if (this.sectionRenderDispatcher == null) {
				this.sectionRenderDispatcher = new SectionRenderDispatcher(
					this.level, this, Util.backgroundExecutor(), this.renderBuffers, this.minecraft.getBlockRenderer(), this.minecraft.getBlockEntityRenderDispatcher()
				);
			} else {
				this.sectionRenderDispatcher.setLevel(this.level);
			}

			this.cloudRenderer.markForRebuild();
			ItemBlockRenderTypes.setFancy(Minecraft.useFancyGraphics());
			this.lastViewDistance = this.minecraft.options.getEffectiveRenderDistance();
			if (this.viewArea != null) {
				this.viewArea.releaseAllBuffers();
			}

			this.sectionRenderDispatcher.clearCompileQueue();
			// Sodium: Nullify vanilla chunk storage allocation (return 0 for render distance)
			this.viewArea = new ViewArea(this.sectionRenderDispatcher, this.level, 0, this);
			this.sectionOcclusionGraph.waitAndReset(this.viewArea);
			this.clearVisibleSections();
			Camera camera = this.minecraft.gameRenderer.getMainCamera();
			this.viewArea.repositionCamera(SectionPos.of(camera.getPosition()));
		}
		
		// Sodium: Reload renderer
		RenderDevice.enterManagedCode();
		try {
			this.renderer.reload();
		} finally {
			RenderDevice.exitManagedCode();
		}
	}

	public void resize(int i, int j) {
		this.needsUpdate();
		if (this.entityOutlineTarget != null) {
			this.entityOutlineTarget.resize(i, j);
		}
	}

	@Nullable
	public String getSectionStatistics() {
		// Sodium: Return renderer debug string
		return this.renderer.getChunksDebugString();
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
		// Sodium: Redirect to our renderer
		return this.renderer.getVisibleChunkCount();
	}

	@Nullable
	public String getEntityStatistics() {
		return this.level == null
			? null
			: "E: " + this.levelRenderState.entityRenderStates.size() + "/" + this.level.getEntityCount() + ", SD: " + this.level.getServerSimulationDistance();
	}

public void cullTerrain(Camera camera, Frustum frustum, boolean spectator) { // Made public for Iris shadow rendering
// Sodium: Redirect terrain setup to our renderer
	var viewport = ((ViewportProvider) frustum).sodium$createViewport();
	var updateChunksImmediately = FlawlessFrames.isActive();

	int sectionX = SectionPos.posToSectionCoord(camera.getPosition().x());
	int sectionY = SectionPos.posToSectionCoord(camera.getPosition().y());
	int sectionZ = SectionPos.posToSectionCoord(camera.getPosition().z());

	if (this.lastCameraSectionX != sectionX || this.lastCameraSectionY != sectionY || this.lastCameraSectionZ != sectionZ) {
	this.lastCameraSectionX = sectionX;
	this.lastCameraSectionY = sectionY;
	this.lastCameraSectionZ = sectionZ;
	this.worldBorderRenderer.invalidate();
	}

	RenderDevice.enterManagedCode();
	try {
	this.renderer.setupTerrain(camera, viewport, SodiumFogRenderHook.getFogParameters(), spectator, updateChunksImmediately, matrices);
	} finally {
	RenderDevice.exitManagedCode();
	}
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

	private Frustum prepareCullFrustum(Matrix4f matrix4f, Matrix4f matrix4f2, Vec3 vec3) {
		// Iris: From MixinLevelRenderer - Disable frustum culling when Iris pipeline requests it
		if (this.disableFrustumCulling) {
			net.irisshaders.iris.shadows.frustum.fallback.NonCullingFrustum f = new net.irisshaders.iris.shadows.frustum.fallback.NonCullingFrustum();
			f.prepare(vec3.x(), vec3.y(), vec3.z());
			return f;
		}
		
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

	public void renderLevel(
		GraphicsResourceAllocator graphicsResourceAllocator,
		DeltaTracker deltaTracker,
		boolean bl,
		Camera camera,
		Matrix4f matrix4f,
		Matrix4f matrix4f2,
		Matrix4f matrix4f3,
		GpuBufferSlice gpuBufferSlice,
		Vector4f vector4f,
		boolean bl2
	) {
		// Iris: From MixinLevelRenderer - Setup Iris pipeline at the beginning
		net.irisshaders.iris.compat.dh.DHCompat.checkFrame();
		net.irisshaders.iris.uniforms.IrisTimeUniforms.updateTime();
		net.irisshaders.iris.uniforms.CapturedRenderingState.INSTANCE.setGbufferModelView(matrix4f);
		net.irisshaders.iris.uniforms.CapturedRenderingState.INSTANCE.setGbufferProjection(matrix4f2);
		this.blockOutlineProbeProjection.set(matrix4f2);
		float fakeTickDelta = net.irisshaders.iris.uniforms.SystemTimeUniforms.isDeterministicTemporalParityEnabled()
			? net.irisshaders.iris.uniforms.SystemTimeUniforms.deterministicTemporalPartialTick()
			: deltaTracker.getGameTimeDeltaPartialTick(false);
		net.irisshaders.iris.uniforms.CapturedRenderingState.INSTANCE.setTickDelta(fakeTickDelta);
		net.irisshaders.iris.uniforms.CapturedRenderingState.INSTANCE.setCloudTime((this.ticks + fakeTickDelta) * 0.03F);
		
		this.pipeline = net.irisshaders.iris.Iris.getPipelineManager().preparePipeline(net.irisshaders.iris.Iris.getCurrentDimension());
		this.disableFrustumCulling = this.pipeline.shouldDisableFrustumCulling();
		
		this.pipeline.beginLevelRendering();
		this.pipeline.setPhase(net.irisshaders.iris.pipeline.WorldRenderingPhase.NONE);
		net.irisshaders.iris.gl.IrisRenderSystem.backupAndDisableCullingState(this.pipeline.shouldDisableOcclusionCulling());
		
		if (net.irisshaders.iris.Iris.shouldActivateWireframe() && this.minecraft.isLocalServer()) {
			net.irisshaders.iris.gl.IrisRenderSystem.setPolygonMode(net.vulkanic.VulkanicPolygonMode.LINE);
		}
		
		// Iris: Begin level render immediate state (from MixinLevelRenderer vertices.immediate)
		net.irisshaders.iris.vertices.ImmediateState.isRenderingLevel = true;
		
		// Call registered hooks before rendering level
		for (net.minecraft.hooks.LevelRendererHooks hook : net.minecraft.hooks.HookRegistry.getLevelRendererHooks()) {
			hook.onBeforeRenderLevel(camera, matrix4f, matrix4f2);
		}
		
		float f = net.irisshaders.iris.uniforms.SystemTimeUniforms.isDeterministicTemporalParityEnabled()
			? net.irisshaders.iris.uniforms.SystemTimeUniforms.deterministicTemporalPartialTick()
			: deltaTracker.getGameTimeDeltaPartialTick(false);
		this.levelRenderState.reset();
		this.blockEntityRenderDispatcher.prepare(camera);
		this.entityRenderDispatcher.prepare(camera, this.minecraft.crosshairPickEntity);
		final ProfilerFiller profilerFiller = Profiler.get();
		profilerFiller.push("populateLightUpdates");
		this.level.pollLightUpdates();
		profilerFiller.popPush("runLightUpdates");
		this.level.getChunkSource().getLightEngine().runLightUpdates();
		profilerFiller.popPush("prepareCullFrustum");
		Vec3 vec3 = camera.getPosition();
		Frustum frustum = this.prepareCullFrustum(matrix4f, matrix4f3, vec3);
		
		// Sodium: Store matrices for setupTerrain (from LevelRendererMixin @Inject at="INVOKE cullTerrain")
		this.matrices = new ChunkRenderMatrices(matrix4f2, matrix4f);
			if (net.vulkanic.world.RustGalWorldPrimitiveRenderer.shouldUseRustOpenGlWorldPrimitives()) {
				net.vulkanic.world.RustGalWorldPrimitiveRenderer.beginFrame(
					matrix4f,
					matrix4f2,
				this.minecraft.getWindow().getWidth(),
				this.minecraft.getWindow().getHeight()
			);
		}
		
		// Iris: From MixinLevelRenderer - Render shadow terrain after frustum preparation
		this.pipeline.renderShadows(this, camera, this.levelRenderState.cameraRenderState);
		
		profilerFiller.popPush("cullTerrain");
		// Iris: From MixinLevelRenderer_SkipRendering - skip terrain culling if pipeline requests
		if (net.irisshaders.iris.Iris.getPipelineManager().getPipelineNullable() instanceof net.irisshaders.iris.pipeline.IrisRenderingPipeline pipeline) {
			if (!pipeline.skipAllRendering()) {
				this.cullTerrain(camera, frustum, this.minecraft.player.isSpectator());
			}
		} else {
			this.cullTerrain(camera, frustum, this.minecraft.player.isSpectator());
		}
		profilerFiller.popPush("compileSections");
		this.compileSections(camera);
		profilerFiller.popPush("extract");
		profilerFiller.push("entities");
		this.extractVisibleEntities(camera, frustum, deltaTracker, this.levelRenderState);
		profilerFiller.popPush("blockEntities");
		this.extractVisibleBlockEntities(camera, f, this.levelRenderState);
		profilerFiller.popPush("blockOutline");
		this.extractBlockOutline(camera, this.levelRenderState);
		profilerFiller.popPush("blockBreaking");
		this.extractBlockDestroyAnimation(camera, this.levelRenderState);
		profilerFiller.popPush("weather");
		this.weatherEffectRenderer.extractRenderState(this.level, this.ticks, f, vec3, this.levelRenderState.weatherRenderState);
		profilerFiller.popPush("sky");
		this.skyRenderer.extractRenderState(this.level, f, vec3, this.levelRenderState.skyRenderState);
		profilerFiller.popPush("border");
		this.worldBorderRenderer
			.extract(this.level.getWorldBorder(), vec3, this.minecraft.options.getEffectiveRenderDistance() * 16, this.levelRenderState.worldBorderRenderState);
		profilerFiller.pop();
		profilerFiller.popPush("setupFrameGraph");
		Matrix4fStack matrix4fStack = VulkanicAPI.getModelViewStack();
		matrix4fStack.pushMatrix();
		matrix4fStack.mul(matrix4f);
		FrameGraphBuilder frameGraphBuilder = new FrameGraphBuilder();
		this.targets.main = frameGraphBuilder.importExternal("main", this.minecraft.getMainRenderTarget());
		int i = this.minecraft.getMainRenderTarget().width;
		int j = this.minecraft.getMainRenderTarget().height;
		RenderTargetDescriptor renderTargetDescriptor = new RenderTargetDescriptor(i, j, true, 0);
		PostChain postChain = this.getTransparencyChain();
		if (postChain != null) {
			this.targets.translucent = frameGraphBuilder.createInternal("translucent", renderTargetDescriptor);
			this.targets.itemEntity = frameGraphBuilder.createInternal("item_entity", renderTargetDescriptor);
			this.targets.particles = frameGraphBuilder.createInternal("particles", renderTargetDescriptor);
			this.targets.weather = frameGraphBuilder.createInternal("weather", renderTargetDescriptor);
			this.targets.clouds = frameGraphBuilder.createInternal("clouds", renderTargetDescriptor);
		}

		if (this.entityOutlineTarget != null) {
			this.targets.entityOutline = frameGraphBuilder.importExternal("entity_outline", this.entityOutlineTarget);
		}

		FramePass framePass = frameGraphBuilder.addPass("clear");
		this.targets.main = framePass.readsAndWrites(this.targets.main);
		framePass.executes(
			() -> {
				RenderTarget renderTarget = this.minecraft.getMainRenderTarget();
				VulkanicAPI.createCommandEncoder()
					.clearColorAndDepthTextures(
						renderTarget.getColorTexture(), ARGB.colorFromFloat(0.0F, vector4f.x, vector4f.y, vector4f.z), renderTarget.getDepthTexture(), 1.0
					);
			}
		);
		
		// Iris: From MixinLevelRenderer - Add iris_setup pass after clear
		FramePass irisSetupPass = frameGraphBuilder.addPass("iris_setup");
		this.targets.main = irisSetupPass.readsAndWrites(this.targets.main);
		irisSetupPass.requires(framePass);
		irisSetupPass.executes(() -> {
			GpuBufferSlice params = VulkanicAPI.getShaderFog();
			this.pipeline.onBeginClear();
			this.pipeline.traceColortex0PhaseForDiagnostics("after-iris-setup");
			VulkanicAPI.setShaderFog(params);
		});
		
		if (bl2) {
			this.addSkyPass(frameGraphBuilder, camera, gpuBufferSlice);
		}

		this.addMainPass(frameGraphBuilder, frustum, matrix4f, gpuBufferSlice, bl, this.levelRenderState, deltaTracker, profilerFiller);
		PostChain postChain2 = this.minecraft.getShaderManager().getPostChain(ENTITY_OUTLINE_POST_CHAIN_ID, LevelTargetBundle.OUTLINE_TARGETS);
		if (this.levelRenderState.haveGlowingEntities && postChain2 != null) {
			postChain2.addToFrame(frameGraphBuilder, i, j, this.targets);
		}

		this.minecraft.particleEngine.extract(this.particlesRenderState, new Frustum(frustum).offset(-3.0F), camera, f);
		this.addParticlesPass(frameGraphBuilder, gpuBufferSlice);
		CloudStatus cloudStatus = this.minecraft.options.getCloudsType();
		if (cloudStatus != CloudStatus.OFF) {
			Optional<Integer> optional = this.level.dimensionType().cloudHeight();
			if (optional.isPresent()) {
				float g = this.ticks + f;
				int k = this.level.getCloudColor(f);
				this.addCloudsPass(frameGraphBuilder, cloudStatus, this.levelRenderState.cameraRenderState.pos, g, k, ((Integer)optional.get()).intValue() + 0.33F);
			}
		}

		this.addWeatherPass(frameGraphBuilder, this.levelRenderState.cameraRenderState.pos, gpuBufferSlice);
		if (postChain != null) {
			postChain.addToFrame(frameGraphBuilder, i, j, this.targets);
		}

		this.addLateDebugPass(frameGraphBuilder, this.levelRenderState.cameraRenderState.pos, gpuBufferSlice, frustum);
		profilerFiller.popPush("executeFrameGraph");
		frameGraphBuilder.execute(graphicsResourceAllocator, new FrameGraphBuilder.Inspector() {
			@Override
			public void beforeExecutePass(String string) {
				profilerFiller.push(string);
			}

			@Override
			public void afterExecutePass(String string) {
				profilerFiller.pop();
			}
		});
		this.targets.clear();
		
		// Iris: From MixinLevelRenderer - Finalize Iris rendering before popping matrix
		net.irisshaders.iris.pathways.HandRenderer.INSTANCE.renderTranslucent(
			matrix4f,
			deltaTracker.getGameTimeDeltaPartialTick(true),
			camera,
			this.minecraft.gameRenderer,
			this.pipeline
		);
		net.minecraft.util.profiling.Profiler.get().popPush("iris_final");
		
		if (net.irisshaders.iris.Iris.shouldActivateWireframe() && this.minecraft.isLocalServer()) {
			net.irisshaders.iris.gl.IrisRenderSystem.setPolygonMode(net.vulkanic.VulkanicPolygonMode.FILL);
		}
			this.pipeline.finalizeLevelRendering();
			this.renderPendingRustOpenGlPostIrisBlockOutline();
			this.renderPendingRustOpenGlPostIrisBlockCracks();
			this.renderPendingRustOpenGlPostIrisWorldMaterials();
			this.auditPendingBlockOutlineFramebufferProbe("after-iris-final");
			this.auditPendingBlockCrackFramebufferProbe("after-iris-final");
		
		// Show beta warning once
		if (!this.warned) {
			this.warned = true;
		}
		
		net.irisshaders.iris.gl.IrisRenderSystem.restoreCullingState();
		this.pipeline = null;
		
		matrix4fStack.popMatrix();
		profilerFiller.pop();
		
		// Iris: End level render immediate state (from MixinLevelRenderer vertices.immediate)
		net.irisshaders.iris.vertices.ImmediateState.isRenderingLevel = false;
		
		// VoxelMap: Render waypoint beacons after level rendering
		try {
			PoseStack voxelmap_poseStack = new PoseStack();
			voxelmap_poseStack.pushPose();
			voxelmap_poseStack.last().pose().set(matrix4f);
			net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource = this.minecraft.renderBuffers().bufferSource();
			VoxelConstants.onRenderWaypoints(deltaTracker.getGameTimeDeltaPartialTick(false), voxelmap_poseStack, bufferSource, camera);
			voxelmap_poseStack.popPose();
		} catch (Exception e) {
			// Silently catch to avoid crashes
		}
	}

	private void addMainPass(
		FrameGraphBuilder frameGraphBuilder,
		Frustum frustum,
		Matrix4f matrix4f,
		GpuBufferSlice gpuBufferSlice,
		boolean bl,
		LevelRenderState levelRenderState,
		DeltaTracker deltaTracker,
		ProfilerFiller profilerFiller
	) {
		FramePass framePass = frameGraphBuilder.addPass("main");
		this.targets.main = framePass.readsAndWrites(this.targets.main);
		if (this.targets.translucent != null) {
			this.targets.translucent = framePass.readsAndWrites(this.targets.translucent);
		}

		if (this.targets.itemEntity != null) {
			this.targets.itemEntity = framePass.readsAndWrites(this.targets.itemEntity);
		}

		if (this.targets.weather != null) {
			this.targets.weather = framePass.readsAndWrites(this.targets.weather);
		}

		if (levelRenderState.haveGlowingEntities && this.targets.entityOutline != null) {
			this.targets.entityOutline = framePass.readsAndWrites(this.targets.entityOutline);
		}
		
		// Iris: From MixinLevelRenderer (fantastic) - Add particle target for BEFORE rendering
		net.irisshaders.iris.shaderpack.properties.ParticleRenderingSettings settings = net.irisshaders.iris.Iris.getPipelineManager().getPipeline()
			.map(net.irisshaders.iris.pipeline.WorldRenderingPipeline::getParticleRenderingSettings)
			.orElse(net.irisshaders.iris.shaderpack.properties.ParticleRenderingSettings.MIXED);
		if (settings == net.irisshaders.iris.shaderpack.properties.ParticleRenderingSettings.BEFORE) {
			if (this.targets.particles != null) {
				this.targets.particles = framePass.readsAndWrites(this.targets.particles);
			}
		}

		ResourceHandle<RenderTarget> resourceHandle = this.targets.main;
		ResourceHandle<RenderTarget> resourceHandle2 = this.targets.translucent;
		ResourceHandle<RenderTarget> resourceHandle3 = this.targets.itemEntity;
		ResourceHandle<RenderTarget> resourceHandle4 = this.targets.entityOutline;
		framePass.executes(() -> {
			iris$renderMainPassBody();
			VulkanicAPI.setShaderFog(gpuBufferSlice);
			Vec3 vec3 = levelRenderState.cameraRenderState.pos;
			double d = vec3.x();
			double e = vec3.y();
			double f = vec3.z();
			profilerFiller.push("terrain");
			ChunkSectionsToRender chunkSectionsToRender = this.prepareChunkRenders(matrix4f, d, e, f);
			iris$renderTerrainGroup(chunkSectionsToRender, ChunkSectionLayerGroup.OPAQUE);
			this.pipeline.traceColortex0PhaseForDiagnostics("after-opaque-terrain");
			this.minecraft.gameRenderer
				.getLighting()
				.setupFor(Lighting.Entry.LEVEL);
			if (resourceHandle3 != null) {
				resourceHandle3.get().copyDepthFrom(this.minecraft.getMainRenderTarget());
			}

			if (this.shouldShowEntityOutlines() && resourceHandle4 != null) {
				RenderTarget renderTarget = resourceHandle4.get();
				VulkanicAPI.createCommandEncoder().clearColorAndDepthTextures(renderTarget.getColorTexture(), 0, renderTarget.getDepthTexture(), 1.0);
			}

			PoseStack poseStack = new PoseStack();
			MultiBufferSource.BufferSource bufferSource = this.renderBuffers.bufferSource();
			MultiBufferSource.BufferSource bufferSource2 = this.renderBuffers.crumblingBufferSource();
			profilerFiller.popPush("submitEntities");
			this.submitEntities(poseStack, levelRenderState, this.submitNodeStorage);
			profilerFiller.popPush("submitBlockEntities");
			this.submitBlockEntities(poseStack, levelRenderState, this.submitNodeStorage);
			profilerFiller.popPush("renderFeatures");
			iris$renderAllFeaturesMain();
			bufferSource.endLastBatch();
			this.checkPoseStack(poseStack);
			bufferSource.endBatch(RenderType.solid());
			bufferSource.endBatch(RenderType.endPortal());
			bufferSource.endBatch(RenderType.endGateway());
			bufferSource.endBatch(Sheets.solidBlockSheet());
			bufferSource.endBatch(Sheets.cutoutBlockSheet());
			bufferSource.endBatch(Sheets.bedSheet());
			bufferSource.endBatch(Sheets.shulkerBoxSheet());
			bufferSource.endBatch(Sheets.signSheet());
			bufferSource.endBatch(Sheets.hangingSignSheet());
			bufferSource.endBatch(Sheets.chestSheet());
			this.renderBuffers.outlineBufferSource().endOutlineBatch();
			if (bl) {
				this.renderBlockOutline(bufferSource, poseStack, false, levelRenderState);
			}

			profilerFiller.popPush("debug");
			iris$beginDebugRender();
			this.debugRenderer.render(poseStack, frustum, bufferSource, d, e, f, false);
			iris$endDebugRender();
			bufferSource.endLastBatch();
			profilerFiller.pop();
			this.gameTestBlockHighlightRenderer.render(poseStack, bufferSource);
			bufferSource.endLastBatch();
			this.checkPoseStack(poseStack);
			bufferSource.endBatch(Sheets.translucentItemSheet());
			bufferSource.endBatch(Sheets.bannerSheet());
			bufferSource.endBatch(Sheets.shieldSheet());
			bufferSource.endBatch(RenderType.armorEntityGlint());
			bufferSource.endBatch(RenderType.glint());
			bufferSource.endBatch(RenderType.glintTranslucent());
			bufferSource.endBatch(RenderType.entityGlint());
			profilerFiller.push("destroyProgress");
			this.renderBlockDestroyAnimation(poseStack, bufferSource2, levelRenderState);
			bufferSource2.endBatch();
			profilerFiller.pop();
			this.checkPoseStack(poseStack);
			bufferSource.endBatch(RenderType.waterMask());
			bufferSource.endBatch();
			this.pipeline.traceColortex0PhaseForDiagnostics("after-main-opaque-work");
			iris$beginTranslucents();
			if (resourceHandle2 != null) {
				resourceHandle2.get().copyDepthFrom(resourceHandle.get());
			}

			profilerFiller.push("translucent");
			iris$renderTerrainGroup(chunkSectionsToRender, ChunkSectionLayerGroup.TRANSLUCENT);
			profilerFiller.popPush("string");
			iris$renderTerrainGroup(chunkSectionsToRender, ChunkSectionLayerGroup.TRIPWIRE);
			if (bl) {
				this.renderBlockOutline(bufferSource, poseStack, true, levelRenderState);
			}

			bufferSource.endBatch();
			profilerFiller.pop();
		});
	}

	private void addParticlesPass(FrameGraphBuilder frameGraphBuilder, GpuBufferSlice gpuBufferSlice) {
		// Iris: From MixinLevelRenderer (fantastic) - Disable particles pass if rendering BEFORE
		net.irisshaders.iris.shaderpack.properties.ParticleRenderingSettings settings = net.irisshaders.iris.Iris.getPipelineManager().getPipeline()
			.map(net.irisshaders.iris.pipeline.WorldRenderingPipeline::getParticleRenderingSettings)
			.orElse(net.irisshaders.iris.shaderpack.properties.ParticleRenderingSettings.MIXED);
		if (settings == net.irisshaders.iris.shaderpack.properties.ParticleRenderingSettings.BEFORE) {
			return; // Cancel this pass - particles already rendered in main pass
		}
		
		FramePass framePass = frameGraphBuilder.addPass("particles");
		if (this.targets.particles != null) {
			this.targets.particles = framePass.readsAndWrites(this.targets.particles);
			framePass.reads(this.targets.main);
		} else {
			this.targets.main = framePass.readsAndWrites(this.targets.main);
		}

		ResourceHandle<RenderTarget> resourceHandle = this.targets.main;
		ResourceHandle<RenderTarget> resourceHandle2 = this.targets.particles;
		framePass.executes(() -> {
			iris$renderParticlesPassBody();
			VulkanicAPI.setShaderFog(gpuBufferSlice);
			if (resourceHandle2 != null) {
				resourceHandle2.get().copyDepthFrom(resourceHandle.get());
			}

			iris$submitParticles();
			iris$renderAllFeaturesParticles();
			this.particlesRenderState.reset();
		});
	}

	private void addCloudsPass(FrameGraphBuilder frameGraphBuilder, CloudStatus cloudStatus, Vec3 vec3, float f, int i, float g) {
		FramePass framePass = frameGraphBuilder.addPass("clouds");
		if (this.targets.clouds != null) {
			this.targets.clouds = framePass.readsAndWrites(this.targets.clouds);
		} else {
			this.targets.main = framePass.readsAndWrites(this.targets.main);
		}

		framePass.executes(() -> { 
			iris$renderCloudsPassBody(); 
			this.cloudRenderer.render(i, cloudStatus, g, vec3, f);
			// Iris: Reset phase after clouds rendering
			if (LevelRenderer.this.pipeline != null) {
				LevelRenderer.this.pipeline.setPhase(net.irisshaders.iris.pipeline.WorldRenderingPhase.NONE);
			}
		});
	}

	private void addWeatherPass(FrameGraphBuilder frameGraphBuilder, Vec3 vec3, GpuBufferSlice gpuBufferSlice) {
		int i = this.minecraft.options.getEffectiveRenderDistance() * 16;
		float f = this.minecraft.gameRenderer.getDepthFar();
		FramePass framePass = frameGraphBuilder.addPass("weather");
		if (this.targets.weather != null) {
			this.targets.weather = framePass.readsAndWrites(this.targets.weather);
		} else {
			this.targets.main = framePass.readsAndWrites(this.targets.main);
		}

		framePass.executes(() -> {
			iris$renderWeatherPassBody();
			VulkanicAPI.setShaderFog(gpuBufferSlice);
			MultiBufferSource.BufferSource bufferSource = this.renderBuffers.bufferSource();
			this.weatherEffectRenderer.render(bufferSource, vec3, this.levelRenderState.weatherRenderState);
			// Iris: Reset phase after weather, before world border
			if (LevelRenderer.this.pipeline != null) {
				LevelRenderer.this.pipeline.setPhase(net.irisshaders.iris.pipeline.WorldRenderingPhase.NONE);
			}
			iris$renderWorldBorderBody();
			this.worldBorderRenderer.render(this.levelRenderState.worldBorderRenderState, vec3, i, f);
			// Iris: Reset phase after world border
			if (LevelRenderer.this.pipeline != null) {
				LevelRenderer.this.pipeline.setPhase(net.irisshaders.iris.pipeline.WorldRenderingPhase.NONE);
			}
			bufferSource.endBatch();
		});
	}

	private void addLateDebugPass(FrameGraphBuilder frameGraphBuilder, Vec3 vec3, GpuBufferSlice gpuBufferSlice, Frustum frustum) {
		FramePass framePass = frameGraphBuilder.addPass("late_debug");
		this.targets.main = framePass.readsAndWrites(this.targets.main);
		if (this.targets.itemEntity != null) {
			this.targets.itemEntity = framePass.readsAndWrites(this.targets.itemEntity);
		}

		ResourceHandle<RenderTarget> resourceHandle = this.targets.main;
		framePass.executes(() -> {
			VulkanicAPI.setShaderFog(gpuBufferSlice);
			PoseStack poseStack = new PoseStack();
			MultiBufferSource.BufferSource bufferSource = this.renderBuffers.bufferSource();
			VulkanicAPI.setOutputColorTextureOverride(resourceHandle.get().getColorTextureView());
			VulkanicAPI.setOutputDepthTextureOverride(resourceHandle.get().getDepthTextureView());
			this.debugRenderer.render(poseStack, frustum, bufferSource, vec3.x, vec3.y, vec3.z, true);
			bufferSource.endLastBatch();
			VulkanicAPI.setOutputColorTextureOverride(null);
			VulkanicAPI.setOutputDepthTextureOverride(null);
			this.checkPoseStack(poseStack);
		});
	}

	private void extractVisibleEntities(Camera camera, Frustum frustum, DeltaTracker deltaTracker, LevelRenderState levelRenderState) {
		Vec3 vec3 = camera.getPosition();
		double d = vec3.x();
		double e = vec3.y();
		double f = vec3.z();
		TickRateManager tickRateManager = this.minecraft.level.tickRateManager();
		boolean bl = this.shouldShowEntityOutlines();
		Entity.setViewScale(Mth.clamp(this.minecraft.options.getEffectiveRenderDistance() / 8.0, 1.0, 2.5) * this.minecraft.options.entityDistanceScaling().get());

		// Iris: From MixinLevelRenderer_SkipRendering - skip entity extraction if pipeline requests
		Iterable<Entity> entities;
		if (net.irisshaders.iris.Iris.getPipelineManager().getPipelineNullable() instanceof net.irisshaders.iris.pipeline.IrisRenderingPipeline pipeline && pipeline.skipAllRendering()) {
			entities = java.util.Collections.emptyList();
		} else {
			entities = this.level.entitiesForRendering();
		}

		for (Entity entity : entities) {
			if (this.entityRenderDispatcher.shouldRender(entity, frustum, d, e, f) || entity.hasIndirectPassenger(this.minecraft.player)) {
				BlockPos blockPos = entity.blockPosition();
				if ((this.level.isOutsideBuildHeight(blockPos.getY()) || this.isSectionCompiled(blockPos))
					&& (entity != camera.getEntity() || camera.isDetached() || camera.getEntity() instanceof LivingEntity && ((LivingEntity)camera.getEntity()).isSleeping())
					&& (!(entity instanceof LocalPlayer) || camera.getEntity() == entity)) {
					if (entity.tickCount == 0) {
						entity.xOld = entity.getX();
						entity.yOld = entity.getY();
						entity.zOld = entity.getZ();
					}

					float g = deltaTracker.getGameTimeDeltaPartialTick(!tickRateManager.isEntityFrozen(entity));
					EntityRenderState entityRenderState = this.extractEntity(entity, g);
					levelRenderState.entityRenderStates.add(entityRenderState);
					if (entityRenderState.appearsGlowing() && bl) {
						levelRenderState.haveGlowingEntities = true;
					}
				}
			}
		}
	}

	private void submitEntities(PoseStack poseStack, LevelRenderState levelRenderState, SubmitNodeCollector submitNodeCollector) {
		Vec3 vec3 = levelRenderState.cameraRenderState.pos;
		double d = vec3.x();
		double e = vec3.y();
		double f = vec3.z();

		for (EntityRenderState entityRenderState : levelRenderState.entityRenderStates) {
			if (!levelRenderState.haveGlowingEntities) {
				entityRenderState.outlineColor = 0;
			}

			this.entityRenderDispatcher
				.submit(
					entityRenderState,
					levelRenderState.cameraRenderState,
					entityRenderState.x - d,
					entityRenderState.y - e,
					entityRenderState.z - f,
					poseStack,
					submitNodeCollector
				);
		}
	}

	public void extractVisibleBlockEntities(Camera camera, float f, LevelRenderState levelRenderState) { // Made public for Iris shadow rendering
		// Sodium: Redirect to SodiumWorldRenderer instead of using vanilla visibleSections
		// This was previously done via LevelRendererMixin but has been inlined
		this.renderer.extractBlockEntities(camera, f, this.destructionProgress, levelRenderState);
	}

	private void submitBlockEntities(PoseStack poseStack, LevelRenderState levelRenderState, SubmitNodeStorage submitNodeStorage) {
		Vec3 vec3 = levelRenderState.cameraRenderState.pos;
		double d = vec3.x();
		double e = vec3.y();
		double f = vec3.z();

		for (BlockEntityRenderState blockEntityRenderState : levelRenderState.blockEntityRenderStates) {
			BlockPos blockPos = blockEntityRenderState.blockPos;
			poseStack.pushPose();
			poseStack.translate(blockPos.getX() - d, blockPos.getY() - e, blockPos.getZ() - f);
			this.blockEntityRenderDispatcher.submit(blockEntityRenderState, poseStack, submitNodeStorage, levelRenderState.cameraRenderState);
			poseStack.popPose();
		}
	}

	private void extractBlockDestroyAnimation(Camera camera, LevelRenderState levelRenderState) {
		this.collectBlockDestroyAnimation(camera, levelRenderState);
		net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueBlockBreakingCracks(levelRenderState.blockBreakingRenderStates, camera);
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
		net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueWorldBorder(this.levelRenderState.worldBorderRenderState, vec3, renderDistance, depthFar);
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

	private void renderBlockDestroyAnimation(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, LevelRenderState levelRenderState) {
		for (BlockBreakingRenderState blockBreakingRenderState : levelRenderState.blockBreakingRenderStates) {
			net.minecraft.client.dev.DeterministicCameraCapture.recordRealSurvivalCrackRenderState(blockBreakingRenderState);
		}
		if (net.vulkanic.world.RustGalWorldPrimitiveRenderer.crackDisabledForDiagnostics()) {
			auditBlockOutline("crack draw route=disabled retained=false states="
				+ levelRenderState.blockBreakingRenderStates.size());
			return;
		}
		if (net.vulkanic.world.RustGalWorldPrimitiveRenderer.shouldUseRustOpenGlCrack()
			&& this.minecraft.isGameLoadFinished()
			&& this.minecraft.screen == null
			&& this.minecraft.getOverlay() == null) {
			Camera camera = this.minecraft.gameRenderer.getMainCamera();
			if (!net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasValidOpenGlBlockBreakingCracks(
				levelRenderState.blockBreakingRenderStates,
				camera
			)) {
				auditBlockOutline("crack draw route=rust-opengl retained=false states="
					+ levelRenderState.blockBreakingRenderStates.size()
					+ " selected=false reason=no-valid-destroy-progress");
				return;
			}
			if (net.irisshaders.iris.Iris.isPackInUseQuick()) {
				this.pendingRustOpenGlPostIrisBlockCracks = List.copyOf(levelRenderState.blockBreakingRenderStates);
				this.pendingRustOpenGlPostIrisBlockCracksCamera = camera;
				auditBlockOutline("crack queue route=rust-opengl retained=false postIris=true states="
					+ levelRenderState.blockBreakingRenderStates.size());
				return;
			}
			boolean rendered = this.renderRustOpenGlBlockBreakingCracks(levelRenderState);
			if (!rendered) {
				throw new IllegalStateException("Rust OpenGL block-breaking crack overlay was selected with valid semantic requests but submitted no work");
			}
			return;
		}
		Vec3 vec3 = levelRenderState.cameraRenderState.pos;
		double d = vec3.x();
		double e = vec3.y();
		double f = vec3.z();

		for (BlockBreakingRenderState blockBreakingRenderState : levelRenderState.blockBreakingRenderStates) {
			poseStack.pushPose();
			BlockPos blockPos = blockBreakingRenderState.blockPos;
			poseStack.translate(blockPos.getX() - d, blockPos.getY() - e, blockPos.getZ() - f);
			PoseStack.Pose pose = poseStack.last();
			VertexConsumer vertexConsumer = new SheetedDecalTextureGenerator(
				bufferSource.getBuffer((RenderType)ModelBakery.DESTROY_TYPES.get(blockBreakingRenderState.progress)), pose, 1.0F
			);
			this.minecraft.getBlockRenderer().renderBreakingTexture(blockBreakingRenderState.blockState, blockPos, blockBreakingRenderState, poseStack, vertexConsumer);
			poseStack.popPose();
		}
	}

	private void extractBlockOutline(Camera camera, LevelRenderState levelRenderState) {
		levelRenderState.blockOutlineRenderState = null;
		if (extractDiagnosticBlockOutline(camera, levelRenderState)) {
			return;
		}
		if (this.minecraft.hitResult instanceof BlockHitResult blockHitResult) {
			if (blockHitResult.getType() != Type.MISS) {
				BlockPos blockPos = blockHitResult.getBlockPos();
				BlockState blockState = this.level.getBlockState(blockPos);
				if (!blockState.isAir() && this.level.getWorldBorder().isWithinBounds(blockPos)) {
					boolean bl = ItemBlockRenderTypes.getChunkRenderType(blockState).sortOnUpload();
					boolean bl2 = this.minecraft.options.highContrastBlockOutline().get();
					CollisionContext collisionContext = CollisionContext.of(camera.getEntity());
					VoxelShape voxelShape = blockState.getShape(this.level, blockPos, collisionContext);
					if (SharedConstants.DEBUG_SHAPES) {
						VoxelShape voxelShape2 = blockState.getCollisionShape(this.level, blockPos, collisionContext);
						VoxelShape voxelShape3 = blockState.getOcclusionShape();
						VoxelShape voxelShape4 = blockState.getInteractionShape(this.level, blockPos);
						levelRenderState.blockOutlineRenderState = new BlockOutlineRenderState(blockPos, bl, bl2, voxelShape, voxelShape2, voxelShape3, voxelShape4);
					} else {
						levelRenderState.blockOutlineRenderState = new BlockOutlineRenderState(blockPos, bl, bl2, voxelShape);
					}
					auditBlockOutline("extract route=" + javaBlockOutlineRoute()
						+ " target=true pos=" + blockPos.toShortString()
						+ " translucent=" + bl
						+ " highContrast=" + bl2
						+ " shapeEmpty=" + voxelShape.isEmpty());
				}
			}
		}
		if (levelRenderState.blockOutlineRenderState == null) {
			auditBlockOutline("extract route=" + javaBlockOutlineRoute() + " target=false");
		}
	}

	private static boolean extractDiagnosticBlockOutline(Camera camera, LevelRenderState levelRenderState) {
		if (!BLOCK_OUTLINE_DIAGNOSTICS || BLOCK_OUTLINE_DIAGNOSTIC_SCENARIO.isBlank()) {
			return false;
		}
		if ("no-target".equalsIgnoreCase(BLOCK_OUTLINE_DIAGNOSTIC_SCENARIO)) {
			auditBlockOutline("extract route=" + javaBlockOutlineRoute() + " target=false diagnostic=no-target");
			return true;
		}
		VoxelShape shape = diagnosticBlockOutlineShape();
		if (shape.isEmpty()) {
			auditBlockOutline("extract route=" + javaBlockOutlineRoute() + " target=false diagnostic=empty-shape");
			return true;
		}
		BlockPos blockPos = diagnosticBlockOutlinePos(camera);
		boolean highContrast = "high-contrast".equalsIgnoreCase(BLOCK_OUTLINE_DIAGNOSTIC_STYLE);
		levelRenderState.blockOutlineRenderState = new BlockOutlineRenderState(blockPos, false, highContrast, shape);
		auditBlockOutline("extract route=" + javaBlockOutlineRoute()
			+ " target=true diagnostic=" + BLOCK_OUTLINE_DIAGNOSTIC_SCENARIO
			+ " pos=" + blockPos.toShortString()
			+ " highContrast=" + highContrast
			+ " shapeEmpty=false");
		return true;
	}

	private static VoxelShape diagnosticBlockOutlineShape() {
		return switch (BLOCK_OUTLINE_DIAGNOSTIC_SCENARIO.toLowerCase(java.util.Locale.ROOT)) {
			case "full-cube", "cube" -> Shapes.block();
			case "partial-shape", "partial" -> Shapes.box(0.0, 0.0, 0.0, 1.0, 0.5, 1.0);
			case "disconnected-shape", "disconnected" -> Shapes.or(
				Shapes.box(0.0, 0.0, 0.0, 0.375, 0.375, 0.375),
				Shapes.box(0.625, 0.625, 0.625, 1.0, 1.0, 1.0)
			);
			default -> Shapes.empty();
		};
	}

	private static BlockPos diagnosticBlockOutlinePos(Camera camera) {
		org.joml.Vector3f look = camera.getLookVector();
		Vec3 cameraPos = camera.getPosition();
		double distance = 4.0;
		return BlockPos.containing(
			cameraPos.x() + look.x() * distance - 0.5,
			cameraPos.y() + look.y() * distance - 0.5,
			cameraPos.z() + look.z() * distance - 0.5
		);
	}

	private void renderBlockOutline(MultiBufferSource.BufferSource bufferSource, PoseStack poseStack, boolean bl, LevelRenderState levelRenderState) {
		BlockOutlineRenderState blockOutlineRenderState = levelRenderState.blockOutlineRenderState;
		if (blockOutlineRenderState != null) {
			if (blockOutlineRenderState.isTranslucent() == bl) {
					Vec3 vec3 = levelRenderState.cameraRenderState.pos;
						if (net.vulkanic.world.RustGalWorldPrimitiveRenderer.shouldUseRustOpenGlOutline()
							&& this.minecraft.isGameLoadFinished()
							&& (this.minecraft.screen == null || this.minecraft.screen.isPauseScreen())
							&& this.minecraft.getOverlay() == null) {
							if (net.irisshaders.iris.Iris.isPackInUseQuick()) {
								this.pendingRustOpenGlPostIrisBlockOutline = blockOutlineRenderState;
								this.pendingRustOpenGlPostIrisBlockOutlineCamera = vec3;
								this.pendingRustOpenGlPostIrisBlockOutlineTranslucentPass = bl;
								auditBlockOutline("queue route=rust-opengl postIris=true translucentPass=" + bl
									+ " pos=" + blockOutlineRenderState.pos().toShortString()
									+ " highContrast=" + blockOutlineRenderState.highContrast());
								return;
							}
							RenderTarget rustOutlineTarget = RenderType.lines().iris$getRenderTarget();
							if (rustOutlineTarget == null) {
								rustOutlineTarget = this.minecraft.getMainRenderTarget();
							}
							try (RenderPass outlinePass = VulkanicAPI.createRenderPass(
								() -> "Rust GAL block outline",
								rustOutlineTarget.getColorTextureView(),
								OptionalInt.empty(),
								rustOutlineTarget.useDepth ? rustOutlineTarget.getDepthTextureView() : null,
								OptionalDouble.empty()
							)) {
								outlinePass.setPipeline(RenderType.lines().pipeline());
								this.renderRustOpenGlBlockOutline(blockOutlineRenderState, poseStack, vec3, bl);
							}
							auditBlockOutline("draw route=rust-opengl retained=false translucentPass=" + bl
								+ " pos=" + blockOutlineRenderState.pos().toShortString()
								+ " highContrast=" + blockOutlineRenderState.highContrast());
						return;
					}
				BlockOutlineFramebufferProbe framebufferProbe = this.createBlockOutlineFramebufferProbe(blockOutlineRenderState, poseStack, vec3, bl);
				if (blockOutlineRenderState.highContrast()) {
					// Iris: Wrap with outline render state shard
					RenderType wrappedType = new net.irisshaders.iris.layer.OuterWrappedRenderType(
						"iris:is_outline",
						RenderType.secondaryBlockOutline(),
						net.irisshaders.iris.layer.IsOutlineRenderStateShard.INSTANCE
					);
					VertexConsumer vertexConsumer = bufferSource.getBuffer(wrappedType);
					this.renderHitOutline(poseStack, vertexConsumer, vec3.x, vec3.y, vec3.z, blockOutlineRenderState, -16777216);
				}

				// Iris: Wrap with outline render state shard
				RenderType wrappedType = new net.irisshaders.iris.layer.OuterWrappedRenderType(
					"iris:is_outline",
					RenderType.lines(),
					net.irisshaders.iris.layer.IsOutlineRenderStateShard.INSTANCE
				);
				VertexConsumer vertexConsumer = bufferSource.getBuffer(wrappedType);
					int i = blockOutlineRenderState.highContrast() ? -11010079 : ARGB.color(102, -16777216);
					this.renderHitOutline(poseStack, vertexConsumer, vec3.x, vec3.y, vec3.z, blockOutlineRenderState, i);
					bufferSource.endLastBatch();
					this.completeBlockOutlineFramebufferProbe(framebufferProbe);
					String retainedRoute = net.vulkanic.world.RustGalWorldPrimitiveRenderer.shouldUseRustOpenGlOutline()
						? "java-opengl-pre-ready"
						: javaBlockOutlineRoute();
					auditBlockOutline("draw route=" + retainedRoute
						+ " retained=true translucentPass=" + bl
						+ " pos=" + blockOutlineRenderState.pos().toShortString()
						+ " highContrast=" + blockOutlineRenderState.highContrast());
			}
		}
	}

	private boolean renderRustOpenGlBlockBreakingCracks(LevelRenderState levelRenderState) {
		RenderType crumblingType = (RenderType)ModelBakery.DESTROY_TYPES.get(0);
		int drawFramebuffer = VulkanicAPI.getDrawFramebufferBinding();
		if (drawFramebuffer != 0) {
			try (RenderPass crackPass = VulkanicAPI.createRenderPass(
				() -> "Rust GAL block-breaking crack overlay",
				drawFramebuffer,
				this.minecraft.getMainRenderTarget().useDepth
			)) {
				crackPass.setPipeline(crumblingType.pipeline());
				return this.renderRustOpenGlBlockBreakingCracksInCurrentScope(levelRenderState);
			}
		}
		RenderTarget target = this.minecraft.getMainRenderTarget();
		try (RenderPass crackPass = VulkanicAPI.createRenderPass(
			() -> "Rust GAL block-breaking crack overlay",
			target.getColorTextureView(),
			OptionalInt.empty(),
			target.useDepth ? target.getDepthTextureView() : null,
			OptionalDouble.empty()
		)) {
			crackPass.setPipeline(crumblingType.pipeline());
			return this.renderRustOpenGlBlockBreakingCracksInCurrentScope(levelRenderState);
		}
	}

	private boolean renderRustOpenGlBlockBreakingCracksInCurrentScope(LevelRenderState levelRenderState) {
		return this.renderRustOpenGlBlockBreakingCracksInCurrentScope(
			levelRenderState.blockBreakingRenderStates,
			this.minecraft.gameRenderer.getMainCamera()
		);
	}

	private boolean renderRustOpenGlBlockBreakingCracksInCurrentScope(List<BlockBreakingRenderState> states, Camera camera) {
		BlockCrackFramebufferProbe framebufferProbe = this.createBlockCrackFramebufferProbe(states);
		net.vulkanic.world.RustGalWorldPrimitiveRenderer.reseedFrameMatrices(
			new Matrix4f(this.matrices.modelView()),
			new Matrix4f(this.matrices.projection()),
			this.minecraft.getWindow().getWidth(),
			this.minecraft.getWindow().getHeight()
		);
		boolean rendered = net.vulkanic.world.RustGalWorldPrimitiveRenderer.renderOpenGlBlockBreakingCracks(
			this.minecraft,
			states,
			camera
		);
		this.completeBlockCrackFramebufferProbe(framebufferProbe);
		auditBlockOutline("crack draw route=rust-opengl retained=false states="
			+ states.size()
			+ " rendered=" + rendered);
		return rendered;
	}

	private void renderPendingRustOpenGlPostIrisBlockOutline() {
		BlockOutlineRenderState blockOutlineRenderState = this.pendingRustOpenGlPostIrisBlockOutline;
		Vec3 cameraPos = this.pendingRustOpenGlPostIrisBlockOutlineCamera;
		boolean translucentPass = this.pendingRustOpenGlPostIrisBlockOutlineTranslucentPass;
		this.pendingRustOpenGlPostIrisBlockOutline = null;
		this.pendingRustOpenGlPostIrisBlockOutlineCamera = null;
		this.pendingRustOpenGlPostIrisBlockOutlineTranslucentPass = false;
		if (blockOutlineRenderState == null || cameraPos == null) {
			return;
		}
		RenderTarget finalTarget = this.minecraft.getMainRenderTarget();
		try (RenderPass outlinePass = VulkanicAPI.createRenderPass(
			() -> "Rust GAL block outline after Iris final",
			finalTarget.getColorTextureView(),
			OptionalInt.empty(),
			finalTarget.useDepth ? finalTarget.getDepthTextureView() : null,
			OptionalDouble.empty()
		)) {
			outlinePass.setPipeline(RenderType.lines().pipeline());
			this.renderRustOpenGlBlockOutline(blockOutlineRenderState, new PoseStack(), cameraPos, translucentPass);
		}
		auditBlockOutline("draw route=rust-opengl retained=false postIris=true translucentPass=" + translucentPass
			+ " pos=" + blockOutlineRenderState.pos().toShortString()
			+ " highContrast=" + blockOutlineRenderState.highContrast());
	}

	private void renderPendingRustOpenGlPostIrisBlockCracks() {
		List<BlockBreakingRenderState> states = this.pendingRustOpenGlPostIrisBlockCracks;
		Camera camera = this.pendingRustOpenGlPostIrisBlockCracksCamera;
		this.pendingRustOpenGlPostIrisBlockCracks = null;
		this.pendingRustOpenGlPostIrisBlockCracksCamera = null;
		if (states == null || states.isEmpty() || camera == null) {
			return;
		}
		RenderTarget finalTarget = this.minecraft.getMainRenderTarget();
		RenderType crumblingType = (RenderType)ModelBakery.DESTROY_TYPES.get(0);
		boolean rendered;
		try (RenderPass crackPass = VulkanicAPI.createRenderPass(
			() -> "Rust GAL block-breaking crack overlay after Iris final",
			finalTarget.getColorTextureView(),
			OptionalInt.empty(),
			finalTarget.useDepth ? finalTarget.getDepthTextureView() : null,
			OptionalDouble.empty()
		)) {
			crackPass.setPipeline(crumblingType.pipeline());
			rendered = this.renderRustOpenGlBlockBreakingCracksInCurrentScope(states, camera);
		}
		auditBlockOutline("crack draw route=rust-opengl retained=false postIris=true states="
			+ states.size()
			+ " rendered=" + rendered);
		if (!rendered) {
			throw new IllegalStateException("Rust OpenGL block-breaking crack overlay was selected post-Iris with valid semantic requests but submitted no work");
		}
	}

	private void renderPendingRustOpenGlPostIrisWorldMaterials() {
		if (!net.vulkanic.world.RustGalWorldPrimitiveRenderer.hasPendingMaterialQuads()) {
			return;
		}
		RenderTarget finalTarget = this.minecraft.getMainRenderTarget();
		try (RenderPass materialPass = VulkanicAPI.createRenderPass(
			() -> "Rust GAL world material quads after Iris final",
			finalTarget.getColorTextureView(),
			OptionalInt.empty(),
			finalTarget.useDepth ? finalTarget.getDepthTextureView() : null,
			OptionalDouble.empty()
		)) {
			materialPass.setPipeline(RenderPipelines.OPAQUE_PARTICLE);
			boolean rendered = net.vulkanic.world.RustGalWorldPrimitiveRenderer.renderOpenGlPendingMaterialQuads(
				this.minecraft,
				"minecraft.particle.block-marker.post-iris"
			);
			auditBlockOutline("world-material draw route=rust-opengl retained=false postIris=true rendered=" + rendered);
			if (!rendered) {
				throw new IllegalStateException("Rust OpenGL world material quads were selected post-Iris with valid semantic requests but submitted no work");
			}
		}
	}

	private void renderRustOpenGlBlockOutline(BlockOutlineRenderState blockOutlineRenderState, PoseStack poseStack, Vec3 cameraPos, boolean translucentPass) {
		BlockOutlineFramebufferProbe framebufferProbe = this.createBlockOutlineFramebufferProbe(blockOutlineRenderState, poseStack, cameraPos, translucentPass);
		net.irisshaders.iris.layer.GbufferPrograms.beginOutline();
		try {
			net.vulkanic.world.RustGalWorldPrimitiveRenderer.reseedFrameMatrices(
				new Matrix4f(this.matrices.modelView()),
				new Matrix4f(this.matrices.projection()),
				this.minecraft.getWindow().getWidth(),
				this.minecraft.getWindow().getHeight()
			);
			net.vulkanic.world.RustGalWorldPrimitiveRenderer.renderOpenGlBlockOutline(this.minecraft, blockOutlineRenderState, cameraPos);
			this.completeBlockOutlineFramebufferProbe(framebufferProbe);
		} finally {
			net.irisshaders.iris.layer.GbufferPrograms.endOutline();
		}
	}

	private static String javaBlockOutlineRoute() {
		return switch (net.vulkanic.world.RustGalWorldPrimitiveRenderer.currentBlockOutlineRoute()) {
			case RUST_OPENGL_BORROWED_CONTEXT -> "rust-opengl";
			case RUST_VULKAN_WHOLE_FRAME -> "rust-vulkan";
			case JAVA_COMPATIBILITY -> VulkanicAPI.isVulkanBackendSelected() ? "java-vulkan" : "java-opengl";
			case DISABLED -> "disabled";
		};
	}

	private static void auditBlockOutline(String message) {
		if (BLOCK_OUTLINE_DIAGNOSTICS && blockOutlineDiagnosticLogs < 128) {
			blockOutlineDiagnosticLogs++;
			LOGGER.info("[MattMC graphics-audit] block-outline {}", message);
		}
	}

	@Nullable
	private BlockOutlineFramebufferProbe createBlockOutlineFramebufferProbe(
		BlockOutlineRenderState blockOutlineRenderState,
		PoseStack poseStack,
		Vec3 cameraPos,
		boolean translucentPass
	) {
		if (!BLOCK_OUTLINE_FRAMEBUFFER_DIAGNOSTICS
			|| VulkanicAPI.isVulkanBackendSelected()
			|| blockOutlineFramebufferDiagnosticLogs >= BLOCK_OUTLINE_FRAMEBUFFER_DIAGNOSTIC_LIMIT) {
			return null;
		}
		FramebufferProbeCrop crop = this.projectedBlockShapeProbeCrop(
			blockOutlineRenderState.pos(),
			blockOutlineRenderState.shape(),
			this.matrices != null ? this.matrices.modelView() : poseStack.last().pose(),
			this.blockOutlineProbeProjection,
			cameraPos,
			"projected-outline",
			48
		);
		FramebufferProbeSample before = this.readBlockOutlineProbeSample(crop);
		if (before == null) {
			return null;
		}
		ProjectedOutlineEdgeSamples edgeSamples = this.projectedBlockShapeEdgeSamples(
			blockOutlineRenderState.pos(),
			blockOutlineRenderState.shape(),
			this.matrices != null ? this.matrices.modelView() : poseStack.last().pose(),
				this.blockOutlineProbeProjection,
				cameraPos,
				crop,
				before
			);
		return new BlockOutlineFramebufferProbe(
			blockOutlineRenderState.pos().toShortString(),
			blockOutlineRenderState.highContrast(),
			translucentPass,
			crop,
			edgeSamples,
			before
		);
	}

	@Nullable
	private BlockCrackFramebufferProbe createBlockCrackFramebufferProbe(List<BlockBreakingRenderState> states) {
		if (!BLOCK_OUTLINE_FRAMEBUFFER_DIAGNOSTICS
			|| VulkanicAPI.isVulkanBackendSelected()
			|| blockCrackFramebufferDiagnosticLogs >= BLOCK_OUTLINE_FRAMEBUFFER_DIAGNOSTIC_LIMIT
			|| states == null
			|| states.isEmpty()) {
			return null;
		}
		Camera camera = this.minecraft.gameRenderer.getMainCamera();
		Vec3 cameraPos = camera.getPosition();
		for (BlockBreakingRenderState state : states) {
			if (state.progress < 0 || state.progress >= 10 || state.blockState.isAir()) {
				continue;
			}
			if (blockCrackFramebufferDiagnosticStages[state.progress]) {
				continue;
			}
			VoxelShape shape = state.blockState.getShape(state.level, state.blockPos, CollisionContext.of(camera.getEntity()));
			if (shape.isEmpty()) {
				shape = Shapes.block();
			}
			FramebufferProbeCrop crop = this.projectedBlockShapeProbeCrop(
				state.blockPos,
				shape,
				this.matrices.modelView(),
				this.blockOutlineProbeProjection,
				cameraPos,
				"projected-crack-face",
				16
			);
			FramebufferProbeSample before = this.readBlockOutlineProbeSample(crop);
			if (before == null) {
				return null;
			}
			return new BlockCrackFramebufferProbe(
				state.blockPos.toShortString(),
				state.progress,
				shape.toAabbs().size(),
				crop,
				before
			);
		}
		return null;
	}

	private void completeBlockOutlineFramebufferProbe(@Nullable BlockOutlineFramebufferProbe probe) {
		if (probe == null) {
			return;
		}
		FramebufferProbeSample afterDraw = this.readBlockOutlineProbeSample(probe.crop());
		if (afterDraw == null) {
			return;
		}
		this.pendingBlockOutlineFramebufferProbe = probe.withAfterDraw(afterDraw);
		this.logBlockOutlineFramebufferProbe("after-draw", this.pendingBlockOutlineFramebufferProbe, afterDraw);
	}

	private void auditPendingBlockOutlineFramebufferProbe(String stage) {
		BlockOutlineFramebufferProbe probe = this.pendingBlockOutlineFramebufferProbe;
		if (probe == null || blockOutlineFramebufferDiagnosticLogs >= BLOCK_OUTLINE_FRAMEBUFFER_DIAGNOSTIC_LIMIT) {
			return;
		}
		FramebufferProbeSample finalSample = this.readBlockOutlineProbeSample(probe.crop());
		if (finalSample != null) {
			this.logBlockOutlineFramebufferProbe(stage, probe, finalSample);
			blockOutlineFramebufferDiagnosticLogs++;
		}
		this.pendingBlockOutlineFramebufferProbe = null;
	}

	private void completeBlockCrackFramebufferProbe(@Nullable BlockCrackFramebufferProbe probe) {
		if (probe == null) {
			return;
		}
		FramebufferProbeSample afterDraw = this.readBlockOutlineProbeSample(probe.crop());
		if (afterDraw == null) {
			return;
		}
		this.pendingBlockCrackFramebufferProbe = probe.withAfterDraw(afterDraw);
		this.logBlockCrackFramebufferProbe("after-draw", this.pendingBlockCrackFramebufferProbe, afterDraw);
	}

	private void auditPendingBlockCrackFramebufferProbe(String stage) {
		BlockCrackFramebufferProbe probe = this.pendingBlockCrackFramebufferProbe;
		if (probe == null || blockCrackFramebufferDiagnosticLogs >= BLOCK_OUTLINE_FRAMEBUFFER_DIAGNOSTIC_LIMIT) {
			return;
		}
		FramebufferProbeSample finalSample = this.readBlockOutlineProbeSample(probe.crop());
		if (finalSample != null) {
			this.logBlockCrackFramebufferProbe(stage, probe, finalSample);
			if (probe.stageIndex() >= 0 && probe.stageIndex() < blockCrackFramebufferDiagnosticStages.length) {
				blockCrackFramebufferDiagnosticStages[probe.stageIndex()] = true;
			}
			blockCrackFramebufferDiagnosticLogs++;
		}
		this.pendingBlockCrackFramebufferProbe = null;
	}

	private void logBlockOutlineFramebufferProbe(String stage, BlockOutlineFramebufferProbe probe, FramebufferProbeSample sample) {
		FramebufferProbeDelta beforeDelta = sample.deltaFrom(probe.beforeDraw());
		FramebufferProbeDelta afterDelta = probe.afterDraw() == null
			? new FramebufferProbeDelta(0, 0, 0L)
			: sample.deltaFrom(probe.afterDraw());
		LOGGER.info(
			"[MattMC graphics-audit] block-outline framebuffer stage={} route={} pos={} highContrast={} translucentPass={} crop={} "
				+ "drawFb={} readFb={} program={} viewport={} depthTest={} blend={} scissor={} "
				+ "changedFromBefore={} maxDeltaFromBefore={} sumDeltaFromBefore={} changedFromAfterDraw={} maxDeltaFromAfterDraw={} sumDeltaFromAfterDraw={} "
				+ "edgeSamples={} avgRgba={} minRgb={} maxRgb={} outlinePixels={}",
			stage,
			javaBlockOutlineRoute(),
			probe.blockPos(),
			probe.highContrast(),
			probe.translucentPass(),
			probe.crop().describe(),
			sample.drawFramebuffer(),
			sample.readFramebuffer(),
			sample.currentProgram(),
			sample.viewport(),
			sample.depthTest(),
			sample.blend(),
			sample.scissor(),
			beforeDelta.changedPixels(),
			beforeDelta.maxChannelDelta(),
			beforeDelta.sumChannelDelta(),
			afterDelta.changedPixels(),
			afterDelta.maxChannelDelta(),
			afterDelta.sumChannelDelta(),
			probe.edgeSamples().summary(sample, probe.beforeDraw()),
			sample.averageRgba(),
			sample.minRgb(),
			sample.maxRgb(),
				sample.outlinePixelSummary()
			);
		}

	private void logBlockCrackFramebufferProbe(String stage, BlockCrackFramebufferProbe probe, FramebufferProbeSample sample) {
		FramebufferProbeDelta beforeDelta = sample.deltaFrom(probe.beforeDraw());
		FramebufferProbeDelta afterDelta = probe.afterDraw() == null
			? new FramebufferProbeDelta(0, 0, 0L)
			: sample.deltaFrom(probe.afterDraw());
		LOGGER.info(
			"[MattMC graphics-audit] block-crack framebuffer stage={} route={} pos={} stageIndex={} faceCount={} crop={} "
				+ "drawFb={} readFb={} program={} viewport={} depthTest={} blend={} scissor={} "
				+ "changedFromBefore={} maxDeltaFromBefore={} sumDeltaFromBefore={} changedFromAfterDraw={} maxDeltaFromAfterDraw={} sumDeltaFromAfterDraw={} "
				+ "darkenedFootprintPixels={} brightenedFootprintPixels={} avgRgba={} minRgb={} maxRgb={}",
			stage,
			javaBlockOutlineRoute(),
			probe.blockPos(),
			probe.stageIndex(),
			probe.faceCount(),
			probe.crop().describe(),
			sample.drawFramebuffer(),
			sample.readFramebuffer(),
			sample.currentProgram(),
			sample.viewport(),
			sample.depthTest(),
			sample.blend(),
			sample.scissor(),
			beforeDelta.changedPixels(),
			beforeDelta.maxChannelDelta(),
			beforeDelta.sumChannelDelta(),
			afterDelta.changedPixels(),
			afterDelta.maxChannelDelta(),
			afterDelta.sumChannelDelta(),
			sample.darkenedPixelsFrom(probe.beforeDraw()),
			sample.brightenedPixelsFrom(probe.beforeDraw()),
			sample.averageRgba(),
			sample.minRgb(),
			sample.maxRgb()
		);
	}

	private FramebufferProbeCrop projectedBlockOutlineProbeCrop(BlockOutlineRenderState blockOutlineRenderState, PoseStack poseStack, Vec3 cameraPos) {
		return this.projectedBlockShapeProbeCrop(
			blockOutlineRenderState.pos(),
			blockOutlineRenderState.shape(),
			this.matrices != null ? this.matrices.modelView() : poseStack.last().pose(),
			this.blockOutlineProbeProjection,
			cameraPos,
			"projected-outline",
			48
		);
	}

	private FramebufferProbeCrop projectedBlockShapeProbeCrop(
		BlockPos blockPos,
		VoxelShape shape,
		Matrix4fc modelView,
		Matrix4fc projection,
		Vec3 cameraPos,
		String source,
		int padding
	) {
		int width = Math.max(1, this.minecraft.getMainRenderTarget().width);
		int height = Math.max(1, this.minecraft.getMainRenderTarget().height);
		double baseX = blockPos.getX() - cameraPos.x();
		double baseY = blockPos.getY() - cameraPos.y();
		double baseZ = blockPos.getZ() - cameraPos.z();
		ProjectedBounds bounds = new ProjectedBounds();
		shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
			for (double x : new double[] { minX, maxX }) {
				for (double y : new double[] { minY, maxY }) {
					for (double z : new double[] { minZ, maxZ }) {
						this.includeProjectedBlockOutlinePoint(bounds, modelView, projection, width, height, baseX + x, baseY + y, baseZ + z);
					}
				}
			}
		});
		if (!bounds.valid()) {
			return this.centerBlockOutlineProbeCrop();
		}
		int minX = Mth.clamp((int)Math.floor(bounds.minX) - padding, 0, width - 1);
		int maxX = Mth.clamp((int)Math.ceil(bounds.maxX) + padding, 0, width - 1);
		int minY = Mth.clamp((int)Math.floor(bounds.minY) - padding, 0, height - 1);
		int maxY = Mth.clamp((int)Math.ceil(bounds.maxY) + padding, 0, height - 1);
		if (maxX <= minX || maxY <= minY) {
			return this.centerBlockOutlineProbeCrop();
		}
		return new FramebufferProbeCrop(minX, minY, maxX - minX + 1, maxY - minY + 1, source);
	}

	private ProjectedOutlineEdgeSamples projectedBlockShapeEdgeSamples(
		BlockPos blockPos,
		VoxelShape shape,
		Matrix4fc modelView,
		Matrix4fc projection,
		Vec3 cameraPos,
		FramebufferProbeCrop crop,
		FramebufferProbeSample before
	) {
		int width = Math.max(1, this.minecraft.getMainRenderTarget().width);
		int height = Math.max(1, this.minecraft.getMainRenderTarget().height);
		double baseX = blockPos.getX() - cameraPos.x();
		double baseY = blockPos.getY() - cameraPos.y();
		double baseZ = blockPos.getZ() - cameraPos.z();
			List<FramebufferProbePoint> visible = new ArrayList<>();
		List<FramebufferProbePoint> hidden = new ArrayList<>();
		Set<String> visibleKeys = Sets.newHashSet();
		Set<String> hiddenKeys = Sets.newHashSet();
		shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
				this.addProjectedBoxEdgeSamples(visible, hidden, visibleKeys, hiddenKeys, modelView, projection, width, height, crop, before, baseX, baseY, baseZ, minX, minY, minZ, maxX, maxY, maxZ, 0);
				this.addProjectedBoxEdgeSamples(visible, hidden, visibleKeys, hiddenKeys, modelView, projection, width, height, crop, before, baseX, baseY, baseZ, minX, minY, minZ, maxX, maxY, maxZ, 1);
				this.addProjectedBoxEdgeSamples(visible, hidden, visibleKeys, hiddenKeys, modelView, projection, width, height, crop, before, baseX, baseY, baseZ, minX, minY, minZ, maxX, maxY, maxZ, 2);
			});
		return new ProjectedOutlineEdgeSamples(visible, hidden);
	}

	private void addProjectedBoxEdgeSamples(
		List<FramebufferProbePoint> visible,
		List<FramebufferProbePoint> hidden,
		Set<String> visibleKeys,
		Set<String> hiddenKeys,
		Matrix4fc modelView,
		Matrix4fc projection,
		int width,
		int height,
		FramebufferProbeCrop crop,
		FramebufferProbeSample before,
		double baseX,
		double baseY,
		double baseZ,
		double minX,
		double minY,
		double minZ,
		double maxX,
		double maxY,
		double maxZ,
		int axis
	) {
		double[][] fixedPairs = axis == 0
			? new double[][] { { minY, minZ }, { minY, maxZ }, { maxY, minZ }, { maxY, maxZ } }
			: axis == 1
				? new double[][] { { minX, minZ }, { minX, maxZ }, { maxX, minZ }, { maxX, maxZ } }
				: new double[][] { { minX, minY }, { minX, maxY }, { maxX, minY }, { maxX, maxY } };
		for (double[] fixed : fixedPairs) {
				for (int sample = 1; sample <= 3; sample++) {
				double t = sample / 4.0;
				double x = axis == 0 ? Mth.lerp(t, minX, maxX) : fixed[0];
				double y = axis == 1 ? Mth.lerp(t, minY, maxY) : axis == 0 ? fixed[0] : fixed[1];
				double z = axis == 2 ? Mth.lerp(t, minZ, maxZ) : axis == 0 ? fixed[1] : fixed[1];
				if (axis == 1) {
					z = fixed[1];
				} else if (axis == 2) {
					x = fixed[0];
					y = fixed[1];
				}
					FramebufferProbePoint point = this.projectBlockProbePoint(
						modelView,
						projection,
						width,
						height,
						crop,
						(baseX + x) * (1.0 - (1.0 / 256.0)),
						(baseY + y) * (1.0 - (1.0 / 256.0)),
						(baseZ + z) * (1.0 - (1.0 / 256.0))
					);
					if (point == null) {
						continue;
					}
					String key = point.x() + "," + point.y();
					boolean visibleEdge = point.depth() <= before.depthAt(point) + 0.0015F;
					if (visibleEdge) {
					if (visible.size() < 512 && visibleKeys.add(key)) {
						visible.add(point);
					}
				} else if (hidden.size() < 512 && hiddenKeys.add(key)) {
					hidden.add(point);
				}
			}
		}
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

	private void includeProjectedBlockOutlinePoint(
		ProjectedBounds bounds,
		Matrix4fc modelView,
		Matrix4fc projection,
		int width,
		int height,
		double x,
		double y,
		double z
	) {
		Vector4f clip = new Vector4f((float)x, (float)y, (float)z, 1.0F).mul(modelView).mul(projection);
		if (!Float.isFinite(clip.x()) || !Float.isFinite(clip.y()) || !Float.isFinite(clip.w()) || Math.abs(clip.w()) < 1.0E-5F) {
			return;
		}
		float ndcX = clip.x() / clip.w();
		float ndcY = clip.y() / clip.w();
		if (!Float.isFinite(ndcX) || !Float.isFinite(ndcY)) {
			return;
		}
		float screenX = (ndcX * 0.5F + 0.5F) * width;
		float screenY = (ndcY * 0.5F + 0.5F) * height;
		bounds.include(screenX, screenY);
	}

	private FramebufferProbeCrop centerBlockOutlineProbeCrop() {
		int width = Math.max(1, this.minecraft.getMainRenderTarget().width);
		int height = Math.max(1, this.minecraft.getMainRenderTarget().height);
		int cropWidth = Math.min(width, Math.max(96, width / 4));
		int cropHeight = Math.min(height, Math.max(96, height / 4));
		int x = Math.max(0, (width - cropWidth) / 2);
		int y = Math.max(0, (height - cropHeight) / 2);
		return new FramebufferProbeCrop(x, y, cropWidth, cropHeight, "center-fallback");
	}

	@Nullable
	private FramebufferProbeSample readBlockOutlineProbeSample(FramebufferProbeCrop crop) {
		try {
			VulkanicAPI.FramebufferProbeSnapshot snapshot = VulkanicAPI.readDrawFramebufferProbe(
				crop.x(),
				crop.y(),
				crop.width(),
				crop.height()
			);
				return new FramebufferProbeSample(
					snapshot.rgba(),
					snapshot.depth(),
					snapshot.readFramebuffer(),
					snapshot.drawFramebuffer(),
				snapshot.currentProgram(),
				snapshot.viewport(),
				snapshot.depthTest(),
				snapshot.blend(),
				snapshot.scissor(),
				crop.width(),
				crop.height()
			);
		} catch (Throwable throwable) {
			LOGGER.warn("[MattMC graphics-audit] block-outline framebuffer probe failed: {}", throwable.toString());
			return null;
		}
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

		BlockOutlineFramebufferProbe withAfterDraw(FramebufferProbeSample afterDraw) {
			return new BlockOutlineFramebufferProbe(this.blockPos, this.highContrast, this.translucentPass, this.crop, this.edgeSamples, this.beforeDraw, afterDraw);
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

		BlockCrackFramebufferProbe withAfterDraw(FramebufferProbeSample afterDraw) {
			return new BlockCrackFramebufferProbe(this.blockPos, this.stageIndex, this.faceCount, this.crop, this.beforeDraw, afterDraw);
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
		FramebufferProbeDelta deltaFrom(FramebufferProbeSample before) {
			int changedPixels = 0;
			int maxChannelDelta = 0;
			long sumChannelDelta = 0L;
			for (int i = 0; i < Math.min(this.rgba.length, before.rgba.length); i += 4) {
				boolean changed = false;
				for (int channel = 0; channel < 4; channel++) {
					int delta = Math.abs((this.rgba[i + channel] & 0xFF) - (before.rgba[i + channel] & 0xFF));
					if (delta != 0) {
						changed = true;
					}
					maxChannelDelta = Math.max(maxChannelDelta, delta);
					sumChannelDelta += delta;
				}
				if (changed) {
					changedPixels++;
				}
			}
			return new FramebufferProbeDelta(changedPixels, maxChannelDelta, sumChannelDelta);
		}

		int darkenedPixelsFrom(FramebufferProbeSample before) {
			int pixels = 0;
			for (int i = 0; i < Math.min(this.rgba.length, before.rgba.length); i += 4) {
				int beforeLum = (before.rgba[i] & 0xFF) + (before.rgba[i + 1] & 0xFF) + (before.rgba[i + 2] & 0xFF);
				int afterLum = (this.rgba[i] & 0xFF) + (this.rgba[i + 1] & 0xFF) + (this.rgba[i + 2] & 0xFF);
				if (beforeLum - afterLum >= 24) {
					pixels++;
				}
			}
			return pixels;
		}

		int brightenedPixelsFrom(FramebufferProbeSample before) {
			int pixels = 0;
			for (int i = 0; i < Math.min(this.rgba.length, before.rgba.length); i += 4) {
				int beforeLum = (before.rgba[i] & 0xFF) + (before.rgba[i + 1] & 0xFF) + (before.rgba[i + 2] & 0xFF);
				int afterLum = (this.rgba[i] & 0xFF) + (this.rgba[i + 1] & 0xFF) + (this.rgba[i + 2] & 0xFF);
				if (afterLum - beforeLum >= 24) {
					pixels++;
				}
			}
			return pixels;
		}

		String averageRgba() {
			long r = 0L;
			long g = 0L;
			long b = 0L;
			long a = 0L;
			int pixels = Math.max(1, this.rgba.length / 4);
			for (int i = 0; i < this.rgba.length; i += 4) {
				r += this.rgba[i] & 0xFF;
				g += this.rgba[i + 1] & 0xFF;
				b += this.rgba[i + 2] & 0xFF;
				a += this.rgba[i + 3] & 0xFF;
			}
			return String.format(Locale.ROOT, "%d,%d,%d,%d", r / pixels, g / pixels, b / pixels, a / pixels);
		}

		String minRgb() {
			int minR = 255;
			int minG = 255;
			int minB = 255;
			for (int i = 0; i < this.rgba.length; i += 4) {
				minR = Math.min(minR, this.rgba[i] & 0xFF);
				minG = Math.min(minG, this.rgba[i + 1] & 0xFF);
				minB = Math.min(minB, this.rgba[i + 2] & 0xFF);
			}
			return minR + "," + minG + "," + minB;
		}

		String maxRgb() {
			int maxR = 0;
			int maxG = 0;
			int maxB = 0;
			for (int i = 0; i < this.rgba.length; i += 4) {
				maxR = Math.max(maxR, this.rgba[i] & 0xFF);
				maxG = Math.max(maxG, this.rgba[i + 1] & 0xFF);
				maxB = Math.max(maxB, this.rgba[i + 2] & 0xFF);
			}
			return maxR + "," + maxG + "," + maxB;
		}

		String outlinePixelSummary() {
			int highContrastCyan = 0;
			int highContrastBlack = 0;
			int normalDark = 0;
			int saturatedGreenBlue = 0;
			for (int i = 0; i < this.rgba.length; i += 4) {
				int r = this.rgba[i] & 0xFF;
				int g = this.rgba[i + 1] & 0xFF;
				int b = this.rgba[i + 2] & 0xFF;
				int a = this.rgba[i + 3] & 0xFF;
				if (Math.abs(r - 87) <= 16 && Math.abs(g - 255) <= 16 && Math.abs(b - 225) <= 16) {
					highContrastCyan++;
				}
				if (r <= 8 && g <= 8 && b <= 8 && a >= 192) {
					highContrastBlack++;
				}
				if (r <= 24 && g <= 24 && b <= 24 && a >= 32) {
					normalDark++;
				}
				if (r <= 128 && g >= 192 && b >= 176) {
					saturatedGreenBlue++;
				}
			}
			return "cyan=" + highContrastCyan
				+ ",black=" + highContrastBlack
				+ ",normalDark=" + normalDark
				+ ",greenBlue=" + saturatedGreenBlue;
		}

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

			float depthAt(FramebufferProbePoint point) {
				int width = Math.max(1, this.sampleWidth);
				int index = point.y() * width + point.x();
				if (index < 0 || index >= this.depth.length) {
					return 1.0F;
				}
				float value = this.depth[index];
				return Float.isFinite(value) ? value : 1.0F;
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

	private void checkPoseStack(PoseStack poseStack) {
		if (!poseStack.isEmpty()) {
			throw new IllegalStateException("Pose stack not empty");
		}
	}

	private EntityRenderState extractEntity(Entity entity, float f) {
		return this.entityRenderDispatcher.extractEntity(entity, f);
	}

	private void scheduleTranslucentSectionResort(Vec3 vec3) {
		if (!this.visibleSections.isEmpty()) {
			BlockPos blockPos = BlockPos.containing(vec3);
			boolean bl = !blockPos.equals(this.lastTranslucentSortBlockPos);
			TranslucencyPointOfView translucencyPointOfView = new TranslucencyPointOfView();

			for (SectionRenderDispatcher.RenderSection renderSection : this.nearbyVisibleSections) {
				this.scheduleResort(renderSection, translucencyPointOfView, vec3, bl, true);
			}

			this.translucencyResortIterationIndex = this.translucencyResortIterationIndex % this.visibleSections.size();
			int i = Math.max(this.visibleSections.size() / 8, 15);

			while (i-- > 0) {
				int j = this.translucencyResortIterationIndex++ % this.visibleSections.size();
				this.scheduleResort(this.visibleSections.get(j), translucencyPointOfView, vec3, bl, false);
			}

			this.lastTranslucentSortBlockPos = blockPos;
		}
	}

	private void scheduleResort(
		SectionRenderDispatcher.RenderSection renderSection, TranslucencyPointOfView translucencyPointOfView, Vec3 vec3, boolean bl, boolean bl2
	) {
		translucencyPointOfView.set(vec3, renderSection.getSectionNode());
		boolean bl3 = renderSection.getSectionMesh().isDifferentPointOfView(translucencyPointOfView);
		boolean bl4 = bl && (translucencyPointOfView.isAxisAligned() || bl2);
		if ((bl4 || bl3) && !renderSection.transparencyResortingScheduled() && renderSection.hasTranslucentGeometry()) {
			renderSection.resortTransparency(this.sectionRenderDispatcher);
		}
	}

	private ChunkSectionsToRender prepareChunkRenders(Matrix4fc matrix4fc, double x, double y, double z) {
		// Sodium: Redirect to our renderer
		// Call Distant Horizons hooks before Sodium's terrain preparation
		for (net.minecraft.hooks.LevelRendererHooks hook : net.minecraft.hooks.HookRegistry.getLevelRendererHooks()) {
			hook.onBeforePrepareChunkRenders(matrix4fc, x, y, z);
		}
		
		ChunkSectionsToRender chunkSectionsToRender = new ChunkSectionsToRender(SODIUM_STATIC_MAP, -1, new GpuBufferSlice[0]);
		((SodiumChunkSection) (Object) chunkSectionsToRender).sodium$setRendering(renderer, matrices, x, y, z);
		return chunkSectionsToRender;
	}

	public void endFrame() {
		this.cloudRenderer.endFrame();
		if (this.renderer != null) {
			this.renderer.endFrame();
		}
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

	private void addSkyPass(FrameGraphBuilder frameGraphBuilder, Camera camera, GpuBufferSlice gpuBufferSlice) {
		FogType fogType = camera.getFluidInCamera();
		if (fogType != FogType.POWDER_SNOW && fogType != FogType.LAVA && !this.doesMobEffectBlockSky(camera)) {
			SkyRenderState skyRenderState = this.levelRenderState.skyRenderState;
			if (skyRenderState.skyType != DimensionSpecialEffects.SkyType.NONE) {
				FramePass framePass = frameGraphBuilder.addPass("sky");
				this.targets.main = framePass.readsAndWrites(this.targets.main);
				framePass.executes(
					() -> {
						iris$renderSkyPassBody();
						VulkanicAPI.setShaderFog(gpuBufferSlice);
						if (skyRenderState.skyType == DimensionSpecialEffects.SkyType.END) {
							this.skyRenderer.renderEndSky();
							if (skyRenderState.endFlashIntensity > 1.0E-5F) {
								PoseStack poseStack = new PoseStack();
								this.skyRenderer.renderEndFlash(poseStack, skyRenderState.endFlashIntensity, skyRenderState.endFlashXAngle, skyRenderState.endFlashYAngle);
			}
						} else {
							PoseStack poseStack = new PoseStack();
							float f = ARGB.redFloat(skyRenderState.skyColor);
							float g = ARGB.greenFloat(skyRenderState.skyColor);
							float h = ARGB.blueFloat(skyRenderState.skyColor);
							this.skyRenderer.renderSkyDisc(f, g, h);
							if (skyRenderState.isSunriseOrSunset) {
								this.skyRenderer.renderSunriseAndSunset(poseStack, skyRenderState.sunAngle, skyRenderState.sunriseAndSunsetColor);
							}

							this.skyRenderer
								.renderSunMoonAndStars(poseStack, skyRenderState.timeOfDay, skyRenderState.moonPhase, skyRenderState.rainBrightness, skyRenderState.starBrightness);
							if (skyRenderState.shouldRenderDarkDisc) {
								this.skyRenderer.renderDarkDisc();
							}
						}
							// Iris: Reset phase after sky rendering
							if (LevelRenderer.this.pipeline != null) {
								LevelRenderer.this.pipeline.setPhase(net.irisshaders.iris.pipeline.WorldRenderingPhase.NONE);
								LevelRenderer.this.pipeline.traceColortex0PhaseForDiagnostics("after-sky");
							}
					}
				);
			}
		}
	}

	public boolean doesMobEffectBlockSky(Camera camera) { // Made public for Iris sky rendering
		return !(camera.getEntity() instanceof LivingEntity livingEntity)
			? false
			: livingEntity.hasEffect(MobEffects.BLINDNESS) || livingEntity.hasEffect(MobEffects.DARKNESS);
	}

	private void compileSections(Camera camera) {
		ProfilerFiller profilerFiller = Profiler.get();
		profilerFiller.push("populateSectionsToCompile");
		RenderRegionCache renderRegionCache = new RenderRegionCache();
		BlockPos blockPos = camera.getBlockPosition();
		List<SectionRenderDispatcher.RenderSection> list = Lists.<SectionRenderDispatcher.RenderSection>newArrayList();

		for (SectionRenderDispatcher.RenderSection renderSection : this.visibleSections) {
			if (renderSection.isDirty() && (renderSection.getSectionMesh() != CompiledSectionMesh.UNCOMPILED || renderSection.hasAllNeighbors())) {
				boolean bl = false;
				if (this.minecraft.options.prioritizeChunkUpdates().get() == PrioritizeChunkUpdates.NEARBY) {
					BlockPos blockPos2 = SectionPos.of(renderSection.getSectionNode()).center();
					bl = blockPos2.distSqr(blockPos) < 768.0 || renderSection.isDirtyFromPlayer();
				} else if (this.minecraft.options.prioritizeChunkUpdates().get() == PrioritizeChunkUpdates.PLAYER_AFFECTED) {
					bl = renderSection.isDirtyFromPlayer();
				}

				if (bl) {
					profilerFiller.push("compileSectionSynchronously");
					this.sectionRenderDispatcher.rebuildSectionSync(renderSection, renderRegionCache);
					renderSection.setNotDirty();
					profilerFiller.pop();
				} else {
					list.add(renderSection);
				}
			}
		}

		profilerFiller.popPush("uploadSectionMeshes");
		this.sectionRenderDispatcher.uploadAllPendingUploads();
		profilerFiller.popPush("scheduleAsyncCompile");

		for (SectionRenderDispatcher.RenderSection renderSectionx : list) {
			renderSectionx.rebuildSectionAsync(renderRegionCache);
			renderSectionx.setNotDirty();
		}

		profilerFiller.popPush("scheduleTranslucentResort");
		this.scheduleTranslucentSectionResort(camera.getPosition());
		profilerFiller.pop();
	}

	private void renderHitOutline(
		PoseStack poseStack, VertexConsumer vertexConsumer, double d, double e, double f, BlockOutlineRenderState blockOutlineRenderState, int i
	) {
		BlockPos blockPos = blockOutlineRenderState.pos();
		if (SharedConstants.DEBUG_SHAPES) {
			ShapeRenderer.renderShape(
				poseStack,
				vertexConsumer,
				blockOutlineRenderState.shape(),
				blockPos.getX() - d,
				blockPos.getY() - e,
				blockPos.getZ() - f,
				ARGB.colorFromFloat(1.0F, 1.0F, 1.0F, 1.0F)
			);
			if (blockOutlineRenderState.collisionShape() != null) {
				ShapeRenderer.renderShape(
					poseStack,
					vertexConsumer,
					blockOutlineRenderState.collisionShape(),
					blockPos.getX() - d,
					blockPos.getY() - e,
					blockPos.getZ() - f,
					ARGB.colorFromFloat(0.4F, 0.0F, 0.0F, 0.0F)
				);
			}

			if (blockOutlineRenderState.occlusionShape() != null) {
				ShapeRenderer.renderShape(
					poseStack,
					vertexConsumer,
					blockOutlineRenderState.occlusionShape(),
					blockPos.getX() - d,
					blockPos.getY() - e,
					blockPos.getZ() - f,
					ARGB.colorFromFloat(0.4F, 0.0F, 1.0F, 0.0F)
				);
			}

			if (blockOutlineRenderState.interactionShape() != null) {
				ShapeRenderer.renderShape(
					poseStack,
					vertexConsumer,
					blockOutlineRenderState.interactionShape(),
					blockPos.getX() - d,
					blockPos.getY() - e,
					blockPos.getZ() - f,
					ARGB.colorFromFloat(0.4F, 0.0F, 0.0F, 1.0F)
				);
			}
		} else {
			ShapeRenderer.renderShape(poseStack, vertexConsumer, blockOutlineRenderState.shape(), blockPos.getX() - d, blockPos.getY() - e, blockPos.getZ() - f, i);
		}
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
		// Sodium: Redirect chunk updates to our renderer
		this.renderer.scheduleRebuildForBlockArea(minX, minY, minZ, maxX, maxY, maxZ, false);
	}

	public void setBlockDirty(BlockPos blockPos, BlockState blockState, BlockState blockState2) {
		if (this.minecraft.getModelManager().requiresRender(blockState, blockState2)) {
			// Sodium: Redirect chunk updates to our renderer
			this.renderer.scheduleRebuildForBlockArea(blockPos.getX() - 1, blockPos.getY() - 1, blockPos.getZ() - 1, 
				blockPos.getX() + 1, blockPos.getY() + 1, blockPos.getZ() + 1, true);
		}
	}

	public void setSectionDirtyWithNeighbors(int x, int y, int z) {
		// Sodium: Redirect chunk updates to our renderer  
		this.renderer.scheduleRebuildForChunks(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1, false);
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
		// Sodium: Redirect to renderer
		this.renderer.scheduleRebuildForChunk(x, y, z, important);
		
		// VoxelMap: Notify world update listener for chunk changes
		try {
			if (VoxelConstants.getVoxelMapInstance().getWorldUpdateListener() != null) {
				VoxelConstants.getVoxelMapInstance().getWorldUpdateListener().notifyObservers(x, z);
			}
		} catch (Exception e) {
			// Silently catch to avoid crashes
		}
	}

	public void onSectionBecomingNonEmpty(long l) {
		SectionRenderDispatcher.RenderSection renderSection = this.viewArea.getRenderSection(l);
		if (renderSection != null) {
			this.sectionOcclusionGraph.schedulePropagationFrom(renderSection);
		}
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
		// Sodium: Redirect to our renderer
		return this.renderer.isTerrainRenderComplete();
	}

	public void onChunkReadyToRender(ChunkPos chunkPos) {
		this.sectionOcclusionGraph.onChunkReadyToRender(chunkPos);
	}

	public void needsUpdate() {
		this.sectionOcclusionGraph.invalidate();
		this.cloudRenderer.markForRebuild();
		// Sodium: Schedule terrain update
		this.renderer.scheduleTerrainUpdate();
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
		// Sodium: Redirect to renderer
		return this.renderer.isSectionReady(blockPos.getX() >> 4, blockPos.getY() >> 4, blockPos.getZ() >> 4);
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

	// Iris compatibility: Named methods for mixin injection (replacing lambda targets)
	// These are called from the lambda bodies to provide stable mixin targets
	
	public void iris$renderSkyPassBody() {
		// Iris: From MixinLevelRenderer - Set CUSTOM_SKY phase
		if (this.pipeline != null) {
			this.pipeline.setPhase(net.irisshaders.iris.pipeline.WorldRenderingPhase.CUSTOM_SKY);
		}
		
		// Sodium + Iris: Prevents the sky layer from rendering when the fog distance is reduced
		// Fixes MC-152504 by canceling sky rendering when camera is submersed
		Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
		
		// When Iris pack is NOT active, use the advanced fog checks
		if (net.irisshaders.iris.Iris.getCurrentPack().isEmpty()) {
			net.minecraft.world.phys.Vec3 cameraPosition = camera.getPosition();
			boolean isSubmersed = camera.getFluidInCamera() != net.minecraft.world.level.material.FogType.NONE;
			boolean blockSky = this.doesMobEffectBlockSky(camera);
			boolean useThickFog = this.minecraft.level.effects().isFoggyAt(net.minecraft.util.Mth.floor(cameraPosition.x()),
				net.minecraft.util.Mth.floor(cameraPosition.y())) || this.minecraft.gui.getBossOverlay().shouldCreateWorldFog();
			
			if (isSubmersed || blockSky || useThickFog) {
				if (this.pipeline != null) {
					this.pipeline.setPhase(net.irisshaders.iris.pipeline.WorldRenderingPhase.NONE);
				}
				return; // Early exit cancels sky rendering
			}
		} else {
			// When Iris pack is active, use simple submersion check
			if (camera.getFluidInCamera() != net.minecraft.world.level.material.FogType.NONE || this.doesMobEffectBlockSky(camera)) {
				if (this.pipeline != null) {
					this.pipeline.setPhase(net.irisshaders.iris.pipeline.WorldRenderingPhase.NONE);
				}
				return; // Early exit cancels sky rendering
			}
		}
		
		// This method is injected into by Iris mixins for sky rendering phase changes
		// The actual sky rendering happens in addSkyPass lambda
		// Reset phase after sky rendering in the lambda - see addSkyPass
	}
	
	public void iris$renderMainPassBody() {
		// This method is injected into by Iris mixins for main pass phase changes
		// The actual main pass rendering happens in addMainPass lambda
	}
	
	public void iris$renderWeatherPassBody() {
		// Iris: From MixinLevelRenderer - Set RAIN_SNOW phase
		if (this.pipeline != null) {
			this.pipeline.setPhase(net.irisshaders.iris.pipeline.WorldRenderingPhase.RAIN_SNOW);
		}
		// This method is injected into by Iris mixins for weather rendering phase changes
		// The actual weather rendering happens in addWeatherPass lambda
	}
	
	public void iris$renderCloudsPassBody() {
		// Iris: From MixinLevelRenderer - Set CLOUDS phase
		if (this.pipeline != null) {
			this.pipeline.setPhase(net.irisshaders.iris.pipeline.WorldRenderingPhase.CLOUDS);
		}
		// This method is injected into by Iris mixins for clouds rendering phase changes
		// The actual clouds rendering happens in addCloudsPass lambda
	}
	
	public void iris$renderParticlesPassBody() {
		// This method is injected into by Iris mixins for particles rendering phase changes
		// The actual particles rendering happens in addParticlesPass lambda
	}
	
	public void iris$createWeatherBody() {
		// This method is injected into by Iris mixins for weather type creation
	}
	
	public void iris$renderWorldBorderBody() {
		// Iris: From MixinLevelRenderer - Set WORLD_BORDER phase
		if (this.pipeline != null) {
			this.pipeline.setPhase(net.irisshaders.iris.pipeline.WorldRenderingPhase.WORLD_BORDER);
		}
		// This method is injected into by Iris mixins for world border rendering phase changes
		// The actual world border rendering happens in addWeatherPass lambda
	}
	
	public void iris$beginDebugRender() {
		// Iris: From MixinLevelRenderer - Set DEBUG phase
		if (this.pipeline != null) {
			this.pipeline.setPhase(net.irisshaders.iris.pipeline.WorldRenderingPhase.DEBUG);
		}
		// This method is injected into by Iris mixins for debug rendering phase changes
		// The actual debug rendering happens in addMainPass lambda
	}
	
	public void iris$endDebugRender() {
		// Iris: From MixinLevelRenderer - Reset to NONE phase
		if (this.pipeline != null) {
			this.pipeline.setPhase(net.irisshaders.iris.pipeline.WorldRenderingPhase.NONE);
		}
		// This method is injected into by Iris mixins for debug rendering phase changes
		// The actual debug rendering happens in addMainPass lambda
	}
	
	public void iris$beginTranslucents() {
		// Iris: From MixinLevelRenderer - Begin hand and translucents
		if (this.pipeline != null) {
			this.pipeline.beginHand();
			net.irisshaders.iris.pathways.HandRenderer.INSTANCE.renderSolid(
				net.irisshaders.iris.uniforms.CapturedRenderingState.INSTANCE.getGbufferModelView(),
				net.minecraft.client.Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true),
				net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera(),
				net.minecraft.client.Minecraft.getInstance().gameRenderer,
				this.pipeline
			);
			net.minecraft.util.profiling.Profiler.get().popPush("iris_pre_translucent");
			this.pipeline.beginTranslucents();
		}
		// This method is injected into by Iris mixins for translucent rendering phase changes
		// The actual translucent rendering happens in addMainPass lambda
	}
	
	// Wrapper method for terrain chunk rendering - allows Iris mixins to intercept
	public void iris$renderTerrainGroup(ChunkSectionsToRender chunkSectionsToRender, ChunkSectionLayerGroup group) {
		// Iris: From MixinLevelRenderer - Set phase based on terrain render type
		if (this.pipeline != null) {
			this.pipeline.setPhase(net.irisshaders.iris.pipeline.WorldRenderingPhase.fromTerrainRenderType(group));
		}
		
		// Iris: From MixinLevelRenderer_SkipRendering - skip chunk rendering if pipeline requests
		if (net.irisshaders.iris.Iris.getPipelineManager().getPipelineNullable() instanceof net.irisshaders.iris.pipeline.IrisRenderingPipeline pipeline) {
			if (!pipeline.skipAllRendering()) {
				chunkSectionsToRender.renderGroup(group);
			}
		} else {
			chunkSectionsToRender.renderGroup(group);
		}
		
		// Iris: Reset phase after rendering
		if (this.pipeline != null) {
			this.pipeline.setPhase(net.irisshaders.iris.pipeline.WorldRenderingPhase.NONE);
		}
	}
	
	// Wrapper method for feature rendering in main pass - allows Iris mixins to intercept
	public void iris$renderAllFeaturesMain() {
		// Iris: From MixinLevelRenderer (fantastic) - Handle particle rendering phases
		net.irisshaders.iris.shaderpack.properties.ParticleRenderingSettings settings = net.irisshaders.iris.Iris.getPipelineManager().getPipeline()
			.map(net.irisshaders.iris.pipeline.WorldRenderingPipeline::getParticleRenderingSettings)
			.orElse(net.irisshaders.iris.shaderpack.properties.ParticleRenderingSettings.MIXED);
		
		if (settings == net.irisshaders.iris.shaderpack.properties.ParticleRenderingSettings.AFTER) {
			// After mode: just render features normally
			this.featureRenderDispatcher.renderAllFeatures();
			return;
		} else {
			// Before or Mixed mode: submit particles first, then render with phase control
			this.particlesRenderState.submit(this.submitNodeStorage, this.levelRenderState.cameraRenderState);
			((net.irisshaders.iris.fantastic.PhasedParticleEngine) this.featureRenderDispatcher.particleFeatureRenderer)
				.setParticleRenderingPhase(settings == net.irisshaders.iris.shaderpack.properties.ParticleRenderingSettings.BEFORE 
					? net.irisshaders.iris.fantastic.ParticleRenderingPhase.EVERYTHING 
					: net.irisshaders.iris.fantastic.ParticleRenderingPhase.OPAQUE);
			this.featureRenderDispatcher.renderAllFeatures();
			((net.irisshaders.iris.fantastic.PhasedParticleEngine) this.featureRenderDispatcher.particleFeatureRenderer)
				.setParticleRenderingPhase(net.irisshaders.iris.fantastic.ParticleRenderingPhase.EVERYTHING);
			
			if (settings == net.irisshaders.iris.shaderpack.properties.ParticleRenderingSettings.BEFORE) {
				this.particlesRenderState.reset();
			}
		}
	}
	
	// Wrapper method for particle submission - allows Iris mixins to intercept
	public void iris$submitParticles() {
		// Iris: From MixinLevelRenderer (fantastic) - Redirect to avoid item pickup particles in Mixed mode
		net.irisshaders.iris.shaderpack.properties.ParticleRenderingSettings settings = net.irisshaders.iris.Iris.getPipelineManager().getPipeline()
			.map(net.irisshaders.iris.pipeline.WorldRenderingPipeline::getParticleRenderingSettings)
			.orElse(net.irisshaders.iris.shaderpack.properties.ParticleRenderingSettings.MIXED);
		
		if (settings == net.irisshaders.iris.shaderpack.properties.ParticleRenderingSettings.MIXED) {
			((net.irisshaders.iris.mixinterface.ParticleRenderStateExtension) this.particlesRenderState)
				.submitWithoutItems(this.submitNodeStorage, this.levelRenderState.cameraRenderState);
		} else {
			this.particlesRenderState.submit(this.submitNodeStorage, this.levelRenderState.cameraRenderState);
		}
	}
	
	// Wrapper method for feature rendering in particles pass - allows Iris mixins to intercept
	public void iris$renderAllFeaturesParticles() {
		// Iris: From MixinLevelRenderer (fantastic) - Render translucent particles with phase control
		net.irisshaders.iris.shaderpack.properties.ParticleRenderingSettings settings = net.irisshaders.iris.Iris.getPipelineManager().getPipeline()
			.map(net.irisshaders.iris.pipeline.WorldRenderingPipeline::getParticleRenderingSettings)
			.orElse(net.irisshaders.iris.shaderpack.properties.ParticleRenderingSettings.MIXED);
		
		((net.irisshaders.iris.fantastic.PhasedParticleEngine) this.featureRenderDispatcher.particleFeatureRenderer)
			.setParticleRenderingPhase(settings == net.irisshaders.iris.shaderpack.properties.ParticleRenderingSettings.AFTER 
				? net.irisshaders.iris.fantastic.ParticleRenderingPhase.EVERYTHING 
				: net.irisshaders.iris.fantastic.ParticleRenderingPhase.TRANSLUCENT);
		this.featureRenderDispatcher.renderAllFeatures();
		((net.irisshaders.iris.fantastic.PhasedParticleEngine) this.featureRenderDispatcher.particleFeatureRenderer)
			.setParticleRenderingPhase(net.irisshaders.iris.fantastic.ParticleRenderingPhase.EVERYTHING);
	}
	
	// Iris: From shadows.MixinLevelRenderer - implement CullingDataCache interface
	@Override
	public void saveState() {
		iris$swap();
	}

	@Override
	public void restoreState() {
		iris$swap();
	}

	private void iris$swap() {
		// Swap the contents of the lists, not the references (visibleSections is final)
		ObjectArrayList<SectionRenderDispatcher.RenderSection> tmpList = new ObjectArrayList<>(visibleSections);
		visibleSections.clear();
		visibleSections.addAll(iris$savedRenderChunks);
		iris$savedRenderChunks.clear();
		iris$savedRenderChunks.addAll(tmpList);
		
		double tmp;

		tmp = prevCamRotX;
		prevCamRotX = iris$savedLastCameraPitch;
		iris$savedLastCameraPitch = tmp;

		tmp = prevCamRotY;
		prevCamRotY = iris$savedLastCameraYaw;
		iris$savedLastCameraYaw = tmp;
	}

	// Sodium: LevelRendererExtension interface implementation
	@Override
	public SodiumWorldRenderer sodium$getWorldRenderer() {
		return this.renderer;
	}

	@Override
	public void sodium$setMatrices(ChunkRenderMatrices matrices) {
		this.matrices = matrices;
	}

	@Override
	public ChunkRenderMatrices sodium$getMatrices() {
		return this.matrices;
	}
}
