package net.blaze3d.systems;

import net.blaze3d.TracyFrameCapture;
import net.blaze3d.buffers.GpuBuffer;
import net.blaze3d.buffers.GpuBufferSlice;
import net.blaze3d.buffers.Std140SizeCalculator;
import net.blaze3d.opengl.GlDevice;
import net.blaze3d.platform.GLX;
import net.blaze3d.platform.Window;
import net.blaze3d.shaders.ShaderType;
import net.blaze3d.textures.GpuTextureView;
import net.blaze3d.vertex.Tesselator;
import net.blaze3d.vertex.VertexFormat;
import net.logging.LogUtils;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.IntConsumer;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicUniforms;
import net.minecraft.hooks.HookRegistry;
import net.minecraft.hooks.RenderHooks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.TimeSource.NanoTimeSource;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallbackI;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class RenderSystem {
	static final Logger LOGGER = LogUtils.getLogger();
	public static final int MINIMUM_ATLAS_TEXTURE_SIZE = 1024;
	public static final int PROJECTION_MATRIX_UBO_SIZE = new Std140SizeCalculator().putMat4f().get();
	// Sodium: Track WGL context for security checks (from RenderSystemMixin)
	private static long wglPrevContext = MemoryUtil.NULL;
	@Nullable
	private static Thread renderThread;
	@Nullable
	private static GpuDevice DEVICE;
	private static double lastDrawTime = Double.MIN_VALUE;
	private static final RenderSystem.AutoStorageIndexBuffer sharedSequential = new RenderSystem.AutoStorageIndexBuffer(1, 1, IntConsumer::accept);
	private static final RenderSystem.AutoStorageIndexBuffer sharedSequentialQuad = new RenderSystem.AutoStorageIndexBuffer(4, 6, (intConsumer, i) -> {
		intConsumer.accept(i);
		intConsumer.accept(i + 1);
		intConsumer.accept(i + 2);
		intConsumer.accept(i + 2);
		intConsumer.accept(i + 3);
		intConsumer.accept(i);
	});
	private static final RenderSystem.AutoStorageIndexBuffer sharedSequentialLines = new RenderSystem.AutoStorageIndexBuffer(4, 6, (intConsumer, i) -> {
		intConsumer.accept(i);
		intConsumer.accept(i + 1);
		intConsumer.accept(i + 2);
		intConsumer.accept(i + 3);
		intConsumer.accept(i + 2);
		intConsumer.accept(i + 1);
	});
	private static String apiDescription = "Unknown";
	private static final AtomicLong pollEventsWaitStart = new AtomicLong();
	private static final AtomicBoolean pollingEvents = new AtomicBoolean(false);
	@Nullable
	public static GpuTextureView outputColorTextureOverride;
	@Nullable
	public static GpuTextureView outputDepthTextureOverride;
	@Nullable
	private static DynamicUniforms dynamicUniforms;
	private static ScissorState scissorStateForRenderTypeDraws = new ScissorState();

	public static void initRenderThread() {
		if (renderThread != null) {
			throw new IllegalStateException("Could not initialize render thread");
		} else {
			renderThread = Thread.currentThread();
		}
	}

	public static boolean isOnRenderThread() {
		return Thread.currentThread() == renderThread;
	}

	public static boolean isInInit() {
		// During initialization, we're on the main thread before the render thread is set
		return renderThread == null || Thread.currentThread() == renderThread;
	}

	public static void assertOnRenderThread() {
		if (!isOnRenderThread()) {
			throw constructThreadException();
		}
	}

	public static void assertOnRenderThreadOrInit() {
		if (!isOnRenderThread() && !isInInit()) {
			throw constructThreadException();
		}
	}

	private static IllegalStateException constructThreadException() {
		return new IllegalStateException("Rendersystem called from wrong thread");
	}

	private static void pollEvents() {
		pollEventsWaitStart.set(Util.getMillis());
		pollingEvents.set(true);
		GLFW.glfwPollEvents();
		pollingEvents.set(false);
	}

	public static boolean isFrozenAtPollEvents() {
		return pollingEvents.get() && Util.getMillis() - pollEventsWaitStart.get() > 200L;
	}

	public static void flipFrame(Window window, @Nullable TracyFrameCapture tracyFrameCapture) {
		// HOOK: Check if mods want to skip the first pollEvents call
		boolean skipFirstPoll = false;
		for (RenderHooks hook : HookRegistry.getRenderHooks()) {
			if (hook.shouldSkipFirstPollEvents()) {
				skipFirstPoll = true;
				break;
			}
		}
		
		if (!skipFirstPoll) {
			pollEvents();
		}
		
		Tesselator.getInstance().clear();
		GLFW.glfwSwapBuffers(window.handle());
		if (tracyFrameCapture != null) {
			tracyFrameCapture.endFrame();
		}

		dynamicUniforms.reset();
		Minecraft.getInstance().levelRenderer.endFrame();
		pollEvents();
		
		// Sodium: Check for context replacement (from RenderSystemMixin)
		if (wglPrevContext != MemoryUtil.NULL) {
			var context = net.vulkanic.VulkanicAPI.getGraphicsContext();

			if (wglPrevContext != context) {
				// Something has decided to replace the OpenGL context, which is not a good sign
				LOGGER.warn("The OpenGL context appears to have been suddenly replaced! Something has likely just injected into the game process.");

				// Likely, this indicates a module was injected into the current process. We should check that
				// nothing problematic was just installed.
				net.sodium.client.compatibility.checks.ModuleScanner.checkModules(() -> org.lwjgl.glfw.GLFWNativeWin32.glfwGetWin32Window(window.handle()));

				// If we didn't find anything problematic (which would have thrown an exception), then let's just record
				// the new context pointer and carry on.
				wglPrevContext = context;
			}
		}
	}

	public static void limitDisplayFPS(int i) {
		double d = lastDrawTime + 1.0 / i;

		double e;
		for (e = GLFW.glfwGetTime(); e < d; e = GLFW.glfwGetTime()) {
			GLFW.glfwWaitEventsTimeout(d - e);
		}

		lastDrawTime = e;
	}

	public static void enableScissorForRenderTypeDraws(int i, int j, int k, int l) {
		scissorStateForRenderTypeDraws.enable(i, j, k, l);
	}

	public static void disableScissorForRenderTypeDraws() {
		scissorStateForRenderTypeDraws.disable();
	}

	public static ScissorState getScissorStateForRenderTypeDraws() {
		return scissorStateForRenderTypeDraws;
	}

	public static String getBackendDescription() {
		return String.format(Locale.ROOT, "LWJGL version %s", GLX._getLWJGLVersion());
	}

	public static String getApiDescription() {
		return apiDescription;
	}

	public static NanoTimeSource initBackendSystem() {
		return GLX._initGlfw()::getAsLong;
	}

	public static void initRenderer(long l, int i, boolean bl, BiFunction<ResourceLocation, ShaderType, String> biFunction, boolean bl2) {
		DEVICE = new GlDevice(l, i, bl, biFunction, bl2);
		apiDescription = getDevice().getImplementationInformation();
		dynamicUniforms = new DynamicUniforms();
		
		// Sodium: Post-context initialization (from RenderSystemMixin)
		net.sodium.client.compatibility.environment.GlContextInfo context = net.sodium.client.compatibility.environment.GlContextInfo.create();
		LOGGER.info("OpenGL Vendor: {}", context.vendor());
		LOGGER.info("OpenGL Renderer: {}", context.renderer());
		LOGGER.info("OpenGL Version: {}", context.version());

		// Capture the current WGL context so that we can detect it being replaced later.
		if (Util.getPlatform() == Util.OS.WINDOWS) {
			wglPrevContext = net.vulkanic.VulkanicAPI.getGraphicsContext();
		} else {
			wglPrevContext = MemoryUtil.NULL;
		}

		net.sodium.client.platform.NativeWindowHandle handle = () -> org.lwjgl.glfw.GLFWNativeWin32.glfwGetWin32Window(l);

		net.sodium.client.compatibility.checks.PostLaunchChecks.onContextInitialized(handle, context);
		net.sodium.client.compatibility.checks.ModuleScanner.checkModules(handle);
		
		// Iris: Post-renderer initialization (from MixinRenderSystem)
		net.irisshaders.iris.Iris.duringRenderSystemInit();
		net.irisshaders.iris.gl.GLDebug.reloadDebugState();
		net.irisshaders.iris.gl.IrisRenderSystem.initRenderer();
		net.irisshaders.iris.samplers.IrisSamplers.initRenderer();
		net.irisshaders.iris.Iris.onRenderSystemInit();
	}

	public static void setErrorCallback(GLFWErrorCallbackI gLFWErrorCallbackI) {
		GLX._setGlfwErrorCallback(gLFWErrorCallbackI);
	}

	public static void setupDefaultState() {
		net.vulkanic.VulkanicAPI.getModelViewStack().clear();
		net.vulkanic.VulkanicAPI.resetTextureMatrix();
	}

	public static RenderSystem.AutoStorageIndexBuffer getSequentialBuffer(VertexFormat.Mode mode) {
		assertOnRenderThread();

		return switch (mode) {
			case QUADS -> sharedSequentialQuad;
			case LINES -> sharedSequentialLines;
			default -> sharedSequential;
		};
	}

	public static GpuDevice getDevice() {
		if (DEVICE == null) {
			throw new IllegalStateException("Can't getDevice() before it was initialized");
		} else {
			return DEVICE;
		}
	}

	@Nullable
	public static GpuDevice tryGetDevice() {
		return DEVICE;
	}

	public static DynamicUniforms getDynamicUniforms() {
		if (dynamicUniforms == null) {
			throw new IllegalStateException("Can't getDynamicUniforms() before device was initialized");
		} else {
			return dynamicUniforms;
		}
	}

	@Environment(EnvType.CLIENT)
	public static final class AutoStorageIndexBuffer {
		private final int vertexStride;
		private final int indexStride;
		private final RenderSystem.AutoStorageIndexBuffer.IndexGenerator generator;
		@Nullable
		private GpuBuffer buffer;
		private VertexFormat.IndexType type = VertexFormat.IndexType.SHORT;
		private int indexCount;

		AutoStorageIndexBuffer(int i, int j, RenderSystem.AutoStorageIndexBuffer.IndexGenerator indexGenerator) {
			this.vertexStride = i;
			this.indexStride = j;
			this.generator = indexGenerator;
		}

		public boolean hasStorage(int i) {
			return i <= this.indexCount;
		}

		public GpuBuffer getBuffer(int i) {
			this.ensureStorage(i);
			return this.buffer;
		}

		private void ensureStorage(int i) {
			if (!this.hasStorage(i)) {
				i = Mth.roundToward(i * 2, this.indexStride);
				RenderSystem.LOGGER.debug("Growing IndexBuffer: Old limit {}, new limit {}.", this.indexCount, i);
				int j = i / this.indexStride;
				int k = j * this.vertexStride;
				VertexFormat.IndexType indexType = VertexFormat.IndexType.least(k);
				int l = Mth.roundToward(i * indexType.bytes, 4);
				ByteBuffer byteBuffer = MemoryUtil.memAlloc(l);

				try {
					this.type = indexType;
					it.unimi.dsi.fastutil.ints.IntConsumer intConsumer = this.intConsumer(byteBuffer);

					for (int m = 0; m < i; m += this.indexStride) {
						this.generator.accept(intConsumer, m * this.vertexStride / this.indexStride);
					}

					byteBuffer.flip();
					if (this.buffer != null) {
						this.buffer.close();
					}

					this.buffer = RenderSystem.getDevice().createBuffer(() -> "Auto Storage index buffer", 64, byteBuffer);
				} finally {
					MemoryUtil.memFree(byteBuffer);
				}

				this.indexCount = i;
			}
		}

		private it.unimi.dsi.fastutil.ints.IntConsumer intConsumer(ByteBuffer byteBuffer) {
			switch (this.type) {
				case SHORT:
					return i -> byteBuffer.putShort((short)i);
				case INT:
				default:
					return byteBuffer::putInt;
			}
		}

		public VertexFormat.IndexType type() {
			return this.type;
		}

		@Environment(EnvType.CLIENT)
		interface IndexGenerator {
			void accept(it.unimi.dsi.fastutil.ints.IntConsumer intConsumer, int i);
		}
	}

}
