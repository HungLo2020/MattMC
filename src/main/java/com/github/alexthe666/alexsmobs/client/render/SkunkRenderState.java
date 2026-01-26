package com.github.alexthe666.alexsmobs.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Render state for EntitySkunk
 * Holds animation data extracted from the entity for rendering
 */
public class SkunkRenderState extends LivingEntityRenderState {
    public float sprayProgress;
    public float prevSprayProgress;
}
