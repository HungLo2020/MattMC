package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelRattlesnake;
import com.github.alexthe666.alexsmobs.entity.EntityRattlesnake;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderRattlesnake extends MobRenderer<EntityRattlesnake, RattlesnakeRenderState, ModelRattlesnake> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/rattlesnake.png");

    public RenderRattlesnake(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelRattlesnake(), 0.2F);
    }

    @Override
    public RattlesnakeRenderState createRenderState() {
        return new RattlesnakeRenderState();
    }

    @Override
    public void extractRenderState(EntityRattlesnake entity, RattlesnakeRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.prevCurlProgress = entity.prevCurlProgress;
        state.curlProgress = entity.curlProgress;
        state.randomToungeTick = entity.randomToungeTick;
        state.isRattling = entity.isRattling();
    }

    protected void scale(RattlesnakeRenderState state, PoseStack matrixStackIn) {
    }

    public ResourceLocation getTextureLocation(RattlesnakeRenderState state) {
        return TEXTURE;
    }
}
