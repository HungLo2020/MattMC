package net.alexsmobs.client.render.layer;

import net.alexsmobs.client.model.ModelMantisShrimp;
import net.alexsmobs.client.render.MantisShrimpRenderState;
import net.alexsmobs.client.render.RenderMantisShrimp;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.math.Axis;

public class LayerMantisShrimpItem extends RenderLayer<MantisShrimpRenderState, ModelMantisShrimp> {

    public LayerMantisShrimpItem(RenderMantisShrimp render) {
        super(render);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, MantisShrimpRenderState renderState, float bob, float yRot) {
        if (renderState.mainHandItem.isEmpty()) return;
        poseStack.pushPose();
        translateToHand(poseStack, renderState.isLeftHanded);
        poseStack.translate(0.0F, 0.15F, -0.45F);
        poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
        renderState.mainHandItem.submit(poseStack, submitNodeCollector, packedLight,
            OverlayTexture.NO_OVERLAY, renderState.outlineColor);
        poseStack.popPose();
    }

    protected void translateToHand(PoseStack matrixStack, boolean left) {
        this.getParentModel().root.translateAndRotate(matrixStack);
        this.getParentModel().body.translateAndRotate(matrixStack);
        this.getParentModel().head.translateAndRotate(matrixStack);
        if(left){
            this.getParentModel().arm_left.translateAndRotate(matrixStack);
            this.getParentModel().fist_left.translateAndRotate(matrixStack);
        }else{
            this.getParentModel().arm_right.translateAndRotate(matrixStack);
            this.getParentModel().fist_right.translateAndRotate(matrixStack);
        }
    }
}
