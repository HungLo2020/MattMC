package net.minecraft.hooks;

import net.minecraft.client.renderer.SectionBufferBuilderPool;
import org.jetbrains.annotations.Nullable;

/**
 * Hook interface for customizing RenderBuffers initialization.
 * Allows mods to provide custom section buffer builder pool implementations.
 */
public interface RenderBuffersHooks {
    /**
     * Provide a custom SectionBufferBuilderPool implementation.
     * If this returns a non-null value, it replaces the default SectionBufferBuilderPool.allocate() call.
     *
     * @param size The requested pool size
     * @return Custom section buffer pool, or null to use default behavior
     */
    @Nullable
    default SectionBufferBuilderPool provideSectionBufferPool(int size) {
        return null;
    }
}
