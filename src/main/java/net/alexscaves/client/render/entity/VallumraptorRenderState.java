package net.alexscaves.client.render.entity;

import net.citadel.animation.Animation;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class VallumraptorRenderState extends LivingEntityRenderState {
    public float leapProgress;
    public float runProgress;
    public float relaxedProgress;
    public float hideProgress;
    public float tailYawRadians; // Pre-calculated: wrapDegrees(tailYaw - yaw) / 57.295776F
    public float puzzledHeadYRot;
    public int altSkin;
    public boolean isRunning;
    public boolean isLeaping;
    public boolean isElder;
    public Animation animation;
    public int animationTick;
}
