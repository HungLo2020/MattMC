package net.blaze3d.systems;

import net.blaze3d.TracyFrameCapture;
import net.blaze3d.buffers.Std140SizeCalculator;
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
	private static boolean vulkanFrameAcquired;

	public static void cleanupRendererBootstrapResources() {
		net.vulkanic.VulkanicAPI.cleanupRendererBootstrapResources();
		wglPrevContext = MemoryUtil.NULL;
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
		if (vulkanBackendSelected && !vulkanFrameAcquired) {
			vulkanFrameAcquired = net.vulkanic.VulkanicAPI.beginFrame() >= 0;
		}
		
		Tesselator.getInstance().clear();
		if (vulkanBackendSelected) {
			try {
				if (vulkanFrameAcquired) {
					net.vulkanic.VulkanicAPI.endFrame();
				}
			} finally {
				vulkanFrameAcquired = false;
			}
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

	public static boolean beginVulkanFrameForRenderWork() {
		if (!net.vulkanic.VulkanicAPI.isVulkanBackendSelected()) {
			return true;
		}
		if (!vulkanFrameAcquired) {
			vulkanFrameAcquired = net.vulkanic.VulkanicAPI.beginFrame() >= 0;
		}
		return vulkanFrameAcquired;
	}

	public static void initRenderer(long l, int i, boolean bl, BiFunction<ResourceLocation, ShaderType, String> biFunction, boolean bl2) {
		net.vulkanic.VulkanicAPI.initializeNativeVulkanRuntimeOnRendererStartupIfSelected();
		long rendererBootstrapWindowHandle = net.vulkanic.VulkanicAPI.prepareRendererBootstrapWindowHandle(l);
		net.vulkanic.VulkanicAPI.setDevice(
			net.vulkanic.VulkanicAPI.createRendererDevice(rendererBootstrapWindowHandle, i, bl, biFunction, bl2)
		);
		net.vulkanic.VulkanicAPI.initializeDynamicUniforms();
		net.vulkanic.VulkanicAPI.onRendererDeviceInitialized(l, net.vulkanic.VulkanicAPI.getDevice());

		// Capture the current WGL context so that we can detect it being replaced later.
		if (!net.vulkanic.VulkanicAPI.isVulkanBackendSelected() && Util.getPlatform() == Util.OS.WINDOWS) {
			wglPrevContext = net.vulkanic.VulkanicAPI.getGraphicsContext();
		} else {
			wglPrevContext = MemoryUtil.NULL;
		}
	}

	public static GpuDevice getDevice() {
		return net.vulkanic.VulkanicAPI.getDevice();
	}

}
