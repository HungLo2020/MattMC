package net.minecraft.client.renderer.blockentity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.SkullBlock.Type;
import net.minecraft.world.level.block.SkullBlock.Types;

@Environment(EnvType.CLIENT)
public class SkullBlockRenderState extends BlockEntityRenderState {
	public float animationProgress;
	public Direction direction = Direction.NORTH;
	public float rotationDegrees;
	public Type skullType = Types.ZOMBIE;
	public RenderType renderType;
}
