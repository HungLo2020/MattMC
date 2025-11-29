package net.matt.quantize.entities.render;

import net.matt.quantize.entities.models.ModelAlligatorSnappingTurtle;
import net.matt.quantize.entities.mobs.EntityAlligatorSnappingTurtle;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.matt.quantize.utils.ResourceIdentifier;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.matt.quantize.modules.entities.AMRenderTypes;

public class RenderAlligatorSnappingTurtle extends MobRenderer<EntityAlligatorSnappingTurtle, ModelAlligatorSnappingTurtle> {
    private static final ResourceLocation TEXTURE_MOSS = new ResourceIdentifier("textures/model/entity/alligator_snapping_turtle/alligator_snapping_turtle_moss.png");
    private static final ResourceLocation TEXTURE = new ResourceIdentifier("textures/model/entity/alligator_snapping_turtle/alligator_snapping_turtle.png");

    public RenderAlligatorSnappingTurtle(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelAlligatorSnappingTurtle(), 0.75F);
        this.addLayer(new AlligatorSnappingTurtleMossLayer(this));
    }

    protected void scale(EntityAlligatorSnappingTurtle entitylivingbaseIn, PoseStack matrixStackIn, float partialTickTime) {
        float d = entitylivingbaseIn.getTurtleScale() < 0.01F ? 1F : entitylivingbaseIn.getTurtleScale();
        matrixStackIn.scale(d, d, d);
    }

    public ResourceLocation getTextureLocation(EntityAlligatorSnappingTurtle entity) {
        return TEXTURE;
    }

    static class AlligatorSnappingTurtleMossLayer extends RenderLayer<EntityAlligatorSnappingTurtle, ModelAlligatorSnappingTurtle> {

        public AlligatorSnappingTurtleMossLayer(RenderAlligatorSnappingTurtle p_i50928_1_) {
            super(p_i50928_1_);
        }

        public void render(PoseStack matrixStackIn, MultiBufferSource bufferIn, int packedLightIn, EntityAlligatorSnappingTurtle entitylivingbaseIn, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
            if(entitylivingbaseIn.getMoss() > 0){
                float mossAlpha = 0.15F * Mth.clamp(entitylivingbaseIn.getMoss(), 0, 10);
                VertexConsumer mossbuffer = bufferIn.getBuffer(AMRenderTypes.entityTranslucent(TEXTURE_MOSS));
                this.getParentModel().renderToBuffer(matrixStackIn, mossbuffer, packedLightIn, LivingEntityRenderer.getOverlayCoords(entitylivingbaseIn, 0), 1.0F, 1.0F, 1.0F, Math.min(1.0F, mossAlpha));
            }
        }
    }
}
