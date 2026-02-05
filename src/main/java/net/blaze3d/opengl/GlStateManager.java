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
import org.jetbrains.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
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
		
		// Route through Vulkanic abstraction layer
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().disableScissorTest();
			// Update state tracker to stay in sync
			SCISSOR.mode.enabled = false;
		} else {
			SCISSOR.mode.disable();
		}
	}

	public static void _enableScissorTest() {
		RenderSystem.assertOnRenderThread();
		
		// Route through Vulkanic abstraction layer
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().enableScissorTest();
			// Update state tracker to stay in sync
			SCISSOR.mode.enabled = true;
		} else {
			SCISSOR.mode.enable();
		}
	}

	public static void _scissorBox(int i, int j, int k, int l) {
		RenderSystem.assertOnRenderThread();
		
		// Route through Vulkanic abstraction layer
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().setScissor(i, j, k, l);
		} else {
			GL11.glScissor(i, j, k, l);
		}
	}

	public static void _disableDepthTest() {
		RenderSystem.assertOnRenderThread();
		
		// Route through Vulkanic abstraction layer
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().disableDepthTest();
			// Update state tracker to stay in sync
			DEPTH.mode.enabled = false;
		} else {
			DEPTH.mode.disable();
		}
	}

	public static void _enableDepthTest() {
		RenderSystem.assertOnRenderThread();
		
		// Route through Vulkanic abstraction layer
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().enableDepthTest();
			// Update state tracker to stay in sync
			DEPTH.mode.enabled = true;
		} else {
			DEPTH.mode.enable();
		}
	}

	public static void _depthFunc(int i) {
		RenderSystem.assertOnRenderThread();
		if (i != DEPTH.func) {
			DEPTH.func = i;
			
			// Route through Vulkanic abstraction layer
			if (net.vulkanic.Vulkanic.isInitialized()) {
				net.vulkanic.Vulkanic.getDevice().createCommandBuffer().setDepthFunc(i);
			} else {
				GL11.glDepthFunc(i);
			}
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
			
			// Route through Vulkanic abstraction layer
			if (net.vulkanic.Vulkanic.isInitialized()) {
				net.vulkanic.Vulkanic.getDevice().createCommandBuffer().setDepthMask(bl);
			} else {
				GL11.glDepthMask(bl);
			}
		}
	}

	public static void _disableBlend() {
		RenderSystem.assertOnRenderThread();
		// Iris: Check blend lock (from MixinGlStateManager_BlendOverride)
		if (net.irisshaders.iris.gl.blending.BlendModeStorage.isBlendLocked()) {
			net.irisshaders.iris.gl.blending.BlendModeStorage.deferBlendModeToggle(false);
			return;
		}
		
		// Route through Vulkanic abstraction layer
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().disableBlend();
			// Update state tracker to stay in sync
			BLEND.mode.enabled = false;
		} else {
			BLEND.mode.disable();
		}
	}

	public static void _enableBlend() {
		RenderSystem.assertOnRenderThread();
		// Iris: Check blend lock (from MixinGlStateManager_BlendOverride)
		if (net.irisshaders.iris.gl.blending.BlendModeStorage.isBlendLocked()) {
			net.irisshaders.iris.gl.blending.BlendModeStorage.deferBlendModeToggle(true);
			return;
		}
		
		// Route through Vulkanic abstraction layer
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().enableBlend();
			// Update state tracker to stay in sync
			BLEND.mode.enabled = true;
		} else {
			BLEND.mode.enable();
		}
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
			
			// Route through Vulkanic abstraction layer
			if (net.vulkanic.Vulkanic.isInitialized()) {
				net.vulkanic.Vulkanic.getDevice().createCommandBuffer().setBlendFuncSeparate(i, j, k, l);
			} else {
				glBlendFuncSeparate(i, j, k, l);
			}
		}
		
		// Iris: Notify listener of blend function changes
		if (blendFuncListener != null) {
			blendFuncListener.run();
		}
	}

	public static int glGetProgrami(int i, int j) {
		RenderSystem.assertOnRenderThread();
		if (net.vulkanic.Vulkanic.isInitialized()) {
			return net.vulkanic.Vulkanic.getDevice().createCommandBuffer().getProgrami(i, j);
		}
		return GL20.glGetProgrami(i, j);
	}

	public static void glAttachShader(int i, int j) {
		RenderSystem.assertOnRenderThread();
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().attachShader(i, j);
		} else {
			GL20.glAttachShader(i, j);
		}
	}

	public static void glDeleteShader(int i) {
		RenderSystem.assertOnRenderThread();
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().deleteShader(i);
		} else {
			GL20.glDeleteShader(i);
		}
	}

	public static int glCreateShader(int i) {
		RenderSystem.assertOnRenderThread();
		if (net.vulkanic.Vulkanic.isInitialized()) {
			return net.vulkanic.Vulkanic.getDevice().createShaderObject(i);
		}
		return GL20.glCreateShader(i);
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
			if (net.vulkanic.Vulkanic.isInitialized()) {
				// For Vulkanic, use the standard glShaderSource with CharSequence array
				GL20C.nglShaderSource(i, 1, pointerBuffer.address0(), 0L);
			} else {
				GL20C.nglShaderSource(i, 1, pointerBuffer.address0(), 0L);
			}
		} finally {
			MemoryUtil.memFree(byteBuffer);
		}
	}

	public static void glCompileShader(int i) {
		RenderSystem.assertOnRenderThread();
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().compileShader(i);
		} else {
			GL20.glCompileShader(i);
		}
	}

	public static int glGetShaderi(int i, int j) {
		RenderSystem.assertOnRenderThread();
		if (net.vulkanic.Vulkanic.isInitialized()) {
			return net.vulkanic.Vulkanic.getDevice().createCommandBuffer().getShaderi(i, j);
		}
		return GL20.glGetShaderi(i, j);
	}

	public static void _glUseProgram(int i) {
		RenderSystem.assertOnRenderThread();
		// Iris: From MixinGlStateManager_FramebufferBinding - avoid redundant program switches
		if (iris$program == 0 && i == 0) {
			return;
		}
		
		net.irisshaders.iris.gl.IrisRenderSystem.onProgramUse();
		
		iris$program = i;
		
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().useProgram(i);
		} else {
			GL20.glUseProgram(i);
		}
		
		// Iris: From MixinGlStateManager_DepthColorOverride - reset tessellation flag
		net.irisshaders.iris.vertices.ImmediateState.usingTessellation = false;
	}

	public static int glCreateProgram() {
		RenderSystem.assertOnRenderThread();
		if (net.vulkanic.Vulkanic.isInitialized()) {
			return net.vulkanic.Vulkanic.getDevice().createProgramObject();
		}
		return GL20.glCreateProgram();
	}

	public static void glDeleteProgram(int i) {
		RenderSystem.assertOnRenderThread();
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().deleteProgram(i);
		} else {
			GL20.glDeleteProgram(i);
		}
	}

	public static void glLinkProgram(int i) {
		RenderSystem.assertOnRenderThread();
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().linkProgram(i);
		} else {
			GL20.glLinkProgram(i);
		}
	}

	public static int _glGetUniformLocation(int programId, CharSequence name) {
		RenderSystem.assertOnRenderThread();
		int location;
		
		if (net.vulkanic.Vulkanic.isInitialized()) {
			location = net.vulkanic.Vulkanic.getDevice().createCommandBuffer().getUniformLocation(programId, name);
		} else {
			location = GL20.glGetUniformLocation(programId, name);
		}
		
		// Iris: Handle sampler name fallbacks for extended shaders
		if (location == -1 && name.equals("Sampler0")) {
			if (net.vulkanic.Vulkanic.isInitialized()) {
				location = net.vulkanic.Vulkanic.getDevice().createCommandBuffer().getUniformLocation(programId, "tex");
				if (location == -1) {
					location = net.vulkanic.Vulkanic.getDevice().createCommandBuffer().getUniformLocation(programId, "gtexture");
					if (location == -1) {
						location = net.vulkanic.Vulkanic.getDevice().createCommandBuffer().getUniformLocation(programId, "texture");
					}
				}
			} else {
				location = GL20.glGetUniformLocation(programId, "tex");
				if (location == -1) {
					location = GL20.glGetUniformLocation(programId, "gtexture");
					if (location == -1) {
						location = GL20.glGetUniformLocation(programId, "texture");
					}
				}
			}
		}
		
		if (location == -1 && name.equals("Sampler1")) {
			if (net.vulkanic.Vulkanic.isInitialized()) {
				location = net.vulkanic.Vulkanic.getDevice().createCommandBuffer().getUniformLocation(programId, "iris_overlay");
			} else {
				location = GL20.glGetUniformLocation(programId, "iris_overlay");
			}
		}
		
		if (location == -1 && name.equals("Sampler2")) {
			if (net.vulkanic.Vulkanic.isInitialized()) {
				location = net.vulkanic.Vulkanic.getDevice().createCommandBuffer().getUniformLocation(programId, "lightmap");
			} else {
				location = GL20.glGetUniformLocation(programId, "lightmap");
			}
		}
		
		return location;
	}

	public static void _glUniform1i(int i, int j) {
		RenderSystem.assertOnRenderThread();
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().uniform1i(i, j);
		} else {
			GL20.glUniform1i(i, j);
		}
	}

	public static void _glBindAttribLocation(int i, int j, CharSequence charSequence) {
		RenderSystem.assertOnRenderThread();
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().bindAttribLocation(i, j, charSequence);
		} else {
			GL20.glBindAttribLocation(i, j, charSequence);
		}
	}

	public static void incrementTrackedBuffers() {
		numBuffers++;
		PLOT_BUFFERS.setValue(numBuffers);
	}

	public static int _glGenBuffers() {
		RenderSystem.assertOnRenderThread();
		incrementTrackedBuffers();
		if (net.vulkanic.Vulkanic.isInitialized()) {
			return net.vulkanic.Vulkanic.getDevice().genBuffer();
		}
		return GL15.glGenBuffers();
	}

	public static int _glGenVertexArrays() {
		RenderSystem.assertOnRenderThread();
		if (net.vulkanic.Vulkanic.isInitialized()) {
			return net.vulkanic.Vulkanic.getDevice().genVertexArray();
		}
		return GL30.glGenVertexArrays();
	}

	public static void _glBindBuffer(int i, int j) {
		RenderSystem.assertOnRenderThread();
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().bindBuffer(i, j);
		} else {
			GL15.glBindBuffer(i, j);
		}
	}

	public static void _glBindVertexArray(int i) {
		RenderSystem.assertOnRenderThread();
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().bindVertexArray(i);
		} else {
			GL30.glBindVertexArray(i);
		}
	}

	public static void _glBufferData(int i, ByteBuffer byteBuffer, int j) {
		RenderSystem.assertOnRenderThread();
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().bufferData(i, byteBuffer, j);
		} else {
			GL15.glBufferData(i, byteBuffer, j);
		}
	}

	public static void _glBufferSubData(int i, int j, ByteBuffer byteBuffer) {
		RenderSystem.assertOnRenderThread();
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().bufferSubData(i, j, byteBuffer);
		} else {
			GL15.glBufferSubData(i, (long)j, byteBuffer);
		}
	}

	public static void _glBufferData(int i, long l, int j) {
		RenderSystem.assertOnRenderThread();
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().bufferData(i, l, j);
		} else {
			GL15.glBufferData(i, l, j);
		}
	}

	@Nullable
	public static ByteBuffer _glMapBufferRange(int i, int j, int k, int l) {
		RenderSystem.assertOnRenderThread();
		if (net.vulkanic.Vulkanic.isInitialized()) {
			return net.vulkanic.Vulkanic.getDevice().createCommandBuffer().mapBufferRange(i, j, k, l);
		}
		return GL30.glMapBufferRange(i, j, k, l);
	}

	public static void _glUnmapBuffer(int i) {
		RenderSystem.assertOnRenderThread();
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().unmapBuffer(i);
		} else {
			GL15.glUnmapBuffer(i);
		}
	}

	public static void _glDeleteBuffers(int i) {
		RenderSystem.assertOnRenderThread();
		numBuffers--;
		PLOT_BUFFERS.setValue(numBuffers);
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().deleteBuffer(i);
		} else {
			GL15.glDeleteBuffers(i);
		}
	}

	public static void _glBindFramebuffer(int i, int j) {
		if ((i == 36008 || i == 36160) && readFbo != j) {
			if (net.vulkanic.Vulkanic.isInitialized()) {
				net.vulkanic.Vulkanic.getDevice().createCommandBuffer().bindFramebuffer(36008, j);
			} else {
				GL30.glBindFramebuffer(36008, j);
			}
			readFbo = j;
		}

		if ((i == 36009 || i == 36160) && writeFbo != j) {
			if (net.vulkanic.Vulkanic.isInitialized()) {
				net.vulkanic.Vulkanic.getDevice().createCommandBuffer().bindFramebuffer(36009, j);
			} else {
				GL30.glBindFramebuffer(36009, j);
			}
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
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().blitFramebuffer(i, j, k, l, m, n, o, p, q, r);
		} else {
			GL30.glBlitFramebuffer(i, j, k, l, m, n, o, p, q, r);
		}
	}

	public static void _glDeleteFramebuffers(int i) {
		RenderSystem.assertOnRenderThread();
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().deleteFramebuffer(i);
		} else {
			GL30.glDeleteFramebuffers(i);
		}
		if (readFbo == i) {
			readFbo = 0;
		}

		if (writeFbo == i) {
			writeFbo = 0;
		}
	}

	public static int glGenFramebuffers() {
		RenderSystem.assertOnRenderThread();
		if (net.vulkanic.Vulkanic.isInitialized()) {
			return net.vulkanic.Vulkanic.getDevice().genFramebuffer();
		}
		return GL30.glGenFramebuffers();
	}

	public static void _glFramebufferTexture2D(int i, int j, int k, int l, int m) {
		RenderSystem.assertOnRenderThread();
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().framebufferTexture2D(i, j, k, l, m);
		} else {
			GL30.glFramebufferTexture2D(i, j, k, l, m);
		}
	}

	public static void glBlendFuncSeparate(int i, int j, int k, int l) {
		RenderSystem.assertOnRenderThread();
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().blendFuncSeparate(i, j, k, l);
		} else {
			GL14.glBlendFuncSeparate(i, j, k, l);
		}
	}

	public static String glGetShaderInfoLog(int i, int j) {
		RenderSystem.assertOnRenderThread();
		if (net.vulkanic.Vulkanic.isInitialized()) {
			return net.vulkanic.Vulkanic.getDevice().createCommandBuffer().getShaderInfoLog(i, j);
		}
		return GL20.glGetShaderInfoLog(i, j);
	}

	public static String glGetProgramInfoLog(int i, int j) {
		RenderSystem.assertOnRenderThread();
		if (net.vulkanic.Vulkanic.isInitialized()) {
			return net.vulkanic.Vulkanic.getDevice().createCommandBuffer().getProgramInfoLog(i, j);
		}
		return GL20.glGetProgramInfoLog(i, j);
	}

	public static void _enableCull() {
		RenderSystem.assertOnRenderThread();
		
		// Route through Vulkanic abstraction layer
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().enableCull();
			// Update state tracker to stay in sync
			CULL.enable.enabled = true;
		} else {
			CULL.enable.enable();
		}
	}

	public static void _disableCull() {
		RenderSystem.assertOnRenderThread();
		
		// Route through Vulkanic abstraction layer
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().disableCull();
			// Update state tracker to stay in sync
			CULL.enable.enabled = false;
		} else {
			CULL.enable.disable();
		}
	}

	public static void _polygonMode(int i, int j) {
		RenderSystem.assertOnRenderThread();
		
		// Route through Vulkanic abstraction layer
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().setPolygonMode(i, j);
		} else {
			GL11.glPolygonMode(i, j);
		}
	}

	public static void _enablePolygonOffset() {
		RenderSystem.assertOnRenderThread();
		
		// Route through Vulkanic abstraction layer
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().enablePolygonOffset();
			// Update state tracker to stay in sync
			POLY_OFFSET.fill.enabled = true;
		} else {
			POLY_OFFSET.fill.enable();
		}
	}

	public static void _disablePolygonOffset() {
		RenderSystem.assertOnRenderThread();
		
		// Route through Vulkanic abstraction layer
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().disablePolygonOffset();
			// Update state tracker to stay in sync
			POLY_OFFSET.fill.enabled = false;
		} else {
			POLY_OFFSET.fill.disable();
		}
	}

	public static void _polygonOffset(float f, float g) {
		RenderSystem.assertOnRenderThread();
		if (f != POLY_OFFSET.factor || g != POLY_OFFSET.units) {
			POLY_OFFSET.factor = f;
			POLY_OFFSET.units = g;
			
			// Route through Vulkanic abstraction layer
			if (net.vulkanic.Vulkanic.isInitialized()) {
				net.vulkanic.Vulkanic.getDevice().createCommandBuffer().setPolygonOffset(f, g);
			} else {
				GL11.glPolygonOffset(f, g);
			}
		}
	}

	public static void _enableColorLogicOp() {
		RenderSystem.assertOnRenderThread();
		
		// Route through Vulkanic abstraction layer
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().enableColorLogicOp();
			// Update state tracker to stay in sync
			COLOR_LOGIC.enable.enabled = true;
		} else {
			COLOR_LOGIC.enable.enable();
		}
	}

	public static void _disableColorLogicOp() {
		RenderSystem.assertOnRenderThread();
		
		// Route through Vulkanic abstraction layer
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().disableColorLogicOp();
			// Update state tracker to stay in sync
			COLOR_LOGIC.enable.enabled = false;
		} else {
			COLOR_LOGIC.enable.disable();
		}
	}

	public static void _logicOp(int i) {
		RenderSystem.assertOnRenderThread();
		if (i != COLOR_LOGIC.op) {
			COLOR_LOGIC.op = i;
			
			// Route through Vulkanic abstraction layer
			if (net.vulkanic.Vulkanic.isInitialized()) {
				net.vulkanic.Vulkanic.getDevice().createCommandBuffer().setLogicOp(i);
			} else {
				GL11.glLogicOp(i);
			}
		}
	}

	public static void _activeTexture(int i) {
		RenderSystem.assertOnRenderThread();
		// Iris: From MixinGlStateManager_FramebufferBinding - validate texture unit range
		int tex = i - org.lwjgl.opengl.GL46C.GL_TEXTURE0;
		if (tex < 0 || tex > 128) {
			throw new IllegalArgumentException("Texture " + tex + " out of range");
		}
		
		if (activeTexture != i - 33984) {
			activeTexture = i - 33984;
			
			// Route through Vulkanic abstraction layer
			if (net.vulkanic.Vulkanic.isInitialized()) {
				net.vulkanic.Vulkanic.getDevice().createCommandBuffer().setActiveTexture(i);
			} else {
				GL13.glActiveTexture(i);
			}
		}
	}

	public static void _texParameter(int i, int j, int k) {
		RenderSystem.assertOnRenderThread();
		
		// Route through Vulkanic abstraction layer
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().setTexParameter(i, j, k);
		} else {
			GL11.glTexParameteri(i, j, k);
		}
	}

	public static int _getTexLevelParameter(int i, int j, int k) {
		if (net.vulkanic.Vulkanic.isInitialized()) {
			return net.vulkanic.Vulkanic.getDevice().createCommandBuffer().getTexLevelParameteri(i, j, k);
		} else {
			return GL11.glGetTexLevelParameteri(i, j, k);
		}
	}

	public static int _genTexture() {
		RenderSystem.assertOnRenderThread();
		numTextures++;
		PLOT_TEXTURES.setValue(numTextures);
		
		// Route through Vulkanic abstraction layer
		if (net.vulkanic.Vulkanic.isInitialized()) {
			return net.vulkanic.Vulkanic.getDevice().createCommandBuffer().genTexture();
		} else {
			return GL11.glGenTextures();
		}
	}

	public static void _deleteTexture(int i) {
		RenderSystem.assertOnRenderThread();
		
		// Route through Vulkanic abstraction layer
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().deleteTexture(i);
		} else {
			GL11.glDeleteTextures(i);
		}

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
		if (i != TEXTURES[activeTexture].binding) {
			TEXTURES[activeTexture].binding = i;
			
			// Route through Vulkanic abstraction layer
			if (net.vulkanic.Vulkanic.isInitialized()) {
				net.vulkanic.Vulkanic.getDevice().createCommandBuffer().bindTexture(i);
			} else {
				GL11.glBindTexture(3553, i);
			}
		}
	}

	public static void _texImage2D(int i, int j, int k, int l, int m, int n, int o, int p, @Nullable ByteBuffer byteBuffer) {
		RenderSystem.assertOnRenderThread();
		
		// Route through Vulkanic abstraction layer
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().texImage2D(i, j, k, l, m, n, o, p, byteBuffer);
		} else {
			GL11.glTexImage2D(i, j, k, l, m, n, o, p, byteBuffer);
		}
		
		// Iris: Track texture image data (from MixinGlStateManager texture)
		net.irisshaders.iris.pbr.TextureInfoCache.INSTANCE.onTexImage2D(i, j, k, l, m, n, o, p, byteBuffer);
	}

	public static void _texSubImage2D(int i, int j, int k, int l, int m, int n, int o, int p, long q) {
		RenderSystem.assertOnRenderThread();
		
		// Route through Vulkanic abstraction layer
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().texSubImage2D(i, j, k, l, m, n, o, p, q);
		} else {
			GL11.glTexSubImage2D(i, j, k, l, m, n, o, p, q);
		}
	}

	public static void _texSubImage2D(int i, int j, int k, int l, int m, int n, int o, int p, ByteBuffer byteBuffer) {
		RenderSystem.assertOnRenderThread();
		
		// Route through Vulkanic abstraction layer
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().texSubImage2D(i, j, k, l, m, n, o, p, byteBuffer);
		} else {
			GL11.glTexSubImage2D(i, j, k, l, m, n, o, p, byteBuffer);
		}
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
		
		// Route through Vulkanic abstraction layer
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().setViewport(i, j, k, l);
		} else {
			RenderSystem.assertOnRenderThreadOrInit();
			GL11.glViewport(i, j, k, l);
		}
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
			
			// Route through Vulkanic abstraction layer
			if (net.vulkanic.Vulkanic.isInitialized()) {
				net.vulkanic.Vulkanic.getDevice().createCommandBuffer().setColorMask(bl, bl2, bl3, bl4);
			} else {
				GL11.glColorMask(bl, bl2, bl3, bl4);
			}
		}
	}

	public static void _clear(int i) {
		RenderSystem.assertOnRenderThread();
		
		// Route through Vulkanic abstraction layer
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().clearBuffers(i);
		} else {
			GL11.glClear(i);
		}
		
		if (MacosUtil.IS_MACOS) {
			_getError();
		}
	}

	public static void _vertexAttribPointer(int i, int j, int k, boolean bl, int l, long m) {
		RenderSystem.assertOnRenderThread();
		
		// Route through Vulkanic abstraction layer
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().vertexAttribPointer(i, j, k, bl, l, m);
		} else {
			GL20.glVertexAttribPointer(i, j, k, bl, l, m);
		}
	}

	public static void _vertexAttribIPointer(int i, int j, int k, int l, long m) {
		RenderSystem.assertOnRenderThread();
		
		// Route through Vulkanic abstraction layer
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().vertexAttribIPointer(i, j, k, l, m);
		} else {
			GL30.glVertexAttribIPointer(i, j, k, l, m);
		}
	}

	public static void _enableVertexAttribArray(int i) {
		RenderSystem.assertOnRenderThread();
		
		// Route through Vulkanic abstraction layer
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().enableVertexAttribArray(i);
		} else {
			GL20.glEnableVertexAttribArray(i);
		}
	}

	public static void _drawElements(int i, int j, int k, long l) {
		RenderSystem.assertOnRenderThread();
		// Iris: From MixinGlStateManager_DepthColorOverride - tessellation support
		int mode = i;
		if (mode == org.lwjgl.opengl.GL43C.GL_TRIANGLES && net.irisshaders.iris.vertices.ImmediateState.usingTessellation) {
			mode = org.lwjgl.opengl.GL43C.GL_PATCHES;
		}
		
		// Route through Vulkanic abstraction layer
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().drawElements(mode, j, k, l);
		} else {
			org.lwjgl.opengl.GL43C.glDrawElements(mode, j, k, l);
		}
	}

	public static void _drawArrays(int i, int j, int k) {
		RenderSystem.assertOnRenderThread();
		
		// Route through Vulkanic abstraction layer
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().drawArrays(i, j, k);
		} else {
			GL11.glDrawArrays(i, j, k);
		}
	}

	public static void _pixelStore(int i, int j) {
		RenderSystem.assertOnRenderThread();
		
		// Route through Vulkanic abstraction layer
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().setPixelStore(i, j);
		} else {
			GL11.glPixelStorei(i, j);
		}
	}

	public static void _readPixels(int i, int j, int k, int l, int m, int n, long o) {
		RenderSystem.assertOnRenderThread();
		
		// Route through Vulkanic abstraction layer
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().readPixels(i, j, k, l, m, n, o);
		} else {
			GL11.glReadPixels(i, j, k, l, m, n, o);
		}
	}

	public static int _getError() {
		RenderSystem.assertOnRenderThread();
		if (net.vulkanic.Vulkanic.isInitialized()) {
			return net.vulkanic.Vulkanic.getDevice().createCommandBuffer().getError();
		} else {
			return GL11.glGetError();
		}
	}

	public static void clearGlErrors() {
		RenderSystem.assertOnRenderThread();

		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.VulkanicCommandBuffer cmd = net.vulkanic.Vulkanic.getDevice().createCommandBuffer();
			while (cmd.getError() != 0) {
			}
		} else {
			while (GL11.glGetError() != 0) {
			}
		}
	}

	public static String _getString(int i) {
		RenderSystem.assertOnRenderThread();
		if (net.vulkanic.Vulkanic.isInitialized()) {
			return net.vulkanic.Vulkanic.getDevice().createCommandBuffer().getString(i);
		} else {
			return GL11.glGetString(i);
		}
	}

	public static int _getInteger(int i) {
		RenderSystem.assertOnRenderThread();
		if (net.vulkanic.Vulkanic.isInitialized()) {
			return net.vulkanic.Vulkanic.getDevice().createCommandBuffer().getInteger(i);
		} else {
			return GL11.glGetInteger(i);
		}
	}

	public static long _glFenceSync(int i, int j) {
		RenderSystem.assertOnRenderThread();
		if (net.vulkanic.Vulkanic.isInitialized()) {
			return net.vulkanic.Vulkanic.getDevice().createCommandBuffer().fenceSync(i, j);
		} else {
			return GL32.glFenceSync(i, j);
		}
	}

	public static int _glClientWaitSync(long l, int i, long m) {
		RenderSystem.assertOnRenderThread();
		if (net.vulkanic.Vulkanic.isInitialized()) {
			return net.vulkanic.Vulkanic.getDevice().createCommandBuffer().clientWaitSync(l, i, m);
		} else {
			return GL32.glClientWaitSync(l, i, m);
		}
	}

	public static void _glDeleteSync(long l) {
		RenderSystem.assertOnRenderThread();
		if (net.vulkanic.Vulkanic.isInitialized()) {
			net.vulkanic.Vulkanic.getDevice().createCommandBuffer().deleteSync(l);
		} else {
			GL32.glDeleteSync(l);
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
				if (net.vulkanic.Vulkanic.isInitialized()) {
					net.vulkanic.VulkanicCommandBuffer cmd = net.vulkanic.Vulkanic.getDevice().createCommandBuffer();
					if (bl) {
						cmd.glEnable(this.state);
					} else {
						cmd.glDisable(this.state);
					}
				} else {
					if (bl) {
						GL11.glEnable(this.state);
					} else {
						GL11.glDisable(this.state);
					}
				}
				return;
			}
			if (bl != this.enabled) {
				this.enabled = bl;
				if (net.vulkanic.Vulkanic.isInitialized()) {
					net.vulkanic.VulkanicCommandBuffer cmd = net.vulkanic.Vulkanic.getDevice().createCommandBuffer();
					if (bl) {
						cmd.glEnable(this.state);
					} else {
						cmd.glDisable(this.state);
					}
				} else {
					if (bl) {
						GL11.glEnable(this.state);
					} else {
						GL11.glDisable(this.state);
					}
				}
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
