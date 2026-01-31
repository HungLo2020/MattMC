package net.alexsmobs.client.render;

import net.alexsmobs.client.model.ModelFlyingFish;
import net.alexsmobs.client.render.state.FlyingFishRenderState;
import net.alexsmobs.entity.EntityFlyingFish;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderFlyingFish extends MobRenderer<EntityFlyingFish, FlyingFishRenderState, EntityModel<FlyingFishRenderState>> {
    private static final ResourceLocation TEXTURE_0 = ResourceLocation.withDefaultNamespace("textures/entity/flying_fish_0.png");
    private static final ResourceLocation TEXTURE_1 = ResourceLocation.withDefaultNamespace("textures/entity/flying_fish_1.png");
    private static final ResourceLocation TEXTURE_2 = ResourceLocation.withDefaultNamespace("textures/entity/flying_fish_2.png");

    public RenderFlyingFish(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelFlyingFish(), 0.2F);
    }

    @Override
    public FlyingFishRenderState createRenderState() {
        return new FlyingFishRenderState();
    }

    @Override
    public void extractRenderState(EntityFlyingFish entity, FlyingFishRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.prevOnLandProgress = entity.prevOnLandProgress;
        renderState.onLandProgress = entity.onLandProgress;
        renderState.prevFlyProgress = entity.prevFlyProgress;
        renderState.flyProgress = entity.flyProgress;
        renderState.variant = entity.getVariant();
    }

    protected void scale(FlyingFishRenderState renderState, PoseStack matrixStackIn) {
        matrixStackIn.scale(0.8F, 0.8F, 0.8F);
    }

    public ResourceLocation getTextureLocation(FlyingFishRenderState renderState) {
        switch (renderState.variant){
            case 0:
                return TEXTURE_0;
            case 1:
                return TEXTURE_1;
            case 2:
                return TEXTURE_2;
        }
        return TEXTURE_0;
    }
}
