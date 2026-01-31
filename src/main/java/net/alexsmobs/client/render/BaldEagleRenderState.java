package net.alexsmobs.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;

public class BaldEagleRenderState extends LivingEntityRenderState {
    public boolean hasCap;
    public boolean isPassenger;
    public Entity vehicle;
    public float flyProgress;
    public float attackProgress;
    public float tackleProgress;
    public float swoopProgress;
    public float flapAmount;
    public float birdPitch;
    public float sitProgress;
}
