// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.uniform.providers;

import net.minecraft.client.renderer.shaders.uniform.UniformHolder;
import net.minecraft.client.renderer.shaders.uniform.UniformUpdateFrequency;
import org.joml.Vector2i;
import org.joml.Vector3i;

import java.time.LocalDateTime;

/**
 * Implements uniforms relating to real-world time (Iris-specific).
 * 
 * COPIED VERBATIM from IRIS's IrisTimeUniforms.java
 * Reference: frnsrc/Iris-1.21.9/.../uniforms/IrisTimeUniforms.java
 * 
 * Step 27 of NEW-SHADER-PLAN.md
 * 
 * Uniforms provided (3 uniforms):
 * - currentDate: Current date (year, month, day)
 * - currentTime: Current time (hour, minute, second)
 * - currentYearTime: Seconds elapsed in year and seconds remaining in year
 */
public class IrisTimeUniforms {
	private static LocalDateTime dateTime = LocalDateTime.now();

	public static void updateTime() {
		dateTime = LocalDateTime.now();
	}

	public static void addTimeUniforms(UniformHolder uniforms) {
		Vector3i date = new Vector3i();
		Vector3i time = new Vector3i();
		Vector2i yearTime = new Vector2i();
		
		uniforms
			.uniform3i(UniformUpdateFrequency.PER_TICK, "currentDate", () -> 
				date.set(dateTime.getYear(), dateTime.getMonthValue(), dateTime.getDayOfMonth()))
			.uniform3i(UniformUpdateFrequency.PER_TICK, "currentTime", () -> 
				time.set(dateTime.getHour(), dateTime.getMinute(), dateTime.getSecond()))
			.uniform2i(UniformUpdateFrequency.PER_TICK, "currentYearTime", () -> 
				yearTime.set(
					((dateTime.getDayOfYear() - 1) * 86400) + (dateTime.getHour() * 3600) + (dateTime.getMinute() * 60) + dateTime.getSecond(),
					(dateTime.toLocalDate().lengthOfYear() * 86400) - (((dateTime.getDayOfYear() - 1) * 86400) + (dateTime.getHour() * 3600) + (dateTime.getMinute() * 60) + dateTime.getSecond())
				)
			);
	}
}
