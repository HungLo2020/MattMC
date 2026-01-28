package com.github.alexthe666.alexsmobs.client.render.layer;

import com.github.alexthe666.alexsmobs.client.model.ModelTiger;
import com.github.alexthe666.alexsmobs.client.render.AMRenderTypes;
import com.github.alexthe666.alexsmobs.client.render.RenderTiger;
import com.github.alexthe666.alexsmobs.client.render.TigerRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class LayerTigerEyes extends RenderLayer<TigerRenderState, ModelTiger> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/tiger/tiger_eyes.png");
    private static final ResourceLocation TEXTURE_WHITE = ResourceLocation.withDefaultNamespace("textures/entity/tiger/tiger_white_eyes.png");
    private static final ResourceLocation TEXTURE_ANGRY = ResourceLocation.withDefaultNamespace("textures/entity/tiger/tiger_angry_eyes.png");

    public LayerTigerEyes(RenderTiger render) {
        super(render);
    }

    @Override
    public void submit(PoseStack matrixStackIn, net.minecraft.client.renderer.SubmitNodeCollector submitNodeCollector, int packedLightIn, TigerRenderState state, float f, float g) {
        if (!state.isSleeping) {
            ResourceLocation texture = state.remainingPersistentAngerTime > 0 ? TEXTURE_ANGRY : (state.isWhite ? TEXTURE_WHITE : TEXTURE);
            submitNodeCollector.order(1)
                .submitModel(
                    this.getParentModel(),
                    state,
                    matrixStackIn,
                    AMRenderTypes.eyes(texture),
                    packedLightIn,
                    OverlayTexture.NO_OVERLAY,
                    -1,
                    null,
                    state.outlineColor,
                    null
                );
        }
    }
}
