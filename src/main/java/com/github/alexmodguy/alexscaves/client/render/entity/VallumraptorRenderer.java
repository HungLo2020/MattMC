package com.github.alexmodguy.alexscaves.client.render.entity;

import com.github.alexmodguy.alexscaves.client.model.VallumraptorModel;
import com.github.alexmodguy.alexscaves.server.entity.living.VallumraptorEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class VallumraptorRenderer extends MobRenderer<VallumraptorEntity, VallumraptorRenderState, VallumraptorModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/vallumraptor.png");
    private static final ResourceLocation TEXTURE_ELDER = ResourceLocation.withDefaultNamespace("textures/entity/vallumraptor_elder.png");
    private static final ResourceLocation TEXTURE_ALAN = ResourceLocation.withDefaultNamespace("textures/entity/vallumraptor_alan.png");
    private static final ResourceLocation TEXTURE_ALAN_ELDER = ResourceLocation.withDefaultNamespace("textures/entity/vallumraptor_alan_elder.png");
    private static final ResourceLocation TEXTURE_RETRO = ResourceLocation.withDefaultNamespace("textures/entity/vallumraptor_retro.png");
    private static final ResourceLocation TEXTURE_RETRO_ELDER = ResourceLocation.withDefaultNamespace("textures/entity/vallumraptor_retro_elder.png");
    private static final ResourceLocation TEXTURE_TECTONIC = ResourceLocation.withDefaultNamespace("textures/entity/vallumraptor_tectonic.png");
    private static final ResourceLocation TEXTURE_TECTONIC_ELDER = ResourceLocation.withDefaultNamespace("textures/entity/vallumraptor_tectonic_elder.png");

    public VallumraptorRenderer(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new VallumraptorModel(), 0.3F);
        // TODO: Fix ItemLayer to work with render state system
    }

    @Override
    public VallumraptorRenderState createRenderState() {
        return new VallumraptorRenderState();
    }

    @Override
    public void extractRenderState(VallumraptorEntity entity, VallumraptorRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        // Populate custom render state fields
        renderState.altSkin = entity.getAltSkin();
        renderState.isElder = entity.isElder();
        renderState.isRunning = entity.isRunning();
        renderState.isLeaping = entity.isLeaping();
        renderState.leapProgress = entity.getLeapProgress(partialTick);
        renderState.runProgress = entity.getRunProgress(partialTick);
        renderState.relaxedProgress = entity.getRelaxedProgress(partialTick);
        renderState.hideProgress = entity.getHideProgress(partialTick);
        renderState.tailYaw = entity.getTailYaw(partialTick);
        renderState.puzzledHeadYRot = entity.getPuzzledHeadRot(partialTick);
        renderState.animation = entity.getAnimation();
        renderState.animationTick = entity.getAnimationTick();
        
        // Set alpha based on hide progress
        float alpha = 1.0F - 0.9F * renderState.hideProgress;
        this.model.setAlpha(alpha);
        
        // Scale for elder
        if (renderState.isElder) {
            renderState.scale = 1.1F;
        }
    }

    @Override
    public ResourceLocation getTextureLocation(VallumraptorRenderState state) {
        boolean isAlan = state.nameTag != null && "alan".equalsIgnoreCase(state.nameTag);
        if (isAlan) {
            return state.isElder ? TEXTURE_ALAN_ELDER : TEXTURE_ALAN;
        } else if (state.altSkin == 1) {
            return state.isElder ? TEXTURE_RETRO_ELDER : TEXTURE_RETRO;
        } else if (state.altSkin == 2) {
            return state.isElder ? TEXTURE_TECTONIC_ELDER : TEXTURE_TECTONIC;
        } else {
            return state.isElder ? TEXTURE_ELDER : TEXTURE;
        }
    }
}

