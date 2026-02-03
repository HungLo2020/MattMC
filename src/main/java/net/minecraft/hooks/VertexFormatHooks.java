package net.minecraft.hooks;

import net.blaze3d.vertex.VertexFormat;

/**
 * Hook interface for vertex format lifecycle events.
 * Allows mods to track vertex format creation and register custom formats.
 */
public interface VertexFormatHooks {
    /**
     * Called when a vertex format is initialized.
     * 
     * @param format The vertex format being initialized
     */
    default void onVertexFormatInit(VertexFormat format) {
    }
}
