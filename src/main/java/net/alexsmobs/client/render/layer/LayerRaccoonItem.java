package net.alexsmobs.client.render.layer;

import net.alexsmobs.client.model.ModelRaccoon;
import net.alexsmobs.client.render.RaccoonRenderState;
import net.alexsmobs.client.render.RenderRaccoon;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.math.Axis;

public class LayerRaccoonItem extends RenderLayer<RaccoonRenderState, ModelRaccoon> {

    public LayerRaccoonItem(RenderRaccoon render) {
        super(render);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, RaccoonRenderState state, float bob, float yRot) {
        if (state.mainHandItem.isEmpty()) return;
        poseStack.pushPose();
        translateToHand(true, poseStack);
        poseStack.translate(-0.05F, 0.2F, -0.35F);
        poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
        state.mainHandItem.submit(poseStack, submitNodeCollector, packedLight,
                OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
    }

    protected void translateToHand(boolean inHand, PoseStack matrixStack) {
        if(inHand){
            this.getParentModel().root.translateAndRotate(matrixStack);
            this.getParentModel().body.translateAndRotate(matrixStack);
            this.getParentModel().arm_right.translateAndRotate(matrixStack);
        }else{
            this.getParentModel().root.translateAndRotate(matrixStack);
            this.getParentModel().body.translateAndRotate(matrixStack);
            this.getParentModel().head.translateAndRotate(matrixStack);
        }
    }
}
