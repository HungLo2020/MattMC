package com.github.alexthe666.alexsmobs.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Render state for crocodile entity containing animation and appearance data.
 * Used in Minecraft 1.21's render state architecture to separate rendering logic from entity logic.
 */
public class CrocodileRenderState extends LivingEntityRenderState {
    public float groundProgress;
    public float swimProgress;
    public float baskingProgress;
    public float grabProgress;
    public int baskingType;
    public boolean isInWater;
    public boolean isDesert;
    public boolean isCrowned;
    public boolean isBaby;
}
