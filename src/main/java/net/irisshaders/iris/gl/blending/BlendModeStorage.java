package net.irisshaders.iris.gl.blending;

import net.blaze3d.opengl.GlStateManager;
import net.blaze3d.systems.RenderSystem;
import net.irisshaders.iris.gl.IrisRenderSystem;

public class BlendModeStorage {
	private static boolean originalBlendEnable;
	private static BlendMode originalBlend;
	private static boolean blendLocked;

	public static boolean isBlendLocked() {
		return blendLocked;
	}

	public static void overrideBlend(BlendMode override) {
		if (!blendLocked) {
			// Only save the previous state if the blend mode wasn't already locked
			GlStateManager.BlendState blendState = GlStateManager.BLEND; // Direct static field access

			originalBlendEnable = blendState.mode.enabled; // Direct field access - enabled is public
			originalBlend = new BlendMode(blendState.srcRgb, blendState.dstRgb, blendState.srcAlpha, blendState.dstAlpha);
		}

		blendLocked = false;

		if (override == null) {
			setBlendEnabled(false);
		} else {
			setBlendEnabled(true);
			setBlendFuncSeparate(override.srcRgb(), override.dstRgb(), override.srcAlpha(), override.dstAlpha());
		}

		blendLocked = true;
	}

	public static void setBlendEnabled(boolean enabled) {
		RenderSystem.assertOnRenderThread();
		if (blendLocked) {
			deferBlendModeToggle(enabled);
			return;
		}

		if (enabled) {
			GlStateManager.BLEND.mode.enable();
		} else {
			GlStateManager.BLEND.mode.disable();
		}
	}

	public static void setBlendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
		RenderSystem.assertOnRenderThread();
		if (blendLocked) {
			deferBlendFunc(srcRgb, dstRgb, srcAlpha, dstAlpha);
			return;
		}

		GlStateManager.BlendState blendState = GlStateManager.BLEND;
		if (srcRgb != blendState.srcRgb || dstRgb != blendState.dstRgb || srcAlpha != blendState.srcAlpha || dstAlpha != blendState.dstAlpha) {
			blendState.srcRgb = srcRgb;
			blendState.dstRgb = dstRgb;
			blendState.srcAlpha = srcAlpha;
			blendState.dstAlpha = dstAlpha;
			net.vulkanic.VulkanicAPI.setBlendFunction(net.vulkanic.VulkanicAPI.getImmediateContext(), srcRgb, dstRgb, srcAlpha, dstAlpha);
		}

		GlStateManager.notifyBlendFuncChanged();
	}

	public static void overrideBufferBlend(int index, BlendMode override) {
		if (!blendLocked) {
			// Only save the previous state if the blend mode wasn't already locked
			GlStateManager.BlendState blendState = GlStateManager.BLEND; // Direct static field access

			originalBlendEnable = blendState.mode.enabled; // Direct field access - enabled is public
			originalBlend = new BlendMode(blendState.srcRgb, blendState.dstRgb, blendState.srcAlpha, blendState.dstAlpha);
		}

		if (override == null) {
			IrisRenderSystem.disableBufferBlend(index);
		} else {
			IrisRenderSystem.enableBufferBlend(index);
			IrisRenderSystem.blendFuncSeparatei(index, override.srcRgb(), override.dstRgb(), override.srcAlpha(), override.dstAlpha());
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
			setBlendEnabled(true);
		} else {
			setBlendEnabled(false);
		}

		setBlendFuncSeparate(originalBlend.srcRgb(), originalBlend.dstRgb(),
			originalBlend.srcAlpha(), originalBlend.dstAlpha());
	}
}
