package net.alexsmobs.client.render.layer;

import net.alexsmobs.client.model.ModelMimicube;
import net.alexsmobs.client.render.MimicubeRenderState;
import net.alexsmobs.client.render.RenderMimicube;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.math.Axis;

public class LayerMimicubeHelmet extends RenderLayer<MimicubeRenderState, ModelMimicube> {

    public LayerMimicubeHelmet(RenderMimicube render, EntityRendererProvider.Context renderManagerIn) {
        super(render);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, MimicubeRenderState renderState, float f, float g) {
        if (renderState.headItem.isEmpty()) return;
        poseStack.pushPose();
        this.getParentModel().root.translateAndRotate(poseStack);
        poseStack.translate(0.0F, -1.0F, 0.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
        renderState.headItem.submit(poseStack, submitNodeCollector, packedLight,
            OverlayTexture.NO_OVERLAY, renderState.outlineColor);
        poseStack.popPose();
    }
}
