package net.sodium.fabric;

import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.hooks.ModelBlockRendererHooks;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.sodium.api.texture.SpriteUtil;

/**
 * Sodium implementation of ModelBlockRendererHooks.
 * Marks sprites as active when quads are rendered during block rendering.
 */
public class SodiumModelBlockRendererHook implements ModelBlockRendererHooks {
    @Override
    public void onPutQuadData(BlockAndTintGetter blockAndTintGetter, BlockState blockState, BlockPos blockPos, VertexConsumer vertexConsumer, PoseStack.Pose pose, BakedQuad quad, int brightness) {
        // Mark sprite as active when quad is rendered
        // This ensures sprites rendered through renderSmooth/renderFlat in immediate-mode are marked as active
        if (quad.sprite() != null) {
            SpriteUtil.INSTANCE.markSpriteActive(quad.sprite());
        }
    }
}
