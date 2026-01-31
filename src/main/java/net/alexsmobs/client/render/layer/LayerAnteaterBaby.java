package net.alexsmobs.client.render.layer;

import net.alexsmobs.client.model.ModelAnteater;
import net.alexsmobs.client.render.AnteaterRenderState;
import net.alexsmobs.client.render.RenderAnteater;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/**
 * Render layer for baby anteaters riding parents
 * Note: In the render state architecture, this functionality is limited
 * Baby rendering is handled in the model's renderToBuffer method
 */
public class LayerAnteaterBaby extends RenderLayer<AnteaterRenderState, ModelAnteater> {

    public LayerAnteaterBaby(RenderAnteater render) {
        super(render);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, AnteaterRenderState renderState, float f, float g) {
        // Baby rendering is handled in the model's renderToBuffer method
        // This layer is a stub for compatibility
    }
}
