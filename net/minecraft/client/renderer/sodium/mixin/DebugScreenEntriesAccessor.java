package net.minecraft.client.renderer.sodium.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.gui.components.DebugScreenOverlay;

import java.util.Map;
import java.util.HashMap;

/**
 * Temporary accessor stub for DebugScreenOverlay entries.
 * This will be replaced when mixins are inlined in Phase 4.
 * 
 * @see net.minecraft.client.renderer.advanced.AdvancedRenderingConfig
 */
public class DebugScreenEntriesAccessor {
    private static final Map<ResourceLocation, DebugScreenOverlay.Entry> entries = new HashMap<>();
    
    public static Map<ResourceLocation, DebugScreenOverlay.Entry> getEntries() {
        return entries;
    }
}
