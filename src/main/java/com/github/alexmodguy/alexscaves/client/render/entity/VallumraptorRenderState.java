package com.github.alexmodguy.alexscaves.client.render.entity;

import com.github.alexthe666.citadel.animation.Animation;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class VallumraptorRenderState extends LivingEntityRenderState {
    public float leapProgress;
    public float runProgress;
    public float relaxedProgress;
    public float hideProgress;
    public float tailYaw;
    public float puzzledHeadYRot;
    public int altSkin;
    public boolean isRunning;
    public boolean isLeaping;
    public boolean isElder;
    public Animation animation;
    public int animationTick;
}
