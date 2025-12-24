package net.minecraft.client.renderer.blockentity.state;

import net.minecraft.api.EnvType;
import net.minecraft.api.Environment;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class BellRenderState extends BlockEntityRenderState {
	@Nullable
	public Direction shakeDirection;
	public float ticks;
}
