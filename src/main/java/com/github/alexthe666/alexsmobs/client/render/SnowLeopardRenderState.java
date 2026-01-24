package com.github.alexthe666.alexsmobs.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import com.github.alexthe666.citadel.animation.Animation;

/**
 * Render state for EntitySnowLeopard
 * Holds animation data extracted from the entity for rendering
 */
public class SnowLeopardRenderState extends LivingEntityRenderState {
    public float sneakProgress;
    public float prevSneakProgress;
    public float tackleProgress;
    public float prevTackleProgress;
    public float sitProgress;
    public float prevSitProgress;
    public float sleepProgress;
    public float prevSleepProgress;
    public boolean isSleeping;
    public int animationTick;
    public Animation currentAnimation;
    public int entityId;
}
