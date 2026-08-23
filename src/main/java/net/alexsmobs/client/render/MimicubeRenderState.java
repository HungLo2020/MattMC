package net.alexsmobs.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

public class MimicubeRenderState extends LivingEntityRenderState {
    public final ItemStackRenderState mainHandItem = new ItemStackRenderState();
    public final ItemStackRenderState offHandItem = new ItemStackRenderState();
    public float squishFactor;
    public float squishAmount;
}
