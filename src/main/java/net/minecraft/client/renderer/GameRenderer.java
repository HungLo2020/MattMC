package net.minecraft.client.renderer;

import net.blaze3d.ProjectionType;
import net.blaze3d.buffers.GpuBufferSlice;
import net.blaze3d.pipeline.RenderTarget;
import net.blaze3d.platform.GLX;
import net.blaze3d.platform.Lighting;
import net.blaze3d.platform.NativeImage;
import net.blaze3d.resource.CrossFrameResourcePool;
import net.blaze3d.shaders.ShaderType;
import net.blaze3d.systems.GpuDevice;
import net.blaze3d.systems.RenderSystem;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.util.profiling.TracyCompat;
import net.logging.LogUtils;
import net.math.Axis;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiFunction;

import net.alexscaves.server.entity.util.ShakesScreen;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.PanoramaTheme;
import net.minecraft.client.Screenshot;
import net.minecraft.client.dev.DeterministicCameraCapture;
import net.minecraft.client.entity.ClientAvatarState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.pip.GuiBannerResultRenderer;
import net.minecraft.client.gui.render.pip.GuiBookModelRenderer;
import net.minecraft.client.gui.render.pip.GuiEntityRenderer;
import net.minecraft.client.gui.render.pip.GuiProfilerChartRenderer;
import net.minecraft.client.gui.render.pip.GuiSignRenderer;
import net.minecraft.client.gui.render.pip.GuiSkinRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.screens.debug.DebugOptionsScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.AtlasManager;
import net.vulkanic.VulkanicAPI;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.tacz.TaczCameraRecoil;
import net.minecraft.client.tacz.TaczScopeData;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.Zone;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TaczMvpGunItem;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.waypoints.TrackedWaypoint.Projector;
import net.sodium.client.util.FogParameters;
import net.sodium.client.util.FogStorage;
import net.sodium.fabric.SodiumFogRenderHook;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class GameRenderer implements Projector, AutoCloseable, FogStorage {
	private static final ResourceLocation BLUR_POST_CHAIN_ID = ResourceLocation.withDefaultNamespace("blur");
	public static final int MAX_BLUR_RADIUS = 10;
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final boolean BLOCK_OUTLINE_PICK_DIAGNOSTICS = Boolean.getBoolean("mattmc.dev.blockOutlinePickDiagnostics");
	private static final int BLOCK_OUTLINE_PICK_DIAGNOSTIC_LIMIT = Math.max(0, Integer.getInteger("mattmc.dev.blockOutlinePickDiagnostics.maxLogs", 160));
	private static int blockOutlinePickDiagnosticLogs;
	public static final float PROJECTION_Z_NEAR = 0.05F;
	public static final float PROJECTION_3D_HUD_Z_FAR = 100.0F;
	private static final float HAND_DEPTH_SCALE = 0.125F;
	private static final float PORTAL_SPINNING_SPEED = 20.0F;
	private static final float NAUSEA_SPINNING_SPEED = 7.0F;
	private final Minecraft minecraft;
	private final RandomSource random = RandomSource.create();
	private float renderDistance;
	public final ItemInHandRenderer itemInHandRenderer;
	private final ScreenEffectRenderer screenEffectRenderer;
	private final RenderBuffers renderBuffers;
	private float spinningEffectTime;
	private float spinningEffectSpeed;
	// Iris: Merged from MixinModelViewBobbing
	private boolean areShadersOn;
	private float fovModifier;
	private float oldFovModifier;
	private float darkenWorldAmount;
	private float darkenWorldAmountO;
	private boolean renderBlockOutline = true;
	private long lastScreenshotAttempt;
	private boolean hasWorldScreenshot;
	private long lastActiveTime = Util.getMillis();
	private final LightTexture lightTexture;
	private final OverlayTexture overlayTexture = new OverlayTexture();
	public boolean panoramicMode; // Made public for Iris shader integration
	protected CubeMap cubeMap;
	protected PanoramaRenderer panorama;
	public final CrossFrameResourcePool resourcePool = new CrossFrameResourcePool(3); // Made public for Iris shader integration
	// VoxelMap: Made accessible
	public final FogRenderer fogRenderer = new FogRenderer();
	private final GuiRenderer guiRenderer;
	private final GuiRenderState guiRenderState;
	private final LevelRenderState levelRenderState = new LevelRenderState();
	private final SubmitNodeStorage submitNodeStorage;
	private final FeatureRenderDispatcher featureRenderDispatcher;
	@Nullable
	private ResourceLocation postEffectId;
	private boolean effectActive;
	private final Camera mainCamera = new Camera();
	private final Lighting lighting = new Lighting();
	private final GlobalSettingsUniform globalSettingsUniform = new GlobalSettingsUniform();
	private final PerspectiveProjectionMatrixBuffer levelProjectionMatrixBuffer = new PerspectiveProjectionMatrixBuffer("level");
	private final PerspectiveProjectionMatrixBuffer handProjectionMatrixBuffer = new PerspectiveProjectionMatrixBuffer("hand");
	private final CachedPerspectiveProjectionMatrixBuffer hud3dProjectionMatrixBuffer = new CachedPerspectiveProjectionMatrixBuffer("3d hud", 0.05F, 100.0F);
	// Screen shake support for ShakesScreen entities
	private static final double SCREEN_SHAKE_SEARCH_RADIUS = 64.0;
	private static final float MAX_SCREEN_SHAKE_AMOUNT = 2.0F;
	private static final float SCREEN_SHAKE_INTENSITY_XY = 0.2F;
	private static final float SCREEN_SHAKE_INTENSITY_Z = 0.5F;
	private int lastTremorTick = -1;
	private final float[] randomTremorOffsets = new float[3];

	public GameRenderer(Minecraft minecraft, ItemInHandRenderer itemInHandRenderer, RenderBuffers renderBuffers, BlockRenderDispatcher blockRenderDispatcher) {
		this.minecraft = minecraft;
		this.itemInHandRenderer = itemInHandRenderer;
		this.lightTexture = new LightTexture(this, minecraft);
		this.renderBuffers = renderBuffers;
		this.guiRenderState = new GuiRenderState();
		MultiBufferSource.BufferSource bufferSource = renderBuffers.bufferSource();
		AtlasManager atlasManager = minecraft.getAtlasManager();
		this.submitNodeStorage = new SubmitNodeStorage();
		this.featureRenderDispatcher = new FeatureRenderDispatcher(
			this.submitNodeStorage,
			blockRenderDispatcher,
			bufferSource,
			atlasManager,
			renderBuffers.outlineBufferSource(),
			renderBuffers.crumblingBufferSource(),
			minecraft.font
		);
		this.guiRenderer = new GuiRenderer(
			this.guiRenderState,
			bufferSource,
			this.submitNodeStorage,
			this.featureRenderDispatcher,
			List.of(
				new GuiEntityRenderer(bufferSource, minecraft.getEntityRenderDispatcher()),
				new GuiSkinRenderer(bufferSource),
				new GuiBookModelRenderer(bufferSource),
				new GuiBannerResultRenderer(bufferSource, atlasManager),
				new GuiSignRenderer(bufferSource, atlasManager),
				new GuiProfilerChartRenderer(bufferSource)
			)
		);
		
		// Iris: From MixinGameRenderer - log hardware information
		net.irisshaders.iris.Iris.logger.info("Hardware information:");
		net.irisshaders.iris.Iris.logger.info("CPU: " + GLX._getCpuInfo());
		GpuDevice.GpuDeviceInfo gpuDeviceInfo = VulkanicAPI.getBackendDeviceInfo();
		net.irisshaders.iris.Iris.logger.info("GPU: " + gpuDeviceInfo.rendererDisplayString() + " (" + gpuDeviceInfo.driverDisplayString() + ")");
		net.irisshaders.iris.Iris.logger.info("OS: " + System.getProperty("os.name") + " (" + System.getProperty("os.version") + ")");
		this.screenEffectRenderer = new ScreenEffectRenderer(minecraft, atlasManager, bufferSource);
		this.cubeMap = this.createCubeMap(minecraft.options.panoramaTheme().get());
		this.panorama = new PanoramaRenderer(this.cubeMap);
	}

	private CubeMap createCubeMap(PanoramaTheme theme) {
		String path = "textures/gui/title/background/" + theme.getPath() + "/panorama";
		return new CubeMap(ResourceLocation.withDefaultNamespace(path));
	}

	public synchronized void reloadPanorama(PanoramaTheme theme) {
		// Store old resources to close after creating new ones
		CubeMap oldCubeMap = this.cubeMap;
		
		// Create new panorama resources
		CubeMap newCubeMap = this.createCubeMap(theme);
		PanoramaRenderer newPanorama = new PanoramaRenderer(newCubeMap);
		
		// Register and load textures for new panorama
		if (this.minecraft != null && this.minecraft.getTextureManager() != null) {
			newCubeMap.registerAndLoadTextures(this.minecraft.getTextureManager());
		}
		
		// Atomically swap to new panorama
		this.cubeMap = newCubeMap;
		this.panorama = newPanorama;
		
		// Close old resources after swap
		if (oldCubeMap != null) {
			oldCubeMap.close();
		}
	}

	public void close() {
		net.vulkanic.gui.RustGalGuiRenderer.shutdown();
		this.globalSettingsUniform.close();
		this.lightTexture.close();
		this.overlayTexture.close();
		this.resourcePool.close();
		this.guiRenderer.close();
		this.levelProjectionMatrixBuffer.close();
		this.handProjectionMatrixBuffer.close();
		this.hud3dProjectionMatrixBuffer.close();
		this.lighting.close();
		this.cubeMap.close();
		this.fogRenderer.close();
		this.featureRenderDispatcher.close();
	}

	public SubmitNodeStorage getSubmitNodeStorage() {
		return this.submitNodeStorage;
	}

	public FeatureRenderDispatcher getFeatureRenderDispatcher() {
		return this.featureRenderDispatcher;
	}

	public LevelRenderState getLevelRenderState() {
		return this.levelRenderState;
	}

	public void setRenderBlockOutline(boolean bl) {
		this.renderBlockOutline = bl;
	}

	public void setPanoramicMode(boolean bl) {
		this.panoramicMode = bl;
	}

	public boolean isPanoramicMode() {
		return this.panoramicMode;
	}

	public void clearPostEffect() {
		this.postEffectId = null;
		this.effectActive = false;
	}

	public void togglePostEffect() {
		this.effectActive = !this.effectActive;
	}

	public void loadPostEffect(ResourceLocation resourceLocation) {
		this.postEffectId = resourceLocation;
		this.effectActive = true;
	}

	public void checkEntityPostEffect(@Nullable Entity entity) {
		switch (entity) {
			case Creeper creeper:
				this.setPostEffect(ResourceLocation.withDefaultNamespace("creeper"));
				break;
			case Spider spider:
				this.setPostEffect(ResourceLocation.withDefaultNamespace("spider"));
				break;
			case EnderMan enderMan:
				this.setPostEffect(ResourceLocation.withDefaultNamespace("invert"));
				break;
			case null:
			default:
				this.clearPostEffect();
		}
	}

	private void setPostEffect(ResourceLocation resourceLocation) {
		this.postEffectId = resourceLocation;
		this.effectActive = true;
	}

	public void processBlurEffect() {
		PostChain postChain = this.minecraft.getShaderManager().getPostChain(BLUR_POST_CHAIN_ID, LevelTargetBundle.MAIN_TARGETS);
		if (postChain != null) {
			postChain.process(this.minecraft.getMainRenderTarget(), this.resourcePool);
		}
	}

	public void preloadUiShader(ResourceProvider resourceProvider) {
		BiFunction<ResourceLocation, ShaderType, String> biFunction = (resourceLocation, shaderType) -> {
			ResourceLocation resourceLocation2 = shaderType.idConverter().idToFile(resourceLocation);

			try {
				Reader reader = resourceProvider.getResourceOrThrow(resourceLocation2).openAsReader();

				String var5;
				try {
					var5 = IOUtils.toString(reader);
				} catch (Throwable var8) {
					if (reader != null) {
						try {
							reader.close();
						} catch (Throwable var7) {
							var8.addSuppressed(var7);
						}
					}

					throw var8;
				}

				if (reader != null) {
					reader.close();
				}

				return var5;
			} catch (IOException var9) {
				LOGGER.error("Coudln't preload {} shader {}: {}", shaderType, resourceLocation, var9);
				return null;
			}
		};
		VulkanicAPI.precompileRenderPipeline(RenderPipelines.GUI, biFunction);
		VulkanicAPI.precompileRenderPipeline(RenderPipelines.GUI_TEXTURED, biFunction);
		if (TracyCompat.isAvailable()) {
			VulkanicAPI.precompileRenderPipeline(RenderPipelines.TRACY_BLIT, biFunction);
		}
	}

	public void tick() {
		this.tickFov();
		this.lightTexture.tick();
		LocalPlayer localPlayer = this.minecraft.player;
		if (this.minecraft.getCameraEntity() == null) {
			this.minecraft.setCameraEntity(localPlayer);
		}

		this.mainCamera.tick();
		this.itemInHandRenderer.tick();
		float f = localPlayer.portalEffectIntensity;
		float g = localPlayer.getEffectBlendFactor(MobEffects.NAUSEA, 1.0F);
		if (!(f > 0.0F) && !(g > 0.0F)) {
			this.spinningEffectSpeed = 0.0F;
		} else {
			this.spinningEffectSpeed = (f * 20.0F + g * 7.0F) / (f + g);
			this.spinningEffectTime = this.spinningEffectTime + this.spinningEffectSpeed;
		}

		if (this.minecraft.level.tickRateManager().runsNormally()) {
			this.darkenWorldAmountO = this.darkenWorldAmount;
			if (this.minecraft.gui.getBossOverlay().shouldDarkenScreen()) {
				this.darkenWorldAmount += 0.05F;
				if (this.darkenWorldAmount > 1.0F) {
					this.darkenWorldAmount = 1.0F;
				}
			} else if (this.darkenWorldAmount > 0.0F) {
				this.darkenWorldAmount -= 0.0125F;
			}

			this.screenEffectRenderer.tick();
			ProfilerFiller profilerFiller = Profiler.get();
			profilerFiller.push("levelRenderer");
			this.minecraft.levelRenderer.tick(this.mainCamera);
			profilerFiller.pop();
		}
	}

	@Nullable
	public ResourceLocation currentPostEffect() {
		return this.postEffectId;
	}

	public void resize(int i, int j) {
		this.resourcePool.clear();
		net.vulkanic.gui.RustGalGuiRenderer.resize(i, j);
		this.minecraft.levelRenderer.resize(i, j);
	}

	public void pick(float f) {
		Entity entity = this.minecraft.getCameraEntity();
		if (entity != null) {
			if (this.minecraft.level != null && this.minecraft.player != null) {
				Profiler.get().push("pick");
				double d = this.minecraft.player.blockInteractionRange();
				double e = this.minecraft.player.entityInteractionRange();
				HitResult hitResult = this.pick(entity, d, e, f);
				this.minecraft.hitResult = hitResult;
				this.minecraft.crosshairPickEntity = hitResult instanceof EntityHitResult entityHitResult ? entityHitResult.getEntity() : null;
				this.auditBlockOutlinePick(entity, hitResult, d, e, f);
				Profiler.get().pop();
			}
		}
	}

	private void auditBlockOutlinePick(Entity entity, HitResult hitResult, double blockRange, double entityRange, float partialTick) {
		if (!BLOCK_OUTLINE_PICK_DIAGNOSTICS || blockOutlinePickDiagnosticLogs >= BLOCK_OUTLINE_PICK_DIAGNOSTIC_LIMIT) {
			return;
		}
		blockOutlinePickDiagnosticLogs++;
		Vec3 eye = entity.getEyePosition(partialTick);
		Vec3 location = hitResult == null ? eye : hitResult.getLocation();
		double distance = location.distanceTo(eye);
		String blockPos = "none";
		String face = "none";
		if (hitResult instanceof BlockHitResult blockHitResult) {
			blockPos = blockHitResult.getBlockPos().toShortString();
			face = blockHitResult.getDirection().getSerializedName();
		}
		LOGGER.info(
			"[MattMC graphics-audit] block-outline pick type={} blockPos={} face={} distance={} blockRange={} entityRange={} "
				+ "eye=({}, {}, {}) yaw={} pitch={} shouldRender={} highContrast={} hideGui={} screen={} overlay={}",
			hitResult == null ? "null" : hitResult.getType(),
			blockPos,
			face,
			String.format(java.util.Locale.ROOT, "%.4f", distance),
			String.format(java.util.Locale.ROOT, "%.4f", blockRange),
			String.format(java.util.Locale.ROOT, "%.4f", entityRange),
			String.format(java.util.Locale.ROOT, "%.4f", eye.x),
			String.format(java.util.Locale.ROOT, "%.4f", eye.y),
			String.format(java.util.Locale.ROOT, "%.4f", eye.z),
			String.format(java.util.Locale.ROOT, "%.4f", entity.getYRot()),
			String.format(java.util.Locale.ROOT, "%.4f", entity.getXRot()),
			this.shouldRenderBlockOutline(),
			this.minecraft.options.highContrastBlockOutline().get(),
			this.minecraft.options.hideGui,
			this.minecraft.screen == null ? "none" : this.minecraft.screen.getClass().getSimpleName(),
			this.minecraft.getOverlay() == null ? "none" : this.minecraft.getOverlay().getClass().getSimpleName()
		);
	}

	private HitResult pick(Entity entity, double d, double e, float f) {
		double g = Math.max(d, e);
		double h = Mth.square(g);
		Vec3 vec3 = entity.getEyePosition(f);
		HitResult hitResult = entity.pick(g, f, false);
		double i = hitResult.getLocation().distanceToSqr(vec3);
		if (hitResult.getType() != Type.MISS) {
			h = i;
			g = Math.sqrt(i);
		}

		Vec3 vec32 = entity.getViewVector(f);
		Vec3 vec33 = vec3.add(vec32.x * g, vec32.y * g, vec32.z * g);
		float j = 1.0F;
		AABB aABB = entity.getBoundingBox().expandTowards(vec32.scale(g)).inflate(1.0, 1.0, 1.0);
		EntityHitResult entityHitResult = ProjectileUtil.getEntityHitResult(entity, vec3, vec33, aABB, EntitySelector.CAN_BE_PICKED, h);
		return entityHitResult != null && entityHitResult.getLocation().distanceToSqr(vec3) < i
			? filterHitResult(entityHitResult, vec3, e)
			: filterHitResult(hitResult, vec3, d);
	}

	private static HitResult filterHitResult(HitResult hitResult, Vec3 vec3, double d) {
		Vec3 vec32 = hitResult.getLocation();
		if (!vec32.closerThan(vec3, d)) {
			Vec3 vec33 = hitResult.getLocation();
			Direction direction = Direction.getApproximateNearest(vec33.x - vec3.x, vec33.y - vec3.y, vec33.z - vec3.z);
			return BlockHitResult.miss(vec33, direction, BlockPos.containing(vec33));
		} else {
			return hitResult;
		}
	}

	private void tickFov() {
		if (SystemTimeUniforms.isDeterministicTemporalParityEnabled()) {
			float modifier = SystemTimeUniforms.deterministicTemporalFovModifier();
			this.oldFovModifier = modifier;
			this.fovModifier = modifier;
			return;
		}

		float g;
		if (this.minecraft.getCameraEntity() instanceof AbstractClientPlayer abstractClientPlayer) {
			Options options = this.minecraft.options;
			boolean bl = options.getCameraType().isFirstPerson();
			float f = options.fovEffectScale().get().floatValue();
			g = abstractClientPlayer.getFieldOfViewModifier(bl, f);
		} else {
			g = 1.0F;
		}

		this.oldFovModifier = this.fovModifier;
		this.fovModifier = this.fovModifier + (g - this.fovModifier) * 0.5F;
		this.fovModifier = Mth.clamp(this.fovModifier, 0.1F, 1.5F);
	}

	public float getFov(Camera camera, float f, boolean bl) {
		if (this.panoramicMode) {
			return 90.0F;
		} else {
			if (SystemTimeUniforms.isDeterministicTemporalParityEnabled()) {
				f = SystemTimeUniforms.deterministicTemporalPartialTick();
			}

			float g = 70.0F;
			if (bl) {
				g = this.minecraft.options.fov().get().intValue();
				g *= Mth.lerp(f, this.oldFovModifier, this.fovModifier);
			}

			if (camera.getEntity() instanceof LivingEntity livingEntity && livingEntity.isDeadOrDying()) {
				float h = Math.min(livingEntity.deathTime + f, 20.0F);
				g /= (1.0F - 500.0F / (h + 500.0F)) * 2.0F + 1.0F;
			}

			FogType fogType = camera.getFluidInCamera();
			if (fogType == FogType.LAVA || fogType == FogType.WATER) {
				float h = this.minecraft.options.fovEffectScale().get().floatValue();
				g *= Mth.lerp(h, 1.0F, 0.85714287F);
			}

			if (this.minecraft.options.getCameraType().isFirstPerson()
				&& camera.getEntity() == this.minecraft.player
				&& this.minecraft.player != null) {
				ItemStack itemStack = this.minecraft.player.getMainHandItem();
				g = bl ? TaczScopeData.applyWorldFov(itemStack, g, f) : TaczScopeData.applyItemFov(itemStack, g, f);
			}

			return g;
		}
	}

	public void bobHurt(PoseStack poseStack, float f) { // Made public for Iris hand rendering
		if (this.minecraft.getCameraEntity() instanceof LivingEntity livingEntity) {
			float g = livingEntity.hurtTime - f;
			if (livingEntity.isDeadOrDying()) {
				float h = Math.min(livingEntity.deathTime + f, 20.0F);
				poseStack.mulPose(Axis.ZP.rotationDegrees(40.0F - 8000.0F / (h + 200.0F)));
			}

			if (g < 0.0F) {
				return;
			}

			g /= livingEntity.hurtDuration;
			g = Mth.sin(g * g * g * g * (float) Math.PI);
			float h = livingEntity.getHurtDir();
			poseStack.mulPose(Axis.YP.rotationDegrees(-h));
			float i = (float)(-g * 14.0 * this.minecraft.options.damageTiltStrength().get());
			poseStack.mulPose(Axis.ZP.rotationDegrees(i));
			poseStack.mulPose(Axis.YP.rotationDegrees(h));
		}
	}

	public void bobView(PoseStack poseStack, float f) { // Made public for Iris hand rendering
		if (this.minecraft.getCameraEntity() instanceof AbstractClientPlayer abstractClientPlayer) {
			ClientAvatarState clientAvatarState = abstractClientPlayer.avatarState();
			float g = clientAvatarState.getBackwardsInterpolatedWalkDistance(f);
			float h = clientAvatarState.getInterpolatedBob(f);
			poseStack.translate(Mth.sin(g * (float) Math.PI) * h * 0.5F, -Math.abs(Mth.cos(g * (float) Math.PI) * h), 0.0F);
			poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(g * (float) Math.PI) * h * 3.0F));
			poseStack.mulPose(Axis.XP.rotationDegrees(Math.abs(Mth.cos(g * (float) Math.PI - 0.2F) * h) * 5.0F));
		}
	}

	/**
	 * Applies screen shake effect from nearby ShakesScreen entities (e.g., Tremorsaurus).
	 * This creates a camera shake effect by translating the view matrix based on proximity
	 * to entities that implement the ShakesScreen interface.
	 * 
	 * @param poseStack The pose stack to apply translations to
	 * @param partialTick The partial tick time for smooth interpolation
	 */
	public void applyScreenShake(PoseStack poseStack, float partialTick) {
		Entity cameraEntity = this.minecraft.getCameraEntity();
		if (cameraEntity == null || this.minecraft.level == null) {
			return;
		}

		float tremorAmount = 0F;
		double distance = Double.MAX_VALUE;
		AABB aabb = cameraEntity.getBoundingBox().inflate(SCREEN_SHAKE_SEARCH_RADIUS);
		
		// Find nearby entities that implement ShakesScreen
		for (Entity entity : this.minecraft.level.getEntities(cameraEntity, aabb)) {
			if (entity instanceof ShakesScreen shakesScreen) {
				double entityDistance = entity.distanceTo(cameraEntity);
				if (shakesScreen.canFeelShake(cameraEntity) && entityDistance < distance) {
					distance = entityDistance;
					tremorAmount = Math.min((1F - (float) Math.min(1, distance / shakesScreen.getShakeDistance()))
							* Math.max(shakesScreen.getScreenShakeAmount(partialTick), 0F), MAX_SCREEN_SHAKE_AMOUNT);
				}
			}
		}

		if (tremorAmount > 0) {
			// Update random offsets once per tick for consistent shake within a frame
			if (this.lastTremorTick != cameraEntity.tickCount) {
				RandomSource randomSource = this.minecraft.level.random;
				this.randomTremorOffsets[0] = randomSource.nextFloat();
				this.randomTremorOffsets[1] = randomSource.nextFloat();
				this.randomTremorOffsets[2] = randomSource.nextFloat();
				this.lastTremorTick = cameraEntity.tickCount;
			}
			
			float intensity = (float)(tremorAmount * this.minecraft.options.screenEffectScale().get());
			poseStack.translate(
				this.randomTremorOffsets[0] * SCREEN_SHAKE_INTENSITY_XY * intensity,
				this.randomTremorOffsets[1] * SCREEN_SHAKE_INTENSITY_XY * intensity,
				this.randomTremorOffsets[2] * SCREEN_SHAKE_INTENSITY_Z * intensity
			);
		}
	}

	private void renderItemInHand(float f, boolean bl, Matrix4f matrix4f) {
		if (!this.panoramicMode) {
			this.featureRenderDispatcher.renderAllFeatures();
			this.renderBuffers.bufferSource().endBatch();
			Matrix4fStack matrix4fStack = VulkanicAPI.getModelViewStack();
			PoseStack poseStack = new PoseStack();
			boolean taczHandPath = this.useTaczHandModelViewPath();
			poseStack.pushPose();
			matrix4fStack.pushMatrix();
			if (taczHandPath) {
				PoseStack modelViewPoseStack = new PoseStack();
				this.bobHurt(modelViewPoseStack, f);
				if (this.minecraft.options.bobView().get()) {
					this.bobView(modelViewPoseStack, f);
				}
				matrix4fStack.set(modelViewPoseStack.last().pose());
			} else {
				poseStack.mulPose(matrix4f.invert(new Matrix4f()));
				matrix4fStack.mul(matrix4f);
				this.bobHurt(poseStack, f);
				if (this.minecraft.options.bobView().get()) {
					this.bobView(poseStack, f);
				}
			}

			if (this.minecraft.options.getCameraType().isFirstPerson()
				&& !bl
				&& !this.minecraft.options.hideGui
				&& this.minecraft.gameMode.getPlayerMode() != GameType.SPECTATOR) {
				this.lightTexture.turnOnLightLayer();
				// Iris: From MixinGameRenderer - disable vanilla hand rendering when shaders are active
				if (!net.irisshaders.iris.Iris.isPackInUseQuick()) {
					this.itemInHandRenderer
						.renderHandsWithItems(
							f,
							poseStack,
							this.minecraft.gameRenderer.getSubmitNodeStorage(),
							this.minecraft.player,
							this.minecraft.getEntityRenderDispatcher().getPackedLightCoords(this.minecraft.player, f)
						);
				}
				this.lightTexture.turnOffLightLayer();
			}

			matrix4fStack.popMatrix();
			poseStack.popPose();
		}
	}

	private boolean useTaczHandModelViewPath() {
		return !net.irisshaders.iris.Iris.isPackInUseQuick()
			&& this.minecraft.player != null
			&& this.minecraft.player.getMainHandItem().getItem() instanceof TaczMvpGunItem;
	}

	public Matrix4f getProjectionMatrix(float f) {
		Matrix4f matrix4f = new Matrix4f();
		return matrix4f.perspective(
			f * (float) (Math.PI / 180.0), (float)this.minecraft.getWindow().getWidth() / this.minecraft.getWindow().getHeight(), 0.05F, this.getDepthFar()
		);
	}

	public float getDepthFar() {
		return Math.max(this.renderDistance * 4.0F, this.minecraft.options.cloudRange().get() * 16);
	}

	public static float getNightVisionScale(LivingEntity livingEntity, float f) {
		MobEffectInstance mobEffectInstance = livingEntity.getEffect(MobEffects.NIGHT_VISION);
		// Iris: Origins compatibility - allow getNightVisionScale even if entity doesn't have night vision
		if (mobEffectInstance == null) {
			return 0.0F;
		}
		return !mobEffectInstance.endsWithin(200) ? 1.0F : 0.7F + Mth.sin((mobEffectInstance.getDuration() - f) * (float) Math.PI * 0.2F) * 0.3F;
	}

	public void render(DeltaTracker deltaTracker, boolean bl) {
		// Iris: From MixinGameRenderer - set real tick delta and begin frame timers
		float realTickDelta = SystemTimeUniforms.isDeterministicTemporalParityEnabled()
			? SystemTimeUniforms.deterministicTemporalPartialTick()
			: deltaTracker.getGameTimeDeltaPartialTick(true);
		net.irisshaders.iris.uniforms.CapturedRenderingState.INSTANCE.setRealTickDelta(realTickDelta);
		net.irisshaders.iris.uniforms.SystemTimeUniforms.COUNTER.beginFrame();
		net.irisshaders.iris.uniforms.SystemTimeUniforms.TIMER.beginFrame(net.minecraft.Util.getNanos());
		
		if (!this.minecraft.isWindowActive()
			&& this.minecraft.options.pauseOnLostFocus
			&& (!this.minecraft.options.touchscreen().get() || !this.minecraft.mouseHandler.isRightPressed())) {
			if (Util.getMillis() - this.lastActiveTime > 500L) {
				this.minecraft.pauseGame(false);
			}
		} else {
			this.lastActiveTime = Util.getMillis();
		}

		if (!this.minecraft.noRender) {
			// Iris: From MixinGameRenderer - modify blur for shader pack screen
			int blurRadius = this.minecraft.options.getMenuBackgroundBlurriness();
			if (this.minecraft.screen instanceof net.irisshaders.iris.gui.screen.ShaderPackScreen sps) {
				float f = Math.min(this.minecraft.options.getMenuBackgroundBlurriness(), sps.blurTransition.getAsFloat());
				blurRadius = (int) f;
			}
			
			this.globalSettingsUniform
				.update(
					this.minecraft.getWindow().getWidth(),
					this.minecraft.getWindow().getHeight(),
					this.minecraft.options.glintStrength().get(),
					this.minecraft.level == null ? 0L : this.minecraft.level.getGameTime(),
					deltaTracker,
					blurRadius
				);
			ProfilerFiller profilerFiller = Profiler.get();
			boolean bl2 = this.minecraft.isGameLoadFinished();
			int i = (int)this.minecraft.mouseHandler.getScaledXPos(this.minecraft.getWindow());
			int j = (int)this.minecraft.mouseHandler.getScaledYPos(this.minecraft.getWindow());
			if (bl2 && bl && this.minecraft.level != null) {
				profilerFiller.push("world");
				this.renderLevel(deltaTracker);
				this.tryTakeScreenshotIfNeeded();
				this.minecraft.levelRenderer.doEntityOutline();
				if (this.postEffectId != null && this.effectActive) {
					VulkanicAPI.resetTextureMatrix();
					PostChain postChain = this.minecraft.getShaderManager().getPostChain(this.postEffectId, LevelTargetBundle.MAIN_TARGETS);
					if (postChain != null) {
						postChain.process(this.minecraft.getMainRenderTarget(), this.resourcePool);
					}
				}

				profilerFiller.pop();
			}

			this.fogRenderer.endFrame();
			RenderTarget renderTarget = this.minecraft.getMainRenderTarget();
			VulkanicAPI.createCommandEncoder().clearDepthTexture(renderTarget.getDepthTexture(), 1.0);
			this.minecraft.gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_3D);
			this.guiRenderState.reset();
			profilerFiller.push("guiExtraction");
			GuiGraphics guiGraphics = new GuiGraphics(this.minecraft, this.guiRenderState);
			if (bl2 && bl && this.minecraft.level != null) {
				this.minecraft.gui.render(guiGraphics, deltaTracker);
			}

			if (this.minecraft.getOverlay() != null) {
				try {
					this.minecraft.getOverlay().render(guiGraphics, i, j, deltaTracker.getGameTimeDeltaTicks());
				} catch (Throwable var15) {
					CrashReport crashReport = CrashReport.forThrowable(var15, "Rendering overlay");
					CrashReportCategory crashReportCategory = crashReport.addCategory("Overlay render details");
					crashReportCategory.setDetail("Overlay name", () -> this.minecraft.getOverlay().getClass().getCanonicalName());
					throw new ReportedException(crashReport);
				}
			} else if (bl2 && this.minecraft.screen != null) {
				try {
					this.minecraft.screen.renderWithTooltipAndSubtitles(guiGraphics, i, j, deltaTracker.getGameTimeDeltaTicks());
				} catch (Throwable var14) {
					CrashReport crashReport = CrashReport.forThrowable(var14, "Rendering screen");
					CrashReportCategory crashReportCategory = crashReport.addCategory("Screen render details");
					crashReportCategory.setDetail("Screen name", () -> this.minecraft.screen.getClass().getCanonicalName());
					this.minecraft.mouseHandler.fillMousePositionDetails(crashReportCategory, this.minecraft.getWindow());
					throw new ReportedException(crashReport);
				}

				if (SharedConstants.DEBUG_CURSOR_POS) {
					this.minecraft.mouseHandler.drawDebugMouseInfo(this.minecraft.font, guiGraphics);
				}

				try {
					if (this.minecraft.screen != null) {
						this.minecraft.screen.handleDelayedNarration();
					}
				} catch (Throwable var13) {
					CrashReport crashReport = CrashReport.forThrowable(var13, "Narrating screen");
					CrashReportCategory crashReportCategory = crashReport.addCategory("Screen details");
					crashReportCategory.setDetail("Screen name", () -> this.minecraft.screen.getClass().getCanonicalName());
					throw new ReportedException(crashReport);
				}
			}

			if (bl2 && bl && this.minecraft.level != null) {
				this.minecraft.gui.renderSavingIndicator(guiGraphics, deltaTracker);
			}

			if (bl2) {
				Zone zone = profilerFiller.zone("toasts");

				try {
					this.minecraft.getToastManager().render(guiGraphics);
				} catch (Throwable var16) {
					if (zone != null) {
						try {
							zone.close();
						} catch (Throwable var12) {
							var16.addSuppressed(var12);
						}
					}

					throw var16;
				}

				if (zone != null) {
					zone.close();
				}
			}

			if (!(this.minecraft.screen instanceof DebugOptionsScreen)) {
				this.minecraft.gui.renderDebugOverlay(guiGraphics);
			}

				this.minecraft.gui.renderDeferredSubtitles();
				profilerFiller.popPush("guiRendering");

				// Call GUI render hooks to allow mods to render custom overlays
				for (net.minecraft.hooks.GuiRenderHooks hook : net.minecraft.hooks.HookRegistry.getGuiRenderHooks()) {
					hook.onBeforeGuiRender(this.minecraft, this.guiRenderState, this.renderBuffers, deltaTracker, bl);
				}

				this.guiRenderer.render(this.fogRenderer.getBuffer(FogRenderer.FogMode.NONE));
				this.guiRenderer.incrementFrameNumber();
				profilerFiller.pop();

				guiGraphics.applyCursor(this.minecraft.getWindow());
			this.submitNodeStorage.endFrame();
			this.featureRenderDispatcher.endFrame();
			this.resourcePool.endFrame();
		}
	}

	public boolean renderRustVulkanWholeFrameShell(DeltaTracker deltaTracker, boolean bl) {
		if (!net.vulkanic.gui.RustGalGuiRenderer.isWholeFrameVulkanActive()) {
			return false;
		}
		if (!VulkanicAPI.isVulkanBackendSelected()) {
			throw new IllegalStateException("Rust Vulkan whole-frame shell requires Vulkan backend selection");
		}
		ProfilerFiller profilerFiller = Profiler.get();
		boolean gameLoadFinished = this.minecraft.isGameLoadFinished();
		this.guiRenderState.reset();
		net.vulkanic.world.RustGalWorldPrimitiveRenderer.clearFrame();
		float f = SystemTimeUniforms.isDeterministicTemporalParityEnabled()
			? SystemTimeUniforms.deterministicTemporalPartialTick()
			: deltaTracker.getGameTimeDeltaPartialTick(true);
		if (gameLoadFinished && bl && this.minecraft.level != null && this.minecraft.player != null) {
			LocalPlayer localPlayer = this.minecraft.player;
			if (this.minecraft.getCameraEntity() == null) {
				this.minecraft.setCameraEntity(localPlayer);
			}
			this.pick(f);
			DeterministicCameraCapture.forceBlockOutlineTargetForDiagnostics(this.minecraft);
			Entity entity = (Entity)(this.minecraft.getCameraEntity() == null ? localPlayer : this.minecraft.getCameraEntity());
			float g = this.minecraft.level.tickRateManager().isEntityFrozen(entity) ? 1.0F : f;
			TaczCameraRecoil.apply(this.minecraft);
			this.mainCamera
				.setup(this.minecraft.level, entity, !this.minecraft.options.getCameraType().isFirstPerson(), this.minecraft.options.getCameraType().isMirrored(), g);
			this.extractCamera(f);
			float h = this.getFov(this.mainCamera, f, true);
			Matrix4f projection = this.getProjectionMatrix(h);
			PoseStack poseStack = new PoseStack();
			areShadersOn = net.irisshaders.iris.Iris.isPackInUseQuick();
			if (areShadersOn) {
				poseStack.pushPose();
				poseStack.last().pose().identity();
			}
			this.bobHurt(poseStack, this.mainCamera.getPartialTickTime());
			if (this.minecraft.options.bobView().get() && !areShadersOn) {
				this.bobView(poseStack, this.mainCamera.getPartialTickTime());
			}
			this.applyScreenShake(poseStack, this.mainCamera.getPartialTickTime());
			projection.mul(poseStack.last().pose());
			Quaternionf cameraRotation = this.mainCamera.rotation().conjugate(new Quaternionf());
			Matrix4f view = new Matrix4f();
			if (areShadersOn) {
				PoseStack stack = new PoseStack();
				stack.last().pose().set(view);
				float tickDelta = this.mainCamera.getPartialTickTime();
				this.bobHurt(stack, tickDelta);
				if (this.minecraft.options.bobView().get()) {
					this.bobView(stack, tickDelta);
				}
				this.applyScreenShake(stack, tickDelta);
				view.set(stack.last().pose());
				view.rotate(cameraRotation);
			} else {
				view.rotation(cameraRotation);
			}
			net.vulkanic.world.RustGalWorldPrimitiveRenderer.beginFrame(
				view,
				projection,
				this.minecraft.getWindow().getWidth(),
				this.minecraft.getWindow().getHeight()
			);
			net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueBlockOutline(this.minecraft, this, this.mainCamera);
			this.minecraft.levelRenderer.enqueueRustGalBlockBreakingCracks(this.mainCamera);
		}
		profilerFiller.push("rustVulkanWholeFrameGuiExtraction");
		GuiGraphics guiGraphics = new GuiGraphics(this.minecraft, this.guiRenderState);
		if (gameLoadFinished && bl && this.minecraft.level != null) {
			this.minecraft.gui.render(guiGraphics, deltaTracker);
			this.minecraft.gui.renderSavingIndicator(guiGraphics, deltaTracker);
		}
		profilerFiller.popPush("rustVulkanWholeFramePresent");
		net.vulkanic.gui.RustGalGuiRenderer.executeWholeFrameVulkan(this.minecraft, this.guiRenderState);
		profilerFiller.pop();
		guiGraphics.applyCursor(this.minecraft.getWindow());
		this.submitNodeStorage.endFrame();
		this.featureRenderDispatcher.endFrame();
		this.resourcePool.endFrame();
		return true;
	}

	private void tryTakeScreenshotIfNeeded() {
		if (!this.hasWorldScreenshot && this.minecraft.isLocalServer()) {
			long l = Util.getMillis();
			if (l - this.lastScreenshotAttempt >= 1000L) {
				this.lastScreenshotAttempt = l;
				IntegratedServer integratedServer = this.minecraft.getSingleplayerServer();
				if (integratedServer != null && !integratedServer.isStopped()) {
					integratedServer.getWorldScreenshotFile().ifPresent(path -> {
						if (Files.isRegularFile(path, new LinkOption[0])) {
							this.hasWorldScreenshot = true;
						} else {
							this.takeAutoScreenshot(path);
						}
					});
				}
			}
		}
	}

	private void takeAutoScreenshot(Path path) {
		if (this.minecraft.levelRenderer.countRenderedSections() > 10 && this.minecraft.levelRenderer.hasRenderedAllSections()) {
			Screenshot.takeScreenshot(this.minecraft.getMainRenderTarget(), nativeImage -> Util.ioPool().execute(() -> {
				int i = nativeImage.getWidth();
				int j = nativeImage.getHeight();
				int k = 0;
				int l = 0;
				if (i > j) {
					k = (i - j) / 2;
					i = j;
				} else {
					l = (j - i) / 2;
					j = i;
				}

				try (NativeImage nativeImage2 = new NativeImage(64, 64, false)) {
					nativeImage.resizeSubRectTo(k, l, i, j, nativeImage2);
					nativeImage2.writeToFile(path);
				} catch (IOException var16) {
					LOGGER.warn("Couldn't save auto screenshot", (Throwable)var16);
				} finally {
					nativeImage.close();
				}
			}));
		}
	}

	public boolean shouldRenderBlockOutline() { // Made public for Iris shader integration
		if (!this.renderBlockOutline) {
			return false;
		} else {
			Entity entity = this.minecraft.getCameraEntity();
			boolean bl = entity instanceof Player && !this.minecraft.options.hideGui;
			if (bl && !((Player)entity).getAbilities().mayBuild) {
				ItemStack itemStack = ((LivingEntity)entity).getMainHandItem();
				HitResult hitResult = this.minecraft.hitResult;
				if (hitResult != null && hitResult.getType() == Type.BLOCK) {
					BlockPos blockPos = ((BlockHitResult)hitResult).getBlockPos();
					BlockState blockState = this.minecraft.level.getBlockState(blockPos);
					if (this.minecraft.gameMode.getPlayerMode() == GameType.SPECTATOR) {
						bl = blockState.getMenuProvider(this.minecraft.level, blockPos) != null;
					} else {
						BlockInWorld blockInWorld = new BlockInWorld(this.minecraft.level, blockPos, false);
						Registry<Block> registry = this.minecraft.level.registryAccess().lookupOrThrow(Registries.BLOCK);
						bl = !itemStack.isEmpty() && (itemStack.canBreakBlockInAdventureMode(blockInWorld) || itemStack.canPlaceOnBlockInAdventureMode(blockInWorld));
					}
				}
			}

			return bl;
		}
	}

	public void renderLevel(DeltaTracker deltaTracker) {
		// Iris: Save shaders state (merged from MixinModelViewBobbing)
		areShadersOn = net.irisshaders.iris.Iris.isPackInUseQuick();
		
		float f = SystemTimeUniforms.isDeterministicTemporalParityEnabled()
			? SystemTimeUniforms.deterministicTemporalPartialTick()
			: deltaTracker.getGameTimeDeltaPartialTick(true);
		LocalPlayer localPlayer = this.minecraft.player;
		this.lightTexture.updateLightTexture(f);
		if (this.minecraft.getCameraEntity() == null) {
			this.minecraft.setCameraEntity(localPlayer);
		}

		this.pick(f);
		DeterministicCameraCapture.forceBlockOutlineTargetForDiagnostics(this.minecraft);
		ProfilerFiller profilerFiller = Profiler.get();
		profilerFiller.push("center");
		boolean bl = this.shouldRenderBlockOutline();
		profilerFiller.popPush("camera");
		Entity entity = (Entity)(this.minecraft.getCameraEntity() == null ? localPlayer : this.minecraft.getCameraEntity());
		float g = this.minecraft.level.tickRateManager().isEntityFrozen(entity) ? 1.0F : f;
		TaczCameraRecoil.apply(this.minecraft);
		this.mainCamera
			.setup(this.minecraft.level, entity, !this.minecraft.options.getCameraType().isFirstPerson(), this.minecraft.options.getCameraType().isMirrored(), g);
		this.extractCamera(f);
		this.renderDistance = this.minecraft.options.getEffectiveRenderDistance() * 16;
		float h = this.getFov(this.mainCamera, f, true);
		Matrix4f matrix4f = this.getProjectionMatrix(h);
		PoseStack poseStack = new PoseStack();
		
		// Iris: Separate view bobbing for shaders (merged from MixinModelViewBobbing)
		if (areShadersOn) {
			poseStack.pushPose();
			poseStack.last().pose().identity();
		}
		
		this.bobHurt(poseStack, this.mainCamera.getPartialTickTime());
		if (this.minecraft.options.bobView().get()) {
			// Iris: Skip bobView when shaders are active (merged from MixinModelViewBobbing)
			if (!areShadersOn) {
				this.bobView(poseStack, this.mainCamera.getPartialTickTime());
			}
		}
		// Apply screen shake from ShakesScreen entities
		this.applyScreenShake(poseStack, this.mainCamera.getPartialTickTime());

		matrix4f.mul(poseStack.last().pose());
		// Iris: Disable screen effect scale when shaders are on (merged from MixinModelViewBobbing)
		float i = areShadersOn ? 0.0f : this.minecraft.options.screenEffectScale().get().floatValue();
		float j = Mth.lerp(f, localPlayer.oPortalEffectIntensity, localPlayer.portalEffectIntensity);
		float k = localPlayer.getEffectBlendFactor(MobEffects.NAUSEA, f);
		float l = Math.max(j, k) * (i * i);
		if (l > 0.0F) {
			float m = 5.0F / (l * l + 5.0F) - l * 0.04F;
			m *= m;
			Vector3f vector3f = new Vector3f(0.0F, Mth.SQRT_OF_TWO / 2.0F, Mth.SQRT_OF_TWO / 2.0F);
			float n = (this.spinningEffectTime + f * this.spinningEffectSpeed) * (float) (Math.PI / 180.0);
			matrix4f.rotate(n, vector3f);
			matrix4f.scale(1.0F / m, 1.0F, 1.0F);
			matrix4f.rotate(-n, vector3f);
		}

		VulkanicAPI.setProjectionMatrix(this.levelProjectionMatrixBuffer.getBuffer(matrix4f), ProjectionType.PERSPECTIVE);
		Quaternionf quaternionf = this.mainCamera.rotation().conjugate(new Quaternionf());
		Matrix4f matrix4f2 = new Matrix4f();
		
		// Iris: Apply bobbing to model view when shaders are on (merged from MixinModelViewBobbing)
		if (areShadersOn) {
			PoseStack stack = new PoseStack();
			stack.last().pose().set(matrix4f2);

			float tickDelta = this.mainCamera.getPartialTickTime();

			this.bobHurt(stack, tickDelta);
			if (this.minecraft.options.bobView().get()) {
				this.bobView(stack, tickDelta);
			}
			// Apply screen shake from ShakesScreen entities
			this.applyScreenShake(stack, tickDelta);

			matrix4f2.set(stack.last().pose());

			float i2 = this.minecraft.options.screenEffectScale().get().floatValue();
			float j2 = Mth.lerp(f, localPlayer.oPortalEffectIntensity, localPlayer.portalEffectIntensity);
			float k2 = localPlayer.getEffectBlendFactor(MobEffects.NAUSEA, f);
			float l2 = Math.max(j2, k2) * i2 * i2;
			if (l2 > 0.0F) {
				float m2 = 5.0F / (l2 * l2 + 5.0F) - l2 * 0.04F;
				m2 *= m2;
				Vector3f vector3f2 = new Vector3f(0.0F, Mth.SQRT_OF_TWO / 2.0F, Mth.SQRT_OF_TWO / 2.0F);
				float n2 = (this.spinningEffectTime + f * this.spinningEffectSpeed) * ((float)Math.PI / 180F);
				matrix4f2.rotate(n2, vector3f2);
				matrix4f2.scale(1.0F / m2, 1.0F, 1.0F);
				matrix4f2.rotate(-n2, vector3f2);
			}

			matrix4f2.rotate(quaternionf);
		} else {
			matrix4f2.rotation(quaternionf);
		}
		profilerFiller.popPush("fog");
		boolean bl2 = this.minecraft.level.effects().isFoggyAt(this.mainCamera.getBlockPosition().getX(), this.mainCamera.getBlockPosition().getZ())
			|| this.minecraft.gui.getBossOverlay().shouldCreateWorldFog();
		Vector4f vector4f = this.fogRenderer
			.setupFog(this.mainCamera, this.minecraft.options.getEffectiveRenderDistance(), bl2, deltaTracker, this.getDarkenWorldAmount(f), this.minecraft.level);
		GpuBufferSlice gpuBufferSlice = this.fogRenderer.getBuffer(FogRenderer.FogMode.WORLD);
		profilerFiller.popPush("level");
		this.minecraft
			.levelRenderer
			.renderLevel(
				this.resourcePool, deltaTracker, bl, this.mainCamera, matrix4f2, matrix4f, this.getProjectionMatrixForCulling(h), gpuBufferSlice, vector4f, !bl2
			);
		profilerFiller.popPush("hand");
		boolean bl3 = this.minecraft.getCameraEntity() instanceof LivingEntity && ((LivingEntity)this.minecraft.getCameraEntity()).isSleeping();
		float itemFov = this.getFov(this.mainCamera, f, false);
		Matrix4f handProjection = new Matrix4f().scale(1.0F, 1.0F, HAND_DEPTH_SCALE);
		handProjection.mul(this.getProjectionMatrix(itemFov));
		VulkanicAPI.setProjectionMatrix(
			this.handProjectionMatrixBuffer.getBuffer(handProjection),
			ProjectionType.PERSPECTIVE
		);
		VulkanicAPI.createCommandEncoder().clearDepthTexture(this.minecraft.getMainRenderTarget().getDepthTexture(), 1.0);
		this.renderItemInHand(f, bl3, matrix4f2);
		VulkanicAPI.setProjectionMatrix(
			this.hud3dProjectionMatrixBuffer.getBuffer(this.minecraft.getWindow().getWidth(), this.minecraft.getWindow().getHeight(), itemFov),
			ProjectionType.PERSPECTIVE
		);
		profilerFiller.popPush("screenEffects");
		MultiBufferSource.BufferSource bufferSource = this.renderBuffers.bufferSource();
		this.screenEffectRenderer.renderScreenEffect(bl3, f, this.submitNodeStorage);
		this.featureRenderDispatcher.renderAllFeatures();
		bufferSource.endBatch();
		profilerFiller.pop();
		VulkanicAPI.setShaderFog(this.fogRenderer.getBuffer(FogRenderer.FogMode.NONE));
		if (this.minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.THREE_DIMENSIONAL_CROSSHAIR)
			&& this.minecraft.options.getCameraType().isFirstPerson()
			&& !this.minecraft.options.hideGui) {
			this.minecraft.getDebugOverlay().render3dCrosshair(this.mainCamera);
		}
		
		// Iris: From MixinGameRenderer - finalize game rendering for color space conversion
		net.irisshaders.iris.Iris.getPipelineManager().getPipeline().ifPresent(net.irisshaders.iris.pipeline.WorldRenderingPipeline::finalizeGameRendering);
	}

	private void extractCamera(float f) {
		CameraRenderState cameraRenderState = this.levelRenderState.cameraRenderState;
		cameraRenderState.initialized = this.mainCamera.isInitialized();
		cameraRenderState.pos = this.mainCamera.getPosition();
		cameraRenderState.blockPos = this.mainCamera.getBlockPosition();
		cameraRenderState.entityPos = this.mainCamera.getEntity().getPosition(f);
		cameraRenderState.orientation = new Quaternionf(this.mainCamera.rotation());
	}

	private Matrix4f getProjectionMatrixForCulling(float f) {
		float g = Math.max(f, this.minecraft.options.fov().get().intValue());
		return this.getProjectionMatrix(g);
	}

	public void resetData() {
		this.screenEffectRenderer.resetItemActivation();
		this.minecraft.getMapTextureManager().resetData();
		this.mainCamera.reset();
		this.hasWorldScreenshot = false;
	}

	public void displayItemActivation(ItemStack itemStack) {
		this.screenEffectRenderer.displayItemActivation(itemStack, this.random);
	}

	public Minecraft getMinecraft() {
		return this.minecraft;
	}

	public float getDarkenWorldAmount(float f) {
		return Mth.lerp(f, this.darkenWorldAmountO, this.darkenWorldAmount);
	}

	public float getRenderDistance() {
		return this.renderDistance;
	}

	public Camera getMainCamera() {
		return this.mainCamera;
	}

	public LightTexture lightTexture() {
		return this.lightTexture;
	}

	public OverlayTexture overlayTexture() {
		return this.overlayTexture;
	}

	public Vec3 projectPointToScreen(Vec3 vec3) {
		Matrix4f matrix4f = this.getProjectionMatrix(this.getFov(this.mainCamera, 0.0F, true));
		Quaternionf quaternionf = this.mainCamera.rotation().conjugate(new Quaternionf());
		Matrix4f matrix4f2 = new Matrix4f().rotation(quaternionf);
		Matrix4f matrix4f3 = matrix4f.mul(matrix4f2);
		Vec3 vec32 = this.mainCamera.getPosition();
		Vec3 vec33 = vec3.subtract(vec32);
		Vector3f vector3f = matrix4f3.transformProject(vec33.toVector3f());
		return new Vec3(vector3f);
	}

	public double projectHorizonToScreen() {
		float f = this.mainCamera.getXRot();
		if (f <= -90.0F) {
			return Double.NEGATIVE_INFINITY;
		} else if (f >= 90.0F) {
			return Double.POSITIVE_INFINITY;
		} else {
			float g = this.getFov(this.mainCamera, 0.0F, true);
			return Math.tan(f * (float) (Math.PI / 180.0)) / Math.tan(g / 2.0F * (float) (Math.PI / 180.0));
		}
	}

	public GlobalSettingsUniform getGlobalSettingsUniform() {
		return this.globalSettingsUniform;
	}

	public Lighting getLighting() {
		return this.lighting;
	}

	public void setLevel(@Nullable ClientLevel clientLevel) {
		if (clientLevel != null) {
			this.lighting.updateLevel(clientLevel.effects().constantAmbientLight());
		}
	}

	public synchronized PanoramaRenderer getPanorama() {
		return this.panorama;
	}

	@Override
	public FogParameters sodium$getFogParameters() {
		// Use the hook-based fog parameter storage instead of mixin
		return SodiumFogRenderHook.getFogParameters();
	}
}
