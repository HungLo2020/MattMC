package net.fabricmc.fabric.api.client.rendering.v1.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Interface for rendering HUD elements.
 * Stub for VoxelMap compatibility.
 */
public interface HudElement {
    /**
     * Renders the HUD element.
     */
    void render(GuiGraphics context, DeltaTracker tickCounter);
}
