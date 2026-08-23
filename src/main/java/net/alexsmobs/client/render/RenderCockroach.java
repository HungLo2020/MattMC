package net.alexsmobs.client.render;

import net.alexsmobs.client.model.ModelCockroach;
import net.alexsmobs.client.render.state.CockroachRenderState;
import net.alexsmobs.entity.EntityCockroach;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderCockroach extends MobRenderer<EntityCockroach, CockroachRenderState, ModelCockroach> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/cockroach.png");

    public RenderCockroach(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelCockroach(), 0.3F);
    }

    @Override
    public CockroachRenderState createRenderState() {
        return new CockroachRenderState();
    }

    @Override
    public void extractRenderState(EntityCockroach entity, CockroachRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.prevDanceProgress = entity.prevDanceProgress;
        renderState.danceProgress = entity.danceProgress;
        renderState.randomWingFlapTick = entity.randomWingFlapTick;
        renderState.hasMaracas = entity.hasMaracas();
        renderState.isHeadless = entity.isHeadless();
        renderState.isBaby = entity.isBaby();
    }

    protected void scale(CockroachRenderState renderState, PoseStack matrixStackIn) {
        matrixStackIn.scale(0.85F, 0.85F, 0.85F);
    }

    public ResourceLocation getTextureLocation(CockroachRenderState renderState) {
        return TEXTURE;
    }
}
