package com.github.alexthe666.alexsmobs.client.render.state;

import com.github.alexthe666.citadel.animation.Animation;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class FrilledSharkRenderState extends LivingEntityRenderState {
    public float onLandProgress;
    public float prevOnLandProgress;
    public boolean isDepressurized;
    public boolean isKaiju;
    public Animation currentAnimation;
    public int animationTick;
}
