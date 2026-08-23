package net.alexsmobs.client.render.layer;

import net.alexsmobs.client.model.ModelGrizzlyBear;
import net.alexsmobs.client.render.GrizzlyBearRenderState;
import net.alexsmobs.client.render.RenderGrizzlyBear;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

public class LayerGrizzlyHoney extends RenderLayer<GrizzlyBearRenderState, ModelGrizzlyBear> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/grizzly_bear_honey.png");

    public LayerGrizzlyHoney(RenderGrizzlyBear renderGrizzlyBear) {
        super(renderGrizzlyBear);
    }

    @Override
    public void submit(PoseStack matrixStackIn, net.minecraft.client.renderer.SubmitNodeCollector submitNodeCollector, int packedLightIn, GrizzlyBearRenderState state, float limbSwing, float limbSwingAmount) {
        if(state.isHoneyed){
            submitNodeCollector.order(1).submitModelSemanticTexture(
                this.getParentModel(), state, matrixStackIn, RenderType.entityTranslucent(TEXTURE), 
                packedLightIn, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, -1, TEXTURE, state.outlineColor, null
            );
        }
    }
}
