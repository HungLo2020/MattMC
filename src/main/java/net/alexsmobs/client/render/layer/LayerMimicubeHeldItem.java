package net.alexsmobs.client.render.layer;

import net.alexsmobs.client.model.ModelMimicube;
import net.alexsmobs.client.render.MimicubeRenderState;
import net.alexsmobs.client.render.RenderMimicube;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.math.Axis;

public class LayerMimicubeHeldItem extends RenderLayer<MimicubeRenderState, ModelMimicube> {

    public LayerMimicubeHeldItem(RenderMimicube render) {
        super(render);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, MimicubeRenderState renderState, float f, float g) {
        submitItem(poseStack, submitNodeCollector, packedLight, renderState.mainHandItem, -1.0F);
        submitItem(poseStack, submitNodeCollector, packedLight, renderState.offHandItem, 1.0F);
    }

    private void submitItem(PoseStack poseStack, SubmitNodeCollector collector, int light,
                            net.minecraft.client.renderer.item.ItemStackRenderState item, float side) {
        if (item.isEmpty()) return;
        poseStack.pushPose();
        this.getParentModel().root.translateAndRotate(poseStack);
        poseStack.translate(side * 0.65F, -0.8F, -0.65F);
        poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
        item.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }
}
