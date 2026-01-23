package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelCosmaw;
import com.github.alexthe666.alexsmobs.client.render.layer.LayerBasicGlow;
import com.github.alexthe666.alexsmobs.entity.EntityCosmaw;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class RenderCosmaw extends MobRenderer<EntityCosmaw, CosmawRenderState, ModelCosmaw> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/cosmaw.png");
    private static final ResourceLocation TEXTURE_GLOW = ResourceLocation.withDefaultNamespace("textures/entity/cosmaw_glow.png");

    public RenderCosmaw(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelCosmaw(), 0.9F);
        this.addLayer(new LayerHeldItem());
        this.addLayer(new LayerBasicGlow(this, TEXTURE_GLOW));
    }

    @Override
    public CosmawRenderState createRenderState() {
        return new CosmawRenderState();
    }

    @Override
    public void extractRenderState(EntityCosmaw entity, CosmawRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.clutchProgress = entity.prevClutchProgress + (entity.clutchProgress - entity.prevClutchProgress) * partialTick;
        state.openProgress = entity.prevOpenProgress + (entity.openProgress - entity.prevOpenProgress) * partialTick;
        state.cosmawPitch = entity.getClampedCosmawPitch(partialTick);
        state.biteProgress = entity.prevBiteProgress + (entity.biteProgress - entity.prevBiteProgress) * partialTick;
        state.mainHandItem = entity.getMainHandItem().copy();
    }

    protected void scale(CosmawRenderState state, PoseStack matrixStackIn) {
        matrixStackIn.translate(0, -0.5F, 0);
    }

    public ResourceLocation getTextureLocation(CosmawRenderState state) {
        return TEXTURE;
    }

    class LayerHeldItem extends RenderLayer<CosmawRenderState, ModelCosmaw> {

        public LayerHeldItem() {
            super(RenderCosmaw.this);
        }

        public void render(PoseStack matrixStackIn, MultiBufferSource bufferIn, int packedLightIn, CosmawRenderState state, float limbSwing, float limbSwingAmount) {
            ItemStack itemstack = state.mainHandItem;
            if (!itemstack.isEmpty()) {
                matrixStackIn.pushPose();
                translateToHand(matrixStackIn);
                matrixStackIn.translate(-0.0, 0.1F, -1.35F);
                matrixStackIn.mulPose(Axis.XP.rotationDegrees(-45F));
                matrixStackIn.mulPose(Axis.YP.rotationDegrees(-180F));
                matrixStackIn.mulPose(Axis.ZP.rotationDegrees(135F));
                matrixStackIn.scale(2, 2, 2);
                ItemInHandRenderer renderer = Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer();
                renderer.renderItem(null, itemstack, ItemDisplayContext.GROUND, false, matrixStackIn, bufferIn, packedLightIn);
                matrixStackIn.popPose();
            }
        }

        protected void translateToHand(PoseStack matrixStack) {
            this.getParentModel().root.translateAndRotate(matrixStack);
            this.getParentModel().body.translateAndRotate(matrixStack);
            this.getParentModel().mouthArm1.translateAndRotate(matrixStack);
            this.getParentModel().mouthArm2.translateAndRotate(matrixStack);

        }
    }
}
