package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelSkunk;
import com.github.alexthe666.alexsmobs.entity.EntitySkunk;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderSkunk extends MobRenderer<EntitySkunk, SkunkRenderState, ModelSkunk> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/skunk.png");

    public RenderSkunk(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelSkunk(), 0.45F);
    }

    @Override
    public SkunkRenderState createRenderState() {
        return new SkunkRenderState();
    }

    @Override
    public void extractRenderState(EntitySkunk entity, SkunkRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.sprayProgress = entity.sprayProgress;
        state.prevSprayProgress = entity.prevSprayProgress;
        state.tickCount = entity.tickCount;
        state.isBaby = entity.isBaby();
    }

    public ResourceLocation getTextureLocation(SkunkRenderState state) {
        return TEXTURE;
    }
}
