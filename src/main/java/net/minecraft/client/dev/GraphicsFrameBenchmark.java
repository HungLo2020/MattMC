package net.minecraft.client.dev;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

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

/**
 * Dev-only frame sampler for graphics migration audits.
 *
 * <p>Enabled only by {@code -Dmattmc.dev.graphicsFrameBenchmark=true}.
 */
public final class GraphicsFrameBenchmark {
	private static final boolean ENABLED = Boolean.getBoolean("mattmc.dev.graphicsFrameBenchmark");
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
	private static final boolean STOP_AFTER_COMPLETE = Boolean.parseBoolean(System.getProperty("mattmc.dev.graphicsFrameBenchmark.stopAfterComplete", "true"));
	private static final Path STATUS_PATH = Path.of(System.getProperty("mattmc.dev.graphicsFrameBenchmark.status", "run/graphics_frame_benchmark.json"));

	private static final ArrayDeque<OpenPhase> PHASE_STACK = new ArrayDeque<>();
	private static final Map<String, PhaseStats> EXCLUSIVE_PHASES = new LinkedHashMap<>();
	private static final Map<String, PhaseStats> NESTED_PHASES = new LinkedHashMap<>();
	private static final Map<String, Integer> SUBMITTED_WORK_COUNTS = new LinkedHashMap<>();
	private static final Map<Long, Map<String, Set<String>>> SUBMITTED_WORK_BY_FRAME = new LinkedHashMap<>();
	private static final List<Long> FRAME_NANOS = new ArrayList<>();
	private static boolean initialized;
	private static boolean complete;
	private static boolean failed;
	private static boolean stopIssued;
	private static boolean frameActive;
	private static boolean measurementFrame;
	private static long initializationWaitFrames;
	private static long frameIndex;
	private static long settledFrameIndex = -1L;
	private static long measurementStartNanos = -1L;
	private static long measurementEndNanos = -1L;
	private static long gcCountAtStart = -1L;
	private static long gcTimeAtStart = -1L;
	private static long gcCountAtEnd = -1L;
	private static long gcTimeAtEnd = -1L;
	private static long usedMemoryAtStart = -1L;
	private static long usedMemoryAtEnd = -1L;
	private static int displayedFpsAtMeasurementStart = -1;
	private static int displayedFpsAtMeasurementEnd = -1;
	private static Vec3 initialPosition = Vec3.ZERO;
	private static float initialYaw;
	private static float initialPitch;
	private static String dimension = "missing";
	private static String failureReason = "";
	private static String lastReadinessBlocker = "not checked";

	private GraphicsFrameBenchmark() {
	}

	public static void beginFrame(Minecraft minecraft) {
		if (!ENABLED) {
			return;
		}
		if ((complete || failed) && STOP_AFTER_COMPLETE && !stopIssued) {
			stopIssued = true;
			minecraft.stop();
			return;
		}
		if (complete || failed) {
			return;
		}
		frameActive = ensureInitialized(minecraft) && !complete && !failed;
		measurementFrame = false;
		PHASE_STACK.clear();
		if (!frameActive) {
			return;
		}
		holdPlayerStillAndApplyCameraPath(minecraft);
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
		measurementFrame = framesAfterSettle >= WARMUP_FRAMES && FRAME_NANOS.size() < MEASURE_FRAMES;
		if (measurementFrame && FRAME_NANOS.isEmpty()) {
			measurementStartNanos = System.nanoTime();
			displayedFpsAtMeasurementStart = minecraft.getFps();
			gcCountAtStart = totalGcCount();
			gcTimeAtStart = totalGcTimeMillis();
			usedMemoryAtStart = usedMemoryBytes();
		}
	}

	public static void endFrame(Minecraft minecraft, long frameNanos) {
		if (!ENABLED || !frameActive || complete || failed) {
			return;
		}
		if (measurementFrame) {
			FRAME_NANOS.add(frameNanos);
			measurementEndNanos = System.nanoTime();
			displayedFpsAtMeasurementEnd = minecraft.getFps();
			gcCountAtEnd = totalGcCount();
			gcTimeAtEnd = totalGcTimeMillis();
			usedMemoryAtEnd = usedMemoryBytes();
		}
		frameIndex++;
		if (FRAME_NANOS.size() >= MEASURE_FRAMES) {
			complete = true;
			writeStatus(minecraft, "complete");
			if (STOP_AFTER_COMPLETE && !stopIssued) {
				stopIssued = true;
				minecraft.stop();
			}
		} else if ((frameIndex % 60L) == 0L) {
			writeStatus(minecraft, measurementFrame ? "measuring" : "warming_or_settling");
		}
		frameActive = false;
		measurementFrame = false;
		PHASE_STACK.clear();
	}

