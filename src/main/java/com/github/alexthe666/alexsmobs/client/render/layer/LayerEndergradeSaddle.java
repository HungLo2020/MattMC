package com.github.alexthe666.alexsmobs.client.render.layer;

import com.github.alexthe666.alexsmobs.client.model.ModelEndergrade;
import com.github.alexthe666.alexsmobs.client.render.EndergadeRenderState;
import com.github.alexthe666.alexsmobs.client.render.RenderEndergrade;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class LayerEndergradeSaddle extends RenderLayer<EndergadeRenderState, ModelEndergrade> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/endergrade_saddle.png");
    private final RenderType renderType;

    public LayerEndergradeSaddle(RenderEndergrade renderEndergrade) {
        super(renderEndergrade);
        this.renderType = RenderType.entityCutout(TEXTURE);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, EndergadeRenderState state, float netHeadYaw, float headPitch) {
        if(state.isSaddled){
            int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
            submitNodeCollector.order(0).submitModel(
                this.getParentModel(), state, poseStack, renderType, packedLight, overlay, -1, null, state.outlineColor, null
            );
        }
    }
}
