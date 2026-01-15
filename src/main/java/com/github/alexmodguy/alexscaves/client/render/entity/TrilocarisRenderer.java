package com.github.alexmodguy.alexscaves.client.render.entity;

import com.github.alexmodguy.alexscaves.client.model.TrilocarisModel;
import com.github.alexmodguy.alexscaves.server.entity.living.TrilocarisEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class TrilocarisRenderer extends MobRenderer<TrilocarisEntity, TrilocarisRenderState, TrilocarisModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/trilocaris.png");

    public TrilocarisRenderer(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new TrilocarisModel(), 0.3F);
    }

    @Override
    public TrilocarisRenderState createRenderState() {
        return new TrilocarisRenderState();
    }

    @Override
    public void extractRenderState(TrilocarisEntity entity, TrilocarisRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.groundProgress = entity.getGroundProgress(partialTick);
        renderState.biteProgress = entity.getBiteProgress(partialTick);
        renderState.deathTime = entity.deathTime;
    }

    protected float getFlipDegrees(TrilocarisEntity centipede) {
        return 180.0F;
    }


    protected void scale(TrilocarisEntity mob, PoseStack matrixStackIn, float partialTicks) {
    }

    public ResourceLocation getTextureLocation(TrilocarisRenderState renderState) {
        return TEXTURE;
    }
}

