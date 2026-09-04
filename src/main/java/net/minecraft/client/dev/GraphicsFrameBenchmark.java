package net.minecraft.client.dev;

import net.minecraft.util.profiling.TracyCompat;
import net.minecraft.util.profiling.TracyCompat.Zone;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.vulkanic.gui.RustGalFrameCoordinator;
import net.vulkanic.world.RustGalTerrainRenderer;
import net.vulkanic.world.RustGalWorldPrimitiveRenderer;
import net.vulkanic.world.DistantHorizonsSemanticCollector;

import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Dev-only frame sampler for graphics migration audits.
 *
 * <p>Enabled only by {@code -Dmattmc.dev.graphicsFrameBenchmark=true}.
 */
public final class GraphicsFrameBenchmark {
	private static final boolean ENABLED = Boolean.getBoolean("mattmc.dev.graphicsFrameBenchmark");
	private static final boolean TRACY_ENABLED = Boolean.getBoolean("mattmc.dev.tracyCapture");
	private static final int SETTLE_FRAMES = Math.max(0, Integer.getInteger("mattmc.dev.graphicsFrameBenchmark.settleFrames", 240));
	private static final int MAX_SETTLE_FRAMES = Math.max(1, Integer.getInteger("mattmc.dev.graphicsFrameBenchmark.maxSettleFrames", 2400));
	private static final int WARMUP_FRAMES = Math.max(0, Integer.getInteger("mattmc.dev.graphicsFrameBenchmark.warmupFrames", 600));
	private static final int MEASURE_FRAMES = Math.max(1, Integer.getInteger("mattmc.dev.graphicsFrameBenchmark.measureFrames", 900));
	private static final float YAW_DELTA = Float.parseFloat(System.getProperty("mattmc.dev.graphicsFrameBenchmark.yawDelta", "70.0"));
	private static final double CAMERA_X = Double.parseDouble(System.getProperty("mattmc.dev.graphicsFrameBenchmark.cameraX", "150.5"));
	private static final double CAMERA_Y = Double.parseDouble(System.getProperty("mattmc.dev.graphicsFrameBenchmark.cameraY", "100.0"));
	private static final double CAMERA_Z = Double.parseDouble(System.getProperty("mattmc.dev.graphicsFrameBenchmark.cameraZ", "530.5"));
	private static final float CAMERA_YAW = Float.parseFloat(System.getProperty("mattmc.dev.graphicsFrameBenchmark.cameraYaw", "0.0"));
	private static final float CAMERA_PITCH = Float.parseFloat(System.getProperty("mattmc.dev.graphicsFrameBenchmark.cameraPitch", "9.7"));
	private static final double WALL_CLOCK_TOLERANCE = Double.parseDouble(System.getProperty("mattmc.dev.graphicsFrameBenchmark.wallClockTolerance", "0.35"));
	private static final double DISPLAY_FPS_TOLERANCE = Double.parseDouble(System.getProperty("mattmc.dev.graphicsFrameBenchmark.displayFpsTolerance", "0.40"));
	private static final int DISPLAY_FPS_MIN_FRAMES = Math.max(1, Integer.getInteger("mattmc.dev.graphicsFrameBenchmark.displayFpsMinFrames", 240));
	private static final long DISPLAY_FPS_MIN_NANOS = Math.max(1L, Long.getLong("mattmc.dev.graphicsFrameBenchmark.displayFpsMinNanos", 2_000_000_000L));
	private static final boolean DISPLAY_FPS_CHECK_ENABLED =
		Boolean.parseBoolean(System.getProperty("mattmc.dev.graphicsFrameBenchmark.displayFpsCheckEnabled", "true"));
	private static final long READINESS_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(Math.max(1L, Long.getLong("mattmc.dev.graphicsFrameBenchmark.readinessTimeoutSeconds", 120L)));
	private static final boolean REQUIRE_DH_EXECUTION =
		Boolean.getBoolean("mattmc.dev.graphicsFrameBenchmark.requireDistantHorizonsExecution");
	private static final long POSITIVE_CONTROL_DELAY_NANOS = Math.max(0L, Long.getLong("mattmc.dev.graphicsFrameBenchmark.positiveControlDelayNanos", 0L));
	private static final boolean GC_BEFORE_MEASUREMENT =
		Boolean.parseBoolean(System.getProperty("mattmc.dev.graphicsFrameBenchmark.gcBeforeMeasurement", "false"));
	private static final long GC_BEFORE_MEASUREMENT_OFFSET_FRAMES =
		Math.max(0L, Long.getLong("mattmc.dev.graphicsFrameBenchmark.gcBeforeMeasurementOffsetFrames", 30L));
	private static final boolean STOP_AFTER_COMPLETE = Boolean.parseBoolean(System.getProperty("mattmc.dev.graphicsFrameBenchmark.stopAfterComplete", "true"));
	private static final int FORCED_ARMOR_VALUE = Integer.getInteger("mattmc.dev.graphicsFrameBenchmark.armorValue", -1);
	private static final float FORCED_PLAYER_HEALTH =
		Float.parseFloat(System.getProperty("mattmc.dev.graphicsFrameBenchmark.playerHealth", "NaN"));
	private static final float FORCED_PLAYER_MAX_HEALTH =
		Float.parseFloat(System.getProperty("mattmc.dev.graphicsFrameBenchmark.playerMaxHealth", "NaN"));
	private static final float FORCED_PLAYER_ABSORPTION =
		Float.parseFloat(System.getProperty("mattmc.dev.graphicsFrameBenchmark.playerAbsorption", "NaN"));
	private static final int FORCED_PLAYER_FOOD_LEVEL = Integer.getInteger("mattmc.dev.graphicsFrameBenchmark.playerFoodLevel", -1);
	private static final float FORCED_PLAYER_FOOD_SATURATION =
		Float.parseFloat(System.getProperty("mattmc.dev.graphicsFrameBenchmark.playerFoodSaturation", "NaN"));
	private static final int FORCED_PLAYER_AIR_SUPPLY = Integer.getInteger("mattmc.dev.graphicsFrameBenchmark.playerAirSupply", -1);
	private static final int FORCED_PLAYER_MAX_AIR_SUPPLY = Integer.getInteger("mattmc.dev.graphicsFrameBenchmark.playerMaxAirSupply", -1);
	private static final boolean FORCE_MOUNT_PRESENT = Boolean.getBoolean("mattmc.dev.graphicsFrameBenchmark.mountPresent");
	private static final float FORCED_MOUNT_HEALTH =
		Float.parseFloat(System.getProperty("mattmc.dev.graphicsFrameBenchmark.mountHealth", "NaN"));
	private static final float FORCED_MOUNT_MAX_HEALTH =
		Float.parseFloat(System.getProperty("mattmc.dev.graphicsFrameBenchmark.mountMaxHealth", "NaN"));
	private static final int FORCED_MOUNT_HEALTH_ROWS = Integer.getInteger("mattmc.dev.graphicsFrameBenchmark.mountHealthRows", -1);
	private static final String FORCED_GAME_MODE = System.getProperty("mattmc.dev.graphicsFrameBenchmark.gameMode", "").trim();
	private static final boolean REAL_TERRAIN_PARTICLE_GAMEPLAY =
		Boolean.getBoolean("mattmc.dev.rustGalWorldMaterial.terrainParticleRealGameplay");
	private static final String BLOCK_DISPLAY_SCENARIO =
		System.getProperty("mattmc.dev.rustGalWorldMesh.blockDisplayScenario", "").trim().toLowerCase(Locale.ROOT);
	private static final String BLOCK_DISPLAY_WORKLOAD =
		System.getProperty("mattmc.dev.rustGalWorldMesh.blockDisplayWorkload", "single").trim().toLowerCase(Locale.ROOT);
	private static final int BLOCK_DISPLAY_INSTANCE_COUNT =
		Integer.getInteger("mattmc.dev.rustGalWorldMesh.blockDisplayInstanceCount", -1);
	private static final String FALLING_BLOCK_SCENARIO =
		System.getProperty("mattmc.dev.rustGalWorldMesh.fallingBlockScenario", "").trim().toLowerCase(Locale.ROOT);
	private static final int FALLING_BLOCK_COUNT =
		Math.max(1, Integer.getInteger("mattmc.dev.rustGalWorldMesh.fallingBlockCount", 1));
	private static final String PISTON_SCENARIO =
		System.getProperty("mattmc.dev.rustGalWorldMesh.pistonScenario", "").trim().toLowerCase(Locale.ROOT);
	private static final String PRIMED_TNT_SCENARIO =
		System.getProperty("mattmc.dev.rustGalWorldMesh.primedTntScenario", "").trim().toLowerCase(Locale.ROOT);
	private static final String ARROW_SCENARIO =
		System.getProperty("mattmc.dev.rustGalWorldMesh.arrowScenario", "").trim().toLowerCase(Locale.ROOT);
	private static final String ITEM_ENTITY_SCENARIO =
		System.getProperty("mattmc.dev.rustGalWorldItemEntity.scenario", "").trim().toLowerCase(Locale.ROOT);
	private static final String EXPERIENCE_ORB_SCENARIO =
		System.getProperty("mattmc.dev.rustGalWorldExperienceOrb.scenario", "").trim().toLowerCase(Locale.ROOT);
	private static final String MODEL_MESH_SCENARIO =
		System.getProperty("mattmc.dev.rustGalWorldMesh.modelScenario", "").trim().toLowerCase(Locale.ROOT);
	private static final String STATIC_TERRAIN_SCENARIO =
		System.getProperty("mattmc.dev.rustGalStaticTerrain.scenario", "").trim().toLowerCase(Locale.ROOT);
	private static final int STATIC_TERRAIN_STEADY_FRAMES =
		Math.max(1, Integer.getInteger("mattmc.dev.rustGalStaticTerrain.steadyFrames", 120));
	private static final int PISTON_COUNT =
		Math.max(1, Integer.getInteger("mattmc.dev.rustGalWorldMesh.pistonCount", 1));
	private static final int REAL_TERRAIN_PARTICLE_MATERIAL_COUNT = 5;
	private static final int REAL_TERRAIN_PARTICLE_RESET_FRAMES =
		Math.max(12, Integer.getInteger("mattmc.dev.rustGalWorldMaterial.terrainParticleRealGameplayResetFrames", 48));
	private static final int REAL_TERRAIN_PARTICLE_EFFECTS_PER_FRAME =
		Math.max(1, Math.min(REAL_TERRAIN_PARTICLE_MATERIAL_COUNT, Integer.getInteger("mattmc.dev.rustGalWorldMaterial.terrainParticleRealGameplayEffectsPerFrame", REAL_TERRAIN_PARTICLE_MATERIAL_COUNT)));
	private static final Path STATUS_PATH = Path.of(System.getProperty("mattmc.dev.graphicsFrameBenchmark.status", "run/graphics_frame_benchmark.json"));
	private static final String WORKLOAD_COUNTER_DEFINITION_VERSION = "phase-family-v2";
	private static final int MAX_FRAME_TIMELINE_EVENTS =
		Math.max(1, Integer.getInteger("mattmc.dev.graphicsFrameBenchmark.maxTimelineEvents", 128));

	private static final ArrayDeque<OpenPhase> PHASE_STACK = new ArrayDeque<>();
	private static final Map<String, PhaseStats> EXCLUSIVE_PHASES = new LinkedHashMap<>();
	private static final Map<String, PhaseStats> NESTED_PHASES = new LinkedHashMap<>();
	private static final List<FrameTimelineEvent> FRAME_TIMELINE_EVENTS = new ArrayList<>();
	private static final Map<String, Integer> SUBMITTED_WORK_COUNTS = new LinkedHashMap<>();
	private static final Map<String, Integer> FALLING_BLOCK_ROUTE_COUNTS = new LinkedHashMap<>();
	private static final Map<String, Integer> MOVING_BLOCK_ROUTE_COUNTS = new LinkedHashMap<>();
	private static int movingBlockShellScanSamples;
	private static int movingBlockShellScanFallbackSamples;
	private static long movingBlockShellScanNanos;
	private static long movingBlockShellScanMaxNanos;
	private static int movingBlockShellScanChunks;
	private static int movingBlockShellScanBlockEntities;
	private static int movingBlockShellScanPistonsFound;
	private static int movingBlockShellScanPistonStatesExtracted;
	private static final List<Long> FRAME_NANOS = new ArrayList<>();
	private static boolean initialized;
	private static boolean complete;
	private static boolean failed;
	private static boolean stopIssued;
	private static boolean frameActive;
	private static boolean measurementFrame;
	private static boolean preMeasurementGcIssued;
	private static long beginFrameCalls;
	private static long activeBeginFrameCalls;
	private static long endFrameCalls;
	private static long endFrameInactiveReturns;
	private static long endFrameTerminalReturns;
	private static String lastFrameLifecycle = "not-entered";
	private static long initializationWaitFrames;
	private static long frameIndex;
	private static long settledFrameIndex = -1L;
	private static long measurementStartNanos = -1L;
	private static long measurementEndNanos = -1L;
	private static long firstSampleNanos = -1L;
	private static long lastSampleNanos = -1L;
	private static long currentFrameStartNanos = -1L;
	private static long lastMeasurementFrameStartNanos = -1L;
	private static long frameAllocatedBytesAtStart = -1L;
	private static long frameGcCountAtStart = -1L;
	private static long frameGcTimeAtStart = -1L;
	private static long readinessStartNanos = -1L;
	private static long producerWorkloadStartNanos = -1L;
	private static long producerWorkloadWaitFrames;
	private static long staticTerrainSteadyFrames;
	private static long staticTerrainLastActiveLayers = Long.MIN_VALUE;
	private static long staticTerrainLastActiveSectionAssets = Long.MIN_VALUE;
	private static long staticTerrainLastCachedLayers = Long.MIN_VALUE;
	private static long staticTerrainLastRegisteredMeshes = Long.MIN_VALUE;
	private static long staticTerrainLastAcceptedBuildOutputs = Long.MIN_VALUE;
	private static long staticTerrainLastAtlasGeneration = Long.MIN_VALUE;
	private static long staticTerrainLastTextureUpdates = Long.MIN_VALUE;
	private static long staticTerrainLastInvalidations = Long.MIN_VALUE;
	private static long staticTerrainLastFailedSubmissions = Long.MIN_VALUE;
	private static long staticTerrainWorldReadyNanos = -1L;
	private static long staticTerrainFirstBuildNanos = -1L;
	private static long staticTerrainFirstRegistrationNanos = -1L;
	private static long staticTerrainFirstUploadNanos = -1L;
	private static long staticTerrainFirstVisibleNanos = -1L;
	private static long staticTerrainLastMutationNanos = -1L;
	private static long staticTerrainQuiescenceStartNanos = -1L;
	private static long staticTerrainQuiescenceEndNanos = -1L;
	private static TerrainPerfSnapshot staticTerrainLastSnapshot;
	private static TerrainPerfSnapshot staticTerrainQuiescenceStartSnapshot;
	private static TerrainPerfSnapshot staticTerrainQuiescenceEndSnapshot;
	private static String staticTerrainLastChangingCounters = "not-sampled";
	private static String staticTerrainLastMutationSections = "";
	private static String staticTerrainQuiescenceClassification = "";
	private static long gcCountAtStart = -1L;
	private static long gcTimeAtStart = -1L;
	private static long gcCountAtEnd = -1L;
	private static long gcTimeAtEnd = -1L;
	private static long usedMemoryAtStart = -1L;
	private static long usedMemoryAtEnd = -1L;
	private static long threadAllocatedBytesAtStart = -1L;
	private static long threadAllocatedBytesAtEnd = -1L;
	private static int displayedFpsAtMeasurementStart = -1;
	private static int displayedFpsAtMeasurementEnd = -1;
	private static Vec3 initialPosition = Vec3.ZERO;
	private static float initialYaw;
	private static float initialPitch;
	private static String dimension = "missing";
	private static String failureReason = "";
	private static String lastReadinessBlocker = "not checked";
	private static String lastProducerWorkloadBlocker = "not requested";
	private static int originalArmorValueOverride = -1;
	private static float originalHealthOverride = Float.NaN;
	private static float originalMaxHealthOverride = Float.NaN;
	private static boolean armorOverrideApplied;
	private static boolean healthOverrideApplied;
	private static GameType originalGameMode;
	private static GameType originalPreviousGameMode;
	private static boolean gameModeOverrideApplied;
	private static BlockPos realTerrainParticleTarget;
	private static BlockPos[] realTerrainParticleTargets = new BlockPos[0];
	private static Direction realTerrainParticleDirection = Direction.NORTH;
	private static int realTerrainParticleMaterialIndex;
	private static long realTerrainParticleLastResetFrame = Long.MIN_VALUE;
	private static int realTerrainParticleSetupCount;
	private static int realTerrainParticleDriveCalls;
	private static int realTerrainParticleStartCalls;
	private static int realTerrainParticleContinueCalls;
	private static int realTerrainParticleBreakingEffects;
	private static int realTerrainParticleMaterialMask;
	private static String realTerrainParticleStatus = "inactive";
	private static String realTerrainParticleTargetText = "unset";
	private static String realTerrainParticleBlockType = "unset";
	private static boolean blockDisplayScenarioSetup;
	private static int blockDisplayScenarioEntityId = Integer.MIN_VALUE + 2048;
	private static String blockDisplayScenarioStatus = "inactive";
	private static String blockDisplayScenarioBlock = "unset";
	private static String blockDisplayScenarioPosition = "unset";
	private static int blockDisplayScenarioEntityCount;
	private static int blockDisplayScenarioDistinctBlockCount;
	private static String blockDisplayScenarioFingerprint = "unset";
	private static boolean fallingBlockScenarioSetup;
	private static String fallingBlockScenarioStatus = "inactive";
	private static String fallingBlockScenarioBlock = "unset";
	private static String fallingBlockScenarioPosition = "unset";
	private static String fallingBlockScenarioFingerprint = "unset";
	private static boolean pistonScenarioSetup;
	private static String pistonScenarioStatus = "inactive";
	private static String pistonScenarioBlock = "unset";
	private static String pistonScenarioPosition = "unset";
	private static String pistonScenarioFingerprint = "unset";
	private static BlockPos pistonScenarioPos;
	private static BlockState pistonScenarioMovedState;
	private static Direction pistonScenarioDirection;
	private static boolean pistonScenarioExtending;
	private static boolean pistonScenarioSourcePiston;
	private static boolean pistonScenarioClientBlockEntityPresent;
	private static boolean pistonScenarioServerBlockEntityPresent;
	private static int pistonScenarioReseedCount;
	private static final Map<String, Integer> TERRAIN_PARTICLE_ROUTE_COUNTS = new LinkedHashMap<>();
	private static final Map<String, Long> TERRAIN_PARTICLE_ROUTE_NANOS = new LinkedHashMap<>();

