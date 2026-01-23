package com.github.alexthe666.alexsmobs.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.item.ItemStack;

public class CosmawRenderState extends LivingEntityRenderState {
    public float clutchProgress;
    public float openProgress;
    public float cosmawPitch;
    public float biteProgress;
    public ItemStack mainHandItem = ItemStack.EMPTY;
}
