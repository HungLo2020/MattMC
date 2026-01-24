package com.github.alexthe666.alexsmobs.client.render.layer;

import com.github.alexthe666.alexsmobs.client.model.ModelAnteater;
import com.github.alexthe666.alexsmobs.client.render.AnteaterRenderState;
import com.github.alexthe666.alexsmobs.client.render.RenderAnteater;
import com.mojang.blaze3d.vertex.PoseStack;
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
