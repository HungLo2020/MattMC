package net.fabricmc.fabric.api.client.rendering.v1.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Registry for HUD elements.
 * Stub for VoxelMap compatibility.
 */
public final class HudElementRegistry {
    private static final List<HudElementEntry> ELEMENTS = new ArrayList<>();

    private HudElementRegistry() {}

    /**
     * Attaches a HUD element after another element.
     */
    public static void attachElementAfter(ResourceLocation after, ResourceLocation id, HudElement element) {
        ELEMENTS.add(new HudElementEntry(id, element));
    }

    /**
     * Renders all registered HUD elements.
     * Called from Minecraft's Gui.render() method.
     */
    public static void renderAll(GuiGraphics context, DeltaTracker tickCounter) {
        for (HudElementEntry entry : ELEMENTS) {
            entry.element.render(context, tickCounter);
        }
    }

    /**
     * Lets an explicit backend reject a registered hook whose producer has not
     * crossed the semantic rendering boundary yet.
     */
    public static boolean isRegistered(ResourceLocation id) {
        for (HudElementEntry entry : ELEMENTS) {
            if (entry.id.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static class HudElementEntry {
        final ResourceLocation id;
        final HudElement element;

        HudElementEntry(ResourceLocation id, HudElement element) {
            this.id = id;
            this.element = element;
        }
    }
}
