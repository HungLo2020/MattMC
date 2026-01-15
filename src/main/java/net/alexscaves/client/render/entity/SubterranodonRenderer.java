package net.alexscaves.client.render.entity;

import net.alexscaves.client.model.SubterranodonModel;
import net.alexscaves.server.entity.living.SubterranodonEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

// TODO: Fix layer to work with render state system
// this.addLayer(new SubterranodonRiderLayer(this));
public class SubterranodonRenderer extends MobRenderer<SubterranodonEntity, SubterranodonRenderState, SubterranodonModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/subterranodon.png");
    private static final ResourceLocation TEXTURE_RETRO = ResourceLocation.withDefaultNamespace("textures/entity/subterranodon_retro.png");
    private static final ResourceLocation TEXTURE_TECTONIC = ResourceLocation.withDefaultNamespace("textures/entity/subterranodon_tectonic.png");

    public SubterranodonRenderer(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new SubterranodonModel(), 0.5F);
    }

    @Override
    public SubterranodonRenderState createRenderState() {
        return new SubterranodonRenderState();
    }

    @Override
    public void extractRenderState(SubterranodonEntity entity, SubterranodonRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        // Populate custom render state fields
        renderState.altSkin = entity.getAltSkin();
        renderState.isFlying = entity.isFlying();
        renderState.isSitting = entity.isInSittingPose();
        renderState.flapProgress = entity.getFlapAmount(partialTick);
        renderState.attackProgress = entity.getBiteProgress(partialTick); // getBiteProgress, not getAttackProgress
        renderState.sitProgress = entity.getSitProgress(partialTick);
        renderState.danceProgress = entity.getDanceProgress(partialTick);
        
        // Calculate animation values properly as done in source model
        float flyProgress = renderState.isFlying ? 1.0F : 0.0F;
        float hoverProgress = entity.getHoverProgress(partialTick) * flyProgress;
        renderState.hoverProgress = hoverProgress;
        
        float flightPitch = entity.getFlightPitch(partialTick);
        float flightRoll = entity.getFlightRoll(partialTick);
        float yaw = entity.yBodyRotO + (entity.yBodyRot - entity.yBodyRotO) * partialTick;
        float tailYaw = entity.getTailYaw(partialTick);
        
        // Pre-calculate animation amounts as in source
        renderState.rollAmount = flightRoll / 57.295776F * flyProgress;
        renderState.pitchAmount = flightPitch / 57.295776F * (flyProgress - hoverProgress);
        renderState.tailYawRadians = net.minecraft.util.Mth.wrapDegrees(tailYaw - yaw) / 57.295776F;
    }

    @Override
    public ResourceLocation getTextureLocation(SubterranodonRenderState state) {
        return state.altSkin == 1 ? TEXTURE_RETRO : state.altSkin == 2 ? TEXTURE_TECTONIC : TEXTURE;
    }
}


