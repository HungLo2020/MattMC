package net.caffeinemc.mods.sodium.client.hooks;

import net.blaze3d.platform.Window;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.minecraft.hooks.WindowHooks;
import net.sodium.client.compatibility.workarounds.Workarounds;
import net.caffeinemc.mods.sodium.client.services.PlatformRuntimeInformation;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeWin32;

import java.lang.reflect.Field;

/**
 * Sodium's hook for Window operations.
 * Provides window hint configuration and native handle access.
 */
public class SodiumWindowHook implements WindowHooks {
    private static final SodiumWindowHook INSTANCE = new SodiumWindowHook();
    
    private static Field handleField;
    
    static {
        try {
            handleField = Window.class.getDeclaredField("handle");
            handleField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Failed to initialize SodiumWindowHook", e);
        }
    }

    private SodiumWindowHook() {
    }

    public static SodiumWindowHook getInstance() {
        return INSTANCE;
    }

    @Override
    public void onBeforeWindowCreate() {
        if (!PlatformRuntimeInformation.getInstance().platformHasEarlyLoadingScreen()) {
            if (SodiumClientMod.options().performance.useNoErrorGLContext) {
                if (!Workarounds.isWorkaroundEnabled(Workarounds.Reference.NO_ERROR_CONTEXT_UNSUPPORTED)) {
                    GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_NO_ERROR, GLFW.GLFW_TRUE);
                }
            }
        }
    }

    /**
     * Get Win32 window handle.
     */
    public static long getWin32Handle(Window window) {
        try {
            long handle = handleField.getLong(window);
            return GLFWNativeWin32.glfwGetWin32Window(handle);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to get Win32 handle", e);
        }
    }
}
