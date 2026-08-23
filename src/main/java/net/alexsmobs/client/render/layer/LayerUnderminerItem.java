package net.alexsmobs.client.render.layer;

import net.alexsmobs.client.model.ModelUnderminerWrapper;
import net.alexsmobs.client.render.RenderUnderminer;
import net.alexsmobs.client.render.UnderminerRenderState;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.math.Axis;

/**
 * Render layer for underminer held items using copied humanoid item state.
 */
public class LayerUnderminerItem extends RenderLayer<UnderminerRenderState, ModelUnderminerWrapper> {

    public LayerUnderminerItem(RenderUnderminer render) {
        super(render);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, UnderminerRenderState renderState, float f, float g) {
        if (renderState.getMainHandItem().isEmpty()) return;
        boolean left = renderState.mainArm == net.minecraft.world.entity.HumanoidArm.LEFT;
        poseStack.pushPose();
        this.getParentModel().translateToHand(poseStack, renderState, left);
        poseStack.translate(0.0F, 0.15F, -0.35F);
        poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
        renderState.getMainHandItem().submit(poseStack, submitNodeCollector, packedLight,
            OverlayTexture.NO_OVERLAY, renderState.outlineColor);
        poseStack.popPose();
    }
}
