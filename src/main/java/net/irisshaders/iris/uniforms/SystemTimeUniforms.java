package net.irisshaders.iris.uniforms;

import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.minecraft.world.level.dimension.DimensionType;

import java.util.OptionalLong;
import java.util.function.IntSupplier;

/**
 * Implements uniforms relating the system time (as opposed to the world time)
 *
 * @see <a href="https://github.com/IrisShaders/ShaderDoc/blob/master/uniforms.md#system-time">Uniforms: System time</a>
 */
public final class SystemTimeUniforms {
	public static final Timer TIMER = new Timer();
	public static final FrameCounter COUNTER = new FrameCounter();
	private static final boolean DETERMINISTIC_TEMPORAL_PARITY = Boolean.getBoolean("mattmc.vulkan.deterministicTemporalParity");
	private static final int DETERMINISTIC_FRAME_COUNTER =
		Integer.getInteger("mattmc.vulkan.deterministicTemporalParity.frameCounter", 0);
	private static final float DETERMINISTIC_FRAME_TIME_SECONDS =
		Float.parseFloat(System.getProperty("mattmc.vulkan.deterministicTemporalParity.frameTime", "0.016666668"));
	private static final float DETERMINISTIC_FRAME_TIME_COUNTER_SECONDS =
		Float.parseFloat(System.getProperty("mattmc.vulkan.deterministicTemporalParity.frameTimeCounter", "0.0"));
	private static final long DETERMINISTIC_WORLD_TIME =
		Long.getLong("mattmc.vulkan.deterministicTemporalParity.worldTime", 6000L);
	private static final float DETERMINISTIC_PARTIAL_TICK =
		Float.parseFloat(System.getProperty("mattmc.vulkan.deterministicTemporalParity.partialTick", "1.0"));
	private static final float DETERMINISTIC_FOV_MODIFIER =
		Float.parseFloat(System.getProperty("mattmc.vulkan.deterministicTemporalParity.fovModifier", "1.0"));

	private SystemTimeUniforms() {
	}

	/**
	 * Makes system time uniforms available to the given program
	 *
	 * @param uniforms the program to make the uniforms available to
	 */
	public static void addSystemTimeUniforms(UniformHolder uniforms) {
		uniforms
			.uniform1i(UniformUpdateFrequency.PER_FRAME, "frameCounter", COUNTER)
			.uniform1f(UniformUpdateFrequency.PER_FRAME, "frameTime", TIMER::getLastFrameTime)
			.uniform1f(UniformUpdateFrequency.PER_FRAME, "frameTimeCounter", TIMER::getFrameTimeCounter);
	}

	public static boolean isDeterministicTemporalParityEnabled() {
		return DETERMINISTIC_TEMPORAL_PARITY;
	}

	public static int deterministicTemporalFrameCounter() {
		if (!DETERMINISTIC_TEMPORAL_PARITY) {
			return COUNTER.count;
		}
		return Math.floorMod(DETERMINISTIC_FRAME_COUNTER, 720720);
	}

	public static float deterministicTemporalFrameTime() {
		return DETERMINISTIC_FRAME_TIME_SECONDS;
	}

	public static float deterministicTemporalFrameTimeCounter() {
		return DETERMINISTIC_FRAME_TIME_COUNTER_SECONDS;
	}

	public static float deterministicTemporalFrameTimeSmooth() {
		return DETERMINISTIC_FRAME_TIME_SECONDS;
	}

	public static float deterministicTemporalPartialTick() {
		return DETERMINISTIC_PARTIAL_TICK;
	}

	public static float deterministicTemporalFovModifier() {
		return DETERMINISTIC_FOV_MODIFIER;
	}

	public static long deterministicTemporalWorldTime() {
		return DETERMINISTIC_WORLD_TIME;
	}

	public static int deterministicTemporalWorldDayTime(DimensionType dimensionType) {
		long dayTime = dimensionType.fixedTime().orElse(Math.floorMod(DETERMINISTIC_WORLD_TIME, 24000L));
		return (int)dayTime;
	}

	public static int deterministicTemporalWorldDay() {
		return (int)Math.floorDiv(DETERMINISTIC_WORLD_TIME, 24000L);
	}

	public static int deterministicTemporalMoonPhase(DimensionType dimensionType) {
		return dimensionType.moonPhase(DETERMINISTIC_WORLD_TIME);
	}

	public static float deterministicTemporalTimeOfDay(DimensionType dimensionType) {
		return dimensionType.timeOfDay(DETERMINISTIC_WORLD_TIME);
	}

	/**
	 * A simple frame counter. On each frame, it is incremented by 1, and it wraps around every 720720 frames. It starts
	 * at zero and goes from there.
	 */
	public static class FrameCounter implements IntSupplier {
		private int count;

		private FrameCounter() {
			this.count = 0;
		}

		@Override
		public int getAsInt() {
			if (DETERMINISTIC_TEMPORAL_PARITY) {
				return deterministicTemporalFrameCounter();
			}
			return count;
		}

		public void beginFrame() {
			if (DETERMINISTIC_TEMPORAL_PARITY) {
				return;
			}
			count = (count + 1) % 720720;
		}

		public void reset() {
			count = 0;
		}
	}

	/**
	 * Keeps track of the time that the last frame took to render as well as the number of milliseconds since the start
	 * of the first frame to the start of the current frame. Updated at the start of each frame.
	 */
	public static final class Timer {
		private float frameTimeCounter;
		private float lastFrameTime;

		// Disabling this because OptionalLong provides a nice wrapper around (boolean valid, long value)
		@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
		private OptionalLong lastStartTime;

		public Timer() {
			reset();
		}

		public void beginFrame(long frameStartTime) {
			if (DETERMINISTIC_TEMPORAL_PARITY) {
				lastFrameTime = deterministicTemporalFrameTime();
				frameTimeCounter = deterministicTemporalFrameTimeCounter();
				lastStartTime = OptionalLong.of(frameStartTime);
				return;
			}

			// Track how much time passed since the last time we began rendering a frame.
			// If this is the first frame, then use a value of 0.
			long diffNs = frameStartTime - lastStartTime.orElse(frameStartTime);
			// Convert to milliseconds
			long diffMs = (diffNs / 1000) / 1000;

			// Convert to seconds with a resolution of 1 millisecond, and store as the time taken for the last frame to complete.
			lastFrameTime = diffMs / 1000.0F;

			// Advance the current frameTimeCounter by the amount of time the last frame took.
			frameTimeCounter += lastFrameTime;

			// Prevent the frameTimeCounter from getting too large, since that causes issues with some shaderpacks
			// This means that it should reset every hour.
			if (frameTimeCounter >= 3600.0F) {
				frameTimeCounter = 0.0F;
			}

			// Finally, update the "last start time" value.
			lastStartTime = OptionalLong.of(frameStartTime);
		}

		public float getFrameTimeCounter() {
			if (DETERMINISTIC_TEMPORAL_PARITY) {
				return deterministicTemporalFrameTimeCounter();
			}
			return frameTimeCounter;
		}

		public float getLastFrameTime() {
			if (DETERMINISTIC_TEMPORAL_PARITY) {
				return deterministicTemporalFrameTime();
			}
			return lastFrameTime;
		}

		public void reset() {
			frameTimeCounter = 0.0F;
			lastFrameTime = 0.0F;
			lastStartTime = OptionalLong.empty();
		}
	}
}
