package net.alexsmobs.client.render.layer;

import net.alexsmobs.client.model.ModelCrow;
import net.alexsmobs.client.render.RenderCrow;
import net.alexsmobs.client.render.state.CrowRenderState;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.math.Axis;

public class LayerCrowItem extends RenderLayer<CrowRenderState, ModelCrow> {

    public LayerCrowItem(RenderCrow render) {
        super(render);
    }

    public void submit(PoseStack matrixStackIn, net.minecraft.client.renderer.SubmitNodeCollector submitNodeCollector, int packedLightIn,
            CrowRenderState renderState, float limbSwing, float limbSwingAmount) {
        if (renderState.heldItem.isEmpty()) return;
        matrixStackIn.pushPose();
        this.getParentModel().root.translateAndRotate(matrixStackIn);
        this.getParentModel().body.translateAndRotate(matrixStackIn);
        this.getParentModel().head.translateAndRotate(matrixStackIn);
        this.getParentModel().beak.translateAndRotate(matrixStackIn);
        matrixStackIn.translate(0.0F, 0.0F, -0.25F);
        matrixStackIn.mulPose(Axis.XP.rotationDegrees(180.0F));
        renderState.heldItem.submit(matrixStackIn, submitNodeCollector, packedLightIn,
            OverlayTexture.NO_OVERLAY, renderState.outlineColor);
        matrixStackIn.popPose();
    }
}
