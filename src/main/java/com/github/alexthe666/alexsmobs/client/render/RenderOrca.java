package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelOrca;
import com.github.alexthe666.alexsmobs.entity.EntityOrca;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderOrca extends MobRenderer<EntityOrca, OrcaRenderState, ModelOrca> {
    private static final ResourceLocation TEXTURE_NE = ResourceLocation.withDefaultNamespace("textures/entity/orca_ne.png");
    private static final ResourceLocation TEXTURE_NW = ResourceLocation.withDefaultNamespace("textures/entity/orca_nw.png");
    private static final ResourceLocation TEXTURE_SE = ResourceLocation.withDefaultNamespace("textures/entity/orca_se.png");
    private static final ResourceLocation TEXTURE_SW = ResourceLocation.withDefaultNamespace("textures/entity/orca_sw.png");

    public RenderOrca(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelOrca(), 1.0F);
    }

    @Override
    public OrcaRenderState createRenderState() {
        return new OrcaRenderState();
    }

    @Override
    public void extractRenderState(EntityOrca entity, OrcaRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.variant = entity.getVariant();
        state.animationTick = entity.getAnimationTick();
        state.currentAnimation = entity.getAnimation();
        state.xd = entity.getDeltaMovement().x;
        state.zd = entity.getDeltaMovement().z;
    }

    protected void scale(OrcaRenderState state, PoseStack matrixStackIn) {
        matrixStackIn.scale(1.3F, 1.3F, 1.3F);
    }

    public ResourceLocation getTextureLocation(OrcaRenderState state) {
        return switch (state.variant) {
            case 0 -> TEXTURE_NE;
            case 1 -> TEXTURE_NW;
            case 2 -> TEXTURE_SE;
            default -> TEXTURE_SW;
        };
    }
}