	public static void beginPhase(String name) {
		if (!ENABLED || !frameActive) {
			return;
		}
		PHASE_STACK.push(new OpenPhase(name, System.nanoTime()));
	}

	public static void endPhase(String name) {
		if (!ENABLED || !frameActive || PHASE_STACK.isEmpty()) {
			return;
		}
		long now = System.nanoTime();
		OpenPhase phase = PHASE_STACK.pop();
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
		SUBMITTED_WORK_BY_FRAME
			.computeIfAbsent(frameIndex, ignored -> new LinkedHashMap<>())
			.computeIfAbsent(normalizedFamily, ignored -> new LinkedHashSet<>())
			.add(normalizedIdentity);
		long minimumFrame = Math.max(0L, frameIndex - 512L);
		SUBMITTED_WORK_BY_FRAME.keySet().removeIf(frame -> frame < minimumFrame);
	}

	private static boolean ensureInitialized(Minecraft minecraft) {
		if (initialized) {
			dismissKnownGameplayScreen(minecraft);
			boolean ready = minecraft.level != null && minecraft.player != null && minecraft.getConnection() != null && minecraft.screen == null && minecraft.getOverlay() == null;
			if (!ready) {
				recordInitializationBlocker(minecraft);
			}
			return ready;
		}
		LocalPlayer player = minecraft.player;
		dismissKnownGameplayScreen(minecraft);
		if (minecraft.level == null || player == null || minecraft.getConnection() == null || minecraft.screen != null || minecraft.getOverlay() != null) {
			recordInitializationBlocker(minecraft);
			return false;
		}
		initialized = true;
		initialPosition = new Vec3(CAMERA_X, CAMERA_Y, CAMERA_Z);
		initialYaw = CAMERA_YAW;
		initialPitch = CAMERA_PITCH;
		player.setPos(initialPosition);
		player.setYRot(initialYaw);
		player.setXRot(initialPitch);
		dimension = minecraft.level.dimension().location().toString();
		writeStatus(minecraft, "initialized");
		return true;
	}

	private static void dismissKnownGameplayScreen(Minecraft minecraft) {
		disableVoxelMapWelcomeScreen();
		if (minecraft.level == null || minecraft.player == null || minecraft.getConnection() == null || minecraft.getOverlay() != null || minecraft.screen == null) {
			return;
		}
		String screen = minecraft.screen.getClass().getSimpleName();
		if ("PauseScreen".equals(screen) || "GuiWelcomeScreen".equals(screen)) {
			lastReadinessBlocker = "auto-dismissed screen=" + screen;
			minecraft.setScreen(null);
			writeStatus(minecraft, "dismissed_gameplay_screen");
		}
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
		initializationWaitFrames++;
		lastReadinessBlocker = readinessSummary(minecraft);
		if (initializationWaitFrames >= MAX_SETTLE_FRAMES) {
			fail(minecraft, "timed out waiting for gameplay entry: " + lastReadinessBlocker);
		} else if ((initializationWaitFrames % 60L) == 0L || initializationWaitFrames == 1L) {
			writeStatus(minecraft, "waiting_for_gameplay");
		}
	}

	private static String readinessSummary(Minecraft minecraft) {
		String screen = minecraft.screen == null ? "none" : minecraft.screen.getClass().getSimpleName();
		String overlay = minecraft.getOverlay() == null ? "none" : minecraft.getOverlay().getClass().getSimpleName();
		return "level=" + (minecraft.level != null)
			+ ", player=" + (minecraft.player != null)
			+ ", connection=" + (minecraft.getConnection() != null)
			+ ", screen=" + screen
			+ ", overlay=" + overlay;
	}

