package net.alexsmobs.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.citadel.animation.Animation;

/**
 * Render state for EntityAnteater
 * Holds animation data extracted from the entity for rendering
 */
public class AnteaterRenderState extends LivingEntityRenderState {
    public float standProgress;
    public float prevStandProgress;
    public float tongueProgress;
    public float prevTongueProgress;
    public float leaningProgress;
    public float prevLeaningProgress;
    public int tickCount;
    public boolean isBaby;
    public boolean isPassenger;
    public int animationTick;
    public Animation currentAnimation;
}
