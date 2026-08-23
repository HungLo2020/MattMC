package net.alexsmobs.client.render.layer;

import net.alexsmobs.client.model.ModelAnteater;
import net.alexsmobs.client.render.AnteaterRenderState;
import net.alexsmobs.client.render.RenderAnteater;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.math.Axis;

/**
 * Render layer for copied item state held on the anteater tongue.
 */
public class LayerAnteaterTongueItem extends RenderLayer<AnteaterRenderState, ModelAnteater> {

    public LayerAnteaterTongueItem(RenderAnteater render) {
        super(render);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, AnteaterRenderState renderState, float f, float g) {
        if (renderState.tongueItem.isEmpty()) return;
        poseStack.pushPose();
        this.getParentModel().root.translateAndRotate(poseStack);
        this.getParentModel().body.translateAndRotate(poseStack);
        this.getParentModel().head.translateAndRotate(poseStack);
        this.getParentModel().tongue1.translateAndRotate(poseStack);
        this.getParentModel().tongue2.translateAndRotate(poseStack);
        poseStack.translate(0.0F, 0.0F, -0.25F);
        poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
        renderState.tongueItem.submit(poseStack, submitNodeCollector, packedLight,
            OverlayTexture.NO_OVERLAY, renderState.outlineColor);
        poseStack.popPose();
    }
}
