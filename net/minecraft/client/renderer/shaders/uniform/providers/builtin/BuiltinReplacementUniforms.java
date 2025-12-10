// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.uniform.providers.builtin;

import net.minecraft.client.renderer.shaders.uniform.UniformHolder;
import net.minecraft.client.renderer.shaders.uniform.UniformUpdateFrequency;
import org.joml.Matrix4f;

/**
 * Builtin replacement uniforms for compatibility with vanilla shaders that use specific matrices
 * 
 * COPIED VERBATIM from IRIS's BuiltinReplacementUniforms.java
 * Reference: frnsrc/Iris-1.21.9/.../uniforms/builtin/BuiltinReplacementUniforms.java
 * 
 * Step 27 of NEW-SHADER-PLAN.md
 */
public class BuiltinReplacementUniforms {
	private static final Matrix4f lightmapTextureMatrix;

	static {
		// This mimics the transformations done in LightTexture to the GL_TEXTURE matrix.
		lightmapTextureMatrix = new Matrix4f(0.00390625f, 0.0f, 0.0f, 0.0f, 0.0f, 0.00390625f, 0.0f, 0.0f, 0.0f, 0.0f, 0.00390625f, 0.0f, 0.03125f, 0.03125f, 0.03125f, 1.0f);
	}

	public static void addBuiltinReplacementUniforms(UniformHolder uniforms) {
		uniforms.uniformMatrix(UniformUpdateFrequency.ONCE, "iris_LightmapTextureMatrix", () -> {
			// Note: These warnings are commented out in IRIS source as well
			// Kept for reference - indicates shader is doing unusual lightmap coordinate transformations
			//Iris.logger.warn("A shader appears to require the lightmap texture matrix even after transformations have occurred");
			//Iris.logger.warn("Iris handles this correctly but it indicates that the shader is doing weird things with lightmap coordinates");

			return lightmapTextureMatrix;
		});
	}
}
