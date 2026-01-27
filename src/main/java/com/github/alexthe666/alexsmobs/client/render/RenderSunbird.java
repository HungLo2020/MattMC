package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelSunbird;
import com.github.alexthe666.alexsmobs.entity.EntitySunbird;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class RenderSunbird extends MobRenderer<EntitySunbird, SunbirdRenderState, ModelSunbird> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/sunbird.png");
    private static final ResourceLocation TEXTURE_GLOW = ResourceLocation.withDefaultNamespace("textures/entity/sunbird_glow.png");

    public RenderSunbird(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelSunbird(), 0.5F);
        this.addLayer(new LayerScorch(this));
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

    private static void vertex(VertexConsumer p_114090_, Matrix4f p_114091_, Matrix3f p_114092_, int p_114093_,
            float p_114094_, float p_114095_, int p_114096_, int p_114097_) {
        org.joml.Vector3f normal = new org.joml.Vector3f(0.0F, 1.0F, 0.0F);
        normal.mul(p_114092_);
        org.joml.Vector4f pos = new org.joml.Vector4f(p_114094_, p_114095_, 0.0F, 1.0F);
        pos.mul(p_114091_);
        p_114090_.addVertex(pos.x, pos.y, pos.z).setColor(255, 255, 255, 100)
                .setUv((float) p_114096_, (float) p_114097_).setOverlay(OverlayTexture.NO_OVERLAY).setLight(p_114093_)
                .setNormal(normal.x, normal.y, normal.z);
    }

    @Override
    public void render(SunbirdRenderState state, PoseStack poseStack, MultiBufferSource buffer, int light) {
        super.render(state, poseStack, buffer, light);
        final float ageInTicks = state.ageInTicks;
        final float scale = (12.0F + (float) Math.sin(ageInTicks * 0.3F)) * state.scorchProgress;
        if (scale > 0.0F) {
            poseStack.pushPose();
            poseStack.translate(0, state.boundingBoxHeight * 0.5F, 0);
            poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            poseStack.pushPose();
            poseStack.mulPose(Axis.ZP.rotationDegrees(ageInTicks * 8F));
            poseStack.translate(-scale * 0.5F, -scale * 0.5F, 0);
            PoseStack.Pose posestack$pose = poseStack.last();
            Matrix4f matrix4f = posestack$pose.pose();
            Matrix3f matrix3f = posestack$pose.normal();
            VertexConsumer vertexconsumer = buffer.getBuffer(AMRenderTypes.getSunbirdShine());
            vertex(vertexconsumer, matrix4f, matrix3f, light, 0.0F, 0, 0, 1);
            vertex(vertexconsumer, matrix4f, matrix3f, light, scale, 0, 1, 1);
            vertex(vertexconsumer, matrix4f, matrix3f, light, scale, scale, 1, 0);
            vertex(vertexconsumer, matrix4f, matrix3f, light, 0.0F, scale, 0, 0);
            poseStack.popPose();
            poseStack.popPose();
        }
    }

    protected void scale(SunbirdRenderState state, PoseStack matrixStackIn) {
    }

    public ResourceLocation getTextureLocation(SunbirdRenderState state) {
        return TEXTURE;
    }

    static class LayerScorch extends RenderLayer<SunbirdRenderState, ModelSunbird> {

        public LayerScorch(RenderSunbird p_i50928_1_) {
            super(p_i50928_1_);
        }

        public void render(PoseStack matrixStackIn, MultiBufferSource bufferIn, int packedLightIn,
                SunbirdRenderState state, float limbSwing, float limbSwingAmount) {
            VertexConsumer scorch = bufferIn.getBuffer(AMRenderTypes.getEyesAlphaEnabled(TEXTURE_GLOW));
            float alpha = state.scorchProgress;
            this.getParentModel().renderToBuffer(matrixStackIn, scorch, 240,
                    OverlayTexture.NO_OVERLAY, AMColorUtil.packColor(1.0F, 1.0F, 1.0F, alpha));
        }
    }
}
