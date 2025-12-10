// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.uniform.providers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.shaders.uniform.UniformHolder;

import static net.minecraft.client.renderer.shaders.uniform.UniformUpdateFrequency.PER_FRAME;

/**
 * Implements uniforms relating the current viewport.
 * 
 * COPIED VERBATIM from IRIS's ViewportUniforms.java
 * Reference: frnsrc/Iris-1.21.9/.../uniforms/ViewportUniforms.java
 * 
 * Step 26 of NEW-SHADER-PLAN.md
 * 
 * Uniforms provided:
 * - viewHeight: Height of the viewport in pixels
 * - viewWidth: Width of the viewport in pixels
 * - aspectRatio: Aspect ratio (width/height)
 */
public final class ViewportUniforms {
	// cannot be constructed
	private ViewportUniforms() {
	}

	/**
	 * Makes the viewport uniforms available to the given program
	 *
	 * @param uniforms the program to make the uniforms available to
	 */
	public static void addViewportUniforms(UniformHolder uniforms) {
		// NB: It is not safe to cache the render target due to mods like Resolution Control modifying the render target field.
		uniforms
			.uniform1f(PER_FRAME, "viewHeight", () -> Minecraft.getInstance().getMainRenderTarget().height)
			.uniform1f(PER_FRAME, "viewWidth", () -> Minecraft.getInstance().getMainRenderTarget().width)
			.uniform1f(PER_FRAME, "aspectRatio", ViewportUniforms::getAspectRatio);
	}

	/**
	 * @return the current viewport aspect ratio, calculated from the current Minecraft window size
	 */
	private static float getAspectRatio() {
		return ((float) Minecraft.getInstance().getMainRenderTarget().width) / ((float) Minecraft.getInstance().getMainRenderTarget().height);
	}
}
