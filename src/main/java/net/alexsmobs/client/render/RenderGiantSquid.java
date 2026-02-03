package net.alexsmobs.client.render;

import net.alexsmobs.client.model.ModelGiantSquid;
import net.alexsmobs.client.render.state.GiantSquidRenderState;
import net.alexsmobs.entity.EntityGiantSquid;
import net.alexsmobs.entity.EntityGiantSquidPart;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

public class RenderGiantSquid extends MobRenderer<EntityGiantSquid, GiantSquidRenderState, ModelGiantSquid> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/giant_squid.png");
    private static final ResourceLocation TEXTURE_BLUE = ResourceLocation
            .withDefaultNamespace("textures/entity/giant_squid_blue.png");
    private static final ResourceLocation TEXTURE_DEPRESSURIZED = ResourceLocation
            .withDefaultNamespace("textures/entity/giant_squid_depressurized.png");

    public RenderGiantSquid(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelGiantSquid(), 1F);
        this.addLayer(new LayerDepressurization(this));
    }

    @Override
    public GiantSquidRenderState createRenderState() {
        return new GiantSquidRenderState();
    }

    @Override
    public void extractRenderState(EntityGiantSquid entity, GiantSquidRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.squidPitch = entity.prevSquidPitch + (entity.getSquidPitch() - entity.prevSquidPitch) * partialTick;
        renderState.depressurization = entity.prevDepressurization + (entity.getDepressurization() - entity.prevDepressurization) * partialTick;
        renderState.grabProgress = entity.prevGrabProgress + (entity.grabProgress - entity.prevGrabProgress) * partialTick;
        renderState.dryProgress = entity.prevDryProgress + (entity.dryProgress - entity.prevDryProgress) * partialTick;
        renderState.capturedProgress = entity.prevCapturedProgress + (entity.capturedProgress - entity.prevCapturedProgress) * partialTick;
        renderState.isBlue = entity.isBlue();
        renderState.ringBufferIndex = entity.ringBufferIndex;
        for (int i = 0; i < renderState.ringBuffer.length; i++) {
            renderState.ringBuffer[i][0] = entity.ringBuffer[i][0];
            renderState.ringBuffer[i][1] = entity.ringBuffer[i][1];
        }
    }

    protected float getFlipDegrees(EntityGiantSquid squid) {
        return 0.0F;
    }

    public boolean shouldRender(EntityGiantSquid livingEntityIn, Frustum camera, double camX, double camY,
            double camZ) {
        if (livingEntityIn.isCaptured() && livingEntityIn.isAlive()) {
            return false;
        }
        if (super.shouldRender(livingEntityIn, camera, camX, camY, camZ)) {
            return true;
        } else {
            for (EntityGiantSquidPart part : livingEntityIn.allParts) {
                if (camera.isVisible(part.getBoundingBox())) {
                    return true;
                }
            }
            return false;
        }
    }

    protected void scale(EntityGiantSquid entitylivingbaseIn, PoseStack matrixStackIn, float partialTickTime) {
    }

    public ResourceLocation getTextureLocation(GiantSquidRenderState renderState) {
        return renderState.isBlue ? TEXTURE_BLUE : TEXTURE;
    }

    static class LayerDepressurization extends RenderLayer<GiantSquidRenderState, ModelGiantSquid> {

        public LayerDepressurization(RenderGiantSquid render) {
            super(render);
        }

        @Override
        public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, GiantSquidRenderState renderState, float f, float g) {
            // Note: In 1.21 render state architecture, we need to use the submitNodeCollector
            // The old render() method with MultiBufferSource is no longer available
            // This is a stub for now - proper translucent rendering may require different approach
            float alpha = renderState.depressurization;
            if (alpha > 0.01F) {
                // Translucent overlay rendering would go here
                // In the new architecture, this might need to be handled differently
            }
        }
    }
}
