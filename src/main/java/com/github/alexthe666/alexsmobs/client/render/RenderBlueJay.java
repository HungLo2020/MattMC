package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelBlueJay;
import com.github.alexthe666.alexsmobs.client.render.state.BlueJayRenderState;
import com.github.alexthe666.alexsmobs.entity.EntityBlueJay;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

public class RenderBlueJay extends MobRenderer<EntityBlueJay, BlueJayRenderState, ModelBlueJay> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/blue_jay.png");
    private static final ResourceLocation TEXTURE_SHINY = ResourceLocation.withDefaultNamespace("textures/entity/blue_jay_shiny.png");

    public RenderBlueJay(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelBlueJay(), 0.2F);
        this.addLayer(new LayerShiny());
    }

    @Override
    public BlueJayRenderState createRenderState() {
        return new BlueJayRenderState();
    }

    @Override
    public void extractRenderState(EntityBlueJay entity, BlueJayRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.flyProgress = entity.prevFlyProgress + (entity.flyProgress - entity.prevFlyProgress) * partialTick;
        renderState.flapAmount = entity.prevFlapAmount + (entity.flapAmount - entity.prevFlapAmount) * partialTick;
        renderState.attackProgress = entity.prevAttackProgress + (entity.attackProgress - entity.prevAttackProgress) * partialTick;
        renderState.crestAmount = entity.prevCrestAmount + (entity.crestAmount - entity.prevCrestAmount) * partialTick;
        renderState.birdPitch = entity.prevBirdPitch + (entity.birdPitch - entity.prevBirdPitch) * partialTick;
        renderState.feedTime = entity.getFeedTime();
        renderState.singTime = entity.getSingTime();
        renderState.isYoung = entity.isBaby();
    }

    protected void scale(BlueJayRenderState renderState, PoseStack matrixStackIn) {
        matrixStackIn.scale(0.9F, 0.9F, 0.9F);
    }

    public ResourceLocation getTextureLocation(BlueJayRenderState renderState) {
        return TEXTURE;
    }

    class LayerShiny extends RenderLayer<BlueJayRenderState, ModelBlueJay> {

        public LayerShiny() {
            super(RenderBlueJay.this);
        }

        public void submit(PoseStack matrixStackIn, com.mojang.blaze3d.vertex.SubmitNodeCollector submitNodeCollector, int packedLightIn,
                BlueJayRenderState renderState, float limbSwing, float limbSwingAmount) {
            if (renderState.feedTime > 0) {
                float alpha = (float) (1F + Math.sin(renderState.ageInTicks * 0.3F)) * 0.1F + 0.8F;
                coloredCutoutModelCopyLayerRender(
                    this.getParentModel(),
                    TEXTURE_SHINY,
                    matrixStackIn,
                    submitNodeCollector,
                    packedLightIn,
                    renderState,
                    AMColorUtil.packColor(1.0F, 1.0F, 1.0F, alpha),
                    alpha
                );
            }
        }
    }
}
