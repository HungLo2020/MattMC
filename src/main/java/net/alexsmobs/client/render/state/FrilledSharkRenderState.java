package net.alexsmobs.client.render.state;

import net.citadel.animation.Animation;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class FrilledSharkRenderState extends LivingEntityRenderState {
    public float onLandProgress;
    public float prevOnLandProgress;
    public boolean isDepressurized;
    public boolean isKaiju;
    public Animation currentAnimation;
    public int animationTick;
}
