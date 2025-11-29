package net.matt.quantize.entities.layer;

import net.matt.quantize.Quantize;
import net.matt.quantize.entities.models.TremorsaurusModel;
import net.matt.quantize.entities.render.TremorsaurusRenderer;
import net.matt.quantize.entities.mobs.SubterranodonEntity;
import net.matt.quantize.entities.mobs.TremorsaurusEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.Entity;

public class TremorsaurusHeldMobLayer extends RenderLayer<TremorsaurusEntity, TremorsaurusModel> {

    public TremorsaurusHeldMobLayer(TremorsaurusRenderer render) {
        super(render);
    }

    public void render(PoseStack matrixStackIn, MultiBufferSource bufferIn, int packedLightIn, TremorsaurusEntity tremorsaurus, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        Entity heldMob = tremorsaurus.getHeldMob();
        if (heldMob != null) {
            float riderRot = heldMob.yRotO + (heldMob.getYRot() - heldMob.yRotO) * partialTicks;
            boolean holdSideways = heldMob.getBbHeight() > heldMob.getBbWidth() + 0.2F;
            Quantize.PROXY.releaseRenderingEntity(heldMob.getUUID());
            matrixStackIn.pushPose();
            getParentModel().translateToMouth(matrixStackIn);
            matrixStackIn.translate(0, heldMob.getBbWidth() * 0.35F + 0.2F, -1F);
            if (heldMob instanceof SubterranodonEntity subterranodon) {
                matrixStackIn.translate(0, subterranodon.getFlyProgress(partialTicks) * -0.5F, 0);
            }
            if (holdSideways) {
                matrixStackIn.mulPose(Axis.ZP.rotationDegrees(90F));
            }
            matrixStackIn.mulPose(Axis.ZP.rotationDegrees(180F));
            matrixStackIn.mulPose(Axis.YP.rotationDegrees(riderRot + 180F));
            if (!holdSideways) {
                matrixStackIn.mulPose(Axis.YP.rotationDegrees(90F));
            }
            matrixStackIn.translate(0, -heldMob.getBbHeight() * 0.5F, 0);
            if (!Quantize.PROXY.isFirstPersonPlayer(heldMob)) {
                renderEntity(heldMob, 0, 0, 0, 0, partialTicks, matrixStackIn, bufferIn, packedLightIn);
            }
            matrixStackIn.popPose();
            Quantize.PROXY.blockRenderingEntity(heldMob.getUUID());
        }
    }

    public <E extends Entity> void renderEntity(E entityIn, double x, double y, double z, float yaw, float partialTicks, PoseStack matrixStack, MultiBufferSource bufferIn, int packedLight) {
        EntityRenderer<? super E> render = null;
        EntityRenderDispatcher manager = Minecraft.getInstance().getEntityRenderDispatcher();
        try {
            render = manager.getRenderer(entityIn);

            if (render != null) {
                try {
                    render.render(entityIn, yaw, partialTicks, matrixStack, bufferIn, packedLight);
                } catch (Throwable throwable1) {
                    throw new ReportedException(CrashReport.forThrowable(throwable1, "Rendering entity in world"));
                }
            }
        } catch (Throwable throwable3) {
            CrashReport crashreport = CrashReport.forThrowable(throwable3, "Rendering entity in world");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Entity being rendered");
            entityIn.fillCrashReportCategory(crashreportcategory);
            CrashReportCategory crashreportcategory1 = crashreport.addCategory("Renderer details");
            crashreportcategory1.setDetail("Assigned renderer", render);
            crashreportcategory1.setDetail("Rotation", Float.valueOf(yaw));
            crashreportcategory1.setDetail("Delta", Float.valueOf(partialTicks));
            throw new ReportedException(crashreport);
        }
    }
}
