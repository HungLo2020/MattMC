package com.github.alexmodguy.alexscaves.client.render.entity;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class SubterranodonRenderState extends LivingEntityRenderState {
    public float attackProgress;
    public float sitProgress;
    public float danceProgress;
    public int altSkin;
    public boolean isFlying;
    public boolean isSitting;
    public float flapProgress;
    public float hoverProgress;
    public float rollAmount;     // Pre-calculated: flightRoll / 57.295776F * flyProgress
    public float pitchAmount;    // Pre-calculated: flightPitch / 57.295776F * (flyProgress - hoverProgress)
    public float tailYawRadians; // Pre-calculated: wrapDegrees(tailYaw - yaw) / 57.295776F
}
