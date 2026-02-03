package net.sodium.fabric;

import net.sodium.client.render.chunk.NonStoringBuilderPool;
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
        // Note: size parameter is ignored as NonStoringBuilderPool doesn't allocate buffers upfront
        return new NonStoringBuilderPool();
    }
}
