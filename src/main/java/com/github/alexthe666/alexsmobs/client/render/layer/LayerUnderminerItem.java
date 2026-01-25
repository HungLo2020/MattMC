package com.github.alexthe666.alexsmobs.client.render.layer;

import com.github.alexthe666.alexsmobs.client.model.ModelUnderminerDwarf;
import com.github.alexthe666.alexsmobs.client.render.RenderUnderminer;
import com.github.alexthe666.alexsmobs.client.render.UnderminerRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/**
 * Render layer for underminer held items
 * Note: In the render state architecture, this would require extending the render state
 * Simplified for now as a stub for compatibility
 */
public class LayerUnderminerItem extends RenderLayer<UnderminerRenderState, ModelUnderminerDwarf> {

    public LayerUnderminerItem(RenderUnderminer render) {
        super(render);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, UnderminerRenderState renderState, float f, float g) {
        // Would need item state in UnderminerRenderState to implement properly
        // This layer is a stub for compatibility
    }
}
