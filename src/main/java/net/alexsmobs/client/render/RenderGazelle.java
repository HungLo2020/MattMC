package net.alexsmobs.client.render;

import net.alexsmobs.client.model.ModelGazelle;
import net.alexsmobs.client.render.state.GazelleRenderState;
import net.alexsmobs.entity.EntityGazelle;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderGazelle extends MobRenderer<EntityGazelle, GazelleRenderState, ModelGazelle> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/gazelle.png");

    public RenderGazelle(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelGazelle(), 0.4F);
    }

    @Override
    public GazelleRenderState createRenderState() {
        return new GazelleRenderState();
    }

    @Override
    public void extractRenderState(EntityGazelle entity, GazelleRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.animationTick = entity.getAnimationTick();
        renderState.currentAnimationId = entity.getAnimation() == null ? -1 : entity.getAnimation().getID();
        renderState.isRunning = entity.isRunning();
    }

    protected void scale(GazelleRenderState renderState, PoseStack matrixStackIn) {
        matrixStackIn.scale(0.8F, 0.8F, 0.8F);
    }

    public ResourceLocation getTextureLocation(GazelleRenderState renderState) {
        return TEXTURE;
    }
}
