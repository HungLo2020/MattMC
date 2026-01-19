package com.github.alexthe666.alexsmobs.client.render.state;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class CombJellyRenderState extends LivingEntityRenderState {
    public int variant;
    public float jellyScale;
    public float jellyPitch;
    public float prevJellyPitch;
    public float onLandProgress;
    public float prevOnLandProgress;
}
