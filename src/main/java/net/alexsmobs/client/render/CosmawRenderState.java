package net.alexsmobs.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

public class CosmawRenderState extends LivingEntityRenderState {
    public float clutchProgress;
    public float openProgress;
    public float cosmawPitch;
    public float biteProgress;
    public final ItemStackRenderState mainHandItem = new ItemStackRenderState();
}