	private static void holdPlayerStillAndApplyCameraPath(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}
		player.input.keyPresses = Input.EMPTY;
		player.xxa = 0.0F;
		player.zza = 0.0F;
		player.setSprinting(false);
		player.setShiftKeyDown(false);
		player.setDeltaMovement(Vec3.ZERO);
		player.setPos(initialPosition);
		double period = Math.max(1.0, WARMUP_FRAMES + MEASURE_FRAMES);
		float yaw = initialYaw + (float)Math.sin((frameIndex / period) * Math.PI * 2.0) * YAW_DELTA;
		player.setYRot(yaw);
		player.setXRot(initialPitch);
		player.yRotO = yaw;
		player.xRotO = initialPitch;
		player.yHeadRot = yaw;
		player.yHeadRotO = yaw;
		player.yBodyRot = yaw;
		player.yBodyRotO = yaw;
	}

	private static void fail(Minecraft minecraft, String reason) {
		if (failed || complete) {
			return;
		}
		failed = true;
		failureReason = reason;
		writeStatus(minecraft, "failed");
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
		json.append("  \"initializationWaitFrames\": ").append(initializationWaitFrames).append(",\n");
		field(json, "lastReadinessBlocker", lastReadinessBlocker, 2, true);
		json.append("  \"framesSeenIncludingSettleWarmup\": ").append(frameIndex).append(",\n");
		json.append("  \"settledFrameIndex\": ").append(settledFrameIndex).append(",\n");
		json.append("  \"measuredFrameCount\": ").append(FRAME_NANOS.size()).append(",\n");
		json.append("  \"window\": { \"width\": ").append(minecraft.getWindow().getWidth()).append(", \"height\": ").append(minecraft.getWindow().getHeight()).append(" },\n");
		writeRuntimeState(json, minecraft);
		json.append(",\n");
		json.append("  \"cameraPath\": { \"type\": \"settled-sine-yaw\", \"yawDelta\": ").append(format(YAW_DELTA))
			.append(", \"initialYaw\": ").append(format(initialYaw))
			.append(", \"initialPitch\": ").append(format(initialPitch))
			.append(", \"initialPosition\": { \"x\": ").append(format(initialPosition.x))
			.append(", \"y\": ").append(format(initialPosition.y))
			.append(", \"z\": ").append(format(initialPosition.z)).append(" } },\n");
		writeValidity(json);
		json.append(",\n");
		json.append("  \"java\": {\n");
		json.append("    \"gcCountDelta\": ").append(delta(gcCountAtStart, gcCountAtEnd)).append(",\n");
		json.append("    \"gcTimeMillisDelta\": ").append(delta(gcTimeAtStart, gcTimeAtEnd)).append(",\n");
		json.append("    \"usedMemoryBytesAtStart\": ").append(usedMemoryAtStart).append(",\n");
		json.append("    \"usedMemoryBytesAtEnd\": ").append(usedMemoryAtEnd).append("\n");
		json.append("  },\n");
		writeSamples(json);
		json.append(",\n");
		writeStringIntMap(json, "submittedWorkCounts", SUBMITTED_WORK_COUNTS);
		json.append(",\n");
		writePhaseMap(json, "exclusivePhaseNanos", EXCLUSIVE_PHASES);
		json.append(",\n");
		writePhaseMap(json, "nestedPhaseNanos", NESTED_PHASES);
		json.append("\n}\n");
		return json.toString();
	}

	private static void writeRuntimeState(StringBuilder json, Minecraft minecraft) {
		int loadedChunks = minecraft.level == null ? -1 : minecraft.level.getChunkSource().getLoadedChunksCount();
		int entityCount = minecraft.level == null ? -1 : minecraft.level.getEntityCount();
		int playerCount = minecraft.level == null ? -1 : minecraft.level.players().size();
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
		boolean displayedRequired = FRAME_NANOS.size() >= DISPLAY_FPS_MIN_FRAMES && wallClock >= DISPLAY_FPS_MIN_NANOS;
		boolean displayedOk = !displayedRequired || displayedRatio < 0.0 || displayedRatio <= DISPLAY_FPS_TOLERANCE;
		json.append("  \"validity\": {\n");
		json.append("    \"sampleSumNanos\": ").append(sampleSum).append(",\n");
		json.append("    \"wallClockMeasurementNanos\": ").append(wallClock).append(",\n");
		json.append("    \"wallClockRelativeError\": ").append(format(wallClockRatio)).append(",\n");
		json.append("    \"wallClockCheckPassed\": ").append(wallClockOk).append(",\n");
		json.append("    \"measuredAverageFps\": ").append(format(measuredFps)).append(",\n");
		json.append("    \"displayedFpsAtMeasurementStart\": ").append(displayedFpsAtMeasurementStart).append(",\n");
		json.append("    \"displayedFpsAtMeasurementEnd\": ").append(displayedFpsAtMeasurementEnd).append(",\n");
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

	private record OpenPhase(String name, long startNanos, long childNanos) {
		OpenPhase(String name, long startNanos) {
			this(name, startNanos, 0L);
		}

		OpenPhase withAdditionalChild(long nanos) {
			return new OpenPhase(this.name, this.startNanos, this.childNanos + Math.max(0L, nanos));
		}
	}

	private static final class PhaseStats {
		private long count;
		private long totalNanos;
		private long worstNanos;

		private void add(long nanos) {
			this.count++;
			this.totalNanos += nanos;
			this.worstNanos = Math.max(this.worstNanos, nanos);
		}
	}
}