	private GraphicsFrameBenchmark() {
	}

	public static long currentFrameIndex() {
		return frameIndex;
	}

	public static boolean isActiveForDiagnostics() {
		return ENABLED && initialized && !complete && !failed;
	}

	/**
	 * A deterministic visual capture may have its screenshot receipt before the
	 * independent frame-sampling contract completes.  The capture lifecycle
	 * uses this to defer process shutdown, never to reduce or bypass the
	 * benchmark's requested sample window.
	 */
	public static boolean isAwaitingCompletion() {
		return ENABLED && !complete && !failed;
	}

	public static void beginFrame(Minecraft minecraft) {
		beginFrameCalls++;
		if (!ENABLED) {
			lastFrameLifecycle = "begin-disabled";
			return;
		}
		currentFrameStartNanos = System.nanoTime();
		if ((complete || failed) && STOP_AFTER_COMPLETE && !stopIssued
			&& !DeterministicCameraCapture.isAwaitingCompletion()) {
			lastFrameLifecycle = complete ? "begin-complete" : "begin-failed";
			stopIssued = true;
			minecraft.stop();
			return;
		}
		if (complete || failed) {
			lastFrameLifecycle = complete ? "begin-complete" : "begin-failed";
			return;
		}
		frameActive = ensureInitialized(minecraft) && !complete && !failed;
		measurementFrame = false;
		PHASE_STACK.clear();
		if (!frameActive) {
			lastFrameLifecycle = "begin-not-ready";
			return;
		}
		activeBeginFrameCalls++;
		lastFrameLifecycle = "begin-active";
		if ((beginFrameCalls % 60L) == 0L) {
			writeStatus(minecraft, "frame-lifecycle");
		}
		frameAllocatedBytesAtStart = currentThreadAllocatedBytes();
		frameGcCountAtStart = totalGcCount();
		frameGcTimeAtStart = totalGcTimeMillis();
		beginPhase("java.frame.render-production");
		holdPlayerStillAndApplyCameraPath(minecraft);
		if (!DeterministicCameraCapture.setupGameplayProducerScenarios(minecraft)) {
			lastProducerWorkloadBlocker = "waiting-for-gameplay-producer-fixture";
			return;
		}
		if (!producerWorkloadReady(minecraft)) {
			return;
		}
		if (settledFrameIndex < 0L) {
			if (frameIndex >= SETTLE_FRAMES) {
				settledFrameIndex = frameIndex;
				writeStatus(minecraft, "settled");
			} else if (frameIndex > MAX_SETTLE_FRAMES) {
				fail(minecraft, "timed out waiting for settled gameplay window");
			}
			return;
		}
		long framesAfterSettle = frameIndex - settledFrameIndex;
		if (
			GC_BEFORE_MEASUREMENT
				&& !preMeasurementGcIssued
				&& framesAfterSettle >= Math.max(0L, WARMUP_FRAMES - GC_BEFORE_MEASUREMENT_OFFSET_FRAMES)
				&& framesAfterSettle < WARMUP_FRAMES
		) {
			preMeasurementGcIssued = true;
			System.gc();
		}
		measurementFrame = framesAfterSettle >= WARMUP_FRAMES && FRAME_NANOS.size() < MEASURE_FRAMES;
		if (measurementFrame && FRAME_NANOS.isEmpty()) {
			measurementStartNanos = System.nanoTime();
			displayedFpsAtMeasurementStart = minecraft.getFps();
			gcCountAtStart = totalGcCount();
			gcTimeAtStart = totalGcTimeMillis();
			usedMemoryAtStart = usedMemoryBytes();
			threadAllocatedBytesAtStart = currentThreadAllocatedBytes();
			if (tracyAvailable()) {
				TracyCompat.message("MattMC graphics gameplay measurement start");
			}
		}
		if (measurementFrame) {
			if (lastMeasurementFrameStartNanos > 0L) {
				recordPhaseSample("benchmark.frame-start-interval", currentFrameStartNanos - lastMeasurementFrameStartNanos);
			}
			lastMeasurementFrameStartNanos = currentFrameStartNanos;
		}
		applyPositiveControlDelay();
	}

	public static void endFrame(Minecraft minecraft, long frameNanos) {
		endFrameCalls++;
		if (!ENABLED || !frameActive) {
			endFrameInactiveReturns++;
			lastFrameLifecycle = !ENABLED ? "end-disabled" : "end-inactive";
			return;
		}
		if (complete || failed) {
			endFrameTerminalReturns++;
			lastFrameLifecycle = complete ? "end-complete" : "end-failed";
			return;
		}
		if (measurementFrame) {
			FRAME_NANOS.add(frameNanos);
			recordFrameAllocationAndGcSamples();
			long sampleNanos = System.nanoTime();
			if (firstSampleNanos < 0L) {
				firstSampleNanos = sampleNanos;
			}
			lastSampleNanos = sampleNanos;
			measurementEndNanos = sampleNanos;
			displayedFpsAtMeasurementEnd = minecraft.getFps();
			gcCountAtEnd = totalGcCount();
			gcTimeAtEnd = totalGcTimeMillis();
			usedMemoryAtEnd = usedMemoryBytes();
			threadAllocatedBytesAtEnd = currentThreadAllocatedBytes();
		}
		endPhase("java.frame.render-production");
		if (tracyAvailable()) {
			TracyCompat.markFrame();
		}
		frameIndex++;
		if (FRAME_NANOS.size() >= MEASURE_FRAMES) {
			complete = true;
			if (tracyAvailable()) {
				TracyCompat.message("MattMC graphics gameplay measurement complete");
			}
			writeStatus(minecraft, "complete");
			restoreArmorOverride(minecraft);
			if (STOP_AFTER_COMPLETE && !stopIssued
				&& !DeterministicCameraCapture.isAwaitingCompletion()) {
				stopIssued = true;
				minecraft.stop();
			}
		} else if ((frameIndex % 60L) == 0L) {
			writeStatus(minecraft, measurementFrame ? "measuring" : "warming_or_settling");
		}
		frameActive = false;
		measurementFrame = false;
		lastFrameLifecycle = "end-completed";
		frameAllocatedBytesAtStart = -1L;
		frameGcCountAtStart = -1L;
		frameGcTimeAtStart = -1L;
		PHASE_STACK.clear();
	}

	private static void recordFrameAllocationAndGcSamples() {
		long allocatedBytesAtEnd = currentThreadAllocatedBytes();
		if (frameAllocatedBytesAtStart >= 0L && allocatedBytesAtEnd >= frameAllocatedBytesAtStart) {
			recordPhaseSample("java.alloc.render-thread-bytes", allocatedBytesAtEnd - frameAllocatedBytesAtStart);
		}
		long gcCountAtFrameEnd = totalGcCount();
		if (frameGcCountAtStart >= 0L && gcCountAtFrameEnd >= frameGcCountAtStart) {
			recordPhaseSample("java.gc.count", gcCountAtFrameEnd - frameGcCountAtStart);
		}
		long gcTimeAtFrameEnd = totalGcTimeMillis();
		if (frameGcTimeAtStart >= 0L && gcTimeAtFrameEnd >= frameGcTimeAtStart) {
			recordPhaseSample("java.gc.time-nanos", (gcTimeAtFrameEnd - frameGcTimeAtStart) * 1_000_000L);
		}
	}

	public static void beginPhase(String name) {
		if (!ENABLED || !frameActive) {
			return;
		}
		PHASE_STACK.push(new OpenPhase(name, System.nanoTime(), beginTracyZone(name)));
	}

	public static void endPhase(String name) {
		if (!ENABLED || !frameActive || PHASE_STACK.isEmpty()) {
			return;
		}
		long now = System.nanoTime();
		OpenPhase phase = PHASE_STACK.pop();
		phase.closeTracyZone();
		long inclusive = Math.max(0L, now - phase.startNanos());
		long exclusive = Math.max(0L, inclusive - phase.childNanos());
		if (!PHASE_STACK.isEmpty()) {
			OpenPhase parent = PHASE_STACK.pop();
			PHASE_STACK.push(parent.withAdditionalChild(inclusive));
		}
		if (measurementFrame) {
			String label = phase.name().equals(name) ? name : phase.name() + "/ended-as/" + name;
			NESTED_PHASES.computeIfAbsent(label, ignored -> new PhaseStats()).add(inclusive);
			EXCLUSIVE_PHASES.computeIfAbsent(label, ignored -> new PhaseStats()).add(exclusive);
		}
	}

	public static void recordPhaseSample(String name, long nanos) {
		if (!ENABLED || !frameActive || !measurementFrame || name == null || nanos < 0L) {
			return;
		}
		NESTED_PHASES.computeIfAbsent(name, ignored -> new PhaseStats()).add(nanos);
		EXCLUSIVE_PHASES.computeIfAbsent(name, ignored -> new PhaseStats()).add(nanos);
	}

	public static void recordRustWholeFrameTimeline(
		long correlationId,
		long rustFrameId,
		long submissionId,
		long acquiredImage,
		long presentedImage,
		long executeStartNanos,
		long acquireStartNanos,
		long acquireEndNanos,
		long submitStartNanos,
		long submitEndNanos,
		long presentStartNanos,
		long presentEndNanos,
		long guiSprites,
		long meshInstances,
		long meshDraws,
		long gpuFrameTotalNanos,
		long presentMode,
		long imagesInFlight,
		long availableFrameSlots
	) {
		if (!ENABLED || !frameActive || !measurementFrame || FRAME_TIMELINE_EVENTS.size() >= MAX_FRAME_TIMELINE_EVENTS) {
			return;
		}
		long frameStart = currentFrameStartNanos > 0L ? currentFrameStartNanos : executeStartNanos;
		FRAME_TIMELINE_EVENTS.add(new FrameTimelineEvent(
			frameIndex,
			correlationId,
			rustFrameId,
			submissionId,
			acquiredImage,
			presentedImage,
			relativeNanos(frameStart, executeStartNanos),
			relativeNanos(frameStart, acquireStartNanos),
			relativeNanos(frameStart, acquireEndNanos),
			relativeNanos(frameStart, submitStartNanos),
			relativeNanos(frameStart, submitEndNanos),
			relativeNanos(frameStart, presentStartNanos),
			relativeNanos(frameStart, presentEndNanos),
			Math.max(0L, acquireEndNanos - acquireStartNanos),
			Math.max(0L, submitEndNanos - submitStartNanos),
			Math.max(0L, presentEndNanos - presentStartNanos),
			guiSprites,
			meshInstances,
			meshDraws,
			gpuFrameTotalNanos,
			presentMode,
			imagesInFlight,
			availableFrameSlots
		));
	}

	public static void recordSubmittedWorkIdentity(String family, String identity) {
		if (!ENABLED || !initialized || complete || failed || family == null || identity == null) {
			return;
		}
		String normalizedFamily = family.trim();
		String normalizedIdentity = identity.trim();
		if (normalizedFamily.isEmpty() || normalizedIdentity.isEmpty()) {
			return;
		}
		SUBMITTED_WORK_COUNTS.merge(normalizedFamily, 1, Integer::sum);
	}

	/** Capture-only count used by deterministic settling when an explicit backend
	 * reports its completion on a different render-hook thread. */
	public static int submittedWorkCount(String family) {
		if (family == null) {
			return 0;
		}
		return SUBMITTED_WORK_COUNTS.getOrDefault(family.trim(), 0);
	}

	public static void recordFallingBlockRouteDecision(String route, BlockState blockState) {
		recordMovingBlockRouteDecision("falling-block", route, blockState);
	}

	public static void recordMovingBlockRouteDecision(String provenance, String route, BlockState blockState) {
		if (!ENABLED || !initialized || complete || failed || route == null) {
			return;
		}
		String normalizedProvenance = provenance == null ? "unknown" : provenance.trim();
		if (normalizedProvenance.isEmpty()) {
			normalizedProvenance = "unknown";
		}
		String normalizedRoute = route.trim();
		if (normalizedRoute.isEmpty()) {
			return;
		}
		if ("falling-block".equals(normalizedProvenance)) {
			FALLING_BLOCK_ROUTE_COUNTS.merge(normalizedRoute, 1, Integer::sum);
		}
		MOVING_BLOCK_ROUTE_COUNTS.merge(normalizedProvenance + ":" + normalizedRoute, 1, Integer::sum);
		recordSubmittedWorkIdentity(
			"moving-block-route",
			normalizedProvenance + ":" + normalizedRoute + ":" + blockName(blockState)
		);
	}

	public static void recordMovingBlockShellScan(
		String route,
		int visiblePistonStates,
		boolean fallbackUsed,
		int chunksScanned,
		int blockEntitiesInspected,
		int pistonEntitiesFound,
		int pistonStatesExtracted,
		long elapsedNanos
	) {
		if (!ENABLED || !initialized || complete || failed || route == null) {
			return;
		}
		movingBlockShellScanSamples++;
		if (fallbackUsed) {
			movingBlockShellScanFallbackSamples++;
		}
		movingBlockShellScanNanos += Math.max(0L, elapsedNanos);
		movingBlockShellScanMaxNanos = Math.max(movingBlockShellScanMaxNanos, Math.max(0L, elapsedNanos));
		movingBlockShellScanChunks += Math.max(0, chunksScanned);
		movingBlockShellScanBlockEntities += Math.max(0, blockEntitiesInspected);
		movingBlockShellScanPistonsFound += Math.max(0, pistonEntitiesFound);
		movingBlockShellScanPistonStatesExtracted += Math.max(0, pistonStatesExtracted);
		recordSubmittedWorkIdentity(
			"moving-block-shell-scan",
			route.trim() + ":visible=" + Math.max(0, visiblePistonStates) + ":fallback=" + fallbackUsed
		);
	}

	public static Zone beginTracyZone(String name) {
		if (!tracyAvailable()) {
			return null;
		}
		return TracyCompat.beginZone(name, false);
	}

	public static void closeTracyZone(Zone zone) {
		if (zone != null) {
			zone.close();
		}
	}

	public static void tracyMessage(String message) {
		if (tracyAvailable()) {
			TracyCompat.message(message);
		}
	}

	private static boolean tracyAvailable() {
		return TRACY_ENABLED && TracyCompat.isAvailable();
	}

	private static boolean ensureInitialized(Minecraft minecraft) {
		if (initialized) {
			dismissKnownGameplayScreen(minecraft);
			boolean ready = isBenchmarkWorldReady(minecraft);
			if (!ready) {
				recordInitializationBlocker(minecraft);
			} else {
				// A dismissed startup screen is historical context, not a continuing
				// readiness failure once the active world satisfies every gate.
				lastReadinessBlocker = "ready";
			}
			return ready;
		}
		dismissKnownGameplayScreen(minecraft);
		LocalPlayer player = minecraft.player;
		if (!isBenchmarkWorldReady(minecraft) || player == null) {
			recordInitializationBlocker(minecraft);
			return false;
		}
		initialized = true;
		lastReadinessBlocker = "ready";
		staticTerrainWorldReadyNanos = System.nanoTime();
		applyGameModeOverride(minecraft);
		applyArmorOverride(player);
		applyHealthOverride(player);
		initialPosition = new Vec3(CAMERA_X, CAMERA_Y, CAMERA_Z);
		initialYaw = CAMERA_YAW;
		initialPitch = CAMERA_PITCH;
		player.setPos(initialPosition);
			player.setYRot(initialYaw);
			player.setXRot(initialPitch);
				setupRealTerrainParticleGameplayBlock(minecraft, player);
				setupBlockDisplayScenario(minecraft, player);
				setupFallingBlockScenario(minecraft, player);
				setupPistonScenario(minecraft, player);
				dimension = minecraft.level.dimension().location().toString();
				writeStatus(minecraft, "initialized");
				return true;
		}

	private static void dismissKnownGameplayScreen(Minecraft minecraft) {
		disableVoxelMapWelcomeScreen();
		if (minecraft.level == null || minecraft.player == null || minecraft.getConnection() == null
			|| !minecraft.getConnection().isAcceptingMessages() || minecraft.getOverlay() != null || minecraft.screen == null) {
			return;
		}
		String screen = minecraft.screen.getClass().getSimpleName();
		if ("PauseScreen".equals(screen) || "GuiWelcomeScreen".equals(screen) || isStaleStartupScreen(minecraft)) {
			lastReadinessBlocker = "auto-dismissed screen=" + screen;
			minecraft.setScreen(null);
			writeStatus(minecraft, "dismissed_gameplay_screen");
		}
	}

	private static boolean isBenchmarkWorldReady(Minecraft minecraft) {
		return minecraft.level != null
			&& minecraft.player != null
			&& minecraft.getConnection() != null
			&& minecraft.getOverlay() == null
			&& (minecraft.screen == null || isStaleStartupScreen(minecraft))
			&& minecraft.level.getChunkSource().getLoadedChunksCount() > 0;
	}

	private static boolean isStaleStartupScreen(Minecraft minecraft) {
		if (minecraft.screen == null || minecraft.level == null || minecraft.player == null || minecraft.getConnection() == null || minecraft.getOverlay() != null) {
			return false;
		}
		String screen = minecraft.screen.getClass().getSimpleName();
		if ("LevelLoadingScreen".equals(screen)) {
			return minecraft.level.getChunkSource().getLoadedChunksCount() > 0;
		}
		if (!"GenericMessageScreen".equals(screen)) {
			return false;
		}
		String title = screenTitle(minecraft).toLowerCase(Locale.ROOT);
		if (minecraft.level.getChunkSource().getLoadedChunksCount() <= 0) {
			return false;
		}
		if (title.contains("downloading terrain") || title.contains("loading terrain") || title.contains("joining world")) {
			return true;
		}
		return title.contains("saving world") && initializationWaitFrames > 60L;
	}

	private static void disableVoxelMapWelcomeScreen() {
		try {
			var options = net.voxelmap.VoxelConstants.getVoxelMapInstance().getMapOptions();
			if (options.getOptionBooleanValue(net.voxelmap.gui.overridden.EnumOptionsMinimap.WELCOME_SCREEN)) {
				options.setOptionValue(net.voxelmap.gui.overridden.EnumOptionsMinimap.WELCOME_SCREEN);
			}
		} catch (Throwable ignored) {
			// Optional benchmark hygiene only; never let VoxelMap diagnostics affect gameplay startup.
		}
	}

	private static void recordInitializationBlocker(Minecraft minecraft) {
		long now = System.nanoTime();
		if (readinessStartNanos < 0L) {
			readinessStartNanos = now;
		}
		initializationWaitFrames++;
		lastReadinessBlocker = readinessSummary(minecraft);
		if (now - readinessStartNanos >= READINESS_TIMEOUT_NANOS) {
			fail(minecraft, "timed out waiting for gameplay entry: " + lastReadinessBlocker);
		} else if ((initializationWaitFrames % 60L) == 0L || initializationWaitFrames == 1L) {
			writeStatus(minecraft, "waiting_for_gameplay");
		}
	}

