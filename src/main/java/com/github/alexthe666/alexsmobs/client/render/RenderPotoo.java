package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelPotoo;
import com.github.alexthe666.alexsmobs.client.render.state.PotooRenderState;
import com.github.alexthe666.alexsmobs.entity.EntityPotoo;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderPotoo extends MobRenderer<EntityPotoo, PotooRenderState, ModelPotoo> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/potoo.png");

    public RenderPotoo(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelPotoo(), 0.35F);
    }

    @Override
    public PotooRenderState createRenderState() {
        return new PotooRenderState();
    }

    @Override
    public void extractRenderState(EntityPotoo entity, PotooRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.flyProgress = entity.prevFlyProgress + (entity.flyProgress - entity.prevFlyProgress) * partialTick;
        renderState.perchProgress = entity.prevPerchProgress + (entity.perchProgress - entity.prevPerchProgress) * partialTick;
        renderState.mouthProgress = entity.prevMouthProgress + (entity.mouthProgress - entity.prevMouthProgress) * partialTick;
        renderState.eyeScale = entity.getEyeScale(10, partialTick);
        renderState.isSleeping = entity.isSleeping();
        renderState.isYoung = entity.isBaby();
    }

    public ResourceLocation getTextureLocation(PotooRenderState renderState) {
        return TEXTURE;
    }
}
