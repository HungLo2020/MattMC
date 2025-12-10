// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.uniform.providers;

import net.minecraft.client.renderer.shaders.uniform.UniformHolder;
import org.joml.Vector4f;

import static net.minecraft.client.renderer.shaders.uniform.UniformUpdateFrequency.PER_FRAME;

/**
 * Internal Iris uniforms that are not directly accessible by shaders.
 * 
 * Based on IRIS's IrisInternalUniforms.java (simplified for MattMC)
 * Reference: frnsrc/Iris-1.21.9/.../uniforms/IrisInternalUniforms.java
 * 
 * Step 27 of NEW-SHADER-PLAN.md
 * 
 * Uniforms provided (5 uniforms):
 * - iris_FogColor: Fog color (RGBA)
 * - iris_FogStart, iris_FogEnd: Fog distance range
 * - iris_FogDensity: Fog density value
 * - iris_currentAlphaTest, alphaTestRef: Alpha test reference values
 * 
 * Note: IRIS version uses DynamicUniformHolder and Sodium's FogStorage
 * MattMC version uses simplified fog access through CapturedRenderingState
 */
public class IrisInternalUniforms {
	private static final Vector4f ONE = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);

	private IrisInternalUniforms() {
		// no construction
	}

	public static void addFogUniforms(UniformHolder uniforms) {
		uniforms
			.uniform4f(PER_FRAME, "iris_FogColor", () -> {
				// Note: IRIS uses Sodium's FogStorage
				// For MattMC, use fog color from CapturedRenderingState
				if (CapturedRenderingState.INSTANCE.getFogColor() != null) {
					return new Vector4f(
						(float) CapturedRenderingState.INSTANCE.getFogColor().x,
						(float) CapturedRenderingState.INSTANCE.getFogColor().y,
						(float) CapturedRenderingState.INSTANCE.getFogColor().z,
						1.0f
					);
				}
				return ONE;
			})
			.uniform1f(PER_FRAME, "iris_FogStart", () -> 0.0f) // Simplified - would need Sodium integration
			.uniform1f(PER_FRAME, "iris_FogEnd", () -> 1000.0f) // Simplified - would need Sodium integration
			.uniform1f(PER_FRAME, "iris_FogDensity", () -> {
				// ensure that the minimum value is 0.0
				return java.lang.Math.max(0.0F, CapturedRenderingState.INSTANCE.getFogDensity());
			})
			.uniform1f(PER_FRAME, "iris_currentAlphaTest", CapturedRenderingState.INSTANCE::getCurrentAlphaTest)
			// Optifine compatibility
			.uniform1f(PER_FRAME, "alphaTestRef", CapturedRenderingState.INSTANCE::getCurrentAlphaTest);
	}
}
