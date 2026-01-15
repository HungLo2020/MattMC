package net.alexscaves.client.render.entity.layer;

import net.alexscaves.client.model.AtlatitanModel;
import net.alexscaves.client.render.entity.AtlatitanRenderState;
import net.alexscaves.client.render.entity.AtlatitanRenderer;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

public class AtlatitanRiderLayer extends RenderLayer<AtlatitanRenderState, AtlatitanModel> {

    public AtlatitanRiderLayer(AtlatitanRenderer render) {
        super(render);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLightIn, AtlatitanRenderState renderState, float f1, float f2) {
        // Rider rendering disabled for now - requires more complex 1.21 render state updates
    }
}
