package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelCombJelly;
import com.github.alexthe666.alexsmobs.client.render.state.CombJellyRenderState;
import com.github.alexthe666.alexsmobs.entity.EntityCombJelly;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderCombJelly extends MobRenderer<EntityCombJelly, CombJellyRenderState, EntityModel<CombJellyRenderState>> {
    private static final ResourceLocation TEXTURE_0 = ResourceLocation.withDefaultNamespace("textures/entity/comb_jelly_blue.png");
    private static final ResourceLocation TEXTURE_1 = ResourceLocation.withDefaultNamespace("textures/entity/comb_jelly_green.png");
    private static final ResourceLocation TEXTURE_2 = ResourceLocation.withDefaultNamespace("textures/entity/comb_jelly_red.png");
    private float jellyScale = 1.0F;
    
    public RenderCombJelly(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelCombJelly(0.0F), 0.3F);
    }

    @Override
    public CombJellyRenderState createRenderState() {
        return new CombJellyRenderState();
    }

    @Override
    public void extractRenderState(EntityCombJelly entity, CombJellyRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.variant = entity.getVariant();
        renderState.jellyScale = entity.getJellyScale();
        renderState.jellyPitch = entity.getJellyPitch();
        renderState.prevJellyPitch = entity.prevjellyPitch;
        renderState.onLandProgress = entity.onLandProgress;
        renderState.prevOnLandProgress = entity.prevOnLandProgress;
        renderState.partialTick = partialTick;
        this.jellyScale = entity.getJellyScale();
    }

    @Override
    protected void scale(CombJellyRenderState renderState, PoseStack matrixStackIn) {
        matrixStackIn.scale(jellyScale, jellyScale, jellyScale);
    }

    @Override
    public ResourceLocation getTextureLocation(CombJellyRenderState renderState) {
        return renderState.variant == 0 ? TEXTURE_0 : renderState.variant == 1 ? TEXTURE_1 : TEXTURE_2;
    }
}
