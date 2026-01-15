package net.caffeinemc.mods.sodium.client.hooks;

import net.blaze3d.vertex.VertexFormat;
import net.minecraft.hooks.VertexFormatHooks;
import net.sodium.api.vertex.format.VertexFormatRegistry;

/**
 * Sodium's implementation of VertexFormatHooks.
 * Allocates global IDs for vertex formats to enable fast lookups.
 */
public class SodiumVertexFormatHook implements VertexFormatHooks {
    private static final SodiumVertexFormatHook INSTANCE = new SodiumVertexFormatHook();

    private SodiumVertexFormatHook() {
    }

    public static SodiumVertexFormatHook getInstance() {
        return INSTANCE;
    }

    @Override
    public void onVertexFormatInit(VertexFormat format) {
        // Allocate a global ID for this vertex format
        VertexFormatRegistry.instance().allocateGlobalId(format);
    }
}
