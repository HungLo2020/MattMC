package net.blaze3d.opengl;

import net.blaze3d.systems.RenderSystem;
import com.mojang.jtracy.Plot;
import com.mojang.jtracy.TracyClient;
import java.util.stream.IntStream;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;

@Environment(EnvType.CLIENT)
public class GlStateManager {
	private static final Plot PLOT_TEXTURES = TracyClient.createPlot("GPU Textures");
	private static int numTextures = 0;
	
	// Iris: State update notification support
	private static Runnable blendFuncListener;
	
	static {
		net.irisshaders.iris.gl.state.StateUpdateNotifiers.blendFuncNotifier = listener -> blendFuncListener = listener;
	}
	private static final Plot PLOT_BUFFERS = TracyClient.createPlot("GPU Buffers");
	private static int numBuffers = 0;
	public static final GlStateManager.BlendState BLEND = new GlStateManager.BlendState(); // Made public for Iris shader mod integration
	public static final GlStateManager.DepthState DEPTH = new GlStateManager.DepthState(); // Made public for Iris shader mod integration
	public static int activeTexture; // Made public for Iris shader mod integration
	// Iris: Increased from 12 to 128 to support more texture units for shaders
	public static final GlStateManager.TextureState[] TEXTURES = (GlStateManager.TextureState[])IntStream.range(0, 128)
		.mapToObj(i -> new GlStateManager.TextureState())
		.toArray(GlStateManager.TextureState[]::new); // Made public for Iris shader mod integration
	public static final GlStateManager.ColorMask COLOR_MASK = new GlStateManager.ColorMask(); // Made public for Iris shader mod integration
	
	public static void incrementTrackedBuffers() {
		numBuffers++;
		PLOT_BUFFERS.setValue(numBuffers);
	}

	public static void decrementTrackedBuffers() {
		numBuffers--;
		PLOT_BUFFERS.setValue(numBuffers);
	}

	public static void incrementTrackedTextures() {
		numTextures++;
		PLOT_TEXTURES.setValue(numTextures);
	}

	public static void decrementTrackedTextures() {
		numTextures--;
		PLOT_TEXTURES.setValue(numTextures);
	}

	public static void notifyBlendFuncChanged() {
		if (blendFuncListener != null) {
			blendFuncListener.run();
		}
	}

	@Environment(EnvType.CLIENT)
	public static class BlendState {
		public final GlStateManager.BooleanState mode = new GlStateManager.BooleanState(3042);
		public int srcRgb = 1;
		public int dstRgb = 0;
		public int srcAlpha = 1;
		public int dstAlpha = 0;
	}

	@Environment(EnvType.CLIENT)
	public static class BooleanState implements net.irisshaders.iris.gl.BooleanStateExtended {
		private final int state;
		/**
		 * The enabled state.
		 * 
		 * @apiNote Made public for Iris shader pipeline state management.
		 * Originally widened by: iris.accesswidener
		 */
		public boolean enabled;
		// Iris integration: track unknown state
		private boolean stateUnknown;

		public BooleanState(int i) {
			this.state = i;
		}

		public void disable() {
			this.setEnabled(false);
		}

		public void enable() {
			this.setEnabled(true);
		}

		public void setEnabled(boolean bl) {
			RenderSystem.assertOnRenderThread();
			// Iris: Handle unknown state
			if (stateUnknown) {
				this.enabled = bl;
				stateUnknown = false;
				// Delegate ALL enable/disable to VulkanicAPI
				CommandContext ctx = VulkanicAPI.getImmediateContext();
				net.vulkanic.VulkanicAPI.setCapabilityEnabled(ctx, this.state, bl);
				return;
			}
			if (bl != this.enabled) {
				this.enabled = bl;
				// Delegate ALL enable/disable to VulkanicAPI
				CommandContext ctx = VulkanicAPI.getImmediateContext();
				net.vulkanic.VulkanicAPI.setCapabilityEnabled(ctx, this.state, bl);
			}
		}

		@Override
		public void setUnknownState() {
			stateUnknown = true;
		}
	}

	@Environment(EnvType.CLIENT)
	public static class ColorMask {
		public boolean red = true;
		public boolean green = true;
		public boolean blue = true;
		public boolean alpha = true;
	}

	@Environment(EnvType.CLIENT)
	public static class DepthState {
		public final GlStateManager.BooleanState mode = new GlStateManager.BooleanState(2929);
		public boolean mask = true;
		public int func = 513;
	}

	@Environment(EnvType.CLIENT)
	public static class TextureState {
		public int binding;
	}
}
