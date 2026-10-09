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
import net.minecraft.util.ARGB;
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
	private static boolean rustHandPredicateLogged;
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
	// The regular renderer gets a tick before its first world frame. Rust
	// whole-frame presentation can legitimately render first, so retain the
	// neutral gameplay FOV until the first tick computes the live modifier.
	private float fovModifier = 1.0F;
	private float oldFovModifier = 1.0F;
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
	@Nullable
	private final GlobalSettingsUniform globalSettingsUniform;
	@Nullable
	private final PerspectiveProjectionMatrixBuffer levelProjectionMatrixBuffer;
	@Nullable
	private final PerspectiveProjectionMatrixBuffer handProjectionMatrixBuffer;
	@Nullable
	private final CachedPerspectiveProjectionMatrixBuffer hud3dProjectionMatrixBuffer;
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
		// Rust whole-frame extraction owns copied matrices and frame settings;
		// these Java compatibility UBOs are never consumed on that route.
		this.globalSettingsUniform = null;
		this.levelProjectionMatrixBuffer = null;
		this.handProjectionMatrixBuffer = null;
		this.hud3dProjectionMatrixBuffer = null;
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
			this.featureRenderDispatcher
		);
		
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
		net.vulkanic.gui.RustGalFrameCoordinator.shutdown();
		if (this.globalSettingsUniform != null) {
			this.globalSettingsUniform.close();
		}
		this.lightTexture.close();
		this.overlayTexture.close();
		this.resourcePool.close();
		this.guiRenderer.close();
		if (this.levelProjectionMatrixBuffer != null) {
			this.levelProjectionMatrixBuffer.close();
		}
		if (this.handProjectionMatrixBuffer != null) {
			this.handProjectionMatrixBuffer.close();
		}
		if (this.hud3dProjectionMatrixBuffer != null) {
			this.hud3dProjectionMatrixBuffer.close();
		}
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
		net.vulkanic.gui.RustGalFrameCoordinator.resize(i, j);
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
		net.minecraft.client.dev.GraphicsAuditHandFoilTiming.beginFrame();
		net.minecraft.client.dev.GraphicsAuditGroundFoilTiming.beginFrame();
		net.minecraft.client.dev.GraphicsAuditEquipmentFoilTiming.beginFrame();
		// Minecraft's render loop selects the Rust whole-frame shell for this
		// route. Keep this legacy entry point fail-closed as well so a
		// mod or future callsite cannot accidentally reopen Java world,
		// PostChain, GUI, or presenter work beside the Rust frame.
		throw new IllegalStateException("Java GameRenderer.render is unavailable while Rust Vulkan owns the whole frame");
	}

	public boolean renderRustVulkanWholeFrameShell(DeltaTracker deltaTracker, boolean bl) {
		// Observe one common frame boundary even while a reload overlay skips
		// GUI item collection; independent counters would otherwise drift.
		net.minecraft.client.dev.GraphicsAuditGuiFoilTiming.beginFrame();
		net.minecraft.client.dev.GraphicsAuditHandFoilTiming.beginFrame();
		net.minecraft.client.dev.GraphicsAuditGroundFoilTiming.beginFrame();
		net.minecraft.client.dev.GraphicsAuditEquipmentFoilTiming.beginFrame();
		ProfilerFiller profilerFiller = Profiler.get();
		boolean gameLoadFinished = this.minecraft.isGameLoadFinished();
		float f = net.vulkanic.bridge.RustGalDeterministicTiming.partialTick(deltaTracker);
		// The whole-frame route never enters render(), where the copied shader
		// clock is advanced for OpenGL. Seed it here before world semantics are
		// captured so source uniforms receive a real frame duration and counter.
		net.vulkanic.world.RustGalWorldPrimitiveRenderer.beginShaderPackFrame(Util.getNanos(), f);
		boolean captureTerrainParticleScenario = !System.getProperty("mattmc.dev.rustGalWorldMaterial.terrainParticleScenario", "").isBlank();
		if (captureTerrainParticleScenario
			&& this.minecraft.level instanceof net.minecraft.client.multiplayer.ClientLevel clientLevel
			&& this.minecraft.player != null) {
			// Seed before the shell's world predicate so the particle is available
			// to this frame's normal semantic extraction, even when beforeRender
			// observed the client before its level was published.
			clientLevel.spawnDeterministicTerrainParticlesForCapture();
			this.minecraft.particleEngine.flushPendingParticlesForCapture();
		}
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("game.rust-vulkan.frame-reset");
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("game.rust-vulkan.frame-reset.gui-state");
		this.guiRenderState.reset();
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("game.rust-vulkan.frame-reset.gui-state");
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("game.rust-vulkan.frame-reset.world-clear");
		net.vulkanic.world.RustGalWorldPrimitiveRenderer.clearFrame();
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("game.rust-vulkan.frame-reset.world-clear");
		// Consume the lightmap-dirty flag set by GameRenderer.tick before the
		// first semantic-world seed. The sky reads live world time directly, but
		// terrain samples this copied 16x16 lightmap; leaving the cached snapshot
		// untouched made a /time change brighten the sky while terrain stayed dark.
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("game.rust-vulkan.frame-reset.lightmap");
		this.lightTexture.updateLightTexture(f);
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("game.rust-vulkan.frame-reset.lightmap");
		// A normal loaded gameplay frame immediately enters the full semantic-world
		// path below, whose beginFrame seeds the same copied background, voxel, and
		// shader-environment values. Avoid doing that work twice. Keep the prime for
		// loading, GUI handoff, and any frame that will not reach beginFrame.
		boolean fullWorldSemanticFrameExpected = (gameLoadFinished || captureTerrainParticleScenario)
			&& bl && this.minecraft.level != null && this.minecraft.player != null;
		if (!fullWorldSemanticFrameExpected) {
			net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("game.rust-vulkan.frame-reset.world-prime");
			net.vulkanic.world.RustGalWorldPrimitiveRenderer.primeWorldSemanticState(
				this.minecraft.level,
				this.mainCamera,
				this.minecraft.getWindow().getWidth(),
				this.minecraft.getWindow().getHeight()
			);
			net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("game.rust-vulkan.frame-reset.world-prime");
		}
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("game.rust-vulkan.frame-reset.route-ensure");
		net.vulkanic.gui.RustGalGuiRenderer.beginWholeFrameVulkanFrame();
		// Activate any newly configured filesystem-backed shader-pack snapshot
		// before deriving camera matrices. Rust owns this copied semantic source;
		// the first frame must not use a stale Java/Iris readiness decision.
		net.vulkanic.gui.RustGalFrameCoordinator.refreshConfiguredShaderPackSourcesForSemanticFrame();
		this.overlayTexture.ensureSemanticAsset();
		this.minecraft.getTextureManager().ensureRustSemanticRoute();
		net.vulkanic.VulkanicAPI.ensureRustSemanticRoute();
		this.minecraft.getFontManager().ensureRustSemanticRoute();
		this.minecraft.levelRenderer.ensureRustSemanticRoute();
		this.resourcePool.ensureRustSemanticRoute();
		this.guiRenderer.ensureRustSemanticRoute();
		if (this.globalSettingsUniform != null) this.globalSettingsUniform.ensureRustSemanticRoute();
		if (this.levelProjectionMatrixBuffer != null) this.levelProjectionMatrixBuffer.ensureRustSemanticRoute();
		if (this.handProjectionMatrixBuffer != null) this.handProjectionMatrixBuffer.ensureRustSemanticRoute();
		if (this.hud3dProjectionMatrixBuffer != null) this.hud3dProjectionMatrixBuffer.ensureRustSemanticRoute();
		this.cubeMap.ensureRustSemanticRoute();
		this.fogRenderer.ensureRustSemanticRoute();
		this.lightTexture().ensureRustSemanticRoute();
		this.minecraft.levelRenderer.getWorldBorderRenderer().ensureRustSemanticRoute();
		this.minecraft.levelRenderer.getSkyRenderer().ensureRustSemanticRoute();
		this.minecraft.levelRenderer.getCloudRenderer().ensureRustSemanticRoute();
		this.getFeatureRenderDispatcher().particleFeatureRenderer.ensureRustSemanticRoute();
		this.minecraft.getMainRenderTarget().ensureRustSemanticRoute();
		this.minecraft.getShaderManager().ensureRustSemanticRoute();
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("game.rust-vulkan.frame-reset.route-ensure");
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("game.rust-vulkan.frame-reset");
		// Match the baseline world's CPU light-update ordering before copying any
		// terrain semantics. The selected Rust route owns rendering, while this
		// only publishes authoritative client world state for its source snapshot.
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("game.rust-vulkan.light-state-advance");
		this.minecraft.levelRenderer.advanceRustWholeFrameLightState();
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("game.rust-vulkan.light-state-advance");
		if (!rustHandPredicateLogged && Boolean.getBoolean("mattmc.dev.deterministicCameraCapture")
			&& this.minecraft.level != null && this.minecraft.player != null) {
			rustHandPredicateLogged = true;
			LOGGER.info("Rust Vulkan first-person predicate: gameLoadFinished={} blockOutline={} level={} player={} cameraType={} hideGui={} mode={} invisible={}",
				gameLoadFinished, bl, this.minecraft.level != null, this.minecraft.player != null,
				this.minecraft.options.getCameraType(), this.minecraft.options.hideGui,
				this.minecraft.gameMode == null ? "null" : this.minecraft.gameMode.getPlayerMode(),
				this.minecraft.player != null && this.minecraft.player.isInvisible());
		}
		if ((gameLoadFinished || captureTerrainParticleScenario) && bl && this.minecraft.level != null && this.minecraft.player != null) {
			net.voxelmap.VoxelConstants.assertRustWholeFrameWaypointsSupported();
			// Collision debugging is a world-render callsite too. The legacy
			// DebugRenderer is bypassed by the whole-frame shell, so copy its
			// bounded shape stream into Rust's explicit line primitive here.
			this.minecraft.levelRenderer.debugRenderer.collectRustCollisionSemantics(
				new PoseStack(), this.submitNodeStorage, this.mainCamera
			);
			this.minecraft.levelRenderer.debugRenderer.collectRustSolidFaceSemantics(
				this.submitNodeStorage, this.mainCamera
			);
			this.minecraft.levelRenderer.debugRenderer.collectRustSupportBlockSemantics(this.mainCamera);
			this.minecraft.levelRenderer.collectRustNeighborUpdateSemantics(this.mainCamera, this.submitNodeStorage);
			this.minecraft.levelRenderer.debugRenderer.collectRustStructureSemantics(this.mainCamera);
			this.minecraft.levelRenderer.collectRustGameEventListenerSemantics(this.mainCamera, this.submitNodeStorage);
			this.minecraft.levelRenderer.debugRenderer.collectRustRedstoneWireOrientationSemantics(this.mainCamera);
			this.minecraft.levelRenderer.debugRenderer.collectRustChunkBorderSemantics(this.mainCamera);
			this.minecraft.levelRenderer.debugRenderer.collectRustBreezeSemantics(this.mainCamera, this.submitNodeStorage);
			this.minecraft.levelRenderer.collectRustPathfindingSemantics(this.mainCamera, this.submitNodeStorage);
			this.minecraft.levelRenderer.debugRenderer.collectRustLightSectionSemantics(this.mainCamera, this.submitNodeStorage);
			this.minecraft.levelRenderer.debugRenderer.collectRustHeightMapSemantics(this.mainCamera, this.submitNodeStorage);
			this.minecraft.levelRenderer.debugRenderer.collectRustChunkCullingSemantics(this.mainCamera, this.submitNodeStorage);
			this.minecraft.levelRenderer.collectRustWaterSemantics(this.mainCamera, this.submitNodeStorage);
			this.minecraft.levelRenderer.collectRustLightSemantics(this.mainCamera);
			this.minecraft.levelRenderer.debugRenderer.collectRustVillageSectionSemantics(this.mainCamera, this.submitNodeStorage);
			this.minecraft.levelRenderer.collectRustChunkSemantics(this.mainCamera);
			this.minecraft.levelRenderer.debugRenderer.collectRustEntityBlockIntersectionSemantics(this.mainCamera, this.submitNodeStorage);
			this.minecraft.levelRenderer.collectRustGoalSelectorSemantics(this.mainCamera);
			this.minecraft.levelRenderer.collectRustRaidSemantics(this.mainCamera, this.submitNodeStorage);
			this.minecraft.levelRenderer.collectRustPoiSemantics(this.mainCamera, this.submitNodeStorage);
			this.minecraft.levelRenderer.collectRustBrainSemantics(this.mainCamera);
			this.minecraft.levelRenderer.collectRustBeeSemantics(this.mainCamera, this.submitNodeStorage);
			// The final whole-frame request carries the active semantic effect
			// identity. Rust resolves and validates its copied source/asset graph;
			// Java neither classifies nor executes that graph.
			LocalPlayer localPlayer = this.minecraft.player;
			if (this.minecraft.getCameraEntity() == null) {
				this.minecraft.setCameraEntity(localPlayer);
			}
			net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("game.rust-vulkan.camera-pick");
			this.pick(f);
			DeterministicCameraCapture.forceBlockOutlineTargetForDiagnostics(this.minecraft);
			net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("game.rust-vulkan.camera-pick");
			Entity entity = (Entity)(this.minecraft.getCameraEntity() == null ? localPlayer : this.minecraft.getCameraEntity());
			float g = this.minecraft.level.tickRateManager().isEntityFrozen(entity) ? 1.0F : f;
			net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("game.rust-vulkan.camera-setup");
			TaczCameraRecoil.apply(this.minecraft);
			this.mainCamera
				.setup(this.minecraft.level, entity, !this.minecraft.options.getCameraType().isFirstPerson(), this.minecraft.options.getCameraType().isMirrored(), g);
			this.extractCamera(f);
			this.renderDistance = this.minecraft.options.getEffectiveRenderDistance() * 16;
			boolean worldFog = this.minecraft.level.effects().isFoggyAt(
				this.mainCamera.getBlockPosition().getX(), this.mainCamera.getBlockPosition().getZ()
			) || this.minecraft.gui.getBossOverlay().shouldCreateWorldFog();
			// Whole-frame Vulkan does not use the Java fog UBO. Publish the same
			// computed gameplay fog record before Rust extracts source semantics.
			net.sodium.client.util.FogParameters wholeFrameFog = this.fogRenderer.collectFogParameters(
				this.mainCamera,
				this.minecraft.options.getEffectiveRenderDistance(),
				worldFog,
				deltaTracker,
				this.getDarkenWorldAmount(f),
				this.minecraft.level
			);
			int wholeFrameFogColor = ARGB.color(
				255,
				Mth.clamp(Math.round(wholeFrameFog.red() * 255.0F), 0, 255),
				Mth.clamp(Math.round(wholeFrameFog.green() * 255.0F), 0, 255),
				Mth.clamp(Math.round(wholeFrameFog.blue() * 255.0F), 0, 255)
			);
			float h = this.getFov(this.mainCamera, f, true);
			Matrix4f projection = this.getProjectionMatrix(h);
			net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("game.rust-vulkan.camera-setup");
			net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("game.rust-vulkan.matrix-build");
			PoseStack poseStack = new PoseStack();
			// Whole-frame Vulkan uses the source collector's immutable pack
			// readiness signal, never Iris's renderer-facing runtime state.
			areShadersOn = net.vulkanic.gui.RustGalFrameCoordinator.isRustShaderExecutionActive();
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
			net.minecraft.client.renderer.culling.Frustum rustFrameFrustum =
				net.minecraft.client.renderer.culling.Frustum.forCamera(view, projection, this.mainCamera.getPosition());
			// Observe immutable CPU camera data for the copied-world fluid test.
			net.minecraft.client.dev.GraphicsAuditFlowingWaterFixture.observeWorldView(this.minecraft, projection, view, this.mainCamera.getPosition());
			net.minecraft.client.dev.GraphicsAuditMixedFluidFixture.observeWorldView(this.minecraft, projection, view, this.mainCamera.getPosition());
			if (this.minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.CHUNK_SECTION_OCTREE)) {
				this.minecraft.levelRenderer.collectRustOctreeSemantics(this.mainCamera, this.submitNodeStorage,
					rustFrameFrustum);
			}
				net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("game.rust-vulkan.matrix-build");
				net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("game.rust-vulkan.semantic-world-extraction");
				net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.frame-begin");
				net.vulkanic.world.RustGalWorldPrimitiveRenderer.beginFrame(
					view,
					projection,
					this.minecraft.getWindow().getWidth(),
					this.minecraft.getWindow().getHeight(),
					this.minecraft.level,
					this.mainCamera
				);
				// The debug crosshair is part of this frame's Rust line stream. Enqueue
				// it only after beginFrame has seeded the bounded viewport and matrices;
				// real gameplay can reach the shell before any later world callback.
				if (this.minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.THREE_DIMENSIONAL_CROSSHAIR)
					&& this.minecraft.options.getCameraType().isFirstPerson()
					&& !this.minecraft.options.hideGui) {
					if (!net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueThreeDimensionalDebugCrosshair(
						this.mainCamera, this.minecraft.getWindow().getGuiScale()
					)) {
						throw new IllegalStateException("Rust whole-frame Vulkan 3D debug crosshair route rejected semantic line work");
					}
				}
				net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.frame-begin");
				if (net.vulkanic.world.DistantHorizonsSemanticCollector.requiresWholeFrameSemanticCollection()) {
					net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.distant-horizons.enqueue");
					com.seibel.distanthorizons.fabric.hooks.DistantHorizonsLevelRenderHook.collectRustOpaqueForWholeFrame(view, projection);
					net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.distant-horizons.enqueue");
				}
				net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.background.enqueue");
				net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueWorldBackground(
					this.minecraft.level, this.mainCamera, f, wholeFrameFogColor
				);
				net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.background.enqueue");
				net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.static-terrain.enqueue");
				// Publish the semantic block atlas before any terrain mesh is submitted.
				// Shader-pack terrain samples the explicit Rust-owned atlas; relying on a
				// nearby chunk build to publish it leaves source execution with valid draws
				// but an unbound/empty terrain texture.
				net.vulkanic.world.RustGalTerrainRenderer.ensureTerrainAtlasAssetForWorldMesh();
				this.minecraft.levelRenderer.enqueueRustGalStaticTerrainForWholeFrame(
					this.mainCamera,
					view,
					projection,
					wholeFrameFog,
					false
				);
				net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.static-terrain.enqueue");
				net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.weather.enqueue");
				this.minecraft.levelRenderer.enqueueRustGalWeatherForWholeFrame(this.mainCamera, f);
				net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.weather.enqueue");
				net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.sky.enqueue");
				this.minecraft.levelRenderer.enqueueRustGalSkyForWholeFrame(this.mainCamera, f);
				net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.sky.enqueue");
				net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.clouds.enqueue");
				this.minecraft.levelRenderer.enqueueRustGalCloudsForWholeFrame(this.mainCamera, f);
				net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.clouds.enqueue");
				net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.block-outline.enqueue");
				net.vulkanic.world.RustGalWorldPrimitiveRenderer.enqueueBlockOutline(this.minecraft, this, this.mainCamera);
				net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.block-outline.enqueue");
				net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.particles.enqueue");
				if (!System.getProperty("mattmc.dev.rustGalWorldMaterial.blockMarkerScenario", "").isBlank()
					&& this.minecraft.level instanceof net.minecraft.client.multiplayer.ClientLevel clientLevel) {
					clientLevel.spawnDeterministicBlockMarkerParticlesForCapture();
					this.minecraft.particleEngine.flushPendingParticlesForCapture();
				}
				if (!System.getProperty("mattmc.dev.rustGalWorldMaterial.terrainParticleScenario", "").isBlank()
					&& this.minecraft.level instanceof net.minecraft.client.multiplayer.ClientLevel clientLevel) {
					net.vulkanic.world.RustGalTerrainRenderer.ensureTerrainAtlasAssetForWorldMesh();
					clientLevel.spawnDeterministicTerrainParticlesForCapture();
					this.minecraft.particleEngine.flushPendingParticlesForCapture();
				}
				// Markers are extracted once with the visible particle group.
				this.minecraft.particleEngine.enqueueRustGalParticles(
					rustFrameFrustum, this.mainCamera, f
				);
				this.minecraft.particleEngine.enqueueRustGalModelParticles(
					this.mainCamera,
					f,
					this.submitNodeStorage,
					this.levelRenderState.cameraRenderState
				);
				net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.particles.enqueue");
			net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.block-crack.enqueue");
			this.minecraft.levelRenderer.enqueueRustGalBlockBreakingCracks(this.mainCamera);
			net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.block-crack.enqueue");
			net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.border.enqueue");
			this.minecraft.levelRenderer.enqueueRustGalWorldBorder(this.mainCamera);
			net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.border.enqueue");
				net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("world.indexed-mesh.enqueue");
				this.minecraft.levelRenderer.enqueueRustGalIndexedMeshFeaturesForWholeFrame(this.mainCamera, deltaTracker, view, projection);
					net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("world.indexed-mesh.enqueue");
				// Minecraft.useShaderTransparency() is semantic frame state. Rust now
				// owns the Fabulous attachment graph: material-only translucent work is
				// admitted there, while unsupported mixtures fail closed there. Keep the
				// diagnostic vocabulary here for source-contract compatibility, but do
				// not reject or render Fabulous work in Java. The old message,
				// "Fabulous transparency is unavailable until its Rust-owned external attachments
				// are wired", describes the Rust-side rejection boundary for all six distinct
				// Rust-owned external attachments.
				net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("game.rust-vulkan.semantic-world-extraction");
					// First-person rendering is a semantic callsite on the Rust-owned
					// route. The dedicated selected-source hand writer may admit baked
					// item meshes; ordinary unsupported hand families fail closed in
					// ItemInHandRenderer rather than reopening Java Vulkan rendering.
					if (this.minecraft.options.getCameraType().isFirstPerson()
						&& !this.minecraft.options.hideGui
						&& this.minecraft.gameMode.getPlayerMode() != GameType.SPECTATOR
						&& !(this.minecraft.getCameraEntity() instanceof LivingEntity sleepingEntity
							&& sleepingEntity.isSleeping())) {
						float handFov = this.getFov(this.mainCamera, f, false);
						Matrix4f handProjection = new Matrix4f().scale(1.0F, 1.0F, HAND_DEPTH_SCALE);
						handProjection.mul(this.getProjectionMatrix(handFov));
						net.vulkanic.world.RustGalWorldPrimitiveRenderer.beginFirstPersonFrame(handProjection, view);
						// The vanilla hand model is authored in camera space, but its
						// semantic PoseStack still carries the inverse of the world
						// model-view transform.  Keep that same explicit transform here:
						// Rust receives view * (view^-1 * handModel), rather than an
						// identity-pose model that is projected as world geometry.
						PoseStack handPoseStack = new PoseStack();
						handPoseStack.last().pose().set(view).invert();
						// Match Frozen's first-person pose domain exactly: the ordinary
						// hand path applies view bobbing after cancelling the world view
						// and before ItemInHandRenderer applies its hand/item transforms.
						// These are copied gameplay transforms, not Java renderer state.
						this.bobHurt(handPoseStack, f);
						if (this.minecraft.options.bobView().get()) {
							this.bobView(handPoseStack, f);
						}
						this.itemInHandRenderer.renderRustVulkanHands(
							f,
							handPoseStack,
							this.minecraft.player,
							this.minecraft.getEntityRenderDispatcher().getPackedLightCoords(this.minecraft.player, f),
							view,
							projection
						);
					}
			}
			net.vulkanic.gui.RustGalGuiItemRenderer.enqueueDebugStandard3dItem(this.guiRenderState);
			profilerFiller.push("rustVulkanWholeFrameGuiExtraction");
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("game.rust-vulkan.semantic-gui-extraction");
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("gui.state-create");
		GuiGraphics guiGraphics = new GuiGraphics(this.minecraft, this.guiRenderState);
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("gui.state-create");
		// The blur boundary is semantic frame data. RustGalFrameCoordinator
		// transports the boundary stratum and bounded radius through the whole-frame
		// ABI, and Rust's GUI frontend owns the snapshot/blur/composite passes.
		// Do not mark this implemented path unavailable or reopen Java PostChain.
		this.screenEffectRenderer.renderRustVulkanScreenEffects(guiGraphics, this.getFov(this.mainCamera, f, false));
		if (gameLoadFinished && bl && this.minecraft.level != null) {
			net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("gui.hud-render");
			this.minecraft.gui.render(guiGraphics, deltaTracker);
			net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("gui.hud-render");
			net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("gui.saving-indicator");
			this.minecraft.gui.renderSavingIndicator(guiGraphics, deltaTracker);
			net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("gui.saving-indicator");
		}
		// Match Frozen's lifecycle: the loading overlay remains a semantic producer
		// even while the normal in-game GUI predicate is false during reload.
		// Rust consumes these commands directly; this does not reopen Java GUI
		// rendering or presentation.
		if (this.minecraft.getOverlay() != null) {
			net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("gui.loading-overlay-semantic-extraction");
			this.minecraft.getOverlay().render(
				guiGraphics,
				(int)this.minecraft.mouseHandler.getScaledXPos(this.minecraft.getWindow()),
				(int)this.minecraft.mouseHandler.getScaledYPos(this.minecraft.getWindow()),
				deltaTracker.getGameTimeDeltaTicks()
			);
			net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("gui.loading-overlay-semantic-extraction");
		} else if (gameLoadFinished && this.minecraft.screen != null) {
			net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("gui.screen-semantic-extraction");
			// Every screen is extracted into GuiRenderState here. The Rust GUI
			// collector admits only explicit text, item, rectangle, and copied-blit
			// records; unsupported element families remain unavailable rather than
			// reopening a Java draw path. Keeping the screen callsite broad is
			// necessary for pause, inventory, container, and resource-pack screens.
			this.minecraft.screen.renderWithTooltipAndSubtitles(
				guiGraphics,
				(int)this.minecraft.mouseHandler.getScaledXPos(this.minecraft.getWindow()),
				(int)this.minecraft.mouseHandler.getScaledYPos(this.minecraft.getWindow()),
				deltaTracker.getGameTimeDeltaTicks()
			);
			if (net.minecraft.SharedConstants.DEBUG_CURSOR_POS) {
				this.minecraft.mouseHandler.drawDebugMouseInfo(this.minecraft.font, guiGraphics);
			}
			this.minecraft.screen.handleDelayedNarration();
			net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("gui.screen-semantic-extraction");
		}
		// Preserve the normal GUI callsite ordering on the Rust semantic route.
		// These producers append to GuiRenderState; they do not submit Java
		// buffers or render passes. Omitting them here silently drops toast,
		// debug, subtitle, and mod-hook overlays from whole-frame Vulkan.
		if (gameLoadFinished && bl) {
			net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("gui.deferred-subtitles-semantic-extraction");
			this.minecraft.gui.renderDeferredSubtitles();
			net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("gui.deferred-subtitles-semantic-extraction");
		}
		if (gameLoadFinished) {
			net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("gui.toast-semantic-extraction");
			this.minecraft.getToastManager().render(guiGraphics);
			net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("gui.toast-semantic-extraction");
			if (!(this.minecraft.screen instanceof DebugOptionsScreen)) {
				net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("gui.debug-overlay-semantic-extraction");
				this.minecraft.gui.renderDebugOverlay(guiGraphics);
				net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("gui.debug-overlay-semantic-extraction");
			}
		}
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("gui.hook-semantic-extraction");
		for (net.minecraft.hooks.GuiRenderHooks hook : net.minecraft.hooks.HookRegistry.getGuiRenderHooks()) {
			hook.onBeforeGuiRender(this.minecraft, this.guiRenderState, this.renderBuffers, deltaTracker, bl);
		}
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("gui.hook-semantic-extraction");
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("gui.text-semantic-enqueue");
		this.guiRenderer.collectRustGalTextSemantics();
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("gui.text-semantic-enqueue");
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("gui.item-semantic-enqueue");
		this.guiRenderer.collectRustGalItemSemantics();
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("gui.item-semantic-enqueue");
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("gui.picture-in-picture-semantic-enqueue");
		this.guiRenderer.collectRustGalPictureInPictureSemantics();
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("gui.picture-in-picture-semantic-enqueue");
		// Item activation is an explicit item-model submission. Extract it on the
		// Rust route without invoking ScreenEffectRenderer's Java buffer-backed
		// underwater/fire overlays.
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("gui.item-activation-semantic-enqueue");
		this.screenEffectRenderer.renderRustVulkanItemActivation(f, this.submitNodeStorage);
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("gui.item-activation-semantic-enqueue");
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("gui.rectangle-semantic-enqueue");
		this.guiRenderer.collectRustGalRectangleSemantics();
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("gui.rectangle-semantic-enqueue");
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("gui.file-backed-blit-semantic-enqueue");
		this.guiRenderer.collectRustGalCopiedBlitSemantics();
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("gui.file-backed-blit-semantic-enqueue");
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("game.rust-vulkan.semantic-gui-extraction");
		profilerFiller.popPush("rustVulkanWholeFramePresent");
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("game.rust-vulkan.frame-coordinator");
		// executeWholeFrameVulkan(this.minecraft, this.guiRenderState)
		int semanticMenuBlurRadius = this.minecraft.options.getMenuBackgroundBlurriness();
		if (this.minecraft.screen instanceof net.irisshaders.iris.gui.screen.ShaderPackScreen shaderPackScreen) {
			semanticMenuBlurRadius = (int) Math.min(semanticMenuBlurRadius, shaderPackScreen.blurTransition.getAsFloat());
		}
		net.vulkanic.gui.RustGalFrameCoordinator.executeWholeFrameVulkan(
			this.minecraft,
			this.guiRenderState,
			this.effectActive && this.postEffectId != null
				? this.postEffectId.toString()
				: Minecraft.useShaderTransparency() ? "minecraft:transparency" : null,
			new net.vulkanic.bridge.VulkanicGalBridge.EngineGlobalsRecord(
				this.minecraft.getWindow().getWidth(), this.minecraft.getWindow().getHeight(),
				this.minecraft.level == null ? 0L : this.minecraft.level.getGameTime(),
				deltaTracker.getGameTimeDeltaPartialTick(false),
				this.minecraft.options.glintStrength().get().floatValue(),
				semanticMenuBlurRadius)
		);
		this.guiRenderer.finishRustGalWholeFrame();
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("game.rust-vulkan.frame-coordinator");
		profilerFiller.pop();
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("gui.cursor-apply");
		guiGraphics.applyCursor(this.minecraft.getWindow());
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("gui.cursor-apply");
		net.minecraft.client.dev.GraphicsFrameBenchmark.beginPhase("game.rust-vulkan.frame-cleanup");
		this.submitNodeStorage.endFrame();
		this.featureRenderDispatcher.endFrame();
		this.resourcePool.endFrame();
		net.minecraft.client.dev.GraphicsFrameBenchmark.endPhase("game.rust-vulkan.frame-cleanup");
		return true;
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

	private void extractCamera(float f) {
		CameraRenderState cameraRenderState = this.levelRenderState.cameraRenderState;
		cameraRenderState.initialized = this.mainCamera.isInitialized();
		cameraRenderState.pos = this.mainCamera.getPosition();
		cameraRenderState.blockPos = this.mainCamera.getBlockPosition();
		cameraRenderState.entityPos = this.mainCamera.getEntity().getPosition(f);
		cameraRenderState.orientation = new Quaternionf(this.mainCamera.rotation());
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
