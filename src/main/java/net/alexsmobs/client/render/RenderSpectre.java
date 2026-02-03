package net.alexsmobs.client.render;

import net.alexsmobs.client.model.ModelSpectre;
import net.alexsmobs.client.render.state.SpectreRenderState;
import net.alexsmobs.entity.EntitySpectre;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
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
        renderState.isLeashed = entity.isLeashed();
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

        @Override
        public void submit(PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector submitNodeCollector, int packedLight,
                SpectreRenderState renderState, float f, float g) {
            float alpha = getAlphaForRender(renderState);
            int color = AMColorUtil.packColor(1.0F, 1.0F, 1.0F, alpha);
            
            submitNodeCollector.order(1).submitModel(
                this.getParentModel(), renderState, poseStack, this.getRenderType(), 15728640,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, color, null, renderState.outlineColor, null
            );
            
            if (renderState.isLeashed) {
                submitNodeCollector.order(1).submitModel(
                    this.getParentModel(), renderState, poseStack, AMRenderTypes.entityCutoutNoCull(TEXTURE_LEAD), 15728640,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, -1, null, renderState.outlineColor, null
                );
            }
        }

        public RenderType getRenderType() {
            return AMRenderTypes.getSpectreBones(TEXTURE);
        }
    }
}
