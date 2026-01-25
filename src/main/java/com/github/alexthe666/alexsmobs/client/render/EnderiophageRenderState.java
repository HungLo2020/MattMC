package com.github.alexthe666.alexsmobs.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class EnderiophageRenderState extends LivingEntityRenderState {
    public float phageScale = 1.0F;
    public float prevPhageScale = 1.0F;
    public float phagePitch;
    public float prevPhagePitch;
    public float tentacleAngle;
    public float lastTentacleAngle;
    public float phageRotation;
    public float flyProgress;
    public float prevFlyProgress;
    public int passengerIndex = 0;
    public boolean isPassenger;
    public boolean isMissingEye;
    public int variant;
    public float yBodyRot;
    public float yHeadRot;
    public float xHeadRot;
}
