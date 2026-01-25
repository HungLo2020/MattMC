package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelPlatypus;
import com.github.alexthe666.alexsmobs.entity.EntityPlatypus;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

public class RenderPlatypus extends MobRenderer<EntityPlatypus, PlatypusRenderState, ModelPlatypus> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/platypus.png");
    private static final ResourceLocation TEXTURE_PERRY = ResourceLocation.withDefaultNamespace("textures/entity/platypus_perry.png");

    public RenderPlatypus(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelPlatypus(), 0.45F);
        this.addLayer(new FedoraLayer(this));
    }

    @Override
    public PlatypusRenderState createRenderState() {
        return new PlatypusRenderState();
    }

    @Override
    public void extractRenderState(EntityPlatypus entity, PlatypusRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.inWaterProgress = entity.prevInWaterProgress + (entity.inWaterProgress - entity.prevInWaterProgress) * partialTick;
        state.digProgress = entity.prevDigProgress + (entity.digProgress - entity.prevDigProgress) * partialTick;
        state.hasFedora = entity.hasFedora();
        state.isPerry = entity.isPerry();
    }

    protected void scale(PlatypusRenderState state, PoseStack matrixStackIn) {
         matrixStackIn.scale(0.9F, 0.9F, 0.9F);
    }

    public ResourceLocation getTextureLocation(PlatypusRenderState state) {
        return state.isPerry ? TEXTURE_PERRY : TEXTURE;
    }

    static class FedoraLayer extends RenderLayer<PlatypusRenderState, ModelPlatypus> {
        private final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/platypus_fedora.png");

        public FedoraLayer(RenderPlatypus renderGrizzlyBear) {
            super(renderGrizzlyBear);
        }

        public void render(PoseStack matrixStackIn, MultiBufferSource bufferIn, int packedLightIn, PlatypusRenderState state, float limbSwing, float limbSwingAmount) {
            if(state.hasFedora){
                VertexConsumer ivertexbuilder = bufferIn.getBuffer(RenderType.entityCutout(TEXTURE));
                this.getParentModel().renderToBuffer(matrixStackIn, ivertexbuilder, packedLightIn, LivingEntityRenderer.getOverlayCoords(state, 0.0F));
            }
        }
    }
}
