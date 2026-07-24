package net.minecraft.client.dev;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.irisshaders.iris.uniforms.SystemTimeUniforms;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;
import net.vulkanic.VulkanicAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

/**
 * Development-only deterministic camera capture hook.
 *
 * <p>This is inert unless {@code -Dmattmc.dev.deterministicCameraCapture=true} is set.
 * It does not move the player or mutate world data; it only suppresses local movement
 * input, applies a short yaw-only camera sequence, captures screenshots after a few
 * rendered frames at each pose, and writes metadata for capture harnesses.</p>
 */
public final class DeterministicCameraCapture {
	private static final Logger LOGGER = LoggerFactory.getLogger("MattMC-DeterministicCapture");
	private static final boolean ENABLED = Boolean.getBoolean("mattmc.dev.deterministicCameraCapture");
	private static final int FRAMES_PER_POSE = Math.max(1, Integer.getInteger("mattmc.dev.deterministicCameraCapture.framesPerPose", 8));
	private static final int ACK_TIMEOUT_FRAMES = Math.max(1, Integer.getInteger("mattmc.dev.deterministicCameraCapture.ackTimeoutFrames", 600));
	private static final int POSE_COUNT = Math.max(1, Math.min(4, Integer.getInteger("mattmc.dev.deterministicCameraCapture.poseCount", 4)));
	private static final float YAW_DELTA = Float.parseFloat(System.getProperty("mattmc.dev.deterministicCameraCapture.yawDelta", "35.0"));
	private static final boolean STOP_AFTER_COMPLETE = Boolean.parseBoolean(System.getProperty("mattmc.dev.deterministicCameraCapture.stopAfterComplete", "true"));
	private static final boolean INTERNAL_SCREENSHOTS = Boolean.parseBoolean(System.getProperty("mattmc.dev.deterministicCameraCapture.internalScreenshots", "false"));
	private static final int SETTLED_READY_FRAMES = Math.max(0, Integer.getInteger("mattmc.dev.deterministicCameraCapture.settledReadyFrames", 0));
	private static final int SETTLED_READY_MAX_WAIT_FRAMES = Math.max(1, Integer.getInteger("mattmc.dev.deterministicCameraCapture.settledReadyMaxWaitFrames", 900));
	private static final Set<String> SETTLED_READY_FAMILIES = parseSettledReadyFamilies();
	private static final String FORCED_CAMERA_TYPE = System.getProperty("mattmc.dev.deterministicCameraCapture.cameraType", "").trim();
	private static final int FORCED_SELECTED_HOTBAR_SLOT = Integer.getInteger("mattmc.dev.deterministicCameraCapture.selectedHotbarSlot", 0);
	private static final float FIXED_VIGNETTE_BRIGHTNESS =
		Float.parseFloat(System.getProperty("mattmc.dev.deterministicCameraCapture.vignetteBrightness", "1.0"));
	private static final Path METADATA_PATH = Path.of(System.getProperty("mattmc.dev.deterministicCameraCapture.metadata", "run/deterministic_camera_capture.json"));
	private static final Path SCREENSHOT_DIR = Path.of(System.getProperty("mattmc.dev.deterministicCameraCapture.screenshotDir", "run/deterministic_camera_capture"));

	private static final List<PoseCapture> CAPTURES = new ArrayList<>();
	private static boolean initialized;
	private static boolean complete;
	private static boolean failed;
	private static boolean stopIssued;
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
	private static long startedGameTime;
	private static long renderedFrameIndex;
	private static int windowWidth;
	private static int windowHeight;
	private static boolean settledReadyGateSatisfied;
	private static int framesWaitingForSettledReady;
	private static CameraType originalCameraType;
	private static int originalSelectedHotbarSlot;
	private static final Map<Long, Map<String, Set<String>>> SUBMITTED_WORK_BY_FRAME = new LinkedHashMap<>();

	private DeterministicCameraCapture() {
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
		player.setDeltaMovement(Vec3.ZERO);
		applyRuntimeOverrides(minecraft, player);
		if (initialized && initialPosition != null && initialPose != null) {
			player.setPos(initialPosition);
			player.setOldPosAndRot(initialPosition, initialPose.yaw(), initialPose.pitch());
		}
	}

	public static void beforeRender(Minecraft minecraft) {
		if (!ENABLED || complete || failed) {
			return;
		}
		if (!ensureInitialized(minecraft)) {
			return;
		}
		if (poseIndex >= poses.length) {
			stabilizeGuiState(minecraft);
			applyPose(minecraft.player, initialPose);
			return;
		}

		stabilizeGuiState(minecraft);
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
			applyPose(minecraft.player, initialPose);
			return;
		}

		stabilizeGuiState(minecraft);
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

