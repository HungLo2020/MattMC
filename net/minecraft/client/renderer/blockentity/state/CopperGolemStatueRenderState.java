package net.minecraft.client.renderer.blockentity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.CopperGolemStatueBlock.Pose;

@Environment(EnvType.CLIENT)
public class CopperGolemStatueRenderState extends BlockEntityRenderState {
	public Pose pose = Pose.STANDING;
	public Direction direction = Direction.NORTH;
}
