package com.github.alexthe666.alexsmobs.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * Render state for EntityKomodoDragon
 * Holds animation data extracted from the entity for rendering
 */
public class KomodoDragonRenderState extends LivingEntityRenderState {
    public float prevJostleAngle;
    public float jostleAngle;
    public float prevJostleProgress;
    public float jostleProgress;
    public float prevSitProgress;
    public float sitProgress;
    public boolean isSaddled;
    public boolean isMaid;
    public boolean isBaby;
}
