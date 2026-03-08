package net.blaze3d.opengl;

import net.blaze3d.systems.RenderSystem;
import com.mojang.jtracy.Plot;
import com.mojang.jtracy.TracyClient;
import java.nio.ByteBuffer;
import java.util.stream.IntStream;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.vulkanic.CommandContext;
import net.vulkanic.VulkanicAPI;
import org.jetbrains.annotations.Nullable;

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
	private static int readFbo;
	private static int writeFbo;
	
	// Iris: From MixinGlStateManager_FramebufferBinding - program and viewport state tracking
	private static int iris$program;
	private static int iris$viewportX;
	private static int iris$viewportY;
	private static int iris$viewportWidth;
	private static int iris$viewportHeight;

	public static void _depthMask(boolean bl) {
		RenderSystem.assertOnRenderThread();
		// Iris: From MixinGlStateManager_DepthColorOverride - depth mask lock support
		if (net.irisshaders.iris.gl.blending.DepthColorStorage.isDepthColorLocked()) {
			net.irisshaders.iris.gl.blending.DepthColorStorage.deferDepthEnable(bl);
			return;
		}
		
		if (bl != DEPTH.mask) {
			DEPTH.mask = bl;
			CommandContext ctx = VulkanicAPI.getImmediateContext();
			net.vulkanic.VulkanicAPI.setDepthWriteMask(ctx, bl);
		}
	}

	public static void _disableBlend() {
		RenderSystem.assertOnRenderThread();
		// Iris: Check blend lock (from MixinGlStateManager_BlendOverride)
		if (net.irisshaders.iris.gl.blending.BlendModeStorage.isBlendLocked()) {
			net.irisshaders.iris.gl.blending.BlendModeStorage.deferBlendModeToggle(false);
			return;
		}
		BLEND.mode.disable();
	}

	public static void _enableBlend() {
		RenderSystem.assertOnRenderThread();
		// Iris: Check blend lock (from MixinGlStateManager_BlendOverride)
		if (net.irisshaders.iris.gl.blending.BlendModeStorage.isBlendLocked()) {
			net.irisshaders.iris.gl.blending.BlendModeStorage.deferBlendModeToggle(true);
			return;
		}
		BLEND.mode.enable();
	}

	public static void _blendFuncSeparate(int i, int j, int k, int l) {
		RenderSystem.assertOnRenderThread();
		// Iris: Check blend lock (from MixinGlStateManager_BlendOverride)
		if (net.irisshaders.iris.gl.blending.BlendModeStorage.isBlendLocked()) {
			net.irisshaders.iris.gl.blending.BlendModeStorage.deferBlendFunc(i, j, k, l);
			return;
		}
		if (i != BLEND.srcRgb || j != BLEND.dstRgb || k != BLEND.srcAlpha || l != BLEND.dstAlpha) {
			BLEND.srcRgb = i;
			BLEND.dstRgb = j;
			BLEND.srcAlpha = k;
			BLEND.dstAlpha = l;
			glBlendFuncSeparate(i, j, k, l);
		}
		
		// Iris: Notify listener of blend function changes
		if (blendFuncListener != null) {
			blendFuncListener.run();
		}
	}

	public static void _glUseProgram(int i) {
		RenderSystem.assertOnRenderThread();
		// Iris: From MixinGlStateManager_FramebufferBinding - avoid redundant program switches
		if (iris$program == 0 && i == 0) {
			return;
		}
		
		net.irisshaders.iris.gl.IrisRenderSystem.onProgramUse();
		
		iris$program = i;
		CommandContext ctx = VulkanicAPI.getImmediateContext();
		net.vulkanic.VulkanicAPI.bindShaderProgram(ctx, i);
		
		// Iris: From MixinGlStateManager_DepthColorOverride - reset tessellation flag
		net.irisshaders.iris.vertices.ImmediateState.usingTessellation = false;
	}

	public static void incrementTrackedBuffers() {
		numBuffers++;
		PLOT_BUFFERS.setValue(numBuffers);
	}

	public static void decrementTrackedBuffers() {
		numBuffers--;
		PLOT_BUFFERS.setValue(numBuffers);
	}

	public static void _glBindFramebuffer(int i, int j) {
		CommandContext ctx = VulkanicAPI.getImmediateContext();
		if ((i == VulkanicAPI.GL_READ_FRAMEBUFFER || i == VulkanicAPI.GL_FRAMEBUFFER) && readFbo != j) {
			net.vulkanic.VulkanicAPI.bindReadFramebuffer(ctx, j);
			readFbo = j;
		}

		if ((i == VulkanicAPI.GL_DRAW_FRAMEBUFFER || i == VulkanicAPI.GL_FRAMEBUFFER) && writeFbo != j) {
			net.vulkanic.VulkanicAPI.bindDrawFramebuffer(ctx, j);
			writeFbo = j;
		}
	}

	public static int getFrameBuffer(int i) {
		if (i == VulkanicAPI.GL_READ_FRAMEBUFFER) {
			return readFbo;
		} else {
			return i == VulkanicAPI.GL_DRAW_FRAMEBUFFER ? writeFbo : 0;
		}
	}

	public static void glBlendFuncSeparate(int i, int j, int k, int l) {
		RenderSystem.assertOnRenderThread();
		net.vulkanic.VulkanicAPI.setBlendFunction(net.vulkanic.VulkanicAPI.getImmediateContext(), i, j, k, l);
	}

	public static void _activeTexture(int i) {
		RenderSystem.assertOnRenderThread();
		// Iris: From MixinGlStateManager_FramebufferBinding - validate texture unit range
		int tex = i - VulkanicAPI.GL_TEXTURE0;
		if (tex < 0 || tex > 128) {
			throw new IllegalArgumentException("Texture " + tex + " out of range");
		}
		
		if (activeTexture != i - 33984) {
			activeTexture = i - 33984;
			CommandContext ctx = VulkanicAPI.getImmediateContext();
			net.vulkanic.VulkanicAPI.setActiveTextureUnit(ctx, i);
		}
	}

	public static int _genTexture() {
		RenderSystem.assertOnRenderThread();
		numTextures++;
		PLOT_TEXTURES.setValue(numTextures);
		return net.vulkanic.VulkanicAPI.createTexture2D(net.vulkanic.VulkanicAPI.getImmediateContext());
	}

	public static void _deleteTexture(int i) {
		RenderSystem.assertOnRenderThread();
		net.vulkanic.VulkanicAPI.deleteTexture(net.vulkanic.VulkanicAPI.getImmediateContext(), i);

		for (GlStateManager.TextureState textureState : TEXTURES) {
			if (textureState.binding == i) {
				textureState.binding = -1;
			}
		}

		numTextures--;
		PLOT_TEXTURES.setValue(numTextures);
		
		// Iris: Track texture deletion (from MixinGlStateManager texture)
		net.irisshaders.iris.pbr.TextureTracker.INSTANCE.onDeleteTexture(i);
		net.irisshaders.iris.pbr.TextureInfoCache.INSTANCE.onDeleteTexture(i);
		net.irisshaders.iris.pbr.texture.PBRTextureManager.INSTANCE.onDeleteTexture(i);
	}

	public static void _texImage2D(int i, int j, int k, int l, int m, int n, int o, int p, @Nullable ByteBuffer byteBuffer) {
		RenderSystem.assertOnRenderThread();
		net.vulkanic.VulkanicAPI.uploadTexture2D(net.vulkanic.VulkanicAPI.getImmediateContext(), i, j, k, l, m, n, o, p, byteBuffer);
		
		// Iris: Track texture image data (from MixinGlStateManager texture)
		net.irisshaders.iris.pbr.TextureInfoCache.INSTANCE.onTexImage2D(i, j, k, l, m, n, o, p, byteBuffer);
	}

	public static void _viewport(int i, int j, int k, int l) {
		// Iris: From MixinGlStateManager_FramebufferBinding - avoid redundant viewport changes
		if (iris$viewportX == i && iris$viewportY == j && iris$viewportWidth == k && iris$viewportHeight == l) {
			return;
		}
		
		iris$viewportX = i;
		iris$viewportY = j;
		iris$viewportWidth = k;
		iris$viewportHeight = l;
		
		CommandContext ctx = VulkanicAPI.getImmediateContext();
		net.vulkanic.VulkanicAPI.setDynamicViewport(ctx, i, j, k, l);
	}

	public static void _colorMask(boolean bl, boolean bl2, boolean bl3, boolean bl4) {
		RenderSystem.assertOnRenderThread();
		// Iris: From MixinGlStateManager_DepthColorOverride - color mask lock support
		if (net.irisshaders.iris.gl.blending.DepthColorStorage.isDepthColorLocked()) {
			net.irisshaders.iris.gl.blending.DepthColorStorage.deferColorMask(bl, bl2, bl3, bl4);
			return;
		}
		
		if (bl != COLOR_MASK.red || bl2 != COLOR_MASK.green || bl3 != COLOR_MASK.blue || bl4 != COLOR_MASK.alpha) {
			COLOR_MASK.red = bl;
			COLOR_MASK.green = bl2;
			COLOR_MASK.blue = bl3;
			COLOR_MASK.alpha = bl4;
			CommandContext ctx = VulkanicAPI.getImmediateContext();
			net.vulkanic.VulkanicAPI.setColorMask(ctx, bl, bl2, bl3, bl4);
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
