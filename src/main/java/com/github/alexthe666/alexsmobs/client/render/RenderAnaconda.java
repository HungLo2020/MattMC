package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelAnaconda;
import com.github.alexthe666.alexsmobs.entity.EntityAnaconda;
import com.github.alexthe666.alexsmobs.entity.util.AnacondaPartIndex;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderAnaconda extends MobRenderer<EntityAnaconda, AnacondaRenderState, ModelAnaconda> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/anaconda.png");
    private static final ResourceLocation TEXTURE_SHEDDING = ResourceLocation.withDefaultNamespace("textures/entity/anaconda_shedding.png");
    private static final ResourceLocation TEXTURE_YELLOW = ResourceLocation.withDefaultNamespace("textures/entity/anaconda_yellow.png");
    private static final ResourceLocation TEXTURE_YELLOW_SHEDDING = ResourceLocation.withDefaultNamespace("textures/entity/anaconda_yellow_shedding.png");

    public RenderAnaconda(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelAnaconda(AnacondaPartIndex.HEAD), 0.3F);
    }

    @Override
    public AnacondaRenderState createRenderState() {
        return new AnacondaRenderState();
    }

    @Override
    public void extractRenderState(EntityAnaconda entity, AnacondaRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.strangleProgress = entity.getStrangleProgress(partialTick);
        renderState.yellow = entity.isYellow();
        renderState.sheddingTime = entity.getSheddingTime();
        System.arraycopy(entity.ringBuffer, 0, renderState.ringBuffer, 0, entity.ringBuffer.length);
    }

    @Override
    protected void scale(AnacondaRenderState renderState, PoseStack matrixStackIn) {
        float scale = renderState.isBaby ? 0.5F : 1.0F;
        matrixStackIn.scale(scale, scale, scale);
    }

    public static ResourceLocation getAnacondaTexture(boolean yellow, boolean shedding) {
        return yellow ? shedding ? TEXTURE_YELLOW_SHEDDING : TEXTURE_YELLOW : shedding ? TEXTURE_SHEDDING : TEXTURE;
    }

    @Override
    public ResourceLocation getTextureLocation(AnacondaRenderState renderState) {
        return getAnacondaTexture(renderState.yellow, renderState.sheddingTime > 0);
    }
}
