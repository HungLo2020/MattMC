package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.alexsmobs.client.model.ModelMungus;
import com.github.alexthe666.alexsmobs.client.render.layer.MungusBeamLayer;
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
        this.addLayer(new MungusBeamLayer(this));
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
        renderState.swellProgress = entity.swellProgress;
        renderState.prevSwellProgress = entity.prevSwellProgress;
        renderState.x = entity.getX();
        renderState.y = entity.getY();
        renderState.z = entity.getZ();
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
            float rotationYaw) {
        if (renderState.deathTime > 0) {
            // Note: partialTicks is now in renderState.ageInTicks
            float f = ((float) renderState.deathTime - 1.0F) / 20.0F * 1.6F;
            f = Mth.sqrt(f);
            if (f > 1.0F) {
                f = 1.0F;
            }
            matrixStackIn.mulPose(Axis.YP.rotationDegrees(180.0F - rotationYaw));
            matrixStackIn.mulPose(Axis.XP.rotationDegrees(f * -90));
        } else {
            super.setupRotations(renderState, matrixStackIn, ageInTicks, rotationYaw);
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

    public ResourceLocation getTextureLocation(MungusRenderState renderState) {
        return TEXTURE;
    }

    static class MungusSackLayer extends RenderLayer<MungusRenderState, ModelMungus> {

        public MungusSackLayer(RenderMungus p_i50928_1_) {
            super(p_i50928_1_);
        }

        @Override
        public void submit(PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector submitNodeCollector, int packedLight, MungusRenderState renderState, float limbSwing, float limbSwingAmount) {
            // Render glowing sack overlay
            float alpha = 0.75F + (Mth.cos(renderState.ageInTicks * 0.2F) + 1F) * 0.125F;
            int color = packColor(1.0F, 1.0F, 1.0F, alpha);
            submitNodeCollector.submitModel(
                this.getParentModel(),
                renderState,
                poseStack,
                RenderType.eyes(TEXTURE_SACK_OVERLAY),
                240,
                OverlayTexture.NO_OVERLAY,
                color,
                null,
                0,
                null
            );
            
            // Render beam overlay if target exists
            if (renderState.beamTarget != null) {
                float beamAlpha = 0.75F + (Mth.cos(renderState.ageInTicks * 1) + 1F) * 0.125F;
                int beamColor = packColor(1.0F, 1.0F, 1.0F, beamAlpha);
                submitNodeCollector.submitModel(
                    this.getParentModel(),
                    renderState,
                    poseStack,
                    RenderType.entityTranslucent(TEXTURE_BEAM_OVERLAY),
                    240,
                    OverlayTexture.pack(OverlayTexture.u(0), OverlayTexture.v(false)),
                    beamColor,
                    null,
                    0,
                    null
                );
            }
            
            // Render shoes for "drip" named mungus
            String s = ChatFormatting.stripFormatting(renderState.nameTag != null ? renderState.nameTag.getString() : "");
            if (s != null && s.toLowerCase().contains("drip")) {
                poseStack.pushPose();
                this.getParentModel().renderShoes();
                submitNodeCollector.submitModel(
                    this.getParentModel(),
                    renderState,
                    poseStack,
                    RenderType.entityCutoutNoCull(TEXTURE_SHOES),
                    packedLight,
                    OverlayTexture.NO_OVERLAY,
                    -1,
                    null,
                    0,
                    null
                );
                this.getParentModel().postRenderShoes();
                poseStack.popPose();
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

        @Override
        public void submit(PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector submitNodeCollector, int packedLight, MungusRenderState renderState, float limbSwing, float limbSwingAmount) {
            BlockState blockstate = renderState.mushroomState;
            if (blockstate == null) {
                return;
            }
            int overlay = OverlayTexture.pack(OverlayTexture.u(0), OverlayTexture.v(false));
            boolean altOrder = renderState.altOrderMushroom;
            int mushroomCount = renderState.mushroomCount;
            poseStack.pushPose();
            if (renderState.isBaby) {
                poseStack.scale(0.5F, 0.5F, 0.5F);
                poseStack.translate(0.0D, 1.5D, 0D);
            }
            poseStack.pushPose();
            translateToBody(poseStack);
            if (mushroomCount == 1 && !altOrder || mushroomCount >= 2) {
                poseStack.pushPose();
                poseStack.translate(0.2F, -1.4F, 0.15D);
                poseStack.scale(-1.0F, -1.0F, 1.0F);
                poseStack.translate(-0.5D, -0.5D, -0.5D);
                submitNodeCollector.submitBlock(poseStack, blockstate, packedLight, overlay, renderState.outlineColor);
                poseStack.popPose();
            }
            if (mushroomCount == 1 && altOrder || mushroomCount >= 2) {
                poseStack.pushPose();
                poseStack.translate(-0.2F, -1.5F, -0.2D);
                poseStack.scale(-1.0F, -1.0F, 1.0F);
                poseStack.translate(-0.5D, -0.5D, -0.5D);
                submitNodeCollector.submitBlock(poseStack, blockstate, packedLight, overlay, renderState.outlineColor);
                poseStack.popPose();
            }
            if (mushroomCount >= 3) {
                poseStack.pushPose();
                poseStack.translate(0.76F, -0.4F, 0.1D);
                poseStack.mulPose(Axis.ZP.rotationDegrees(90F));
                poseStack.scale(-1.0F, -1.0F, 1.0F);
                poseStack.translate(-0.5D, -0.5D, -0.5D);
                submitNodeCollector.submitBlock(poseStack, blockstate, packedLight, overlay, renderState.outlineColor);
                poseStack.popPose();
            }
            if (mushroomCount >= 4) {
                poseStack.pushPose();
                poseStack.translate(-0.76F, -1.0F, 0.1D);
                poseStack.mulPose(Axis.ZP.rotationDegrees(-60F));
                poseStack.scale(-1.0F, -1.0F, 1.0F);
                poseStack.translate(-0.5D, -0.5D, -0.5D);
                submitNodeCollector.submitBlock(poseStack, blockstate, packedLight, overlay, renderState.outlineColor);
                poseStack.popPose();
            }
            if (mushroomCount >= 5) {
                poseStack.pushPose();
                poseStack.translate(-0.76F, -0.1F, 0.1D);
                poseStack.mulPose(Axis.ZP.rotationDegrees(-100F));
                poseStack.scale(-1.0F, -1.0F, 1.0F);
                poseStack.translate(-0.5D, -0.5D, -0.5D);
                submitNodeCollector.submitBlock(poseStack, blockstate, packedLight, overlay, renderState.outlineColor);
                poseStack.popPose();
            }
            poseStack.popPose();
            poseStack.popPose();
        }

        protected void translateToBody(PoseStack matrixStack) {
            this.getParentModel().root.translateAndRotate(matrixStack);
            this.getParentModel().body.translateAndRotate(matrixStack);
        }
    }

}
