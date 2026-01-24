package com.github.alexthe666.alexsmobs.client.render.layer;

import com.github.alexthe666.alexsmobs.client.model.ModelRaccoon;
import com.github.alexthe666.alexsmobs.client.render.RaccoonRenderState;
import com.github.alexthe666.alexsmobs.client.render.RenderRaccoon;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LightLayer;

public class LayerRaccoonEyes extends RenderLayer<RaccoonRenderState, ModelRaccoon> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/raccoon_eyes.png");

    public LayerRaccoonEyes(RenderRaccoon render) {
        super(render);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, RaccoonRenderState state, float bob, float yRot) {
        if (!state.isRigby) {
            submitNodeCollector.order(1).submitModel(
                this.getParentModel(), state, poseStack, RenderType.eyes(TEXTURE), 
                packedLight, OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor, null
            );
        }
    }
}
