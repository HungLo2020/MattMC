package net.alexsmobs.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.Direction;

public class SugarGliderRenderState extends LivingEntityRenderState {
    public float glideProgress;
    public float forageProgress;
    public float sitProgress;
    public float attachChangeProgress;
    public Direction attachmentFacing = Direction.DOWN;
    public Direction prevAttachDir = Direction.DOWN;
    public boolean isPassenger;
}
