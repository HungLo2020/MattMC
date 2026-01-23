package com.github.alexthe666.alexsmobs.client.render.state;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;

public class CachalotWhaleRenderState extends LivingEntityRenderState {
    public float chargeProgress;
    public float sleepProgress;
    public float beachedProgress;
    public float grabProgress;
    public int grabTime;
    public boolean isAlbino;
    public boolean isSleeping;
    public boolean isBeached;
    public boolean hasCaughtSquid;
    public Entity caughtSquid;
    public boolean isHoldingSquidLeft;
    public double[][] movementOffsets = new double[64][3];
}
