package net.alexsmobs.client.render;

import net.alexsmobs.client.model.ModelCachalotWhale;
import net.alexsmobs.client.render.layer.LayerCachalotWhaleCapturedSquid;
import net.alexsmobs.client.render.state.CachalotWhaleRenderState;
import net.alexsmobs.entity.EntityCachalotPart;
import net.alexsmobs.entity.EntityCachalotWhale;
import net.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class RenderCachalotWhale extends MobRenderer<EntityCachalotWhale, CachalotWhaleRenderState, ModelCachalotWhale> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/cachalot/cachalot_whale.png");
    private static final ResourceLocation TEXTURE_SLEEPING = ResourceLocation.withDefaultNamespace("textures/entity/cachalot/cachalot_whale_sleeping.png");
    private static final ResourceLocation TEXTURE_ALBINO = ResourceLocation.withDefaultNamespace("textures/entity/cachalot/cachalot_whale_albino.png");
    private static final ResourceLocation TEXTURE_ALBINO_SLEEPING = ResourceLocation.withDefaultNamespace("textures/entity/cachalot/cachalot_whale_albino_sleeping.png");

    public RenderCachalotWhale(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new ModelCachalotWhale(), 4.2F);
        this.addLayer(new LayerCachalotWhaleCapturedSquid(this));
    }

    @Override
    public CachalotWhaleRenderState createRenderState() {
        return new CachalotWhaleRenderState();
    }

    @Override
    public void extractRenderState(EntityCachalotWhale entity, CachalotWhaleRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.chargeProgress = entity.prevChargingProgress + (entity.chargeProgress - entity.prevChargingProgress) * partialTick;
        renderState.sleepProgress = entity.prevSleepProgress + (entity.sleepProgress - entity.prevSleepProgress) * partialTick;
        renderState.beachedProgress = entity.prevBeachedProgress + (entity.beachedProgress - entity.prevBeachedProgress) * partialTick;
        renderState.grabProgress = entity.prevGrabProgress + (entity.grabProgress - entity.prevGrabProgress) * partialTick;
        renderState.grabTime = entity.grabTime;
        renderState.isAlbino = entity.isAlbino();
        renderState.isSleeping = entity.isSleeping();
        renderState.isBeached = entity.isBeached();
        renderState.hasCaughtSquid = entity.hasCaughtSquid();
        renderState.caughtSquid = entity.getCaughtSquid();
        renderState.isHoldingSquidLeft = entity.isHoldingSquidLeft();
        // Store movement offsets for model animation
        for (int i = 0; i < renderState.movementOffsets.length; i++) {
            renderState.movementOffsets[i] = entity.getMovementOffsets(i, partialTick);
        }
    }

    protected void scale(EntityCachalotWhale entitylivingbaseIn, PoseStack matrixStackIn, float partialTickTime) {
    }

    public boolean shouldRender(EntityCachalotWhale livingEntityIn, Frustum camera, double camX, double camY, double camZ) {
        if (super.shouldRender(livingEntityIn, camera, camX, camY, camZ)) {
            return true;
        } else {
            for(EntityCachalotPart part : livingEntityIn.whaleParts){
                if(camera.isVisible(part.getBoundingBox())){
                    return true;
                }
            }
            return false;
        }
    }

    public ResourceLocation getTextureLocation(CachalotWhaleRenderState renderState) {
        if(renderState.isAlbino){
            return renderState.isSleeping || renderState.isBeached ? TEXTURE_ALBINO_SLEEPING : TEXTURE_ALBINO;
        }else {
            return renderState.isSleeping || renderState.isBeached  ? TEXTURE_SLEEPING : TEXTURE;
        }
    }
}
