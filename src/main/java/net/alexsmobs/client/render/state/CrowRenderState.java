package net.alexsmobs.client.render.state;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

public class CrowRenderState extends LivingEntityRenderState {
    public final ItemStackRenderState heldItem = new ItemStackRenderState();
    public float flyProgress;
    public float attackProgress;
    public float sitProgress;
    public boolean hasItemInBeak;
}