	private static boolean producerWorkloadReady(Minecraft minecraft) {
		List<String> missing = missingProducerWorkloads();
		if (missing.isEmpty()) {
			lastProducerWorkloadBlocker = "ready";
			return true;
		}
		long now = System.nanoTime();
		if (producerWorkloadStartNanos < 0L) {
			producerWorkloadStartNanos = now;
		}
		producerWorkloadWaitFrames++;
		lastProducerWorkloadBlocker = "missing=" + String.join(",", missing)
			+ ", blockDisplayStatus=" + blockDisplayScenarioStatus
			+ ", fallingBlockStatus=" + fallingBlockScenarioStatus
			+ ", pistonStatus=" + pistonScenarioStatus
			+ ", submitted=" + SUBMITTED_WORK_COUNTS
			+ ", fallingRoutes=" + FALLING_BLOCK_ROUTE_COUNTS
			+ ", movingRoutes=" + MOVING_BLOCK_ROUTE_COUNTS;
		if (now - producerWorkloadStartNanos >= READINESS_TIMEOUT_NANOS) {
			fail(minecraft, "timed out waiting for requested producer traversal: " + lastProducerWorkloadBlocker);
		} else if ((producerWorkloadWaitFrames % 60L) == 0L || producerWorkloadWaitFrames == 1L) {
			writeStatus(minecraft, "waiting_for_producer_workload");
		}
		return false;
	}

	private static List<String> missingProducerWorkloads() {
		List<String> missing = new ArrayList<>();
		if (REQUIRE_DH_EXECUTION && !submittedWorkObserved("distant-horizons")) {
			missing.add("distant-horizons");
		}
		if (scenarioRequiresProducerTraversal(BLOCK_DISPLAY_SCENARIO)
			&& !submittedWorkObserved("block-display")) {
			missing.add("block-display");
		}
		if (scenarioRequiresProducerTraversal(FALLING_BLOCK_SCENARIO)
			&& !submittedWorkObserved("falling-block")
			&& !routeObserved(FALLING_BLOCK_ROUTE_COUNTS)
			&& !routeObservedForProvenance("falling-block")) {
			missing.add("falling-block");
		}
		if (scenarioRequiresProducerTraversal(PISTON_SCENARIO)
			&& !submittedWorkObserved("piston")
			&& !routeObservedForProvenance("piston")) {
			missing.add("piston");
		}
		if (scenarioRequiresProducerTraversal(PRIMED_TNT_SCENARIO)
			&& !submittedWorkObserved("primed-tnt")
			&& !routeObservedForProvenance("primed-tnt")) {
			missing.add("primed-tnt");
		}
		if (scenarioRequiresProducerTraversal(ARROW_SCENARIO)
			&& RustGalWorldPrimitiveRenderer.arrowRouteDecisions().isEmpty()
			&& !routeObservedForProvenance("arrow")
			&& !submittedWorkObserved("arrow")) {
			missing.add("arrow");
		}
		if (scenarioRequiresProducerTraversal(ITEM_ENTITY_SCENARIO)
			&& RustGalWorldPrimitiveRenderer.itemEntityRouteDecisions().isEmpty()) {
			missing.add("item-entity");
		}
		if (scenarioRequiresProducerTraversal(EXPERIENCE_ORB_SCENARIO)
			&& RustGalWorldPrimitiveRenderer.experienceOrbRouteDecisions().isEmpty()
			&& RustGalWorldPrimitiveRenderer.experienceOrbExecutionDiagnostics().isEmpty()) {
			missing.add("experience-orb");
		}
		if (scenarioRequiresProducerTraversal(MODEL_MESH_SCENARIO)
			&& RustGalWorldPrimitiveRenderer.modelMeshRouteDecisions().isEmpty()
			&& !routeObservedForProvenance("model")
			&& !("end-crystal".equals(MODEL_MESH_SCENARIO) && submittedWorkObserved("model-mesh"))
			&& !("conduit".equals(MODEL_MESH_SCENARIO) && submittedWorkObserved("model-part"))) {
			missing.add("model-mesh");
		}
		if ("end-crystal".equals(MODEL_MESH_SCENARIO)
			&& scenarioRequiresProducerTraversal(MODEL_MESH_SCENARIO)
			&& !submittedWorkObserved("crystal-beam")) {
			missing.add("crystal-beam");
		}
		if (scenarioRequiresProducerTraversal(STATIC_TERRAIN_SCENARIO)
			&& !submittedWorkObserved("static-terrain")) {
			missing.add("static-terrain");
		}
		if ("steady-state-performance".equals(STATIC_TERRAIN_SCENARIO)
			&& !staticTerrainSteadyStateReady()) {
			missing.add("static-terrain-steady-cache");
		}
		return missing;
	}

	private static boolean staticTerrainSteadyStateReady() {
		TerrainPerfSnapshot snapshot = terrainPerfSnapshot();
		updateStaticTerrainLoadMarkers(snapshot);
		String fault = System.getProperty("mattmc.dev.rustGalStaticTerrain.fault", "").trim().toLowerCase(Locale.ROOT);
		if ("steady-state-upload-detected".equals(fault)
			|| "visibility-fingerprint-unstable".equals(fault)
			|| "rebuild-loop-detected".equals(fault)
			|| "cache-eviction-loop".equals(fault)
			|| "atlas-generation-churn".equals(fault)
			|| "identical-mesh-reregistered".equals(fault)
			|| "terrain-generation-never-quiesced".equals(fault)) {
			staticTerrainQuiescenceClassification = fault;
			staticTerrainLastChangingCounters = "fault-injection=" + fault;
			staticTerrainLastMutationSections = latestTerrainMutationSections(snapshot.diagnostics().recentEvents());
			staticTerrainLastSnapshot = snapshot;
			staticTerrainSteadyFrames = 0L;
			return false;
		}
		boolean unchanged = staticTerrainLastSnapshot != null && snapshot.quiescenceKeyEquals(staticTerrainLastSnapshot);
		String changed = staticTerrainLastSnapshot == null ? "initial-snapshot" : snapshot.changedCounters(staticTerrainLastSnapshot);
		staticTerrainLastSnapshot = snapshot;
		staticTerrainLastActiveLayers = snapshot.activeTerrainLayers();
		staticTerrainLastActiveSectionAssets = snapshot.activeSectionAssets();
		staticTerrainLastCachedLayers = snapshot.cachedLayerAssets();
		staticTerrainLastRegisteredMeshes = snapshot.registeredMeshes();
		staticTerrainLastAcceptedBuildOutputs = snapshot.acceptedBuildOutputs();
		staticTerrainLastAtlasGeneration = snapshot.atlasGeneration();
		staticTerrainLastTextureUpdates = snapshot.texturePayloadUpdates();
		staticTerrainLastInvalidations = snapshot.invalidations();
		staticTerrainLastFailedSubmissions = snapshot.failedLayerSubmissions();
		if (snapshot.mutationCountersNonZero() && !unchanged) {
			staticTerrainLastMutationNanos = System.nanoTime();
			staticTerrainLastChangingCounters = changed;
			staticTerrainLastMutationSections = latestTerrainMutationSections(snapshot.diagnostics().recentEvents());
			staticTerrainQuiescenceClassification = classifyTerrainChurn(changed);
		}
		if (unchanged && snapshot.readyForSteadyState()) {
			if (staticTerrainSteadyFrames == 0L) {
				staticTerrainQuiescenceStartNanos = System.nanoTime();
				staticTerrainQuiescenceStartSnapshot = snapshot;
			}
			staticTerrainSteadyFrames++;
			if (staticTerrainSteadyFrames >= STATIC_TERRAIN_STEADY_FRAMES) {
				if (staticTerrainQuiescenceEndNanos < 0L) {
					staticTerrainQuiescenceEndNanos = System.nanoTime();
					staticTerrainQuiescenceEndSnapshot = snapshot;
				}
				staticTerrainQuiescenceClassification = "quiescent";
				staticTerrainLastChangingCounters = "";
			} else {
				staticTerrainQuiescenceEndNanos = System.nanoTime();
				staticTerrainQuiescenceEndSnapshot = snapshot;
			}
		} else {
			staticTerrainSteadyFrames = 0L;
			staticTerrainQuiescenceStartNanos = -1L;
			staticTerrainQuiescenceEndNanos = -1L;
			staticTerrainQuiescenceStartSnapshot = null;
			staticTerrainQuiescenceEndSnapshot = null;
			if (!snapshot.readyForSteadyState()) {
				staticTerrainQuiescenceClassification = classifyTerrainNotReady(snapshot);
				staticTerrainLastChangingCounters = changed + ";notReady=" + staticTerrainQuiescenceClassification;
				staticTerrainLastMutationSections = latestTerrainMutationSections(snapshot.diagnostics().recentEvents());
			}
		}
		return staticTerrainSteadyFrames >= STATIC_TERRAIN_STEADY_FRAMES;
	}

	private static boolean staticTerrainScenarioEnabled() {
		return STATIC_TERRAIN_SCENARIO != null && !STATIC_TERRAIN_SCENARIO.isBlank();
	}

	private static TerrainPerfSnapshot terrainPerfSnapshot() {
		RustGalTerrainRenderer.TerrainDiagnostics diagnostics = RustGalTerrainRenderer.diagnosticsSnapshot();
		RustGalWorldPrimitiveRenderer.WorldMeshAssetMetrics meshMetrics = RustGalWorldPrimitiveRenderer.worldMeshAssetMetrics();
		Minecraft minecraft = Minecraft.getInstance();
		int loadedChunks = minecraft.level == null ? -1 : minecraft.level.getChunkSource().getLoadedChunksCount();
		int renderDistance = minecraft.options == null ? -1 : minecraft.options.getEffectiveRenderDistance();
		long cameraSignature = 0L;
		if (minecraft.player != null) {
			cameraSignature = 31L * Float.floatToIntBits(minecraft.player.getYRot()) + Float.floatToIntBits(minecraft.player.getXRot());
			cameraSignature = 31L * cameraSignature + Double.doubleToLongBits(minecraft.player.getX());
			cameraSignature = 31L * cameraSignature + Double.doubleToLongBits(minecraft.player.getY());
			cameraSignature = 31L * cameraSignature + Double.doubleToLongBits(minecraft.player.getZ());
		}
		return new TerrainPerfSnapshot(
			diagnostics,
			meshMetrics,
			latestVisibleFingerprint(diagnostics.recentEvents()),
			loadedChunks,
			renderDistance,
			cameraSignature
		);
	}

	private static long latestVisibleFingerprint(List<RustGalTerrainRenderer.TerrainDiagnosticEvent> events) {
		long latestFrame = Long.MIN_VALUE;
		for (RustGalTerrainRenderer.TerrainDiagnosticEvent event : events) {
			if ("visible-submit".equals(event.reason())) {
				latestFrame = Math.max(latestFrame, event.gameplayFrameId());
			}
		}
		if (latestFrame == Long.MIN_VALUE) {
			return 0L;
		}
		long hash = 0xcbf29ce484222325L;
		int count = 0;
		for (RustGalTerrainRenderer.TerrainDiagnosticEvent event : events) {
			if ("visible-submit".equals(event.reason()) && event.gameplayFrameId() == latestFrame) {
				hash = fnv64Long(hash, event.sectionPos());
				hash = fnv64Long(hash, event.meshKey());
				hash = fnv64Long(hash, event.meshGeneration());
				hash = fnv64Long(hash, event.contentHash());
				hash = fnv64String(hash, event.layer());
				count++;
			}
		}
		return count == 0 ? 0L : fnv64Long(hash, count);
	}

	private static void updateStaticTerrainLoadMarkers(TerrainPerfSnapshot snapshot) {
		long now = System.nanoTime();
		if (staticTerrainWorldReadyNanos < 0L) {
			staticTerrainWorldReadyNanos = now;
		}
		if (staticTerrainFirstBuildNanos < 0L && snapshot.acceptedBuildOutputs() > 0L) {
			staticTerrainFirstBuildNanos = now;
		}
		if (staticTerrainFirstRegistrationNanos < 0L && snapshot.registeredMeshes() > 0L) {
			staticTerrainFirstRegistrationNanos = now;
		}
		if (
			staticTerrainFirstUploadNanos < 0L
				&& snapshot.registeredMeshes() > 0L
				&& snapshot.meshMetrics().payloadCount() > 0L
				&& snapshot.meshMetrics().uploadedGeneration() >= snapshot.meshMetrics().generation()
		) {
			staticTerrainFirstUploadNanos = now;
		}
		if (staticTerrainFirstVisibleNanos < 0L && snapshot.visibleFingerprint() != 0L) {
			staticTerrainFirstVisibleNanos = now;
		}
	}

	private static String classifyTerrainChurn(String changedCounters) {
		if (changedCounters.contains("atlasGeneration") || changedCounters.contains("texturePayloadUpdates")) {
			return "atlas_generation_churn";
		}
		if (changedCounters.contains("worldMeshGeneration") || changedCounters.contains("payloadCount") || changedCounters.contains("payloadBytes")) {
			return "steady_state_upload_detected";
		}
		if (changedCounters.contains("acceptedBuildOutputs") || changedCounters.contains("registeredMeshes")) {
			return "rebuild_loop_detected";
		}
		if (changedCounters.contains("cachedLayerAssets") || changedCounters.contains("activeTerrainLayers") || changedCounters.contains("removedLayers")) {
			return "cache_eviction_loop";
		}
		if (changedCounters.contains("visibleFingerprint")) {
			return "visibility_fingerprint_unstable";
		}
		return "terrain_generation_never_quiesced";
	}

	private static String classifyTerrainNotReady(TerrainPerfSnapshot snapshot) {
		if (snapshot.failedLayerSubmissions() > 0L) {
			return "stale_terrain_submission";
		}
		if (snapshot.meshMetrics().dirtyMeshes() > 0 || snapshot.meshMetrics().dirtyTextures() > 0 || snapshot.meshMetrics().pendingInstances() > 0) {
			return "pending_rust_asset_updates";
		}
		if (snapshot.activeTerrainLayers() <= 0 || snapshot.activeSectionAssets() <= 0 || snapshot.visibleFingerprint() == 0L) {
			return "terrain_visibility_not_ready";
		}
		return "terrain_generation_never_quiesced";
	}

	private static String latestTerrainMutationSections(List<RustGalTerrainRenderer.TerrainDiagnosticEvent> events) {
		StringBuilder builder = new StringBuilder();
		int count = 0;
		for (int i = events.size() - 1; i >= 0 && count < 8; i--) {
			RustGalTerrainRenderer.TerrainDiagnosticEvent event = events.get(i);
			String reason = event.reason();
			if ("visible-submit".equals(reason) || "executed-submit".equals(reason)) {
				continue;
			}
			if (count++ > 0) {
				builder.append(';');
			}
			builder.append(reason)
				.append("@section=").append(event.sectionPos())
				.append(":layer=").append(event.layer())
				.append(":mesh=").append(event.meshGeneration())
				.append(":content=").append(event.contentHash());
		}
		return builder.toString();
	}

	private static long fnv64String(long hash, String value) {
		if (value == null) {
			return fnv64Long(hash, 0L);
		}
		for (int i = 0; i < value.length(); i++) {
			hash ^= value.charAt(i);
			hash *= 0x100000001b3L;
		}
		return hash;
	}

	private static long fnv64Long(long hash, long value) {
		for (int shift = 0; shift < 64; shift += 8) {
			hash ^= (value >>> shift) & 0xffL;
			hash *= 0x100000001b3L;
		}
		return hash;
	}

	private static boolean scenarioRequiresProducerTraversal(String scenario) {
		if (scenario == null || scenario.isBlank()) {
			return false;
		}
		String normalized = scenario.trim().toLowerCase(Locale.ROOT);
		return !normalized.equals("hidden") && !normalized.equals("completed") && !normalized.equals("removed");
	}

	private static boolean submittedWorkObserved(String family) {
		return SUBMITTED_WORK_COUNTS.getOrDefault(family, 0) > 0;
	}

	private static boolean routeObserved(Map<String, Integer> routes) {
		for (int count : routes.values()) {
			if (count > 0) {
				return true;
			}
		}
		return false;
	}

	private static boolean routeObservedForProvenance(String provenance) {
		String prefix = provenance + ":";
		for (Map.Entry<String, Integer> entry : MOVING_BLOCK_ROUTE_COUNTS.entrySet()) {
			if (entry.getKey().startsWith(prefix) && entry.getValue() > 0) {
				return true;
			}
		}
		return false;
	}

	private static String readinessSummary(Minecraft minecraft) {
		String screen = minecraft.screen == null ? "none" : minecraft.screen.getClass().getSimpleName();
		String overlay = minecraft.getOverlay() == null ? "none" : minecraft.getOverlay().getClass().getSimpleName();
		int loadedChunks = minecraft.level == null ? -1 : minecraft.level.getChunkSource().getLoadedChunksCount();
		return "level=" + (minecraft.level != null)
			+ ", player=" + (minecraft.player != null)
			+ ", connection=" + (minecraft.getConnection() != null)
			+ ", screen=" + screen
			+ ", screenTitle=" + screenTitle(minecraft)
			+ ", overlay=" + overlay
			+ ", loadedChunks=" + loadedChunks
			+ ", staleStartupScreen=" + isStaleStartupScreen(minecraft);
	}

