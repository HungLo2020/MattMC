package com.github.alexthe666.alexsmobs.client.render.layer;

import com.github.alexthe666.alexsmobs.client.model.KangarooModel;
import com.github.alexthe666.alexsmobs.client.render.KangarooRenderer;
import com.github.alexthe666.alexsmobs.client.render.KangarooRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

public class LayerKangarooItem extends RenderLayer<KangarooRenderState, KangarooModel> {

    public LayerKangarooItem(KangarooRenderer render) {
        super(render);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, KangarooRenderState renderState, float bob, float yRot) {
        // This layer would need access to entity inventory which is not in RenderState
        // TODO: Add inventory items to KangarooRenderState if needed
    }

    protected void translateToHand(PoseStack matrixStack, boolean left) {
        this.getParentModel().root.translateAndRotate(matrixStack);
        this.getParentModel().body.translateAndRotate(matrixStack);
        this.getParentModel().chest.translateAndRotate(matrixStack);
        if(left){
            this.getParentModel().arm_left.translateAndRotate(matrixStack);
        }else{
            this.getParentModel().arm_right.translateAndRotate(matrixStack);
        }
    }
}
