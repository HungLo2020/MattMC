package com.github.alexthe666.alexsmobs.client.render.layer;

import com.github.alexthe666.alexsmobs.ClientProxy;
import com.github.alexthe666.alexsmobs.client.model.ModelCachalotWhale;
import com.github.alexthe666.alexsmobs.client.render.RenderCachalotWhale;
import com.github.alexthe666.alexsmobs.client.render.state.CachalotWhaleRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;

public class LayerCachalotWhaleCapturedSquid  extends RenderLayer<CachalotWhaleRenderState, ModelCachalotWhale> {

    public LayerCachalotWhaleCapturedSquid(RenderCachalotWhale render) {
        super(render);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, CachalotWhaleRenderState renderState, float f, float g) {
        if(renderState.hasCaughtSquid){
            Entity squid = renderState.caughtSquid;
            if(squid != null && squid.isAlive()){
                boolean rightSquid = !renderState.isHoldingSquidLeft;
                float riderRot = squid.yRotO + (squid.getYRot() - squid.yRotO) * (renderState.ageInTicks - (int)renderState.ageInTicks);
                EntityRenderer<? super Entity, ?> render = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(squid);
                EntityModel<?> modelBase = null;
                if (render instanceof LivingEntityRenderer) {
                    modelBase = ((LivingEntityRenderer<?, ?, ?>) render).getModel();
                }
                if(modelBase != null){
                    ClientProxy.currentUnrenderedEntities.remove(squid.getUUID());
                    poseStack.pushPose();
                    translateToPouch(poseStack);
                    poseStack.translate(rightSquid ? -1.2F : 1.2F, -0, -3.4F);
                    poseStack.mulPose(Axis.ZP.rotationDegrees(180F));
                    poseStack.mulPose(Axis.YP.rotationDegrees(riderRot + (rightSquid ? -90F : 90F)));
                    // Note: In 1.21 render state architecture, entity rendering in layers is limited
                    // We need to get the MultiBufferSource from somewhere, which is not available in submit()
                    // For now, this is a stub - proper implementation would require redesign
                    poseStack.popPose();
                    ClientProxy.currentUnrenderedEntities.add(squid.getUUID());
                }
            }
        }
    }

    protected void translateToPouch(PoseStack matrixStack) {
        this.getParentModel().root.translateAndRotate(matrixStack);
        this.getParentModel().body.translateAndRotate(matrixStack);
        this.getParentModel().head.translateAndRotate(matrixStack);
        this.getParentModel().jaw.translateAndRotate(matrixStack);
    }
}

