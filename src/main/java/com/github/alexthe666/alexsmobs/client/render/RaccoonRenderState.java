package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.citadel.animation.Animation;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.item.DyeColor;

public class RaccoonRenderState extends LivingEntityRenderState {
    public float begProgress;
    public float standProgress;
    public float sitProgress;
    public float washProgress;
    public boolean isRigby;
    public boolean hasColor;
    public DyeColor color;
    public int tickCount;
    public String customName;
    public int animationTick;
    public Animation currentAnimation;
    public int id;
}