	private static void applyPositiveControlDelay() {
		if (!measurementFrame || POSITIVE_CONTROL_DELAY_NANOS <= 0L) {
			return;
		}
		long deadline = System.nanoTime() + POSITIVE_CONTROL_DELAY_NANOS;
		while (true) {
			long remaining = deadline - System.nanoTime();
			if (remaining <= 0L) {
				return;
			}
			try {
				TimeUnit.NANOSECONDS.sleep(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(1L)));
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	private static String screenTitle(Minecraft minecraft) {
		return minecraft.screen == null ? "none" : minecraft.screen.getTitle().getString();
	}

	private static void holdPlayerStillAndApplyCameraPath(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}
		applyArmorOverride(player);
		applyHealthOverride(player);
		applyGameModeOverride(minecraft);
		player.input.keyPresses = Input.EMPTY;
		player.xxa = 0.0F;
		player.zza = 0.0F;
		player.setSprinting(false);
			player.setShiftKeyDown(false);
			player.setDeltaMovement(Vec3.ZERO);
			player.setPos(initialPosition);
			double period = Math.max(1.0, WARMUP_FRAMES + MEASURE_FRAMES);
			float yaw = REAL_TERRAIN_PARTICLE_GAMEPLAY || staticTerrainScenarioEnabled()
				? initialYaw
				: initialYaw + (float)Math.sin((frameIndex / period) * Math.PI * 2.0) * YAW_DELTA;
			player.setYRot(yaw);
			player.setXRot(initialPitch);
			player.yRotO = yaw;
			player.xRotO = initialPitch;
			player.yHeadRot = yaw;
			player.yHeadRotO = yaw;
			player.yBodyRot = yaw;
			player.yBodyRotO = yaw;
			driveRealTerrainParticleGameplay(minecraft, player);
			maintainPistonScenario(minecraft);
		}

	private static void setupRealTerrainParticleGameplayBlock(Minecraft minecraft, LocalPlayer player) {
		if (!REAL_TERRAIN_PARTICLE_GAMEPLAY || minecraft.level == null || player == null) {
			return;
		}
		Vec3 eye = player.getEyePosition(1.0F);
		Vec3 look = player.getLookAngle();
		BlockPos center = BlockPos.containing(eye.add(look.scale(2.6)));
		realTerrainParticleDirection = player.getDirection().getOpposite();
		Direction rowDirection = player.getDirection().getClockWise();
		BlockPos[] targets = new BlockPos[terrainParticleBenchmarkMaterialCount()];
		int middle = terrainParticleBenchmarkMaterialCount() / 2;
		for (int i = 0; i < targets.length; i++) {
			targets[i] = center.relative(rowDirection, i - middle);
			placeRealTerrainParticleBlock(minecraft, targets[i], terrainParticleBenchmarkState(i));
		}
		realTerrainParticleTargets = targets;
		realTerrainParticleTarget = targets[0];
		realTerrainParticleTargetText = terrainParticleTargetsText();
		realTerrainParticleStatus = "setup-fixed-material-row";
	}

	private static void setupBlockDisplayScenario(Minecraft minecraft, LocalPlayer player) {
		if (blockDisplayScenarioSetup) {
			return;
		}
		blockDisplayScenarioSetup = true;
		if (BLOCK_DISPLAY_SCENARIO.isEmpty()) {
			blockDisplayScenarioStatus = "inactive";
			return;
		}
		if ("hidden".equals(BLOCK_DISPLAY_SCENARIO)) {
			blockDisplayScenarioStatus = "hidden";
			return;
		}
		if (minecraft.level == null) {
			blockDisplayScenarioStatus = "missing-client-level";
			return;
		}
		List<BlockState> states = blockDisplayBenchmarkStates();
		if (states.isEmpty()) {
			blockDisplayScenarioStatus = "missing-states";
			return;
		}
		Vec3 forward = player.getLookAngle();
		if (forward.lengthSqr() < 0.0001) {
			forward = new Vec3(0.0, 0.0, 1.0);
		}
		Vec3 normalizedForward = forward.normalize();
		Vec3 right = new Vec3(-normalizedForward.z, 0.0, normalizedForward.x);
		if (right.lengthSqr() < 0.0001) {
			right = new Vec3(1.0, 0.0, 0.0);
		} else {
			right = right.normalize();
		}
		int entityCount = blockDisplayBenchmarkInstanceCount(states.size());
		int columns = Math.max(1, (int)Math.ceil(Math.sqrt(entityCount)));
		int rows = Math.max(1, (int)Math.ceil(entityCount / (double)columns));
		double spacing = 1.35;
		Vec3 center = player.position()
			.add(normalizedForward.scale(4.5))
			.add(0.0, rows <= 1 ? 0.0 : 0.7, 0.0);
		Set<String> blockNames = new LinkedHashSet<>();
		StringBuilder fingerprint = new StringBuilder(BLOCK_DISPLAY_WORKLOAD.isEmpty() ? "single" : BLOCK_DISPLAY_WORKLOAD);
		for (int i = 0; i < entityCount; i++) {
			BlockState state = states.get(i % states.size());
			int column = i % columns;
			int row = i / columns;
			double horizontal = (column - (columns - 1) * 0.5) * spacing;
			double vertical = ((rows - 1) * 0.5 - row) * spacing;
			Vec3 position = center.add(right.scale(horizontal)).add(0.0, vertical, 0.0).add(-0.5, -0.5, -0.5);
			Display.BlockDisplay display = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, minecraft.level);
			display.setId(blockDisplayScenarioEntityId++);
			display.setPos(position);
			display.setBlockState(state);
			display.setViewRange(32.0F);
			display.setWidth(2.0F);
			display.setHeight(2.0F);
			minecraft.level.addEntity(display);
			String blockName = blockName(state);
			blockNames.add(blockName);
			fingerprint.append('|').append(i).append(':').append(blockName);
			if (i == 0) {
				blockDisplayScenarioBlock = blockName;
				blockDisplayScenarioPosition = String.format(Locale.ROOT, "%.3f,%.3f,%.3f", position.x, position.y, position.z);
			}
		}
		blockDisplayScenarioStatus = "spawned";
		blockDisplayScenarioEntityCount = entityCount;
		blockDisplayScenarioDistinctBlockCount = blockNames.size();
		blockDisplayScenarioFingerprint = fingerprint.toString();
	}

	private static int blockDisplayBenchmarkInstanceCount(int distinctStateCount) {
		if (BLOCK_DISPLAY_INSTANCE_COUNT > 0) {
			return BLOCK_DISPLAY_INSTANCE_COUNT;
		}
		return switch (BLOCK_DISPLAY_WORKLOAD) {
			case "performance" -> 30;
			case "scale-one-mesh" -> 96;
			case "scale-mixed-meshes" -> Math.max(96, distinctStateCount * 16);
			default -> 1;
		};
	}

	private static List<BlockState> blockDisplayBenchmarkStates() {
		return switch (BLOCK_DISPLAY_WORKLOAD) {
			case "performance", "scale-mixed-meshes" -> List.of(
				Blocks.STONE.defaultBlockState(),
				Blocks.FURNACE.defaultBlockState(),
				Blocks.OAK_LEAVES.defaultBlockState(),
				Blocks.OAK_STAIRS.defaultBlockState(),
				Blocks.WHITE_WOOL.defaultBlockState()
			);
			case "scale-one-mesh" -> List.of(blockDisplayBenchmarkState());
			default -> List.of(blockDisplayBenchmarkState());
		};
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
		if (fallingBlockScenarioSetup) {
			return;
		}
		fallingBlockScenarioSetup = true;
		if (FALLING_BLOCK_SCENARIO.isEmpty()) {
			fallingBlockScenarioStatus = "inactive";
			return;
		}
		if ("hidden".equals(FALLING_BLOCK_SCENARIO)) {
			fallingBlockScenarioStatus = "hidden";
			return;
		}
		if (minecraft.level == null || minecraft.getSingleplayerServer() == null) {
			fallingBlockScenarioStatus = "missing-level";
			return;
		}
		ServerLevel serverLevel = minecraft.getSingleplayerServer().getLevel(minecraft.level.dimension());
		if (serverLevel == null) {
			fallingBlockScenarioStatus = "missing-server-level";
			return;
		}
		BlockState state = fallingBlockBenchmarkState();
		Vec3 forward = player.getLookAngle();
		if (forward.lengthSqr() < 0.0001) {
			forward = new Vec3(0.0, 0.0, 1.0);
		}
		int fallHeight = Math.max(4, Integer.getInteger("mattmc.dev.rustGalWorldMesh.fallingBlockFallHeight", 6));
		BlockPos origin = BlockPos.containing(player.getEyePosition().add(forward.normalize().scale(4.0)).add(0.0, 2.0, 0.0));
		for (int i = 0; i < FALLING_BLOCK_COUNT; i++) {
			BlockPos pos = origin.offset(i, 0, 0);
			prepareFallingBlockColumn(serverLevel, minecraft.level, pos, fallHeight);
			serverLevel.setBlock(pos, state, 2);
			minecraft.level.setBlock(pos, state, 2);
			FallingBlockEntity serverEntity = FallingBlockEntity.fall(serverLevel, pos, state);
			serverEntity.setDeltaMovement(0.0, -0.02, 0.0);
			serverEntity.dropItem = false;
			minecraft.level.setBlock(pos, state.getFluidState().createLegacyBlock(), 3);
		}
		fallingBlockScenarioStatus = "spawned";
		fallingBlockScenarioBlock = blockName(state);
		fallingBlockScenarioPosition = origin.toShortString();
		fallingBlockScenarioFingerprint = FALLING_BLOCK_SCENARIO + "|count=" + FALLING_BLOCK_COUNT + "|block=" + fallingBlockScenarioBlock;
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
		if (pistonScenarioSetup) {
			return;
		}
		pistonScenarioSetup = true;
		if (PISTON_SCENARIO.isEmpty()) {
			pistonScenarioStatus = "inactive";
			return;
		}
		if ("hidden".equals(PISTON_SCENARIO)) {
			pistonScenarioStatus = "hidden";
			return;
		}
		if (minecraft.level == null || minecraft.getSingleplayerServer() == null) {
			pistonScenarioStatus = "missing-level";
			return;
		}
		ServerLevel serverLevel = minecraft.getSingleplayerServer().getLevel(minecraft.level.dimension());
		if (serverLevel == null) {
			pistonScenarioStatus = "missing-server-level";
			return;
		}
		BlockState movedState = pistonMovedState();
		Direction direction = pistonDirection();
		Vec3 forward = player.getLookAngle();
		if (forward.lengthSqr() < 0.0001) {
			forward = new Vec3(0.0, 0.0, 1.0);
		}
		BlockPos origin = BlockPos.containing(player.getEyePosition().add(forward.normalize().scale(4.0)).add(0.0, -0.25, 0.0));
		for (int i = 0; i < PISTON_COUNT; i++) {
			BlockPos pos = origin.offset(i, 0, 0);
			clearPistonScenarioSpace(serverLevel, minecraft.level, pos);
			seedMovingPiston(serverLevel, minecraft.level, pos, movedState, direction, pistonExtending(), pistonSourcePiston());
		}
		pistonScenarioPos = origin;
		pistonScenarioMovedState = movedState;
		pistonScenarioDirection = direction;
		pistonScenarioExtending = pistonExtending();
		pistonScenarioSourcePiston = pistonSourcePiston();
		pistonScenarioClientBlockEntityPresent = minecraft.level.getBlockEntity(origin) instanceof net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
		pistonScenarioServerBlockEntityPresent = serverLevel.getBlockEntity(origin) instanceof net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
		pistonScenarioStatus = "spawned";
		pistonScenarioBlock = blockName(movedState);
		pistonScenarioPosition = origin.toShortString();
		pistonScenarioFingerprint = PISTON_SCENARIO + "|count=" + PISTON_COUNT + "|block=" + pistonScenarioBlock;
	}

	private static void maintainPistonScenario(Minecraft minecraft) {
		if (PISTON_SCENARIO.isEmpty()
			|| "hidden".equals(PISTON_SCENARIO)
			|| !pistonScenarioSetup
			|| complete
			|| failed
			|| pistonScenarioPos == null
			|| pistonScenarioMovedState == null
			|| pistonScenarioDirection == null
			|| minecraft.level == null
			|| minecraft.getSingleplayerServer() == null) {
			return;
		}
		ServerLevel serverLevel = minecraft.getSingleplayerServer().getLevel(minecraft.level.dimension());
		if (serverLevel == null) {
			pistonScenarioStatus = "maintain-missing-server-level";
			return;
		}
		boolean clientPresent = minecraft.level.getBlockEntity(pistonScenarioPos) instanceof net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
		boolean serverPresent = serverLevel.getBlockEntity(pistonScenarioPos) instanceof net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
		pistonScenarioClientBlockEntityPresent = clientPresent;
		pistonScenarioServerBlockEntityPresent = serverPresent;
		if (clientPresent) {
			return;
		}
		clearPistonScenarioSpace(serverLevel, minecraft.level, pistonScenarioPos);
		seedMovingPiston(
			serverLevel,
			minecraft.level,
			pistonScenarioPos,
			pistonScenarioMovedState,
			pistonScenarioDirection,
			pistonScenarioExtending,
			pistonScenarioSourcePiston
		);
		pistonScenarioReseedCount++;
		pistonScenarioStatus = "reseeded";
		pistonScenarioClientBlockEntityPresent = minecraft.level.getBlockEntity(pistonScenarioPos) instanceof net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
		pistonScenarioServerBlockEntityPresent = serverLevel.getBlockEntity(pistonScenarioPos) instanceof net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
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

	private static void seedMovingPiston(ServerLevel serverLevel, net.minecraft.client.multiplayer.ClientLevel clientLevel, BlockPos pos, BlockState movedState, Direction direction, boolean extending, boolean sourcePiston) {
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

	private static void clearPistonScenarioSpace(ServerLevel serverLevel, net.minecraft.client.multiplayer.ClientLevel clientLevel, BlockPos center) {
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

	private static String blockDisplayRouteControl() {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldBlockDisplay.disabled")) {
			return "disabled";
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldBlockDisplay.legacyControl")) {
			return "legacy";
		}
		return "rust";
	}

	private static String fallingBlockRouteControl() {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldFallingBlock.disabled")) {
			return "disabled";
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldFallingBlock.legacyControl")) {
			return "legacy";
		}
		return "rust";
	}

	private static String pistonRouteControl() {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldPiston.disabled")) {
			return "disabled";
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldPiston.legacyControl")) {
			return "legacy";
		}
		return "rust";
	}

	private static void driveRealTerrainParticleGameplay(Minecraft minecraft, LocalPlayer player) {
		if (!REAL_TERRAIN_PARTICLE_GAMEPLAY || minecraft.level == null || minecraft.gameMode == null || player == null || player.isSpectator()) {
			realTerrainParticleStatus = REAL_TERRAIN_PARTICLE_GAMEPLAY ? "inactive" : "disabled";
			return;
		}
		if (realTerrainParticleTargets.length != terrainParticleBenchmarkMaterialCount()) {
			setupRealTerrainParticleGameplayBlock(minecraft, player);
		}
		if (realTerrainParticleTargets.length != terrainParticleBenchmarkMaterialCount()) {
			realTerrainParticleStatus = "missing-target";
			return;
		}
		realTerrainParticleDriveCalls++;
		if (realTerrainParticleLastResetFrame == Long.MIN_VALUE || frameIndex - realTerrainParticleLastResetFrame >= REAL_TERRAIN_PARTICLE_RESET_FRAMES) {
			for (int i = 0; i < realTerrainParticleTargets.length; i++) {
				placeRealTerrainParticleBlock(minecraft, realTerrainParticleTargets[i], terrainParticleBenchmarkState(i));
			}
			minecraft.gameMode.stopDestroyBlock();
			realTerrainParticleLastResetFrame = frameIndex;
			realTerrainParticleMaterialIndex = (realTerrainParticleMaterialIndex + 1) % terrainParticleBenchmarkMaterialCount();
			realTerrainParticleTarget = realTerrainParticleTargets[realTerrainParticleMaterialIndex];
			minecraft.gameMode.startDestroyBlock(realTerrainParticleTarget, realTerrainParticleDirection);
			realTerrainParticleStartCalls++;
		}
		BlockState state = minecraft.level.getBlockState(realTerrainParticleTarget);
		if (state.isAir()) {
			placeRealTerrainParticleBlock(minecraft, realTerrainParticleTarget, terrainParticleBenchmarkState(realTerrainParticleMaterialIndex));
			state = minecraft.level.getBlockState(realTerrainParticleTarget);
		}
		realTerrainParticleBlockType = blockName(state);
		realTerrainParticleTargetText = terrainParticleTargetsText();
		if (realTerrainParticleStartCalls == 0) {
			minecraft.gameMode.startDestroyBlock(realTerrainParticleTarget, realTerrainParticleDirection);
			realTerrainParticleStartCalls++;
		}
		minecraft.gameMode.continueDestroyBlock(realTerrainParticleTarget, realTerrainParticleDirection);
		realTerrainParticleContinueCalls++;
		for (int i = 0; i < REAL_TERRAIN_PARTICLE_EFFECTS_PER_FRAME; i++) {
			int materialIndex = (int)Math.floorMod(realTerrainParticleDriveCalls + i, terrainParticleBenchmarkMaterialCount());
			BlockPos effectTarget = realTerrainParticleTargets[materialIndex];
			BlockState effectState = minecraft.level.getBlockState(effectTarget);
			if (effectState.isAir()) {
				placeRealTerrainParticleBlock(minecraft, effectTarget, terrainParticleBenchmarkState(materialIndex));
				effectState = minecraft.level.getBlockState(effectTarget);
			}
			realTerrainParticleMaterialMask |= 1 << materialIndex;
			minecraft.level.addBreakingBlockEffect(effectTarget, realTerrainParticleDirection);
			realTerrainParticleBreakingEffects++;
		}
		player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
		realTerrainParticleStatus = "fixed-frame-block-hit-effects";
	}

	private static void placeRealTerrainParticleBlock(Minecraft minecraft, BlockPos target, BlockState state) {
		if (minecraft.level == null || target == null) {
			return;
		}
		minecraft.level.setBlock(target, state, 3);
		if (minecraft.getSingleplayerServer() != null) {
			ServerLevel serverLevel = minecraft.getSingleplayerServer().getLevel(minecraft.level.dimension());
			if (serverLevel != null) {
				serverLevel.setBlock(target, state, 3);
			}
		}
		realTerrainParticleSetupCount++;
		realTerrainParticleLastResetFrame = frameIndex;
		realTerrainParticleTargetText = target.toShortString();
		realTerrainParticleBlockType = blockName(state);
	}

	private static int terrainParticleBenchmarkMaterialCount() {
		return REAL_TERRAIN_PARTICLE_MATERIAL_COUNT;
	}

	private static BlockState terrainParticleBenchmarkState(int index) {
		return switch (Math.floorMod(index, terrainParticleBenchmarkMaterialCount())) {
			case 1 -> Blocks.DIRT.defaultBlockState();
			case 2 -> Blocks.OAK_LEAVES.defaultBlockState();
			case 3 -> Blocks.DEEPSLATE.defaultBlockState();
			case 4 -> Blocks.WHITE_WOOL.defaultBlockState();
			default -> Blocks.STONE.defaultBlockState();
		};
	}

