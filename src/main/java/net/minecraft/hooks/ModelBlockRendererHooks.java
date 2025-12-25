package net.minecraft.hooks;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Hook interface for ModelBlockRenderer quad rendering events.
 * Allows mods to track quad/sprite usage during block rendering.
 */
public interface ModelBlockRendererHooks {
    /**
     * Called before a quad is rendered during block rendering.
     * 
     * @param blockAndTintGetter The block and tint getter
     * @param blockState The block state being rendered
     * @param blockPos The position of the block
     * @param vertexConsumer The vertex consumer
     * @param pose The pose
     * @param quad The quad being rendered
     * @param brightness The brightness value
     */
    default void onPutQuadData(BlockAndTintGetter blockAndTintGetter, BlockState blockState, BlockPos blockPos, VertexConsumer vertexConsumer, PoseStack.Pose pose, BakedQuad quad, int brightness) {}
}
