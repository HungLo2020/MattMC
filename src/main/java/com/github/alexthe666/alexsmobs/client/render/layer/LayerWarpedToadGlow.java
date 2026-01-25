package com.github.alexthe666.alexsmobs.client.render.layer;

import com.github.alexthe666.alexsmobs.client.model.ModelWarpedToad;
import com.github.alexthe666.alexsmobs.client.render.AMColorUtil;
import com.github.alexthe666.alexsmobs.client.render.AMRenderTypes;
import com.github.alexthe666.alexsmobs.client.render.RenderWarpedToad;
import com.github.alexthe666.alexsmobs.client.render.WarpedToadRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class LayerWarpedToadGlow extends RenderLayer<WarpedToadRenderState, ModelWarpedToad> {
    private static final ResourceLocation TEXTURE = ResourceLocation
            .withDefaultNamespace("textures/entity/warped_toad_glow.png");
    private static final ResourceLocation TEXTURE_BLINKING = ResourceLocation
            .withDefaultNamespace("textures/entity/warped_toad_glow_blink.png");

    public LayerWarpedToadGlow(RenderWarpedToad renderWarpedToad) {
        super(renderWarpedToad);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, WarpedToadRenderState state, float f, float g) {
        if (!state.isBased) {
            ResourceLocation texture = state.isBlinking ? TEXTURE_BLINKING : TEXTURE;
            RenderType renderType = AMRenderTypes.getEyesFlickering(texture, 0);
            final float alpha = 0.75F + (Mth.cos(state.ageInTicks * 0.2F) + 1F) * 0.125F;
            int color = AMColorUtil.packColor(1.0F, 1.0F, 1.0F, alpha);
            submitNodeCollector.order(1).submitModel(
                this.getParentModel(), state, poseStack, renderType, 240, OverlayTexture.NO_OVERLAY, color, null, state.outlineColor, null
            );
        }
    }
}
