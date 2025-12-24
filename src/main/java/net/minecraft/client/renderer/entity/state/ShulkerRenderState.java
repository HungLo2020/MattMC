package net.minecraft.client.renderer.entity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ShulkerRenderState extends LivingEntityRenderState {
	public Vec3 renderOffset = Vec3.ZERO;
	@Nullable
	public DyeColor color;
	public float peekAmount;
	public float yHeadRot;
	public float yBodyRot;
	public Direction attachFace = Direction.DOWN;
}
