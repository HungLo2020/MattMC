package net.alexsmobs.client.render.layer;

import net.alexsmobs.client.model.ModelMimicube;
import net.alexsmobs.client.render.MimicubeRenderState;
import net.alexsmobs.client.render.RenderMimicube;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class LayerMimicubeTexture extends RenderLayer<MimicubeRenderState, ModelMimicube> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/mimicube_outer.png");

    public LayerMimicubeTexture(RenderMimicube render) {
        super(render);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, MimicubeRenderState renderState, float f, float g) {
        submitNodeCollector.order(1).submitModelSemanticTexture(
            this.getParentModel(), renderState, poseStack,
            RenderType.entityTranslucent(TEXTURE), packedLight,
            OverlayTexture.NO_OVERLAY, 0xFFFFFFFF, TEXTURE,
            renderState.outlineColor, null
        );
    }
}
