// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.uniform.transforms;

import net.minecraft.client.renderer.shaders.uniform.providers.FrameUpdateNotifier;
import org.joml.Vector2f;
import org.joml.Vector2i;

import java.util.function.Supplier;

/**
 * Smooths a 2D integer vector into a 2D float vector using exponential smoothing.
 * 
 * COPIED VERBATIM from IRIS's SmoothedVec2f.java
 * Reference: frnsrc/Iris-1.21.9/.../uniforms/transforms/SmoothedVec2f.java
 * 
 * Step 27 of NEW-SHADER-PLAN.md
 */
public class SmoothedVec2f implements Supplier<Vector2f> {
	private final SmoothedFloat x;
	private final SmoothedFloat y;

	public SmoothedVec2f(float halfLifeUp, float halfLifeDown, Supplier<Vector2i> unsmoothed, FrameUpdateNotifier updateNotifier) {
		x = new SmoothedFloat(halfLifeUp, halfLifeDown, () -> unsmoothed.get().x, updateNotifier);
		y = new SmoothedFloat(halfLifeUp, halfLifeDown, () -> unsmoothed.get().y, updateNotifier);
	}

	@Override
	public Vector2f get() {
		return new Vector2f(x.getAsFloat(), y.getAsFloat());
	}
}
