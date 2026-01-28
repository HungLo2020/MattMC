package com.github.alexthe666.alexsmobs.client.render.layer;

import com.github.alexthe666.alexsmobs.client.model.ModelMimicube;
import com.github.alexthe666.alexsmobs.client.render.MimicubeRenderState;
import com.github.alexthe666.alexsmobs.client.render.RenderMimicube;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

public class LayerMimicubeHeldItem extends RenderLayer<MimicubeRenderState, ModelMimicube> {

    public LayerMimicubeHeldItem(RenderMimicube render) {
        super(render);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, MimicubeRenderState renderState, float f, float g) {
        // TODO: Implement held item rendering in the new architecture
        // For now, this is a stub for compilation
    }
}
