package net.alexsmobs.client.render;

import net.alexsmobs.client.model.ModelRhinoceros;
import net.alexsmobs.entity.EntityRhinoceros;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class RenderRhinoceros extends MobRenderer<EntityRhinoceros, RhinocerosRenderState, ModelRhinoceros> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/rhinoceros.png");
    private static final ResourceLocation TEXTURE_ANGRY = ResourceLocation.withDefaultNamespace("textures/entity/rhinoceros_angry.png");
    private static final ResourceLocation TEXTURE_POTION = ResourceLocation.withDefaultNamespace("textures/entity/rhinoceros_potion.png");

    public RenderRhinoceros(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelRhinoceros(), 0.9F);
        this.addLayer(new PotionLayer(this));
    }

    @Override
    public RhinocerosRenderState createRenderState() {
        return new RhinocerosRenderState();
    }

    @Override
    public void extractRenderState(EntityRhinoceros entity, RhinocerosRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.animationTick = entity.getAnimationTick();
        // Map Animation objects to IDs for the render state
        if (entity.getAnimation() == EntityRhinoceros.ANIMATION_FLICK_EARS) {
            state.currentAnimationId = 0;
        } else if (entity.getAnimation() == EntityRhinoceros.ANIMATION_EAT_GRASS) {
            state.currentAnimationId = 1;
        } else if (entity.getAnimation() == EntityRhinoceros.ANIMATION_FLING) {
            state.currentAnimationId = 2;
        } else if (entity.getAnimation() == EntityRhinoceros.ANIMATION_SLASH) {
            state.currentAnimationId = 3;
        } else {
            state.currentAnimationId = -1;
        }
        state.isAngry = entity.isAngry();
        state.potionColor = entity.getPotionColor();
    }

    protected void scale(RhinocerosRenderState state, PoseStack matrixStackIn) {
        matrixStackIn.scale(1.1F, 1.1F, 1.1F);
    }

    public ResourceLocation getTextureLocation(RhinocerosRenderState state) {
        return state.isAngry ? TEXTURE_ANGRY : TEXTURE;
    }

    private static class PotionLayer extends RenderLayer<RhinocerosRenderState, ModelRhinoceros> {
        public PotionLayer(RenderRhinoceros parent) {
            super(parent);
        }

        @Override
        public void submit(PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector submitNodeCollector, int i, 
                           RhinocerosRenderState state, float f, float g) {
            int color = state.potionColor;
            if (color != -1 && !state.isInvisible) {
                float r = (float) (color >> 16 & 255) / 255.0F;
                float gb = (float) (color >> 8 & 255) / 255.0F;
                float b = (float) (color & 255) / 255.0F;
                submitNodeCollector.order(1)
                    .submitModel(this.getParentModel(), state, poseStack,
                        AMRenderTypes.entityCutoutNoCull(TEXTURE_POTION), i,
                        OverlayTexture.NO_OVERLAY, AMColorUtil.packColor(r, gb, b, 1.0F), null, state.outlineColor, null);
            }
        }
    }
}
