package net.minecraft.client.dev;

import net.minecraft.client.CameraType;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.GameType;
import net.minecraft.world.BossEvent.BossBarOverlay;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.network.chat.Component;
import net.vulkanic.VulkanicAPI;
import net.vulkanic.world.RustGalTerrainRenderer;
import net.vulkanic.world.RustGalWorldPrimitiveRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Development-only deterministic camera capture hook.
 *
 * <p>This is inert unless {@code -Dmattmc.dev.deterministicCameraCapture=true} is set.
 * It suppresses local movement input, applies a short deterministic camera sequence,
 * captures screenshots after a few rendered frames at each pose, and writes metadata
 * for capture harnesses.</p>
 */
public final class DeterministicCameraCapture {
	private static final Logger LOGGER = LoggerFactory.getLogger("MattMC-DeterministicCapture");
	private static final boolean ENABLED = Boolean.getBoolean("mattmc.dev.deterministicCameraCapture");
	private static final int FRAMES_PER_POSE = Math.max(1, Integer.getInteger("mattmc.dev.deterministicCameraCapture.framesPerPose", 8));
	private static final int ACK_TIMEOUT_FRAMES = Math.max(1, Integer.getInteger("mattmc.dev.deterministicCameraCapture.ackTimeoutFrames", 600));
	private static final int POSE_COUNT = Math.max(1, Math.min(8, Integer.getInteger("mattmc.dev.deterministicCameraCapture.poseCount", 4)));
	private static final float YAW_DELTA = Float.parseFloat(System.getProperty("mattmc.dev.deterministicCameraCapture.yawDelta", "35.0"));
	private static final boolean STOP_AFTER_COMPLETE = Boolean.parseBoolean(System.getProperty("mattmc.dev.deterministicCameraCapture.stopAfterComplete", "true"));
	private static final boolean INTERNAL_SCREENSHOTS = Boolean.parseBoolean(System.getProperty("mattmc.dev.deterministicCameraCapture.internalScreenshots", "false"));
	private static final int SETTLED_READY_FRAMES = Math.max(0, Integer.getInteger("mattmc.dev.deterministicCameraCapture.settledReadyFrames", 0));
	private static final int SETTLED_READY_MAX_WAIT_FRAMES = Math.max(1, Integer.getInteger("mattmc.dev.deterministicCameraCapture.settledReadyMaxWaitFrames", 900));
	private static final Set<String> SETTLED_READY_FAMILIES = parseSettledReadyFamilies();
	private static final boolean STATIC_TERRAIN_WATER_ANIMATION_DENSE_CAPTURE =
		Boolean.getBoolean("mattmc.dev.rustGalStaticTerrain.waterAnimationDenseCapture");
	private static final int STATIC_TERRAIN_WATER_ANIMATION_DENSE_FRAMES = Math.max(
		1,
		Math.min(120, Integer.getInteger("mattmc.dev.rustGalStaticTerrain.waterAnimationDenseFrames", 24))
	);
	private static final String FORCED_CAMERA_TYPE = System.getProperty("mattmc.dev.deterministicCameraCapture.cameraType", "").trim();
	private static final String FORCED_GAME_MODE = System.getProperty("mattmc.dev.deterministicCameraCapture.gameMode", "").trim();
	private static final int FORCED_SELECTED_HOTBAR_SLOT = Integer.getInteger("mattmc.dev.deterministicCameraCapture.selectedHotbarSlot", 0);
	private static final float FORCED_EXPERIENCE_PROGRESS =
		Float.parseFloat(System.getProperty("mattmc.dev.deterministicCameraCapture.experienceProgress", "NaN"));
	private static final int FORCED_EXPERIENCE_LEVEL =
		Integer.getInteger("mattmc.dev.deterministicCameraCapture.experienceLevel", -1);
	private static final String FORCED_ATTACK_INDICATOR =
		System.getProperty("mattmc.dev.deterministicCameraCapture.attackIndicator", "").trim();
	private static final float FORCED_ATTACK_PROGRESS =
		Float.parseFloat(System.getProperty("mattmc.dev.deterministicCameraCapture.attackProgress", "NaN"));
	private static final float FORCED_ATTACK_DELAY =
		Float.parseFloat(System.getProperty("mattmc.dev.deterministicCameraCapture.attackDelay", "NaN"));
	private static final boolean FORCE_ATTACK_TARGET =
		Boolean.getBoolean("mattmc.dev.deterministicCameraCapture.attackTarget");
	private static final boolean FORCE_BLOCK_OUTLINE_TARGET =
		Boolean.getBoolean("mattmc.dev.deterministicCameraCapture.blockOutlineTarget");
	private static final boolean AIM_BLOCK_OUTLINE_TARGET =
		Boolean.getBoolean("mattmc.dev.deterministicCameraCapture.blockOutlineAimTarget");
	private static final boolean BLOCK_OUTLINE_PAUSE_PARITY =
		Boolean.getBoolean("mattmc.dev.deterministicCameraCapture.blockOutlinePauseParity");
	private static final boolean FORCE_BLOCK_OUTLINE_HIGH_CONTRAST =
		Boolean.getBoolean("mattmc.dev.deterministicCameraCapture.blockOutlineHighContrast");
	private static final boolean FORCE_REAL_SURVIVAL_CRACK =
		Boolean.getBoolean("mattmc.dev.deterministicCameraCapture.realSurvivalCrack");
	private static final boolean FORCE_REAL_SURVIVAL_CRACK_SETUP_BLOCK =
		Boolean.getBoolean("mattmc.dev.deterministicCameraCapture.realSurvivalCrackSetupBlock");
	private static final String BLOCK_DISPLAY_SCENARIO =
		System.getProperty("mattmc.dev.rustGalWorldMesh.blockDisplayScenario", "").trim().toLowerCase(Locale.ROOT);
	private static final String FALLING_BLOCK_SCENARIO =
		System.getProperty("mattmc.dev.rustGalWorldMesh.fallingBlockScenario", "").trim().toLowerCase(Locale.ROOT);
	private static final String FALLING_BLOCK_ROUTE_CONTROL =
		Boolean.getBoolean("mattmc.dev.rustGalWorldFallingBlock.disabled")
			? "disabled"
			: Boolean.getBoolean("mattmc.dev.rustGalWorldFallingBlock.legacyControl") ? "legacy" : "rust";
	private static final String PISTON_SCENARIO =
		System.getProperty("mattmc.dev.rustGalWorldMesh.pistonScenario", "").trim().toLowerCase(Locale.ROOT);
	private static final String STATIC_TERRAIN_SCENARIO =
		System.getProperty("mattmc.dev.rustGalStaticTerrain.scenario", "").trim().toLowerCase(Locale.ROOT);
	private static final String STATIC_TERRAIN_FAULT =
		System.getProperty("mattmc.dev.rustGalStaticTerrain.fault", "").trim().toLowerCase(Locale.ROOT);
	private static final String STATIC_TERRAIN_RESOURCE_PACK_SCENARIO =
		System.getProperty("mattmc.dev.rustGalStaticTerrain.resourcePackScenario", "").trim().toLowerCase(Locale.ROOT);
	private static final String STATIC_TERRAIN_WORLD_ID =
		System.getProperty("mattmc.dev.rustGalStaticTerrain.worldId", "").trim();
	private static final String STATIC_TERRAIN_WORLD_B_ID =
		System.getProperty("mattmc.dev.rustGalStaticTerrain.worldB", "").trim();
	private static final int FORCED_ARMOR_VALUE =
		Integer.getInteger("mattmc.dev.deterministicCameraCapture.armorValue", -1);
	private static final float FORCED_PLAYER_HEALTH =
		Float.parseFloat(System.getProperty("mattmc.dev.deterministicCameraCapture.playerHealth", "NaN"));
	private static final float FORCED_PLAYER_MAX_HEALTH =
		Float.parseFloat(System.getProperty("mattmc.dev.deterministicCameraCapture.playerMaxHealth", "NaN"));
	private static final float FORCED_PLAYER_ABSORPTION =
		Float.parseFloat(System.getProperty("mattmc.dev.deterministicCameraCapture.playerAbsorption", "NaN"));
	private static final int FORCED_PLAYER_FOOD_LEVEL =
		Integer.getInteger("mattmc.dev.deterministicCameraCapture.playerFoodLevel", -1);
	private static final float FORCED_PLAYER_FOOD_SATURATION =
		Float.parseFloat(System.getProperty("mattmc.dev.deterministicCameraCapture.playerFoodSaturation", "NaN"));
	private static final boolean FORCE_PLAYER_FOOD_HUNGER_EFFECT =
		Boolean.getBoolean("mattmc.dev.deterministicCameraCapture.playerFoodHungerEffect");
	private static final boolean FORCE_PLAYER_FOOD_JITTER =
		Boolean.getBoolean("mattmc.dev.deterministicCameraCapture.playerFoodJitter");
	private static final int FORCED_PLAYER_AIR_SUPPLY =
		Integer.getInteger("mattmc.dev.deterministicCameraCapture.playerAirSupply", -1);
	private static final int FORCED_PLAYER_MAX_AIR_SUPPLY =
		Integer.getInteger("mattmc.dev.deterministicCameraCapture.playerMaxAirSupply", -1);
	private static final boolean FORCE_PLAYER_UNDERWATER =
		Boolean.getBoolean("mattmc.dev.deterministicCameraCapture.playerUnderwater");
	private static final boolean FORCE_PLAYER_AIR_POP =
		Boolean.getBoolean("mattmc.dev.deterministicCameraCapture.playerAirPop");
	private static final boolean FORCE_MOUNT_PRESENT =
		Boolean.getBoolean("mattmc.dev.deterministicCameraCapture.mountPresent");
	private static final float FORCED_MOUNT_HEALTH =
		Float.parseFloat(System.getProperty("mattmc.dev.deterministicCameraCapture.mountHealth", "NaN"));
	private static final float FORCED_MOUNT_MAX_HEALTH =
		Float.parseFloat(System.getProperty("mattmc.dev.deterministicCameraCapture.mountMaxHealth", "NaN"));
	private static final int FORCED_MOUNT_HEALTH_ROWS =
		Integer.getInteger("mattmc.dev.deterministicCameraCapture.mountHealthRows", -1);
	private static final String FORCED_PLAYER_HEART_VARIANT =
		System.getProperty("mattmc.dev.deterministicCameraCapture.playerHeartVariant", "").trim();
	private static final boolean FORCE_PLAYER_HEALTH_REGEN =
		Boolean.getBoolean("mattmc.dev.deterministicCameraCapture.playerHealthRegeneration");
	private static final boolean HIDE_CHAT =
		Boolean.getBoolean("mattmc.dev.deterministicCameraCapture.hideChat");
	private static final int FORCED_BOSS_BAR_COUNT =
		Integer.getInteger("mattmc.dev.deterministicCameraCapture.bossBars", -1);
	private static final String FORCED_BOSS_BAR_PROGRESS =
		System.getProperty("mattmc.dev.deterministicCameraCapture.bossProgress", "").trim();
	private static final String FORCED_BOSS_BAR_OVERLAY =
		System.getProperty("mattmc.dev.deterministicCameraCapture.bossOverlay", "").trim();
	private static final float FIXED_VIGNETTE_BRIGHTNESS =
		Float.parseFloat(System.getProperty("mattmc.dev.deterministicCameraCapture.vignetteBrightness", "1.0"));
	private static final boolean RUST_GAL_GUI_SCREEN_CYCLE =
		Boolean.getBoolean("mattmc.dev.deterministicCameraCapture.rustGalGuiScreenCycle");
	private static final int RUST_GAL_GUI_SCREEN_CYCLE_REPEATS =
		Math.max(1, Integer.getInteger("mattmc.dev.deterministicCameraCapture.rustGalGuiScreenCycleRepeats", 2));
	private static final int RUST_GAL_GUI_SCREEN_CYCLE_HOLD_FRAMES =
		Math.max(1, Integer.getInteger("mattmc.dev.deterministicCameraCapture.rustGalGuiScreenCycleHoldFrames", 4));
	private static final Path METADATA_PATH = Path.of(System.getProperty("mattmc.dev.deterministicCameraCapture.metadata", "run/deterministic_camera_capture.json"));
	private static final Path SCREENSHOT_DIR = Path.of(System.getProperty("mattmc.dev.deterministicCameraCapture.screenshotDir", "run/deterministic_camera_capture"));

	private static final List<PoseCapture> CAPTURES = new ArrayList<>();
	private static boolean initialized;
	private static boolean complete;
	private static boolean failed;
	private static boolean stopIssued;
	private static BlockPos realSurvivalCrackBlock;
	private static Direction realSurvivalCrackDirection;
	private static String realSurvivalCrackLastHitType = "unset";
	private static String realSurvivalCrackLastTarget = "unset";
	private static String realSurvivalCrackLastDirection = "unset";
	private static String realSurvivalCrackLastStatus = "unset";
	private static String realSurvivalCrackSetupTarget = "unset";
	private static String realSurvivalCrackLastValidTarget = "unset";
	private static String realSurvivalCrackLastValidBlockType = "unset";
	private static String realSurvivalCrackLastRenderedTarget = "unset";
	private static String realSurvivalCrackLastRenderedBlockType = "unset";
	private static int realSurvivalCrackStartCalls;
	private static int realSurvivalCrackContinueCalls;
	private static int realSurvivalCrackStopCalls;
	private static int realSurvivalCrackValidBlockHitCount;
	private static int realSurvivalCrackRenderedStateCount;
	private static int realSurvivalCrackMinRenderedStage = 10;
	private static int realSurvivalCrackMaxRenderedStage = -1;
	private static int realSurvivalCrackFramesWaitingForStage;
	private static long realSurvivalCrackLastDriveGameTime = Long.MIN_VALUE;
	private static Pose[] poses;
	private static Pose initialPose;
	private static Vec3 initialPosition;
	private static String initialDimension;
	private static int poseIndex;
	private static int renderedFramesAtPose;
	private static boolean awaitingScreenshotAck;
	private static int framesAwaitingAck;
	private static Path currentScreenshotPath;
	private static Path currentAckPath;
	private static final List<WaterAnimationFrameCapture> WATER_ANIMATION_CAPTURES = new ArrayList<>();
	private static int staticTerrainWaterAnimationDenseCapturedFrames;
	private static boolean staticTerrainWaterAnimationDenseComplete;
	private static boolean staticTerrainWaterAnimationScreenshotInFlight;
	private static int framesAwaitingStaticTerrainWaterAnimationAck;
	private static Path staticTerrainWaterAnimationScreenshotPath;
	private static Path staticTerrainWaterAnimationAckPath;
	private static long staticTerrainWaterAnimationPendingRenderedFrameIndex = -1L;
	private static long staticTerrainWaterAnimationPendingGameTime = -1L;
	private static long staticTerrainWaterAnimationPendingHash;
	private static String staticTerrainWaterAnimationPendingSummary = "missing";
	private static String staticTerrainWaterAnimationPendingState = "missing";
	private static long staticTerrainWaterAnimationPendingVisibleLayerSubmissions;
	private static long staticTerrainWaterAnimationPendingCurrentFrameVisibleLayerSubmissions;
	private static long staticTerrainWaterAnimationPendingAtlasGeneration;
	private static long startedGameTime;
	private static long renderedFrameIndex;
	private static int windowWidth;
	private static int windowHeight;
	private static boolean settledReadyGateSatisfied;
	private static String staticTerrainSettledSignature = "";
	private static int staticTerrainSettledFrames;
	private static boolean movingMeshScenarioSetup;
	private static boolean staticTerrainLifecycleSetup;
	private static boolean staticTerrainLifecycleAfterRecorded;
	private static boolean staticTerrainTranslucentFixtureApplied;
	private static boolean staticTerrainTranslucentWorldBFixtureApplied;
	private static int framesWaitingForStaticTerrainLifecycle;
	private static BlockPos staticTerrainLifecycleEditBlock;
	private static long staticTerrainLifecycleBeforeGeneration;
	private static long staticTerrainLifecycleAfterGeneration;
	private static String staticTerrainLifecycleStage = "inactive";
	private static String staticTerrainLifecycleBlockType = "";
	private static int staticTerrainLifecycleActionStep;
	private static int staticTerrainLifecycleResizeCount;
	private static int staticTerrainOriginalRenderDistance;
	private static int staticTerrainOriginalSimulationDistance;
	private static Vec3 staticTerrainOriginalPosition;
	private static int staticTerrainLifecycleBeforeCachedLayers;
	private static int staticTerrainLifecycleAfterCachedLayers;
	private static long staticTerrainLifecycleBeforeRssBytes;
	private static long staticTerrainLifecycleAfterRssBytes;
	private static int staticTerrainMenuCachedLayers;
	private static long staticTerrainMenuUsedMemoryBytes;
	private static long staticTerrainUnloadSubmissionSnapshot;
	private static long staticTerrainMenuSubmissionSnapshot;
	private static long staticTerrainMenuCurrentFrameSubmissionSnapshot;
	private static int staticTerrainMenuActiveLayerSnapshot;
	private static int staticTerrainMenuActiveSectionAssetSnapshot;
	private static boolean staticTerrainCrossWorldStaleFaultInjected;
	private static long staticTerrainReloadGenerationA;
	private static long staticTerrainReloadGenerationB;
	private static String staticTerrainLifecycleWorldA = "";
	private static String staticTerrainLifecycleWorldB = "";
	private static boolean staticTerrainLifecycleTransitionInProgress;
	private static CompletableFuture<Void> staticTerrainReloadFuture;
	private static String movingMeshSetupStage = "inactive";
	private static String movingMeshSetupLastMissing = "";
	private static int movingMeshSetupAttempts;
	private static int framesWaitingForSettledReady;
	private static int framesWaitingForMovingMeshProducer;
	private static int fallingEntitySeenCount;
	private static int fallingEntityShouldRenderCount;
	private static int fallingEntityCompiledSectionCount;
	private static int fallingEntityExtractedCount;
	private static String fallingBlockSetupStatus = "inactive";
	private static String fallingBlockSetupBlockId = "";
	private static String fallingBlockSetupSpawnMethod = "";
	private static String fallingBlockSetupOrigin = "";
	private static String fallingBlockSetupLanding = "";
	private static int fallingBlockSetupEntityCount;
	private static int fallingBlockSetupFallHeight;
	private static String pistonSetupStatus = "inactive";
	private static String pistonSetupBlockId = "";
	private static String pistonSetupOrigin = "";
	private static String pistonSetupBlockState = "";
	private static boolean pistonSetupClientBlockEntityPresent;
	private static int pistonSetupEntityCount;
	private static BlockPos pistonSetupPos;
	private static BlockState pistonSetupMovedState;
	private static Direction pistonSetupDirection;
	private static boolean pistonSetupExtending;
	private static boolean pistonSetupSourcePiston;
	private static int pistonSetupReseedCount;
	private static CameraType originalCameraType;
	private static GameType originalGameMode;
	private static GameType originalPreviousGameMode;
	private static AttackIndicatorStatus originalAttackIndicator;
	private static ChatVisiblity originalChatVisibility;
	private static boolean originalHighContrastBlockOutline;
	private static boolean originalNoGravity;
	private static int originalSelectedHotbarSlot;
	private static float originalExperienceProgress;
	private static int originalExperienceLevel;
	private static int originalExperienceDisplayStartTick;
	private static int originalAttackStrengthTicker;
	private static int originalArmorValueOverride;
	private static float originalHealthOverride;
	private static float originalMaxHealthOverride;
	private static int originalTicksFrozen;
	private static MobEffectInstance originalPoisonEffect;
	private static MobEffectInstance originalWitherEffect;
	private static MobEffectInstance originalRegenerationEffect;
	private static Entity originalCrosshairPickEntity;
	private static float initialHealth;
	private static Vec3 lastSafetyPosition = Vec3.ZERO;
	private static Vec3 lastSafetyVelocity = Vec3.ZERO;
	private static float lastSafetyHealth;
	private static double lastSafetyFallDistance;
	private static String deterministicSupportBlock = "unset";
	private static String deterministicSupportBlockType = "unset";
	private static ForcedBlockOutlineTarget forcedBlockOutlineTarget;
	private static final Map<Long, Map<String, Set<String>>> SUBMITTED_WORK_BY_FRAME = new LinkedHashMap<>();
	private static int rustGalGuiScreenCycleStage;
	private static int rustGalGuiScreenCycleFramesInStage;
	private static int rustGalGuiScreenCyclesCompleted;
	private static boolean rustGalGuiScreenCycleComplete = !RUST_GAL_GUI_SCREEN_CYCLE;

	private DeterministicCameraCapture() {
	}

	public static long currentRenderedFrameIndex() {
		return renderedFrameIndex;
	}

	public static void beforeTick(Minecraft minecraft) {
		if (ENABLED && complete && STOP_AFTER_COMPLETE && !stopIssued) {
			stopIssued = true;
			minecraft.stop();
			return;
		}
		if (!ENABLED || complete || failed) {
			return;
		}

		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}

