package com.github.alexthe666.alexsmobs.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class RhinocerosRenderState extends LivingEntityRenderState {
    public int animationTick;
    public int currentAnimationId; // Store animation ID instead of Animation object
    public boolean isAngry;
    public int potionColor;
}
