package net.alexsmobs.client.render.layer;

import net.alexsmobs.client.model.ModelMungus;
import net.alexsmobs.client.render.MungusRenderState;
import net.alexsmobs.entity.util.Maths;
import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.VertexConsumer;
import net.math.Axis;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class MungusBeamLayer extends RenderLayer<MungusRenderState, ModelMungus> {
    private static final ResourceLocation BEAM_TEXTURE = ResourceLocation.fromNamespaceAndPath("alexsmobs", "textures/entity/mungus_beam.png");
    private static final RenderType beamType = RenderType.eyes(BEAM_TEXTURE);
    private static final int BEAM_ALPHA = 255;

    public MungusBeamLayer(RenderLayerParent<MungusRenderState, ModelMungus> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector submitNodeCollector, int packedLight, MungusRenderState renderState, float limbSwing, float limbSwingAmount) {
        BlockPos target = renderState.beamTarget;
        if (target == null) {
            return;
        }

        float f = 1.0F;
        float f1 = renderState.ageInTicks;
        float f2 = -1.0F * (f1 * 0.15F % 1.0F);
        float f3 = 1.13F;
        if (renderState.isBaby) {
            f3 = 0.555F;
        }

        poseStack.pushPose();
        poseStack.translate(0.0D, f3, 0.0D);
        
        // Calculate beam direction - target position relative to entity eye height
        Vec3 vector3d = Vec3.upFromBottomCenterOf(target, 0.15F);
        // Entity position is at 0,0,0 in this coordinate space, eye at 0,f3,0
        Vec3 vector3d1 = new Vec3(renderState.x, renderState.y + f3, renderState.z);
        Vec3 vector3d2 = vector3d.subtract(vector3d1);
        float f4 = (float) (vector3d2.length());
        vector3d2 = vector3d2.normalize();
        float f5 = (float) Math.acos(vector3d2.y);
        float f6 = (float) Math.atan2(vector3d2.z, vector3d2.x);
        
        poseStack.mulPose(Axis.YP.rotationDegrees(((Mth.PI / 2F) - f6) * Mth.RAD_TO_DEG));
        poseStack.mulPose(Axis.XP.rotationDegrees(f5 * Mth.RAD_TO_DEG));
        
        int i = 1;
        float f7 = f1 * 0.05F * 1.5F;
        float f8 = 1F;
        int j = (int) (f8 * 255.0F);
        int k = (int) (f8 * 255.0F);
        int l = (int) (f8 * 255.0F);
        float f9 = 0.2F;
        float f10 = 0.282F;
        float f11 = Mth.cos(0 + 2.3561945F) * 0.8F;
        float f12 = Mth.sin(0 + 2.3561945F) * 0.8F;
        float f13 = Mth.cos(0 + Maths.QUARTER_PI) * 0.8F;
        float f14 = Mth.sin(0 + Maths.QUARTER_PI) * 0.8F;
        float f15 = Mth.cos(0 + 3.926991F) * 0.8F;
        float f16 = Mth.sin(0 + 3.926991F) * 0.8F;
        float f17 = Mth.cos(0 + 5.4977875F) * 0.8F;
        float f18 = Mth.sin(0 + 5.4977875F) * 0.8F;
        float f19 = Mth.cos(0 + Mth.PI) * 0.4F;
        float f20 = Mth.sin(0 + Mth.PI) * 0.4F;
        float f21 = Mth.cos(0 + 0.0F) * 0.4F;
        float f22 = Mth.sin(0 + 0.0F) * 0.4F;
        float f23 = Mth.cos(0 + (Mth.PI / 2F)) * 0.4F;
        float f24 = Mth.sin(0 + (Mth.PI / 2F)) * 0.4F;
        float f25 = Mth.cos(0 + (Mth.PI * 1.5F)) * 0.4F;
        float f26 = Mth.sin(0 + (Mth.PI * 1.5F)) * 0.4F;
        float f27 = 0.0F;
        float f28 = 0.4999F;
        float f29 = -1.0F + f2;
        float f30 = f4 * 0.5F + f29;
        float f31 = ((int)(renderState.ageInTicks * 20)) % 4 > 1 ? 0.5F : 0.0F;

        // The beam consists of three ordinary textured quads. Copy those
        // semantic vertices directly when Rust owns the whole Vulkan frame;
        // the Java callback remains available only on compatibility routes.
        boolean rustPresentation = net.vulkanic.VulkanicAPI.isVulkanBackendSelected()
                || net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled();
        boolean rustWholeFrame = rustPresentation
                && net.vulkanic.world.WorldRenderRoutePolicy.currentTexturedBillboardRoute().usesRustWholeFrameVulkan();
        if (rustPresentation && !rustWholeFrame) {
            poseStack.popPose();
            throw new IllegalStateException("Selected Vulkan Mungus beam route is unavailable; Java beam geometry is not a fallback");
        }
        // A coincident target produces an undefined direction after normalize()
        // and therefore NaN angles. Keep that malformed primitive out of the
        // explicit Rust semantic ABI; compatibility rendering retains vanilla's
        // original behavior on the private OpenGL route.
        if (rustWholeFrame && (!Float.isFinite(f4) || f4 <= 1.0e-6F
                || !Float.isFinite(f5) || !Float.isFinite(f6))) {
            poseStack.popPose();
            throw new IllegalStateException("Rust whole-frame Mungus beam route rejected non-finite or degenerate direction");
        }
        if (rustWholeFrame) {
            boolean accepted = submitNodeCollector.submitTranslucentTexturedQuadSemantic(
                    poseStack, beamType, BEAM_TEXTURE,
                    new float[] {f19, f4, f20, f19, 0.0F, f20, f21, 0.0F, f22, f21, f4, f22},
                    new float[] {0.4999F, f30, 0.4999F, f29, 0.0F, f29, 0.0F, f30},
                    0xFFFFFFFF, packedLight
            ) && submitNodeCollector.submitTranslucentTexturedQuadSemantic(
                    poseStack, beamType, BEAM_TEXTURE,
                    new float[] {f23, f4, f24, f23, 0.0F, f24, f25, 0.0F, f26, f25, f4, f26},
                    new float[] {0.4999F, f30, 0.4999F, f29, 0.0F, f29, 0.0F, f30},
                    0xFFFFFFFF, packedLight
            ) && submitNodeCollector.submitTranslucentTexturedQuadSemantic(
                    poseStack, beamType, BEAM_TEXTURE,
                    new float[] {f11, f4, f12, f13, f4, f14, f17, f4, f18, f15, f4, f16},
                    new float[] {0.5F, f31 + 0.5F, 1.0F, f31 + 0.5F, 1.0F, f31, 0.5F, f31},
                    0xFFFFFFFF, packedLight
            );
            if (!accepted) {
                throw new IllegalStateException("Rust whole-frame Mungus beam route rejected semantic textured quads");
            }
            poseStack.popPose();
            return;
        }

        // Compatibility routes retain the original custom-geometry producer.
        submitNodeCollector.submitCustomGeometrySemantic(poseStack, beamType, (pose, vertexConsumer) -> {
            Matrix4f matrix4f = pose.pose();
            Matrix3f matrix3f = pose.normal();
            
            vertex(vertexConsumer, matrix4f, matrix3f, f19, f4, f20, j, k, l, 0.4999F, f30);
            vertex(vertexConsumer, matrix4f, matrix3f, f19, 0.0F, f20, j, k, l, 0.4999F, f29);
            vertex(vertexConsumer, matrix4f, matrix3f, f21, 0.0F, f22, j, k, l, 0.0F, f29);
            vertex(vertexConsumer, matrix4f, matrix3f, f21, f4, f22, j, k, l, 0.0F, f30);
            vertex(vertexConsumer, matrix4f, matrix3f, f23, f4, f24, j, k, l, 0.4999F, f30);
            vertex(vertexConsumer, matrix4f, matrix3f, f23, 0.0F, f24, j, k, l, 0.4999F, f29);
            vertex(vertexConsumer, matrix4f, matrix3f, f25, 0.0F, f26, j, k, l, 0.0F, f29);
            vertex(vertexConsumer, matrix4f, matrix3f, f25, f4, f26, j, k, l, 0.0F, f30);
            
            vertex(vertexConsumer, matrix4f, matrix3f, f11, f4, f12, j, k, l, 0.5F, f31 + 0.5F);
            vertex(vertexConsumer, matrix4f, matrix3f, f13, f4, f14, j, k, l, 1.0F, f31 + 0.5F);
            vertex(vertexConsumer, matrix4f, matrix3f, f17, f4, f18, j, k, l, 1.0F, f31);
            vertex(vertexConsumer, matrix4f, matrix3f, f15, f4, f16, j, k, l, 0.5F, f31);
        });
        
        poseStack.popPose();
    }

    private static void vertex(VertexConsumer p_229108_0_, Matrix4f p_229108_1_, Matrix3f p_229108_2_,
            float p_229108_3_, float p_229108_4_, float p_229108_5_, int p_229108_6_, int p_229108_7_, int p_229108_8_,
            float p_229108_9_, float p_229108_10_) {
        org.joml.Vector4f pos = new org.joml.Vector4f(p_229108_3_, p_229108_4_, p_229108_5_, 1.0F);
        pos.mul(p_229108_1_);
        p_229108_0_.addVertex(pos.x, pos.y, pos.z)
                .setUv(p_229108_9_, p_229108_10_)
                .setColor(p_229108_6_, p_229108_7_, p_229108_8_, BEAM_ALPHA);
    }
}
