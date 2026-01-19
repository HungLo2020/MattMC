package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelCombJelly;
import com.github.alexthe666.alexsmobs.client.render.state.CombJellyRenderState;
import com.github.alexthe666.alexsmobs.entity.EntityCombJelly;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

public class RenderCombJelly extends MobRenderer<EntityCombJelly, CombJellyRenderState, EntityModel<CombJellyRenderState>> {
    private static final ResourceLocation TEXTURE_0 = ResourceLocation.withDefaultNamespace("textures/entity/comb_jelly_blue.png");
    private static final ResourceLocation TEXTURE_1 = ResourceLocation.withDefaultNamespace("textures/entity/comb_jelly_green.png");
    private static final ResourceLocation TEXTURE_2 = ResourceLocation.withDefaultNamespace("textures/entity/comb_jelly_red.png");
    private static final ResourceLocation TEXTURE_OVERLAY = ResourceLocation.withDefaultNamespace("textures/entity/comb_jelly_overlay.png");
    private static final ModelCombJelly STRIPES_MODEL = new ModelCombJelly(0.05F);
    private float jellyScale = 1.0F;
    
    public RenderCombJelly(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelCombJelly(0.0F), 0.3F);
        this.addLayer(new RainbowLayer(this));
    }

    @Override
    public CombJellyRenderState createRenderState() {
        return new CombJellyRenderState();
    }

    @Override
    public void extractRenderState(EntityCombJelly entity, CombJellyRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.variant = entity.getVariant();
        renderState.jellyScale = entity.getJellyScale();
        renderState.jellyPitch = entity.getJellyPitch();
        renderState.prevJellyPitch = entity.prevjellyPitch;
        renderState.onLandProgress = entity.onLandProgress;
        renderState.prevOnLandProgress = entity.prevOnLandProgress;
        this.jellyScale = entity.getJellyScale();
    }

    @Override
    protected void scale(CombJellyRenderState renderState, PoseStack matrixStackIn) {
        matrixStackIn.scale(jellyScale, jellyScale, jellyScale);
    }

    @Nullable
    @Override
    protected RenderType getRenderType(CombJellyRenderState renderState, ResourceLocation texture, boolean normal, boolean invis) {
        if (invis) {
            return RenderType.itemEntityTranslucentCull(texture);
        } else if (normal) {
            return RenderType.entityTranslucent(texture);
        } else {
            return null;
        }
    }

    @Override
    public ResourceLocation getTextureLocation(CombJellyRenderState renderState) {
        return renderState.variant == 0 ? TEXTURE_0 : renderState.variant == 1 ? TEXTURE_1 : TEXTURE_2;
    }

    static class RainbowLayer extends RenderLayer<CombJellyRenderState, EntityModel<CombJellyRenderState>> {

        public RainbowLayer(RenderCombJelly render) {
            super(render);
        }

        @Override
        public void render(PoseStack matrixStackIn, MultiBufferSource bufferIn, int packedLightIn, CombJellyRenderState renderState, float p_117353_, float p_117354_) {
            VertexConsumer rainbow = AMRenderTypes.createMergedVertexConsumer(bufferIn.getBuffer(AMRenderTypes.COMBJELLY_RAINBOW_GLINT), bufferIn.getBuffer(RenderType.entityCutoutNoCull(TEXTURE_OVERLAY)));
            STRIPES_MODEL.setupAnim(renderState);
            STRIPES_MODEL.renderToBuffer(matrixStackIn, rainbow, packedLightIn, OverlayTexture.NO_OVERLAY);
        }
    }
}
