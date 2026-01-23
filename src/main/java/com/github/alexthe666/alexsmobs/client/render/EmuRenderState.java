package com.github.alexthe666.alexsmobs.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import com.github.alexthe666.citadel.animation.Animation;

/**
 * Render state for EntityEmu
 * Holds animation data extracted from the entity for rendering
 */
public class EmuRenderState extends LivingEntityRenderState {
    public int variant;
    public int animationTick;
    public Animation currentAnimation;
}
