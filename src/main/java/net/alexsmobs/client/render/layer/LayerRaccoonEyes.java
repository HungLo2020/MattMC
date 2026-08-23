package net.alexsmobs.client.render.layer;

import net.alexsmobs.client.model.ModelRaccoon;
import net.alexsmobs.client.render.RaccoonRenderState;
import net.alexsmobs.client.render.RenderRaccoon;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class LayerRaccoonEyes extends RenderLayer<RaccoonRenderState, ModelRaccoon> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/raccoon_eyes.png");

    public LayerRaccoonEyes(RenderRaccoon render) {
        super(render);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, RaccoonRenderState state, float bob, float yRot) {
        if (!state.isRigby) {
            submitNodeCollector.order(1).submitModelSemanticTexture(
                this.getParentModel(), state, poseStack, RenderType.eyes(TEXTURE), 
                packedLight, OverlayTexture.NO_OVERLAY, -1, TEXTURE, state.outlineColor, null
            );
        }
    }
}
