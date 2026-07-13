package net.minecraft.client.dev;

import net.minecraft.client.Minecraft;
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
import java.util.List;
import java.util.Locale;

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
	private static final float YAW_DELTA = Float.parseFloat(System.getProperty("mattmc.dev.deterministicCameraCapture.yawDelta", "35.0"));
	private static final boolean STOP_AFTER_COMPLETE = Boolean.parseBoolean(System.getProperty("mattmc.dev.deterministicCameraCapture.stopAfterComplete", "true"));
	private static final Path METADATA_PATH = Path.of(System.getProperty("mattmc.dev.deterministicCameraCapture.metadata", "run/deterministic_camera_capture.json"));
	private static final Path SCREENSHOT_DIR = Path.of(System.getProperty("mattmc.dev.deterministicCameraCapture.screenshotDir", "run/deterministic_camera_capture"));

	private static final List<PoseCapture> CAPTURES = new ArrayList<>();
	private static boolean initialized;
	private static boolean complete;
	private static boolean failed;
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

	private DeterministicCameraCapture() {
	}

	public static void beforeTick(Minecraft minecraft) {
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
			applyPose(minecraft.player, initialPose);
			return;
		}

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
			applyPose(minecraft.player, initialPose);
			return;
		}

		applyPose(minecraft.player, poses[poseIndex]);
		renderedFrameIndex++;
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
		if (renderedFramesAtPose < FRAMES_PER_POSE) {
			return;
		}

		VulkanicAPI.traceScopedCompositeColortex0PoseBoundary();
		requestCurrentPoseScreenshot(minecraft);
		renderedFramesAtPose = 0;
	}

	public static boolean isEnabledForDiagnostics() {
		return ENABLED && initialized && !failed;
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
		initialPose = new Pose("initial", player.getYRot(), player.getXRot());
		poses = new Pose[] {
			initialPose,
			new Pose("right", initialPose.yaw() + YAW_DELTA, initialPose.pitch()),
			new Pose("left", initialPose.yaw() - YAW_DELTA, initialPose.pitch()),
			new Pose("return", initialPose.yaw(), initialPose.pitch())
		};
		startedGameTime = level.getGameTime();
		windowWidth = minecraft.getWindow().getWidth();
		windowHeight = minecraft.getWindow().getHeight();
		initialized = true;
		LOGGER.info(
			"Deterministic camera capture started dimension={} pos=({}, {}, {}) yaw={} pitch={} framesPerPose={} yawDelta={} screenshots={}",
			initialDimension,
			initialPosition.x,
			initialPosition.y,
			initialPosition.z,
			initialPose.yaw(),
			initialPose.pitch(),
			FRAMES_PER_POSE,
			YAW_DELTA,
			SCREENSHOT_DIR
		);
		writeMetadata(minecraft, "running");
		return true;
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
		LOGGER.info("Deterministic camera capture complete metadata={}", METADATA_PATH);
		if (STOP_AFTER_COMPLETE) {
			minecraft.stop();
		}
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
		json.append("  \"framesPerPose\": ").append(FRAMES_PER_POSE).append(",\n");
		json.append("  \"ackTimeoutFrames\": ").append(ACK_TIMEOUT_FRAMES).append(",\n");
			json.append("  \"yawDelta\": ").append(format(YAW_DELTA)).append(",\n");
			json.append("  \"deterministicTemporalParity\": { \"enabled\": ").append(SystemTimeUniforms.isDeterministicTemporalParityEnabled())
				.append(", \"frameIndex\": ").append(deterministicTemporalFrameIndex())
				.append(", \"frameCounter\": ").append(SystemTimeUniforms.deterministicTemporalFrameCounter())
				.append(", \"frameTime\": ").append(format(SystemTimeUniforms.deterministicTemporalFrameTime()))
				.append(", \"frameTimeCounter\": ").append(format(SystemTimeUniforms.deterministicTemporalFrameTimeCounter()))
				.append(", \"frameTimeSmooth\": ").append(format(SystemTimeUniforms.deterministicTemporalFrameTimeSmooth()))
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
