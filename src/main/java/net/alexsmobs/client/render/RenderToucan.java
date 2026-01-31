package net.alexsmobs.client.render;

import net.alexsmobs.client.model.ModelToucan;
import net.alexsmobs.entity.EntityToucan;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

public class RenderToucan extends MobRenderer<EntityToucan, ToucanRenderState, ModelToucan> {
    private static final ResourceLocation TEXTURE_0 = ResourceLocation.withDefaultNamespace("textures/entity/toucan/toucan_0.png");
    private static final ResourceLocation TEXTURE_1 = ResourceLocation.withDefaultNamespace("textures/entity/toucan/toucan_1.png");
    private static final ResourceLocation TEXTURE_2 = ResourceLocation.withDefaultNamespace("textures/entity/toucan/toucan_2.png");
    private static final ResourceLocation TEXTURE_3 = ResourceLocation.withDefaultNamespace("textures/entity/toucan/toucan_3.png");
    private static final ResourceLocation TEXTURE_GOLDEN = ResourceLocation.withDefaultNamespace("textures/entity/toucan/toucan_gold.png");
    private static final ResourceLocation TEXTURE_SAM = ResourceLocation.withDefaultNamespace("textures/entity/toucan/toucan_sam.png");

    public RenderToucan(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelToucan(), 0.2F);
        this.addLayer(new LayerGlint(this));
    }

    @Override
    public ToucanRenderState createRenderState() {
        return new ToucanRenderState();
    }

    @Override
    public void extractRenderState(EntityToucan entity, ToucanRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.flyProgress = entity.prevFlyProgress + (entity.flyProgress - entity.prevFlyProgress) * partialTick;
        state.peckProgress = entity.prevPeckProgress + (entity.peckProgress - entity.prevPeckProgress) * partialTick;
        state.isSam = entity.isSam();
        state.isGolden = entity.isGolden();
        state.isEnchanted = entity.isEnchanted();
        state.variant = entity.getVariant();
        state.heldItem = entity.getItemBySlot(EquipmentSlot.MAINHAND);
    }

    protected void scale(ToucanRenderState state, PoseStack matrixStackIn) {
        matrixStackIn.scale(0.9F, 0.9F, 0.9F);
    }

    public ResourceLocation getTextureLocation(ToucanRenderState state) {
        if(state.isSam){
            return TEXTURE_SAM;
        }
        if(state.isGolden){
            return TEXTURE_GOLDEN;
        }
        switch (state.variant){
            case 3:
                return TEXTURE_3;
            case 2:
                return TEXTURE_2;
            case 1:
                return TEXTURE_1;
            default:
                return TEXTURE_0;
        }
    }

    static class LayerGlint extends RenderLayer<ToucanRenderState, ModelToucan> {

        public LayerGlint(RenderToucan render) {
            super(render);
        }

        @Override
        public void submit(PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector submitNodeCollector, int packedLight,
                ToucanRenderState state, float f, float g) {
            if(state.isEnchanted){
                int color = -1;
                submitNodeCollector.order(1).submitModel(
                    this.getParentModel(), state, poseStack, RenderType.armorCutoutNoCull(TEXTURE_GOLDEN), packedLight,
                    LivingEntityRenderer.getOverlayCoords(state, 0.0F), color, null, state.outlineColor, null
                );
            }
        }
    }
}
