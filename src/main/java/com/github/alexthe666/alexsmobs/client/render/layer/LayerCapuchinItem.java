package com.github.alexthe666.alexsmobs.client.render.layer;

import com.github.alexthe666.alexsmobs.client.model.ModelCapuchinMonkey;
import com.github.alexthe666.alexsmobs.client.render.CapuchinMonkeyRenderState;
import com.github.alexthe666.alexsmobs.client.render.RenderCapuchinMonkey;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/**
 * Render layer for Capuchin Monkey held items (darts and thrown items)
 * Note: In the render state architecture, item rendering is handled differently
 */
public class LayerCapuchinItem extends RenderLayer<CapuchinMonkeyRenderState, ModelCapuchinMonkey> {

    public LayerCapuchinItem(RenderCapuchinMonkey render) {
        super(render);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, CapuchinMonkeyRenderState renderState, float f, float g) {
        // TODO: Implement item rendering in the new architecture
        // For now, this is a stub for compilation
    }
}
