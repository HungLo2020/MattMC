package net.minecraft.hooks;

import com.mojang.blaze3d.platform.Window;

/**
 * Hooks for OpenGL context initialization and monitoring.
 * Allows mods to perform checks after context creation and monitor context changes.
 */
public interface RenderContextHooks {
    /**
     * Called after the OpenGL renderer is initialized.
     *
     * @param windowHandle The GLFW window handle
     */
    default void onRendererInitialized(long windowHandle) {}

    /**
     * Called before swapping buffers to check for context changes.
     *
     * @param window The Minecraft window
     */
    default void beforeSwapBuffers(Window window) {}
}
