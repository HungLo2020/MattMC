package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelSpectre;
import com.github.alexthe666.alexsmobs.client.render.state.SpectreRenderState;
import com.github.alexthe666.alexsmobs.entity.EntitySpectre;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public class RenderSpectre extends MobRenderer<EntitySpectre, SpectreRenderState, ModelSpectre> {
    private static final ResourceLocation TEXTURE_BONE = ResourceLocation
            .withDefaultNamespace("textures/entity/spectre_bone.png");
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/spectre.png");
    private static final ResourceLocation TEXTURE_EYES = ResourceLocation
            .withDefaultNamespace("textures/entity/spectre_glow.png");
    private static final ResourceLocation TEXTURE_LEAD = ResourceLocation
            .withDefaultNamespace("textures/entity/spectre_lead.png");

    public RenderSpectre(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelSpectre(), 0.5F);
        this.addLayer(new SpectreEyesLayer(this));
        this.addLayer(new SpectreMembraneLayer(this));
    }
    
    @Override
    public void extractRenderState(EntitySpectre entity, SpectreRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.birdPitch = entity.birdPitch;
        renderState.prevBirdPitch = entity.prevBirdPitch;
    }

    protected void scale(SpectreRenderState renderState, PoseStack matrixStackIn) {
        matrixStackIn.scale(1.3F, 1.3F, 1.3F);
    }

    protected int getBlockLightLevel(EntitySpectre entityIn, BlockPos partialTicks) {
        return 15;
    }

    public ResourceLocation getTextureLocation(SpectreRenderState renderState) {
        return TEXTURE_BONE;
    }

    public float getAlphaForRender(SpectreRenderState renderState) {
        return ((float) Math.sin(renderState.ageInTicks * 0.1F) + 1.5F) * 0.1F + 0.5F;
    }

    @Override
    public SpectreRenderState createRenderState() {
        return new SpectreRenderState();
    }

    static class SpectreEyesLayer extends EyesLayer<SpectreRenderState, ModelSpectre> {

        public SpectreEyesLayer(RenderSpectre p_i50928_1_) {
            super(p_i50928_1_);
        }

        public RenderType renderType() {
            return RenderType.eyes(TEXTURE_EYES);
        }
    }

    class SpectreMembraneLayer extends RenderLayer<SpectreRenderState, ModelSpectre> {

        public SpectreMembraneLayer(RenderSpectre p_i50928_1_) {
            super(p_i50928_1_);
        }

        public void render(PoseStack matrixStackIn, MultiBufferSource bufferIn, int packedLightIn,
                SpectreRenderState renderState, float limbSwing, float limbSwingAmount) {
            VertexConsumer lvt_11_1_ = bufferIn.getBuffer(this.getRenderType());
            this.getParentModel().renderToBuffer(matrixStackIn, lvt_11_1_, 15728640,
                    LivingEntityRenderer.getOverlayCoords(renderState, 0),
                    AMColorUtil.packColor(1.0F, 1.0F, 1.0F, getAlphaForRender(renderState)));
            if (renderState.isLeashed) {
                VertexConsumer lead = bufferIn.getBuffer(AMRenderTypes.entityCutoutNoCull(TEXTURE_LEAD));
                this.getParentModel().renderToBuffer(matrixStackIn, lead, 15728640,
                        LivingEntityRenderer.getOverlayCoords(renderState, 0), -1);
            }
        }

        public RenderType getRenderType() {
            return AMRenderTypes.getSpectreBones(TEXTURE);
        }
    }
}
