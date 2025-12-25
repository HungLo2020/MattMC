package net.caffeinemc.mods.sodium.fabric;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.Util;
import net.minecraft.hooks.RenderContextHooks;
import net.sodium.client.compatibility.checks.ModuleScanner;
import net.sodium.client.compatibility.checks.PostLaunchChecks;
import net.sodium.client.compatibility.environment.GlContextInfo;
import net.sodium.client.platform.NativeWindowHandle;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.opengl.WGL;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sodium implementation of render context hooks.
 * Replaces mixin: workarounds.context_creation.RenderSystemMixin
 */
public class SodiumRenderContextHook implements RenderContextHooks {
    private static final Logger LOGGER = LoggerFactory.getLogger("Sodium-RenderContext");
    private static long wglPrevContext = MemoryUtil.NULL;

    @Override
    public void onRendererInitialized(long windowHandle) {
        GlContextInfo context = GlContextInfo.create();
        LOGGER.info("OpenGL Vendor: {}", context.vendor());
        LOGGER.info("OpenGL Renderer: {}", context.renderer());
        LOGGER.info("OpenGL Version: {}", context.version());

        // Capture the current WGL context so that we can detect it being replaced later.
        if (Util.getPlatform() == Util.OS.WINDOWS) {
            wglPrevContext = WGL.wglGetCurrentContext();
        } else {
            wglPrevContext = MemoryUtil.NULL;
        }

        NativeWindowHandle handle = () -> GLFWNativeWin32.glfwGetWin32Window(windowHandle);

        PostLaunchChecks.onContextInitialized(handle, context);
        ModuleScanner.checkModules(handle);
    }

    @Override
    public void beforeSwapBuffers(Window window) {
        if (wglPrevContext == MemoryUtil.NULL) {
            // There is no prior recorded context.
            return;
        }

        var context = WGL.wglGetCurrentContext();

        if (wglPrevContext == context) {
            // The context has not changed.
            return;
        }

        // Something has decided to replace the OpenGL context, which is not a good sign
        LOGGER.warn("The OpenGL context appears to have been suddenly replaced! Something has likely just injected into the game process.");

        // Likely, this indicates a module was injected into the current process. We should check that
        // nothing problematic was just installed.
        ModuleScanner.checkModules(() -> GLFWNativeWin32.glfwGetWin32Window(window.handle()));

        // If we didn't find anything problematic (which would have thrown an exception), then let's just record
        // the new context pointer and carry on.
        wglPrevContext = context;
    }
}
