package net.alexscaves.client.render.entity;

import net.alexscaves.client.model.RelicheirusModel;
import net.alexscaves.server.entity.living.RelicheirusEntity;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RelicheirusRenderer extends MobRenderer<RelicheirusEntity, RelicheirusRenderState, RelicheirusModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/relicheirus.png");
    private static final ResourceLocation TEXTURE_RETRO = ResourceLocation.withDefaultNamespace("textures/entity/relicheirus_retro.png");
    private static final ResourceLocation TEXTURE_TECTONIC = ResourceLocation.withDefaultNamespace("textures/entity/relicheirus_tectonic.png");

    public RelicheirusRenderer(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new RelicheirusModel(), 1.0F);
    }

    @Override
    public RelicheirusRenderState createRenderState() {
        return new RelicheirusRenderState();
    }

    @Override
    public void extractRenderState(RelicheirusEntity entity, RelicheirusRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.raiseArmsAmount = entity.getRaiseArmsAmount(partialTick);
        renderState.peckY = entity.getPeckY();
        renderState.altSkin = entity.getAltSkin();
        renderState.animation = entity.getAnimation();
        renderState.animationTick = entity.getAnimationTick();
    }

    protected void scale(RelicheirusEntity mob, PoseStack matrixStackIn, float partialTicks) {
    }

    @Override
    public ResourceLocation getTextureLocation(RelicheirusRenderState state) {
        return state.altSkin == 2 ? TEXTURE_TECTONIC : state.altSkin == 1 ? TEXTURE_RETRO : TEXTURE;
    }
}
