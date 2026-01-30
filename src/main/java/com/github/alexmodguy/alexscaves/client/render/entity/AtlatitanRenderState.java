package com.github.alexmodguy.alexscaves.client.render.entity;

import com.github.alexthe666.citadel.animation.Animation;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class AtlatitanRenderState extends LivingEntityRenderState {
    public int altSkin;
    public float danceProgress;
    public float danceAmount;
    public float neckXRot;
    public float neckYRot;
    public float tailXRot;
    public float tailYRot;
    // walkAnimationPos and walkAnimationSpeed inherited from LivingEntityRenderState
    public float legBackAmount;
    public float raiseArmsAmount;
    public Animation animation;
    public int animationTick;
    
    // Pre-calculated neck and tail part angles for proper multi-segment articulation
    public float neckPart1YawAngle;
    public float neckPart1PitchAngle;
    public float neckPart2YawAngle;
    public float neckPart2PitchAngle;
    public float neckPart3PitchAngle;
    public float headPartYawAngle;
    public float headPartPitchAngle;
    public float tailPart1YawAngle;
    public float tailPart1PitchAngle;
    public float tailPart2YawAngle;
    public float tailPart2PitchAngle;
    public float tailPart3YawAngle;
    public float tailPart3PitchAngle;
    public float headPitch;
    public boolean isFakeEntity;
}
