package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelBunfungus;
import com.github.alexthe666.alexsmobs.entity.EntityBunfungus;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class RenderBunfungus extends MobRenderer<EntityBunfungus, BunfungusRenderState, ModelBunfungus> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/bunfungus.png");
    private static final ResourceLocation TEXTURE_SLEEPING = ResourceLocation.withDefaultNamespace("textures/entity/bunfungus_sleeping.png");

    public RenderBunfungus(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelBunfungus(), 0.6F);
        this.addLayer(new LayerHeldItem(this));
    }

    @Override
    public BunfungusRenderState createRenderState() {
        return new BunfungusRenderState();
    }

    @Override
    public void extractRenderState(EntityBunfungus entity, BunfungusRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.jumpProgress = entity.prevJumpProgress + (entity.jumpProgress - entity.prevJumpProgress) * partialTick;
        renderState.reboundProgress = entity.prevReboundProgress + (entity.reboundProgress - entity.prevReboundProgress) * partialTick;
        renderState.sleepProgress = entity.prevSleepProgress + (entity.sleepProgress - entity.prevSleepProgress) * partialTick;
        renderState.interestedProgress = entity.prevInterestedProgress + (entity.interestedProgress - entity.prevInterestedProgress) * partialTick;
        renderState.transformsIn = entity.transformsIn();
        renderState.prevTransformTime = entity.prevTransformTime;
        renderState.isSleeping = entity.isSleeping();
        renderState.mainHandItem = entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND).copy();
        renderState.currentAnimation = entity.getAnimation();
        renderState.animationTick = entity.getAnimationTick();
    }


    protected void scale(BunfungusRenderState renderState, PoseStack matrixStackIn, float partialTickTime) {
        float f = renderState.prevTransformTime + (renderState.transformsIn - renderState.prevTransformTime) * partialTickTime;
        float f1 = (EntityBunfungus.MAX_TRANSFORM_TIME - f) / (float)EntityBunfungus.MAX_TRANSFORM_TIME;
        float f2 = f1 * 0.7F + 0.3F;
        matrixStackIn.scale(f2, f2, f2);
    }

    public ResourceLocation getTextureLocation(BunfungusRenderState renderState) {
        return renderState.isSleeping ? TEXTURE_SLEEPING : TEXTURE;
    }

    static class LayerHeldItem extends RenderLayer<BunfungusRenderState, ModelBunfungus> {

        public LayerHeldItem(RenderBunfungus render) {
            super(render);
        }

        @Override
        public void submit(PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector submitNodeCollector, int packedLight, BunfungusRenderState renderState, float limbSwing, float limbSwingAmount) {
            // TODO: Item rendering in layers needs re-implementation with the new 1.21 API
            // The ItemInHandRenderer API may have changed. Commenting out for now.
            // ItemStack itemstack = renderState.mainHandItem;
            // This would need to use submitItem or similar method if available
        }

        protected void translateToHand(PoseStack matrixStack) {
            this.getParentModel().root.translateAndRotate(matrixStack);
            this.getParentModel().body.translateAndRotate(matrixStack);
            this.getParentModel().right_arm.translateAndRotate(matrixStack);

        }
    }
}
