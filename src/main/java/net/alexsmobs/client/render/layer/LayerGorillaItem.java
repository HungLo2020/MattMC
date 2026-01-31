package net.alexsmobs.client.render.layer;

import net.alexsmobs.client.model.ModelGorilla;
import net.alexsmobs.client.render.GorillaRenderState;
import net.alexsmobs.client.render.RenderGorilla;
import net.blaze3d.vertex.PoseStack;
import net.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;

public class LayerGorillaItem extends RenderLayer<GorillaRenderState, ModelGorilla> {

    public LayerGorillaItem(RenderGorilla render) {
        super(render);
    }

    @Override
    public void submit(PoseStack matrixStackIn, SubmitNodeCollector submitNodeCollector, int packedLight, GorillaRenderState state, float f, float g) {
        // Note: Halo rendering for "harambe" simplified for now
        
        if(!state.heldItem.isEmpty()) {
            matrixStackIn.pushPose();
            if(state.isBaby){
                matrixStackIn.scale(0.35F, 0.35F, 0.35F);
                matrixStackIn.translate(-0.1D, 2D, -1.15D);
                translateToHand(false, matrixStackIn);
                matrixStackIn.translate(-0.4F, 0.75F, -0.0F);
                matrixStackIn.scale(2.8F, 2.8F, 2.8F);
            }else{
                translateToHand(false, matrixStackIn);
                matrixStackIn.translate(-0.4F, 0.75F, -0.0F);
            }
            matrixStackIn.mulPose(Axis.YP.rotationDegrees(-2.5F));
            matrixStackIn.mulPose(Axis.XP.rotationDegrees(-90F));
            // Note: Cannot check if item is BlockItem from ItemStackRenderState
            state.heldItem.submit(matrixStackIn, submitNodeCollector, packedLight, OverlayTexture.NO_OVERLAY, state.outlineColor);
            matrixStackIn.popPose();
        }
    }

    protected void translateToHand(boolean left, PoseStack matrixStack) {
        this.getParentModel().root.translateAndRotate(matrixStack);
        this.getParentModel().body.translateAndRotate(matrixStack);
        this.getParentModel().chest.translateAndRotate(matrixStack);
        this.getParentModel().leftArm.translateAndRotate(matrixStack);
    }
}
