package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelCatfishLarge;
import com.github.alexthe666.alexsmobs.client.model.ModelCatfishMedium;
import com.github.alexthe666.alexsmobs.client.model.ModelCatfishSmall;
import com.github.alexthe666.alexsmobs.client.render.state.CatfishRenderState;
import com.github.alexthe666.alexsmobs.entity.EntityCatfish;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderCatfish extends MobRenderer<EntityCatfish, CatfishRenderState, EntityModel<CatfishRenderState>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/catfish_small.png");
    private static final ResourceLocation TEXTURE_MEDIUM = ResourceLocation.withDefaultNamespace("textures/entity/catfish_medium.png");
    private static final ResourceLocation TEXTURE_LARGE = ResourceLocation.withDefaultNamespace("textures/entity/catfish_large.png");
    private static final ResourceLocation TEXTURE_SPIT = ResourceLocation.withDefaultNamespace("textures/entity/catfish_small_spit.png");
    private static final ResourceLocation TEXTURE_SPIT_MEDIUM = ResourceLocation.withDefaultNamespace("textures/entity/catfish_medium_spit.png");
    private static final ResourceLocation TEXTURE_SPIT_LARGE = ResourceLocation.withDefaultNamespace("textures/entity/catfish_large_spit.png");
    private final ModelCatfishSmall modelSmall = new ModelCatfishSmall();
    private final ModelCatfishMedium modelMedium = new ModelCatfishMedium();
    private final ModelCatfishLarge modelLarge = new ModelCatfishLarge();

    public RenderCatfish(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelCatfishSmall(), 0.5F);
    }

    @Override
    public CatfishRenderState createRenderState() {
        return new CatfishRenderState();
    }

    @Override
    public void extractRenderState(EntityCatfish entity, CatfishRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.catfishSize = entity.getCatfishSize();
        renderState.isSpitting = entity.isSpitting();
    }

    protected void scale(CatfishRenderState renderState, PoseStack matrixStackIn) {
        if (renderState.catfishSize == 2) {
            model = modelLarge;
        } else if (renderState.catfishSize == 1) {
            model = modelMedium;
        } else {
            model = modelSmall;
        }
    }

    public ResourceLocation getTextureLocation(CatfishRenderState renderState) {
        if(renderState.catfishSize == 2){
            return renderState.isSpitting ? TEXTURE_SPIT_LARGE : TEXTURE_LARGE;
        }
        if(renderState.catfishSize == 1){
            return renderState.isSpitting ? TEXTURE_SPIT_MEDIUM : TEXTURE_MEDIUM;
        }
        return renderState.isSpitting ? TEXTURE_SPIT : TEXTURE;
    }
}
