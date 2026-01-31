package net.alexsmobs.client.render.layer;

import net.alexsmobs.client.model.ModelCapuchinMonkey;
import net.alexsmobs.client.render.CapuchinMonkeyRenderState;
import net.alexsmobs.client.render.RenderCapuchinMonkey;
import net.blaze3d.vertex.PoseStack;
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
