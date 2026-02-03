package net.alexsmobs.client.render.layer;

import net.alexsmobs.client.model.ModelCrow;
import net.alexsmobs.client.render.RenderCrow;
import net.alexsmobs.client.render.state.CrowRenderState;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

public class LayerCrowItem extends RenderLayer<CrowRenderState, ModelCrow> {

    public LayerCrowItem(RenderCrow render) {
        super(render);
    }

    public void submit(PoseStack matrixStackIn, net.minecraft.client.renderer.SubmitNodeCollector submitNodeCollector, int packedLightIn,
            CrowRenderState renderState, float limbSwing, float limbSwingAmount) {
        // Item rendering layer - simplified for now
        // TODO: Implement item rendering when render state has item data
    }
}
