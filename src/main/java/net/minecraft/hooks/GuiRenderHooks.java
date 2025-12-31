package net.minecraft.hooks;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.renderer.RenderBuffers;

/**
 * Hook interface for GUI rendering extensions.
 * Allows mods to inject custom rendering logic at specific points in the rendering pipeline.
 */
public interface GuiRenderHooks {
    /**
     * Called during GameRenderer.render() before the GUI is rendered.
     * This allows mods to render overlays or custom GUI elements.
     * 
     * @param minecraft The Minecraft instance
     * @param guiRenderState The GUI render state
     * @param renderBuffers The render buffers
     * @param deltaTracker The delta tracker for frame timing
     * @param renderTick Whether this is a render tick
     */
    default void onBeforeGuiRender(Minecraft minecraft, GuiRenderState guiRenderState, 
                                   RenderBuffers renderBuffers, DeltaTracker deltaTracker, boolean renderTick) {}
}
