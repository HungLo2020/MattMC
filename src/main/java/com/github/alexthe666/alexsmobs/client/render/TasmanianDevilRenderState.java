package com.github.alexthe666.alexsmobs.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import com.github.alexthe666.citadel.animation.Animation;

/**
 * Render state for EntityTasmanianDevil
 * Holds animation data extracted from the entity for rendering
 */
public class TasmanianDevilRenderState extends LivingEntityRenderState {
    public float prevBaskProgress;
    public float prevSitProgress;
    public float baskProgress;
    public float sitProgress;
    public int animationTick;
    public Animation currentAnimation;
    public boolean isBaby;
}
