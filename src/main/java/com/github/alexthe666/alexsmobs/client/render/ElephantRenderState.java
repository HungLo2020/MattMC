package com.github.alexthe666.alexsmobs.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

public class ElephantRenderState extends LivingEntityRenderState {
    public float sitProgress;
    public float standProgress;
    public boolean tusked;
    public boolean sitting;
    public boolean standing;
    public boolean chested;
    public int carpetColor = -1;
    public final ItemStackRenderState mainHandItem = new ItemStackRenderState();
}
