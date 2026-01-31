package net.alexsmobs.client.render.layer;

import net.alexsmobs.client.model.ModelRaccoon;
import net.alexsmobs.client.render.RaccoonRenderState;
import net.alexsmobs.client.render.RenderRaccoon;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

public class LayerRaccoonItem extends RenderLayer<RaccoonRenderState, ModelRaccoon> {

    public LayerRaccoonItem(RenderRaccoon render) {
        super(render);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, RaccoonRenderState state, float bob, float yRot) {
        // Item rendering layer - simplified for now
        // TODO: Implement item rendering when render state has item data
        // In the new architecture, item rendering would require adding ItemStack to RaccoonRenderState
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
