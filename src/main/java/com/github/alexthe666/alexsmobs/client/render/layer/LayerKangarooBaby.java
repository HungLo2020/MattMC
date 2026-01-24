package com.github.alexthe666.alexsmobs.client.render.layer;

import com.github.alexthe666.alexsmobs.client.model.KangarooModel;
import com.github.alexthe666.alexsmobs.client.render.KangarooRenderer;
import com.github.alexthe666.alexsmobs.client.render.KangarooRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

public class LayerKangarooBaby extends RenderLayer<KangarooRenderState, KangarooModel> {

    public LayerKangarooBaby(KangarooRenderer render) {
        super(render);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, KangarooRenderState renderState, float bob, float yRot) {
        // This layer would need access to entity passengers which is not available in RenderState
        // TODO: Add passenger rendering support if needed
    }

    protected void translateToPouch(PoseStack matrixStack) {
        this.getParentModel().root.translateAndRotate(matrixStack);
        this.getParentModel().body.translateAndRotate(matrixStack);
    }
}
