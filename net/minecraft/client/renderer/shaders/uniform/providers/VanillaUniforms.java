// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.uniform.providers;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.shaders.uniform.UniformHolder;
import org.joml.Vector2f;

import static net.minecraft.client.renderer.shaders.uniform.UniformUpdateFrequency.PER_FRAME;

/**
 * Vanilla Minecraft uniforms for shader compatibility.
 * 
 * Based on IRIS's VanillaUniforms.java (simplified for MattMC)
 * Reference: frnsrc/Iris-1.21.9/.../uniforms/VanillaUniforms.java
 * 
 * Step 27 of NEW-SHADER-PLAN.md
 * 
 * Uniforms provided (2 uniforms):
 * - iris_LineWidth: Current line width for rendering
 * - iris_ScreenSize: Screen dimensions (width, height)
 */
public class VanillaUniforms {
	public static void addVanillaUniforms(UniformHolder uniforms) {
		Vector2f cachedScreenSize = new Vector2f();
		// Note: IRIS uses DynamicUniformHolder for these
		// For MattMC, we use regular UniformHolder with PER_FRAME updates
		uniforms.uniform1f(PER_FRAME, "iris_LineWidth", RenderSystem::getShaderLineWidth);
		uniforms.uniform2f(PER_FRAME, "iris_ScreenSize", () -> cachedScreenSize.set(Minecraft.getInstance().getMainRenderTarget().width, Minecraft.getInstance().getMainRenderTarget().height));
	}
}
