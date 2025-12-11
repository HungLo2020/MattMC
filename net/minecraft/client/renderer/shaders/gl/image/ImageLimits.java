// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.gl.image;

import net.minecraft.client.renderer.shaders.gl.IrisRenderSystem;

/**
 * Caches OpenGL image-related limits.
 * 
 * Based on IRIS's ImageLimits class
 * Reference: frnsrc/Iris-1.21.9/.../gl/image/ImageLimits.java
 */
public class ImageLimits {
	private static ImageLimits instance;
	private final int maxImageUnits;

	private ImageLimits() {
		this.maxImageUnits = IrisRenderSystem.getMaxImageUnits();
	}

	public static ImageLimits get() {
		if (instance == null) {
			instance = new ImageLimits();
		}

		return instance;
	}

	public int getMaxImageUnits() {
		return maxImageUnits;
	}
}