	public static boolean isReadyForShaderInputParityPoseDiagnostics() {
		return !ENABLED || !initialized || SETTLED_READY_FRAMES <= 0 || settledReadyGateSatisfied || poseIndex > 0;
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
		originalSelectedHotbarSlot = player.getInventory().getSelectedSlot();
		applyRuntimeOverrides(minecraft, player);
		initialPose = new Pose("initial", player.getYRot(), player.getXRot());
		stabilizeGuiState(minecraft);
		Pose[] fullSequence = new Pose[] {
			initialPose,
			new Pose("right", initialPose.yaw() + YAW_DELTA, initialPose.pitch()),
			new Pose("left", initialPose.yaw() - YAW_DELTA, initialPose.pitch()),
			new Pose("return", initialPose.yaw(), initialPose.pitch())
		};
		poses = java.util.Arrays.copyOf(fullSequence, POSE_COUNT);
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
		return summary.toString();
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
		CAPTURES.add(new PoseCapture(
			captureIndex,
			pose.name(),
			currentScreenshotPath.toAbsolutePath().toString(),
			minecraft.level == null ? "missing" : minecraft.level.dimension().location().toString(),
			player == null ? Vec3.ZERO : player.position(),
			pose.yaw(),
			pose.pitch(),
			player == null ? 0.0F : player.getYRot(),
			player == null ? 0.0F : player.getXRot(),
			renderedFrameIndex,
			minecraft.level == null ? -1L : minecraft.level.getGameTime()
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
		player.setYRot(pose.yaw());
		player.setXRot(pose.pitch());
		player.yRotO = pose.yaw();
		player.xRotO = pose.pitch();
		player.yHeadRot = pose.yaw();
		player.yHeadRotO = pose.yaw();
		player.yBodyRot = pose.yaw();
		player.yBodyRotO = pose.yaw();
	}

	private static void applyRuntimeOverrides(Minecraft minecraft, LocalPlayer player) {
		CameraType cameraType = forcedCameraType();
		if (cameraType != null && minecraft.options.getCameraType() != cameraType) {
			minecraft.options.setCameraType(cameraType);
			minecraft.gameRenderer.checkEntityPostEffect(cameraType.isFirstPerson() ? minecraft.getCameraEntity() : null);
		}
		if (FORCED_SELECTED_HOTBAR_SLOT > 0) {
			int selectedSlot = Math.max(1, Math.min(9, FORCED_SELECTED_HOTBAR_SLOT)) - 1;
			if (player.getInventory().getSelectedSlot() != selectedSlot) {
				player.getInventory().setSelectedSlot(selectedSlot);
			}
		}
	}

	private static void restoreRuntimeOverrides(Minecraft minecraft) {
		if (minecraft.player != null && FORCED_SELECTED_HOTBAR_SLOT > 0 && originalSelectedHotbarSlot >= 0 && originalSelectedHotbarSlot < 9) {
			minecraft.player.getInventory().setSelectedSlot(originalSelectedHotbarSlot);
		}
		if (!FORCED_CAMERA_TYPE.isBlank() && originalCameraType != null && minecraft.options.getCameraType() != originalCameraType) {
			minecraft.options.setCameraType(originalCameraType);
			minecraft.gameRenderer.checkEntityPostEffect(originalCameraType.isFirstPerson() ? minecraft.getCameraEntity() : null);
		}
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
		String json = "{\n"
			+ "  \"status\": \"failed\",\n"
			+ "  \"reason\": \"" + escape(reason) + "\"\n"
			+ "}\n";
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
		json.append("  \"selectedHotbarSlot\": ").append(player == null ? -1 : currentSelectedHotbarSlot(player)).append(",\n");
		json.append("  \"framesPerPose\": ").append(FRAMES_PER_POSE).append(",\n");
		json.append("  \"settledReadyFrames\": ").append(SETTLED_READY_FRAMES).append(",\n");
		json.append("  \"settledReadyMaxWaitFrames\": ").append(SETTLED_READY_MAX_WAIT_FRAMES).append(",\n");
		json.append("  \"settledReadyGateSatisfied\": ").append(settledReadyGateSatisfied).append(",\n");
		appendField(json, "settledReadySummary", settledReadySummary()).append(",\n");
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
				json.append("      \"gameTime\": ").append(capture.gameTime()).append("\n");
			json.append("    }").append(i + 1 == CAPTURES.size() ? "\n" : ",\n");
		}
		json.append("  ]\n");
		json.append("}\n");

		try {
			Files.writeString(METADATA_PATH, json.toString(), StandardCharsets.UTF_8);
		} catch (IOException exception) {
			LOGGER.error("Unable to write deterministic capture metadata", exception);
		}
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

	private static StringBuilder appendPoseObject(StringBuilder json, String key, Pose pose) {
		json.append("  \"").append(key).append("\": { \"yaw\": ")
			.append(format(pose.yaw())).append(", \"pitch\": ")
			.append(format(pose.pitch())).append(" }");
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

	private record Pose(String name, float yaw, float pitch) {
	}

	private record PoseCapture(
		int index,
		String poseName,
		String screenshot,
		String dimension,
		Vec3 position,
		float requestedYaw,
		float requestedPitch,
		float observedYaw,
		float observedPitch,
		long renderedFrameIndex,
		long gameTime
	) {
	}
}
