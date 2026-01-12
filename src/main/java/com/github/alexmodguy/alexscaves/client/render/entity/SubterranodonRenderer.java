package com.github.alexmodguy.alexscaves.client.render.entity;

import com.github.alexmodguy.alexscaves.client.model.SubterranodonModel;
import com.github.alexmodguy.alexscaves.server.entity.living.SubterranodonEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

// TODO: Fix layer to work with render state system
// this.addLayer(new SubterranodonRiderLayer(this));
public class SubterranodonRenderer extends MobRenderer<SubterranodonEntity, SubterranodonRenderState, SubterranodonModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("alexscaves", "textures/entity/subterranodon.png");
    private static final ResourceLocation TEXTURE_RETRO = ResourceLocation.fromNamespaceAndPath("alexscaves", "textures/entity/subterranodon_retro.png");
    private static final ResourceLocation TEXTURE_TECTONIC = ResourceLocation.fromNamespaceAndPath("alexscaves", "textures/entity/subterranodon_tectonic.png");

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
        renderState.attackProgress = entity.getAttackProgress(partialTick);
        renderState.sitProgress = entity.getSitProgress(partialTick);
        renderState.danceProgress = entity.getDanceProgress(partialTick);
    }

    @Override
    public ResourceLocation getTextureLocation(SubterranodonRenderState state) {
        return state.altSkin == 1 ? TEXTURE_RETRO : state.altSkin == 2 ? TEXTURE_TECTONIC : TEXTURE;
    }
}


