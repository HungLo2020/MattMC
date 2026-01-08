package net.fabricmc.fabric.api.renderer.v1.render;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import org.jetbrains.annotations.Nullable;

/**
 * Utility for render layer handling.
 */
public final class RenderLayerHelper {
    
    private RenderLayerHelper() { }
    
    /**
     * Creates a BlockVertexConsumerProvider that delegates to a MultiBufferSource.
     */
    public static BlockVertexConsumerProvider entityDelegate(MultiBufferSource multiBufferSource) {
        return layer -> multiBufferSource.getBuffer(getEntityBlockLayer(layer));
    }
    
    /**
     * Gets the entity RenderType for a given blend mode / chunk section layer.
     */
    public static RenderType getEntityBlockLayer(@Nullable ChunkSectionLayer layer) {
        if (layer == null) {
            return RenderType.solid();
        }
        return switch (layer) {
            case SOLID -> RenderType.solid();
            case CUTOUT -> RenderType.cutout();
            case CUTOUT_MIPPED -> RenderType.cutoutMipped();
            case TRANSLUCENT, TRIPWIRE -> RenderType.translucentMovingBlock();
        };
    }
}
