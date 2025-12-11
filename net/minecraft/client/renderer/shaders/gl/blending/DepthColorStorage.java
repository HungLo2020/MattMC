// This file is based on code from IRIS Shaders, licensed under the LGPLv3 license.
// https://github.com/IrisShaders/Iris

package net.minecraft.client.renderer.shaders.gl.blending;

import com.mojang.blaze3d.opengl.GlStateManager;

/**
 * Manages depth and color mask overrides.
 * 
 * Based on IRIS's DepthColorStorage class
 * Reference: frnsrc/Iris-1.21.9/.../gl/blending/DepthColorStorage.java
 */
public class DepthColorStorage {
	private static boolean originalDepthEnable = true;
	private static ColorMask originalColor = new ColorMask(true, true, true, true);
	private static boolean depthColorLocked;

	public static boolean isDepthColorLocked() {
		return depthColorLocked;
	}

	public static void disableDepthColor() {
		if (!depthColorLocked) {
			// Save current state - simplified version without mixins
			// IRIS uses mixins to access GlStateManagerAccessor
			originalDepthEnable = true;
			originalColor = new ColorMask(true, true, true, true);
		}

		depthColorLocked = false;

		GlStateManager._depthMask(false);
		GlStateManager._colorMask(false, false, false, false);

		depthColorLocked = true;
	}

	public static void deferDepthEnable(boolean enabled) {
		originalDepthEnable = enabled;
	}

	public static void deferColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
		originalColor = new ColorMask(red, green, blue, alpha);
	}

	public static void unlockDepthColor() {
		if (!depthColorLocked) {
			return;
		}

		depthColorLocked = false;

		GlStateManager._depthMask(originalDepthEnable);

		GlStateManager._colorMask(originalColor.isRedMasked(), originalColor.isGreenMasked(), originalColor.isBlueMasked(), originalColor.isAlphaMasked());
	}
}
