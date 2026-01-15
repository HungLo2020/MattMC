package com.github.alexmodguy.alexscaves.client.render.entity.layer;

import com.github.alexmodguy.alexscaves.client.model.RelicheirusModel;
import com.github.alexmodguy.alexscaves.client.render.entity.RelicheirusRenderState;
import com.github.alexmodguy.alexscaves.client.render.entity.RelicheirusRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

public class RelicheirusHeldTrilocarisLayer extends RenderLayer<RelicheirusRenderState, RelicheirusModel> {
    private final RelicheirusRenderer renderer;

    public RelicheirusHeldTrilocarisLayer(RelicheirusRenderer render) {
        super(render);
        this.renderer = render;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight, RelicheirusRenderState renderState, float p_225628_5_, float p_225628_6_) {
        // Held mob rendering simplified - skip for now as it requires entity access
        // This would need to be refactored to use render state properly
    }
}

