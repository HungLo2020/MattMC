package net.alexsmobs.client.render.layer;

import net.alexsmobs.client.model.ModelCachalotWhale;
import net.alexsmobs.client.render.RenderCachalotWhale;
import net.alexsmobs.client.render.state.CachalotWhaleRenderState;
import net.blaze3d.vertex.PoseStack;
import net.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

public class LayerCachalotWhaleCapturedSquid  extends RenderLayer<CachalotWhaleRenderState, ModelCachalotWhale> {

    public LayerCachalotWhaleCapturedSquid(RenderCachalotWhale render) {
        super(render);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, CachalotWhaleRenderState renderState, float f, float g) {
        if(renderState.hasCaughtSquid){
            EntityRenderState squid = renderState.caughtSquidState;
            if(squid != null){
                boolean rightSquid = !renderState.isHoldingSquidLeft;
                float riderRot = squid instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState living
                    ? living.yRot : 0.0F;
                poseStack.pushPose();
                translateToPouch(poseStack);
                poseStack.translate(rightSquid ? -1.2F : 1.2F, 0, -3.4F);
                poseStack.mulPose(Axis.ZP.rotationDegrees(180F));
                poseStack.mulPose(Axis.YP.rotationDegrees(riderRot + (rightSquid ? -90F : 90F)));
                // The nested entity is copied state and submitted through the same dispatcher
                // route as top-level entities; no live entity, model, or Java buffer is retained.
                var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
                if (net.minecraft.client.renderer.entity.EntityRenderDispatcher.isSemanticSubmission()) {
                    dispatcher.submitSemantic(squid, renderState.cameraRenderState, 0, 0, 0, poseStack, submitNodeCollector);
                } else {
                    // Compatibility OpenGL keeps the ordinary dispatcher path;
                    // the selected Rust Vulkan route always takes submitSemantic above.
                    Minecraft.getInstance().getEntityRenderDispatcher().submit(
                        squid, renderState.cameraRenderState, 0, 0, 0, poseStack, submitNodeCollector);
                }
                poseStack.popPose();
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
