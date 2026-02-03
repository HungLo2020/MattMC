package net.alexsmobs.client.render;

import net.citadel.animation.Animation;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class OrcaRenderState extends LivingEntityRenderState {
    public int variant;
    public int animationTick;
    public Animation currentAnimation;
    public double xd;
    public double zd;
}
