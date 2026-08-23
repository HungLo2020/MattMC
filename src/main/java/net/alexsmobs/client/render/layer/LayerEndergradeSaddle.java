package net.alexsmobs.client.render.layer;

import net.alexsmobs.client.model.ModelEndergrade;
import net.alexsmobs.client.render.EndergadeRenderState;
import net.alexsmobs.client.render.RenderEndergrade;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
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
            submitNodeCollector.order(0).submitModelSemanticTexture(
                this.getParentModel(), state, poseStack, renderType, packedLight, overlay, -1, TEXTURE, state.outlineColor, null
            );
        }
    }
}
