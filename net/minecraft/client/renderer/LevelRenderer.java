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
import net.minecraft.client.renderer.chunk.RenderRegionCache;
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
public class LevelRenderer implements ResourceManagerReloadListener, AutoCloseable {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final ResourceLocation TRANSPARENCY_POST_CHAIN_ID = ResourceLocation.withDefaultNamespace("transparency");
	private static final ResourceLocation ENTITY_OUTLINE_POST_CHAIN_ID = ResourceLocation.withDefaultNamespace("entity_outline");
	public static final int SECTION_SIZE = 16;
	public static final int HALF_SECTION_SIZE = 8;
	public static final int NEARBY_SECTION_DISTANCE_IN_BLOCKS = 32;
	private static final int MINIMUM_TRANSPARENT_SORT_COUNT = 15;
	private final Minecraft minecraft;
	private final EntityRenderDispatcher entityRenderDispatcher;
	private final BlockEntityRenderDispatcher blockEntityRenderDispatcher;
	private final RenderBuffers renderBuffers;
	private final SkyRenderer skyRenderer = new SkyRenderer();
	private final CloudRenderer cloudRenderer = new CloudRenderer();
	private final WorldBorderRenderer worldBorderRenderer = new WorldBorderRenderer();
	private final WeatherEffectRenderer weatherEffectRenderer = new WeatherEffectRenderer();
	private final ParticlesRenderState particlesRenderState = new ParticlesRenderState();
	public final DebugRenderer debugRenderer = new DebugRenderer();
	public final GameTestBlockHighlightRenderer gameTestBlockHighlightRenderer = new GameTestBlockHighlightRenderer();
	@Nullable
	private ClientLevel level;
	private final SectionOcclusionGraph sectionOcclusionGraph = new SectionOcclusionGraph();
	private final ObjectArrayList<SectionRenderDispatcher.RenderSection> visibleSections = new ObjectArrayList<>(10000);
	private final ObjectArrayList<SectionRenderDispatcher.RenderSection> nearbyVisibleSections = new ObjectArrayList<>(50);
	@Nullable
	private ViewArea viewArea;
	private int ticks;
	private final Int2ObjectMap<BlockDestructionProgress> destroyingBlocks = new Int2ObjectOpenHashMap<>();
	private final Long2ObjectMap<SortedSet<BlockDestructionProgress>> destructionProgress = new Long2ObjectOpenHashMap<>();
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

	public void onResourceManagerReload(ResourceManager resourceManager) {
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
	}

	private void clearVisibleSections() {
		this.visibleSections.clear();
		this.nearbyVisibleSections.clear();
	}

	public void allChanged() {
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
			this.viewArea = new ViewArea(this.sectionRenderDispatcher, this.level, this.minecraft.options.getEffectiveRenderDistance(), this);
			this.sectionOcclusionGraph.waitAndReset(this.viewArea);
			this.clearVisibleSections();
			Camera camera = this.minecraft.gameRenderer.getMainCamera();
			this.viewArea.repositionCamera(SectionPos.of(camera.getPosition()));
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
		if (this.viewArea == null) {
			return null;
		} else {
			int i = this.viewArea.sections.length;
			int j = this.countRenderedSections();
			return String.format(
				Locale.ROOT,
				"C: %d/%d %sD: %d, %s",
				j,
				i,
				this.minecraft.smartCull ? "(s) " : "",
				this.lastViewDistance,
				this.sectionRenderDispatcher == null ? "null" : this.sectionRenderDispatcher.getStats()
			);
		}
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
		int i = 0;

		for (SectionRenderDispatcher.RenderSection renderSection : this.visibleSections) {
			if (renderSection.getSectionMesh().hasRenderableLayers()) {
				i++;
			}
		}

		return i;
	}

	@Nullable
	public String getEntityStatistics() {
		return this.level == null
			? null
			: "E: " + this.levelRenderState.entityRenderStates.size() + "/" + this.level.getEntityCount() + ", SD: " + this.level.getServerSimulationDistance();
	}

