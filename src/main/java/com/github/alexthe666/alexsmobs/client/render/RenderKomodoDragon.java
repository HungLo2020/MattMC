package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelKomodoDragon;
import com.github.alexthe666.alexsmobs.entity.EntityKomodoDragon;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

public class RenderKomodoDragon extends MobRenderer<EntityKomodoDragon, KomodoDragonRenderState, ModelKomodoDragon> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/komodo_dragon.png");
    private static final ResourceLocation TEXTURE_SADDLE = ResourceLocation.withDefaultNamespace("textures/entity/komodo_dragon_saddle.png");
    private static final ResourceLocation TEXTURE_MAID = ResourceLocation.withDefaultNamespace("textures/entity/komodo_dragon_maid.png");

    public RenderKomodoDragon(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelKomodoDragon(0.0F), 0.6F);
        this.addLayer(new LayerSaddle(this));
    }

    @Override
    public KomodoDragonRenderState createRenderState() {
        return new KomodoDragonRenderState();
    }

    @Override
    public void extractRenderState(EntityKomodoDragon komodo, KomodoDragonRenderState renderState, float partialTick) {
        super.extractRenderState(komodo, renderState, partialTick);
        renderState.prevJostleAngle = komodo.prevJostleAngle;
        renderState.jostleAngle = komodo.getJostleAngle();
        renderState.prevJostleProgress = komodo.prevJostleProgress;
        renderState.jostleProgress = komodo.jostleProgress;
        renderState.prevSitProgress = komodo.prevSitProgress;
        renderState.sitProgress = komodo.sitProgress;
        renderState.isSaddled = komodo.isSaddled();
        renderState.isMaid = komodo.isMaid();
        renderState.isBaby = komodo.isBaby();
    }

    protected void scale(KomodoDragonRenderState renderState, PoseStack matrixStackIn) {
    }

    public ResourceLocation getTextureLocation(KomodoDragonRenderState renderState) {
        return TEXTURE;
    }

    static class LayerSaddle extends RenderLayer<KomodoDragonRenderState, ModelKomodoDragon> {

        private static final ModelKomodoDragon MAID_MODEL = new ModelKomodoDragon(0.3F);
        private static final ModelKomodoDragon SADDLE_MODEL = new ModelKomodoDragon(0.5F);

        public LayerSaddle(RenderKomodoDragon render) {
            super(render);
        }

        public void render(PoseStack matrixStackIn, MultiBufferSource bufferIn, int packedLightIn, KomodoDragonRenderState renderState, float netHeadYaw, float headPitch) {
            if(renderState.isMaid){
                VertexConsumer maid = bufferIn.getBuffer(AMRenderTypes.entityCutoutNoCull(TEXTURE_MAID));
                this.getParentModel().copyPropertiesTo(MAID_MODEL);
                MAID_MODEL.setupAnim(renderState);
                MAID_MODEL.renderToBuffer(matrixStackIn, maid, packedLightIn, LivingEntityRenderer.getWhiteOverlayProgress(renderState.hurtTime, renderState.deathTime), -1);
            }
            if(renderState.isSaddled){
                VertexConsumer saddle = bufferIn.getBuffer(AMRenderTypes.entityCutoutNoCull(TEXTURE_SADDLE));
                this.getParentModel().copyPropertiesTo(SADDLE_MODEL);
                SADDLE_MODEL.setupAnim(renderState);
                SADDLE_MODEL.renderToBuffer(matrixStackIn, saddle, packedLightIn, LivingEntityRenderer.getWhiteOverlayProgress(renderState.hurtTime, renderState.deathTime), -1);
            }
        }
    }
}