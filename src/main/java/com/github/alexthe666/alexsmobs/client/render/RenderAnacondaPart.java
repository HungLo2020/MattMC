package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelAnaconda;
import com.github.alexthe666.alexsmobs.entity.EntityAnacondaPart;
import com.github.alexthe666.alexsmobs.entity.util.AnacondaPartIndex;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderAnacondaPart extends LivingEntityRenderer<EntityAnacondaPart, AnacondaRenderState, ModelAnaconda> {
    private final ModelAnaconda neckModel = new ModelAnaconda(AnacondaPartIndex.NECK);
    private final ModelAnaconda bodyModel = new ModelAnaconda(AnacondaPartIndex.BODY);
    private final ModelAnaconda tailModel = new ModelAnaconda(AnacondaPartIndex.TAIL);

    public RenderAnacondaPart(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelAnaconda(AnacondaPartIndex.NECK), 0.3F);
    }

    @Override
    public AnacondaRenderState createRenderState() {
        return new AnacondaRenderState();
    }

    @Override
    public void extractRenderState(EntityAnacondaPart entity, AnacondaRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.strangleProgress = entity.getStrangleProgress(partialTick);
        renderState.yellow = entity.isYellow();
        renderState.sheddingTime = entity.isShedding() ? 1 : 0;
        // Set the model based on the part type
        this.model = getModelForType(entity.getPartType());
    }

    @Override
    protected void scale(AnacondaRenderState renderState, PoseStack matrixStackIn) {
        float scale = renderState.isBaby ? 0.5F : 1.0F;
        matrixStackIn.scale(scale, scale, scale);
    }

    private ModelAnaconda getModelForType(AnacondaPartIndex partType) {
        switch (partType){
            case BODY: return bodyModel;
            case NECK: return neckModel;
            case TAIL: return tailModel;
        }
        return bodyModel;
    }


    @Override
    public ResourceLocation getTextureLocation(AnacondaRenderState renderState) {
        return RenderAnaconda.getAnacondaTexture(renderState.yellow, renderState.sheddingTime > 0);
    }
}
