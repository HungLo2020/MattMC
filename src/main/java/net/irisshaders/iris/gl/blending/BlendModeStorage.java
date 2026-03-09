package net.irisshaders.iris.gl.blending;

import net.blaze3d.systems.RenderSystem;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;

public class BlendModeStorage {
	private static boolean originalBlendEnable;
	private static BlendMode originalBlend;
	private static boolean blendLocked;
	private static boolean blendStateUnknown;
	private static boolean blendEnabled;
	private static int blendSrcRgb = 1;
	private static int blendDstRgb = 0;
	private static int blendSrcAlpha = 1;
	private static int blendDstAlpha = 0;

	public static boolean isBlendLocked() {
		return blendLocked;
	}

	public static boolean isBlendEnabled() {
		return blendEnabled;
	}

	public static int getBlendSrcRgb() {
		return blendSrcRgb;
	}

	public static int getBlendDstRgb() {
		return blendDstRgb;
	}

	public static int getBlendSrcAlpha() {
		return blendSrcAlpha;
	}

	public static int getBlendDstAlpha() {
		return blendDstAlpha;
	}

	public static void overrideBlend(BlendMode override) {
		if (!blendLocked) {
			// Only save the previous state if the blend mode wasn't already locked
			originalBlendEnable = blendEnabled;
			originalBlend = new BlendMode(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
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

		if (blendStateUnknown) {
			blendEnabled = enabled;
			blendStateUnknown = false;
			CommandContext ctx = VulkanicAPI.getCommandContext();
			VulkanicAPI.setCapabilityEnabled(ctx, VulkanicAPI.GL_BLEND, enabled);
			return;
		}

		if (enabled != blendEnabled) {
			blendEnabled = enabled;
			CommandContext ctx = VulkanicAPI.getCommandContext();
			VulkanicAPI.setCapabilityEnabled(ctx, VulkanicAPI.GL_BLEND, enabled);
		}
	}

	public static void setBlendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
		RenderSystem.assertOnRenderThread();
		if (blendLocked) {
			deferBlendFunc(srcRgb, dstRgb, srcAlpha, dstAlpha);
			return;
		}

		if (srcRgb != blendSrcRgb || dstRgb != blendDstRgb || srcAlpha != blendSrcAlpha || dstAlpha != blendDstAlpha) {
			blendSrcRgb = srcRgb;
			blendDstRgb = dstRgb;
			blendSrcAlpha = srcAlpha;
			blendDstAlpha = dstAlpha;
			VulkanicAPI.setBlendFunction(VulkanicAPI.getCommandContext(), srcRgb, dstRgb, srcAlpha, dstAlpha);
		}

		IrisRenderSystem.notifyBlendFuncChanged();
	}

	public static void overrideBufferBlend(int index, BlendMode override) {
		if (!blendLocked) {
			// Only save the previous state if the blend mode wasn't already locked
			originalBlendEnable = blendEnabled;
			originalBlend = new BlendMode(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
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

	public static void markBlendStateUnknown() {
		blendStateUnknown = true;
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
