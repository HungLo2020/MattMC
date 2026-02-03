package net.alexsmobs.client.render;

import net.alexsmobs.entity.EntityBunfungus;
import net.citadel.animation.Animation;
import net.citadel.animation.IAnimatedEntity;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

public class BunfungusRenderState extends LivingEntityRenderState implements IAnimatedEntity {
    public float jumpProgress;
    public float reboundProgress;
    public float sleepProgress;
    public float interestedProgress;
    public int transformsIn;
    public int prevTransformTime;
    public boolean isSleeping;
    public final ItemStackRenderState mainHandItem = new ItemStackRenderState();
    public Animation currentAnimation;
    public int animationTick;
    
    @Override
    public int getAnimationTick() {
        return animationTick;
    }
    
    @Override
    public void setAnimationTick(int tick) {
        this.animationTick = tick;
    }
    
    @Override
    public Animation getAnimation() {
        return currentAnimation;
    }
    
    @Override
    public void setAnimation(Animation animation) {
        this.currentAnimation = animation;
    }
    
    @Override
    public Animation[] getAnimations() {
        return EntityBunfungus.ANIMATIONS;
    }
}