	private static String terrainParticleTargetsText() {
		if (realTerrainParticleTargets.length == 0) {
			return "unset";
		}
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < realTerrainParticleTargets.length; i++) {
			if (i > 0) {
				text.append(';');
			}
			text.append(i).append('=').append(realTerrainParticleTargets[i].toShortString());
		}
		return text.toString();
	}

	public static void recordTerrainParticleExtraction(String route, BlockState blockState, long nanos) {
		if (!REAL_TERRAIN_PARTICLE_GAMEPLAY) {
			return;
		}
		String normalizedRoute = route == null || route.isBlank() ? "unknown" : route.trim();
		TERRAIN_PARTICLE_ROUTE_COUNTS.merge(normalizedRoute, 1, Integer::sum);
		TERRAIN_PARTICLE_ROUTE_NANOS.merge(normalizedRoute, Math.max(0L, nanos), Long::sum);
		recordSubmittedWorkIdentity("terrain-particles", normalizedRoute + ":" + blockName(blockState));
	}

	private static String blockName(BlockState blockState) {
		return blockState == null
			? "missing"
			: blockState.getBlock().builtInRegistryHolder().key().location().toString();
	}

	private static void fail(Minecraft minecraft, String reason) {
		if (failed || complete) {
			return;
		}
		failed = true;
		failureReason = reason;
		writeStatus(minecraft, "failed");
		restoreArmorOverride(minecraft);
	}

	private static void writeStatus(Minecraft minecraft, String status) {
		try {
			Path parent = STATUS_PATH.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			Files.writeString(STATUS_PATH, json(minecraft, status), StandardCharsets.UTF_8);
		} catch (IOException exception) {
			complete = true;
		}
	}

	private static String json(Minecraft minecraft, String status) {
		StringBuilder json = new StringBuilder(12288);
		json.append("{\n");
		field(json, "schema", "mattmc-graphics-frame-benchmark-v2", 2, true);
		field(json, "workloadCounterDefinitionVersion", WORKLOAD_COUNTER_DEFINITION_VERSION, 2, true);
		field(json, "status", status, 2, true);
		field(json, "failureReason", failureReason, 2, true);
		field(json, "implementationAttribution", System.getProperty("mattmc.dev.graphicsFrameBenchmark.implementation", "unknown"), 2, true);
		field(json, "workloadProfile", System.getProperty("mattmc.dev.graphicsFrameBenchmark.workloadProfile", "unknown"), 2, true);
		field(json, "world", System.getProperty("mattmc.dev.graphicsFrameBenchmark.world", "unknown"), 2, true);
		field(json, "dimension", dimension, 2, true);
		field(json, "backend", System.getProperty("mattmc.dev.graphicsFrameBenchmark.backend", "unknown"), 2, true);
		field(json, "shaders", System.getProperty("mattmc.dev.graphicsFrameBenchmark.shaders", "unknown"), 2, true);
		json.append("  \"worldEntered\": ").append(minecraft.level != null && minecraft.player != null).append(",\n");
		json.append("  \"settleFramesRequested\": ").append(SETTLE_FRAMES).append(",\n");
		json.append("  \"warmupFramesRequested\": ").append(WARMUP_FRAMES).append(",\n");
		json.append("  \"measureFramesRequested\": ").append(MEASURE_FRAMES).append(",\n");
		json.append("  \"readinessTimeoutNanos\": ").append(READINESS_TIMEOUT_NANOS).append(",\n");
		json.append("  \"positiveControlDelayNanos\": ").append(POSITIVE_CONTROL_DELAY_NANOS).append(",\n");
		json.append("  \"gcBeforeMeasurement\": ").append(GC_BEFORE_MEASUREMENT).append(",\n");
		json.append("  \"gcBeforeMeasurementOffsetFrames\": ").append(GC_BEFORE_MEASUREMENT_OFFSET_FRAMES).append(",\n");
		json.append("  \"preMeasurementGcIssued\": ").append(preMeasurementGcIssued).append(",\n");
		json.append("  \"beginFrameCalls\": ").append(beginFrameCalls).append(",\n");
		json.append("  \"activeBeginFrameCalls\": ").append(activeBeginFrameCalls).append(",\n");
		json.append("  \"endFrameCalls\": ").append(endFrameCalls).append(",\n");
		json.append("  \"endFrameInactiveReturns\": ").append(endFrameInactiveReturns).append(",\n");
		json.append("  \"endFrameTerminalReturns\": ").append(endFrameTerminalReturns).append(",\n");
		field(json, "lastFrameLifecycle", lastFrameLifecycle, 2, true);
		json.append("  \"initializationWaitFrames\": ").append(initializationWaitFrames).append(",\n");
		field(json, "lastReadinessBlocker", lastReadinessBlocker, 2, true);
		json.append("  \"producerWorkloadWaitFrames\": ").append(producerWorkloadWaitFrames).append(",\n");
		field(json, "lastProducerWorkloadBlocker", lastProducerWorkloadBlocker, 2, true);
		json.append("  \"framesSeenIncludingSettleWarmup\": ").append(frameIndex).append(",\n");
		json.append("  \"settledFrameIndex\": ").append(settledFrameIndex).append(",\n");
		json.append("  \"measuredFrameCount\": ").append(FRAME_NANOS.size()).append(",\n");
		json.append("  \"window\": { \"width\": ").append(minecraft.getWindow().getWidth()).append(", \"height\": ").append(minecraft.getWindow().getHeight()).append(" },\n");
		writeRuntimeState(json, minecraft);
		json.append(",\n");
			json.append("  \"cameraPath\": { \"type\": \"").append(staticTerrainScenarioEnabled() ? "fixed-static-terrain" : "settled-sine-yaw")
				.append("\", \"yawDelta\": ").append(format(staticTerrainScenarioEnabled() ? 0.0F : YAW_DELTA))
				.append(", \"initialYaw\": ").append(format(initialYaw))
				.append(", \"initialPitch\": ").append(format(initialPitch))
				.append(", \"initialPosition\": { \"x\": ").append(format(initialPosition.x))
				.append(", \"y\": ").append(format(initialPosition.y))
				.append(", \"z\": ").append(format(initialPosition.z)).append(" } },\n");
			writeTerrainParticleRealGameplay(json);
			json.append(",\n");
			writeBlockDisplayScenario(json);
			json.append(",\n");
			writeStaticTerrainScenario(json);
			json.append(",\n");
			writeStaticTerrainPerformance(json);
			json.append(",\n");
				writeFallingBlockScenario(json);
				json.append(",\n");
				writePistonScenario(json);
				json.append(",\n");
				writeDistantHorizonsRoute(json);
				json.append(",\n");
				writeValidity(json);
		json.append(",\n");
		json.append("  \"java\": {\n");
		json.append("    \"gcCountDelta\": ").append(delta(gcCountAtStart, gcCountAtEnd)).append(",\n");
		json.append("    \"gcTimeMillisDelta\": ").append(delta(gcTimeAtStart, gcTimeAtEnd)).append(",\n");
		json.append("    \"usedMemoryBytesAtStart\": ").append(usedMemoryAtStart).append(",\n");
		json.append("    \"usedMemoryBytesAtEnd\": ").append(usedMemoryAtEnd).append(",\n");
		json.append("    \"currentThreadAllocatedBytesAtStart\": ").append(threadAllocatedBytesAtStart).append(",\n");
		json.append("    \"currentThreadAllocatedBytesAtEnd\": ").append(threadAllocatedBytesAtEnd).append(",\n");
		json.append("    \"currentThreadAllocatedBytesDelta\": ").append(delta(threadAllocatedBytesAtStart, threadAllocatedBytesAtEnd)).append("\n");
		json.append("  },\n");
		writeSamples(json);
		json.append(",\n");
		writeFrameTimeline(json);
		json.append(",\n");
		field(json, "rustGalSliceMetricsLine", RustGalFrameCoordinator.currentAuditMetricsLine(), 2, true);
		writeStringIntMap(json, "submittedWorkCounts", SUBMITTED_WORK_COUNTS);
		json.append(",\n");
		writePhaseMap(json, "exclusivePhaseNanos", EXCLUSIVE_PHASES);
		json.append(",\n");
		writePhaseMap(json, "nestedPhaseNanos", NESTED_PHASES);
		json.append(",\n");
		writeGameplayWeatherCloudDiagnostics(json);
		json.append(",\n");
		writeGameplayArrowDiagnostics(json);
		json.append(",\n");
		writeGameplayOrbBeaconDiagnostics(json);
		json.append(",\n");
		writeGameplayPrimedTntDiagnostics(json);
		json.append(",\n");
		writeGameplayWorldTextDiagnostics(json);
		json.append("\n}\n");
		return json.toString();
	}

	private static void writeDistantHorizonsRoute(StringBuilder json) {
		DistantHorizonsSemanticCollector.RouteDiagnostics route =
			DistantHorizonsSemanticCollector.routeDiagnosticsSnapshot();
		json.append("  \"distantHorizonsRoute\": {\n");
		field(json, "decision", route.decision(), 4, true);
		field(json, "reason", route.reason(), 4, true);
		json.append("    \"frame\": ").append(route.frame()).append(",\n");
		json.append("    \"visibleColumns\": ").append(route.visibleColumns()).append(",\n");
		json.append("    \"cachedColumns\": ").append(route.cachedColumns()).append(",\n");
		json.append("    \"unpublishedVisibleColumns\": ").append(route.unpublishedVisibleColumns()).append(",\n");
		json.append("    \"opaqueSegments\": ").append(route.opaqueSegments()).append(",\n");
		json.append("    \"transparentSegments\": ").append(route.transparentSegments()).append(",\n");
		json.append("    \"waterSegments\": ").append(route.waterSegments()).append(",\n");
		json.append("    \"frameSemanticsEnabled\": ").append(route.frameSemanticsEnabled()).append(",\n");
		json.append("    \"selected\": ").append(route.selected()).append(",\n");
		json.append("    \"lastExecutedWorldFrame\": ").append(route.lastExecutedWorldFrame()).append(",\n");
		json.append("    \"lastExecutedSubmission\": ").append(route.lastExecutedSubmission()).append(",\n");
		json.append("    \"lastExecutedInstances\": ").append(route.lastExecutedInstances()).append(",\n");
		json.append("    \"lastExecutedFrameSemanticsEnabled\": ").append(route.lastExecutedFrameSemanticsEnabled()).append("\n");
		json.append("  }");
	}

	private static void writeGameplayWorldTextDiagnostics(StringBuilder json) {
		RustGalWorldPrimitiveRenderer.WorldTextDiagnostic diagnostic =
			RustGalWorldPrimitiveRenderer.worldTextDiagnostic();
		json.append("  \"gameplayWorldText\": {\"scenario\": \"")
			.append(escape(System.getProperty("mattmc.dev.rustGalWorldText.scenario", "")))
			.append("\", \"semanticFrame\": ").append(diagnostic.semanticFrame())
			.append(", \"visibleEntityStates\": ").append(diagnostic.visibleEntityStates())
			.append(", \"nameTagCallbacks\": ").append(diagnostic.nameTagCallbacks())
			.append(", \"textCallbacks\": ").append(diagnostic.textCallbacks())
			.append(", \"normalSubmits\": ").append(diagnostic.normalSubmits())
			.append(", \"seeThroughSubmits\": ").append(diagnostic.seeThroughSubmits())
			.append(", \"polygonOffsetSubmits\": ").append(diagnostic.polygonOffsetSubmits())
			.append(", \"emittedQuads\": ").append(diagnostic.emittedQuads())
			.append(", \"emittedImages\": ").append(diagnostic.emittedImages())
			.append(", \"fullySupported\": ").append(diagnostic.fullySupported())
			.append(", \"consumedQuads\": ").append(diagnostic.consumedQuads())
			.append("}");
	}

	private static void writeGameplayPrimedTntDiagnostics(StringBuilder json) {
		String scenario = System.getProperty("mattmc.dev.rustGalWorldMesh.primedTntScenario", "");
		List<RustGalWorldPrimitiveRenderer.MovingMeshExecutionDiagnostic> executions =
			RustGalWorldPrimitiveRenderer.movingMeshExecutionDiagnostics().stream()
				.filter(execution -> "primed-tnt".equals(execution.provenance()))
				.toList();
		json.append("  \"rustGalWorldPrimedTntScenario\": \"")
			.append(escape(scenario)).append("\",\n");
		json.append("  \"rustGalWorldPrimedTntSetup\": {\"status\": \"")
			.append(escape(DeterministicCameraCapture.gameplayPrimedTntSetupStatus()))
			.append("\", \"blockId\": \"")
			.append(escape(DeterministicCameraCapture.gameplayPrimedTntSetupBlockId()))
			.append("\", \"origin\": \"")
			.append(escape(DeterministicCameraCapture.gameplayPrimedTntSetupOrigin()))
			.append("\", \"entityCount\": ")
			.append(DeterministicCameraCapture.gameplayPrimedTntSetupEntityCount()).append("},\n");
		json.append("  \"gameplayPrimedTntExecution\": [");
		for (int i = 0; i < executions.size(); i++) {
			if (i > 0) json.append(',');
			RustGalWorldPrimitiveRenderer.MovingMeshExecutionDiagnostic execution = executions.get(i);
			json.append("{\"deterministicFrameIndex\": ").append(execution.deterministicFrameIndex())
				.append(", \"route\": \"").append(escape(execution.route()))
				.append("\", \"gameplayFrameId\": ").append(execution.gameplayFrameId())
				.append(", \"submissionId\": ").append(execution.submissionId())
				.append(", \"instances\": ").append(execution.instances()).append('}');
		}
		json.append("]");
	}

	private static void writeGameplayWeatherCloudDiagnostics(StringBuilder json) {
		json.append("  \"gameplayWeather\": {\n");
		json.append("    \"setupComplete\": ")
			.append(DeterministicCameraCapture.gameplayWeatherSetupComplete()).append(",\n");
		field(json, "setupStage", DeterministicCameraCapture.gameplayWeatherSetupStage(), 4, true);
		field(json, "setupLastMissing", DeterministicCameraCapture.gameplayWeatherSetupLastMissing(), 4, true);
		RustGalWorldPrimitiveRenderer.WeatherTraversalDiagnostic traversal = last(
			RustGalWorldPrimitiveRenderer.weatherTraversalDiagnostics());
		RustGalWorldPrimitiveRenderer.WeatherSemanticDiagnostic semantic = last(
			RustGalWorldPrimitiveRenderer.weatherSemanticDiagnostics());
		RustGalWorldPrimitiveRenderer.WeatherExecutionDiagnostic execution = last(
			RustGalWorldPrimitiveRenderer.weatherExecutionDiagnostics());
		json.append("    \"traversalReceipts\": [");
		if (traversal != null) {
			json.append("{\"frameIndex\": ").append(traversal.frameIndex())
				.append(", \"route\": \"").append(escape(traversal.route()))
				.append("\", \"rainColumns\": ").append(traversal.rainColumns())
				.append(", \"intensity\": ").append(traversal.intensity()).append("}");
		}
		json.append("],\n    \"semanticReceipts\": [");
		if (semantic != null) {
			json.append("{\"frameIndex\": ").append(semantic.frameIndex())
				.append(", \"rainColumns\": ").append(semantic.rainColumns())
				.append(", \"quads\": ").append(semantic.quads())
				.append(", \"intensity\": ").append(semantic.intensity()).append("}");
		}
		json.append("],\n    \"executionReceipts\": [");
		if (execution != null) {
			json.append("{\"deterministicFrameIndex\": ").append(execution.deterministicFrameIndex())
				.append(", \"route\": \"").append(escape(execution.route()))
				.append("\", \"gameplayFrameId\": ").append(execution.gameplayFrameId())
				.append(", \"submissionId\": ").append(execution.submissionId())
				.append(", \"quads\": ").append(execution.quads()).append("}");
		}
		json.append("]\n  },\n  \"gameplayClouds\": {\n    \"traversalReceipts\": [");
		RustGalWorldPrimitiveRenderer.CloudTraversalDiagnostic cloudTraversal = last(
			RustGalWorldPrimitiveRenderer.cloudTraversalDiagnostics());
		RustGalWorldPrimitiveRenderer.CloudSemanticDiagnostic cloudSemantic = last(
			RustGalWorldPrimitiveRenderer.cloudSemanticDiagnostics());
		RustGalWorldPrimitiveRenderer.CloudExecutionDiagnostic cloudExecution = last(
			RustGalWorldPrimitiveRenderer.cloudExecutionDiagnostics());
		if (cloudTraversal != null) {
			json.append("{\"frameIndex\": ").append(cloudTraversal.frameIndex())
				.append(", \"route\": \"").append(escape(cloudTraversal.route()))
				.append("\", \"cells\": ").append(cloudTraversal.cells())
				.append(", \"radius\": ").append(cloudTraversal.radius()).append("}");
		}
		json.append("], \"semanticReceipts\": [");
		if (cloudSemantic != null) {
			json.append("{\"frameIndex\": ").append(cloudSemantic.frameIndex())
				.append(", \"quads\": ").append(cloudSemantic.quads())
				.append(", \"sourceProgram\": 3}");
		}
		json.append("], \"executionReceipts\": [");
		if (cloudExecution != null) {
			json.append("{\"deterministicFrameIndex\": ").append(cloudExecution.deterministicFrameIndex())
				.append(", \"route\": \"").append(escape(cloudExecution.route()))
				.append("\", \"quads\": ").append(cloudExecution.quads())
				.append(", \"sourceProgram\": 3}");
		}
		json.append("]\n  }");
	}

	private static <T> T last(List<T> values) {
		return values == null || values.isEmpty() ? null : values.get(values.size() - 1);
	}

	private static void writeGameplayOrbBeaconDiagnostics(StringBuilder json) {
		List<RustGalWorldPrimitiveRenderer.ExperienceOrbDiagnostic> orbs =
			RustGalWorldPrimitiveRenderer.experienceOrbDiagnostics();
		List<RustGalWorldPrimitiveRenderer.ExperienceOrbRouteDecision> orbRoutes =
			RustGalWorldPrimitiveRenderer.experienceOrbRouteDecisions();
		List<RustGalWorldPrimitiveRenderer.ExperienceOrbExecutionDiagnostic> orbExecutions =
			RustGalWorldPrimitiveRenderer.experienceOrbExecutionDiagnostics();
		json.append("  \"gameplayExperienceOrbSetup\": {\"status\": \"")
			.append(orbs.isEmpty() && orbExecutions.isEmpty() ? "not-spawned" : "spawned")
			.append("\", \"entityCount\": ").append(orbs.isEmpty()
				? Integer.getInteger("mattmc.dev.rustGalWorldExperienceOrb.count", 1)
				: orbs.size()).append("},\n");
		json.append("  \"gameplayExperienceOrbs\": [");
		for (int i = 0; i < orbs.size(); i++) {
			if (i > 0) json.append(',');
			RustGalWorldPrimitiveRenderer.ExperienceOrbDiagnostic orb = orbs.get(i);
			json.append("{\"frameIndex\": ").append(orb.frameIndex())
				.append(", \"route\": \"").append(escape(orb.route()))
				.append("\", \"projected\": ").append(orb.projected()).append('}');
		}
		if (orbs.isEmpty()) {
			for (int i = 0; i < orbExecutions.size(); i++) {
				if (i > 0) json.append(',');
				json.append("{\"frameIndex\": ").append(orbExecutions.get(i).deterministicFrameIndex())
					.append(", \"route\": \"rust-vulkan-whole-frame\", \"projected\": false}");
			}
		}
		json.append("],\n  \"gameplayExperienceOrbRouteDecisions\": [");
		for (int i = 0; i < orbRoutes.size(); i++) {
			if (i > 0) json.append(',');
			RustGalWorldPrimitiveRenderer.ExperienceOrbRouteDecision route = orbRoutes.get(i);
			json.append("{\"frameIndex\": ").append(route.frameIndex())
				.append(", \"route\": \"").append(escape(route.route()))
				.append("\", \"rustSelected\": ").append(route.rustSelected())
				.append(", \"rustQueued\": ").append(route.rustQueued())
				.append(", \"javaDrawn\": ").append(route.javaDrawn()).append('}');
		}
		if (orbRoutes.isEmpty()) {
			for (int i = 0; i < orbExecutions.size(); i++) {
				if (i > 0) json.append(',');
				json.append("{\"frameIndex\": ").append(orbExecutions.get(i).deterministicFrameIndex())
					.append(", \"route\": \"rust-vulkan-whole-frame\", \"rustSelected\": true, \"rustQueued\": true, \"javaDrawn\": false}");
			}
		}
		json.append("],\n  \"gameplayExperienceOrbExecution\": [");
		for (int i = 0; i < orbExecutions.size(); i++) {
			if (i > 0) json.append(',');
			RustGalWorldPrimitiveRenderer.ExperienceOrbExecutionDiagnostic execution = orbExecutions.get(i);
			json.append("{\"deterministicFrameIndex\": ").append(execution.deterministicFrameIndex())
				.append(", \"route\": \"").append(escape(execution.route()))
				.append("\", \"gameplayFrameId\": ").append(execution.gameplayFrameId())
				.append(", \"submissionId\": ").append(execution.submissionId())
				.append(", \"quads\": ").append(execution.quads()).append('}');
		}
		json.append("],\n  \"gameplayBeaconSetup\": {\"status\": \"")
			.append(escape(DeterministicCameraCapture.gameplayBeaconSetupStatus()));
		List<RustGalWorldPrimitiveRenderer.BeaconBeamDiagnostic> beams =
			RustGalWorldPrimitiveRenderer.beaconBeamDiagnostics();
		List<RustGalWorldPrimitiveRenderer.BeaconBeamExecutionDiagnostic> beamExecutions =
			RustGalWorldPrimitiveRenderer.beaconBeamExecutionDiagnostics();
		json.append("\", \"sectionCount\": ").append(beams.size())
			.append(", \"clientBeamSectionsReady\": ")
			.append(DeterministicCameraCapture.gameplayBeaconClientReady())
			.append(", \"serverBeamSectionsReady\": ")
			.append(DeterministicCameraCapture.gameplayBeaconServerReady())
			.append(", \"gameTime\": ")
			.append(DeterministicCameraCapture.gameplayBeaconGameTime())
			.append(", \"baseValid\": ")
			.append(DeterministicCameraCapture.gameplayBeaconBaseValid())
			.append(", \"tickerInvocations\": ")
			.append(DeterministicCameraCapture.gameplayBeaconTickerInvocations())
			.append(", \"clientTickerInvocations\": ")
			.append(DeterministicCameraCapture.gameplayBeaconClientTickerInvocations())
			.append(", \"lastTickerGameTime\": ")
			.append(DeterministicCameraCapture.gameplayBeaconLastTickerGameTime())
			.append(", \"tickerSawBlockEntity\": ")
			.append(DeterministicCameraCapture.gameplayBeaconTickerSawBlockEntity())
			.append("},\n");
		json.append("  \"gameplayBeaconExecution\": [");
		for (int i = 0; i < beamExecutions.size(); i++) {
			if (i > 0) json.append(',');
			RustGalWorldPrimitiveRenderer.BeaconBeamExecutionDiagnostic execution = beamExecutions.get(i);
			json.append("{\"deterministicFrameIndex\": ").append(execution.deterministicFrameIndex())
				.append(", \"route\": \"").append(escape(execution.route()))
				.append("\", \"gameplayFrameId\": ").append(execution.gameplayFrameId())
				.append(", \"submissionId\": ").append(execution.submissionId())
				.append(", \"quads\": ").append(execution.quads()).append('}');
		}
		json.append("]");
	}

	private static void writeGameplayArrowDiagnostics(StringBuilder json) {
		List<RustGalWorldPrimitiveRenderer.ArrowDiagnostic> arrows =
			RustGalWorldPrimitiveRenderer.arrowDiagnostics();
		List<RustGalWorldPrimitiveRenderer.ArrowRouteDecision> routes =
			RustGalWorldPrimitiveRenderer.arrowRouteDecisions();
		List<RustGalWorldPrimitiveRenderer.MovingMeshExecutionDiagnostic> executions =
			RustGalWorldPrimitiveRenderer.movingMeshExecutionDiagnostics();
		List<RustGalWorldPrimitiveRenderer.MovingMeshExecutionDiagnostic> arrowExecutions = executions.stream()
			.filter(execution -> "arrow".equals(execution.provenance())
				&& execution.instances() > 0
				&& "rust-vulkan-whole-frame".equals(execution.route()))
			.toList();
		json.append("  \"gameplayArrowSetup\": {\"status\": \"")
			.append(arrows.isEmpty() && arrowExecutions.isEmpty() ? "not-spawned" : "spawned")
			.append("\", \"entityCount\": ").append(arrows.isEmpty()
				? arrowExecutions.stream().mapToInt(RustGalWorldPrimitiveRenderer.MovingMeshExecutionDiagnostic::instances).max().orElse(0)
				: arrows.size()).append("},\n");
		json.append("  \"gameplayArrows\": [");
		for (int i = 0; i < arrows.size(); i++) {
			RustGalWorldPrimitiveRenderer.ArrowDiagnostic arrow = arrows.get(i);
			if (i > 0) json.append(',');
			json.append("{\"frameIndex\": ").append(arrow.frameIndex())
				.append(", \"route\": \"").append(escape(arrow.route()))
				.append("\", \"textureId\": \"").append(escape(arrow.textureId()))
				.append("\", \"projected\": ").append(arrow.projected()).append('}');
		}
		if (arrows.isEmpty()) {
			for (int i = 0; i < arrowExecutions.size(); i++) {
				if (i > 0) json.append(',');
				json.append("{\"frameIndex\": ").append(arrowExecutions.get(i).deterministicFrameIndex())
					.append(", \"route\": \"rust-vulkan-whole-frame\", \"projected\": false}");
			}
		}
		json.append("],\n  \"gameplayArrowRouteDecisions\": [");
		for (int i = 0; i < routes.size(); i++) {
			RustGalWorldPrimitiveRenderer.ArrowRouteDecision route = routes.get(i);
			if (i > 0) json.append(',');
			json.append("{\"frameIndex\": ").append(route.frameIndex())
				.append(", \"route\": \"").append(escape(route.route()))
				.append("\", \"textureId\": \"").append(escape(route.textureId()))
				.append("\", \"rustSelected\": ").append(route.rustSelected())
				.append(", \"rustQueued\": ").append(route.rustQueued())
				.append(", \"javaDrawn\": ").append(route.javaDrawn()).append('}');
		}
		if (routes.isEmpty()) {
			for (int i = 0; i < arrowExecutions.size(); i++) {
				if (i > 0) json.append(',');
				json.append("{\"frameIndex\": ").append(arrowExecutions.get(i).deterministicFrameIndex())
					.append(", \"route\": \"rust-vulkan-whole-frame\", \"rustSelected\": true, \"rustQueued\": true, \"javaDrawn\": false}");
			}
		}
		json.append("],\n  \"gameplayMovingMeshExecution\": [");
		int emitted = 0;
		for (RustGalWorldPrimitiveRenderer.MovingMeshExecutionDiagnostic execution : executions) {
			if (!"arrow".equals(execution.provenance())) continue;
			if (emitted++ > 0) json.append(',');
			json.append("{\"deterministicFrameIndex\": ").append(execution.deterministicFrameIndex())
				.append(", \"route\": \"").append(escape(execution.route()))
				.append("\", \"provenance\": \"arrow\", \"gameplayFrameId\": ")
				.append(execution.gameplayFrameId()).append(", \"submissionId\": ")
				.append(execution.submissionId()).append(", \"instances\": ")
				.append(execution.instances()).append('}');
		}
		json.append("]");
	}

	private static void writeRuntimeState(StringBuilder json, Minecraft minecraft) {
		int loadedChunks = minecraft.level == null ? -1 : minecraft.level.getChunkSource().getLoadedChunksCount();
		int entityCount = minecraft.level == null ? -1 : minecraft.level.getEntityCount();
		int playerCount = minecraft.level == null ? -1 : minecraft.level.players().size();
		int armorValue = minecraft.player == null ? -1 : minecraft.player.getArmorValue();
		float absorptionValue = minecraft.player == null ? -1.0F : minecraft.player.getAbsorptionAmount();
		int foodLevel = minecraft.player == null ? -1 : minecraft.player.getFoodData().getFoodLevel();
		float foodSaturation = minecraft.player == null ? -1.0F : minecraft.player.getFoodData().getSaturationLevel();
		int airSupply = minecraft.player == null ? -1 : minecraft.player.getAirSupply();
		int maxAirSupply = minecraft.player == null ? -1 : minecraft.player.getMaxAirSupply();
		String gameMode = minecraft.gameMode == null ? "missing" : String.valueOf(minecraft.gameMode.getPlayerMode());
		String screen = minecraft.screen == null ? "none" : minecraft.screen.getClass().getSimpleName();
		String overlay = minecraft.getOverlay() == null ? "none" : minecraft.getOverlay().getClass().getSimpleName();
		json.append("  \"runtimeState\": {\n");
		json.append("    \"loadedChunks\": ").append(loadedChunks).append(",\n");
		json.append("    \"entityCount\": ").append(entityCount).append(",\n");
		json.append("    \"playerCount\": ").append(playerCount).append(",\n");
		json.append("    \"windowFocused\": ").append(minecraft.isWindowActive()).append(",\n");
		json.append("    \"fullscreen\": ").append(minecraft.getWindow().isFullscreen()).append(",\n");
		field(json, "screen", screen, 4, true);
		field(json, "overlay", overlay, 4, true);
		json.append("    \"hideGui\": ").append(minecraft.options.hideGui).append(",\n");
		json.append("    \"debugOverlayVisible\": ").append(minecraft.getDebugOverlay().showDebugScreen()).append(",\n");
		json.append("    \"renderDistance\": ").append(minecraft.options.renderDistance().get()).append(",\n");
		json.append("    \"effectiveRenderDistance\": ").append(minecraft.options.getEffectiveRenderDistance()).append(",\n");
		json.append("    \"simulationDistance\": ").append(minecraft.options.simulationDistance().get()).append(",\n");
		json.append("    \"entityDistanceScaling\": ").append(format(minecraft.options.entityDistanceScaling().get())).append(",\n");
		json.append("    \"guiScale\": ").append(minecraft.options.guiScale().get()).append(",\n");
		json.append("    \"armorValue\": ").append(armorValue).append(",\n");
		json.append("    \"armorValueOverride\": ").append(FORCED_ARMOR_VALUE).append(",\n");
		json.append("    \"playerHealthOverride\": ").append(format(FORCED_PLAYER_HEALTH)).append(",\n");
		json.append("    \"playerMaxHealthOverride\": ").append(format(FORCED_PLAYER_MAX_HEALTH)).append(",\n");
		json.append("    \"playerAbsorption\": ").append(format(absorptionValue)).append(",\n");
		json.append("    \"playerAbsorptionOverride\": ").append(format(FORCED_PLAYER_ABSORPTION)).append(",\n");
		json.append("    \"playerFoodLevel\": ").append(foodLevel).append(",\n");
		json.append("    \"playerFoodLevelOverride\": ").append(FORCED_PLAYER_FOOD_LEVEL).append(",\n");
		json.append("    \"playerFoodSaturation\": ").append(format(foodSaturation)).append(",\n");
		json.append("    \"playerFoodSaturationOverride\": ").append(format(FORCED_PLAYER_FOOD_SATURATION)).append(",\n");
		json.append("    \"playerFoodHungerEffectOverride\": ").append(Boolean.getBoolean("mattmc.dev.deterministicCameraCapture.playerFoodHungerEffect")).append(",\n");
		json.append("    \"playerFoodJitterOverride\": ").append(Boolean.getBoolean("mattmc.dev.deterministicCameraCapture.playerFoodJitter")).append(",\n");
		json.append("    \"playerAirSupply\": ").append(airSupply).append(",\n");
		json.append("    \"playerAirSupplyOverride\": ").append(FORCED_PLAYER_AIR_SUPPLY).append(",\n");
		json.append("    \"playerMaxAirSupply\": ").append(maxAirSupply).append(",\n");
		json.append("    \"playerMaxAirSupplyOverride\": ").append(FORCED_PLAYER_MAX_AIR_SUPPLY).append(",\n");
		json.append("    \"playerUnderwaterOverride\": ").append(Boolean.getBoolean("mattmc.dev.graphicsFrameBenchmark.playerUnderwater")).append(",\n");
		json.append("    \"playerAirPopOverride\": ").append(Boolean.getBoolean("mattmc.dev.graphicsFrameBenchmark.playerAirPop")).append(",\n");
		json.append("    \"mountPresentOverride\": ").append(FORCE_MOUNT_PRESENT).append(",\n");
		json.append("    \"mountHealthOverride\": ").append(format(FORCED_MOUNT_HEALTH)).append(",\n");
		json.append("    \"mountMaxHealthOverride\": ").append(format(FORCED_MOUNT_MAX_HEALTH)).append(",\n");
		json.append("    \"mountHealthRowsOverride\": ").append(FORCED_MOUNT_HEALTH_ROWS).append(",\n");
		field(json, "gameMode", gameMode, 4, true);
		field(json, "gameModeOverride", FORCED_GAME_MODE, 4, true);
		field(json, "rustGalGuiControl", rustGalGuiControl(), 4, true);
		json.append("    \"maxFps\": ").append(minecraft.options.framerateLimit().get()).append(",\n");
		json.append("    \"enableVsync\": ").append(minecraft.options.enableVsync().get()).append(",\n");
		field(json, "graphicsMode", String.valueOf(minecraft.options.graphicsMode().get()), 4, false);
		json.append("  }");
	}

	private static void writeValidity(StringBuilder json) {
		long sampleSum = FRAME_NANOS.stream().mapToLong(Long::longValue).sum();
		long wallClock = measurementStartNanos > 0L && measurementEndNanos >= measurementStartNanos ? measurementEndNanos - measurementStartNanos : -1L;
		double wallClockRatio = sampleSum > 0L && wallClock > 0L ? Math.abs((double)sampleSum - (double)wallClock) / (double)sampleSum : -1.0;
		double measuredFps = sampleSum > 0L ? FRAME_NANOS.size() * 1_000_000_000.0 / sampleSum : -1.0;
		double displayedFps = displayedFpsAtMeasurementEnd > 0 ? displayedFpsAtMeasurementEnd : displayedFpsAtMeasurementStart;
		double displayedRatio = measuredFps > 0.0 && displayedFps > 0.0 ? Math.abs(measuredFps - displayedFps) / measuredFps : -1.0;
		boolean wallClockOk = wallClockRatio >= 0.0 && wallClockRatio <= WALL_CLOCK_TOLERANCE;
		boolean displayedRequired = DISPLAY_FPS_CHECK_ENABLED && FRAME_NANOS.size() >= DISPLAY_FPS_MIN_FRAMES && wallClock >= DISPLAY_FPS_MIN_NANOS;
		boolean displayedOk = !displayedRequired || displayedRatio < 0.0 || displayedRatio <= DISPLAY_FPS_TOLERANCE;
		json.append("  \"validity\": {\n");
		json.append("    \"sampleSumNanos\": ").append(sampleSum).append(",\n");
		json.append("    \"wallClockMeasurementNanos\": ").append(wallClock).append(",\n");
		json.append("    \"measurementStartNanos\": ").append(measurementStartNanos).append(",\n");
		json.append("    \"measurementEndNanos\": ").append(measurementEndNanos).append(",\n");
		json.append("    \"firstSampleNanos\": ").append(firstSampleNanos).append(",\n");
		json.append("    \"lastSampleNanos\": ").append(lastSampleNanos).append(",\n");
		json.append("    \"wallClockRelativeError\": ").append(format(wallClockRatio)).append(",\n");
		json.append("    \"wallClockCheckPassed\": ").append(wallClockOk).append(",\n");
		json.append("    \"measuredAverageFps\": ").append(format(measuredFps)).append(",\n");
		json.append("    \"displayedFpsAtMeasurementStart\": ").append(displayedFpsAtMeasurementStart).append(",\n");
		json.append("    \"displayedFpsAtMeasurementEnd\": ").append(displayedFpsAtMeasurementEnd).append(",\n");
		json.append("    \"displayedFpsCheckEnabled\": ").append(DISPLAY_FPS_CHECK_ENABLED).append(",\n");
		json.append("    \"displayedFpsCheckRequired\": ").append(displayedRequired).append(",\n");
		json.append("    \"displayedFpsRelativeError\": ").append(format(displayedRatio)).append(",\n");
		json.append("    \"displayedFpsCheckPassed\": ").append(displayedOk).append("\n");
		json.append("  }");
	}

	private static void writeSamples(StringBuilder json) {
		json.append("  \"frameNanosSamples\": [");
		for (int i = 0; i < FRAME_NANOS.size(); i++) {
			if (i > 0) {
				json.append(", ");
			}
			json.append(FRAME_NANOS.get(i));
		}
		json.append("]");
	}

	private static void writeFrameTimeline(StringBuilder json) {
		json.append("  \"rustWholeFrameTimeline\": [");
		for (int i = 0; i < FRAME_TIMELINE_EVENTS.size(); i++) {
			FrameTimelineEvent event = FRAME_TIMELINE_EVENTS.get(i);
			if (i > 0) {
				json.append(",");
			}
			json.append("\n    {");
			json.append(" \"frameIndex\": ").append(event.frameIndex());
			json.append(", \"correlationId\": ").append(event.correlationId());
			json.append(", \"rustFrameId\": ").append(event.rustFrameId());
			json.append(", \"submissionId\": ").append(event.submissionId());
			json.append(", \"acquiredImage\": ").append(event.acquiredImage());
			json.append(", \"presentedImage\": ").append(event.presentedImage());
			json.append(", \"executeStartNanos\": ").append(event.executeStartNanos());
			json.append(", \"acquireStartNanos\": ").append(event.acquireStartNanos());
			json.append(", \"acquireEndNanos\": ").append(event.acquireEndNanos());
			json.append(", \"submitStartNanos\": ").append(event.submitStartNanos());
			json.append(", \"submitEndNanos\": ").append(event.submitEndNanos());
			json.append(", \"presentStartNanos\": ").append(event.presentStartNanos());
			json.append(", \"presentEndNanos\": ").append(event.presentEndNanos());
			json.append(", \"acquireDurationNanos\": ").append(event.acquireDurationNanos());
			json.append(", \"submitDurationNanos\": ").append(event.submitDurationNanos());
			json.append(", \"presentDurationNanos\": ").append(event.presentDurationNanos());
			json.append(", \"guiSprites\": ").append(event.guiSprites());
			json.append(", \"meshInstances\": ").append(event.meshInstances());
			json.append(", \"meshDraws\": ").append(event.meshDraws());
			json.append(", \"gpuFrameTotalNanos\": ").append(event.gpuFrameTotalNanos());
			json.append(", \"presentMode\": ").append(event.presentMode());
			json.append(", \"imagesInFlight\": ").append(event.imagesInFlight());
			json.append(", \"availableFrameSlots\": ").append(event.availableFrameSlots());
			json.append(" }");
		}
		if (!FRAME_TIMELINE_EVENTS.isEmpty()) {
			json.append("\n  ");
		}
		json.append("]");
	}

	private static long currentThreadAllocatedBytes() {
		java.lang.management.ThreadMXBean baseBean = ManagementFactory.getThreadMXBean();
		if (!(baseBean instanceof com.sun.management.ThreadMXBean threadBean)) {
			return -1L;
		}
		if (!threadBean.isThreadAllocatedMemorySupported()) {
			return -1L;
		}
		try {
			if (!threadBean.isThreadAllocatedMemoryEnabled()) {
				threadBean.setThreadAllocatedMemoryEnabled(true);
			}
			return threadBean.getThreadAllocatedBytes(Thread.currentThread().getId());
		} catch (SecurityException | UnsupportedOperationException exception) {
			return -1L;
		}
	}

	private static void writeTerrainParticleRealGameplay(StringBuilder json) {
		json.append("  \"terrainParticleRealGameplay\": {\n");
		json.append("    \"enabled\": ").append(REAL_TERRAIN_PARTICLE_GAMEPLAY).append(",\n");
		field(json, "routeControl", terrainParticleRouteControl(), 4, true);
		field(json, "status", realTerrainParticleStatus, 4, true);
		field(json, "target", realTerrainParticleTargetText, 4, true);
		field(json, "blockType", realTerrainParticleBlockType, 4, true);
		json.append("    \"setupCount\": ").append(realTerrainParticleSetupCount).append(",\n");
		json.append("    \"driveCalls\": ").append(realTerrainParticleDriveCalls).append(",\n");
		json.append("    \"startCalls\": ").append(realTerrainParticleStartCalls).append(",\n");
		json.append("    \"continueCalls\": ").append(realTerrainParticleContinueCalls).append(",\n");
		json.append("    \"breakingEffects\": ").append(realTerrainParticleBreakingEffects).append(",\n");
		json.append("    \"effectsPerFrame\": ").append(REAL_TERRAIN_PARTICLE_EFFECTS_PER_FRAME).append(",\n");
		json.append("    \"materialCount\": ").append(terrainParticleBenchmarkMaterialCount()).append(",\n");
		json.append("    \"expectedMaterialMask\": ").append((1 << terrainParticleBenchmarkMaterialCount()) - 1).append(",\n");
		json.append("    \"materialMask\": ").append(realTerrainParticleMaterialMask).append(",\n");
		writeStringIntMap(json, "routeCounts", TERRAIN_PARTICLE_ROUTE_COUNTS);
		json.append(",\n");
		writeStringLongMap(json, "routeNanos", TERRAIN_PARTICLE_ROUTE_NANOS);
		json.append("\n  }");
	}

	private static void writeBlockDisplayScenario(StringBuilder json) {
		json.append("  \"blockDisplayScenario\": {\n");
		json.append("    \"enabled\": ").append(!BLOCK_DISPLAY_SCENARIO.isEmpty()).append(",\n");
		field(json, "scenario", BLOCK_DISPLAY_SCENARIO, 4, true);
		field(json, "workload", BLOCK_DISPLAY_WORKLOAD, 4, true);
		field(json, "routeControl", blockDisplayRouteControl(), 4, true);
		field(json, "status", blockDisplayScenarioStatus, 4, true);
		field(json, "block", blockDisplayScenarioBlock, 4, true);
		field(json, "position", blockDisplayScenarioPosition, 4, true);
		json.append("    \"entityCount\": ").append(blockDisplayScenarioEntityCount).append(",\n");
		json.append("    \"distinctBlockCount\": ").append(blockDisplayScenarioDistinctBlockCount).append(",\n");
		field(json, "workloadFingerprint", blockDisplayScenarioFingerprint, 4, false);
		json.append("  }");
	}

	private static void writeStaticTerrainScenario(StringBuilder json) {
		RustGalTerrainRenderer.TerrainDiagnostics diagnostics = RustGalTerrainRenderer.diagnosticsSnapshot();
		json.append("  \"staticTerrainScenario\": {\n");
		json.append("    \"enabled\": ").append(!STATIC_TERRAIN_SCENARIO.isEmpty()).append(",\n");
		field(json, "scenario", STATIC_TERRAIN_SCENARIO, 4, true);
		json.append("    \"cachedLayerAssets\": ").append(diagnostics.cachedLayerAssets()).append(",\n");
		json.append("    \"activeTerrainLayers\": ").append(diagnostics.activeTerrainLayers()).append(",\n");
		json.append("    \"activeSectionAssets\": ").append(diagnostics.activeSectionAssets()).append(",\n");
		json.append("    \"currentFrameVisibleLayerSubmissions\": ").append(diagnostics.currentFrameVisibleLayerSubmissions()).append(",\n");
		json.append("    \"atlasGeneration\": ").append(diagnostics.atlasGeneration()).append(",\n");
		json.append("    \"registeredAtlasGeneration\": ").append(diagnostics.registeredAtlasGeneration()).append(",\n");
		json.append("    \"activeNativeVertexStride\": ").append(diagnostics.activeNativeVertexStride()).append(",\n");
		json.append("    \"expectedNativeVertexStride\": ").append(diagnostics.expectedNativeVertexStride()).append(",\n");
		json.append("    \"acceptedBuildOutputs\": ").append(diagnostics.acceptedBuildOutputs()).append(",\n");
		json.append("    \"registeredMeshes\": ").append(diagnostics.registeredMeshes()).append(",\n");
		json.append("    \"texturePayloadUpdates\": ").append(diagnostics.texturePayloadUpdates()).append(",\n");
		json.append("    \"texturePayloadUpdateBytes\": ").append(diagnostics.texturePayloadUpdateBytes()).append(",\n");
		json.append("    \"atlasTextureOnlyUpdates\": ").append(diagnostics.atlasTextureOnlyUpdates()).append(",\n");
		json.append("    \"atlasMissingPayloadUpdates\": ").append(diagnostics.atlasMissingPayloadUpdates()).append(",\n");
		json.append("    \"atlasMalformedPayloadUpdates\": ").append(diagnostics.atlasMalformedPayloadUpdates()).append(",\n");
		json.append("    \"atlasPartialPayloadUpdates\": ").append(diagnostics.atlasPartialPayloadUpdates()).append(",\n");
		json.append("    \"visibleLayerProbes\": ").append(diagnostics.visibleLayerProbes()).append(",\n");
		json.append("    \"visibleLayerSubmissions\": ").append(diagnostics.visibleLayerSubmissions()).append(",\n");
		json.append("    \"failedLayerSubmissions\": ").append(diagnostics.failedLayerSubmissions()).append(",\n");
		json.append("    \"removedLayers\": ").append(diagnostics.removedLayers()).append(",\n");
		json.append("    \"invalidations\": ").append(diagnostics.invalidations()).append(",\n");
		json.append("    \"unsupportedAnimatedSections\": ").append(diagnostics.skippedUnsupportedAnimatedSections());
		String fault = System.getProperty("mattmc.dev.rustGalStaticTerrain.fault", "").trim();
		if (!fault.isBlank()) {
			json.append(",\n");
			json.append("    \"recentEvents\": [");
			appendTerrainDiagnosticEvents(json, diagnostics.recentEvents(), 6, 16, diagnostics.expectedNativeVertexStride());
			json.append("\n    ]\n");
		} else {
			json.append("\n");
		}
		json.append("  }");
	}

	private static void writeStaticTerrainPerformance(StringBuilder json) {
		TerrainPerfSnapshot current = terrainPerfSnapshot();
		json.append("  \"staticTerrainPerformance\": {\n");
		json.append("    \"quiescenceWindowFrames\": ").append(STATIC_TERRAIN_STEADY_FRAMES).append(",\n");
		json.append("    \"steadyFramesObserved\": ").append(staticTerrainSteadyFrames).append(",\n");
		field(json, "classification", staticTerrainQuiescenceClassification, 4, true);
		field(json, "changingCounters", staticTerrainLastChangingCounters, 4, true);
		field(json, "topMutationSections", staticTerrainLastMutationSections, 4, true);
		json.append("    \"timelineNanos\": {");
		json.append(" \"worldReady\": ").append(staticTerrainWorldReadyNanos).append(",");
		json.append(" \"firstBuild\": ").append(staticTerrainFirstBuildNanos).append(",");
		json.append(" \"firstRegistration\": ").append(staticTerrainFirstRegistrationNanos).append(",");
		json.append(" \"firstUpload\": ").append(staticTerrainFirstUploadNanos).append(",");
		json.append(" \"firstVisible\": ").append(staticTerrainFirstVisibleNanos).append(",");
		json.append(" \"lastMutation\": ").append(staticTerrainLastMutationNanos).append(",");
		json.append(" \"quiescenceStart\": ").append(staticTerrainQuiescenceStartNanos).append(",");
		json.append(" \"quiescenceEnd\": ").append(staticTerrainQuiescenceEndNanos).append(",");
		json.append(" \"measurementStart\": ").append(measurementStartNanos).append(",");
		json.append(" \"measurementEnd\": ").append(measurementEndNanos);
		json.append(" },\n");
		json.append("    \"phaseDurationsNanos\": {");
		json.append(" \"worldOpenToFirstBuild\": ").append(duration(staticTerrainWorldReadyNanos, staticTerrainFirstBuildNanos)).append(",");
		json.append(" \"firstBuildToFirstRegistration\": ").append(duration(staticTerrainFirstBuildNanos, staticTerrainFirstRegistrationNanos)).append(",");
		json.append(" \"firstRegistrationToFirstUpload\": ").append(duration(staticTerrainFirstRegistrationNanos, staticTerrainFirstUploadNanos)).append(",");
		json.append(" \"firstUploadToFirstVisible\": ").append(duration(staticTerrainFirstUploadNanos, staticTerrainFirstVisibleNanos)).append(",");
		json.append(" \"firstVisibleToQuiescenceStart\": ").append(duration(staticTerrainFirstVisibleNanos, staticTerrainQuiescenceStartNanos)).append(",");
		json.append(" \"quiescenceWindow\": ").append(duration(staticTerrainQuiescenceStartNanos, staticTerrainQuiescenceEndNanos)).append(",");
		json.append(" \"quiescenceToMeasurementStart\": ").append(duration(staticTerrainQuiescenceEndNanos, measurementStartNanos));
		json.append(" },\n");
		appendTerrainPerfSnapshot(json, "current", current, 4, true);
		appendTerrainPerfSnapshot(json, "quiescenceStart", staticTerrainQuiescenceStartSnapshot, 4, true);
		appendTerrainPerfSnapshot(json, "quiescenceEnd", staticTerrainQuiescenceEndSnapshot, 4, false);
		json.append("\n  }");
	}

	private static void appendTerrainPerfSnapshot(StringBuilder json, String name, TerrainPerfSnapshot snapshot, int indent, boolean comma) {
		json.append(" ".repeat(indent)).append('"').append(name).append("\": ");
		if (snapshot == null) {
			json.append("null");
			if (comma) {
				json.append(',');
			}
			json.append('\n');
			return;
		}
		json.append("{ ");
		json.append("\"acceptedBuildOutputs\": ").append(snapshot.acceptedBuildOutputs()).append(", ");
		json.append("\"registeredMeshes\": ").append(snapshot.registeredMeshes()).append(", ");
		json.append("\"terrainExtractionFrames\": ").append(snapshot.diagnostics().terrainExtractionFrames()).append(", ");
		json.append("\"cachedLayerAssets\": ").append(snapshot.cachedLayerAssets()).append(", ");
		json.append("\"activeTerrainLayers\": ").append(snapshot.activeTerrainLayers()).append(", ");
		json.append("\"activeSectionAssets\": ").append(snapshot.activeSectionAssets()).append(", ");
		json.append("\"atlasGeneration\": ").append(snapshot.atlasGeneration()).append(", ");
		json.append("\"texturePayloadUpdates\": ").append(snapshot.texturePayloadUpdates()).append(", ");
		json.append("\"texturePayloadUpdateBytes\": ").append(snapshot.diagnostics().texturePayloadUpdateBytes()).append(", ");
		json.append("\"removedLayers\": ").append(snapshot.diagnostics().removedLayers()).append(", ");
		json.append("\"invalidations\": ").append(snapshot.invalidations()).append(", ");
		json.append("\"failedLayerSubmissions\": ").append(snapshot.failedLayerSubmissions()).append(", ");
		json.append("\"visibleFingerprint\": ").append(snapshot.visibleFingerprint()).append(", ");
		json.append("\"loadedChunks\": ").append(snapshot.loadedChunks()).append(", ");
		json.append("\"renderDistance\": ").append(snapshot.renderDistance()).append(", ");
		json.append("\"worldMeshGeneration\": ").append(snapshot.meshMetrics().generation()).append(", ");
		json.append("\"worldMeshUploadedGeneration\": ").append(snapshot.meshMetrics().uploadedGeneration()).append(", ");
		json.append("\"worldMeshPayloadCount\": ").append(snapshot.meshMetrics().payloadCount()).append(", ");
		json.append("\"worldMeshPayloadBytes\": ").append(snapshot.meshMetrics().payloadBytes()).append(", ");
		json.append("\"worldMeshFailures\": ").append(snapshot.meshMetrics().failures()).append(", ");
		json.append("\"cachedMeshes\": ").append(snapshot.meshMetrics().cachedMeshes()).append(", ");
		json.append("\"cachedTextures\": ").append(snapshot.meshMetrics().cachedTextures()).append(", ");
		json.append("\"dirtyMeshes\": ").append(snapshot.meshMetrics().dirtyMeshes()).append(", ");
		json.append("\"dirtyTextures\": ").append(snapshot.meshMetrics().dirtyTextures()).append(", ");
		json.append("\"pendingInstances\": ").append(snapshot.meshMetrics().pendingInstances());
		json.append(" }");
		if (comma) {
			json.append(',');
		}
		json.append('\n');
	}

	private static void writeFallingBlockScenario(StringBuilder json) {
		json.append("  \"fallingBlockScenario\": {\n");
		json.append("    \"enabled\": ").append(!FALLING_BLOCK_SCENARIO.isEmpty()).append(",\n");
		field(json, "scenario", FALLING_BLOCK_SCENARIO, 4, true);
		field(json, "routeControl", fallingBlockRouteControl(), 4, true);
		field(json, "status", fallingBlockScenarioStatus, 4, true);
		field(json, "block", fallingBlockScenarioBlock, 4, true);
		field(json, "position", fallingBlockScenarioPosition, 4, true);
		json.append("    \"entityCount\": ").append(FALLING_BLOCK_SCENARIO.isEmpty() ? 0 : FALLING_BLOCK_COUNT).append(",\n");
		writeStringIntMap(json, "routeCounts", FALLING_BLOCK_ROUTE_COUNTS);
		json.append(",\n");
		writeStringIntMap(json, "movingRouteCounts", MOVING_BLOCK_ROUTE_COUNTS);
		json.append(",\n");
		field(json, "workloadFingerprint", fallingBlockScenarioFingerprint, 4, false);
		json.append("  }");
	}

	private static void writePistonScenario(StringBuilder json) {
		json.append("  \"pistonScenario\": {\n");
		json.append("    \"enabled\": ").append(!PISTON_SCENARIO.isEmpty()).append(",\n");
		field(json, "scenario", PISTON_SCENARIO, 4, true);
		field(json, "routeControl", pistonRouteControl(), 4, true);
		field(json, "status", pistonScenarioStatus, 4, true);
		field(json, "block", pistonScenarioBlock, 4, true);
		field(json, "position", pistonScenarioPosition, 4, true);
		json.append("    \"entityCount\": ").append(PISTON_SCENARIO.isEmpty() ? 0 : PISTON_COUNT).append(",\n");
		json.append("    \"reseedCount\": ").append(pistonScenarioReseedCount).append(",\n");
		json.append("    \"clientBlockEntityPresent\": ").append(pistonScenarioClientBlockEntityPresent).append(",\n");
		json.append("    \"serverBlockEntityPresent\": ").append(pistonScenarioServerBlockEntityPresent).append(",\n");
		writeStringIntMap(json, "movingRouteCounts", MOVING_BLOCK_ROUTE_COUNTS);
		json.append(",\n");
		writeMovingBlockShellScan(json);
		json.append(",\n");
		field(json, "workloadFingerprint", pistonScenarioFingerprint, 4, false);
		json.append("  }");
	}

	private static void writeMovingBlockShellScan(StringBuilder json) {
		json.append("  \"shellScan\": {");
		json.append(" \"samples\": ").append(movingBlockShellScanSamples).append(",");
		json.append(" \"fallbackSamples\": ").append(movingBlockShellScanFallbackSamples).append(",");
		json.append(" \"totalNanos\": ").append(movingBlockShellScanNanos).append(",");
		json.append(" \"maxNanos\": ").append(movingBlockShellScanMaxNanos).append(",");
		json.append(" \"chunksScanned\": ").append(movingBlockShellScanChunks).append(",");
		json.append(" \"blockEntitiesInspected\": ").append(movingBlockShellScanBlockEntities).append(",");
		json.append(" \"pistonEntitiesFound\": ").append(movingBlockShellScanPistonsFound).append(",");
		json.append(" \"pistonStatesExtracted\": ").append(movingBlockShellScanPistonStatesExtracted);
		json.append(" }");
	}

	private static void writeStringIntMap(StringBuilder json, String name, Map<String, Integer> values) {
		json.append("  \"").append(name).append("\": {");
		int index = 0;
		for (Map.Entry<String, Integer> entry : values.entrySet()) {
			if (index++ > 0) {
				json.append(", ");
			}
			json.append('"').append(escape(entry.getKey())).append("\": ").append(entry.getValue());
		}
		json.append("}");
	}

	private static void writeStringLongMap(StringBuilder json, String name, Map<String, Long> values) {
		json.append("  \"").append(name).append("\": {");
		int index = 0;
		for (Map.Entry<String, Long> entry : values.entrySet()) {
			if (index++ > 0) {
				json.append(", ");
			}
			json.append('"').append(escape(entry.getKey())).append("\": ").append(entry.getValue());
		}
		json.append("}");
	}

	private static void appendTerrainDiagnosticEvents(
		StringBuilder json,
		List<RustGalTerrainRenderer.TerrainDiagnosticEvent> events,
		int indent,
		int maxEvents,
		int expectedNativeVertexStride
	) {
		int start = Math.max(0, events.size() - Math.max(0, maxEvents));
		for (int i = start; i < events.size(); i++) {
			RustGalTerrainRenderer.TerrainDiagnosticEvent event = events.get(i);
			if (i > start) {
				json.append(",");
			}
			json.append("\n").append(" ".repeat(indent)).append("{ ");
			json.append("\"frame\": ").append(event.gameplayFrameId()).append(", ");
			json.append("\"sectionKey\": ").append(event.sectionPos()).append(", ");
			json.append("\"gameplayFrameId\": ").append(event.gameplayFrameId()).append(", ");
			json.append("\"terrainExtractionFrameId\": ").append(event.terrainExtractionFrameId()).append(", ");
			json.append("\"rustEnqueueFrameId\": ").append(event.rustEnqueueFrameId()).append(", ");
			json.append("\"executionFrameId\": ").append(event.executionFrameId()).append(", ");
			json.append("\"executionSubmissionId\": ").append(event.executionSubmissionId()).append(", ");
			json.append("\"sectionPos\": ").append(event.sectionPos()).append(", ");
			json.append("\"layer\": \"").append(escape(event.layer())).append("\", ");
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
			json.append("\"primitiveCount\": ").append(event.primitiveCount()).append(", ");
			json.append("\"sortedIndexHash\": ").append(event.sortedIndexHash()).append(", ");
			json.append("\"indexUploadGeneration\": ").append(event.indexUploadGeneration()).append(", ");
			json.append("\"translucentDrawOrder\": ").append(event.translucentDrawOrder()).append(", ");
			json.append("\"sorterType\": \"").append(escape(event.sorterType())).append("\", ");
			json.append("\"sourceSortedIndexHash\": ").append(event.sourceSortedIndexHash()).append(", ");
			json.append("\"rustCopiedSortedIndexHash\": ").append(event.rustCopiedSortedIndexHash()).append(", ");
			json.append("\"sourceSortedIndexSampleHash\": ").append(event.sourceSortedIndexSampleHash()).append(", ");
			json.append("\"rustCopiedSortedIndexSampleHash\": ").append(event.rustCopiedSortedIndexSampleHash()).append(", ");
			json.append("\"sortedIndexSample\": \"").append(escape(event.sortedIndexSample())).append("\", ");
			json.append("\"reason\": \"").append(escape(event.reason())).append("\"");
			json.append(" }");
		}
	}

	private static void writePhaseMap(StringBuilder json, String name, Map<String, PhaseStats> phases) {
		json.append("  \"").append(name).append("\": {");
		if (!phases.isEmpty()) {
			json.append("\n");
		}
		int index = 0;
		for (Map.Entry<String, PhaseStats> entry : phases.entrySet()) {
			if (index > 0) {
				json.append(",\n");
			}
			PhaseStats stats = entry.getValue();
			json.append("    \"").append(escape(entry.getKey())).append("\": { ");
			json.append("\"count\": ").append(stats.count).append(", ");
			json.append("\"total\": ").append(stats.totalNanos).append(", ");
			json.append("\"median\": ").append(stats.percentile(0.50)).append(", ");
			json.append("\"p95\": ").append(stats.percentile(0.95)).append(", ");
			json.append("\"p99\": ").append(stats.percentile(0.99)).append(", ");
			json.append("\"max\": ").append(stats.worstNanos).append(", ");
			json.append("\"worst\": ").append(stats.worstNanos).append(" }");
			index++;
		}
		if (!phases.isEmpty()) {
			json.append("\n  ");
		}
		json.append("}");
	}

	private static void field(StringBuilder json, String key, String value, int indent, boolean comma) {
		json.append(" ".repeat(indent)).append('"').append(key).append("\": \"").append(escape(value)).append('"');
		if (comma) {
			json.append(',');
		}
		json.append('\n');
	}

	private static long delta(long start, long end) {
		if (start < 0L || end < 0L) {
			return -1L;
		}
		return Math.max(0L, end - start);
	}

	private static long duration(long start, long end) {
		if (start < 0L || end < 0L || end < start) {
			return -1L;
		}
		return end - start;
	}

	private static long usedMemoryBytes() {
		Runtime runtime = Runtime.getRuntime();
		return runtime.totalMemory() - runtime.freeMemory();
	}

	private static long totalGcCount() {
		long total = 0L;
		for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
			long count = bean.getCollectionCount();
			if (count > 0L) {
				total += count;
			}
		}
		return total;
	}

	private static long totalGcTimeMillis() {
		long total = 0L;
		for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
			long time = bean.getCollectionTime();
			if (time > 0L) {
				total += time;
			}
		}
		return total;
	}

	private static String escape(String value) {
		return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
	}

	private static String format(double value) {
		return String.format(Locale.ROOT, "%.6f", value);
	}

	private static long relativeNanos(long originNanos, long timestampNanos) {
		if (originNanos <= 0L || timestampNanos <= 0L) {
			return -1L;
		}
		return timestampNanos - originNanos;
	}

	private static void applyArmorOverride(LocalPlayer player) {
		if (FORCED_ARMOR_VALUE < 0) {
			return;
		}
		if (!armorOverrideApplied) {
			originalArmorValueOverride = player.getArmorValueForDeterministicCapture();
			armorOverrideApplied = true;
		}
		player.setArmorValueForDeterministicCapture(Math.min(20, FORCED_ARMOR_VALUE));
	}

	private static void applyHealthOverride(LocalPlayer player) {
		// Player health diagnostics are HUD-only. Mutating actual health can trip death/readiness paths.
	}

	private static void applyGameModeOverride(Minecraft minecraft) {
		GameType gameType = forcedGameMode();
		if (gameType == null || minecraft.gameMode == null) {
			return;
		}
		if (!gameModeOverrideApplied) {
			originalGameMode = minecraft.gameMode.getPlayerMode();
			originalPreviousGameMode = minecraft.gameMode.getPreviousPlayerMode();
			gameModeOverrideApplied = true;
		}
		if (minecraft.gameMode.getPlayerMode() != gameType) {
			minecraft.gameMode.setLocalMode(gameType, minecraft.gameMode.getPreviousPlayerMode());
		}
	}

	private static void restoreArmorOverride(Minecraft minecraft) {
		if (armorOverrideApplied && minecraft.player != null) {
			minecraft.player.setArmorValueForDeterministicCapture(originalArmorValueOverride);
			armorOverrideApplied = false;
		}
		if (healthOverrideApplied && minecraft.player != null) {
			minecraft.player.setHealthForDeterministicCapture(originalHealthOverride);
			minecraft.player.setMaxHealthForDeterministicCapture(originalMaxHealthOverride);
			healthOverrideApplied = false;
		}
		if (gameModeOverrideApplied && minecraft.gameMode != null && originalGameMode != null) {
			minecraft.gameMode.setLocalMode(originalGameMode, originalPreviousGameMode);
			gameModeOverrideApplied = false;
		}
	}

	private static GameType forcedGameMode() {
		if (FORCED_GAME_MODE.isBlank()) {
			return null;
		}
		GameType gameType = GameType.byName(FORCED_GAME_MODE, null);
		if (gameType == null) {
			throw new IllegalArgumentException("unknown graphics frame benchmark game mode: " + FORCED_GAME_MODE);
		}
		return gameType;
	}

	private static String rustGalGuiControl() {
		if (Boolean.getBoolean("mattmc.dev.rustGalGui.mountHealth.disabled")) {
			return "mount-health-disabled";
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalGui.mountHealth.legacyControl")) {
			return "mount-health-legacy";
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalGui.air.disabled")) {
			return "air-disabled";
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalGui.air.legacyControl")) {
			return "air-legacy";
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalGui.hunger.disabled")) {
			return "hunger-disabled";
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalGui.hunger.legacyControl")) {
			return "hunger-legacy";
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalGui.absorption.disabled")) {
			return "absorption-disabled";
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalGui.absorption.legacyControl")) {
			return "absorption-legacy";
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalGui.playerHealth.disabled")) {
			return "player-health-disabled";
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalGui.playerHealth.legacyControl")) {
			return "player-health-legacy";
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalGui.armor.disabled")) {
			return "armor-disabled";
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalGui.armor.legacyControl")) {
			return "armor-legacy";
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalGui.disabled")) {
			return "all-disabled";
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalGui.legacyControl")) {
			return "all-legacy";
		}
		return "rust";
	}

	private static String terrainParticleRouteControl() {
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldMaterial.terrainParticle.disabled")) {
			return "disabled";
		}
		if (Boolean.getBoolean("mattmc.dev.rustGalWorldMaterial.terrainParticle.legacyControl")) {
			return "legacy";
		}
		return "rust";
	}

	private record OpenPhase(String name, long startNanos, long childNanos, Zone tracyZone) {
		OpenPhase(String name, long startNanos) {
			this(name, startNanos, 0L, null);
		}

		OpenPhase(String name, long startNanos, Zone tracyZone) {
			this(name, startNanos, 0L, tracyZone);
		}

		OpenPhase withAdditionalChild(long nanos) {
			return new OpenPhase(this.name, this.startNanos, this.childNanos + Math.max(0L, nanos), this.tracyZone);
		}

		void closeTracyZone() {
			GraphicsFrameBenchmark.closeTracyZone(this.tracyZone);
		}
	}

	private record FrameTimelineEvent(
		long frameIndex,
		long correlationId,
		long rustFrameId,
		long submissionId,
		long acquiredImage,
		long presentedImage,
		long executeStartNanos,
		long acquireStartNanos,
		long acquireEndNanos,
		long submitStartNanos,
		long submitEndNanos,
		long presentStartNanos,
		long presentEndNanos,
		long acquireDurationNanos,
		long submitDurationNanos,
		long presentDurationNanos,
		long guiSprites,
		long meshInstances,
		long meshDraws,
		long gpuFrameTotalNanos,
		long presentMode,
		long imagesInFlight,
		long availableFrameSlots
	) {
	}

	private record TerrainPerfSnapshot(
		RustGalTerrainRenderer.TerrainDiagnostics diagnostics,
		RustGalWorldPrimitiveRenderer.WorldMeshAssetMetrics meshMetrics,
		long visibleFingerprint,
		int loadedChunks,
		int renderDistance,
		long cameraSignature
	) {
		long acceptedBuildOutputs() {
			return this.diagnostics.acceptedBuildOutputs();
		}

		long registeredMeshes() {
			return this.diagnostics.registeredMeshes();
		}

		long atlasGeneration() {
			return this.diagnostics.atlasGeneration();
		}

		long texturePayloadUpdates() {
			return this.diagnostics.texturePayloadUpdates();
		}

		long invalidations() {
			return this.diagnostics.invalidations();
		}

		long failedLayerSubmissions() {
			return this.diagnostics.failedLayerSubmissions();
		}

		int activeTerrainLayers() {
			return this.diagnostics.activeTerrainLayers();
		}

		int activeSectionAssets() {
			return this.diagnostics.activeSectionAssets();
		}

		int cachedLayerAssets() {
			return this.diagnostics.cachedLayerAssets();
		}

		boolean readyForSteadyState() {
			return this.activeTerrainLayers() > 0
				&& this.activeSectionAssets() > 0
				&& this.cachedLayerAssets() > 0
				&& this.visibleFingerprint != 0L
				&& this.failedLayerSubmissions() == 0L
				&& this.meshMetrics.dirtyMeshes() == 0
				&& this.meshMetrics.dirtyTextures() == 0
				&& this.meshMetrics.pendingInstances() == 0
				&& this.meshMetrics.cachedMeshes() > 0
				&& this.meshMetrics.uploadedGeneration() >= this.meshMetrics.generation();
		}

		boolean mutationCountersNonZero() {
			return this.acceptedBuildOutputs() > 0L
				|| this.registeredMeshes() > 0L
				|| this.meshMetrics.generation() > 0L
				|| this.meshMetrics.payloadCount() > 0L
				|| this.visibleFingerprint != 0L;
		}

		boolean quiescenceKeyEquals(TerrainPerfSnapshot other) {
			return other != null
				&& this.acceptedBuildOutputs() == other.acceptedBuildOutputs()
				&& this.registeredMeshes() == other.registeredMeshes()
				&& this.diagnostics.terrainExtractionFrames() == other.diagnostics.terrainExtractionFrames()
				&& this.diagnostics.skippedUnsupportedAnimatedSections() == other.diagnostics.skippedUnsupportedAnimatedSections()
				&& this.diagnostics.skippedEmptyLayers() == other.diagnostics.skippedEmptyLayers()
				&& this.diagnostics.removedLayers() == other.diagnostics.removedLayers()
				&& this.atlasGeneration() == other.atlasGeneration()
				&& this.texturePayloadUpdates() == other.texturePayloadUpdates()
				&& this.invalidations() == other.invalidations()
				&& this.failedLayerSubmissions() == other.failedLayerSubmissions()
				&& this.cachedLayerAssets() == other.cachedLayerAssets()
				&& this.activeTerrainLayers() == other.activeTerrainLayers()
				&& this.activeSectionAssets() == other.activeSectionAssets()
				&& this.meshMetrics.generation() == other.meshMetrics.generation()
				&& this.meshMetrics.uploadedGeneration() == other.meshMetrics.uploadedGeneration()
				&& this.meshMetrics.payloadCount() == other.meshMetrics.payloadCount()
				&& this.meshMetrics.payloadBytes() == other.meshMetrics.payloadBytes()
				&& this.meshMetrics.failures() == other.meshMetrics.failures()
				&& this.meshMetrics.cachedMeshes() == other.meshMetrics.cachedMeshes()
				&& this.meshMetrics.cachedTextures() == other.meshMetrics.cachedTextures()
				&& this.meshMetrics.dirtyMeshes() == other.meshMetrics.dirtyMeshes()
				&& this.meshMetrics.dirtyTextures() == other.meshMetrics.dirtyTextures()
				&& this.meshMetrics.pendingInstances() == other.meshMetrics.pendingInstances()
				&& this.visibleFingerprint == other.visibleFingerprint
				&& this.loadedChunks == other.loadedChunks
				&& this.renderDistance == other.renderDistance
				&& this.cameraSignature == other.cameraSignature;
		}

		String changedCounters(TerrainPerfSnapshot other) {
			if (other == null) {
				return "initial-snapshot";
			}
			StringBuilder builder = new StringBuilder();
			appendChange(builder, "acceptedBuildOutputs", other.acceptedBuildOutputs(), this.acceptedBuildOutputs());
			appendChange(builder, "registeredMeshes", other.registeredMeshes(), this.registeredMeshes());
			appendChange(builder, "terrainExtractionFrames", other.diagnostics.terrainExtractionFrames(), this.diagnostics.terrainExtractionFrames());
			appendChange(builder, "skippedUnsupportedAnimatedSections", other.diagnostics.skippedUnsupportedAnimatedSections(), this.diagnostics.skippedUnsupportedAnimatedSections());
			appendChange(builder, "skippedEmptyLayers", other.diagnostics.skippedEmptyLayers(), this.diagnostics.skippedEmptyLayers());
			appendChange(builder, "removedLayers", other.diagnostics.removedLayers(), this.diagnostics.removedLayers());
			appendChange(builder, "atlasGeneration", other.atlasGeneration(), this.atlasGeneration());
			appendChange(builder, "texturePayloadUpdates", other.texturePayloadUpdates(), this.texturePayloadUpdates());
			appendChange(builder, "invalidations", other.invalidations(), this.invalidations());
			appendChange(builder, "failedLayerSubmissions", other.failedLayerSubmissions(), this.failedLayerSubmissions());
			appendChange(builder, "cachedLayerAssets", other.cachedLayerAssets(), this.cachedLayerAssets());
			appendChange(builder, "activeTerrainLayers", other.activeTerrainLayers(), this.activeTerrainLayers());
			appendChange(builder, "activeSectionAssets", other.activeSectionAssets(), this.activeSectionAssets());
			appendChange(builder, "worldMeshGeneration", other.meshMetrics.generation(), this.meshMetrics.generation());
			appendChange(builder, "worldMeshUploadedGeneration", other.meshMetrics.uploadedGeneration(), this.meshMetrics.uploadedGeneration());
			appendChange(builder, "payloadCount", other.meshMetrics.payloadCount(), this.meshMetrics.payloadCount());
			appendChange(builder, "payloadBytes", other.meshMetrics.payloadBytes(), this.meshMetrics.payloadBytes());
			appendChange(builder, "meshFailures", other.meshMetrics.failures(), this.meshMetrics.failures());
			appendChange(builder, "cachedMeshes", other.meshMetrics.cachedMeshes(), this.meshMetrics.cachedMeshes());
			appendChange(builder, "cachedTextures", other.meshMetrics.cachedTextures(), this.meshMetrics.cachedTextures());
			appendChange(builder, "dirtyMeshes", other.meshMetrics.dirtyMeshes(), this.meshMetrics.dirtyMeshes());
			appendChange(builder, "dirtyTextures", other.meshMetrics.dirtyTextures(), this.meshMetrics.dirtyTextures());
			appendChange(builder, "pendingInstances", other.meshMetrics.pendingInstances(), this.meshMetrics.pendingInstances());
			appendChange(builder, "visibleFingerprint", other.visibleFingerprint, this.visibleFingerprint);
			appendChange(builder, "loadedChunks", other.loadedChunks, this.loadedChunks);
			appendChange(builder, "renderDistance", other.renderDistance, this.renderDistance);
			appendChange(builder, "cameraSignature", other.cameraSignature, this.cameraSignature);
			return builder.isEmpty() ? "none" : builder.toString();
		}

		private static void appendChange(StringBuilder builder, String name, long before, long after) {
			if (before == after) {
				return;
			}
			if (!builder.isEmpty()) {
				builder.append(';');
			}
			builder.append(name).append('=').append(before).append("->").append(after);
		}
	}

	private static final class PhaseStats {
		private long count;
		private long totalNanos;
		private long worstNanos;
		private final ArrayList<Long> samples = new ArrayList<>();

		private void add(long nanos) {
			this.count++;
			this.totalNanos += nanos;
			this.worstNanos = Math.max(this.worstNanos, nanos);
			this.samples.add(nanos);
		}

		private long percentile(double percentile) {
			if (this.samples.isEmpty()) {
				return 0L;
			}
			ArrayList<Long> sorted = new ArrayList<>(this.samples);
			sorted.sort(Long::compare);
			int index = (int)Math.floor((sorted.size() - 1) * percentile);
			return sorted.get(Math.max(0, Math.min(sorted.size() - 1, index)));
		}
	}
}
