package com.github.alexthe666.alexsmobs.client.render.layer;

import com.github.alexthe666.alexsmobs.client.model.ModelElephant;
import com.github.alexthe666.alexsmobs.client.render.ElephantRenderState;
import com.github.alexthe666.alexsmobs.client.render.RenderElephant;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class LayerElephantItem extends RenderLayer<ElephantRenderState, ModelElephant> {

    public LayerElephantItem(RenderElephant render) {
        super(render);
    }

    public void render(PoseStack matrixStackIn, MultiBufferSource bufferIn, int packedLightIn, ElephantRenderState state, float limbSwing, float limbSwingAmount) {
        ItemStack itemstack = state.mainHandItem;
        if (itemstack.isEmpty()) {
            return;
        }
        matrixStackIn.pushPose();
        if(state.isBaby){
            matrixStackIn.scale(0.35F, 0.35F, 0.35F);
            matrixStackIn.translate(0.0D, 2.8D, 0D);
        }
        matrixStackIn.pushPose();
        translateToHand(matrixStackIn);
        if(state.isBaby){
            matrixStackIn.translate(0.0D, 0.2F, -0.22D);
        }
        matrixStackIn.translate(-0.0, 1.0F, 0.15F);
        matrixStackIn.mulPose(Axis.XP.rotationDegrees(180F));
        matrixStackIn.scale(1.3F, 1.3F, 1.3F);
        if(Minecraft.getInstance().getItemRenderer().getItemModelShaper().getItemModel(itemstack).isGui3d()){
            matrixStackIn.translate(-0.05F, -0.1F, -0.15F);
            matrixStackIn.scale(2, 2, 2);
        }
        ItemInHandRenderer renderer = Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer();
        // Note: renderItem needs a LivingEntity, but we only have render state. Using null as fallback.
        renderer.renderItem(null, itemstack, ItemDisplayContext.GROUND, false, matrixStackIn, bufferIn, packedLightIn);
        matrixStackIn.popPose();
        matrixStackIn.popPose();
    }

    protected void translateToHand(PoseStack matrixStack) {
        this.getParentModel().root.translateAndRotate(matrixStack);
        this.getParentModel().body.translateAndRotate(matrixStack);
        this.getParentModel().head.translateAndRotate(matrixStack);
        this.getParentModel().trunk1.translateAndRotate(matrixStack);
        this.getParentModel().trunk2.translateAndRotate(matrixStack);

    }
}
