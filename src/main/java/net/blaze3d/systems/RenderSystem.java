package net.blaze3d.systems;

import net.minecraft.util.profiling.TracyCompat;
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

	public static void assertOnRenderThread() {
		net.vulkanic.VulkanicAPI.assertOnRenderThread();
	}

	public static void flipFrame(Window window) {
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

		Tesselator.getInstance().clear();
		TracyCompat.markFrame();
		Minecraft.getInstance().levelRenderer.endFrame();
		if (!net.vulkanic.VulkanicAPI.usesRustVulkanPresenter()) {
			// Rust renders into the borrowed context's default framebuffer;
			// the GLFW window owns the swap.
			GLFW.glfwSwapBuffers(window.handle());
		}
		net.vulkanic.VulkanicAPI.pollEvents();
		return;

	}

	public static void initRenderer(long l, int i, boolean bl, BiFunction<ResourceLocation, ShaderType, String> biFunction, boolean bl2) {
		net.vulkanic.VulkanicAPI.prepareRendererBootstrapWindowHandle(l);
		net.vulkanic.VulkanicAPI.setDevice(net.vulkanic.VulkanicAPI.createRendererDevice());
		net.vulkanic.VulkanicAPI.onRendererDeviceInitialized(l);
		wglPrevContext = MemoryUtil.NULL;
	}

	public static GpuDevice getDevice() {
		return net.vulkanic.VulkanicAPI.getDevice();
	}

}
