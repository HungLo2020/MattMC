package net.alexsmobs.client.render;

import net.alexsmobs.client.model.ModelAlligatorSnappingTurtle;
import net.alexsmobs.entity.EntityAlligatorSnappingTurtle;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class RenderAlligatorSnappingTurtle extends MobRenderer<EntityAlligatorSnappingTurtle, AlligatorSnappingTurtleRenderState, ModelAlligatorSnappingTurtle> {
    private static final ResourceLocation TEXTURE_MOSS = ResourceLocation.withDefaultNamespace("textures/entity/alligator_snapping_turtle_moss.png");
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/alligator_snapping_turtle.png");

    public RenderAlligatorSnappingTurtle(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelAlligatorSnappingTurtle(), 0.75F);
        this.addLayer(new AlligatorSnappingTurtleMossLayer(this));
    }

    @Override
    public AlligatorSnappingTurtleRenderState createRenderState() {
        return new AlligatorSnappingTurtleRenderState();
    }

    @Override
    public void extractRenderState(EntityAlligatorSnappingTurtle entity, AlligatorSnappingTurtleRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.openMouthProgress = entity.prevOpenMouthProgress + (entity.openMouthProgress - entity.prevOpenMouthProgress) * partialTick;
        renderState.attackProgress = entity.prevAttackProgress + (entity.attackProgress - entity.prevAttackProgress) * partialTick;
        renderState.mossLevel = entity.getMoss();
        renderState.turtleScale = entity.getTurtleScale() < 0.01F ? 1F : entity.getTurtleScale();
        renderState.isInWater = entity.isInWater();
    }

    @Override
    protected void scale(AlligatorSnappingTurtleRenderState renderState, PoseStack matrixStackIn) {
        matrixStackIn.scale(renderState.turtleScale, renderState.turtleScale, renderState.turtleScale);
    }

    public ResourceLocation getTextureLocation(AlligatorSnappingTurtleRenderState renderState) {
        return TEXTURE;
    }

    static class AlligatorSnappingTurtleMossLayer extends RenderLayer<AlligatorSnappingTurtleRenderState, ModelAlligatorSnappingTurtle> {

        public AlligatorSnappingTurtleMossLayer(RenderAlligatorSnappingTurtle renderer) {
            super(renderer);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight, AlligatorSnappingTurtleRenderState state, float limbSwing, float limbSwingAmount) {
            if (state.mossLevel > 0) {
                float mossAlpha = 0.15F * Mth.clamp(state.mossLevel, 0, 10);
                RenderType renderType = AMRenderTypes.entityTranslucent(TEXTURE_MOSS);
                int color = AMColorUtil.packColor(1.0F, 1.0F, 1.0F, Math.min(1.0F, mossAlpha));
                collector.order(1).submitModelSemanticTexture(this.getParentModel(), state, poseStack, renderType, packedLight, OverlayTexture.NO_OVERLAY, color, TEXTURE_MOSS, state.outlineColor, null);
            }
        }
    }
}
