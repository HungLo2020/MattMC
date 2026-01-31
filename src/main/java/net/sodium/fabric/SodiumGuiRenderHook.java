package net.sodium.fabric;

import net.sodium.client.gui.console.ConsoleHooks;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.hooks.GuiRenderHooks;
import net.minecraft.util.profiling.Profiler;
import org.lwjgl.glfw.GLFW;

/**
 * Implements GUI rendering hooks for Sodium's console overlay.
 */
public class SodiumGuiRenderHook implements GuiRenderHooks {
    private static boolean hasRenderedOverlayOnce = false;

    @Override
    public void onBeforeGuiRender(Minecraft minecraft, GuiRenderState guiRenderState,
                                  RenderBuffers renderBuffers, DeltaTracker deltaTracker, boolean renderTick) {
        // Do not start updating the console overlay until the font renderer is ready
        // This prevents the console from using tofu boxes for everything during early startup
        if (Minecraft.getInstance().getOverlay() != null) {
            if (!hasRenderedOverlayOnce) {
                return;
            }
        }

        Profiler.get().push("sodium_console_overlay");

        GuiGraphics drawContext = new GuiGraphics(minecraft, guiRenderState);

        ConsoleHooks.render(drawContext, GLFW.glfwGetTime());

        Profiler.get().pop();

        hasRenderedOverlayOnce = true;
    }
}
