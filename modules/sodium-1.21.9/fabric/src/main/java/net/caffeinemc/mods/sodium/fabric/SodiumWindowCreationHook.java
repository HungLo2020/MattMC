package net.caffeinemc.mods.sodium.fabric;

import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.minecraft.hooks.WindowCreationHooks;
import net.sodium.client.compatibility.workarounds.Workarounds;
import org.lwjgl.glfw.GLFW;

/**
 * Sodium implementation of window creation hooks.
 * Replaces mixin: core.WindowMixin
 */
public class SodiumWindowCreationHook implements WindowCreationHooks {
    @Override
    public void beforeWindowCreation() {
        if (SodiumClientMod.options().performance.useNoErrorGLContext) {
            if (!Workarounds.isWorkaroundEnabled(Workarounds.Reference.NO_ERROR_CONTEXT_UNSUPPORTED)) {
                GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_NO_ERROR, GLFW.GLFW_TRUE);
            }
        }
    }
}
