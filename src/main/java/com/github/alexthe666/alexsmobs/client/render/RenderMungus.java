package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelMungus;
import com.github.alexthe666.alexsmobs.entity.EntityMungus;
import com.github.alexthe666.alexsmobs.entity.util.Maths;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class RenderMungus extends MobRenderer<EntityMungus, MungusRenderState, ModelMungus> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/mungus.png");
    private static final ResourceLocation BEAM_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/mungus_beam.png");
    private static final ResourceLocation TEXTURE_BEAM_OVERLAY = ResourceLocation.withDefaultNamespace("textures/entity/mungus_beam_overlay.png");
    private static final ResourceLocation TEXTURE_SACK_OVERLAY = ResourceLocation.withDefaultNamespace("textures/entity/mungus_sack.png");
    private static final ResourceLocation TEXTURE_SHOES = ResourceLocation.withDefaultNamespace("textures/entity/mungus_shoes.png");
    private static final RenderType beamType = RenderType.eyes(BEAM_TEXTURE);

    public RenderMungus(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelMungus(0), 0.5F);
        this.addLayer(new MungusSackLayer(this));
        this.addLayer(new MungusMushroomLayer(this));
    }

    @Override
    public MungusRenderState createRenderState() {
        return new MungusRenderState();
    }

    @Override
    public void extractRenderState(EntityMungus entity, MungusRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.beamTarget = entity.getBeamTarget();
        renderState.mushroomState = entity.getMushroomState();
        renderState.mushroomCount = entity.getMushroomCount();
        renderState.altOrderMushroom = entity.isAltOrderMushroom();
        renderState.isReverting = entity.isReverting();
    }

    protected boolean isShaking(MungusRenderState renderState) {
        return renderState.isReverting;
    }

    private static void vertex(VertexConsumer p_229108_0_, Matrix4f p_229108_1_, Matrix3f p_229108_2_,
            float p_229108_3_, float p_229108_4_, float p_229108_5_, int p_229108_6_, int p_229108_7_, int p_229108_8_,
            float p_229108_9_, float p_229108_10_) {
        org.joml.Vector4f pos = new org.joml.Vector4f(p_229108_3_, p_229108_4_, p_229108_5_, 1.0F);
        pos.mul(p_229108_1_);
        // POSITION_TEX_COLOR format: position -> tex -> color
        p_229108_0_.addVertex(pos.x, pos.y, pos.z)
                .setUv(p_229108_9_, p_229108_10_)
                .setColor(p_229108_6_, p_229108_7_, p_229108_8_, 255);
    }

    protected void setupRotations(MungusRenderState renderState, PoseStack matrixStackIn, float ageInTicks,
            float rotationYaw, float partialTicks) {
        if (renderState.deathTime > 0) {
            matrixStackIn.mulPose(Axis.YP.rotationDegrees(180.0F - rotationYaw));
            float f = ((float) renderState.deathTime + partialTicks - 1.0F) / 20.0F * 1.6F;
            f = Mth.sqrt(f);
            if (f > 1.0F) {
                f = 1.0F;
            }
            matrixStackIn.mulPose(Axis.XP.rotationDegrees(f * -90));
        } else {
            super.setupRotations(renderState, matrixStackIn, ageInTicks, rotationYaw, partialTicks);
        }
    }

    protected float getFlipDegrees(MungusRenderState renderState) {
        return 0F;
    }

    protected void scale(MungusRenderState renderState, PoseStack matrixStackIn, float partialTickTime) {
        String s = ChatFormatting.stripFormatting(renderState.nameTag != null ? renderState.nameTag.getString() : "");
        if (s != null && s.toLowerCase().contains("drip")) {
            matrixStackIn.translate(0F, renderState.isBaby ? -0.075F : -0.15F, 0F);
        }
    }

    // TODO: shouldRender and render methods for beam need to be adapted to 1.21 RenderState system
    // The beam rendering should be moved to a custom RenderLayer or handled differently

    public ResourceLocation getTextureLocation(MungusRenderState renderState) {
        return TEXTURE;
    }

    static class MungusSackLayer extends RenderLayer<MungusRenderState, ModelMungus> {

        public MungusSackLayer(RenderMungus p_i50928_1_) {
            super(p_i50928_1_);
        }

        public void render(PoseStack matrixStackIn, MultiBufferSource bufferIn, int packedLightIn,
                MungusRenderState renderState, float limbSwing, float limbSwingAmount) {
            VertexConsumer lead = bufferIn.getBuffer(RenderType.eyes(TEXTURE_SACK_OVERLAY));
            float alpha = 0.75F + (Mth.cos(renderState.ageInTicks * 0.2F) + 1F) * 0.125F;
            this.getParentModel().renderToBuffer(matrixStackIn, lead, 240, OverlayTexture.NO_OVERLAY,
                    packColor(1.0F, 1.0F, 1.0F, alpha));
            if (renderState.beamTarget != null) {
                VertexConsumer beam = bufferIn.getBuffer(RenderType.entityTranslucent(TEXTURE_BEAM_OVERLAY));
                float beamAlpha = 0.75F + (Mth.cos(renderState.ageInTicks * 1) + 1F) * 0.125F;
                this.getParentModel().renderToBuffer(matrixStackIn, beam, 240,
                        OverlayTexture.pack(OverlayTexture.u(0), OverlayTexture.v(false)),
                        packColor(1.0F, 1.0F, 1.0F, beamAlpha));
            }
            String s = ChatFormatting.stripFormatting(renderState.nameTag != null ? renderState.nameTag.getString() : "");
            if (s != null && s.toLowerCase().contains("drip")) {
                VertexConsumer shoeBuffer = bufferIn.getBuffer(RenderType.entityCutoutNoCull(TEXTURE_SHOES));
                matrixStackIn.pushPose();
                this.getParentModel().renderShoes();
                this.getParentModel().renderToBuffer(matrixStackIn, shoeBuffer, packedLightIn,
                        OverlayTexture.NO_OVERLAY, -1);
                this.getParentModel().postRenderShoes();
                matrixStackIn.popPose();
            }
        }
        
        private int packColor(float r, float g, float b, float a) {
            return ((int)(a * 255.0F) << 24) | ((int)(r * 255.0F) << 16) | ((int)(g * 255.0F) << 8) | (int)(b * 255.0F);
        }
    }

    static class MungusMushroomLayer extends RenderLayer<MungusRenderState, ModelMungus> {

        public MungusMushroomLayer(RenderMungus p_i50928_1_) {
            super(p_i50928_1_);
        }

        public void render(PoseStack matrixStackIn, MultiBufferSource bufferIn, int packedLightIn,
                MungusRenderState renderState, float limbSwing, float limbSwingAmount) {
            BlockRenderDispatcher blockrendererdispatcher = Minecraft.getInstance().getBlockRenderer();
            BlockState blockstate = renderState.mushroomState;
            if (blockstate == null) {
                return;
            }
            int i = OverlayTexture.pack(OverlayTexture.u(0), OverlayTexture.v(false));
            boolean altOrder = renderState.altOrderMushroom;
            int mushroomCount = renderState.mushroomCount;
            matrixStackIn.pushPose();
            if (renderState.isBaby) {
                matrixStackIn.scale(0.5F, 0.5F, 0.5F);
                matrixStackIn.translate(0.0D, 1.5D, 0D);
            }
            matrixStackIn.pushPose();
            translateToBody(matrixStackIn);
            if (mushroomCount == 1 && !altOrder || mushroomCount >= 2) {
                matrixStackIn.pushPose();
                matrixStackIn.translate(0.2F, -1.4F, 0.15D);
                matrixStackIn.scale(-1.0F, -1.0F, 1.0F);
                matrixStackIn.translate(-0.5D, -0.5D, -0.5D);
                blockrendererdispatcher.renderSingleBlock(blockstate, matrixStackIn, bufferIn, packedLightIn, i);
                matrixStackIn.popPose();
            }
            if (mushroomCount == 1 && altOrder || mushroomCount >= 2) {
                matrixStackIn.pushPose();
                matrixStackIn.translate(-0.2F, -1.5F, -0.2D);
                matrixStackIn.scale(-1.0F, -1.0F, 1.0F);
                matrixStackIn.translate(-0.5D, -0.5D, -0.5D);
                blockrendererdispatcher.renderSingleBlock(blockstate, matrixStackIn, bufferIn, packedLightIn, i);
                matrixStackIn.popPose();
            }
            if (mushroomCount >= 3) {
                matrixStackIn.pushPose();
                matrixStackIn.translate(0.76F, -0.4F, 0.1D);
                matrixStackIn.mulPose(Axis.ZP.rotationDegrees(90F));
                matrixStackIn.scale(-1.0F, -1.0F, 1.0F);
                matrixStackIn.translate(-0.5D, -0.5D, -0.5D);
                blockrendererdispatcher.renderSingleBlock(blockstate, matrixStackIn, bufferIn, packedLightIn, i);
                matrixStackIn.popPose();
            }
            if (mushroomCount >= 4) {
                matrixStackIn.pushPose();
                matrixStackIn.translate(-0.76F, -1.0F, 0.1D);
                matrixStackIn.mulPose(Axis.ZP.rotationDegrees(-60F));
                matrixStackIn.scale(-1.0F, -1.0F, 1.0F);
                matrixStackIn.translate(-0.5D, -0.5D, -0.5D);
                blockrendererdispatcher.renderSingleBlock(blockstate, matrixStackIn, bufferIn, packedLightIn, i);
                matrixStackIn.popPose();
            }
            if (mushroomCount >= 5) {
                matrixStackIn.pushPose();
                matrixStackIn.translate(-0.76F, -0.1F, 0.1D);
                matrixStackIn.mulPose(Axis.ZP.rotationDegrees(-100F));
                matrixStackIn.scale(-1.0F, -1.0F, 1.0F);
                matrixStackIn.translate(-0.5D, -0.5D, -0.5D);
                blockrendererdispatcher.renderSingleBlock(blockstate, matrixStackIn, bufferIn, packedLightIn, i);
                matrixStackIn.popPose();
            }
            matrixStackIn.popPose();
            matrixStackIn.popPose();

        }

        protected void translateToBody(PoseStack matrixStack) {
            this.getParentModel().root.translateAndRotate(matrixStack);
            this.getParentModel().body.translateAndRotate(matrixStack);
        }
    }

}
