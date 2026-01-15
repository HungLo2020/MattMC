package net.alexscaves.client.render.entity.layer;

import net.alexscaves.client.model.TremorsaurusModel;
import net.alexscaves.client.render.entity.TremorsaurusRenderState;
import net.alexscaves.client.render.entity.TremorsaurusRenderer;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

public class TremorsaurusRiderLayer extends RenderLayer<TremorsaurusRenderState, TremorsaurusModel> {

    public TremorsaurusRiderLayer(TremorsaurusRenderer render) {
        super(render);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight, TremorsaurusRenderState renderState, float p_225628_5_, float p_225628_6_) {
        // Rider rendering simplified - skip for now as it requires entity access
        // This would need to be refactored to use render state properly
    }
}
