package net.alexsmobs.client.render.state;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class GiantSquidRenderState extends LivingEntityRenderState {
    public float squidPitch;
    public float depressurization;
    public float grabProgress;
    public float dryProgress;
    public float capturedProgress;
    public boolean overrideBodyRot;
    public boolean isGrabbing;
    public boolean isCaptured;
    public boolean isBlue;
    public float[][] ringBuffer = new float[64][2];
    public int ringBufferIndex = -1;
}
