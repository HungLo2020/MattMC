package net.alexsmobs.client.render;

import net.alexsmobs.client.model.ModelEnderiophage;
import net.alexsmobs.entity.EntityEnderiophage;
import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

public class RenderEnderiophage extends MobRenderer<EntityEnderiophage, EnderiophageRenderState, ModelEnderiophage> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/enderiophage.png");
    private static final ResourceLocation TEXTURE_GLOW = ResourceLocation.withDefaultNamespace("textures/entity/enderiophage_glow.png");
    private static final ResourceLocation TEXTURE_OVERWORLD = ResourceLocation.withDefaultNamespace("textures/entity/enderiophage_overworld.png");
    private static final ResourceLocation TEXTURE_OVERWORLD_GLOW = ResourceLocation.withDefaultNamespace("textures/entity/enderiophage_overworld_glow.png");
    private static final ResourceLocation TEXTURE_NETHER = ResourceLocation.withDefaultNamespace("textures/entity/enderiophage_nether.png");
    private static final ResourceLocation TEXTURE_NETHER_GLOW = ResourceLocation.withDefaultNamespace("textures/entity/enderiophage_nether_glow.png");
    private static final int FULL_BRIGHT = 15728640;

    public RenderEnderiophage(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelEnderiophage(), 0.5F);
        this.addLayer(new EnderiophageEyesLayer(this));
    }

    @Override
    public EnderiophageRenderState createRenderState() {
        return new EnderiophageRenderState();
    }

    @Override
    public void extractRenderState(EntityEnderiophage entity, EnderiophageRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.phageScale = entity.getPhageScale();
        state.prevPhageScale = entity.prevEnderiophageScale;
        state.phagePitch = entity.getPhagePitch();
        state.prevPhagePitch = entity.prevPhagePitch;
        state.tentacleAngle = entity.tentacleAngle;
        state.lastTentacleAngle = entity.lastTentacleAngle;
        state.phageRotation = entity.phageRotation;
        state.flyProgress = entity.flyProgress;
        state.prevFlyProgress = entity.prevFlyProgress;
        state.passengerIndex = entity.passengerIndex;
        state.isPassenger = entity.isPassenger();
        state.isMissingEye = entity.isMissingEye();
        state.variant = entity.getVariant();
        state.yHeadRot = entity.getYHeadRot();
        state.xHeadRot = entity.getXRot();
        state.yBodyRot = entity.yBodyRot;
    }

    @Nullable
    @Override
    protected RenderType getRenderType(EnderiophageRenderState state, boolean visible, boolean visibleToPlayer, boolean bodyVisible) {
        ResourceLocation resourcelocation = this.getTextureLocation(state);
        if (visibleToPlayer) {
            return RenderType.itemEntityTranslucentCull(resourcelocation);
        } else if (visible) {
            return RenderType.entityTranslucent(resourcelocation);
        } else {
            return bodyVisible ? RenderType.outline(resourcelocation) : null;
        }
    }

    protected void scale(EnderiophageRenderState state, PoseStack matrixStackIn) {
        float scale = state.prevPhageScale + (state.phageScale - state.prevPhageScale);
        matrixStackIn.scale(0.8F * scale, 0.8F * scale, 0.8F * scale);
    }


    public ResourceLocation getTextureLocation(EnderiophageRenderState state) {
        return state.variant == 2 ? TEXTURE_NETHER : state.variant == 1 ? TEXTURE_OVERWORLD : TEXTURE;
    }

    class EnderiophageEyesLayer extends EyesLayer<EnderiophageRenderState, ModelEnderiophage> {

        public EnderiophageEyesLayer(RenderEnderiophage p_i50928_1_) {
            super(p_i50928_1_);
        }


        public void render(PoseStack matrixStackIn, MultiBufferSource bufferIn, int packedLightIn, EnderiophageRenderState state, float limbSwing, float limbSwingAmount) {
            VertexConsumer ivertexbuilder = bufferIn.getBuffer(this.getRenderType(state));
            this.getParentModel().renderToBuffer(matrixStackIn, ivertexbuilder, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }

        @Override
        public RenderType renderType() {
            return AMRenderTypes.getEyesFlickering(TEXTURE_GLOW, FULL_BRIGHT);
        }

        public RenderType getRenderType(EnderiophageRenderState state) {
            ResourceLocation tex = state.variant == 2 ? TEXTURE_NETHER_GLOW : state.variant == 1 ? TEXTURE_OVERWORLD_GLOW : TEXTURE_GLOW;
            return AMRenderTypes.getEyesFlickering(tex, FULL_BRIGHT);
        }
    }

}
