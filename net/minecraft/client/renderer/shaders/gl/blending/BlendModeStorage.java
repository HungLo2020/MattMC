package net.minecraft.client.renderer.shaders.gl.blending;

import com.mojang.blaze3d.platform.GlStateManager;

/**
 * Manages blend mode overrides and restoration.
 * Simplified from IRIS 1.21.9 - from net.irisshaders.iris.gl.blending.BlendModeStorage
 * 
 * Note: Full IRIS implementation uses mixins to access GlStateManager internals.
 * This simplified version provides the core API for blend mode management.
 */
public class BlendModeStorage {
	private static boolean originalBlendEnable;
	private static BlendMode originalBlend;
	private static boolean blendLocked;

	public static boolean isBlendLocked() {
		return blendLocked;
	}

	public static void overrideBlend(BlendMode override) {
		if (!blendLocked) {
			// TODO: Save current blend state from GlStateManager
			// IRIS uses mixins to access GlStateManagerAccessor.getBLEND()
			// For now, we'll use a default state
			originalBlendEnable = true; // Assume blending was enabled
			originalBlend = new BlendMode(770, 771, 1, 0); // Default GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA
		}

		blendLocked = false;

		if (override == null) {
			GlStateManager._disableBlend();
		} else {
			GlStateManager._enableBlend();
			GlStateManager._blendFuncSeparate(override.srcRgb(), override.dstRgb(), override.srcAlpha(), override.dstAlpha());
		}

		blendLocked = true;
	}

	public static void deferBlendModeToggle(boolean enabled) {
		originalBlendEnable = enabled;
	}

	public static void deferBlendFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
		originalBlend = new BlendMode(srcRgb, dstRgb, srcAlpha, dstAlpha);
	}

	public static void restoreBlend() {
		if (!blendLocked) {
			return;
		}

		blendLocked = false;

		if (originalBlendEnable) {
			GlStateManager._enableBlend();
		} else {
			GlStateManager._disableBlend();
		}

		if (originalBlend != null) {
			GlStateManager._blendFuncSeparate(originalBlend.srcRgb(), originalBlend.dstRgb(),
				originalBlend.srcAlpha(), originalBlend.dstAlpha());
		}
	}
}
