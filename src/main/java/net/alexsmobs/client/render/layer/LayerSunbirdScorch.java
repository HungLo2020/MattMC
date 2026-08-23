package net.alexsmobs.client.render.layer;

import net.alexsmobs.client.model.ModelSunbird;
import net.alexsmobs.client.render.AMColorUtil;
import net.alexsmobs.client.render.RenderSunbird;
import net.alexsmobs.client.render.SunbirdRenderState;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Semantic emissive overlay for the sunbird's scorch animation. */
public final class LayerSunbirdScorch extends RenderLayer<SunbirdRenderState, ModelSunbird> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace(
        "textures/entity/sunbird_glow.png");

    public LayerSunbirdScorch(RenderSunbird renderer) {
        super(renderer);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                       SunbirdRenderState state, float f, float g) {
        if (state.scorchProgress <= 0.0F) return;
        int color = AMColorUtil.packColor(1.0F, 1.0F, 1.0F,
            Math.min(1.0F, Math.max(0.0F, state.scorchProgress)));
        collector.order(1).submitModelSemanticTexture(
            this.getParentModel(), state, poseStack, RenderType.eyes(TEXTURE),
            15728640, OverlayTexture.NO_OVERLAY, color, TEXTURE, state.outlineColor, null);
    }
}
