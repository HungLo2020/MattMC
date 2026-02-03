package net.alexsmobs.client.render;

import net.alexsmobs.client.model.ModelSnowLeopard;
import net.alexsmobs.entity.EntitySnowLeopard;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderSnowLeopard extends MobRenderer<EntitySnowLeopard, SnowLeopardRenderState, ModelSnowLeopard> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/snow_leopard.png");
    private static final ResourceLocation TEXTURE_SLEEPING = ResourceLocation.withDefaultNamespace("textures/entity/snow_leopard_sleeping.png");

    public RenderSnowLeopard(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelSnowLeopard(), 0.4F);
    }

    @Override
    public SnowLeopardRenderState createRenderState() {
        return new SnowLeopardRenderState();
    }

    @Override
    public void extractRenderState(EntitySnowLeopard entity, SnowLeopardRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.sneakProgress = entity.sneakProgress;
        renderState.prevSneakProgress = entity.prevSneakProgress;
        renderState.tackleProgress = entity.tackleProgress;
        renderState.prevTackleProgress = entity.prevTackleProgress;
        renderState.sitProgress = entity.sitProgress;
        renderState.prevSitProgress = entity.prevSitProgress;
        renderState.sleepProgress = entity.sleepProgress;
        renderState.prevSleepProgress = entity.prevSleepProgress;
        renderState.isSleeping = entity.isSleeping();
        renderState.animationTick = entity.getAnimationTick();
        renderState.currentAnimation = entity.getAnimation();
        renderState.entityId = entity.getId();
    }

    protected void scale(EntitySnowLeopard entitylivingbaseIn, PoseStack matrixStackIn, float partialTickTime) {
        matrixStackIn.scale(0.9F,0.9F, 0.9F);
    }


    public ResourceLocation getTextureLocation(SnowLeopardRenderState renderState) {
        return renderState.isSleeping ? TEXTURE_SLEEPING : TEXTURE;
    }
}
