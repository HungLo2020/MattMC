package com.github.alexthe666.alexsmobs.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class AnacondaRenderState extends LivingEntityRenderState {
    public float strangleProgress;
    public boolean yellow;
    public int sheddingTime;
    public float[] ringBuffer = new float[64];
}
