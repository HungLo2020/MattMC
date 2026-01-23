package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.citadel.animation.Animation;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class GeladaMonkeyRenderState extends LivingEntityRenderState {
    public float sitProgress;
    public boolean isLeader;
    public boolean isAggro;
    public Animation currentAnimation = null;
    public int animationTick = 0;
    public boolean isBaby;
    public float geladaScale = 1.0F;
}
