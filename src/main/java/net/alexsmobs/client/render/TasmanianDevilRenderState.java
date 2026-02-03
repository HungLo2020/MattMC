package net.alexsmobs.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.citadel.animation.Animation;

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
