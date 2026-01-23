package com.github.alexthe666.alexsmobs.client.render.layer;

import com.github.alexthe666.alexsmobs.client.model.ModelAnteater;
import com.github.alexthe666.alexsmobs.client.render.AnteaterRenderState;
import com.github.alexthe666.alexsmobs.client.render.RenderAnteater;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/**
 * Render layer for items on anteater tongue
 * Note: In the render state architecture, this functionality would require
 * adding item and ant state to AnteaterRenderState - simplified for now
 */
public class LayerAnteaterTongueItem extends RenderLayer<AnteaterRenderState, ModelAnteater> {

    public LayerAnteaterTongueItem(RenderAnteater render) {
        super(render);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, AnteaterRenderState renderState, float f, float g) {
        // Would need item state in AnteaterRenderState to implement properly
        // This layer is a stub for compatibility
    }
}
