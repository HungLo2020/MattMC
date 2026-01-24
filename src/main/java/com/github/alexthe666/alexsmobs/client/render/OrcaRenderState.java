package com.github.alexthe666.alexsmobs.client.render;

import com.github.alexthe666.citadel.animation.Animation;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class OrcaRenderState extends LivingEntityRenderState {
    public int variant;
    public int animationTick;
    public Animation currentAnimation;
    public double xd;
    public double zd;
}
