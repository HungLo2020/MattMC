package net.alexsmobs.client.render;

import net.alexsmobs.client.model.ModelShoebill;
import net.alexsmobs.entity.EntityShoebill;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderShoebill extends MobRenderer<EntityShoebill, ShoebillRenderState, ModelShoebill> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/shoebill.png");

    public RenderShoebill(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelShoebill(), 0.3F);
    }

    @Override
    public ShoebillRenderState createRenderState() {
        return new ShoebillRenderState();
    }

    @Override
    public void extractRenderState(EntityShoebill entity, ShoebillRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.flyProgress = entity.prevFlyProgress + (entity.flyProgress - entity.prevFlyProgress) * partialTick;
        state.animationTick = entity.getAnimationTick();
        // Map animation to an ID for the render state
        if (entity.getAnimation() == EntityShoebill.ANIMATION_FISH) {
            state.currentAnimationId = 1;
        } else if (entity.getAnimation() == EntityShoebill.ANIMATION_BEAKSHAKE) {
            state.currentAnimationId = 2;
        } else if (entity.getAnimation() == EntityShoebill.ANIMATION_ATTACK) {
            state.currentAnimationId = 3;
        } else {
            state.currentAnimationId = 0;
        }
    }

    protected void scale(ShoebillRenderState state, PoseStack matrixStackIn) {
    }

    public ResourceLocation getTextureLocation(ShoebillRenderState state) {
        return TEXTURE;
    }
}
