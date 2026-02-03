package net.alexsmobs.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.citadel.animation.Animation;

/**
 * Render state for EntityEmu
 * Holds animation data extracted from the entity for rendering
 */
public class EmuRenderState extends LivingEntityRenderState {
    public int variant;
    public int animationTick;
    public Animation currentAnimation;
}
