package com.github.alexthe666.alexsmobs.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.item.ItemStack;

public class ElephantRenderState extends LivingEntityRenderState {
    public float sitProgress;
    public float standProgress;
    public boolean tusked;
    public boolean sitting;
    public boolean standing;
    public boolean chested;
    public int carpetColor = -1;
    public ItemStack mainHandItem = ItemStack.EMPTY;
}
