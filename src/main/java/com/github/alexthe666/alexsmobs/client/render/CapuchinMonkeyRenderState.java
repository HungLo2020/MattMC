package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.citadel.animation.Animation;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;

public class CapuchinMonkeyRenderState extends LivingEntityRenderState {
    public float sitProgress;
    public boolean hasDart;
    public int variant;
    public Animation currentAnimation = null;
    public int animationTick = 0;
    public boolean isBaby;
    public Entity vehicle;
    public boolean isPassenger;
}
