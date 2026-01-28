package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelCrocodile;
import com.github.alexthe666.alexsmobs.entity.EntityCrocodile;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class RenderCrocodile extends MobRenderer<EntityCrocodile, CrocodileRenderState, ModelCrocodile> {
    private static final ResourceLocation TEXTURE_0 = ResourceLocation.withDefaultNamespace("textures/entity/crocodile_0.png");
    private static final ResourceLocation TEXTURE_1 = ResourceLocation.withDefaultNamespace("textures/entity/crocodile_1.png");
    private static final ResourceLocation TEXTURE_CROWN = ResourceLocation.withDefaultNamespace("textures/entity/crocodile_crown.png");

    public RenderCrocodile(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelCrocodile(), 0.8F);
        this.addLayer(new CrownLayer(this));
    }

    @Override
    public CrocodileRenderState createRenderState() {
        return new CrocodileRenderState();
    }

    @Override
    public void extractRenderState(EntityCrocodile entity, CrocodileRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.groundProgress = entity.prevGroundProgress + (entity.groundProgress - entity.prevGroundProgress) * partialTick;
        state.swimProgress = entity.prevSwimProgress + (entity.swimProgress - entity.prevSwimProgress) * partialTick;
        state.baskingProgress = entity.prevBaskingProgress + (entity.baskingProgress - entity.prevBaskingProgress) * partialTick;
        state.grabProgress = entity.prevGrabProgress + (entity.grabProgress - entity.prevGrabProgress) * partialTick;
        state.baskingType = entity.baskingType;
        state.isInWater = entity.isInWater();
        state.isDesert = entity.isDesert();
        state.isCrowned = entity.isCrowned();
        state.isBaby = entity.isBaby();
    }

    protected void scale(CrocodileRenderState state, PoseStack matrixStackIn) {
        float scale = state.isBaby ? 0.15F : 0.9F;
        matrixStackIn.scale(scale, scale, scale);
    }

    public ResourceLocation getTextureLocation(CrocodileRenderState state) {
        return state.isDesert ? TEXTURE_1 : TEXTURE_0;
    }

    static class CrownLayer extends RenderLayer<CrocodileRenderState, ModelCrocodile> {

        public CrownLayer(RenderCrocodile p_i50928_1_) {
            super(p_i50928_1_);
        }

        @Override
        public void submit(PoseStack matrixStackIn, net.minecraft.client.renderer.SubmitNodeCollector submitNodeCollector, int packedLightIn, CrocodileRenderState state, float f, float g) {
            if (state.isCrowned) {
                submitNodeCollector.order(1)
                    .submitModel(
                        this.getParentModel(),
                        state,
                        matrixStackIn,
                        AMRenderTypes.entityCutoutNoCull(TEXTURE_CROWN),
                        packedLightIn,
                        OverlayTexture.NO_OVERLAY,
                        -1,
                        null,
                        state.outlineColor,
                        null
                    );
            }
        }
    }


}
