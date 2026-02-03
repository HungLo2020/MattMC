package net.alexsmobs.client.render.layer;

import net.alexsmobs.client.model.ModelElephant;
import net.alexsmobs.client.render.ElephantRenderState;
import net.alexsmobs.client.render.RenderElephant;
import net.blaze3d.vertex.PoseStack;
import net.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;

public class LayerElephantItem extends RenderLayer<ElephantRenderState, ModelElephant> {

    public LayerElephantItem(RenderElephant render) {
        super(render);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, ElephantRenderState state, float f, float g) {
        if (!state.mainHandItem.isEmpty()) {
            poseStack.pushPose();
            if(state.isBaby){
                poseStack.scale(0.35F, 0.35F, 0.35F);
                poseStack.translate(0.0D, 2.8D, 0D);
            }
            poseStack.pushPose();
            translateToHand(poseStack);
            if(state.isBaby){
                poseStack.translate(0.0D, 0.2F, -0.22D);
            }
            poseStack.translate(-0.0, 1.0F, 0.15F);
            poseStack.mulPose(Axis.XP.rotationDegrees(180F));
            poseStack.scale(1.3F, 1.3F, 1.3F);
            state.mainHandItem.submit(poseStack, submitNodeCollector, i, OverlayTexture.NO_OVERLAY, state.outlineColor);
            poseStack.popPose();
            poseStack.popPose();
        }
    }

    protected void translateToHand(PoseStack matrixStack) {
        this.getParentModel().root.translateAndRotate(matrixStack);
        this.getParentModel().body.translateAndRotate(matrixStack);
        this.getParentModel().head.translateAndRotate(matrixStack);
        this.getParentModel().trunk1.translateAndRotate(matrixStack);
        this.getParentModel().trunk2.translateAndRotate(matrixStack);

    }
}
