package com.github.alexthe666.alexsmobs.client.render.layer;

import com.github.alexthe666.alexsmobs.client.model.ModelGorilla;
import com.github.alexthe666.alexsmobs.client.render.GorillaRenderState;
import com.github.alexthe666.alexsmobs.client.render.RenderGorilla;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class LayerGorillaItem extends RenderLayer<GorillaRenderState, ModelGorilla> {

    public LayerGorillaItem(RenderGorilla render) {
        super(render);
    }

    public void render(PoseStack matrixStackIn, MultiBufferSource bufferIn, int packedLightIn, GorillaRenderState state, float limbSwing, float limbSwingAmount) {
        ItemStack itemstack = state.mainHandItem;
        String name = state.name.toLowerCase();
        ItemInHandRenderer renderer = Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer();
        if(name.contains("harambe")){
            ItemStack haloStack = new ItemStack(Items.GOLDEN_HELMET); // Use golden helmet as halo substitute
            matrixStackIn.pushPose();
            this.getParentModel().root.translateAndRotate(matrixStackIn);
            this.getParentModel().body.translateAndRotate(matrixStackIn);
            this.getParentModel().chest.translateAndRotate(matrixStackIn);
            this.getParentModel().head.translateAndRotate(matrixStackIn);
            float f = 0.1F * (float) Math.sin(state.ageInTicks * 0.1F) + (state.isBaby ? 0.2F : 0F);
            matrixStackIn.translate(0.0F, -0.7F - f, -0.2F);
            matrixStackIn.mulPose(Axis.XP.rotationDegrees(90F));
            matrixStackIn.scale(1.3F, 1.3F, 1.3F);
            renderer.renderItem(null, haloStack, ItemDisplayContext.GROUND, false, matrixStackIn, bufferIn, packedLightIn);
            matrixStackIn.popPose();
        }
        if(!itemstack.isEmpty()) {
            matrixStackIn.pushPose();
            if(state.isBaby){
                matrixStackIn.scale(0.35F, 0.35F, 0.35F);
                matrixStackIn.translate(-0.1D, 2D, -1.15D);
                translateToHand(false, matrixStackIn);
                matrixStackIn.translate(-0.4F, 0.75F, -0.0F);
                matrixStackIn.scale(2.8F, 2.8F, 2.8F);
            }else{
                translateToHand(false, matrixStackIn);
                matrixStackIn.translate(-0.4F, 0.75F, -0.0F);
            }
            matrixStackIn.mulPose(Axis.YP.rotationDegrees(-2.5F));
            matrixStackIn.mulPose(Axis.XP.rotationDegrees(-90F));
            if(itemstack.getItem() instanceof BlockItem){
                matrixStackIn.scale(2, 2, 2);
            }
            renderer.renderItem(null, itemstack, ItemDisplayContext.GROUND, false, matrixStackIn, bufferIn, packedLightIn);
            matrixStackIn.popPose();
        }
    }

    protected void translateToHand(boolean left, PoseStack matrixStack) {
        this.getParentModel().root.translateAndRotate(matrixStack);
        this.getParentModel().body.translateAndRotate(matrixStack);
        this.getParentModel().chest.translateAndRotate(matrixStack);
        this.getParentModel().leftArm.translateAndRotate(matrixStack);
    }
}
