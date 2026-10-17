package net.minecraft.client.dev;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Unattended look-around workload for performance runs
 * ({@code DevUtils/tests/rendering/RunScriptedLook.py}). Enabled only by
 * {@code -Dmattmc.dev.scriptedCamera=true}.
 *
 * <p>Once the player is in a world with no screen open, every frame sets the
 * camera to a continuous sweep: yaw oscillates side to side and pitch up and
 * down on independent sine periods, so the visible terrain, Distant Horizons
 * sections and rotating minimap change every frame. After
 * {@code mattmc.dev.scriptedCamera.seconds} in the world the client stops
 * normally, so recorders flush and JFR dumps.
 *
 * <p>Optional timed actions (seconds since the sweep started):
 * {@code .enableShadersAt} turns shaders on mid-world, as the shader-pack
 * screen does, {@code .disableShadersAt} turns them off again, {@code .openWorldMapAt}
 * opens VoxelMap's world map (the M key), {@code .crashAt} throws from the
 * frame loop to exercise Minecraft's emergency save and crash, and {@code .screenshotsAt} (comma separated) saves
 * {@code screenshots/scripted-<seconds>s.png} into the game directory.
 */
public final class ScriptedCameraSweep {
	private static final boolean ENABLED = Boolean.getBoolean("mattmc.dev.scriptedCamera");
	private static final double SECONDS = doubleProperty("seconds", 150.0);
	private static final double YAW_AMPLITUDE = doubleProperty("yawAmplitude", 150.0);
	private static final double YAW_PERIOD = doubleProperty("yawPeriod", 2.4);
	private static final double PITCH_AMPLITUDE = doubleProperty("pitchAmplitude", 60.0);
	private static final double PITCH_PERIOD = doubleProperty("pitchPeriod", 1.6);
	private static final double ENABLE_SHADERS_AT = doubleProperty("enableShadersAt", -1.0);
	private static final double DISABLE_SHADERS_AT = doubleProperty("disableShadersAt", -1.0);
	private static final double OPEN_WORLD_MAP_AT = doubleProperty("openWorldMapAt", -1.0);
	private static final double CRASH_AT = doubleProperty("crashAt", -1.0);
	/** {@code toggle} (keybind) or {@code screen} (the shader-pack screen's Apply). */
	private static final String ENABLE_SHADERS_VIA = System.getProperty("mattmc.dev.scriptedCamera.enableShadersVia", "toggle");
	private static final double[] SCREENSHOTS_AT = doubleListProperty("screenshotsAt");

	private static long startNanos;
	private static float baseYaw;
	private static boolean stopIssued;
	private static boolean shadersEnabled;
	private static boolean shadersDisabled;
	private static boolean worldMapOpened;
	private static int screenshotsTaken;

	private ScriptedCameraSweep() {}

	private static double doubleProperty(String name, double fallback) {
		String value = System.getProperty("mattmc.dev.scriptedCamera." + name);
		if (value == null || value.isBlank()) return fallback;
		return Double.parseDouble(value.trim());
	}

	private static double[] doubleListProperty(String name) {
		String value = System.getProperty("mattmc.dev.scriptedCamera." + name);
		if (value == null || value.isBlank()) return new double[0];
		double[] values = java.util.Arrays.stream(value.split(",")).map(String::trim)
			.mapToDouble(Double::parseDouble).toArray();
		java.util.Arrays.sort(values);
		return values;
	}

	/** Yaw (degrees) relative to the starting yaw at {@code seconds} into the sweep. */
	static float yawOffset(double seconds) {
		return (float)(YAW_AMPLITUDE * Math.sin(2.0 * Math.PI * seconds / YAW_PERIOD));
	}

	/** Pitch (degrees, clamped to the vanilla range) at {@code seconds} into the sweep. */
	static float pitch(double seconds) {
		double value = PITCH_AMPLITUDE * Math.sin(2.0 * Math.PI * seconds / PITCH_PERIOD);
		return (float)Math.max(-90.0, Math.min(90.0, value));
	}

	private static void takeDueScreenshot(Minecraft minecraft, double elapsed) {
		if (screenshotsTaken < SCREENSHOTS_AT.length && elapsed >= SCREENSHOTS_AT[screenshotsTaken]) {
			String name = "scripted-" + Math.round(SCREENSHOTS_AT[screenshotsTaken]) + "s.png";
			screenshotsTaken++;
			net.minecraft.client.Screenshot.grab(minecraft.gameDirectory, name, minecraft.getMainRenderTarget(), 1,
				message -> System.out.println("MattMC scripted camera screenshot " + name + ": " + message.getString()));
		}
	}

