package net.alexsmobs.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class MungusRenderState extends LivingEntityRenderState {
    public BlockPos beamTarget;
    public BlockState mushroomState;
    public int mushroomCount;
    public boolean altOrderMushroom;
    public boolean isReverting;
    public float swellProgress;
    public float prevSwellProgress;
    public double x;
    public double y;
    public double z;
}
