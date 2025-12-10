// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.uniform.providers;

import java.util.ArrayList;
import java.util.List;

/**
 * Notifies listeners when a new frame begins.
 * 
 * COPIED VERBATIM from IRIS's FrameUpdateNotifier.java
 * Reference: frnsrc/Iris-1.21.9/.../uniforms/FrameUpdateNotifier.java
 * 
 * Step 26 of NEW-SHADER-PLAN.md
 */
public class FrameUpdateNotifier {
	private final List<Runnable> listeners;

	public FrameUpdateNotifier() {
		listeners = new ArrayList<>();
	}

	public void addListener(Runnable onNewFrame) {
		listeners.add(onNewFrame);
	}

	public void onNewFrame() {
		listeners.forEach(Runnable::run);
	}
}
