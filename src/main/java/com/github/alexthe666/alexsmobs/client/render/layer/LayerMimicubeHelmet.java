package com.github.alexthe666.alexsmobs.client.render.layer;

import com.github.alexthe666.alexsmobs.client.model.ModelMimicube;
import com.github.alexthe666.alexsmobs.client.render.MimicubeRenderState;
import com.github.alexthe666.alexsmobs.client.render.RenderMimicube;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

public class LayerMimicubeHelmet extends RenderLayer<MimicubeRenderState, ModelMimicube> {

    public LayerMimicubeHelmet(RenderMimicube render, EntityRendererProvider.Context renderManagerIn) {
        super(render);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, MimicubeRenderState renderState, float f, float g) {
        // TODO: Implement helmet rendering in the new architecture
        // For now, this is a stub for compilation
    }
}
