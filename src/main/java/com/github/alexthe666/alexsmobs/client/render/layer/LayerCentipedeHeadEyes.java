package com.github.alexthe666.alexsmobs.client.render.layer;

import com.github.alexthe666.alexsmobs.client.model.ModelCaveCentipede;
import com.github.alexthe666.alexsmobs.client.render.CentipedeHeadRenderState;
import com.github.alexthe666.alexsmobs.client.render.RenderCentipedeHead;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class LayerCentipedeHeadEyes extends RenderLayer<CentipedeHeadRenderState, ModelCaveCentipede> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/cave_centipede_eyes.png");

    public LayerCentipedeHeadEyes(RenderCentipedeHead render) {
        super(render);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, CentipedeHeadRenderState state, float f, float g) {
        submitNodeCollector.order(1)
            .submitModel(
                this.getParentModel(), state, poseStack, RenderType.eyes(TEXTURE), 15728640, OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor, null
            );
    }
}
