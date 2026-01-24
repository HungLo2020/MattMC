package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelMoose;
import com.github.alexthe666.alexsmobs.entity.EntityMoose;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

public class RenderMoose extends MobRenderer<EntityMoose, MooseRenderState, ModelMoose> {
    private static final ResourceLocation TEXTURE_ANTLERED = ResourceLocation.withDefaultNamespace("textures/entity/moose_antlered.png");
    private static final ResourceLocation TEXTURE_SNOWY_ANTLERED = ResourceLocation.withDefaultNamespace("textures/entity/moose_snowy_antlered.png");
    private static final ResourceLocation TEXTURE_SNOWY = ResourceLocation.withDefaultNamespace("textures/entity/moose_snowy.png");
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/moose.png");

    public RenderMoose(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelMoose(), 0.8F);
        this.addLayer(new LayerSnow());
    }

    @Override
    public MooseRenderState createRenderState() {
        return new MooseRenderState();
    }

    @Override
    public void extractRenderState(EntityMoose entity, MooseRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.antlered = entity.isAntlered();
        state.jostling = entity.isJostling();
        state.snowy = entity.isSnowy();
        state.isBaby = entity.isBaby();
        state.jostleAngle = entity.prevJostleAngle + (entity.getJostleAngle() - entity.prevJostleAngle) * partialTick;
        state.prevJostleAngle = entity.prevJostleAngle;
        state.jostleProgress = entity.prevJostleProgress + (entity.jostleProgress - entity.prevJostleProgress) * partialTick;
        state.prevJostleProgress = entity.prevJostleProgress;
    }

    public ResourceLocation getTextureLocation(MooseRenderState state) {
        return state.antlered && !state.isBaby ? TEXTURE_ANTLERED : TEXTURE;
    }


    class LayerSnow extends RenderLayer<MooseRenderState, ModelMoose> {

        public LayerSnow() {
            super(RenderMoose.this);
        }

        public void render(PoseStack matrixStackIn, MultiBufferSource bufferIn, int packedLightIn, MooseRenderState state, float limbSwing, float limbSwingAmount) {
            if (state.snowy) {
                VertexConsumer ivertexbuilder = bufferIn.getBuffer(RenderType.entityCutoutNoCull(state.antlered && !state.isBaby ? TEXTURE_SNOWY_ANTLERED : TEXTURE_SNOWY));
                this.getParentModel().renderToBuffer(matrixStackIn, ivertexbuilder, packedLightIn, LivingEntityRenderer.getOverlayCoords(state, 0.0F));
            }
        }
    }
}
