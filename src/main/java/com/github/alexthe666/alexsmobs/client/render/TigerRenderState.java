package com.github.alexthe666.alexsmobs.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Render state for tiger entity containing animation and appearance data.
 * Used in Minecraft 1.21's render state architecture to separate rendering logic from entity logic.
 */
public class TigerRenderState extends LivingEntityRenderState {
    public float sitProgress;
    public float sleepProgress;
    public float holdProgress;
    public float stealthProgress;
    public boolean isWhite;
    public boolean isSitting;
    public boolean isSleeping;
    public boolean isRunning;
    public boolean isStealth;
    public boolean isHolding;
    public boolean isInWater;
    public boolean isBaby;
    public int remainingPersistentAngerTime;
    public int entityId;
}
