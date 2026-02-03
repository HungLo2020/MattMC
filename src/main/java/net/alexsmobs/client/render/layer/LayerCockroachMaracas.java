package net.alexsmobs.client.render.layer;

import net.alexsmobs.client.model.ModelCockroach;
import net.alexsmobs.client.render.RenderCockroach;
import net.alexsmobs.client.render.state.CockroachRenderState;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

public class LayerCockroachMaracas extends RenderLayer<CockroachRenderState, ModelCockroach> {

    public LayerCockroachMaracas(RenderCockroach render, EntityRendererProvider.Context renderManagerIn) {
        super(render);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, CockroachRenderState renderState, float bob, float yRot) {
        // TODO: Implement maraca rendering using 1.21 architecture
        // For now, this is a stub to allow compilation
    }
}
