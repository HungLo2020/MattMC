package net.alexsmobs.client.render;

import net.alexsmobs.client.render.state.CachalotEchoRenderState;
import net.alexsmobs.entity.EntityCachalotEcho;
import net.blaze3d.vertex.PoseStack;
import net.blaze3d.vertex.VertexConsumer;
import net.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class RenderCachalotEcho extends EntityRenderer<EntityCachalotEcho, CachalotEchoRenderState> {
    private static final ResourceLocation TEXTURE_0 = ResourceLocation
            .withDefaultNamespace("textures/entity/cachalot/whale_echo_0.png");
    private static final ResourceLocation TEXTURE_1 = ResourceLocation
            .withDefaultNamespace("textures/entity/cachalot/whale_echo_1.png");
    private static final ResourceLocation TEXTURE_2 = ResourceLocation
            .withDefaultNamespace("textures/entity/cachalot/whale_echo_2.png");
    private static final ResourceLocation TEXTURE_3 = ResourceLocation
            .withDefaultNamespace("textures/entity/cachalot/whale_echo_3.png");
    private static final ResourceLocation GREEN_TEXTURE_0 = ResourceLocation
            .withDefaultNamespace("textures/entity/cachalot/whale_echo_0_green.png");
    private static final ResourceLocation GREEN_TEXTURE_1 = ResourceLocation
            .withDefaultNamespace("textures/entity/cachalot/whale_echo_1_green.png");
    private static final ResourceLocation GREEN_TEXTURE_2 = ResourceLocation
            .withDefaultNamespace("textures/entity/cachalot/whale_echo_2_green.png");
    private static final ResourceLocation GREEN_TEXTURE_3 = ResourceLocation
            .withDefaultNamespace("textures/entity/cachalot/whale_echo_3_green.png");

    public RenderCachalotEcho(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn);
    }

    @Override
    public CachalotEchoRenderState createRenderState() {
        return new CachalotEchoRenderState();
    }

    @Override
    public void extractRenderState(EntityCachalotEcho entity, CachalotEchoRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.tickCount = entity.tickCount;
        renderState.isFasterAnimation = entity.isFasterAnimation();
        renderState.isGreen = entity.isGreen();
        renderState.yRot = entity.getYRot();
        renderState.xRot = entity.getXRot();
        renderState.yRotO = entity.yRotO;
        renderState.xRotO = entity.xRotO;
    }

    @Override
    public void submit(CachalotEchoRenderState renderState, PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
        poseStack.pushPose();
        poseStack.translate(0.0D, 0.25F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(renderState.yRot - 90.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(renderState.xRot));
        poseStack.translate(0.0D, 0.0D, 0.4D);
        int arcs = Mth.clamp(Mth.floor(renderState.tickCount / 5F), 1, 4);
        boolean rustWholeFrame = net.vulkanic.world.WorldRenderRoutePolicy
                .currentTexturedBillboardRoute().usesRustWholeFrameVulkan();
        for (int i = 0; i < arcs; i++) {
            poseStack.pushPose();
            poseStack.translate(0.0D, 0.0D, -0.5F * i);
            int age = (i + 1) * 5;
            ResourceLocation texture = renderState.isFasterAnimation
                    ? getEntityTextureFaster(age, renderState.isGreen) : getEntityTexture(age);
            float[] vertices = new float[] {-1.0F, 0.0F, -1.0F, -1.0F, 0.0F, 1.0F,
                    1.0F, 0.0F, 1.0F, 1.0F, 0.0F, -1.0F};
            float[] uvs = new float[] {0.0F, 1.0F, 0.0F, 0.0F, 1.0F, 0.0F, 1.0F, 1.0F};
            boolean accepted;
            if (rustWholeFrame) {
                accepted = submitNodeCollector.submitTexturedQuad(
                        poseStack, RenderType.entityCutoutNoCull(texture), texture,
                        vertices, uvs, 0xFFFFFFFF, 240);
            } else {
                submitNodeCollector.submitCustomGeometry(
                        poseStack, RenderType.entityCutoutNoCull(texture),
                        (pose, consumer) -> emitArc(pose, consumer));
                accepted = true;
            }
            if (rustWholeFrame && !accepted) {
                poseStack.popPose();
                poseStack.popPose();
                throw new IllegalStateException("Rust whole-frame Cachalot Echo route rejected semantic textured quad");
            }
            poseStack.popPose();
        }
        poseStack.popPose();
        super.submit(renderState, poseStack, submitNodeCollector, cameraRenderState);
    }

    public void render(CachalotEchoRenderState renderState, PoseStack matrixStackIn,
            MultiBufferSource bufferIn, int packedLightIn) {
        if (net.vulkanic.bridge.RustGalVulkanWholeFrameMode.enabled()) {
            throw new IllegalStateException("Java Cachalot Echo rendering is unavailable while Rust owns whole-frame presentation");
        }
        matrixStackIn.pushPose();
        matrixStackIn.translate(0.0D, 0.25F, 0.0D);
        matrixStackIn
                .mulPose(Axis.YP.rotationDegrees(renderState.yRot - 90.0F));
        matrixStackIn.mulPose(Axis.XP.rotationDegrees(renderState.xRot));
        int arcs = Mth.clamp(Mth.floor(renderState.tickCount / 5F), 1, 4);
        matrixStackIn.translate(0.0D, 0.0F, 0.4D);
        for (int i = 0; i < arcs; i++) {
            matrixStackIn.pushPose();
            matrixStackIn.translate(0, 0, -0.5F * i);
            renderArc(matrixStackIn, bufferIn, (i + 1) * 5, renderState.isFasterAnimation, renderState.isGreen);
            matrixStackIn.popPose();
        }
        matrixStackIn.popPose();
    }

    private void renderArc(PoseStack matrixStackIn, MultiBufferSource bufferIn, int age, boolean fast, boolean green) {
        matrixStackIn.pushPose();
        ResourceLocation res;
        if (fast) {
            res = getEntityTextureFaster(age, green);
        } else {
            res = getEntityTexture(age);
        }
        emitArc(matrixStackIn.last(), bufferIn.getBuffer(RenderType.entityCutoutNoCull(res)));
        matrixStackIn.popPose();
    }

    private static void emitArc(PoseStack.Pose pose, VertexConsumer consumer) {
        // The texture is selected by the caller; the copied quad uses the
        // stable unit-square coordinates shared by every echo frame.
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();
        drawVertex(matrix, normal, consumer, -1, 0, -1, 0, 0, 1, 0, 1, 240);
        drawVertex(matrix, normal, consumer, -1, 0, 1, 0, 1, 1, 0, 1, 240);
        drawVertex(matrix, normal, consumer, 1, 0, 1, 1, 1, 1, 0, 1, 240);
        drawVertex(matrix, normal, consumer, 1, 0, -1, 1, 0, 1, 0, 1, 240);
    }

    public ResourceLocation getTextureLocation(CachalotEchoRenderState renderState) {
        return TEXTURE_0;
    }

    public static void drawVertex(Matrix4f p_229039_1_, Matrix3f p_229039_2_, VertexConsumer p_229039_3_, int p_229039_4_,
            int p_229039_5_, int p_229039_6_, float p_229039_7_, float p_229039_8_, int p_229039_9_, int p_229039_10_,
            int p_229039_11_, int p_229039_12_) {
        org.joml.Vector3f normal = new org.joml.Vector3f((float) p_229039_9_, (float) p_229039_11_,
                (float) p_229039_10_);
        normal.mul(p_229039_2_);
        org.joml.Vector4f pos = new org.joml.Vector4f((float) p_229039_4_, (float) p_229039_5_, (float) p_229039_6_,
                1.0F);
        pos.mul(p_229039_1_);
        p_229039_3_.addVertex(pos.x, pos.y, pos.z).setColor(255, 255, 255, 255).setUv(p_229039_7_, p_229039_8_)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(p_229039_12_).setNormal(normal.x, normal.y, normal.z);
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getEntityTexture(int age) {
        if (age < 5) {
            return TEXTURE_0;
        } else if (age < 10) {
            return TEXTURE_1;
        } else if (age < 15) {
            return TEXTURE_2;
        } else {
            return TEXTURE_3;
        }
    }

    public ResourceLocation getEntityTextureFaster(int age, boolean green) {
        if (age < 3) {
            return green ? GREEN_TEXTURE_0 : TEXTURE_0;
        } else if (age < 6) {
            return green ? GREEN_TEXTURE_1 : TEXTURE_1;
        } else if (age < 9) {
            return green ? GREEN_TEXTURE_2 : TEXTURE_2;
        } else {
            return green ? GREEN_TEXTURE_3 : TEXTURE_3;
        }
    }
}
