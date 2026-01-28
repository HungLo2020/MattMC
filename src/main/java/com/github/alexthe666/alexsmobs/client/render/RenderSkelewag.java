package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelSkelewag;
import com.github.alexthe666.alexsmobs.entity.EntitySkelewag;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public class RenderSkelewag extends MobRenderer<EntitySkelewag, SkelewagRenderState, ModelSkelewag> {
    private static final ResourceLocation TEXTURE_0 = ResourceLocation.withDefaultNamespace("textures/entity/skelewag_0.png");
    private static final ResourceLocation TEXTURE_1 = ResourceLocation.withDefaultNamespace("textures/entity/skelewag_1.png");

    public RenderSkelewag(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelSkelewag(), 0.5F);
    }

    @Override
    public SkelewagRenderState createRenderState() {
        return new SkelewagRenderState();
    }

    @Override
    public void extractRenderState(EntitySkelewag entity, SkelewagRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.onLandProgress = entity.prevOnLandProgress + (entity.onLandProgress - entity.prevOnLandProgress) * partialTick;
        state.variant = entity.getVariant();
        state.animationTick = entity.getAnimationTick();
        state.currentAnimationId = entity.getAnimation() != null ? entity.getAnimation().getID() : 0;
        state.deathTime = entity.deathTime;
    }

    protected void scale(SkelewagRenderState state, PoseStack matrixStackIn) {
    }

    public ResourceLocation getTextureLocation(SkelewagRenderState state) {
        return state.variant == 1 ? TEXTURE_1 : TEXTURE_0;
    }
}
