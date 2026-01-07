package net.minecraft.client.renderer;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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

import net.minecraft.client.renderer.chunk.SectionBuffers;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.chunk.TranslucencyPointOfView;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.client.renderer.debug.GameTestBlockHighlightRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class LevelRenderer implements ResourceManagerReloadListener, AutoCloseable, net.irisshaders.iris.shadows.CullingDataCache, net.caffeinemc.mods.sodium.client.world.LevelRendererExtension {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final ResourceLocation TRANSPARENCY_POST_CHAIN_ID = ResourceLocation.withDefaultNamespace("transparency");
	private static final ResourceLocation ENTITY_OUTLINE_POST_CHAIN_ID = ResourceLocation.withDefaultNamespace("entity_outline");
	public static final int SECTION_SIZE = 16;
	public static final int HALF_SECTION_SIZE = 8;
	public static final int NEARBY_SECTION_DISTANCE_IN_BLOCKS = 32;
	private static final int MINIMUM_TRANSPARENT_SORT_COUNT = 15;
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
	
	// Sodium: From LevelRendererMixin - fields for Sodium world renderer integration
	private static final EnumMap<ChunkSectionLayer, List<RenderPass.Draw<GpuBufferSlice[]>>> SODIUM_STATIC_MAP = new EnumMap<>(ChunkSectionLayer.class);
	private net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer renderer;
	private net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices matrices;

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
		this.renderer = new net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer(minecraft);
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
		net.caffeinemc.mods.sodium.client.gl.device.RenderDevice.enterManagedCode();
		try {
			this.renderer.setLevel(clientLevel);
		} finally {
			net.caffeinemc.mods.sodium.client.gl.device.RenderDevice.exitManagedCode();
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
		net.caffeinemc.mods.sodium.client.gl.device.RenderDevice.enterManagedCode();
		try {
			this.renderer.reload();
		} finally {
			net.caffeinemc.mods.sodium.client.gl.device.RenderDevice.exitManagedCode();
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
	var viewport = ((net.caffeinemc.mods.sodium.client.render.viewport.ViewportProvider) frustum).sodium$createViewport();
	var updateChunksImmediately = net.caffeinemc.mods.sodium.client.util.FlawlessFrames.isActive();

	int sectionX = SectionPos.posToSectionCoord(camera.getPosition().x());
	int sectionY = SectionPos.posToSectionCoord(camera.getPosition().y());
	int sectionZ = SectionPos.posToSectionCoord(camera.getPosition().z());

	if (this.lastCameraSectionX != sectionX || this.lastCameraSectionY != sectionY || this.lastCameraSectionZ != sectionZ) {
	this.lastCameraSectionX = sectionX;
	this.lastCameraSectionY = sectionY;
	this.lastCameraSectionZ = sectionZ;
	this.worldBorderRenderer.invalidate();
	}

	net.caffeinemc.mods.sodium.client.gl.device.RenderDevice.enterManagedCode();
	try {
	this.renderer.setupTerrain(camera, viewport, net.caffeinemc.mods.sodium.fabric.SodiumFogRenderHook.getFogParameters(), spectator, updateChunksImmediately, matrices);
	} finally {
	net.caffeinemc.mods.sodium.client.gl.device.RenderDevice.exitManagedCode();
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
		float fakeTickDelta = deltaTracker.getGameTimeDeltaPartialTick(false);
		net.irisshaders.iris.uniforms.CapturedRenderingState.INSTANCE.setTickDelta(fakeTickDelta);
		net.irisshaders.iris.uniforms.CapturedRenderingState.INSTANCE.setCloudTime((this.ticks + fakeTickDelta) * 0.03F);
		
		this.pipeline = net.irisshaders.iris.Iris.getPipelineManager().preparePipeline(net.irisshaders.iris.Iris.getCurrentDimension());
		this.disableFrustumCulling = this.pipeline.shouldDisableFrustumCulling();
		
		this.pipeline.beginLevelRendering();
		this.pipeline.setPhase(net.irisshaders.iris.pipeline.WorldRenderingPhase.NONE);
		net.irisshaders.iris.gl.IrisRenderSystem.backupAndDisableCullingState(this.pipeline.shouldDisableOcclusionCulling());
		
		if (net.irisshaders.iris.Iris.shouldActivateWireframe() && this.minecraft.isLocalServer()) {
			net.irisshaders.iris.gl.IrisRenderSystem.setPolygonMode(org.lwjgl.opengl.GL43C.GL_LINE);
		}
		
		// Iris: Begin level render immediate state (from MixinLevelRenderer vertices.immediate)
		net.irisshaders.iris.vertices.ImmediateState.isRenderingLevel = true;
		
		// Call registered hooks before rendering level
		for (net.minecraft.hooks.LevelRendererHooks hook : net.minecraft.hooks.HookRegistry.getLevelRendererHooks()) {
			hook.onBeforeRenderLevel(camera, matrix4f, matrix4f2);
		}
		
		float f = deltaTracker.getGameTimeDeltaPartialTick(false);
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
		this.matrices = new net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices(matrix4f2, matrix4f);
		
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
		Matrix4fStack matrix4fStack = RenderSystem.getModelViewStack();
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
				RenderSystem.getDevice()
					.createCommandEncoder()
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
			GpuBufferSlice params = RenderSystem.getShaderFog();
			this.pipeline.onBeginClear();
			RenderSystem.setShaderFog(params);
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
			net.irisshaders.iris.gl.IrisRenderSystem.setPolygonMode(org.lwjgl.opengl.GL43C.GL_FILL);
		}
		this.pipeline.finalizeLevelRendering();
		
		// Show beta warning once
		if (!this.warned) {
			this.warned = true;
			net.irisshaders.iris.Iris.getUpdateChecker().getBetaInfo().ifPresent(info ->
				net.minecraft.client.Minecraft.getInstance().gui.getChat().addMessage(
					net.minecraft.network.chat.Component.literal("A new beta is out for Iris " + info.betaTag + ". Please redownload it.")
						.withStyle(net.minecraft.ChatFormatting.BOLD, net.minecraft.ChatFormatting.RED)
				)
			);
		}
		
		net.irisshaders.iris.gl.IrisRenderSystem.restoreCullingState();
		this.pipeline = null;
		
		matrix4fStack.popMatrix();
		profilerFiller.pop();
		
		// Iris: End level render immediate state (from MixinLevelRenderer vertices.immediate)
		net.irisshaders.iris.vertices.ImmediateState.isRenderingLevel = false;
		
		// VoxelMap: Render waypoint beacons after level rendering
		try {
			com.mojang.blaze3d.vertex.PoseStack voxelmap_poseStack = new com.mojang.blaze3d.vertex.PoseStack();
			voxelmap_poseStack.pushPose();
			voxelmap_poseStack.last().pose().set(matrix4f);
			net.minecraft.client.renderer.MultiBufferSource.BufferSource bufferSource = this.minecraft.renderBuffers().bufferSource();
			com.mamiyaotaru.voxelmap.VoxelConstants.onRenderWaypoints(deltaTracker.getGameTimeDeltaPartialTick(false), voxelmap_poseStack, bufferSource, camera);
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
			RenderSystem.setShaderFog(gpuBufferSlice);
			Vec3 vec3 = levelRenderState.cameraRenderState.pos;
			double d = vec3.x();
			double e = vec3.y();
			double f = vec3.z();
			profilerFiller.push("terrain");
			ChunkSectionsToRender chunkSectionsToRender = this.prepareChunkRenders(matrix4f, d, e, f);
			iris$renderTerrainGroup(chunkSectionsToRender, ChunkSectionLayerGroup.OPAQUE);
			this.minecraft.gameRenderer.getLighting().setupFor(Lighting.Entry.LEVEL);
			if (resourceHandle3 != null) {
				resourceHandle3.get().copyDepthFrom(this.minecraft.getMainRenderTarget());
			}

			if (this.shouldShowEntityOutlines() && resourceHandle4 != null) {
				RenderTarget renderTarget = resourceHandle4.get();
				RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(renderTarget.getColorTexture(), 0, renderTarget.getDepthTexture(), 1.0);
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
			RenderSystem.setShaderFog(gpuBufferSlice);
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
			RenderSystem.setShaderFog(gpuBufferSlice);
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
			RenderSystem.setShaderFog(gpuBufferSlice);
			PoseStack poseStack = new PoseStack();
			MultiBufferSource.BufferSource bufferSource = this.renderBuffers.bufferSource();
			RenderSystem.outputColorTextureOverride = resourceHandle.get().getColorTextureView();
			RenderSystem.outputDepthTextureOverride = resourceHandle.get().getDepthTextureView();
			this.debugRenderer.render(poseStack, frustum, bufferSource, vec3.x, vec3.y, vec3.z, true);
			bufferSource.endLastBatch();
			RenderSystem.outputColorTextureOverride = null;
			RenderSystem.outputDepthTextureOverride = null;
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
				}
			}
		}
	}

	private void renderBlockOutline(MultiBufferSource.BufferSource bufferSource, PoseStack poseStack, boolean bl, LevelRenderState levelRenderState) {
		BlockOutlineRenderState blockOutlineRenderState = levelRenderState.blockOutlineRenderState;
		if (blockOutlineRenderState != null) {
			if (blockOutlineRenderState.isTranslucent() == bl) {
				Vec3 vec3 = levelRenderState.cameraRenderState.pos;
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
			}
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
		((net.caffeinemc.mods.sodium.client.util.SodiumChunkSection) (Object) chunkSectionsToRender).sodium$setRendering(renderer, matrices, x, y, z);
		return chunkSectionsToRender;
	}

	public void endFrame() {
		this.cloudRenderer.endFrame();
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
						RenderSystem.setShaderFog(gpuBufferSlice);
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
		// Sodium: This method is a no-op since visibleSections is always empty (ViewArea has 0 render distance)
		// All chunk compilation is handled by SodiumWorldRenderer.setupTerrain()
		// We keep the profiler calls for compatibility and call scheduleTranslucentSectionResort which has its own empty check
		ProfilerFiller profilerFiller = Profiler.get();
		profilerFiller.push("populateSectionsToCompile");
		profilerFiller.popPush("uploadSectionMeshes");
		profilerFiller.popPush("scheduleAsyncCompile");
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
			if (com.mamiyaotaru.voxelmap.VoxelConstants.getVoxelMapInstance().getWorldUpdateListener() != null) {
				com.mamiyaotaru.voxelmap.VoxelConstants.getVoxelMapInstance().getWorldUpdateListener().notifyObservers(x, z);
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
	public net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer sodium$getWorldRenderer() {
		return this.renderer;
	}

	@Override
	public void sodium$setMatrices(net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices matrices) {
		this.matrices = matrices;
	}

	@Override
	public net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices sodium$getMatrices() {
		return this.matrices;
	}
}