package net.caffeinemc.mods.sodium.fabric;

import net.caffeinemc.mods.sodium.client.render.chunk.NonStoringBuilderPool;
import net.minecraft.client.renderer.SectionBufferBuilderPool;
import net.minecraft.hooks.RenderBuffersHooks;

/**
 * Sodium implementation of RenderBuffersHooks.
 * Provides a custom NonStoringBuilderPool instead of the default SectionBufferBuilderPool.
 */
public class SodiumRenderBuffersHook implements RenderBuffersHooks {
    @Override
    public SectionBufferBuilderPool provideSectionBufferPool(int size) {
        // Return Sodium's optimized non-storing builder pool
        return new NonStoringBuilderPool();
    }
}
