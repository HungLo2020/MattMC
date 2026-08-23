package net.alexsmobs.client.render;

import net.alexsmobs.client.model.ModelRaccoon;
import net.alexsmobs.client.render.layer.LayerRaccoonEyes;
import net.alexsmobs.client.render.layer.LayerRaccoonItem;
import net.alexsmobs.entity.EntityRaccoon;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;

public class RenderRaccoon extends MobRenderer<EntityRaccoon, RaccoonRenderState, ModelRaccoon> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/raccoon.png");
    private static final ResourceLocation TEXTURE_RIGBY = ResourceLocation
            .withDefaultNamespace("textures/entity/raccoon_rigby.png");
    private static final ResourceLocation TEXTURE_BANDANA = ResourceLocation
            .withDefaultNamespace("textures/entity/raccoon_bandana.png");

    public RenderRaccoon(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelRaccoon(), 0.4F);
        this.addLayer(new LayerRaccoonEyes(this));
        this.addLayer(new LayerRaccoonItem(this));
        this.addLayer(new BandanaLayer(this));
    }

    @Override
    public RaccoonRenderState createRenderState() {
        return new RaccoonRenderState();
    }

    @Override
    public void extractRenderState(EntityRaccoon entity, RaccoonRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.begProgress = entity.prevBegProgress + (entity.begProgress - entity.prevBegProgress) * partialTick;
        state.standProgress = entity.prevStandProgress + (entity.standProgress - entity.prevStandProgress) * partialTick;
        state.sitProgress = entity.prevSitProgress + (entity.sitProgress - entity.prevSitProgress) * partialTick;
        state.washProgress = entity.prevWashProgress + (entity.washProgress - entity.prevWashProgress) * partialTick;
        state.isRigby = entity.isRigby();
        state.hasColor = entity.getColor() != null;
        state.color = entity.getColor();
        state.tickCount = entity.tickCount;
        state.customName = entity.hasCustomName() ? entity.getName().getString() : null;
        state.animationTick = entity.getAnimationTick();
        state.currentAnimation = entity.getAnimation();
        state.id = entity.getId();
        this.itemModelResolver.updateForLiving(state.mainHandItem, entity.getMainHandItem(),
                net.minecraft.world.item.ItemDisplayContext.GROUND, entity);
    }

    protected void scale(RaccoonRenderState state, PoseStack matrixStackIn) {
        matrixStackIn.scale(0.75F, 0.75F, 0.75F);
    }

    public ResourceLocation getTextureLocation(RaccoonRenderState state) {
        return state.isRigby ? TEXTURE_RIGBY : TEXTURE;
    }

    private static class BandanaLayer extends RenderLayer<RaccoonRenderState, ModelRaccoon> {
        public BandanaLayer(RenderRaccoon renderRaccoon) {
            super(renderRaccoon);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, RaccoonRenderState state, float bob, float yRot) {
            if (state.hasColor && !state.isInvisibleToPlayer) {
                float lvt_11_2_;
                float lvt_12_2_;
                float lvt_13_2_;
                if (state.customName != null && "jeb_".equals(state.customName)) {
                    int lvt_15_1_ = state.tickCount / 25 + state.id;
                    int lvt_16_1_ = DyeColor.values().length;
                    int lvt_17_1_ = lvt_15_1_ % lvt_16_1_;
                    int lvt_18_1_ = (lvt_15_1_ + 1) % lvt_16_1_;
                    float lvt_19_1_ = ((float) (state.tickCount % 25) + bob) / 25.0F;
                    int color1 = DyeColor.byId(lvt_17_1_).getTextureDiffuseColor();
                    int color2 = DyeColor.byId(lvt_18_1_).getTextureDiffuseColor();
                    float r1 = (float)(color1 >> 16 & 255) / 255.0F;
                    float g1 = (float)(color1 >> 8 & 255) / 255.0F;
                    float b1 = (float)(color1 & 255) / 255.0F;
                    float r2 = (float)(color2 >> 16 & 255) / 255.0F;
                    float g2 = (float)(color2 >> 8 & 255) / 255.0F;
                    float b2 = (float)(color2 & 255) / 255.0F;
                    lvt_11_2_ = r1 * (1.0F - lvt_19_1_) + r2 * lvt_19_1_;
                    lvt_12_2_ = g1 * (1.0F - lvt_19_1_) + g2 * lvt_19_1_;
                    lvt_13_2_ = b1 * (1.0F - lvt_19_1_) + b2 * lvt_19_1_;
                } else {
                    int color = state.color.getTextureDiffuseColor();
                    lvt_11_2_ = (float)(color >> 16 & 255) / 255.0F;
                    lvt_12_2_ = (float)(color >> 8 & 255) / 255.0F;
                    lvt_13_2_ = (float)(color & 255) / 255.0F;
                }
                submitNodeCollector.order(1).submitModelSemanticTexture(
                    this.getParentModel(), state, poseStack, RenderType.entityCutoutNoCull(TEXTURE_BANDANA), 
                    packedLight, OverlayTexture.NO_OVERLAY, AMColorUtil.packColor(lvt_11_2_, lvt_12_2_, lvt_13_2_, 1.0F), 
                    TEXTURE_BANDANA, state.outlineColor, null
                );
            }
        }
    }
}
