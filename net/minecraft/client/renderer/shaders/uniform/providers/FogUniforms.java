// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.uniform.providers;

import net.minecraft.client.renderer.shaders.uniform.UniformHolder;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import static net.minecraft.client.renderer.shaders.uniform.UniformUpdateFrequency.ONCE;
import static net.minecraft.client.renderer.shaders.uniform.UniformUpdateFrequency.PER_FRAME;

/**
 * Implements uniforms relating to fog.
 * 
 * Based on IRIS's FogUniforms.java (simplified for MattMC)
 * Reference: frnsrc/Iris-1.21.9/.../uniforms/FogUniforms.java
 * 
 * Step 27 of NEW-SHADER-PLAN.md
 * 
 * Uniforms provided (5 uniforms):
 * - fogMode: OpenGL fog mode (GL_LINEAR or GL_EXP2)
 * - fogShape: Fog shape (0=spherical, 1=cylindrical, -1=none)
 * - fogDensity: Fog density value
 * - fogStart: Fog start distance
 * - fogEnd: Fog end distance
 * - fogColor: RGB color of fog
 */
public class FogUniforms {
	private FogUniforms() {
		// no construction
	}

	public static void addFogUniforms(UniformHolder uniforms) {
		// Simplified fog uniforms for MattMC
		// Full IRIS version requires DynamicUniformHolder and FogMode enum
		
		uniforms
			.uniform1i(PER_FRAME, "fogMode", () -> {
				float fogDensity = CapturedRenderingState.INSTANCE.getFogDensity();
				if (fogDensity < 0.0F) {
					return GL11.GL_LINEAR;
				} else {
					return GL11.GL_EXP2;
				}
			})
			.uniform1i(PER_FRAME, "fogShape", () -> 1) // 1 = cylindrical
			.uniform1f(PER_FRAME, "fogDensity", () -> Math.max(0.0F, CapturedRenderingState.INSTANCE.getFogDensity()))
			.uniform1f(PER_FRAME, "fogStart", () -> 0.0f) // Simplified - would need FogStorage from Sodium
			.uniform1f(PER_FRAME, "fogEnd", () -> 1000.0f) // Simplified - would need FogStorage from Sodium
			.uniform3f(PER_FRAME, "fogColor", () -> {
				return new Vector3f(
					(float) CapturedRenderingState.INSTANCE.getFogColor().x,
					(float) CapturedRenderingState.INSTANCE.getFogColor().y,
					(float) CapturedRenderingState.INSTANCE.getFogColor().z
				);
			});
	}
}
