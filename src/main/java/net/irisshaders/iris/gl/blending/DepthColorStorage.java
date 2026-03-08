package net.irisshaders.iris.gl.blending;

import net.blaze3d.opengl.GlStateManager;
import net.vulkanic.VulkanicAPI;

public class DepthColorStorage {
	private static boolean originalDepthEnable;
	private static ColorMask originalColor;
	private static boolean depthColorLocked;

	public static boolean isDepthColorLocked() {
		return depthColorLocked;
	}

	public static void disableDepthColor() {
		if (!depthColorLocked) {
			// Only save the previous state if the depth and color mask wasn't already locked
			GlStateManager.ColorMask colorMask = GlStateManager.COLOR_MASK;
			GlStateManager.DepthState depthState = GlStateManager.DEPTH;

			originalDepthEnable = depthState.mask;
			originalColor = new ColorMask(colorMask.red, colorMask.green, colorMask.blue, colorMask.alpha);
		}

		depthColorLocked = false;

		setDepthMask(false);
		setColorMask(false, false, false, false);

		depthColorLocked = true;
	}

	public static void setDepthMask(boolean enabled) {
		if (depthColorLocked) {
			deferDepthEnable(enabled);
			return;
		}

		if (enabled != GlStateManager.DEPTH.mask) {
			GlStateManager.DEPTH.mask = enabled;
			VulkanicAPI.setDepthWriteMask(VulkanicAPI.getImmediateContext(), enabled);
		}
	}

	public static void setColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
		if (depthColorLocked) {
			deferColorMask(red, green, blue, alpha);
			return;
		}

		GlStateManager.ColorMask colorMask = GlStateManager.COLOR_MASK;
		if (red != colorMask.red || green != colorMask.green || blue != colorMask.blue || alpha != colorMask.alpha) {
			colorMask.red = red;
			colorMask.green = green;
			colorMask.blue = blue;
			colorMask.alpha = alpha;
			VulkanicAPI.setColorMask(VulkanicAPI.getImmediateContext(), red, green, blue, alpha);
		}
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

		setDepthMask(originalDepthEnable);

		setColorMask(originalColor.isRedMasked(), originalColor.isGreenMasked(), originalColor.isBlueMasked(), originalColor.isAlphaMasked());
	}
}
