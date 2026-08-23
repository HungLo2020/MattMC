package net.alexsmobs.client.render.state;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;

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
    /** Copied semantic state for the captured squid; never retain the live entity. */
    public EntityRenderState caughtSquidState;
    public boolean isHoldingSquidLeft;
    public double[][] movementOffsets = new double[64][3];
    public CameraRenderState cameraRenderState;
}
