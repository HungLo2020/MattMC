// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.gl.blending;

/**
 * Color mask state.
 * 
 * Based on IRIS's ColorMask class
 * Reference: frnsrc/Iris-1.21.9/.../gl/blending/ColorMask.java
 */
public class ColorMask {
	private final boolean red;
	private final boolean green;
	private final boolean blue;
	private final boolean alpha;

	public ColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
		this.red = red;
		this.green = green;
		this.blue = blue;
		this.alpha = alpha;
	}

	public boolean isRedMasked() {
		return red;
	}

	public boolean isGreenMasked() {
		return green;
	}

	public boolean isBlueMasked() {
		return blue;
	}

	public boolean isAlphaMasked() {
		return alpha;
	}
}
