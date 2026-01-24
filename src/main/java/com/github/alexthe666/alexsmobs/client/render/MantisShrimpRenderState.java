package com.github.alexthe666.alexsmobs.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.item.ItemStack;

public class MantisShrimpRenderState extends LivingEntityRenderState {
    public float rightEyePitch;
    public float rightEyeYaw;
    public float leftEyePitch;
    public float leftEyeYaw;
    public float inWaterProgress;
    public float punchProgress;
    public boolean isBaby;
    public int variant;
    public ItemStack mainHandItem = ItemStack.EMPTY;
    public boolean isLeftHanded;
}
