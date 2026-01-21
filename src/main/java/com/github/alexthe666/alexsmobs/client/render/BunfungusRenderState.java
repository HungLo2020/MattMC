package com.github.alexthe666.alexsmobs.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.item.ItemStack;

public class BunfungusRenderState extends LivingEntityRenderState {
    public float jumpProgress;
    public float reboundProgress;
    public float sleepProgress;
    public float interestedProgress;
    public int transformsIn;
    public int prevTransformTime;
    public boolean isSleeping;
    public ItemStack mainHandItem = ItemStack.EMPTY;
}