	/** Called once per client frame from {@code Minecraft.runTick}. */
	public static void beforeFrame(Minecraft minecraft) {
		if (!ENABLED || stopIssued) return;
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null) return;
		long now = System.nanoTime();
		if (minecraft.screen != null) {
			// A screen opened by a timed action (the world map) still gets its
			// screenshots and the scheduled stop; the camera and other actions wait.
			if (startNanos != 0L) {
				double elapsed = (now - startNanos) / 1.0e9;
				if (elapsed >= SECONDS) {
					stopIssued = true;
					System.out.println("MattMC scripted camera sweep complete after " + elapsed + "s; stopping client");
					minecraft.stop();
				} else {
					takeDueScreenshot(minecraft, elapsed);
				}
			}
			return;
		}
		if (startNanos == 0L) {
			startNanos = now;
			baseYaw = player.getYRot();
			System.out.println("MattMC scripted camera sweep started: seconds=" + SECONDS
				+ " yaw=" + YAW_AMPLITUDE + "deg/" + YAW_PERIOD + "s pitch=" + PITCH_AMPLITUDE + "deg/" + PITCH_PERIOD + "s");
		}
		double elapsed = (now - startNanos) / 1.0e9;
		if (elapsed >= SECONDS) {
			stopIssued = true;
			System.out.println("MattMC scripted camera sweep complete after " + elapsed + "s; stopping client");
			minecraft.stop();
			return;
		}
		if (ENABLE_SHADERS_AT >= 0.0 && !shadersEnabled && elapsed >= ENABLE_SHADERS_AT) {
			shadersEnabled = true;
			System.out.println("MattMC scripted camera enabling shaders at " + elapsed + "s");
			if ("screen".equals(ENABLE_SHADERS_VIA)) {
				// ShaderPackScreen.applyChanges: enable, then reapply the pack when
				// the first load requests an option reset.
				var config = net.irisshaders.iris.apiimpl.IrisApiV0Impl.INSTANCE.getConfig();
				config.setShadersEnabledAndApply(true);
				if (net.irisshaders.iris.Iris.shouldResetShaderPackOptionsOnNextReload()) {
					net.irisshaders.iris.Iris.getIrisConfig().getShaderPackName()
						.ifPresent(net.irisshaders.iris.Iris.getIrisConfig()::setShaderPackName);
					config.setShadersEnabledAndApply(true);
				}
			} else {
				try {
					net.irisshaders.iris.Iris.toggleShaders(minecraft, true);
				} catch (java.io.IOException error) {
					throw new java.io.UncheckedIOException(error);
				}
			}
		}
		if (DISABLE_SHADERS_AT >= 0.0 && !shadersDisabled && elapsed >= DISABLE_SHADERS_AT) {
			shadersDisabled = true;
			System.out.println("MattMC scripted camera disabling shaders at " + elapsed + "s");
			try {
				net.irisshaders.iris.Iris.toggleShaders(minecraft, false);
			} catch (java.io.IOException error) {
				throw new java.io.UncheckedIOException(error);
			}
		}
		if (CRASH_AT >= 0.0 && elapsed >= CRASH_AT) {
			System.out.println("MattMC scripted camera forcing a client crash at " + elapsed + "s");
			throw new IllegalStateException("MattMC scripted camera forced crash (crashAt)");
		}
		if (OPEN_WORLD_MAP_AT >= 0.0 && !worldMapOpened && elapsed >= OPEN_WORLD_MAP_AT) {
			worldMapOpened = true;
			System.out.println("MattMC scripted camera opening the world map at " + elapsed + "s");
			minecraft.setScreen(new net.voxelmap.persistent.GuiPersistentMap(null));
			return;
		}
		takeDueScreenshot(minecraft, elapsed);
		// The sweep stands in for mouse movement: without input the vanilla AFK
		// throttle caps the client at 30 FPS after 60 s.
		minecraft.getFramerateLimitTracker().onInputReceived();
		float yaw = baseYaw + yawOffset(elapsed);
		float pitch = pitch(elapsed);
		// Set current and previous rotation so the frame renders exactly this pose.
		player.setYRot(yaw);
		player.setXRot(pitch);
		player.yRotO = yaw;
		player.xRotO = pitch;
		player.setYHeadRot(yaw);
		player.yHeadRotO = yaw;
	}
}