	private void cullTerrain(Camera camera, Frustum frustum, boolean bl) {
		Vec3 vec3 = camera.getPosition();
		if (this.minecraft.options.getEffectiveRenderDistance() != this.lastViewDistance) {
			this.allChanged();
		}

		ProfilerFiller profilerFiller = Profiler.get();
		profilerFiller.push("repositionCamera");
		int i = SectionPos.posToSectionCoord(vec3.x());
		int j = SectionPos.posToSectionCoord(vec3.y());
		int k = SectionPos.posToSectionCoord(vec3.z());
		if (this.lastCameraSectionX != i || this.lastCameraSectionY != j || this.lastCameraSectionZ != k) {
			this.lastCameraSectionX = i;
			this.lastCameraSectionY = j;
			this.lastCameraSectionZ = k;
			this.viewArea.repositionCamera(SectionPos.of(vec3));
			this.worldBorderRenderer.invalidate();
		}

		this.sectionRenderDispatcher.setCameraPosition(vec3);
		double d = Math.floor(vec3.x / 8.0);
		double e = Math.floor(vec3.y / 8.0);
		double f = Math.floor(vec3.z / 8.0);
		if (d != this.prevCamX || e != this.prevCamY || f != this.prevCamZ) {
			this.sectionOcclusionGraph.invalidate();
		}

		this.prevCamX = d;
		this.prevCamY = e;
		this.prevCamZ = f;
		profilerFiller.pop();
		if (this.capturedFrustum == null) {
			boolean bl2 = this.minecraft.smartCull;
			if (bl && this.level.getBlockState(camera.getBlockPosition()).isSolidRender()) {
				bl2 = false;
			}

			profilerFiller.push("updateSOG");
			this.sectionOcclusionGraph.update(bl2, camera, frustum, this.visibleSections, this.level.getChunkSource().getLoadedEmptySections());
			profilerFiller.pop();
			double g = Math.floor(camera.getXRot() / 2.0F);
			double h = Math.floor(camera.getYRot() / 2.0F);
			if (this.sectionOcclusionGraph.consumeFrustumUpdate() || g != this.prevCamRotX || h != this.prevCamRotY) {
				profilerFiller.push("applyFrustum");
				this.applyFrustum(offsetFrustum(frustum));
				profilerFiller.pop();
				this.prevCamRotX = g;
				this.prevCamRotY = h;
			}
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
		profilerFiller.popPush("cullTerrain");
		this.cullTerrain(camera, frustum, this.minecraft.player.isSpectator());
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
		matrix4fStack.popMatrix();
		profilerFiller.pop();
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
			chunkSectionsToRender.renderGroup(ChunkSectionLayerGroup.OPAQUE);
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
			this.featureRenderDispatcher.renderAllFeatures();
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
			chunkSectionsToRender.renderGroup(ChunkSectionLayerGroup.TRANSLUCENT);
			profilerFiller.popPush("string");
			chunkSectionsToRender.renderGroup(ChunkSectionLayerGroup.TRIPWIRE);
			if (bl) {
				this.renderBlockOutline(bufferSource, poseStack, true, levelRenderState);
			}

			bufferSource.endBatch();
			profilerFiller.pop();
		});
	}

	private void addParticlesPass(FrameGraphBuilder frameGraphBuilder, GpuBufferSlice gpuBufferSlice) {
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

			this.particlesRenderState.submit(this.submitNodeStorage, this.levelRenderState.cameraRenderState);
			this.featureRenderDispatcher.renderAllFeatures();
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

		framePass.executes(() -> { iris$renderCloudsPassBody(); this.cloudRenderer.render(i, cloudStatus, g, vec3, f); });
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
			iris$renderWorldBorderBody();
			this.worldBorderRenderer.render(this.levelRenderState.worldBorderRenderState, vec3, i, f);
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

		for (Entity entity : this.level.entitiesForRendering()) {
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

	private void extractVisibleBlockEntities(Camera camera, float f, LevelRenderState levelRenderState) {
		Vec3 vec3 = camera.getPosition();
		double d = vec3.x();
		double e = vec3.y();
		double g = vec3.z();
		PoseStack poseStack = new PoseStack();

		for (SectionRenderDispatcher.RenderSection renderSection : this.visibleSections) {
			List<BlockEntity> list = renderSection.getSectionMesh().getRenderableBlockEntities();
			if (!list.isEmpty()) {
				for (BlockEntity blockEntity : list) {
					BlockPos blockPos = blockEntity.getBlockPos();
					SortedSet<BlockDestructionProgress> sortedSet = this.destructionProgress.get(blockPos.asLong());
					ModelFeatureRenderer.CrumblingOverlay crumblingOverlay;
					if (sortedSet != null && !sortedSet.isEmpty()) {
						poseStack.pushPose();
						poseStack.translate(blockPos.getX() - d, blockPos.getY() - e, blockPos.getZ() - g);
						crumblingOverlay = new ModelFeatureRenderer.CrumblingOverlay(((BlockDestructionProgress)sortedSet.last()).getProgress(), poseStack.last());
						poseStack.popPose();
					} else {
						crumblingOverlay = null;
					}

					BlockEntityRenderState blockEntityRenderState = this.blockEntityRenderDispatcher.tryExtractRenderState(blockEntity, f, crumblingOverlay);
					if (blockEntityRenderState != null) {
						levelRenderState.blockEntityRenderStates.add(blockEntityRenderState);
					}
				}
			}
		}

		Iterator<BlockEntity> iterator = this.level.getGloballyRenderedBlockEntities().iterator();

		while (iterator.hasNext()) {
			BlockEntity blockEntity2 = (BlockEntity)iterator.next();
			if (blockEntity2.isRemoved()) {
				iterator.remove();
			} else {
				BlockEntityRenderState blockEntityRenderState2 = this.blockEntityRenderDispatcher.tryExtractRenderState(blockEntity2, f, null);
				if (blockEntityRenderState2 != null) {
					levelRenderState.blockEntityRenderStates.add(blockEntityRenderState2);
				}
			}
		}
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
					VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.secondaryBlockOutline());
					this.renderHitOutline(poseStack, vertexConsumer, vec3.x, vec3.y, vec3.z, blockOutlineRenderState, -16777216);
				}

				VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.lines());
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

	private ChunkSectionsToRender prepareChunkRenders(Matrix4fc matrix4fc, double d, double e, double f) {
		ObjectListIterator<SectionRenderDispatcher.RenderSection> objectListIterator = this.visibleSections.listIterator(0);
		EnumMap<ChunkSectionLayer, List<RenderPass.Draw<GpuBufferSlice[]>>> enumMap = new EnumMap(ChunkSectionLayer.class);
		int i = 0;

		for (ChunkSectionLayer chunkSectionLayer : ChunkSectionLayer.values()) {
			enumMap.put(chunkSectionLayer, new ArrayList());
		}

		List<DynamicUniforms.Transform> list = new ArrayList();
		Vector4f vector4f = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
		Matrix4f matrix4f = new Matrix4f();

		while (objectListIterator.hasNext()) {
			SectionRenderDispatcher.RenderSection renderSection = (SectionRenderDispatcher.RenderSection)objectListIterator.next();
			SectionMesh sectionMesh = renderSection.getSectionMesh();

			for (ChunkSectionLayer chunkSectionLayer2 : ChunkSectionLayer.values()) {
				SectionBuffers sectionBuffers = sectionMesh.getBuffers(chunkSectionLayer2);
				if (sectionBuffers != null) {
					GpuBuffer gpuBuffer;
					VertexFormat.IndexType indexType;
					if (sectionBuffers.getIndexBuffer() == null) {
						if (sectionBuffers.getIndexCount() > i) {
							i = sectionBuffers.getIndexCount();
						}

						gpuBuffer = null;
						indexType = null;
					} else {
						gpuBuffer = sectionBuffers.getIndexBuffer();
						indexType = sectionBuffers.getIndexType();
					}

					BlockPos blockPos = renderSection.getRenderOrigin();
					int j = list.size();
					list.add(
						new DynamicUniforms.Transform(
							matrix4fc, vector4f, new Vector3f((float)(blockPos.getX() - d), (float)(blockPos.getY() - e), (float)(blockPos.getZ() - f)), matrix4f, 1.0F
						)
					);
					((List)enumMap.get(chunkSectionLayer2))
						.add(
							new RenderPass.Draw<GpuBufferSlice[]>(
								0,
								sectionBuffers.getVertexBuffer(),
								gpuBuffer,
								indexType,
								0,
								sectionBuffers.getIndexCount(),
								(gpuBufferSlicesx, uniformUploader) -> uniformUploader.upload("DynamicTransforms", gpuBufferSlicesx[j])
							)
						);
				}
			}
		}

		GpuBufferSlice[] gpuBufferSlices = RenderSystem.getDynamicUniforms()
			.writeTransforms((DynamicUniforms.Transform[])list.toArray(new DynamicUniforms.Transform[0]));
		return new ChunkSectionsToRender(enumMap, i, gpuBufferSlices);
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
					}
				);
			}
		}
	}

	private boolean doesMobEffectBlockSky(Camera camera) {
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

	public void setBlocksDirty(int i, int j, int k, int l, int m, int n) {
		for (int o = k - 1; o <= n + 1; o++) {
			for (int p = i - 1; p <= l + 1; p++) {
				for (int q = j - 1; q <= m + 1; q++) {
					this.setSectionDirty(SectionPos.blockToSectionCoord(p), SectionPos.blockToSectionCoord(q), SectionPos.blockToSectionCoord(o));
				}
			}
		}
	}

	public void setBlockDirty(BlockPos blockPos, BlockState blockState, BlockState blockState2) {
		if (this.minecraft.getModelManager().requiresRender(blockState, blockState2)) {
			this.setBlocksDirty(blockPos.getX(), blockPos.getY(), blockPos.getZ(), blockPos.getX(), blockPos.getY(), blockPos.getZ());
		}
	}

	public void setSectionDirtyWithNeighbors(int i, int j, int k) {
		this.setSectionRangeDirty(i - 1, j - 1, k - 1, i + 1, j + 1, k + 1);
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

	private void setSectionDirty(int i, int j, int k, boolean bl) {
		this.viewArea.setDirty(i, j, k, bl);
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
		return this.sectionRenderDispatcher.isQueueEmpty();
	}

	public void onChunkReadyToRender(ChunkPos chunkPos) {
		this.sectionOcclusionGraph.onChunkReadyToRender(chunkPos);
	}

	public void needsUpdate() {
		this.sectionOcclusionGraph.invalidate();
		this.cloudRenderer.markForRebuild();
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
		SectionRenderDispatcher.RenderSection renderSection = this.viewArea.getRenderSectionAt(blockPos);
		return renderSection != null && renderSection.sectionMesh.get() != CompiledSectionMesh.UNCOMPILED;
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
		// This method is injected into by Iris mixins for sky rendering phase changes
		// The actual sky rendering happens in addSkyPass lambda
	}
	
	public void iris$renderMainPassBody() {
		// This method is injected into by Iris mixins for main pass phase changes
		// The actual main pass rendering happens in addMainPass lambda
	}
	
	public void iris$renderWeatherPassBody() {
		// This method is injected into by Iris mixins for weather rendering phase changes
		// The actual weather rendering happens in addWeatherPass lambda
	}
	
	public void iris$renderCloudsPassBody() {
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
		// This method is injected into by Iris mixins for world border rendering phase changes
		// The actual world border rendering happens in addWeatherPass lambda
	}
	
	public void iris$beginDebugRender() {
		// This method is injected into by Iris mixins for debug rendering phase changes
		// The actual debug rendering happens in addMainPass lambda
	}
	
	public void iris$endDebugRender() {
		// This method is injected into by Iris mixins for debug rendering phase changes
		// The actual debug rendering happens in addMainPass lambda
	}
	
	public void iris$beginTranslucents() {
		// This method is injected into by Iris mixins for translucent rendering phase changes
		// The actual translucent rendering happens in addMainPass lambda
	}
}
