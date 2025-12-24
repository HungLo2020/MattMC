package net.minecraft.client.renderer.entity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.decoration.PaintingVariant;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class PaintingRenderState extends EntityRenderState {
	public Direction direction = Direction.NORTH;
	@Nullable
	public PaintingVariant variant;
	public int[] lightCoordsPerBlock = new int[0];
}
