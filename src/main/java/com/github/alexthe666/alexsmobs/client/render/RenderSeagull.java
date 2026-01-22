package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelSeagull;
import com.github.alexthe666.alexsmobs.entity.EntitySeagull;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderSeagull extends MobRenderer<EntitySeagull, SeagullRenderState, ModelSeagull> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/seagull.png");
    private static final ResourceLocation TEXTURE_WINGULL = ResourceLocation.withDefaultNamespace("textures/entity/seagull_wingull.png");

    public RenderSeagull(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelSeagull(), 0.2F);
    }

    @Override
    public SeagullRenderState createRenderState() {
        return new SeagullRenderState();
    }

    @Override
    public void extractRenderState(EntitySeagull entity, SeagullRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.flyProgress = entity.prevFlyProgress + (entity.flyProgress - entity.prevFlyProgress) * partialTick;
        state.flapAmount = entity.prevFlapAmount + (entity.flapAmount - entity.prevFlapAmount) * partialTick;
        state.attackProgress = entity.prevAttackProgress + (entity.attackProgress - entity.prevAttackProgress) * partialTick;
        state.sitProgress = entity.prevSitProgress + (entity.sitProgress - entity.prevSitProgress) * partialTick;
        state.flightLookYaw = entity.getFlightLookYaw();
        state.isWingull = entity.isWingull();
    }

    protected void scale(SeagullRenderState state, PoseStack matrixStackIn) {
    }

    public ResourceLocation getTextureLocation(SeagullRenderState state) {
        return state.isWingull ? TEXTURE_WINGULL : TEXTURE;
    }
}
