package net.alexsmobs.client.render;

import net.alexsmobs.client.model.ModelMimicOctopus;
import net.alexsmobs.client.render.state.MimicOctopusRenderState;
import net.alexsmobs.entity.EntityMimicOctopus;
import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class RenderMimicOctopus extends MobRenderer<EntityMimicOctopus, MimicOctopusRenderState, ModelMimicOctopus> {
    private static final ResourceLocation TEXTURE = ResourceLocation
            .withDefaultNamespace("textures/entity/mimic_octopus.png");
    private static final ResourceLocation TEXTURE_OVERLAY = ResourceLocation
            .withDefaultNamespace("textures/entity/mimic_octopus_overlay.png");
    private static final ResourceLocation TEXTURE_CREEPER = ResourceLocation
            .withDefaultNamespace("textures/entity/mimic_octopus_creeper.png");
    private static final ResourceLocation TEXTURE_GUARDIAN = ResourceLocation
            .withDefaultNamespace("textures/entity/mimic_octopus_guardian.png");
    private static final ResourceLocation TEXTURE_PUFFERFISH = ResourceLocation
            .withDefaultNamespace("textures/entity/mimic_octopus_pufferfish.png");
    private static final ResourceLocation TEXTURE_MIMICUBE = ResourceLocation
            .withDefaultNamespace("textures/entity/mimic_octopus_mimicube.png");
    private static final ResourceLocation TEXTURE_EYES = ResourceLocation
            .withDefaultNamespace("textures/entity/mimic_octopus_eyes.png");
    private static final ResourceLocation GUARDIAN_BEAM_TEXTURE = ResourceLocation
            .withDefaultNamespace("textures/entity/guardian_beam.png");
    private static final RenderType BEAM_RENDER_TYPE = RenderType.entityCutoutNoCull(GUARDIAN_BEAM_TEXTURE);

    public RenderMimicOctopus(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelMimicOctopus(), 0.4F);
        this.addLayer(new OverlayLayer(this));
    }

    @Override
    public MimicOctopusRenderState createRenderState() {
        return new MimicOctopusRenderState();
    }

    @Override
    public void extractRenderState(EntityMimicOctopus entity, MimicOctopusRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.transProgress = entity.transProgress;
        renderState.prevTransProgress = entity.prevTransProgress;
        renderState.colorShiftProgress = entity.colorShiftProgress;
        renderState.prevColorShiftProgress = entity.prevColorShiftProgress;
        renderState.groundProgress = entity.groundProgress;
        renderState.prevGroundProgress = entity.prevGroundProgress;
        renderState.sitProgress = entity.sitProgress;
        renderState.prevSitProgress = entity.prevSitProgress;
        renderState.mimicState = entity.getMimicState();
        renderState.prevMimicState = entity.getPrevMimicState();
        renderState.mimickedBlock = entity.getMimickedBlock();
        renderState.prevMimickedBlock = entity.getPrevMimickedBlock();
        renderState.hasGuardianLaser = entity.hasGuardianLaser();
        renderState.scale = entity.getScale();
        net.minecraft.world.entity.LivingEntity laserTarget = entity.getGuardianLaser();
        renderState.guardianLaserTargetPresent = renderState.hasGuardianLaser && laserTarget != null;
        if (renderState.guardianLaserTargetPresent) {
            double sourceX = net.minecraft.util.Mth.lerp(partialTick, entity.xo, entity.getX());
            double sourceY = net.minecraft.util.Mth.lerp(partialTick, entity.yo, entity.getY()) + entity.getEyeHeight();
            double sourceZ = net.minecraft.util.Mth.lerp(partialTick, entity.zo, entity.getZ());
            double targetX = net.minecraft.util.Mth.lerp(partialTick, laserTarget.xo, laserTarget.getX());
            double targetY = net.minecraft.util.Mth.lerp(partialTick, laserTarget.yo, laserTarget.getY())
                + laserTarget.getBbHeight() * 0.5D;
            double targetZ = net.minecraft.util.Mth.lerp(partialTick, laserTarget.zo, laserTarget.getZ());
            renderState.guardianLaserTargetX = (float) (targetX - sourceX);
            renderState.guardianLaserTargetY = (float) (targetY - sourceY);
            renderState.guardianLaserTargetZ = (float) (targetZ - sourceZ);
            renderState.guardianLaserProgress = entity.getLaserAttackAnimationScale(partialTick);
        } else {
            renderState.guardianLaserTargetX = renderState.guardianLaserTargetY = renderState.guardianLaserTargetZ = 0.0F;
            renderState.guardianLaserProgress = 0.0F;
        }
    }

    private static void vertex(VertexConsumer p_229108_0_, Matrix4f p_229108_1_, Matrix3f p_229108_2_,
            float p_229108_3_, float p_229108_4_, float p_229108_5_, int p_229108_6_, int p_229108_7_, int p_229108_8_,
            float p_229108_9_, float p_229108_10_) {
        org.joml.Vector3f normal = new org.joml.Vector3f(0.0F, 1.0F, 0.0F);
        normal.mul(p_229108_2_);
        org.joml.Vector4f pos = new org.joml.Vector4f(p_229108_3_, p_229108_4_, p_229108_5_, 1.0F);
        pos.mul(p_229108_1_);
        p_229108_0_.addVertex(pos.x, pos.y, pos.z)
                .setColor(p_229108_6_, p_229108_7_, p_229108_8_, 255).setUv(p_229108_9_, p_229108_10_)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(normal.x, normal.y, normal.z);
    }

    // Legacy geometry reference retained below; the active beam route is semantic
    // and uses copied target state in OverlayLayer.submitGuardianBeam().
    /*
    public void render(EntityMimicOctopus entityIn, float entityYaw, float partialTicks, PoseStack matrixStackIn,
            MultiBufferSource bufferIn, int packedLightIn) {
        LivingEntity livingentity = entityIn.getGuardianLaser();
        if (livingentity != null) {
            float f = entityIn.getLaserAttackAnimationScale(partialTicks);
            float f1 = (float) entityIn.level().getGameTime() + partialTicks;
            float f2 = f1 * 0.5F % 1.0F;
            float f3 = entityIn.getEyeHeight();
            matrixStackIn.pushPose();
            matrixStackIn.translate(0.0D, f3, 0.0D);
            Vec3 vector3d = this.getPosition(livingentity, (double) livingentity.getBbHeight() * 0.5D, partialTicks);
            Vec3 vector3d1 = this.getPosition(entityIn, f3, partialTicks);
            Vec3 vector3d2 = vector3d.subtract(vector3d1);
            float f4 = (float) (vector3d2.length() + 1.0D);
            vector3d2 = vector3d2.normalize();
            float f5 = (float) Math.acos(vector3d2.y);
            float f6 = (float) Math.atan2(vector3d2.z, vector3d2.x);
            matrixStackIn.mulPose(Axis.YP.rotationDegrees(((Mth.PI / 2F) - f6) * Mth.RAD_TO_DEG));
            matrixStackIn.mulPose(Axis.XP.rotationDegrees(f5 * Mth.RAD_TO_DEG));
            int i = 1;
            float f7 = f1 * 0.05F * -1.5F;
            float f8 = f * f;
            int j = 64 + (int) (f8 * 191.0F);
            int k = 32 + (int) (f8 * 191.0F);
            int l = 128 - (int) (f8 * 64.0F);
            float f9 = 0.2F;
            float f10 = 0.282F;
            float f11 = Mth.cos(f7 + 2.3561945F) * 0.282F;
            float f12 = Mth.sin(f7 + 2.3561945F) * 0.282F;
            float f13 = Mth.cos(f7 + Maths.QUARTER_PI) * 0.282F;
            float f14 = Mth.sin(f7 + Maths.QUARTER_PI) * 0.282F;
            float f15 = Mth.cos(f7 + 3.926991F) * 0.282F;
            float f16 = Mth.sin(f7 + 3.926991F) * 0.282F;
            float f17 = Mth.cos(f7 + 5.4977875F) * 0.282F;
            float f18 = Mth.sin(f7 + 5.4977875F) * 0.282F;
            float f19 = Mth.cos(f7 + Mth.PI) * 0.2F;
            float f20 = Mth.sin(f7 + Mth.PI) * 0.2F;
            float f21 = Mth.cos(f7 + 0.0F) * 0.2F;
            float f22 = Mth.sin(f7 + 0.0F) * 0.2F;
            float f23 = Mth.cos(f7 + (Mth.PI / 2F)) * 0.2F;
            float f24 = Mth.sin(f7 + (Mth.PI / 2F)) * 0.2F;
            float f25 = Mth.cos(f7 + (Mth.PI * 1.5F)) * 0.2F;
            float f26 = Mth.sin(f7 + (Mth.PI * 1.5F)) * 0.2F;
            float f27 = 0.0F;
            float f28 = 0.4999F;
            float f29 = -1.0F + f2;
            float f30 = f4 * 2.5F + f29;
            VertexConsumer ivertexbuilder = bufferIn.getBuffer(BEAM_RENDER_TYPE);
            PoseStack.Pose matrixstack$entry = matrixStackIn.last();
            Matrix4f matrix4f = matrixstack$entry.pose();
            Matrix3f matrix3f = matrixstack$entry.normal();
            vertex(ivertexbuilder, matrix4f, matrix3f, f19, f4, f20, j, k, l, 0.4999F, f30);
            vertex(ivertexbuilder, matrix4f, matrix3f, f19, 0.0F, f20, j, k, l, 0.4999F, f29);
            vertex(ivertexbuilder, matrix4f, matrix3f, f21, 0.0F, f22, j, k, l, 0.0F, f29);
            vertex(ivertexbuilder, matrix4f, matrix3f, f21, f4, f22, j, k, l, 0.0F, f30);
            vertex(ivertexbuilder, matrix4f, matrix3f, f23, f4, f24, j, k, l, 0.4999F, f30);
            vertex(ivertexbuilder, matrix4f, matrix3f, f23, 0.0F, f24, j, k, l, 0.4999F, f29);
            vertex(ivertexbuilder, matrix4f, matrix3f, f25, 0.0F, f26, j, k, l, 0.0F, f29);
            vertex(ivertexbuilder, matrix4f, matrix3f, f25, f4, f26, j, k, l, 0.0F, f30);
            float f31 = 0.0F;
            if (entityIn.tickCount % 2 == 0) {
                f31 = 0.5F;
            }

            vertex(ivertexbuilder, matrix4f, matrix3f, f11, f4, f12, j, k, l, 0.5F, f31 + 0.5F);
            vertex(ivertexbuilder, matrix4f, matrix3f, f13, f4, f14, j, k, l, 1.0F, f31 + 0.5F);
            vertex(ivertexbuilder, matrix4f, matrix3f, f17, f4, f18, j, k, l, 1.0F, f31);
            vertex(ivertexbuilder, matrix4f, matrix3f, f15, f4, f16, j, k, l, 0.5F, f31);
            matrixStackIn.popPose();
        }
        super.render(entityIn, entityYaw, partialTicks, matrixStackIn, bufferIn, packedLightIn);

    }
    */

    @Override
    protected void scale(MimicOctopusRenderState renderState, PoseStack matrixStackIn) {
        matrixStackIn.translate(0, -0.02F, 0);
        matrixStackIn.scale(0.9F * renderState.scale, 0.9F * renderState.scale, 0.9F * renderState.scale);
    }

    /* Commenting out shouldRender override - needs entity access
    public boolean shouldRender(EntityMimicOctopus livingEntityIn, Frustum camera, double camX, double camY,
            double camZ) {
        if (super.shouldRender(livingEntityIn, camera, camX, camY, camZ)) {
            return true;
        } else {
            if (livingEntityIn.hasGuardianLaser()) {
                LivingEntity livingentity = livingEntityIn.getGuardianLaser();
                if (livingentity != null) {
                    Vec3 vector3d = this.getPosition(livingentity, (double) livingentity.getBbHeight() * 0.5D, 1.0F);
                    Vec3 vector3d1 = this.getPosition(livingEntityIn, livingEntityIn.getEyeHeight(), 1.0F);
                    return camera.isVisible(
                            new AABB(vector3d1.x, vector3d1.y, vector3d1.z, vector3d.x, vector3d.y, vector3d.z));
                }
            }

            return false;
        }
    }

    private Vec3 getPosition(LivingEntity entityLivingBaseIn, double p_177110_2_, float p_177110_4_) {
        double d0 = Mth.lerp(p_177110_4_, entityLivingBaseIn.xOld, entityLivingBaseIn.getX());
        double d1 = Mth.lerp(p_177110_4_, entityLivingBaseIn.yOld, entityLivingBaseIn.getY()) + p_177110_2_;
        double d2 = Mth.lerp(p_177110_4_, entityLivingBaseIn.zOld, entityLivingBaseIn.getZ());
        return new Vec3(d0, d1, d2);
    }
    */

    @Override
    public ResourceLocation getTextureLocation(MimicOctopusRenderState renderState) {
        return TEXTURE;
    }

    static class OverlayLayer extends RenderLayer<MimicOctopusRenderState, ModelMimicOctopus> {

        public OverlayLayer(RenderMimicOctopus render) {
            super(render);
        }

        @Override
        public void submit(PoseStack matrixStackIn, net.minecraft.client.renderer.SubmitNodeCollector collector, int packedLightIn,
                MimicOctopusRenderState renderState, float u, float v) {
            submitGuardianBeam(matrixStackIn, collector, packedLightIn, renderState);
            float transProgress = renderState.transProgress;
            float colorProgress = (renderState.prevColorShiftProgress
                    + (renderState.colorShiftProgress - renderState.prevColorShiftProgress))
                    * 0.2F;
            float r = 1F;
            float g = 1F;
            float b = 1F;
            float a = 1F;
            float startR = 1.0F;
            float startG = 1.0F;
            float startB = 1.0F;
            float startA = 1.0F;
            float finR = 1.0F;
            float finG = 1.0F;
            float finB = 1.0F;
            float finA = 1.0F;
            
            if (renderState.prevMimicState == EntityMimicOctopus.MimicState.OVERLAY) {
                if (renderState.prevMimickedBlock != null) {
                    int j = OctopusColorRegistry.getBlockColor(renderState.prevMimickedBlock);
                    startR = (float) (j >> 16 & 255) / 255.0F;
                    startG = (float) (j >> 8 & 255) / 255.0F;
                    startB = (float) (j & 255) / 255.0F;
                } else {
                    startA = 0.0F;
                }
            }
            if ((renderState.mimicState == EntityMimicOctopus.MimicState.OVERLAY)) {
                if (renderState.mimickedBlock != null) {
                    int i = OctopusColorRegistry.getBlockColor(renderState.mimickedBlock);
                    finR = (float) (i >> 16 & 255) / 255.0F;
                    finG = (float) (i >> 8 & 255) / 255.0F;
                    finB = (float) (i & 255) / 255.0F;
                } else {
                    finA = 0.0F;
                }
                r = startR + (finR - startR) * colorProgress;
                g = startG + (finG - startG) * colorProgress;
                b = startB + (finB - startB) * colorProgress;
                a = startA + (finA - startA) * colorProgress;
            }
            if (a == 1.0F) {
                a *= 0.9F + 0.1F * (float) Math.sin(renderState.ageInTicks * 0.1F);
            }
            
            if (renderState.prevMimicState != null) {
                float alphaPrev = 1 - transProgress * 0.2F;
                RenderType prevRenderType = AMRenderTypes.entityTranslucent(getFor(renderState.prevMimicState));
                if (renderState.prevMimicState == renderState.mimicState) {
                    alphaPrev *= a;
                }
                int prevColor = AMColorUtil.packColor(r, g, b, alphaPrev);
                collector.order(1).submitModelSemanticTexture(
                    this.getParentModel(), renderState, matrixStackIn, prevRenderType, packedLightIn, OverlayTexture.NO_OVERLAY, prevColor, getFor(renderState.prevMimicState), renderState.outlineColor, null
                );
            }
            
            float alphaCurrent = transProgress * 0.2F;
            RenderType currentRenderType = AMRenderTypes.entityTranslucent(getFor(renderState.mimicState));
            int currentColor = AMColorUtil.packColor(r, g, b, a * alphaCurrent);
            collector.order(1).submitModelSemanticTexture(
                this.getParentModel(), renderState, matrixStackIn, currentRenderType, packedLightIn, OverlayTexture.NO_OVERLAY, currentColor, getFor(renderState.mimicState), renderState.outlineColor, null
            );
            
            // Render eyes
            RenderType eyesRenderType = AMRenderTypes.entityTranslucent(TEXTURE_EYES);
            collector.order(1).submitModelSemanticTexture(
                this.getParentModel(), renderState, matrixStackIn, eyesRenderType, packedLightIn, OverlayTexture.NO_OVERLAY, -1, TEXTURE_EYES, renderState.outlineColor, null
            );
        }

        private void submitGuardianBeam(PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector collector,
                                        int light, MimicOctopusRenderState state) {
            if (!state.guardianLaserTargetPresent || state.guardianLaserProgress <= 0.0F) return;
            float dx = state.guardianLaserTargetX;
            float dy = state.guardianLaserTargetY + state.eyeHeight;
            float dz = state.guardianLaserTargetZ;
            float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (!(length > 0.001F) || !Float.isFinite(length)) return;
            dx /= length; dy /= length; dz /= length;
            float px = -dz;
            float py = 0.0F;
            float pz = dx;
            float pLen = (float) Math.sqrt(px * px + pz * pz);
            if (pLen < 0.001F) { px = 1.0F; pz = 0.0F; pLen = 1.0F; }
            px /= pLen; pz /= pLen;
            float radius = 0.16F + 0.16F * state.guardianLaserProgress;
            float sx = 0.0F, sy = state.eyeHeight, sz = 0.0F;
            float[] vertices = {
                sx + px * radius, sy + py * radius, sz + pz * radius,
                sx - px * radius, sy - py * radius, sz - pz * radius,
                dx * length - px * radius, sy + dy * length - py * radius, dz * length - pz * radius,
                dx * length + px * radius, sy + dy * length + py * radius, dz * length + pz * radius
            };
            float[] crossed = {
                sx + radius, sy, sz, sx - radius, sy, sz,
                dx * length - radius, sy + dy * length, dz * length,
                dx * length + radius, sy + dy * length, dz * length
            };
            float[] uvs = {0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F};
            boolean accepted = collector.submitTranslucentTexturedQuad(
                poseStack, BEAM_RENDER_TYPE, GUARDIAN_BEAM_TEXTURE, vertices, uvs, 0xFFFFFFFF, light)
                && collector.submitTranslucentTexturedQuad(
                poseStack, BEAM_RENDER_TYPE, GUARDIAN_BEAM_TEXTURE, crossed, uvs, 0xFFFFFFFF, light);
            boolean rustWholeFrame = net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
                && net.vulkanic.world.WorldRenderRoutePolicy.currentTexturedBillboardRoute().usesRustWholeFrameVulkan();
            boolean vulkanSelected = net.vulkanic.VulkanicAPI.isVulkanBackendSelected();
            if (!accepted && rustWholeFrame) {
                throw new IllegalStateException("Rust whole-frame Mimic Octopus beam route rejected semantic quads");
            }
            if (vulkanSelected && !rustWholeFrame) {
                throw new IllegalStateException("Mimic Octopus beam is unavailable until the Rust Vulkan billboard route is admitted");
            }
            if (!vulkanSelected) {
                collector.submitCustomGeometry(poseStack, BEAM_RENDER_TYPE, (pose, consumer) -> {
                    Matrix4f matrix = pose.pose();
                    Matrix3f normal = pose.normal();
                    vertex(consumer, matrix, normal, vertices[0], vertices[1], vertices[2], 255, 255, 255, 0.0F, 0.0F);
                    vertex(consumer, matrix, normal, vertices[3], vertices[4], vertices[5], 255, 255, 255, 1.0F, 0.0F);
                    vertex(consumer, matrix, normal, vertices[6], vertices[7], vertices[8], 255, 255, 255, 1.0F, 1.0F);
                    vertex(consumer, matrix, normal, vertices[9], vertices[10], vertices[11], 255, 255, 255, 0.0F, 1.0F);
                    vertex(consumer, matrix, normal, crossed[0], crossed[1], crossed[2], 255, 255, 255, 0.0F, 0.0F);
                    vertex(consumer, matrix, normal, crossed[3], crossed[4], crossed[5], 255, 255, 255, 1.0F, 0.0F);
                    vertex(consumer, matrix, normal, crossed[6], crossed[7], crossed[8], 255, 255, 255, 1.0F, 1.0F);
                    vertex(consumer, matrix, normal, crossed[9], crossed[10], crossed[11], 255, 255, 255, 0.0F, 1.0F);
                });
            }
        }

        public ResourceLocation getFor(EntityMimicOctopus.MimicState state) {
            if (state == EntityMimicOctopus.MimicState.CREEPER) {
                return TEXTURE_CREEPER;
            }
            if (state == EntityMimicOctopus.MimicState.GUARDIAN) {
                return TEXTURE_GUARDIAN;
            }
            if (state == EntityMimicOctopus.MimicState.PUFFERFISH) {
                return TEXTURE_PUFFERFISH;
            }
            if (state == EntityMimicOctopus.MimicState.MIMICUBE) {
                return TEXTURE_MIMICUBE;
            }
            return TEXTURE_OVERLAY;
        }
    }
}
