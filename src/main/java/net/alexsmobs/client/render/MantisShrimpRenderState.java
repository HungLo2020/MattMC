package net.alexsmobs.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

public class MantisShrimpRenderState extends LivingEntityRenderState {
    public float rightEyePitch;
    public float rightEyeYaw;
    public float leftEyePitch;
    public float leftEyeYaw;
    public float inWaterProgress;
    public float punchProgress;
    public boolean isBaby;
    public int variant;
    public final ItemStackRenderState mainHandItem = new ItemStackRenderState();
    public boolean isLeftHanded;
}
