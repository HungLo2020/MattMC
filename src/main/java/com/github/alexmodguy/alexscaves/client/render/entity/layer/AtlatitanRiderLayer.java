package com.github.alexmodguy.alexscaves.client.render.entity.layer;

import com.github.alexmodguy.alexscaves.client.model.AtlatitanModel;
import com.github.alexmodguy.alexscaves.client.render.entity.AtlatitanRenderState;
import com.github.alexmodguy.alexscaves.client.render.entity.AtlatitanRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

public class AtlatitanRiderLayer extends RenderLayer<AtlatitanRenderState, AtlatitanModel> {

    public AtlatitanRiderLayer(AtlatitanRenderer render) {
        super(render);
    }

    @Override
    public void submit(PoseStack poseStack, net.minecraft.client.renderer.entity.layers.SubmitNodeCollector collector, int packedLightIn, AtlatitanRenderState renderState, float f1, float f2) {
        // Rider rendering disabled for now - requires more complex 1.21 render state updates
    }
}
