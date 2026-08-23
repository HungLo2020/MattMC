package net.alexsmobs.client.render.layer;

import net.alexsmobs.client.model.ModelTiger;
import net.alexsmobs.client.render.AMRenderTypes;
import net.alexsmobs.client.render.RenderTiger;
import net.alexsmobs.client.render.TigerRenderState;
import net.blaze3d.vertex.PoseStack;
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
                .submitModelSemanticTexture(
                    this.getParentModel(),
                    state,
                    matrixStackIn,
                    AMRenderTypes.eyes(texture),
                    packedLightIn,
                    OverlayTexture.NO_OVERLAY,
                    -1,
                    texture,
                    state.outlineColor,
                    null
                );
        }
    }
}
