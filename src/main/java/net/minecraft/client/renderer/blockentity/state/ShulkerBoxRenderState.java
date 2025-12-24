package net.minecraft.client.renderer.blockentity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ShulkerBoxRenderState extends BlockEntityRenderState {
	public Direction direction = Direction.NORTH;
	@Nullable
	public DyeColor color;
	public float progress;
}
