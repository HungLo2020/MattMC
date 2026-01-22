package com.github.alexthe666.alexsmobs.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.item.ItemStack;

public class ToucanRenderState extends LivingEntityRenderState {
    public float flyProgress;
    public float peckProgress;
    public boolean isSam;
    public boolean isGolden;
    public boolean isEnchanted;
    public int variant;
    public ItemStack heldItem = ItemStack.EMPTY;
}
