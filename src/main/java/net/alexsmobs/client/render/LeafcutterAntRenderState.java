package net.alexsmobs.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class LeafcutterAntRenderState extends LivingEntityRenderState {
    public boolean hasLeaf;
    public boolean isQueen;
    public boolean isAngry;
    public float antScale;
    public Direction attachmentFacing = Direction.DOWN;
    public float attachChangeProgress;
    public float prevAttachChangeProgress;
    public BlockState leafHarvestedState;
    public BlockPos leafHarvestedPos;
    public int animationTick;
    public int id;
}
