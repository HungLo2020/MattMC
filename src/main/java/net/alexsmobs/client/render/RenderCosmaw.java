package net.alexsmobs.client.render;

import net.alexsmobs.client.model.ModelCosmaw;
import net.alexsmobs.client.render.layer.LayerBasicGlow;
import net.alexsmobs.entity.EntityCosmaw;
import net.blaze3d.vertex.PoseStack;
import net.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.texture.OverlayTexture;

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
        this.itemModelResolver.updateForLiving(state.mainHandItem, entity.getMainHandItem(),
            net.minecraft.world.item.ItemDisplayContext.GROUND, entity);
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

        public void submit(PoseStack matrixStackIn, SubmitNodeCollector bufferIn, int packedLightIn, CosmawRenderState state, float limbSwing, float limbSwingAmount) {
            if (!state.mainHandItem.isEmpty()) {
                matrixStackIn.pushPose();
                translateToHand(matrixStackIn);
                matrixStackIn.translate(-0.0, 0.1F, -1.35F);
                matrixStackIn.mulPose(Axis.XP.rotationDegrees(-45F));
                matrixStackIn.mulPose(Axis.YP.rotationDegrees(-180F));
                matrixStackIn.mulPose(Axis.ZP.rotationDegrees(135F));
                matrixStackIn.scale(2, 2, 2);
                state.mainHandItem.submit(matrixStackIn, bufferIn, packedLightIn,
                    OverlayTexture.NO_OVERLAY, state.outlineColor);
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
