package net.alexsmobs.client.render.layer;

import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;

public class LayerBasicGlow<T extends LivingEntityRenderState> extends RenderLayer<T, EntityModel<T>> {
    private final ResourceLocation texture;
    private final RenderType renderType;

    public LayerBasicGlow(RenderLayerParent<T, EntityModel<T>> renderer, ResourceLocation texture) {
        super(renderer);
        this.texture = texture;
        this.renderType = RenderType.eyes(texture);
    }

    public boolean shouldCombineTextures() {
        return true;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, T renderState, float f, float g) {
        submitNodeCollector.order(1).submitModelSemanticTexture(
            this.getParentModel(), renderState, poseStack, renderType, i, OverlayTexture.NO_OVERLAY, -1, texture, renderState.outlineColor, null
        );
    }

}
