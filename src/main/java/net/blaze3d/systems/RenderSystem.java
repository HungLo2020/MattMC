package net.blaze3d.systems;

import net.blaze3d.TracyFrameCapture;
import net.blaze3d.buffers.Std140SizeCalculator;
import net.blaze3d.opengl.GlDevice;
import net.blaze3d.platform.Window;
import net.blaze3d.shaders.ShaderType;
import net.blaze3d.vertex.Tesselator;
import net.logging.LogUtils;
import java.util.function.BiFunction;
import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.hooks.HookRegistry;
import net.minecraft.hooks.RenderHooks;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class RenderSystem {
	static final Logger LOGGER = LogUtils.getLogger();
	public static final int MINIMUM_ATLAS_TEXTURE_SIZE = 1024;
	public static final int PROJECTION_MATRIX_UBO_SIZE = new Std140SizeCalculator().putMat4f().get();
	// Sodium: Track WGL context for security checks (from RenderSystemMixin)
	private static long wglPrevContext = MemoryUtil.NULL;
	private static long auxiliaryOpenGlContextWindow = MemoryUtil.NULL;

	private static long resolveGlDeviceWindowHandleForRendererInitialization(long mainWindowHandle) {
		if (!net.vulkanic.VulkanicAPI.isVulkanBackendInitializedAndSelected()) {
			return mainWindowHandle;
		}

		if (auxiliaryOpenGlContextWindow != MemoryUtil.NULL) {
			return auxiliaryOpenGlContextWindow;
		}

		GLFW.glfwDefaultWindowHints();
		GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
		GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_OPENGL_API);
		GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
		GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
		GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
		GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);

		auxiliaryOpenGlContextWindow = GLFW.glfwCreateWindow(1, 1, "MattMC Aux OpenGL Context", 0L, 0L);
		if (auxiliaryOpenGlContextWindow == MemoryUtil.NULL) {
			throw new IllegalStateException("Failed to create auxiliary OpenGL context window for Vulkan startup compatibility path");
		}

		LOGGER.info("Created auxiliary hidden OpenGL context window for Vulkan startup compatibility path");
		return auxiliaryOpenGlContextWindow;
	}

	public static void cleanupAuxiliaryOpenGlContextWindow() {
		if (auxiliaryOpenGlContextWindow == MemoryUtil.NULL) {
			return;
		}

		try {
			if (GLFW.glfwGetCurrentContext() == auxiliaryOpenGlContextWindow) {
				GLFW.glfwMakeContextCurrent(0L);
			}
			GLFW.glfwDestroyWindow(auxiliaryOpenGlContextWindow);
			LOGGER.info("Destroyed auxiliary hidden OpenGL context window for Vulkan startup compatibility path");
		} catch (Throwable throwable) {
			LOGGER.warn("Failed to destroy auxiliary hidden OpenGL context window cleanly", throwable);
		} finally {
			auxiliaryOpenGlContextWindow = MemoryUtil.NULL;
			wglPrevContext = MemoryUtil.NULL;
		}
	}

	public static void assertOnRenderThread() {
		net.vulkanic.VulkanicAPI.assertOnRenderThread();
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
			net.vulkanic.VulkanicAPI.pollEvents();
		}

		boolean vulkanBackendSelected = net.vulkanic.VulkanicAPI.isVulkanBackendSelected();
		if (vulkanBackendSelected) {
			net.vulkanic.VulkanicAPI.beginFrame();
		}
		
		Tesselator.getInstance().clear();
		if (vulkanBackendSelected) {
			net.vulkanic.VulkanicAPI.endFrame();
		} else {
			GLFW.glfwSwapBuffers(window.handle());
		}
		if (tracyFrameCapture != null) {
			tracyFrameCapture.endFrame();
		}

		net.vulkanic.VulkanicAPI.resetDynamicUniforms();
		Minecraft.getInstance().levelRenderer.endFrame();
		net.vulkanic.VulkanicAPI.pollEvents();
		
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

	public static void initRenderer(long l, int i, boolean bl, BiFunction<ResourceLocation, ShaderType, String> biFunction, boolean bl2) {
		net.vulkanic.VulkanicAPI.initializeNativeVulkanRuntimeOnRendererStartupIfSelected();
		long glDeviceWindowHandle = resolveGlDeviceWindowHandleForRendererInitialization(l);
		net.vulkanic.VulkanicAPI.setDevice(new GlDevice(glDeviceWindowHandle, i, bl, biFunction, bl2));
		net.vulkanic.VulkanicAPI.initializeDynamicUniforms();
		
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

	public static GpuDevice getDevice() {
		return net.vulkanic.VulkanicAPI.getDevice();
	}

}
