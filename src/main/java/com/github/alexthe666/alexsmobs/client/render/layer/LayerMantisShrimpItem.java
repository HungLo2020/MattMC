package com.github.alexthe666.alexsmobs.client.render.layer;

import com.github.alexthe666.alexsmobs.client.model.ModelMantisShrimp;
import com.github.alexthe666.alexsmobs.client.render.MantisShrimpRenderState;
import com.github.alexthe666.alexsmobs.client.render.RenderMantisShrimp;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

public class LayerMantisShrimpItem extends RenderLayer<MantisShrimpRenderState, ModelMantisShrimp> {

    public LayerMantisShrimpItem(RenderMantisShrimp render) {
        super(render);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, MantisShrimpRenderState renderState, float bob, float yRot) {
        // This layer would need access to entity inventory which is not fully supported in RenderState
        // TODO: Implement item rendering when render state has complete item data
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
