package net.sodium.client.platform;

import net.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFWNativeWin32;

public interface NativeWindowHandle {
    long getWin32Handle();
    
    /**
     * Static helper to get Win32 handle from Window.
     * Uses reflection to access the handle field.
     */
    static long getWin32Handle(Window window) {
        try {
            java.lang.reflect.Field handleField = Window.class.getDeclaredField("handle");
            handleField.setAccessible(true);
            long handle = handleField.getLong(window);
            return GLFWNativeWin32.glfwGetWin32Window(handle);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get Win32 handle", e);
        }
    }
}
