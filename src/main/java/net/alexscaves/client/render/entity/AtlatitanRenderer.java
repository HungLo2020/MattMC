package net.alexscaves.client.render.entity;

import net.alexscaves.client.model.AtlatitanModel;
import net.alexscaves.server.entity.living.AtlatitanEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class AtlatitanRenderer extends MobRenderer<AtlatitanEntity, AtlatitanRenderState, AtlatitanModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/atlatitan.png");
    private static final ResourceLocation TEXTURE_RETRO = ResourceLocation.withDefaultNamespace("textures/entity/atlatitan_retro.png");
    private static final ResourceLocation TEXTURE_TECTONIC = ResourceLocation.withDefaultNamespace("textures/entity/atlatitan_tectonic.png");

    public AtlatitanRenderer(EntityRendererProvider.Context renderManagerIn) {
        super(renderManagerIn, new AtlatitanModel(), 4.0F);
    }

    @Override
    public AtlatitanRenderState createRenderState() {
        return new AtlatitanRenderState();
    }

    @Override
    public void extractRenderState(AtlatitanEntity entity, AtlatitanRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.altSkin = entity.getAltSkin();
        renderState.danceProgress = entity.getDanceProgress(partialTick);
        renderState.danceAmount = entity.getDanceProgress(partialTick);
        renderState.neckXRot = entity.neckXRot;
        renderState.neckYRot = entity.neckYRot;
        renderState.tailXRot = entity.tailXRot;
        renderState.tailYRot = entity.tailYRot;
        // Override parent's walkAnimation values with custom entity tracking
        renderState.walkAnimationPos = entity.getWalkAnimPosition(partialTick);
        renderState.walkAnimationSpeed = entity.getWalkAnimSpeed(partialTick);
        renderState.legBackAmount = entity.getLegBackAmount(partialTick);
        renderState.raiseArmsAmount = entity.getRaiseArmsAmount(partialTick);
        renderState.animation = entity.getAnimation();
        renderState.animationTick = entity.getAnimationTick();
        
        // Calculate neck and tail part angles for proper multi-segment articulation
        renderState.neckPart1YawAngle = entity.neckPart1.calculateAnimationAngle(partialTick, false);
        renderState.neckPart1PitchAngle = entity.neckPart1.calculateAnimationAngle(partialTick, true);
        renderState.neckPart2YawAngle = entity.neckPart2.calculateAnimationAngle(partialTick, false);
        renderState.neckPart2PitchAngle = entity.neckPart2.calculateAnimationAngle(partialTick, true);
        renderState.neckPart3PitchAngle = entity.neckPart3.calculateAnimationAngle(partialTick, true);
        renderState.headPartYawAngle = entity.headPart.calculateAnimationAngle(partialTick, false);
        renderState.headPartPitchAngle = entity.headPart.calculateAnimationAngle(partialTick, true);
        renderState.tailPart1YawAngle = entity.tailPart1.calculateAnimationAngle(partialTick, false);
        renderState.tailPart1PitchAngle = entity.tailPart1.calculateAnimationAngle(partialTick, true);
        renderState.tailPart2YawAngle = entity.tailPart2.calculateAnimationAngle(partialTick, false);
        renderState.tailPart2PitchAngle = entity.tailPart2.calculateAnimationAngle(partialTick, true);
        renderState.tailPart3YawAngle = entity.tailPart3.calculateAnimationAngle(partialTick, false);
        renderState.tailPart3PitchAngle = entity.tailPart3.calculateAnimationAngle(partialTick, true);
        renderState.headPitch = renderState.xRot; // from LivingEntityRenderState
        renderState.isFakeEntity = entity.isFakeEntity();
    }

    @Override
    public ResourceLocation getTextureLocation(AtlatitanRenderState state) {
        return state.altSkin == 2 ? TEXTURE_TECTONIC : state.altSkin == 1 ? TEXTURE_RETRO : TEXTURE;
    }
}
