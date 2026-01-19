package com.github.alexthe666.alexsmobs.client.render.layer;

import com.github.alexthe666.alexsmobs.client.model.ModelCockroach;
import com.github.alexthe666.alexsmobs.client.render.RenderCockroach;
import com.github.alexthe666.alexsmobs.client.render.state.CockroachRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class LayerCockroachMaracas extends RenderLayer<CockroachRenderState, ModelCockroach> {

    private final ItemStack stack;
    private static final ResourceLocation SOMBRERO_TEX = ResourceLocation.withDefaultNamespace("textures/armor/sombrero.png");

    public LayerCockroachMaracas(RenderCockroach render, EntityRendererProvider.Context renderManagerIn) {
        super(render);
        stack = new ItemStack(Items.MARACA);
    }

    public void render(PoseStack matrixStackIn, MultiBufferSource bufferIn, int packedLightIn, CockroachRenderState renderState, float limbSwing, float limbSwingAmount) {
        if(renderState.hasMaracas){
            ItemInHandRenderer renderer = Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer();
            matrixStackIn.pushPose();
            if (renderState.isBaby) {
                matrixStackIn.scale(0.65F, 0.65F, 0.65F);
                matrixStackIn.translate(0.0D, 0.815D, 0.125D);
            }
            matrixStackIn.pushPose();
            translateToHand(0, matrixStackIn);
            matrixStackIn.translate(-0.25F, 0.0F, 0);
            matrixStackIn.scale(1.4F, 1.4F, 1.4F);
            matrixStackIn.mulPose(Axis.XP.rotationDegrees(-90F));
            matrixStackIn.mulPose(Axis.ZP.rotationDegrees(60F));
            renderer.renderItem(null, stack, ItemDisplayContext.GROUND, false, matrixStackIn, bufferIn, packedLightIn);
            matrixStackIn.popPose();
            matrixStackIn.pushPose();
            translateToHand(1, matrixStackIn);
            matrixStackIn.translate(0.25F, 0.0F, 0);
            matrixStackIn.scale(1.4F, 1.4F, 1.4F);
            matrixStackIn.mulPose(Axis.XP.rotationDegrees(90F));
            matrixStackIn.mulPose(Axis.ZP.rotationDegrees(-120F));
            renderer.renderItem(null, stack, ItemDisplayContext.GROUND, false, matrixStackIn, bufferIn, packedLightIn);
            matrixStackIn.popPose();
            matrixStackIn.pushPose();
            translateToHand(2, matrixStackIn);
            matrixStackIn.translate(-0.35F, 0.0F, 0);
            matrixStackIn.scale(1.4F, 1.4F, 1.4F);
            matrixStackIn.mulPose(Axis.XP.rotationDegrees(-90F));
            matrixStackIn.mulPose(Axis.ZP.rotationDegrees(60F));
            renderer.renderItem(null, stack, ItemDisplayContext.GROUND, false, matrixStackIn, bufferIn, packedLightIn);
            matrixStackIn.popPose();
            matrixStackIn.pushPose();
            translateToHand(3, matrixStackIn);
            matrixStackIn.translate(0.35F, 0.0F, 0);
            matrixStackIn.scale(1.4F, 1.4F, 1.4F);
            matrixStackIn.mulPose(Axis.XP.rotationDegrees(90F));
            matrixStackIn.mulPose(Axis.ZP.rotationDegrees(-120F));
            renderer.renderItem(null, stack, ItemDisplayContext.GROUND, false, matrixStackIn, bufferIn, packedLightIn);
            matrixStackIn.popPose();
            // Sombrero rendering removed for simplicity - can be added later
            matrixStackIn.popPose();
        }
    }

    protected void translateToHand(int hand, PoseStack matrixStack) {
        this.getParentModel().root.translateAndRotate(matrixStack);
        this.getParentModel().abdomen.translateAndRotate(matrixStack);
        if (hand == 0) {
            this.getParentModel().right_leg_front.translateAndRotate(matrixStack);
        } else if (hand == 1) {
            this.getParentModel().left_leg_front.translateAndRotate(matrixStack);
        } else if (hand == 2) {
            this.getParentModel().right_leg_mid.translateAndRotate(matrixStack);
        } else if (hand == 3) {
            this.getParentModel().left_leg_mid.translateAndRotate(matrixStack);
        }else{
            this.getParentModel().neck.translateAndRotate(matrixStack);
            this.getParentModel().head.translateAndRotate(matrixStack);
        }
    }
}
