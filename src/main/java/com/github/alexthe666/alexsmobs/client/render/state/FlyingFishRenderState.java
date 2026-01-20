package com.github.alexthe666.alexsmobs.client.render.state;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class FlyingFishRenderState extends LivingEntityRenderState {
    public float prevOnLandProgress;
    public float onLandProgress;
    public float prevFlyProgress;
    public float flyProgress;
    public int variant;
}
