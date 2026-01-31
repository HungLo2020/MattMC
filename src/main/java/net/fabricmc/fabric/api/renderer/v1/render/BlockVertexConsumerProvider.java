package net.fabricmc.fabric.api.renderer.v1.render;

import net.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import org.jetbrains.annotations.Nullable;

/**
 * Interface for providing vertex consumers for block rendering.
 */
@FunctionalInterface
public interface BlockVertexConsumerProvider {
    
    /**
     * Gets a vertex consumer for the given render layer.
     */
    VertexConsumer getBuffer(@Nullable ChunkSectionLayer layer);
    
    /**
     * Gets the underlying MultiBufferSource if available.
     */
    @Nullable
    default MultiBufferSource getMultiBufferSource() {
        return null;
    }
}
