package net.alexsmobs.client.render;

import net.alexsmobs.client.model.ModelSunbird;
import net.alexsmobs.entity.EntitySunbird;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderSunbird extends MobRenderer<EntitySunbird, SunbirdRenderState, ModelSunbird> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/sunbird.png");
    private static final ResourceLocation TEXTURE_GLOW = ResourceLocation.withDefaultNamespace("textures/entity/sunbird_glow.png");

    public RenderSunbird(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelSunbird(), 0.5F);
        // TODO: Add glow layer when render architecture is fully understood
        // this.addLayer(new LayerScorch(this));
    }

    @Override
    public SunbirdRenderState createRenderState() {
        return new SunbirdRenderState();
    }

    @Override
    public void extractRenderState(EntitySunbird entity, SunbirdRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.birdPitch = entity.prevBirdPitch + (entity.birdPitch - entity.prevBirdPitch) * partialTick;
        state.scorchProgress = entity.getScorchProgress(partialTick);
    }

    protected void scale(SunbirdRenderState state, PoseStack matrixStackIn) {
    }

    public ResourceLocation getTextureLocation(SunbirdRenderState state) {
        return TEXTURE;
    }

    // TODO: Implement glow layer when render architecture is fully understood
    /*
    static class LayerScorch extends RenderLayer<SunbirdRenderState, ModelSunbird> {

        public LayerScorch(RenderSunbird p_i50928_1_) {
            super(p_i50928_1_);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int light, SunbirdRenderState state, float f, float g) {
            VertexConsumer scorch = submitNodeCollector.buffer(AMRenderTypes.getEyesAlphaEnabled(TEXTURE_GLOW));
            float alpha = state.scorchProgress;
            this.getParentModel().renderToBuffer(poseStack, scorch, 240,
                    OverlayTexture.NO_OVERLAY, AMColorUtil.packColor(1.0F, 1.0F, 1.0F, alpha));
        }
    }
    */
}