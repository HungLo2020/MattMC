package net.alexsmobs.client.render;

import net.alexsmobs.client.model.ModelTasmanianDevil;
import net.alexsmobs.entity.EntityTasmanianDevil;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderTasmanianDevil extends MobRenderer<EntityTasmanianDevil, TasmanianDevilRenderState, ModelTasmanianDevil> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/tasmanian_devil.png");
    private static final ResourceLocation TEXTURE_ANGRY = ResourceLocation.withDefaultNamespace("textures/entity/tasmanian_devil_angry.png");

    public RenderTasmanianDevil(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelTasmanianDevil(), 0.3F);
    }

    @Override
    public TasmanianDevilRenderState createRenderState() {
        return new TasmanianDevilRenderState();
    }

    @Override
    public void extractRenderState(EntityTasmanianDevil entity, TasmanianDevilRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.prevBaskProgress = entity.prevBaskProgress;
        renderState.prevSitProgress = entity.prevSitProgress;
        renderState.baskProgress = entity.baskProgress;
        renderState.sitProgress = entity.sitProgress;
        renderState.animationTick = entity.getAnimationTick();
        renderState.currentAnimation = entity.getAnimation();
        renderState.isBaby = entity.isBaby();
    }

    protected void scale(EntityTasmanianDevil entitylivingbaseIn, PoseStack matrixStackIn, float partialTickTime) {
    }


    public ResourceLocation getTextureLocation(TasmanianDevilRenderState renderState) {
        return renderState.currentAnimation == EntityTasmanianDevil.ANIMATION_HOWL && renderState.animationTick < 34 ? TEXTURE_ANGRY : TEXTURE;
    }

}