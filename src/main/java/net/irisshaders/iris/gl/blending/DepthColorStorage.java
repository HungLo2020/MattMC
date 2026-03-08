package net.irisshaders.iris.gl.blending;

import net.vulkanic.VulkanicAPI;

public class DepthColorStorage {
	private static boolean originalDepthEnable;
	private static ColorMask originalColor;
	private static boolean depthColorLocked;
	private static boolean currentDepthEnable = true;
	private static boolean currentRedMask = true;
	private static boolean currentGreenMask = true;
	private static boolean currentBlueMask = true;
	private static boolean currentAlphaMask = true;

	public static boolean isDepthColorLocked() {
		return depthColorLocked;
	}

	public static void disableDepthColor() {
		if (!depthColorLocked) {
			// Only save the previous state if the depth and color mask wasn't already locked
			originalDepthEnable = currentDepthEnable;
			originalColor = new ColorMask(currentRedMask, currentGreenMask, currentBlueMask, currentAlphaMask);
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

		if (enabled != currentDepthEnable) {
			currentDepthEnable = enabled;
			VulkanicAPI.setDepthWriteMask(VulkanicAPI.getImmediateContext(), enabled);
		}
	}

	public static void setColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
		if (depthColorLocked) {
			deferColorMask(red, green, blue, alpha);
			return;
		}

		if (red != currentRedMask || green != currentGreenMask || blue != currentBlueMask || alpha != currentAlphaMask) {
			currentRedMask = red;
			currentGreenMask = green;
			currentBlueMask = blue;
			currentAlphaMask = alpha;
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
