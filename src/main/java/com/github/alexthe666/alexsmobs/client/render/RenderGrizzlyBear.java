package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelGrizzlyBear;
import com.github.alexthe666.alexsmobs.client.render.layer.LayerGrizzlyHoney;
import com.github.alexthe666.alexsmobs.client.render.layer.LayerGrizzlyItem;
import com.github.alexthe666.alexsmobs.entity.EntityGrizzlyBear;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

public class RenderGrizzlyBear extends MobRenderer<EntityGrizzlyBear, GrizzlyBearRenderState, ModelGrizzlyBear> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/grizzly_bear.png");
    private static final ResourceLocation TEXTURE_SNOWY = ResourceLocation.withDefaultNamespace("textures/entity/grizzly_bear_snowy.png");
    public static final ResourceLocation TEXTURE_FREDDY = ResourceLocation.withDefaultNamespace("textures/entity/grizzly_bear_freddy.png");
    private static final ResourceLocation TEXTURE_FREDDY_EYES = ResourceLocation.withDefaultNamespace("textures/entity/grizzly_bear_freddy_eyes.png");

    public RenderGrizzlyBear(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelGrizzlyBear(), 0.8F);
        this.addLayer(new LayerFreddyEyes());
        this.addLayer(new LayerGrizzlyHoney(this));
        this.addLayer(new LayerSnow());
        this.addLayer(new LayerGrizzlyItem(this));
    }

    @Override
    public GrizzlyBearRenderState createRenderState() {
        return new GrizzlyBearRenderState();
    }

    @Override
    public void extractRenderState(EntityGrizzlyBear entity, GrizzlyBearRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        net.minecraft.client.renderer.entity.state.HoldingEntityRenderState.extractHoldingEntityRenderState(entity, state, this.itemModelResolver);
        state.standProgress = entity.prevStandProgress + (entity.standProgress - entity.prevStandProgress) * partialTick;
        state.sitProgress = entity.prevSitProgress + (entity.sitProgress - entity.prevSitProgress) * partialTick;
        state.isStanding = entity.isStanding();
        state.isSitting = entity.isSitting();
        state.isHoneyed = entity.isHoneyed();
        state.isSnowy = entity.isSnowy();
        state.isFreddy = entity.isFreddy();
        state.aprilFoolsFlag = entity.getAprilFoolsFlag();
        state.animationTick = entity.getAnimationTick();
        state.isBaby = entity.isBaby();
    }

    public ResourceLocation getTextureLocation(GrizzlyBearRenderState state) {
        return state.isFreddy ? TEXTURE_FREDDY : TEXTURE;
    }

    class LayerSnow extends RenderLayer<GrizzlyBearRenderState, ModelGrizzlyBear> {

        public LayerSnow() {
            super(RenderGrizzlyBear.this);
        }

        @Override
        public void submit(PoseStack matrixStackIn, net.minecraft.client.renderer.SubmitNodeCollector submitNodeCollector, int packedLightIn,
                GrizzlyBearRenderState state, float limbSwing, float limbSwingAmount) {
            if (state.isSnowy) {
                submitNodeCollector.order(1).submitModel(
                    this.getParentModel(), state, matrixStackIn, RenderType.entityCutoutNoCull(TEXTURE_SNOWY), 
                    packedLightIn, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor, null
                );
            }
        }
    }

    class LayerFreddyEyes extends RenderLayer<GrizzlyBearRenderState, ModelGrizzlyBear> {

        public LayerFreddyEyes() {
            super(RenderGrizzlyBear.this);
        }

        @Override
        public void submit(PoseStack matrixStackIn, net.minecraft.client.renderer.SubmitNodeCollector submitNodeCollector, int packedLightIn,
                GrizzlyBearRenderState state, float limbSwing, float limbSwingAmount) {
            if (state.aprilFoolsFlag == 4 && state.ageInTicks % 6 <= 2) {
                submitNodeCollector.order(1).submitModel(
                    this.getParentModel(), state, matrixStackIn, AMRenderTypes.getEyesNoFog(TEXTURE_FREDDY_EYES), 
                    packedLightIn, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 
                    AMColorUtil.packColor(1.0F, 1.0F, 1.0F, 0.1F), null, state.outlineColor, null
                );
            }
        }
    }
}
