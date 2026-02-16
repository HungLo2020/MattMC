package net.blaze3d.opengl;

import com.google.common.base.Charsets;
import net.blaze3d.platform.MacosUtil;
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
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

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
	private static final GlStateManager.CullState CULL = new GlStateManager.CullState();
	private static final GlStateManager.PolygonOffsetState POLY_OFFSET = new GlStateManager.PolygonOffsetState();
	private static final GlStateManager.ColorLogicState COLOR_LOGIC = new GlStateManager.ColorLogicState();
	private static final GlStateManager.ScissorState SCISSOR = new GlStateManager.ScissorState();
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

	public static void _disableScissorTest() {
		RenderSystem.assertOnRenderThread();
		SCISSOR.mode.disable();
	}

	public static void _enableScissorTest() {
		RenderSystem.assertOnRenderThread();
		SCISSOR.mode.enable();
	}

	public static void _scissorBox(int i, int j, int k, int l) {
		RenderSystem.assertOnRenderThread();
		CommandContext ctx = VulkanicAPI.getImmediateContext();
		net.vulkanic.VulkanicAPI.setDynamicScissor(ctx, i, j, k, l);
	}

	public static void _disableDepthTest() {
		RenderSystem.assertOnRenderThread();
		DEPTH.mode.disable();
	}

	public static void _enableDepthTest() {
		RenderSystem.assertOnRenderThread();
		DEPTH.mode.enable();
	}

	public static void _depthFunc(int i) {
		RenderSystem.assertOnRenderThread();
		if (i != DEPTH.func) {
			DEPTH.func = i;
			CommandContext ctx = VulkanicAPI.getImmediateContext();
			net.vulkanic.VulkanicAPI.setDepthTest(ctx, i);
		}
	}

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

	public static int glGetProgrami(int i, int j) {
		RenderSystem.assertOnRenderThread();
		return net.vulkanic.VulkanicAPI.queryProgramParameter(i, j);
	}

	public static void glAttachShader(int i, int j) {
		RenderSystem.assertOnRenderThread();
		net.vulkanic.VulkanicAPI.attachShaderToProgram(i, j);
	}

	public static void glDeleteShader(int i) {
		RenderSystem.assertOnRenderThread();
		net.vulkanic.VulkanicAPI.disposeShaderObject(i);
	}

	public static int glCreateShader(int i) {
		RenderSystem.assertOnRenderThread();
		return net.vulkanic.VulkanicAPI.constructShaderObject(i);
	}

	public static void glShaderSource(int i, String string) {
		RenderSystem.assertOnRenderThread();
		byte[] bs = string.getBytes(Charsets.UTF_8);
		ByteBuffer byteBuffer = MemoryUtil.memAlloc(bs.length + 1);
		byteBuffer.put(bs);
		byteBuffer.put((byte)0);
		byteBuffer.flip();

		try (MemoryStack memoryStack = MemoryStack.stackPush()) {
			PointerBuffer pointerBuffer = memoryStack.mallocPointer(1);
			pointerBuffer.put(byteBuffer);
			VulkanicAPI.uploadShaderSource(i, pointerBuffer.address0(), 1, 0L);
		} finally {
			MemoryUtil.memFree(byteBuffer);
		}
	}

	public static void glCompileShader(int i) {
		RenderSystem.assertOnRenderThread();
		net.vulkanic.VulkanicAPI.compileShaderSource(i);
	}

	public static int glGetShaderi(int i, int j) {
		RenderSystem.assertOnRenderThread();
		return net.vulkanic.VulkanicAPI.queryShaderParameter(i, j);
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

	public static int glCreateProgram() {
		RenderSystem.assertOnRenderThread();
		return net.vulkanic.VulkanicAPI.constructProgramObject();
	}

	public static void glDeleteProgram(int i) {
		RenderSystem.assertOnRenderThread();
		net.vulkanic.VulkanicAPI.disposeProgramObject(i);
	}

	public static void glLinkProgram(int i) {
		RenderSystem.assertOnRenderThread();
		net.vulkanic.VulkanicAPI.linkProgramBinary(i);
	}

	public static int _glGetUniformLocation(int programId, CharSequence name) {
		RenderSystem.assertOnRenderThread();
		int location = net.vulkanic.VulkanicAPI.locateUniformVariable(programId, name);
		
		// Iris: Handle sampler name fallbacks for extended shaders
		if (location == -1 && name.equals("Sampler0")) {
			location = net.vulkanic.VulkanicAPI.locateUniformVariable(programId, "tex");
			
			if (location == -1) {
				location = net.vulkanic.VulkanicAPI.locateUniformVariable(programId, "gtexture");
				
				if (location == -1) {
					location = net.vulkanic.VulkanicAPI.locateUniformVariable(programId, "texture");
				}
			}
		}
		
		if (location == -1 && name.equals("Sampler1")) {
			location = net.vulkanic.VulkanicAPI.locateUniformVariable(programId, "iris_overlay");
		}
		
		if (location == -1 && name.equals("Sampler2")) {
			location = net.vulkanic.VulkanicAPI.locateUniformVariable(programId, "lightmap");
		}
		
		return location;
	}

	public static void _glUniform1i(int i, int j) {
		RenderSystem.assertOnRenderThread();
		net.vulkanic.VulkanicAPI.assignUniformInteger(i, j);
	}

	public static void _glBindAttribLocation(int i, int j, CharSequence charSequence) {
		RenderSystem.assertOnRenderThread();
		net.vulkanic.VulkanicAPI.bindAttributeLocation(i, j, charSequence);
	}

	public static void incrementTrackedBuffers() {
		numBuffers++;
		PLOT_BUFFERS.setValue(numBuffers);
	}

	public static int _glGenBuffers() {
		RenderSystem.assertOnRenderThread();
		incrementTrackedBuffers();
		return net.vulkanic.VulkanicAPI.allocateBufferObject();
	}

	public static int _glGenVertexArrays() {
		RenderSystem.assertOnRenderThread();
		return net.vulkanic.VulkanicAPI.createVertexArrayObject();
	}

	public static void _glBindBuffer(int i, int j) {
		RenderSystem.assertOnRenderThread();
		net.vulkanic.VulkanicAPI.attachBuffer(i, j);
	}

	public static void _glBindVertexArray(int i) {
		RenderSystem.assertOnRenderThread();
		net.vulkanic.VulkanicAPI.selectVertexArray(i);
	}

	public static void _glBufferData(int i, ByteBuffer byteBuffer, int j) {
		RenderSystem.assertOnRenderThread();
		net.vulkanic.VulkanicAPI.fillBufferWithData(i, byteBuffer, j);
	}

	public static void _glBufferSubData(int i, int j, ByteBuffer byteBuffer) {
		RenderSystem.assertOnRenderThread();
		net.vulkanic.VulkanicAPI.fillBufferSubregion(i, (long)j, byteBuffer);
	}

	public static void _glBufferData(int i, long l, int j) {
		RenderSystem.assertOnRenderThread();
		net.vulkanic.VulkanicAPI.fillBufferWithSize(i, l, j);
	}

	@Nullable
	public static ByteBuffer _glMapBufferRange(int i, int j, int k, int l) {
		RenderSystem.assertOnRenderThread();
		return net.vulkanic.VulkanicAPI.mapBufferRegion(i, j, k, l);
	}

	public static void _glUnmapBuffer(int i) {
		RenderSystem.assertOnRenderThread();
		net.vulkanic.VulkanicAPI.unmapBufferData(i);
	}

	public static void _glDeleteBuffers(int i) {
		RenderSystem.assertOnRenderThread();
		numBuffers--;
		PLOT_BUFFERS.setValue(numBuffers);
		net.vulkanic.VulkanicAPI.releaseBufferObject(i);
	}

	public static void _glBindFramebuffer(int i, int j) {
		if ((i == 36008 || i == 36160) && readFbo != j) {
			net.vulkanic.VulkanicAPI.attachFramebuffer(36008, j);
			readFbo = j;
		}

		if ((i == 36009 || i == 36160) && writeFbo != j) {
			net.vulkanic.VulkanicAPI.attachFramebuffer(36009, j);
			writeFbo = j;
		}
	}

	public static int getFrameBuffer(int i) {
		if (i == 36008) {
			return readFbo;
		} else {
			return i == 36009 ? writeFbo : 0;
		}
	}

	public static void _glBlitFrameBuffer(int i, int j, int k, int l, int m, int n, int o, int p, int q, int r) {
		RenderSystem.assertOnRenderThread();
		net.vulkanic.VulkanicAPI.copyFramebufferRegion(i, j, k, l, m, n, o, p, q, r);
	}

	public static void _glDeleteFramebuffers(int i) {
		RenderSystem.assertOnRenderThread();
		net.vulkanic.VulkanicAPI.destroyFramebufferObject(i);
		if (readFbo == i) {
			readFbo = 0;
		}

		if (writeFbo == i) {
			writeFbo = 0;
		}
	}

	public static int glGenFramebuffers() {
		RenderSystem.assertOnRenderThread();
		return net.vulkanic.VulkanicAPI.generateFramebufferObject();
	}

	public static void _glFramebufferTexture2D(int i, int j, int k, int l, int m) {
		RenderSystem.assertOnRenderThread();
		net.vulkanic.VulkanicAPI.attachTextureToFramebuffer(i, j, k, l, m);
	}

	public static void glBlendFuncSeparate(int i, int j, int k, int l) {
		RenderSystem.assertOnRenderThread();
		net.vulkanic.VulkanicAPI.configureBlendFunc(i, j, k, l);
	}

	public static String glGetShaderInfoLog(int i, int j) {
		RenderSystem.assertOnRenderThread();
		return net.vulkanic.VulkanicAPI.retrieveShaderInfoLog(i);
	}

	public static String glGetProgramInfoLog(int i, int j) {
		RenderSystem.assertOnRenderThread();
		return net.vulkanic.VulkanicAPI.retrieveProgramInfoLog(i);
	}

	public static void _enableCull() {
		RenderSystem.assertOnRenderThread();
		CULL.enable.enable();
	}

	public static void _disableCull() {
		RenderSystem.assertOnRenderThread();
		CULL.enable.disable();
	}

	public static void _polygonMode(int i, int j) {
		RenderSystem.assertOnRenderThread();
		net.vulkanic.VulkanicAPI.configurePolygonMode(i, j);
	}

	public static void _enablePolygonOffset() {
		RenderSystem.assertOnRenderThread();
		POLY_OFFSET.fill.enable();
	}

	public static void _disablePolygonOffset() {
		RenderSystem.assertOnRenderThread();
		POLY_OFFSET.fill.disable();
	}

	public static void _polygonOffset(float f, float g) {
		RenderSystem.assertOnRenderThread();
		if (f != POLY_OFFSET.factor || g != POLY_OFFSET.units) {
			POLY_OFFSET.factor = f;
			POLY_OFFSET.units = g;
			net.vulkanic.VulkanicAPI.configurePolygonOffset(f, g);
		}
	}

	public static void _enableColorLogicOp() {
		RenderSystem.assertOnRenderThread();
		COLOR_LOGIC.enable.enable();
	}

	public static void _disableColorLogicOp() {
		RenderSystem.assertOnRenderThread();
		COLOR_LOGIC.enable.disable();
	}

	public static void _logicOp(int i) {
		RenderSystem.assertOnRenderThread();
		if (i != COLOR_LOGIC.op) {
			COLOR_LOGIC.op = i;
			net.vulkanic.VulkanicAPI.configureLogicOp(i);
		}
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
			net.vulkanic.VulkanicAPI.activateTextureUnit(i);
		}
	}

	public static void _texParameter(int i, int j, int k) {
		RenderSystem.assertOnRenderThread();
		net.vulkanic.VulkanicAPI.configureTextureParameter(i, j, k);
	}

	public static int _getTexLevelParameter(int i, int j, int k) {
		return VulkanicAPI.queryTextureLevelParameter(i, j, k);
	}

	public static int _genTexture() {
		RenderSystem.assertOnRenderThread();
		numTextures++;
		PLOT_TEXTURES.setValue(numTextures);
		return net.vulkanic.VulkanicAPI.createTexture();
	}

	public static void _deleteTexture(int i) {
		RenderSystem.assertOnRenderThread();
		net.vulkanic.VulkanicAPI.removeTexture(i);

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

	public static void _bindTexture(int i) {
		RenderSystem.assertOnRenderThread();
		CommandContext ctx = VulkanicAPI.getImmediateContext();
		net.vulkanic.VulkanicAPI.bindTexture2D(ctx, i);
	}

	public static void _texImage2D(int i, int j, int k, int l, int m, int n, int o, int p, @Nullable ByteBuffer byteBuffer) {
		RenderSystem.assertOnRenderThread();
		net.vulkanic.VulkanicAPI.transferTexture2DImage(i, j, k, l, m, n, o, p, byteBuffer);
		
		// Iris: Track texture image data (from MixinGlStateManager texture)
		net.irisshaders.iris.pbr.TextureInfoCache.INSTANCE.onTexImage2D(i, j, k, l, m, n, o, p, byteBuffer);
	}

	public static void _texSubImage2D(int i, int j, int k, int l, int m, int n, int o, int p, long q) {
		RenderSystem.assertOnRenderThread();
		net.vulkanic.VulkanicAPI.transferTexture2DSubregion(i, j, k, l, m, n, o, p, q);
	}

	public static void _texSubImage2D(int i, int j, int k, int l, int m, int n, int o, int p, ByteBuffer byteBuffer) {
		RenderSystem.assertOnRenderThread();
		net.vulkanic.VulkanicAPI.transferTexture2DSubregionBuf(i, j, k, l, m, n, o, p, byteBuffer);
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

	public static void _clear(int i) {
		RenderSystem.assertOnRenderThread();
		CommandContext ctx = VulkanicAPI.getImmediateContext();
		net.vulkanic.VulkanicAPI.clearBuffers(ctx, i);
		if (MacosUtil.IS_MACOS) {
			_getError();
		}
	}

	public static void _vertexAttribPointer(int i, int j, int k, boolean bl, int l, long m) {
		RenderSystem.assertOnRenderThread();
		net.vulkanic.VulkanicAPI.configureVertexAttribute(i, j, k, bl, l, m);
	}

	public static void _vertexAttribIPointer(int i, int j, int k, int l, long m) {
		RenderSystem.assertOnRenderThread();
		net.vulkanic.VulkanicAPI.configureVertexAttributeInteger(i, j, k, l, m);
	}

	public static void _enableVertexAttribArray(int i) {
		RenderSystem.assertOnRenderThread();
		net.vulkanic.VulkanicAPI.activateVertexAttribute(i);
	}

	public static void _drawElements(int i, int j, int k, long l) {
		RenderSystem.assertOnRenderThread();
		// Iris: From MixinGlStateManager_DepthColorOverride - tessellation support
		int mode = i;
		if (mode == VulkanicAPI.GL_TRIANGLES && net.irisshaders.iris.vertices.ImmediateState.usingTessellation) {
			mode = VulkanicAPI.GL_PATCHES;
		}
		
		net.vulkanic.VulkanicAPI.drawIndexedElements(mode, j, k, l);
	}

	public static void _drawArrays(int i, int j, int k) {
		RenderSystem.assertOnRenderThread();
		net.vulkanic.VulkanicAPI.drawPrimitiveArrays(i, j, k);
	}

	public static void _pixelStore(int i, int j) {
		RenderSystem.assertOnRenderThread();
		net.vulkanic.VulkanicAPI.setPixelStoreMode(i, j);
	}

	public static void _readPixels(int i, int j, int k, int l, int m, int n, long o) {
		RenderSystem.assertOnRenderThread();
		VulkanicAPI.readFramebufferPixels(i, j, k, l, m, n, o);
	}

	public static int _getError() {
		RenderSystem.assertOnRenderThread();
		return net.vulkanic.VulkanicAPI.checkForErrors();
	}

	public static void clearGlErrors() {
		RenderSystem.assertOnRenderThread();

		while (VulkanicAPI.pollErrorCode() != 0) {
		}
	}

	public static String _getString(int i) {
		RenderSystem.assertOnRenderThread();
		return VulkanicAPI.queryStringInfo(i);
	}

	public static int _getInteger(int i) {
		RenderSystem.assertOnRenderThread();
		return VulkanicAPI.queryIntegerState(i);
	}

	public static long _glFenceSync(int i, int j) {
		RenderSystem.assertOnRenderThread();
		return net.vulkanic.VulkanicAPI.createFenceSync(i, j);
	}

	public static int _glClientWaitSync(long l, int i, long m) {
		RenderSystem.assertOnRenderThread();
		return net.vulkanic.VulkanicAPI.waitForSync(l, i, m);
	}

	public static void _glDeleteSync(long l) {
		RenderSystem.assertOnRenderThread();
		net.vulkanic.VulkanicAPI.destroySync(l);
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
	static class ColorLogicState {
		public final GlStateManager.BooleanState enable = new GlStateManager.BooleanState(3058);
		public int op = 5379;
	}

	@Environment(EnvType.CLIENT)
	public static class ColorMask {
		public boolean red = true;
		public boolean green = true;
		public boolean blue = true;
		public boolean alpha = true;
	}

	@Environment(EnvType.CLIENT)
	static class CullState {
		public final GlStateManager.BooleanState enable = new GlStateManager.BooleanState(2884);
	}

	@Environment(EnvType.CLIENT)
	public static class DepthState {
		public final GlStateManager.BooleanState mode = new GlStateManager.BooleanState(2929);
		public boolean mask = true;
		public int func = 513;
	}

	@Environment(EnvType.CLIENT)
	static class PolygonOffsetState {
		public final GlStateManager.BooleanState fill = new GlStateManager.BooleanState(32823);
		public float factor;
		public float units;
	}

	@Environment(EnvType.CLIENT)
	static class ScissorState {
		public final GlStateManager.BooleanState mode = new GlStateManager.BooleanState(3089);
	}

	@Environment(EnvType.CLIENT)
	public static class TextureState {
		public int binding;
	}
}
