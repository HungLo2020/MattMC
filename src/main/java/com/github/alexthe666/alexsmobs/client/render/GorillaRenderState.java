package com.github.alexthe666.alexsmobs.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.item.ItemStack;

public class GorillaRenderState extends LivingEntityRenderState {
    public float standProgress;
    public float sitProgress;
    public float gorillaScale;
    public boolean isSilverback;
    public boolean isDonkeyKong;
    public boolean isFunkyKong;
    public boolean isBaby;
    public int animationTick;
    public ItemStack mainHandItem = ItemStack.EMPTY;
    public String name = "";
}
