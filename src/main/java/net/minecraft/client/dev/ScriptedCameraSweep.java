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
 */
public final class ScriptedCameraSweep {
	private static final boolean ENABLED = Boolean.getBoolean("mattmc.dev.scriptedCamera");
	private static final double SECONDS = doubleProperty("seconds", 150.0);
	private static final double YAW_AMPLITUDE = doubleProperty("yawAmplitude", 150.0);
	private static final double YAW_PERIOD = doubleProperty("yawPeriod", 2.4);
	private static final double PITCH_AMPLITUDE = doubleProperty("pitchAmplitude", 60.0);
	private static final double PITCH_PERIOD = doubleProperty("pitchPeriod", 1.6);

	private static long startNanos;
	private static float baseYaw;
	private static boolean stopIssued;

	private ScriptedCameraSweep() {}

	private static double doubleProperty(String name, double fallback) {
		String value = System.getProperty("mattmc.dev.scriptedCamera." + name);
		if (value == null || value.isBlank()) return fallback;
		return Double.parseDouble(value.trim());
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

	/** Called once per client frame from {@code Minecraft.runTick}. */
	public static void beforeFrame(Minecraft minecraft) {
		if (!ENABLED || stopIssued) return;
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null || minecraft.screen != null) return;
		long now = System.nanoTime();
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