		player.input.keyPresses = Input.EMPTY;
		player.xxa = 0.0F;
		player.zza = 0.0F;
		player.setSprinting(false);
		player.setShiftKeyDown(false);
		recordPlayerSafetyState(player);
		Vec3 targetPosition = targetPositionForCurrentPose();
		if (initialized && targetPosition != null
			&& player.position().distanceToSqr(targetPosition) > 0.0004
			&& !isKnownDeterministicPosePosition(player.position())) {
			fail("deterministic player moved unexpectedly before stabilization: initial="
				+ formatVec(initialPosition)
				+ " target="
				+ formatVec(targetPosition)
				+ " current="
				+ formatVec(player.position()));
			return;
		}
		player.setDeltaMovement(Vec3.ZERO);
		player.fallDistance = 0.0F;
		applyRuntimeOverrides(minecraft, player);
		if (initialized && targetPosition != null && initialPose != null) {
			player.setPos(targetPosition);
			player.setOldPosAndRot(targetPosition, initialPose.yaw(), initialPose.pitch());
		}
	}

	public static void beforeRender(Minecraft minecraft) {
		if (!ENABLED || complete || failed) {
			return;
		}
		if (!ensureInitialized(minecraft)) {
			return;
		}
		applyRuntimeOverrides(minecraft, minecraft.player);
		if (poseIndex >= poses.length) {
			stabilizeGuiState(minecraft);
			applyPauseParityScreen(minecraft);
			applyPose(minecraft.player, initialPose);
			return;
		}

		stabilizeGuiState(minecraft);
		applyPauseParityScreen(minecraft);
		applyPose(minecraft.player, poses[poseIndex]);
	}

	public static void afterRender(Minecraft minecraft) {
		if (!ENABLED || complete || failed) {
			return;
		}
		if (!ensureInitialized(minecraft)) {
			return;
		}
		if (poseIndex >= poses.length) {
			stabilizeGuiState(minecraft);
			applyPauseParityScreen(minecraft);
			applyPose(minecraft.player, initialPose);
			return;
		}

		stabilizeGuiState(minecraft);
		applyPauseParityScreen(minecraft);
		applyPose(minecraft.player, poses[poseIndex]);
		renderedFrameIndex++;
		if (!settledReadyGateSatisfied && !settledReadyGateSatisfied(minecraft)) {
			renderedFramesAtPose = 0;
			framesWaitingForSettledReady++;
			if (framesWaitingForSettledReady > SETTLED_READY_MAX_WAIT_FRAMES) {
				fail("timed out waiting for settled submitted work: " + settledReadySummary());
			} else if ((framesWaitingForSettledReady % 30) == 0) {
				writeMetadata(minecraft, "waiting_for_settled_ready_work");
			}
			return;
		}
		if (!setupStaticTerrainLifecycleScenarioAfterSettledReady(minecraft)) {
			renderedFramesAtPose = 0;
			return;
		}
		if (!setupMovingMeshScenarioAfterSettledReady(minecraft)) {
			renderedFramesAtPose = 0;
			return;
		}
		maintainPistonScenario(minecraft);
		if (awaitingScreenshotAck) {
			if (checkScreenshotAck(minecraft)) {
				return;
			}
			framesAwaitingAck++;
			if (framesAwaitingAck > ACK_TIMEOUT_FRAMES) {
				fail("timed out waiting for deterministic screenshot ack: " + currentAckPath);
			}
			return;
		}
		if (!advanceRustGalGuiScreenCycle(minecraft)) {
			return;
		}
		if (!captureStaticTerrainWaterAnimationFrameIfNeeded(minecraft)) {
			return;
		}

		renderedFramesAtPose++;
		if (renderedFramesAtPose == Math.max(1, FRAMES_PER_POSE - 1)) {
			RenderDocCaptureHook.beginFrameCaptureOnce(minecraft.getWindow(), poses[poseIndex].name() + "#" + renderedFrameIndex);
			RenderDocCaptureHook.triggerNextFrameOnce(poses[poseIndex].name() + "#" + renderedFrameIndex);
		}
		if (renderedFramesAtPose < FRAMES_PER_POSE) {
			return;
		}
			RenderDocCaptureHook.endFrameCaptureOnce(minecraft.getWindow(), poses[poseIndex].name() + "#" + renderedFrameIndex);

			VulkanicAPI.traceScopedCompositeColortex0PoseBoundary();
			if (!realSurvivalCrackPoseReady()) {
				renderedFramesAtPose = 0;
				realSurvivalCrackFramesWaitingForStage++;
				if (realSurvivalCrackFramesWaitingForStage > SETTLED_READY_MAX_WAIT_FRAMES) {
					fail("timed out waiting for real survival crack pose " + poses[poseIndex].name()
						+ " target stage: min=" + (realSurvivalCrackMinRenderedStage == 10 ? -1 : realSurvivalCrackMinRenderedStage)
						+ " max=" + realSurvivalCrackMaxRenderedStage
						+ " status=" + realSurvivalCrackLastStatus
						+ " hit=" + realSurvivalCrackLastHitType
						+ " target=" + realSurvivalCrackLastTarget
						+ " validHits=" + realSurvivalCrackValidBlockHitCount
						+ " renderedStates=" + realSurvivalCrackRenderedStateCount);
				} else if ((realSurvivalCrackFramesWaitingForStage % 30) == 0) {
					writeMetadata(minecraft, "waiting_for_real_survival_crack_stage");
				}
				return;
			}
			realSurvivalCrackFramesWaitingForStage = 0;
			if (!movingMeshProducerReady()) {
				renderedFramesAtPose = 0;
				framesWaitingForMovingMeshProducer++;
			if (framesWaitingForMovingMeshProducer > SETTLED_READY_MAX_WAIT_FRAMES) {
						fail("timed out waiting for deterministic moving-mesh producer traversal: "
							+ "setupStage=" + movingMeshSetupStage
							+ " setupAttempts=" + movingMeshSetupAttempts
							+ " setupMissing=" + movingMeshSetupLastMissing
							+ " " + movingMeshProducerSummary());
				} else if ((framesWaitingForMovingMeshProducer % 30) == 0) {
					writeMetadata(minecraft, "waiting_for_moving_mesh_producer");
				}
				return;
			}
			framesWaitingForMovingMeshProducer = 0;
			if (INTERNAL_SCREENSHOTS) {
				captureCurrentPoseInternally(minecraft);
			} else {
				requestCurrentPoseScreenshot(minecraft);
			}
		renderedFramesAtPose = 0;
	}

	public static boolean isEnabledForDiagnostics() {
		return ENABLED && initialized && !failed;
	}

	private static boolean captureStaticTerrainWaterAnimationFrameIfNeeded(Minecraft minecraft) {
		if (!STATIC_TERRAIN_WATER_ANIMATION_DENSE_CAPTURE || !"translucent-water".equals(STATIC_TERRAIN_SCENARIO)) {
			return true;
		}
		if (staticTerrainWaterAnimationDenseComplete) {
			return true;
		}
		if (!staticTerrainLifecycleAfterRecorded || !"fixture-visible".equals(staticTerrainLifecycleStage)) {
			return true;
		}
		if (staticTerrainWaterAnimationScreenshotInFlight) {
			if (checkStaticTerrainWaterAnimationAck(minecraft)) {
				return false;
			}
			framesAwaitingStaticTerrainWaterAnimationAck++;
			if (framesAwaitingStaticTerrainWaterAnimationAck > ACK_TIMEOUT_FRAMES) {
				fail("timed out waiting for water animation dense screenshot ack: " + staticTerrainWaterAnimationAckPath);
			}
			return false;
		}
		if (staticTerrainWaterAnimationDenseCapturedFrames >= STATIC_TERRAIN_WATER_ANIMATION_DENSE_FRAMES) {
			staticTerrainWaterAnimationDenseComplete = true;
			writeMetadata(minecraft, "static_terrain_water_animation_dense_complete");
			return true;
		}

		int frameIndex = staticTerrainWaterAnimationDenseCapturedFrames;
		staticTerrainWaterAnimationScreenshotPath = SCREENSHOT_DIR.resolve(String.format(Locale.ROOT, "water_animation_frame_%03d.png", frameIndex));
		staticTerrainWaterAnimationAckPath = SCREENSHOT_DIR.resolve(String.format(Locale.ROOT, "capture_request_water_animation_%03d.ack.json", frameIndex));
		Path requestPath = SCREENSHOT_DIR.resolve(String.format(Locale.ROOT, "capture_request_water_animation_%03d.json", frameIndex));
		RustGalTerrainRenderer.TerrainDiagnostics diagnostics = RustGalTerrainRenderer.diagnosticsSnapshot();
		long animationTick = renderedFrameIndex;
		staticTerrainWaterAnimationPendingRenderedFrameIndex = renderedFrameIndex;
		staticTerrainWaterAnimationPendingGameTime = minecraft.level == null ? -1L : minecraft.level.getGameTime();
		staticTerrainWaterAnimationPendingHash = RustGalTerrainRenderer.waterAnimationHashForDiagnostics();
		staticTerrainWaterAnimationPendingSummary = RustGalTerrainRenderer.waterAnimationSummaryForDiagnostics();
		staticTerrainWaterAnimationPendingState = RustGalTerrainRenderer.waterAnimationFrameStateForDiagnostics(animationTick);
		staticTerrainWaterAnimationPendingVisibleLayerSubmissions = diagnostics.visibleLayerSubmissions();
		staticTerrainWaterAnimationPendingCurrentFrameVisibleLayerSubmissions = diagnostics.currentFrameVisibleLayerSubmissions();
		staticTerrainWaterAnimationPendingAtlasGeneration = diagnostics.atlasGeneration();
		try {
			Files.createDirectories(SCREENSHOT_DIR);
		} catch (IOException exception) {
			fail("failed to create water animation screenshot directory " + SCREENSHOT_DIR + ": " + exception.getMessage());
			return false;
		}
		StringBuilder json = new StringBuilder(1024);
		json.append("{\n");
		json.append("  \"index\": ").append(frameIndex).append(",\n");
		appendField(json, "poseName", "water-animation-dense").append(",\n");
		appendField(json, "screenshot", staticTerrainWaterAnimationScreenshotPath.toAbsolutePath().toString()).append(",\n");
		appendField(json, "ack", staticTerrainWaterAnimationAckPath.toAbsolutePath().toString()).append(",\n");
		appendField(json, "dimension", minecraft.level == null ? "missing" : minecraft.level.dimension().location().toString()).append(",\n");
		appendVec3(json, "position", minecraft.player == null ? Vec3.ZERO : minecraft.player.position()).append(",\n");
		json.append("  \"renderedFrameIndex\": ").append(staticTerrainWaterAnimationPendingRenderedFrameIndex).append(",\n");
		json.append("  \"gameTime\": ").append(staticTerrainWaterAnimationPendingGameTime).append("\n");
		json.append("}\n");
		try {
			Files.writeString(requestPath, json.toString(), StandardCharsets.UTF_8);
		} catch (IOException exception) {
			fail("failed to write water animation dense screenshot request " + requestPath + ": " + exception.getMessage());
			return false;
		}
		staticTerrainWaterAnimationScreenshotInFlight = true;
		framesAwaitingStaticTerrainWaterAnimationAck = 0;
		writeMetadata(minecraft, "waiting_for_static_terrain_water_animation_dense_screenshot");
		return false;
	}

	private static boolean checkStaticTerrainWaterAnimationAck(Minecraft minecraft) {
		if (staticTerrainWaterAnimationAckPath == null || !Files.isRegularFile(staticTerrainWaterAnimationAckPath)) {
			return false;
		}
		if (staticTerrainWaterAnimationScreenshotPath == null || !Files.isRegularFile(staticTerrainWaterAnimationScreenshotPath)) {
			fail("water animation dense screenshot ack exists but screenshot is missing: " + staticTerrainWaterAnimationScreenshotPath);
			return false;
		}
		WATER_ANIMATION_CAPTURES.add(new WaterAnimationFrameCapture(
			staticTerrainWaterAnimationDenseCapturedFrames,
			staticTerrainWaterAnimationScreenshotPath.toAbsolutePath().toString(),
			staticTerrainWaterAnimationPendingRenderedFrameIndex,
			staticTerrainWaterAnimationPendingGameTime,
			staticTerrainWaterAnimationPendingHash,
			staticTerrainWaterAnimationPendingSummary,
			staticTerrainWaterAnimationPendingState,
			staticTerrainWaterAnimationPendingVisibleLayerSubmissions,
			staticTerrainWaterAnimationPendingCurrentFrameVisibleLayerSubmissions,
			staticTerrainWaterAnimationPendingAtlasGeneration
		));
		staticTerrainWaterAnimationDenseCapturedFrames++;
		staticTerrainWaterAnimationScreenshotInFlight = false;
		framesAwaitingStaticTerrainWaterAnimationAck = 0;
		staticTerrainWaterAnimationScreenshotPath = null;
		staticTerrainWaterAnimationAckPath = null;
		staticTerrainWaterAnimationPendingRenderedFrameIndex = -1L;
		staticTerrainWaterAnimationPendingGameTime = -1L;
		staticTerrainWaterAnimationPendingHash = 0L;
		staticTerrainWaterAnimationPendingSummary = "missing";
		staticTerrainWaterAnimationPendingState = "missing";
		staticTerrainWaterAnimationPendingVisibleLayerSubmissions = 0L;
		staticTerrainWaterAnimationPendingCurrentFrameVisibleLayerSubmissions = 0L;
		staticTerrainWaterAnimationPendingAtlasGeneration = 0L;
		writeMetadata(minecraft, "static_terrain_water_animation_dense_frame_captured");
		return true;
	}

	public static boolean isFallingBlockSequenceActive() {
		return ENABLED && initialized && !FALLING_BLOCK_SCENARIO.isEmpty() && !"hidden".equals(FALLING_BLOCK_SCENARIO);
	}

	public static void recordFallingBlockExtractionProbe(boolean shouldRender, boolean compiledSection, boolean extracted) {
		if (!isFallingBlockSequenceActive()) {
			return;
		}
		fallingEntitySeenCount++;
		if (shouldRender) {
			fallingEntityShouldRenderCount++;
		}
		if (compiledSection) {
			fallingEntityCompiledSectionCount++;
		}
		if (extracted) {
			fallingEntityExtractedCount++;
		}
	}

	public static void forceCrosshairAttackTargetForDiagnostics(Minecraft minecraft) {
		if (ENABLED && FORCE_ATTACK_TARGET && minecraft.player != null) {
			minecraft.crosshairPickEntity = minecraft.player;
		}
	}

	private static boolean setupMovingMeshScenarioAfterSettledReady(Minecraft minecraft) {
		if (movingMeshScenarioSetup || (
			(FALLING_BLOCK_SCENARIO.isEmpty() || "hidden".equals(FALLING_BLOCK_SCENARIO))
				&& (PISTON_SCENARIO.isEmpty() || "hidden".equals(PISTON_SCENARIO))
		)) {
			if (movingMeshScenarioSetup) {
				movingMeshSetupStage = "setup-complete";
			}
			return true;
		}
		movingMeshSetupAttempts++;
		movingMeshSetupStage = "waiting-for-world";
		if (minecraft.player == null) {
			movingMeshSetupLastMissing = "player";
			if ((movingMeshSetupAttempts % 30) == 0) {
				writeMetadata(minecraft, "waiting_for_moving_mesh_setup_player");
			}
			return false;
		}
		if (minecraft.level == null) {
			movingMeshSetupLastMissing = "client-level";
			if ((movingMeshSetupAttempts % 30) == 0) {
				writeMetadata(minecraft, "waiting_for_moving_mesh_setup_client_level");
			}
			return false;
		}
		if (minecraft.getSingleplayerServer() == null) {
			movingMeshSetupLastMissing = "singleplayer-server";
			if ((movingMeshSetupAttempts % 30) == 0) {
				writeMetadata(minecraft, "waiting_for_moving_mesh_setup_server");
			}
			return false;
		}
		ServerLevel serverLevel = minecraft.getSingleplayerServer().getLevel(minecraft.level.dimension());
		if (serverLevel == null) {
			movingMeshSetupLastMissing = "server-level:" + minecraft.level.dimension().location();
			if ((movingMeshSetupAttempts % 30) == 0) {
				writeMetadata(minecraft, "waiting_for_moving_mesh_setup_server_level");
			}
			return false;
		}
		movingMeshSetupStage = "scenario-setup-started";
		movingMeshSetupLastMissing = "";
		writeMetadata(minecraft, "moving_mesh_scenario_setup_started");
		setupFallingBlockScenario(minecraft, minecraft.player);
		setupPistonScenario(minecraft, minecraft.player);
		movingMeshScenarioSetup = true;
		movingMeshSetupStage = "setup-complete";
		writeMetadata(minecraft, "moving_mesh_scenario_spawned_after_settled_ready");
		return false;
	}

	private static boolean setupStaticTerrainLifecycleScenarioAfterSettledReady(Minecraft minecraft) {
		if (!staticTerrainLifecycleScenario()) {
			staticTerrainLifecycleStage = "inactive";
			return true;
		}
		if (staticTerrainLiteralWorldTransitionScenario()) {
			return setupStaticTerrainLiteralWorldTransition(minecraft);
		}
		if (minecraft.player == null || minecraft.level == null || minecraft.getSingleplayerServer() == null) {
			staticTerrainLifecycleStage = "waiting-for-world";
			return false;
		}
		ServerLevel serverLevel = minecraft.getSingleplayerServer().getLevel(minecraft.level.dimension());
		if (serverLevel == null) {
			staticTerrainLifecycleStage = "waiting-for-server-level";
			return false;
		}
		if (!staticTerrainLifecycleSetup) {
			BlockPos target = chooseStaticTerrainLifecycleTarget(minecraft, serverLevel);
			if (target == null) {
				staticTerrainLifecycleStage = "waiting-for-visible-section";
				return false;
			}
			ChunkSectionLayer lifecycleLayer = staticTerrainLifecycleLayer();
			RustGalTerrainRenderer.TerrainLayerSnapshot before =
				RustGalTerrainRenderer.snapshotLayer(target, lifecycleLayer);
			if (!staticTerrainAllowsAirSource() && (before == null || before.meshGeneration() == 0L)) {
				staticTerrainLifecycleStage = "waiting-for-source-generation";
				return false;
			}
			RustGalTerrainRenderer.TerrainDiagnostics beforeDiagnostics = RustGalTerrainRenderer.diagnosticsSnapshot();
			staticTerrainLifecycleEditBlock = target;
			staticTerrainLifecycleBeforeGeneration = staticTerrainUsesAtlasGeneration()
				? beforeDiagnostics.atlasGeneration()
				: before == null ? 0L : before.meshGeneration();
			staticTerrainLifecycleBeforeCachedLayers = beforeDiagnostics.cachedLayerAssets();
			staticTerrainLifecycleBeforeRssBytes = currentUsedMemoryBytes();
			BlockState replacement = staticTerrainReplacementState();
			staticTerrainLifecycleBlockType = replacement.getBlock().builtInRegistryHolder().key().location().toString();
			RustGalTerrainRenderer.recordLifecycleMarker(
				"lifecycle-edit-before",
				target,
				lifecycleLayer,
				STATIC_TERRAIN_SCENARIO + ":block=" + target.toShortString() + ":generation=" + staticTerrainLifecycleBeforeGeneration
			);
			applyStaticTerrainLifecycleAction(minecraft, serverLevel, target, replacement);
			RustGalTerrainRenderer.recordLifecycleMarker(
				"lifecycle-edit-applied",
				target,
				lifecycleLayer,
				STATIC_TERRAIN_SCENARIO + ":block=" + target.toShortString() + ":state=" + staticTerrainLifecycleBlockType
			);
			if ("section-reentry".equals(staticTerrainBaseScenario())) {
				RustGalTerrainRenderer.removeSection(
					net.minecraft.core.SectionPos.blockToSectionCoord(target.getX()),
					net.minecraft.core.SectionPos.blockToSectionCoord(target.getY()),
					net.minecraft.core.SectionPos.blockToSectionCoord(target.getZ()),
					"lifecycle-section-removed"
				);
				RustGalTerrainRenderer.recordLifecycleMarker(
					"lifecycle-section-removed",
					target,
					lifecycleLayer,
					STATIC_TERRAIN_SCENARIO + ":block=" + target.toShortString()
				);
				RustGalTerrainRenderer.recordLifecycleMarker(
					"lifecycle-section-reentry-requested",
					target,
					lifecycleLayer,
					STATIC_TERRAIN_SCENARIO + ":block=" + target.toShortString()
				);
			}
			recordStaticTerrainLifecycleFaultMarker(target);
			staticTerrainLifecycleSetup = true;
			staticTerrainLifecycleStage = "edit-applied";
			writeMetadata(minecraft, "static_terrain_lifecycle_edit_applied");
			return false;
		}
		framesWaitingForStaticTerrainLifecycle++;
		if (continueStaticTerrainLifecycleAction(minecraft)) {
			return false;
		}
		if (staticTerrainBaseTranslucentFixtureScenario()) {
			RustGalTerrainRenderer.TerrainDiagnostics diagnostics = RustGalTerrainRenderer.diagnosticsSnapshot();
			boolean unsupportedFixtureReady = !"translucent-mixed-unsupported".equals(STATIC_TERRAIN_SCENARIO)
				|| diagnostics.unsupportedFluidOmittedSections() > 0;
			if (diagnostics.visibleLayerSubmissions() > 0
				&& unsupportedFixtureReady
				&& framesWaitingForStaticTerrainLifecycle >= Math.max(4, FRAMES_PER_POSE / 2)) {
				staticTerrainLifecycleAfterGeneration = Math.max(1L, diagnostics.atlasGeneration());
				if (!staticTerrainLifecycleAfterRecorded) {
					staticTerrainLifecycleAfterCachedLayers = diagnostics.cachedLayerAssets();
					staticTerrainLifecycleAfterRssBytes = currentUsedMemoryBytes();
					RustGalTerrainRenderer.recordLifecycleMarker(
						"lifecycle-fixture-visible",
						staticTerrainLifecycleEditBlock,
						staticTerrainLifecycleLayer(),
						STATIC_TERRAIN_SCENARIO
							+ ":block=" + staticTerrainLifecycleEditBlock.toShortString()
							+ ":visibleSubmissions=" + diagnostics.visibleLayerSubmissions()
							+ ":waitFrames=" + framesWaitingForStaticTerrainLifecycle
					);
					staticTerrainLifecycleAfterRecorded = true;
					staticTerrainLifecycleStage = "fixture-visible";
					writeMetadata(minecraft, "static_terrain_translucent_fixture_visible");
				}
				return true;
			}
			if ("translucent-mixed-unsupported".equals(STATIC_TERRAIN_SCENARIO)
				&& !unsupportedFixtureReady
				&& (framesWaitingForStaticTerrainLifecycle % 30) == 0) {
				writeMetadata(minecraft, "waiting_for_static_terrain_unsupported_fluid_metadata");
			}
		}
		long observedGeneration = observedStaticTerrainGeneration();
		boolean replacementReady = staticTerrainReplacementReady(observedGeneration);
		if (replacementReady) {
			staticTerrainLifecycleAfterGeneration = observedGeneration;
			if (!staticTerrainLifecycleAfterRecorded) {
				RustGalTerrainRenderer.TerrainDiagnostics afterDiagnostics = RustGalTerrainRenderer.diagnosticsSnapshot();
				staticTerrainLifecycleAfterCachedLayers = afterDiagnostics.cachedLayerAssets();
				staticTerrainLifecycleAfterRssBytes = currentUsedMemoryBytes();
				RustGalTerrainRenderer.recordLifecycleMarker(
					"lifecycle-edit-after",
					staticTerrainLifecycleEditBlock,
					staticTerrainLifecycleLayer(),
					STATIC_TERRAIN_SCENARIO
						+ ":block=" + staticTerrainLifecycleEditBlock.toShortString()
						+ ":before=" + staticTerrainLifecycleBeforeGeneration
						+ ":after=" + staticTerrainLifecycleAfterGeneration
						+ ":waitFrames=" + framesWaitingForStaticTerrainLifecycle
				);
				staticTerrainLifecycleAfterRecorded = true;
				staticTerrainLifecycleStage = "replacement-visible";
				writeMetadata(minecraft, "static_terrain_lifecycle_replacement_visible");
			}
			return framesWaitingForStaticTerrainLifecycle >= Math.max(8, FRAMES_PER_POSE);
		}
		if (framesWaitingForStaticTerrainLifecycle > SETTLED_READY_MAX_WAIT_FRAMES) {
			fail("timed out waiting for static terrain lifecycle replacement: scenario="
				+ STATIC_TERRAIN_SCENARIO
				+ " block="
				+ (staticTerrainLifecycleEditBlock == null ? "null" : staticTerrainLifecycleEditBlock.toShortString())
				+ " beforeGeneration="
				+ staticTerrainLifecycleBeforeGeneration
				+ " afterGeneration="
				+ observedGeneration
				+ " stage="
				+ staticTerrainLifecycleStage);
		} else if ((framesWaitingForStaticTerrainLifecycle % 30) == 0) {
			writeMetadata(minecraft, "waiting_for_static_terrain_lifecycle_rebuild");
		}
		return false;
	}

	private static BlockPos chooseStaticTerrainLifecycleTarget(Minecraft minecraft, ServerLevel serverLevel) {
		if (!staticTerrainTranslucentScenario()) {
			return RustGalTerrainRenderer.chooseLifecycleEditTarget(STATIC_TERRAIN_SCENARIO);
		}
		BlockPos target = chooseStaticTerrainTranslucentPlacementTarget(minecraft, serverLevel);
		return target == null ? RustGalTerrainRenderer.chooseLifecycleEditTarget(STATIC_TERRAIN_SCENARIO) : target;
	}

	private static BlockPos chooseStaticTerrainTranslucentPlacementTarget(Minecraft minecraft, ServerLevel serverLevel) {
		if (minecraft.player == null || minecraft.level == null) {
			return null;
		}
		Direction forward = minecraft.player.getDirection();
		Direction right = forward.getClockWise();
		BlockPos eye = BlockPos.containing(minecraft.player.getEyePosition());
		BlockPos fallback = null;
		for (int distance = 4; distance <= 10; distance++) {
			for (int vertical = -2; vertical <= 2; vertical++) {
				for (int lateral = -2; lateral <= 2; lateral++) {
					BlockPos candidate = eye.relative(forward, distance).relative(right, lateral).above(vertical);
					if (!isLoadedAirBlock(minecraft.level, serverLevel, candidate)) {
						continue;
					}
					if (fallback == null) {
						fallback = candidate;
					}
					RustGalTerrainRenderer.TerrainLayerSnapshot solid =
						RustGalTerrainRenderer.snapshotLayer(candidate, ChunkSectionLayer.SOLID);
					if (solid != null && solid.meshGeneration() != 0L) {
						return candidate;
					}
				}
			}
		}
		return fallback;
	}

	private static boolean isLoadedAirBlock(ClientLevel clientLevel, ServerLevel serverLevel, BlockPos candidate) {
		if (!serverLevel.isLoaded(candidate) || !clientLevel.isLoaded(candidate)) {
			return false;
		}
		BlockState serverState = serverLevel.getBlockState(candidate);
		BlockState clientState = clientLevel.getBlockState(candidate);
		return serverState.isAir()
			&& clientState.isAir()
			&& serverState.getFluidState().isEmpty()
			&& clientState.getFluidState().isEmpty();
	}

	private static boolean staticTerrainLifecycleScenario() {
		return switch (staticTerrainBaseScenario()) {
			case "interior-edit", "boundary-x-edit", "boundary-y-edit", "boundary-z-edit", "section-reentry",
				"resource-reload", "opaque-texture-replacement", "cutout-texture-replacement",
				"pack-priority-reversal", "missing-atlas-payload", "malformed-png-payload", "translucent-glass",
				"translucent-overlap", "translucent-water", "translucent-mixed", "translucent-mixed-unsupported",
				"partial-texture-update", "model-resource-generation-change", "resize-cycle",
				"swapchain-recreate", "world-unload-reload", "world-different-reload",
				"view-distance-decrease", "view-distance-increase", "camera-relocation",
				"return-visited-terrain", "memory-cache-soak", "steady-state-performance" -> true;
			default -> false;
		};
	}

	private static boolean staticTerrainLiteralWorldTransitionScenario() {
		String scenario = staticTerrainBaseScenario();
		return "world-unload-reload".equals(scenario)
			|| "world-different-reload".equals(scenario);
	}

	private static boolean staticTerrainEditScenario() {
		return switch (staticTerrainBaseScenario()) {
			case "interior-edit", "boundary-x-edit", "boundary-y-edit", "boundary-z-edit", "section-reentry",
				"model-resource-generation-change" -> true;
			default -> false;
		};
	}

	private static boolean staticTerrainUsesAtlasGeneration() {
		return switch (staticTerrainBaseScenario()) {
			case "resource-reload", "opaque-texture-replacement", "cutout-texture-replacement",
				"pack-priority-reversal", "missing-atlas-payload", "malformed-png-payload",
				"partial-texture-update" -> true;
			default -> false;
		};
	}

	private static boolean staticTerrainAllowsAirSource() {
		return staticTerrainTranslucentScenario();
	}

	private static BlockState staticTerrainReplacementState() {
		if ("translucent-water".equals(STATIC_TERRAIN_SCENARIO)) {
			return Blocks.WATER.defaultBlockState();
		}
		if (staticTerrainTranslucentScenario()) {
			return staticTerrainTranslucentReplacementState();
		}
		return staticTerrainEditScenario() ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState();
	}

	private static String staticTerrainBaseScenario() {
		if (!STATIC_TERRAIN_SCENARIO.startsWith("translucent-")) {
			return STATIC_TERRAIN_SCENARIO;
		}
		String suffix = STATIC_TERRAIN_SCENARIO.substring("translucent-".length());
		return switch (suffix) {
			case "interior-edit", "boundary-x-edit", "boundary-y-edit", "boundary-z-edit", "section-reentry",
				"resource-reload", "opaque-texture-replacement", "cutout-texture-replacement",
				"pack-priority-reversal", "missing-atlas-payload", "malformed-png-payload",
				"partial-texture-update", "model-resource-generation-change", "resize-cycle",
				"swapchain-recreate", "world-unload-reload", "world-different-reload",
				"view-distance-decrease", "view-distance-increase", "camera-relocation",
				"return-visited-terrain", "memory-cache-soak", "steady-state-performance" -> suffix;
			case "rapid-edit" -> "interior-edit";
			case "quiescence-stationary-performance", "moving-camera-performance" -> "steady-state-performance";
			default -> STATIC_TERRAIN_SCENARIO;
		};
	}

	private static boolean staticTerrainTranslucentScenario() {
		if ("translucent-glass".equals(STATIC_TERRAIN_SCENARIO)
			|| "translucent-overlap".equals(STATIC_TERRAIN_SCENARIO)
			|| "translucent-water".equals(STATIC_TERRAIN_SCENARIO)
			|| "translucent-mixed".equals(STATIC_TERRAIN_SCENARIO)
			|| "translucent-mixed-unsupported".equals(STATIC_TERRAIN_SCENARIO)) {
			return true;
		}
		return STATIC_TERRAIN_SCENARIO.startsWith("translucent-")
			&& !staticTerrainBaseScenario().equals(STATIC_TERRAIN_SCENARIO);
	}

	private static boolean staticTerrainBaseTranslucentFixtureScenario() {
		return "translucent-glass".equals(STATIC_TERRAIN_SCENARIO)
			|| "translucent-overlap".equals(STATIC_TERRAIN_SCENARIO)
			|| "translucent-water".equals(STATIC_TERRAIN_SCENARIO)
			|| "translucent-mixed".equals(STATIC_TERRAIN_SCENARIO)
			|| "translucent-mixed-unsupported".equals(STATIC_TERRAIN_SCENARIO);
	}

	private static boolean staticTerrainRequiresTranslucentCameraSequence() {
		return "translucent-overlap".equals(STATIC_TERRAIN_SCENARIO)
			|| "translucent-moving-camera-performance".equals(STATIC_TERRAIN_SCENARIO);
	}

	private static ChunkSectionLayer staticTerrainLifecycleLayer() {
		return staticTerrainTranslucentScenario() ? ChunkSectionLayer.TRANSLUCENT : ChunkSectionLayer.SOLID;
	}

	private static BlockState staticTerrainTranslucentReplacementState() {
		for (BlockState candidate : List.of(
			Blocks.BLUE_STAINED_GLASS.defaultBlockState(),
			Blocks.TINTED_GLASS.defaultBlockState(),
			Blocks.ICE.defaultBlockState(),
			Blocks.SLIME_BLOCK.defaultBlockState(),
			Blocks.HONEY_BLOCK.defaultBlockState()
		)) {
			if (ItemBlockRenderTypes.getChunkRenderType(candidate) == ChunkSectionLayer.TRANSLUCENT) {
				return candidate;
			}
		}
		return Blocks.BLUE_STAINED_GLASS.defaultBlockState();
	}

	private static void applyStaticTerrainLifecycleAction(
		Minecraft minecraft,
		ServerLevel serverLevel,
		BlockPos target,
		BlockState replacement
	) {
		String scenario = staticTerrainBaseScenario();
		if (staticTerrainTranslucentScenario()
			&& !"translucent-glass".equals(STATIC_TERRAIN_SCENARIO)
			&& !"translucent-water".equals(STATIC_TERRAIN_SCENARIO)
			&& !"translucent-mixed".equals(STATIC_TERRAIN_SCENARIO)
			&& !"translucent-mixed-unsupported".equals(STATIC_TERRAIN_SCENARIO)
			&& !staticTerrainTranslucentFixtureApplied) {
			applyStaticTerrainTranslucentOverlap(minecraft, serverLevel, target);
			staticTerrainTranslucentFixtureApplied = true;
			staticTerrainLifecycleStage = "translucent-fixture-placed";
			RustGalTerrainRenderer.recordLifecycleMarker(
				"lifecycle-translucent-overlap-placed",
				target,
				ChunkSectionLayer.TRANSLUCENT,
				STATIC_TERRAIN_SCENARIO + ":baseScenario=" + scenario + ":block=" + target.toShortString()
			);
		}
		if (staticTerrainEditScenario()) {
			applyStaticTerrainLifecycleEdit(serverLevel, minecraft.level, target, replacement);
			return;
		}
		switch (scenario) {
			case "translucent-glass" -> {
				applyStaticTerrainLifecycleEdit(serverLevel, minecraft.level, target, replacement);
				staticTerrainLifecycleStage = "translucent-glass-placed";
				RustGalTerrainRenderer.recordLifecycleMarker(
					"lifecycle-translucent-glass-placed",
					target,
					ChunkSectionLayer.TRANSLUCENT,
					STATIC_TERRAIN_SCENARIO + ":block=" + target.toShortString()
				);
			}
			case "translucent-overlap" -> {
				applyStaticTerrainTranslucentOverlap(minecraft, serverLevel, target);
				staticTerrainTranslucentFixtureApplied = true;
				staticTerrainLifecycleStage = "translucent-overlap-placed";
				RustGalTerrainRenderer.recordLifecycleMarker(
					"lifecycle-translucent-overlap-placed",
					target,
					ChunkSectionLayer.TRANSLUCENT,
					STATIC_TERRAIN_SCENARIO + ":block=" + target.toShortString()
				);
			}
			case "translucent-water" -> {
				applyStaticTerrainLifecycleEdit(serverLevel, minecraft.level, target, Blocks.WATER.defaultBlockState());
				staticTerrainLifecycleStage = "translucent-water-placed";
				RustGalTerrainRenderer.recordLifecycleMarker(
					"lifecycle-translucent-water-placed",
					target,
					ChunkSectionLayer.TRANSLUCENT,
					STATIC_TERRAIN_SCENARIO + ":block=" + target.toShortString()
				);
			}
			case "translucent-mixed", "translucent-mixed-unsupported" -> {
				applyStaticTerrainTranslucentMixed(minecraft, serverLevel, target,
					"translucent-mixed-unsupported".equals(STATIC_TERRAIN_SCENARIO));
				minecraft.levelRenderer.allChanged();
				staticTerrainTranslucentFixtureApplied = true;
				staticTerrainLifecycleStage = STATIC_TERRAIN_SCENARIO + "-placed";
				RustGalTerrainRenderer.recordLifecycleMarker(
					"lifecycle-" + STATIC_TERRAIN_SCENARIO + "-placed",
					target,
					ChunkSectionLayer.TRANSLUCENT,
					STATIC_TERRAIN_SCENARIO + ":block=" + target.toShortString()
				);
			}
			case "resource-reload", "pack-priority-reversal" -> {
				RustGalTerrainRenderer.invalidateForResourceReload();
				minecraft.levelRenderer.allChanged();
				staticTerrainLifecycleStage = "resource-reload-started";
				RustGalTerrainRenderer.recordLifecycleMarker(
						"lifecycle-resource-reload-started",
						target,
						staticTerrainLifecycleLayer(),
						STATIC_TERRAIN_SCENARIO + ":pack=" + STATIC_TERRAIN_RESOURCE_PACK_SCENARIO + ":mode=bounded-terrain-rebuild"
					);
			}
			case "opaque-texture-replacement" ->
				RustGalTerrainRenderer.injectAtlasTexturePayloadForDiagnostics(staticTerrainValidTinyPngPayload(), "lifecycle-atlas-opaque-texture-replacement");
			case "cutout-texture-replacement" ->
				RustGalTerrainRenderer.injectAtlasTexturePayloadForDiagnostics(staticTerrainValidTinyPngPayload(), "lifecycle-atlas-cutout-texture-replacement");
			case "missing-atlas-payload" ->
				RustGalTerrainRenderer.injectAtlasTexturePayloadForDiagnostics(new byte[0], "lifecycle-atlas-missing-payload");
			case "malformed-png-payload" ->
				RustGalTerrainRenderer.injectAtlasTexturePayloadForDiagnostics("not a png".getBytes(StandardCharsets.UTF_8), "lifecycle-atlas-malformed-payload");
			case "partial-texture-update" ->
				RustGalTerrainRenderer.injectAtlasTexturePayloadForDiagnostics(staticTerrainValidTinyPngPayload(), "lifecycle-atlas-partial-texture-update");
			case "resize-cycle", "swapchain-recreate" -> {
				staticTerrainOriginalRenderDistance = minecraft.options.renderDistance().get();
				staticTerrainOriginalSimulationDistance = minecraft.options.simulationDistance().get();
				minecraft.getWindow().setWindowed(960, 540);
				minecraft.resizeDisplay();
				staticTerrainLifecycleResizeCount = 1;
				staticTerrainLifecycleStage = "resize-1";
				RustGalTerrainRenderer.recordLifecycleMarker("lifecycle-resize-1", target, staticTerrainLifecycleLayer(), "extent=960x540");
			}
			case "view-distance-decrease" -> {
				staticTerrainOriginalRenderDistance = minecraft.options.renderDistance().get();
				staticTerrainOriginalSimulationDistance = minecraft.options.simulationDistance().get();
				minecraft.options.renderDistance().set(Math.max(2, Math.min(staticTerrainOriginalRenderDistance, 4)));
				minecraft.options.simulationDistance().set(Math.max(2, Math.min(staticTerrainOriginalSimulationDistance, 4)));
				staticTerrainLifecycleStage = "view-distance-decreased";
				RustGalTerrainRenderer.recordLifecycleMarker(
						"lifecycle-view-distance-decreased",
						target,
						staticTerrainLifecycleLayer(),
						"renderDistance=" + minecraft.options.renderDistance().get()
						+ ":simulationDistance=" + minecraft.options.simulationDistance().get()
				);
			}
			case "view-distance-increase", "memory-cache-soak", "steady-state-performance" -> {
				staticTerrainOriginalRenderDistance = minecraft.options.renderDistance().get();
				staticTerrainOriginalSimulationDistance = minecraft.options.simulationDistance().get();
				minecraft.options.renderDistance().set(Math.max(staticTerrainOriginalRenderDistance, 12));
				minecraft.options.simulationDistance().set(Math.max(staticTerrainOriginalSimulationDistance, 12));
				staticTerrainLifecycleStage = "visibility-expanded";
				RustGalTerrainRenderer.recordLifecycleMarker(
						"lifecycle-view-distance-increased",
						target,
						staticTerrainLifecycleLayer(),
						"renderDistance=" + minecraft.options.renderDistance().get()
						+ ":simulationDistance=" + minecraft.options.simulationDistance().get()
				);
				if ("memory-cache-soak".equals(scenario)) {
					RustGalTerrainRenderer.recordLifecycleMarker(
							"lifecycle-memory-cache-soak-started",
							target,
							staticTerrainLifecycleLayer(),
							"usedMemoryBytes=" + currentUsedMemoryBytes()
					);
				}
				if ("steady-state-performance".equals(scenario) || "quiescence-stationary-performance".equals(scenario)) {
					RustGalTerrainRenderer.recordLifecycleMarker(
							"lifecycle-steady-state-performance-started",
							target,
							staticTerrainLifecycleLayer(),
							"cachedLayers=" + RustGalTerrainRenderer.diagnosticsSnapshot().cachedLayerAssets()
					);
				}
			}
			case "camera-relocation", "return-visited-terrain" -> {
				staticTerrainOriginalRenderDistance = minecraft.options.renderDistance().get();
				staticTerrainOriginalSimulationDistance = minecraft.options.simulationDistance().get();
				minecraft.options.renderDistance().set(Math.max(staticTerrainOriginalRenderDistance, 12));
				minecraft.options.simulationDistance().set(Math.max(staticTerrainOriginalSimulationDistance, 12));
				staticTerrainOriginalPosition = initialPosition;
				Vec3 relocated = initialPosition.add(64.0, 0.0, 64.0);
				initialPosition = relocated;
				if (minecraft.player != null) {
					minecraft.player.setPos(relocated);
					minecraft.player.setOldPosAndRot(relocated, initialPose.yaw(), initialPose.pitch());
				}
				staticTerrainLifecycleStage = "camera-relocated";
				RustGalTerrainRenderer.recordLifecycleMarker(
						"lifecycle-camera-relocated",
						target,
						staticTerrainLifecycleLayer(),
						"position=" + formatVec(relocated)
				);
				if ("return-visited-terrain".equals(scenario)) {
					staticTerrainLifecycleActionStep = 1;
					RustGalTerrainRenderer.recordLifecycleMarker(
							"lifecycle-return-visited-terrain-away",
							target,
							staticTerrainLifecycleLayer(),
							"position=" + formatVec(relocated)
					);
				}
			}
			default -> staticTerrainLifecycleStage = "action-applied";
		}
		recordStaticTerrainLifecycleFaultMarker(target);
	}

	private static boolean setupStaticTerrainLiteralWorldTransition(Minecraft minecraft) {
		if (staticTerrainLifecycleAfterRecorded && "replacement-visible".equals(staticTerrainLifecycleStage)) {
			return true;
		}
		framesWaitingForStaticTerrainLifecycle++;
		String scenario = staticTerrainBaseScenario();
		if (framesWaitingForStaticTerrainLifecycle > SETTLED_READY_MAX_WAIT_FRAMES) {
			fail("timed out waiting for literal static terrain world transition: scenario="
				+ STATIC_TERRAIN_SCENARIO
				+ " stage="
				+ staticTerrainLifecycleStage
				+ " actionStep="
				+ staticTerrainLifecycleActionStep);
		}
		if (staticTerrainLifecycleTransitionInProgress) {
			return false;
		}
		staticTerrainLifecycleWorldA = STATIC_TERRAIN_WORLD_ID.isEmpty() ? "Origin" : STATIC_TERRAIN_WORLD_ID;
		staticTerrainLifecycleWorldB = STATIC_TERRAIN_WORLD_B_ID.isEmpty()
			? staticTerrainLifecycleWorldA + "-different"
			: STATIC_TERRAIN_WORLD_B_ID;
		RustGalTerrainRenderer.TerrainDiagnostics diagnostics = RustGalTerrainRenderer.diagnosticsSnapshot();
		switch (staticTerrainLifecycleActionStep) {
			case 0 -> {
				if (staticTerrainTranslucentScenario() && !staticTerrainTranslucentFixtureApplied) {
					if (minecraft.player == null || minecraft.level == null || minecraft.getSingleplayerServer() == null) {
						staticTerrainLifecycleStage = "waiting-for-translucent-world-a-fixture-world";
						return false;
					}
					ServerLevel serverLevel = minecraft.getSingleplayerServer().getLevel(minecraft.level.dimension());
					BlockPos target = serverLevel == null ? null : chooseStaticTerrainTranslucentPlacementTarget(minecraft, serverLevel);
					if (serverLevel == null || target == null) {
						staticTerrainLifecycleStage = "waiting-for-translucent-world-a-fixture-target";
						return false;
					}
					applyStaticTerrainTranslucentOverlap(minecraft, serverLevel, target);
					staticTerrainTranslucentFixtureApplied = true;
					RustGalTerrainRenderer.recordLifecycleMarker(
						"lifecycle-translucent-overlap-placed",
						target,
						ChunkSectionLayer.TRANSLUCENT,
						STATIC_TERRAIN_SCENARIO + ":phase=world-a-transition-fixture:block=" + target.toShortString()
					);
					staticTerrainLifecycleStage = "translucent-world-a-fixture-placed";
					return false;
				}
				if (!staticTerrainInPlayableWorld(minecraft)
					|| diagnostics.visibleLayerSubmissions() <= 0
					|| diagnostics.cachedLayerAssets() <= 0) {
					staticTerrainLifecycleStage = "waiting-for-initial-world-a-terrain";
					return false;
				}
				staticTerrainLifecycleBeforeGeneration = Math.max(1L, diagnostics.atlasGeneration());
				staticTerrainLifecycleBeforeCachedLayers = diagnostics.cachedLayerAssets();
				staticTerrainLifecycleBeforeRssBytes = currentUsedMemoryBytes();
				staticTerrainUnloadSubmissionSnapshot = diagnostics.visibleLayerSubmissions();
				staticTerrainLifecycleSetup = true;
				staticTerrainLifecycleStage = "leaving-world-a";
				RustGalTerrainRenderer.recordLifecycleMarker(
					"lifecycle-world-unload",
					BlockPos.ZERO,
					ChunkSectionLayer.SOLID,
					"world=" + staticTerrainLifecycleWorldA + ":dimension=" + initialDimension
				);
				staticTerrainLifecycleTransitionInProgress = true;
				staticTerrainLifecycleActionStep = 1;
				framesWaitingForStaticTerrainLifecycle = 0;
				try {
					minecraft.disconnectFromWorld(Component.literal("static terrain lifecycle transition"));
				} finally {
					staticTerrainLifecycleTransitionInProgress = false;
				}
				return false;
			}
			case 1 -> {
				if (staticTerrainInPlayableWorld(minecraft)) {
					staticTerrainLifecycleStage = "waiting-for-menu-after-world-a";
					return false;
				}
				diagnostics = RustGalTerrainRenderer.diagnosticsSnapshot();
				staticTerrainMenuCachedLayers = diagnostics.cachedLayerAssets();
				staticTerrainMenuActiveLayerSnapshot = diagnostics.activeTerrainLayers();
				staticTerrainMenuActiveSectionAssetSnapshot = diagnostics.activeSectionAssets();
				staticTerrainMenuCurrentFrameSubmissionSnapshot = diagnostics.currentFrameVisibleLayerSubmissions();
				staticTerrainMenuUsedMemoryBytes = currentUsedMemoryBytes();
				staticTerrainMenuSubmissionSnapshot = diagnostics.visibleLayerSubmissions();
				if (staticTerrainMenuSubmissionSnapshot != staticTerrainUnloadSubmissionSnapshot
					|| staticTerrainMenuCachedLayers != 0
					|| staticTerrainMenuActiveLayerSnapshot != 0
					|| staticTerrainMenuActiveSectionAssetSnapshot != 0
					|| staticTerrainMenuCurrentFrameSubmissionSnapshot != 0) {
					staticTerrainLifecycleStage = "menu-baseline-invalid";
					return false;
				}
				RustGalTerrainRenderer.recordLifecycleMarker(
					"lifecycle-world-menu-baseline",
					BlockPos.ZERO,
					ChunkSectionLayer.SOLID,
					"world=" + staticTerrainLifecycleWorldA
						+ ":cachedLayers=" + staticTerrainMenuCachedLayers
						+ ":activeLayers=" + staticTerrainMenuActiveLayerSnapshot
						+ ":activeSectionAssets=" + staticTerrainMenuActiveSectionAssetSnapshot
						+ ":currentFrameVisibleSubmissions=" + staticTerrainMenuCurrentFrameSubmissionSnapshot
						+ ":visibleSubmissions=" + staticTerrainMenuSubmissionSnapshot
				);
				RustGalTerrainRenderer.recordLifecycleMarker(
					"lifecycle-world-reload-requested",
					BlockPos.ZERO,
					ChunkSectionLayer.SOLID,
					"world=" + staticTerrainLifecycleWorldA
				);
				staticTerrainLifecycleStage = "reloading-world-a";
				staticTerrainLifecycleTransitionInProgress = true;
				staticTerrainLifecycleActionStep = 2;
				framesWaitingForStaticTerrainLifecycle = 0;
				try {
					minecraft.createWorldOpenFlows().openWorld(staticTerrainLifecycleWorldA, () -> minecraft.setScreen(new TitleScreen()));
				} finally {
					staticTerrainLifecycleTransitionInProgress = false;
				}
				return false;
			}
			case 2 -> {
				if (!staticTerrainInPlayableWorld(minecraft)
					|| diagnostics.visibleLayerSubmissions() <= staticTerrainMenuSubmissionSnapshot
					|| diagnostics.cachedLayerAssets() <= 0) {
					staticTerrainLifecycleStage = "waiting-for-world-a-reload-terrain";
					return false;
				}
				staticTerrainReloadGenerationA = Math.max(1L, diagnostics.atlasGeneration());
				staticTerrainLifecycleAfterGeneration = staticTerrainReloadGenerationA;
				staticTerrainLifecycleAfterCachedLayers = diagnostics.cachedLayerAssets();
				staticTerrainLifecycleAfterRssBytes = currentUsedMemoryBytes();
				RustGalTerrainRenderer.recordLifecycleMarker(
					"lifecycle-world-reload-valid",
					BlockPos.ZERO,
					ChunkSectionLayer.SOLID,
					"world=" + staticTerrainLifecycleWorldA
						+ ":generation=" + staticTerrainReloadGenerationA
						+ ":cachedLayers=" + diagnostics.cachedLayerAssets()
				);
				if (!"world-different-reload".equals(scenario)) {
					staticTerrainLifecycleAfterRecorded = true;
					staticTerrainLifecycleStage = "replacement-visible";
					framesWaitingForStaticTerrainLifecycle++;
					return framesWaitingForStaticTerrainLifecycle >= Math.max(8, FRAMES_PER_POSE);
				}
				staticTerrainUnloadSubmissionSnapshot = diagnostics.visibleLayerSubmissions();
				RustGalTerrainRenderer.recordLifecycleMarker(
					"lifecycle-world-unload",
					BlockPos.ZERO,
					ChunkSectionLayer.SOLID,
					"world=" + staticTerrainLifecycleWorldA + ":phase=second"
				);
				staticTerrainLifecycleStage = "leaving-world-a-second";
				staticTerrainLifecycleTransitionInProgress = true;
				staticTerrainLifecycleActionStep = 3;
				framesWaitingForStaticTerrainLifecycle = 0;
				try {
					minecraft.disconnectFromWorld(Component.literal("static terrain lifecycle transition to second world"));
				} finally {
					staticTerrainLifecycleTransitionInProgress = false;
				}
				return false;
			}
			case 3 -> {
				if (staticTerrainInPlayableWorld(minecraft)) {
					staticTerrainLifecycleStage = "waiting-for-menu-before-world-b";
					return false;
				}
				diagnostics = RustGalTerrainRenderer.diagnosticsSnapshot();
				if (diagnostics.visibleLayerSubmissions() != staticTerrainUnloadSubmissionSnapshot
					|| diagnostics.cachedLayerAssets() != 0
					|| diagnostics.activeTerrainLayers() != 0
					|| diagnostics.activeSectionAssets() != 0
					|| diagnostics.currentFrameVisibleLayerSubmissions() != 0) {
					staticTerrainLifecycleStage = "menu-before-world-b-baseline-invalid";
					return false;
				}
				staticTerrainMenuSubmissionSnapshot = diagnostics.visibleLayerSubmissions();
				staticTerrainMenuCachedLayers = diagnostics.cachedLayerAssets();
				staticTerrainMenuActiveLayerSnapshot = diagnostics.activeTerrainLayers();
				staticTerrainMenuActiveSectionAssetSnapshot = diagnostics.activeSectionAssets();
				staticTerrainMenuCurrentFrameSubmissionSnapshot = diagnostics.currentFrameVisibleLayerSubmissions();
				RustGalTerrainRenderer.recordLifecycleMarker(
					"lifecycle-world-menu-baseline",
					BlockPos.ZERO,
					ChunkSectionLayer.SOLID,
					"world=" + staticTerrainLifecycleWorldB
						+ ":cachedLayers=" + diagnostics.cachedLayerAssets()
						+ ":activeLayers=" + diagnostics.activeTerrainLayers()
						+ ":activeSectionAssets=" + diagnostics.activeSectionAssets()
						+ ":currentFrameVisibleSubmissions=" + diagnostics.currentFrameVisibleLayerSubmissions()
						+ ":visibleSubmissions=" + diagnostics.visibleLayerSubmissions()
				);
				RustGalTerrainRenderer.recordLifecycleMarker(
					"lifecycle-world-reload-requested",
					BlockPos.ZERO,
					ChunkSectionLayer.SOLID,
					"world=" + staticTerrainLifecycleWorldB
				);
				staticTerrainLifecycleStage = "loading-world-b";
				staticTerrainLifecycleTransitionInProgress = true;
				staticTerrainLifecycleActionStep = 4;
				framesWaitingForStaticTerrainLifecycle = 0;
				try {
					minecraft.createWorldOpenFlows().openWorld(staticTerrainLifecycleWorldB, () -> minecraft.setScreen(new TitleScreen()));
				} finally {
					staticTerrainLifecycleTransitionInProgress = false;
				}
				return false;
			}
			case 4 -> {
				if (staticTerrainTranslucentScenario() && !staticTerrainTranslucentWorldBFixtureApplied) {
					if (minecraft.player == null || minecraft.level == null || minecraft.getSingleplayerServer() == null) {
						staticTerrainLifecycleStage = "waiting-for-translucent-world-b-fixture-world";
						return false;
					}
					ServerLevel serverLevel = minecraft.getSingleplayerServer().getLevel(minecraft.level.dimension());
					BlockPos target = serverLevel == null ? null : chooseStaticTerrainTranslucentPlacementTarget(minecraft, serverLevel);
					if (serverLevel == null || target == null) {
						staticTerrainLifecycleStage = "waiting-for-translucent-world-b-fixture-target";
						return false;
					}
					applyStaticTerrainTranslucentOverlap(minecraft, serverLevel, target);
					staticTerrainTranslucentWorldBFixtureApplied = true;
					RustGalTerrainRenderer.recordLifecycleMarker(
						"lifecycle-translucent-overlap-placed",
						target,
						ChunkSectionLayer.TRANSLUCENT,
						STATIC_TERRAIN_SCENARIO + ":phase=world-b-transition-fixture:block=" + target.toShortString()
					);
					if (STATIC_TERRAIN_FAULT.startsWith("translucent-")) {
						recordStaticTerrainLifecycleFaultMarker(target);
					}
					staticTerrainLifecycleStage = "translucent-world-b-fixture-placed";
					return false;
				}
				if (!staticTerrainInPlayableWorld(minecraft)
					|| diagnostics.visibleLayerSubmissions() <= staticTerrainUnloadSubmissionSnapshot
					|| diagnostics.cachedLayerAssets() <= 0) {
					staticTerrainLifecycleStage = "waiting-for-world-b-terrain";
					return false;
				}
				staticTerrainReloadGenerationB = Math.max(1L, diagnostics.atlasGeneration());
				staticTerrainLifecycleAfterGeneration = staticTerrainReloadGenerationB;
				staticTerrainLifecycleAfterCachedLayers = diagnostics.cachedLayerAssets();
				staticTerrainLifecycleAfterRssBytes = currentUsedMemoryBytes();
				if ("cross-world-stale-submission".equals(STATIC_TERRAIN_FAULT)
					&& !staticTerrainCrossWorldStaleFaultInjected) {
					staticTerrainCrossWorldStaleFaultInjected = true;
					RustGalTerrainRenderer.injectCrossWorldStaleSubmissionForDiagnostics(
						minecraft.getWindow().getWidth(),
						minecraft.getWindow().getHeight()
					);
				}
				staticTerrainLifecycleAfterRecorded = true;
				staticTerrainLifecycleStage = "replacement-visible";
				RustGalTerrainRenderer.recordLifecycleMarker(
					"lifecycle-world-reload-valid",
					BlockPos.ZERO,
					ChunkSectionLayer.SOLID,
					"world=" + staticTerrainLifecycleWorldB
						+ ":generation=" + staticTerrainReloadGenerationB
						+ ":cachedLayers=" + diagnostics.cachedLayerAssets()
				);
				framesWaitingForStaticTerrainLifecycle++;
				return framesWaitingForStaticTerrainLifecycle >= Math.max(8, FRAMES_PER_POSE);
			}
			default -> {
				staticTerrainLifecycleStage = "replacement-visible";
				return true;
			}
		}
	}

	private static boolean staticTerrainInPlayableWorld(Minecraft minecraft) {
		return minecraft.player != null && minecraft.level != null && minecraft.getSingleplayerServer() != null;
	}

	private static boolean continueStaticTerrainLifecycleAction(Minecraft minecraft) {
		String scenario = staticTerrainBaseScenario();
		if (staticTerrainReloadFuture != null && !staticTerrainReloadFuture.isDone()) {
			staticTerrainLifecycleStage = "waiting-for-resource-reload";
			return true;
		}
		if (("resize-cycle".equals(scenario) || "swapchain-recreate".equals(scenario))
			&& staticTerrainLifecycleResizeCount == 1
			&& framesWaitingForStaticTerrainLifecycle >= Math.max(2, FRAMES_PER_POSE / 2)) {
			minecraft.getWindow().setWindowed(1280, 720);
			minecraft.resizeDisplay();
			staticTerrainLifecycleResizeCount = 2;
			staticTerrainLifecycleStage = "resize-2";
			RustGalTerrainRenderer.recordLifecycleMarker("lifecycle-resize-2", staticTerrainLifecycleEditBlock, staticTerrainLifecycleLayer(), "extent=1280x720");
			return true;
		}
		if ("return-visited-terrain".equals(scenario)
			&& staticTerrainLifecycleActionStep == 1
			&& staticTerrainOriginalPosition != null
			&& framesWaitingForStaticTerrainLifecycle >= Math.max(2, FRAMES_PER_POSE / 2)) {
			initialPosition = staticTerrainOriginalPosition;
			if (minecraft.player != null && initialPose != null) {
				minecraft.player.setPos(initialPosition);
				minecraft.player.setOldPosAndRot(initialPosition, initialPose.yaw(), initialPose.pitch());
			}
			staticTerrainLifecycleActionStep = 2;
			staticTerrainLifecycleStage = "returned-to-visited-terrain";
			RustGalTerrainRenderer.recordLifecycleMarker(
				"lifecycle-return-visited-terrain-back",
				staticTerrainLifecycleEditBlock,
				staticTerrainLifecycleLayer(),
				"position=" + formatVec(initialPosition)
			);
			return true;
		}
		return false;
	}

	private static long observedStaticTerrainGeneration() {
		if (staticTerrainUsesAtlasGeneration()) {
			return RustGalTerrainRenderer.diagnosticsSnapshot().atlasGeneration();
		}
		if (staticTerrainAllowsAirSource()) {
			RustGalTerrainRenderer.TerrainLayerSnapshot after =
				RustGalTerrainRenderer.snapshotLayer(staticTerrainLifecycleEditBlock, ChunkSectionLayer.TRANSLUCENT);
			return after == null ? 0L : after.meshGeneration();
		}
		if (!staticTerrainEditScenario()) {
			return Math.max(1L, RustGalTerrainRenderer.diagnosticsSnapshot().atlasGeneration());
		}
		RustGalTerrainRenderer.TerrainLayerSnapshot after =
			RustGalTerrainRenderer.snapshotLayer(staticTerrainLifecycleEditBlock, ChunkSectionLayer.SOLID);
		return after == null ? 0L : after.meshGeneration();
	}

	private static boolean staticTerrainReplacementReady(long observedGeneration) {
		if (observedGeneration == 0L) {
			return false;
		}
		if (staticTerrainAllowsAirSource()) {
			return RustGalTerrainRenderer.diagnosticsSnapshot().visibleLayerSubmissions() > 0
				&& framesWaitingForStaticTerrainLifecycle >= Math.max(4, FRAMES_PER_POSE / 2);
		}
		if (staticTerrainEditScenario() || staticTerrainUsesAtlasGeneration()) {
			return observedGeneration != staticTerrainLifecycleBeforeGeneration;
		}
		return RustGalTerrainRenderer.diagnosticsSnapshot().visibleLayerSubmissions() > 0
			&& framesWaitingForStaticTerrainLifecycle >= Math.max(4, FRAMES_PER_POSE / 2);
	}

	private static byte[] staticTerrainValidTinyPngPayload() {
		BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(0, 0, 0xffff3355);
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			ImageIO.write(image, "png", output);
			return output.toByteArray();
		} catch (IOException error) {
			throw new IllegalStateException("Failed to create diagnostic static-terrain atlas PNG", error);
		}
	}

	private static long currentUsedMemoryBytes() {
		Runtime runtime = Runtime.getRuntime();
		return Math.max(0L, runtime.totalMemory() - runtime.freeMemory());
	}

	private static void applyStaticTerrainLifecycleEdit(ServerLevel serverLevel, ClientLevel clientLevel, BlockPos target, BlockState replacement) {
		serverLevel.setBlock(target, replacement, 3);
		clientLevel.setBlock(target, replacement, 3);
	}

	private static void applyStaticTerrainTranslucentOverlap(Minecraft minecraft, ServerLevel serverLevel, BlockPos target) {
		if (minecraft.player == null || minecraft.level == null) {
			return;
		}
		Direction forward = minecraft.player.getDirection();
		Direction right = forward.getClockWise();
		BlockState[] layers = {
			Blocks.RED_STAINED_GLASS.defaultBlockState(),
			Blocks.GREEN_STAINED_GLASS.defaultBlockState(),
			Blocks.BLUE_STAINED_GLASS.defaultBlockState()
		};
		for (int depth = 0; depth < layers.length; depth++) {
			BlockPos planeBase = target.relative(forward, depth * 2);
			for (int lateral = -1; lateral <= 1; lateral++) {
				for (int vertical = 0; vertical <= 2; vertical++) {
					BlockPos pos = planeBase.relative(right, lateral).above(vertical);
					if (serverLevel.isLoaded(pos) && minecraft.level.isLoaded(pos)) {
						applyStaticTerrainLifecycleEdit(serverLevel, minecraft.level, pos, layers[depth]);
					}
				}
			}
		}
		BlockState crossing = Blocks.ORANGE_STAINED_GLASS.defaultBlockState();
		BlockPos crossingBase = target.relative(forward, 1).relative(right, 2);
		for (int depth = 0; depth <= 4; depth++) {
			for (int vertical = 0; vertical <= 2; vertical++) {
				BlockPos pos = crossingBase.relative(forward, depth).above(vertical);
				if (serverLevel.isLoaded(pos) && minecraft.level.isLoaded(pos)) {
					applyStaticTerrainLifecycleEdit(serverLevel, minecraft.level, pos, crossing);
				}
			}
		}
	}

	private static void applyStaticTerrainTranslucentMixed(
		Minecraft minecraft,
		ServerLevel serverLevel,
		BlockPos target,
		boolean includeUnsupportedFluid
	) {
		if (minecraft.player == null || minecraft.level == null) {
			return;
		}
		Direction forward = minecraft.player.getDirection();
		Direction right = forward.getClockWise();
		BlockState glass = Blocks.BLUE_STAINED_GLASS.defaultBlockState();
		BlockState water = Blocks.WATER.defaultBlockState();
		BlockState lava = Blocks.LAVA.defaultBlockState();
		BlockPos[] positions = {
			target,
			target.relative(right),
			target.relative(forward),
			target.relative(forward).relative(right),
			target.above(),
			target.above().relative(right)
		};
		BlockState[] states = {
			glass,
			water,
			water,
			glass,
			glass,
			water
		};
		for (int index = 0; index < positions.length; index++) {
			BlockPos pos = positions[index];
			if (serverLevel.isLoaded(pos) && minecraft.level.isLoaded(pos)) {
				applyStaticTerrainLifecycleEdit(serverLevel, minecraft.level, pos, states[index]);
			}
		}
		if (includeUnsupportedFluid) {
			BlockPos unsupported = sameSectionBlockPos(target, 8, 8, 8);
			BlockPos[] clear = {
				unsupported.above(),
				unsupported.below(),
				unsupported.north(),
				unsupported.south(),
				unsupported.east(),
				unsupported.west()
			};
			for (BlockPos air : clear) {
				if (serverLevel.isLoaded(air) && minecraft.level.isLoaded(air)) {
					applyStaticTerrainLifecycleEdit(serverLevel, minecraft.level, air, Blocks.AIR.defaultBlockState());
				}
			}
			BlockPos[] companionPositions = {
				sameSectionBlockPos(target, 10, unsupported.getY() & 15, 8),
				sameSectionBlockPos(target, 12, unsupported.getY() & 15, 8),
				sameSectionBlockPos(target, 8, unsupported.getY() & 15, 10),
				sameSectionBlockPos(target, 10, unsupported.getY() & 15, 10)
			};
			BlockState[] companionStates = {
				glass,
				water,
				glass,
				water
			};
			for (int index = 0; index < companionPositions.length; index++) {
				BlockPos companion = companionPositions[index];
				if (serverLevel.isLoaded(companion) && minecraft.level.isLoaded(companion)) {
					applyStaticTerrainLifecycleEdit(serverLevel, minecraft.level, companion, companionStates[index]);
				}
			}
			if (serverLevel.isLoaded(unsupported) && minecraft.level.isLoaded(unsupported)) {
				applyStaticTerrainLifecycleEdit(serverLevel, minecraft.level, unsupported, lava);
				RustGalTerrainRenderer.recordLifecycleMarker(
					"lifecycle-translucent-unsupported-fluid-placed",
					unsupported,
					ChunkSectionLayer.TRANSLUCENT,
					STATIC_TERRAIN_SCENARIO
						+ ":block=" + unsupported.toShortString()
						+ ":serverState=" + serverLevel.getBlockState(unsupported)
						+ ":clientState=" + minecraft.level.getBlockState(unsupported)
						+ ":serverFluid=" + serverLevel.getFluidState(unsupported)
						+ ":clientFluid=" + minecraft.level.getFluidState(unsupported)
				);
			} else {
				RustGalTerrainRenderer.recordLifecycleMarker(
					"lifecycle-translucent-unsupported-fluid-not-loaded",
					unsupported,
					ChunkSectionLayer.TRANSLUCENT,
					STATIC_TERRAIN_SCENARIO
						+ ":block=" + unsupported.toShortString()
						+ ":serverLoaded=" + serverLevel.isLoaded(unsupported)
						+ ":clientLoaded=" + minecraft.level.isLoaded(unsupported)
				);
			}
		}
	}

	private static BlockPos sameSectionBlockPos(BlockPos anchor, int localX, int localY, int localZ) {
		return new BlockPos(
			(anchor.getX() & ~15) + Math.max(0, Math.min(15, localX)),
			(anchor.getY() & ~15) + Math.max(0, Math.min(15, localY)),
			(anchor.getZ() & ~15) + Math.max(0, Math.min(15, localZ))
		);
	}

	private static void recordStaticTerrainLifecycleFaultMarker(BlockPos target) {
		switch (STATIC_TERRAIN_FAULT) {
			case "old-generation-after-edit" -> RustGalTerrainRenderer.recordLifecycleMarker(
				"lifecycle-fault-old-generation-after-edit",
				target,
				ChunkSectionLayer.SOLID,
				STATIC_TERRAIN_SCENARIO + ":block=" + target.toShortString()
			);
			case "old-new-together" -> RustGalTerrainRenderer.recordLifecycleMarker(
				"lifecycle-fault-old-new-overlap",
				target,
				ChunkSectionLayer.SOLID,
				STATIC_TERRAIN_SCENARIO + ":block=" + target.toShortString()
			);
			case "removed-section-resubmitted" -> RustGalTerrainRenderer.recordLifecycleMarker(
				"lifecycle-fault-removed-section-resubmitted",
				target,
				ChunkSectionLayer.SOLID,
				STATIC_TERRAIN_SCENARIO + ":block=" + target.toShortString()
			);
			case "wrong-neighbor-invalidated" -> RustGalTerrainRenderer.recordLifecycleMarker(
				"lifecycle-fault-wrong-neighbor-invalidated",
				target,
				ChunkSectionLayer.SOLID,
				STATIC_TERRAIN_SCENARIO + ":block=" + target.toShortString()
			);
			case "translucent-sort-reversed" -> RustGalTerrainRenderer.recordTranslucentFaultMarker(
				"sort-reversed",
				target,
				STATIC_TERRAIN_SCENARIO + ":block=" + target.toShortString()
			);
			case "translucent-sort-stale" -> RustGalTerrainRenderer.recordTranslucentFaultMarker(
				"sort-stale",
				target,
				STATIC_TERRAIN_SCENARIO + ":block=" + target.toShortString()
			);
			case "translucent-sort-missing" -> RustGalTerrainRenderer.recordTranslucentFaultMarker(
				"sort-missing",
				target,
				STATIC_TERRAIN_SCENARIO + ":block=" + target.toShortString()
			);
			case "translucent-duplicate-primitive" -> RustGalTerrainRenderer.recordTranslucentFaultMarker(
				"duplicate-primitive",
				target,
				STATIC_TERRAIN_SCENARIO + ":block=" + target.toShortString()
			);
			case "translucent-index-out-of-range" -> RustGalTerrainRenderer.recordTranslucentFaultMarker(
				"index-out-of-range",
				target,
				STATIC_TERRAIN_SCENARIO + ":block=" + target.toShortString()
			);
			case "translucent-index-type-invalid" -> RustGalTerrainRenderer.recordTranslucentFaultMarker(
				"index-type-invalid",
				target,
				STATIC_TERRAIN_SCENARIO + ":block=" + target.toShortString()
			);
			case "translucent-index-alignment-invalid" -> RustGalTerrainRenderer.recordTranslucentFaultMarker(
				"index-alignment-invalid",
				target,
				STATIC_TERRAIN_SCENARIO + ":block=" + target.toShortString()
			);
			case "translucent-sort-section-mismatch" -> RustGalTerrainRenderer.recordTranslucentFaultMarker(
				"sort-section-mismatch",
				target,
				STATIC_TERRAIN_SCENARIO + ":block=" + target.toShortString()
			);
			case "translucent-old-new-overlap" -> RustGalTerrainRenderer.recordTranslucentFaultMarker(
				"old-new-overlap",
				target,
				STATIC_TERRAIN_SCENARIO + ":block=" + target.toShortString()
			);
			case "translucent-section-order-reversed" -> RustGalTerrainRenderer.recordTranslucentFaultMarker(
				"section-order-reversed",
				target,
				STATIC_TERRAIN_SCENARIO + ":block=" + target.toShortString()
			);
			case "translucent-depth-write-enabled" -> RustGalTerrainRenderer.recordTranslucentFaultMarker(
				"depth-write-enabled",
				target,
				STATIC_TERRAIN_SCENARIO + ":block=" + target.toShortString()
			);
			case "translucent-opaque-blend" -> RustGalTerrainRenderer.recordTranslucentFaultMarker(
				"opaque-blend",
				target,
				STATIC_TERRAIN_SCENARIO + ":block=" + target.toShortString()
			);
			case "translucent-cross-world-sort" -> RustGalTerrainRenderer.recordTranslucentFaultMarker(
				"cross-world-sort",
				target,
				STATIC_TERRAIN_SCENARIO + ":block=" + target.toShortString()
			);
			case "translucent-source-sort-mismatch" -> RustGalTerrainRenderer.recordTranslucentFaultMarker(
				"source-sort-mismatch",
				target,
				STATIC_TERRAIN_SCENARIO + ":block=" + target.toShortString()
			);
			case "translucent-sort-payload-corrupt" -> RustGalTerrainRenderer.recordTranslucentFaultMarker(
				"sort-payload-corrupt",
				target,
				STATIC_TERRAIN_SCENARIO + ":block=" + target.toShortString()
			);
			case "translucent-primitive-metadata-missing" -> RustGalTerrainRenderer.recordTranslucentFaultMarker(
				"primitive-metadata-missing",
				target,
				STATIC_TERRAIN_SCENARIO + ":block=" + target.toShortString()
			);
			case "translucent-primitive-kind-unknown" -> RustGalTerrainRenderer.recordTranslucentFaultMarker(
				"primitive-kind-unknown",
				target,
				STATIC_TERRAIN_SCENARIO + ":block=" + target.toShortString()
			);
			case "translucent-primitive-range-overlap" -> RustGalTerrainRenderer.recordTranslucentFaultMarker(
				"primitive-range-overlap",
				target,
				STATIC_TERRAIN_SCENARIO + ":block=" + target.toShortString()
			);
			case "translucent-primitive-range-out-of-bounds" -> RustGalTerrainRenderer.recordTranslucentFaultMarker(
				"primitive-range-out-of-bounds",
				target,
				STATIC_TERRAIN_SCENARIO + ":block=" + target.toShortString()
			);
			case "translucent-primitive-classification-swapped" -> RustGalTerrainRenderer.recordTranslucentFaultMarker(
				"primitive-classification-swapped",
				target,
				STATIC_TERRAIN_SCENARIO + ":block=" + target.toShortString()
			);
			case "translucent-supported-primitive-omitted" -> RustGalTerrainRenderer.recordTranslucentFaultMarker(
				"supported-primitive-omitted",
				target,
				STATIC_TERRAIN_SCENARIO + ":block=" + target.toShortString()
			);
			case "translucent-unsupported-primitive-executed" -> RustGalTerrainRenderer.recordTranslucentFaultMarker(
				"unsupported-primitive-executed",
				target,
				STATIC_TERRAIN_SCENARIO + ":block=" + target.toShortString()
			);
			case "translucent-source-index-unknown-primitive" -> RustGalTerrainRenderer.recordTranslucentFaultMarker(
				"source-index-unknown-primitive",
				target,
				STATIC_TERRAIN_SCENARIO + ":block=" + target.toShortString()
			);
			case "translucent-retained-index-duplicated" -> RustGalTerrainRenderer.recordTranslucentFaultMarker(
				"retained-index-duplicated",
				target,
				STATIC_TERRAIN_SCENARIO + ":block=" + target.toShortString()
			);
			case "translucent-filtered-order-changed" -> RustGalTerrainRenderer.recordTranslucentFaultMarker(
				"filtered-order-changed",
				target,
				STATIC_TERRAIN_SCENARIO + ":block=" + target.toShortString()
			);
			case "translucent-ordered-range-material-mismatch" -> RustGalTerrainRenderer.recordTranslucentFaultMarker(
				"ordered-range-material-mismatch",
				target,
				STATIC_TERRAIN_SCENARIO + ":block=" + target.toShortString()
			);
			case "translucent-ordered-range-overlap" -> RustGalTerrainRenderer.recordTranslucentFaultMarker(
				"ordered-range-overlap",
				target,
				STATIC_TERRAIN_SCENARIO + ":block=" + target.toShortString()
			);
			case "translucent-execution-order-hash-mismatch" -> RustGalTerrainRenderer.recordTranslucentFaultMarker(
				"execution-order-hash-mismatch",
				target,
				STATIC_TERRAIN_SCENARIO + ":block=" + target.toShortString()
			);
			case "translucent-cross-generation-metadata" -> RustGalTerrainRenderer.recordTranslucentFaultMarker(
				"cross-generation-metadata",
				target,
				STATIC_TERRAIN_SCENARIO + ":block=" + target.toShortString()
			);
			case "translucent-cross-world-primitive-range-reuse" -> RustGalTerrainRenderer.recordTranslucentFaultMarker(
				"cross-world-primitive-range-reuse",
				target,
				STATIC_TERRAIN_SCENARIO + ":block=" + target.toShortString()
			);
			case "water-still-flow-texture-swapped",
				"water-overlay-identity-invalid",
				"water-animation-entry-mismatch",
				"water-animation-invalid-frame",
				"water-animation-duplicate-frame",
				"water-animation-missing-frame",
				"water-animation-zero-duration",
				"water-animation-duration-overflow",
				"water-animation-region-out-of-bounds",
				"water-animation-missing-pixels",
				"water-animation-incompatible-dimensions",
				"water-animation-generation-mismatch",
				"water-animation-stale-generation",
				"water-animation-region-mismatch",
				"water-animation-invalid-interp",
				"water-animation-section-divergence",
				"water-animation-cross-world-state",
				"water-animation-per-frame-recreation",
				"water-corner-height-invalid",
				"water-flow-reversed",
				"water-uv-invalid",
				"water-tint-invalid",
				"water-light-swapped",
				"water-normal-invalid",
				"water-duplicate-face",
				"water-missing-face",
				"water-boundary-crack",
				"water-depth-write-enabled",
				"water-opaque-blend",
				"water-stale-mesh-generation",
				"water-glass-range-mismatch" -> RustGalTerrainRenderer.recordTranslucentFaultMarker(
				STATIC_TERRAIN_FAULT,
				target,
				STATIC_TERRAIN_SCENARIO + ":block=" + target.toShortString()
			);
			default -> {
			}
		}
	}

	public static void forceBlockOutlineTargetForDiagnostics(Minecraft minecraft) {
		if (!ENABLED || !FORCE_BLOCK_OUTLINE_TARGET || !initialized || forcedBlockOutlineTarget == null) {
			return;
		}
		minecraft.hitResult = new BlockHitResult(
			forcedBlockOutlineTarget.hitLocation(),
			forcedBlockOutlineTarget.face(),
			forcedBlockOutlineTarget.blockPos(),
			false
		);
	}

	public static void applyBossBarOverridesForDiagnostics(BossHealthOverlay overlay) {
		if (!ENABLED || !hasBossBarOverride()) {
			return;
		}
		int count = forcedBossBarCount();
		overlay.replaceEventsForDeterministicCapture(count, forcedBossBarProgresses(count), forcedBossBarOverlays(count));
	}

	public static boolean isReadyForShaderInputParityPoseDiagnostics() {
		return !ENABLED || !initialized || SETTLED_READY_FRAMES <= 0 || settledReadyGateSatisfied || poseIndex > 0;
	}

	public static boolean isActiveForDiagnostics() {
		return ENABLED && initialized && !complete && !failed;
	}

	public static String currentPoseNameForDiagnostics() {
		if (!ENABLED || !initialized || poses == null) {
			return "none";
		}
		if (poseIndex >= 0 && poseIndex < poses.length) {
			return poses[poseIndex].name();
		}
		return complete ? "complete" : "none";
	}

	public static String shaderInputParityContextFields() {
		if (!ENABLED) {
			return "detCapture=false detPose=none detPoseIndex=0 detRenderedFrame=0 detAwaitingScreenshot=false detComplete=false detFailed=false";
		}

		String poseName = "none";
		int displayPoseIndex = 0;
		if (initialized && poses != null) {
			if (poseIndex >= 0 && poseIndex < poses.length) {
				poseName = poses[poseIndex].name();
				displayPoseIndex = poseIndex + 1;
			} else if (complete) {
				poseName = "complete";
				displayPoseIndex = poses.length;
			}
		}

		return "detCapture=true"
			+ " detPose=" + poseName
			+ " detPoseIndex=" + displayPoseIndex
			+ " detRenderedFrame=" + renderedFrameIndex
			+ " detAwaitingScreenshot=" + awaitingScreenshotAck
				+ " detComplete=" + complete
				+ " detFailed=" + failed;
	}

	public static void recordSubmittedWorkIdentity(String family, String identity) {
		if (!ENABLED || !initialized || complete || failed || SETTLED_READY_FRAMES <= 0 || family == null || identity == null) {
			return;
		}
		String normalizedFamily = family.trim();
		if (!SETTLED_READY_FAMILIES.contains(normalizedFamily)) {
			return;
		}
		String normalizedIdentity = identity.trim();
		if (normalizedIdentity.isEmpty()) {
			return;
		}
		synchronized (SUBMITTED_WORK_BY_FRAME) {
			SUBMITTED_WORK_BY_FRAME
				.computeIfAbsent(renderedFrameIndex, ignored -> new LinkedHashMap<>())
				.computeIfAbsent(normalizedFamily, ignored -> new LinkedHashSet<>())
				.add(normalizedIdentity);
			pruneSubmittedWorkFrames();
		}
	}

	public static int deterministicTemporalFrameIndex() {
		if (!ENABLED || !initialized || poses == null || poses.length == 0) {
			return 0;
		}
		int clampedPoseIndex = Math.max(0, Math.min(poseIndex, poses.length - 1));
		int clampedFrameAtPose = Math.max(0, Math.min(renderedFramesAtPose, FRAMES_PER_POSE));
		return clampedPoseIndex * FRAMES_PER_POSE + clampedFrameAtPose;
	}

	private static boolean ensureInitialized(Minecraft minecraft) {
		if (initialized) {
			return true;
		}

		ClientLevel level = minecraft.level;
		LocalPlayer player = minecraft.player;
		if (level == null || player == null || minecraft.getConnection() == null) {
			return false;
		}
		if (minecraft.screen != null || minecraft.getOverlay() != null) {
			return false;
		}

		try {
			Files.createDirectories(SCREENSHOT_DIR);
			Path parent = METADATA_PATH.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
		} catch (IOException exception) {
			fail("failed to create deterministic capture directories: " + exception.getMessage());
			return false;
		}

		initialPosition = player.position();
		initialDimension = level.dimension().location().toString();
		originalCameraType = minecraft.options.getCameraType();
			originalGameMode = minecraft.gameMode == null ? null : minecraft.gameMode.getPlayerMode();
			originalPreviousGameMode = minecraft.gameMode == null ? null : minecraft.gameMode.getPreviousPlayerMode();
			originalAttackIndicator = minecraft.options.attackIndicator().get();
					originalChatVisibility = minecraft.options.chatVisibility().get();
					originalHighContrastBlockOutline = minecraft.options.highContrastBlockOutline().get();
					originalNoGravity = player.isNoGravity();
					originalSelectedHotbarSlot = player.getInventory().getSelectedSlot();
			originalExperienceProgress = player.experienceProgress;
			originalExperienceLevel = player.experienceLevel;
					originalExperienceDisplayStartTick = player.experienceDisplayStartTick;
					originalAttackStrengthTicker = player.getAttackStrengthTickerForDeterministicCapture();
					originalArmorValueOverride = player.getArmorValueForDeterministicCapture();
					originalHealthOverride = player.getHealthForDeterministicCapture();
					originalMaxHealthOverride = player.getMaxHealthForDeterministicCapture();
					originalTicksFrozen = player.getTicksFrozen();
					originalPoisonEffect = copyEffect(player.getEffect(MobEffects.POISON));
					originalWitherEffect = copyEffect(player.getEffect(MobEffects.WITHER));
						originalRegenerationEffect = copyEffect(player.getEffect(MobEffects.REGENERATION));
						originalCrosshairPickEntity = minecraft.crosshairPickEntity;
						initialHealth = player.getHealth();
					if (FORCE_BLOCK_OUTLINE_TARGET || AIM_BLOCK_OUTLINE_TARGET) {
					forcedBlockOutlineTarget = findForcedBlockOutlineTarget(level, player);
					if (forcedBlockOutlineTarget == null) {
						return false;
					}
					initialPosition = forcedBlockOutlineTarget.playerPosition();
				}
					applyRuntimeOverrides(minecraft, player);
					setupDeterministicSupportPlatform(minecraft, player);
						setupRealSurvivalCrackBlock(minecraft, player);
						setupBlockDisplayScenario(minecraft, player);
		initialPose = new Pose("initial", player.getYRot(), player.getXRot());
		if (forcedBlockOutlineTarget != null) {
			initialPose = forcedBlockOutlineTarget.pose();
			applyPose(player, initialPose);
		}
		stabilizeGuiState(minecraft);
		Pose[] fullSequence = new Pose[] {
			initialPose,
			new Pose("right", initialPose.yaw() + YAW_DELTA, initialPose.pitch()),
			new Pose("left", initialPose.yaw() - YAW_DELTA, initialPose.pitch()),
			new Pose("return", initialPose.yaw(), initialPose.pitch())
		};
			if (BLOCK_OUTLINE_PAUSE_PARITY) {
				fullSequence = new Pose[] {
					new Pose("playing", initialPose.yaw(), initialPose.pitch()),
					new Pose("paused", initialPose.yaw(), initialPose.pitch()),
					new Pose("unpaused", initialPose.yaw(), initialPose.pitch())
				};
			} else if (FORCE_REAL_SURVIVAL_CRACK) {
				fullSequence = new Pose[] {
					new Pose("crack-early", initialPose.yaw(), initialPose.pitch()),
					new Pose("crack-middle", initialPose.yaw(), initialPose.pitch()),
					new Pose("crack-late", initialPose.yaw(), initialPose.pitch())
				};
				} else if (staticTerrainRequiresTranslucentCameraSequence()) {
					fullSequence = translucentTerrainPoseSequence(initialPose);
			} else if (!FALLING_BLOCK_SCENARIO.isEmpty() && !"hidden".equals(FALLING_BLOCK_SCENARIO)
				&& !PISTON_SCENARIO.isEmpty() && !"hidden".equals(PISTON_SCENARIO)) {
				fullSequence = combinedMovingMeshPoseSequence(initialPose);
			} else if (!PISTON_SCENARIO.isEmpty() && !"hidden".equals(PISTON_SCENARIO)) {
				fullSequence = movingMeshPoseSequence("piston", initialPose, 7);
			} else if (!FALLING_BLOCK_SCENARIO.isEmpty() && !"hidden".equals(FALLING_BLOCK_SCENARIO)) {
				fullSequence = movingMeshPoseSequence("falling", initialPose, 5);
			}
			poses = java.util.Arrays.copyOf(fullSequence, Math.min(POSE_COUNT, fullSequence.length));
		startedGameTime = level.getGameTime();
		windowWidth = minecraft.getWindow().getWidth();
		windowHeight = minecraft.getWindow().getHeight();
		initialized = true;
		LOGGER.info(
			"Deterministic camera capture started dimension={} pos=({}, {}, {}) yaw={} pitch={} framesPerPose={} yawDelta={} cameraType={} selectedHotbarSlot={} screenshots={}",
			initialDimension,
			initialPosition.x,
			initialPosition.y,
			initialPosition.z,
			initialPose.yaw(),
			initialPose.pitch(),
			FRAMES_PER_POSE,
			YAW_DELTA,
			currentCameraType(minecraft),
			currentSelectedHotbarSlot(player),
			SCREENSHOT_DIR
		);
		if (forcedBlockOutlineTarget != null) {
			LOGGER.info(
				"Deterministic block-outline real target pos={} face={} playerPos=({}, {}, {}) yaw={} pitch={} hit=({}, {}, {}) forceHitResult={} aimOnly={}",
				forcedBlockOutlineTarget.blockPos().toShortString(),
				forcedBlockOutlineTarget.face(),
				forcedBlockOutlineTarget.playerPosition().x,
				forcedBlockOutlineTarget.playerPosition().y,
				forcedBlockOutlineTarget.playerPosition().z,
				forcedBlockOutlineTarget.pose().yaw(),
				forcedBlockOutlineTarget.pose().pitch(),
				forcedBlockOutlineTarget.hitLocation().x,
				forcedBlockOutlineTarget.hitLocation().y,
				forcedBlockOutlineTarget.hitLocation().z,
				FORCE_BLOCK_OUTLINE_TARGET,
				AIM_BLOCK_OUTLINE_TARGET
			);
		}
		writeMetadata(minecraft, "running");
		return true;
	}

	private static boolean settledReadyGateSatisfied(Minecraft minecraft) {
		if (SETTLED_READY_FRAMES <= 0) {
			settledReadyGateSatisfied = true;
			return true;
		}
		if (poseIndex > 0) {
			settledReadyGateSatisfied = true;
			return true;
		}

		long latestCompletedFrame = Math.max(0L, renderedFrameIndex - 1L);
		synchronized (SUBMITTED_WORK_BY_FRAME) {
			for (String family : SETTLED_READY_FAMILIES) {
				Set<String> stableIntersection = null;
				for (int offset = SETTLED_READY_FRAMES - 1; offset >= 0; offset--) {
					Map<String, Set<String>> frameWork = SUBMITTED_WORK_BY_FRAME.get(latestCompletedFrame - offset);
					Set<String> current = frameWork == null ? null : frameWork.get(family);
					if (current == null || current.isEmpty()) {
						return false;
					}
					if (stableIntersection == null) {
						stableIntersection = new LinkedHashSet<>(current);
					} else {
						stableIntersection.retainAll(current);
					}
				}
				if (stableIntersection == null || stableIntersection.isEmpty()) {
					return false;
				}
			}
		}
		if (!staticTerrainAssetsSettled()) {
			return false;
		}

		settledReadyGateSatisfied = true;
		writeMetadata(minecraft, "settled_ready_work");
		LOGGER.info(
			"Deterministic camera capture settled submitted work after {} frames: {}",
			framesWaitingForSettledReady,
			settledReadySummary()
		);
		return true;
	}

	private static void pruneSubmittedWorkFrames() {
		long minimumFrame = Math.max(0L, renderedFrameIndex - Math.max(SETTLED_READY_FRAMES * 4L, 64L));
		SUBMITTED_WORK_BY_FRAME.keySet().removeIf(frame -> frame < minimumFrame);
	}

	private static String settledReadySummary() {
		if (SETTLED_READY_FRAMES <= 0) {
			return "disabled";
		}
		long latestCompletedFrame = Math.max(0L, renderedFrameIndex - 1L);
		StringBuilder summary = new StringBuilder();
		synchronized (SUBMITTED_WORK_BY_FRAME) {
			summary.append("frames=").append(Math.max(0L, latestCompletedFrame - SETTLED_READY_FRAMES + 1L))
				.append("..").append(latestCompletedFrame);
			for (String family : SETTLED_READY_FAMILIES) {
				Map<String, Set<String>> frameWork = SUBMITTED_WORK_BY_FRAME.get(latestCompletedFrame);
				Set<String> work = frameWork == null ? null : frameWork.get(family);
				Set<String> stableIntersection = null;
				for (int offset = SETTLED_READY_FRAMES - 1; offset >= 0; offset--) {
					Map<String, Set<String>> windowFrameWork = SUBMITTED_WORK_BY_FRAME.get(latestCompletedFrame - offset);
					Set<String> current = windowFrameWork == null ? null : windowFrameWork.get(family);
					if (current == null || current.isEmpty()) {
						stableIntersection = null;
						break;
					}
					if (stableIntersection == null) {
						stableIntersection = new LinkedHashSet<>(current);
					} else {
						stableIntersection.retainAll(current);
					}
				}
				summary.append(";").append(family).append("=").append(work == null ? 0 : work.size())
					.append("/stable=").append(stableIntersection == null ? 0 : stableIntersection.size());
			}
		}
		if (staticTerrainRequiresSettledAssets()) {
			summary.append(";static-terrain-assets-quiet=")
				.append(staticTerrainSettledFrames)
				.append("/")
				.append(SETTLED_READY_FRAMES)
				.append(" signature=")
				.append(staticTerrainSettledSignature);
		}
		return summary.toString();
	}

	private static boolean staticTerrainAssetsSettled() {
		if (!staticTerrainRequiresSettledAssets()) {
			return true;
		}
		RustGalTerrainRenderer.TerrainDiagnostics diagnostics = RustGalTerrainRenderer.diagnosticsSnapshot();
		if (diagnostics.registeredMeshes() <= 0 || diagnostics.visibleLayerSubmissions() <= 0) {
			staticTerrainSettledFrames = 0;
			staticTerrainSettledSignature = "";
			return false;
		}
		String signature = diagnostics.acceptedBuildOutputs()
			+ "/" + diagnostics.registeredMeshes()
			+ "/" + diagnostics.texturePayloadUpdates()
			+ "/" + diagnostics.removedLayers()
			+ "/" + diagnostics.skippedUnsupportedAnimatedSections()
			+ "/" + diagnostics.skippedEmptyLayers()
			+ "/" + diagnostics.invalidations()
			+ "/" + diagnostics.cachedLayerAssets()
			+ "/" + diagnostics.atlasGeneration()
			+ "/" + diagnostics.registeredAtlasGeneration();
		if (!signature.equals(staticTerrainSettledSignature)) {
			staticTerrainSettledSignature = signature;
			staticTerrainSettledFrames = 1;
			return false;
		}
		staticTerrainSettledFrames++;
		return staticTerrainSettledFrames >= SETTLED_READY_FRAMES;
	}

	private static boolean staticTerrainRequiresSettledAssets() {
		return "real-world".equals(STATIC_TERRAIN_SCENARIO) || staticTerrainLifecycleScenario();
	}

	private static Set<String> parseSettledReadyFamilies() {
		String raw = System.getProperty("mattmc.dev.deterministicCameraCapture.settledReadyFamilies", "sodium-terrain,distant-horizons");
		LinkedHashSet<String> families = new LinkedHashSet<>();
		Arrays.stream(raw.split(","))
			.map(String::trim)
			.filter(value -> !value.isEmpty())
			.forEach(families::add);
		if (families.isEmpty()) {
			families.add("sodium-terrain");
		}
		return Set.copyOf(families);
	}

	private static void stabilizeGuiState(Minecraft minecraft) {
		minecraft.gui.vignetteBrightness = FIXED_VIGNETTE_BRIGHTNESS;
	}

	private static void applyPauseParityScreen(Minecraft minecraft) {
		if (!BLOCK_OUTLINE_PAUSE_PARITY || poses == null || poseIndex < 0 || poseIndex >= poses.length) {
			return;
		}
		boolean wantsPauseScreen = "paused".equals(poses[poseIndex].name());
		if (wantsPauseScreen) {
			if (minecraft.screen == null) {
				minecraft.setScreen(new PauseScreen(false));
			}
			return;
		}
		if (minecraft.screen instanceof PauseScreen) {
			minecraft.setScreen(null);
		}
	}

	private static boolean advanceRustGalGuiScreenCycle(Minecraft minecraft) {
		if (rustGalGuiScreenCycleComplete) {
			return true;
		}
		if (minecraft.player == null) {
			return false;
		}
		rustGalGuiScreenCycleFramesInStage++;
		switch (rustGalGuiScreenCycleStage) {
			case 0 -> {
				if (minecraft.screen != null) {
					fail("Rust GAL GUI screen-cycle expected gameplay HUD before inventory but screen was " + minecraft.screen.getClass().getSimpleName());
					return false;
				}
				if (rustGalGuiScreenCycleFramesInStage == 1) {
					writeMetadata(minecraft, "rust_gal_gui_screen_cycle_hud_before");
				}
				if (rustGalGuiScreenCycleFramesInStage >= RUST_GAL_GUI_SCREEN_CYCLE_HOLD_FRAMES) {
					minecraft.setScreen(new InventoryScreen(minecraft.player));
					rustGalGuiScreenCycleStage = 1;
					rustGalGuiScreenCycleFramesInStage = 0;
					writeMetadata(minecraft, "rust_gal_gui_screen_cycle_inventory_open");
				}
				return false;
			}
			case 1 -> {
				if (!(minecraft.screen instanceof InventoryScreen)) {
					fail("Rust GAL GUI screen-cycle expected InventoryScreen but screen was "
						+ (minecraft.screen == null ? "none" : minecraft.screen.getClass().getSimpleName()));
					return false;
				}
				if (rustGalGuiScreenCycleFramesInStage >= RUST_GAL_GUI_SCREEN_CYCLE_HOLD_FRAMES) {
					minecraft.setScreen(null);
					rustGalGuiScreenCycleStage = 2;
					rustGalGuiScreenCycleFramesInStage = 0;
					writeMetadata(minecraft, "rust_gal_gui_screen_cycle_inventory_closed");
				}
				return false;
			}
			case 2 -> {
				if (minecraft.screen != null) {
					fail("Rust GAL GUI screen-cycle expected HUD recovery after inventory but screen was " + minecraft.screen.getClass().getSimpleName());
					return false;
				}
				if (rustGalGuiScreenCycleFramesInStage >= RUST_GAL_GUI_SCREEN_CYCLE_HOLD_FRAMES) {
					rustGalGuiScreenCyclesCompleted++;
					if (rustGalGuiScreenCyclesCompleted >= RUST_GAL_GUI_SCREEN_CYCLE_REPEATS) {
						rustGalGuiScreenCycleComplete = true;
						writeMetadata(minecraft, "rust_gal_gui_screen_cycle_complete");
						return true;
					}
					rustGalGuiScreenCycleStage = 0;
					rustGalGuiScreenCycleFramesInStage = 0;
				}
				return false;
			}
			default -> {
				fail("invalid Rust GAL GUI screen-cycle stage: " + rustGalGuiScreenCycleStage);
				return false;
			}
		}
	}

	private static void requestCurrentPoseScreenshot(Minecraft minecraft) {
		Pose pose = poses[poseIndex];
		int captureIndex = poseIndex + 1;
		String fileName = String.format(Locale.ROOT, "%02d_%s.png", captureIndex, pose.name());
		String requestName = String.format(Locale.ROOT, "capture_request_%02d_%s.json", captureIndex, pose.name());
		String ackName = String.format(Locale.ROOT, "capture_request_%02d_%s.ack.json", captureIndex, pose.name());
		currentScreenshotPath = SCREENSHOT_DIR.resolve(fileName);
		currentAckPath = SCREENSHOT_DIR.resolve(ackName);
		Path requestPath = SCREENSHOT_DIR.resolve(requestName);

		StringBuilder json = new StringBuilder(1024);
		json.append("{\n");
		json.append("  \"index\": ").append(captureIndex).append(",\n");
		appendField(json, "poseName", pose.name()).append(",\n");
		appendField(json, "screenshot", currentScreenshotPath.toAbsolutePath().toString()).append(",\n");
		appendField(json, "ack", currentAckPath.toAbsolutePath().toString()).append(",\n");
		appendField(json, "dimension", minecraft.level == null ? "missing" : minecraft.level.dimension().location().toString()).append(",\n");
		appendVec3(json, "position", minecraft.player == null ? Vec3.ZERO : minecraft.player.position()).append(",\n");
		appendVec3(json, "requestedPosition", targetPositionForPose(pose)).append(",\n");
		json.append("  \"requestedYaw\": ").append(format(pose.yaw())).append(",\n");
		json.append("  \"requestedPitch\": ").append(format(pose.pitch())).append(",\n");
		json.append("  \"observedYaw\": ").append(format(minecraft.player == null ? 0.0F : minecraft.player.getYRot())).append(",\n");
		json.append("  \"observedPitch\": ").append(format(minecraft.player == null ? 0.0F : minecraft.player.getXRot())).append(",\n");
		json.append("  \"renderedFrameIndex\": ").append(renderedFrameIndex).append(",\n");
		json.append("  \"gameTime\": ").append(minecraft.level == null ? -1L : minecraft.level.getGameTime()).append("\n");
		json.append("}\n");

		try {
			Files.writeString(requestPath, json.toString(), StandardCharsets.UTF_8);
		} catch (IOException exception) {
			fail("failed to write deterministic screenshot request " + requestPath + ": " + exception.getMessage());
			return;
		}

		awaitingScreenshotAck = true;
		framesAwaitingAck = 0;
		writeMetadata(minecraft, "waiting_for_screenshot");
		LOGGER.info(
			"Deterministic camera capture requested screenshot index={} pose={} path={} ack={} yaw={} pitch={}",
			captureIndex,
			pose.name(),
			currentScreenshotPath,
			currentAckPath,
			pose.yaw(),
			pose.pitch()
		);
	}

	private static void captureCurrentPoseInternally(Minecraft minecraft) {
		Pose pose = poses[poseIndex];
		int captureIndex = poseIndex + 1;
		String fileName = String.format(Locale.ROOT, "%02d_%s.png", captureIndex, pose.name());
		currentScreenshotPath = SCREENSHOT_DIR.resolve(fileName);
		currentAckPath = SCREENSHOT_DIR.resolve(String.format(Locale.ROOT, "capture_request_%02d_%s.ack.json", captureIndex, pose.name()));
		awaitingScreenshotAck = true;
		framesAwaitingAck = 0;
		writeMetadata(minecraft, "capturing_screenshot");
		try {
			Files.createDirectories(SCREENSHOT_DIR);
		} catch (IOException exception) {
			fail("failed to create deterministic screenshot directory " + SCREENSHOT_DIR + ": " + exception.getMessage());
			return;
		}

		try {
			Screenshot.takeScreenshot(minecraft.getMainRenderTarget(), nativeImage -> {
				try (nativeImage) {
					nativeImage.writeToFile(currentScreenshotPath);
					writeInternalScreenshotAck(minecraft, pose, captureIndex);
					checkScreenshotAck(minecraft);
				} catch (IOException exception) {
					fail("failed to write deterministic internal screenshot " + currentScreenshotPath + ": " + exception.getMessage());
				}
			});
		} catch (RuntimeException exception) {
			fail("failed to capture deterministic internal screenshot " + currentScreenshotPath + ": " + exception.getMessage());
		}
	}

	private static void writeInternalScreenshotAck(Minecraft minecraft, Pose pose, int captureIndex) throws IOException {
		if (currentAckPath == null || currentScreenshotPath == null) {
			return;
		}
		StringBuilder json = new StringBuilder(1024);
		json.append("{\n");
		json.append("  \"index\": ").append(captureIndex).append(",\n");
		appendField(json, "poseName", pose.name()).append(",\n");
		appendField(json, "screenshot", currentScreenshotPath.toAbsolutePath().toString()).append(",\n");
		appendField(json, "ack", currentAckPath.toAbsolutePath().toString()).append(",\n");
		appendField(json, "dimension", minecraft.level == null ? "missing" : minecraft.level.dimension().location().toString()).append(",\n");
		appendVec3(json, "position", minecraft.player == null ? Vec3.ZERO : minecraft.player.position()).append(",\n");
		appendVec3(json, "requestedPosition", targetPositionForPose(pose)).append(",\n");
		json.append("  \"requestedYaw\": ").append(format(pose.yaw())).append(",\n");
		json.append("  \"requestedPitch\": ").append(format(pose.pitch())).append(",\n");
		json.append("  \"observedYaw\": ").append(format(minecraft.player == null ? 0.0F : minecraft.player.getYRot())).append(",\n");
		json.append("  \"observedPitch\": ").append(format(minecraft.player == null ? 0.0F : minecraft.player.getXRot())).append(",\n");
		json.append("  \"renderedFrameIndex\": ").append(renderedFrameIndex).append(",\n");
		json.append("  \"gameTime\": ").append(minecraft.level == null ? -1L : minecraft.level.getGameTime()).append(",\n");
		appendField(json, "status", "captured", 2).append(",\n");
		appendField(json, "captureMethod", "internal-main-render-target", 2).append("\n");
		json.append("}\n");
		Files.writeString(currentAckPath, json.toString(), StandardCharsets.UTF_8);
	}

	private static boolean checkScreenshotAck(Minecraft minecraft) {
		if (currentAckPath == null || !Files.isRegularFile(currentAckPath)) {
			return false;
		}
		if (currentScreenshotPath == null || !Files.isRegularFile(currentScreenshotPath)) {
			fail("deterministic screenshot ack exists but screenshot is missing: " + currentScreenshotPath);
			return false;
		}

		Pose pose = poses[poseIndex];
		int captureIndex = poseIndex + 1;
		LocalPlayer player = minecraft.player;
		String targetWindow = readAckStringField("targetWindow");
		CAPTURES.add(new PoseCapture(
			captureIndex,
			pose.name(),
			currentScreenshotPath.toAbsolutePath().toString(),
			minecraft.level == null ? "missing" : minecraft.level.dimension().location().toString(),
			player == null ? Vec3.ZERO : player.position(),
			targetPositionForPose(pose),
			pose.yaw(),
			pose.pitch(),
			player == null ? 0.0F : player.getYRot(),
			player == null ? 0.0F : player.getXRot(),
			renderedFrameIndex,
			minecraft.level == null ? -1L : minecraft.level.getGameTime(),
			INTERNAL_SCREENSHOTS ? "internal-main-render-target" : "external-window-request",
			targetWindow
		));
		LOGGER.info(
			"Deterministic camera capture acknowledged screenshot index={} pose={} path={} yaw={} pitch={}",
			captureIndex,
			pose.name(),
			currentScreenshotPath,
			pose.yaw(),
			pose.pitch()
		);

		awaitingScreenshotAck = false;
		framesAwaitingAck = 0;
		currentScreenshotPath = null;
		currentAckPath = null;
		poseIndex++;
		if (poseIndex >= poses.length) {
			applyPose(minecraft.player, initialPose);
			finish(minecraft);
		} else {
			writeMetadata(minecraft, "running");
		}
		return true;
	}

	private static void finish(Minecraft minecraft) {
		if (complete || failed) {
			return;
		}
		complete = true;
		writeMetadata(minecraft, "complete");
		restoreRuntimeOverrides(minecraft);
		LOGGER.info("Deterministic camera capture complete metadata={}", METADATA_PATH);
	}

	private static void fail(String reason) {
		if (failed || complete) {
			return;
		}
		failed = true;
		LOGGER.error("Deterministic camera capture failed: {}", reason);
		writeFailureMetadata(reason);
	}

	private static void applyPose(LocalPlayer player, Pose pose) {
		if (player == null) {
			return;
		}
		Vec3 targetPosition = targetPositionForPose(pose);
		if (targetPosition != null) {
			player.setPos(targetPosition);
			player.setOldPosAndRot(targetPosition, pose.yaw(), pose.pitch());
		}
		player.setYRot(pose.yaw());
		player.setXRot(pose.pitch());
		player.yRotO = pose.yaw();
		player.xRotO = pose.pitch();
		player.yHeadRot = pose.yaw();
		player.yHeadRotO = pose.yaw();
		player.yBodyRot = pose.yaw();
		player.yBodyRotO = pose.yaw();
	}

	private static ForcedBlockOutlineTarget findForcedBlockOutlineTarget(ClientLevel level, LocalPlayer player) {
		BlockPos origin = player.blockPosition();
		Direction[] faces = new Direction[] { Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST };
		ForcedBlockOutlineTarget best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int y = -96; y <= 48; y++) {
			for (int radius = 0; radius <= 48; radius++) {
				for (int x = -radius; x <= radius; x++) {
					for (int z = -radius; z <= radius; z++) {
						if (Math.max(Math.abs(x), Math.abs(z)) != radius) {
							continue;
						}
						BlockPos blockPos = origin.offset(x, y, z);
						BlockState blockState = level.getBlockState(blockPos);
						if (blockState.isAir()
							|| blockState.getShape(level, blockPos, CollisionContext.of(player)).isEmpty()
							|| !level.getWorldBorder().isWithinBounds(blockPos)) {
							continue;
						}
						Vec3 target = Vec3.atCenterOf(blockPos);
						for (Direction face : faces) {
							Vec3 eye = target.add(face.getStepX() * 3.0, 0.35, face.getStepZ() * 3.0);
							Vec3 feet = eye.subtract(0.0, player.getEyeHeight(), 0.0);
							Vec3 delta = target.subtract(eye);
							double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
							if (horizontal < 0.001) {
								continue;
							}
							float yaw = (float)(Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0);
							float pitch = (float)(-Math.toDegrees(Math.atan2(delta.y, horizontal)));
							double distance = player.position().distanceToSqr(feet);
							if (distance < bestDistance) {
								bestDistance = distance;
								best = new ForcedBlockOutlineTarget(blockPos, face, target, feet, new Pose("initial", yaw, pitch));
							}
						}
					}
				}
				if (best != null) {
					return best;
				}
			}
		}
		return best;
	}

	private static void applyRuntimeOverrides(Minecraft minecraft, LocalPlayer player) {
		CameraType cameraType = forcedCameraType();
		if (cameraType != null && minecraft.options.getCameraType() != cameraType) {
			minecraft.options.setCameraType(cameraType);
			minecraft.gameRenderer.checkEntityPostEffect(cameraType.isFirstPerson() ? minecraft.getCameraEntity() : null);
		}
		GameType gameType = forcedGameMode();
		if (gameType != null && minecraft.gameMode != null && minecraft.gameMode.getPlayerMode() != gameType) {
			minecraft.gameMode.setLocalMode(gameType, minecraft.gameMode.getPreviousPlayerMode());
		}
		if (player == null) {
			return;
		}
		if (gameType != null && minecraft.getSingleplayerServer() != null) {
			ServerPlayer serverPlayer = minecraft.getSingleplayerServer().getPlayerList().getPlayer(player.getUUID());
			if (serverPlayer != null && serverPlayer.gameMode() != gameType) {
				serverPlayer.setGameMode(gameType);
			}
		}
		AttackIndicatorStatus attackIndicator = forcedAttackIndicator();
		if (attackIndicator != null && minecraft.options.attackIndicator().get() != attackIndicator) {
			minecraft.options.attackIndicator().set(attackIndicator);
		}
		if (FORCED_SELECTED_HOTBAR_SLOT > 0) {
			int selectedSlot = Math.max(1, Math.min(9, FORCED_SELECTED_HOTBAR_SLOT)) - 1;
			if (player.getInventory().getSelectedSlot() != selectedSlot) {
				player.getInventory().setSelectedSlot(selectedSlot);
			}
		}
		if (!Float.isNaN(FORCED_EXPERIENCE_PROGRESS)) {
			float progress = Math.max(0.0F, Math.min(1.0F, FORCED_EXPERIENCE_PROGRESS));
			if (player.experienceProgress != progress) {
				player.experienceProgress = progress;
			}
			int level = FORCED_EXPERIENCE_LEVEL > 0 ? FORCED_EXPERIENCE_LEVEL : Math.max(1, player.experienceLevel);
			if (player.experienceLevel != level) {
				player.experienceLevel = level;
			}
			player.experienceDisplayStartTick = player.tickCount;
		}
			if (!Float.isNaN(FORCED_ATTACK_PROGRESS)) {
				float progress = Math.max(0.0F, Math.min(1.0F, FORCED_ATTACK_PROGRESS));
				player.setAttackStrengthDelayForDeterministicCapture(FORCED_ATTACK_DELAY);
				int ticks = Math.round(progress * player.getCurrentItemAttackStrengthDelay());
				player.setAttackStrengthTickerForDeterministicCapture(ticks);
			}
			if (FORCE_ATTACK_TARGET) {
				minecraft.crosshairPickEntity = player;
			}
			player.setNoGravity(true);
			player.setDeltaMovement(Vec3.ZERO);
			player.fallDistance = 0.0F;
			recordPlayerSafetyState(player);
			if (initialized && player.getHealth() + 0.001F < initialHealth) {
				fail("deterministic player took damage during capture: initialHealth="
					+ format(initialHealth)
					+ " currentHealth="
					+ format(player.getHealth()));
				return;
			}
				if (forcedBlockOutlineTarget != null) {
					player.setPos(initialPosition);
				player.setOldPosAndRot(initialPosition, forcedBlockOutlineTarget.pose().yaw(), forcedBlockOutlineTarget.pose().pitch());
				applyPose(player, forcedBlockOutlineTarget.pose());
				if (FORCE_BLOCK_OUTLINE_HIGH_CONTRAST && !minecraft.options.highContrastBlockOutline().get()) {
					minecraft.options.highContrastBlockOutline().set(true);
				}
			}
			driveRealSurvivalCrack(minecraft, player);
				if (FORCED_ARMOR_VALUE >= 0) {
					player.setArmorValueForDeterministicCapture(Math.min(20, FORCED_ARMOR_VALUE));
				}
			applyHeartVariantOverride(player);
			if (FORCE_PLAYER_HEALTH_REGEN) {
				player.forceAddEffect(new MobEffectInstance(MobEffects.REGENERATION, 20_000, 0, false, false, false), null);
				}
				if (HIDE_CHAT && minecraft.options.chatVisibility().get() != ChatVisiblity.HIDDEN) {
					minecraft.options.chatVisibility().set(ChatVisiblity.HIDDEN);
				}
			}

	private static void restoreRuntimeOverrides(Minecraft minecraft) {
		if (BLOCK_OUTLINE_PAUSE_PARITY && minecraft.screen instanceof PauseScreen) {
			minecraft.setScreen(null);
		}
		if (minecraft.player != null && FORCED_SELECTED_HOTBAR_SLOT > 0 && originalSelectedHotbarSlot >= 0 && originalSelectedHotbarSlot < 9) {
			minecraft.player.getInventory().setSelectedSlot(originalSelectedHotbarSlot);
		}
		if (minecraft.player != null && !Float.isNaN(FORCED_EXPERIENCE_PROGRESS)) {
			minecraft.player.experienceProgress = originalExperienceProgress;
			minecraft.player.experienceLevel = originalExperienceLevel;
			minecraft.player.experienceDisplayStartTick = originalExperienceDisplayStartTick;
		}
			if (minecraft.player != null && !Float.isNaN(FORCED_ATTACK_PROGRESS)) {
				minecraft.player.setAttackStrengthDelayForDeterministicCapture(Float.NaN);
				minecraft.player.setAttackStrengthTickerForDeterministicCapture(originalAttackStrengthTicker);
			}
				if (FORCE_ATTACK_TARGET) {
					minecraft.crosshairPickEntity = originalCrosshairPickEntity;
				}
					if (FORCE_REAL_SURVIVAL_CRACK) {
						if (minecraft.gameMode != null) {
							minecraft.gameMode.stopDestroyBlock();
						}
						realSurvivalCrackBlock = null;
						realSurvivalCrackDirection = null;
						realSurvivalCrackLastStatus = "restored";
							realSurvivalCrackLastDriveGameTime = Long.MIN_VALUE;
						}
						if (minecraft.player != null && minecraft.player.isNoGravity() != originalNoGravity) {
							minecraft.player.setNoGravity(originalNoGravity);
						}
						if (minecraft.player != null && FORCED_ARMOR_VALUE >= 0) {
							minecraft.player.setArmorValueForDeterministicCapture(originalArmorValueOverride);
						}
					if (minecraft.player != null && (!Float.isNaN(FORCED_PLAYER_HEALTH) || !Float.isNaN(FORCED_PLAYER_MAX_HEALTH))) {
						minecraft.player.setHealthForDeterministicCapture(originalHealthOverride);
						minecraft.player.setMaxHealthForDeterministicCapture(originalMaxHealthOverride);
					}
					if (minecraft.player != null && (!FORCED_PLAYER_HEART_VARIANT.isBlank() || FORCE_PLAYER_HEALTH_REGEN)) {
						restoreEffect(minecraft.player, MobEffects.POISON, originalPoisonEffect);
						restoreEffect(minecraft.player, MobEffects.WITHER, originalWitherEffect);
						restoreEffect(minecraft.player, MobEffects.REGENERATION, originalRegenerationEffect);
						minecraft.player.setTicksFrozen(originalTicksFrozen);
					}
				if (!FORCED_ATTACK_INDICATOR.isBlank() && originalAttackIndicator != null) {
					minecraft.options.attackIndicator().set(originalAttackIndicator);
				}
			if (HIDE_CHAT && originalChatVisibility != null) {
				minecraft.options.chatVisibility().set(originalChatVisibility);
			}
			if (FORCE_BLOCK_OUTLINE_HIGH_CONTRAST && minecraft.options.highContrastBlockOutline().get() != originalHighContrastBlockOutline) {
				minecraft.options.highContrastBlockOutline().set(originalHighContrastBlockOutline);
			}
			if (!FORCED_GAME_MODE.isBlank() && minecraft.gameMode != null && originalGameMode != null) {
				minecraft.gameMode.setLocalMode(originalGameMode, originalPreviousGameMode);
			}
		if (!FORCED_CAMERA_TYPE.isBlank() && originalCameraType != null && minecraft.options.getCameraType() != originalCameraType) {
			minecraft.options.setCameraType(originalCameraType);
			minecraft.gameRenderer.checkEntityPostEffect(originalCameraType.isFirstPerson() ? minecraft.getCameraEntity() : null);
		}
	}

		public static void recordRealSurvivalCrackRenderState(net.minecraft.client.renderer.state.BlockBreakingRenderState state) {
			if (!ENABLED || !FORCE_REAL_SURVIVAL_CRACK || state == null || state.blockState == null || state.blockState.isAir()) {
				return;
			}
		realSurvivalCrackRenderedStateCount++;
		realSurvivalCrackMinRenderedStage = Math.min(realSurvivalCrackMinRenderedStage, state.progress);
		realSurvivalCrackMaxRenderedStage = Math.max(realSurvivalCrackMaxRenderedStage, state.progress);
			realSurvivalCrackLastRenderedTarget = state.blockPos == null ? "unknown" : state.blockPos.toShortString();
			realSurvivalCrackLastRenderedBlockType = state.blockState.getBlock().builtInRegistryHolder().key().location().toString();
		}

		private static boolean realSurvivalCrackPoseReady() {
			if (!FORCE_REAL_SURVIVAL_CRACK || poses == null || poseIndex < 0 || poseIndex >= poses.length) {
				return true;
			}
			return switch (poses[poseIndex].name()) {
				case "crack-early" -> realSurvivalCrackMaxRenderedStage >= 0;
				case "crack-middle" -> realSurvivalCrackMaxRenderedStage >= 4;
				case "crack-late" -> realSurvivalCrackMaxRenderedStage >= 7;
			default -> true;
		};
	}

	private static boolean movingMeshProducerReady() {
		long frameIndex = renderedFrameIndex;
		if (!FALLING_BLOCK_SCENARIO.isEmpty() && !"hidden".equals(FALLING_BLOCK_SCENARIO)
			&& !hasCurrentFallingBlockRoute(frameIndex)) {
			return false;
		}
		if (!PISTON_SCENARIO.isEmpty()
			&& !"hidden".equals(PISTON_SCENARIO)
			&& !"completed".equals(PISTON_SCENARIO)
			&& !"removed".equals(PISTON_SCENARIO)) {
			return hasCurrentPistonRoute(frameIndex);
		}
		return true;
	}

	private static boolean hasCurrentFallingBlockRoute(long frameIndex) {
		long frameTolerance = movingMeshFrameTolerance();
		for (RustGalWorldPrimitiveRenderer.FallingBlockRouteDecision decision : RustGalWorldPrimitiveRenderer.fallingBlockRouteDecisions()) {
			if (Math.abs(decision.frameIndex() - frameIndex) > frameTolerance) {
				continue;
			}
			if ("legacy".equals(FALLING_BLOCK_ROUTE_CONTROL)) {
				if ("java-legacy".equals(decision.route()) && decision.javaDrawn()) {
					return true;
				}
			} else if ("disabled".equals(FALLING_BLOCK_ROUTE_CONTROL)) {
				if ("disabled".equals(decision.route()) && !decision.rustSelected() && !decision.javaDrawn()) {
					return true;
				}
			} else if (decision.rustSelected() && decision.rustQueued()) {
				return hasCurrentMovingMeshDiagnostic("falling-block", frameIndex, frameTolerance);
			}
		}
		return false;
	}

	private static boolean hasCurrentPistonRoute(long frameIndex) {
		long frameTolerance = movingMeshFrameTolerance();
		for (RustGalWorldPrimitiveRenderer.MovingBlockRouteDecision decision : RustGalWorldPrimitiveRenderer.movingBlockRouteDecisions()) {
			if ("piston".equals(decision.provenance())
				&& Math.abs(decision.frameIndex() - frameIndex) <= frameTolerance
				&& decision.rustSelected()
				&& decision.rustQueued()) {
				return hasCurrentMovingMeshDiagnostic("piston", frameIndex, frameTolerance);
			}
		}
		return false;
	}

	private static long movingMeshFrameTolerance() {
		return Math.max(16L, FRAMES_PER_POSE + 4L);
	}

	private static boolean hasCurrentMovingMeshDiagnostic(String provenance, long frameIndex, long frameTolerance) {
		for (RustGalWorldPrimitiveRenderer.MovingBlockDiagnostic diagnostic : RustGalWorldPrimitiveRenderer.movingBlockDiagnostics()) {
			if (provenance.equals(diagnostic.provenance())
				&& Math.abs(diagnostic.frameIndex() - frameIndex) <= frameTolerance
				&& diagnostic.projected()
				&& diagnostic.sectionCount() > 0) {
				return true;
			}
		}
		return false;
	}

	private static String movingMeshProducerSummary() {
		long pistonDecisions = RustGalWorldPrimitiveRenderer.movingBlockRouteDecisions()
			.stream()
			.filter(decision -> "piston".equals(decision.provenance()))
			.count();
		long pistonMeshes = RustGalWorldPrimitiveRenderer.movingBlockDiagnostics()
			.stream()
			.filter(diagnostic -> "piston".equals(diagnostic.provenance()))
			.count();
		return "fallingScenario=" + FALLING_BLOCK_SCENARIO
			+ " fallingDecisions=" + RustGalWorldPrimitiveRenderer.fallingBlockRouteDecisions().size()
			+ " fallingMeshes=" + RustGalWorldPrimitiveRenderer.fallingBlockDiagnostics().size()
			+ " fallingEntitySeen=" + fallingEntitySeenCount
			+ " fallingShouldRender=" + fallingEntityShouldRenderCount
			+ " fallingCompiledSection=" + fallingEntityCompiledSectionCount
			+ " fallingExtracted=" + fallingEntityExtractedCount
			+ " pistonScenario=" + PISTON_SCENARIO
			+ " pistonDecisions=" + pistonDecisions
			+ " pistonMeshes=" + pistonMeshes;
	}

	private static void setupRealSurvivalCrackBlock(Minecraft minecraft, LocalPlayer player) {
		if (!FORCE_REAL_SURVIVAL_CRACK || !FORCE_REAL_SURVIVAL_CRACK_SETUP_BLOCK || minecraft.level == null || !"unset".equals(realSurvivalCrackSetupTarget)) {
			return;
		}

		Vec3 eye = player.getEyePosition(1.0F);
		Vec3 look = player.getLookAngle();
		BlockPos target = BlockPos.containing(eye.add(look.scale(1.5)));
		BlockState setupState = Blocks.STONE.defaultBlockState();
		minecraft.level.setBlock(target, setupState, 3);
		if (minecraft.getSingleplayerServer() != null) {
			ServerLevel serverLevel = minecraft.getSingleplayerServer().getLevel(minecraft.level.dimension());
			if (serverLevel != null) {
				serverLevel.setBlock(target, setupState, 3);
			}
		}
		realSurvivalCrackSetupTarget = target.toShortString();
		realSurvivalCrackLastStatus = "setup-block";
		LOGGER.info(
			"Deterministic real survival crack setup placed minecraft:stone at {} from saved view eye=({}, {}, {}) look=({}, {}, {})",
			realSurvivalCrackSetupTarget,
			eye.x,
			eye.y,
			eye.z,
			look.x,
			look.y,
			look.z
		);
	}

	private static void setupBlockDisplayScenario(Minecraft minecraft, LocalPlayer player) {
		if (BLOCK_DISPLAY_SCENARIO.isEmpty() || "hidden".equals(BLOCK_DISPLAY_SCENARIO) || minecraft.level == null) {
			return;
		}
		BlockState state = blockDisplayBenchmarkState();
		Vec3 forward = player.getLookAngle();
		if (forward.lengthSqr() < 0.0001) {
			forward = new Vec3(0.0, 0.0, 1.0);
		}
		Vec3 position = player.position().add(forward.normalize().scale(4.0)).add(-0.5, -0.5, -0.5);
		Display.BlockDisplay display = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, minecraft.level);
		display.setId(Integer.MIN_VALUE + 4096);
		display.setPos(position);
		display.setBlockState(state);
		display.setViewRange(16.0F);
		display.setWidth(2.0F);
		display.setHeight(2.0F);
		minecraft.level.addEntity(display);
		LOGGER.info(
			"Deterministic BlockDisplay setup spawned {} at {} for Rust-GAL mesh capture",
			state.getBlock().builtInRegistryHolder().key().location(),
			position
		);
	}

	private static BlockState blockDisplayBenchmarkState() {
		return switch (BLOCK_DISPLAY_SCENARIO) {
			case "oak-leaves", "cutout", "tinted" -> Blocks.OAK_LEAVES.defaultBlockState();
			case "asymmetric", "furnace" -> Blocks.FURNACE.defaultBlockState();
			case "non-full-cube", "stairs" -> Blocks.OAK_STAIRS.defaultBlockState();
			default -> Blocks.STONE.defaultBlockState();
		};
	}

	private static void setupFallingBlockScenario(Minecraft minecraft, LocalPlayer player) {
		if (FALLING_BLOCK_SCENARIO.isEmpty() || "hidden".equals(FALLING_BLOCK_SCENARIO) || minecraft.level == null || minecraft.getSingleplayerServer() == null) {
			return;
		}
		ServerLevel serverLevel = minecraft.getSingleplayerServer().getLevel(minecraft.level.dimension());
		if (serverLevel == null) {
			return;
		}
		BlockState state = fallingBlockBenchmarkState();
		Vec3 forward = player.getLookAngle();
		if (forward.lengthSqr() < 0.0001) {
			forward = new Vec3(0.0, 0.0, 1.0);
		}
		int fallHeight = Math.max(4, Integer.getInteger("mattmc.dev.rustGalWorldMesh.fallingBlockFallHeight", 6));
		boolean slowCapture = Boolean.getBoolean("mattmc.dev.rustGalWorldMesh.fallingBlockSlowCapture");
		BlockPos origin = BlockPos.containing(player.getEyePosition().add(forward.normalize().scale(4.0)).add(0.0, 2.0, 0.0));
		int entityCount = Math.max(1, Integer.getInteger("mattmc.dev.rustGalWorldMesh.fallingBlockCount", 1));
		fallingBlockSetupStatus = "spawned";
		fallingBlockSetupBlockId = state.getBlock().builtInRegistryHolder().key().location().toString();
		fallingBlockSetupSpawnMethod = "FallingBlockEntity.fall";
		fallingBlockSetupOrigin = origin.toShortString();
		fallingBlockSetupLanding = origin.below(fallHeight).toShortString();
		fallingBlockSetupEntityCount = entityCount;
		fallingBlockSetupFallHeight = fallHeight;
		for (int i = 0; i < entityCount; i++) {
			BlockPos pos = origin.offset(i, 0, 0);
			prepareFallingBlockColumn(serverLevel, minecraft.level, pos, fallHeight);
			serverLevel.setBlock(pos, state, 2);
			minecraft.level.setBlock(pos, state, 2);
			FallingBlockEntity serverEntity = FallingBlockEntity.fall(serverLevel, pos, state);
			if (slowCapture) {
				serverEntity.setNoGravity(true);
				serverEntity.setDeltaMovement(0.0, -0.015, 0.0);
			} else {
				serverEntity.setDeltaMovement(0.0, -0.02, 0.0);
			}
			serverEntity.dropItem = false;
			minecraft.level.setBlock(pos, state.getFluidState().createLegacyBlock(), 3);
		}
		LOGGER.info(
			"Deterministic FallingBlock setup spawned {} count={} near {} for Rust-GAL moving mesh capture",
			state.getBlock().builtInRegistryHolder().key().location(),
			entityCount,
			origin
		);
	}

	private static void prepareFallingBlockColumn(ServerLevel serverLevel, net.minecraft.client.multiplayer.ClientLevel clientLevel, BlockPos top, int fallHeight) {
		for (int y = 0; y < fallHeight; y++) {
			BlockPos clear = top.below(y);
			serverLevel.setBlock(clear, Blocks.AIR.defaultBlockState(), 3);
			clientLevel.setBlock(clear, Blocks.AIR.defaultBlockState(), 3);
		}
		BlockPos landing = top.below(fallHeight);
		serverLevel.setBlock(landing, Blocks.STONE.defaultBlockState(), 3);
		clientLevel.setBlock(landing, Blocks.STONE.defaultBlockState(), 3);
	}

	private static BlockState fallingBlockBenchmarkState() {
		return switch (FALLING_BLOCK_SCENARIO) {
			case "gravel" -> Blocks.GRAVEL.defaultBlockState();
			case "concrete-powder", "concrete_powder" -> Blocks.WHITE_CONCRETE_POWDER.defaultBlockState();
			default -> Blocks.SAND.defaultBlockState();
		};
	}

	private static void setupPistonScenario(Minecraft minecraft, LocalPlayer player) {
		if (PISTON_SCENARIO.isEmpty() || "hidden".equals(PISTON_SCENARIO) || minecraft.level == null || minecraft.getSingleplayerServer() == null) {
			pistonSetupStatus = PISTON_SCENARIO.isEmpty() ? "inactive" : "hidden-or-missing-level";
			return;
		}
		ServerLevel serverLevel = minecraft.getSingleplayerServer().getLevel(minecraft.level.dimension());
		if (serverLevel == null) {
			pistonSetupStatus = "missing-server-level";
			return;
		}
		BlockState movedState = pistonMovedState();
		Direction direction = pistonDirection();
		Vec3 forward = player.getLookAngle();
		if (forward.lengthSqr() < 0.0001) {
			forward = new Vec3(0.0, 0.0, 1.0);
		}
		BlockPos origin = BlockPos.containing(player.getEyePosition().add(forward.normalize().scale(4.0)).add(0.0, -0.25, 0.0));
		int count = Math.max(1, Integer.getInteger("mattmc.dev.rustGalWorldMesh.pistonCount", 1));
		for (int i = 0; i < count; i++) {
			BlockPos pos = origin.offset(i, 0, 0);
			clearPistonScenarioSpace(serverLevel, minecraft.level, pos);
			seedMovingPiston(serverLevel, minecraft.level, pos, movedState, direction, pistonExtending(), pistonSourcePiston());
		}
		pistonSetupPos = origin;
		pistonSetupMovedState = movedState;
		pistonSetupDirection = direction;
		pistonSetupExtending = pistonExtending();
		pistonSetupSourcePiston = pistonSourcePiston();
		pistonSetupStatus = "spawned";
		pistonSetupBlockId = movedState.getBlock().builtInRegistryHolder().key().location().toString();
		pistonSetupOrigin = origin.toShortString();
		pistonSetupBlockState = minecraft.level.getBlockState(origin).toString();
		pistonSetupClientBlockEntityPresent = minecraft.level.getBlockEntity(origin) instanceof net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
		pistonSetupEntityCount = count;
		LOGGER.info(
			"Deterministic piston setup spawned {} count={} scenario={} direction={} near {} for Rust-GAL moving mesh capture",
			movedState.getBlock().builtInRegistryHolder().key().location(),
			count,
			PISTON_SCENARIO,
			direction,
			origin
		);
	}

	private static void maintainPistonScenario(Minecraft minecraft) {
		if (PISTON_SCENARIO.isEmpty()
			|| "hidden".equals(PISTON_SCENARIO)
			|| !movingMeshScenarioSetup
			|| complete
			|| failed
			|| pistonSetupPos == null
			|| pistonSetupMovedState == null
			|| pistonSetupDirection == null
			|| minecraft.level == null
			|| minecraft.getSingleplayerServer() == null) {
			return;
		}
		ServerLevel serverLevel = minecraft.getSingleplayerServer().getLevel(minecraft.level.dimension());
		if (serverLevel == null) {
			pistonSetupStatus = "maintain-missing-server-level";
			return;
		}
		boolean clientPresent = minecraft.level.getBlockEntity(pistonSetupPos) instanceof net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
		boolean serverPresent = serverLevel.getBlockEntity(pistonSetupPos) instanceof net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
		pistonSetupClientBlockEntityPresent = clientPresent;
		pistonSetupBlockState = minecraft.level.getBlockState(pistonSetupPos).toString();
		if (clientPresent && serverPresent && minecraft.level.getBlockState(pistonSetupPos).is(Blocks.MOVING_PISTON)) {
			return;
		}
		clearPistonScenarioSpace(serverLevel, minecraft.level, pistonSetupPos);
		seedMovingPiston(
			serverLevel,
			minecraft.level,
			pistonSetupPos,
			pistonSetupMovedState,
			pistonSetupDirection,
			pistonSetupExtending,
			pistonSetupSourcePiston
		);
		pistonSetupReseedCount++;
		pistonSetupStatus = "reseeded";
		pistonSetupBlockState = minecraft.level.getBlockState(pistonSetupPos).toString();
		pistonSetupClientBlockEntityPresent = minecraft.level.getBlockEntity(pistonSetupPos) instanceof net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
	}

	private static BlockState pistonMovedState() {
		return switch (PISTON_SCENARIO) {
			case "sticky-extending", "sticky-retracting" -> Blocks.PISTON_HEAD.defaultBlockState()
				.setValue(net.minecraft.world.level.block.piston.PistonHeadBlock.FACING, pistonDirection())
				.setValue(net.minecraft.world.level.block.piston.PistonHeadBlock.TYPE, net.minecraft.world.level.block.state.properties.PistonType.STICKY);
			case "retracting-source", "normal-retracting" -> Blocks.PISTON.defaultBlockState()
				.setValue(net.minecraft.world.level.block.piston.PistonBaseBlock.FACING, pistonDirection());
			case "cutout", "leaves" -> Blocks.OAK_LEAVES.defaultBlockState();
			default -> Blocks.STONE.defaultBlockState();
		};
	}

	private static Direction pistonDirection() {
		return switch (System.getProperty("mattmc.dev.rustGalWorldMesh.pistonDirection", "north").trim().toLowerCase(Locale.ROOT)) {
			case "down" -> Direction.DOWN;
			case "up" -> Direction.UP;
			case "south" -> Direction.SOUTH;
			case "west" -> Direction.WEST;
			case "east" -> Direction.EAST;
			default -> Direction.NORTH;
		};
	}

	private static boolean pistonExtending() {
		return !PISTON_SCENARIO.contains("retracting");
	}

	private static boolean pistonSourcePiston() {
		return PISTON_SCENARIO.contains("source");
	}

	private static void seedMovingPiston(ServerLevel serverLevel, ClientLevel clientLevel, BlockPos pos, BlockState movedState, Direction direction, boolean extending, boolean sourcePiston) {
		BlockState movingState = Blocks.MOVING_PISTON.defaultBlockState()
			.setValue(net.minecraft.world.level.block.piston.MovingPistonBlock.FACING, direction)
			.setValue(net.minecraft.world.level.block.piston.MovingPistonBlock.TYPE, pistonTypeFor(movedState));
		serverLevel.setBlock(pos, movingState, 3);
		net.minecraft.world.level.block.piston.PistonMovingBlockEntity serverEntity =
			(net.minecraft.world.level.block.piston.PistonMovingBlockEntity)net.minecraft.world.level.block.piston.MovingPistonBlock.newMovingBlockEntity(pos, movingState, movedState, direction, extending, sourcePiston);
		serverLevel.setBlockEntity(serverEntity);
		serverEntity.setChanged();
		clientLevel.setBlock(pos, movingState, 3);
		net.minecraft.world.level.block.piston.PistonMovingBlockEntity clientEntity =
			(net.minecraft.world.level.block.piston.PistonMovingBlockEntity)net.minecraft.world.level.block.piston.MovingPistonBlock.newMovingBlockEntity(pos, movingState, movedState, direction, extending, sourcePiston);
		clientLevel.setBlockEntity(clientEntity);
		clientEntity.setChanged();
	}

	private static void clearPistonScenarioSpace(ServerLevel serverLevel, ClientLevel clientLevel, BlockPos center) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					BlockPos pos = center.offset(dx, dy, dz);
					serverLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
					clientLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}
	}

	private static net.minecraft.world.level.block.state.properties.PistonType pistonTypeFor(BlockState movedState) {
		if (movedState.is(Blocks.STICKY_PISTON)) {
			return net.minecraft.world.level.block.state.properties.PistonType.STICKY;
		}
		if (movedState.is(Blocks.PISTON_HEAD)
			&& movedState.getValue(net.minecraft.world.level.block.piston.PistonHeadBlock.TYPE) == net.minecraft.world.level.block.state.properties.PistonType.STICKY) {
			return net.minecraft.world.level.block.state.properties.PistonType.STICKY;
		}
		return net.minecraft.world.level.block.state.properties.PistonType.DEFAULT;
	}

	private static void setupDeterministicSupportPlatform(Minecraft minecraft, LocalPlayer player) {
		if (!FORCE_REAL_SURVIVAL_CRACK || !FORCE_REAL_SURVIVAL_CRACK_SETUP_BLOCK || minecraft.level == null || !"unset".equals(deterministicSupportBlock)) {
			return;
		}
		BlockPos center = BlockPos.containing(player.getX(), player.getY() - 1.0, player.getZ());
		BlockState setupState = Blocks.STONE.defaultBlockState();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				BlockPos pos = center.offset(dx, 0, dz);
				minecraft.level.setBlock(pos, setupState, 3);
				if (minecraft.getSingleplayerServer() != null) {
					ServerLevel serverLevel = minecraft.getSingleplayerServer().getLevel(minecraft.level.dimension());
					if (serverLevel != null) {
						serverLevel.setBlock(pos, setupState, 3);
					}
				}
			}
		}
		deterministicSupportBlock = center.toShortString();
		deterministicSupportBlockType = setupState.getBlock().builtInRegistryHolder().key().location().toString();
		realSurvivalCrackLastStatus = "setup-support-platform";
		LOGGER.info(
			"Deterministic capture support platform placed {} centered at {}",
			deterministicSupportBlockType,
			deterministicSupportBlock
		);
	}

	private static void recordPlayerSafetyState(LocalPlayer player) {
		lastSafetyPosition = player.position();
		lastSafetyVelocity = player.getDeltaMovement();
		lastSafetyHealth = player.getHealth();
		lastSafetyFallDistance = player.fallDistance;
	}

	private static void driveRealSurvivalCrack(Minecraft minecraft, LocalPlayer player) {
		if (!FORCE_REAL_SURVIVAL_CRACK || minecraft.screen != null || minecraft.gameMode == null || player.isSpectator()) {
			realSurvivalCrackLastStatus = "inactive";
			return;
		}
		if (!(minecraft.hitResult instanceof BlockHitResult blockHitResult) || blockHitResult.getType() != HitResult.Type.BLOCK) {
			minecraft.gameMode.stopDestroyBlock();
			realSurvivalCrackBlock = null;
			realSurvivalCrackDirection = null;
			realSurvivalCrackStopCalls++;
			realSurvivalCrackLastHitType = minecraft.hitResult == null ? "null" : minecraft.hitResult.getType().name();
			realSurvivalCrackLastTarget = "none";
			realSurvivalCrackLastDirection = "none";
			realSurvivalCrackLastStatus = "no-block-hit";
			return;
		}
		BlockPos blockPos = blockHitResult.getBlockPos();
		BlockState state = minecraft.level == null ? null : minecraft.level.getBlockState(blockPos);
		realSurvivalCrackLastHitType = blockHitResult.getType().name();
		realSurvivalCrackLastTarget = blockPos.toShortString();
		realSurvivalCrackLastDirection = blockHitResult.getDirection().name();
		if (state == null || state.isAir()) {
			minecraft.gameMode.stopDestroyBlock();
			realSurvivalCrackBlock = null;
			realSurvivalCrackDirection = null;
			realSurvivalCrackStopCalls++;
			realSurvivalCrackLastStatus = "air-or-no-level";
			return;
		}
		long gameTime = minecraft.level.getGameTime();
		if (realSurvivalCrackLastDriveGameTime == gameTime) {
			realSurvivalCrackLastStatus = "already-drove-this-tick";
			return;
		}
		realSurvivalCrackLastDriveGameTime = gameTime;
		realSurvivalCrackValidBlockHitCount++;
		realSurvivalCrackLastValidTarget = blockPos.toShortString();
		realSurvivalCrackLastValidBlockType = state.getBlock().builtInRegistryHolder().key().location().toString();
		Direction direction = blockHitResult.getDirection();
		if (!blockPos.equals(realSurvivalCrackBlock) || direction != realSurvivalCrackDirection) {
			minecraft.gameMode.startDestroyBlock(blockPos, direction);
			realSurvivalCrackBlock = blockPos;
			realSurvivalCrackDirection = direction;
			realSurvivalCrackStartCalls++;
			realSurvivalCrackLastStatus = "start";
			return;
		}
		minecraft.gameMode.continueDestroyBlock(blockPos, direction);
		realSurvivalCrackContinueCalls++;
		realSurvivalCrackLastStatus = "continue";
	}

	private static boolean hasBossBarOverride() {
		return FORCED_BOSS_BAR_COUNT >= 0 || !FORCED_BOSS_BAR_PROGRESS.isBlank() || !FORCED_BOSS_BAR_OVERLAY.isBlank();
	}

	private static int forcedBossBarCount() {
		if (FORCED_BOSS_BAR_COUNT >= 0) {
			return Math.max(0, FORCED_BOSS_BAR_COUNT);
		}
		if ("all".equalsIgnoreCase(FORCED_BOSS_BAR_OVERLAY)) {
			return BossBarOverlay.values().length;
		}
		return 1;
	}

	private static float[] forcedBossBarProgresses(int count) {
		String source = FORCED_BOSS_BAR_PROGRESS.isBlank() ? "0.5" : FORCED_BOSS_BAR_PROGRESS;
		String[] tokens = source.split(",");
		float[] values = new float[Math.max(1, tokens.length)];
		for (int i = 0; i < values.length; i++) {
			String token = tokens[i].trim();
			values[i] = token.isBlank() ? 0.5F : Math.max(0.0F, Math.min(1.0F, Float.parseFloat(token)));
		}
		return values;
	}

	private static BossBarOverlay[] forcedBossBarOverlays(int count) {
		if ("all".equalsIgnoreCase(FORCED_BOSS_BAR_OVERLAY)) {
			return BossBarOverlay.values();
		}
		String source = FORCED_BOSS_BAR_OVERLAY.isBlank() ? BossBarOverlay.PROGRESS.getSerializedName() : FORCED_BOSS_BAR_OVERLAY;
		String[] tokens = source.split(",");
		BossBarOverlay[] values = new BossBarOverlay[Math.max(1, tokens.length)];
		for (int i = 0; i < values.length; i++) {
			values[i] = parseBossBarOverlay(tokens[i].trim());
		}
		return values;
	}

	private static BossBarOverlay parseBossBarOverlay(String value) {
		for (BossBarOverlay overlay : BossBarOverlay.values()) {
			if (overlay.name().equalsIgnoreCase(value) || overlay.getSerializedName().equalsIgnoreCase(value)) {
				return overlay;
			}
		}
		throw new IllegalArgumentException("unknown deterministic capture boss bar overlay: " + value);
	}

	private static void applyHeartVariantOverride(LocalPlayer player) {
		if (FORCED_PLAYER_HEART_VARIANT.isBlank()) {
			return;
		}
		player.removeEffect(MobEffects.POISON);
		player.removeEffect(MobEffects.WITHER);
		player.setTicksFrozen(0);
		switch (FORCED_PLAYER_HEART_VARIANT.toLowerCase(Locale.ROOT)) {
			case "normal" -> {
			}
			case "poison", "poisoned" -> player.forceAddEffect(new MobEffectInstance(MobEffects.POISON, 20_000, 0, false, false, false), null);
			case "wither", "withered" -> player.forceAddEffect(new MobEffectInstance(MobEffects.WITHER, 20_000, 0, false, false, false), null);
			case "frozen" -> player.setTicksFrozen(player.getTicksRequiredToFreeze());
			default -> throw new IllegalArgumentException("unknown deterministic capture player heart variant: " + FORCED_PLAYER_HEART_VARIANT);
		}
	}

	private static MobEffectInstance copyEffect(MobEffectInstance effect) {
		return effect == null ? null : new MobEffectInstance(effect);
	}

	private static void restoreEffect(LocalPlayer player, Holder<MobEffect> effect, MobEffectInstance original) {
		player.removeEffect(effect);
		if (original != null) {
			player.forceAddEffect(new MobEffectInstance(original), null);
		}
	}

	private static GameType forcedGameMode() {
		if (FORCED_GAME_MODE.isBlank()) {
			return null;
		}
		GameType gameType = GameType.byName(FORCED_GAME_MODE, null);
		if (gameType == null) {
			throw new IllegalArgumentException("unknown deterministic capture game mode: " + FORCED_GAME_MODE);
		}
		return gameType;
	}

	private static AttackIndicatorStatus forcedAttackIndicator() {
		if (FORCED_ATTACK_INDICATOR.isBlank()) {
			return null;
		}
		for (AttackIndicatorStatus status : AttackIndicatorStatus.values()) {
			if (status.name().equalsIgnoreCase(FORCED_ATTACK_INDICATOR)) {
				return status;
			}
		}
		throw new IllegalArgumentException("unknown deterministic capture attack indicator: " + FORCED_ATTACK_INDICATOR);
	}

	private static CameraType forcedCameraType() {
		if (FORCED_CAMERA_TYPE.isBlank()) {
			return null;
		}
		try {
			return CameraType.valueOf(FORCED_CAMERA_TYPE.toUpperCase(Locale.ROOT).replace('-', '_'));
		} catch (IllegalArgumentException exception) {
			fail("invalid deterministic camera type override: " + FORCED_CAMERA_TYPE);
			return null;
		}
	}

	private static String currentCameraType(Minecraft minecraft) {
		return minecraft.options.getCameraType().name();
	}

	private static int currentSelectedHotbarSlot(LocalPlayer player) {
		return player.getInventory().getSelectedSlot() + 1;
	}

	private static void writeFailureMetadata(String reason) {
		StringBuilder json = new StringBuilder(4096);
		json.append("{\n");
		appendField(json, "status", "failed").append(",\n");
		appendField(json, "reason", reason).append(",\n");
		json.append("  \"rustGalWorldMovingMeshSetup\": { ");
		appendField(json, "stage", movingMeshSetupStage, 0).append(", ");
		appendField(json, "lastMissing", movingMeshSetupLastMissing, 0).append(", ");
		json.append("\"attempts\": ").append(movingMeshSetupAttempts)
			.append(", \"complete\": ").append(movingMeshScenarioSetup)
			.append(" },\n");
		appendField(json, "rustGalWorldMovingMeshProducerSummary", movingMeshProducerSummary()).append(",\n");
		json.append("  \"rustGalWorldPistonSetup\": { ");
		appendField(json, "status", pistonSetupStatus, 0).append(", ");
		appendField(json, "blockId", pistonSetupBlockId, 0).append(", ");
		appendField(json, "origin", pistonSetupOrigin, 0).append(", ");
		appendField(json, "blockState", pistonSetupBlockState, 0).append(", ");
		json.append("\"clientBlockEntityPresent\": ").append(pistonSetupClientBlockEntityPresent)
			.append(", \"entityCount\": ").append(pistonSetupEntityCount)
			.append(", \"reseedCount\": ").append(pistonSetupReseedCount)
			.append(" },\n");
		appendMovingBlockDiagnostics(json).append(",\n");
		appendMovingBlockRouteDecisions(json).append(",\n");
		appendMovingBlockShellScanDiagnostics(json).append(",\n");
		appendField(json, "rustGalStaticTerrainScenario", STATIC_TERRAIN_SCENARIO).append(",\n");
		appendStaticTerrainLifecycleState(json).append(",\n");
		appendStaticTerrainDiagnostics(json).append("\n");
		json.append("}\n");
		try {
			Path parent = METADATA_PATH.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			Files.writeString(METADATA_PATH, json, StandardCharsets.UTF_8);
		} catch (IOException exception) {
			LOGGER.error("Unable to write deterministic capture failure metadata", exception);
		}
	}

	private static void writeMetadata(Minecraft minecraft, String status) {
		StringBuilder json = new StringBuilder(4096);
		ClientLevel level = minecraft.level;
		LocalPlayer player = minecraft.player;
		Vec3 currentPosition = player == null ? Vec3.ZERO : player.position();
		String currentDimension = level == null ? "missing" : level.dimension().location().toString();
		long gameTime = level == null ? -1L : level.getGameTime();

		json.append("{\n");
		appendField(json, "status", status).append(",\n");
		appendField(json, "backend", activeBackend()).append(",\n");
		appendField(json, "shaderEnabled", shaderEnabled()).append(",\n");
		appendField(json, "shaderPack", shaderPack()).append(",\n");
		json.append("  \"window\": { \"width\": ").append(windowWidth).append(", \"height\": ").append(windowHeight).append(" },\n");
		appendField(json, "dimension", currentDimension).append(",\n");
		appendVec3(json, "initialPosition", initialPosition == null ? currentPosition : initialPosition).append(",\n");
		appendPoseObject(json, "initialPose", initialPose == null ? new Pose("initial", 0.0F, 0.0F) : initialPose).append(",\n");
		appendVec3(json, "currentPosition", currentPosition).append(",\n");
		json.append("  \"gameTime\": ").append(gameTime).append(",\n");
		json.append("  \"startedGameTime\": ").append(startedGameTime).append(",\n");
		appendField(json, "gitCommit", gitCommit()).append(",\n");
		json.append("  \"distantHorizonsActive\": ").append(isDistantHorizonsActive()).append(",\n");
		appendField(json, "cameraType", minecraft.options.getCameraType().name()).append(",\n");
		appendField(json, "gameMode", minecraft.gameMode == null ? "unknown" : minecraft.gameMode.getPlayerMode().getName()).append(",\n");
		appendField(json, "rustGalWorldOutlineScenario", System.getProperty("mattmc.dev.rustGalWorldOutline.scenario", "")).append(",\n");
		appendField(json, "rustGalWorldOutlineStyle", System.getProperty("mattmc.dev.rustGalWorldOutline.style", "")).append(",\n");
		appendField(json, "rustGalWorldOutlineDepthPolicy", System.getProperty("mattmc.dev.rustGalWorldOutline.depthPolicy", "")).append(",\n");
		appendField(json, "rustGalWorldMaterialMarkerScenario", System.getProperty("mattmc.dev.rustGalWorldMaterial.blockMarkerScenario", "")).append(",\n");
		appendBlockMarkerDiagnostics(json).append(",\n");
		appendField(json, "rustGalWorldTerrainParticleScenario", System.getProperty("mattmc.dev.rustGalWorldMaterial.terrainParticleScenario", "")).append(",\n");
		appendTerrainParticleDiagnostics(json).append(",\n");
		appendField(json, "rustGalStaticTerrainScenario", STATIC_TERRAIN_SCENARIO).append(",\n");
		appendStaticTerrainLifecycleState(json).append(",\n");
		appendStaticTerrainDiagnostics(json).append(",\n");
		appendField(json, "rustGalWorldBlockDisplayScenario", System.getProperty("mattmc.dev.rustGalWorldMesh.blockDisplayScenario", "")).append(",\n");
		appendBlockDisplayDiagnostics(json).append(",\n");
				appendField(json, "rustGalWorldFallingBlockScenario", System.getProperty("mattmc.dev.rustGalWorldMesh.fallingBlockScenario", "")).append(",\n");
				json.append("  \"rustGalWorldMovingMeshSetup\": { ");
				appendField(json, "stage", movingMeshSetupStage, 0).append(", ");
				appendField(json, "lastMissing", movingMeshSetupLastMissing, 0).append(", ");
				json.append("\"attempts\": ").append(movingMeshSetupAttempts)
					.append(", \"complete\": ").append(movingMeshScenarioSetup)
					.append(" },\n");
				json.append("  \"rustGalWorldFallingBlockSetup\": { ");
				appendField(json, "status", fallingBlockSetupStatus, 0).append(", ");
			appendField(json, "blockId", fallingBlockSetupBlockId, 0).append(", ");
			appendField(json, "spawnMethod", fallingBlockSetupSpawnMethod, 0).append(", ");
			appendField(json, "origin", fallingBlockSetupOrigin, 0).append(", ");
			appendField(json, "landing", fallingBlockSetupLanding, 0).append(", ");
			json.append("\"entityCount\": ").append(fallingBlockSetupEntityCount)
				.append(", \"fallHeight\": ").append(fallingBlockSetupFallHeight)
				.append(" },\n");
			json.append("  \"rustGalWorldFallingBlockExtractionProbe\": { \"seen\": ").append(fallingEntitySeenCount)
				.append(", \"shouldRender\": ").append(fallingEntityShouldRenderCount)
				.append(", \"compiledSection\": ").append(fallingEntityCompiledSectionCount)
				.append(", \"extracted\": ").append(fallingEntityExtractedCount)
				.append(" },\n");
			appendField(json, "rustGalWorldPistonScenario", System.getProperty("mattmc.dev.rustGalWorldMesh.pistonScenario", "")).append(",\n");
		json.append("  \"rustGalWorldPistonSetup\": { ");
		appendField(json, "status", pistonSetupStatus, 0).append(", ");
		appendField(json, "blockId", pistonSetupBlockId, 0).append(", ");
		appendField(json, "origin", pistonSetupOrigin, 0).append(", ");
		appendField(json, "blockState", pistonSetupBlockState, 0).append(", ");
		json.append("\"clientBlockEntityPresent\": ").append(pistonSetupClientBlockEntityPresent)
			.append(", \"entityCount\": ").append(pistonSetupEntityCount)
			.append(", \"reseedCount\": ").append(pistonSetupReseedCount)
			.append(" },\n");
			appendFallingBlockDiagnostics(json).append(",\n");
				appendFallingBlockRouteDecisions(json).append(",\n");
				appendMovingBlockDiagnostics(json).append(",\n");
				appendMovingBlockRouteDecisions(json).append(",\n");
				appendMovingBlockShellScanDiagnostics(json).append(",\n");
				json.append("  \"rustGalWorldOutlineDepthProbe\": ").append(Boolean.getBoolean("mattmc.dev.rustGalWorldOutline.depthProbe")).append(",\n");
		json.append("  \"blockOutlineRealTargetForced\": ").append(FORCE_BLOCK_OUTLINE_TARGET).append(",\n");
		json.append("  \"blockOutlineRealTargetAimed\": ").append(AIM_BLOCK_OUTLINE_TARGET).append(",\n");
		json.append("  \"blockOutlinePauseParity\": ").append(BLOCK_OUTLINE_PAUSE_PARITY).append(",\n");
		json.append("  \"realSurvivalCrackCapture\": ").append(FORCE_REAL_SURVIVAL_CRACK).append(",\n");
		json.append("  \"realSurvivalCrackSetupBlock\": ").append(FORCE_REAL_SURVIVAL_CRACK_SETUP_BLOCK).append(",\n");
		appendField(json, "deterministicSupportBlock", deterministicSupportBlock).append(",\n");
		appendField(json, "deterministicSupportBlockType", deterministicSupportBlockType).append(",\n");
		appendVec3(json, "deterministicPlayerPosition", lastSafetyPosition).append(",\n");
		appendVec3(json, "deterministicPlayerVelocity", lastSafetyVelocity).append(",\n");
		json.append("  \"deterministicPlayerHealth\": ").append(format(lastSafetyHealth)).append(",\n");
		json.append("  \"deterministicPlayerFallDistance\": ").append(format(lastSafetyFallDistance)).append(",\n");
		json.append("  \"deterministicPlayerNoGravity\": ").append(player != null && player.isNoGravity()).append(",\n");
		appendField(json, "realSurvivalCrackSetupTarget", realSurvivalCrackSetupTarget).append(",\n");
		appendField(json, "realSurvivalCrackHitType", realSurvivalCrackLastHitType).append(",\n");
		appendField(json, "realSurvivalCrackTarget", realSurvivalCrackLastTarget).append(",\n");
		appendField(json, "realSurvivalCrackDirection", realSurvivalCrackLastDirection).append(",\n");
		appendField(json, "realSurvivalCrackStatus", realSurvivalCrackLastStatus).append(",\n");
		appendField(json, "realSurvivalCrackLastValidTarget", realSurvivalCrackLastValidTarget).append(",\n");
		appendField(json, "realSurvivalCrackLastValidBlockType", realSurvivalCrackLastValidBlockType).append(",\n");
		appendField(json, "realSurvivalCrackLastRenderedTarget", realSurvivalCrackLastRenderedTarget).append(",\n");
		appendField(json, "realSurvivalCrackLastRenderedBlockType", realSurvivalCrackLastRenderedBlockType).append(",\n");
		json.append("  \"realSurvivalCrackStartCalls\": ").append(realSurvivalCrackStartCalls).append(",\n");
		json.append("  \"realSurvivalCrackContinueCalls\": ").append(realSurvivalCrackContinueCalls).append(",\n");
		json.append("  \"realSurvivalCrackStopCalls\": ").append(realSurvivalCrackStopCalls).append(",\n");
		json.append("  \"realSurvivalCrackValidBlockHitCount\": ").append(realSurvivalCrackValidBlockHitCount).append(",\n");
		json.append("  \"realSurvivalCrackRenderedStateCount\": ").append(realSurvivalCrackRenderedStateCount).append(",\n");
		json.append("  \"realSurvivalCrackMinRenderedStage\": ").append(realSurvivalCrackMinRenderedStage == 10 ? -1 : realSurvivalCrackMinRenderedStage).append(",\n");
		json.append("  \"realSurvivalCrackMaxRenderedStage\": ").append(realSurvivalCrackMaxRenderedStage).append(",\n");
		appendForcedBlockOutlineTarget(json).append(",\n");
		appendField(json, "attackIndicator", minecraft.options.attackIndicator().get().name()).append(",\n");
			json.append("  \"attackProgress\": ").append(player == null ? -1.0F : player.getAttackStrengthScale(0.0F)).append(",\n");
				json.append("  \"attackDelay\": ").append(player == null ? -1.0F : player.getCurrentItemAttackStrengthDelay()).append(",\n");
				json.append("  \"attackTargetForced\": ").append(FORCE_ATTACK_TARGET).append(",\n");
				appendField(json, "attackTargetEntity", minecraft.crosshairPickEntity == null ? "none" : minecraft.crosshairPickEntity.getType().toShortString()).append(",\n");
			json.append("  \"armorValue\": ").append(player == null ? -1 : player.getArmorValue()).append(",\n");
			json.append("  \"armorValueOverride\": ").append(FORCED_ARMOR_VALUE).append(",\n");
			json.append("  \"playerHealthOverride\": ").append(format(FORCED_PLAYER_HEALTH)).append(",\n");
			json.append("  \"playerMaxHealthOverride\": ").append(format(FORCED_PLAYER_MAX_HEALTH)).append(",\n");
			json.append("  \"playerAbsorption\": ").append(player == null ? -1.0F : player.getAbsorptionAmount()).append(",\n");
			json.append("  \"playerAbsorptionOverride\": ").append(format(FORCED_PLAYER_ABSORPTION)).append(",\n");
			json.append("  \"playerFoodLevel\": ").append(player == null ? -1 : player.getFoodData().getFoodLevel()).append(",\n");
			json.append("  \"playerFoodLevelOverride\": ").append(FORCED_PLAYER_FOOD_LEVEL).append(",\n");
			json.append("  \"playerFoodSaturation\": ").append(player == null ? -1.0F : player.getFoodData().getSaturationLevel()).append(",\n");
			json.append("  \"playerFoodSaturationOverride\": ").append(format(FORCED_PLAYER_FOOD_SATURATION)).append(",\n");
			json.append("  \"playerFoodHungerEffectOverride\": ").append(FORCE_PLAYER_FOOD_HUNGER_EFFECT).append(",\n");
			json.append("  \"playerFoodJitterOverride\": ").append(FORCE_PLAYER_FOOD_JITTER).append(",\n");
			json.append("  \"playerAirSupply\": ").append(player == null ? -1 : player.getAirSupply()).append(",\n");
			json.append("  \"playerAirSupplyOverride\": ").append(FORCED_PLAYER_AIR_SUPPLY).append(",\n");
			json.append("  \"playerMaxAirSupply\": ").append(player == null ? -1 : player.getMaxAirSupply()).append(",\n");
			json.append("  \"playerMaxAirSupplyOverride\": ").append(FORCED_PLAYER_MAX_AIR_SUPPLY).append(",\n");
			json.append("  \"playerUnderwaterOverride\": ").append(FORCE_PLAYER_UNDERWATER).append(",\n");
			json.append("  \"playerAirPopOverride\": ").append(FORCE_PLAYER_AIR_POP).append(",\n");
			json.append("  \"mountPresentOverride\": ").append(FORCE_MOUNT_PRESENT).append(",\n");
			json.append("  \"mountHealthOverride\": ").append(format(FORCED_MOUNT_HEALTH)).append(",\n");
			json.append("  \"mountMaxHealthOverride\": ").append(format(FORCED_MOUNT_MAX_HEALTH)).append(",\n");
			json.append("  \"mountHealthRowsOverride\": ").append(FORCED_MOUNT_HEALTH_ROWS).append(",\n");
			json.append("  \"hideChat\": ").append(HIDE_CHAT).append(",\n");
			json.append("  \"bossBarOverride\": ").append(hasBossBarOverride()).append(",\n");
			json.append("  \"bossBarCount\": ").append(hasBossBarOverride() ? forcedBossBarCount() : -1).append(",\n");
			appendField(json, "bossBarProgress", FORCED_BOSS_BAR_PROGRESS).append(",\n");
			appendField(json, "bossBarOverlay", FORCED_BOSS_BAR_OVERLAY).append(",\n");
			json.append("  \"selectedHotbarSlot\": ").append(player == null ? -1 : currentSelectedHotbarSlot(player)).append(",\n");
		json.append("  \"experienceProgress\": ").append(player == null ? -1.0F : player.experienceProgress).append(",\n");
		json.append("  \"experienceLevel\": ").append(player == null ? -1 : player.experienceLevel).append(",\n");
		json.append("  \"framesPerPose\": ").append(FRAMES_PER_POSE).append(",\n");
		json.append("  \"settledReadyFrames\": ").append(SETTLED_READY_FRAMES).append(",\n");
		json.append("  \"settledReadyMaxWaitFrames\": ").append(SETTLED_READY_MAX_WAIT_FRAMES).append(",\n");
		json.append("  \"settledReadyGateSatisfied\": ").append(settledReadyGateSatisfied).append(",\n");
		appendField(json, "settledReadySummary", settledReadySummary()).append(",\n");
		json.append("  \"rustGalGuiScreenCycle\": { \"enabled\": ").append(RUST_GAL_GUI_SCREEN_CYCLE)
			.append(", \"complete\": ").append(rustGalGuiScreenCycleComplete)
			.append(", \"stage\": ").append(rustGalGuiScreenCycleStage)
			.append(", \"framesInStage\": ").append(rustGalGuiScreenCycleFramesInStage)
			.append(", \"cyclesCompleted\": ").append(rustGalGuiScreenCyclesCompleted)
			.append(", \"repeatCount\": ").append(RUST_GAL_GUI_SCREEN_CYCLE_REPEATS)
			.append(" },\n");
		json.append("  \"ackTimeoutFrames\": ").append(ACK_TIMEOUT_FRAMES).append(",\n");
			json.append("  \"poseCount\": ").append(poses == null ? POSE_COUNT : poses.length).append(",\n");
			json.append("  \"poseSequence\": [");
			if (poses != null) {
				for (int i = 0; i < poses.length; i++) {
					if (i > 0) {
						json.append(", ");
					}
					json.append('"').append(escape(poses[i].name())).append('"');
				}
			}
			json.append("],\n");
			json.append("  \"internalScreenshots\": ").append(INTERNAL_SCREENSHOTS).append(",\n");
			json.append("  \"yawDelta\": ").append(format(YAW_DELTA)).append(",\n");
			json.append("  \"deterministicTemporalParity\": { \"enabled\": ").append(SystemTimeUniforms.isDeterministicTemporalParityEnabled())
				.append(", \"frameIndex\": ").append(deterministicTemporalFrameIndex())
				.append(", \"frameCounter\": ").append(SystemTimeUniforms.deterministicTemporalFrameCounter())
				.append(", \"frameTime\": ").append(format(SystemTimeUniforms.deterministicTemporalFrameTime()))
				.append(", \"frameTimeCounter\": ").append(format(SystemTimeUniforms.deterministicTemporalFrameTimeCounter()))
				.append(", \"frameTimeSmooth\": ").append(format(SystemTimeUniforms.deterministicTemporalFrameTimeSmooth()))
				.append(", \"partialTick\": ").append(format(SystemTimeUniforms.deterministicTemporalPartialTick()))
				.append(", \"fovModifier\": ").append(format(SystemTimeUniforms.deterministicTemporalFovModifier()))
				.append(", \"worldTime\": ").append(SystemTimeUniforms.deterministicTemporalWorldTime())
				.append(" },\n");
			json.append("  \"renderedFrameIndex\": ").append(renderedFrameIndex).append(",\n");
		json.append("  \"currentPoseIndex\": ").append(poseIndex).append(",\n");
		json.append("  \"awaitingScreenshotAck\": ").append(awaitingScreenshotAck).append(",\n");
		json.append("  \"captures\": [\n");
		for (int i = 0; i < CAPTURES.size(); i++) {
			PoseCapture capture = CAPTURES.get(i);
			json.append("    {\n");
			json.append("      \"index\": ").append(capture.index()).append(",\n");
			appendField(json, "poseName", capture.poseName(), 6).append(",\n");
			appendField(json, "screenshot", capture.screenshot(), 6).append(",\n");
			appendField(json, "backend", activeBackend(), 6).append(",\n");
			appendField(json, "shaderEnabled", shaderEnabled(), 6).append(",\n");
			appendField(json, "shaderPack", shaderPack(), 6).append(",\n");
			appendField(json, "gitCommit", gitCommit(), 6).append(",\n");
			json.append("      \"window\": { \"width\": ").append(windowWidth).append(", \"height\": ").append(windowHeight).append(" },\n");
			json.append("      \"distantHorizonsActive\": ").append(isDistantHorizonsActive()).append(",\n");
			appendField(json, "dimension", capture.dimension(), 6).append(",\n");
			appendVec3(json, "position", capture.position(), 6).append(",\n");
			appendVec3(json, "requestedPosition", capture.requestedPosition(), 6).append(",\n");
			json.append("      \"requestedYaw\": ").append(format(capture.requestedYaw())).append(",\n");
			json.append("      \"requestedPitch\": ").append(format(capture.requestedPitch())).append(",\n");
				json.append("      \"observedYaw\": ").append(format(capture.observedYaw())).append(",\n");
				json.append("      \"observedPitch\": ").append(format(capture.observedPitch())).append(",\n");
				json.append("      \"renderedFrameIndex\": ").append(capture.renderedFrameIndex()).append(",\n");
				json.append("      \"deterministicTemporal\": { \"enabled\": ").append(SystemTimeUniforms.isDeterministicTemporalParityEnabled())
					.append(", \"frameIndex\": ").append(capture.index() * FRAMES_PER_POSE)
					.append(", \"frameCounter\": ").append(capture.index() * FRAMES_PER_POSE)
					.append(", \"frameTime\": ").append(format(SystemTimeUniforms.deterministicTemporalFrameTime()))
					.append(", \"frameTimeCounter\": ").append(format((capture.index() * FRAMES_PER_POSE * SystemTimeUniforms.deterministicTemporalFrameTime()) % 3600.0F))
					.append(", \"frameTimeSmooth\": ").append(format(SystemTimeUniforms.deterministicTemporalFrameTimeSmooth()))
					.append(", \"partialTick\": ").append(format(SystemTimeUniforms.deterministicTemporalPartialTick()))
					.append(", \"fovModifier\": ").append(format(SystemTimeUniforms.deterministicTemporalFovModifier()))
					.append(", \"worldTime\": ").append(SystemTimeUniforms.deterministicTemporalWorldTime())
					.append(" },\n");
				json.append("      \"gameTime\": ").append(capture.gameTime()).append(",\n");
				appendField(json, "captureMethod", capture.captureMethod(), 6);
				if (!capture.targetWindow().isEmpty()) {
					json.append(",\n");
					appendField(json, "targetWindow", capture.targetWindow(), 6).append("\n");
				} else {
					json.append("\n");
				}
			json.append("    }").append(i + 1 == CAPTURES.size() ? "\n" : ",\n");
		}
		json.append("  ],\n");
		appendStaticTerrainWaterAnimationDenseCapture(json);
		json.append("}\n");

		try {
			Files.writeString(METADATA_PATH, json.toString(), StandardCharsets.UTF_8);
		} catch (IOException exception) {
			LOGGER.error("Unable to write deterministic capture metadata", exception);
		}
	}

	private static void appendStaticTerrainWaterAnimationDenseCapture(StringBuilder json) {
		json.append("  \"rustGalStaticTerrainWaterAnimationDenseCapture\": {\n");
		json.append("    \"enabled\": ").append(STATIC_TERRAIN_WATER_ANIMATION_DENSE_CAPTURE).append(",\n");
		json.append("    \"requestedFrames\": ").append(STATIC_TERRAIN_WATER_ANIMATION_DENSE_FRAMES).append(",\n");
		json.append("    \"capturedFrames\": ").append(WATER_ANIMATION_CAPTURES.size()).append(",\n");
		json.append("    \"complete\": ").append(staticTerrainWaterAnimationDenseComplete).append(",\n");
		json.append("    \"inFlight\": ").append(staticTerrainWaterAnimationScreenshotInFlight).append(",\n");
		json.append("    \"frames\": [");
		for (int i = 0; i < WATER_ANIMATION_CAPTURES.size(); i++) {
			WaterAnimationFrameCapture frame = WATER_ANIMATION_CAPTURES.get(i);
			if (i > 0) {
				json.append(",");
			}
			json.append("\n      { ");
			json.append("\"index\": ").append(frame.index()).append(", ");
			appendField(json, "screenshot", frame.screenshot(), 0).append(", ");
			json.append("\"renderedFrameIndex\": ").append(frame.renderedFrameIndex()).append(", ");
			json.append("\"gameTime\": ").append(frame.gameTime()).append(", ");
			json.append("\"animationHash\": ").append(frame.animationHash()).append(", ");
			appendField(json, "animationSummary", frame.animationSummary(), 0).append(", ");
			appendField(json, "animationState", frame.animationState(), 0).append(", ");
			json.append("\"visibleLayerSubmissions\": ").append(frame.visibleLayerSubmissions()).append(", ");
			json.append("\"currentFrameVisibleLayerSubmissions\": ").append(frame.currentFrameVisibleLayerSubmissions()).append(", ");
			json.append("\"atlasGeneration\": ").append(frame.atlasGeneration()).append(" }");
		}
		if (!WATER_ANIMATION_CAPTURES.isEmpty()) {
			json.append("\n    ");
		}
		json.append("]\n");
		json.append("  }\n");
	}

	private static boolean isDistantHorizonsActive() {
		try {
			Class.forName("com.seibel.distanthorizons.core.render.renderer.LodRenderer", false, DeterministicCameraCapture.class.getClassLoader());
			return true;
		} catch (ClassNotFoundException exception) {
			return false;
		}
	}

	private static String activeBackend() {
		return VulkanicAPI.getActiveBackendType().name().toLowerCase(Locale.ROOT);
	}

	private static String shaderEnabled() {
		return System.getProperty("mattmc.dev.deterministicCameraCapture.shaderEnabled", "unknown");
	}

	private static String shaderPack() {
		return System.getProperty("mattmc.dev.deterministicCameraCapture.shaderPack", "unknown");
	}

	private static String gitCommit() {
		return System.getProperty("mattmc.dev.deterministicCameraCapture.gitCommit", "unknown");
	}

	private static StringBuilder appendField(StringBuilder json, String key, String value) {
		return appendField(json, key, value, 2);
	}

	private static StringBuilder appendField(StringBuilder json, String key, String value, int indent) {
		json.append(" ".repeat(indent)).append('"').append(key).append("\": \"").append(escape(value)).append('"');
		return json;
	}

	private static StringBuilder appendVec3(StringBuilder json, String key, Vec3 value) {
		return appendVec3(json, key, value, 2);
	}

	private static StringBuilder appendVec3(StringBuilder json, String key, Vec3 value, int indent) {
		json.append(" ".repeat(indent))
			.append('"').append(key).append("\": { \"x\": ")
			.append(format(value.x)).append(", \"y\": ")
			.append(format(value.y)).append(", \"z\": ")
			.append(format(value.z)).append(" }");
		return json;
	}

	private static StringBuilder appendForcedBlockOutlineTarget(StringBuilder json) {
		json.append("  \"blockOutlineTarget\": ");
		if (forcedBlockOutlineTarget == null) {
			return json.append("null");
		}
		json.append("{ ");
		appendField(json, "blockPos", forcedBlockOutlineTarget.blockPos().toShortString(), 0).append(", ");
		appendField(json, "face", forcedBlockOutlineTarget.face().name(), 0).append(", ");
		appendVec3(json, "hitLocation", forcedBlockOutlineTarget.hitLocation(), 0).append(", ");
		appendVec3(json, "playerPosition", forcedBlockOutlineTarget.playerPosition(), 0).append(", ");
		appendPoseObject(json, "pose", forcedBlockOutlineTarget.pose());
		json.append(" }");
		return json;
	}

	private static StringBuilder appendBlockMarkerDiagnostics(StringBuilder json) {
		List<RustGalWorldPrimitiveRenderer.BlockMarkerDiagnostic> diagnostics =
			RustGalWorldPrimitiveRenderer.blockMarkerDiagnostics();
		json.append("  \"rustGalWorldMaterialMarkers\": [");
		for (int i = 0; i < diagnostics.size(); i++) {
			RustGalWorldPrimitiveRenderer.BlockMarkerDiagnostic marker = diagnostics.get(i);
			if (i > 0) {
				json.append(",");
			}
			json.append("\n    { ");
			json.append("\"frameIndex\": ").append(marker.frameIndex()).append(", ");
			appendField(json, "route", marker.route(), 0).append(", ");
			json.append("\"textureId\": ").append(marker.textureId()).append(", ");
			json.append("\"center\": { \"x\": ").append(format(marker.centerX()))
				.append(", \"y\": ").append(format(marker.centerY()))
				.append(", \"z\": ").append(format(marker.centerZ())).append(" }, ");
			json.append("\"quadSize\": ").append(format(marker.quadSize())).append(", ");
			json.append("\"colorArgb\": ").append(Integer.toUnsignedLong(marker.colorArgb())).append(", ");
			json.append("\"viewport\": { \"width\": ").append(marker.viewportWidth())
				.append(", \"height\": ").append(marker.viewportHeight()).append(" }, ");
			json.append("\"projected\": ").append(marker.projected()).append(", ");
			json.append("\"screenBounds\": { \"left\": ").append(format(marker.screenLeft()))
				.append(", \"top\": ").append(format(marker.screenTop()))
				.append(", \"right\": ").append(format(marker.screenRight()))
				.append(", \"bottom\": ").append(format(marker.screenBottom())).append(" }");
			json.append(" }");
		}
		if (!diagnostics.isEmpty()) {
			json.append("\n  ");
		}
		json.append("]");
		return json;
	}

	private static StringBuilder appendTerrainParticleDiagnostics(StringBuilder json) {
		List<RustGalWorldPrimitiveRenderer.TerrainParticleDiagnostic> diagnostics =
			RustGalWorldPrimitiveRenderer.terrainParticleDiagnostics();
		json.append("  \"rustGalWorldTerrainParticles\": [");
		for (int i = 0; i < diagnostics.size(); i++) {
			RustGalWorldPrimitiveRenderer.TerrainParticleDiagnostic particle = diagnostics.get(i);
			if (i > 0) {
				json.append(",");
			}
			json.append("\n    { ");
			json.append("\"frameIndex\": ").append(particle.frameIndex()).append(", ");
			appendField(json, "route", particle.route(), 0).append(", ");
			json.append("\"textureId\": ").append(particle.textureId()).append(", ");
			appendField(json, "spriteId", particle.spriteId(), 0).append(", ");
			json.append("\"center\": { \"x\": ").append(format(particle.centerX()))
				.append(", \"y\": ").append(format(particle.centerY()))
				.append(", \"z\": ").append(format(particle.centerZ())).append(" }, ");
			json.append("\"quadSize\": ").append(format(particle.quadSize())).append(", ");
			json.append("\"colorArgb\": ").append(Integer.toUnsignedLong(particle.colorArgb())).append(", ");
			json.append("\"packedLight\": ").append(particle.packedLight()).append(", ");
			json.append("\"materialMode\": ").append(particle.materialMode()).append(", ");
			json.append("\"uv\": { \"u0\": ").append(format(particle.localU0()))
				.append(", \"u1\": ").append(format(particle.localU1()))
				.append(", \"v0\": ").append(format(particle.localV0()))
				.append(", \"v1\": ").append(format(particle.localV1())).append(" }, ");
			json.append("\"viewport\": { \"width\": ").append(particle.viewportWidth())
				.append(", \"height\": ").append(particle.viewportHeight()).append(" }, ");
			json.append("\"projected\": ").append(particle.projected()).append(", ");
			json.append("\"screenBounds\": { \"left\": ").append(format(particle.screenLeft()))
				.append(", \"top\": ").append(format(particle.screenTop()))
				.append(", \"right\": ").append(format(particle.screenRight()))
				.append(", \"bottom\": ").append(format(particle.screenBottom())).append(" }");
			json.append(" }");
		}
		if (!diagnostics.isEmpty()) {
			json.append("\n  ");
		}
		json.append("]");
		return json;
	}

	private static StringBuilder appendStaticTerrainDiagnostics(StringBuilder json) {
		RustGalTerrainRenderer.TerrainDiagnostics diagnostics = RustGalTerrainRenderer.diagnosticsSnapshot();
		json.append("  \"rustGalStaticTerrainDiagnostics\": {\n");
		json.append("    \"cachedLayerAssets\": ").append(diagnostics.cachedLayerAssets()).append(",\n");
		json.append("    \"activeTerrainLayers\": ").append(diagnostics.activeTerrainLayers()).append(",\n");
		json.append("    \"activeSectionAssets\": ").append(diagnostics.activeSectionAssets()).append(",\n");
		json.append("    \"currentFrameVisibleLayerSubmissions\": ").append(diagnostics.currentFrameVisibleLayerSubmissions()).append(",\n");
		json.append("    \"atlasGeneration\": ").append(diagnostics.atlasGeneration()).append(",\n");
		json.append("    \"registeredAtlasGeneration\": ").append(diagnostics.registeredAtlasGeneration()).append(",\n");
		json.append("    \"activeNativeVertexStride\": ").append(diagnostics.activeNativeVertexStride()).append(",\n");
		json.append("    \"expectedNativeVertexStride\": ").append(diagnostics.expectedNativeVertexStride()).append(",\n");
		json.append("    \"acceptedBuildOutputs\": ").append(diagnostics.acceptedBuildOutputs()).append(",\n");
		json.append("    \"skippedRouteBuildOutputs\": ").append(diagnostics.skippedRouteBuildOutputs()).append(",\n");
		json.append("    \"skippedUnsupportedAnimatedSections\": ").append(diagnostics.skippedUnsupportedAnimatedSections()).append(",\n");
		json.append("    \"skippedUnsupportedFluidTranslucentSections\": ").append(diagnostics.skippedUnsupportedFluidTranslucentSections()).append(",\n");
		json.append("    \"acceptedWaterAnimatedSections\": ").append(diagnostics.acceptedWaterAnimatedSections()).append(",\n");
		json.append("    \"unsupportedFluidOmittedSections\": ").append(diagnostics.unsupportedFluidOmittedSections()).append(",\n");
		json.append("    \"skippedEmptyLayers\": ").append(diagnostics.skippedEmptyLayers()).append(",\n");
		json.append("    \"registeredMeshes\": ").append(diagnostics.registeredMeshes()).append(",\n");
		json.append("    \"registeredTranslucentSorts\": ").append(diagnostics.registeredTranslucentSorts()).append(",\n");
		json.append("    \"registeredTranslucentSortBytes\": ").append(diagnostics.registeredTranslucentSortBytes()).append(",\n");
		json.append("    \"translucentSortGenerations\": ").append(diagnostics.translucentSortGenerations()).append(",\n");
		json.append("    \"texturePayloadUpdates\": ").append(diagnostics.texturePayloadUpdates()).append(",\n");
		json.append("    \"texturePayloadUpdateBytes\": ").append(diagnostics.texturePayloadUpdateBytes()).append(",\n");
		json.append("    \"atlasTextureOnlyUpdates\": ").append(diagnostics.atlasTextureOnlyUpdates()).append(",\n");
		json.append("    \"atlasMissingPayloadUpdates\": ").append(diagnostics.atlasMissingPayloadUpdates()).append(",\n");
		json.append("    \"atlasMalformedPayloadUpdates\": ").append(diagnostics.atlasMalformedPayloadUpdates()).append(",\n");
		json.append("    \"atlasPartialPayloadUpdates\": ").append(diagnostics.atlasPartialPayloadUpdates()).append(",\n");
		json.append("    \"removedLayers\": ").append(diagnostics.removedLayers()).append(",\n");
		json.append("    \"visibleLayerProbes\": ").append(diagnostics.visibleLayerProbes()).append(",\n");
		json.append("    \"visibleLayerSubmissions\": ").append(diagnostics.visibleLayerSubmissions()).append(",\n");
		json.append("    \"failedLayerSubmissions\": ").append(diagnostics.failedLayerSubmissions()).append(",\n");
		json.append("    \"invalidations\": ").append(diagnostics.invalidations()).append(",\n");
		json.append("    \"terrainExtractionFrames\": ").append(diagnostics.terrainExtractionFrames()).append(",\n");
		json.append("    \"rustEnqueueFrames\": ").append(diagnostics.rustEnqueueFrames()).append(",\n");
		appendWorldMeshAssetMetrics(json).append(",\n");
		json.append("    \"lifecycleEvents\": [");
		appendTerrainDiagnosticEvents(json, diagnostics.lifecycleEvents(), diagnostics.expectedNativeVertexStride());
		json.append("    ],\n");
		json.append("    \"translucentEvents\": [");
		appendTerrainDiagnosticEvents(json, diagnostics.translucentEvents(), diagnostics.expectedNativeVertexStride());
		json.append("    ],\n");
		json.append("    \"recentEvents\": [");
		appendTerrainDiagnosticEvents(json, diagnostics.recentEvents(), diagnostics.expectedNativeVertexStride());
		json.append("]\n  }");
		return json;
	}

	private static StringBuilder appendWorldMeshAssetMetrics(StringBuilder json) {
		RustGalWorldPrimitiveRenderer.WorldMeshAssetMetrics metrics = RustGalWorldPrimitiveRenderer.worldMeshAssetMetrics();
		json.append("    \"worldMeshAssetMetrics\": { ");
		json.append("\"generation\": ").append(metrics.generation()).append(", ");
		json.append("\"uploadedGeneration\": ").append(metrics.uploadedGeneration()).append(", ");
		json.append("\"payloadCount\": ").append(metrics.payloadCount()).append(", ");
		json.append("\"payloadBytes\": ").append(metrics.payloadBytes()).append(", ");
		json.append("\"failures\": ").append(metrics.failures()).append(", ");
		json.append("\"cachedMeshes\": ").append(metrics.cachedMeshes()).append(", ");
		json.append("\"cachedTextures\": ").append(metrics.cachedTextures()).append(", ");
		json.append("\"dirtyMeshes\": ").append(metrics.dirtyMeshes()).append(", ");
		json.append("\"dirtyTextures\": ").append(metrics.dirtyTextures()).append(", ");
		json.append("\"pendingInstances\": ").append(metrics.pendingInstances());
		json.append(" }");
		return json;
	}

	private static void appendTerrainDiagnosticEvents(
		StringBuilder json,
		List<RustGalTerrainRenderer.TerrainDiagnosticEvent> events,
		int expectedNativeVertexStride
	) {
		for (int i = 0; i < events.size(); i++) {
			RustGalTerrainRenderer.TerrainDiagnosticEvent event = events.get(i);
			if (i > 0) {
				json.append(",");
			}
			json.append("\n      { ");
			json.append("\"frame\": ").append(event.gameplayFrameId()).append(", ");
			json.append("\"sectionKey\": ").append(event.sectionPos()).append(", ");
			json.append("\"gameplayFrameId\": ").append(event.gameplayFrameId()).append(", ");
			json.append("\"terrainExtractionFrameId\": ").append(event.terrainExtractionFrameId()).append(", ");
			json.append("\"rustEnqueueFrameId\": ").append(event.rustEnqueueFrameId()).append(", ");
			json.append("\"executionFrameId\": ").append(event.executionFrameId()).append(", ");
			json.append("\"executionSubmissionId\": ").append(event.executionSubmissionId()).append(", ");
			json.append("\"sectionPos\": ").append(event.sectionPos()).append(", ");
			appendField(json, "layer", event.layer(), 0).append(", ");
				json.append("\"sourceGeneration\": ").append(event.sourceGeneration()).append(", ");
				json.append("\"meshGeneration\": ").append(event.meshGeneration()).append(", ");
					json.append("\"visibleGeneration\": ").append(event.visibleGeneration()).append(", ");
					json.append("\"uploadGeneration\": ").append(event.uploadGeneration()).append(", ");
					json.append("\"meshKey\": ").append(event.meshKey()).append(", ");
					json.append("\"contentHash\": ").append(event.contentHash()).append(", ");
					json.append("\"vertexCount\": ").append(event.vertexCount()).append(", ");
					json.append("\"bufferVertexCapacity\": ").append(event.bufferVertexCapacity()).append(", ");
					json.append("\"vertexStride\": ").append(event.vertexStride()).append(", ");
					json.append("\"expectedNativeVertexStride\": ").append(expectedNativeVertexStride).append(", ");
					json.append("\"indexCount\": ").append(event.indexCount()).append(", ");
					json.append("\"maxIndex\": ").append(event.maxIndex()).append(", ");
					json.append("\"indexType\": ").append(event.indexType()).append(", ");
					json.append("\"sectionCount\": ").append(event.sectionCount()).append(", ");
				json.append("\"sectionOrigin\": { \"x\": ").append(event.sectionOriginX()).append(", \"y\": ").append(event.sectionOriginY()).append(", \"z\": ").append(event.sectionOriginZ()).append(" }, ");
				json.append("\"transformTranslation\": { \"x\": ").append(format(event.transformX())).append(", \"y\": ").append(format(event.transformY())).append(", \"z\": ").append(format(event.transformZ())).append(" }, ");
					json.append("\"localBounds\": { \"minX\": ").append(format(event.localMinX())).append(", \"minY\": ").append(format(event.localMinY())).append(", \"minZ\": ").append(format(event.localMinZ())).append(", \"maxX\": ").append(format(event.localMaxX())).append(", \"maxY\": ").append(format(event.localMaxY())).append(", \"maxZ\": ").append(format(event.localMaxZ())).append(" }, ");
					json.append("\"uvBounds\": { \"minU\": ").append(format(event.uvMinU())).append(", \"minV\": ").append(format(event.uvMinV())).append(", \"maxU\": ").append(format(event.uvMaxU())).append(", \"maxV\": ").append(format(event.uvMaxV())).append(" }, ");
					json.append("\"vertexPositionsFinite\": ").append(event.vertexPositionsFinite()).append(", ");
					json.append("\"localBoundsValid\": ").append(event.localBoundsValid()).append(", ");
					json.append("\"uvBoundsValid\": ").append(event.uvBoundsValid()).append(", ");
					json.append("\"indexRangeValid\": ").append(event.indexRangeValid()).append(", ");
					json.append("\"segmentLayoutValid\": ").append(event.segmentLayoutValid()).append(", ");
					json.append("\"sectionOriginValid\": ").append(event.sectionOriginValid()).append(", ");
					json.append("\"indexOffsetAlignmentValid\": ").append(event.indexOffsetAlignmentValid()).append(", ");
					json.append("\"cameraBoundsFinite\": ").append(event.cameraBoundsFinite()).append(", ");
					json.append("\"normalContractValid\": ").append(event.normalContractValid()).append(", ");
					json.append("\"aoContractValid\": ").append(event.aoContractValid()).append(", ");
					json.append("\"blockSkyLightContractValid\": ").append(event.blockSkyLightContractValid()).append(", ");
					json.append("\"topFaceShadeContractValid\": ").append(event.topFaceShadeContractValid()).append(", ");
					json.append("\"separateAoActive\": ").append(event.separateAoActive()).append(", ");
					json.append("\"separateAoVertexCount\": ").append(event.separateAoVertexCount()).append(", ");
					json.append("\"aoRange\": { \"min\": ").append(format(event.minAo())).append(", \"max\": ").append(format(event.maxAo())).append(" }, ");
					json.append("\"normalSectionCounts\": { \"posY\": ").append(event.positiveYNormalSections()).append(", \"negY\": ").append(event.negativeYNormalSections()).append(", \"horizontal\": ").append(event.horizontalNormalSections()).append(" }, ");
					json.append("\"sortGeneration\": ").append(event.sortGeneration()).append(", ");
					json.append("\"cameraPosition\": { \"x\": ").append(format(event.cameraX())).append(", \"y\": ").append(format(event.cameraY())).append(", \"z\": ").append(format(event.cameraZ())).append(" }, ");
					json.append("\"sortOrigin\": { \"x\": ").append(format(event.sortOriginX())).append(", \"y\": ").append(format(event.sortOriginY())).append(", \"z\": ").append(format(event.sortOriginZ())).append(" }, ");
					json.append("\"primitiveCount\": ").append(event.primitiveCount()).append(", ");
					json.append("\"sortedIndexHash\": ").append(event.sortedIndexHash()).append(", ");
					json.append("\"indexUploadGeneration\": ").append(event.indexUploadGeneration()).append(", ");
					json.append("\"translucentDrawOrder\": ").append(event.translucentDrawOrder()).append(", ");
					appendField(json, "sorterType", event.sorterType(), 0).append(", ");
					json.append("\"sourceSortedIndexHash\": ").append(event.sourceSortedIndexHash()).append(", ");
					json.append("\"rustCopiedSortedIndexHash\": ").append(event.rustCopiedSortedIndexHash()).append(", ");
					json.append("\"sourceSortedIndexSampleHash\": ").append(event.sourceSortedIndexSampleHash()).append(", ");
					json.append("\"rustCopiedSortedIndexSampleHash\": ").append(event.rustCopiedSortedIndexSampleHash()).append(", ");
					appendField(json, "sortedIndexSample", event.sortedIndexSample(), 0).append(", ");
				appendField(json, "reason", event.reason(), 0);
				json.append(" }");
			}
		if (!events.isEmpty()) {
			json.append("\n    ");
		}
	}

	private static StringBuilder appendStaticTerrainLifecycleState(StringBuilder json) {
		json.append("  \"rustGalStaticTerrainLifecycle\": { ");
		appendField(json, "stage", staticTerrainLifecycleStage, 0).append(", ");
		appendField(json, "scenario", STATIC_TERRAIN_SCENARIO, 0).append(", ");
		appendField(
			json,
			"editBlock",
			staticTerrainLifecycleEditBlock == null ? "" : staticTerrainLifecycleEditBlock.toShortString(),
			0
		).append(", ");
		appendField(json, "replacementBlock", staticTerrainLifecycleBlockType, 0).append(", ");
		appendField(json, "resourcePackScenario", STATIC_TERRAIN_RESOURCE_PACK_SCENARIO, 0).append(", ");
		appendField(json, "worldId", STATIC_TERRAIN_WORLD_ID, 0).append(", ");
		json.append("\"setup\": ").append(staticTerrainLifecycleSetup).append(", ");
		json.append("\"afterRecorded\": ").append(staticTerrainLifecycleAfterRecorded).append(", ");
		json.append("\"beforeGeneration\": ").append(staticTerrainLifecycleBeforeGeneration).append(", ");
		json.append("\"afterGeneration\": ").append(staticTerrainLifecycleAfterGeneration).append(", ");
		json.append("\"actionStep\": ").append(staticTerrainLifecycleActionStep).append(", ");
		json.append("\"resizeCount\": ").append(staticTerrainLifecycleResizeCount).append(", ");
		json.append("\"originalRenderDistance\": ").append(staticTerrainOriginalRenderDistance).append(", ");
		json.append("\"originalSimulationDistance\": ").append(staticTerrainOriginalSimulationDistance).append(", ");
		json.append("\"beforeCachedLayers\": ").append(staticTerrainLifecycleBeforeCachedLayers).append(", ");
		json.append("\"afterCachedLayers\": ").append(staticTerrainLifecycleAfterCachedLayers).append(", ");
		json.append("\"beforeUsedMemoryBytes\": ").append(staticTerrainLifecycleBeforeRssBytes).append(", ");
		json.append("\"afterUsedMemoryBytes\": ").append(staticTerrainLifecycleAfterRssBytes).append(", ");
		json.append("\"menuCachedLayers\": ").append(staticTerrainMenuCachedLayers).append(", ");
		json.append("\"menuActiveTerrainLayers\": ").append(staticTerrainMenuActiveLayerSnapshot).append(", ");
		json.append("\"menuActiveSectionAssets\": ").append(staticTerrainMenuActiveSectionAssetSnapshot).append(", ");
		json.append("\"menuCurrentFrameVisibleSubmissions\": ").append(staticTerrainMenuCurrentFrameSubmissionSnapshot).append(", ");
		json.append("\"menuUsedMemoryBytes\": ").append(staticTerrainMenuUsedMemoryBytes).append(", ");
		json.append("\"unloadVisibleSubmissions\": ").append(staticTerrainUnloadSubmissionSnapshot).append(", ");
		json.append("\"menuVisibleSubmissions\": ").append(staticTerrainMenuSubmissionSnapshot).append(", ");
		json.append("\"reloadGenerationA\": ").append(staticTerrainReloadGenerationA).append(", ");
		json.append("\"reloadGenerationB\": ").append(staticTerrainReloadGenerationB).append(", ");
		appendField(json, "worldA", staticTerrainLifecycleWorldA, 0).append(", ");
		appendField(json, "worldB", staticTerrainLifecycleWorldB, 0).append(", ");
		json.append("\"currentUsedMemoryBytes\": ").append(currentUsedMemoryBytes()).append(", ");
		json.append("\"waitFrames\": ").append(framesWaitingForStaticTerrainLifecycle);
		json.append(" }");
		return json;
	}

	private static StringBuilder appendBlockDisplayDiagnostics(StringBuilder json) {
		List<RustGalWorldPrimitiveRenderer.BlockDisplayDiagnostic> diagnostics =
			RustGalWorldPrimitiveRenderer.blockDisplayDiagnostics();
		json.append("  \"rustGalWorldBlockDisplays\": [");
		for (int i = 0; i < diagnostics.size(); i++) {
			RustGalWorldPrimitiveRenderer.BlockDisplayDiagnostic display = diagnostics.get(i);
			if (i > 0) {
				json.append(",");
			}
			json.append("\n    { ");
			json.append("\"frameIndex\": ").append(display.frameIndex()).append(", ");
			appendField(json, "route", display.route(), 0).append(", ");
			appendField(json, "blockId", display.blockId(), 0).append(", ");
			json.append("\"meshKey\": ").append(Long.toUnsignedString(display.meshKey())).append(", ");
			json.append("\"meshGeneration\": ").append(display.meshGeneration()).append(", ");
			json.append("\"vertexLayoutVersion\": ").append(display.vertexLayoutVersion()).append(", ");
			json.append("\"indexType\": ").append(display.indexType()).append(", ");
			json.append("\"vertexCount\": ").append(display.vertexCount()).append(", ");
			json.append("\"indexBytes\": ").append(display.indexBytes()).append(", ");
			json.append("\"sectionCount\": ").append(display.sectionCount()).append(", ");
			appendField(json, "textureIds", display.textureIds(), 0).append(", ");
			json.append("\"materialMode\": ").append(display.materialMode()).append(", ");
			json.append("\"viewport\": { \"width\": ").append(display.viewportWidth())
				.append(", \"height\": ").append(display.viewportHeight()).append(" }, ");
			json.append("\"projected\": ").append(display.projected()).append(", ");
			json.append("\"screenBounds\": { \"left\": ").append(format(display.screenLeft()))
				.append(", \"top\": ").append(format(display.screenTop()))
				.append(", \"right\": ").append(format(display.screenRight()))
				.append(", \"bottom\": ").append(format(display.screenBottom())).append(" }");
			json.append(" }");
		}
		if (!diagnostics.isEmpty()) {
			json.append("\n  ");
		}
		json.append("]");
		return json;
	}

	private static StringBuilder appendFallingBlockDiagnostics(StringBuilder json) {
		List<RustGalWorldPrimitiveRenderer.FallingBlockDiagnostic> diagnostics =
			RustGalWorldPrimitiveRenderer.fallingBlockDiagnostics();
		json.append("  \"rustGalWorldFallingBlocks\": [");
		for (int i = 0; i < diagnostics.size(); i++) {
			RustGalWorldPrimitiveRenderer.FallingBlockDiagnostic block = diagnostics.get(i);
			if (i > 0) {
				json.append(",");
			}
			json.append("\n    { ");
			json.append("\"frameIndex\": ").append(block.frameIndex()).append(", ");
			appendField(json, "route", block.route(), 0).append(", ");
			appendField(json, "blockId", block.blockId(), 0).append(", ");
			json.append("\"meshKey\": ").append(Long.toUnsignedString(block.meshKey())).append(", ");
			json.append("\"meshGeneration\": ").append(block.meshGeneration()).append(", ");
			json.append("\"vertexLayoutVersion\": ").append(block.vertexLayoutVersion()).append(", ");
			json.append("\"indexType\": ").append(block.indexType()).append(", ");
			json.append("\"vertexCount\": ").append(block.vertexCount()).append(", ");
			json.append("\"indexBytes\": ").append(block.indexBytes()).append(", ");
			json.append("\"sectionCount\": ").append(block.sectionCount()).append(", ");
			appendField(json, "textureIds", block.textureIds(), 0).append(", ");
			json.append("\"materialMode\": ").append(block.materialMode()).append(", ");
			json.append("\"viewport\": { \"width\": ").append(block.viewportWidth())
				.append(", \"height\": ").append(block.viewportHeight()).append(" }, ");
			json.append("\"projected\": ").append(block.projected()).append(", ");
			json.append("\"screenBounds\": { \"left\": ").append(format(block.screenLeft()))
				.append(", \"top\": ").append(format(block.screenTop()))
				.append(", \"right\": ").append(format(block.screenRight()))
				.append(", \"bottom\": ").append(format(block.screenBottom())).append(" }");
			json.append(" }");
		}
		if (!diagnostics.isEmpty()) {
			json.append("\n  ");
		}
		json.append("]");
		return json;
	}

	private static StringBuilder appendFallingBlockRouteDecisions(StringBuilder json) {
		List<RustGalWorldPrimitiveRenderer.FallingBlockRouteDecision> decisions =
			RustGalWorldPrimitiveRenderer.fallingBlockRouteDecisions();
		json.append("  \"rustGalWorldFallingBlockRouteDecisions\": [");
		for (int i = 0; i < decisions.size(); i++) {
			RustGalWorldPrimitiveRenderer.FallingBlockRouteDecision decision = decisions.get(i);
			if (i > 0) {
				json.append(",");
			}
			json.append("\n    { ");
			json.append("\"frameIndex\": ").append(decision.frameIndex()).append(", ");
			appendField(json, "route", decision.route(), 0).append(", ");
			appendField(json, "blockId", decision.blockId(), 0).append(", ");
			json.append("\"rustSelected\": ").append(decision.rustSelected()).append(", ");
			json.append("\"rustQueued\": ").append(decision.rustQueued()).append(", ");
			json.append("\"javaDrawn\": ").append(decision.javaDrawn());
			json.append(" }");
		}
		if (!decisions.isEmpty()) {
			json.append("\n  ");
		}
		json.append("]");
		return json;
	}

	private static StringBuilder appendMovingBlockDiagnostics(StringBuilder json) {
		List<RustGalWorldPrimitiveRenderer.MovingBlockDiagnostic> diagnostics =
			RustGalWorldPrimitiveRenderer.movingBlockDiagnostics();
		json.append("  \"rustGalWorldMovingBlocks\": [");
		for (int i = 0; i < diagnostics.size(); i++) {
			RustGalWorldPrimitiveRenderer.MovingBlockDiagnostic block = diagnostics.get(i);
			if (i > 0) {
				json.append(",");
			}
			json.append("\n    { ");
			json.append("\"frameIndex\": ").append(block.frameIndex()).append(", ");
			appendField(json, "route", block.route(), 0).append(", ");
			appendField(json, "provenance", block.provenance(), 0).append(", ");
			appendField(json, "blockId", block.blockId(), 0).append(", ");
			json.append("\"meshKey\": ").append(Long.toUnsignedString(block.meshKey())).append(", ");
			json.append("\"meshGeneration\": ").append(block.meshGeneration()).append(", ");
			json.append("\"vertexLayoutVersion\": ").append(block.vertexLayoutVersion()).append(", ");
			json.append("\"indexType\": ").append(block.indexType()).append(", ");
			json.append("\"vertexCount\": ").append(block.vertexCount()).append(", ");
			json.append("\"indexBytes\": ").append(block.indexBytes()).append(", ");
			json.append("\"sectionCount\": ").append(block.sectionCount()).append(", ");
			appendField(json, "textureIds", block.textureIds(), 0).append(", ");
			json.append("\"materialMode\": ").append(block.materialMode()).append(", ");
			json.append("\"viewport\": { \"width\": ").append(block.viewportWidth())
				.append(", \"height\": ").append(block.viewportHeight()).append(" }, ");
			json.append("\"projected\": ").append(block.projected()).append(", ");
			json.append("\"screenBounds\": { \"left\": ").append(format(block.screenLeft()))
				.append(", \"top\": ").append(format(block.screenTop()))
				.append(", \"right\": ").append(format(block.screenRight()))
				.append(", \"bottom\": ").append(format(block.screenBottom())).append(" }, ");
			json.append("\"transformTranslation\": { \"x\": ").append(format(block.transformX()))
				.append(", \"y\": ").append(format(block.transformY()))
				.append(", \"z\": ").append(format(block.transformZ())).append(" }");
			json.append(" }");
		}
		if (!diagnostics.isEmpty()) {
			json.append("\n  ");
		}
		json.append("]");
		return json;
	}

	private static StringBuilder appendMovingBlockRouteDecisions(StringBuilder json) {
		List<RustGalWorldPrimitiveRenderer.MovingBlockRouteDecision> decisions =
			RustGalWorldPrimitiveRenderer.movingBlockRouteDecisions();
		json.append("  \"rustGalWorldMovingBlockRouteDecisions\": [");
		for (int i = 0; i < decisions.size(); i++) {
			RustGalWorldPrimitiveRenderer.MovingBlockRouteDecision decision = decisions.get(i);
			if (i > 0) {
				json.append(",");
			}
			json.append("\n    { ");
			json.append("\"frameIndex\": ").append(decision.frameIndex()).append(", ");
			appendField(json, "provenance", decision.provenance(), 0).append(", ");
			appendField(json, "route", decision.route(), 0).append(", ");
			appendField(json, "blockId", decision.blockId(), 0).append(", ");
			json.append("\"rustSelected\": ").append(decision.rustSelected()).append(", ");
			json.append("\"rustQueued\": ").append(decision.rustQueued()).append(", ");
			json.append("\"javaDrawn\": ").append(decision.javaDrawn());
			json.append(" }");
		}
		if (!decisions.isEmpty()) {
			json.append("\n  ");
		}
		json.append("]");
		return json;
	}

	private static StringBuilder appendMovingBlockShellScanDiagnostics(StringBuilder json) {
		List<RustGalWorldPrimitiveRenderer.MovingBlockShellScanDiagnostic> diagnostics =
			RustGalWorldPrimitiveRenderer.movingBlockShellScanDiagnostics();
		json.append("  \"rustGalWorldMovingBlockShellScans\": [");
		for (int i = 0; i < diagnostics.size(); i++) {
			RustGalWorldPrimitiveRenderer.MovingBlockShellScanDiagnostic scan = diagnostics.get(i);
			if (i > 0) {
				json.append(",");
			}
			json.append("\n    { ");
			json.append("\"frameIndex\": ").append(scan.frameIndex()).append(", ");
			appendField(json, "route", scan.route(), 0).append(", ");
			json.append("\"visiblePistonStates\": ").append(scan.visiblePistonStates()).append(", ");
			json.append("\"fallbackUsed\": ").append(scan.fallbackUsed()).append(", ");
			json.append("\"chunksScanned\": ").append(scan.chunksScanned()).append(", ");
			json.append("\"blockEntitiesInspected\": ").append(scan.blockEntitiesInspected()).append(", ");
			json.append("\"pistonEntitiesFound\": ").append(scan.pistonEntitiesFound()).append(", ");
			json.append("\"pistonStatesExtracted\": ").append(scan.pistonStatesExtracted()).append(", ");
			json.append("\"elapsedNanos\": ").append(scan.elapsedNanos());
			json.append(" }");
		}
		if (!diagnostics.isEmpty()) {
			json.append("\n  ");
		}
		json.append("]");
		return json;
	}

	private static StringBuilder appendPoseObject(StringBuilder json, String key, Pose pose) {
		json.append("  \"").append(key).append("\": { \"yaw\": ")
			.append(format(pose.yaw())).append(", \"pitch\": ")
			.append(format(pose.pitch())).append(", \"offset\": { \"x\": ")
			.append(format(pose.offsetX())).append(", \"y\": ")
			.append(format(pose.offsetY())).append(", \"z\": ")
			.append(format(pose.offsetZ())).append(" } }");
		return json;
	}

	private static String escape(String value) {
		return value == null ? "" : value
			.replace("\\", "\\\\")
			.replace("\"", "\\\"")
			.replace("\n", "\\n")
			.replace("\r", "\\r");
	}

	private static String format(double value) {
		return String.format(Locale.ROOT, "%.6f", value);
	}

	private static Pose[] movingMeshPoseSequence(String prefix, Pose pose, int count) {
		Pose[] sequence = new Pose[count];
		for (int i = 0; i < count; i++) {
			sequence[i] = new Pose(String.format(Locale.ROOT, "%s-%02d", prefix, i), pose.yaw(), pose.pitch());
		}
		return sequence;
	}

	private static Pose[] translucentTerrainPoseSequence(Pose pose) {
		Direction forward = Direction.fromYRot(pose.yaw());
		Direction right = forward.getClockWise();
		return new Pose[] {
			pose.withName("translucent-front"),
			offsetPose("translucent-lateral", pose, right, 4.0, forward, 0.0, 0.0),
			new Pose("translucent-orbit-left", pose.yaw() - 55.0F, pose.pitch(), 0.0, 0.0, 0.0),
			new Pose("translucent-cross-opposite", pose.yaw() + 180.0F, pose.pitch(), forward.getStepX() * 10.0, 0.0, forward.getStepZ() * 10.0),
			offsetPose("translucent-above", pose, forward, 4.0, right, 0.0, 5.0),
			offsetPose("translucent-below", pose, forward, 4.0, right, 0.0, -4.0),
			pose.withName("translucent-return")
		};
	}

	private static Pose offsetPose(String name, Pose pose, Direction primary, double primaryDistance, Direction secondary, double secondaryDistance, double yOffset) {
		double offsetX = primary.getStepX() * primaryDistance + secondary.getStepX() * secondaryDistance;
		double offsetZ = primary.getStepZ() * primaryDistance + secondary.getStepZ() * secondaryDistance;
		return new Pose(name, pose.yaw(), pose.pitch(), offsetX, yOffset, offsetZ);
	}

	private static Vec3 targetPositionForCurrentPose() {
		if (!initialized || initialPosition == null) {
			return null;
		}
		if (poses == null || poseIndex < 0 || poseIndex >= poses.length) {
			return initialPosition;
		}
		return targetPositionForPose(poses[poseIndex]);
	}

	private static Vec3 targetPositionForPose(Pose pose) {
		if (initialPosition == null) {
			return Vec3.ZERO;
		}
		return initialPosition.add(pose.offsetX(), pose.offsetY(), pose.offsetZ());
	}

	private static boolean isKnownDeterministicPosePosition(Vec3 position) {
		if (initialPosition == null) {
			return false;
		}
		if (position.distanceToSqr(initialPosition) <= 0.0004) {
			return true;
		}
		if (poses == null) {
			return false;
		}
		for (Pose pose : poses) {
			if (position.distanceToSqr(targetPositionForPose(pose)) <= 0.0004) {
				return true;
			}
		}
		return false;
	}

	private static Pose[] combinedMovingMeshPoseSequence(Pose pose) {
		Pose[] falling = movingMeshPoseSequence("falling", pose, 5);
		Pose[] piston = movingMeshPoseSequence("piston", pose, 7);
		Pose[] combined = new Pose[falling.length + piston.length];
		System.arraycopy(falling, 0, combined, 0, falling.length);
		System.arraycopy(piston, 0, combined, falling.length, piston.length);
		return combined;
	}

	private static String formatVec(Vec3 value) {
		return "(" + format(value.x) + "," + format(value.y) + "," + format(value.z) + ")";
	}

	private static String readAckStringField(String fieldName) {
		if (currentAckPath == null || !Files.isRegularFile(currentAckPath)) {
			return "";
		}
		try {
			String json = Files.readString(currentAckPath, StandardCharsets.UTF_8);
			String key = "\"" + fieldName + "\"";
			int keyIndex = json.indexOf(key);
			if (keyIndex < 0) {
				return "";
			}
			int colonIndex = json.indexOf(':', keyIndex + key.length());
			if (colonIndex < 0) {
				return "";
			}
			int quoteIndex = json.indexOf('"', colonIndex + 1);
			if (quoteIndex < 0) {
				return "";
			}
			StringBuilder value = new StringBuilder();
			boolean escaped = false;
			for (int i = quoteIndex + 1; i < json.length(); i++) {
				char c = json.charAt(i);
				if (escaped) {
					value.append(c);
					escaped = false;
				} else if (c == '\\') {
					escaped = true;
				} else if (c == '"') {
					return value.toString();
				} else {
					value.append(c);
				}
			}
		} catch (IOException ignored) {
			return "";
		}
		return "";
	}

	private record Pose(String name, float yaw, float pitch, double offsetX, double offsetY, double offsetZ) {
		private Pose(String name, float yaw, float pitch) {
			this(name, yaw, pitch, 0.0, 0.0, 0.0);
		}

		private Pose withName(String name) {
			return new Pose(name, this.yaw, this.pitch, this.offsetX, this.offsetY, this.offsetZ);
		}
	}

	private record ForcedBlockOutlineTarget(
		BlockPos blockPos,
		Direction face,
		Vec3 hitLocation,
		Vec3 playerPosition,
		Pose pose
	) {
	}

	private record PoseCapture(
		int index,
		String poseName,
		String screenshot,
		String dimension,
		Vec3 position,
		Vec3 requestedPosition,
		float requestedYaw,
		float requestedPitch,
		float observedYaw,
		float observedPitch,
		long renderedFrameIndex,
		long gameTime,
		String captureMethod,
		String targetWindow
	) {
	}

	private record WaterAnimationFrameCapture(
		int index,
		String screenshot,
		long renderedFrameIndex,
		long gameTime,
		long animationHash,
		String animationSummary,
		String animationState,
		long visibleLayerSubmissions,
		long currentFrameVisibleLayerSubmissions,
		long atlasGeneration
	) {
	}
}
