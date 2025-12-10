// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.uniform.providers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.shaders.uniform.UniformHolder;

import java.util.Objects;

import static net.minecraft.client.renderer.shaders.uniform.UniformUpdateFrequency.PER_TICK;

/**
 * Implements uniforms relating to world time.
 * 
 * COPIED VERBATIM from IRIS's WorldTimeUniforms.java
 * Reference: frnsrc/Iris-1.21.9/.../uniforms/WorldTimeUniforms.java
 * 
 * Step 26 of NEW-SHADER-PLAN.md
 * 
 * Uniforms provided:
 * - worldTime: Current time of day (0-23999)
 * - worldDay: Current day number
 * - moonPhase: Current moon phase (0-7)
 */
public final class WorldTimeUniforms {
	private WorldTimeUniforms() {
	}

	/**
	 * Makes world time uniforms available to the given program
	 *
	 * @param uniforms the program to make the uniforms available to
	 */
	public static void addWorldTimeUniforms(UniformHolder uniforms) {
		uniforms
			.uniform1i(PER_TICK, "worldTime", WorldTimeUniforms::getWorldDayTime)
			.uniform1i(PER_TICK, "worldDay", WorldTimeUniforms::getWorldDay)
			.uniform1i(PER_TICK, "moonPhase", () -> getWorld().getMoonPhase());
	}

	static int getWorldDayTime() {
		long timeOfDay = getWorld().getDayTime();

		// Note: In IRIS, this checks for dimension type (End/Nether) to handle fixed time
		// For MattMC, we'll use the vanilla behavior which respects dimensionType().fixedTime()
		long dayTime = getWorld().dimensionType().fixedTime()
			.orElse(timeOfDay % 24000L);

		return (int) dayTime;
	}

	private static int getWorldDay() {
		long timeOfDay = getWorld().getDayTime();
		long day = timeOfDay / 24000L;

		return (int) day;
	}

	private static ClientLevel getWorld() {
		return Objects.requireNonNull(Minecraft.getInstance().level);
	}
}
