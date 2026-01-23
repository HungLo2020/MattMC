package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelFrilledShark;
import com.github.alexthe666.alexsmobs.client.render.state.FrilledSharkRenderState;
import com.github.alexthe666.alexsmobs.entity.EntityFrilledShark;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

import static net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY;

public class RenderFrilledShark extends MobRenderer<EntityFrilledShark, FrilledSharkRenderState, EntityModel<FrilledSharkRenderState>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/frilled_shark.png");
    private static final ResourceLocation TEXTURE_DEPRESSURIZED = ResourceLocation.withDefaultNamespace("textures/entity/frilled_shark_depressurized.png");
    private static final ResourceLocation TEXTURE_KAIJU = ResourceLocation.withDefaultNamespace("textures/entity/frilled_shark_kaiju.png");
    private static final ResourceLocation TEXTURE_KAIJU_DEPRESSURIZED = ResourceLocation.withDefaultNamespace("textures/entity/frilled_shark_kaiju_depressurized.png");
    private static final ResourceLocation TEXTURE_TEETH = ResourceLocation.withDefaultNamespace("textures/entity/frilled_shark_teeth.png");

    public RenderFrilledShark(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelFrilledShark(), 0.4F);
        this.addLayer(new TeethLayer(this));
    }

    @Override
    public FrilledSharkRenderState createRenderState() {
        return new FrilledSharkRenderState();
    }

    @Override
    public void extractRenderState(EntityFrilledShark entity, FrilledSharkRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.onLandProgress = entity.onLandProgress;
        renderState.prevOnLandProgress = entity.prevOnLandProgress;
        renderState.isDepressurized = entity.isDepressurized();
        renderState.isKaiju = entity.isKaiju();
        renderState.currentAnimation = entity.getAnimation();
        renderState.animationTick = entity.getAnimationTick();
    }

    protected void scale(EntityFrilledShark entitylivingbaseIn, PoseStack matrixStackIn, float partialTickTime) {
        matrixStackIn.scale(0.85F, 0.85F, 0.85F);
    }

    public ResourceLocation getTextureLocation(FrilledSharkRenderState renderState) {
        return renderState.isKaiju ? (renderState.isDepressurized ? TEXTURE_KAIJU_DEPRESSURIZED : TEXTURE_KAIJU) : (renderState.isDepressurized ? TEXTURE_DEPRESSURIZED : TEXTURE);
    }

    static class TeethLayer extends RenderLayer<FrilledSharkRenderState, EntityModel<FrilledSharkRenderState>> {

        public TeethLayer(RenderFrilledShark render) {
            super(render);
        }

        public void render(PoseStack matrixStackIn, MultiBufferSource buffer, int packedLightIn, FrilledSharkRenderState renderState, float limbSwing, float limbSwingAmount) {
            VertexConsumer glintBuilder = buffer.getBuffer(AMRenderTypes.getEyesFlickering(TEXTURE_TEETH, 240));
            this.getParentModel().renderToBuffer(matrixStackIn, glintBuilder, 240, NO_OVERLAY);
        }
    }
}
