// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.uniform.providers;

import net.minecraft.client.Minecraft;

/**
 * Stores current and previous end flash intensity for rendering End portal effects.
 * 
 * COPIED VERBATIM from IRIS's EndFlashStorage.java
 * Reference: frnsrc/Iris-1.21.9/.../uniforms/EndFlashStorage.java
 * 
 * Step 27 of NEW-SHADER-PLAN.md
 */
public class EndFlashStorage {
	private float lastEndFlash;
	private float currentEndFlash;

	public void tick() {
		lastEndFlash = currentEndFlash;
		currentEndFlash = Minecraft.getInstance().level.endFlashState() == null ? 0 : Minecraft.getInstance().level.endFlashState().getIntensity(CapturedRenderingState.INSTANCE.getTickDelta());
	}

	public float getLastEndFlash() {
		return lastEndFlash;
	}

	public float getCurrentEndFlash() {
		return currentEndFlash;
	}
}
