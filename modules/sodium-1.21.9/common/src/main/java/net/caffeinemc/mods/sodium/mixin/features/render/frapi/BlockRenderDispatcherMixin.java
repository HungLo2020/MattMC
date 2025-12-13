package net.caffeinemc.mods.sodium.mixin.features.render.frapi;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.caffeinemc.mods.sodium.client.render.frapi.render.NonTerrainBlockRenderContext;
import net.caffeinemc.mods.sodium.client.render.frapi.render.SimpleBlockRenderContext;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBlockStateModel;
import net.fabricmc.fabric.api.renderer.v1.render.FabricBlockModelRenderer;
import net.fabricmc.fabric.api.renderer.v1.render.RenderLayerHelper;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.EmptyBlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockRenderDispatcher.class)
public class BlockRenderDispatcherMixin {
    @Shadow
    @Final
    private BlockColors blockColors;

    @Shadow
    @Final
    private ModelBlockRenderer modelRenderer;

    @Shadow
    @Final
    private BlockModelShaper blockModelShaper;

    @Inject(method = "renderBreakingTexture", at = @At("HEAD"), cancellable = true)
    private void afterGetModel(BlockState blockState, BlockPos blockPos, BlockAndTintGetter world, PoseStack matrixStack, VertexConsumer vertexConsumer, CallbackInfo ci) {
        if (blockState.getRenderShape() == net.minecraft.world.level.block.RenderShape.MODEL) {
            BlockStateModel model = this.blockModelShaper.getBlockModel(blockState);
            ((FabricBlockModelRenderer) modelRenderer).render(world, model, blockState, blockPos, matrixStack, layer -> vertexConsumer, true, blockState.getSeed(blockPos), OverlayTexture.NO_OVERLAY);
            ci.cancel();
        }
    }

    @Group(name = "sodium$proxy", min = 1, max = 1)
    @Redirect(method = "renderSingleBlock", require = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;renderModel(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/renderer/block/model/BlockStateModel;FFFII)V"))
    private void renderProxy(PoseStack.Pose entry, VertexConsumer vertexConsumer, BlockStateModel model, float red, float green, float blue, int light, int overlay, BlockState state, PoseStack matrices, MultiBufferSource vertexConsumers, int light1, int overlay1) {
        FabricBlockModelRenderer.render(entry, layer -> vertexConsumers.getBuffer(RenderLayerHelper.getEntityBlockLayer(layer)), model, red, green, blue, light, overlay, EmptyBlockAndTintGetter.INSTANCE, BlockPos.ZERO, state);
    }

    @Group(name = "sodium$proxy", min = 1, max = 1)
    @Dynamic
    @Redirect(method = "renderSingleBlock(Lnet/minecraft/world/level/block/state/BlockState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;)V", require = 0, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;renderModel(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/renderer/block/model/BlockStateModel;FFFIILnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V"))
    private void renderProxyNeo(PoseStack.Pose entry,
                                MultiBufferSource bufferSource,
                                BlockStateModel model,
                                float red,
                                float green,
                                float blue,
                                int light,
                                int overlay,
                                BlockAndTintGetter level,
                                BlockPos pos,
                                BlockState state) {
        FabricBlockModelRenderer.render(entry, layer -> bufferSource.getBuffer(RenderLayerHelper.getEntityBlockLayer(layer)), model, red, green, blue, light, overlay, level, pos, state);
    }
}
